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
import org.nyet.ecuxplot.AxisMenuItems.MenuCalculatedField;

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
    boolean isDebugEnabled() {
        // Check if listener is ECUxPlot and has verbose option set
        if (this.listener instanceof ECUxPlot) {
            ECUxPlot plot = (ECUxPlot) this.listener;
            return plot.getVerbose() > 0;
        }
        return false;
    }

    // Submenu constants are now in AxisMenuItems - use AxisMenuItems.SUBMENU_* constants
    // Base menu names that should be pre-populated and available for X-axis
    private static final String[] BASE_MENU_NAMES = {
        AxisMenuItems.SUBMENU_RPM, AxisMenuItems.SUBMENU_TIME, AxisMenuItems.SUBMENU_SAMPLE, AxisMenuItems.SUBMENU_SPEED
    };

    // Calc menu names that should be pre-populated and grouped near the top
    private static final String[] CALC_MENU_NAMES = {
        AxisMenuItems.SUBMENU_ACCELERATION, AxisMenuItems.SUBMENU_POWER, AxisMenuItems.SUBMENU_BOOST,
        AxisMenuItems.SUBMENU_MAF, AxisMenuItems.SUBMENU_FUEL,
        AxisMenuItems.SUBMENU_CALC_PID, AxisMenuItems.SUBMENU_CALC_IAT
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

    void addToSubmenu(String submenu, JComponent item, boolean autoadd) {
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
    void addToSubmenu(String submenu, JComponent item) {
        addToSubmenu(submenu, item, true);
    }

    /**
     * Add a menu item to a submenu without units (for calculated/derived fields).
     * This calls addToSubmenu(String, DatasetId) with null units.
     */
    void addToSubmenu(String submenu, String id) {
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
    void addToSubmenu(String submenu, DatasetId dsid) {
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
     * Add calculated fields that are triggered when RPM is detected.
     * Uses data-driven approach to separate field definitions from creation logic.
     */
    void addRpmCalculatedFields() {
        for (AxisMenuItems.RpmCalculatedField field : AxisMenuItems.RPM_CALCULATED_FIELDS) {
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

    AbstractButton makeMenuItem(DatasetId dsid) {
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
    /**
     * Add calculated fields from AxisMenuItems data structure.
     * This method retrieves calculated fields for a base field and adds them to the menu.
     *
     * @param baseFieldId The base field ID (e.g., "MassAirFlow")
     * @param loggerType The logger type (e.g., "ME7LOGGER") or null
     */
    void addCalculatedFieldsFromData(String baseFieldId, Object loggerType) {
        String loggerTypeStr = (loggerType != null) ? loggerType.toString() : null;
        List<MenuCalculatedField> fields = AxisMenuItems.getCalculatedFieldsForBaseField(baseFieldId, loggerTypeStr);
        for (MenuCalculatedField field : fields) {
            if (field.submenu == null) {
                // Standalone item (this.add)
                this.add(field.fieldName);
            } else {
                // Submenu item
                addToSubmenu(field.submenu, field.fieldName);
            }
        }
    }

    /* process through this.add() to detect submenu */
    /* override add */
    public JMenuItem add(String id) {
        return this.add(new DatasetId(id));
    }

    /* dsid */
    JMenuItem add(DatasetId dsid) {
        /* Return null if item already exists (duplicate) */
        if (this.members.containsKey(dsid.id)) return null;

        final AbstractButton item = makeMenuItem(dsid);

        final String id = dsid.id;

        // Try exact string matches with switch/case first (fastest, most specific)
        boolean handled = false;
        switch (id) {
            case "RPM": {
                // Add RPM and variants to RPM submenu
                // We are guaranteed top placement since the parent RPM menu is the first menu in the menu bar
                addToSubmenu(AxisMenuItems.SUBMENU_RPM, dsid);
                addToSubmenu(AxisMenuItems.SUBMENU_RPM, "RPM - raw");

                // Add debug columns if debug level is enabled
                if (isDebugEnabled()) {
                    addToSubmenu(AxisMenuItems.SUBMENU_RPM, "RPM - base");  // Base RPM for range detection (debug only)
                }

                // Add calculated fields using data-driven approach
                addRpmCalculatedFields();
                handled = true;
                break;
            }
            case "TIME": {
                // Add TIME and variants to TIME submenu
                addToSubmenu(AxisMenuItems.SUBMENU_TIME, dsid);
                addToSubmenu(AxisMenuItems.SUBMENU_TIME, "TIME - raw");
                addToSubmenu(AxisMenuItems.SUBMENU_TIME, "TIME [Range]");
                handled = true;
                break;
            }
            case "Sample": {
                // Add Sample and variants to Sample submenu
                addToSubmenu(AxisMenuItems.SUBMENU_SAMPLE, dsid);
                addToSubmenu(AxisMenuItems.SUBMENU_SAMPLE, "Sample [Range]");
                handled = true;
                break;
            }
            case "TIME [Range]": {
                // Handle TIME [Range] when processed separately
                // (Also in pattern table, but explicit check ensures correct handling)
                addToSubmenu(AxisMenuItems.SUBMENU_TIME, dsid);
                handled = true;
                break;
            }
            case "Sample [Range]": {
                // Handle Sample [Range] when processed separately
                // (Also in pattern table, but explicit check ensures correct handling)
                addToSubmenu(AxisMenuItems.SUBMENU_SAMPLE, dsid);
                handled = true;
                break;
            }
            default: {
                // Not an exact match - will try pattern routing below
                break;
            }
        }

        // If switch/case handled it, we're done
        if (handled) {
            this.members.put(dsid.id, item);
            if (item instanceof JMenuItem) {
                return (JMenuItem) item;
            }
            return null;
        }

        // Try pattern-based routing (for regex patterns, order matters)
        if (AxisMenuItems.tryPatternRouting(this, id, dsid, item)) {
            // Pattern handler processed the field
            this.members.put(dsid.id, item);
            if (item instanceof JMenuItem) {
                return (JMenuItem) item;
            }
            return null;
        }

        // Try pattern table for simple routing cases (fallback)
        String submenu = AxisMenuItems.findSubmenuForField(id);
        if (submenu != null) {
            addToSubmenu(submenu, dsid);
        } else {
            // No pattern match, add as standalone item
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
            final JMenu me7l=this.subMenus.get(AxisMenuItems.SUBMENU_ME7_LOGGER);
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
