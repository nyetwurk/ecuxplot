package org.nyet.ecuxplot;

import java.io.File;
import java.io.IOException;
import java.util.TreeMap;

import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

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
    private JCheckBox useMphCheckbox;
    private JLabel startLabel;
    private JLabel endLabel;
    private JTextField rpmPerMphField;
    private JLabel rpmPerMphLabel;
    private JLabel unitLabel;

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
        final ECUxChartPanel chartPanel = new ECUxChartPanel(chart);
        // chartPanel.setBorder(BorderFactory.createLineBorder(Color.black));
        chartPanel.setBorder(BorderFactory.createLoweredBevelBorder());

        // Disable zoom and pan functionality for FATS chart
        chartPanel.setDomainZoomable(false);
        chartPanel.setRangeZoomable(false);
        chartPanel.setMouseZoomable(false);

        // Disable the popup menu (context menu) to prevent zoom options
        chartPanel.setPopupMenu(null);

        panel.add(chartPanel, BorderLayout.CENTER);


        final JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.PAGE_AXIS));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));

        // Create input panel
        final JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.LINE_AXIS));

        // Initialize labels and text fields based on current FATS mode
        this.startLabel = new JLabel("Start");
        this.endLabel = new JLabel("End");
        if (this.fats.useMph()) {
            this.start=new JTextField(""+this.fats.startMph(), 3);
            this.end=new JTextField(""+this.fats.endMph(), 3);
        } else {
            this.start=new JTextField(""+this.fats.start(), 3);
            this.end=new JTextField(""+this.fats.end(), 3);
        }

        inputPanel.add(this.startLabel);
        inputPanel.add(Box.createRigidArea(new Dimension(5,0)));
        inputPanel.add(this.start);
        inputPanel.add(Box.createRigidArea(new Dimension(5,0)));
        inputPanel.add(new JLabel(" to "));
        inputPanel.add(Box.createRigidArea(new Dimension(5,0)));
        inputPanel.add(this.end);
        inputPanel.add(Box.createRigidArea(new Dimension(5,0)));

        // Add unit label (will be updated dynamically)
        this.unitLabel = new JLabel();
        updateUnitLabel(this.unitLabel);
        inputPanel.add(this.unitLabel);

        // Add rpm_per_mph field (always create, show/hide as needed)
        inputPanel.add(Box.createRigidArea(new Dimension(10,0)));
        this.rpmPerMphLabel = new JLabel("RPM/MPH:");
        this.rpmPerMphField = new JTextField(""+this.plotFrame.env.c.rpm_per_mph(), 4);
        inputPanel.add(this.rpmPerMphLabel);
        inputPanel.add(Box.createRigidArea(new Dimension(5,0)));
        inputPanel.add(this.rpmPerMphField);

        controlPanel.add(inputPanel);

        // Add buttons panel
        final JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));

        // Add MPH checkbox
        this.useMphCheckbox = new JCheckBox("Use MPH");
        this.useMphCheckbox.setSelected(this.fats.useMph());
        this.useMphCheckbox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // Toggle MPH mode
                FATSChartFrame.this.fats.useMph(useMphCheckbox.isSelected());
                updateLabelsAndValues(FATSChartFrame.this.startLabel, FATSChartFrame.this.endLabel, FATSChartFrame.this.start, FATSChartFrame.this.end);
                updateRpmPerMphVisibility();
                // Update unit label
                updateUnitLabel(FATSChartFrame.this.unitLabel);
                // Refresh the FATS dataset to show updated results
                FATSChartFrame.this.dataset.refreshFromFATS();
                FATSChartFrame.this.getChartPanel().getChart().setTitle(FATSChartFrame.this.dataset.getTitle());
            }
        });
        buttonPanel.add(this.useMphCheckbox);

        buttonPanel.add(Box.createRigidArea(new Dimension(10,0)));

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

        // Set initial visibility of rpm_per_mph field
        updateRpmPerMphVisibility();
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
        if (this.fats.useMph()) {
            this.start.setText("" + this.fats.startMph());
            this.end.setText("" + this.fats.endMph());
        } else {
            this.start.setText("" + this.fats.start());
            this.end.setText("" + this.fats.end());
        }
        this.getChartPanel().getChart().setTitle(this.dataset.getTitle());
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        if(event.getActionCommand().equals("Apply")) {
            if (this.fats.useMph()) {
                this.fats.startMph(Double.valueOf(this.start.getText()));
                this.fats.endMph(Double.valueOf(this.end.getText()));
            } else {
                this.fats.start(Integer.valueOf(this.start.getText()));
                this.fats.end(Integer.valueOf(this.end.getText()));
            }
            // Update rpm_per_mph if field is visible (regardless of mode)
            if (this.rpmPerMphField != null && this.rpmPerMphField.isVisible()) {
                this.plotFrame.env.c.rpm_per_mph(Double.valueOf(this.rpmPerMphField.getText()));
            }
            this.dataset.refreshFromFATS();
        } else if(event.getActionCommand().equals("Defaults")) {
            this.fats.useMph(false);
            this.fats.start(4200);
            this.fats.end(6500);
            this.dataset.refreshFromFATS();
            // Update text fields to show defaults
            this.start.setText("4200");
            this.end.setText("6500");
            // Update checkbox to reflect defaults
            this.useMphCheckbox.setSelected(false);
            updateRpmPerMphVisibility();
        }
        this.getChartPanel().getChart().setTitle(this.dataset.getTitle());
    }

    private void updateLabelsAndValues(JLabel startLabel, JLabel endLabel, JTextField start, JTextField end) {
        // Labels are now static "Start" and "End" - no need to change them
        // Values are updated based on current mode
        if (this.fats.useMph()) {
            start.setText("" + this.fats.startMph());
            end.setText("" + this.fats.endMph());
        } else {
            start.setText("" + this.fats.start());
            end.setText("" + this.fats.end());
        }
    }

    private void updateUnitLabel(JLabel unitLabel) {
        if (this.fats.useMph()) {
            unitLabel.setText("MPH");
        } else {
            unitLabel.setText("RPM");
        }
    }

    private void updateRpmPerMphVisibility() {
        if (this.rpmPerMphField != null && this.rpmPerMphLabel != null) {
            // Show rpm_per_mph field when using MPH mode
            boolean shouldShow = this.fats.useMph();
            this.rpmPerMphField.setVisible(shouldShow);
            this.rpmPerMphLabel.setVisible(shouldShow);
            if (shouldShow) {
                this.rpmPerMphField.setText("" + this.plotFrame.env.c.rpm_per_mph());
            }
        }
    }

    /**
     * Update the rpm_per_mph field from the Constants when it changes
     */
    public void updateRpmPerMphFromConstants() {
        if (this.rpmPerMphField != null) {
            this.rpmPerMphField.setText("" + this.plotFrame.env.c.rpm_per_mph());
        }
    }

    private java.awt.Dimension windowSize() {
        return new java.awt.Dimension(
            ECUxPlot.getPreferences().getInt("FATSWindowWidth", 300),
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
        // Uncheck "Show FATS window..." in Options menu when window is closed
        this.plotFrame.prefs.putBoolean("showfats", false);
        // Update the menu checkbox to reflect the change
        this.plotFrame.optionsMenu.updateFATSCheckbox();
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
