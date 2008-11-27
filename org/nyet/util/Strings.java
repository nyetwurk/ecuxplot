package org.nyet.util;

import java.util.ArrayList;

public class Strings {
	public static String join(String sep, ArrayList a) {
	    return join(sep, a.toArray(), a.size());
	}
	public static String join(String sep, Object [] a) {
	    return join(sep, a, a.length);
	}

	public static String join(String sep, Object [] a, int count) {
	    String out = "";
	    for (int i=0; i<count; i++) {
		out += ((i==0)?"":sep) + a[i].toString();
	    }
	    return out;
	}
}
