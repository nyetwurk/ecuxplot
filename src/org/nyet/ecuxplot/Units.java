package org.nyet.ecuxplot;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public final class Units {

    private static final Pattern UNIT_PATTERN = Pattern.compile("^(.+?)\\s*\\((.*?)\\)$");

    /**
     * Parses unit conversion pattern "FieldName (unit)"
     * Returns null if pattern doesn't match
     */
    public static class ParsedUnitConversion {
        public final String baseField;
        public final String targetUnit;

        private ParsedUnitConversion(String baseField, String targetUnit) {
            this.baseField = baseField;
            this.targetUnit = targetUnit;
        }
    }

    /**
     * Parses a field ID to extract unit conversion information
     * @param id Field ID like "FieldName (unit)"
     * @return ParsedUnitConversion if pattern matches, null otherwise
     */
    public static ParsedUnitConversion parseUnitConversion(String id) {
        if (id == null) return null;
        Matcher m = UNIT_PATTERN.matcher(id);
        if (m.matches()) {
            return new ParsedUnitConversion(m.group(1), m.group(2));
        }
        return null;
    }

    /**
     * Infers unit from field name when unit cannot be determined from headers.
     * Called by DataLogger.processUnits() as a fallback when normalize() returns empty.
     *
     * Processing order in DataLogger.processUnits():
     * 1. Logger-specific unit parsing (unit_regex, header_format tokens)
     * 2. Units.normalize() - normalizes units extracted from headers
     * 3. Units.find() - THIS METHOD - fallback when units are still unknown
     *
     * This method handles:
     * - ECUx files: Have NO units in CSV, so logger-specific parsing often fails, making this important
     * - Other loggers: Only used for fields where logger-specific parsing and headers provided no units
     *
     * Note: Logger-specific mappings in loggers.yaml (e.g., unit_regex) do NOT replace this method.
     * If a logger's unit_regex doesn't match a field, this method is still called to fill the gap.
     *
     * @param id The field ID to look up
     * @return The inferred unit, or empty string if unknown
     */
    public final static String find(String id) {
        if (id == null) return null;
        final String[][] legend = {
            // Note: .matches() requires entire string match, so ^ and $ are implicit
            // .* at start allows text before pattern, .* at end allows text after, overriding the implicit ^ and $
            {"AccelPedalPosition", UnitConstants.UNIT_PERCENT},  // Canonical name (after ECUX alias converts AcceleratorPedalPosition)
            {"AirFuelRatioDesired", UnitConstants.UNIT_LAMBDA}, // Unfortunately, ME7L has AFR as an alias, but it is implcitly lambda
            {"EGTbank1OXS", UnitConstants.UNIT_CELSIUS},  // Fallback ONLY for ecux.csv EGTbank1OXS (no units in header - exact match)
            {"FuelInjectorOnTime", UnitConstants.UNIT_MS},
            {"FuelInjectorDutyCycle", UnitConstants.UNIT_PERCENT},
            {"IntakeAirTemperature", UnitConstants.UNIT_CELSIUS}, // If not otherwise specified, assume C
            {"MassAirFlow", UnitConstants.UNIT_GPS},
            {"ThrottlePlateAngle", UnitConstants.UNIT_PERCENT},
            {"VehicleSpeed", UnitConstants.UNIT_KMH}, // M-tuner native is km/h but must use default, so default is km/h
            {"TPS", UnitConstants.UNIT_PERCENT},

            {"RPM", UnitConstants.UNIT_RPM},
            {"TIME.*", UnitConstants.UNIT_SECONDS},
            {"Time.*", UnitConstants.UNIT_SECONDS},

            {"Zeitronix TPS", UnitConstants.UNIT_PERCENT},
            {"Zeitronix TIME.*", UnitConstants.UNIT_SECONDS},
            {"Zeitronix IAT.*", UnitConstants.UNIT_FAHRENHEIT},
            {"Zeitronix SPARKADV.*", UnitConstants.UNIT_DEGREES},
            {"Zeitronix EGT.*", UnitConstants.UNIT_FAHRENHEIT},
            {"Zeitronix MAP.*", UnitConstants.UNIT_PSI},
            {"Zeitronix ThrottlePlateAngle.*", UnitConstants.UNIT_PERCENT},

            {"KnockVolt.*", UnitConstants.UNIT_VOLTS},
            {"OXSVolt.*", UnitConstants.UNIT_VOLTS},

            {".*BoostPressure.*", UnitConstants.UNIT_MBAR},
            {".*Duty.*Cycle.*", UnitConstants.UNIT_PERCENT},
            {".*Angle.*", UnitConstants.UNIT_DEGREES},
            {".*Ignition.*Retard.*", UnitConstants.UNIT_DEGREES},
            {".*Ignition.*Timing.*", UnitConstants.UNIT_DEGREES},
            {".*Load.*", UnitConstants.UNIT_PERCENT},
            {".*Pressure.*", UnitConstants.UNIT_MBAR},
            {".*Temperature.*", UnitConstants.UNIT_CELSIUS},
            {".*Voltage.*", UnitConstants.UNIT_VOLTS},
            {"TargetAFRDriverRequest.*", UnitConstants.UNIT_LAMBDA}, // ECUx/ME7l oddity - must come before .*AFR.*
            {".*AFR.*", UnitConstants.UNIT_AFR},
            {".*AirFuelRatio.*", UnitConstants.UNIT_AFR},
            {"LambdaControl.*", ""}, // LambdaControl et al are not lambda, they are unitless, prevent falling through to Lambda
            {".*Lambda.*", UnitConstants.UNIT_LAMBDA}
        };

        for (final String[] element : legend) {
            if(id.matches(element[0])) return element[1];
        }
        return "";  // Empty string for unitless fields
    }

    /**
     * Normalizes unit strings from various log file formats to standard unit notation.
     * Handles variations like "RPM" vs "rpm", "degC" vs "°C", corrupted symbols, etc.
     *
     * This is the PRIMARY method for determining units. Called by DataLogger.processUnits()
     * after parsing the CSV header. Only if normalize() returns empty will find() be used.
     *
     * @param u The raw unit string from the log file header
     * @return Normalized unit string using UnitConstants
     */
    public final static String normalize(String u) {
        if(u==null) return "";
        u = u.trim();
        // Extract units from parentheses: "(sec)" -> "sec"
        if (u.matches("\\(.+\\)")) {
            u = u.replaceAll("\\((.+)\\)", "$1");
        }
        // Note: .matches() requires entire string match, so ^ and $ are implicit
        // We keep anchors here for clarity since these are exact unit string matches
        final String[][] map = {
            {"1/min", UnitConstants.UNIT_RPM},
            {"/min", UnitConstants.UNIT_RPM},  // VCDS RPM unit format
            {"g/s", UnitConstants.UNIT_GPS},  // VCDS mass flow unit format
            {"°BTDC", UnitConstants.UNIT_DEGREES},  // VCDS ignition timing unit (degrees before top dead center)
            {"[^\u00B0]BTDC", UnitConstants.UNIT_DEGREES},  // VCDS corrupted ignition timing unit (corrupted degree + BTDC)
            {"\u00FF", UnitConstants.UNIT_DEGREES},  // VCDS corrupted degree symbol (U+00FF)
            {"\uFFFD", UnitConstants.UNIT_DEGREES},  // VCDS corrupted degree symbol (U+FFFD - replacement character)
            {"[^\u00B0]KW", UnitConstants.UNIT_DEGREES},  // Fix for ME7L degree symbol encoding issue (non-degree char + KW)
            {"[^\u00B0]C", UnitConstants.UNIT_CELSIUS},  // Fix for ME7L temperature degree symbol encoding issue (non-degree char + C)
            {"DK", UnitConstants.UNIT_DEGREES},
            {"°CRK", UnitConstants.UNIT_DEGREES},  // Convert °CRK to ° (crank degrees)
            {"°TPS", UnitConstants.UNIT_PERCENT},  // Convert °TPS to % (throttle position sensor)
            //{"°RFP", "mBar"},  // SWCOMM oddity? Leave as is.
            {"[Dd]egrees", UnitConstants.UNIT_DEGREES},
            {"PED", UnitConstants.UNIT_DEGREES},
            {"degC", UnitConstants.UNIT_CELSIUS},
            {"C", UnitConstants.UNIT_CELSIUS},
            {"F", UnitConstants.UNIT_FAHRENHEIT},
            {"lambda", UnitConstants.UNIT_LAMBDA},
            {"mbar", UnitConstants.UNIT_MBAR},
            {"hPa", UnitConstants.UNIT_MBAR},  // Convert hPa to mBar (1 hPa = 1 mBar)
            {"k[Pp]a", UnitConstants.UNIT_KPA},
            {"km/h", UnitConstants.UNIT_KMH},  // Keep km/h as km/h
            {"KPH", UnitConstants.UNIT_KMH},   // Convert KPH to km/h
            {"kph", UnitConstants.UNIT_KMH},   // Convert kph to km/h
            {"psi", UnitConstants.UNIT_PSI},
            {"PSI/.*", UnitConstants.UNIT_PSI},
            {"sec\\.ms", UnitConstants.UNIT_SECONDS},  // ME7L time unit normalization
            {"sec", UnitConstants.UNIT_SECONDS},       // VOLVO time unit normalization
            {"rpm", UnitConstants.UNIT_RPM},
            {"-|Marker|Markierung|MARKE|STAMP", ""} // Random junk we don't need
        };
        for (final String[] element : map) {
            if(u.matches(element[0])) return element[1];
        }
        return u;
     }

    // Unit conversion configuration
    private static final Map<String, List<String>> CONVERSIONS;

    static {
        Map<String, List<String>> conversions = new HashMap<>();

        // Air-Fuel Ratio conversions
        conversions.put("AirFuelRatioActual", List.of(UnitConstants.UNIT_LAMBDA, UnitConstants.UNIT_AFR));

        // Pressure conversions
        conversions.put("BoostPressureActual", List.of(UnitConstants.UNIT_MBAR, UnitConstants.UNIT_PSI, UnitConstants.UNIT_KPA));
        conversions.put("BoostPressure", List.of(UnitConstants.UNIT_MBAR, UnitConstants.UNIT_PSI, UnitConstants.UNIT_KPA));

        // Temperature conversions (applies to all temperature fields)
        conversions.put("Temperature", List.of(UnitConstants.UNIT_CELSIUS, UnitConstants.UNIT_FAHRENHEIT));

        // Speed conversions
        conversions.put("VehicleSpeed", List.of(UnitConstants.UNIT_MPH, UnitConstants.UNIT_KMH));

        // Mass flow conversions
        conversions.put("MassAirFlow", List.of(UnitConstants.UNIT_GPS, UnitConstants.UNIT_KGH));

        // Torque conversions
        conversions.put("Torque", List.of(UnitConstants.UNIT_FTLB, UnitConstants.UNIT_NM));

        CONVERSIONS = Collections.unmodifiableMap(conversions);
    }

    /**
     * Returns the list of alternate units for a given unit.
     * Used by AxisMenu to automatically generate unit conversion menu items.
     *
     * Example: For "lambda", returns ["AFR"] (the only alternate in the AirFuelRatio category).
     *
     * @param unit The unit to find alternates for (from UnitConstants)
     * @return List of alternate unit strings, or empty list if no alternates exist
     */
    public static List<String> getAlternateUnits(String unit) {
        if (unit == null) return Collections.emptyList();

        // Find the category containing this unit by searching CONVERSIONS
        for (Map.Entry<String, List<String>> entry : CONVERSIONS.entrySet()) {
            if (entry.getValue().contains(unit)) {
                // Return all units in the category except the current one
                // Blacklist: Don't show kPa as a conversion option (users can convert FROM kPa but not TO kPa)
                return entry.getValue().stream()
                    .filter(u -> !u.equals(unit))
                    .filter(u -> !u.equals(UnitConstants.UNIT_KPA))  // Blacklist converting TO kPa
                    .collect(java.util.stream.Collectors.toList());
            }
        }

        return Collections.emptyList();
    }

}

// vim: set sw=4 ts=8 expandtab:
