package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

public class Constants {
    public static final String PREFS_TAG = "constants";

    private static final double defaultMass = 1700;
    private static final double defaultRpm_per_mph = 72.1;
    private static final double defaultCd = 0.31;
    private static final double defaultFA = 2.034;
    private static final double defaultRolling_drag = 0.015;
    private static final double defaultStatic_loss=0;
    private static final double defaultDriveline_loss=.25;

    private Preferences prefs;

    public Constants(Preferences prefs) {
	this.prefs = prefs.node(PREFS_TAG);
    }

    public double mass() {
	return this.prefs.getDouble("mass", defaultMass);
    }
    public void mass(Double val) {
	this.prefs.putDouble("mass", val);
    }
    public double rpm_per_mph() {
	return this.prefs.getDouble("rpm_per_mph", defaultRpm_per_mph);
    }
    public void rpm_per_mph(Double val) {
	this.prefs.putDouble("rpm_per_mph", val);
    }
    public double Cd() {
	return this.prefs.getDouble("Cd", defaultCd);
    }
    public void Cd(Double val) {
	this.prefs.putDouble("Cd", val);
    }
    public double FA() {
	return this.prefs.getDouble("FA", defaultFA);
    }
    public void FA(Double val) {
	this.prefs.putDouble("FA", val);
    }
    public double rolling_drag() {
	return this.prefs.getDouble("rolling_drag", defaultRolling_drag);
    }
    public void rolling_drag(Double val) {
	this.prefs.putDouble("rolling_drag", val);
    }
    public double static_loss() {
	return this.prefs.getDouble("static_loss", defaultStatic_loss);
    }
    public void static_loss(Double val) {
	this.prefs.putDouble("static_loss", val);
    }
    public double driveline_loss() {
	return this.prefs.getDouble("driveline_loss", defaultDriveline_loss);
    }
    public void driveline_loss(Double val) {
	this.prefs.putDouble("driveline_loss", val);
    }

    // editor overrides the gettor/settor and passes strings
    // do the % conversion here too.
    public String driveline_loss_string() {
	return "" + driveline_loss()*100;
    }
    public void driveline_loss_string(String val) {
	this.prefs.putDouble("driveline_loss", Double.valueOf(val)/100);
    }

    public Preferences get() {return this.prefs;}
}
