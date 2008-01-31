package org.nyet.util;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MMapFile {
    private MappedByteBuffer buf;

    public MMapFile(String fname, ByteOrder order) throws Exception {
	File file = new File(fname);
	if(!file.exists()) throw new Exception(fname + ": no such file");

	this.buf = new FileInputStream(fname).getChannel().map(
	    FileChannel.MapMode.READ_ONLY, 0, file.length());
	if(this.buf == null) throw new Exception("constructor failed");

	this.buf.order(order);
    }

    public ByteBuffer getByteBuffer() throws Exception {
	return this.buf;
    }
}
