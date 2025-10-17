package org.nyet.ecuxplot;

import java.util.TreeMap;

import org.jfree.data.category.DefaultCategoryDataset;

import org.nyet.util.Files;

public class FATSDataset extends DefaultCategoryDataset {
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

    private void updateFromFATS() {
	if (this.fats.useMph()) {
	    // Check if we have actual VehicleSpeed data available
	    boolean hasVehicleSpeed = hasVehicleSpeedData();

	    if (hasVehicleSpeed) {
		// Use actual VehicleSpeed data - no conversion needed
		// The MPH values are used directly as speed ranges
		this.start = (int) Math.round(this.fats.startMph());
		this.end = (int) Math.round(this.fats.endMph());
	    } else {
		// Fall back to RPM-based calculation using rpm_per_mph constant
		if (!this.fileDatasets.isEmpty()) {
		    ECUxDataset firstDataset = this.fileDatasets.values().iterator().next();
		    double rpmPerMph = firstDataset.getEnv().c.rpm_per_mph();
		    this.start = this.fats.mphToRpm(this.fats.startMph(), rpmPerMph);
		    this.end = this.fats.mphToRpm(this.fats.endMph(), rpmPerMph);
		}
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
	for(final ECUxDataset data : this.fileDatasets.values()) {
	    setValue(data);
	}
    }

    // set one (calls super)
    public void setValue(ECUxDataset data, int series, double value) {
	final String xkey = Files.stem(data.getFileId());
	final String ykey = "Run " + (series+1);
	// System.out.println("adding " + xkey + "," + ykey + "=" + value);
	super.setValue(value, xkey, ykey);
    }
    // remove one (calls super)
    public void removeValue(ECUxDataset data, int series) {
	final String xkey = Files.stem(data.getFileId());
	final String ykey = "Run " + (series+1);
	// System.out.println("removing " + xkey + "," + ykey);
	super.removeValue(xkey, ykey);
    }

    // helpers
    public void setValue(ECUxDataset data) {
	try { removeRow(Files.stem(data.getFileId()));
	} catch (final Exception e) {}
	for(int i=0;i<data.getRanges().size();i++)
	    setValue(data, i);
    }
    public void setValue(ECUxDataset data, int series) {
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
	} catch (final Exception e) {
	    // System.out.println(e);
	    removeValue(data, series);
	}
    }

    // helpers
    public void removeValue(ECUxDataset data) {
	for(int i=0;i<data.getRanges().size();i++)
	    removeValue(data, i);
    }

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
