package org.nyet.util;

import java.nio.ByteBuffer;

public class Signed {
    public static short getSignedByte (ByteBuffer bb) {
	    return ((byte)(bb.get() & 0xff));
    }

    public static void putSignedByte (ByteBuffer bb, int val) {
	    bb.put ((byte)(val & 0xff));
    }

    public static short getSignedByte (ByteBuffer bb, int position) {
	    return ((byte)(bb.get (position) & (short)0xff));
    }

    public static void putSignedByte (ByteBuffer bb, int position, int val) {
	    bb.put (position, (byte)(val & 0xff));
    }

    // ---------------------------------------------------------------

    public static int getSignedShort (ByteBuffer bb) {
	    return ((short)(bb.getShort() & 0xffff));
    }

    public static void putSignedShort (ByteBuffer bb, int val) {
	    bb.putShort ((short)(val & 0xffff));
    }

    public static int getSignedShort (ByteBuffer bb, int position) {
	    return ((short)(bb.getShort (position) & 0xffff));
    }

    public static void putSignedShort (ByteBuffer bb, int position, int val) {
	    bb.putShort (position, (short)(val & 0xffff));
    }

    // ---------------------------------------------------------------

    public static long getSignedInt (ByteBuffer bb) {
	    return (bb.getInt() & 0xffffffffL);
    }

    public static void putSignedInt (ByteBuffer bb, long val) {
	    bb.putInt ((int)(val & 0xffffffffL));
    }

    public static long getSignedInt (ByteBuffer bb, int position) {
	    return (bb.getInt (position) & 0xffffffffL);
    }

    public static void putSignedInt (ByteBuffer bb, int position, long val) {
	    bb.putInt (position, (int)(val & 0xffffffffL));
    }
}
