package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

import java.awt.event.*;
import javax.swing.*;

public class SAEEditor extends PreferencesEditor {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private final SAE s;

    public JTextField temperature;
    public JTextField altitude;
    public JTextField humidity;
    public JLabel correction;

    @Override
    protected void Process(ActionEvent event) {
        this.s.temperature(Double.valueOf(this.temperature.getText()));
        this.s.altitude(Double.valueOf(this.altitude.getText()));
        this.s.humidity(Double.valueOf(this.humidity.getText()));
        updateCorrection();

        // Enable SAE when settings are applied (same pattern as Filter window)
        this.s.enabled(true);

        // Update the SAE checkbox in OptionsMenu to reflect that SAE is enabled
        if (this.eplot != null && this.eplot.optionsMenu != null) {
            this.eplot.optionsMenu.updateSAECheckBox();
        }

        // Trigger rebuild to update chart with new SAE correction values
        // CACHE COHERENCY: SAE correction affects WHP and HP columns which are marked as
        // VEHICLE_CONSTANTS. rebuild() automatically invalidates VEHICLE_CONSTANTS columns,
        // so columns will be recreated with new SAE correction values.
        // eplot is always set by showDialog() when owner is ECUxPlot
        if (this.eplot != null) {
            this.eplot.rebuild();
        } else {
            // Fallback: try super.Process() which also calls rebuild if eplot is set
            super.Process(event);
        }
    }

    private static String [][] pairs = {
        { "Temperature (C)", "temperature" },
        { "Altitude (m)", "altitude" },
        { "Humidity (%)", "humidity" },
        { "SAE correction", "correction" }
    };
    private static int [] fieldSizes = {4,5,3,0};

    public SAEEditor (Preferences prefs, SAE s) {
        super(prefs.node(SAE.PREFS_TAG), pairs, fieldSizes);
        this.s = s;
    }

    private String getCorrection() {
        return String.format("%.3f",this.s.correction());
    }
    private void updateCorrection() {
        this.correction.setText(getCorrection());
    }

    @Override
    public void updateDialog() {
        this.temperature.setText("" + this.s.temperature());
        this.altitude.setText("" + this.s.altitude());
        this.humidity.setText("" + this.s.humidity());
        updateCorrection();
    }

    @Override
    protected String[] getExcludedKeysFromDefaults() {
        // Preserve the "enabled" preference when resetting to defaults
        // Only reset temperature, altitude, and humidity values
        return new String[] { "enabled" };
    }
}

// vim: set sw=4 ts=8 expandtab:
