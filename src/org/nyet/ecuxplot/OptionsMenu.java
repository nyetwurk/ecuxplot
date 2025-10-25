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
    private JCheckBox showAllRangesCheckBox;
    private JMenuItem nextRangeItem;
    private JMenuItem previousRangeItem;
    private JMenuItem showFATSItem;

    public OptionsMenu(String id, ECUxPlot plotFrame) {
        super(id);
        JMenuItem item;
        JCheckBox jcb;

        // Options - most commonly used first
        final Preferences prefs = ECUxPlot.getPreferences();

        jcb = new JCheckBox("Enable filter", Filter.enabled(prefs));
        jcb.addActionListener(plotFrame);
        this.add(jcb);

        jcb = new JCheckBox("Scatter plot", ECUxPlot.scatter(prefs));
        jcb.addActionListener(plotFrame);
        this.add(jcb);

        jcb = new JCheckBox("Alt column names",
                prefs.getBoolean("altnames", false));
        jcb.addActionListener(plotFrame);
        this.add(jcb);

        jcb = new JCheckBox("Apply SAE", SAE.enabled(prefs));
        jcb.addActionListener(plotFrame);
        this.add(jcb);

        jcb = new JCheckBox("Hide axis menus",
                prefs.getBoolean("hideaxismenus", false));
        jcb.addActionListener(plotFrame);
        this.add(jcb);

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
        item.addActionListener(plotFrame);
        this.add(item);

        jcb = new JCheckBox("Show all ranges", Filter.showAllRanges(ECUxPlot.getPreferences()));
        jcb.setToolTipText("Requires filter to be enabled and multiple ranges to exist");
        jcb.addActionListener(plotFrame);
        this.add(jcb);
        this.showAllRangesCheckBox = jcb;

        item = new JMenuItem("Next Range");
        item.setToolTipText("Requires filter to be enabled and multiple ranges to exist");
        item.addActionListener(plotFrame);
        this.add(item);
        this.nextRangeItem = item;

        item = new JMenuItem("Previous Range");
        item.setToolTipText("Requires filter to be enabled and multiple ranges to exist");
        item.addActionListener(plotFrame);
        this.add(item);
        this.previousRangeItem = item;

        this.add(new JSeparator());

        // Editors
        item = new JMenuItem("SAE constants...");
        item.addActionListener(plotFrame);
        this.add(item);

        item = new JMenuItem("Edit PID...");
        item.addActionListener(plotFrame);
        this.add(item);

        // Update initial state
        updateRangeNavigationState();
        updateFATSAvailability();
    }

    public void updateRangeNavigationState() {
        if (this.showAllRangesCheckBox != null && this.nextRangeItem != null && this.previousRangeItem != null) {
            boolean showAllRanges = this.showAllRangesCheckBox.isSelected();
            this.nextRangeItem.setEnabled(!showAllRanges);
            this.previousRangeItem.setEnabled(!showAllRanges);
        }
    }

    public void updateShowAllRangesCheckbox() {
        if (this.showAllRangesCheckBox != null) {
            final Preferences prefs = ECUxPlot.getPreferences();
            this.showAllRangesCheckBox.setSelected(Filter.showAllRanges(prefs));
            updateRangeNavigationState();
        }
    }

    public void updateFATSAvailability() {
        if (this.showFATSItem != null) {
            final Preferences prefs = ECUxPlot.getPreferences();
            boolean filterEnabled = Filter.enabled(prefs);
            this.showFATSItem.setEnabled(filterEnabled);
        }
    }

    public void updateRangeControlsAvailability(boolean filterEnabled, boolean hasMultipleRanges) {
        if (this.showAllRangesCheckBox != null && this.nextRangeItem != null && this.previousRangeItem != null) {
            // Range controls are only useful when filter is enabled AND there are multiple ranges
            boolean rangeControlsUseful = filterEnabled && hasMultipleRanges;

            // Disable range controls if filter is off or only one range exists
            this.showAllRangesCheckBox.setEnabled(rangeControlsUseful);
            this.nextRangeItem.setEnabled(rangeControlsUseful);
            this.previousRangeItem.setEnabled(rangeControlsUseful);

            // If range controls are not useful, force "Show all ranges" to be selected
            if (!rangeControlsUseful) {
                this.showAllRangesCheckBox.setSelected(true);
            }
        }
    }
}

// vim: set sw=4 ts=8 expandtab:
