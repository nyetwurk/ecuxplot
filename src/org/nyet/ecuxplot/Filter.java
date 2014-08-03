package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

public class Filter {
    public static final String PREFS_TAG = "filter";

    private static final boolean defaultEnabled = true;
    private static final boolean defaultShowAllRanges = true;
    private static final boolean defaultMonotonicRPM = true;
    private static final int defaultMonotonicRPMfuzz = 100;
    private static final int defaultMinRPM = 2000;
    private static final int defaultMaxRPM = 8000;
    private static final int defaultMinRPMRange = 1200;
    private static final int defaultMinPedal = 95;
    private static final int defaultMinThrottle = 50;
    private static final int defaultGear = -1;
    private static final int defaultMinPoints = 5;
    private static final int defaultHPTQMAW = 5; // hp/tq moving average window
    private static final int defaultZeitMAW = 30; // zeitronix MAW

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

    public int currentRange = 0;

    public static boolean showAllRanges(Preferences prefs) {
    return prefs.node(PREFS_TAG).getBoolean("showAllRanges", defaultShowAllRanges);
    }

    public boolean showAllRanges() {
    return this.prefs.getBoolean("showAllRanges", defaultShowAllRanges);
    }
    public void showAllRanges(boolean val) {
    this.prefs.putBoolean("showAllRanges", val);
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
    public void monotonicRPMfuzz(Integer val) {
	this.prefs.putInt("monotonicRPMfuzz", val);
    }

    public int minRPM() {
	return this.prefs.getInt("minRPM", defaultMinRPM);
    }
    public void minRPM(Integer val) {
	this.prefs.putInt("minRPM", val);
    }

    public int maxRPM() {
	return this.prefs.getInt("maxRPM", defaultMaxRPM);
    }
    public void maxRPM(Integer val) {
	this.prefs.putInt("maxRPM", val);
    }

    public int minRPMRange() {
	return this.prefs.getInt("minRPMRange", defaultMinRPMRange);
    }
    public void minRPMRange(Integer val) {
	this.prefs.putInt("minRPMRange", val);
    }
    public int minPedal() {
	return this.prefs.getInt("minPedal", defaultMinPedal);
    }
    public void minPedal(Integer val) {
	this.prefs.putInt("minPedal", val);
    }

    public int minThrottle() {
	return this.prefs.getInt("minThrottle", defaultMinThrottle);
    }
    public void minThrottle(Integer val) {
	this.prefs.putInt("minThrottle", val);
    }

    public int gear() {
	return this.prefs.getInt("gear", defaultGear);
    }
    public void gear(Integer val) {
	this.prefs.putInt("gear", val);
    }

    public int minPoints() {
	return this.prefs.getInt("minPoints", defaultMinPoints);
    }
    public void minPoints(Integer val) {
	this.prefs.putInt("minPoints", val);
    }

    public int HPTQMAW() {
	return this.prefs.getInt("HPTQMAW", defaultHPTQMAW);
    }
    public void HPTQMAW(Integer val) {
	this.prefs.putInt("HPTQMAW", val);
    }

    public int ZeitMAW() {
	return this.prefs.getInt("ZeitMAW", defaultZeitMAW);
    }
    public void ZeitMAW(Integer val) {
	this.prefs.putInt("ZeitMAW", val);
    }
}
