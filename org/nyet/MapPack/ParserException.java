package org.nyet.MapPack;

import java.nio.ByteBuffer;

public class ParserException extends Exception {
    public ByteBuffer b;
    public Object o;
    public ParserException(ByteBuffer b, String msg, Object o) {
	super(msg);
	this.b=b;
	this.o=o;
    }
    public String getMessage()
    {
	return String.format("%s: obj=%s @ 0x%x:\n%s", super.getMessage(),
	    this.o.toString(), b.position(), HexValue.dumpHex(b, 64));
    }
}
