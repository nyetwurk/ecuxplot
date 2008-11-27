package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

public class Fueling {
    public static final String PREFS_TAG = "fueling";

    private static final double defaultMAF = 73;
    private static final double defaultInjector = 320;	// in cc/min
    private static final double defaultMAF_offset = 6;
    private static final int defaultCylinders = 6;
    private static final int defaultTurbos = 2;
    private Preferences prefs;

    public Fueling(Preferences prefs) {
	this.prefs = prefs.node(PREFS_TAG);
    }

    public double MAF() {
	return prefs.getDouble("MAF", defaultMAF);
    }
    public void MAF(double val) {
	prefs.putDouble("MAF", val);
    }
    public double MAF_correction() {
	double maf = this.MAF();
	return maf*maf/(73*73);
    }

    public double injector() {
	return prefs.getDouble("injector", defaultInjector);
    }
    public void injector(double val) {
	prefs.putDouble("injector", val);
    }
    public double MAF_offset() {
	return prefs.getDouble("MAF_offset", defaultMAF_offset);
    }
    public void MAF_offset(double val) {
	prefs.putDouble("MAF_offset", val);
    }
    public int cylinders() {
	return prefs.getInt("cylinders", defaultCylinders);
    }
    public void cylinders(int val) {
	prefs.putInt("cylinders", val);
    }
    public int turbos() {
	return prefs.getInt("turbos", defaultTurbos);
    }
    public void turbos(int val) {
	prefs.putInt("turbos", val);
    }
}
