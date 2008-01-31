package org.nyet.logfile;

import java.util.ArrayList;
import java.util.Iterator;

public class CSVRow extends ArrayList<String> {
    public String toString() {
	String out = "";
	Iterator i = iterator();
	while(i.hasNext())
	    out += "\"" + i.next().toString() + "\"" + ",";
	return out;
    }

    public CSVRow() { super(); }
    public CSVRow(Object[] data) {
	for(int i=0;i<data.length;i++) {
	    super.add(data[i].toString());
	}
    }

    public boolean add(Comparable o) {
	return super.add(o.toString());
    }

    public boolean add(double f) {
	return super.add(String.valueOf(f));
    }

    public boolean add(int i) {
	return super.add(String.valueOf(i));
    }
}
