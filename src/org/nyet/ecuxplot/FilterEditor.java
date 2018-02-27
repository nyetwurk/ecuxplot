package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

import java.awt.event.*;
import javax.swing.*;

public class FilterEditor extends PreferencesEditor {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private final Filter filter;

    public JTextField gear;
    public JTextField minRPM;
    public JTextField maxRPM;
    public JTextField minRPMRange;
    public JTextField monotonicRPMfuzz;
    public JTextField minPedal;
    public JTextField minThrottle;
    public JTextField minPoints;
    public JTextField HPTQMAW;
    public JTextField ZeitMAW;

    @Override
    protected void Process(ActionEvent event) {
	processPairs(this.filter, pairs, Integer.class);
	super.Process(event);
    }

    private static final String [][] pairs = {
	{"Gear (-1 to ignore)", "gear"},
	{"Minimum RPM", "minRPM"},
	{"Maximum RPM", "maxRPM"},
	{"Minimum RPM range of run", "minRPMRange"},
	{"RPM fuzz tolerance", "monotonicRPMfuzz"},
	{"Minimum Pedal", "minPedal"},
	{"Minimum Throttle", "minThrottle"},
	{"Minimum Points", "minPoints"},
	{"HP/TQ smoothing", "HPTQMAW"},
	{"Zeitronix smoothing", "ZeitMAW"},
    };

    public FilterEditor (Preferences prefs, Filter filter) {
	super(prefs.node(Filter.PREFS_TAG), pairs);
	this.filter = filter;
    }

    @Override
    public void updateDialog()
    {
	updateDialog(this.filter, pairs);
    }
}
