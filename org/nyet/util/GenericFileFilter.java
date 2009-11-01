package org.nyet.util;
import java.io.File;
import javax.swing.filechooser.FileFilter;

public class GenericFileFilter extends FileFilter implements java.io.FileFilter {
    private String ext;
    private String description;
    private boolean allowDir;

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

    public String getDescription() {return this.description;};
    public static String getExtension(File f) {
       String ext = null;
	String s = f.getName();
	int i = s.lastIndexOf('.');

	if (i > 0 &&  i < s.length() - 1) {
	    ext = s.substring(i+1).toLowerCase();
	}
	return ext;
    }
    public boolean accept(File f) {
	if(f.isDirectory())
	    return this.allowDir;
	String extension = getExtension(f);
	if(extension != null)
	    if(extension.equals(this.ext)) return true;

	return false;
    }
}
