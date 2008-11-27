package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class FuelingEditor extends PreferencesEditor {
    private Fueling fueling;

    public JTextField MAF;
    public JLabel MAFCorrection;
    public JTextField injector;
    public JTextField MAF_offset;
    public JTextField cylinders;
    public JTextField turbos;

    protected void Process(ActionEvent event) {
	this.fueling.MAF(Double.valueOf(this.MAF.getText()));
	updateMAFCorrection();
	this.fueling.injector(Double.valueOf(this.injector.getText()));
	this.fueling.MAF_offset(Double.valueOf(this.MAF_offset.getText()));
	this.fueling.cylinders(Integer.valueOf(this.cylinders.getText()));
	this.fueling.turbos(Integer.valueOf(this.turbos.getText()));
	super.Process(event);
    }

    private static final String [][] pairs = {
	{ "MAF diameter (mm)", "MAF" },
	{ "MAF correction (%)", "MAFCorrection" },
	{ "Injector size (cc/min)", "injector" },
	{ "MAF offset (g/sec)", "MAF_offset" },
	{ "Cylinders",  "cylinders" },
	{ "Turbos",  "turbos" }
    };
    private static final int [] fieldSizes = { 6, 0, 6, 6, 6, 6 };

    public FuelingEditor (Preferences prefs, Fueling f) {
        super(prefs.node(Fueling.PREFS_TAG), pairs, fieldSizes);
	this.fueling=f;
    }

    private String getMAFCorrection() {
	return String.format("%.1f",this.fueling.MAF_correction()*100);
    }

    private void updateMAFCorrection() {
	this.MAFCorrection.setText(getMAFCorrection());
    }

    public void updateDialog() {
	this.MAF.setText("" + this.fueling.MAF());
	updateMAFCorrection();
	this.injector.setText("" + this.fueling.injector());
	this.MAF_offset.setText("" + this.fueling.MAF_offset());
	this.cylinders.setText("" + this.fueling.cylinders());
	this.turbos.setText("" + this.fueling.turbos());
    }
}
