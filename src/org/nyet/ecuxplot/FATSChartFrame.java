package org.nyet.ecuxplot;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.TreeMap;

import javax.swing.*;

import org.jfree.chart.ChartFrame;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BarRenderer;

public class FATSChartFrame extends ChartFrame implements ActionListener {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private final FATSDataset dataset;
    private final ECUxPlot plotFrame;
    private final JTextField start;
    private final JTextField end;
    private final FATS fats;
    private JComboBox<String> speedUnitCombo;
    private JLabel startLabel;
    private JLabel endLabel;
    private JTextField rpmPerMphField;
    private JLabel rpmPerMphLabel;
    private JLabel unitLabel;
    private JPanel conversionGroup;

    public static FATSChartFrame createFATSChartFrame(
            TreeMap<String, ECUxDataset> fileDatasets, ECUxPlot plotFrame) {
        final FATS fats = plotFrame.fats; // Use the FATS instance from ECUxPlot
        final FATSDataset dataset = new FATSDataset(fileDatasets, fats);
        final JFreeChart chart =
            ECUxChartFactory.createFATSChart(dataset);
        return new FATSChartFrame(chart, dataset, plotFrame, fats);
    }

    public FATSChartFrame (JFreeChart chart, FATSDataset dataset,
            ECUxPlot plotFrame, FATS fats) {
        super("FATS Time", chart);

        this.dataset=dataset;
        this.plotFrame=plotFrame;
        this.fats = fats;

        final CategoryPlot plot = chart.getCategoryPlot();
        plot.getRangeAxis().setLabel("seconds");
        final BarRenderer renderer = (BarRenderer)plot.getRenderer();

        renderer.setBaseItemLabelGenerator(
            new org.jfree.chart.labels.StandardCategoryItemLabelGenerator(
                "{2}", new java.text.DecimalFormat("##.##")
            )
        );
        renderer.setBaseItemLabelsVisible(true);

        final JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        final ECUxChartPanel chartPanel = new ECUxChartPanel(chart, plotFrame);
        // chartPanel.setBorder(BorderFactory.createLineBorder(Color.black));
        chartPanel.setBorder(BorderFactory.createLoweredBevelBorder());

        // Disable zoom and pan functionality for FATS chart
        chartPanel.setDomainZoomable(false);
        chartPanel.setRangeZoomable(false);
        chartPanel.setMouseZoomable(false);

        panel.add(chartPanel, BorderLayout.CENTER);

        final JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.PAGE_AXIS));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));

        // Create main input panel with horizontal layout to use full width
        JPanel mainInputPanel = new JPanel(new BorderLayout());

        this.speedUnitCombo = new JComboBox<>(new String[]{"RPM", "MPH", "KPH"});

        // Add action listener to combo box
        this.speedUnitCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                FATSChartFrame.this.fats.speedUnit(getSelectedSpeedUnit());
                updateLabelsAndValues(FATSChartFrame.this.startLabel, FATSChartFrame.this.endLabel, FATSChartFrame.this.start, FATSChartFrame.this.end);
                updateRpmFieldsVisibility();
                updateUnitLabel(FATSChartFrame.this.unitLabel);
                FATSChartFrame.this.dataset.refreshFromFATS();
                FATSChartFrame.this.getChartPanel().getChart().setTitle(FATSChartFrame.this.dataset.getTitle());
            }
        });

        // Create range group with border
        JPanel rangeGroup = new JPanel();
        rangeGroup.setLayout(new BoxLayout(rangeGroup, BoxLayout.X_AXIS));
        rangeGroup.setBorder(BorderFactory.createTitledBorder("Range"));

        // Initialize labels and text fields based on current FATS mode
        this.startLabel = new JLabel("Start");
        this.endLabel = new JLabel("End");
        switch (this.fats.speedUnit()) {
            case MPH:
                this.start=new JTextField(""+Math.round(this.fats.startMph()), 6);
                this.end=new JTextField(""+Math.round(this.fats.endMph()), 6);
                break;
            case KPH:
                this.start=new JTextField(""+Math.round(this.fats.startKph()), 6);
                this.end=new JTextField(""+Math.round(this.fats.endKph()), 6);
                break;
            case RPM:
            default:
                this.start=new JTextField(""+this.fats.start(), 6);
                this.end=new JTextField(""+this.fats.end(), 6);
                break;
        }

        rangeGroup.add(this.startLabel);
        rangeGroup.add(Box.createRigidArea(new Dimension(5,0)));
        rangeGroup.add(this.start);
        rangeGroup.add(Box.createRigidArea(new Dimension(5,0)));
        rangeGroup.add(new JLabel(" to "));
        rangeGroup.add(Box.createRigidArea(new Dimension(5,0)));
        rangeGroup.add(this.end);

        // Add unit label (will be updated dynamically) - only for speed modes
        this.unitLabel = new JLabel();
        updateUnitLabel(this.unitLabel);
        rangeGroup.add(Box.createRigidArea(new Dimension(5,0)));
        rangeGroup.add(this.unitLabel);

        // Add speed unit dropdown
        rangeGroup.add(Box.createRigidArea(new Dimension(5,0)));
        rangeGroup.add(this.speedUnitCombo);

        // Add glue to push everything to the left
        rangeGroup.add(Box.createHorizontalGlue());

        // Create conversion group with border
        this.conversionGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2)); // Reduced vertical spacing
        this.conversionGroup.setBorder(BorderFactory.createTitledBorder("Conversion"));

        // Add rpm conversion fields
        this.rpmPerMphLabel = new JLabel("RPM per MPH:");
        this.rpmPerMphField = new JTextField(""+this.plotFrame.env.c.rpm_per_mph(), 4);
        this.conversionGroup.add(this.rpmPerMphLabel);
        this.conversionGroup.add(Box.createRigidArea(new Dimension(5,0)));
        this.conversionGroup.add(this.rpmPerMphField);

        // Add groups to main panel - range takes center, conversion takes east
        mainInputPanel.add(rangeGroup, BorderLayout.CENTER);
        mainInputPanel.add(this.conversionGroup, BorderLayout.EAST);

        controlPanel.add(mainInputPanel);

        // Add buttons panel
        final JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));

        final JButton apply = new JButton("Apply");
        apply.addActionListener(this);
        this.getRootPane().setDefaultButton(apply);
        buttonPanel.add(apply);

        buttonPanel.add(Box.createRigidArea(new Dimension(5,0)));

        final JButton defaults = new JButton("Defaults");
        defaults.addActionListener(this);
        buttonPanel.add(defaults);

        controlPanel.add(buttonPanel);
        panel.add(controlPanel, BorderLayout.PAGE_END);

        this.setContentPane(panel);
        this.setPreferredSize(this.windowSize());
        restoreLocation();

        // Set initial combo box selection and visibility
        updateComboBoxSelection();
        updateRpmFieldsVisibility();
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

    private void updateComboBoxSelection() {
        FATS.SpeedUnit speedUnit = this.fats.speedUnit();
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
    }

    private void restoreLocation() {
        final Toolkit tk = Toolkit.getDefaultToolkit();
        final Dimension d = tk.getScreenSize();
        final Dimension s = this.windowSize();
        final Point pl = this.plotFrame.getLocation();

        final Point l = this.windowLocation();
        l.translate(pl.x, pl.y);
        if(l.x<0) l.x=0;
        if(l.y<0) l.y=0;
        if(l.x+s.width > d.width-s.width) l.x=d.width-s.width;
        if(l.y+s.height > d.height-s.width) l.y=d.height-s.height;
        super.setLocation(l);
    }

    public void setDatasets(TreeMap<String, ECUxDataset> fileDatasets) {
        this.dataset.clear();
        for(final ECUxDataset data : fileDatasets.values()) {
            setDataset(data);
        }
    }

    public void setDataset(ECUxDataset data) {
        this.dataset.setValue(data);
    }
    public void clearDataset() {
        this.dataset.clear();
    }

    public void refreshFromFATS() {
        this.dataset.refreshFromFATS();
        // Update text fields to show current values
        switch (this.fats.speedUnit()) {
            case MPH:
                this.start.setText("" + Math.round(this.fats.startMph()));
                this.end.setText("" + Math.round(this.fats.endMph()));
                break;
            case KPH:
                this.start.setText("" + Math.round(this.fats.startKph()));
                this.end.setText("" + Math.round(this.fats.endKph()));
                break;
            case RPM:
            default:
                this.start.setText("" + this.fats.start());
                this.end.setText("" + this.fats.end());
                break;
        }
        this.getChartPanel().getChart().setTitle(this.dataset.getTitle());
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        if(event.getActionCommand().equals("Apply")) {
            FATS.SpeedUnit speedUnit = getSelectedSpeedUnit();
            this.fats.speedUnit(speedUnit);

            switch (speedUnit) {
                case MPH:
                    this.fats.startMph(Double.valueOf(this.start.getText()));
                    this.fats.endMph(Double.valueOf(this.end.getText()));
                    break;
                case KPH:
                    this.fats.startKph(Double.valueOf(this.start.getText()));
                    this.fats.endKph(Double.valueOf(this.end.getText()));
                    break;
                case RPM:
                default:
                    this.fats.start(Integer.valueOf(this.start.getText()));
                    this.fats.end(Integer.valueOf(this.end.getText()));
                    break;
            }

            // Update rpm conversion fields if visible
            if (this.rpmPerMphField != null && this.rpmPerMphField.isVisible()) {
                this.plotFrame.env.c.rpm_per_mph(Double.valueOf(this.rpmPerMphField.getText()));
            }

            this.dataset.refreshFromFATS();
        } else if(event.getActionCommand().equals("Defaults")) {
            this.fats.speedUnit(FATS.SpeedUnit.RPM);
            this.fats.start(4200);
            this.fats.end(6500);
            this.dataset.refreshFromFATS();
            // Update text fields to show defaults
            this.start.setText("4200");
            this.end.setText("6500");
            // Update radio buttons to reflect defaults
            updateComboBoxSelection();
            updateRpmFieldsVisibility();
        }
        this.getChartPanel().getChart().setTitle(this.dataset.getTitle());
    }

    private void updateLabelsAndValues(JLabel startLabel, JLabel endLabel, JTextField start, JTextField end) {
        // Labels are now static "Start" and "End" - no need to change them
        // Values are updated based on current mode
        FATS.SpeedUnit speedUnit = getSelectedSpeedUnit();
        switch (speedUnit) {
            case MPH:
                start.setText("" + Math.round(this.fats.startMph()));
                end.setText("" + Math.round(this.fats.endMph()));
                break;
            case KPH:
                start.setText("" + Math.round(this.fats.startKph()));
                end.setText("" + Math.round(this.fats.endKph()));
                break;
            case RPM:
            default:
                start.setText("" + this.fats.start());
                end.setText("" + this.fats.end());
                break;
        }
    }

    private void updateUnitLabel(JLabel unitLabel) {
        // Hide unit label in all modes since radio buttons indicate the units
        unitLabel.setVisible(false);
    }

    private void updateRpmFieldsVisibility() {
        FATS.SpeedUnit speedUnit = getSelectedSpeedUnit();
        // Show rpm conversion fields when not using RPM mode
        boolean showRpmFields = speedUnit != FATS.SpeedUnit.RPM;
        this.rpmPerMphLabel.setVisible(showRpmFields);
        this.rpmPerMphField.setVisible(showRpmFields);
        this.conversionGroup.setVisible(showRpmFields);

        if (showRpmFields) {
            this.rpmPerMphField.setText("" + this.plotFrame.env.c.rpm_per_mph());
        }
    }

    /**
     * Update the rpm conversion fields from the Constants when they change
     */
    public void updateRpmFieldsFromConstants() {
        if (this.rpmPerMphField != null) {
            this.rpmPerMphField.setText("" + this.plotFrame.env.c.rpm_per_mph());
        }
    }

    private java.awt.Dimension windowSize() {
        return new java.awt.Dimension(
            ECUxPlot.getPreferences().getInt("FATSWindowWidth", 600),
            ECUxPlot.getPreferences().getInt("FATSWindowHeight", 400));
    }

    private void putWindowSize() {
        ECUxPlot.getPreferences().putInt("FATSWindowWidth",
                this.getWidth());
        ECUxPlot.getPreferences().putInt("FATSWindowHeight",
                this.getHeight());
    }

    // relative to plot frame
    private java.awt.Point windowLocation() {
        return new java.awt.Point(
            ECUxPlot.getPreferences().getInt("FATSWindowX",
                this.plotFrame.getWidth()),
            ECUxPlot.getPreferences().getInt("FATSWindowY", 0));
    }

    private void putWindowLocation() {
        final Point l = this.getLocation();
        final Point pl = this.plotFrame.getLocation();
        l.translate(-pl.x, -pl.y);
        ECUxPlot.getPreferences().putInt("FATSWindowX", l.x);
        ECUxPlot.getPreferences().putInt("FATSWindowY", l.y);
    }

    // cleanup
    @Override
    public void dispose() {
        putWindowSize();
        putWindowLocation();
        // FATS window is now a menu item, no persistence needed
        super.dispose();
    }

    public void saveChartAsPNG(File f) throws IOException {
           ChartUtilities.saveChartAsPNG(f, this.getChartPanel().getChart(), this.getWidth(),
                   this.getHeight());
    }

    public void saveChartAsPNG(String filename) throws IOException {
           this.saveChartAsPNG(new File(filename));
    }
}

// vim: set sw=4 ts=8 expandtab:
