package org.nyet.util;

import java.util.Collection;

public class Strings {
    public static String join(String sep, Collection c) {
	return join(sep, c.toArray(), c.size());
    }

    public static String join(String sep, Object [] a) {
	return join(sep, a, a.length);
    }

    public static String join(String sep, Object [] a, int count) {
	String out = "";
	for (int i=0; i<count; i++) {
	    if(a[i].toString().length()>0)
		out += ((out.length()==0)?"":sep) + a[i].toString();
	}
	return out;
    }
}
