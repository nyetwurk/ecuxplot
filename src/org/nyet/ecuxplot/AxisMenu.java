package org.nyet.ecuxplot;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // Calc menu names that should be pre-populated and grouped near the top
    private static final String[] CALC_MENU_NAMES = {
        "Calc Power", "Calc MAF", "Calc Fuel", "Calc Boost", "Calc PID", "Calc IAT", "Acceleration"
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
        if(id.matches("RPM")) {
            // Add RPM and variants to RPM submenu
            // We are guaranteed top placement since the parent RPM menu is the first menu in the menu bar
            addToSubmenu("RPM", dsid);
            addToSubmenu("RPM", "RPM - raw");

            addToSubmenu("Calc Power", "WHP");
            addToSubmenu("Calc Power", "WTQ");
            addToSubmenu("Calc Power", idWithUnit("WTQ", UnitConstants.UNIT_NM));
            addToSubmenu("Calc Power", "HP");
            addToSubmenu("Calc Power", "TQ");
            addToSubmenu("Calc Power", idWithUnit("TQ", UnitConstants.UNIT_NM));
            addToSubmenu("Calc Power", "Drag");

            // Add acceleration items to Acceleration submenu
            addToSubmenu("Acceleration", "Calc Velocity");
            addToSubmenu("Acceleration", "Acceleration (RPM/s)");
            addToSubmenu("Acceleration", "Acceleration - raw (RPM/s)");
            addToSubmenu("Acceleration", "Acceleration (m/s^2)");
            addToSubmenu("Acceleration", "Acceleration (m/s^2) - raw");
            addToSubmenu("Acceleration", "Acceleration (g)");

        } else if(id.matches("TIME")) {
            // Add TIME and variants to TIME submenu
            addToSubmenu("TIME", dsid);
            addToSubmenu("TIME", "TIME - raw");
            addToSubmenu("TIME", "TIME [Range]");
        } else if(id.matches("TIME \\[Range\\]")) {
            // Handle TIME [Range] when processed separately
            addToSubmenu("TIME", dsid);
        } else if(id.matches("Sample")) {
            // Add Sample and variants to Sample submenu
            addToSubmenu("Sample", dsid);
            addToSubmenu("Sample", "Sample [Range]");
        } else if(id.matches("Sample \\[Range\\]")) {
            // Handle Sample [Range] when processed separately
            addToSubmenu("Sample", dsid);

        // goes before .*Load.* to catch CalcLoad
        } else if(id.matches(".*(MAF|Mass *Air|Air *Mass|Mass *Air *Flow).*")) {
            addToSubmenu("MAF", dsid);
            if(id.matches("MassAirFlow")) {
                this.add("MassAirFlow (kg/hr)");
                if (dsid.type.equals("ME7LOGGER")) {
                    addToSubmenu("Calc MAF", "Sim Load");
                    addToSubmenu("Calc MAF", "Sim Load Corrected");
                    addToSubmenu("Calc MAF", "Sim MAF");
                }
                // MAF-related calc items always go to MAF submenu (not Calc MAF) for consistency
                addToSubmenu("MAF", "MassAirFlow df/dt");
                addToSubmenu("MAF", "Turbo Flow");
                addToSubmenu("MAF", "Turbo Flow (lb/min)");
                addToSubmenu("Calc MAF", new JSeparator());
            }
        } else if(id.matches(".*(AFR|AdaptationPartial|Injection|Fuel|Lambda|TFT|IDC|Injector|Methanol|E85|[LH]PFP|Rail).*")) {
            addToSubmenu("Fuel", dsid);
            if(id.matches("FuelInjectorOnTime")) {      // ti
                this.add("FuelInjectorDutyCycle");
            }
            if(id.matches("EffInjectionTime")) {        // te
                this.add("EffInjectorDutyCycle");
                addToSubmenu("Calc Fuel", "Sim Fuel Mass");
                addToSubmenu("Calc Fuel", "Sim AFR");
                addToSubmenu("Calc Fuel", "Sim lambda");
                addToSubmenu("Calc Fuel", "Sim lambda error");
                // addToSubmenu("Calc Fuel", new JSeparator());
            }
            if(id.matches("EffInjectionTimeBank2")) {   // te
                this.add("EffInjectorDutyCycleBank2");
            }
        } else if(id.matches("^Zeitronix.*")) {
            /* do zeitronix before boost so we get the conversions we want */
            if(id.matches("^Zeitronix Boost")) {
                this.add("Zeitronix Boost (PSI)");
                addToSubmenu("Calc Boost", "Boost Spool Rate Zeit (RPM)");
            }
            if(id.matches("^Zeitronix AFR")) {
                this.add("Zeitronix AFR (lambda)");
            }
            if(id.matches("^Zeitronix Lambda")) {
                this.add("Zeitronix Lambda (AFR)");
            }
            addToSubmenu("Zeitronix", dsid);
        } else if(id.matches(".*(Load|Torque|ChargeLimit.*Protection).*")) {
            // before Boost so we catch ChargeLimit*Protection before Charge
            addToSubmenu("Load", dsid);
            if(id.matches("EngineLoad(Requested|Corrected)")) {
                addToSubmenu("Calc Boost", "Sim BoostPressureDesired");
                addToSubmenu("Calc Boost", "Sim LoadSpecified correction");
            }
        } else if(id.matches(".*([Bb]oost|Wastegate|Charge|WGDC|PSI|Baro|Press|PID|Turbine).*")) {
            addToSubmenu("Boost", dsid);
            if(id.matches("BoostPressureDesired")) {
                addToSubmenu("Calc Boost", "BoostDesired PR");
            }
            if(id.matches("BoostPressureActual")) {
                addToSubmenu("Calc Boost", "BoostActual PR");
                addToSubmenu("Calc Boost", "Boost Spool Rate (RPM)");
                addToSubmenu("Calc Boost", "Boost Spool Rate (time)");
                addToSubmenu("Calc PID", "LDR error");
                addToSubmenu("Calc PID", "LDR de/dt");
                addToSubmenu("Calc PID", "LDR I e dt");
                addToSubmenu("Calc PID", "LDR PID");
            }
            /* JB4 does noth boost pressure desired, its calc'd */
            /* "target" is this delta */
            if(id.matches("BoostPressureDesiredDelta")) {
                this.add(new DatasetId("BoostPressureDesired", null, dsid.unit));
            }
        /* do this before Timing so we don't match Throttle Angle */
        } else if(id.matches(".*(Pedal|Throttle).*")) {
            addToSubmenu("Throttle", dsid);
        } else if(id.matches(".*(Eta|Avg|Adapted)?(Ign|Timing|Angle|Spark|Combustion).*")) {
            addToSubmenu("Ignition", dsid);
            if(id.matches("IgnitionTimingAngleOverall")) {
                this.add("IgnitionTimingAngleOverallDesired");
            }
            final AbstractButton titem = makeMenuItem(new DatasetId(id + " (ms)", dsid.id2, UnitConstants.UNIT_MS));
            addToSubmenu("TrueTiming", titem, true);
        } else if(id.matches(".*(Cam|NWS|Valve).*")) {
            addToSubmenu("VVT", dsid);
        } else if(id.matches("(Cat|MainCat).*")) {
            addToSubmenu("Cats", dsid);
        } else if(id.matches(".*EGT.*")) {
            addToSubmenu("EGT", dsid);
        } else if(id.matches(".*(Idle|Idling).*")) {
            addToSubmenu("Idle", dsid);
        } else if(id.matches(".*[Kk]nock.*")) {
            addToSubmenu("Knock", dsid);
        } else if(id.matches(".*Misfire.*")) {
            addToSubmenu("Misfires", dsid);
        } else if(id.matches(".*(OXS|O2|ResistanceSensor).*")) {
            addToSubmenu("O2 Sensor(s)", dsid);
        } else if(id.matches("VehicleSpeed")) {
            addToSubmenu("Vehicle Speed", dsid);
        } else if(id.matches("TorqueDesired")) {
            this.add(item);  /* Must add self - standalone item with derived ft-lb and HP versions */
            this.add("Engine torque (ft-lb)");
            this.add("Engine HP");
        } else if(id.matches("IntakeAirTemperature")) {
            addToSubmenu("Temperature", dsid);
            if (dsid.type.equals("ME7LOGGER")) {
                addToSubmenu("Calc IAT", "Sim evtmod");
                addToSubmenu("Calc IAT", "Sim ftbr");
                addToSubmenu("Calc IAT", "Sim BoostIATCorrection");
            }
        } else if(id.matches(".*Temperature.*")) {
            addToSubmenu("Temperature", dsid);
        } else if(id.matches(".*VV.*")) {       // EvoScan
            addToSubmenu("VVT", dsid);
        } else if(id.matches("^Log.*")) {       // EvoScan
            addToSubmenu("EvoScan", dsid);
        } else if(id.matches("^ME7L.*")) {
            addToSubmenu("ME7 Logger", dsid);
            if(id.matches("ME7L ps_w")) {
                addToSubmenu("Calc Boost", "Sim pspvds");
                addToSubmenu("Boost", "ps_w error");
            }
        } else {
            this.add(item);
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

            // Pre-populate RPM, TIME, and Sample submenus for all axes (same pattern as calc menus)
            String[] baseMenus = {"RPM", "TIME", "Sample"};
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
            final JMenu me7l=this.subMenus.get("ME7 Logger");
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
