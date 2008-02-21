package org.nyet.ecuxplot;

import java.awt.event.ActionListener;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JCheckBox;
import javax.swing.JSeparator;

public final class OptionsMenu extends JMenu {
    public OptionsMenu(String id, ActionListener listener) {
	super(id);
	JCheckBox scatter = new JCheckBox("Scatter plot");
	scatter.addActionListener(listener);
	this.add(scatter);
	JCheckBox filter = new JCheckBox("Filter data", true);
	filter.addActionListener(listener);
	this.add(filter);
	this.add(new JSeparator());
	JMenuItem constants = new JMenuItem("Edit constants...");
	constants.addActionListener(listener);
	this.add(constants);
	JMenuItem cf = new JMenuItem("Configure filter...");
	cf.addActionListener(listener);
	this.add(cf);
    }
}
