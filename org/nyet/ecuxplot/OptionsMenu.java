package org.nyet.ecuxplot;

import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.prefs.Preferences;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JCheckBox;
import javax.swing.JSeparator;
import javax.swing.JOptionPane;

public final class OptionsMenu extends JMenu {
    private ECUxPlot plotFrame;
    private JMenu loadPresetsMenu;
    private JMenu savePresetsMenu;
    private JMenu deletePresetsMenu;

    public OptionsMenu(String id, ECUxPlot plotFrame) {
	super(id);
	this.plotFrame=plotFrame;
	JMenuItem jmi;
	JCheckBox jcb;

	// Presets
	this.loadPresetsMenu = new JMenu("Load Preset...");
	this.savePresetsMenu = new JMenu("Save Preset...");
	jmi = new JMenuItem("Load All Presets");
	jmi.addActionListener(new LoadAllPresetsAction());
	this.add(jmi);

	this.deletePresetsMenu = new JMenu("Delete Preset...");

	updatePresets();

	this.add(this.loadPresetsMenu);
	this.add(jmi);
	this.add(this.savePresetsMenu);
	this.add(this.deletePresetsMenu);

	this.add(new JSeparator());

	// Prefs
        Preferences prefs = plotFrame.getPreferences();

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
	this.loadPresetsMenu.removeAll();
	this.savePresetsMenu.removeAll();
	this.deletePresetsMenu.removeAll();

	LoadPresetAction lpa = new LoadPresetAction();
	SavePresetAction spa = new SavePresetAction();
	DeletePresetAction dpa = new DeletePresetAction();
	JMenuItem jmi;
	Boolean undoPresent = false;
	Boolean addSeparator = false;
	Boolean presetsPresent = false;
	String [] keys = null;

	for(String s : ECUxPreset.getPresets()) {
	    if(!s.equals("Undo")) {
		jmi = new JMenuItem(s);
		jmi.addActionListener(lpa);
		this.loadPresetsMenu.add(jmi);

		jmi = new JMenuItem(s);
		this.savePresetsMenu.add(jmi);
		jmi.addActionListener(spa);

		jmi = new JMenuItem(s);
		this.deletePresetsMenu.add(jmi);
		jmi.addActionListener(dpa);
	    } else {
		undoPresent = true;
	    }
	    addSeparator = true;
	}

	if (undoPresent) {
	    if(addSeparator)
		this.loadPresetsMenu.add(new JSeparator());

	    jmi = new JMenuItem("Undo");
	    jmi.addActionListener(lpa);
	    this.loadPresetsMenu.add(jmi);
	}
	if(addSeparator)
	    this.savePresetsMenu.add(new JSeparator());

	jmi = new JMenuItem("New Preset...");
	jmi.addActionListener(spa);
	this.savePresetsMenu.add(jmi);

	this.savePresetsMenu.add(new JSeparator());
	jmi = new JMenuItem("Restore Defaults");
	jmi.addActionListener(spa);
	this.savePresetsMenu.add(jmi);
    }

    private class LoadPresetAction implements ActionListener {
	public void actionPerformed(ActionEvent event) {
	    String s = event.getActionCommand();
	    if(!s.equals("Undo")) {
		/* save last setup */
		plotFrame.saveUndoPreset();
		/* maybe undo didn't exist. rebuild load menu just in case */
		updatePresets();
	    }
	    plotFrame.loadPreset(s);
	}
    }

    private class LoadAllPresetsAction implements ActionListener {
	public void actionPerformed(ActionEvent event) {
	    plotFrame.loadAllPresets();
	}
    }

    private class SavePresetAction implements ActionListener {
	private final List<String> blacklist = Arrays.asList(
	    "Undo",
	    "New Preset...",
	    "Restore Defaults",
	    ""
	);

	public void actionPerformed(ActionEvent event) {
	    String s = event.getActionCommand();
	    if(s.equals("Restore Defaults")) {
		ECUxPreset.createDefaultECUxPresets();
	    } else {
		if(s.equals("New Preset...")) {
		    s = ECUxPlot.showInputDialog("Enter preset name");
		    if (s==null) return;

		    if (blacklist.contains(s)) {
			JOptionPane.showMessageDialog(null, "Illegal name");
			return;
		    }

		    for(String k : ECUxPreset.getPresets()) {
			if (s.equals(k)) {
			    JOptionPane.showMessageDialog(null, "Name in use");
			    return;
			}
		    }
		}
		plotFrame.savePreset(s);
	    }
	    updatePresets();
	}
    }

    private class DeletePresetAction implements ActionListener {
	public void actionPerformed(ActionEvent event) {
	    String s = event.getActionCommand();
	    try {
		ECUxPreset.getPreferencesStatic().node(s).removeNode();
		updatePresets();
	    } catch (Exception e) {}
	}
    }
}
