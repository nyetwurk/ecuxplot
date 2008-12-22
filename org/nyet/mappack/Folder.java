package org.nyet.mappack;

import java.util.Arrays;
import java.nio.ByteBuffer;

public class Folder {
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

    public String toString() {
	String out = "     id: " + id + "\n";
	out += " header: " + header + "\n";
	out += "   name: " + name + "\n";
	out += "header1: " + Arrays.toString(header1) + "\n";
	out += "header2: " + header2 + "\n";
	return out;
    }
    public String toString(int format) {
	switch(format) {
	    case Map.FORMAT_DUMP: return toString();
	    default: return "";
	}
    }
}
