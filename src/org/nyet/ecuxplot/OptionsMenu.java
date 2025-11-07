package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JCheckBox;
import javax.swing.JSeparator;

public final class OptionsMenu extends JMenu {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private JMenuItem showFATSItem;
    private JCheckBox filterCheckBox;
    private JCheckBox scatterCheckBox;
    private JCheckBox saeCheckBox;

    public OptionsMenu(String id, ECUxPlot plotFrame) {
        super(id);
        JMenuItem item;
        JCheckBox jcb;

        // Options - most commonly used first
        final Preferences prefs = ECUxPlot.getPreferences();

        jcb = new JCheckBox("Enable filter", Filter.enabled(prefs));
        jcb.setToolTipText("Enable filter to apply range detection and other filters. Required for FATS and Ranges windows.");
        jcb.addActionListener(plotFrame);
        this.add(jcb);
        this.filterCheckBox = jcb;

        jcb = new JCheckBox("Scatter plot", ECUxPlot.scatter(prefs));
        jcb.setToolTipText("Show scatter plot instead of line plot.");
        jcb.addActionListener(plotFrame);
        this.add(jcb);
        this.scatterCheckBox = jcb;

        jcb = new JCheckBox("Original names",
                prefs.getBoolean("altnames", false));
        jcb.setToolTipText("Show original field names from logger (before aliasing to canonical names)");
        jcb.addActionListener(plotFrame);
        this.add(jcb);

        jcb = new JCheckBox("Apply SAE", SAE.enabled(prefs));
        jcb.setToolTipText("Apply SAE constants to HP and TQ calculations");
        jcb.addActionListener(plotFrame);
        this.add(jcb);
        this.saeCheckBox = jcb;

        this.add(new JSeparator());

        // Analysis tools
        item = new JMenuItem("Show FATS");
        item.setToolTipText("Requires filter to be enabled");
        item.addActionListener(plotFrame);
        this.add(item);
        this.showFATSItem = item;

        this.add(new JSeparator());

        // Filter tools
        item = new JMenuItem("Filter...");
        item.setToolTipText("Adjust filter parameters for range start/end detection");
        item.addActionListener(plotFrame);
        this.add(item);

        item = new JMenuItem("Ranges...");
        item.setToolTipText("Select files and ranges to display");
        item.addActionListener(plotFrame);
        this.add(item);

        item = new JMenuItem("Smoothing...");
        item.setToolTipText("Visualize smoothing path from RPM → acceleration → HP with derivatives and boundary analysis");
        item.addActionListener(plotFrame);
        this.add(item);

        this.add(new JSeparator());

        // Editors
        item = new JMenuItem("SAE constants...");
        item.setToolTipText("Adjust SAE constants for HP and TQ calculations");
        item.addActionListener(plotFrame);
        this.add(item);

        item = new JMenuItem("Edit PID...");
        item.setToolTipText("Adjust PID parameters for PID simulaton");
        item.addActionListener(plotFrame);
        this.add(item);

        // Update initial state
        updateFATSAvailability();
    }

    public void updateFATSAvailability() {
        if (this.showFATSItem != null) {
            final Preferences prefs = ECUxPlot.getPreferences();
            boolean filterEnabled = Filter.enabled(prefs);
            this.showFATSItem.setEnabled(filterEnabled);
        }
    }

    /**
     * Update the filter checkbox to reflect the current filter enabled state
     */
    public void updateFilterCheckBox() {
        if (this.filterCheckBox != null) {
            final Preferences prefs = ECUxPlot.getPreferences();
            boolean filterEnabled = Filter.enabled(prefs);
            // Only update if state has changed to avoid triggering action listeners
            if (this.filterCheckBox.isSelected() != filterEnabled) {
                this.filterCheckBox.setSelected(filterEnabled);
            }
        }
        // Also update FATS availability when filter state changes
        updateFATSAvailability();
    }

    /**
     * Update the scatter checkbox to reflect the current scatter state
     */
    public void updateScatterCheckBox() {
        if (this.scatterCheckBox != null) {
            final Preferences prefs = ECUxPlot.getPreferences();
            boolean scatterEnabled = ECUxPlot.scatter(prefs);
            // Only update if state has changed to avoid triggering action listeners
            if (this.scatterCheckBox.isSelected() != scatterEnabled) {
                this.scatterCheckBox.setSelected(scatterEnabled);
            }
        }
    }

    /**
     * Update the SAE checkbox to reflect the current SAE enabled state
     */
    public void updateSAECheckBox() {
        if (this.saeCheckBox != null) {
            final Preferences prefs = ECUxPlot.getPreferences();
            boolean saeEnabled = SAE.enabled(prefs);
            // Only update if state has changed to avoid triggering action listeners
            if (this.saeCheckBox.isSelected() != saeEnabled) {
                this.saeCheckBox.setSelected(saeEnabled);
            }
        }
    }
}

// vim: set sw=4 ts=8 expandtab:
