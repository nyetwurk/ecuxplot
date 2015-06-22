package org.nyet.ecuxplot;

public class Loggers {
    public static final int LOG_UNKNOWN = -2;
    public static final int LOG_ERR = -1;
    public static final int LOG_DETECT = 0;
    public static final int LOG_ECUX = 1;
    public static final int LOG_VCDS = 2;
    public static final int LOG_ZEITRONIX = 3;
    public static final int LOG_ME7LOGGER = 4;
    public static final int LOG_EVOSCAN = 5;
    public static final int LOG_VOLVOLOGGER = 6;

    public static final String[] pedalnames = new String []
		{"AcceleratorPedalPosition", "AccelPedalPosition", "Zeitronix TPS", "Accelerator position", "Pedal Position"};
    public static final String[] throttlenames = new String []
		{"ThrottlePlateAngle", "Throttle Angle", "Throttle Valve Angle", "TPS"};
    public static final String[] gearnames = new String []
		{"Gear", "SelectedGear", "Engaged Gear"};

    private static final String[][] VCDS_aliases = new String[][] {
	{"^Zeit$", "TIME"},
	// remap engine speed to "RPM'
	{"^(Engine [Ss]peed|Motordrehzahl).*", "RPM"},
	// ignore weird letter case for throttle angle
	{"^Throttle [Aa]ngle.*", "Throttle Angle"},
	// ignore weird spacing for MAF
	{"^Mass [Aa]ir [Ff]low.*", "MassAirFlow"},
	{"^Mass Flow$", "MassAirFlow"},
	{"^Ign timing.*", "Ignition Timing Angle"}
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

    private static final String[][] DEFAULT_aliases = new String[][] {
	{"^Time$", "TIME"},
	{"^Engine [Ss]peed.*", "RPM"},
	{"^Mass air flow$", "MassAirFlow"}
    };

    private static String[][] which(int logger) {
	switch(logger) {
	    case LOG_ECUX: return ECUX_aliases;
	    case LOG_VCDS: return VCDS_aliases;
	    case LOG_ZEITRONIX: return Zeitronix_aliases;
	    case LOG_ME7LOGGER: return ME7L_aliases;
	    case LOG_EVOSCAN: return EVOSCAN_aliases;
	    case LOG_VOLVOLOGGER: return VOLVOLOGGER_aliases;
	    default: return DEFAULT_aliases;
	}
    }

    public static void processAliases(String[] h, int logger) {
	processAliases(h, which(logger));
    }

    public static void processAliases(String[] h) {
	processAliases(h, DEFAULT_aliases);
    }

    public static void processAliases(String[] h, String[][] a) {
	for(int i=0;i<h.length;i++) {
	    h[i]=h[i].trim();
	    for (String [] s: a) {
		if (h[i].matches(s[0])) h[i]=s[1];
	    }
	}
    }
}
