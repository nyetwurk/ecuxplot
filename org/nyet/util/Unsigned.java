package org.nyet.util;

import java.nio.ByteBuffer;

public class Unsigned {
    public static short getUnsignedByte (ByteBuffer bb)
    {
	    return ((short)(bb.get() & 0xff));
    }

    public static void putUnsignedByte (ByteBuffer bb, int value)
    {
	    bb.put ((byte)(value & 0xff));
    }

    public static short getUnsignedByte (ByteBuffer bb, int position)
    {
	    return ((short)(bb.get (position) & (short)0xff));
    }

    public static void putUnsignedByte (ByteBuffer bb, int position,
	    int value)
    {
	    bb.put (position, (byte)(value & 0xff));
    }

    // ---------------------------------------------------------------

    public static int getUnsignedShort (ByteBuffer bb)
    {
	    return (bb.getShort() & 0xffff);
    }

    public static void putUnsignedShort (ByteBuffer bb, int value)
    {
	    bb.putShort ((short)(value & 0xffff));
    }

    public static int getUnsignedShort (ByteBuffer bb, int position)
    {
	    return (bb.getShort (position) & 0xffff);
    }

    public static void putUnsignedShort (ByteBuffer bb, int position,
	    int value)
    {
	    bb.putShort (position, (short)(value & 0xffff));
    }

    // ---------------------------------------------------------------

    public static long getUnsignedInt (ByteBuffer bb)
    {
	    return ((long)bb.getInt() & 0xffffffffL);
    }

    public static void putUnsignedInt (ByteBuffer bb, long value)
    {
	    bb.putInt ((int)(value & 0xffffffffL));
    }

    public static long getUnsignedInt (ByteBuffer bb, int position)
    {
	    return ((long)bb.getInt (position) & 0xffffffffL);
    }

    public static void putUnsignedInt (ByteBuffer bb, int position,
	    long value)
    {
	    bb.putInt (position, (int)(value & 0xffffffffL));
    }
}
