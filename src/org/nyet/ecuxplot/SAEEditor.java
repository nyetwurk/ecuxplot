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
        super.Process(event);
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
}

// vim: set sw=4 ts=8 expandtab:
