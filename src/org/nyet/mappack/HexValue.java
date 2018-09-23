package org.nyet.mappack;

import java.nio.ByteBuffer;

public class HexValue implements Comparable<Object> {
    public int v;
    @Override
    public String toString() { return String.format("0x%x", this.v); }
    public HexValue(int vv) { this.v=vv; }
    public HexValue(ByteBuffer b) { this.v = b.getInt(); }

    public static final String dumpHex(ByteBuffer b, int length) {
	if(length > b.limit()-b.position())
	    length = b.limit()-b.position();
	final byte[] out = new byte[length];
	b.slice().get(out, 0, length);
	String s="";
	for(int i=0;i<length;i++) {
	    if(i>0) s+=(i%4==0)?" ":":";
	    s += String.format("%02x", out[i]);
	}
	return "[" + s + "]";
    }

    public boolean equals(HexValue v) { return (v.v==this.v); }
    public boolean equals(int v) { return (v==this.v); }
    @Override
    public int compareTo(Object o) {
	return (Integer.valueOf(this.v).compareTo(((HexValue)o).v));
    }

    public static final String dumpHex(ByteBuffer b) {
	return dumpHex(b, b.limit()-b.position());
    }
}
