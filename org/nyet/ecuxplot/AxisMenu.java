package org.nyet.ecuxplot;

import java.util.Hashtable;

import javax.swing.JMenu;
import javax.swing.JCheckBox;
import javax.swing.JSeparator;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;

import org.nyet.util.MenuListener;
import org.nyet.util.SubActionListener;

public class AxisMenu extends JMenu {
    private Hashtable<String, JMenu> subMenus = new Hashtable<String, JMenu>();

    private void addToSubmenu(String id, AbstractButton item, boolean autoadd) {
	JMenu sub = this.subMenus.get(id);
	if(sub==null) {
	    sub=new JMenu(id + "...");
	    this.subMenus.put(id, sub);
	    if(autoadd) this.add(sub);
	}
	sub.add(item);
    }
    private void addToSubmenu(String id, AbstractButton item) {
	addToSubmenu(id, item, true);
    }

    private void handleHeader(String id,
	SubActionListener listener, ButtonGroup bg, boolean checked) {
	AbstractButton item = (bg==null)?new JCheckBox(id, checked):
	    new JRadioButtonMenuItem(id, checked);

	item.addActionListener(new MenuListener(listener,this.getText()));
	if(bg!=null) bg.add(item);
	if(id.matches("MassAirFlow")) {
	    handleHeader("CalcLoad", listener, bg);
	    this.add(item);
	} else if(id.matches(".*Fuel.*")) {
	    addToSubmenu("Fuel", item);
	    if(id.matches("FuelInjectorOnTime")) {
		handleHeader("FuelInjectorDutyCycle", listener, bg);
	    }
	} else if(id.matches("^Boost.*")) {
	    addToSubmenu("Boost", item);
	} else if(id.matches("^Ignition.*")) {
	    addToSubmenu("Ignition", item);
	} else if(id.matches("^Knock.*")) {
	    addToSubmenu("Knock", item);
	} else if(id.matches("^EGT.*")) {
	    addToSubmenu("EGT", item);
	} else if(id.matches("^OXS.*")) {
	    addToSubmenu("OXS", item);
	} else if(id.matches("^Calc.*")) {
	    addToSubmenu("Calc", item, false);
	} else if(id.matches(".*Load.*")) { // goes last, its very loose
	    addToSubmenu("Load", item);
	} else {
	    this.add(item);
	}
    }
    private void handleHeader(String id,
	SubActionListener listener, ButtonGroup bg) {
	handleHeader(id, listener, bg, false);
    }

    public AxisMenu (String text, String[] headers, SubActionListener listener,
	boolean radioButton, String initialChecked) {
	super(text);

	ButtonGroup bg = null;
	if(radioButton) bg = new ButtonGroup();

	for(int i=0;i<headers.length;i++) {
	    handleHeader(headers[i], listener, bg, headers[i].equals(initialChecked));
	}

    	if(this.subMenus.get("Calc")!=null) {
	    this.add(new JSeparator());
	    this.add(this.subMenus.get("Calc"));
	}
    }
}
