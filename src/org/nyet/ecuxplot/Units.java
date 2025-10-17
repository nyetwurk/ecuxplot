package org.nyet.ecuxplot;

import java.util.Arrays;

public final class Units {
    public final static String find(String id) {
	if (id == null) return null;
	final String[][] legend = {
	    {"AcceleratorPedalPosition", "%"},
	    {"AirFuelRatioDesired", "lambda"},
	    {"EGTbank1", "\u00B0F"},
	    {"EGTbank1OXS", "\u00B0C"},
	    {"EGTbank2", "\u00B0F"},
	    {"EGTbank2OXS", "\u00B0C"},
	    {"FuelInjectorOnTime", "ms"},
	    {"FuelInjectorDutyCycle", "%"},
	    {"IntakeAirTemperature", "\u00B0C"}, // If not otherwise specified, assume C
	    {"MassAirFlow", "g/sec"},
	    {"ThrottlePlateAngle", "%"},
	    {"VehicleSpeed", "kph"},
	    {"TPS", "%"},

	    {"RPM", "RPM"},
	    {"TIME", "s"},
	    {"Time", "s"},

	    {"Zeitronix TPS", "%"},
	    {"Zeitronix Time", "s"},

	    {"KnockVolt.*", "V"},
	    {"OXSVolt.*", "V"},

	    {".*BoostPressure.*", "mBar"},
	    {".*DutyCycle.*", "%"},
	    {".*Angle.*", "\u00B0"},
	    {".*Ignition.*Retard.*", "\u00B0"},
	    {".*Ignition.*Timing.*", "\u00B0"},
	    {".*Load.*", "%"},
	    {".*Pressure.*", "mBar"},
	    {".*Voltage.*", "V"},
	    {".*AFR.*", "AFR"},
	    {".*AirFuelRatio.*", "AFR"},
	    {".*[Ll]ambda.*", "lambda"},
	};

	for (final String[] element : legend) {
	    if(id.matches(element[0])) return element[1];
	}
	return "";
    }

    public final static String normalize(String u) {
	if(u==null) return "";
	final String[][] map = {
	    {"^1/min$", "RPM"},
	    {"\u00B0KW","\u00B0"},
	    {"^DK$", "\u00B0"},
	    {"^[Dd]egrees$", "\u00B0"},
	    {"^PED$", "\u00B0"},
	    {"^C$", "\u00B0C"},
	    {"^F$", "\u00B0F"},
	    {"^mbar$", "mBar"},
	    {"^psi$", "PSI"},
	    {"^PSI/.*", "PSI"},
	    {"^rpm", "RPM"},
	    {"^-$", ""}
	};
	for (final String[] element : map) {
	    if(u.trim().matches(element[0])) return element[1];
	}
	return u.trim();
     }

     public final static String[] processUnits(String h[], String u[], int verbose) {
	// Fill pad out any units missing to match length of h
	if(u.length<h.length) {
	    u = Arrays.copyOf(u, h.length);
	}

	for(int i=0;i<h.length;i++) {
	    // Convert to conventional units
	    u[i]=Units.normalize(u[i]);
	    if(h[i].length()>0 && (u[i]==null || u[i].length()==0)) {
		// Whatever is missing, try to guess from name
		u[i]=Units.find(h[i]);
		if (verbose>0 && (u[i]==null || u[i].length()==0))
		    System.out.printf("Can't find units for '%s'\n", h[i]);
	    }
	}
	return u;
    }
}
