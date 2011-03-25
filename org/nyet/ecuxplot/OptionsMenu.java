package org.nyet.ecuxplot;

import java.util.TreeMap;
import java.util.prefs.Preferences;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JCheckBox;
import javax.swing.JSeparator;

public final class OptionsMenu extends JMenu {
    private ECUxPlot plotFrame;
    private JMenu presetsMenu;

    public OptionsMenu(String id, ECUxPlot plotFrame) {
	super(id);
	this.plotFrame=plotFrame;
	JMenuItem jmi;
	JCheckBox jcb;

        Preferences prefs = plotFrame.getPreferences();

	this.presetsMenu = new JMenu("Load Preset...");
	updatePresets();
	this.add(this.presetsMenu);

	this.add(new JSeparator());

	jcb = new JCheckBox("Scatter plot",
		ECUxPlot.scatter(prefs));
	jcb.addActionListener(plotFrame);
	this.add(jcb);

	jcb = new JCheckBox("Filter data", Filter.enabled(prefs));
	jcb.addActionListener(plotFrame);
	this.add(jcb);

	jcb = new JCheckBox("Apply SAE", SAE.enabled(prefs));
	jcb.addActionListener(plotFrame);
	this.add(jcb);

	jcb = new JCheckBox("Show FATS window...", prefs.getBoolean("showfats", false));
	jcb.addActionListener(plotFrame);
	this.add(jcb);

	this.add(new JSeparator());

	jmi = new JMenuItem("Configure filter...");
	jmi.addActionListener(plotFrame);
	this.add(jmi);

	jmi = new JMenuItem("Edit SAE constants...");
	jmi.addActionListener(plotFrame);
	this.add(jmi);

	jmi = new JMenuItem("Edit PID...");
	jmi.addActionListener(plotFrame);
	this.add(jmi);
    }

    private void updatePresets() {
	TreeMap<Comparable, Preset> presets = this.plotFrame.getPresets();
	PresetAction pa = new PresetAction();
	if(presets!=null) {
	    for(Preset p : presets.values()) {
		JMenuItem jmi = new JMenuItem(p.getName().toString());
		jmi.addActionListener(pa);
		if(!p.getName().equals("Undo"))
		    this.presetsMenu.add(jmi);
	    }
	}
	this.presetsMenu.add(new JSeparator());
	JMenuItem jmi = new JMenuItem("Undo");
	jmi.addActionListener(pa);
	this.presetsMenu.add(jmi);
    }

    private class PresetAction implements ActionListener {
	public void actionPerformed(ActionEvent event) {
	    OptionsMenu om = OptionsMenu.this;
	    if(!event.getActionCommand().equals("Undo"))
		om.plotFrame.savePreset("Undo");
	    om.presetsMenu.removeAll();
	    updatePresets();
	    om.plotFrame.loadPreset(event.getActionCommand());
	}
    }
}
