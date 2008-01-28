package org.nyet.logfile;

import java.io.*;
import java.lang.Math;
import java.util.*;
import au.com.bytecode.opencsv.*;

public class Dataset {
    public String[] headers;
    public ArrayList<Column> data;
    public Filter filter = new Filter();
    private Column rpm, pedal, gear;

    public class Filter {
	public boolean enabled = false;
	public boolean monotonicRPM = true;
	public double monotonicRPMfuzz = 100;
	public double minRPM = 2500;
	public double maxRPM = 8000;
	public double minPedal = 95;
	public int gear = 3;
    }
    public class Column {
	public String id;
	public String units;
	public ArrayList<Double> data;

	public Column(String id) {
	    this.id = id;
	    data = new ArrayList<Double>();
	}

	public void add(String s) {
	    data.add(Double.valueOf(s));
	}
    }

    public Dataset(String filename) throws Exception {
	CSVReader reader = new CSVReader(new FileReader(filename));
	headers = reader.readNext();
	data = new ArrayList<Column>();
	int i;
	for(i=0;i<headers.length;i++) {
	    data.add(new Column(headers[i]));
	}
	String [] nextLine;
	while((nextLine = reader.readNext()) != null) {
	    for(i=0;i<nextLine.length;i++) {
		data.get(i).add(nextLine[i]);
	    }
	}
	rpm = find("RPM");
	pedal = find("AcceleratorPedalPosition");
	gear = find("Gear");
    }

    public Column find(String id) {
	Iterator itc = data.iterator();
	while(itc.hasNext()) {
	    Column c = (Column) itc.next();
	    if(c.id.equals(id)) return c;
	}
	return null;
    }

    private boolean dataValid(int i) {
	if(!filter.enabled) return true;
	if(gear!=null && Math.round(gear.data.get(i)) != filter.gear) return false;
	if(rpm.data.get(i)<filter.minRPM) return false;
	if(rpm.data.get(i)>filter.maxRPM) return false;
	if(pedal!=null && pedal.data.get(i)<filter.minPedal) return false;
	if(i<1 || rpm.data.get(i-1) - rpm.data.get(i) > filter.monotonicRPMfuzz) return false;
	return true;
    }

    public double[] asDoubles(String id) {
	Column c = find(id);
	int divisor=1;
	if(id.equals("TIME")) divisor=1000;
	if(c==null) return new double[0];
	double[] f = new double[c.data.size()];
	int j=0;
	for(int i=0;i<c.data.size(); i++) {
	    if(dataValid(i)) f[j++]=c.data.get(i)/divisor;
	}
	double out[] = new double[j];
	System.arraycopy(f, 0, out, 0, j);
	return out;
    }
}
