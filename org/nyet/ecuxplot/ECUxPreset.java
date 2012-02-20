package org.nyet.ecuxplot;

import java.util.TreeMap;
import java.util.prefs.Preferences;

import org.nyet.util.Strings;

public class ECUxPreset extends Preset {
    public static Preferences getPreferencesStatic() {
        return Preferences.userNodeForPackage(ECUxPlot.class).node("presets");
    }

    public Preferences getPreferences() {
        return getPreferencesStatic();
    }

    public static void createDefaultECUxPresets() {
	new ECUxPreset("Power", "RPM",
	    new String[] { "Calc WHP","Calc WTQ","Calc HP","Calc TQ" },
	    new String[] {"BoostPressureDesired (PSI)","BoostPressureActual (PSI)"});

	new ECUxPreset("Timing", "RPM",
	    new String[] { "EngineLoad" },
	    new String[] { "IgnitionTimingAngleOverall", "IgnitionTimingAngleOverallDesired"},
	    true);

	new ECUxPreset("Fueling", "RPM",
	    new String[] { "Zeitronix AFR", "Calc AFR" },
	    new String[] { "Zeitronix Boost (PSI)" ,
		"BoostPressureDesired (PSI)" , "BoostPressureActual (PSI)"});

	new ECUxPreset("Compressor Map", "Calc Turbo Flow", "Calc BoostActual PR");

	new ECUxPreset("Spool Rate", "BoostPressureActual (PSI)",
	    "Calc Boost Spool Rate (RPM)",
	    "Calc Boost Spool Rate (time)");
    }

    public ECUxPreset(Comparable name) { super(name);}
    public ECUxPreset(Comparable name, Comparable xkey, Comparable [] ykeys) {
	this(name, xkey, ykeys, new Comparable [] {});
    }
    public ECUxPreset(Comparable name, Comparable xkey, Comparable ykey) {
	this(name, xkey, new Comparable [] {ykey}, new Comparable [] {});
    }
    public ECUxPreset(Comparable name, Comparable xkey, Comparable ykey,
	Comparable ykey2) {
	this(name, xkey, new Comparable [] {ykey}, new Comparable [] {ykey2});
    }
    public ECUxPreset(Comparable name, Comparable xkey, Comparable [] ykeys,
	Comparable [] ykeys2) {
	this(name, xkey, ykeys, ykeys2, false);
    }
    public ECUxPreset(Comparable name, Comparable xkey, Comparable [] ykeys,
	Comparable [] ykeys2, boolean scatter)
    {
	super(name);
	this.xkey(xkey);
	this.ykeys(ykeys);
	this.ykeys2(ykeys2);
	this.scatter(scatter);
    }

    // GETS
    public String tag() { return this.prefs.get("tag", this.prefs.name()); } // for Undo
    public Comparable xkey() { return this.prefs.get("xkey", null); }
    public Comparable[] ykeys() { return this.getArray("ykeys"); }
    public Comparable[] ykeys2() { return this.getArray("ykeys2"); }
    public Boolean scatter() { return this.prefs.getBoolean("scatter", false); }

    // PUTS
    public void tag(String tag) { this.prefs.put("tag", tag); }	// for Undo
    public void xkey(Comparable xkey) { this.prefs.put("xkey", xkey.toString()); }
    public void ykeys(Comparable[] ykeys) { this.putArray("ykeys", ykeys); }
    public void ykeys2(Comparable[] ykeys2) { this.putArray("ykeys2", ykeys2); }
    public void scatter(Boolean scatter) { this.prefs.putBoolean("scatter", scatter); }

    // misc
    public String toString() {
	return this.prefs.name() + ": \"" +
	    this.xkey() + "\" vs \"" +
            Strings.join(", ", this.ykeys()) + "\" and \"" +
            Strings.join(", ", this.ykeys2()) + "\"";
    }
    public static String[] getPresets() {
	String [] ret = null;
	for(int i=0; i<2; i++) {
	    try { ret = getPreferencesStatic().childrenNames();
	    } catch (Exception e) { }
	    if (ret!=null && ret.length>0) return ret;
	    ECUxPreset.createDefaultECUxPresets();
	}
        return new String[0];
    }
}
