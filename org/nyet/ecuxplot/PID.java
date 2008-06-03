package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

public class PID {
    public double time_constant = 1;
    public double P_deadband = 300;	// in mBar
    public double I_limit = 77;

    public double P = 15;
    public double I = 10;
    public double[] D = {.8, 4, 4, 0}; // 0, 300, 500, 700 mBar

    public PID(Preferences prefs) {}
}
