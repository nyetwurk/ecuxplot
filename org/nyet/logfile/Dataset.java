package org.nyet.logfile;

import java.io.*;
import java.lang.Math;
import java.util.*;
import au.com.bytecode.opencsv.*;

import org.nyet.util.DoubleArray;

public class Dataset {
    private String[] headers;
    private String[] units;
    private ArrayList<Column> columns;
    private int rows;
    protected String lastFilterReason;

    public class Range {
	public int start;
	public int end;
	public Range(int s, int e) { this.start=s; this.end=e; }
	public Range(int s) { this(s,s); }
	public Range() { this(0,0); }
	public int size() { return this.end-this.start+1; }
	public String toString() {
	    return String.format("[%d:%d]", start, end);
	}
    }

    public class Column {
	private Comparable id;
	private String units;
	public DoubleArray data;

	public Column(Comparable id, String units) {
	    this.id = id;
	    this.units = units;
	    this.data = new DoubleArray();
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

	public String getUnits() { return this.units; }
    }

    public class Key implements Comparable {
	private String fn;
	private String s;
	private Integer series;
	private BitSet flags;

	public Key (String fn, String s, int series, BitSet flags) {
	    this.fn=fn;
	    this.s=s;
	    this.series=new Integer(series);
	    this.flags=flags;
	}

	public Key (Key k) {
	    this.fn=k.fn;
	    this.s= new String(k.s);
	    this.series=k.series;
	    this.flags=k.flags;
	}

	public Key (Key k, int series) {
	    this.fn=k.fn;
	    this.s= new String(k.s);
	    this.series=series;
	    this.flags=k.flags;
	}

	public Key (String fn, String s, int series) {
	    this(fn, s, series, new BitSet(2));
	}

	public Key (String fn, String s) {
	    this(fn, s, 0, new BitSet(2));
	}


	public String toString() {
	    String ret = null;
	    if(!this.flags.get(0)) {
		// don't skip fn
		ret = org.nyet.util.Files.filenameStem(this.fn) + ":" + this.s;
	    } else ret = this.s;

	    if(!this.flags.get(1)) {
		// don't skip series
		return ret + " " + (this.series+1);
	    }

	    return ret;
	}

	public void hideFilename() { this.flags.set(0); }
	public void showFilename() { this.flags.clear(0); }
	public void hideSeries() { this.flags.set(1); }
	public void showSeries() { this.flags.clear(1); }

	public String getFilename() { return this.fn; }
	public String getString() { return this.s; }
	public Integer getSeries() { return this.series; }
	public void setSeries(int s) { this.series=s; }

	public int compareTo(Object o) {
	    if(o instanceof Key) {
		Key k = (Key)o;
		int out = this.fn.compareTo(k.fn);
		if(out!=0) return out;
		out = this.s.compareTo(k.s);
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
		if(!this.fn.equals(k.fn)) return false;
		if(!this.s.equals(k.s)) return false;
		return this.series.equals(k.series);
	    }
	    // if passed a string, only check the "s" portion
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
	ParseHeaders(reader);
	int i;
	for(i=0;i<this.headers.length;i++) {
	    this.columns.add(new Column(this.headers[i], this.units[i]));
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

    public void ParseHeaders(CSVReader reader) throws Exception {
	this.headers=reader.readNext();
    }

    public Column get(int id) {
	return (Column) this.columns.get(id);
    }

    public String units(Comparable id) {
	return this.get(id).getUnits();
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
		if(r==null) {
		    r = new Range(i);
		    this.lastFilterReason=null;
		}
		if(i==this.rows-1) end=true; // we hit end of data
	    } else {
		end = true;
	    }
	    if(r!=null && end) {
		r.end=i-1;
		if(rangeValid(r)) {
		    /*
		    if(this.lastFilterReason!=null) {
			System.out.println(this.lastFilterReason +
				": adding range " + r.toString());
		    }
		    */
		    out.add(r);
		}
		r=null;
	    }
	}
	return out;
    }

    public double[] getData(Key id, Range r) {
	// only match the string portion of the key
	Column c = this.get(id.getString());
	return c.data.toArray(r.start, r.end);
    }

    public double[] getData(Comparable id, Range r) {
	Column c = this.get(id);
	return c.data.toArray(r.start, r.end);
    }

    public void setHeaders(String [] headers) {this.headers=headers;}
    public String [] getHeaders() {return this.headers;}
    public String getHeader(int i) {return this.headers[i];}
    public void setUnits(String [] units) {this.units=units;}
    public String getLastFilterReason() { return this.lastFilterReason; }
    public int length() { return this.rows; }
}
