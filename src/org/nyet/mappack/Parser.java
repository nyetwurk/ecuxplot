package org.nyet.mappack;

import java.util.*;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;

import org.nyet.util.MMapFile;

public class Parser extends MMapFile {
    private String signature;
    private String filename;
    private String version;

    public ArrayList<Project> projects;

    private static final void eatNumber(ByteBuffer b, int count, int width) {
	for (int i=0; i<count; i++) {
	    switch (width) {
		case 1: b.get(); break;
		case 2: b.getShort(); break;
		case 4: b.getInt(); break;
		default: return;
	    }
	}
    }
    private static final void eatNumber(ByteBuffer b, int count) { eatNumber(b, count, 4); }
    private static final void eatNumber(ByteBuffer b) { eatNumber(b, 1); }

    private static final void parseHeader(ByteBuffer b, int ver) throws Exception {
	eatNumber(b);		// 0
	eatNumber(b);		// 1
	eatNumber(b);		// 49
	eatNumber(b,3);		// 0 x 3
	eatNumber(b,1,2);	// 0 (short)
	eatNumber(b);		// 2

	if(b.getInt()!=-1) {
	    int pos = b.position();
	    throw new Exception(HexValue.dumpHex(b, 16) +
		": can't find term 1 @" + pos);
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
	    int pos = b.position();
	    throw new Exception(HexValue.dumpHex(b, 16) +
		": can't find term 2 @" + pos);
	}

	switch(ver) {
	    case Map.INPUT_KP_v1:
		eatNumber(b,2);		// 0 x 2
		eatNumber(b);		// 1
		eatNumber(b,4);		// 0 x 4
		eatNumber(b,1,1);	// 0 (char)
		break;
	    case Map.INPUT_KP_v2:
		eatNumber(b,1,1);	// 0 (char)
		eatNumber(b,1);		// 5
		eatNumber(b,5);		// 0 x 5
		@SuppressWarnings("unused")
		String car = Parse.string(b);
		@SuppressWarnings("unused")
		String engine = Parse.string(b);
		eatNumber(b,10);	// 0 x 10

		if(b.getInt()!=-1) {
		    int pos = b.position();
		    throw new Exception(HexValue.dumpHex(b, 16) +
			": can't find term 2a @" + pos);
		}

		eatNumber(b,1,1);	// 0 (char)
		eatNumber(b,12);	// 0 x 12
		@SuppressWarnings("unused")
		String data = Parse.string(b);
		eatNumber(b,7);	// 0 x 7
		eatNumber(b,1);	// 9d ff ff ff
		eatNumber(b,2);		// 0 x 2
		eatNumber(b,1,1);	// 0 (char)
		break;
	}

	eatNumber(b,0x14,2);	// 0x14 shorts 0,1,2,3 etc
	eatNumber(b,0x10,2);	// 0x10 shorts 0,1,2,3 etc
	eatNumber(b,18);	// 0 x 18

	if(b.getInt()!=-1) {
	    int pos = b.position();
	    throw new Exception(HexValue.dumpHex(b, 16) +
		": can't find term 3 @" + pos);
	}

	eatNumber(b);		// 1

	switch(ver) {
	    case Map.INPUT_KP_v1:
		break;
	    case Map.INPUT_KP_v2:
		eatNumber(b);		// 0
		eatNumber(b);		// 1
		break;
	}

	eatNumber(b);		// 0xXXXXXXXX
	eatNumber(b);		// 0
	eatNumber(b);		// 0x42007899
	eatNumber(b);		// 2
    }

    public Parser (String fname) throws Exception {
	super(fname, ByteOrder.LITTLE_ENDIAN);
	int kp[] = new int[2];
	int kpv;
	ByteBuffer buf = this.getByteBuffer();
	signature = Parse.string(buf);
	kp[0] = buf.getInt();
	kp[1] = buf.getInt();
	switch(kp[0]) {
	    case 0x71:
	    case 0x74:
		kpv = Map.INPUT_KP_v1;
		buf.position(0x5c);
		break;
	    case 0x124:
	    case 0x149:
		kpv = Map.INPUT_KP_v2;
		buf.position(0x60);
		break;
	    default:
		throw new Exception("Unknown kp version " + kp[0]);
	}

	filename = Parse.string(buf);
	version = Parse.string(buf);

	parseHeader(buf, kpv);

	projects = new ArrayList<Project>();
	Project p = new Project(fname, buf, kpv);
	p.mTime = new Date(this.mTime);
	projects.add(p);
    }

    public String toString() {
	String out="signature: " + signature + "\n";
	out += "filename: " + filename + "\n";
	out += "version: " + version + "\n";
	out += "projects: " + projects.size() + "\n";

	for (Project p: projects)
	    out += p.toString();
	return out;
    }

    public ArrayList<Map> find(Map map) {
	ArrayList<Map> matches = new ArrayList<Map>();
	for (Project p: projects)
	    matches.addAll(p.find(map));
	return matches;
    }

    public ArrayList<Map> find(String id) {
	ArrayList<Map> matches = new ArrayList<Map>();
	for (Project p: projects)
	    matches.addAll(p.find(id));
	return matches;
    }

    public ArrayList<Map> find(HexValue v) {
	ArrayList<Map> matches = new ArrayList<Map>();
	for (Project p: projects)
	    matches.addAll(p.find(v));
	return matches;
    }

    public String getFilename() { return this.filename; }
}
