package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

public class Filter {
    public static final String PREFS_TAG = "filter";

    private static final boolean defaultEnabled = true;
    private static final boolean defaultMonotonicRPM = true;
    private static final int defaultMonotonicRPMfuzz = 100;
    private static final int defaultMinRPM = 2500;
    private static final int defaultMaxRPM = 8000;
    private static final int defaultMinRPMRange = 3000;
    private static final int defaultMinPedal = 95;
    private static final int defaultMinThrottle = 50;
    private static final int defaultGear = 3;
    private static final int defaultMinPoints = 30;
    private static final int defaultHPTQMAW = 5; // hp/tq moving average window

    private Preferences prefs;

    public Filter (Preferences prefs) {
	this.prefs = prefs.node(PREFS_TAG);
    }

    public static boolean enabled(Preferences prefs) {
	return prefs.node(PREFS_TAG).getBoolean("enabled", defaultEnabled);
    }

    public boolean enabled() {
	return this.prefs.getBoolean("enabled", defaultEnabled);
    }
    public void enabled(boolean val) {
	this.prefs.putBoolean("enabled", val);
    }

    public boolean monotonicRPM() {
	return this.prefs.getBoolean("monotonicRPM", defaultMonotonicRPM);
    }
    public void monotonicRPM(boolean val) {
	this.prefs.putBoolean("monotonicRPM", val);
    }

    public int monotonicRPMfuzz() {
	return this.prefs.getInt("monotonicRPMfuzz", defaultMonotonicRPMfuzz);
    }
    public void monotonicRPMfuzz(int val) {
	this.prefs.putInt("monotonicRPMfuzz", val);
    }

    public int minRPM() {
	return this.prefs.getInt("minRPM", defaultMinRPM);
    }
    public void minRPM(int val) {
	this.prefs.putInt("minRPM", val);
    }

    public int maxRPM() {
	return this.prefs.getInt("maxRPM", defaultMaxRPM);
    }
    public void maxRPM(int val) {
	this.prefs.putInt("maxRPM", val);
    }

    public int minRPMRange() {
	return this.prefs.getInt("minRPMRange", defaultMinRPMRange);
    }
    public void minRPMRange(int val) {
	this.prefs.putInt("minRPMRange", val);
    }
    public int minPedal() {
	return this.prefs.getInt("minPedal", defaultMinPedal);
    }
    public void minPedal(int val) {
	this.prefs.putInt("minPedal", val);
    }

    public int minThrottle() {
	return this.prefs.getInt("minThrottle", defaultMinThrottle);
    }
    public void minThrottle(int val) {
	this.prefs.putInt("minThrottle", val);
    }

    public int gear() {
	return this.prefs.getInt("gear", defaultGear);
    }
    public void gear(int val) {
	this.prefs.putInt("gear", val);
    }

    public int minPoints() {
	return this.prefs.getInt("minPoints", defaultMinPoints);
    }
    public void minPoints(int val) {
	this.prefs.putInt("minPoints", val);
    }

    public int HPTQMAW() {
	return this.prefs.getInt("HPTQMAW", defaultHPTQMAW);
    }
    public void HPTQMAW(int val) {
	this.prefs.putInt("HPTQMAW", val);
    }
}
