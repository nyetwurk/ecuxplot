package org.nyet.ecuxplot;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class Units {

    private static final Pattern UNIT_PATTERN = Pattern.compile("^(.+?)\\s*\\((.*?)\\)$");

    /**
     * Set of all known normalized unit constants.
     * Automatically discovered from UnitConstants using reflection.
     * Used for early return optimization in normalize() to avoid unnecessary processing.
     *
     * This ensures any new UNIT_* constants added to UnitConstants are automatically
     * included without manual maintenance.
     */
    private static final Set<String> KNOWN_NORMALIZED_UNITS;

    static {
        Set<String> units = new HashSet<>();
        try {
            Field[] fields = UnitConstants.class.getDeclaredFields();
            for (Field field : fields) {
                // Find all public static final String fields starting with "UNIT_"
                int modifiers = field.getModifiers();
                if (Modifier.isPublic(modifiers) &&
                    Modifier.isStatic(modifiers) &&
                    Modifier.isFinal(modifiers) &&
                    field.getType() == String.class &&
                    field.getName().startsWith("UNIT_")) {
                    String unitValue = (String) field.get(null);
                    if (unitValue != null && !unitValue.isEmpty()) {
                        units.add(unitValue);
                    }
                }
            }
        } catch (IllegalAccessException e) {
            // Should never happen for public static final fields
            throw new RuntimeException("Failed to discover unit constants from UnitConstants", e);
        }
        KNOWN_NORMALIZED_UNITS = Collections.unmodifiableSet(units);
    }

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
     * Maps unit-converted preset/preference keys to base field names.
     * This handles the case where presets request unit-converted columns (e.g., "BoostPressureActual (PSI)")
     * but datasets/menus only contain base field names (e.g., "BoostPressureActual") because the normalized
     * unit already matches the requested unit.
     *
     * @param key The preset/preference key (may be unit-converted like "BoostPressureActual (PSI)")
     * @param normalizedUnitGetter Function to get normalized unit for a field name, or null if field doesn't exist
     * @return The mapped base field name if mapping applies, otherwise the original key
     */
    public static String mapUnitConversionToBaseField(String key, java.util.function.Function<String, String> normalizedUnitGetter) {
        if (key == null) return null;

        ParsedUnitConversion parsed = parseUnitConversion(key);
        if (parsed == null) {
            // Not a unit conversion request, return as-is
            return key;
        }

        // Get normalized unit for the base field
        String normalizedUnit = normalizedUnitGetter != null ? normalizedUnitGetter.apply(parsed.baseField) : null;

        // If normalized unit matches requested unit, map to base field
        if (normalizedUnit != null && normalizedUnit.equals(parsed.targetUnit)) {
            return parsed.baseField;
        }

        // No mapping applies, return original
        return key;
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

            {"BoostPressureRelative", UnitConstants.UNIT_MBAR_GAUGE}, // Must come before .*BoostPressure.*
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

        // Early return: if input is already a known constant, return it directly
        if (KNOWN_NORMALIZED_UNITS.contains(u)) {
            return u;
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
            // "km/h" matches UNIT_KMH exactly, handled by early return
            {"KPH", UnitConstants.UNIT_KMH},   // Convert KPH to km/h
            {"kph", UnitConstants.UNIT_KMH},   // Convert kph to km/h
            {"psi", UnitConstants.UNIT_PSI},
            {"PSI/.*", UnitConstants.UNIT_PSI},
            {"sec\\.ms", UnitConstants.UNIT_SECONDS},  // ME7L time unit normalization
            {"sec", UnitConstants.UNIT_SECONDS},       // VOLVO time unit normalization ("sec" != "s", so keep)
            {"rpm", UnitConstants.UNIT_RPM},  // "rpm" != "RPM", so keep
            {"-|Marker|Markierung|MARKE|STAMP", ""} // Random junk we don't need
        };
        for (final String[] element : map) {
            if(u.matches(element[0])) return element[1];
        }
        return u;
     }

    /**
     * Map of units to their preference (US_CUSTOMARY or METRIC).
     * Each unit inherently belongs to one preference system.
     */
    private static final Map<String, UnitPreference> UNIT_PREFERENCE_MAP;

    static {
        Map<String, UnitPreference> preferenceMap = new HashMap<>();

        // US Customary units
        preferenceMap.put(UnitConstants.UNIT_PSI, UnitPreference.US_CUSTOMARY);
        preferenceMap.put(UnitConstants.UNIT_MPH, UnitPreference.US_CUSTOMARY);
        preferenceMap.put(UnitConstants.UNIT_FTLB, UnitPreference.US_CUSTOMARY);
        preferenceMap.put(UnitConstants.UNIT_FAHRENHEIT, UnitPreference.US_CUSTOMARY);

        // Metric units
        preferenceMap.put(UnitConstants.UNIT_MBAR, UnitPreference.METRIC);
        preferenceMap.put(UnitConstants.UNIT_MBAR_GAUGE, UnitPreference.METRIC);
        preferenceMap.put(UnitConstants.UNIT_KPA, UnitPreference.METRIC);
        preferenceMap.put(UnitConstants.UNIT_KMH, UnitPreference.METRIC);
        preferenceMap.put(UnitConstants.UNIT_NM, UnitPreference.METRIC);
        preferenceMap.put(UnitConstants.UNIT_CELSIUS, UnitPreference.METRIC);

        // Units that are the same in both (no preference)
        // RPM, %, V, ms, g/s, etc. - these don't need conversion

        UNIT_PREFERENCE_MAP = Collections.unmodifiableMap(preferenceMap);
    }

    // Unit conversion configuration
    private static final Map<String, List<String>> CONVERSIONS;

    static {
        Map<String, List<String>> conversions = new HashMap<>();

        // Air-Fuel Ratio conversions
        conversions.put("AirFuelRatioActual", List.of(UnitConstants.UNIT_LAMBDA, UnitConstants.UNIT_AFR));

        // Pressure conversions
        conversions.put("BoostPressureActual", List.of(UnitConstants.UNIT_MBAR, UnitConstants.UNIT_PSI, UnitConstants.UNIT_KPA));
        conversions.put("BoostPressureRelative", List.of(UnitConstants.UNIT_MBAR_GAUGE, UnitConstants.UNIT_PSI));
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

    /**
     * Get the standard unit for a column based on global preference.
     *
     * Logic:
     * 1. Check if column has special case override (e.g., BaroPressure always Metric)
     * 2. Get native unit from column
     * 3. Check if native unit's preference matches global preference
     * 4. If doesn't match, find alternate unit with matching preference
     * 5. Return standard unit (or null if no conversion needed)
     *
     * @param canonicalName The canonical column name (e.g., "BoostPressureActual")
     * @param nativeUnit The native unit from the column (e.g., "PSI", "mBar", "kPa")
     * @param preference The global unit preference (US_CUSTOMARY or METRIC)
     * @return The standard unit string, or null if no conversion needed
     */
    public static String getStandardUnit(String canonicalName, String nativeUnit, UnitPreference preference) {
        // 1. Check special case overrides first (e.g., BaroPressure always Metric)
        String overrideUnit = getSpecialCaseUnit(canonicalName);
        if (overrideUnit != null) {
            return overrideUnit;
        }

        // 2. If no native unit, can't determine standard
        if (nativeUnit == null || nativeUnit.isEmpty()) {
            return null;
        }

        // 3. Check if native unit's preference matches global preference
        UnitPreference nativeUnitPreference = UNIT_PREFERENCE_MAP.get(nativeUnit);
        if (nativeUnitPreference == null) {
            // Unit has no preference (e.g., RPM, %, V) - use native unit
            return null;
        }

        if (nativeUnitPreference == preference) {
            // Native unit already matches preference
            // Check if there's a preferred unit in the same conversion category
            // The order in CONVERSIONS lists encodes preference (first unit of matching preference is preferred)
            List<String> alternates = getAlternateUnits(nativeUnit);
            if (!alternates.isEmpty()) {
                // Find the conversion category containing this unit
                for (Map.Entry<String, List<String>> entry : CONVERSIONS.entrySet()) {
                    List<String> categoryUnits = entry.getValue();
                    if (categoryUnits.contains(nativeUnit)) {
                        // Find first unit in category that matches preference and is available as alternate
                        for (String unit : categoryUnits) {
                            if (!unit.equals(nativeUnit) && alternates.contains(unit)) {
                                UnitPreference unitPref = UNIT_PREFERENCE_MAP.get(unit);
                                if (unitPref == preference) {
                                    return unit;
                                }
                            }
                        }
                        break; // Found the category, no need to continue searching
                    }
                }
            }
            return null;
        }

        // 4. Native unit doesn't match preference - find alternate unit
        // Use existing getAlternateUnits() to find conversion options
        List<String> alternates = getAlternateUnits(nativeUnit);
        for (String alt : alternates) {
            UnitPreference altPreference = UNIT_PREFERENCE_MAP.get(alt);
            if (altPreference == preference) {
                return alt; // Found matching alternate unit
            }
        }

        // Note: If no alternate found, return null (use native unit)
        // This handles edge cases where conversion isn't available

        // 5. No matching alternate found - use native unit
        return null;
    }

    /**
     * Get special case unit override from canonical_unit_standards config.
     * Only for columns that have non-standard requirements (e.g., BaroPressure always Metric).
     *
     * @param canonicalName The canonical column name
     * @return The override unit string, or null if no special case
     */
    private static String getSpecialCaseUnit(String canonicalName) {
        // Get special case configuration from DataLogger
        // Returns a single unit string that overrides global preference
        // Examples: BaroPressure always mBar, Torque columns always Nm
        return DataLogger.getCanonicalUnitStandard(canonicalName);
    }

}

// vim: set sw=4 ts=8 expandtab:
