package org.nyet.ecuxplot;

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

    public FATSDataset(TreeMap<String, ECUxDataset> fileDatasets, FATS fats) {
        this.fileDatasets=fileDatasets;
        this.fats = fats;
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

    private void rebuild() {
        clear();
        for(final ECUxDataset data : this.fileDatasets.values()) {
            setValue(data);
        }
    }

    // set one (calls super)
    public void setValue(ECUxDataset data, int series, double value) {
        final String xkey = "Run " + (series+1);
        final String ykey = Files.stem(data.getFileId());
        // System.out.println("adding " + xkey + "," + ykey + "=" + value);p

        super.setValue(value, xkey, ykey);
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
     * Set FATS data for all runs in a dataset
     * @param data The dataset containing FATS runs
     */
    public void setValue(ECUxDataset data) {
        try { removeColumn(Files.stem(data.getFileId()));
        } catch (final Exception e) {}
        for(int i=0;i<data.getRanges().size();i++)
            setValue(data, i);
    }
    /**
     * Set FATS data for a specific run in a dataset
     * @param data The dataset containing the FATS run
     * @param series The run number (0-based)
     */
    public void setValue(ECUxDataset data, int series) {
        try {
            double value;
            FATS.SpeedUnitHandler handler = this.fats.speedUnit().getHandler();

            if (handler.requiresRpmConversionFields()) {
                // Use speed-based calculation (will fall back to RPM if no VehicleSpeed)
                value = data.calcFATSBySpeed(series, handler.getStartValue(this.fats), handler.getEndValue(this.fats), this.fats.speedUnit());
            } else {
                // Use RPM-based calculation
                value = data.calcFATS(series, this.start, this.end);
            }
            setValue(data, series, value);

            // Log successful FATS calculation with concise summary
            logFATSSummary(data, series, value, this.start, this.end);
        } catch (final Exception e) {
            logger.warn("FATS calculation failed for {} run {}: {}", Files.stem(data.getFileId()), series, e.getMessage());
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
        rebuild();
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
        rebuild();
    }
    public int getStart() { return this.start; }
    public int getEnd() { return this.end; }

    public void refreshFromFATS() {
        updateFromFATS();
        rebuild();
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
