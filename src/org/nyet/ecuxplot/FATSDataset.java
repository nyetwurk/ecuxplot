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
     * regardless of whether FATS is configured in RPM or MPH mode. In MPH mode,
     * the configured MPH values are converted to RPM using the rpm_per_mph constant.
     */
    private void updateFromFATS() {
	if (this.fats.useMph()) {
	    // Always convert MPH to RPM for calculation, regardless of VehicleSpeed data availability
	    if (!this.fileDatasets.isEmpty()) {
		ECUxDataset firstDataset = this.fileDatasets.values().iterator().next();
		double rpmPerMph = firstDataset.getEnv().c.rpm_per_mph();
		this.start = this.fats.mphToRpm(this.fats.startMph(), rpmPerMph);
		this.end = this.fats.mphToRpm(this.fats.endMph(), rpmPerMph);
	    }
	} else {
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

	// Always show RPM range with MPH conversion in parentheses
	double startMph, endMph;
	if (this.fats.useMph()) {
	    // MPH mode: use the configured MPH values
	    startMph = this.fats.startMph();
	    endMph = this.fats.endMph();
	} else {
	    // RPM mode: convert RPM to MPH using rpm_per_mph constant
	    ECUxDataset firstDataset = this.fileDatasets.values().iterator().next();
	    double rpmPerMph = firstDataset.getEnv().c.rpm_per_mph();
	    startMph = rpmStart / rpmPerMph;
	    endMph = rpmEnd / rpmPerMph;
	}

	String message = String.format("FATS: %s %s, %d (%.0fmph) - %d (%.0fmph) = %.1f seconds",
	    filename, runNumber, rpmStart, startMph, rpmEnd, endMph, value);
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
	// Don't calculate FATS when filter is disabled - FATS depends on filtered data
	if (!data.getFilter().enabled()) {
	    logger.info("FATS calculation skipped for {} run {}: filter disabled", Files.stem(data.getFileId()), series);
	    removeValue(data, series);
	    return;
	}

	try {
	    double value;
	    if (this.fats.useMph()) {
		// Use speed-based calculation (will fall back to RPM if no VehicleSpeed)
		value = data.calcFATSBySpeed(series, this.fats.startMph(), this.fats.endMph());
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
	if (this.fats.useMph()) {
	    // Convert RPM to MPH and save to FATS preferences
	    if (!this.fileDatasets.isEmpty()) {
		ECUxDataset firstDataset = this.fileDatasets.values().iterator().next();
		double rpmPerMph = firstDataset.getEnv().c.rpm_per_mph();
		this.fats.startMph(this.fats.rpmToMph(start, rpmPerMph));
	    }
	} else {
	    this.fats.start(start);
	}
	updateFromFATS();
	rebuild();
    }
    public void setEnd(int end) {
	if (this.fats.useMph()) {
	    // Convert RPM to MPH and save to FATS preferences
	    if (!this.fileDatasets.isEmpty()) {
		ECUxDataset firstDataset = this.fileDatasets.values().iterator().next();
		double rpmPerMph = firstDataset.getEnv().c.rpm_per_mph();
		this.fats.endMph(this.fats.rpmToMph(end, rpmPerMph));
	    }
	} else {
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
	if (this.fats.useMph()) {
	    if (hasVehicleSpeedData()) {
		return String.format("%d-%d MPH (VehicleSpeed)", Math.round(this.fats.startMph()), Math.round(this.fats.endMph()));
	    } else {
		return String.format("%d-%d MPH (Calculated)", Math.round(this.fats.startMph()), Math.round(this.fats.endMph()));
	    }
	} else {
	    return this.start + "-" + this.end + " RPM";
	}
    }
}
