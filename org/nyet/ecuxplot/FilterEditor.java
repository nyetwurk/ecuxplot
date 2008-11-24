package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class FilterEditor extends PreferencesEditor {
    private Filter filter;

    public JTextField gear;
    public JTextField minRPM;
    public JTextField maxRPM;
    public JTextField minRPMRange;
    public JTextField minPedal;
    public JTextField minThrottle;
    public JTextField minPoints;
    public JTextField HPTQMAW;

    protected void Process(ActionEvent event) {
	processPairs(this.filter, pairs, Integer.class);
	super.Process(event);
    }

    private static final String [][] pairs = {
	{"Gear", "gear"},
	{"Minimum RPM", "minRPM"},
	{"Maximum RPM", "maxRPM"},
	{"Minimum RPM range of run", "minRPMRange"},
	{"Minimum Pedal", "minPedal"},
	{"Minimum Throttle", "minThrottle"},
	{"Minimum Points", "minPoints"},
	{"HW/TQ smoothing", "HPTQMAW"},
    };

    public FilterEditor (Preferences prefs, Filter filter) {
	super(prefs.node(Filter.PREFS_TAG), pairs);
	this.filter = filter;
    }

    public void updateDialog()
    {
	updateDialog(this.filter, pairs);
    }
}
