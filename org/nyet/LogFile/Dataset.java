package org.nyet.LogFile;

import java.io.*;
import java.util.*;
import au.com.bytecode.opencsv.*;

public class Dataset {
    public class Column {
	public String id;
	public ArrayList<Double> data;

	public Column(String id) {
	    this.id = id;
	    data = new ArrayList<Double>();
	}

	public void add(String s) {
	    data.add(Double.valueOf(s));
	}
    }

    public ArrayList<Column> data;
    public Dataset(String filename) throws Exception {
	CSVReader reader = new CSVReader(new FileReader(filename));
	String [] header = reader.readNext();
	data = new ArrayList<Column>();
	int i;
	for(i=0;i<header.length;i++) {
	    data.add(new Column(header[i]));
	}
	String [] nextLine;
	while((nextLine = reader.readNext()) != null) {
	    for(i=0;i<nextLine.length;i++) {
		data.get(i).add(nextLine[i]);
	    }
	}
    }

    public Column find(String id) {
	Iterator itc = data.iterator();
	while(itc.hasNext()) {
	    Column c = (Column) itc.next();
	    if(c.id.equals(id)) return c;
	}
	return null;
    }

    public double[] asDoubles(String id) {
	Column c = find(id);
	if(c==null) return new double[0];
	double[] out = new double[c.data.size()];
	for(int i=0;i<c.data.size();i++) out[i]=c.data.get(i);
	return out;
    }
}
