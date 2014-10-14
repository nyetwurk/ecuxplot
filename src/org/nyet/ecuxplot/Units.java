package org.nyet.ecuxplot;

public final class Units {
    public final static String find(String id) {
	if (id == null) return null;
	final String[][] legend = {
	    {"AcceleratorPedalPosition", "%"},
	    {"AirFuelRatioDesired", "lambda"},
	    {"EGTbank1", "degrees F"},
	    {"EGTbank1OXS", "degrees C"},
	    {"EGTbank2", "degrees F"},
	    {"EGTbank2OXS", "degrees C"},
	    {"FuelInjectorOnTime", "ms"},
	    {"FuelInjectorDutyCycle", "%"},
	    {"IntakeAirTemperature", "degrees F"},
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
	    {".*IgnitionRetard.*", "degrees"},
	    {".*IgnitionTiming.*", "degrees"},
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
