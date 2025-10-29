package org.nyet.ecuxplot;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jfree.data.category.DefaultCategoryDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.nyet.util.Files;

public class FATSDataset extends DefaultCategoryDataset {
    private static final Logger logger = LoggerFactory.getLogger(FATSDataset.class);
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private int start = 4200;
    private int end = 6500;
    private final TreeMap<String, ECUxDataset> fileDatasets;
    private final FATS fats;

    // Store FATS data keyed by (filename, rangeIndex) for reliable lookup
    private final Map<String, Map<Integer, Double>> fatsDataMap = new HashMap<>();

    // Filter to query which ranges are selected - only controls FATS window DISPLAY, not calculation
    // FATS is calculated for ALL ranges regardless of selection
    private Filter filter;

    /**
     * Create FATS dataset with filter support for range selection
     * @param fileDatasets The loaded file datasets
     * @param fats FATS configuration settings
     * @param filter Filter containing selected ranges for per-file visibility
     */
    public FATSDataset(TreeMap<String, ECUxDataset> fileDatasets, FATS fats, Filter filter) {
        this.fileDatasets=fileDatasets;
        this.fats = fats;
        this.filter = filter;
        updateFromFATS();
        rebuild();
    }

    /**
     * Update internal RPM values from FATS configuration
     *
     * This method ensures that this.start and this.end always contain RPM values,
     * regardless of whether FATS is configured in RPM, MPH, or KPH mode. In MPH/KPH mode,
     * the configured speed values are converted to RPM using the appropriate conversion constant.
     */
    private void updateFromFATS() {
        FATS.SpeedUnitHandler handler = this.fats.speedUnit().getHandler();

        if (handler.requiresRpmConversionFields() && !this.fileDatasets.isEmpty()) {
            ECUxDataset firstDataset = this.fileDatasets.values().iterator().next();
            double rpmPerSpeed = handler.getRpmConversionFactor(firstDataset.getEnv().c);
            this.start = handler.speedToRpm(handler.getStartValue(this.fats), rpmPerSpeed);
            this.end = handler.speedToRpm(handler.getEndValue(this.fats), rpmPerSpeed);
        } else {
            // RPM mode: use direct values
            this.start = this.fats.start();
            this.end = this.fats.end();
        }
    }

    boolean hasVehicleSpeedData() {
        // Check if any dataset has VehicleSpeed data
        for (ECUxDataset dataset : this.fileDatasets.values()) {
            if (dataset.get("VehicleSpeed") != null) {
                return true;
            }
        }
        return false;
    }

    boolean needsRpmPerMphConversion() {
        // Check if any dataset lacks VehicleSpeed data (needs RPM conversion)
        for (ECUxDataset dataset : this.fileDatasets.values()) {
            if (dataset.get("VehicleSpeed") == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rebuild FATS dataset with current selections.
     * CRITICAL: FATS calculation is completely independent of range selection.
     * - Calculates FATS for ALL ranges (stored in fatsDataMap)
     * - Filter only controls DISPLAY in FATS window chart (not calculation)
     * - Range Selector tree queries fatsDataMap directly for all ranges
     *
     * Note: This method is called from the EDT. All Swing operations are on EDT,
     * so there's no true thread concurrency, but there could be order-of-operations
     * issues if rebuild() is called while another rebuild is in progress.
     * The calling code (ECUxPlot.rebuild()) handles this by cancelling previous rebuilds.
     */
    public void rebuild() {
        logger.debug(">>> FATSDataset.rebuild() [Thread: {}]", Thread.currentThread().getName());
        clear();

        // Calculate FATS for ALL ranges (independent of Filter selection)
        // This ensures fatsDataMap has all values for tree display and quick toggling
        for(final ECUxDataset data : this.fileDatasets.values()) {
            String filename = data.getFileId();
            Map<Integer, Double> fileData = fatsDataMap.computeIfAbsent(filename, k -> new HashMap<>());

            // Calculate FATS for any ranges that aren't in the map yet
            for(int i = 0; i < data.getRanges().size(); i++) {
                if (!fileData.containsKey(i)) {
                    double value = calculateFATS(data, i);
                    if (!Double.isNaN(value)) {
                        fileData.put(i, value);
                        logFATSSummary(data, i, value, this.start, this.end);
                    }
                }
            }
        }

        // Populate chart with selected ranges only
        int fileCount = 0;
        for(final ECUxDataset data : this.fileDatasets.values()) {
            fileCount++;
            setValue(data);
        }
        logger.debug("<<< FATSDataset.rebuild() complete - processed {} files, fatsDataMap entries: {}",
            fileCount, fatsDataMap.size());
    }

    /**
     * Calculate FATS for a specific range
     * @return The calculated value, or NaN if calculation fails
     */
    private double calculateFATS(ECUxDataset data, int series) {
        try {
            FATS.SpeedUnitHandler handler = this.fats.speedUnit().getHandler();
            if (handler.requiresRpmConversionFields()) {
                return data.calcFATSBySpeed(series, handler.getStartValue(this.fats), handler.getEndValue(this.fats), this.fats.speedUnit());
            } else {
                return data.calcFATS(series, this.start, this.end);
            }
        } catch (final Exception e) {
            logger.debug("FATS calculation failed for {} run {}: {}", Files.stem(data.getFileId()), series, e.getMessage());
            return Double.NaN;
        }
    }

    /**
     * Force a full recalculation of all FATS values
     * Used when FATS parameters (start/end/speed unit) change
     */
    public void rebuildAll() {
        fatsDataMap.clear();
        rebuild();
    }

    // set one (calls super)
    public void setValue(ECUxDataset data, int series, double value) {
        final String xkey = "Run " + (series+1);
        final String ykey = Files.stem(data.getFileId());
        // System.out.println("adding " + xkey + "," + ykey + "=" + value);

        super.setValue(value, xkey, ykey);

        // Also store in the map for reliable lookup
        String filename = data.getFileId();
        fatsDataMap.computeIfAbsent(filename, k -> new HashMap<>()).put(series, value);
    }

    /**
     * Get FATS data for a specific file and range
     * @param filename The full filename (before stemming)
     * @param rangeIndex The range index (0-based)
     * @return The FATS time in seconds, or -1 if not available
     */
    public double getFATSValue(String filename, int rangeIndex) {
        Map<Integer, Double> fileData = fatsDataMap.get(filename);
        if (fileData != null) {
            Double value = fileData.get(rangeIndex);
            if (value != null) {
                return value;
            }
        }
        return -1;
    }
    /**
     * Remove FATS data for a specific run in a dataset
     * @param data The dataset containing the FATS run
     * @param series The run number (0-based)
     */
    public void removeValue(ECUxDataset data, int series) {
        final String xkey = "Run " + (series+1);
        final String ykey = Files.stem(data.getFileId());
        // System.out.println("removing " + xkey + "," + ykey);
        super.removeValue(xkey, ykey);
    }

    /**
     * Log a concise FATS calculation summary
     * @param data The dataset being processed
     * @param series The run number (0-based)
     * @param value The calculated FATS time in seconds
     * @param rpmStart The RPM start value used in calculation
     * @param rpmEnd The RPM end value used in calculation
     */
    private void logFATSSummary(ECUxDataset data, int series, double value, int rpmStart, int rpmEnd) {
        String filename = Files.stem(data.getFileId());
        String runNumber = "run " + (series + 1);

        // Always show RPM range with speed conversion in parentheses
        FATS.SpeedUnitHandler handler = this.fats.speedUnit().getHandler();
        double startSpeed, endSpeed;
        String speedUnit;

        if (handler.requiresRpmConversionFields()) {
            // Speed mode: use the configured speed values
            startSpeed = handler.getStartValue(this.fats);
            endSpeed = handler.getEndValue(this.fats);
            speedUnit = handler.getAbbreviation();
        } else {
            // RPM mode: convert RPM to MPH using rpm_per_mph constant
            ECUxDataset firstDataset = this.fileDatasets.values().iterator().next();
            double rpmPerMph = firstDataset.getEnv().c.rpm_per_mph();
            startSpeed = rpmStart / rpmPerMph;
            endSpeed = rpmEnd / rpmPerMph;
            speedUnit = "mph";
        }

        String message = String.format("FATS: %s %s, %d (%.0f%s) - %d (%.0f%s) = %.1f seconds",
            filename, runNumber, rpmStart, startSpeed, speedUnit, rpmEnd, endSpeed, speedUnit, value);
        logger.info(message);
    }

    // helpers
    /**
     * Add FATS data to chart dataset for selected ranges only.
     * Note: FATS is calculated for ALL ranges in rebuild() and stored in fatsDataMap.
     * This method only controls what appears in the FATS window chart - Filter controls display, not calculation.
     * @param data The dataset containing FATS runs
     */
    public void setValue(ECUxDataset data) {
        try { removeColumn(Files.stem(data.getFileId()));
        } catch (final Exception e) {}

        // Only add FATS data for selected ranges (display in FATS window)
        // Calculation happens independently in rebuild() for ALL ranges
        if (filter != null) {
            String filename = data.getFileId();
            Set<Integer> selectedRanges = filter.getSelectedRanges(filename);

            for(Integer i : selectedRanges) {
                if (i >= 0 && i < data.getRanges().size()) {
                    setValue(data, i);
                }
            }
        } else {
            // No filter - add all ranges (backward compatibility)
            for(int i=0;i<data.getRanges().size();i++)
                setValue(data, i);
        }
    }
    /**
     * Set FATS data for a specific run in a dataset
     * @param data The dataset containing the FATS run
     * @param series The run number (0-based)
     */
    public void setValue(ECUxDataset data, int series) {
        double value = calculateFATS(data, series);
        if (!Double.isNaN(value)) {
            setValue(data, series, value);
            logFATSSummary(data, series, value, this.start, this.end);
        } else {
            removeValue(data, series);
        }
    }

    // helpers

    public void setStart(int start) {
        FATS.SpeedUnitHandler handler = this.fats.speedUnit().getHandler();

        if (handler.requiresRpmConversionFields() && !this.fileDatasets.isEmpty()) {
            ECUxDataset firstDataset = this.fileDatasets.values().iterator().next();
            double rpmPerSpeed = handler.getRpmConversionFactor(firstDataset.getEnv().c);
            handler.setStartValue(this.fats, handler.rpmToSpeed(start, rpmPerSpeed));
        } else {
            // RPM mode: set direct value
            this.fats.start(start);
        }
        updateFromFATS();
        // Start changed - recalculate all FATS values
        rebuildAll();
    }
    public void setEnd(int end) {
        FATS.SpeedUnitHandler handler = this.fats.speedUnit().getHandler();

        if (handler.requiresRpmConversionFields() && !this.fileDatasets.isEmpty()) {
            ECUxDataset firstDataset = this.fileDatasets.values().iterator().next();
            double rpmPerSpeed = handler.getRpmConversionFactor(firstDataset.getEnv().c);
            handler.setEndValue(this.fats, handler.rpmToSpeed(end, rpmPerSpeed));
        } else {
            // RPM mode: set direct value
            this.fats.end(end);
        }
        updateFromFATS();
        // End changed - recalculate all FATS values
        rebuildAll();
    }
    public int getStart() { return this.start; }
    public int getEnd() { return this.end; }

    public void refreshFromFATS() {
        updateFromFATS();
        // FATS parameters changed - need to recalculate all values
        rebuildAll();
    }

    public String getTitle() {
        FATS.SpeedUnitHandler handler = this.fats.speedUnit().getHandler();

        if (handler.requiresRpmConversionFields()) {
            // Speed mode: show speed values
            String suffix = hasVehicleSpeedData() ? "(VehicleSpeed)" : "(Calculated)";
            return String.format("%d-%d %s %s",
                Math.round(handler.getStartValue(this.fats)),
                Math.round(handler.getEndValue(this.fats)),
                handler.getDisplayName(), suffix);
        } else {
            // RPM mode: show RPM values
            return this.start + "-" + this.end + " RPM";
        }
    }
}

// vim: set sw=4 ts=8 expandtab:
