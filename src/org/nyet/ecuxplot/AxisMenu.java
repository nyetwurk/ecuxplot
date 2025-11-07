package org.nyet.ecuxplot;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.awt.Component;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JCheckBox;
import javax.swing.JSeparator;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JComponent;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;

import org.nyet.util.MenuListener;
import org.nyet.util.SubActionListener;
import org.nyet.logfile.Dataset.DatasetId;

public class AxisMenu extends JMenu {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private final SubActionListener listener;
    private ButtonGroup buttonGroup = null;
    private final boolean radioButton;
    private final Comparable<?>[] initialChecked;

    private final HashMap<String, AbstractButton> members =
        new HashMap<String, AbstractButton>();

    private final HashMap<String, JMenu> subMenus =
        new HashMap<String, JMenu>();

    private int count=0;
    private int maxItems=20;
    private AxisMenu more=null;
    private AxisMenu parent=null;

    /**
     * Creates a multiline HTML tooltip for calculation descriptions.
     * Format:
     *   [processing step 1]
     *   [processing step 2]
     *   ...
     *   Range-aware: [range-aware smoothing] (optional)
     *
     * @param processingSteps Array of processing steps (each on a separate line)
     * @param rangeAware Optional range-aware smoothing description (null to omit)
     * @return HTML-formatted tooltip string
     */
    private String createCalcTooltip(String[] processingSteps, String rangeAware) {
        StringBuilder sb = new StringBuilder("<html>");
        for (int i = 0; i < processingSteps.length; i++) {
            if (i > 0) sb.append("<br>");
            sb.append(processingSteps[i]);
        }
        if (rangeAware != null && !rangeAware.isEmpty()) {
            if (processingSteps.length > 0) sb.append("<br>");
            sb.append("Range-aware: ").append(rangeAware);
        }
        sb.append("</html>");
        return sb.toString();
    }

    /**
     * Check if debug logging is enabled (verbose >= 1, i.e., -v or -vv flag).
     * @return true if debug level is enabled
     */
    private boolean isDebugEnabled() {
        // Check if listener is ECUxPlot and has verbose option set
        if (this.listener instanceof ECUxPlot) {
            ECUxPlot plot = (ECUxPlot) this.listener;
            return plot.getVerbose() > 0;
        }
        return false;
    }

    /**
     * Submenu name constants - single source of truth for all submenu names.
     * Use these constants instead of string literals throughout the code.
     */
    private static final String SUBMENU_RPM = "RPM";
    private static final String SUBMENU_TIME = "TIME";
    private static final String SUBMENU_SAMPLE = "Sample";
    private static final String SUBMENU_SPEED = "Speed";
    private static final String SUBMENU_MAF = "MAF";
    private static final String SUBMENU_FUEL = "Fuel";
    private static final String SUBMENU_BOOST = "Boost";
    private static final String SUBMENU_THROTTLE = "Throttle";
    private static final String SUBMENU_IGNITION = "Ignition";
    private static final String SUBMENU_TRUE_TIMING = "TrueTiming";
    private static final String SUBMENU_TEMPERATURE = "Temperature";
    private static final String SUBMENU_VVT = "VVT";
    private static final String SUBMENU_CATS = "Cats";
    private static final String SUBMENU_EGT = "EGT";
    private static final String SUBMENU_IDLE = "Idle";
    private static final String SUBMENU_KNOCK = "Knock";
    private static final String SUBMENU_MISFIRES = "Misfires";
    private static final String SUBMENU_O2_SENSORS = "O2 Sensor(s)";
    private static final String SUBMENU_TORQUE = "Torque";
    private static final String SUBMENU_ZEITRONIX = "Zeitronix";
    private static final String SUBMENU_LOAD = "Load";
    private static final String SUBMENU_ME7_LOGGER = "ME7 Logger";
    private static final String SUBMENU_EVOSCAN = "EvoScan";

    private static final String SUBMENU_ACCELERATION = "Acceleration";
    private static final String SUBMENU_POWER = "Power";
    private static final String SUBMENU_CALC_MAF = "Calc MAF";
    private static final String SUBMENU_CALC_FUEL = "Calc Fuel";
    private static final String SUBMENU_CALC_PID = "Calc PID";
    private static final String SUBMENU_CALC_IAT = "Calc IAT";

    // Base menu names that should be pre-populated and available for X-axis
    private static final String[] BASE_MENU_NAMES = {
        SUBMENU_RPM, SUBMENU_TIME, SUBMENU_SAMPLE, SUBMENU_SPEED
    };

    // Calc menu names that should be pre-populated and grouped near the top
    private static final String[] CALC_MENU_NAMES = {
        SUBMENU_ACCELERATION, SUBMENU_POWER, SUBMENU_BOOST, SUBMENU_MAF, SUBMENU_FUEL,
        SUBMENU_CALC_PID, SUBMENU_CALC_IAT
    };

    /**
     * Check if a menu has any items (excluding separators).
     */
    private boolean hasMenuItems(JMenu menu) {
        if (menu == null) return false;
        for (int i = 0; i < menu.getMenuComponentCount(); i++) {
            Component comp = menu.getMenuComponent(i);
            if (!(comp instanceof JSeparator)) {
                return true;
            }
        }
        return false;
    }

    private void addToSubmenu(String submenu, JComponent item, boolean autoadd) {
        JMenu sub = this.subMenus.get(submenu);
        if(sub==null) {
            // Don't add "..." here - the AxisMenu(String, AxisMenu) constructor will add it
            sub = new AxisMenu(submenu, this);
            this.subMenus.put(submenu, sub);
            if(autoadd) this.add(sub);
        }
        sub.add(item);
        if (item instanceof AbstractButton) {
            AbstractButton b = (AbstractButton)item;
            this.members.put(b.getText(), b);
        }
    }

    /**
     * Add a menu item to a submenu.
     * NOTE: Prefer addToSubmenu(String, String, String) or addToSubmenu(String, DatasetId) to enable unit conversions.
     * This overload is only for JSeparators and similar components.
     */
    private void addToSubmenu(String submenu, JComponent item) {
        addToSubmenu(submenu, item, true);
    }

    /**
     * Add a menu item to a submenu without units (for calculated/derived fields).
     * This calls addToSubmenu(String, DatasetId) with null units.
     */
    private void addToSubmenu(String submenu, String id) {
        addToSubmenu(submenu, new DatasetId(id));
    }

    /**
     * Add a menu item to a submenu WITH units from DatasetId.
     * This enables automatic unit conversion menu items.
     *
     * Recursion Prevention:
     * This method automatically creates unit-converted menu items (e.g., "VehicleSpeed (mph)")
     * from the original field and its unit. To prevent infinite recursion:
     * 1. Track column state (ORIGINAL vs UNIT_CONVERTED) in columnStates Map
     * 2. Only process each column once
     * 3. For converted items, pass a dummy DatasetId with null unit to prevent further conversions
     *
     * Example: For "VehicleSpeed" with unit "km/h":
     * 1. Adds "VehicleSpeed" menu item (state: ORIGINAL)
     * 2. Calls Units.getAlternateUnits("km/h") → returns ["mph"]
     * 3. Adds "VehicleSpeed (mph)" menu item (state: UNIT_CONVERTED)
     * 4. Since convertedId has no unit, no further conversions are attempted
     *
     * PREFERRED: Use this overload to ensure units are always passed.
     */
    private void addToSubmenu(String submenu, DatasetId dsid) {
        final AbstractButton item = makeMenuItem(dsid);
        addToSubmenu(submenu, item, true);

        // Track this column's state
        columnStates.put(dsid.id, ColumnState.ORIGINAL);

        // Automatically add unit conversions if available (but avoid recursion)
        if (dsid.unit != null) {
            List<String> alternates = Units.getAlternateUnits(dsid.unit);
            for (String alt : alternates) {
                String convertedId = dsid.id + " (" + alt + ")";
                // Only add if we haven't already processed this conversion
                if (!columnStates.containsKey(convertedId)) {
                    columnStates.put(convertedId, ColumnState.UNIT_CONVERTED);
                    // For unit conversions, pass a dummy DatasetId with no units to avoid recursion
                    DatasetId dummyDsid = new DatasetId(convertedId);
                    addToSubmenu(submenu, dummyDsid); // Already a conversion, don't recurse
                }
            }
        }
    }

    // Track the state of columns to prevent recursion
    private enum ColumnState {
        ORIGINAL,      // Original field from data
        UNIT_CONVERTED // Field with unit conversion applied
    }

    private final Map<String, ColumnState> columnStates = new HashMap<>();

    /**
     * Data structure for RPM-triggered calculated fields.
     * Separates data (what fields to add) from code (how to add them).
     */
    private static class RpmCalculatedField {
        final String fieldName;
        final String submenu;
        final String[] tooltipSteps;
        final String rangeAwareSmoothing;  // null if none
        final boolean debugOnly;
        final String unitConversion;  // null if none (e.g., "Nm" for unit conversion)

        RpmCalculatedField(String fieldName, String submenu, String[] tooltipSteps,
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
     * Calculated fields that are added when "RPM" field is detected.
     * This data structure separates what fields to add from how to add them.
     */
    private static final RpmCalculatedField[] RPM_CALCULATED_FIELDS = {
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
     * Add calculated fields that are triggered when RPM is detected.
     * Uses data-driven approach to separate field definitions from creation logic.
     */
    private void addRpmCalculatedFields() {
        for (RpmCalculatedField field : RPM_CALCULATED_FIELDS) {
            // Skip debug-only fields if debug not enabled
            if (field.debugOnly && !isDebugEnabled()) {
                continue;
            }

            // Handle unit conversion fields
            if (field.unitConversion != null) {
                String fieldId = idWithUnit(field.fieldName, field.unitConversion);
                addToSubmenu(field.submenu, fieldId);
                continue;
            }

            // Regular calculated field
            AbstractButton item = makeMenuItem(new DatasetId(field.fieldName));
            if (field.tooltipSteps != null && field.tooltipSteps.length > 0) {
                item.setToolTipText(createCalcTooltip(field.tooltipSteps, field.rangeAwareSmoothing));
            }
            addToSubmenu(field.submenu, item, true);
        }
    }

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
     */
    private static class PatternSubmenuPair {
        final Pattern pattern;
        final String submenu;

        PatternSubmenuPair(String patternStr, String submenu) {
            this.pattern = Pattern.compile(patternStr);
            this.submenu = submenu;
        }
    }

    private static final PatternSubmenuPair[] SUBMENU_PATTERNS = {
        // Note: Pattern.matches() requires the entire string to match, so ^ and $ anchors are ignored/redundant
        // Patterns use .* at start/end to match anywhere within the field name

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
     * Find the submenu name for a field ID using pattern matching.
     * Returns null if no pattern matches (field should be handled by special cases).
     *
     * @param id The field ID to look up
     * @return Submenu name if pattern matches, null otherwise
     */
    private String findSubmenuForField(String id) {
        for (PatternSubmenuPair pair : SUBMENU_PATTERNS) {
            if (pair.pattern.matcher(id).matches()) {
                return pair.submenu;
            }
        }
        return null;
    }

    private AbstractButton makeMenuItem(DatasetId dsid) {
        boolean checked = false;

        for (final Comparable<?> element : this.initialChecked) {
            if(dsid.id.equals(element)) {
                checked = true;
                break;
            }
        }

        final AbstractButton item = (this.buttonGroup==null)?new JCheckBox(dsid.id, checked):
            new JRadioButtonMenuItem(dsid.id, checked);

        // Only set tooltip if id2 exists and is different from the menu item text
        if(dsid.id2!=null && !dsid.id2.equals(dsid.id)) {
            item.setToolTipText(dsid.id2);
        }

        item.addActionListener(new MenuListener(this.listener, this.getText()));
        if(this.buttonGroup!=null) this.buttonGroup.add(item);

        return item;
    }

    /* string */
    /* process through this.add() to detect submenu */
    /* override add */
    public JMenuItem add(String id) {
        return this.add(new DatasetId(id));
    }

    /* dsid */
    private JMenuItem add(DatasetId dsid) {
        /* Return null if item already exists (duplicate) */
        if (this.members.containsKey(dsid.id)) return null;

        final AbstractButton item = makeMenuItem(dsid);

        final String id = dsid.id;

        // Special cases with nested conditionals or calculated fields (checked first)
        // These take precedence over pattern table because they have complex logic
        if(id.matches("RPM")) {
            // Add RPM and variants to RPM submenu
            // We are guaranteed top placement since the parent RPM menu is the first menu in the menu bar
            addToSubmenu(SUBMENU_RPM, dsid);
            addToSubmenu(SUBMENU_RPM, "RPM - raw");

            // Add debug columns if debug level is enabled
            if (isDebugEnabled()) {
                addToSubmenu(SUBMENU_RPM, "RPM - base");  // Base RPM for range detection (debug only)
            }

            // Add calculated fields using data-driven approach
            addRpmCalculatedFields();

        } else if(id.matches("TIME")) {
            // Add TIME and variants to TIME submenu
            addToSubmenu(SUBMENU_TIME, dsid);
            addToSubmenu(SUBMENU_TIME, "TIME - raw");
            addToSubmenu(SUBMENU_TIME, "TIME [Range]");
        } else if(id.matches("Sample")) {
            // Add Sample and variants to Sample submenu
            addToSubmenu(SUBMENU_SAMPLE, dsid);
            addToSubmenu(SUBMENU_SAMPLE, "Sample [Range]");
        } else if(id.matches("TIME \\[Range\\]")) {
            // Handle TIME [Range] when processed separately
            // (Also in pattern table, but explicit check ensures correct handling)
            addToSubmenu(SUBMENU_TIME, dsid);
        } else if(id.matches("Sample \\[Range\\]")) {
            // Handle Sample [Range] when processed separately
            // (Also in pattern table, but explicit check ensures correct handling)
            addToSubmenu(SUBMENU_SAMPLE, dsid);
        } else if(id.matches(".*(Intake.*Air|MAF|Mass.*Air|Air.*Mass|Mass.*Flow|Intake.*Flow|Airflow).*")) {
            // goes before .*Load.* to catch CalcLoad
            addToSubmenu(SUBMENU_MAF, dsid);
            if(id.matches("MassAirFlow")) {
                this.add("MassAirFlow (kg/hr)");
                if (dsid.type.equals("ME7LOGGER")) {
                    addToSubmenu(SUBMENU_CALC_MAF, "Sim Load");
                    addToSubmenu(SUBMENU_CALC_MAF, "Sim Load Corrected");
                    addToSubmenu(SUBMENU_CALC_MAF, "Sim MAF");
                }
                // MAF-related calc items always go to MAF submenu (not Calc MAF) for consistency
                addToSubmenu(SUBMENU_MAF, "MassAirFlow df/dt");
                addToSubmenu(SUBMENU_MAF, "Turbo Flow");
                addToSubmenu(SUBMENU_MAF, "Turbo Flow (lb/min)");
                addToSubmenu(SUBMENU_CALC_MAF, new JSeparator());
            }
        } else if(id.matches(".*(AFR|AdaptationPartial|Injection|Fuel|Lambda|TFT|IDC|Inject|Ethanol|Methanol|E85|[LH]PFP|Rail|Combustion).*")) {
            addToSubmenu(SUBMENU_FUEL, dsid);
            if(id.matches("FuelInjectorOnTime")) {      // ti
                this.add("FuelInjectorDutyCycle");
            }
            if(id.matches("EffInjectionTime")) {        // te
                this.add("EffInjectorDutyCycle");
                addToSubmenu(SUBMENU_CALC_FUEL, "Sim Fuel Mass");
                addToSubmenu(SUBMENU_CALC_FUEL, "Sim AFR");
                addToSubmenu(SUBMENU_CALC_FUEL, "Sim lambda");
                addToSubmenu(SUBMENU_CALC_FUEL, "Sim lambda error");
                // addToSubmenu(SUBMENU_CALC_FUEL, new JSeparator());
            }
            if(id.matches("EffInjectionTimeBank2")) {   // te
                this.add("EffInjectorDutyCycleBank2");
            }
        } else if(id.matches("^Zeitronix.*")) {
            /* do zeitronix before boost so we get the conversions we want */
            if(id.matches("^Zeitronix Boost")) {
                this.add("Zeitronix Boost (PSI)");
                addToSubmenu(SUBMENU_BOOST, "Boost Spool Rate Zeit (RPM)");
            }
            if(id.matches("^Zeitronix AFR")) {
                this.add("Zeitronix AFR (lambda)");
            }
            if(id.matches("^Zeitronix Lambda")) {
                this.add("Zeitronix Lambda (AFR)");
            }
            addToSubmenu(SUBMENU_ZEITRONIX, dsid);
        } else if(id.matches(".*(Load|ChargeLimit.*Protection).*")) {
            // before Boost so we catch ChargeLimit*Protection before Charge
            addToSubmenu(SUBMENU_LOAD, dsid);
            if(id.matches("EngineLoad(Requested|Corrected)")) {
                addToSubmenu(SUBMENU_BOOST, "Sim BoostPressureDesired");
                addToSubmenu(SUBMENU_BOOST, "Sim LoadSpecified correction");
            }
        } else if(id.matches(".*([Bb]oost|Wastegate|Charge|WGDC|PSI|Baro|Press|PID|Turbine).*")) {
            addToSubmenu(SUBMENU_BOOST, dsid);
            if(id.matches("BoostPressureDesired")) {
                addToSubmenu(SUBMENU_BOOST, "BoostDesired PR");
                addToSubmenu(SUBMENU_CALC_PID, "LDR error");
                addToSubmenu(SUBMENU_CALC_PID, "LDR de/dt");
                addToSubmenu(SUBMENU_CALC_PID, "LDR I e dt");
                addToSubmenu(SUBMENU_CALC_PID, "LDR PID");
            }
            if(id.matches("BoostPressureActual")) {
                addToSubmenu(SUBMENU_BOOST, "BoostActual PR");
                addToSubmenu(SUBMENU_BOOST, "Boost Spool Rate (RPM)");
                addToSubmenu(SUBMENU_BOOST, "Boost Spool Rate (time)");
            }
            /* JB4 does noth boost pressure desired, its calc'd */
            /* "target" is this delta */
            if(id.matches("BoostPressureDesiredDelta")) {
                this.add(new DatasetId("BoostPressureDesired", null, dsid.unit));
            }
        /* do this before Timing so we don't match Throttle Angle */
        } else if(id.matches(".*(Pedal|Throttle).*")) {
            addToSubmenu(SUBMENU_THROTTLE, dsid);
        } else if(id.matches(".*Accel.*")) {
            addToSubmenu(SUBMENU_ACCELERATION, dsid);
        } else if(id.matches(".*(Eta|Avg|Adapted)?(Ign|Timing|Angle|Spark|Combustion).*")) {
            addToSubmenu(SUBMENU_IGNITION, dsid);
            if(id.matches("IgnitionTimingAngleOverall")) {
                this.add("IgnitionTimingAngleOverallDesired");
            }
            final AbstractButton titem = makeMenuItem(new DatasetId(id + " (ms)", dsid.id2, UnitConstants.UNIT_MS));
            addToSubmenu(SUBMENU_TRUE_TIMING, titem, true);
        } else if(id.matches("TorqueDesired")) {
            this.add(item);  /* Must add self - standalone item with derived ft-lb and HP versions */
            this.add("Engine torque (ft-lb)");
            this.add("Engine HP");
        } else if(id.matches("IntakeAirTemperature")) {
            addToSubmenu(SUBMENU_TEMPERATURE, dsid);
            if (dsid.type.equals("ME7LOGGER")) {
                addToSubmenu(SUBMENU_CALC_IAT, "Sim evtmod");
                addToSubmenu(SUBMENU_CALC_IAT, "Sim ftbr");
                addToSubmenu(SUBMENU_CALC_IAT, "Sim BoostIATCorrection");
            }
        } else if(id.matches("^ME7L.*")) {
            addToSubmenu(SUBMENU_ME7_LOGGER, dsid);
            if(id.matches("ME7L ps_w")) {
                addToSubmenu(SUBMENU_BOOST, "Sim pspvds");
                addToSubmenu(SUBMENU_BOOST, "ps_w error");
            }
        } else {
            // Try pattern table for simple routing cases (after all special cases)
            String submenu = findSubmenuForField(id);
            if (submenu != null) {
                addToSubmenu(submenu, dsid);
            } else {
                // No pattern match, add as standalone item
                this.add(item);
            }
        }

        this.members.put(dsid.id, item);

        /* Return the item if it's a JMenuItem (e.g., JRadioButtonMenuItem),
         * otherwise return null (e.g., for JCheckBox) */
        if (item instanceof JMenuItem) {
            return (JMenuItem) item;
        }
        return null;
    }

    // constructors
    public AxisMenu(String text, DatasetId[] ids, SubActionListener listener,
        boolean radioButton, Comparable<?>[] initialChecked) {
        this(text, ids, listener, radioButton, initialChecked, -1);
    }
    public AxisMenu(String text, DatasetId[] ids, SubActionListener listener,
        boolean radioButton, Comparable<?>[] initialChecked, int maxItems) {

        super(text);

        this.listener = listener;
        this.radioButton = radioButton;
        this.initialChecked = initialChecked;
        if (maxItems>0) this.maxItems = maxItems;

        if (ids!=null) {
            // Pre-populate all calc menus and add them to the menu near the top
            for (String calcMenuName : CALC_MENU_NAMES) {
                if (!this.subMenus.containsKey(calcMenuName)) {
                    AxisMenu calcMenu = new AxisMenu(calcMenuName, this);
                    this.subMenus.put(calcMenuName, calcMenu);
                }
            }

            // Pre-populate RPM, TIME, Sample, and Speed submenus for all axes (same pattern as calc menus)
            String[] baseMenus = BASE_MENU_NAMES;
            for (String menuName : baseMenus) {
                if (!this.subMenus.containsKey(menuName)) {
                    AxisMenu baseMenu = new AxisMenu(menuName, this);
                    this.subMenus.put(menuName, baseMenu);
                }
            }

            /* top level menu (before "more...") */
            if(radioButton) {
                this.buttonGroup = new ButtonGroup();
                // Add base menus to X-axis (radioButton menu) - will be removed if empty
                for (String baseMenuName : baseMenus) {
                    JMenu baseMenu = this.subMenus.get(baseMenuName);
                    if (baseMenu != null) {
                        super.add(baseMenu);
                    }
                }
                this.add(new JSeparator());
            }

            // Add calc menus and base menus (RPM, TIME, Sample) near the top (only for non-X axis menus, X axis uses radioButton)
            if (!radioButton) {
                // Add base menus first (RPM, TIME, Sample) - same pattern as calc menus
                super.add(new JSeparator());
                for (String baseMenuName : baseMenus) {
                    JMenu baseMenu = this.subMenus.get(baseMenuName);
                    if (baseMenu != null) {
                        super.add(baseMenu);
                    }
                }
                // Add separator between base menus and calc menus
                super.add(new JSeparator());
                // Add calc menus after base menus
                for (String calcMenuName : CALC_MENU_NAMES) {
                    JMenu calcMenu = this.subMenus.get(calcMenuName);
                    if (calcMenu != null) {
                        super.add(calcMenu);
                    }
                }
                super.add(new JSeparator());
            }

            // Process all items to populate submenus
            for(int i=0;i<ids.length;i++) {
                if(ids[i] == null) continue;
                if(ids[i].id.length()>0 && !this.members.containsKey(ids[i].id)) {
                    this.add(ids[i]);
                }
            }

            // Ensure Sample submenu is populated even if "Sample" isn't in DatasetIds
            if (!this.members.containsKey("Sample")) {
                this.add("Sample");
            }

            // put ME7Log next
            final JMenu me7l=this.subMenus.get(SUBMENU_ME7_LOGGER);
            if(me7l!=null) {
                super.add(new JSeparator());
                super.add(me7l);
            }

            // put More.. next
            if(this.more!=null) {
                super.add(new JSeparator());
                super.add(this.more);
            }

            // Remove empty menus last (calc menus and base menus) - same pattern for all
            for (String calcMenuName : CALC_MENU_NAMES) {
                JMenu calcMenu = this.subMenus.get(calcMenuName);
                if (calcMenu != null && !hasMenuItems(calcMenu)) {
                    this.remove(calcMenu);
                }
            }
            for (String baseMenuName : baseMenus) {
                JMenu baseMenu = this.subMenus.get(baseMenuName);
                if (baseMenu != null && !hasMenuItems(baseMenu)) {
                    this.remove(baseMenu);
                }
            }
        }

        // add "Remove all" to top level menu
        if(ids!=null && !radioButton) {
            super.add(new JSeparator());
            final JMenuItem item=new JMenuItem("Remove all");
            super.add(item);
            item.addActionListener(new MenuListener(listener,this.getText()));
        }
    }

    public AxisMenu(String text, DatasetId[] ids, SubActionListener listener,
        boolean radioButton, Comparable<?> initialChecked) {
        this(text, ids, listener, radioButton,
            new Comparable [] {initialChecked});
    }

    public AxisMenu(String id, AxisMenu parent) {
        this(id + "...", null, parent.listener, parent.radioButton, parent.initialChecked, parent.maxItems);
        this.parent = parent;
    }
    // end constructors

    public Component add(JMenu item) {return add(item, false);}
    @Override
    public Component add(Component item) {return add(item, false);}
    private Component add(Component item, boolean force) {
        if (this.more==null && item instanceof JSeparator) {
            final Component c=this.getMenuComponent(this.getMenuComponentCount()-1);
            if (c==null || (c instanceof JSeparator))
                return null;
            return super.add(item);
        }

        // recursively cascade too many items into a "more" submenu
        if(force || this.count++<this.maxItems) {
            return super.add(item);
        }

        if (this.more == null) {
            this.more = new AxisMenu("More...", this);
            // if this is root, delay add until later so its closer to the bottom
            if (this.parent != null) {
                this.add(new JSeparator());
                this.add(this.more, true);
            }
        }
        // will recurse until it fits
        return this.more.add(item);
    }

    public void uncheckAll() {
        // Recursively find and uncheck all AbstractButtons in the entire menu tree
        // This ensures we catch all items regardless of how they're tracked
        uncheckAllRecursive(this);
    }

    private void uncheckAllRecursive(Component comp) {
        if(comp == null) return;

        // If this is an AbstractButton, uncheck it
        if(comp instanceof AbstractButton) {
            ((AbstractButton)comp).setSelected(false);
        }

        // If this is a menu, recursively process all its components
        if(comp instanceof JMenu) {
            JMenu menu = (JMenu)comp;
            for(int i = 0; i < menu.getMenuComponentCount(); i++) {
                uncheckAllRecursive(menu.getMenuComponent(i));
            }
        }
    }

    public void setSelected(Comparable<?> key) {
        final AbstractButton item = this.members.get(key);
        if(item!=null) item.setSelected(true);
    }

    public void setSelected(Comparable<?>[] keys) {
        for (final Comparable<?> key : keys) {
            this.setSelected(key);
        }
    }

    public void setOnlySelected(Comparable<?>[] keys) {
        this.setOnlySelected(new HashSet<Comparable<?>>(Arrays.asList(keys)));
    }

    public void setOnlySelected(Set<Comparable<?>> keys) {
        for(final String ik : this.members.keySet()) {
            final AbstractButton item = this.members.get(ik);
            item.setSelected(keys.contains(ik));
        }
    }

    /**
     * Helper method to construct a unit-converted column ID.
     * @param originalId The original column ID (e.g., "WTQ")
     * @param unit The target unit constant (e.g., UnitConstants.UNIT_NM)
     * @return Formatted string like "WTQ (Nm)"
     */
    private static String idWithUnit(String originalId, String unit) {
        return String.format("%s (%s)", originalId, unit);
    }
}

// vim: set sw=4 ts=8 expandtab:
