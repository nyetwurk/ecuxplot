package org.nyet.ecuxplot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

import javax.swing.AbstractButton;

import org.nyet.logfile.Dataset.Column;
import org.nyet.logfile.Dataset.DatasetId;

/**
 * Data structures for AxisMenu calculated fields and routing patterns.
 *
 * This class separates field name data from menu creation logic in AxisMenu.java,
 * similar to how AxisMenuHandlers.java separates calculation logic from ECUxDataset.
 *
 * Each base field can trigger the creation of multiple calculated menu items.
 * This data structure maps base fields to their calculated fields, including:
 * - Field names
 * - Submenu routing
 * - Standalone vs submenu placement
 * - Handler method references (shows connection to AxisMenuHandlers)
 * - Logger type filters (e.g., ME7LOGGER only)
 */
public class AxisMenuItems {
    /**
     * Submenu name constants - single source of truth for all submenu names.
     */
    public static final String SUBMENU_RPM = "RPM";
    public static final String SUBMENU_TIME = "TIME";
    public static final String SUBMENU_SAMPLE = "Sample";
    public static final String SUBMENU_SPEED = "Speed";
    public static final String SUBMENU_MAF = "MAF";
    public static final String SUBMENU_FUEL = "Fuel";
    public static final String SUBMENU_BOOST = "Boost";
    public static final String SUBMENU_THROTTLE = "Throttle";
    public static final String SUBMENU_IGNITION = "Ignition";
    public static final String SUBMENU_TRUE_TIMING = "TrueTiming";
    public static final String SUBMENU_TEMPERATURE = "Temperature";
    public static final String SUBMENU_VVT = "VVT";
    public static final String SUBMENU_CATS = "Cats";
    public static final String SUBMENU_EGT = "EGT";
    public static final String SUBMENU_IDLE = "Idle";
    public static final String SUBMENU_KNOCK = "Knock";
    public static final String SUBMENU_MISFIRES = "Misfires";
    public static final String SUBMENU_O2_SENSORS = "O2 Sensor(s)";
    public static final String SUBMENU_TORQUE = "Torque";
    public static final String SUBMENU_ZEITRONIX = "Zeitronix";
    public static final String SUBMENU_LOAD = "Load";
    public static final String SUBMENU_ME7_LOGGER = "ME7 Logger";
    public static final String SUBMENU_EVOSCAN = "EvoScan";
    public static final String SUBMENU_ACCELERATION = "Acceleration";
    public static final String SUBMENU_POWER = "Power";
    public static final String SUBMENU_CALC_MAF = "Calc MAF";
    public static final String SUBMENU_CALC_PID = "Calc PID";
    public static final String SUBMENU_CALC_IAT = "Calc IAT";
    public static final String SUBMENU_CALC_FUEL = "Calc Fuel";

    /**
     * Data structure for a calculated menu field.
     * Represents a single calculated field that should be added to the menu
     * when a base field is detected.
     */
    public static class MenuCalculatedField {
        public final String fieldName;
        public final String submenu;  // null for standalone items (this.add), non-null for submenu items
        public final BiFunction<ECUxDataset, Comparable<?>, Column> handlerMethod;  // Method reference to handler, or null
        public final boolean me7loggerOnly;  // true if this field should only be added for ME7LOGGER

        public MenuCalculatedField(String fieldName, String submenu, BiFunction<ECUxDataset, Comparable<?>, Column> handlerMethod) {
            this(fieldName, submenu, handlerMethod, false);
        }

        public MenuCalculatedField(String fieldName, String submenu, BiFunction<ECUxDataset, Comparable<?>, Column> handlerMethod, boolean me7loggerOnly) {
            this.fieldName = fieldName;
            this.submenu = submenu;  // null means standalone (this.add), non-null means addToSubmenu
            this.handlerMethod = handlerMethod;
            this.me7loggerOnly = me7loggerOnly;
        }
    }

    /**
     * Data structure for RPM-triggered calculated fields.
     * Separates data (what fields to add) from code (how to add them).
     */
    public static class RpmCalculatedField {
        public final String fieldName;
        public final String submenu;
        public final String[] tooltipSteps;
        public final String rangeAwareSmoothing;  // null if none
        public final boolean debugOnly;
        public final String unitConversion;  // null if none (e.g., "Nm" for unit conversion)

        public RpmCalculatedField(String fieldName, String submenu, String[] tooltipSteps,
                                String rangeAwareSmoothing, boolean debugOnly, String unitConversion) {
            this.fieldName = fieldName;
            this.submenu = submenu;
            this.tooltipSteps = tooltipSteps;
            this.rangeAwareSmoothing = rangeAwareSmoothing;
            this.debugOnly = debugOnly;
            this.unitConversion = unitConversion;
        }
    }

    /**
     * Data structure for base field to calculated fields mapping.
     * Maps a base field pattern (regex) to a list of calculated fields that should be added.
     */
    public static class BaseFieldCalculatedFields {
        public final String baseFieldPattern;  // Regex pattern or exact match
        public final List<MenuCalculatedField> calculatedFields;
        public final String loggerTypeFilter;  // null for all, or specific logger type (e.g., "ME7LOGGER")

        public BaseFieldCalculatedFields(String baseFieldPattern, List<MenuCalculatedField> calculatedFields, String loggerTypeFilter) {
            this.baseFieldPattern = baseFieldPattern;
            this.calculatedFields = calculatedFields;
            this.loggerTypeFilter = loggerTypeFilter;
        }
    }

    /**
     * Data structure for pattern-to-submenu routing.
     */
    public static class PatternSubmenuPair {
        public final Pattern pattern;
        public final String submenu;

        public PatternSubmenuPair(String patternStr, String submenu) {
            this.pattern = Pattern.compile(patternStr);
            this.submenu = submenu;
        }
    }

    /**
     * Calculated fields that are added when "RPM" field is detected.
     * This data structure separates what fields to add from how to add them.
     */
    public static final RpmCalculatedField[] RPM_CALCULATED_FIELDS = {
        // Power/Torque fields
        new RpmCalculatedField("WHP", SUBMENU_POWER,
            new String[]{
                "Acceleration (m/s^2): MA+SG or SG -> accelMAW",
                "Calc Velocity: MA+SG or SG",
                "HPMAW"},
            "HPMAW", false, null),
        new RpmCalculatedField("WTQ", SUBMENU_POWER,
            new String[]{"MA+SG or SG", "accelMAW", "HPMAW"},
            null, false, null),
        new RpmCalculatedField("WTQ", SUBMENU_POWER, null, null, false, UnitConstants.UNIT_NM),  // Unit conversion
        new RpmCalculatedField("HP", SUBMENU_POWER,
            new String[]{"MA+SG or SG", "accelMAW", "HPMAW"},
            "HPMAW", false, null),
        new RpmCalculatedField("TQ", SUBMENU_POWER,
            new String[]{"MA+SG or SG", "accelMAW", "HPMAW"},
            null, false, null),
        new RpmCalculatedField("TQ", SUBMENU_POWER, null, null, false, UnitConstants.UNIT_NM),  // Unit conversion
        new RpmCalculatedField("Drag", SUBMENU_POWER,
            new String[]{"MA+SG or SG"},
            null, false, null),

        // Speed fields
        new RpmCalculatedField("Calc Velocity", SUBMENU_SPEED,
            new String[]{"MA+SG for quantized, SG for smooth"},
            null, false, null),

        // Acceleration fields (RPM/s group)
        new RpmCalculatedField("Acceleration (RPM/s) - raw", SUBMENU_ACCELERATION,
            new String[]{"MA+SG for quantized, SG for smooth", "derivative"},
            null, true, null),  // Debug only
        new RpmCalculatedField("Acceleration (RPM/s) - from base RPM", SUBMENU_ACCELERATION,
            new String[]{"SG", "accelMAW", "derivative"},
            null, true, null),  // Debug only
        new RpmCalculatedField("Acceleration (RPM/s)", SUBMENU_ACCELERATION,
            new String[]{
                "MA+SG for quantized, SG for smooth",
                "derivative (accelMAW during calculation)"},
            "accelMAW (applied in getData())", false, null),

        // Acceleration fields (m/s^2 group)
        new RpmCalculatedField("Acceleration (m/s^2) - raw", SUBMENU_ACCELERATION,
            new String[]{"MA+SG for quantized, SG for smooth", "derivative"},
            null, true, null),  // Debug only
        new RpmCalculatedField("Acceleration (m/s^2)", SUBMENU_ACCELERATION,
            new String[]{
                "MA+SG for quantized, SG for smooth",
                "derivative (accelMAW during calculation)"},
            "accelMAW (applied in getData())", false, null),

        // Derived acceleration
        new RpmCalculatedField("Acceleration (g)", SUBMENU_ACCELERATION,
            new String[]{
                "MA+SG for quantized, SG for smooth",
                "derivative (accelMAW during calculation)",
                "unit conversion to g"},
            "accelMAW (applied in getData())", false, null),
    };

    /**
     * Simple regex patterns for field-to-submenu mapping.
     * Patterns are checked in order (most specific first).
     * Only simple 1:1 mappings without nested conditionals are included here.
     * Special cases with nested conditionals remain in the if/else chain.
     *
     * IMPORTANT: Special cases are checked BEFORE the pattern table.
     * Fields that match both a pattern table pattern AND a special case
     * will be handled by the special case (e.g., "RPM", "TorqueDesired", "IntakeAirTemperature").
     * Do NOT add patterns here that would match fields needing special handling.
     *
     * Note: Pattern.matches() requires the entire string to match, so ^ and $ anchors are ignored/redundant.
     * Patterns use .* at start/end to match anywhere within the field name.
     */
    public static final PatternSubmenuPair[] SUBMENU_PATTERNS = {
        // Exact matches first (most specific)
        // Note: "TIME [Range]" and "Sample [Range]" are also handled by special cases,
        // but included here for fields that come in directly from dataset
        new PatternSubmenuPair("TIME \\[Range\\]", SUBMENU_TIME),
        new PatternSubmenuPair("Sample \\[Range\\]", SUBMENU_SAMPLE),
        // Pattern matches (less specific)
        // Note: "RPM.*" matches "RPM" but "RPM" is handled by special case first
        new PatternSubmenuPair("RPM.*", SUBMENU_RPM),
        new PatternSubmenuPair(".*(Cam|NWS|Valve|VV).*", SUBMENU_VVT),
        new PatternSubmenuPair(".*(Cat|MainCat).*", SUBMENU_CATS),
        new PatternSubmenuPair(".*EGT.*", SUBMENU_EGT),
        new PatternSubmenuPair(".*(Idle|Idling).*", SUBMENU_IDLE),
        new PatternSubmenuPair(".*[Kk]nock.*", SUBMENU_KNOCK),
        new PatternSubmenuPair(".*Misfire.*", SUBMENU_MISFIRES),
        new PatternSubmenuPair(".*(OXS|O2|ResistanceSensor).*", SUBMENU_O2_SENSORS),
        new PatternSubmenuPair(".*(Vehicle|Wheel).*Speed.*", SUBMENU_SPEED),
        // Note: "IntakeAirTemperature" is handled by special case (adds calculated fields)
        // Only match other temperature fields (CoolantTemperature, WaterTemperature, etc.)
        // Also matches "Temp" (e.g., "Coolant Outlet Temp")
        new PatternSubmenuPair(".*(Coolant|Water|Oil|Exhaust|EGT|Cat|MainCat|Ambient|Transmission).*(Temperature|Temp).*", SUBMENU_TEMPERATURE),
        new PatternSubmenuPair("Log.*", SUBMENU_EVOSCAN),  // EvoScan
        // Note: "TorqueDesired" is handled by special case (adds derived fields)
        // Only match other torque fields (TorqueActual, TorqueAtClutch, etc.)
        // Also matches variations with spaces: "Torque Actual", "Maximum Torque at Clutch"
        new PatternSubmenuPair(".*Torque.*(Actual|Clutch|Requested|Corrected|Limit).*", SUBMENU_TORQUE),
        new PatternSubmenuPair(".*Requested.*Torque.*", SUBMENU_TORQUE),
    };

    /**
     * Mapping of base fields to their calculated fields for menu creation.
     *
     * This data structure:
     * 1. Separates field name data from menu creation logic
     * 2. Shows connection to AxisMenuHandlers via handlerMethod field (method references)
     * 3. Maintains separation of concerns (UI vs calculation)
     *
     * AxisMenu.java uses this to determine which calculated fields to add
     * when base fields are detected in the CSV.
     *
     * IMPORTANT: Order matters for pattern matching - more specific patterns should come first.
     * Patterns are matched using String.matches() which requires the entire string to match.
     * Therefore, ^ and $ anchors are redundant and ignored. Do NOT use anchors in patterns.
     */
    public static final BaseFieldCalculatedFields[] MENU_CALCULATED_FIELDS = {
        // MassAirFlow -> calculated fields (ME7LOGGER only for some)
        new BaseFieldCalculatedFields("MassAirFlow", Arrays.asList(
            new MenuCalculatedField("MassAirFlow (kg/hr)", null, null),  // unit conversion, standalone
            new MenuCalculatedField("Sim Load", SUBMENU_CALC_MAF, AxisMenuHandlers::getMafFuelColumn, true),  // ME7LOGGER only
            new MenuCalculatedField("Sim Load Corrected", SUBMENU_CALC_MAF, AxisMenuHandlers::getMafFuelColumn, true),  // ME7LOGGER only
            new MenuCalculatedField("Sim MAF", SUBMENU_CALC_MAF, AxisMenuHandlers::getMafFuelColumn, true),  // ME7LOGGER only
            new MenuCalculatedField("MassAirFlow df/dt", SUBMENU_MAF, AxisMenuHandlers::getMafFuelColumn),
            new MenuCalculatedField("Turbo Flow", SUBMENU_MAF, AxisMenuHandlers::getMafFuelColumn),
            new MenuCalculatedField("Turbo Flow (lb/min)", SUBMENU_MAF, AxisMenuHandlers::getMafFuelColumn)
        ), null),

        // EffInjectionTime -> calculated fields
        new BaseFieldCalculatedFields("EffInjectionTime", Arrays.asList(
            new MenuCalculatedField("EffInjectorDutyCycle", null, AxisMenuHandlers::getInjectorDutyCycleColumn),  // standalone
            new MenuCalculatedField("Sim Fuel Mass", SUBMENU_CALC_FUEL, AxisMenuHandlers::getMafFuelColumn),
            new MenuCalculatedField("Sim AFR", SUBMENU_CALC_FUEL, AxisMenuHandlers::getAfrColumn),
            new MenuCalculatedField("Sim lambda", SUBMENU_CALC_FUEL, AxisMenuHandlers::getAfrColumn),
            new MenuCalculatedField("Sim lambda error", SUBMENU_CALC_FUEL, AxisMenuHandlers::getAfrColumn)
        ), null),

        // FuelInjectorOnTime -> calculated fields
        new BaseFieldCalculatedFields("FuelInjectorOnTime", Arrays.asList(
            new MenuCalculatedField("FuelInjectorDutyCycle", null, AxisMenuHandlers::getInjectorDutyCycleColumn)  // standalone
        ), null),

        // EffInjectionTimeBank2 -> calculated fields
        new BaseFieldCalculatedFields("EffInjectionTimeBank2", Arrays.asList(
            new MenuCalculatedField("EffInjectorDutyCycleBank2", null, AxisMenuHandlers::getInjectorDutyCycleColumn)  // standalone
        ), null),

        // Zeitronix Boost -> calculated fields
        new BaseFieldCalculatedFields("Zeitronix Boost", Arrays.asList(
            new MenuCalculatedField("Zeitronix Boost (PSI)", null, null),  // unit conversion, standalone
            new MenuCalculatedField("Boost Spool Rate Zeit (RPM)", SUBMENU_BOOST, AxisMenuHandlers::getBoostZeitronixColumn)
        ), null),

        // Zeitronix AFR -> calculated fields
        new BaseFieldCalculatedFields("Zeitronix AFR", Arrays.asList(
            new MenuCalculatedField("Zeitronix AFR (lambda)", null, AxisMenuHandlers::getBoostZeitronixColumn)  // formula conversion, standalone
        ), null),

        // Zeitronix Lambda -> calculated fields
        new BaseFieldCalculatedFields("Zeitronix Lambda", Arrays.asList(
            new MenuCalculatedField("Zeitronix Lambda (AFR)", null, AxisMenuHandlers::getBoostZeitronixColumn)  // formula conversion, standalone
        ), null),

        // EngineLoad(Requested|Corrected) -> calculated fields
        new BaseFieldCalculatedFields("EngineLoad(Requested|Corrected)", Arrays.asList(
            new MenuCalculatedField("Sim BoostPressureDesired", SUBMENU_BOOST, AxisMenuHandlers::getBoostZeitronixColumn),
            new MenuCalculatedField("Sim LoadSpecified correction", SUBMENU_BOOST, AxisMenuHandlers::getMiscSimColumn)
        ), null),

        // BoostPressureDesired -> calculated fields
        new BaseFieldCalculatedFields("BoostPressureDesired", Arrays.asList(
            new MenuCalculatedField("BoostDesired PR", SUBMENU_BOOST, AxisMenuHandlers::getBoostZeitronixColumn),
            new MenuCalculatedField("LDR error", SUBMENU_CALC_PID, AxisMenuHandlers::getBoostZeitronixColumn),
            new MenuCalculatedField("LDR de/dt", SUBMENU_CALC_PID, AxisMenuHandlers::getBoostZeitronixColumn),
            new MenuCalculatedField("LDR I e dt", SUBMENU_CALC_PID, AxisMenuHandlers::getBoostZeitronixColumn),
            new MenuCalculatedField("LDR PID", SUBMENU_CALC_PID, AxisMenuHandlers::getBoostZeitronixColumn)
        ), null),

        // BoostPressureActual -> calculated fields
        new BaseFieldCalculatedFields("BoostPressureActual", Arrays.asList(
            new MenuCalculatedField("BoostActual PR", SUBMENU_BOOST, AxisMenuHandlers::getBoostZeitronixColumn),
            new MenuCalculatedField("Boost Spool Rate (RPM)", SUBMENU_BOOST, AxisMenuHandlers::getBoostZeitronixColumn),
            new MenuCalculatedField("Boost Spool Rate (time)", SUBMENU_BOOST, AxisMenuHandlers::getBoostZeitronixColumn)
        ), null),

        // IgnitionTimingAngleOverall -> calculated fields
        new BaseFieldCalculatedFields("IgnitionTimingAngleOverall", Arrays.asList(
            new MenuCalculatedField("IgnitionTimingAngleOverallDesired", null, AxisMenuHandlers::getIgnitionTimingColumn)  // standalone
        ), null),

        // TorqueDesired -> calculated fields
        new BaseFieldCalculatedFields("TorqueDesired", Arrays.asList(
            new MenuCalculatedField("Engine torque (ft-lb)", null, AxisMenuHandlers::getEngineTorqueHpColumn),  // standalone
            new MenuCalculatedField("Engine HP", null, AxisMenuHandlers::getEngineTorqueHpColumn)  // standalone
        ), null),

        // IntakeAirTemperature -> calculated fields (ME7LOGGER only)
        new BaseFieldCalculatedFields("IntakeAirTemperature", Arrays.asList(
            new MenuCalculatedField("Sim evtmod", SUBMENU_CALC_IAT, AxisMenuHandlers::getBoostZeitronixColumn, true),  // ME7LOGGER only
            new MenuCalculatedField("Sim ftbr", SUBMENU_CALC_IAT, AxisMenuHandlers::getBoostZeitronixColumn, true),  // ME7LOGGER only
            new MenuCalculatedField("Sim BoostIATCorrection", SUBMENU_CALC_IAT, AxisMenuHandlers::getBoostZeitronixColumn, true)  // ME7LOGGER only
        ), null),

        // ME7L ps_w -> calculated fields
        new BaseFieldCalculatedFields("ME7L ps_w", Arrays.asList(
            new MenuCalculatedField("Sim pspvds", SUBMENU_BOOST, AxisMenuHandlers::getBoostZeitronixColumn),
            new MenuCalculatedField("ps_w error", SUBMENU_BOOST, AxisMenuHandlers::getBoostZeitronixColumn)
        ), null),
    };

    /**
     * Get calculated fields for a base field.
     *
     * @param baseFieldId The base field ID to look up
     * @param loggerType The logger type (e.g., "ME7LOGGER") or null for all
     * @return List of calculated fields that should be added for this base field
     */
    public static List<MenuCalculatedField> getCalculatedFieldsForBaseField(String baseFieldId, String loggerType) {
        List<MenuCalculatedField> result = new ArrayList<>();
        for (BaseFieldCalculatedFields mapping : MENU_CALCULATED_FIELDS) {
            if (baseFieldId.matches(mapping.baseFieldPattern)) {
                // Check logger type filter (if specified in mapping, must match)
                if (mapping.loggerTypeFilter == null || mapping.loggerTypeFilter.equals(loggerType)) {
                    // Add fields, filtering out ME7LOGGER-only fields if logger type doesn't match
                    for (MenuCalculatedField field : mapping.calculatedFields) {
                        if (!field.me7loggerOnly || "ME7LOGGER".equals(loggerType)) {
                            result.add(field);
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Find the submenu name for a field ID using pattern matching.
     * Returns null if no pattern matches (field should be handled by special cases).
     *
     * @param id The field ID to look up
     * @return Submenu name if pattern matches, null otherwise
     */
    public static String findSubmenuForField(String id) {
        for (PatternSubmenuPair pair : SUBMENU_PATTERNS) {
            if (pair.pattern.matcher(id).matches()) {
                return pair.submenu;
            }
        }
        return null;
    }

    /**
     * Functional interface for field routing handlers.
     * Each handler takes an AxisMenu instance, field ID, DatasetId, and AbstractButton item,
     * and returns true if it handled the field, false otherwise.
     */
    @FunctionalInterface
    public interface FieldRoutingHandler {
        /**
         * Handle field routing for a specific field.
         *
         * @param menu The AxisMenu instance (for calling addToSubmenu, add, etc.)
         * @param id The field ID string
         * @param dsid The DatasetId
         * @param item The AbstractButton item created for this field
         * @return true if this handler processed the field, false otherwise
         */
        boolean handle(AxisMenu menu, String id, DatasetId dsid, AbstractButton item);
    }

    /**
     * Pattern-to-handler mapping for regex-based routing.
     * Patterns are checked in order (most specific first).
     * Handlers should use switch/case internally for exact field matching.
     *
     * IMPORTANT: Pattern.matcher().matches() requires the entire string to match,
     * so ^ and $ anchors are redundant and ignored. Do NOT use anchors in patterns.
     * For exact matches, use the exact string (e.g., "TorqueDesired" not "^TorqueDesired$").
     * For partial matches, use .* at start/end (e.g., ".*Torque.*" not "^.*Torque.*$").
     */
    public static class PatternRoutingPair {
        public final Pattern pattern;
        public final FieldRoutingHandler handler;

        public PatternRoutingPair(String patternStr, FieldRoutingHandler handler) {
            this.pattern = Pattern.compile(patternStr);
            this.handler = handler;
        }
    }

    /**
     * Regex patterns for pattern-based field routing.
     * Patterns are checked after switch/case for RPM/TIME/Sample.
     * Used to route patterns directly to handlers.
     *
     * Patterns are ordered from most specific to least specific.
     */
    public static final PatternRoutingPair[] PATTERN_ROUTING_HANDLERS = {
        // Exact matches for specific fields (most specific first)
        // Note: No anchors needed - Pattern.matcher().matches() requires full string match
        new PatternRoutingPair("TorqueDesired",
            (menu, id, dsid, item) -> {
                menu.add(item);  /* Must add self - standalone item with derived ft-lb and HP versions */
                menu.addCalculatedFieldsFromData("TorqueDesired", dsid.type);
                return true;
            }),
        new PatternRoutingPair("IntakeAirTemperature",
            (menu, id, dsid, item) -> {
                menu.addToSubmenu(SUBMENU_TEMPERATURE, dsid);
                // Add calculated fields (ME7LOGGER filtering handled in data structure)
                menu.addCalculatedFieldsFromData("IntakeAirTemperature", dsid.type);
                return true;
            }),

        // MAF pattern (must come before Load pattern to catch CalcLoad)
        new PatternRoutingPair(".*(Intake.*Air|MAF|Mass.*Air|Air.*Mass|Mass.*Flow|Intake.*Flow|Airflow).*",
            (menu, id, dsid, item) -> {
                menu.addToSubmenu(SUBMENU_MAF, dsid);
                if (id.equals("MassAirFlow")) {
                    menu.addCalculatedFieldsFromData("MassAirFlow", dsid.type);
                }
                return true;
            }),

        // Fuel pattern
        new PatternRoutingPair(".*(AFR|AdaptationPartial|Injection|Fuel|Lambda|TFT|IDC|Inject|Ethanol|Methanol|E85|[LH]PFP|Rail|Combustion).*",
            (menu, id, dsid, item) -> {
                menu.addToSubmenu(SUBMENU_FUEL, dsid);
                if (id.equals("FuelInjectorOnTime")) {
                    menu.addCalculatedFieldsFromData("FuelInjectorOnTime", dsid.type);
                }
                if (id.equals("EffInjectionTime")) {
                    menu.addCalculatedFieldsFromData("EffInjectionTime", dsid.type);
                }
                if (id.equals("EffInjectionTimeBank2")) {
                    menu.addCalculatedFieldsFromData("EffInjectionTimeBank2", dsid.type);
                }
                return true;
            }),

        // Zeitronix pattern (must come before Boost to get conversions)
        new PatternRoutingPair("Zeitronix.*",
            (menu, id, dsid, item) -> {
                if (id.equals("Zeitronix Boost")) {
                    menu.addCalculatedFieldsFromData("Zeitronix Boost", dsid.type);
                }
                if (id.equals("Zeitronix AFR")) {
                    menu.addCalculatedFieldsFromData("Zeitronix AFR", dsid.type);
                }
                if (id.equals("Zeitronix Lambda")) {
                    menu.addCalculatedFieldsFromData("Zeitronix Lambda", dsid.type);
                }
                menu.addToSubmenu(SUBMENU_ZEITRONIX, dsid);
                return true;
            }),

        // Load pattern (must come before Boost to catch ChargeLimit*Protection)
        new PatternRoutingPair(".*(Load|ChargeLimit.*Protection).*",
            (menu, id, dsid, item) -> {
                menu.addToSubmenu(SUBMENU_LOAD, dsid);
                if (id.matches("EngineLoad(Requested|Corrected)")) {
                    menu.addCalculatedFieldsFromData("EngineLoad(Requested|Corrected)", dsid.type);
                }
                return true;
            }),

        // Boost pattern
        new PatternRoutingPair(".*([Bb]oost|Wastegate|Charge|WGDC|PSI|Baro|Press|PID|Turbine).*",
            (menu, id, dsid, item) -> {
                menu.addToSubmenu(SUBMENU_BOOST, dsid);
                if (id.equals("BoostPressureDesired")) {
                    menu.addCalculatedFieldsFromData("BoostPressureDesired", dsid.type);
                }
                if (id.equals("BoostPressureActual")) {
                    menu.addCalculatedFieldsFromData("BoostPressureActual", dsid.type);
                }
                if (id.equals("BoostPressureDesiredDelta")) {
                    menu.add(new DatasetId("BoostPressureDesired", null, dsid.unit));
                }
                return true;
            }),

        // Throttle pattern (must come before Timing to avoid matching Throttle Angle)
        new PatternRoutingPair(".*(Pedal|Throttle).*",
            (menu, id, dsid, item) -> {
                menu.addToSubmenu(SUBMENU_THROTTLE, dsid);
                return true;
            }),

        // Acceleration pattern
        new PatternRoutingPair(".*Accel.*",
            (menu, id, dsid, item) -> {
                menu.addToSubmenu(SUBMENU_ACCELERATION, dsid);
                return true;
            }),

        // Ignition/Timing pattern
        new PatternRoutingPair(".*(Eta|Avg|Adapted)?(Ign|Timing|Angle|Spark|Combustion).*",
            (menu, id, dsid, item) -> {
                menu.addToSubmenu(SUBMENU_IGNITION, dsid);
                if (id.equals("IgnitionTimingAngleOverall")) {
                    menu.addCalculatedFieldsFromData("IgnitionTimingAngleOverall", dsid.type);
                }
                final AbstractButton titem = menu.makeMenuItem(new DatasetId(id + " (ms)", dsid.id_orig, org.nyet.ecuxplot.UnitConstants.UNIT_MS));
                menu.addToSubmenu(SUBMENU_TRUE_TIMING, titem, true);
                return true;
            }),

        // ME7L pattern
        new PatternRoutingPair("ME7L.*",
            (menu, id, dsid, item) -> {
                menu.addToSubmenu(SUBMENU_ME7_LOGGER, dsid);
                if (id.equals("ME7L ps_w")) {
                    menu.addCalculatedFieldsFromData("ME7L ps_w", dsid.type);
                }
                return true;
            }),
    };

    /**
     * Try pattern-based routing for regex patterns.
     * Returns true if a pattern handler matched and processed the field.
     *
     * @param menu The AxisMenu instance
     * @param id The field ID to route
     * @param dsid The DatasetId
     * @param item The AbstractButton item
     * @return true if a pattern handler matched, false otherwise
     */
    public static boolean tryPatternRouting(AxisMenu menu, String id, DatasetId dsid, AbstractButton item) {
        for (PatternRoutingPair pair : PATTERN_ROUTING_HANDLERS) {
            if (pair.pattern.matcher(id).matches()) {
                if (pair.handler.handle(menu, id, dsid, item)) {
                    return true;
                }
            }
        }
        return false;
    }
}

// vim: set sw=4 ts=8 expandtab:
