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
    // Track which columns need moving average smoothing
    // Maps column name to smoothing window size (in samples)
    private final Map<String, Integer> smoothingWindows = new HashMap<>();

    /**
     * Get the smoothing window size for a column, if it has one.
     * @param columnName The name of the column
     * @return The smoothing window size in samples, or null if column doesn't have smoothing
     */
    public Integer getSmoothingWindow(String columnName) {
        return this.smoothingWindows.get(columnName);
    }

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




    public String getLogDetected() {
        return this.log_detected;
    }

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
     * Get the CSV RPM column (native data from CSV).
     *
     * @return The CSV RPM column (CSV_NATIVE), or null if not available
     */
    public Column getCsvRpmColumn() {
        return this.csvRpm;
    }

    /**
     * Get the base RPM column used for range detection.
     *
     * @return The base RPM column (SG smoothing only), or null if not available
     */
    public Column getBaseRpmColumn() {
        return this.baseRpm;
    }

    private int HPMAW() {
        // HPMAW is in seconds, convert to samples (Smoothing constructor will make it odd)
        return (int)Math.round(this.samples_per_sec * this.filter.HPMAW());
    }

    private int AccelMAW() {
        // accelMAW is in seconds, convert to samples (Smoothing constructor will make it odd)
        return (int)Math.round(this.samples_per_sec * this.filter.accelMAW());
    }

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
     * Calculate torque from smoothed power data.
     * Creates a column with torque calculated from smoothed power (HP or WHP) and RPM.
     *
     * @param torqueId The ID for the torque column (e.g., "TQ" or "WTQ")
     * @param powerColumnName The name of the power column (e.g., "HP" or "WHP")
     * @return Column with calculated torque, or null if power/RPM data is unavailable
     */
    private Column calculateTorque(String torqueId, String powerColumnName) {
        final Range fullRange = new Range(0, this.length() - 1);
        final double[] smoothedPower = this.getData(powerColumnName, fullRange);
        final double[] rpm = this.getData("RPM", fullRange);
        if (smoothedPower == null || rpm == null || smoothedPower.length != rpm.length) {
            logger.error("_get('{}'): Failed to get smoothed {} or RPM data - {}={}, RPM={}, lengths match={}",
                torqueId, powerColumnName, powerColumnName, smoothedPower != null, rpm != null,
                smoothedPower != null && rpm != null ? smoothedPower.length == rpm.length : false);
            return null;
        }
        // Calculate torque from smoothed power
        final double[] torque = new double[smoothedPower.length];
        for (int i = 0; i < smoothedPower.length; i++) {
            torque[i] = smoothedPower[i] * UnitConstants.HP_CALCULATION_FACTOR / rpm[i];
        }
        String label = UnitConstants.UNIT_FTLB;
        if(this.env.sae.enabled()) label += " (SAE)";
        return new Column(torqueId, label, new DoubleArray(torque), Dataset.ColumnType.VEHICLE_CONSTANTS);
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
        // Get base column - use _get() to allow calculation and go through recursion protection
        Column column = _get(columnName);
        if(column == null || targetUnit == null || targetUnit.isEmpty()) {
            return column;
        }

        String currentUnit = column.getUnits();
        if(currentUnit == null || currentUnit.isEmpty() || currentUnit.equals(targetUnit)) {
            return column;
        }

        // Convert to target unit using DatasetUnits helper
        // Provide ambient pressure supplier that gets BaroPressure normalized to mBar
        java.util.function.Supplier<Double> ambientSupplier = () -> {
            Column baro = super.get("BaroPressure");
            if (baro != null && baro.data != null && baro.data.size() > 0) {
                double ambient = baro.data.get(0);
                // Normalize to mBar if needed (inline conversion, no recursion risk)
                String baroUnit = baro.getUnits();
                if (baroUnit != null && baroUnit.equals(UnitConstants.UNIT_KPA)) {
                    ambient = ambient * UnitConstants.MBAR_PER_KPA;
                } else if (baroUnit != null && baroUnit.equals(UnitConstants.UNIT_PSI)) {
                    // PSI gauge to mBar absolute (BaroPressure should never be PSI, but handle it)
                    ambient = ambient * UnitConstants.MBAR_PER_PSI + UnitConstants.MBAR_PER_ATM;
                }
                // If mBar or null, use as-is
                return ambient;
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
        final DoubleArray derivative = y.derivative(x, this.AccelMAW()).max(0);

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

        // Create DatasetId objects
        this.setIds(h, config);
    }

    private DoubleArray drag (DoubleArray v) {
        final DoubleArray windDrag = v.pow(3).mult(0.5 * UnitConstants.AIR_DENSITY_STANDARD * this.env.c.Cd() *
            this.env.c.FA());

        final DoubleArray rollingDrag = v.mult(this.env.c.rolling_drag() *
            this.env.c.mass() * UnitConstants.STANDARD_GRAVITY);

        return windDrag.add(rollingDrag);
    }


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
            Column baseColumn = super.get(parsed.baseField);
            if (baseColumn != null) {
                // Pass full requested ID so converted column is stored with correct ID (not base ID)
                return getColumnInUnits(parsed.baseField, parsed.targetUnit, idStr);
            }
        }

        Column c = null;

            // ========== BASIC FIELDS ==========
        if(id.equals("Sample")) {
            final double[] idx = new double[this.length()];
            for (int i=0;i<this.length();i++)
                idx[i]=i;
            final DoubleArray a = new DoubleArray(idx);
            c = new Column("Sample", "#", a, Dataset.ColumnType.PROCESSED_VARIANT);
        } else if(id.equals("TIME")) {
            // Smooth TIME data to reduce jitter in sample rate calculations
            // Use segment-aware smoothing to avoid artifacts from time discontinuities
            c = createSegmentAwareSmoothedTimeColumn();
        } else if(id.equals("TIME - raw")) {
            c = getOrCreateRawColumn("TIME", UnitConstants.UNIT_SECONDS,
                                    (data) -> data.div(this.time_ticks_per_sec));
        } else if(id.equals("RPM")) {
            // smooth sampling quantum noise/jitter, RPM is an integer!
            // Always applies MA (if enough samples), then optionally SG
            c = createSmoothedColumn("RPM", UnitConstants.UNIT_RPM, null, 0.0);
        } else if(id.equals("RPM - raw")) {
            c = getOrCreateRawColumn("RPM", UnitConstants.UNIT_RPM, null);
        } else if(id.equals("RPM - base")) {
            // Debug column: return base RPM used for range detection
            c = this.baseRpm;

        // ========== CALCULATED MAF & FUEL FIELDS ==========
        } else if(id.equals("Sim Load")) {
            // g/sec to kg/hr
            final DoubleArray a = super.get("MassAirFlow").data.mult(UnitConstants.GPS_PER_KGH);
            final DoubleArray b = super.get("RPM").data.smooth();

            // KUMSRL
            c = new Column(id, UnitConstants.UNIT_PERCENT, a.div(b).div(.001072));
        } else if(id.equals("Sim Load Corrected")) {
            // g/sec to kg/hr
            final DoubleArray a = this.get("Sim MAF").data.mult(UnitConstants.GPS_PER_KGH);
            final DoubleArray b = this.get("RPM").data;

            // KUMSRL
            c = new Column(id, UnitConstants.UNIT_PERCENT, a.div(b).div(.001072));
        } else if(id.equals("Sim MAF")) {
            // mass in g/sec
            final DoubleArray a = super.get("MassAirFlow").data.
                mult(this.env.f.MAF_correction()).add(this.env.f.MAF_offset());
            c = new Column(id, UnitConstants.UNIT_GPS, a, Dataset.ColumnType.OTHER_RUNTIME);
        } else if(id.equals("MassAirFlow df/dt")) {
            // mass in g/sec
            final DoubleArray maf = super.get("MassAirFlow").data;
            final DoubleArray time = this.get("TIME").data;
            c = new Column(id, "g/sec^s", maf.derivative(time).max(0));
        } else if(id.equals("Turbo Flow")) {
            final DoubleArray a = this.get("Sim MAF").data;
            c = new Column(id, "m^3/sec", a.div(1225*this.env.f.turbos()), Dataset.ColumnType.OTHER_RUNTIME);
        } else if(id.equals("Turbo Flow (lb/min)")) {
            final DoubleArray a = this.get("Sim MAF").data;
            c = new Column(id, "lb/min", a.div(7.55*this.env.f.turbos()), Dataset.ColumnType.OTHER_RUNTIME);
        } else if(id.equals("Sim Fuel Mass")) { // based on te
            final double gps = this.env.f.injector()*UnitConstants.GPS_PER_CCMIN;
            final double cylinders = this.env.f.cylinders();
            final Column bank1 = this.get("EffInjectorDutyCycle");
            final Column bank2 = this.get("EffInjectorDutyCycleBank2");
            DoubleArray duty = bank1.data;
            /* average two duties for overall mass */
            if (bank2!=null) duty = duty.add(bank2.data).div(2);
            final DoubleArray a = duty.mult(cylinders*gps/100);
            c = new Column(id, "g/sec", a, Dataset.ColumnType.OTHER_RUNTIME);

        // ========== CALCULATED AIR-FUEL RATIO FIELDS ==========
        // Note: AFR conversions (lambda to AFR) are now handled by generic unit conversion handler
        } else if(id.equals("Sim AFR")) {
            final DoubleArray a = this.get("Sim MAF").data;
            final DoubleArray b = this.get("Sim Fuel Mass").data;
            c = new Column(id, UnitConstants.UNIT_AFR, a.div(b));
        } else if(id.equals("Sim lambda")) {
            final DoubleArray a = this.get("Sim AFR").data.div(UnitConstants.STOICHIOMETRIC_AFR);
            c = new Column(id, UnitConstants.UNIT_LAMBDA, a);
        } else if(id.equals("Sim lambda error")) {
            final DoubleArray a = super.get("AirFuelRatioDesired").data;
            final DoubleArray b = this.get("Sim lambda").data;
            c = new Column(id, UnitConstants.UNIT_PERCENT, a.div(b).mult(-1).add(1).mult(100).
                max(-25).min(25));

        } else if(id.equals("FuelInjectorDutyCycle")) {
            final DoubleArray a = super.get("FuelInjectorOnTime").data. /* ti */
                div(60*1000);   /* assumes injector on time is in ms */

            final DoubleArray b = this.get("RPM").data.div(2); // 1/2 cycle
            c = new Column(id, UnitConstants.UNIT_PERCENT, a.mult(b).mult(100)); // convert to %
        } else if(id.equals("EffInjectorDutyCycle")) {          /* te */
            final DoubleArray a = super.get("EffInjectionTime").data.
                div(60*1000);   /* assumes injector on time is in ms */

            final DoubleArray b = this.get("RPM").data.div(2); // 1/2 cycle
            c = new Column(id, UnitConstants.UNIT_PERCENT, a.mult(b).mult(100)); // convert to %
        } else if(id.equals("EffInjectorDutyCycleBank2")) {             /* te */
            final DoubleArray a = super.get("EffInjectionTimeBank2").data.
                div(60*1000);   /* assumes injector on time is in ms */

            final DoubleArray b = this.get("RPM").data.div(2); // 1/2 cycle
            c = new Column(id, UnitConstants.UNIT_PERCENT, a.mult(b).mult(100)); // convert to %

        // ========== SPECIAL HANDLERS: ENGINE TORQUE/HP ==========
        // if log contains Engine torque / converts TorqueDesired (Nm) to ft-lb and calculates HP
        // See MenuHandlerRegistry.REGISTRY["Engine torque (ft-lb)"], etc.
        } else if(id.equals("Engine torque (ft-lb)")) {
            final DoubleArray tq = this.get("TorqueDesired").data;
            final DoubleArray value = tq.mult(UnitConstants.NM_PER_FTLB);       // nm to ft-lb
            c = new Column(id, UnitConstants.UNIT_FTLB, value);
        } else if(id.equals("Engine HP")) {
            final DoubleArray tq = this.get("Engine torque (ft-lb)").data;
            final DoubleArray rpm = this.get("RPM").data;
            final DoubleArray value = tq.div(UnitConstants.HP_CALCULATION_FACTOR).mult(rpm);
            c = new Column(id, UnitConstants.UNIT_HP, value);

        // ========== CALCULATED FIELDS: VELOCITY & ACCELERATION ==========
        // Calc Velocity, Acceleration (RPM/s), Acceleration (m/s^2), Acceleration (g)
        // See MenuHandlerRegistry.REGISTRY["Calc Velocity"], etc.
        //
        // SMOOTHING STRATEGY:
        // - All acceleration calculations use smoothed RPM input (from get("RPM").data)
        //   to reduce quantization noise before differentiation
        //   The final RPM column already has adaptive smoothing (MAW+SG for quantized, SG for smooth)
        // - Acceleration (RPM/s) and Acceleration (m/s^2) use AccelMAW() smoothing window
        //   applied via range-aware smoothing in getData() for consistent smoothing
        // - "Raw" variants (Acceleration (RPM/s) - raw, Acceleration (m/s^2) - raw) use
        //   smoothed RPM input but no smoothing on the derivative (tooltip explains this)
        // - Calc Velocity inherits smoothing quality from smoothed RPM (no additional smoothing)
        //
        // SMOOTHING WINDOWS:
        // - Acceleration derivatives: AccelMAW() (typically 5-10 samples)
        //   Applied via range-aware smoothing in getData() only (NOT during derivative calculation)
        //   IMPORTANT: Do NOT apply smoothing during derivative calculation (derivative(x, AccelMAW()))
        //   AND also register for range-aware smoothing. This creates double-smoothing which is
        //   excessive and causes edge artifacts. The three-stage RPM design already provides
        //   appropriate smoothing at the input level (final RPM with adaptive smoothing).
        // - "Raw" variants: none (derivative without smoothing window, no range-aware smoothing)
        } else if(id.equals("Calc Velocity")) {
            // Calculate vehicle speed from RPM and gear ratio (more accurate than VehicleSpeed sensor)
            // Uses user-specified rpm_per_mph for calibration
            final DoubleArray rpm = this.get("RPM").data;
            c = new Column(id, UnitConstants.UNIT_MPS, rpm.div(this.env.c.rpm_per_mph()).
                mult(UnitConstants.MPS_PER_MPH), Dataset.ColumnType.VEHICLE_CONSTANTS);
        } else if(id.equals("Acceleration (RPM/s)")) {
            // Smoothed RPM acceleration - uses smoothed RPM with AccelMAW() smoothing applied via range-aware smoothing
            // Smoothing window: AccelMAW() (typically 5-10 samples) - applied in getData() via range-aware smoothing
            // NOTE: We do NOT apply smoothing during derivative calculation here to avoid double-smoothing and edge loss.
            // Instead, smoothing is applied only via range-aware smoothing in getData(), which handles edges correctly with padding.
            final DoubleArray y = this.get("RPM").data;
            final DoubleArray x = this.get("TIME").data;
            final DoubleArray derivative = y.derivative(x, 0).max(0);  // No smoothing during derivative - will be smoothed in getData()
            c = new Column(id, UnitConstants.UNIT_RPS, derivative, Dataset.ColumnType.PROCESSED_VARIANT);
            // Register for range-aware smoothing to prevent edge artifacts when viewing truncated ranges
            this.smoothingWindows.put(id.toString(), this.AccelMAW());
        } else if(id.equals("Acceleration (RPM/s) - raw")) {
            // Use smoothed RPM (not raw) to reduce quantization noise before differentiation
            // "Raw" refers to unsmoothed derivative, not unsmoothed input
            // Smoothing window: none (derivative without smoothing window)
            final DoubleArray y = this.get("RPM").data;
            final DoubleArray x = this.get("TIME").data;
            final DoubleArray derivative = y.derivative(x, 0).max(0);
            c = new Column(id, UnitConstants.UNIT_RPS, derivative, Dataset.ColumnType.PROCESSED_VARIANT);
        } else if(id.equals("Acceleration (RPM/s) - from base RPM")) {
            // Debug column: acceleration from base RPM input (uses AccelMAW smoothing on input)
            // Uses base RPM (SG smoothing only) instead of final RPM (adaptive smoothing)
            // Applies AccelMAW smoothing to base RPM input before derivative calculation
            // This allows comparison of base RPM vs final RPM effects on acceleration
            final DoubleArray y = (this.baseRpm != null) ? this.baseRpm.data : null;
            if (y == null) {
                logger.warn("_get('{}'): baseRpm is null, cannot calculate acceleration", id);
                return null;
            }
            final DoubleArray x = this.get("TIME").data;
            // Apply AccelMAW smoothing to base RPM, then calculate derivative
            final int accelMAW = AccelMAW();
            DoubleArray derivative;
            if (accelMAW > 0 && this.samples_per_sec > 0) {
                final double[] baseRpmArray = y.toArray();
                final Smoothing smoother = new Smoothing(accelMAW);
                final double[] smoothedRpm = smoother.smoothAll(baseRpmArray, 0, baseRpmArray.length - 1);
                derivative = new DoubleArray(smoothedRpm).derivative(x, 0).max(0);
            } else {
                derivative = y.derivative(x, 0).max(0);
            }
            c = new Column(id, UnitConstants.UNIT_RPS, derivative, Dataset.ColumnType.PROCESSED_VARIANT);
        } else if(id.equals("Acceleration (m/s^2) - raw")) {
            // Raw (unsmoothed) acceleration in m/s^2 - calculated directly from RPM (same approach as RPM/s)
            // "Raw" refers to unsmoothed derivative, not unsmoothed input
            // Smoothing window: none (derivative without smoothing window)
            final DoubleArray y = this.get("RPM").data;
            final DoubleArray x = this.get("TIME").data;
            final DoubleArray derivative = y.derivative(x, 0).max(0);
            // Convert RPM/s to m/s^2: derivative (RPM/s) / rpm_per_mph * MPS_PER_MPH
            final DoubleArray accel = derivative.div(this.env.c.rpm_per_mph()).
                mult(UnitConstants.MPS_PER_MPH);
            c = new Column(id, "m/s^2", accel, Dataset.ColumnType.VEHICLE_CONSTANTS);
        } else if(id.equals("Acceleration (m/s^2)")) {
            // Smoothed acceleration in m/s^2 - calculated directly from RPM (same approach as Acceleration (RPM/s))
            // Uses smoothed RPM directly, derivative with AccelMAW() smoothing applied via range-aware smoothing
            // Smoothing window: AccelMAW() (typically 5-10 samples) - applied in getData() via range-aware smoothing
            // NOTE: We do NOT apply smoothing during derivative calculation here to avoid double-smoothing and edge loss.
            // Instead, smoothing is applied only via range-aware smoothing in getData(), which handles edges correctly with padding.
            // This ensures consistent smoothing between RPM/s and m/s^2 acceleration
            final DoubleArray y = this.get("RPM").data;
            // Log values from middle of dataset to verify we're using smoothed RPM (not CSV)
            // Also compare with CSV RPM at same indices to verify smoothing is applied
            if (y != null && y.size() > 20 && this.csvRpm != null && this.csvRpm.data.size() == y.size()) {
                final int middleStart = y.size() / 2 - 5;
                final int middleEnd = middleStart + 10;
                final StringBuilder sb = new StringBuilder();
                sb.append("RPM data used for acceleration (indices ").append(middleStart).append("-").append(middleEnd - 1).append("): ");
                for (int j = middleStart; j < middleEnd && j < y.size(); j++) {
                    if (j > middleStart) sb.append(", ");
                    sb.append(String.format("%.1f", y.get(j)));
                }
                logger.debug("_get('{}'): {}", id, sb.toString());
                // Compare with CSV RPM at same indices
                final StringBuilder sbCsv = new StringBuilder();
                sbCsv.append("CSV RPM at same indices (for comparison): ");
                for (int j = middleStart; j < middleEnd && j < this.csvRpm.data.size(); j++) {
                    if (j > middleStart) sbCsv.append(", ");
                    sbCsv.append(String.format("%.1f", this.csvRpm.data.get(j)));
                }
                logger.debug("_get('{}'): {}", id, sbCsv.toString());
            }
            final DoubleArray x = this.get("TIME").data;
            final DoubleArray derivative = y.derivative(x, 0).max(0);  // No smoothing during derivative - will be smoothed in getData()
            // Convert RPM/s to m/s^2: derivative (RPM/s) / rpm_per_mph * MPS_PER_MPH
            final DoubleArray accel = derivative.div(this.env.c.rpm_per_mph()).
                mult(UnitConstants.MPS_PER_MPH);
            c = new Column(id, "m/s^2", accel, Dataset.ColumnType.VEHICLE_CONSTANTS);
            // Register for range-aware smoothing to prevent edge artifacts when viewing truncated ranges
            this.smoothingWindows.put(id.toString(), this.AccelMAW());
        } else if(id.equals("Acceleration (g)")) {
            // Depends on Acceleration (m/s^2) which uses RPM_PER_MPH
            final DoubleArray a = this.get("Acceleration (m/s^2)").data;
            c = new Column(id, UnitConstants.UNIT_G, a.div(UnitConstants.STANDARD_GRAVITY), Dataset.ColumnType.VEHICLE_CONSTANTS);
        } else if(id.equals("TIME [Range]")) {
            // Relative time to start of range (if filter enabled), otherwise just TIME
            if (!this.filter.enabled()) {
                // Filter disabled: return base TIME data
                final DoubleArray time = this.get("TIME").data;
                c = new Column(id, UnitConstants.UNIT_SECONDS, time, Dataset.ColumnType.PROCESSED_VARIANT);
            } else {
                // Filter enabled: calculate relative time to range start
                final DoubleArray time = this.get("TIME").data;
                final ArrayList<Dataset.Range> ranges = this.getRanges();
                final double[] result = new double[this.length()];
                for (int i = 0; i < this.length(); i++) {
                    final Dataset.Range range = findRangeForIndex(ranges, i);
                    if (range != null) {
                        result[i] = time.get(i) - time.get(range.start);
                    } else {
                        // Not in any range: use absolute time
                        result[i] = time.get(i);
                    }
                }
                c = new Column(id, UnitConstants.UNIT_SECONDS, new DoubleArray(result), Dataset.ColumnType.PROCESSED_VARIANT);
            }
        } else if(id.equals("Sample [Range]")) {
            // Relative sample to start of range (if filter enabled), otherwise just Sample
            if (!this.filter.enabled()) {
                // Filter disabled: return base Sample data
                final double[] idx = new double[this.length()];
                for (int i = 0; i < this.length(); i++) {
                    idx[i] = i;
                }
                c = new Column(id, UnitConstants.UNIT_SAMPLE, new DoubleArray(idx), Dataset.ColumnType.PROCESSED_VARIANT);
            } else {
                // Filter enabled: calculate relative sample to range start
                final ArrayList<Dataset.Range> ranges = this.getRanges();
                final double[] result = new double[this.length()];
                for (int i = 0; i < this.length(); i++) {
                    final Dataset.Range range = findRangeForIndex(ranges, i);
                    if (range != null) {
                        result[i] = i - range.start;
                    } else {
                        // Not in any range: use absolute sample index
                        result[i] = i;
                    }
                }
                c = new Column(id, UnitConstants.UNIT_SAMPLE, new DoubleArray(result), Dataset.ColumnType.PROCESSED_VARIANT);
            }

        // ========== CALCULATED FIELDS: POWER & TORQUE ==========
        // WHP, WTQ, HP, TQ, Drag
        // See MenuHandlerRegistry.REGISTRY["WHP"], etc.
        //
        // HP/WHP CALCULATION CHAIN:
        // 1. RPM (smoothed) → Acceleration (m/s^2) [uses AccelMAW() smoothing window]
        // 2. RPM (smoothed) → Calc Velocity [inherits smoothing, no additional smoothing]
        // 3. Acceleration (m/s^2) + Calc Velocity → WHP [smoothed in getData() with HPMAW() window]
        // 4. WHP → HP [calculated from smoothed WHP during _get(), no additional smoothing]
        //    NOTE: HP is calculated from smoothed WHP (not unsmoothed) to avoid redundant smoothing.
        //    Since HP = WHP / (1-driveline_loss) + static_loss is a linear transformation,
        //    smoothing HP directly would be equivalent to smoothing WHP then calculating HP.
        //    We calculate HP from smoothed WHP during column creation to inherit smoothing.
        // 5. HP/WHP → TQ/WTQ [calculated from already-smoothed HP/WHP]
        //
        // SMOOTHING WINDOWS:
        // - Acceleration derivatives: AccelMAW() (typically 5-10 samples)
        //   Applied via range-aware smoothing in getData() only (NOT during derivative calculation)
        //   IMPORTANT: Do NOT apply smoothing during derivative calculation (derivative(x, AccelMAW()))
        //   AND also in getData(). This creates double-smoothing which is excessive and causes edge artifacts.
        //   The three-stage RPM design (CSV → Base → Final) already provides appropriate smoothing:
        //   - Final RPM uses adaptive smoothing (MAW+SG for quantized, SG for smooth)
        //   - Range-aware smoothing in getData() handles edge artifacts with proper padding
        //   - Single smoothing point per architectural layer avoids redundancy
        // - WHP: HPMAW() (typically 10-20 samples) - applied in getData()
        // - HP: No additional smoothing (calculated from smoothed WHP during _get())
        //   NOTE: HP smoothing is applied to WHP data before calculating HP, so HP inherits
        //   smoothing from WHP without redundant smoothing application.
        // - TQ/WTQ: No additional smoothing (calculated from smoothed HP/WHP)
        //
        // RANGE-AWARE SMOOTHING:
        // Range-aware smoothing (via getData()) prevents edge artifacts when data windows
        // are truncated by ranges. It uses padding and re-smooths only the requested range.
        // This is applied to: Acceleration (RPM/s), Acceleration (m/s^2), WHP
        //
        // DESIGN PRINCIPLE: Single Smoothing Point Per Architectural Layer
        // The three-stage RPM architecture (CSV → Base → Final) provides a clear separation:
        // - CSV RPM: Raw data (no smoothing)
        // - Base RPM: SG only (for range detection, no quantization detection needed)
        // - Final RPM: Adaptive smoothing (MAW+SG for quantized, SG for smooth) - for calculations
        // - Display columns: Range-aware smoothing (for edge handling when viewing truncated ranges)
        // - Power calculations: HPMAW() smoothing (user preference on final power values)
        // Each layer adds ONE smoothing step. Double-smoothing (e.g., smoothing in derivative AND
        // range-aware smoothing) violates this principle and causes excessive smoothing and edge artifacts.
        } else if(id.equals("WHP")) {
            // Uses: mass, Cd, FA, rolling_drag (via drag()), rpm_per_mph (via Calc Velocity)
            // Depends on: Acceleration (m/s^2) [smoothed with AccelMAW()], Calc Velocity [from smoothed RPM]
            // Smoothing: Applied in getData() using HPMAW() window
            Column accelCol = this.get("Acceleration (m/s^2)");
            Column velocityCol = this.get("Calc Velocity");
            if (accelCol == null || velocityCol == null) {
                logger.warn("_get('WHP'): Missing dependencies - Acceleration (m/s^2)={}, Calc Velocity={}",
                    accelCol != null, velocityCol != null);
                return null;
            }
            final DoubleArray a = accelCol.data;
            final DoubleArray v = velocityCol.data;
            final DoubleArray whp = a.mult(v).mult(this.env.c.mass()).
                add(this.drag(v));      // in watts

            DoubleArray value = whp.mult(1.0 / UnitConstants.HP_PER_WATT);
            String l = UnitConstants.UNIT_HP;
            if(this.env.sae.enabled()) {
                value = value.mult(this.env.sae.correction());
                l += " (SAE)";
            }
            // Store unsmoothed data and record smoothing requirement
            // Smoothing will be applied in getData() using MAW() window
            c = new Column(id, l, value, Dataset.ColumnType.VEHICLE_CONSTANTS);
            this.smoothingWindows.put(id.toString(), this.HPMAW());
        } else if(id.equals("HP")) {
            // Uses: driveline_loss, static_loss, plus all WHP dependencies
            // Depends on: WHP [smoothed with HPMAW() in getData()]
            //
            // IMPORTANT: HP is calculated from smoothed WHP (not unsmoothed) to avoid redundant smoothing.
            // Since HP = WHP / (1-driveline_loss) + static_loss is a linear transformation,
            // smoothing HP directly would be mathematically equivalent to smoothing WHP then calculating HP.
            // By calculating HP from smoothed WHP during column creation, HP inherits smoothing from WHP
            // without applying smoothing twice. This is more efficient and avoids redundant computation.
            //
            // Change history: Previously HP was calculated from unsmoothed WHP and then smoothed independently
            // in getData(). This was redundant since smoothing a linear transformation is equivalent to
            // transforming smoothed data. The change ensures HP inherits smoothing from WHP.
            Column whpCol = this.get("WHP");
            if (whpCol == null) {
                logger.warn("_get('HP'): Missing dependency - WHP");
                return null;
            }
            // Apply smoothing to WHP data (same as getData() would do), then calculate HP
            // This ensures HP inherits smoothing from WHP without redundant smoothing
            final int whpSmoothingWindow = this.smoothingWindows.getOrDefault("WHP", 0);
            DoubleArray smoothedWHP;
            if (whpSmoothingWindow > 0) {
                final double[] whpData = whpCol.data.toArray();
                final org.nyet.util.Smoothing s = new org.nyet.util.Smoothing(whpSmoothingWindow);
                smoothedWHP = new DoubleArray(s.smoothAll(whpData, 0, whpData.length - 1));
            } else {
                smoothedWHP = whpCol.data;
            }
            // Calculate HP from smoothed WHP (no additional smoothing needed)
            final DoubleArray value = smoothedWHP.div((1-this.env.c.driveline_loss())).
                    add(this.env.c.static_loss());
            String l = UnitConstants.UNIT_HP;
            if(this.env.sae.enabled()) l += " (SAE)";
            // HP is already calculated from smoothed WHP, so no smoothing registration needed
            // getData() will return HP data directly without additional smoothing
            c = new Column(id, l, value, Dataset.ColumnType.VEHICLE_CONSTANTS);
        } else if(id.equals("WTQ")) {
            // Depends on WHP (which uses all WHP constants)
            // Calculate WTQ from smoothed WHP (WHP is smoothed in getData() with MAW() window)
            // No additional smoothing needed - inherits smoothing from WHP
            c = this.calculateTorque("WTQ", "WHP");
        } else if(id.toString().equals(idWithUnit("WTQ", UnitConstants.UNIT_NM))) {
            // Depends on WTQ (which uses WHP constants)
            // WTQ inherits smoothing from WHP
            final DoubleArray wtq = this.get("WTQ").data;
            final DoubleArray value = wtq.mult(UnitConstants.FTLB_PER_NM); // ft-lb to Nm
            String l = UnitConstants.UNIT_NM;
            if(this.env.sae.enabled()) l += " (SAE)";
            c = new Column(id, l, value, Dataset.ColumnType.VEHICLE_CONSTANTS);
        } else if(id.equals("TQ")) {
            // Depends on HP (which uses all HP/WHP constants)
            // Calculate TQ from smoothed HP (HP is smoothed in getData() with MAW() window)
            // No additional smoothing needed - inherits smoothing from HP
            c = this.calculateTorque("TQ", "HP");
        } else if(id.toString().equals(idWithUnit("TQ", UnitConstants.UNIT_NM))) {
            // Depends on TQ (which uses all HP/WHP constants)
            final DoubleArray tq = this.get("TQ").data;
            final DoubleArray value = tq.mult(UnitConstants.FTLB_PER_NM); // ft-lb to Nm
            String l = UnitConstants.UNIT_NM;
            if(this.env.sae.enabled()) l += " (SAE)";
            c = new Column(id, l, value, Dataset.ColumnType.VEHICLE_CONSTANTS);
        } else if(id.equals("Drag")) {
            // Uses: Cd, FA, rolling_drag, mass (via drag()), rpm_per_mph (via Calc Velocity)
            final DoubleArray v = this.get("Calc Velocity").data;
            final DoubleArray drag = this.drag(v);
            c = new Column(id, "HP", drag.mult(1.0 / UnitConstants.HP_PER_WATT), Dataset.ColumnType.VEHICLE_CONSTANTS);

        // ========== BOOST PRESSURE & ZEITRONIX HANDLERS ==========
        // BoostPressureDesired, Zeitronix Boost, Zeitronix AFR, Zeitronix Lambda
        // See MenuHandlerRegistry.REGISTRY["Zeitronix Boost (PSI)"], etc.
        } else if(id.equals("BoostPressureDesired")) {
            final Column delta = super.get("BoostPressureDesiredDelta");
            if (delta != null) {
                final Column ecu = super.get("ECUBoostPressureDesired");
                if (ecu != null) {
                    c = new Column(id, UnitConstants.UNIT_PSI, ecu.data.add(delta.data));
                }
            }
        } else if(id.toString().equals(idWithUnit("Zeitronix Boost", UnitConstants.UNIT_PSI))) {
            final DoubleArray boost = super.get("Zeitronix Boost").data;
            // Store unsmoothed data and record smoothing requirement
            c = new Column(id, UnitConstants.UNIT_PSI, boost);
            // Convert seconds to samples
            int smoothingWindow = (int)Math.floor(this.samples_per_sec * this.filter.ZeitMAW());
            this.smoothingWindows.put(id.toString(), smoothingWindow);
        } else if(id.equals("Zeitronix Boost")) {
            // Get base column directly from map (no calculations) and convert units directly
            Column baseCol = super.get("Zeitronix Boost");
            if (baseCol == null) {
                logger.warn("_get('Zeitronix Boost'): Base column not found in map");
                return null;
            }
            // Convert to PSI using DatasetUnits (bypasses getColumnInUnits to avoid recursion)
            java.util.function.Supplier<Double> ambientSupplier = () -> {
                Column baro = super.get("BaroPressure");
                if (baro != null && baro.data != null && baro.data.size() > 0) {
                    return baro.data.get(0);
                }
                return null;
            };
            Dataset.ColumnType colType = baseCol.getColumnType();
            if (colType == Dataset.ColumnType.CSV_NATIVE) {
                colType = Dataset.ColumnType.COMPILE_TIME_CONSTANTS;
            }
            Column psiCol = DatasetUnits.convertUnits(this, baseCol, UnitConstants.UNIT_PSI, ambientSupplier, colType);
            final DoubleArray boost = psiCol.data;
            c = new Column(id, UnitConstants.UNIT_MBAR, boost.mult(UnitConstants.MBAR_PER_PSI).add(UnitConstants.MBAR_PER_ATM));
        } else if(id.toString().equals(idWithUnit("Zeitronix AFR", UnitConstants.UNIT_LAMBDA))) {
            final DoubleArray abs = super.get("Zeitronix AFR").data;
            c = new Column(id, UnitConstants.UNIT_LAMBDA, abs.div(UnitConstants.STOICHIOMETRIC_AFR));
        } else if(id.toString().equals(idWithUnit("Zeitronix Lambda", UnitConstants.UNIT_AFR))) {
            final DoubleArray abs = super.get("Zeitronix Lambda").data;
            c = new Column(id, UnitConstants.UNIT_AFR, abs.mult(UnitConstants.STOICHIOMETRIC_AFR));
        } else if(id.equals("BoostDesired PR")) {
            final Column act = super.get("BoostPressureDesired");
            try {
                final DoubleArray ambient = super.get("BaroPressure").data;
                c = new Column(id, "PR", act.data.div(ambient));
            } catch (final Exception e) {
                if (act.getUnits().matches(UnitConstants.UNIT_PSI))
                    c = new Column(id, "PR", act.data.div(UnitConstants.STOICHIOMETRIC_AFR));
                else
                    c = new Column(id, "PR", act.data.div(UnitConstants.MBAR_PER_ATM));
            }

        } else if(id.equals("BoostActual PR")) {
            final Column act = super.get("BoostPressureActual");
            try {
                final DoubleArray ambient = super.get("BaroPressure").data;
                c = new Column(id, "PR", act.data.div(ambient));
            } catch (final Exception e) {
                if (act.getUnits().matches(UnitConstants.UNIT_PSI))
                    c = new Column(id, "PR", act.data.div(UnitConstants.STOICHIOMETRIC_AFR));
                else
                    c = new Column(id, "PR", act.data.div(UnitConstants.MBAR_PER_ATM));
            }
        } else if(id.equals("Sim evtmod")) {
            // Get base column directly from map and convert units directly (avoid recursion)
            Column baseCol = super.get("IntakeAirTemperature");
            if (baseCol == null) {
                logger.warn("_get('Sim evtmod'): IntakeAirTemperature not found in map");
                return null;
            }
            Column celsiusCol = DatasetUnits.convertUnits(this, baseCol, UnitConstants.UNIT_CELSIUS, null, baseCol.getColumnType());
            final DoubleArray tans = celsiusCol.data;
            DoubleArray tmot = tans.ident(95);
            try {
                tmot = this.get("CoolantTemperature").data;
            } catch (final Exception e) {}

            // KFFWTBR=0.02
            // evtmod = tans + (tmot-tans)*KFFWTBR
            final DoubleArray evtmod = tans.add((tmot.sub(tans)).mult(0.02));
            c = new Column(id, "\u00B0C", evtmod);
        } else if(id.equals("Sim ftbr")) {
            // Get base column directly from map and convert units directly (avoid recursion)
            Column baseCol = super.get("IntakeAirTemperature");
            if (baseCol == null) {
                logger.warn("_get('Sim ftbr'): IntakeAirTemperature not found in map");
                return null;
            }
            Column celsiusCol = DatasetUnits.convertUnits(this, baseCol, UnitConstants.UNIT_CELSIUS, null, baseCol.getColumnType());
            final DoubleArray tans = celsiusCol.data;
            final DoubleArray evtmod = this.get("Sim evtmod").data;
            // linear fit to stock FWFTBRTA
            // fwtf = (tans+637.425)/731.334

            final DoubleArray fwft = tans.add(673.425).div(731.334);

            // ftbr = 273/(tans+273) * fwft

            //    (tans+637.425)      273
            //    -------------- *  -------
            //      (tans+273)      731.334

            // ftbr=273/(evtmod-273) * fwft
            c = new Column(id, "", evtmod.ident(273).div(evtmod.add(273)).mult(fwft));
        } else if(id.equals("Sim BoostIATCorrection")) {
            final DoubleArray ftbr = this.get("Sim ftbr").data;
            c = new Column(id, "", ftbr.inverse());
        } else if(id.equals("Sim BoostPressureDesired")) {
            final boolean SY_BDE = false;
            final boolean SY_AGR = true;
            DoubleArray load;
            DoubleArray ps;

            try {
                load = super.get("EngineLoadRequested").data; // rlsol
            } catch (final Exception e) {
                load = super.get("EngineLoadCorrected").data; // rlmax
            }

            try {
                ps = super.get("ME7L ps_w").data;
            } catch (final Exception e) {
                ps = super.get("BoostPressureActual").data;
            }

            DoubleArray ambient = ps.ident(UnitConstants.MBAR_PER_ATM); // pu
            try {
                ambient = super.get("BaroPressure").data;
            } catch (final Exception e) { }

            DoubleArray fupsrl = load.ident(0.1037); // KFURL
            try {
                final DoubleArray ftbr = this.get("Sim ftbr").data;
                // fupsrl = KFURL * ftbr
                fupsrl = fupsrl.mult(ftbr);
            } catch (final Exception e) {}

            // pirg = fho * KFPRG = (pu/UnitConstants.MBAR_PER_ATM) * 70
            final DoubleArray pirg = ambient.mult(70/UnitConstants.MBAR_PER_ATM);

            if (!SY_BDE) {
                //load = load.sub(rlr);
                load = load.max(0);     // rlfgs
                if (SY_AGR) {
                    // pbr = ps * fpbrkds
                    // rfges = (pbr-pirg).max(0)*fupsrl
                    final DoubleArray rfges = (ps.mult(1.106)).sub(pirg).max(0).mult(fupsrl);
                    // psagr = 250??
                    // rfagr = rfges * psagr/ps
                    // load = rlfgs + rfagr;
                    load = load.add(rfges.mult(250).div(ps));
                }
                //load = load.add(rlr);
            }

            DoubleArray boost = load.div(fupsrl);

            if (SY_BDE) {
                boost = boost.add(pirg);
            }

            // fpbrkds from KFPBRK/KFPBRKNW
            boost = boost.div(1.016);   // pssol

            // vplsspls from KFVPDKSD/KFVPDKSDSE
            boost = boost.div(1.016);   // plsol

            c = new Column(id, UnitConstants.UNIT_MBAR, boost.max(ambient));
        } else if(id.equals("Boost Spool Rate (RPM)")) {
            final DoubleArray abs = super.get("BoostPressureActual").data.smooth();
            final DoubleArray rpm = this.get("RPM").data;
            c = new Column(id, "mBar/RPM", abs.derivative(rpm).max(0));
        } else if(id.equals("Boost Spool Rate Zeit (RPM)")) {
            final DoubleArray boost = this.get("Zeitronix Boost").data.smooth();
            final DoubleArray rpm =
                this.get("RPM").data; // Remove movingAverage - apply per-range
            c = new Column(id, "mBar/RPM", boost.derivative(rpm).max(0));
            // Note: RPM smoothing handled in getData(), but we need to mark RPM column
            // This is complex because RPM is used in derivative - needs special handling
        } else if(id.equals("Boost Spool Rate (time)")) {
            // Get base column directly from map and convert units directly (avoid recursion)
            Column baseCol = super.get("BoostPressureActual");
            if (baseCol == null) {
                logger.warn("_get('Boost Spool Rate (time)'): BoostPressureActual not found in map");
                return null;
            }
            java.util.function.Supplier<Double> ambientSupplier = () -> {
                Column baro = super.get("BaroPressure");
                if (baro != null && baro.data != null && baro.data.size() > 0) {
                    return baro.data.get(0);
                }
                return null;
            };
            Dataset.ColumnType colType = baseCol.getColumnType();
            if (colType == Dataset.ColumnType.CSV_NATIVE) {
                colType = Dataset.ColumnType.COMPILE_TIME_CONSTANTS;
            }
            Column psiCol = DatasetUnits.convertUnits(this, baseCol, UnitConstants.UNIT_PSI, ambientSupplier, colType);
            final DoubleArray abs = psiCol.data.smooth();
            final DoubleArray time = this.get("TIME").data;
            final DoubleArray derivative = abs.derivative(time, this.HPMAW()).max(0);
            // Store unsmoothed data and record smoothing requirement
            c = new Column(id, "PSI/sec", derivative);
            this.smoothingWindows.put(id.toString(), this.HPMAW());
        } else if(id.equals("ps_w error")) {
            final DoubleArray abs = super.get("BoostPressureActual").data.max(900);
            final DoubleArray ps_w = super.get("ME7L ps_w").data.max(900);
            //c = new Column(id, "%", abs.div(ps_w).sub(1).mult(-100));
            c = new Column(id, UnitConstants.UNIT_LAMBDA, ps_w.div(abs));
        } else if(id.equals("LDR error")) {
            final DoubleArray set = super.get("BoostPressureDesired").data;
            final DoubleArray out = super.get("BoostPressureActual").data;
            c = new Column(id, "100mBar", set.sub(out).div(100));
        } else if(id.equals("LDR de/dt")) {
            final DoubleArray set = super.get("BoostPressureDesired").data;
            final DoubleArray out = super.get("BoostPressureActual").data;
            final DoubleArray t = this.get("TIME").data;
            final DoubleArray o = set.sub(out).derivative(t,this.HPMAW());
            c = new Column(id,"100mBar",o.mult(this.env.pid.time_constant).div(100));
        } else if(id.equals("LDR I e dt")) {
            final DoubleArray set = super.get("BoostPressureDesired").data;
            final DoubleArray out = super.get("BoostPressureActual").data;
            final DoubleArray t = this.get("TIME").data;
            final DoubleArray o = set.sub(out).
                integral(t,0,this.env.pid.I_limit/this.env.pid.I*100);
            c = new Column(id,"100mBar",o.div(this.env.pid.time_constant).div(100));
        } else if(id.equals("LDR PID")) {
            final DoubleArray.TransferFunction fP =
                new DoubleArray.TransferFunction() {
                    @Override
                    public final double f(double x, double y) {
                        if(Math.abs(x)<ECUxDataset.this.env.pid.P_deadband/100) return 0;
                        return x*ECUxDataset.this.env.pid.P;
                    }
            };
            final DoubleArray.TransferFunction fD =
                new DoubleArray.TransferFunction() {
                    @Override
                    public final double f(double x, double y) {
                        y=Math.abs(y);
                        if(y<3) return x*ECUxDataset.this.env.pid.D[0];
                        if(y<5) return x*ECUxDataset.this.env.pid.D[1];
                        if(y<7) return x*ECUxDataset.this.env.pid.D[2];
                        return x*ECUxDataset.this.env.pid.D[3];
                    }
            };
            final DoubleArray E = this.get("LDR error").data;
            final DoubleArray P = E.func(fP);
            final DoubleArray I = this.get("LDR I e dt").data.mult(this.env.pid.I);
            final DoubleArray D = this.get("LDR de/dt").data.func(fD,E);
            c = new Column(id, "%", P.add(I).add(D).max(0).min(95));
        } else if(id.equals("Sim pspvds")) {
            final DoubleArray ps_w = super.get("ME7L ps_w").data;
            final DoubleArray pvdkds = super.get("BoostPressureActual").data;
            c = new Column(id,"",ps_w.div(pvdkds));
/*****************************************************************************/
        } else if(id.equals("IgnitionTimingAngleOverall")) {
            // Calculate from per-cylinder timing angles if Overall not directly available
            // This supports loggers like JB4 that only log per-cylinder timing, not overall timing
            // Note: _get() already handles recursion protection - if calculated version exists, it returns early
            final Column overall = super.get("IgnitionTimingAngleOverall");
            if(overall != null && overall.getColumnType() == Dataset.ColumnType.CSV_NATIVE) {
                // Exists as CSV column, use it directly
                c = overall;
            } else {
                // Calculate average of available per-cylinder timing angles
                DoubleArray avetiming = null;
                int count = 0;
                for(int i=1; i<=8; i++) {
                    final Column timing = this.get("IgnitionTimingAngle" + i);
                    if(timing != null) {
                        if(avetiming == null) avetiming = timing.data;
                        else avetiming = avetiming.add(timing.data);
                        count++;
                    }
                }
                if(count > 0) {
                    c = new Column(id, "\u00B0", avetiming.div(count));
                }
            }
        } else if(id.equals("IgnitionTimingAngleOverallDesired")) {
            DoubleArray averetard = null;
            int count=0;
            for(int i=0;i<8;i++) {
                final Column retard = this.get("IgnitionRetardCyl" + i);
                if(retard!=null) {
                    if(averetard==null) averetard = retard.data;
                    else averetard = averetard.add(retard.data);
                    count++;
                }
            }
            // Fallback to AverageIgnitionRetard if no per-cylinder retard fields found
            if(count == 0) {
                final Column avgRetard = this.get("AverageIgnitionRetard");
                if(avgRetard != null) {
                    averetard = avgRetard.data;
                    count = 1; // Use count=1 to indicate we have average retard
                }
            }
            DoubleArray out = this.get("IgnitionTimingAngleOverall").data;
            if(count>0) {
                // assume retard is always positive... some loggers log it negative
                // abs it to normalize
                out = out.add(averetard.div(count).abs());
            }
            c = new Column(id, "\u00B0", out);
/*****************************************************************************/
        } else if(id.equals("Sim LoadSpecified correction")) {
            final DoubleArray cs = super.get("EngineLoadCorrected").data;
            final DoubleArray s = super.get("EngineLoadSpecified").data;
            c = new Column(id, "K", cs.div(s));
/*****************************************************************************/
            }

            if(c!=null) {
                // LinkedHashMap automatically handles duplicates - put() replaces existing column with same ID
                // This ensures CSV_NATIVE columns are replaced by calculated versions (TIME/RPM, BoostPressureActual/Desired, etc.)
                this.putColumn(c);
                return c;
            }
            return super.get(id);
    }

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
    private Dataset.Range findRangeForIndex(ArrayList<Dataset.Range> ranges, int index) {
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
     */
    private double[] applySmoothingIfNeeded(Column column, String columnName, Range r) {
        Integer smoothingWindow = this.smoothingWindows.get(columnName);

        // Use stored window size - it was set when the column was created
        // For HP/TQ/WHP: uses HPMAW()
        // For Acceleration (RPM/s) and Acceleration (m/s^2): uses AccelMAW()
        // Range-aware smoothing prevents edge artifacts when data is truncated by ranges
        if (smoothingWindow != null && smoothingWindow > 0) {
            final int rangeSize = r.end - r.start + 1;

            // Clamp window to half the range size to prevent issues when window is close to dataset size
            final int originalWindow = smoothingWindow;
            final int effectiveWindow = org.nyet.util.Smoothing.clampWindowToHalfSize(smoothingWindow, rangeSize);
            if (effectiveWindow != originalWindow) {
                logger.debug("getData('{}'): Clamped smoothing window from {} to {} (half of range size {})",
                    columnName, originalWindow, effectiveWindow, rangeSize);
            }

            // Check if range is large enough for smoothing window
            if (rangeSize < effectiveWindow) {
                logger.warn("getData('{}'): Skipping smoothing - range size {} is smaller than window {}",
                    columnName, rangeSize, effectiveWindow);
                return column.data.toArray(r.start, r.end);
            }

            // Apply range-aware smoothing with right-side padding to prevent edge artifacts
            // Extend the range with right-side padding from full dataset to ensure smoothAt() has enough data
            // at the end. The smoothing window needs padding of approximately (window-1)/2 on the right side.
            final double[] fullData = column.data.toArray();
            // For symmetric smoothing with window W, we need (W-1)/2 samples on the right side
            // nk = (1-window)/2, so abs(nk) = (window-1)/2 is the padding needed
            final int paddingNeeded = (effectiveWindow - 1) / 2;

            // Extend range to include right-side padding from full dataset (but don't go outside array bounds)
            // No left padding - start at requested range start
            final int paddedStart = r.start;
            final int paddedEnd = Math.min(fullData.length - 1, r.end + paddingNeeded);

            // Smooth the extended range (includes right-side padding from full dataset)
            final org.nyet.util.Smoothing s = new org.nyet.util.Smoothing(effectiveWindow);
            final double[] smoothedWithPadding = s.smoothAll(fullData, paddedStart, paddedEnd);

            // Extract only the requested range from the smoothed result
            // Since paddedStart == r.start, no offset needed
            final int resultSize = r.end - r.start + 1;

            final double[] result = new double[resultSize];
            System.arraycopy(smoothedWithPadding, 0, result, 0, resultSize);

            return result;
        }

        // No smoothing needed, return raw data
        return column.data.toArray(r.start, r.end);
    }

    @Override
    public double[] getData(Comparable<?> id, Range r) {
        r = normalizeRange(r);
        if (r == null) return null;

        final Column c = this.get(id);
        if (c == null) return null;

        // Apply range-aware smoothing if needed
        final String columnName = id.toString();
        return applySmoothingIfNeeded(c, columnName, r);
    }

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
        return applySmoothingIfNeeded(c, lookupId, r);
    }

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

    public Filter getFilter() { return this.filter; }
    //public void setFilter(Filter f) { this.filter=f; }
    public Env getEnv() { return this.env; }
    //public void setEnv(Env e) { this.env=e; }
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

