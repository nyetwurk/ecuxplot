package org.nyet.mappack;

import java.util.Arrays;
import java.nio.ByteBuffer;

public class Folder implements Comparable<Object> {
    public int id;
    private int kpv;
    private HexValue header;
    public String name;
    private byte[] header1 = new byte[2];
    private HexValue header2;
    private byte[] header3 = new byte[15];

    public Folder(ByteBuffer b, int kpv) throws ParserException {
	this.kpv = kpv;
	this.id = b.getInt();
	this.header = new HexValue(b);
	this.name = Parse.string(b);
	Parse.buffer(b, this.header1);
	this.header2 = new HexValue(b);
	if (kpv == Map.INPUT_KP_v2)
	    Parse.buffer(b, this.header3);
    }

    private String toStringDump() {
	String out = "     id: " + id + "\n";
	out += " header: " + header + "\n";
	out += "   name: " + name + "\n";
	out += "header1: " + Arrays.toString(header1) + "\n";
	out += "header2: " + header2 + "\n";
	if (kpv == Map.INPUT_KP_v2)
	    out += "header3: " + Arrays.toString(header3) + "\n";
	return out;
    }

    public String toString(int format) {
	switch(format) {
	    case Map.FORMAT_DUMP: return toStringDump();
	    default: return "";
	}
    }

    @Override
    public String toString() { return this.name; };

    @Override
    public int compareTo(Object o) {
	if (this.name.equals(o.toString())) return 0;
	if (this.name.equals("My maps")) return -1;
	if (o.toString().equals("My maps")) return 1;
	return this.name.compareTo(o.toString());
    }
}
