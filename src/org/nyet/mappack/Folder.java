package org.nyet.mappack;

import java.util.Arrays;
import java.nio.ByteBuffer;

public class Folder implements Comparable {
    public int id;
    private HexValue header;
    public String name;
    private byte[] header1 = new byte[2];
    private HexValue header2;

    public Folder(ByteBuffer b) throws ParserException {
	this.id = b.getInt();
	this.header = new HexValue(b);
	this.name = Parse.string(b);
	Parse.buffer(b, this.header1);
	this.header2 = new HexValue(b);
    }

    private String toStringDump() {
	String out = "     id: " + id + "\n";
	out += " header: " + header + "\n";
	out += "   name: " + name + "\n";
	out += "header1: " + Arrays.toString(header1) + "\n";
	out += "header2: " + header2 + "\n";
	return out;
    }

    public String toString(int format) {
	switch(format) {
	    case Map.FORMAT_DUMP: return toStringDump();
	    default: return "";
	}
    }

    public String toString() { return this.name; };

    public int compareTo(Object o) {
	if (this.name.equals(o.toString())) return 0;
	if (this.name.equals("My maps")) return -1;
	if (o.toString().equals("My maps")) return 1;
	return this.name.compareTo(o.toString());
    }
}
