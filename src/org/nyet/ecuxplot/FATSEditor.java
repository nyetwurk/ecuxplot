package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

import java.awt.event.*;
import javax.swing.*;
import java.awt.*;

public class FATSEditor extends PreferencesEditor {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private final FATS s;

    public JTextField start;
    public JTextField end;
    public JCheckBox useMph;
    public JLabel startLabel;
    public JLabel endLabel;

    @Override
    protected void Process(ActionEvent event) {
	if (this.useMph.isSelected()) {
	    this.s.startMph(Double.valueOf(this.start.getText()));
	    this.s.endMph(Double.valueOf(this.end.getText()));
	} else {
	    this.s.start(Integer.valueOf(this.start.getText()));
	    this.s.end(Integer.valueOf(this.end.getText()));
	}
	this.s.useMph(this.useMph.isSelected());
	super.Process(event);
    }

    private static String [][] pairs = {
	{ "Start RPM", "start" },
	{ "End RPM", "end" },
    };
    private static int [] fieldSizes = {3,3};

    public FATSEditor (Preferences prefs, FATS s, Constants c) {
	super(prefs.node(FATS.PREFS_TAG), pairs, fieldSizes);
	this.s = s;

	// Add MPH checkbox
	this.useMph = new JCheckBox("Use MPH instead of RPM");
	this.useMph.addActionListener(new ActionListener() {
	    public void actionPerformed(ActionEvent e) {
		updateLabels();
		updateValues();
	    }
	});

	// Get references to the labels for dynamic updating
	Component[] components = this.getPrefsPanel().getComponents();
	for (int i = 0; i < components.length; i++) {
	    if (components[i] instanceof JLabel) {
		if (i == 0) this.startLabel = (JLabel) components[i];
		else if (i == 2) this.endLabel = (JLabel) components[i];
	    }
	}

	// Create a custom panel that contains everything
	JPanel customPanel = new JPanel(new BorderLayout());

	// Add MPH checkbox at the top
	JPanel mphPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
	mphPanel.add(this.useMph);
	customPanel.add(mphPanel, BorderLayout.NORTH);

	// Add the prefs panel in the center
	customPanel.add(this.getPrefsPanel(), BorderLayout.CENTER);

	// Replace the center content with our custom panel
	this.remove(this.getPrefsPanel());
	this.add(customPanel, BorderLayout.CENTER);
    }

    @Override
    public void updateDialog() {
	this.useMph.setSelected(this.s.useMph());
	updateLabels();
	updateValues();
    }

    private void updateLabels() {
	if (this.useMph.isSelected()) {
	    this.startLabel.setText("Start MPH:");
	    this.endLabel.setText("End MPH:");
	} else {
	    this.startLabel.setText("Start RPM:");
	    this.endLabel.setText("End RPM:");
	}
    }

    private void updateValues() {
	if (this.useMph.isSelected()) {
	    this.start.setText("" + this.s.startMph());
	    this.end.setText("" + this.s.endMph());
	} else {
	    this.start.setText("" + this.s.start());
	    this.end.setText("" + this.s.end());
	}
    }
}
