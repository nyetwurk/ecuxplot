package org.nyet.mappack;

import java.nio.ByteBuffer;

import org.nyet.util.Unsigned;

public class MapData {
    private Double[][] data;
    private long maximum = Long.MIN_VALUE;
    private long minimum = Long.MAX_VALUE;
    private Map map;

    public MapData(Map map, ByteBuffer b) {
	this.map = map;
	b.position(map.extent[0].v);
	data = new Double[map.size.x][map.size.y];
	for(int i=0;i<map.size.x;i++) {
	    for(int j=0;j<map.size.y;j++) {
		long out;
		switch(map.values.width()) {
		    case 1: out=Unsigned.getUnsignedByte(b); break;
		    case 2: out=Unsigned.getUnsignedShort(b); break;
		    case 4: out=Unsigned.getUnsignedInt(b); break;
		    default: data[i][j]=Double.NaN; continue;
		}
		if(maximum<out) maximum = out;
		if(minimum>out) minimum = out;
		data[i][j]=map.value.convert(out);
	    }
	}
    }
    public double getMaximumValue() { return this.map.value.convert(this.maximum); }
    public double getMinimumValue() { return this.map.value.convert(this.minimum); }
    public long getMaximum() { return this.maximum; }
    public long getMinimum() { return this.minimum; }
}
