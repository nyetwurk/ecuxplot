package org.nyet.logfile;

import java.util.ArrayList;

public class CSVRow extends ArrayList<String> {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    @Override
    public String toString() {
	return "\"" + org.nyet.util.Strings.join("\",\"", this) + "\"";
    }

    public CSVRow() { super(); }
    public CSVRow(Object[] data) {
	for(int i=0;i<data.length;i++) {
	    add(data[i].toString());
	}
    }

    @Override
    public boolean add(String s) {
	if (s.length()==0) s="-";
	return super.add(s);
    }

    public boolean add(Comparable<?> o) {
	return add(o.toString());
    }

    public boolean add(double f) {
	return add(String.valueOf(f));
    }

    public boolean add(int i) {
	return add(String.valueOf(i));
    }
}
