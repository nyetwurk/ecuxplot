package org.nyet.ecuxplot;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.JOptionPane;

public final class AxisPresetsMenu extends JMenu {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private final ECUxPlot plotFrame;
    private final JMenu loadPresetsMenu;
    private final JMenu savePresetsMenu;
    private final JMenu deletePresetsMenu;
    private final JMenuItem loadAllPresetsItem;

    public AxisPresetsMenu(String id, ECUxPlot plotFrame) {
        super(id);
        this.plotFrame=plotFrame;

        // Presets
        this.loadPresetsMenu = new JMenu("Load Preset");
        this.savePresetsMenu = new JMenu("Save Preset");
        this.deletePresetsMenu = new JMenu("Delete Preset");
        this.loadAllPresetsItem = new JMenuItem("Load All Presets");
        this.loadAllPresetsItem.addActionListener(new LoadAllPresetsAction());

        updatePresets();

        this.add(this.loadPresetsMenu);
        this.add(this.savePresetsMenu);
        this.add(this.deletePresetsMenu);
        this.add(new JSeparator());
        this.add(this.loadAllPresetsItem);
        this.add(new JSeparator());
        final JMenuItem restoreDefaultsItem = new JMenuItem("Restore Defaults");
        restoreDefaultsItem.addActionListener(new RestoreDefaultsAction());
        this.add(restoreDefaultsItem);
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
    }

    private class LoadPresetAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent event) {
            final String s = event.getActionCommand();
            if(!s.equals("Undo")) {
                /* save last setup */
                AxisPresetsMenu.this.plotFrame.saveUndoPreset();
                /* maybe undo didn't exist. rebuild load menu just in case */
                updatePresets();
            }
            AxisPresetsMenu.this.plotFrame.loadPreset(s);
        }
    }

    private class LoadAllPresetsAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent event) {
            final ArrayList<String> list = new
                ArrayList<String>(Arrays.asList(ECUxPreset.getPresets()));
            list.remove("Undo");
            AxisPresetsMenu.this.plotFrame.loadPresets(list);
        }
    }

    private class SavePresetAction implements ActionListener {
        private final List<String> blacklist = Arrays.asList(
            "Undo",
            "New Preset...",
            ""
        );

        @Override
        public void actionPerformed(ActionEvent event) {
            String s = event.getActionCommand();
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
            AxisPresetsMenu.this.plotFrame.savePreset(s);
            updatePresets();
        }
    }

    private class RestoreDefaultsAction implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent event) {
            ECUxPreset.createDefaultECUxPresets();
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

// vim: set sw=4 ts=8 expandtab:
