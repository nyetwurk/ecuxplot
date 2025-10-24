package org.nyet.logfile;
import java.io.File;
import javax.swing.filechooser.FileFilter;

public class CSVFileFilter extends FileFilter {
    @Override
    public String getDescription() {return "CSV Files";};
    public static String getExtension(File f) {
       String ext = null;
        final String s = f.getName();
        final int i = s.lastIndexOf('.');

        if (i > 0 &&  i < s.length() - 1) {
            ext = s.substring(i+1).toLowerCase();
        }
        return ext;
    }
    @Override
    public boolean accept(File f) {
        if(f.isDirectory())
            return true;
        final String extension = getExtension(f);
        if(extension != null)
            if(extension.equals("csv")) return true;

        return false;
    }
}

// vim: set sw=4 ts=8 expandtab:
