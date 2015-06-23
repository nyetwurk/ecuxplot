package org.nyet.util;

public class Files {
    public static String dirname(String s) {
	final String [] a = s.split("\\"+java.io.File.separator);
	return Strings.join(java.io.File.separator, a, a.length-1);
    }

    public static String filename(String s) {
	final String [] a = s.split("\\"+java.io.File.separator);
	return a[a.length-1];
    }

    public static String stem(String s) {
	final String [] a = s.split("\\.");
	return Strings.join(".", a, a.length-1);
    }

    public static String filenameStem(String s) {
	return stem(filename(s));
    }

    public static String extension(String s) {
	final String [] a = s.split("\\.");
	return a[a.length-1];
    }

    public static void main(final String[] args) {
	String [] a = args;
	if(args.length == 0) {
	    a = new String [] {
		    Strings.join(java.io.File.separator,
			new String[] {"a","b","c","d.foo"})
		};
	}
	for (final String element : a) {
	    System.out.println("in " + element);
	    System.out.println(" dirname " +dirname(element));
	    System.out.println(" filename " +filename(element));
	    System.out.println(" stem " +stem(element));
	    System.out.println(" extension " +extension(element));
	    System.out.println(" stem/file " +filenameStem(element));
	}
    }
};
