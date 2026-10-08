package org.nyet.logfile;

import java.util.ArrayList;

public class CSVRow extends ArrayList<String> {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /** Quote one CSV field, doubling embedded quotes. */
    public static String quote(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String toString() {
        final String[] q = new String[size()];
        for (int i = 0; i < q.length; i++) q[i] = quote(get(i));
        return String.join(",", q);
    }

    public CSVRow() { super(); }
    public CSVRow(Object[] data) {
        for (final Object element : data) {
            add(element.toString());
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

// vim: set sw=4 ts=8 expandtab:
