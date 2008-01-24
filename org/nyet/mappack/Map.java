package org.nyet.mappack;

import java.util.*;
import java.nio.ByteBuffer;

public class Map {
    private class Enm {
	private int value;
	public String[] legend;
	public Enm(ByteBuffer b) { value=b.getInt(); }
	public String toString() {
	    if(value<0 || value>legend.length-1)
		return String.format("(len %d) %x", legend.length, value);
	    return legend[value];
	}
    }

    private class Organization extends Enm {
	public Organization(ByteBuffer b) {
	    super(b);
	    String[] l= {
		"??",
		"??",
		"Single value",
		"Onedimensional",
		"Twodimensional",
		"2d Inverse"
	    };
	    legend = l;
	}
    }

    private class ValueType extends Enm {
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
	}
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

    private class Dimension {
	public int x;
	public int y;
	public Dimension(int xx, int yy) { x = xx; y = yy; }
	public Dimension(ByteBuffer b) { x = b.getInt(); y = b.getInt(); }
	public String toString() { return x + "x" + y; }
    }

    private class Value {
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
    }

    private class Axis extends Value {
	public DataSource datasource;
	public HexValue addr;
	public ValueType values;
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
	    values = new ValueType(b);
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
	    out += "\t addr: " + addr + " " + values + "\n";
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
    public ValueType values;
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
	values = new ValueType(b);
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

    private class CSVRow extends ArrayList<String> {
	public String toString() {
	    String out = "";
	    Iterator i = iterator();
	    while(i.hasNext())
		out += "\"" + i.next().toString() + "\"" + ",";
	    return out;
	}

	public boolean add(int i) {
	    return super.add(String.valueOf(i));
	}
    }

    public static final String CSVHeader() {
	return "\"ID\",\"Address\",\"Name\",\"Size\",\"Organization\",\"Description\",\"Units\",\"X Units\",\"Y Units\"";
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

    public String toCSV() {
	CSVRow row = new CSVRow();
	row.add(id);
	row.add(extent[0].toString());
	row.add(name);
	row.add(size.toString());
	row.add(values.toString());
	row.add(value.description);
	row.add(value.units);
	row.add(x_axis.units);
	row.add(y_axis.units);
	return row.toString();
    }

    public String toString() {
	String out = "  map: " + name + " [" + id + "] " + values + "\n";
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
