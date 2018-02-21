package org.nyet.ecuxplot;

import org.nyet.util.Files;

public class Loggers {

    public static enum LoggerType {
	LOG_UNKNOWN,
	LOG_ERR,
	LOG_DETECT,
	LOG_ECUX,
	LOG_VCDS,
	LOG_ZEITRONIX,
	LOG_ME7LOGGER,
	LOG_EVOSCAN,
	LOG_VOLVOLOGGER,
	LOG_LOGWORKS,
	LOG_JB4
    }

    public static final String[] pedalnames = new String []
	{"AcceleratorPedalPosition", "AccelPedalPosition", "Zeitronix TPS",
	 "Accelerator position", "Pedal Position"};
    public static final String[] throttlenames = new String []
	{"ThrottlePlateAngle", "Throttle Angle", "Throttle Valve Angle",
	 "TPS"};
    public static final String[] gearnames = new String []
	{"Gear", "SelectedGear", "Engaged Gear"};

    private static final String[][] VCDS_aliases = new String[][] {
	{"^Zeit$", "TIME"},
	{"^Boost Pressure \\(actual\\)$", "BoostPressureActual"},
	{"^Boost Pressure \\(specified\\)$", "BoostPressureDesired"},
	// remap engine rpm/speedand idle speed to "RPM'
	{"^(Engine RPM|Engine [Ss]peed|Motordrehzahl).*", "RPM"},
	{"^Idle (RPM|[Ss]peed).*", "RPM"},
	// ignore weird letter case for throttle angle
	{"^Throttle [Aa]ngle.*", "ThrottleAngle"},
	{"^Throttle [Vv]alve [Aa]ngle.*", "ThrottleAngle"},
	// ignore weird spacing for MAF
	{"^Mass [Aa]ir [Ff]low.*", "MassAirFlow"},
	{"^Mass [Aa]ir [Tt]aken [Ii]n.*", "MassAirFlow"},
	{"^Mass Flow$", "MassAirFlow"},
	{"^Ign timing.*", "IgnitionTimingAngle"}
    };

    private static final String[][] Zeitronix_aliases = new String[][] {
	{".*RPM$", "RPM"},
	{".*Boost$", "Boost"},
	{".*TPS$", "TPS"},
	{".*AFR$", "AFR"},
	{".*Lambda$", "Lambda"},
	{".*EGT$", "EGT"}
    };

    private static final String[][] ECUX_aliases = new String[][] {
	{"^BstActual$", "BoostPressureActual"},
	{"^BstDesired$", "BoostPressureDesired"}
    };

    private static final String[][] EVOSCAN_aliases = new String[][] {
	{".*RPM$", "RPM"},
	{"^LogEntrySeconds$", "TIME"},
	{"^TPS$", "ThrottlePlateAngle"},
	{"^APP$", "AccelPedalPosition"},
	{"^IAT$", "IntakeAirTemperature"}
    };

    private static final String[][] ME7L_aliases = new String[][] {
	{"^Engine[Ss]peed.*", "RPM"},
	{"^BoostPressureSpecified$", "BoostPressureDesired"},
	{"^EngineLoadCorrectedSpecified$", "EngineLoadCorrected"},
	{"^AtmosphericPressure$", "BaroPressure"},
	{"^AirFuelRatioRequired$", "AirFuelRatioDesired"},
	{"^InjectionTime$", "EffInjectionTime"},	// is this te or ti? Assume te?
	{"^InjectionTimeBank2$", "EffInjectionTimeBank2"}	// is this te or ti? Assume te?
    };

    private static final String[][] VOLVOLOGGER_aliases = new String[][] {
	{"^Time$", "TIME"},
	{"^Engine [Ss]peed.*", "RPM"},
	{"^(Actual )?Boost Pressure$", "BoostPressureActual"},
	{"^Desired Boost Pressure$", "BoostPressureDesired"},
	{"^Mass Air Flow$", "MAF"}
    };

    private static final String[][] LOGWORKS_aliases = new String[][] {
	{"^time$", "TIME"},
	{"^Boost$", "BoostPressureActual"},
	{"^LC1_O2WB$", "AFR"},
    };

    private static final String[][] JB4_aliases = new String[][] {
	{"^timestamp$", "TIME"},
	{"^rpm$", "RPM"},
	{"^pedal$", "Pedal (%)"},
	{"^mph$", "VehicleSpeed (mph)"},
	{"^throttle$", "ThrottlePlateAngle (%)"},
	{"^ecu_psi$", "ECUBoostPressureActual (PSI)"},
	{"^dme_bt$", "ECUBoostPressureDesired (PSI)"},
	{"^target$", "BoostPressureDesiredDelta (PSI)"},
	{"^boost$", "BoostPressureActual (PSI)"},
	{"^boost2$", "BoostPressureActual2 (PSI)"},
	{"^ff$", "BoostFeedForward"},
	{"^map$", "SelectedMap"},
	{"^wgdc$", "WastegateDutyCycle (%)"},
	{"^iat$", "IntakeAirTemperature (\u00B0 F)"},
	{"^fp_h$", "FuelPressureHigh (PSI)"},
	{"^fp_l$", "FuelPressureLow (PSI)"},
	{"^waterf$", "WaterTemperature (\u00B0 F)"},
	{"^oilf$", "OilTemperature (\u00B0 F)"},
	{"^transf$", "TransmissionTemperature (\u00B0 F)"},
	{"^gear$", "Gear"},
	{"^load$", "EngineLoad (%)"},
	{"^calc_torque$", "CalculatedTorque"},
	{"^afr$", "AirFuelRatio (AFR)"},
	{"^afr2$", "AirFuelRatio2 (AFR)"},
	{"^trims$", "FuelTrim (%)"},
	{"^trims2$", "FuelTrim2 (%)"},
	{"^fuelen$", "FuelEnrichment (%)"},
	{"^meth$", "MethanolFlow (%)"},
	{"^e85$", "E85"},
	{"^avg_ign$", "AverageIgnitionRetard"},
	{"^ign_1$", "IgnitionTimingAngle1"},
	{"^ign_2$", "IgnitionTimingAngle2"},
	{"^ign_3$", "IgnitionTimingAngle3"},
	{"^ign_4$", "IgnitionTimingAngle4"},
	{"^ign_5$", "IgnitionTimingAngle5"},
	{"^ign_6$", "IgnitionTimingAngle6"},
	{"^ign_7$", "IgnitionTimingAngle7"},
	{"^ign_6$", "IgnitionTimingAngle8"}
    };

    private static final String[][] DEFAULT_aliases = new String[][] {
	{"^[Tt]ime$", "TIME"},
	{"^[Ee]ngine [Ss]peed.*", "RPM"},
	{"^[Mm]ass air flow$", "MassAirFlow"}
    };

    private static String[][] which(LoggerType logger) {
	switch(logger) {
	    case LOG_ECUX: return ECUX_aliases;
	    case LOG_VCDS: return VCDS_aliases;
	    case LOG_ZEITRONIX: return Zeitronix_aliases;
	    case LOG_ME7LOGGER: return ME7L_aliases;
	    case LOG_EVOSCAN: return EVOSCAN_aliases;
	    case LOG_VOLVOLOGGER: return VOLVOLOGGER_aliases;
	    case LOG_LOGWORKS: return LOGWORKS_aliases;
	    case LOG_JB4: return JB4_aliases;
	    default: return DEFAULT_aliases;
	}
    }

    public static void processAliases(String[] h, LoggerType logger) {
	processAliases(h, which(logger));
    }

    public static void processAliases(String[] h) {
	processAliases(h, DEFAULT_aliases);
    }

    public static void processAliases(String[] h, String[][] a) {
	for(int i=0;i<h.length;i++) {
	    h[i]=h[i].trim();
	    // System.out.printf("%d: '%s'\n", i, h[i]);
	    for (final String [] s: a) {
		if (h[i].matches(s[0])) h[i]=s[1];
	    }
	}
    }

    public static LoggerType detect(String[] h) {
	h[0]=h[0].trim();
	if(h[0].matches("VCDS")) return LoggerType.LOG_VCDS;
	if(h[0].matches("^.*(day|tag)$")) return LoggerType.LOG_VCDS;
	if(h[0].matches("^Filename:.*")) {
	    if(Files.extension(h[0]).equals("zto") ||
	       Files.extension(h[0]).equals("zdl") ||
		h[0].matches(".*<unnamed file>$"))
	    return LoggerType.LOG_ZEITRONIX;
	}
	if(h[0].matches("^TIME$")) return LoggerType.LOG_ECUX;

	if(h[0].matches(".*ME7-Logger.*")) return LoggerType.LOG_ME7LOGGER;

	if(h[0].matches("^LogID$")) return LoggerType.LOG_EVOSCAN;

	if(h[0].matches("^Time\\s*\\(sec\\)$")) return LoggerType.LOG_VOLVOLOGGER;

	if(h[0].matches("^Session: Session [0-8]+$")) return LoggerType.LOG_LOGWORKS;

	if(h[0].matches("^Firmware$")) return LoggerType.LOG_JB4;

	return LoggerType.LOG_UNKNOWN;
    }
}
