package org.nyet.ecuxplot;

import java.awt.*;
import java.awt.event.*;
import java.util.prefs.Preferences;

import javax.swing.*;

public class FATSEditor extends PreferencesEditor {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    private final FATS s;
    private final Constants c;

    public JTextField start;
    public JTextField end;
    public JTextField rpmPerMph;
    public JCheckBox useMph;
    public JLabel startLabel;
    public JLabel endLabel;
    public JLabel rpmPerMphLabel;

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

        // Handle rpm_per_mph separately
        this.c.rpm_per_mph(Double.valueOf(this.rpmPerMph.getText()));

        super.Process(event);
    }

    private static String [][] pairs = {
        { "Range", "start" },
        { "RPM per MPH", "rpmPerMph" },
    };
    private static int [] fieldSizes = {6,4};

    public FATSEditor (Preferences prefs, FATS s, Constants c) {
        super(prefs.node(FATS.PREFS_TAG), pairs, fieldSizes);
        this.s = s;
        this.c = c;

        // Create a custom clean layout
        JPanel customPanel = new JPanel(new BorderLayout());

        // Create the range input panel with clean layout
        JPanel rangePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        // Add start field
        this.start = new JTextField("" + this.s.start(), 3);
        rangePanel.add(this.start);

        // Add "to" label
        rangePanel.add(new JLabel(" to "));

        // Add end field
        this.end = new JTextField("" + this.s.end(), 3);
        rangePanel.add(this.end);

        // Add unit label (will be updated dynamically)
        this.startLabel = new JLabel("RPM");
        rangePanel.add(this.startLabel);

        // Add MPH checkbox
        this.useMph = new JCheckBox("Use MPH instead of RPM");
        this.useMph.setSelected(this.s.useMph());
        this.useMph.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateLabels();
                updateValues();
                updateRpmPerMphVisibility();
            }
        });

        // Create top panel with checkboxes
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(this.useMph);

        // Create bottom panel with range and rpm_per_mph
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.add(new JLabel("Range: "));
        bottomPanel.add(rangePanel);

        // Add rpm_per_mph field
        this.rpmPerMph = new JTextField("" + this.c.rpm_per_mph(), 4);
        this.rpmPerMphLabel = new JLabel("RPM/MPH:");
        bottomPanel.add(Box.createHorizontalStrut(20));
        bottomPanel.add(this.rpmPerMphLabel);
        bottomPanel.add(this.rpmPerMph);

        // Add panels to custom layout
        customPanel.add(topPanel, BorderLayout.NORTH);
        customPanel.add(bottomPanel, BorderLayout.CENTER);

        // Replace the center content with our custom panel
        this.remove(this.getPrefsPanel());
        this.add(customPanel, BorderLayout.CENTER);

        // Initialize labels and visibility
        updateLabels();
        updateRpmPerMphVisibility();
    }

    @Override
    public void updateDialog() {
        this.useMph.setSelected(this.s.useMph());
        this.rpmPerMph.setText("" + this.c.rpm_per_mph());
        updateLabels();
        updateValues();
        updateRpmPerMphVisibility();
    }

    private void updateLabels() {
        if (this.useMph.isSelected()) {
            this.startLabel.setText("MPH");
        } else {
            this.startLabel.setText("RPM");
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

    private void updateRpmPerMphVisibility() {
        // Show rpm_per_mph field and label when NOT using MPH mode
        boolean showRpmPerMph = !this.useMph.isSelected();
        this.rpmPerMphLabel.setVisible(showRpmPerMph);
        this.rpmPerMph.setVisible(showRpmPerMph);
    }
}

// vim: set sw=4 ts=8 expandtab:
