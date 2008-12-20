package org.nyet.mappack;

import java.util.Arrays;
import java.util.Iterator;
import java.nio.ByteBuffer;
import org.nyet.logfile.CSVRow;

public class Map {
    private class Enm implements Comparable {
	protected int enm;
	public String[] legend;
	public Enm(ByteBuffer b) { enm=b.getInt(); }
	public Enm(int enm) { this.enm=enm; }
	public String toString() {
	    if(enm<0 || enm>legend.length-1)
		return String.format("(len %d) %x", legend.length, enm);
	    return legend[enm];
	}
	public int compareTo(Object o) {
	    return (new Integer(enm).compareTo(((Enm)o).enm));
	}
    }

    private class Organization extends Enm {
	public Organization(ByteBuffer b) {
	    super(b);
	    String[] l= {
		"??",			// 0
		"??",			// 1
		"Single value",		// 2
		"Onedimensional",	// 3
		"Twodimensional",	// 4
		"2d Inverse"		// 5
	    };
	    legend = l;
	}
    }

    public class ValueType extends Enm {
	private int width;
	public ValueType(int width, int enm) {
	    super(enm);
	    this.width = width;
	}
	public ValueType(ByteBuffer b) {
	    super(b);
	    String[] l= {
		"??",
		"8 Bit",
		"16 Bit (HiLo)",
		"16 Bit (LoHi)",
		"32 Bit (HiLoHilo)",
		"32 Bit (LoHiLoHi)",
		"32 BitFloat (HiloHiLo)",
		"32 BitFloat (LoHiLoHi)"
	    };
	    legend = l;
	    switch(this.enm) {
		case 1: width=1; break;
		case 2:
		case 3: width=2; break;
		case 4:
		case 5:
		case 6:
		case 7: width=4; break;
		default: width=0;
	    }
	}
	public int width() { return this.width; }
    }

    private class DataSource extends Enm {
	public DataSource(ByteBuffer b) {
	    super(b);
	    String[] l = {
		"1,2,3",
		"Eprom",
		"Eprom, add",
		"Eprom, subtract",
		"Free editable"
	    };
	    legend = l;
	}
    }

    public class Dimension implements Comparable {
	public int x;
	public int y;
	public Dimension(int xx, int yy) { x = xx; y = yy; }
	public Dimension(ByteBuffer b) { x = b.getInt(); y = b.getInt(); }
	public String toString() { return x + "x" + y; }
	public int compareTo(Object o) {
	    return (new Integer(areaOf())).compareTo(((Dimension)o).areaOf());
	}
	public int areaOf() { return this.x*this.y; }
    }

    public class Value {
	public String description;
	public String units;
	public double factor;
	public double offset;
	public String toString() {
	    return "(" + description + ")/" + units + " -  f/o: " + factor + "/" + offset;
	}
	public Value(ByteBuffer b) throws ParserException {
	    description = Parse.string(b);
	    units = Parse.string(b);
	    factor = b.getDouble();
	    offset = b.getDouble();
	}
	public double convert(double in) { return in*factor+offset; }
    }

    private class Axis extends Value {
	public DataSource datasource;
	public HexValue addr;
	public ValueType valueType;
	private int[] header1 = new int[2];	// unk
	private short header1a;
	public byte prec;
	private int header2;
	private int count;
	private int[] header3;
	private int header4;
	public HexValue signature;

	public Axis(ByteBuffer b) throws ParserException {
	    super(b);
	    datasource = new DataSource(b);
	    addr = new HexValue(b);
	    valueType = new ValueType(b);
	    Parse.buffer(b, header1);	// unk
	    header1a = b.getShort();	// unk
	    prec = b.get();
	    header2 = b.getInt();		// unk
	    count = b.getInt();		// unk
	    header3 = new int[count/4];
	    Parse.buffer(b, header3);	// unk
	    header4 = b.getInt();		// unk
	    signature = new HexValue(b);
	}

	public String toString() {
	    String out = super.toString() + "\n";
	    out += "\t   ds: " + datasource + "\n";
	    out += "\t addr: " + addr + " " + valueType + "\n";
	    out += "\t   h1: " + Arrays.toString(header1) + "\n";
	    out += "\t  h1a: " + header1a + " (short)\n";
	    out += "\t prec: " + prec + " (byte)\n";
	    out += "\t   h2: " + header2 + "\n";
	    out += "\tcount: " + count + "\n";
	    out += "\t   h3: " + Arrays.toString(header3) + "\n";
	    out += "\t   h4: " + header4 + "\n";
	    if(signature.v!=-1)
		out += "\t  sig: " + signature + "\n";
	    return out;
	}
    }

    public String name;
    public Organization organization;
    private int header;			//unk
    public ValueType valueType;
    private int[] headera = new int[3];	// unk
    public String id;
    private int header1;		// unk
    private byte header1a;		// unk
    public int[] range = new int[4];
    private HexValue[] header2 = new HexValue[8];	// unk
    public boolean reciprocal;
    public boolean sign;
    public boolean difference;
    public boolean percent;
    public Dimension size;
    private int[] header3 = new int[2];	// unk
    public int precision;
    public Value value;
    public HexValue[] extent = new HexValue[2];
    private HexValue[] header4 = new HexValue[2];	// unk
    private int[] header5 = new int[2];	// unk
    private HexValue header6;
    private int header7;		// unk
    public Axis x_axis;
    public Axis y_axis;
    private int header8;		// unk
    private short header8a;		// unk
    private int[] header9 = new int[8];	// unk
    private short header9a;		// unk
    private int header9b;		// unk
    private byte header9c;		// unk
    private HexValue[] header10 = new HexValue[6];// unk
    private HexValue[] header11 = new HexValue[2];// unk
    public byte[] term2 = new byte[4];

    public Map(ByteBuffer b) throws ParserException {
	name = Parse.string(b);
	organization = new Organization(b);
	header = b.getInt();
	valueType = new ValueType(b);
	Parse.buffer(b, headera);	// unk
	id = Parse.string(b);
	header1 = b.getInt();		// unk
	header1a = b.get();		// unk
	Parse.buffer(b, range);
	Parse.buffer(b, header2);	// unk
	reciprocal = b.get()==1;
	sign = b.get()==1;
	difference = b.get()==1;
	percent = b.get()==1;
	size = new Dimension(b);
	Parse.buffer(b, header3);	// unk
	precision = b.getInt();
	value = new Value(b);
	Parse.buffer(b, extent);
	Parse.buffer(b, header4);	// unk
	Parse.buffer(b, header5);	// unk
	header6 = new HexValue(b);
	header7 = b.getInt();
	x_axis = new Axis(b);
	y_axis = new Axis(b);
	header8 = b.getInt();		// unk
	header8a = b.getShort();	// unk
	Parse.buffer(b, header9);	// unk
	header9a = b.getShort();	// unk
	header9b = b.getInt();		// unk
	header9c = b.get();		// unk
	Parse.buffer(b, header10);	// unk
	Parse.buffer(b, header11);	// unk
	b.get(term2);
	// System.out.println(this);
    }

    // generate a 1d map for an axis
    public Map(Axis axis, int size) {
	this.extent[0] = axis.addr;
	this.size = new Dimension(1, size);
	this.valueType = axis.valueType;
	this.value = axis;
	this.precision = axis.prec;
    }

    public static final String CSVHeader() {
	final String[] header = {
	    "ID","Address","Name","Size","Organization","Description",
	    "Units","X Units","Y Units",
	    "Scale","X Scale","Y Scale",
	    "Value min","Value max","Value min*1", "Value max*1"
	    };
	final CSVRow out = new CSVRow(header);
	return out.toString();
    }

    public boolean equals(Map map) {
	String stem=map.id.split("[? ]")[0];
	if(stem.length()==0) return false;
	return equals(stem);
    }

    public boolean equals(String id) {
	if(id.length()==0 || this.id.length() == 0) return false;
	return (id.equals(this.id.split("[? ]")[0]));
    }


    public static final int FORMAT_CSV = 0;
    public static final int FORMAT_XDF = 1;
    public static final String XDF_LBL = "\t%06d %-17s=";
    public String toString(int format, ByteBuffer image) throws Exception {
	switch(format) {
	    case FORMAT_CSV: return toStringCSV(image);
	    case FORMAT_XDF: return toStringXDF(image);
	}
	return "";
    }

    private String toStringCSV(ByteBuffer image) throws Exception {
	CSVRow row = new CSVRow();
	row.add(this.id);
	row.add(this.extent[0]);
	row.add(this.name);
	row.add(this.size);
	row.add(this.valueType);
	row.add(this.value.description);
	row.add(this.value.units);
	row.add(this.x_axis.units);
	row.add(this.y_axis.units);
	row.add(this.value.factor);
	row.add(this.x_axis.factor);
	row.add(this.y_axis.factor);
	if(image!=null && image.limit()>0) {
	    MapData mapdata = new MapData(this, image);
	    row.add(mapdata.getMinimumValue());
	    row.add(mapdata.getMaximumValue());
	    row.add(String.format("0x%x", mapdata.getMinimum()));
	    row.add(String.format("0x%x", mapdata.getMaximum()));
	} else {
	    row.add("");
	    row.add("");
	}
	return row.toString();
    }
    private String toStringXDF(ByteBuffer image) throws Exception {
	String out;
	boolean table = false;
	boolean oneD = false;
	int off = 20000;
	switch (this.organization.enm) {
	    case 2: out = "%%CONSTANT%%\n"; break;
	    case 3: oneD = true; // fallthrough
	    case 4:
	    case 5: out = "%%TABLE%%\n"; table = true; break;
	    default: return "";
	}
	if(table) off = 40000;
	out += String.format(XDF_LBL+"\"%s\"\n",off+5,"Title",this.id);
	String desc = this.name; // this.value.description;

	if(desc.length()>0) {
	    out += String.format(XDF_LBL+"\"%s\"\n",off+10,"Desc",desc);
	    out += String.format(XDF_LBL+"0x%X\n",off+11,"DescSize",
		    desc.length());
	}

	if(this.value.units.length()>0) {
	    if(table)
		out += String.format(XDF_LBL+"\"%s\"\n",off+330,"ZUnits",
			this.value.units);
	    else
		out += String.format(XDF_LBL+"\"%s\"\n",off+20,"Units",
			this.value.units);
	}

	if(this.valueType.width()>1)
	    out += String.format(XDF_LBL+"0x%X\n",off+50,"SizeInBits",
		    this.valueType.width()*8);

	out += String.format(XDF_LBL+"0x%X\n",off+100,"Address",
		this.extent[0].v);

	if(this.value.factor != 1 || this.value.offset != 0) {
	    out += String.format(XDF_LBL+"%f * X", off+200,
		    table?"ZEq":"Equation", this.value.factor);
	    if(this.value.offset!=0)
		out += String.format(" %+f",this.value.offset);
	    out+=",TH|0|0|0|0|\n";
	}

	if(table) {
	    out += String.format(XDF_LBL+"0x%X\n", off+300, "Rows",
		this.size.x);
	    out += String.format(XDF_LBL+"0x%X\n", off+305, "Cols",
		this.size.y);
	    out += String.format(XDF_LBL+"\"%s\"\n", off+320, "XUnits",
		this.x_axis.units);
	    out += String.format(XDF_LBL+"\"%s\"\n", off+325, "YUnits",
		this.y_axis.units);
	    out += String.format(XDF_LBL+"X,%s\n", off+354, "XEq",
		"TH|0|0|0|0|");
	    if(!oneD) {
		out += String.format(XDF_LBL+"X,%s\n", off+364, "YEq",
		    "TH|0|0|0|0|");
	    }

	}

	if(image!=null && image.limit()>0) {
	    MapData mapdata = new MapData(this, image);
	    if(table) {
		// LabelType 0x1 = float, 0x2 = integer, 0x4 = string
		MapData xaxis = new MapData(new Map(this.x_axis, this.size.x),
			image);
		out += String.format(XDF_LBL+"%s\n", off+350, "XLabels",
			xaxis.toString());
		out += String.format(XDF_LBL+"0x%X\n", off+350,
		    "XLabelType", x_axis.prec==0?2:1);
		if(!oneD) {
		    MapData yaxis = new MapData(new Map(this.y_axis,
				this.size.y), image);
		    out += String.format(XDF_LBL+"%s\n", off+360, "YLabels",
			    yaxis.toString());
		    out += String.format(XDF_LBL+"0x%X\n", off+362,
			"YLabelType", y_axis.prec==0?2:1);
		}
	    }
	    /*
	    out += String.format(XDF_LBL+"%f\n", off+230, "RangeLow",
		mapdata.getMinimumValue());
	    out += String.format(XDF_LBL+"%f\n", off+240, "RangeHigh",
		mapdata.getMaximumValue());
	    */
	}

	if(oneD) {
	    // LabelType 0x1 = float, 0x2 = integer, 0x4 = string
	    out += String.format(XDF_LBL+"%s\n", off+360, "YLabels",
		this.y_axis.units);
	    out += String.format(XDF_LBL+"0x%X\n", off+362,
		"YLabelType", 4);
	}

	return out + "%%END%%\n";
    }

    public String toString() {
	String out = "  map: " + name + " [" + id + "] " + valueType + "\n";
	out += "  org: " + organization + "\n";
	out += "    h: " + header + "\n";
	out += "   ha: " + Arrays.toString(headera) + "\n";
	out += "   h1: " + header1 + "\n";
	out += "  h1a: " + header1a + " (byte)\n";
	out += "range: " + range[0] + "-" + range[2]+ "\n";
	out += "   h2: " + Arrays.toString(header2) + "\n";
	out += "flags: ";
	if(reciprocal) out += "R";
	if(sign) out += "S";
	if(difference) out += "D";
	if(percent) out += "P";
	out += "\n";
	out += " size: " + size + "\n";
	out += "   h3: " + Arrays.toString(header3) + "\n";
	out += " prec: " + precision + "\n";
	out += "value: " + value + "\n";
	out += " addr: " + Arrays.toString(extent) + "\n";
	out += "   h4: " + Arrays.toString(header4) + "\n";
	out += "   h5: " + Arrays.toString(header5) + "\n";
	out += "   h6: " + header6 + "\n";
	out += "   h7: " + header7 + "\n";
	out += "xaxis: " + x_axis + "\n";
	out += "yaxis: " + y_axis + "\n";
	out += "   h8: " + header8 + "\n";
	out += "  h8a: " + header8a + " (short)\n";
	out += "   h9: " + Arrays.toString(header9) + "\n";
	out += "  h9a: " + header9a + " (short)\n";
	out += "  h9b: " + header9b + "\n";
	out += "  h9c: " + header9c + " (byte)\n";
	out += "  h10: " + Arrays.toString(header10) + "\n";
	out += "  h11: " + Arrays.toString(header11) + "\n";
	out += " term2: " + Arrays.toString(term2) + "\n";
	return out;
    }
}
