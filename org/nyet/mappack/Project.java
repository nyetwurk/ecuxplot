package org.nyet.mappack;

import java.util.*;
import java.nio.ByteBuffer;

public class Project {
    public String name;
    private HexValue[] header = new HexValue[4];
    public String version;
    private HexValue[] header1 = new HexValue[5];
    public int numMaps;
    private byte header1a;
    public ArrayList<Map> maps;
    public Project(ByteBuffer b) throws ParserException {
	this.name = Parse.string(b);
	Parse.buffer(b, this.header);	// unk
	this.version = Parse.string(b);
	Parse.buffer(b, this.header1);	// unk
	this.numMaps = b.getInt();
	this.header1a = b.get();
	this.maps = new ArrayList<Map>();
	for(int i=0;i<numMaps;i++) {
	    try {
		this.maps.add(new Map(b));
	    } catch (ParserException e) {
		throw new ParserException(e.b,
		    String.format("error parsing map %d/%d: %s",
			(i+1), this.numMaps, e.toString()),
		    e.o);
	    }
	}
    }
    public String toString () {
	String out ="project: '" + name + "': (" + version + ") - " + numMaps + " maps\n";
	out += "  h: " + Arrays.toString(header) + "\n";
	out += " h1: " + Arrays.toString(header1) + "\n";
	out += "h1a: " + header1a + " (byte)\n";
	return out;
    }
    public String toString(int format, ByteBuffer imagebuf) {
	if(format == Map.FORMAT_XDF) {
	    String out = "%%HEADER%%\n";
	    out += String.format(Map.XDF_LBL+"\"%s\"\n",1000, "FileVers", this.version);
	    out += String.format(Map.XDF_LBL+"\"%s\"\n",1005, "DefTitle", this.name);
	    if(imagebuf!=null && imagebuf.limit()>0)
		out += String.format(Map.XDF_LBL+"0x%X\n",1030, "BinSize", imagebuf.limit());
	    out += String.format(Map.XDF_LBL+"%d\n",1034, "BaseOffset", 0);
	    out += String.format(Map.XDF_LBL+"0x%X\n",1300, "GenFlags", 0);
	    out += String.format(Map.XDF_LBL+"0x%X\n",1325, "ModeFlags", 0);
	    return out + "%%END%%\n\n";
	} else {
	    return "";
	}
    }

    public ArrayList<Map> find(Map map) {
	ArrayList<Map> matches = new ArrayList<Map>();
	Iterator itm = maps.iterator();
	while(itm.hasNext()) {
	    Map m = (Map) itm.next();
	    if(m.equals(map)) matches.add(m);
	}
	return matches;
    }

    public ArrayList<Map> find(String id) {
	ArrayList<Map> matches = new ArrayList<Map>();
	Iterator itm = maps.iterator();
	while(itm.hasNext()) {
	    Map m = (Map) itm.next();
	    if(m.equals(id)) matches.add(m);
	}
	return matches;
    }

    public ArrayList<Map> find(HexValue v) {
	ArrayList<Map> matches = new ArrayList<Map>();
	Iterator itm = maps.iterator();
	while(itm.hasNext()) {
	    Map m = (Map) itm.next();
	    if(m.extent[0].equals(v));
	}
	return matches;
    }
}

