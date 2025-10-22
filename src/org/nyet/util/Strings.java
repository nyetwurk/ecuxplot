package org.nyet.util;

import java.util.Collection;
import java.util.ArrayList;

public class Strings {
    public static String join(String sep, Collection<?> c) {
	return join(sep, c.toArray(), c.size());
    }

    public static String join(String sep, ArrayList<Object> a) {
	return join(sep, a.toArray(), a.size());
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

    /**
     * Trims all non-null elements in a String array in-place.
     * @param array the String array to trim (modified in-place)
     */
    public static void trimArray(String[] array) {
	for (int i = 0; i < array.length; i++) {
	    if (array[i] != null) {
		array[i] = array[i].trim();
	    }
	}
    }
}
