package org.nyet.ecuxplot;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JCheckBox;
import javax.swing.JSeparator;
import javax.swing.JOptionPane;

public final class OptionsMenu extends JMenu {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private final ECUxPlot plotFrame;
    private final JMenu loadPresetsMenu;
    private final JMenu savePresetsMenu;
    private final JMenu deletePresetsMenu;

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
	final Preferences prefs = ECUxPlot.getPreferences();
	jcb = new JCheckBox("Use alternate column names",
		prefs.getBoolean("altnames", false));
	jcb.addActionListener(plotFrame);
	this.add(jcb);
	jcb = new JCheckBox("Scatter plot",
		ECUxPlot.scatter(prefs));
	jcb.addActionListener(plotFrame);
	this.add(jcb);

	this.add(new JSeparator());
	jcb = new JCheckBox("Filter data", Filter.enabled(prefs));
	jcb.addActionListener(plotFrame);
	this.add(jcb);
	jcb = new JCheckBox("Show all ranges", Filter.showAllRanges(prefs));
	jcb.addActionListener(plotFrame);
	this.add(jcb);
	jmi = new JMenuItem("Next range...");
	jmi.addActionListener(plotFrame);
	this.add(jmi);
	jmi = new JMenuItem("Previous range...");
	jmi.addActionListener(plotFrame);
	this.add(jmi);
	this.add(new JSeparator());

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

	final LoadPresetAction lpa = new LoadPresetAction();
	final SavePresetAction spa = new SavePresetAction();
	final DeletePresetAction dpa = new DeletePresetAction();
	JMenuItem jmi;
	Boolean undoPresent = false;
	Boolean addSeparator = false;

	for(final String s : ECUxPreset.getPresets()) {
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
	@Override
	public void actionPerformed(ActionEvent event) {
	    final String s = event.getActionCommand();
	    if(!s.equals("Undo")) {
		/* save last setup */
		OptionsMenu.this.plotFrame.saveUndoPreset();
		/* maybe undo didn't exist. rebuild load menu just in case */
		updatePresets();
	    }
	    OptionsMenu.this.plotFrame.loadPreset(s);
	}
    }

    private class LoadAllPresetsAction implements ActionListener {
	@Override
	public void actionPerformed(ActionEvent event) {
	    final ArrayList<String> list = new
		ArrayList<String>(Arrays.asList(ECUxPreset.getPresets()));
	    list.remove("Undo");
	    OptionsMenu.this.plotFrame.loadPresets(list);
	}
    }

    private class SavePresetAction implements ActionListener {
	private final List<String> blacklist = Arrays.asList(
	    "Undo",
	    "New Preset...",
	    "Restore Defaults",
	    ""
	);

	@Override
	public void actionPerformed(ActionEvent event) {
	    String s = event.getActionCommand();
	    if(s.equals("Restore Defaults")) {
		ECUxPreset.createDefaultECUxPresets();
	    } else {
		if(s.equals("New Preset...")) {
		    s = ECUxPlot.showInputDialog("Enter preset name");
		    if (s==null) return;

		    if (this.blacklist.contains(s)) {
			JOptionPane.showMessageDialog(null, "Illegal name");
			return;
		    }

		    for(final String k : ECUxPreset.getPresets()) {
			if (s.equals(k)) {
			    JOptionPane.showMessageDialog(null, "Name in use");
			    return;
			}
		    }
		}
		OptionsMenu.this.plotFrame.savePreset(s);
	    }
	    updatePresets();
	}
    }

    private class DeletePresetAction implements ActionListener {
	@Override
	public void actionPerformed(ActionEvent event) {
	    final String s = event.getActionCommand();
	    try {
		ECUxPreset.getPreferencesStatic().node(s).removeNode();
		updatePresets();
	    } catch (final Exception e) {}
	}
    }
}
