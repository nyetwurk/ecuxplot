package org.nyet.ecuxplot;

public final class Units {
    public final static String find(String id) {
	if (id == null) return null;
	final String[][] legend = {
	    {"AcceleratorPedalPosition", "%"},
	    {"AirFuelRatioDesired", "lambda"},
	    {"EGTbank1", "\u00B0 F"},
	    {"EGTbank1OXS", "\u00B0 C"},
	    {"EGTbank2", "\u00B0 F"},
	    {"EGTbank2OXS", "\u00B0 C"},
	    {"FuelInjectorOnTime", "ms"},
	    {"FuelInjectorDutyCycle", "%"},
	    {"IntakeAirTemperature", "\u00B0 F"},
	    {"MassAirFlow", "g/sec"},
	    {"ThrottlePlateAngle", "%"},
	    {"VehicleSpeed", "kph"},
	    {"TPS", "%"},

	    {"RPM", "1/min"},
	    {"Time", "s"},

	    {"Zeitronix TPS", "%"},
	    {"Zeitronix AFR", "AFR"},
	    {"Zeitronix Lambda", "lambda"},
	    {"Zeitronix Time", "s"},

	    {"KnockVolt.*", "V"},
	    {"OXSVolt.*", "V"},

	    {".*BoostPressure.*", "mBar"},
	    {".*DutyCycle.*", "%"},
	    {".*IgnitionRetard.*", "\u00B0"},
	    {".*IgnitionTiming.*", "\u00B0"},
	    {".*Load.*", "%"},
	    {".*Pressure.*", "mBar"},
	    {".*Voltage.*", "V"},
	};

	for(int i=0;i<legend.length;i++) {
	    if(id.matches(legend[i][0])) return legend[i][1];
	}
	return "";
    }
}
