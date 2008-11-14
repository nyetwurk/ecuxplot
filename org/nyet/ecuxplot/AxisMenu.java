package org.nyet.ecuxplot;

import java.util.Hashtable;
import java.util.HashMap;
import java.util.Iterator;

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
    private Comparable[] initialChecked;
    private HashMap<String, AbstractButton> members = new HashMap<String, AbstractButton>();

    private Hashtable<String, JMenu> subMenus = new Hashtable<String, JMenu>();

    private void addToSubmenu(String id, JComponent item, boolean autoadd) {
	JMenu sub = this.subMenus.get(id);
	if(sub==null) {
	    sub=new JMenu(id + "...");
	    this.subMenus.put(id, sub);
	    if(autoadd) this.add(sub);
	}
	sub.add(item);
    }
    private void addToSubmenu(String id, JComponent item) {
	addToSubmenu(id, item, true);	// autoadd to submenu
    }

    private void add(String id, SubActionListener listener,
	ButtonGroup bg) {
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
	if(id.matches("RPM")) {
	    this.add("Calc Velocity", listener, bg);
	    this.add("Calc Acceleration (RPM/s)", listener, bg);
	    this.add("Calc Acceleration (m/s^2)", listener, bg);
	    this.add("Calc Acceleration (g)", listener, bg);
	    this.add("Calc WHP", listener, bg);
	    this.add("Calc WTQ", listener, bg);
	    this.add("Calc HP", listener, bg);
	    this.add("Calc TQ", listener, bg);
	    this.add("Calc Drag", listener, bg);
	    this.add(item);
	    addToSubmenu("Calc", new JSeparator(), false);
	} else if(id.matches("MassAirFlow")) {
	    this.add("Calc Load", listener, bg);
	    this.add("Calc MAF", listener, bg);
	    this.add(item);
	    addToSubmenu("Calc", new JSeparator(), false);
	// goes before .*Load.* to catch CalcLoad
	} else if(id.matches("^Calc .*")) {
	    // calc is added last, do not autoadd to submenu
	    addToSubmenu("Calc", item, false);
	} else if(id.matches(".*Fuel.*")) {
	    addToSubmenu("Fuel", item);
	    if(id.matches("AirFuelRatioDesired")) {
		this.add("AirFuelRatioDesired (AFR)", listener, bg);
	    }
	    if(id.matches("FuelInjectorOnTime")) {
		this.add("FuelInjectorDutyCycle", listener, bg);
		this.add("Calc Fuel Mass", listener, bg);
		this.add("Calc AFR", listener, bg);
		this.add("Calc lambda", listener, bg);
		this.add("Calc lambda error", listener, bg);
		// addToSubmenu("Calc", new JSeparator(), false);
	    }
	} else if(id.matches("^Boost.*")) {
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
		addToSubmenu("Calc", new JSeparator(), false);
	    }
	} else if(id.matches("^Ignition.*")) {
	    addToSubmenu("Ignition", item);
	    if(id.matches("IgnitionTimingAngleOverall")) {
		this.add("IgnitionTimingAngleOverallDesired", listener, bg);
	    }
	} else if(id.matches("^Knock.*")) {
	    addToSubmenu("Knock", item);
	} else if(id.matches("^EGT.*")) {
	    addToSubmenu("EGT", item);
	} else if(id.matches("^OXS.*")) {
	    addToSubmenu("OXS", item);
	} else if(id.matches(".*Load.*")) {
	    addToSubmenu("Load", item);
	    if(id.matches("EngineLoadDesired")) {
		this.add("Calc SimBoostPressureDesired", listener, bg);
	    }
	    if(id.matches("EngineLoadCorrectedSpecified")) {
		this.add("Calc LoadSpecified correction", listener, bg);
	    }
	} else {
	    this.add(item);
	}

	this.members.put(id, item);
    }

    public AxisMenu (String text, String[] headers, SubActionListener listener,
	boolean radioButton, Comparable[] initialChecked) {
	super(text);
	this.initialChecked=initialChecked;

	ButtonGroup bg = null;
	if(radioButton) bg = new ButtonGroup();

	for(int i=0;i<headers.length;i++) {
	    if(headers[i].length()>0 && !this.members.containsKey(headers[i]))
		this.add(headers[i], listener, bg);
	}

	// put calc at bottom
	JMenu calc=subMenus.get("Calc");
	if(calc!=null) {
	    this.add(new JSeparator());
	    this.add(calc);
	}

	if(bg==null) {
	    this.add(new JSeparator());
	    JMenuItem item=new JMenuItem("Remove all");
	    this.add(item);
	    item.addActionListener(new MenuListener(listener,this.getText()));
	}
    }
    public AxisMenu (String text, String[] headers, SubActionListener listener,
	boolean radioButton, Comparable initialChecked) {
	this(text, headers, listener, radioButton, new Comparable [] {initialChecked});
    }

    public void uncheckAll() {
	Iterator itc = this.members.values().iterator();
	while(itc.hasNext()) {
	    AbstractButton item = (AbstractButton) itc.next();
	    item.setSelected(false);
	}
    }
}
