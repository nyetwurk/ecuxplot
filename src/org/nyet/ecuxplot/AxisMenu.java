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
import org.nyet.logfile.Dataset.DatasetId;
import org.nyet.ecuxplot.Loggers.LoggerType;

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

    private void addToSubmenu(String submenu, JComponent item, boolean autoadd) {
	JMenu sub = this.subMenus.get(submenu);
	if(sub==null) {
	    sub = new AxisMenu(submenu + "...", this);
	    this.subMenus.put(submenu, sub);
	    if(autoadd) this.add(sub);
	}
	sub.add(item);
	if (item instanceof AbstractButton) {
	    AbstractButton b = (AbstractButton)item;
	    this.members.put(b.getText(), b);
	}
    }

    private void addToSubmenu(String submenu, JComponent item) {
	// autoadd if not Calc, which is added last
	//addToSubmenu(submenu, item, id.matches("^Calc")?false:true);
	addToSubmenu(submenu, item, true);
    }

    private void addToSubmenu(String submenu, String id) {
	final AbstractButton item = makeMenuItem(id, null);
	addToSubmenu(submenu, item);
    }

    private AbstractButton makeMenuItem(String id, String tip) {
	boolean checked = false;

	for (final Comparable<?> element : this.initialChecked) {
	    if(id.equals(element)) {
		checked = true;
		break;
	    }
	}

	final AbstractButton item = (this.buttonGroup==null)?new JCheckBox(id, checked):
	    new JRadioButtonMenuItem(id, checked);

	if(tip!=null) item.setToolTipText(tip);

	item.addActionListener(new MenuListener(this.listener, this.getText()));
	if(this.buttonGroup!=null) this.buttonGroup.add(item);

	return item;
    }

    /* string, index */
    private void addDirect(String id, int index) {
	final AbstractButton item = makeMenuItem(id, null);
	this.members.put(id, item);
	super.add(item, index);
    }

    /* string */
    /* process through this.add() to detect submenu */
    /* override add */
    public JMenuItem add(String id) {
	return this.add(new DatasetId(id));
    }

    /* dsid */
    private JMenuItem add(DatasetId dsid) {
	/* FIXME: return something useful? */
	if (this.members.containsKey(dsid.id)) return null;

	final String id = dsid.id;
	final String tip = dsid.id2;
	final String units = dsid.unit;

	final AbstractButton item = makeMenuItem(id, tip);

	if(id.matches("RPM")) {
	    this.add(item, 0);	// always add rpms first!
	    addDirect("RPM - raw", 1);

	    addToSubmenu("Calc Power", "WHP");
	    addToSubmenu("Calc Power", "WTQ");
	    addToSubmenu("Calc Power", "HP");
	    addToSubmenu("Calc Power", "TQ");
	    addToSubmenu("Calc Power", "Drag");

	    addToSubmenu("Calc Power", new JSeparator());

	    addToSubmenu("Calc Power", "Calc Velocity");
	    addToSubmenu("Calc Power", "Acceleration (RPM/s)");
	    addToSubmenu("Calc Power", "Acceleration - raw (RPM/s)");
	    addToSubmenu("Calc Power", "Acceleration (m/s^2)");
	    addToSubmenu("Calc Power", "Acceleration (g)");

	// goes before .*Load.* to catch CalcLoad
	} else if(id.matches(".*(MAF|MassAir|AirMass|Mass Air Flow).*")) {
	    addToSubmenu("MAF", item);
	    if(id.matches("MassAirFlow")) {
		this.add("MassAirFlow (kg/hr)");
		if (dsid.type == LoggerType.LOG_ME7LOGGER) {
		    addToSubmenu("Calc MAF", "Sim Load");
		    addToSubmenu("Calc MAF", "Sim Load Corrected");
		    addToSubmenu("Calc MAF", "Sim MAF");
		}
		addToSubmenu("Calc MAF", "MassAirFlow df/dt");
		addToSubmenu("Calc MAF", "Turbo Flow");
		addToSubmenu("Calc MAF", "Turbo Flow (lb/min)");
		addToSubmenu("Calc MAF", new JSeparator());
	    }
	} else if(id.matches(".*(AFR|AdaptationPartial|Injection|Fuel|Lambda|TFT|IDC|Injector|Methanol|E85).*")) {
	    addToSubmenu("Fuel", item);
	    if(id.matches("TargetAFRDriverRequest")) {
		if (units==null || !units.equals("AFR"))
		    this.add("TargetAFRDriverRequest (AFR)");
	    }
	    if(id.matches("AirFuelRatioDesired")) {
		if (units==null || !units.equals("AFR"))
		    this.add("AirFuelRatioDesired (AFR)");
	    }
	    if(id.matches("AirFuelRatioCurrent")) {
		this.add("AirFuelRatioCurrent (AFR)");
	    }
	    if(id.matches("FuelInjectorOnTime")) {	// ti
		this.add("FuelInjectorDutyCycle");
	    }
	    if(id.matches("EffInjectionTime")) {	// te
		this.add("EffInjectorDutyCycle");
		addToSubmenu("Calc Fuel", "Sim Fuel Mass");
		addToSubmenu("Calc Fuel", "Sim AFR");
		addToSubmenu("Calc Fuel", "Sim lambda");
		addToSubmenu("Calc Fuel", "Sim lambda error");
		// addToSubmenu("Calc Fuel", new JSeparator());
	    }
	    if(id.matches("EffInjectionTimeBank2")) {	// te
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
	    addToSubmenu("Zeitronix", item);
	} else if(id.matches(".*(Load|Torque|ChargeLimit.*Protection).*")) {
	    // before Boost so we catch ChargeLimit*Protection before Charge
	    addToSubmenu("Load", item);
	    if(id.matches("EngineLoad(Requested|Corrected)")) {
		addToSubmenu("Calc Boost", "Sim BoostPressureDesired");
		addToSubmenu("Calc Boost", "Sim LoadSpecified correction");
	    }
	} else if(id.matches(".*([Bb]oost|Wastegate|Charge|WGDC|PSI|Baro|Pressure|PID).*")) {
	    addToSubmenu("Boost", item);
	    if(id.matches("BoostPressureDesired")) {
		if (units==null || !units.equals("PSI"))
		    this.add("BoostPressureDesired (PSI)");
		addToSubmenu("Calc Boost", "BoostDesired PR");
	    }
	    if(id.matches("BoostPressureActual")) {
		if (units==null || !units.equals("PSI"))
		    this.add("BoostPressureActual (PSI)");
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
		this.add(new DatasetId("BoostPressureDesired", null, units));
	    }
	/* do this before Timing so we don't match Throttle Angle */
	} else if(id.matches(".*(Pedal|Throttle).*")) {
	    addToSubmenu("Throttle", item);
	} else if(id.matches(".*(Eta|Avg|Adapted)?(Ign|Timing|Angle).*")) {
	    addToSubmenu("Ignition", item);
	    if(id.matches("IgnitionTimingAngleOverall")) {
		this.add("IgnitionTimingAngleOverallDesired");
	    }
	    final AbstractButton titem = makeMenuItem(id + " (ms)", tip);
	    addToSubmenu("TrueTiming", titem, true);
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
	} else if(id.matches("Engine torque")) {
	    this.add(item);
	    this.add("Engine torque (ft-lb)");
	    this.add("Engine HP");
	} else if(id.matches("IntakeAirTemperature")) {
	    addToSubmenu("Temperature", item);
	    this.add("IntakeAirTemperature (C)");
	    if (dsid.type == LoggerType.LOG_ME7LOGGER) {
		addToSubmenu("Calc IAT", "Sim evtmod");
		addToSubmenu("Calc IAT", "Sim ftbr");
		addToSubmenu("Calc IAT", "Sim BoostIATCorrection");
	    }
	} else if(id.matches(".*Temperature.*")) {
	    addToSubmenu("Temperature", item);
	} else if(id.matches(".*VV.*")) {	// EvoScan
	    addToSubmenu("VVT", item);
	} else if(id.matches("^Log.*")) {	// EvoScan
	    addToSubmenu("EvoScan", item);
	} else if(id.matches("^ME7L.*")) {
	    addToSubmenu("ME7 Logger", item);
	    if(id.matches("ME7L ps_w")) {
		addToSubmenu("Calc Boost", "Sim pspvds");
	    }
	} else {
	    this.add(item);
	}

	this.members.put(id, item);

	/* FIXME: return something useful? */
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
	    /* top level menu (before "more...") */
	    if(radioButton) {
		this.buttonGroup = new ButtonGroup();
		this.add("Sample");
		this.add(new JSeparator());
	    }

	    for(int i=0;i<ids.length;i++) {
		if(ids[i] == null) continue;
		if(ids[i].id.length()>0 && !this.members.containsKey(ids[i].id)) {
		    this.add(ids[i]);
		}
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
	for(final AbstractButton item : this.members.values())
	    item.setSelected(false);
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
}
