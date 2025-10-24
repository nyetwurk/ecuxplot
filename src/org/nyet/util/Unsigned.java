package org.nyet.util;

import java.nio.ByteBuffer;

public class Unsigned {
    public static short getUnsignedByte (ByteBuffer bb) {
            return ((short)(bb.get() & 0xff));
    }

    public static void putUnsignedByte (ByteBuffer bb, int val) {
            bb.put ((byte)(val & 0xff));
    }

    public static short getUnsignedByte (ByteBuffer bb, int position) {
            return ((short)(bb.get (position) & (short)0xff));
    }

    public static void putUnsignedByte (ByteBuffer bb, int position, int val) {
            bb.put (position, (byte)(val & 0xff));
    }

    // ---------------------------------------------------------------

    public static int getUnsignedShort (ByteBuffer bb) {
            return (bb.getShort() & 0xffff);
    }

    public static void putUnsignedShort (ByteBuffer bb, int val) {
            bb.putShort ((short)(val & 0xffff));
    }

    public static int getUnsignedShort (ByteBuffer bb, int position) {
            return (bb.getShort (position) & 0xffff);
    }

    public static void putUnsignedShort (ByteBuffer bb, int position, int val) {
            bb.putShort (position, (short)(val & 0xffff));
    }

    // ---------------------------------------------------------------

    public static long getUnsignedInt (ByteBuffer bb) {
            return (bb.getInt() & 0xffffffffL);
    }

    public static void putUnsignedInt (ByteBuffer bb, long val) {
            bb.putInt ((int)(val & 0xffffffffL));
    }

    public static long getUnsignedInt (ByteBuffer bb, int position) {
            return (bb.getInt (position) & 0xffffffffL);
    }

    public static void putUnsignedInt (ByteBuffer bb, int position, long val) {
            bb.putInt (position, (int)(val & 0xffffffffL));
    }
}

// vim: set sw=4 ts=8 expandtab:
