package org.nyet.ecuxplot;

import java.util.HashMap;
import java.util.Iterator;

import org.jfree.data.category.DefaultCategoryDataset;

import org.nyet.util.Files;

public class FATSDataset extends DefaultCategoryDataset {
    private int start = 4200;
    private int end = 6500;
    private HashMap<String, ECUxDataset> fileDatasets;

    public FATSDataset(HashMap<String, ECUxDataset> fileDatasets) {
	this.fileDatasets=fileDatasets;
	rebuild();
    }

    private void rebuild() {
	Iterator itc = this.fileDatasets.values().iterator();
	while(itc.hasNext()) {
	    ECUxDataset data = (ECUxDataset) itc.next();
	    setValue(data);
	}
    }

    // set one (calls super)
    public void setValue(ECUxDataset data, int series, double value) {
	String xkey = Files.stem(data.getFilename());
	String ykey = "Run " + (series+1);
	// System.out.println("adding " + xkey + "," + ykey + "=" + value);
	super.setValue(value, xkey, ykey);
    }
    // remove one (calls super)
    public void removeValue(ECUxDataset data, int series) {
	String xkey = Files.stem(data.getFilename());
	String ykey = "Run " + (series+1);
	// System.out.println("removing " + xkey + "," + ykey);
	super.removeValue(xkey, ykey);
    }

    // helpers
    public void setValue(ECUxDataset data) {
	try { removeRow(Files.stem(data.getFilename()));
	} catch (Exception e) {}
	for(int i=0;i<data.getRanges().size();i++)
	    setValue(data, i);
    }
    public void setValue(ECUxDataset data, int series) {
	try {
	    setValue(data, series, data.calcFATS(series,
			this.start, this.end));
	} catch (Exception e) {
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
	this.start=start;
	rebuild();
    }
    public void setEnd(int end) {
	this.end=end;
	rebuild();
    }
    public int getStart() { return this.start; }
    public int getEnd() { return this.end=end; }

    public String getTitle() {
	return this.start + "-" + this.end + " RPM";
    }
}
