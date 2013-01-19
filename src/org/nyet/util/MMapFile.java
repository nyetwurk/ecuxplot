package org.nyet.util;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MMapFile {
    public final long length;
    public final long mTime;
    private MappedByteBuffer buf;

    public MMapFile(String fname, ByteOrder order) throws Exception {
	File file = new File(fname);
	if(!file.exists()) throw new Exception(fname + ": no such file");

	FileInputStream fi = new FileInputStream(fname);
	try {
	    this.buf=fi.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, file.length());
	    if(this.buf == null) {
		if (fi!=null) fi.close();
		throw new Exception("constructor failed");
	    }

	    this.buf.order(order);
	    this.length = file.length();
	    this.mTime = file.lastModified();
	} finally {
	    if (fi!=null) fi.close();
	}
    }

    public ByteBuffer getByteBuffer() throws Exception {
	return this.buf;
    }
}
