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
    private int rows;

    public class Range {
	public int start;
	public int end;
	public Range(int s, int e) { this.start=s; this.end=e; }
	public Range(int s) { this(s,s); }
	public Range() { this(0,0); }
	public int size() { return this.end-this.start+1; }
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
	    try {
		data.append(Double.valueOf(s));
	    } catch (Exception e) {
	    }
	}
    }

    public class Key implements Comparable {
	private String s;
	private Integer series;
	public Key (String s, int series) {
	    this.s=s;
	    this.series=new Integer(series);
	}
	public String toString() { return this.s + " " + (this.series+1); }
	public String getString() { return this.s; }
	public Integer getSeries() { return this.series; }

	public int compareTo(Object o) {
	    if(o instanceof Key) {
		Key k = (Key)o;
		int out = this.s.compareTo(k.s);
		if(out!=0) return out;
		return this.series.compareTo(k.series);
	    }
	    if(o instanceof String) {
		return this.s.compareTo((String)o);
	    }
	    throw new ClassCastException("Not a Key or a String!");
	}
	public boolean equals(Object o) {
	    if(o instanceof Key) {
		Key k = (Key)o;
		if(!this.s.equals(k.s)) return false;
		return this.series.equals(k.series);
	    }
	    if(o instanceof String) {
		return this.s.equals((String)o);
	    }
	    throw new ClassCastException("Not a Key or a String!");
	}
    }

    public Dataset(String filename) throws Exception {
	CSVReader reader = new CSVReader(new FileReader(filename));
	this.rows = 0;
	this.columns = new ArrayList<Column>();
	this.headers = ParseHeaders(reader);
	int i;
	for(i=0;i<this.headers.length;i++) {
	    this.columns.add(new Column(this.headers[i]));
	}
	String [] nextLine;
	while((nextLine = reader.readNext()) != null) {
	    for(i=0;i<nextLine.length;i++) {
		this.columns.get(i).add(nextLine[i]);
	    }
	    this.rows++;
	}
    }

    public ArrayList<Column> getColumns() {return this.columns;}

    public String[] ParseHeaders(CSVReader reader) throws Exception {
	return reader.readNext();
    }

    public Column get(int id) {
	return (Column) this.columns.get(id);
    }

    public String units(Comparable id) {
	return this.get(id).units;
    }

    public Column get(Comparable id) {
	Iterator itc = this.columns.iterator();
	Column c = null;
	while(itc.hasNext()) {
	    c = (Column) itc.next();
	    // order is important!
	    if(id.equals(c.id)) return c;
	}
	return null;
    }

    public boolean exists(Comparable id) {
	if (this.get(id) == null) return false;
	if (this.get(id).data == null) return false;
	if (this.get(id).data.size() == 0) return false;
	return true;
    }

    protected boolean dataValid(int i) { return true; }
    protected boolean rangeValid(Range r) { return true; }

    public ArrayList<Range> getRanges() {
	Range r = null;
	ArrayList<Range> out = new ArrayList<Range>();
	for(int i=0;i<this.rows; i++) {
	    boolean end = false;
	    if(dataValid(i)) {
		if(r==null) r = new Range(i);
		if(i==this.rows-1) end=true; // we hit end of data
	    } else {
		end = true;
	    }
	    if(r!=null && end) {
		r.end=i;
		if(rangeValid(r)) out.add(r);
		r=null;
	    }
	}
	return out;
    }

    public double[] getData(Comparable id, Range r) {
	Column c = this.get(id);
	return c.data.toArray(r.start, r.end);
    }
}
