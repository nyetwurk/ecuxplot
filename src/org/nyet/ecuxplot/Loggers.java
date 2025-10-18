package org.nyet.ecuxplot;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;

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
	LOG_JB4,
	LOG_COBB_AP,
	LOG_SWCOMM
    }

    public static class DetectResult {
	LoggerType type;
	String message;
	public DetectResult(LoggerType type, String message) {
		this.type = type;
		this.message = message;
	}
	public DetectResult(LoggerType type) {
		this.type = type;
		this.message = "";
	}
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
	{"^Idle (RPM|[Ss]peed).*", "Idle RPM"},
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
	{"^Engine[Ss]peed$", "RPM"},
	{"^BoostPressureSpecified$", "BoostPressureDesired"},
	{"^EngineLoadCorrectedSpecified$", "EngineLoadCorrected"},
	{"^AtmosphericPressure$", "BaroPressure"},
	{"^AirFuelRatioRequired$", "AirFuelRatioDesired"},
	{"^InjectionTime$", "EffInjectionTime"},	// is this te or ti? Assume te?
	{"^InjectionTimeBank2$", "EffInjectionTimeBank2"}	// is this te or ti? Assume te?
    };

    private static final String[][] VOLVOLOGGER_aliases = new String[][] {
	{"^Time$", "TIME"},
	{"^Engine [Ss]peed$", "RPM"},
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
	{"^pedal$", "AccelPedalPosition (%)"},
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
	{"^iat$", "IntakeAirTemperature (\u00B0F)"},
	{"^fp_h$", "FuelPressureHigh (PSI)"},
	{"^fp_l$", "FuelPressureLow (PSI)"},
	{"^waterf$", "WaterTemperature (\u00B0F)"},
	{"^oilf$", "OilTemperature (\u00B0F)"},
	{"^transf$", "TransmissionTemperature (\u00B0F)"},
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

    private static final String[][] COBB_AP_aliases = new String[][] {
	{"^AP Info:.*", ""},    // remove column
	{"^Time", "TIME"},
	{"^Engine Speed", "RPM"},
	{"^Current Gear", "Gear"},
	{"^Accel Pedal Position", "AccelPedalPosition"},
	{"^TPS", "ThrottlePlateAngle"},

	{"^AFR", "AirFuelRatio"},
	{"^AFR Set Point", "AirFuelRatioDesired"},
	{"^Trgt\\. Boost Press\\.", "BoostPressureDesired"},
	{"^Boost Press\\.", "BoostPressureActual"},
	{"^Ambient Air Temp\\.", "AmbientTemperature (\u00B0F)"},
	{"^Coolant Temp\\.", "WaterTemperature (\u00B0F)"},
	{"^Engine Oil Temp\\.", "OilTemperature (\u00B0F)"},
	{"^IAT", "IntakeAirTemperature (\u00B0F)"},
	{"^Ignition Timing Final", "IgnitionTimingAngle"},
	{"^Knock Retard Cylinder 1", "IgnitionRetardCyl1"},
	{"^Knock Retard Cylinder 2", "IgnitionRetardCyl2"},
	{"^Knock Retard Cylinder 3", "IgnitionRetardCyl3"},
	{"^Knock Retard Cylinder 4", "IgnitionRetardCyl4"},
	{"^Knock Retard Cylinder 5", "IgnitionRetardCyl5"},
	{"^Knock Retard Cylinder 6", "IgnitionRetardCyl6"},
	{"^Knock Retard Cylinder 7", "IgnitionRetardCyl7"},
	{"^Knock Retard Cylinder 8", "IgnitionRetardCyl8"},
	{"^Turbine Act. Base Value", "WastegateDutyCycleBase"},
	{"^Turbine Act. Final Value", "WastegateDutyCycle"},
	{"^Vehicle Speed", "VehicleSpeed"},
    };

    private static final String[][] SWCOMM_aliases = new String[][] {
	// Time
	{"^TimeStamp$", "TIME"},

	// Engine Speed
	{"^N$", "RPM"},

	// Temperatures
	{"^TIA$", "IntakeAirTemperature"},
	{"^TCO$", "CoolantTemperature"},
	{"^TOIL$", "OilTemperature"},
	{"^Ambient air temperature$", "AmbientTemperature"},

	// Pressures
	{"^AMP$", "BaroPressure"},
	{"^MAP$", "BoostPressureActual"},
	{"^MAP_MES$", "BoostPressureActual2"},
	{"^MAP_SP$", "BoostPressureDesired"},
	{"^FUP$", "FuelPressureHigh"},
	{"^FUP_SP$", "FuelPressureHighSP"},
	{"^FUP_EFP$", "FuelPressureHighEFP"},
	{"^RFP_AV$", "RailPressureActual"},

	// Air Flow
	{"^MAF_THR$", "MassAirFlow"},
	{"^MAF$", "MassAirFlowActual"},

	// Throttle and Pedal
	{"^PV_AV$", "AccelPedalPosition"},
	{"^TPS_AV$", "ThrottlePlateAngle"},

	// Torque
	{"^TQI_AV$", "TorqueActual"},

	// Ignition
	{"^IGA_AV_MV$", "IgnitionTimingAngle"},
	{"^IGA_IGC\\[0\\]$", "IgnitionTimingAngle1"},
	{"^IGA_IGC\\[4\\]$", "IgnitionTimingAngle5"},
	{"^IGA_ADJ_KNK\\[0\\]$", "IgnitionRetardCyl1"},
	{"^IGA_ADJ_KNK\\[3\\]$", "IgnitionRetardCyl4"},
	{"^IGA_ADJ_KNK\\[5\\]$", "IgnitionRetardCyl6"},

	// Injection
	{"^TI_1_HOM\\[0\\]$", "FuelInjectorOnTime1"},
	{"^TI_1_HOM\\[2\\]$", "FuelInjectorOnTime2"},

	// Lambda/AFR
	{"^LAMB_LS_UP\\[1\\]$", "O2SVoltageSensor1"},
	{"^LAMB_LS_UP\\[2\\]$", "O2SVoltageSensor1Bank2"},
	{"^LAMB_SP\\[1\\]$", "AirFuelRatioDesired"},
	{"^LAMB_SP\\[2\\]$", "AirFuelRatioDesired2"},

	// Fuel Trims
	{"^STFT Bank 1$", "STFT"},
	{"^LTFT Bank 1$", "LTFT"},
	{"^STFT Bank 2$", "STFTBank2"},
	{"^LTFT Bank 2$", "LTFTBank2"},

	// Other
	{"^GEAR$", "SelectedGear"},
	{"^VS$", "VehicleSpeed"},
	{"^CAM_PHA\\[IN\\]\\[1\\]$", "IntakeCamPosition"},
	{"^PWM_CWP_2$", "WastegateDutyCycle"},
	{"^EFPPWM$", "WastegateDutyCycle2"},
	{"^FAC_LAM_CP$", "LambdaControl"}
    };

    private static final String[][] DEFAULT_aliases = new String[][] {
	{"^[Tt]ime$", "TIME"},
	{"^[Ee]ngine [Ss]peed$", "RPM"},
	{"^[Mm]ass air flow$", "MassAirFlow"},

	// M-Tuner Logger mappings - no easy way to detect an M-Tuner logger, so we have to hardcode these in DEFAULT_aliases
	{"^Engine Speed$", "RPM"},
	{"^Target Position Throttle$", "ThrottlePlateAngle"},
	{"^Manifold Absolute Pressure$", "BoostPressureActual"},
	{"^Manifold Pressure$", "BoostPressureActual"},
	// We now assume the default IntakeAirTemperature is C
	{"^Intake air temperature$", "IntakeAirTemperature"},
	{"^Ignition Angle Actual$", "IgnitionTimingAngleOverall"},
	{"^Relative Load$", "EngineLoad"},
	{"^Actual Ignition Angle$", "IgnitionTimingAngleOverall"},
	{"^Vehicle Speed$", "VehicleSpeed"}
    };

    private static final Map<LoggerType, String[][]> Aliases = new HashMap<LoggerType, String[][]>() {
	private static final long serialVersionUID = 1L;
	{
	    put(LoggerType.LOG_ECUX, ECUX_aliases);
	    put(LoggerType.LOG_ECUX, ECUX_aliases);
	    put(LoggerType.LOG_VCDS, VCDS_aliases);
	    put(LoggerType.LOG_ZEITRONIX, Zeitronix_aliases);
	    put(LoggerType.LOG_ME7LOGGER, ME7L_aliases);
	    put(LoggerType.LOG_EVOSCAN, EVOSCAN_aliases);
	    put(LoggerType.LOG_VOLVOLOGGER, VOLVOLOGGER_aliases);
	    put(LoggerType.LOG_LOGWORKS, LOGWORKS_aliases);
	    put(LoggerType.LOG_JB4, JB4_aliases);
	    put(LoggerType.LOG_COBB_AP, COBB_AP_aliases);
	    put(LoggerType.LOG_SWCOMM, SWCOMM_aliases);
	}
    };

    private static String[][] which(LoggerType logger) {
	if(Aliases.containsKey(logger))
	    return Aliases.get(logger);

	return DEFAULT_aliases;
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
		if (h[i].matches(s[0])) {
		    // Only rename the first one
		    if (!ArrayUtils.contains(h, s[1])) {
			//System.out.printf("%d: '%s'->'%s'\n", i, h[i], s[1]);
			h[i]=s[1];
		    }
		}
	    }
	    // Make sure all columns are unique
	    if (i>0 && h[i].length() > 0) {
		String[] prev = Arrays.copyOfRange(h, 0, i);
		String renamed = h[i];
		boolean rename = false;
		for (int j = 2; ArrayUtils.contains(prev, renamed); j++)  {
		    renamed = h[i] + " " + Integer.toString(j);
		    // System.out.printf("%d: renamed to '%s'\n", i,  renamed);
		    rename = true;
		}
		if (rename) h[i] = renamed;
	    }
	}
    }


    public static LoggerType detect(String h) {
	// Comment-based detection patterns
	if(h.matches(".*SWComm.*ECUTools.*")) return LoggerType.LOG_SWCOMM;
	if(h.matches(".*ME7-Logger.*")) return LoggerType.LOG_ME7LOGGER;
	if(h.matches("^VCDS.*")) return LoggerType.LOG_VCDS;

	// Data-based detection patterns (more specific first)
	if(h.matches("^AP Info:.*")) return LoggerType.LOG_COBB_AP;
	if(h.matches("^Firmware$")) return LoggerType.LOG_JB4;

	// Legacy patterns (keep for backward compatibility)
	if(h.matches("^.*(day|tag)$")) return LoggerType.LOG_VCDS;	// Day of week, possibly German lol
	if(h.matches("^Filename:.*")) {
	    if(Files.extension(h).equals("zto") ||
	       Files.extension(h).equals("zdl") ||
		h.matches(".*<unnamed file>$"))
	    return LoggerType.LOG_ZEITRONIX;
	}
	if(h.matches("^TIME$")) return LoggerType.LOG_ECUX;

	if(h.matches("^LogID$")) return LoggerType.LOG_EVOSCAN;

	if(h.matches("^Time\\s*\\(sec\\)$")) return LoggerType.LOG_VOLVOLOGGER;

	if(h.matches("^Session: Session [0-8]+$")) return LoggerType.LOG_LOGWORKS;

	return LoggerType.LOG_UNKNOWN;
    }
}
