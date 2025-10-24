package org.nyet.mappack;

import java.util.*;
import java.util.zip.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;

import org.nyet.util.Files;
import org.nyet.util.XmlString;

public class Project {
    public String stem;
    public String name;
    public Date mTime;
    private final HexValue[] header = new HexValue[4];
    public String version;
    private final HexValue[] header1 = new HexValue[4];
    private final HexValue[] h77 = new HexValue[1];     // 77 33 88 11
    public int numMaps;
    public TreeSet<Map> maps;
    private final HexValue[] header2 = new HexValue[3];
    public int numFolders;
    public TreeSet<Folder> folders = new TreeSet<Folder>();
    private final int kpv;

    private void ParseHeader(ByteBuffer b) throws ParserException {
        this.name = Parse.string(b);
        Parse.buffer(b, this.header);   // unk

        if (this.kpv == Map.INPUT_KP_v2) b.getInt();    // eat an extra 0

        this.version = Parse.string(b);
        Parse.buffer(b, this.header1);  // unk
        if (this.kpv == Map.INPUT_KP_v2) b.getInt();    // eat an extra 0

        Parse.buffer(b, this.h77);      // unk
        if (this.kpv == Map.INPUT_KP_v2) b.get(); // eat an extra 0 byte
    }

    private static ByteBuffer is2bb(ZipInputStream in) throws IOException {
        final int BUFSIZE=1024;
        final ByteArrayOutputStream out = new ByteArrayOutputStream(BUFSIZE);

        final byte[] tmp = new byte[BUFSIZE];

        while (true) {
            final int r = in.read(tmp, 0, BUFSIZE);
            if (r == -1) break;
            if (r>0) out.write(tmp,0,r);
        }

        return ByteBuffer.wrap(out.toByteArray());
    }

    private void ParseMapsZip(ByteBuffer b) throws ParserException {
        final int start = b.position();
        final int zsize = b.getInt();
        final byte[] zip = new byte[zsize];
        b.get(zip);
        final ByteArrayInputStream bbis = new ByteArrayInputStream(zip);
        final ZipInputStream zis = new ZipInputStream(bbis);
        try {
            zis.getNextEntry();
            final ByteBuffer bb = is2bb(zis);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            bb.get();   // eat a byte
            ParseMaps(bb);
        } catch (final IOException e) {
            throw new ParserException(b, e.toString(), e);
        }

        b.position(start+zsize);
        b.getInt(); // eat an extra 0 int
    }

    private void ParseMaps(ByteBuffer b) throws ParserException {
        // Maps
        this.numMaps = b.getInt();

        this.maps = new TreeSet<Map>();
        for(int i=0;i<this.numMaps;i++) {
            try {
                final Map m = new Map(i, b, this.kpv);
                this.maps.add(m);
            } catch (final ParserException e) {
                throw new ParserException(e.b,
                    String.format("error parsing map %d/%d:\n  %s",
                        (i+1), this.numMaps, e.getMessage()),
                    e.getCause(), e.o);
            }
        }
    }

    private void ParseFolders(ByteBuffer b) throws ParserException {
        // Folders
        this.numFolders = b.getInt();

        for(int i=0;i<this.numFolders;i++) {
            try {
                final Folder f = new Folder(b, this.kpv);
                if(f.id>=0 && f.id<this.numFolders)
                    this.folders.add(f);
            } catch (final ParserException e) {
                throw new ParserException(e.b,
                    String.format("error parsing folder %d/%d: %s",
                        (i+1), this.numMaps, e.toString()),
                    e.o);
            }
        }

        // renumber ordinally, and make a lookup table for it
        final int flut[] = new int[this.numFolders];
        int i=0;
        for(final Folder f: this.folders) {
            flut[f.id]=i; // old->new
            f.id=i++; // new id
        }

        if(this.maps!=null) {
            // fix up map folder ids
            for(final Map m: this.maps) {
                m.folderId=flut[m.folderId]; // old->new
            }
        }
    }

    public Project(String filename, ByteBuffer b, int kpv) throws ParserException {
        this.kpv = kpv;
        this.stem = Files.stem(filename);

        ParseHeader(b);

        if (kpv == Map.INPUT_KP_v1) ParseMaps(b);
        else ParseMapsZip(b);

        Parse.buffer(b, this.header2);  // unk

        ParseFolders(b);

        // Trailing junk
        //this.remaining = new byte[b.remaining()];
        //Parse.buffer(b, this.remaining);      // unk
    }

    @Override
    public String toString () {
        String out ="project: '" + this.name + "': (" + this.version + ") - " + this.numMaps + " maps\n";
        out += "  h: " + Arrays.toString(this.header) + "\n";
        out += " h1: " + Arrays.toString(this.header1) + "\n";
        out += " h2: " + Arrays.toString(this.header2) + "\n";
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
                for(final Folder f: this.folders) {
                    out += String.format(Map.XDF_LBL+"\"%s\"\n", 2000+f.id, "Category"+f.id, f.name);
                }
                return out + "%%END%%\n\n";
            case Map.FORMAT_XDF:
                final XmlString xs = new XmlString();
                xs.indent();
                xs.append("XDFHEADER");
                xs.indent();
                // xs.append("flags", "0x1");   // ????
                xs.append("fileversion", this.version);
                xs.append("deftitle",this.stem);
                xs.append("description",this.name);
                xs.append("author","mesim translator");
                xs.append("baseoffset", 0);
                final LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
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
                for(final Folder f: this.folders) {
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
        if(this.maps == null) return null;
        final ArrayList<Map> matches = new ArrayList<Map>();
        for(final Map m: this.maps)
            if(m.equals(map)) matches.add(m);
        return matches;
    }

    public ArrayList<Map> find(String id) {
        if(this.maps == null) return null;
        final ArrayList<Map> matches = new ArrayList<Map>();
        for(final Map m: this.maps)
            if(m.equals(id)) matches.add(m);
        return matches;
    }

    public ArrayList<Map> find(HexValue v) {
        if(this.maps == null) return null;
        final ArrayList<Map> matches = new ArrayList<Map>();
        for(final Map m: this.maps)
            if(m.extent[0].equals(v));
        return matches;
    }
}

// vim: set sw=4 ts=8 expandtab:
