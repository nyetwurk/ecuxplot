package org.nyet.ecuxplot;

import org.nyet.util.Strings;

public class Preset {
    private Comparable name;

    public Comparable xkey;
    public Comparable [] ykeys;
    public Comparable [] ykeys2;
    public Boolean scatter;

    public Preset (Comparable name, Comparable xkey, Comparable [] ykeys) {
	this(name, xkey, ykeys, new Comparable [] {});
    }
    public Preset (Comparable name, Comparable xkey, Comparable ykey) {
	this(name, xkey, new Comparable [] {ykey}, new Comparable [] {});
    }
    public Preset (Comparable name, Comparable xkey, Comparable ykey,
	Comparable ykey2) {
	this(name, xkey, new Comparable [] {ykey}, new Comparable [] {ykey2});
    }
    public Preset (Comparable name, Comparable xkey, Comparable [] ykeys,
	Comparable [] ykeys2) {
	this(name, xkey, ykeys, ykeys2, false);
    }
    public Preset (Comparable name, Comparable xkey, Comparable [] ykeys,
	Comparable [] ykeys2, boolean scatter)
    {
	this.name=name;
	this.xkey=xkey;
	this.ykeys=ykeys;
	this.ykeys2=ykeys2;
	this.scatter=scatter;
    }

    public Comparable getName() { return this.name; }
    public void setName(Comparable name) { this.name = name; }

    public String toString() {
	return this.name + ": \"" +
	    this.xkey + "\" vs \"" +
            Strings.join(", ", this.ykeys) + "\" and \"" +
            Strings.join(", ", this.ykeys2) + "\"";
    }
}
