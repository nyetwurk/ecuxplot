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

        jcb = new JCheckBox("Use alternate column names",
                prefs.getBoolean("altnames", false));
        jcb.addActionListener(plotFrame);
        this.add(jcb);

        jcb = new JCheckBox("Apply SAE", SAE.enabled(prefs));
        jcb.addActionListener(plotFrame);
        this.add(jcb);

        // Most commonly used tool
        jcb = new JCheckBox("Show FATS Window", ECUxPlot.getPreferences().getBoolean("showfats", false));
        jcb.addActionListener(plotFrame);
        this.add(jcb);

        this.add(new JSeparator());

        // Filter tools
        item = new JMenuItem("Configure Filter...");
        item.addActionListener(plotFrame);
        this.add(item);

        jcb = new JCheckBox("Show all ranges", Filter.showAllRanges(ECUxPlot.getPreferences()));
        jcb.addActionListener(plotFrame);
        this.add(jcb);
        this.showAllRangesCheckBox = jcb;

        item = new JMenuItem("Next Range");
        item.addActionListener(plotFrame);
        this.add(item);
        this.nextRangeItem = item;

        item = new JMenuItem("Previous Range");
        item.addActionListener(plotFrame);
        this.add(item);
        this.previousRangeItem = item;

        this.add(new JSeparator());

        // Editors
        item = new JMenuItem("Edit SAE constants...");
        item.addActionListener(plotFrame);
        this.add(item);

        item = new JMenuItem("Edit PID...");
        item.addActionListener(plotFrame);
        this.add(item);

        // Update initial state
        updateRangeNavigationState();
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
}

// vim: set sw=4 ts=8 expandtab:
