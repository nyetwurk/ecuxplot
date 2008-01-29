package org.nyet.logfile;

public class Units {
    private static final String[][] legend = {
	{"AcceleratorPedalPosition", "%"},
	{"AirFuelRatioDesired", "AFR"},
	{"BaroPressure", "mBar"},
	{"BatteryVoltage", "V"},
	{"BoostFrequencyValveDutyCycle", "%"},
	{"BoostPressureActual", "mBar"},
	{"BoostPressureDesired", "mBar"},
	{"EGTbank1", "degrees C"},
	{"EGTbank1OXS", "degrees C"},
	{"EGTbank2", "degrees C"},
	{"EGTbank2OXS", "degrees C"},
	{"EngineLoad", "%"},
	{"EngineLoadCorrectedSpecified", "%"},
	{"EngineLoadDesired", "%"},
	{"EngineLoadSpecified", "%"},
	{"FuelInjectorOnTime", "ms"},
	{"FuelInjectorDutyCycle", "%"},
	{"IgnitionRetardCyl1", "degrees"},
	{"IgnitionRetardCyl2", "degrees"},
	{"IgnitionRetardCyl3", "degrees"},
	{"IgnitionRetardCyl4", "degrees"},
	{"IgnitionRetardCyl5", "degrees"},
	{"IgnitionRetardCyl6", "degrees"},
	{"IgnitionRetardAvg", "degrees"},
	{"IgnitionTimingCyl1", "degrees"},
	{"IgnitionTimingCyl2", "degrees"},
	{"IgnitionTimingCyl3", "degrees"},
	{"IgnitionTimingCyl4", "degrees"},
	{"IgnitionTimingCyl5", "degrees"},
	{"IgnitionTimingCyl6", "degrees"},
	{"IgnitionTimingAngleOverall", "degrees"},
	{"IntakeAirTemperature", "degrees F"},
	{"KnockVoltCyl1", "V"},
	{"KnockVoltCyl2", "V"},
	{"KnockVoltCyl3", "V"},
	{"KnockVoltCyl4", "V"},
	{"KnockVoltCyl5", "V"},
	{"KnockVoltCyl6", "V"},
	{"MassAirFlow", "g/sec"},
	{"OXSVoltS1B1", "V"},
	{"OXSVoltS1B2", "V"},
	{"ThrottlePlateAngle", "%"},
	{"Time", "s"},
	{"VehicleSpeed", "mph"},
    };
    public static String find(Comparable id) {
	for(int i=0;i<legend.length;i++) {
	    if(legend[i][0].equals(id)) return legend[i][1];
	}
	return id.toString();
    }
}
