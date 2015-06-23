package org.nyet.mappack;

import java.nio.ByteBuffer;

import org.nyet.util.Unsigned;
import org.nyet.util.Signed;
import org.nyet.util.Strings;

public class MapData {
    private Double[][] data;
    private long maximum = Long.MIN_VALUE;
    private long minimum = Long.MAX_VALUE;
    private Map map;
    private long widthmask = 0xffffffff;

    public MapData(Map map, ByteBuffer b) {
	this.map = map;
	b.position(map.extent[0].v);
	data = new Double[map.size.x][map.size.y];
	widthmask = (1<<(map.value.type.width()*8))-1;
	for(int i=0;i<map.size.x;i++) {
	    for(int j=0;j<map.size.y;j++) {
		long out;
		if (map.value.sign) {
		    switch(map.value.type.width()) {
			case 1: out=Signed.getSignedByte(b); break;
			case 2: out=Signed.getSignedShort(b); break;
			case 4: out=Signed.getSignedInt(b); break;
			default: data[i][j]=Double.NaN; continue;
		    }
		} else {
		    switch(map.value.type.width()) {
			case 1: out=Unsigned.getUnsignedByte(b); break;
			case 2: out=Unsigned.getUnsignedShort(b); break;
			case 4: out=Unsigned.getUnsignedInt(b); break;
			default: data[i][j]=Double.NaN; continue;
		    }
		}
		if(maximum<out) maximum = out;
		if(minimum>out) minimum = out;
		data[i][j]=map.value.convert(out);
	    }
	}
    }
    public double getMaximumValue() { return this.map.value.convert(this.maximum); }
    public double getMinimumValue() { return this.map.value.convert(this.minimum); }
    public long getMaximum() { return this.maximum & widthmask; }
    public long getMinimum() { return this.minimum & widthmask; }
    public Double[][] get() { return this.data; }
    @Override
    public String toString() {
	String[] rows = new String[data.length];
	for(int i=0;i<data.length;i++) {
	    String[] row = new String[data[i].length];
	    for(int j=0;j<data[i].length;j++) {
		if(this.map.value.precision==0)
		    row[j] = String.format("%d", (int)(data[i][j]+.5));
		else
		    row[j] = String.format("%." + this.map.value.precision +"f", data[i][j]);
	    }
	    rows[i]=Strings.join(",", row);
	}
	if(data.length==1) return rows[0];
	return "["+Strings.join("],\n[", rows)+"]";
    }
}
