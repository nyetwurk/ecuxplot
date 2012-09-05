package org.nyet.mappack;

import java.io.*;
import java.nio.ByteBuffer;

public class Parse {
    public static final String string(ByteBuffer b) throws ParserException {
	b.mark();
	int len = b.getInt();
	if(len==0) return "";
	if(len<0) {
	    b.reset();
	    throw new ParserException(b, "negative len", len);
	}
	if(len>b.limit()-b.position()) {
	    b.reset();
	    throw new ParserException(b, "invalid len", len);
	}
	byte[] buf = new byte[len];
	b.get(buf, 0, buf.length);
	b.get();
	try {
	    return new String(buf, "ISO-8859-15");
	} catch (UnsupportedEncodingException e) {
	    throw new ParserException(b, e.getMessage(), buf);
	}
    }

    public static final ByteBuffer buffer(ByteBuffer b, byte[] dst) {
	b.get(dst, 0, dst.length);
	return b;
    }

    public static final ByteBuffer buffer(ByteBuffer b, short[] dst) {
	b.asShortBuffer().get(dst);
	b.position(b.position()+dst.length*2);
	return b;
    }

    public static final ByteBuffer buffer(ByteBuffer b, int[] dst) {
	b.asIntBuffer().get(dst);
	b.position(b.position()+dst.length*4);
	return b;
    }

    public static final ByteBuffer buffer(ByteBuffer b, HexValue[] dst) {
	for(int i=0;i<dst.length;i++) {
	    dst[i]=new HexValue(b);
	}
	return b;
    }
}
