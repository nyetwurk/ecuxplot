package org.nyet.ecuxplot;

import java.util.TreeMap;

public class ECUxPresets extends TreeMap<Comparable, Preset> {
    public ECUxPresets() {
	this.put(
	    new Preset("Power", "RPM",
		new String[] { "Calc WHP","Calc WTQ","Calc HP","Calc TQ" },
		new String[] {"BoostPressureDesired (PSI)","BoostPressureActual (PSI)"})
	);

	this.put(
	    new Preset("Timing", "RPM",
		new String[] { "EngineLoad" },
		new String[] { "IgnitionTimingAngleOverall", "IgnitionTimingAngleOverallDesired"},
		true)
	);

	this.put(
	    new Preset("Fueling", "RPM",
		new String[] { "Zeitronix AFR", "Calc AFR" },
		new String[] { "Zeitronix Boost (PSI)" ,
		    "BoostPressureDesired (PSI)" , "BoostPressureActual (PSI)"})
	);

	this.put(
	    new Preset("Compressor Map", "Calc Turbo Flow", "Calc BoostActual PR")
	);

	this.put(
	    new Preset("Spool Rate", "BoostPressureActual (PSI)",
		"Calc Boost Spool Rate (RPM)",
		"Calc Boost Spool Rate (time)")
	);
    }

    public void put(Preset p) { this.put(p.getName(), p); }
}
