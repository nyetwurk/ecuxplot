package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

public class FATS {
    public static final String PREFS_TAG = "FATS";

    private static final int defaultStart = 4200;
    private static final int defaultEnd = 6500;
    private static final boolean defaultUseMph = false;
    private static final double defaultStartMph = 60.0;
    private static final double defaultEndMph = 90.0;

    private final Preferences prefs;

    public FATS(Preferences prefs) {
        this.prefs = prefs.node(PREFS_TAG);
    }

    public int start() {
        return this.prefs.getInt("start", defaultStart);
    }
    public void start(int val) {
        this.prefs.putInt("start", val);
    }
    public int end() {
        return this.prefs.getInt("end", defaultEnd);
    }
    public void end(int val) {
        this.prefs.putInt("end", val);
    }

    public boolean useMph() {
        return this.prefs.getBoolean("use_mph", defaultUseMph);
    }
    public void useMph(boolean val) {
        this.prefs.putBoolean("use_mph", val);
    }

    public double startMph() {
        return this.prefs.getDouble("start_mph", defaultStartMph);
    }
    public void startMph(double val) {
        this.prefs.putDouble("start_mph", val);
    }

    public double endMph() {
        return this.prefs.getDouble("end_mph", defaultEndMph);
    }
    public void endMph(double val) {
        this.prefs.putDouble("end_mph", val);
    }

    // Convert MPH to RPM using rpm_per_mph constant
    public int mphToRpm(double mph, double rpmPerMph) {
        return (int) Math.round(mph * rpmPerMph);
    }

    // Convert RPM to MPH using rpm_per_mph constant
    public double rpmToMph(int rpm, double rpmPerMph) {
        return rpm / rpmPerMph;
    }
}

// vim: set sw=4 ts=8 expandtab:
