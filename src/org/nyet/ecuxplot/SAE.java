package org.nyet.ecuxplot;

import java.lang.Math;
import java.util.prefs.Preferences;

public class SAE {
    public static final String PREFS_TAG = "SAE";

    private static final boolean defaultEnabled = false;
    private static final double defaultTemperature = 25;
    private static final double defaultAltitude = 196;
    private static final double defaultHumidity = 0;

    private Preferences prefs;

    public SAE(Preferences prefs) {
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

    public double temperature() {
	return this.prefs.getDouble("temperature", defaultTemperature);
    }
    public void temperature(double val) {
	this.prefs.putDouble("temperature", val);
    }
    public double altitude() {
	return this.prefs.getDouble("altitude", defaultAltitude);
    }
    public void altitude(double val) {
	this.prefs.putDouble("altitude", val);
    }
    public double humidity() {
	return this.prefs.getDouble("humidity", defaultHumidity);
    }
    public void humidity(double val) {
	this.prefs.putDouble("humidity", val);
    }

    private double vaporpressure() {
	return 6.1078 * Math.pow(10,
	    ((7.5*this.temperature())/(237.3+this.temperature())));
    }

    private double drypressure() {
	final double p0 = 1013.25;
	final double T0 = 288.15;
	final double g = 9.80665;
	final double L = 0.0065;
	final double R = 8.31432;
	final double M = 0.0289644;
	return p0 * Math.pow(1-L*this.altitude()/T0,g*M/(R*L));
    }

    public double correction() {
	double Pv = this.humidity()/100.0 * vaporpressure();
	double Pd = drypressure()-Pv;
	return 1.180 * ( (990/Pd) *
			 Math.pow((this.temperature()+273)/298,.5)
			) - 0.18;
    }
}
