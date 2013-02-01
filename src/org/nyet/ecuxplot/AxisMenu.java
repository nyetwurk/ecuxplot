package org.nyet.ecuxplot;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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

public class AxisMenu extends JMenu {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private SubActionListener listener;
    private boolean radioButton;
    private Comparable<?>[] initialChecked;

    private HashMap<String, AbstractButton> members =
	new HashMap<String, AbstractButton>();

    private HashMap<String, JMenu> subMenus =
	new HashMap<String, JMenu>();

    private int count=0;
    private int maxItems=18;
    private AxisMenu more=null;
    private AxisMenu parent=null;

    private void addToSubmenu(String id, JComponent item, boolean autoadd) {
	JMenu sub = this.subMenus.get(id);
	if(sub==null) {
	    sub = new AxisMenu(id + "...", this);
	    this.subMenus.put(id, sub);
	    if(autoadd) this.add(sub);
	}
	sub.add(item);
    }

    private void addToSubmenu(String id, JComponent item) {
	// autoadd if not Calc, which is added last
	addToSubmenu(id, item, id.equals("Calc")?false:true);
    }

    private AbstractButton makeMenuItem(String id,
	SubActionListener listener, ButtonGroup bg) {
	boolean checked = false;

	for(int i=0;i<initialChecked.length;i++) {
	    if(id.equals(initialChecked[i])) {
		checked = true;
		break;
	    }
	}

	AbstractButton item = (bg==null)?new JCheckBox(id, checked):
	    new JRadioButtonMenuItem(id, checked);

	item.addActionListener(new MenuListener(listener,this.getText()));
	if(bg!=null) bg.add(item);

	return item;
    }

    private void add(String id, SubActionListener listener,
	ButtonGroup bg, int where) {
	AbstractButton item = makeMenuItem(id, listener, bg);
	this.add(item, where);
    }

    private void add(String id, SubActionListener listener,
	ButtonGroup bg) {

	AbstractButton item = makeMenuItem(id, listener, bg);

	if(id.matches("RPM")) {
	    this.add(item, 0);	// always add rpms first!
	    this.add("RPM - raw", listener, bg, 1);

	    this.add("Calc Velocity", listener, bg);
	    this.add("Calc Acceleration (RPM/s)", listener, bg);
	    this.add("Calc Acceleration - raw (RPM/s)", listener, bg);
	    this.add("Calc Acceleration (m/s^2)", listener, bg);
	    this.add("Calc Acceleration (g)", listener, bg);
	    this.add("Calc WHP", listener, bg);
	    this.add("Calc WTQ", listener, bg);
	    this.add("Calc HP", listener, bg);
	    this.add("Calc TQ", listener, bg);
	    this.add("Calc Drag", listener, bg);

	    addToSubmenu("Calc", new JSeparator());

	// goes before .*Load.* to catch CalcLoad
	} else if(id.matches("^Calc .*")) {
	    addToSubmenu("Calc", item);
	} else if(id.matches(".*(MAF|MassAir|AirMass).*")) {
	    addToSubmenu("MAF", item);
	    if(id.matches("MassAirFlow")) {
		this.add("Calc Load", listener, bg);
		this.add("Calc Load Corrected", listener, bg);
		this.add("Calc MAF", listener, bg);
		this.add("Calc MassAirFlow df/dt", listener, bg);
		this.add("Calc Turbo Flow", listener, bg);
		this.add("Calc Turbo Flow (lb/min)", listener, bg);
		addToSubmenu("Calc", new JSeparator());
	    }
	} else if(id.matches(".*(AFR|AdaptationPartial|Injection|Fuel|Lambda|TFT|IDC|Injector).*")) {
	    addToSubmenu("Fuel", item);
	    if(id.matches("AirFuelRatioDesired")) {
		this.add("AirFuelRatioDesired (AFR)", listener, bg);
	    }
	    if(id.matches("TargetAFRDriverRequest")) {
		this.add("TargetAFRDriverRequest (AFR)", listener, bg);
	    }
	    if(id.matches("FuelInjectorOnTime")) {
		this.add("Calc Fuel Mass", listener, bg);
		this.add("Calc AFR", listener, bg);
		this.add("Calc lambda", listener, bg);
		this.add("Calc lambda error", listener, bg);
		// addToSubmenu("Calc", new JSeparator());
		this.add("FuelInjectorDutyCycle", listener, bg);
	    }
	} else if(id.matches("^Zeitronix.*")) {
	    /* do zeitronix before boost so we get the conversions we want */
	    if(id.matches("^Zeitronix Boost")) {
		this.add("Zeitronix Boost (PSI)", listener, bg);
		this.add("Calc Boost Spool Rate Zeit (RPM)", listener, bg);
	    }
	    if(id.matches("^Zeitronix AFR")) {
		this.add("Zeitronix AFR (lambda)", listener, bg);
	    }
	    if(id.matches("^Zeitronix Lambda")) {
		this.add("Zeitronix Lambda (AFR)", listener, bg);
	    }
	    addToSubmenu("Zeitronix", item);
	} else if(id.matches(".*([Bb]oost|Wastegate|Charge|WGDC|PSI|Baro).*")) {
	    addToSubmenu("Boost", item);
	    if(id.matches("BoostPressureDesired")) {
		this.add("BoostPressureDesired (PSI)", listener, bg);
		this.add("Calc BoostDesired PR", listener, bg);
	    }
	    if(id.matches("BoostPressureActual")) {
		this.add("BoostPressureActual (PSI)", listener, bg);
		this.add("Calc BoostActual PR", listener, bg);
		this.add("Calc Boost Spool Rate (RPM)", listener, bg);
		this.add("Calc Boost Spool Rate (time)", listener, bg);
		this.add("Calc LDR error", listener, bg);
		this.add("Calc LDR de/dt", listener, bg);
		this.add("Calc LDR I e dt", listener, bg);
		this.add("Calc LDR PID", listener, bg);
		addToSubmenu("Calc", new JSeparator());
	    }
	} else if(id.matches(".*(Eta|Avg|Adapted)?(Ign|Timing).*")) {
	    addToSubmenu("Ignition", item);
	    if(id.matches("IgnitionTimingAngleOverall")) {
		this.add("IgnitionTimingAngleOverallDesired", listener, bg);
	    }
	} else if(id.matches("(Cat|MainCat).*")) {
	    addToSubmenu("Cats", item);
	} else if(id.matches(".*EGT.*")) {
	    addToSubmenu("EGT", item);
	} else if(id.matches(".*(Idle|Idling).*")) {
	    addToSubmenu("Idle", item);
	} else if(id.matches(".*[Kk]nock.*")) {
	    addToSubmenu("Knock", item);
	} else if(id.matches(".*Misfire.*")) {
	    addToSubmenu("Misfires", item);
	} else if(id.matches(".*(OXS|O2|ResistanceSensor).*")) {
	    addToSubmenu("O2 Sensor(s)", item);
	} else if(id.matches(".*(Pedal|Throttle).*")) {
	    addToSubmenu("Throttle", item);
	} else if(id.matches(".*(Load|Torque).*")) {
	    addToSubmenu("Load", item);
	    if(id.matches("EngineLoadDesired")) {
		this.add("Calc SimBoostPressureDesired", listener, bg);
	    }
	    if(id.matches("EngineLoadCorrectedSpecified")) {
		this.add("Calc LoadSpecified correction", listener, bg);
	    }
	} else if(id.matches("Engine torque")) {
	    this.add(item);
	    this.add("Engine torque (ft-lb)", listener, bg);
	    this.add("Engine HP", listener, bg);
	} else if(id.matches("IntakeAirTemperature")) {
	    addToSubmenu("Temperature", item);
	    this.add("IntakeAirTemperature (C)", listener, bg);
	} else if(id.matches(".*Temperature.*")) {
	    addToSubmenu("Temperature", item);
	} else if(id.matches(".*VV.*")) {	// EvoScan
	    addToSubmenu("VVT", item);
	} else if(id.matches("^Log.*")) {	// EvoScan
	    addToSubmenu("EvoScan", item);
	} else if(id.matches("^ME7L.*")) {
	    addToSubmenu("ME7 Logger", item);
	} else {
	    this.add(item);
	}

	this.members.put(id, item);
    }

    // constructors
    public AxisMenu(String text, String[] headers, SubActionListener listener,
	boolean radioButton, Comparable<?>[] initialChecked) {
	this(text, headers, listener, radioButton, initialChecked, -1);
    }
    public AxisMenu(String text, String[] headers, SubActionListener listener,
	boolean radioButton, Comparable<?>[] initialChecked, int maxItems) {

	super(text);

	this.listener = listener;
	this.radioButton = radioButton;
	this.initialChecked = initialChecked;
	if (maxItems>0) this.maxItems = maxItems;

	if (headers!=null) {
	    /* top level menu (before "more...") */
	    ButtonGroup bg = null;
	    if(radioButton) {
		bg = new ButtonGroup();
		this.add("Sample", listener, bg);
		this.add(new JSeparator());
	    }

	    for(int i=0;i<headers.length;i++) {
		if(headers[i] == null) continue;
		if(headers[i].length()>0 && !this.members.containsKey(headers[i])) {
		    this.add(headers[i], listener, bg);
		}
	    }

	    // put ME7Log next
	    JMenu me7l=subMenus.get("ME7 Logger");
	    if(me7l!=null) {
		super.add(new JSeparator());
		super.add(me7l);
	    }

	    // put calc next
	    JMenu calc=subMenus.get("Calc");
	    if(calc!=null) {
		super.add(new JSeparator());
		super.add(calc);
	    }

	    // put More.. next
	    if(this.more!=null) {
		super.add(new JSeparator());
		super.add(this.more);
	    }
	}

	// add "Remove all" to top level menu
	if(headers!=null && !radioButton) {
	    super.add(new JSeparator());
	    JMenuItem item=new JMenuItem("Remove all");
	    super.add(item);
	    item.addActionListener(new MenuListener(listener,this.getText()));
	}
    }

    public AxisMenu(String text, String[] headers, SubActionListener listener,
	boolean radioButton, Comparable<?> initialChecked) {
	this(text, headers, listener, radioButton,
	    new Comparable [] {initialChecked});
    }

    public AxisMenu(String id, AxisMenu parent) {
	this(id + "...", null, parent.listener, parent.radioButton, parent.initialChecked, parent.maxItems);
	this.parent = parent;
    }
    // end constructors

    public Component add(JMenu item) {return add(item, false);}
    public Component add(Component item) {return add(item, false);}
    private Component add(Component item, boolean force) {
	if (this.more==null && item instanceof JSeparator) {
	    Component c=this.getMenuComponent(this.getMenuComponentCount()-1);
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
		this.add(more, true);
	    }
	}
	// will recurse until it fits
	return this.more.add(item);
    }

    public void uncheckAll() {
	for(AbstractButton item : this.members.values())
	    item.setSelected(false);
    }

    public void setSelected(Comparable<?> key) {
	AbstractButton item = this.members.get(key);
	if(item!=null) item.setSelected(true);
    }

    public void setSelected(Comparable<?>[] keys) {
	for(int i=0; i<keys.length; i++) {
	    this.setSelected(keys[i]);
	}
    }

    public void setOnlySelected(Comparable<?>[] keys) {
	this.setOnlySelected(new HashSet<Comparable<?>>(Arrays.asList(keys)));
    }

    public void setOnlySelected(Set<Comparable<?>> keys) {
	for(String ik : this.members.keySet()) {
	    AbstractButton item = this.members.get(ik);
	    item.setSelected(keys.contains(ik));
	}
    }
}
