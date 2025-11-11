package org.nyet.ecuxplot;

import java.io.PrintStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.FileReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.opencsv.CSVReader;
import flanagan.interpolation.CubicSpline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.nyet.ecuxplot.DataLogger.DataLoggerConfig;

import org.nyet.logfile.Dataset;
import org.nyet.util.DoubleArray;
import org.nyet.util.MonotonicDataAnalyzer;
import org.nyet.util.Smoothing;
import static org.nyet.util.Smoothing.Strategy;
import static org.nyet.util.Smoothing.Metadata;

public class ECUxDataset extends Dataset {
    private static final Logger logger = LoggerFactory.getLogger(ECUxDataset.class);

    private final Column rpm;      // Final RPM (quantization-aware adaptive smoothing, for display/calculations)
    private final Column baseRpm;  // Base RPM (SG smoothing only, for range detection, full dataset, no ranges)
    private final Column csvRpm;   // CSV RPM (native data from CSV, no smoothing)
    private Column pedal;
    private Column throttle;
    private Column gear;
    private final Column zboost;
    private final Env env;
    private final Filter filter;
    private double time_ticks_per_sec;  // ECUx has time in ms, JB4 in 1/10s
    private double samples_per_sec=0;
    private CubicSpline [] splines;     // rpm vs time splines
    private String log_detected;
    /**
     * Custom map for smoothing windows that provides a put(String, double) overload
     * to accept time in seconds and convert to samples internally.
     */
    private class SmoothingWindowsMap extends HashMap<String, Metadata> {
        private static final long serialVersionUID = 1L;

        /**
         * Register a column for smoothing with window size in seconds.
         * Converts to samples internally using samples_per_sec.
         * @param columnName The name of the column to register
         * @param seconds Smoothing window size in seconds
         * @return The previous Metadata for this column, or null if none
         */
        public Metadata put(String columnName, double seconds) {
            if (seconds <= 0) {
                // Zero or negative means no smoothing
                return super.put(columnName, new Metadata(0));
            }
            if (samples_per_sec <= 0) {
                logger.warn("smoothingWindows.put('{}', {}s): samples_per_sec is {}, cannot convert to samples. Using 0 (no smoothing).",
                    columnName, seconds, samples_per_sec);
                return super.put(columnName, new Metadata(0));
            }
            // Convert seconds to samples (rounding to nearest int)
            int windowSize = (int)Math.round(samples_per_sec * seconds);
            if (windowSize <= 0) {
                logger.warn("smoothingWindows.put('{}', {}s): Converted window size is {} samples (samples_per_sec={}), using 0 (no smoothing).",
                    columnName, seconds, windowSize, samples_per_sec);
                return super.put(columnName, new Metadata(0));
            }
            return super.put(columnName, new Metadata(windowSize));
        }

        /**
         * Register a column for smoothing with window size in samples (for backward compatibility).
         * @param columnName The name of the column to register
         * @param windowSize Smoothing window size in samples
         * @return The previous Metadata for this column, or null if none
         */
        public Metadata put(String columnName, int windowSize) {
            return super.put(columnName, new Metadata(windowSize));
        }
    }

    // Track which columns need moving average smoothing
    // Maps column name to smoothing window size (stored in samples, but registered in seconds)
    private final SmoothingWindowsMap smoothingWindows = new SmoothingWindowsMap();

    // Configurable smoothing parameters (for testing variants)
    // Defaults: MAW with DATA/DATA padding (generally superior to SG)
    public Smoothing.PaddingConfig padding = Smoothing.PaddingConfig.forStrategy(Strategy.MAW);
    public Strategy postDiffSmoothingStrategy = Strategy.MAW;


    // Track range failure reasons per row index
    // Used to explain why points "Not in valid range"
    private final Map<Integer, ArrayList<String>> rangeFailureReasons = new HashMap<>();

    // Track whether this dataset was loaded from preferences (auto-loaded on startup)
    // Used by Desktop handler to determine REPLACE vs ADD behavior
    private boolean loadedFromPrefs = false;

    /**
     * Mark this dataset as having been loaded from preferences.
     * Used to determine if Desktop handler should REPLACE (all prefs) or ADD (mixed/manual).
     */
    public void setLoadedFromPrefs(boolean value) {
        this.loadedFromPrefs = value;
    }

    /**
     * Check if this dataset was loaded from preferences.
     * @return true if this dataset was auto-loaded from preferences, false otherwise
     */
    public boolean isLoadedFromPrefs() {
        return this.loadedFromPrefs;
    }

    /**
     * Detect the logger type from comment lines and CSV field headers.
     * Tries detection in order: comment lines first, then CSV field headers.
     * Sets log_detected to the detected logger type or UNKNOWN if not found.
     *
     * @throws Exception If detection fails
     */
    protected void detectLoggerType() throws Exception {
        // Only detect if not already detected
        if (!DataLogger.isUnknown(this.log_detected)) {
            logger.info("{}: already detected as {}", this.getFileId(), this.log_detected);
            return;
        }

        logger.debug("Starting detection for {}", this.getFileId());

        // Step 1: Look in comment lines for signatures (these are raw text, NOT CSV)
        for (int lineNum = 0; lineNum < this.getComments().size(); lineNum++) {
            // Already trimmed by Dataset.getComments()
            String line = this.getComments().get(lineNum);
            logger.debug("Comment line {}: {}", lineNum, line);

            // Try detection on the entire comment line as raw text
            String t = DataLogger.detectComment(line);
            logger.debug("detectComment('{}') returned: '{}'", line, t);
            if (!DataLogger.isUnknown(t)) {
                logger.info("Detected {} based on comment line {}: \"{}\"", t, lineNum, line);
                this.log_detected = t;
                return;
            }
        }

        // Step 2: If no detection in comments, try field detection
        logger.debug("No logger type detected in comment lines, trying field detection");

        // Scan CSV lines for field detection
        String t = detectFieldInstance();
        logger.debug("detectField() returned: '{}'", t);
        if (!DataLogger.isUnknown(t)) {
                logger.info("Detected {} based on field line");
                this.log_detected = t;
                return;
        }

        // Step 3: Fallback to UNKNOWN
        logger.debug("No logger type detected, using UNKNOWN");
        this.log_detected = DataLogger.UNKNOWN;
    }

    /**
     * Detect logger type by scanning CSV field headers in the file.
     * Reads the file line by line, skipping comments and numeric data lines,
     * and attempts to detect the logger type from field headers.
     *
     * @return The detected logger type, or null if detection fails or file path not set
     */
    private String detectFieldInstance() {
        try {
            // If filePath is not set yet (called from parent constructor), skip field detection
            if (this.getFilePath() == null) {
                logger.debug("filePath not set yet, skipping field detection");
                return null;
            }

            // Read CSV lines from the file
            try (BufferedReader reader = new BufferedReader(new FileReader(this.getFilePath()))) {
                String line;
                boolean foundFirstCsvLine = false;
                while ((line = reader.readLine()) != null) {
                    // Skip comment empty or comment lines
                    if (line.trim().length() == 0 || Dataset.IsLineComment(line)) continue;
                    // Parse the CSV line using common method with separator fallback
                    String[] csvLine = Dataset.parseCSVLineWithFallback(line);
                    //  unexpected null or empty line
                    if (csvLine == null || csvLine.length == 0) return null;

                    // Skip lines where all CSV fields are empty (e.g., ",,,,,,,,,,,,,,,")
                    // Check if all fields are empty by seeing if any field has content
                    boolean hasContent = false;
                    for (String field : csvLine) {
                        if (field != null && field.trim().length() > 0) {
                            hasContent = true;
                            break;
                        }
                    }
                    if (!hasContent) continue;

                    boolean numericOrEmpty = allFieldsAreNumeric(csvLine, true);
                    if (!foundFirstCsvLine) {
                        if (numericOrEmpty) continue; // Skip numeric data, keep looking
                        foundFirstCsvLine = true;
                    } else if (numericOrEmpty) {
                        // We've found the first CSV line, and we've hit numeric data, so we're done
                        return null;
                    }

                    // Found first non-numeric CSV line (header) or continuing non-numeric data - try detection on it
                    String t = DataLogger.detectField(csvLine);
                    if (t != DataLogger.UNKNOWN) {
                        return t;
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to read CSV line for detection: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Check if all fields in a CSV line are numeric.
     * Used to distinguish header lines from data lines during logger detection.
     *
     * @param fields The array of field strings to check
     * @param ignoreEmpty If true, empty fields are ignored (treated as numeric)
     * @return true if all non-empty fields are numeric, false otherwise
     */
    private boolean allFieldsAreNumeric(String[] fields, boolean ignoreEmpty) {
        for (String field : fields) {
            if (ignoreEmpty && (field == null || field.trim().length() == 0)) continue;
            try {
                Float.parseFloat(field);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the detected logger type.
     * @return The logger type string (e.g., "ECUX", "JB4", "UNKNOWN")
     */
    public String getLogDetected() {
        return this.log_detected;
    }

    /**
     * Construct a new ECUxDataset from a CSV file.
     * Initializes the dataset, detects logger type, parses headers, and creates base columns.
     *
     * @param filename The path to the CSV file to load
     * @param env The environment configuration (vehicle constants, preferences)
     * @param filter The filter configuration for range detection
     * @param verbose Verbosity level for logging (0=quiet, higher=more verbose)
     * @throws Exception If file cannot be read, logger detection fails, or header parsing fails
     */
    public ECUxDataset(String filename, Env env, Filter filter, int verbose)
            throws Exception {
        super(filename, verbose);

        this.env = env;
        this.filter = filter;

        // Debug logging to understand filter state
        if (filter == null) {
            logger.debug("ECUxDataset constructor: filter is null");
        } else {
            logger.debug("ECUxDataset constructor: filter is not null, enabled={}", filter.enabled());
        }

        // Get pedal, throttle, and gear columns using field preferences
        this.pedal = get(DataLogger.pedalField());
        this.throttle = get(DataLogger.throttleField());
        this.gear = get(DataLogger.gearField());

        // look for zeitronix boost for filtering
        this.zboost = get("Zeitronix Boost");
        /*
        if(this.pedal==null && this.throttle==null) {
            if(this.pedal==null) System.out.println("could not find pedal position data");
            if(this.throttle==null) System.out.println("could not find throttle position data");
        }
        */
        /* calculate smallest samples per second */
        // Use raw CSV TIME data (before smoothing) to calculate sample rate
        // This avoids circular dependency since TIME smoothing depends on samples_per_sec
        final Column rawTime = super.get("TIME");
        if (rawTime!=null) {
            // Convert raw TIME ticks to seconds for calculation
            final DoubleArray timeSeconds = rawTime.data.div(this.time_ticks_per_sec);
            final double[] timeArray = timeSeconds.toArray();

            // Calculate sample rate from time data
            this.samples_per_sec = MonotonicDataAnalyzer.calculateSampleRate(timeArray);

            if (this.samples_per_sec > 0) {
                logger.debug("samples_per_sec={} (from segment-aware analysis)", this.samples_per_sec);
            } else {
                logger.warn("samples_per_sec calculation failed (need at least 2 points)");
            }
        } else {
            logger.warn("TIME column is null, cannot calculate samples_per_sec");
        }

        // Convert CSV columns to standard units if needed
        // This happens after CSV data is loaded (super() constructor completed)
        convertCSVColumnsToStandardUnits();

        // check for 5120 logged without a 5120 template and double columns with unit of mBar if so
        final Column baroPressure = get("BaroPressure");
        if (baroPressure != null && baroPressure.data.size() > 0 && baroPressure.data.get(0) < 600) {
            //double time! ;)
            for (Column column : getColumns()) {
                if (column.getUnits() != null && column.getUnits().toLowerCase().equals("mbar")) {
                    for (int i = 1; i < column.data.size(); i++) {
                        column.data.set(i, column.data.get(i) * 2);
                    }
                }
            }
        }

        // Three-tier RPM architecture to break circular dependency:
        // 1. CSV RPM - native data from CSV (no smoothing)
        // 2. Base RPM - SG smoothing only (for range detection, full dataset, no ranges needed)
        // 3. Final RPM - quantization-aware adaptive smoothing (for display/calculations, uses ranges when available)

        this.csvRpm = super.get("RPM");  // CSV_NATIVE column, always available

        // Create base RPM for range detection (full dataset, no ranges)
        // This breaks the circular dependency: ranges need smoothed RPM, but this smoothing
        // doesn't need ranges, so it can be created before buildRanges()
        this.baseRpm = createBaseRpm();

        // Build ranges using base RPM (no dependency on final smoothing or ranges)
        // Note: buildRanges() is called by parent constructor, but we need to call it again
        // after filter is assigned to ensure proper spline creation
        buildRanges(); // regenerate ranges, splines

        // After ranges are built, create final RPM (can use ranges for quantization detection)
        this.rpm = get("RPM");
    }

    /**
     * Create base RPM for range detection.
     * This applies SG smoothing to the full dataset without using ranges.
     * Breaks the circular dependency: ranges need smoothed RPM, but this smoothing
     * doesn't need ranges, so it can be created before buildRanges().
     *
     * @return Base RPM column (SG smoothing only)
     */
    private Column createBaseRpm() {
        if (this.csvRpm == null) {
            logger.error("createBaseRpm(): csvRpm is null, cannot create base RPM");
            return null;
        }

        // Apply SG smoothing to full dataset (no ranges, no quantization detection)
        // Use standard SG smoothing - effective for range detection
        final DoubleArray smoothedData = this.csvRpm.data.smooth();

        return new Column("RPM", this.csvRpm.getId2(), UnitConstants.UNIT_RPM,
                         smoothedData, Dataset.ColumnType.PROCESSED_VARIANT);
    }

    /**
     * Get the base RPM column used for range detection.
     *
     * @return The base RPM column (SG smoothing only), or null if not available
     */
    public Column getBaseRpmColumn() {
        return this.baseRpm;
    }

    // Note: HPMAW() and AccelMAW() helper methods removed - smoothingWindows.put() now accepts seconds directly



    /**
     * Create a raw version of a column before applying processing/smoothing.
     * This ensures the original data is preserved before being replaced by processed versions.
     * The raw column name is automatically derived as baseColumnName + " - raw".
     *
     * @param baseColumnName The name of the base CSV column (e.g., "TIME", "RPM")
     * @param units The unit string for the raw column
     * @param dataTransform Optional function to transform the data (e.g., convert ticks to seconds)
     *                      If null, uses baseColumn.data directly
     * @return The raw Column if created, or null if base column doesn't exist or raw already exists
     */
    private Column createRawColumn(String baseColumnName, String units,
                                   java.util.function.Function<DoubleArray, DoubleArray> dataTransform) {
        String rawColumnName = baseColumnName + " - raw";

        // Check if raw column already exists
        if (super.get(rawColumnName) != null) {
            return null;
        }

        // Get base CSV column
        Column baseColumn = super.get(baseColumnName);
        if (baseColumn == null || baseColumn.getColumnType() != Dataset.ColumnType.CSV_NATIVE) {
            return null;
        }

        // Transform data if needed
        DoubleArray rawData = dataTransform != null ? dataTransform.apply(baseColumn.data) : baseColumn.data;

        // Create and store raw column, preserving id2 (original name) from base column
        String id2 = baseColumn.getId2();
        Column rawColumn = new Column(rawColumnName, id2, units, rawData, Dataset.ColumnType.PROCESSED_VARIANT);
        this.putColumn(rawColumn);

        return rawColumn;
    }

    /**
     * Get or create a raw column from a base CSV column.
     * Used by handlers like "TIME - raw" and "RPM - raw".
     * The raw column name is automatically derived as baseColumnName + " - raw".
     *
     * @param baseColumnName The name of the base CSV column (e.g., "TIME", "RPM")
     * @param units The unit string for the raw column
     * @param dataTransform Optional function to transform the data (e.g., convert ticks to seconds)
     *                      If null, uses baseColumn.data directly
     * @return The raw Column, or null if base column doesn't exist
     */
    private Column getOrCreateRawColumn(String baseColumnName, String units,
                                        java.util.function.Function<DoubleArray, DoubleArray> dataTransform) {
        String rawColumnName = baseColumnName + " - raw";

        // Check if raw column already exists
        Column existing = super.get(rawColumnName);
        if (existing != null) {
            return existing;
        }

        // Get base CSV column
        Column baseColumn = super.get(baseColumnName);
        if (baseColumn == null) {
            logger.error("_get('{}'): Base CSV column '{}' not found! Cannot create {} column.",
                rawColumnName, baseColumnName, rawColumnName);
            return null;
        }

        // Transform data if needed
        DoubleArray rawData = dataTransform != null ? dataTransform.apply(baseColumn.data) : baseColumn.data;

        // Create and store raw column, preserving id2 (original name) from base column
        String id2 = baseColumn.getId2();
        Column rawColumn = new Column(rawColumnName, id2, units, rawData, Dataset.ColumnType.PROCESSED_VARIANT);
        this.putColumn(rawColumn);

        return rawColumn;
    }

    /**
     * Helper to create a smoothed/processed column from a base CSV column.
     * Handles the common pattern: get base column, null check, create raw, transform, smooth, create final column.
     * The raw column name is automatically derived as baseColumnName + " - raw".
     *
     * @param baseColumnName The name of the base CSV column (e.g., "TIME", "RPM"), also used as the final column name
     * @param units The unit string for the column
     * @param dataTransform Optional function to transform base data (e.g., convert ticks to seconds)
     *                      If null, uses baseColumn.data directly
     * @param threshold Minimum samples_per_sec to apply Savitzky-Golay smoothing (0.0 = no smoothing)
     * @return The processed Column, or null if base column doesn't exist
     */
    private Column createSmoothedColumn(String baseColumnName, String units,
                                         java.util.function.Function<DoubleArray, DoubleArray> dataTransform,
                                         double threshold) {
        // Get base CSV column
        Column baseColumn = super.get(baseColumnName);
        if (baseColumn == null) {
            logger.error("_get('{}'): Base CSV column '{}' not found! Cannot create smoothed column.",
                baseColumnName, baseColumnName);
            return null;
        }

        // Save raw column BEFORE smoothing (needs access to CSV_NATIVE before it gets replaced)
        createRawColumn(baseColumnName, units, dataTransform);

        // Transform data if needed
        DoubleArray processedData = dataTransform != null ? dataTransform.apply(baseColumn.data) : baseColumn.data;

        // Apply adaptive smoothing for RPM data (quantization detection + MA/SG selection)
        // Other columns use standard SG smoothing
        if (baseColumnName.equals("RPM")) {
            // Use csvRpm column for quantization detection (guaranteed to be CSV_NATIVE, not processed)
            // This ensures we're analyzing the actual CSV data, not a processed version
            // Note: For RPM, dataTransform is null, so processedData == baseColumn.data
            // But baseColumn might be a processed version if get("RPM") was called before,
            // so we use this.csvRpm.data which is always the CSV_NATIVE column
            final DoubleArray csvRpmData = (this.csvRpm != null) ? this.csvRpm.data : processedData;
            // Detect quantization: use ranges if available (avoids false positives from idle/deceleration)
            // But don't require ranges - this is called during column creation which may happen
            // before buildRanges() completes (to avoid dependency loops)
            final ArrayList<Dataset.Range> ranges = this.getRanges();
            // Only use ranges if they're already built (non-empty), otherwise analyze full dataset
            // Use csvRpmData for quantization detection and smoothing (they're the same for RPM)
            processedData = Smoothing.smoothAdaptive(csvRpmData,
                (ranges != null && !ranges.isEmpty()) ? ranges : null,
                baseColumnName);
        } else {
            // Non-RPM columns: use standard SG smoothing
            processedData = processedData.smooth();
        }

        // Create and return final column, preserving id2 (original name) from base column
        String id2 = baseColumn.getId2();
        return new Column(baseColumnName, id2, units, processedData, Dataset.ColumnType.PROCESSED_VARIANT);
    }

    /**
     * Create a smoothed TIME column.
     * Smooths time deltas (intervals) rather than absolute time to prevent drift.
     *
     * @return The processed TIME Column, or null if TIME column doesn't exist
     */
    private Column createSegmentAwareSmoothedTimeColumn() {
        // Get base CSV column
        Column baseColumn = super.get("TIME");
        if (baseColumn == null) {
            logger.error("_get('TIME'): Base CSV column 'TIME' not found! Cannot create smoothed column.");
            return null;
        }

        // Save raw column BEFORE smoothing
        createRawColumn("TIME", UnitConstants.UNIT_SECONDS,
                       (data) -> data.div(this.time_ticks_per_sec));

        // Transform data: convert ticks to seconds
        DoubleArray processedData = baseColumn.data.div(this.time_ticks_per_sec);
        double[] timeArray = processedData.toArray();

        // Apply smoothing if threshold is met (5.0 samples/sec)
        double[] smoothedTime = MonotonicDataAnalyzer.smoothTimeByDeltas(timeArray, this.samples_per_sec);
        if (smoothedTime != timeArray) {
            processedData = new DoubleArray(smoothedTime);
            logger.debug("_get('TIME'): Applied smoothing to time deltas (samples_per_sec={})", this.samples_per_sec);
        }

        // Create and return final column, preserving id2 (original name) from base column
        String id2 = baseColumn.getId2();
        return new Column("TIME", id2, UnitConstants.UNIT_SECONDS, processedData, Dataset.ColumnType.PROCESSED_VARIANT);
    }

    /**
     * Get a column in the specified unit, converting if necessary.
     * @param columnName The name of the column to get
     * @param targetUnit The target unit (from UnitConstants) to convert to, or null to get in original unit
     * @return Column in the requested unit, or null if column doesn't exist
     */
    public Column getColumnInUnits(String columnName, String targetUnit) {
        return getColumnInUnits(columnName, targetUnit, null);
    }

    /**
     * Get a column in the specified unit, converting if necessary.
     *
     * @param columnName The name of the column to get
     * @param targetUnit The target unit (from UnitConstants) to convert to
     * @param targetId The target ID to use for storing the converted column (e.g., "VehicleSpeed (mph)").
     *               If null, will be constructed as "columnName (targetUnit)"
     * @return Column in the requested unit, or null if column doesn't exist
     */
    public Column getColumnInUnits(String columnName, String targetUnit, String targetId) {
        // Construct targetId if not provided
        if (targetId == null && targetUnit != null && !targetUnit.isEmpty()) {
            targetId = columnName + " (" + targetUnit + ")";
        }
        // Get base column - use super.get() to get raw CSV_NATIVE column
        // (normalization is handled in _get(), so we need the raw column for unit conversion)
        // For calculated columns, we'd need _get(), but unit conversion only works on CSV_NATIVE columns
        Column column = super.get(columnName);
        if(column == null || targetUnit == null || targetUnit.isEmpty()) {
            return column;
        }

        // Use native unit (u2) for conversion logic, not display unit (getUnits())
        String currentUnit = column.getNativeUnits();
        if(currentUnit == null || currentUnit.isEmpty() || currentUnit.equals(targetUnit)) {
            return column;
        }

        // Convert to target unit using DatasetUnits helper
        // Provide ambient pressure supplier that gets BaroPressure normalized to mBar
        java.util.function.Supplier<Double> ambientSupplier = () -> {
            Column baro = super.get("BaroPressure");
            if (baro != null) {
                return DatasetUnits.normalizeBaroToMbar(baro);
            }
            return null;
        };
        // Inherit type from base column for unit conversions
        Dataset.ColumnType columnType = column.getColumnType();
        if (columnType == Dataset.ColumnType.CSV_NATIVE) {
            columnType = Dataset.ColumnType.COMPILE_TIME_CONSTANTS;
        }
        Column converted = DatasetUnits.convertUnits(this, column, targetUnit, ambientSupplier, columnType, targetId);
        // LinkedHashMap automatically handles duplicates - put() replaces existing column with same ID
        if (converted != column) {
            this.putColumn(converted);
            // Inherit smoothing registration from base column if base column is registered
            // This ensures unit-converted columns (e.g., "WTQ (Nm)") get smoothing if base column ("WTQ") has it
            Metadata baseMetadata = this.smoothingWindows.get(columnName);
            if (baseMetadata != null && targetId != null) {
                this.smoothingWindows.put(targetId, baseMetadata.windowSize);
            }
        }
        return converted;
    }

    /**
     * Convert a CSV_NATIVE column from native units to normalized units.
     * This is used by _get() to automatically convert normalized columns for display.
     * Uses direct conversion to avoid recursion (doesn't call _get() or getColumnInUnits()).
     *
     * @param baseColumn The base CSV_NATIVE column with native data
     * @param nativeUnit The native unit (from u2)
     * @param normalizedUnit The normalized unit (from unit)
     * @param targetId The target ID for the converted column (usually same as base column ID)
     * @return The converted column, or null if conversion fails
     */
    private Column convertColumnToNormalizedUnits(Column baseColumn, String nativeUnit, String normalizedUnit, String targetId) {
        // Provide ambient pressure supplier that gets BaroPressure normalized to mBar
        java.util.function.Supplier<Double> ambientSupplier = () -> {
            Column baro = super.get("BaroPressure");
            if (baro != null) {
                return DatasetUnits.normalizeBaroToMbar(baro);
            }
            return null;
        };
        // Convert using DatasetUnits - this creates a new column with converted data
        // Use COMPILE_TIME_CONSTANTS type for converted columns (inherited from CSV_NATIVE)
        Column converted = DatasetUnits.convertUnits(this, baseColumn, normalizedUnit, ambientSupplier,
            Dataset.ColumnType.COMPILE_TIME_CONSTANTS, targetId);
        // Inherit smoothing registration from base column if base column is registered
        if (converted != baseColumn && targetId != null) {
            String baseColumnName = baseColumn.getId();
            Metadata baseMetadata = this.smoothingWindows.get(baseColumnName);
            if (baseMetadata != null) {
                this.smoothingWindows.put(targetId, baseMetadata.windowSize);
            }
        }
        return converted;
    }

    /**
     * Check if RPM shows a severe swing (non-monotonic behavior) by comparing
     * the RPM at point i-1 and i+1, converting the delta to RPM/sec for consistency
     * across different logger sample rates.
     * Uses base RPM for range detection (breaks circular dependency).
     *
     * @param i The current point index to check
     * @return The RPM drop rate in RPM/s if it exceeds threshold, or 0.0 if valid
     */
    private double checkRPMMonotonicity(int i) {
        if (this.baseRpm == null || i <= 0 || this.baseRpm.data.size() <= i + 2) {
            return 0.0;
        }

        double delta = this.baseRpm.data.get(i-1) - this.baseRpm.data.get(i+1);
        // Convert delta (RPM over 2 samples) to RPM/sec
        // Time between samples: 1.0 / samples_per_sec
        // Time over 2 samples: 2.0 / samples_per_sec
        if (this.samples_per_sec > 0) {
            double timeDelta = 2.0 / this.samples_per_sec;
            double deltaRPMPerSec = delta / timeDelta;
            if (deltaRPMPerSec > this.filter.monotonicRPMfuzz()) {
                return deltaRPMPerSec;
            }
        } else {
            // Fallback: samples_per_sec invalid, use conservative threshold
            // Assume 10 Hz = 0.2s per 2 samples, so threshold = fuzz * 0.2
            double conservativeThreshold = this.filter.monotonicRPMfuzz() * 0.2;
            if (delta > conservativeThreshold) {
                // Return a value indicating failure, but we don't have RPM/s to report
                return Double.MAX_VALUE;
            }
        }
        return 0.0;
    }

    /**
     * Calculate acceleration from base RPM and TIME data using user-specified AccelMAW.
     * Used for range detection only - avoids dependency on final RPM.
     * This breaks the circular dependency: range detection uses base RPM.
     *
     * NOTE: This uses a different approach than display columns:
     * - Display columns: derivative(x, 0) + range-aware smoothing in getData()
     * - This method: derivative(x, AccelMAW()) with smoothing during derivative
     *
     * Reason: This is called during buildRanges() when ranges don't exist yet,
     * so we can't use range-aware smoothing. Instead, smoothing is applied to
     * the full dataset during derivative calculation. Both approaches use the
     * same AccelMAW() window for consistency.
     *
     * @param i The current point index to check
     * @return Acceleration in RPM/s, or 0.0 if calculation not possible
     */
    private double calculateRangeDetectionAcceleration(int i) {
        if (this.baseRpm == null || i < 0 || i >= this.baseRpm.data.size()) {
            return 0.0;
        }

        // Get TIME column for derivative calculation
        Column timeCol = super.get("TIME");
        if (timeCol == null || i >= timeCol.data.size()) {
            return 0.0;
        }

        // Calculate derivative with smoothing applied during derivative calculation
        // (Not range-aware because ranges don't exist yet - this is called during range detection)
        final DoubleArray y = this.baseRpm.data;
        final DoubleArray x = timeCol.data;
        // Convert accelMAW from seconds to samples for derivative smoothing
        final int accelMAW = (int)Math.round(this.samples_per_sec * this.filter.accelMAW());
        final DoubleArray derivative = y.derivative(x, accelMAW).max(0);

        // Extract value at index i (clamp to valid range)
        if (i >= derivative.size()) {
            return 0.0;
        }

        return derivative.get(i);
    }

    /**
     * Get the acceleration value used by dataValid() for filtering.
     * This matches what the filter actually checks, so visualization can show the same values.
     *
     * @param i The point index
     * @return Acceleration in RPM/s, or 0.0 if not available
     */
    public double getFilterAcceleration(int i) {
        return calculateRangeDetectionAcceleration(i);
    }



    /**
     * Parse CSV headers using logger-specific configuration.
     * Applies TIME scaling correction and processes headers according to detected logger type.
     *
     * @param reader The CSVReader instance for reading header lines
     * @param verbose Verbosity level for logging (0=quiet, higher=more verbose)
     * @throws Exception If header processing fails or logger configuration is invalid
     */
    @Override
    public void ParseHeaders(CSVReader reader, int verbose) throws Exception {
        final String logType = this.log_detected;

        logger.debug("ParseHeaders starting for {}, currently {} (verbose={})", logType, verbose);

        // Get logger configuration for cleaner API
        DataLoggerConfig config = DataLogger.getConfig(logType);
        if (config == null) {
            logger.error("No configuration found for logger type: {}", logType);
            return;
        }

        // Apply TIME scaling correction (ticks per second) from logger configuration
        this.time_ticks_per_sec = config.getTimeTicksPerSec();

        // Process all headers using the logger configuration
        DataLogger.HeaderData h = config.processHeaders(reader, verbose);
        if (h == null) {
            logger.error("Failed to process headers for {}", logType);
            return;
        }

        // Normalize units to standard units based on default preference (US_CUSTOMARY)
        // Keeps h.u[] in native units, stores normalized units separately
        normalizeUnitsToStandard(h);

        // Create DatasetId objects with normalized units (for menu consistency)
        // Use normalized units for DatasetId while keeping Column units native
        // Pass normalizedUnits and nativeUnits maps directly to setIds() - no temporary modification needed
        this.setIds(h, config, this.normalizedUnits, this.nativeUnits);
    }

    // Track native units for columns that need conversion
    // Maps canonical column name -> native unit string
    // Must be initialized lazily because it's accessed from ParseHeaders() which is called
    // from super() constructor before field initializers run
    private Map<String, String> nativeUnits;

    // Track normalized units for DatasetId generation
    // Maps canonical column name -> normalized unit string
    // Used to create DatasetId with normalized units while keeping Column units native
    private Map<String, String> normalizedUnits;

    /**
     * Normalize units for DatasetId generation.
     *
     * Implementation:
     * - Keeps h.u[] in native units (for Column creation - unit conversion system needs native units)
     * - Stores normalized units separately (for DatasetId generation - menu needs normalized units)
     * - Data remains in native units (no conversion at load time)
     *
     * @param h HeaderData containing canonical names (h.id[]) and native units (h.u[])
     */
    private void normalizeUnitsToStandard(DataLogger.HeaderData h) {
        if (h == null || h.id == null || h.u == null) {
            return;
        }

        UnitPreference preference = UnitPreference.US_CUSTOMARY;

        // Normalize each column's unit for DatasetId, but keep h.u[] in native units
        for (int i = 0; i < h.id.length && i < h.u.length; i++) {
            try {
                String canonicalName = h.id[i];
                String nativeUnit = h.u[i];

                // Skip if canonical name or native unit is null/empty
                if (canonicalName == null || canonicalName.isEmpty() ||
                    nativeUnit == null || nativeUnit.isEmpty()) {
                    continue;
                }

                // Get standard unit for this column
                // Units.getStandardUnit() will handle unit preference matching
                // The unit strings themselves (e.g., "mBar[gauge]") already indicate gauge vs absolute
                String standardUnit = Units.getStandardUnit(canonicalName, nativeUnit, preference);

                if (standardUnit != null && !standardUnit.equals(nativeUnit)) {
                    // Store native unit for unit conversion system
                    if (this.nativeUnits == null) {
                        this.nativeUnits = new HashMap<String, String>();
                    }
                    this.nativeUnits.put(canonicalName, nativeUnit);

                    // Store normalized unit for DatasetId generation (menu consistency)
                    if (this.normalizedUnits == null) {
                        this.normalizedUnits = new HashMap<String, String>();
                    }
                    this.normalizedUnits.put(canonicalName, standardUnit);

                    // DO NOT update h.u[] - keep it in native units for Column creation
                    // This allows unit conversion system to work correctly with native units
                }
            } catch (Exception e) {
                // If normalization fails for one column, log and continue with others
                logger.warn("Failed to normalize unit for column {}: {}", i, e.getMessage());
            }
        }
    }

    /**
     * Validate DatasetId.u2 is populated correctly.
     *
     * With shared DatasetId references, Column.getUnits() already returns u2.
     * So we don't need to create new Columns - just validate that DatasetId.u2 is set.
     * DatasetId units remain normalized (for menu consistency).
     *
     * This method is called from the constructor after super() completes (CSV data loaded).
     */
    private void convertCSVColumnsToStandardUnits() {
        // With shared DatasetId references, Column.getUnits() already returns u2
        // So we don't need to create new Columns!
        // Just validate that DatasetId.u2 is populated correctly

        if (this.nativeUnits != null) {
            for (Map.Entry<String, String> entry : this.nativeUnits.entrySet()) {
                String canonicalName = entry.getKey();
                String expectedNativeUnit = entry.getValue();

                // Find DatasetId and verify u2 is set
                for (DatasetId id : this.getIds()) {
                    if (id.id.equals(canonicalName)) {
                        // Validate String reference is shared (optional assertion)
                        assert id.u2 == expectedNativeUnit ||
                               id.u2.equals(expectedNativeUnit);
                        logger.debug("Verified {} u2 is set to {} (original unit intent)",
                            canonicalName, id.u2);
                        break;
                    }
                }
            }
        }

        logger.debug("Validated {} columns have u2 set (DatasetId units remain normalized for menu)",
            this.nativeUnits != null ? this.nativeUnits.size() : 0);

        // DO NOT clear nativeUnits map - we need it for on-demand conversion
        // The map will be used when charts request base columns that need normalization
    }



    /**
     * Get a column by ID with error handling.
     * Wraps _get() with NullPointerException handling for better error messages.
     *
     * @param id The column ID to retrieve
     * @return The Column if found or calculated, null otherwise
     */
    @Override
    public Column get(Comparable<?> id) {
        try {
            return _get(id);
        } catch (final NullPointerException e) {
            // Provide more context about what failed - check if it's env-related
            String cause = e.getMessage();
            if (cause != null && cause.contains("this.env")) {
                logger.warn("get('{}'): NullPointerException - env is null (expected in test contexts): {}", id, cause);
            } else {
                logger.warn("get('{}'): NullPointerException getting column: {}", id, cause != null ? cause : e.getClass().getName());
            }
            return null;
        }
    }

    /**
     * Core method for retrieving or calculating columns.
     * Handles recursion protection, unit conversions, and delegates to handlers.
     * This is the main entry point for all column retrieval after initial CSV loading.
     *
     * @param id The column ID to retrieve
     * @return The Column if found or calculated, null otherwise
     */
    private Column _get(Comparable<?> id) {
        String idStr = id.toString();

        // First check if a calculated column with this ID already exists
        // This prevents memory leaks from creating duplicate calculated columns
        // and breaks infinite recursion (once calculated, subsequent requests return cached version)
        Column existing = super.get(idStr);
        if (existing != null && existing.getColumnType() != Dataset.ColumnType.CSV_NATIVE) {
            return existing;
        }

        // ========== GENERIC UNIT CONVERSION HANDLER ==========
        // This handler parses "FieldName (unit)" pattern from menu items (e.g., "VehicleSpeed (mph)"),
        // retrieves the base field (e.g., "VehicleSpeed"), performs the unit conversion, and returns
        // a new Column with the converted data. This eliminates the need for individual handlers for
        // each unit-converted field (previously there were 16+ hardcoded conversion handlers).
        //
        // Flow:
        // 1. parseUnitConversion() extracts base field and target unit
        // 2. Retrieve base field from dataset
        // 3. getColumnInUnits() performs the actual conversion
        // 4. Returns new Column with converted data
        Units.ParsedUnitConversion parsed = Units.parseUnitConversion(idStr);
        if (parsed != null) {
            // Use this.get() instead of super.get() to ensure base column is created via _get()
            // This ensures smoothing registration happens before we try to inherit it
            Column baseColumn = this.get(parsed.baseField);
            if (baseColumn != null) {
                // Pass full requested ID so converted column is stored with correct ID (not base ID)
                return getColumnInUnits(parsed.baseField, parsed.targetUnit, idStr);
            }
        }

        Column c = null;

        // ========== HARD REQUIREMENT: HANDLERS MUST ALWAYS SET c ==========
        // Every handler in the if/then/else chain MUST set the variable 'c' when it matches.
        // This is a structural requirement - if a handler matches (the condition is true),
        // it MUST set 'c' to either:
        //   - A Column object (if the column can be created)
        //   - null (if the column cannot be created, but the handler still matched)
        // This ensures that the diagnostic column fallback (at the end) only runs when
        // NO handler matched, not when a handler matched but failed to set 'c'.
        // Violating this requirement breaks the chain logic and makes the code unpredictable.
        // ====================================================================

        // Try pattern-based routing first (optimization to skip handlers that won't match)
        c = AxisMenuHandlers.tryPatternRouting(this, id);
        if (c == null) {
            // Try all registered handlers (power/torque, diagnostic columns, etc.)
            c = AxisMenuHandlers.tryAllHandlers(this, id);
        }

        // Continue with individual field handlers if no registered handler matched
        if (c == null) {
            switch (idStr) {
                case "Sample": {
            final double[] idx = new double[this.length()];
            for (int i=0;i<this.length();i++)
                idx[i]=i;
            final DoubleArray a = new DoubleArray(idx);
            c = new Column("Sample", "#", a, Dataset.ColumnType.PROCESSED_VARIANT);
                    break;
                }
                case "TIME": {
            // Smooth TIME data to reduce jitter in sample rate calculations
            // Use segment-aware smoothing to avoid artifacts from time discontinuities
            c = createSegmentAwareSmoothedTimeColumn();
                    break;
                }
                case "TIME - raw": {
            c = getOrCreateRawColumn("TIME", UnitConstants.UNIT_SECONDS,
                                    (data) -> data.div(this.time_ticks_per_sec));
                    break;
                }
                case "RPM": {
            // smooth sampling quantum noise/jitter, RPM is an integer!
            // Always applies MA (if enough samples), then optionally SG
            c = createSmoothedColumn("RPM", UnitConstants.UNIT_RPM, null, 0.0);
                    break;
                }
                case "RPM - raw": {
            c = getOrCreateRawColumn("RPM", UnitConstants.UNIT_RPM, null);
                    break;
                }
                case "RPM - base": {
            // Debug column: return base RPM used for range detection
            c = this.baseRpm;
                    break;
                }
                default:
                    // No match - c remains null
                    break;
            }
            }

            if(c!=null) {
                // LinkedHashMap automatically handles duplicates - put() replaces existing column with same ID
                // This ensures CSV_NATIVE columns are replaced by calculated versions (TIME/RPM, BoostPressureActual/Desired, etc.)
                this.putColumn(c);
                return c;
            }

            // Fallback to base CSV_NATIVE column
            // Check if this is a unit conversion request (has " (unit)" pattern)
            // If so, don't normalize - unit conversion needs native data
            boolean isUnitConversionRequest = Units.parseUnitConversion(idStr) != null;
            Column baseColumn = super.get(id);
            if (baseColumn != null && baseColumn.getColumnType() == Dataset.ColumnType.CSV_NATIVE && !isUnitConversionRequest) {
                // Check if this column was normalized (unit != u2)
                // If so, automatically convert to normalized units for display
                // Skip normalization for unit conversion requests (they need native data)
                String normalizedUnit = baseColumn.getUnits();
                String nativeUnit = baseColumn.getNativeUnits();
                if (normalizedUnit != null && nativeUnit != null && !normalizedUnit.equals(nativeUnit)) {
                    // Column is normalized - convert data to normalized units for display
                    // Use direct conversion to avoid recursion (we're already in _get())
                    // Don't store converted column - preserve base column for unit conversion
                    // Convert on-demand each time (inefficient but correct)
                    Column converted = convertColumnToNormalizedUnits(baseColumn, nativeUnit, normalizedUnit, idStr);
                    if (converted != null && converted != baseColumn) {
                        // Return converted column without storing (base column remains for unit conversion)
                        return converted;
                    }
                }
            }
            return baseColumn;
    }

    /**
     * Check if a data point at index i passes all filter criteria.
     * Validates gear, pedal, throttle, acceleration, and RPM monotonicity.
     * Stores failure reasons for later retrieval via getFilterReasonsForRow().
     *
     * @param i The data point index to validate
     * @return true if the point passes all filter criteria, false otherwise
     */
    @Override
    protected boolean dataValid(int i) {
        boolean ret = true;
        if(this.filter==null) return ret;
        if(!this.filter.enabled()) return ret;

        final ArrayList<String> reasons = new ArrayList<String>();

        if(this.filter.gear()>=0 && this.gear!=null && Math.round(this.gear.data.get(i)) != this.filter.gear()) {
            reasons.add("gear " + Math.round(this.gear.data.get(i)) +
                    "!=" + this.filter.gear());
            ret=false;
        }
        if(this.pedal!=null && this.pedal.data.get(i)<this.filter.minPedal()) {
            reasons.add("ped " + String.format("%.1f", this.pedal.data.get(i)) +
                    "<" + this.filter.minPedal());
            ret=false;
        }
        if(this.throttle!=null && this.throttle.data.get(i)<this.filter.minThrottle()) {
            reasons.add("throt " + String.format("%.1f", this.throttle.data.get(i)) +
                    "<" + this.filter.minThrottle());
            ret=false;
        }
        if(this.filter.minAcceleration()>0) {
            // Calculate acceleration from base RPM (avoids dependency on final RPM)
            double accel = calculateRangeDetectionAcceleration(i);
            if(accel < this.filter.minAcceleration()) {
                reasons.add("accel " + String.format("%.0f", accel) +
                    "<" + this.filter.minAcceleration());
                ret=false;
            }
        }
        // Not user configurable
        if(this.zboost!=null && this.zboost.data.get(i)<0) {
            reasons.add("zboost " + String.format("%.1f", this.zboost.data.get(i)) +
                    "<0");
            ret=false;
        }
        // Check for negative boost pressure (wheel spin detection)
        // Not user configurable
        // Threshold: 1000 mBar (atmospheric pressure) - work in mBar for consistency
        final Column boostActual = getColumnInUnits("BoostPressureActual", UnitConstants.UNIT_MBAR);
        if(boostActual!=null && i < boostActual.data.size()) {
            double threshold = 1000.0; // 1000 mBar absolute
            if(boostActual.data.get(i) < threshold) {
                reasons.add("boost " + String.format("%.0f", boostActual.data.get(i)) +
                        " mBar <" + String.format("%.0f", threshold) + " mBar");
                ret=false;
            }
        }
        // Check for off throttle boost desired
        // Not user configurable
        // Threshold: 1000 mBar (atmospheric pressure) - work in mBar for consistency
        final Column boostDesired = getColumnInUnits("BoostPressureDesired", UnitConstants.UNIT_MBAR);
        if(boostDesired!=null && i < boostDesired.data.size()) {
            double threshold = 1000.0; // 1000 mBar absolute
            if(boostDesired.data.get(i) < threshold) {
                reasons.add("boost req " + String.format("%.0f", boostDesired.data.get(i)) +
                        " mBar <" + String.format("%.0f", threshold) + " mBar");
                ret=false;
            }
        }
        // Use base RPM for range detection checks (breaks circular dependency)
        // Base smoothing doesn't need ranges, so it can be created before buildRanges()
        if(this.baseRpm!=null) {
            if(this.baseRpm.data.get(i)<this.filter.minRPM()) {
                reasons.add("rpm " + String.format("%.0f", this.baseRpm.data.get(i)) +
                    "<" + this.filter.minRPM());
                ret=false;
            }
            if(this.baseRpm.data.get(i)>this.filter.maxRPM()) {
                reasons.add("rpm " + String.format("%.0f", this.baseRpm.data.get(i)) +
                    ">" + this.filter.maxRPM());
                ret=false;
            }
            if(i>0 && this.baseRpm.data.size()>i+2) {
                double dropRate = checkRPMMonotonicity(i);
                if(dropRate > 0.0) {
                    if(dropRate == Double.MAX_VALUE) {
                        // Fallback case: samples_per_sec invalid
                        reasons.add("Δrpm > threshold (samples_per_sec invalid)");
                    } else {
                        reasons.add("Δrpm " + String.format("%.1f", dropRate) + " RPM/s >" +
                            String.format("%.1f", this.filter.monotonicRPMfuzz()) + " RPM/s");
                    }
                    ret=false;
                }
            }
        }

        // Always update lastFilterReasons - clear it if valid, set it if invalid
        if (!ret) {
            this.lastFilterReasons = reasons;
            logger.trace("Filter rejected data point {}: {}", i, String.join(", ", reasons));
        } else {
            this.lastFilterReasons = new ArrayList<String>();
        }

        return ret;
    }

    /**
     * Get filter reasons for a row by calling the existing dataValid() logic
     * @param rowIndex The row index to check
     * @return Filter reasons (empty if valid)
     *
     * Note: Potential race condition/cache coherency issue:
     * This method calls dataValid() which modifies the shared instance variable
     * lastFilterReasons. If another thread calls dataValid() (e.g., during buildRanges())
     * between our dataValid() call and copying getLastFilterReasons(), we could get
     * reasons for the wrong row. In practice, this is unlikely because:
     * - FilterWindow runs on EDT (single-threaded)
     * - Each dataset instance has its own lastFilterReasons
     * - The copy happens immediately after dataValid() with minimal race window
     * However, if buildRanges() or other operations call dataValid() concurrently
     * on the same dataset, incorrect reasons could be returned.
     */
    public ArrayList<String> getFilterReasonsForRow(int rowIndex) {
        if(rowIndex < 0 || rowIndex >= this.length()) {
            return new ArrayList<String>();
        }
        // Use existing dataValid() logic which populates lastFilterReasons
        dataValid(rowIndex);
        return new ArrayList<String>(this.getLastFilterReasons());
    }

    /**
     * Get range failure reasons for a row that is not in any valid range
     * @param rowIndex The row index to check
     * @return Range failure reasons (empty if row is in a valid range or has no stored reasons)
     */
    public ArrayList<String> getRangeFailureReasons(int rowIndex) {
        if(rowIndex < 0 || rowIndex >= this.length()) {
            return new ArrayList<String>();
        }
        if (this.rangeFailureReasons == null) {
            return new ArrayList<String>();
        }
        ArrayList<String> reasons = this.rangeFailureReasons.get(rowIndex);
        return reasons != null ? new ArrayList<String>(reasons) : new ArrayList<String>();
    }

    /**
     * Check if a range passes validation criteria.
     * Validates minimum point count and RPM range requirements.
     * Stores failure reasons for points in failed ranges.
     *
     * @param r The range to validate
     * @return true if the range passes all validation criteria, false otherwise
     */
    @Override
    protected boolean rangeValid(Range r) {
        boolean ret = true;
        if(this.filter==null) return ret;
        if(!this.filter.enabled()) return ret;

        final ArrayList<String> reasons = new ArrayList<String>();

        if(r.size()<this.filter.minPoints()) {
            reasons.add("pts " + r.size() + "<" + this.filter.minPoints());
            ret=false;
        }
        if(this.rpm!=null) {
            if(this.rpm.data.get(r.end)<this.rpm.data.get(r.start)+this.filter.minRPMRange()) {
                reasons.add("rpm " + String.format("%.0f", this.rpm.data.get(r.end)) +
                    "<" + String.format("%.0f", this.rpm.data.get(r.start)) + "+" +this.filter.minRPMRange());
                ret=false;
            }
        }

        if (!ret) {
            this.lastFilterReasons = reasons;
            // Store failure reasons for all points in this failed range (only if initialized)
            if (this.rangeFailureReasons != null) {
                ArrayList<String> failureReasons = new ArrayList<String>(reasons);
                for(int rowIdx = r.start; rowIdx <= r.end; rowIdx++) {
                    this.rangeFailureReasons.put(rowIdx, failureReasons);
                }
            }
            logger.trace("Filter rejected range {}: {}", r, String.join(", ", reasons));
        }

        return ret;
    }

    /**
     * Create a null PrintStream that discards all output.
     * Used to suppress stdout during certain operations.
     *
     * @return The original System.out PrintStream (before it was replaced)
     */
    private static final PrintStream nullStdout() {
        PrintStream original = System.out;
        System.setOut(new PrintStream(new OutputStream() {
                    public void write(int b) {
                        //DO NOTHING
                    }
                }));
        return original;
    }

    /**
     * Normalize range to full dataset if null.
     * @param r The range, or null for full dataset
     * @return Normalized range, or null if dataset is empty
     */
    private Range normalizeRange(Range r) {
        if (r == null) {
            if (this.length() == 0) {
                return null;
            }
            return new Range(0, this.length() - 1);
        }
        return r;
    }

    /**
     * Find which range a given data point index belongs to.
     * @param ranges The list of ranges to search
     * @param index The data point index to find
     * @return The range containing the index, or null if not in any range
     */
    Dataset.Range findRangeForIndex(ArrayList<Dataset.Range> ranges, int index) {
        if (ranges == null) {
            return null;
        }
        for (Dataset.Range range : ranges) {
            if (index >= range.start && index <= range.end) {
                return range;
            }
        }
        return null;
    }

    /**
     * Apply range-aware smoothing to column data if configured.
     * @param column The column containing the data
     * @param columnName The name of the column (for lookup and logging)
     * @param r The range to extract and smooth
     * @return Smoothed data array, or raw data if smoothing not needed/applicable
     * Delegates to Smoothing.applySmoothing().
     */
    private double[] applySmoothing(Column column, String columnName, Range r) {
        return Smoothing.applySmoothing(column, columnName, r,
            this.smoothingWindows.get(columnName),
            this.postDiffSmoothingStrategy, this.padding.left, this.padding.right, logger);
    }

    /**
     * Get data array for a column over a specified range with range-aware smoothing applied.
     * Normalizes the range, retrieves the column, and applies smoothing if configured.
     *
     * @param id The column ID to retrieve
     * @param r The range to extract (null for full dataset)
     * @return Smoothed data array, or null if column not found or range invalid
     */
    @Override
    public double[] getData(Comparable<?> id, Range r) {
        r = normalizeRange(r);
        if (r == null) return null;

        final Column c = this.get(id);
        if (c == null) return null;

        // Apply range-aware smoothing if needed
        final String columnName = id.toString();
        return applySmoothing(c, columnName, r);
    }

    /**
     * Get smoothing window information for a column.
     * @param columnName The name of the column
     * @param rangeSize The size of the range (for calculating effective window after clamping)
     * @return An array with [originalWindow, effectiveWindow], or null if column is not registered for smoothing
     */
    public int[] getSmoothingWindowInfo(String columnName, int rangeSize) {
        Metadata metadata = this.smoothingWindows.get(columnName);
        if (metadata == null || metadata.windowSize <= 0) {
            return null;
        }
        final int originalWindow = metadata.windowSize;
        final int effectiveWindow = Smoothing.clampWindow(originalWindow, rangeSize);
        return new int[] { originalWindow, effectiveWindow };
    }

    /**
     * Get data array for a column using a Key (with filename and range context).
     * Routes through get() to handle unit conversions, then applies range-aware smoothing.
     *
     * @param id The Key containing the column ID, filename, and range context
     * @param r The range to extract (null for full dataset)
     * @return Smoothed data array, or null if column not found or range invalid
     */
    @Override
    public double[] getData(Key id, Range r) {
        // Route through get() which calls _get() for unit conversion handling
        // This ensures Keys with full IDs (e.g., "VehicleSpeed (mph)") are properly handled
        r = normalizeRange(r);
        if (r == null) return null;

        final String lookupId = id.getString();
        final Column c = this.get(lookupId);
        if (c == null) {
            // Check if this might be unexpected recursion behavior (base exists but conversion failed)
            Units.ParsedUnitConversion parsed = Units.parseUnitConversion(lookupId);
            if (parsed != null) {
                Column baseCol = super.get(parsed.baseField);
                if (baseCol != null) {
                    // Base column exists but conversion returned null - possible recursion issue
                    logger.warn("getData('{}'): Unit conversion returned null but base column exists - possible recursion issue. " +
                        "Requested ID: '{}', Base field: '{}', Target unit: '{}', " +
                        "Base column units: '{}', Base column type: '{}', " +
                        "Key: filename={}, range={}",
                        lookupId, lookupId, parsed.baseField, parsed.targetUnit,
                        baseCol.getUnits(), baseCol.getColumnType(),
                        id.getFilename(), id.getRange());
                }
            }
            return null;
        }
        // Apply range-aware smoothing if needed (shared logic with getData(Comparable<?>, Range))
        return applySmoothing(c, lookupId, r);
    }

    /**
     * Build valid ranges from the dataset using filter criteria.
     * Clears previous range failure reasons, calls parent method to build ranges,
     * and creates cubic splines for FATS calculations.
     */
    @Override
    public void buildRanges() {
        // Clear previous range failure reasons (only if initialized)
        // Note: field should always be initialized, but check for safety
        if (this.rangeFailureReasons != null) {
            this.rangeFailureReasons.clear();
        }

        // Build ranges - parent method will call rangeValid() which tracks failures
        // Note: dataValid() uses baseRpm (created before buildRanges()), so no circular dependency
        super.buildRanges();

        // Handle filter null case (timing issue during construction)
        if (this.filter == null) {
            logger.trace("Spline creation disabled: filter is null (called from parent constructor before filter assignment)");
            this.splines = new CubicSpline[0];
            return;
        }

        // Handle filter disabled case (user has explicitly disabled the filter)
        if (!this.filter.enabled()) {
            logger.debug("Spline creation disabled: filter is explicitly disabled by user");
            this.splines = new CubicSpline[0];
            return;
        }

        final ArrayList<Dataset.Range> ranges = this.getRanges();
        this.splines = new CubicSpline[ranges.size()];
        if (ranges.size() > 0) {
            logger.debug("Creating {} splines for {} ranges (filter enabled)", ranges.size(), ranges.size());
        } else {
            logger.trace("No valid ranges found for spline creation (filter enabled but no data passes filter criteria)");
        }
        for(int i=0;i<ranges.size();i++) {
            this.splines[i] = null;
            final Dataset.Range r=ranges.get(i);
            logger.debug("  buildRanges(): Creating spline for range {}: start={}, end={}", i, r.start, r.end);
            final double [] rpm = this.getData("RPM", r);
            final double [] time = this.getData("TIME", r);

            logger.debug("  buildRanges(): Range {} - RPM data: {}, TIME data: {}",
                i, rpm != null ? "present (length=" + rpm.length + ")" : "null",
                time != null ? "present (length=" + time.length + ")" : "null");

            // Need three points for a spline
            if(rpm == null || time == null || time.length != rpm.length || rpm.length<3) {
                logger.debug("  buildRanges(): Range {} - Skipping spline creation (rpm={}, time={}, lengths match={}, min points={})",
                    i, rpm != null, time != null,
                    (rpm != null && time != null) ? (rpm.length == time.length) : false,
                    (rpm != null && time != null) ? rpm.length >= 3 : false);
                continue;
            }

            PrintStream original = null;
            try {
                original = nullStdout();        // hack to disable junk that CubicSpline prints
                this.splines[i] = new CubicSpline(rpm, time);
                System.setOut(original);
                original = null;
                logger.debug("  buildRanges(): Successfully created spline for range {}", i);
            } catch (final Exception e) {
                // restore stdout if we caught something
                if(original != null) System.setOut(original);
                logger.warn("  buildRanges(): Failed to create spline for range {}: {}", i, e.getMessage());
            }
        }
    }

    /**
     * Calculate FATS (For the Advancement of the S4) for a specific run
     *
     * This is the unified method that handles all speed units (RPM, MPH, KPH).
     * It converts speed values to RPM if needed, then performs the FATS calculation.
     *
     * @param run The run number (0-based index into the ranges array)
     * @param speedStart The starting speed value (in RPM, MPH, or KPH)
     * @param speedEnd The ending speed value (in RPM, MPH, or KPH)
     * @param speedUnit The unit of the speed values (RPM, MPH, or KPH)
     * @return The elapsed time in seconds between the specified speed points
     * @throws Exception If the run is invalid, interpolation failed, or calculation error occurs
     */
    public double calcFATS(int run, double speedStart, double speedEnd, FATS.SpeedUnit speedUnit) throws Exception {
        if (this.filter == null) {
            logger.warn("FATS calculation disabled: filter is null");
            throw new Exception("FATS calculation requires filter to be initialized");
        }

        if (!this.filter.enabled()) {
            logger.warn("FATS calculation disabled: filter is explicitly disabled by user");
            throw new Exception("FATS calculation requires filter to be enabled");
        }

        final ArrayList<Dataset.Range> ranges = this.getRanges();
        logger.debug("      calcFATS(): Checking run {} - ranges.size()={}, splines.length={}",
            run, ranges.size(), this.splines != null ? this.splines.length : 0);
        if(run<0 || run>=ranges.size())
            throw new Exception("FATS run " + run + " not found (available: 0-" + (ranges.size()-1) + ")");

        if(this.splines == null) {
            logger.debug("      calcFATS(): splines array is null for run {}", run);
            throw new Exception("FATS run " + run + " interpolation failed - splines array is null");
        }
        if(this.splines.length <= run) {
            logger.debug("      calcFATS(): splines array length {} is too small for run {}", this.splines.length, run);
            throw new Exception("FATS run " + run + " interpolation failed - splines array length mismatch");
        }
        if(this.splines[run]==null) {
            logger.debug("      calcFATS(): spline[{}] is null - spline was not created during buildRanges()", run);
            throw new Exception("FATS run " + run + " interpolation failed - check filter settings");
        }
        logger.debug("      calcFATS(): Spline exists for run {}", run);

        final Dataset.Range r=ranges.get(run);
        logger.trace("FATS {} calculation: run={}, range={}-{}", speedUnit.getDisplayName(), run, r.start, r.end);

        int rpmStart, rpmEnd;
        switch (speedUnit) {
            case RPM:
                // Direct RPM values
                rpmStart = (int) Math.round(speedStart);
                rpmEnd = (int) Math.round(speedEnd);
                logger.trace("FATS RPM calculation: {} RPM -> {} RPM", rpmStart, rpmEnd);
                break;
            case mph:
                // Convert MPH to RPM
                double rpmPerMph = this.env.c.rpm_per_mph();
                rpmStart = (int) Math.round(speedStart * rpmPerMph);
                rpmEnd = (int) Math.round(speedEnd * rpmPerMph);
                logger.trace("FATS MPH->RPM conversion: {} mph -> {} RPM, {} mph -> {} RPM",
                    speedStart, rpmStart, speedEnd, rpmEnd);
                break;
            case kmh:
                // Convert KPH to RPM
                double rpmPerKph = this.env.c.rpm_per_kph();
                rpmStart = (int) Math.round(speedStart * rpmPerKph);
                rpmEnd = (int) Math.round(speedEnd * rpmPerKph);
                logger.trace("FATS KPH->RPM conversion: {} kph -> {} RPM, {} kph -> {} RPM",
                    speedStart, rpmStart, speedEnd, rpmEnd);
                break;
            default:
                throw new IllegalArgumentException("Unsupported speed unit: " + speedUnit);
        }

        // Use RPM calculation
        return calcFATSRPM(run, rpmStart, rpmEnd);
    }

    /**
     * Calculate FATS (For the Advancement of the S4) for a specific run using RPM values
     *
     * @param run The run number (0-based index into the ranges array)
     * @param RPMStart The starting RPM value for the calculation
     * @param RPMEnd The ending RPM value for the calculation
     * @return The elapsed time in seconds between the specified RPM points
     * @throws Exception If the run is invalid, interpolation failed, or calculation error occurs
     */
    public double calcFATS(int run, int RPMStart, int RPMEnd) throws Exception {
        if (this.filter == null) {
            logger.warn("FATS calculation disabled: filter is null");
            throw new Exception("FATS calculation requires filter to be initialized");
        }

        if (!this.filter.enabled()) {
            logger.warn("FATS calculation disabled: filter is explicitly disabled by user");
            throw new Exception("FATS calculation requires filter to be enabled");
        }
        return calcFATS(run, (double)RPMStart, (double)RPMEnd, FATS.SpeedUnit.RPM);
    }

    /**
     * Calculate FATS (For the Advancement of the S4) for a specific run using speed values
     *
     * @param run The run number (0-based index into the ranges array)
     * @param speedStart The starting speed value (in MPH or KPH)
     * @param speedEnd The ending speed value (in MPH or KPH)
     * @param speedUnit The unit of the speed values (MPH or KPH)
     * @return The elapsed time in seconds between the specified speed points
     * @throws Exception If the run is invalid, interpolation failed, or calculation error occurs
     */
    public double calcFATSBySpeed(int run, double speedStart, double speedEnd, FATS.SpeedUnit speedUnit) throws Exception {
        if (this.filter == null) {
            logger.warn("FATS calculation disabled: filter is null");
            throw new Exception("FATS calculation requires filter to be initialized");
        }

        if (!this.filter.enabled()) {
            logger.warn("FATS calculation disabled: filter is explicitly disabled by user");
            throw new Exception("FATS calculation requires filter to be enabled");
        }
        return calcFATS(run, speedStart, speedEnd, speedUnit);
    }

    /**
     * Internal RPM-based FATS calculation method
     *
     * This method performs the core FATS calculation using cubic spline interpolation
     * on RPM vs time data. It is used by both RPM and MPH modes for consistency.
     *
     * @param run The run number (0-based index into the ranges array)
     * @param RPMStart The starting RPM value for the calculation
     * @param RPMEnd The ending RPM value for the calculation
     * @return The elapsed time in seconds between the specified RPM points
     * @throws Exception If the run is invalid, interpolation failed, or calculation error occurs
     */
    private double calcFATSRPM(int run, int RPMStart, int RPMEnd) throws Exception {
        if (this.filter == null) {
            logger.warn("FATS calculation disabled: filter is null");
            throw new Exception("FATS calculation requires filter to be initialized");
        }

        if (!this.filter.enabled()) {
            logger.warn("FATS calculation disabled: filter is explicitly disabled by user");
            throw new Exception("FATS calculation requires filter to be enabled");
        }

        final ArrayList<Dataset.Range> ranges = this.getRanges();
        if(run<0 || run>=ranges.size())
            throw new Exception("FATS run " + run + " not found (available: 0-" + (ranges.size()-1) + ")");

        if(this.splines[run]==null)
            throw new Exception("FATS run " + run + " interpolation failed - check filter settings");

        final Dataset.Range r=ranges.get(run);
        logger.trace("FATS RPM calculation: run={}, range={}-{}", run, r.start, r.end);

        // RPM-based calculation using filter range
        logger.trace("FATS RPM calculation: {} RPM -> {} RPM", RPMStart, RPMEnd);
        // Trust the filter - if we have a valid range, use spline interpolation
        final double et = this.splines[run].interpolate(RPMEnd) -
                this.splines[run].interpolate(RPMStart);
        if(et<=0)
            throw new Exception("FATS RPM calculation failed: timeEnd <= timeStart for RPM range " + RPMStart + "-" + RPMEnd);

        return et;
    }


    public double[] calcFATS(int RPMStart, int RPMEnd) {
        final ArrayList<Dataset.Range> ranges = this.getRanges();
        final double [] out = new double[ranges.size()];
        for(int i=0;i<ranges.size();i++) {
            try {
                out[i]=calcFATS(i, RPMStart, RPMEnd);
            } catch (final Exception e) {
            }
        }
        return out;
    }

    /**
     * Get the filter configuration.
     * @return The Filter instance used for range detection and data validation
     */
    public Filter getFilter() { return this.filter; }
    //public void setFilter(Filter f) { this.filter=f; }

    /**
     * Get the environment configuration.
     * @return The Env instance containing vehicle constants and preferences
     */
    public Env getEnv() { return this.env; }
    //public void setEnv(Env e) { this.env=e; }

    // ========== Package-private accessors for AxisMenuHandlers ==========
    // These methods provide controlled access to internal state for handler functions.

    /**
     * Get the base RPM column (SG smoothing only, used for range detection).
     * @return The base RPM column, or null if not available
     */
    Column getBaseRpm() { return this.baseRpm; }

    /**
     * Get the raw CSV RPM column (no smoothing, native CSV data).
     * @return The CSV RPM column, or null if not available
     */
    Column getCsvRpm() { return this.csvRpm; }

    /**
     * Get the sample rate (samples per second).
     * @return The number of samples per second, or 0.0 if not calculated
     */
    double getSamplesPerSec() { return this.samples_per_sec; }

    /**
     * Get the smoothing windows map.
     * @return The SmoothingWindowsMap instance for registering smoothing windows
     */
    SmoothingWindowsMap getSmoothingWindows() { return this.smoothingWindows; }

    /**
     * Get a CSV column directly without triggering calculations.
     * Calls super.get() to bypass _get() and avoid triggering calculated column handlers.
     * Used by handlers when they need raw CSV data.
     *
     * @param id The column ID to retrieve
     * @return The CSV column if found, null otherwise
     */
    Column getCsvColumn(Comparable<?> id) {
        return super.get(id);
    }

    /**
     * Create a Column instance from handler functions.
     * Column is a non-static inner class, so handlers need this helper to instantiate it.
     *
     * @param id The column ID
     * @param units The unit string
     * @param data The data array
     * @return A new Column instance
     */
    Column createColumn(Comparable<?> id, String units, DoubleArray data) {
        return new Column(id, units, data);
    }

    /**
     * Create a Column instance with a specific ColumnType.
     * Column is a non-static inner class, so handlers need this helper to instantiate it.
     *
     * @param id The column ID
     * @param units The unit string
     * @param data The data array
     * @param columnType The column type (e.g., VEHICLE_CONSTANTS, PROCESSED_VARIANT)
     * @return A new Column instance
     */
    Column createColumn(Comparable<?> id, String units, DoubleArray data, ColumnType columnType) {
        return new Column(id, units, data, columnType);
    }

    /**
     * Register a column for range-aware smoothing.
     * SmoothingWindowsMap.put(String, double) is not accessible from handlers,
     * so this helper method provides access.
     *
     * @param columnName The name of the column to register
     * @param seconds The smoothing window size in seconds (converted to samples internally)
     */
    void registerSmoothingWindow(String columnName, double seconds) {
        this.smoothingWindows.put(columnName, seconds);
    }
    /**
     * Check if alternate column names (id2) should be used for display.
     * Determined by the "altnames" preference in environment settings.
     *
     * @return true if alternate names should be used, false otherwise
     */
    @Override
    public boolean useId2() {
        // Handle null env gracefully (e.g., in test contexts where env is not provided)
        if (this.env == null || this.env.prefs == null) {
            return false;
        }
        return this.env.prefs.getBoolean("altnames", false);
    }

    /**
     * Helper method to construct a unit-converted column ID.
     * @param originalId The original column ID (e.g., "IntakeAirTemperature")
     * @param unit The target unit constant (e.g., UnitConstants.UNIT_CELSIUS)
     * @return Formatted string like "IntakeAirTemperature (°C)"
     */
    private static String idWithUnit(String originalId, String unit) {
        return String.format("%s (%s)", originalId, unit);
    }

    /**
     * Package-private helper for AxisMenuHandlers to construct unit-converted column IDs.
     * Delegates to idWithUnit() to format strings like "FieldName (unit)".
     *
     * @param originalId The original column ID
     * @param unit The target unit constant
     * @return Formatted string like "FieldName (unit)"
     */
    static String idWithUnitHelper(String originalId, String unit) {
        return idWithUnit(originalId, unit);
    }

    /**
     * Invalidate columns that depend on vehicle constants.
     * Called when vehicle constants change to force recalculation on next access.
     * Removes all columns with VEHICLE_CONSTANTS type.
     */
    public void invalidateConstantDependentColumns() {
        java.util.ArrayList<Column> columns = this.getColumns();
        int removedCount = 0;
        java.util.List<String> idsToRemove = new java.util.ArrayList<>();
        for (Column col : columns) {
            if (col.getColumnType() == Dataset.ColumnType.VEHICLE_CONSTANTS) {
                idsToRemove.add(col.getId());
            }
        }
        // Remove from the actual map (getColumns() returns a snapshot, so remove from map directly)
        for (String colId : idsToRemove) {
            if (this.removeColumn(colId) != null) {
                removedCount++;
            }
        }
        logger.debug("invalidateConstantDependentColumns(): Removed {} vehicle constant columns", removedCount);
    }

}

// vim: set sw=4 ts=8 expandtab:

