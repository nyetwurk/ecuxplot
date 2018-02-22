package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

import javax.swing.JOptionPane;

import org.nyet.util.Strings;

public class ECUxPreset extends Preset {

    private static boolean detectOldPrefs(Preferences prefs) {
	String[] names = null;
	boolean ret=false;
	try { names = prefs.childrenNames(); }
	catch (final Exception e) { return false; }
	for (final String s : names) {
	    final Preferences n = prefs.node(s);
	    try {
		if (n.nodeExists("ykeys") || n.nodeExists("ykeys2")) {
		    n.removeNode();
		    ret=true;
		}
	    } catch (final Exception e) {}
	}
	if (ret) {
	    createDefaultECUxPresets();
	    JOptionPane.showMessageDialog(null, "Old presets detected. Resetting to default.");
	}
	return ret;
    }

    public static Preferences getPreferencesStatic() {
	final Preferences p=Preferences.userNodeForPackage(ECUxPlot.class).node("presets");
	detectOldPrefs(p);
	return p;
    }

    @Override
    public Preferences getPreferences() {
        return getPreferencesStatic();
    }

    public static void createDefaultECUxPresets() {
	new ECUxPreset("Power", "RPM",
	    new String[] { "WHP","WTQ","HP","TQ" },
	    new String[] {"BoostPressureDesired (PSI)","BoostPressureActual (PSI)"});

	new ECUxPreset("Timing", "RPM",
	    new String[] { "EngineLoad" },
	    new String[] { "IgnitionTimingAngleOverall", "IgnitionTimingAngleOverallDesired"},
	    true);

	new ECUxPreset("Fueling", "RPM",
	    new String[] { "Zeitronix AFR", "Sim AFR" },
	    new String[] { "Zeitronix Boost (PSI)" ,
		"BoostPressureDesired (PSI)" , "BoostPressureActual (PSI)"});

	new ECUxPreset("Compressor Map", "Turbo Flow", "BoostActual PR");

	new ECUxPreset("Spool Rate", "BoostPressureActual (PSI)",
	    "Boost Spool Rate (RPM)",
	    "Boost Spool Rate (time)");
    }

    public ECUxPreset(Comparable<?> name) { super(name);}
    public ECUxPreset(Comparable<?> name, Comparable<?> xkey, Comparable<?>[] ykeys) {
	this(name, xkey, ykeys, new Comparable [] {});
    }
    public ECUxPreset(Comparable<?> name, Comparable<?> xkey, Comparable<?> ykey) {
	this(name, xkey, new Comparable [] {ykey}, new Comparable<?>[] {});
    }
    public ECUxPreset(Comparable<?> name, Comparable<?> xkey, Comparable<?> ykey,
	Comparable<?> ykey2) {
	this(name, xkey, new Comparable [] {ykey}, new Comparable<?>[] {ykey2});
    }
    public ECUxPreset(Comparable<?> name, Comparable<?> xkey, Comparable<?>[] ykeys,
	Comparable<?>[] ykeys2) {
	this(name, xkey, ykeys, ykeys2, false);
    }
    public ECUxPreset(Comparable<?> name, Comparable<?> xkey, Comparable<?>[] ykeys,
	Comparable<?>[] ykeys2, boolean scatter)
    {
	super(name);
	this.xkey(xkey);
	this.ykeys(0,ykeys);
	this.ykeys(1,ykeys2);
	this.scatter(scatter);
    }

    // GETS
    public String tag() { return this.prefs.get("tag", this.prefs.name()); } // for Undo
    public Comparable<?> xkey() { return this.prefs.get("xkey", null); }
    public Comparable<?>[] ykeys(int which) { return this.getArray(which==0?"ykeys0":"ykeys1"); }
    public Boolean scatter() { return this.prefs.getBoolean("scatter", false); }

    // PUTS
    public void tag(String tag) { this.prefs.put("tag", tag); }	// for Undo
    public void xkey(Comparable<?> xkey) { this.prefs.put("xkey", xkey.toString()); }
    public void ykeys(int which, Comparable<?>[] ykeys) { this.putArray(which==0?"ykeys0":"ykeys1", ykeys); }
    public void scatter(Boolean scatter) { this.prefs.putBoolean("scatter", scatter); }

    // misc
    @Override
    public String toString() {
	return this.prefs.name() + ": \"" +
	    this.xkey() + "\" vs \"" +
            Strings.join(", ", this.ykeys(0)) + "\" and \"" +
            Strings.join(", ", this.ykeys(1)) + "\"";
    }
    public static String[] getPresets() {
	String [] ret = null;
	for(int i=0; i<2; i++) {
	    try { ret = getPreferencesStatic().childrenNames();
	    } catch (final Exception e) { }
	    if (ret!=null && ret.length>0) return ret;
	    ECUxPreset.createDefaultECUxPresets();
	}
        return new String[0];
    }
}
