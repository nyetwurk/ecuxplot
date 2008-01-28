package org.nyet.logfile;

public class Units {
    private static final String[][] legend = {
	{"Time", "s"},
	{"BaroPressure", "mBar"},
	{"BoostPressureDesired", "mBar"},
	{"BoostPressureActual", "mBar"},
	{"BoostFrequencyValveDutyCycle", "mBar"},
	{"MassAirFlow", "g/sec"},
	{"IntakeAirTemperature", "degrees C"},
	{"AcceleratorPedalPosition", "%"},
	{"ThrottlePlateAngle", "%"},
	{"IgnitionRetardCyl1", "degrees"},
	{"IgnitionRetardCyl2", "degrees"},
	{"IgnitionRetardCyl3", "degrees"},
	{"IgnitionRetardCyl4", "degrees"},
	{"IgnitionRetardCyl5", "degrees"},
	{"IgnitionRetardCyl6", "degrees"},
	{"IgnitionTimingAngleOverall", "degress"},
	{"KnockVoltCyl1", "V"},
	{"KnockVoltCyl2", "V"},
	{"KnockVoltCyl3", "V"},
	{"KnockVoltCyl4", "V"},
	{"KnockVoltCyl5", "V"},
	{"KnockVoltCyl6", "V"},
	{"VehicleSpeed", "mph"},
	{"AirFuelRatioDesired", "AFR"},
	{"FuelInjectorOnTime", "ms"},
	{"EGTbank1", "degrees C"},
	{"EGTbank2", "degrees C"},
	{"EGTbank1OXS", "degrees C"},
	{"EGTbank2OXS", "degrees C"},
	{"OXSVoltS1B1", "V"},
	{"OXSVoltS1B2", "V"},
	{"EngineLoadSpecified", "%"},
	{"EngineLoadCorrectedSpecified", "%"},
	{"EngineLoadDesired", "%"},
	{"EngineLoad", "%"}
    };
    public static String find(Comparable id) {
	for(int i=0;i<legend.length;i++) {
	    if(legend[i][0].equals(id)) return legend[i][1];
	}
	return id.toString();
    }
}
