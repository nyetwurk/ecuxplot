package org.nyet.ecuxplot;

import java.util.TreeMap;
import java.util.Iterator;
import java.util.prefs.Preferences;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JCheckBox;
import javax.swing.JSeparator;

public final class OptionsMenu extends JMenu implements ActionListener {
    private ECUxPlot plotFrame;
    private JMenu presetsMenu;

    public OptionsMenu(String id, ECUxPlot plotFrame) {
	super(id);
	this.plotFrame=plotFrame;

        Preferences prefs = plotFrame.getPreferences();

	this.presetsMenu = new JMenu("Load Preset...");
	updatePresets();
	this.add(this.presetsMenu);

	JCheckBox jcb = new JCheckBox("Scatter plot",
		ECUxPlot.scatter(prefs));
	jcb.addActionListener(plotFrame);
	this.add(jcb);

	jcb = new JCheckBox("Filter data", Filter.enabled(prefs));
	jcb.addActionListener(plotFrame);
	this.add(jcb);

	jcb = new JCheckBox("Apply SAE", SAE.enabled(prefs));
	jcb.addActionListener(plotFrame);
	this.add(jcb);

	this.add(new JSeparator());

	JMenuItem jmi = new JMenuItem("Configure filter...");
	jmi.addActionListener(plotFrame);
	this.add(jmi);

	this.add(new JSeparator());

	jmi = new JMenuItem("Edit constants...");
	jmi.addActionListener(plotFrame);
	this.add(jmi);

	jmi = new JMenuItem("Edit fueling...");
	jmi.addActionListener(plotFrame);
	this.add(jmi);

	jmi = new JMenuItem("Edit SAE constants...");
	jmi.addActionListener(plotFrame);
	this.add(jmi);

	this.add(new JSeparator());

	jmi = new JMenuItem("Edit PID...");
	jmi.addActionListener(plotFrame);
	this.add(jmi);
    }

    private void updatePresets() {
	TreeMap<Comparable, Preset> presets = this.plotFrame.getPresets();
	if(presets!=null) {
	    Iterator itc = presets.values().iterator();
	    while(itc.hasNext()) {
		Preset p = (Preset)itc.next();
		JMenuItem jmi = new JMenuItem(p.getName().toString());
		jmi.addActionListener(this);
		if(!p.getName().equals("Undo"))
		    this.presetsMenu.add(jmi);
	    }
	}
	this.presetsMenu.add(new JSeparator());
	JMenuItem jmi = new JMenuItem("Undo");
	jmi.addActionListener(this);
	this.presetsMenu.add(jmi);
    }

    public void actionPerformed(ActionEvent event) {
	if(!event.getActionCommand().equals("Undo"))
	    this.plotFrame.savePreset("Undo");
	this.presetsMenu.removeAll();
	updatePresets();
	this.plotFrame.loadPreset(event.getActionCommand());
    }
}
