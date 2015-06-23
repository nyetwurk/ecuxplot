package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

public class Fueling {
    public static final String PREFS_TAG = "fueling";

    private static final double defaultMAF = 73;
    private static final double defaultInjector = 349;	// in cc/min
    private static final double defaultMAF_offset = 6;
    private static final int defaultCylinders = 6;
    private static final int defaultTurbos = 2;
    private final Preferences prefs;

    public Fueling(Preferences prefs) {
	this.prefs = prefs.node(PREFS_TAG);
    }

    public double MAF() {
	return this.prefs.getDouble("MAF", defaultMAF);
    }
    public void MAF(double val) {
	this.prefs.putDouble("MAF", val);
    }
    public double MAF_correction() {
	final double maf = this.MAF();
	return maf*maf/(73*73);
    }

    public double injector() {
	return this.prefs.getDouble("injector", defaultInjector);
    }
    public void injector(double val) {
	this.prefs.putDouble("injector", val);
    }
    public double MAF_offset() {
	return this.prefs.getDouble("MAF_offset", defaultMAF_offset);
    }
    public void MAF_offset(double val) {
	this.prefs.putDouble("MAF_offset", val);
    }
    public int cylinders() {
	return this.prefs.getInt("cylinders", defaultCylinders);
    }
    public void cylinders(int val) {
	this.prefs.putInt("cylinders", val);
    }
    public int turbos() {
	return this.prefs.getInt("turbos", defaultTurbos);
    }
    public void turbos(int val) {
	this.prefs.putInt("turbos", val);
    }
    public Preferences get() {return this.prefs;}
}
