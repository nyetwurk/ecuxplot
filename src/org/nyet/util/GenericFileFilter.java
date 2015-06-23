package org.nyet.util;
import java.io.File;
import javax.swing.filechooser.FileFilter;

public class GenericFileFilter extends FileFilter implements java.io.FileFilter {
    private final String ext;
    private final String description;
    private final boolean allowDir;

    public GenericFileFilter (String ext, String desc) {
	super();
	this.ext=ext;
	this.description=desc;
	this.allowDir=true;
    }
    public GenericFileFilter (String ext) {
	super();
	this.ext=ext;
	this.description="";
	this.allowDir=false;
    }

    @Override
    public String getDescription() {return this.description;};
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
	    return this.allowDir;
	final String extension = getExtension(f);
	if(extension != null)
	    if(extension.equals(this.ext)) return true;

	return false;
    }
}
