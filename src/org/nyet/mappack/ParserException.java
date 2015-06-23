package org.nyet.mappack;

import java.nio.ByteBuffer;

public class ParserException extends Exception {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    public ByteBuffer b;
    public Object o;
    public ParserException(ByteBuffer b, Throwable cause, Object o) {
	super(cause);
	this.b=b;
	this.o=o;
    }
    public ParserException(ByteBuffer b, String msg, Object o) {
	super(new Throwable(msg));
	this.b=b;
	this.o=o;
    }
    public ParserException(ByteBuffer b, String msg, Throwable cause, Object o) {
	super(msg, cause);
	this.b=b;
	this.o=o;
    }

    @Override
    public String getMessage()
    {
	return String.format("%s: obj=%s @ 0x%x(%d):\n  %s", super.getMessage(),
	    this.o.toString(), b.position(), b.position(), HexValue.dumpHex(b, 16));
    }
}
