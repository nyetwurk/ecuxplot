package org.nyet.mappack;

import java.util.*;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;

import org.nyet.util.MMapFile;

public class Parser extends MMapFile {
    private final String signature;
    private final String filename;
    private final String version;

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
	    final int pos = b.position();
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
	    final int pos = b.position();
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
	    Parse.string(b);
	    Parse.string(b);
		eatNumber(b,10);	// 0 x 10

		if(b.getInt()!=-1) {
		    final int pos = b.position();
		    throw new Exception(HexValue.dumpHex(b, 16) +
			": can't find term 2a @" + pos);
		}

		eatNumber(b,1,1);	// 0 (char)
		eatNumber(b,12);	// 0 x 12
	    Parse.string(b);
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
	    final int pos = b.position();
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
	final int kp[] = new int[2];
	int kpv;
	final ByteBuffer buf = this.getByteBuffer();
	this.signature = Parse.string(buf);
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

	this.filename = Parse.string(buf);
	this.version = Parse.string(buf);

	parseHeader(buf, kpv);

	this.projects = new ArrayList<Project>();
	final Project p = new Project(fname, buf, kpv);
	p.mTime = new Date(this.mTime);
	this.projects.add(p);
    }

    @Override
    public String toString() {
	String out="signature: " + this.signature + "\n";
	out += "filename: " + this.filename + "\n";
	out += "version: " + this.version + "\n";
	out += "projects: " + this.projects.size() + "\n";

	for (final Project p: this.projects)
	    out += p.toString();
	return out;
    }

    public ArrayList<Map> find(Map map) {
	final ArrayList<Map> matches = new ArrayList<Map>();
	for (final Project p: this.projects)
	    matches.addAll(p.find(map));
	return matches;
    }

    public ArrayList<Map> find(String id) {
	final ArrayList<Map> matches = new ArrayList<Map>();
	for (final Project p: this.projects)
	    matches.addAll(p.find(id));
	return matches;
    }

    public ArrayList<Map> find(HexValue v) {
	final ArrayList<Map> matches = new ArrayList<Map>();
	for (final Project p: this.projects)
	    matches.addAll(p.find(v));
	return matches;
    }

    public String getFilename() { return this.filename; }
}
