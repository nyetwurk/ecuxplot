package org.nyet.mappack;

import java.nio.ByteBuffer;

import org.nyet.util.Unsigned;
import org.nyet.util.Signed;
import org.nyet.util.Strings;

public class MapData {
    private final Double[][] data;
    private long maximum = Long.MIN_VALUE;
    private long minimum = Long.MAX_VALUE;
    private final Map map;
    private long widthmask = 0xffffffff;

    public MapData(Map map, ByteBuffer b) {
	this.map = map;
	b.position(map.extent[0].v);
	this.data = new Double[map.size.x][map.size.y];
	this.widthmask = (1<<(map.value.type.width()*8))-1;
	for(int i=0;i<map.size.x;i++) {
	    for(int j=0;j<map.size.y;j++) {
		long out;
		if (map.value.sign) {
		    switch(map.value.type.width()) {
			case 1: out=Signed.getSignedByte(b); break;
			case 2: out=Signed.getSignedShort(b); break;
			case 4: out=Signed.getSignedInt(b); break;
			default: this.data[i][j]=Double.NaN; continue;
		    }
		} else {
		    switch(map.value.type.width()) {
			case 1: out=Unsigned.getUnsignedByte(b); break;
			case 2: out=Unsigned.getUnsignedShort(b); break;
			case 4: out=Unsigned.getUnsignedInt(b); break;
			default: this.data[i][j]=Double.NaN; continue;
		    }
		}
		if(this.maximum<out) this.maximum = out;
		if(this.minimum>out) this.minimum = out;
		this.data[i][j]=map.value.convert(out);
	    }
	}
    }
    public double getMaximumValue() { return this.map.value.convert(this.maximum); }
    public double getMinimumValue() { return this.map.value.convert(this.minimum); }
    public long getMaximum() { return this.maximum & this.widthmask; }
    public long getMinimum() { return this.minimum & this.widthmask; }
    public Double[][] get() { return this.data; }
    @Override
    public String toString() {
	final String[] rows = new String[this.data.length];
	for(int i=0;i<this.data.length;i++) {
	    final String[] row = new String[this.data[i].length];
	    for(int j=0;j<this.data[i].length;j++) {
		if(this.map.value.precision==0)
		    row[j] = String.format("%d", (int)(this.data[i][j]+.5));
		else
		    row[j] = String.format("%." + this.map.value.precision +"f", this.data[i][j]);
	    }
	    rows[i]=Strings.join(",", row);
	}
	if(this.data.length==1) return rows[0];
	return "["+Strings.join("],\n[", rows)+"]";
    }
}
