package org.nyet.mappack;

import java.util.*;
import java.nio.ByteBuffer;

import org.nyet.util.Files;
import org.nyet.util.XmlString;

public class Project {
    public String stem;
    public String name;
    public Date mTime;
    private HexValue[] header = new HexValue[4];
    public String version;
    private HexValue[] header1 = new HexValue[5];
    public int numMaps;
    public TreeSet<Map> maps;
    private HexValue[] header2 = new HexValue[3];
    public int numFolders;
    public TreeSet<Folder> folders = new TreeSet<Folder>();
    private byte[] remaining;
    public Project(String filename, ByteBuffer b) throws ParserException {
	this.stem = Files.stem(filename);
	this.name = Parse.string(b);
	Parse.buffer(b, this.header);	// unk
	this.version = Parse.string(b);
	Parse.buffer(b, this.header1);	// unk

	// Maps
	this.numMaps = b.getInt();
	this.maps = new TreeSet<Map>();
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

	for(int i=0;i<numFolders;i++) {
	    try {
		Folder f = new Folder(b);
		if(f.id>=0 && f.id<numFolders)
		    this.folders.add(f);
	    } catch (ParserException e) {
		throw new ParserException(e.b,
		    String.format("error parsing folder %d/%d: %s",
			(i+1), this.numMaps, e.toString()),
		    e.o);
	    }
	}

	// renumber ordinally, and make a lookup table for it
        int flut[] = new int[numFolders];
	int i=0;
	for(Folder f: this.folders) {
	    flut[f.id]=i; // old->new
	    f.id=i++; // new id
	}

	// fix up map folder ids
	for(Map m: this.maps)
	    m.folderId=flut[m.folderId]; // old->new

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
	String out;
	switch(format) {
	    case Map.FORMAT_OLD_XDF:
		out = "%%HEADER%%\n";
		out += String.format(Map.XDF_LBL+"\"%s\"\n",1000, "FileVers",
			this.version + " - " + this.mTime);
		out += String.format(Map.XDF_LBL+"\"%s\"\n",1005, "DefTitle",
			this.stem);
		out += String.format(Map.XDF_LBL+"\"%s\"\n",1006, "Desc",
			this.name);
		out += String.format(Map.XDF_LBL+"0x%X\n",1007, "DescSize",
			this.name.length()+1);
		out += String.format(Map.XDF_LBL+"\"%s\"\n",1010, "Author", "mesim translator");
		if(imagebuf!=null && imagebuf.limit()>0)
		    out += String.format(Map.XDF_LBL+"0x%X\n",1030, "BinSize", imagebuf.limit());
		out += String.format(Map.XDF_LBL+"%d\n",1035, "BaseOffset", 0);
		out += String.format(Map.XDF_LBL+"\"\"\n",1200, "ADSAssoc", 0);
		out += String.format(Map.XDF_LBL+"0x0\n",1225, "ADSCheck", 0);
		out += String.format(Map.XDF_LBL+"0x%X\n",1300, "GenFlags", 0);
		out += String.format(Map.XDF_LBL+"0x%X\n",1325, "ModeFlags", 0);
		for(Folder f: this.folders) {
		    out += String.format(Map.XDF_LBL+"\"%s\"\n", 2000+f.id, "Category"+f.id, f.name);
		}
		return out + "%%END%%\n\n";
	    case Map.FORMAT_XDF:
		XmlString xs = new XmlString();
		xs.indent();
		xs.append("XDFHEADER");
		xs.indent();
		// xs.append("flags", "0x1");	// ????
		xs.append("fileversion", this.version);
		xs.append("deftitle",this.stem);
		xs.append("description",this.name);
		xs.append("author","mesim translator");
		xs.append("baseoffset", 0);
		LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
		m.put("datasizeinbits",8);
		m.put("sigdigits",2);
		m.put("outputtype",1);
		m.put("signed",0);
		m.put("lsbfirst",1);
		m.put("float",0);
		xs.append("DEFAULTS", m);
		if(imagebuf!=null && imagebuf.limit()>0) {
		    m.clear();
		    m.put("type","0xFFFFFFFF");
		    m.put("startaddress","0x0");
		    m.put("size", String.format("0x%X", imagebuf.limit()));
		    m.put("regionflags", "0x0");
		    m.put("name", "Binary File");
		    m.put("desc", "This region describes the bin file edited by this XDF");
		    xs.append("REGION", m);
		}
		for(Folder f: this.folders) {
		    m.clear();
		    m.put("index", String.format("0x%X", f.id));
		    m.put("name", f.name);
		    xs.append("CATEGORY", m);
		}
		xs.unindent();
		return xs.append("/XDFHEADER").toString();
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
