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

    public ArrayList<Column> getColumns() {return this.columns;}

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
	return null;
    }

    protected boolean dataValid(int i) { return true; }

    public double[] asDoubles(String id) {
	Column c = find(id);
	if(c==null) return new double[0];
	double[] f = new double[c.data.size()];
	int j=0;
	for(int i=0;i<c.data.size(); i++) {
	    if(dataValid(i)) f[j++]=c.data.get(i);
	}
	double out[] = new double[j];
	System.arraycopy(f, 0, out, 0, j);
	return out;
    }
}
