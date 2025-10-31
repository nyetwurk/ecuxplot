package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

import java.awt.event.*;
import javax.swing.*;

public class ConstantsEditor extends PreferencesEditor {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private final Constants c;

    public JTextField mass;
    public JTextField rpm_per_mph;
    public JTextField Cd;
    public JTextField FA;
    public JTextField driveline_loss;

    private static final String[][] pairs = {
        {"Mass (kg)", "mass"},
        //{"RPM per mph (if VehicleSpeed N/A)", "rpm_per_mph"},
        {"RPM per mph", "rpm_per_mph"},
        {"Coefficient of drag", "Cd"},
        {"<html>Frontal area (m<sup>2</sup>)</html>", "FA"},
        {"Driveline loss (%)", "driveline_loss"},
    };

    @Override
    protected void Process(ActionEvent event) {
        // 1. Save new constants
        processPairs(this.c, pairs, Double.class);
        // override using string method
        this.c.driveline_loss_string(this.driveline_loss.getText());

        // 2. Handle constants change - invalidates caches, rebuilds, and updates all windows
        if (this.eplot != null) {
            this.eplot.handleConstantsChange();
        } else {
            // No eplot reference - just do basic processing
            super.Process(event);
        }
    }

    public ConstantsEditor (Preferences prefs, Constants c) {
        super(prefs.node(Constants.PREFS_TAG), pairs);
        this.c = c;
    }

    @Override
    public void updateDialog() {
        updateDialog(this.c, pairs);
        // override using string method
        this.driveline_loss.setText(this.c.driveline_loss_string());
    }
}

// vim: set sw=4 ts=8 expandtab:
