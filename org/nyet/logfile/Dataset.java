package org.nyet.logfile;

import java.io.*;
import java.lang.Math;
import java.util.*;
import au.com.bytecode.opencsv.*;

import org.jfree.data.xy.XYDataset;
import org.nyet.util.DoubleArray;

public class Dataset {
    public String[] headers;
    private ArrayList<Column> columns;
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
	private Comparable id;
	public String units;
	public DoubleArray data;

	public Column(Comparable id) {
	    this.id = id;
	    this.units = Units.find(id);
	    this.data = new DoubleArray();
	}

	public Column(Comparable id, double[] data) {
	    this.id = id;
	    this.units = Units.find(id);
	    this.data = new DoubleArray(data);
	}

	public Column(Comparable id, String units, double[] data) {
	    this.id = id;
	    this.units = units;
	    this.data = new DoubleArray(data);
	}

	public Column(Comparable id, String units, DoubleArray data) {
	    this.id = id;
	    this.units = units;
	    this.data = data;
	}

	public void add(String s) {
	    data.append(Double.valueOf(s));
	}
    }

    public Dataset(String filename) throws Exception {
	CSVReader reader = new CSVReader(new FileReader(filename));
	this.headers = reader.readNext();
	this.columns = new ArrayList<Column>();
	int i;
	for(i=0;i<this.headers.length;i++) {
	    this.columns.add(new Column(this.headers[i]));
	}
	String [] nextLine;
	while((nextLine = reader.readNext()) != null) {
	    for(i=0;i<nextLine.length;i++) {
		this.columns.get(i).add(nextLine[i]);
	    }
	}
	this.rpm = find("RPM");
	this.pedal = find("AcceleratorPedalPosition");
	this.gear = find("Gear");
    }

    public Column get(int id) {
	return (Column) this.columns.get(id);
    }

    public String units(Comparable id) {
	return this.find(id).units;
    }

    public Column find(Comparable id) {
	Iterator itc = this.columns.iterator();
	Column c = null;
	while(itc.hasNext()) {
	    c = (Column) itc.next();
	    if(c.id.equals(id)) return c;
	}
	if(id.equals("CalcLoad")) {
	    DoubleArray a = this.find("MassAirFlow").data.mult(60); // g/sec to g/min
	    DoubleArray b = this.find("RPM").data;
	    c = new Column(id, "%", a.div(b));
	} else if(id.equals("CalcIDC")) {
	    DoubleArray a = this.find("FuelInjectorOnTime").data.div(60*1000); // msec to minutes
	    DoubleArray b = this.find("RPM").data.div(2); // half cycle
	    c = new Column(id, "%", a.mult(b).mult(100)); // convert to %
	}
	if(c!=null) this.columns.add(c);

	return c;
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
