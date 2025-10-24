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
    public JLabel rpmPerMphLabel;
    public JComboBox<String> speedUnitCombo;
    public JLabel startLabel;
    public JLabel endLabel;

    @Override
    protected void Process(ActionEvent event) {
        FATS.SpeedUnit speedUnit = getSelectedSpeedUnit();
        this.s.speedUnit(speedUnit);

        // Use the handler to eliminate switch statement
        FATS.SpeedUnitHandler handler = speedUnit.getHandler();
        handler.setStartValue(this.s, Double.valueOf(this.start.getText()));
        handler.setEndValue(this.s, Double.valueOf(this.end.getText()));

        // Handle rpm_per_mph separately
        this.c.rpm_per_mph(Double.valueOf(this.rpmPerMph.getText()));

        super.Process(event);
    }

    private FATS.SpeedUnit getSelectedSpeedUnit() {
        String selectedUnit = (String) this.speedUnitCombo.getSelectedItem();
        if ("MPH".equals(selectedUnit)) {
            return FATS.SpeedUnit.MPH;
        } else if ("KPH".equals(selectedUnit)) {
            return FATS.SpeedUnit.KPH;
        } else {
            return FATS.SpeedUnit.RPM;
        }
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

        // Create speed unit selection panel
        JPanel speedUnitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        speedUnitPanel.add(new JLabel("Speed Unit: "));

        this.speedUnitCombo = new JComboBox<>(new String[]{"RPM", "MPH", "KPH"});

        // Add action listener to combo box
        this.speedUnitCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateLabels();
                updateValues();
                updateRpmFieldsVisibility();
            }
        });

        // Create main panel with horizontal layout to use full width
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Create range group with border
        JPanel rangeGroup = new JPanel();
        rangeGroup.setLayout(new BoxLayout(rangeGroup, BoxLayout.X_AXIS));
        rangeGroup.setBorder(BorderFactory.createTitledBorder("Range"));

        // Add start field
        this.start = new JTextField("" + this.s.start(), 6);
        rangeGroup.add(this.start);

        // Add "to" label
        rangeGroup.add(Box.createRigidArea(new Dimension(5,0)));
        rangeGroup.add(new JLabel(" to "));
        rangeGroup.add(Box.createRigidArea(new Dimension(5,0)));

        // Add end field
        this.end = new JTextField("" + this.s.end(), 6);
        rangeGroup.add(this.end);

        // Add unit label (will be updated dynamically)
        this.startLabel = new JLabel("RPM");
        rangeGroup.add(Box.createRigidArea(new Dimension(5,0)));
        rangeGroup.add(this.startLabel);

        // Add speed unit dropdown
        rangeGroup.add(Box.createRigidArea(new Dimension(5,0)));
        rangeGroup.add(this.speedUnitCombo);

        // Add glue to push everything to the left
        rangeGroup.add(Box.createHorizontalGlue());

        // Create conversion group with border
        JPanel conversionGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2)); // Reduced vertical spacing
        conversionGroup.setBorder(BorderFactory.createTitledBorder("Conversion"));

        // Add rpm_per_mph field
        this.rpmPerMphLabel = new JLabel("RPM per MPH:");
        this.rpmPerMph = new JTextField("" + this.c.rpm_per_mph(), 4);
        conversionGroup.add(this.rpmPerMphLabel);
        conversionGroup.add(this.rpmPerMph);

        // Add groups to main panel - range takes center, conversion takes east
        mainPanel.add(rangeGroup, BorderLayout.CENTER);
        mainPanel.add(conversionGroup, BorderLayout.EAST);

        // Add main panel to custom layout
        customPanel.add(mainPanel, BorderLayout.CENTER);

        // Replace the center content with our custom panel
        this.remove(this.getPrefsPanel());
        this.add(customPanel, BorderLayout.CENTER);

        // Initialize labels and visibility
        updateLabels();
        updateRpmFieldsVisibility();
    }

    @Override
    public void updateDialog() {
        FATS.SpeedUnit speedUnit = this.s.speedUnit();
        switch (speedUnit) {
            case RPM:
                this.speedUnitCombo.setSelectedItem("RPM");
                break;
            case MPH:
                this.speedUnitCombo.setSelectedItem("MPH");
                break;
            case KPH:
                this.speedUnitCombo.setSelectedItem("KPH");
                break;
        }

        this.rpmPerMph.setText("" + this.c.rpm_per_mph());

        updateLabels();
        updateValues();
        updateRpmFieldsVisibility();
    }

    private void updateLabels() {
        // Hide unit label in all modes since radio buttons indicate the units
        this.startLabel.setVisible(false);
    }

    private void updateValues() {
        FATS.SpeedUnit speedUnit = getSelectedSpeedUnit();

        // Use the handler to eliminate switch statement
        FATS.SpeedUnitHandler handler = speedUnit.getHandler();
        double startValue = handler.getStartValue(this.s);
        double endValue = handler.getEndValue(this.s);

        // Round values for MPH and KPH to whole numbers
        if (speedUnit != FATS.SpeedUnit.RPM) {
            startValue = Math.round(startValue);
            endValue = Math.round(endValue);
        }

        this.start.setText("" + (int)startValue);
        this.end.setText("" + (int)endValue);
    }

    private void updateRpmFieldsVisibility() {
        FATS.SpeedUnit speedUnit = getSelectedSpeedUnit();

        // Use the handler to determine if RPM conversion fields should be shown
        FATS.SpeedUnitHandler handler = speedUnit.getHandler();
        boolean showRpmFields = handler.requiresRpmConversionFields();

        this.rpmPerMphLabel.setVisible(showRpmFields);
        this.rpmPerMph.setVisible(showRpmFields);
    }
}

// vim: set sw=4 ts=8 expandtab:
