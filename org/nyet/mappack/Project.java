package org.nyet.mappack;

import java.util.*;
import java.nio.ByteBuffer;

import org.nyet.util.Files;

public class Project {
    public String stem;
    public String name;
    private HexValue[] header = new HexValue[4];
    public String version;
    private HexValue[] header1 = new HexValue[5];
    public int numMaps;
    public ArrayList<Map> maps;
    private HexValue[] header2 = new HexValue[3];
    public int numFolders;
    public Folder[] folders;
    private byte[] remaining;
    public Project(String filename, ByteBuffer b) throws ParserException {
	this.stem = Files.stem(filename);
	this.name = Parse.string(b);
	Parse.buffer(b, this.header);	// unk
	this.version = Parse.string(b);
	Parse.buffer(b, this.header1);	// unk

	// Maps
	this.numMaps = b.getInt();
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

	Parse.buffer(b, this.header2);	// unk

	// Folders
	this.numFolders = b.getInt();
	this.folders = new Folder[numFolders];
	for(int i=0;i<numFolders;i++) {
	    try {
		Folder f = new Folder(b);
		if(f.id>=0 && f.id<numFolders)
		    this.folders[f.id]=f;
	    } catch (ParserException e) {
		throw new ParserException(e.b,
		    String.format("error parsing folder %d/%d: %s",
			(i+1), this.numMaps, e.toString()),
		    e.o);
	    }
	}

	// Trailing junk
	//this.remaining = new byte[b.remaining()];
	//Parse.buffer(b, this.remaining);	// unk
    }
    public String toString () {
	String out ="project: '" + name + "': (" + version + ") - " + numMaps + " maps\n";
	out += "  h: " + Arrays.toString(header) + "\n";
	out += " h1: " + Arrays.toString(header1) + "\n";
	out += " h2: " + Arrays.toString(header2) + "\n";
	// out += "rem: " + Arrays.toString(remaining) + "\n";
	return out;
    }
    public String toString(int format, ByteBuffer imagebuf) {
	switch(format) {
	    case Map.FORMAT_XDF:
		String out = "%%HEADER%%\n";
		out += String.format(Map.XDF_LBL+"\"%s\"\n",1000, "FileVers",
			this.version);
		out += String.format(Map.XDF_LBL+"\"%s\"\n",1005, "DefTitle",
			this.stem);
		out += String.format(Map.XDF_LBL+"\"%s\"\n",1006, "Desc",
			this.name);
		out += String.format(Map.XDF_LBL+"0x%X\n",1007, "DescSize",
			this.name.length()+1);
		out += String.format(Map.XDF_LBL+"\"%s\"\n",1010, "Author", "mesim translator");
		if(imagebuf!=null && imagebuf.limit()>0)
		    out += String.format(Map.XDF_LBL+"0x%X\n",1030, "BinSize", imagebuf.limit());
		out += String.format(Map.XDF_LBL+"%d\n",1034, "BaseOffset", 0);
		out += String.format(Map.XDF_LBL+"0x%X\n",1300, "GenFlags", 0);
		out += String.format(Map.XDF_LBL+"0x%X\n",1325, "ModeFlags", 0);
		for(Folder f: this.folders)
		    out += String.format(Map.XDF_LBL+"\"%s\"\n", 2000+f.id, "Category"+f.id, f.name);
		return out + "%%END%%\n\n";
	    case Map.FORMAT_DUMP:
		return toString();
	    default:
		return "";
	}
    }

    public ArrayList<Map> find(Map map) {
	ArrayList<Map> matches = new ArrayList<Map>();
	for(Map m: this.maps)
	    if(m.equals(map)) matches.add(m);
	return matches;
    }

    public ArrayList<Map> find(String id) {
	ArrayList<Map> matches = new ArrayList<Map>();
	for(Map m: this.maps)
	    if(m.equals(id)) matches.add(m);
	return matches;
    }

    public ArrayList<Map> find(HexValue v) {
	ArrayList<Map> matches = new ArrayList<Map>();
	for(Map m: this.maps)
	    if(m.extent[0].equals(v));
	return matches;
    }
}
