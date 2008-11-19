package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class SAEEditor extends PreferencesEditor {
    private SAE s;

    private JTextField temperature;
    private JTextField altitude;
    private JTextField humidity;
    private JLabel correction;

    protected void Process(ActionEvent event) {
	this.s.temperature(Double.valueOf(this.temperature.getText()));
	this.s.altitude(Double.valueOf(this.altitude.getText()));
	this.s.humidity(Double.valueOf(this.humidity.getText()));
	updateCorrection();
	super.Process(event);
    }

    public SAEEditor (Preferences prefs, SAE s) {
	super(prefs.node(SAE.PREFS_TAG));

	this.s = s;
	JPanel pp = this.getPrefsPanel();

	pp.add(new JLabel(" Temperature (C):"));
	this.temperature = new JTextField(10);
	pp.add(this.temperature);

	pp.add(new JLabel(" Altitude (m):"));
	this.altitude = new JTextField(10);
	pp.add(this.altitude);

	pp.add(new JLabel(" Humidity (%):"));
	this.humidity = new JTextField(10);
	pp.add(this.humidity);

	pp.add(new JLabel(" SAE correction:"));
	this.correction = new JLabel(getCorrection());
	pp.add(this.correction);
    }

    private String getCorrection() {
	return String.format("%.3f",this.s.correction());
    }
    private void updateCorrection() {
	this.correction.setText(getCorrection());
    }

    public void updateDialog() {
	this.temperature.setText("" + this.s.temperature());
	this.altitude.setText("" + this.s.altitude());
	this.humidity.setText("" + this.s.humidity());
	updateCorrection();
    }
}
