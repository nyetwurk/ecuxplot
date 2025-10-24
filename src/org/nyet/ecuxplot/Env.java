package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

public class Env {
    public Preferences prefs;
    public Constants c; // car profile
    public Fueling f;   // car profile
    public PID pid;
    public SAE sae;
    public Env (Preferences prefs) {
        this.prefs = prefs;
        this.f = new Fueling(prefs);
        this.c = new Constants(prefs);
        this.pid = new PID(prefs);
        this.sae = new SAE(prefs);
    }
}

// vim: set sw=4 ts=8 expandtab:
