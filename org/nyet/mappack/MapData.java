package org.nyet.mappack;

import java.nio.ByteBuffer;

public class MapData {
    private Double[][] data;
    private double maximum = Double.NEGATIVE_INFINITY;
    private double minimum = Double.POSITIVE_INFINITY;
    public MapData(Map map, ByteBuffer b) {
	b.position(map.extent[0].v);
	data = new Double[map.size.x][map.size.y];
	for(int i=0;i<map.size.x;i++) {
	    for(int j=0;j<map.size.y;j++) {
		double out = Double.NaN;
		switch(map.values.width()) {
		    case 1: out=b.get(); break;
		    case 2: out=b.getShort(); break;
		    case 4: out=b.getInt(); break;
		    default: data[i][j]=Double.NaN; continue;
		}
		data[i][j]=map.value.convert(out);
		if(maximum<data[i][j]) maximum = data[i][j];
		if(minimum>data[i][j]) minimum = data[i][j];
	    }
	}
    }
    public double getMaximum() { return this.maximum; }
    public double getMinimum() { return this.minimum; }
}
