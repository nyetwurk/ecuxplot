package org.nyet.logfile;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.text.SimpleDateFormat;

import au.com.bytecode.opencsv.*;

import org.nyet.util.DoubleArray;

public class Dataset {
    public static class DatasetId implements Comparable<Object> {
	public String id;
	public String id2;
	public String unit;
	public Object type;

	@Override
	public int compareTo(Object o) {
	    final DatasetId id = (DatasetId) o;
	    return this.id.compareTo(id.id);
	}

	@Override
	public String toString() { return this.id; }
	public DatasetId(String s) { this.id=s; }
	public DatasetId(String s, String id2, String unit) {
	    this.id=s; this.id2=id2; this.unit=unit;
	}
	public boolean equals(Comparable<?> o) {
	    return this.id.equals(o.toString());
	}
    }

    private DatasetId[] ids;
    private final String fileId;
    private final ArrayList<Column> columns;
    private ArrayList<Range> range_cache = new ArrayList<Range>();
    private int rows;
    protected ArrayList<String> lastFilterReasons = new ArrayList<String>();

    public class Range {
	public int start;
	public int end;
	public Range(int s, int e) { this.start=s; this.end=e; }
	public Range(int s) { this(s,s); }
	public Range() { this(0,0); }
	public int size() { return this.end-this.start+1; }
	@Override
	public String toString() {
	    return String.format("[%d:%d]", this.start, this.end);
	}
    }

    public class Column {
	private final DatasetId id;
	public DoubleArray data;

	public Column(Comparable<?> id, String units) {
	    this(id, null, units, new DoubleArray());
	}
	public Column(Comparable<?> id, String units, DoubleArray data) {
	    this(id, null, units, data);
	}
	public Column(Comparable<?> id, String id2, String units) {
	    this(id, id2, units, new DoubleArray());
	}
	public Column(Comparable<?> id, String id2, String units,
	    DoubleArray data) {
	    this.id = new DatasetId(id.toString(), id2, units);
	    this.data = data;
	}

	public Column(DatasetId id, DoubleArray data) {
	    this.id = id;
	    this.data = data;
	}

	public void add(String s) {
	    // nuke non-printable chars
	    final Pattern p = Pattern.compile("[^\\p{Print}]");
	    s=p.matcher(s).replaceAll("");

	    // look for time stamps, convert to seconds
	    final Pattern p1 = Pattern.compile("\\d{2}:\\d{2}:\\d{2}.\\d{1,3}");
	    final Pattern p2 = Pattern.compile("\\d{2}:\\d{2}:\\d{2}");
	    final Pattern p3 = Pattern.compile("\\d{2}:\\d{2}.\\d{1,3}");

	    SimpleDateFormat fmt=null;
	    if (p1.matcher(s).matches()) {
		fmt = new SimpleDateFormat("HH:mm:ss.SSS");
	    } else if (p2.matcher(s).matches()) {
		fmt = new SimpleDateFormat("HH:mm:ss");
	    } else if (p3.matcher(s).matches()) {
		fmt = new SimpleDateFormat("mm:ss.SSS");
	    }
	    if (fmt != null) {
		try {
		    final Date d = fmt.parse(s);
		    this.data.append(Double.valueOf(d.getTime())/1000);
		} catch (final Exception e) {
		}
	    } else {
		try {
		    this.data.append(Double.valueOf(s));
		} catch (final Exception e) {
		}
	    }
	}

	public String getId() {
	    if(this.id==null) return null;
	    return this.id.id;
	}
	public String getId2() {
	    if(this.id==null) return null;
	    return this.id.id2;
	}
	public String getUnits() {
	    if(this.id==null) return null;
	    return this.id.unit;
	}
	public String getLabel(boolean id2) {
	    return (id2 && this.id.id2!=null)?this.id.id2:this.id.id;
	}
    }

    public class Key implements Comparable<Object> {
	private String fn;
	private final String s;
	private Integer range;
	private final BitSet flags;
	private final Dataset data_cache; // dataset cache
	private DatasetId id_cache; // id cache
/*
	public Key (String fn, String s, int range, BitSet flags) {
	    this.fn=fn;
	    this.s=s;
	    this.range=range;
	    this.flags=flags;
	}
*/
	public Key (Key k, int range, Dataset data) {
	    this.fn=k.fn;
	    if (this.fn==null) this.fn = data.getFileId();
	    this.s= new String(k.s);
	    this.range=range;
	    this.data_cache = data;
	    this.flags=k.flags;
	    data.get(this); //initialize id cache
	}

	public Key (Key k, Dataset data) {
	    this (k, 0, data);
	}

	public Key (String s, Dataset data) {
	    this.fn = data.getFileId();
	    this.s=s;
	    this.data_cache = data;
	    this.range=0;
	    this.flags=new BitSet(2);
	    data.get(this); // initialize cache
	}

	/*
	 * public Key (String fn, String s, int range) { this(fn, s, range, new
	 * BitSet(2)); }
	 *
	 * public Key (String fn, String s) { this(fn, s, 0, new BitSet(2)); }
	 */
	private boolean useId2() {
	    return (this.data_cache != null && this.data_cache.useId2()
		    && this.id_cache != null && this.id_cache.id2 != null);
	}

	@Override
	public String toString() {
	    String ret = this.useId2()?this.id_cache.id2:this.s;

	    // don't skip file name, add to beginning
	    if(!this.flags.get(0))
		ret = org.nyet.util.Files.filenameStem(this.fn) + ":" + ret;

	    // don't skip range #, add to end
	    if(!this.flags.get(1))
		ret += " " + (this.range+1);

	    return ret;
	}

	public void hideFilename() { this.flags.set(0); }
	public void showFilename() { this.flags.clear(0); }
	public void hideRange() { this.flags.set(1); }
	public void showRange() { this.flags.clear(1); }

	public String getFilename() { return this.fn; }
	public String getId2()	{ return (this.id_cache!=null && this.id_cache.id2!=null)?this.id_cache.id2:this.s; }
	public String getString() { return this.s; }
	public Integer getRange() { return this.range; }
	public void setRange(int r) { this.range=r; }

	@Override
	public int compareTo(Object o) {
	    if(o instanceof Key) {
		final Key k = (Key)o;
		int out = this.fn.compareTo(k.fn);
		if(out!=0) return out;
		out = this.s.compareTo(k.s);
		if(out!=0) return out;
		return this.range.compareTo(k.range);
	    }
	    if(o instanceof String) {
		return this.s.compareTo((String)o);
	    }
	    throw new ClassCastException("Not a Key or a String!");
	}
	@Override
	public boolean equals(Object o) {
	    if(o==null) return false;
	    if(o instanceof Key) {
		final Key k = (Key)o;
		if(!this.fn.equals(k.fn)) return false;
		if(!this.s.equals(k.s)) return false;
		return this.range.equals(k.range);
	    }
	    // if passed a string, only check the "s" portion
	    if(o instanceof String) {
		return this.s.equals(o);
	    }
	    throw new ClassCastException(o + ": Not a Key or a String!");
	}
    }

    public Dataset(String filename, int verbose) throws Exception {
	this.fileId = org.nyet.util.Files.filename(filename);
	this.rows = 0;
	this.columns = new ArrayList<Column>();
	CSVReader reader = new CSVReader(new FileReader(filename));
	try {
	    ParseHeaders(reader, verbose);
	} catch ( final Exception e ) {
	    /* try semicolon separated */
	    reader = new CSVReader(new FileReader(filename), ';');
	    ParseHeaders(reader, verbose);
	}
	for (final DatasetId id : this.ids)
	    this.columns.add(new Column(id.id,
		id.id2,
		id.unit));

	String [] nextLine;
	while((nextLine = reader.readNext()) != null) {
	    if (nextLine.length>0) {
		boolean gotone=false;
		for(int i=0;i<nextLine.length;i++) {
 		    if (nextLine[i].trim().length()>0
			&& this.columns.size() > i) {
			this.columns.get(i).add(nextLine[i]);
			gotone=true;
		    }
		}
		if (gotone) this.rows++;
	    }
	}
	buildRanges();
    }

    public ArrayList<Column> getColumns() {return this.columns;}

    public void ParseHeaders(CSVReader reader, int verbose) throws Exception {
	final String [] line = reader.readNext();
	if (line.length>0 && line[0].trim().length()>0) {
	    this.ids = new DatasetId[line.length];
	    for(int i=0;i<line.length;i++) {
		this.ids[i].id = line[i];
	    }
	}
    }

    public Column get(int id) {
	return this.columns.get(id);
    }

    public Column get(Dataset.Key key) {
	final Column c = this.get((Comparable<?>)key);

	if(c!=null) key.id_cache = c.id; // cache column id

	return c;
    }

    public Column get(Comparable<?> id) {
	for(final Column c : this.columns) {
	    if(id.equals(c.id.id))
		return c;
	}
	return null;
    }

    public String units(Comparable<?> id) {
	final Column c = this.get(id);
	if(c==null) return null;
	return c.getUnits();
    }

    public String id2(Comparable<?> id) {
	final Column c = this.get(id);
	if(c==null) return null;
	return c.getId2();
    }

    public String getLabel(Comparable<?> id, boolean altnames) {
	final Column c = this.get(id);
	if(c==null) return null;
	return c.getLabel(altnames);
    }

    public boolean exists(Comparable<?> id) {
	if (this.get(id) == null) return false;
	if (this.get(id).data == null) return false;
	if (this.get(id).data.size() == 0) return false;
	return true;
    }

    protected boolean dataValid(int i) { return true; }
    protected boolean rangeValid(Range r) { return true; }

    public ArrayList<Range> getRanges() {
	return this.range_cache;
    }

    protected void buildRanges() {
        this.range_cache = new ArrayList<Range>();
	Range r = null;
	for(int i=0;i<this.rows; i++) {
	    boolean end = false;
	    if(dataValid(i)) {
		if(r==null) {
		    r = new Range(i);
		    this.lastFilterReasons=new ArrayList<String>();
		}
		if(i==this.rows-1) end=true; // we hit end of data
	    } else {
		end = true;
	    }
	    if(r!=null && end) {
		r.end=i-1;
		if(rangeValid(r)) {
		    /*
		    if(this.lastFilterReasons.size()!=0) {
			System.out.println(Strings.join(":", this.lastFilterReasons) +
				": adding range " + r.toString());
		    }
		    */
		    this.range_cache.add(r);
		}
		r=null;
	    }
	}
    }

    public double[] getData(Key id, Range r) {
	// only match the string portion of the key
	final Column c = this.get(id.getString());
	if (c==null) return null;
	return c.data.toArray(r.start, r.end);
    }

    public double[] getData(Comparable<?> id, Range r) {
	final Column c = this.get(id);
	if (c==null) return null;
	return c.data.toArray(r.start, r.end);
    }

    public String getFileId() { return this.fileId; }

    public DatasetId [] getIds() { return this.ids; }
    public void setIds(DatasetId [] ids) { this.ids=ids; }

    public ArrayList<String> getLastFilterReasons() { return this.lastFilterReasons; }
    public int length() { return this.rows; }
    public boolean useId2() { return false; }
}
