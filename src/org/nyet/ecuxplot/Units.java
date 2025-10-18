package org.nyet.ecuxplot;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Units {
    private static final Logger logger = LoggerFactory.getLogger(Units.class);
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
	u = u.trim();
	// Extract units from parentheses: "(sec)" -> "sec"
	if (u.matches("\\(.+\\)")) {
	    u = u.replaceAll("\\((.+)\\)", "$1");
	}
	final String[][] map = {
	    {"^1/min$", "RPM"},
	    {"^/min$", "RPM"},  // VCDS RPM unit format
	    {"^g/s$", "g/sec"},  // VCDS mass flow unit format
	    {"^°BTDC$", "\u00B0"},  // VCDS ignition timing unit (degrees before top dead center)
	    {"^[^\u00B0]BTDC$", "\u00B0"},  // VCDS corrupted ignition timing unit (corrupted degree + BTDC)
	    {"^\u00FF$", "\u00B0"},  // VCDS corrupted degree symbol (U+00FF)
	    {"^\uFFFD$", "\u00B0"},  // VCDS corrupted degree symbol (U+FFFD - replacement character)
	    {"^[^\u00B0]KW$", "\u00B0"},  // Fix for ME7L degree symbol encoding issue (non-degree char + KW)
	    {"^[^\u00B0]C$", "\u00B0C"},  // Fix for ME7L temperature degree symbol encoding issue (non-degree char + C)
	    {"^DK$", "\u00B0"},
	    {"^°CRK$", "\u00B0"},  // Convert °CRK to ° (crank degrees)
	    {"^°TPS$", "%"},  // Convert °TPS to % (throttle position sensor)
	    //{"^°RFP$", "mBar"},  // SWCOMM oddity? Leave as is.
	    {"^[Dd]egrees$", "\u00B0"},
	    {"^PED$", "\u00B0"},
	    {"^degC$", "\u00B0C"},
	    {"^C$", "\u00B0C"},
	    {"^F$", "\u00B0F"},
	    {"^mbar$", "mBar"},
	    {"^hPa$", "mBar"},  // Convert hPa to mBar (1 hPa = 1 mBar)
	    {"^km/h$", "kph"},  // Convert km/h to kph
	    {"^psi$", "PSI"},
	    {"^PSI/.*", "PSI"},
	    {"^sec\\.ms$", "s"},  // ME7L time unit normalization
	    {"^sec$", "s"},       // VOLVO time unit normalization
	    {"^rpm", "RPM"},
	    {"^(-|Marker|Markierung|STAMP)$", ""} // Random junk we don't need
	};
	for (final String[] element : map) {
	    if(u.matches(element[0])) return element[1];
	}
	return u;
     }

     public final static String[] processUnits(String h[], String u[]) {
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
		if (u[i]==null || u[i].length()==0)
		    logger.warn("Can't find units for '{}'", h[i]);
	    }
	}
	return u;
    }
}
