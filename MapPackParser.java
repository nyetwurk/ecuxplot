import java.io.*;
import java.util.*;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MapPackParser
{
    private String signature;
    private String filename;
    private String version;
    private ArrayList<Project> projects;

    private class HexValue {
	public int v;
	public String toString() { return String.format("0x%x", v); }
	public HexValue(int vv) { v=vv; }
	public HexValue(ByteBuffer b) { v = b.getInt(); }
    }

    private static final String dumpHex(ByteBuffer b, int length) {
	if(length > b.limit()-b.position())
	    length = b.limit()-b.position();
	byte[] out = new byte[length];
	b.slice().get(out, 0, length);
	String s="";
	for(int i=0;i<length;i++) {
	    if(i>0) s+=(i%4==0)?" ":":";
	    s += String.format("%02x", out[i]);
	}
	return "[" + s + "]";
    }

    private static final String dumpHex(ByteBuffer b) {
	return dumpHex(b, b.limit()-b.position());
    }

    private static final String parseString(ByteBuffer b) {
	int len = b.getInt();
	if(len==0) return "";
	if(len<0) {
	    System.out.println(len + " invalid:" + String.format("%08x@0x%x", len, b.position()-4));
	    return "";
	}
	if(len>b.limit()-b.position()) {
	    System.out.println(len + " bytes is too big: " + String.format("%08x@0x%x", len, b.position()-4));
	    return "";
	}
	byte[] buf = new byte[len];
	b.get(buf, 0, buf.length);
	b.get();
	return new String(buf);
    }

    private final ByteBuffer parseBuffer(ByteBuffer b, short[] dst) {
	b.asShortBuffer().get(dst);
	b.position(b.position()+dst.length*2);
	return b;
    }

    private final ByteBuffer parseBuffer(ByteBuffer b, int[] dst) {
	b.asIntBuffer().get(dst);
	b.position(b.position()+dst.length*4);
	return b;
    }

    private final ByteBuffer parseBuffer(ByteBuffer b, HexValue[] dst) {
	for(int i=0;i<dst.length;i++) {
	    dst[i]=new HexValue(b);
	}
	return b;
    }

    private static final void eatNumber(ByteBuffer b, int count, int width) {
	for (int i=0; i<count; i++) {
	    int data;
	    switch (width) {
		case 1: data = b.get(); break;
		case 2: data = b.getShort(); break;
		case 4: data = b.getInt(); break;
		default: return;
	    }
	}
    }
    private static final void eatNumber(ByteBuffer b, int count) { eatNumber(b, count, 4); }
    private static final void eatNumber(ByteBuffer b) { eatNumber(b, 1); }

    private static final boolean parseHeader(ByteBuffer b) {
	eatNumber(b);		// 0
	eatNumber(b);		// 1
	eatNumber(b);		// 49
	eatNumber(b,3);		// 0 x 3
	eatNumber(b,1,2);	// 0 (short)
	eatNumber(b);		// 2
	if(b.getInt()!=-1) {
	    return false;
	}
	eatNumber(b,2);		// 0 x 2
	eatNumber(b);		// 1
	eatNumber(b,4);		// 0 x 4
	eatNumber(b);		// 0x0012b920
	eatNumber(b);		// 0x004d2b97
	eatNumber(b);		// 10
	eatNumber(b,10);	// 0 x 10
	eatNumber(b,10);	// 0 x 10
	eatNumber(b,3);		// 0 x 3
	eatNumber(b);		// 0x02edc648
	eatNumber(b,4);		// 0 x 4
	eatNumber(b);		// 10
	eatNumber(b);		// 0
	eatNumber(b,1,2);	// 0 (short)
	eatNumber(b,1,1);	// 0 (char)
	if(b.getInt()!=-1) {
	    return false;
	}
	eatNumber(b,2);		// 0 x 2
	eatNumber(b);		// 1
	eatNumber(b,4);		// 0 x 4
	eatNumber(b,1,1);	// 0 (char)
	eatNumber(b,0x14,2);	// 14 shorts 0,1,2,3 etc
	eatNumber(b,0x10,2);	// 10 shorts 0,1,2,3 etc
	eatNumber(b,18);	// 0 x 18
	if(b.getInt()!=-1) {
	    return false;
	}
	eatNumber(b);		// 1
	eatNumber(b);		// 0x00002844
	eatNumber(b);		// 0
	eatNumber(b);		// 0x42007899
	eatNumber(b);		// 2

	return true;
    }

    private class Project {
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
	    public Value(ByteBuffer b) {
		description = parseString(b);
		units = parseString(b);
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

	    public Axis(ByteBuffer b) {
		super(b);
		datasource = new DataSource(b);
		addr = new HexValue(b);
		values = new ValueType(b);
		parseBuffer(b, header1);	// unk
		header1a = b.getShort();	// unk
		prec = b.get();
		header2 = b.getInt();		// unk
		count = b.getInt();		// unk
		header3 = new int[count/4];
		parseBuffer(b, header3);	// unk
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

	private class Map {
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
	    private HexValue[] header10 = new HexValue[5];// unk
	    private int term;
	    private HexValue[] header11 = new HexValue[3];// unk

	    private String remain;

	    public Map(ByteBuffer b) {
		name = parseString(b);
		organization = new Organization(b);
		header = b.getInt();
		values = new ValueType(b);
		parseBuffer(b, headera);	// unk
		id = parseString(b);
		header1 = b.getInt();		// unk
		header1a = b.get();		// unk
		parseBuffer(b, range);
		parseBuffer(b, header2);	// unk
		reciprocal = b.get()==1;
		sign = b.get()==1;
		difference = b.get()==1;
		percent = b.get()==1;
		size = new Dimension(b);
		parseBuffer(b, header3);	// unk
		precision = b.getInt();
		value = new Value(b);
		parseBuffer(b, extent);
		parseBuffer(b, header4);	// unk
		parseBuffer(b, header5);	// unk
		header6 = new HexValue(b);
		header7 = b.getInt();
		x_axis = new Axis(b);
		y_axis = new Axis(b);
		header8 = b.getInt();		// unk
		header8a = b.getShort();	// unk
		parseBuffer(b, header9);	// unk
		header9a = b.getShort();	// unk
		header9b = b.getInt();		// unk
		header9c = b.get();		// unk
		parseBuffer(b, header10);	// unk
		term = b.getInt();
		parseBuffer(b, header11);	// unk

		remain = dumpHex(b, 16);
		System.out.println(toString());
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
		out += "  h10: " + Arrays.toString(header10) + " " + term + "\n";
		out += "  h11: " + Arrays.toString(header11) + "\n";
		// out += remain + "\n";
		return out;
	    }
	}
	public String name;
	private HexValue[] header = new HexValue[4];
	public String version;
	private HexValue[] header1 = new HexValue[5];
	public int numMaps;
	private byte header1a;
	public ArrayList<Map> maps;
	public Project(ByteBuffer b) {
	    name = parseString(b);
	    parseBuffer(b, header);	// unk
	    version = parseString(b);
	    parseBuffer(b, header1);	// unk
	    numMaps = b.getInt();
	    header1a = b.get();
	    maps = new ArrayList<Map>();
	    System.out.println(toString());
	    for(int i=0;i<numMaps;i++)
		maps.add(new Map(b));
	}
	public String toString () {
	    String out ="project: '" + name + "': (" + version + ") - " + numMaps + " maps\n";
	    out += "  h: " + Arrays.toString(header) + "\n";
	    out += " h1: " + Arrays.toString(header1) + "\n";
	    out += "h1a: " + header1a + " (byte)\n";
	    return out;
	}
    }

    public MapPackParser (String fname) throws Exception {
	File file = new File(fname);
	if(!file.exists()) {
	    System.out.println(fname + ": no such file");
	    return;
	}

	long length = file.length();
        MappedByteBuffer in = new FileInputStream(fname).getChannel().map(
	    FileChannel.MapMode.READ_ONLY, 0, length);
	in.order(ByteOrder.LITTLE_ENDIAN);
	signature = parseString(in);
	in.position(0x5c);
	filename = parseString(in);
	version = parseString(in);

	if(!parseHeader(in)) {
	    System.out.println(fname + ": header parse failed at " + in.position());
	    return;
	}
	projects = new ArrayList<Project>();
	projects.add(new Project(in));
    }

    public String toString() {
	String out="signature: '" + signature + "'\n";
	out += "filename: '" + filename + "'\n";
	out += "version: '" + version + "'\n";

	Iterator itp = projects.iterator();
	while(itp.hasNext()) {
	    Project p = (Project) itp.next();
	    out += p;
	    Iterator itm = p.maps.iterator();
	    while(itm.hasNext()) {
		Project.Map m = (Project.Map) itm.next();
		out += m;
	    }
	}

	return out;
    }

    public static void main(String[] args) throws Exception
    {
	if(args.length!=1) {
	    System.out.println("need argument");
	    return;
	}
	String filename = args[0];
	MapPackParser mp = new MapPackParser(args[0]);
	// System.out.print(mp);
    }
}
