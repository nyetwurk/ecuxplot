import java.io.*;
import java.util.*;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class Parser {
    private String signature;
    private String filename;
    private String version;
    public ArrayList<Project> projects;

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

    private static final void parseHeader(ByteBuffer b) throws Exception {
	eatNumber(b);		// 0
	eatNumber(b);		// 1
	eatNumber(b);		// 49
	eatNumber(b,3);		// 0 x 3
	eatNumber(b,1,2);	// 0 (short)
	eatNumber(b);		// 2

	if(b.getInt()!=-1)
	    throw new Exception("can't find first term");

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

	if(b.getInt()!=-1)
	    throw new Exception("can't find second term");

	eatNumber(b,2);		// 0 x 2
	eatNumber(b);		// 1
	eatNumber(b,4);		// 0 x 4
	eatNumber(b,1,1);	// 0 (char)
	eatNumber(b,0x14,2);	// 14 shorts 0,1,2,3 etc
	eatNumber(b,0x10,2);	// 10 shorts 0,1,2,3 etc
	eatNumber(b,18);	// 0 x 18

	if(b.getInt()!=-1)
	    throw new Exception("can't find third term");

	eatNumber(b);		// 1
	eatNumber(b);		// 0x00002844
	eatNumber(b);		// 0
	eatNumber(b);		// 0x42007899
	eatNumber(b);		// 2
    }

    public Parser (String fname) throws Exception {
	File file = new File(fname);
	if(!file.exists()) {
	    throw new Exception(fname + ": no such file");
	}

	long length = file.length();
        MappedByteBuffer in = new FileInputStream(fname).getChannel().map(
	    FileChannel.MapMode.READ_ONLY, 0, length);
	in.order(ByteOrder.LITTLE_ENDIAN);
	signature = Parse.string(in);
	in.position(0x5c);
	filename = Parse.string(in);
	version = Parse.string(in);

	parseHeader(in);

	projects = new ArrayList<Project>();
	projects.add(new Project(in));
    }

    public String toString() {
	String out="signature: " + signature + "\n";
	out += "filename: " + filename + "\n";
	out += "version: " + version + "\n";
	out += "projects: " + projects.size() + "\n";

	Iterator itp = projects.iterator();
	while(itp.hasNext()) {
	    Project p = (Project) itp.next();
	    out += p;
	}

	return out;
    }

    public ArrayList<Map> find(Map map) {
	ArrayList<Map> matches = new ArrayList<Map>();
	Iterator itp = projects.iterator();
	while(itp.hasNext()) {
	    Project p = (Project) itp.next();
	    matches.addAll(p.find(map));
	}
	return matches;
    }

    public ArrayList<Map> find(String id) {
	ArrayList<Map> matches = new ArrayList<Map>();
	Iterator itp = projects.iterator();
	while(itp.hasNext()) {
	    Project p = (Project) itp.next();
	    matches.addAll(p.find(id));
	}
	return matches;
    }

    public ArrayList<Map> find(HexValue v) {
	ArrayList<Map> matches = new ArrayList<Map>();
	Iterator itp = projects.iterator();
	while(itp.hasNext()) {
	    Project p = (Project) itp.next();
	    matches.addAll(p.find(v));
	}
	return matches;
    }
}
