package org.nyet.ecuxplot;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.TreeMap;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.table.*;

import org.nyet.logfile.Dataset;
import org.nyet.util.Strings;

/**
 * Filter Window - Integrates filter parameter editing with real-time data visualization
 * Provides a unified interface for configuring filters and immediately seeing their impact on data
 */
public class FilterWindow extends ECUxPlotWindow {
    private static final long serialVersionUID = 1L;

    // Filter editing components (from FilterEditor)
    private JButton okButton; // Store reference to OK button

    // Filter parameter fields
    private JSpinner gear;
    private JTextField minRPM;
    private JTextField maxRPM;
    private JTextField minRPMRange;
    private JTextField monotonicRPMfuzz;
    private JTextField minPedal;
    private JTextField minThrottle;
    private JTextField minAcceleration;
    private JTextField accelMAW;
    private JTextField minPoints;

    // Data visualization components
    private ECUxDataset dataset;
    private ArrayList<Integer> rowIndexMapping;
    private JTable dataTable;
    private DefaultTableModel tableModel;
    private JLabel statusLabel;
    private JCheckBox showRPMDataCheckBox;
    private JSpinner maxRowsSpinner;
    private JComboBox<String> fileSelector;
    private JLabel fileLabel;
    private JCheckBox showOnlyValidDataCheckBox;

    // Column definitions with metadata
    private enum Column {
        SAMPLE(0, 80, false),
        TIME(1, 80, false),
        RPM(2, 60, false),
        DELTA_RPM(3, 70, false),
        RAW_MPH(4, 80, true, true), // Requires both RPM data AND native velocity
        CALC_MPH(5, 80, true, false), // Only requires RPM data
        MPH_DIFF_PERCENT(6, 70, true, true), // Requires both RPM data AND native velocity
        DELTA_MPH(7, 70, true, false), // Only requires RPM data
        ACCELERATION(8, 100, false),
        PEDAL(9, 60, false),
        THROTTLE(10, 70, false),
        GEAR(11, 50, false),
        FILTER_STATUS(12, 80, false),
        RANGE(13, 50, false),
        FILTER_REASONS(14, 400, false);

        private final int index;
        private final int width;
        private final boolean requiresRPMData;
        private final boolean requiresNativeVelocity;

        Column(int index, int width, boolean requiresRPMData) {
            this.index = index;
            this.width = width;
            this.requiresRPMData = requiresRPMData;
            this.requiresNativeVelocity = false;
        }

        Column(int index, int width, boolean requiresRPMData, boolean requiresNativeVelocity) {
            this.index = index;
            this.width = width;
            this.requiresRPMData = requiresRPMData;
            this.requiresNativeVelocity = requiresNativeVelocity;
        }

        public int getIndex() { return index; }
        public int getWidth() { return width; }
        public boolean requiresRPMData() { return requiresRPMData; }
        public boolean requiresNativeVelocity() { return requiresNativeVelocity; }
        public static int getColumnCount() { return values().length; }

        public static int idx(Column col) { return col.getIndex(); }
    }

    // Filter parameter definitions with metadata
    private enum FilterParameter {
        GEAR(0, "Gear"),
        MIN_RPM(1, "Min RPM"),
        MAX_RPM(2, "Max RPM"),
        MIN_RPM_RANGE(3, "Min RPM Range"),
        MONOTONIC_RPM_FUZZ(4, "RPM Fuzz Tolerance (RPM/s)"),
        MIN_PEDAL(5, "Min Pedal (%)"),
        MIN_THROTTLE(6, "Min Throttle (%)"),
        MIN_ACCEL(7, "Min Accel (RPM/s)"),
        MIN_POINTS(8, "Min Points"),
        ACCEL_MAW(9, "Accel Smoothing (s)");

        private final int index;
        private final String label;

        FilterParameter(int index, String label) {
            this.index = index;
            this.label = label;
        }

        public int getIndex() { return index; }
        public String getLabel() { return label; }

        public static FilterParameter fromIndex(int index) {
            for (FilterParameter param : values()) {
                if (param.index == index) {
                    return param;
                }
            }
            return null;
        }
    }

    // Tooltips for filter parameters (indexed by FilterParameter index)
    private static final String[] FILTER_PARAMETER_TOOLTIPS = {
        "Filter to specific gear, or 'Any' for all gears",  // GEAR (0)
        "Filter out points below this RPM",  // MIN_RPM (1)
        "Filter out points above this RPM",  // MAX_RPM (2)
        "Minimum RPM span required for a valid run",  // MIN_RPM_RANGE (3)
        "Max RPM drop rate (RPM/s). Detects severe swings/decel. Lower = stricter.",  // MONOTONIC_RPM_FUZZ (4)
        "Filter out points below this pedal %",  // MIN_PEDAL (5)
        "Filter out points below this throttle %. Lower values allow throttle cuts.",  // MIN_THROTTLE (6)
        "Filter out points with acceleration below this (RPM/s)",  // MIN_ACCEL (7)
        "Minimum points required for a valid run",  // MIN_POINTS (8)
        "Affects: Acceleration (RPM/s), Acceleration (m/s^2) - derivative smoothing and range-aware smoothing",  // ACCEL_MAW (9)
    };


    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible && okButton != null) {
            // Re-set default button when window becomes visible
            // This handles the case where window was disposed and reopened
            this.getRootPane().setDefaultButton(okButton);
        }
    }

    public FilterWindow(Filter filter, ECUxPlot eplot) {
        super("Filter", filter, eplot);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Set minimum size first to ensure loaded preferences are enforced
        setMinimumSize(new Dimension(800, 475));
        setSize(this.windowSize());
        setLocationRelativeTo(null);

        initializeComponents();
        setupLayout();
        updateDialog();

        // Add window listener to save size when window is closed
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                putWindowSize();
            }

            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                putWindowSize();
            }
        });
    }

    private void initializeComponents() {
        // Initialize filter parameter fields
        initializeFilterFields();

        // Initialize data visualization components
        initializeVisualizationComponents();
    }

    private void initializeFilterFields() {
        // Create filter parameter fields
        // Create gear spinner with "any" option (-1) and gears 1-8
        SpinnerListModel gearModel = new SpinnerListModel(new String[]{"Any", "1", "2", "3", "4", "5", "6", "7", "8"});
        gear = new JSpinner(gearModel);
        minRPM = new JTextField(10);
        maxRPM = new JTextField(10);
        minRPMRange = new JTextField(10);
        monotonicRPMfuzz = new JTextField(10);
        minPedal = new JTextField(10);
        minThrottle = new JTextField(10);
        minAcceleration = new JTextField(10);
        minPoints = new JTextField(10);
        accelMAW = new JTextField(10);

        // Add change listeners to automatically refresh visualization
        addChangeListeners();
    }

    private void addChangeListeners() {
        ActionListener refreshListener = e -> {
            processFilterChanges();
            refreshData();
        };

        ChangeListener spinnerListener = e -> {
            processFilterChanges();
            refreshData();
        };

        gear.addChangeListener(spinnerListener);
        minRPM.addActionListener(refreshListener);
        maxRPM.addActionListener(refreshListener);
        minRPMRange.addActionListener(refreshListener);
        monotonicRPMfuzz.addActionListener(refreshListener);
        minPedal.addActionListener(refreshListener);
        minThrottle.addActionListener(refreshListener);
        minAcceleration.addActionListener(refreshListener);
        accelMAW.addActionListener(refreshListener);
        minPoints.addActionListener(refreshListener);
    }

    private void initializeVisualizationComponents() {
        // Create table model with columns
        String[] columnNames = {
            "sample", "Time", "RPM", "Δ RPM", "MPH", "Calc MPH", "Calc Err %", "Δ MPH", "Accel (RPM/s)", "Pedal", "Throttle", "Gear",
            "Status", "Range", "Filter Reasons"
        };
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Read-only table
            }
        };

        dataTable = new JTable(tableModel);
        dataTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        dataTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // Enable copy functionality
        dataTable.getActionMap().put("copy", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copyTableData();
            }
        });

        // Set up keyboard shortcuts
        dataTable.getInputMap().put(KeyStroke.getKeyStroke("ctrl C"), "copy");
        dataTable.getInputMap().put(KeyStroke.getKeyStroke("meta C"), "copy");

        // Set custom renderer for color coding
        dataTable.setDefaultRenderer(Object.class, new RangeColorRenderer());

        // Set column widths
        TableColumnModel columnModel = dataTable.getColumnModel();
        for (Column col : Column.values()) {
            columnModel.getColumn(col.getIndex()).setPreferredWidth(col.getWidth());
        }

        // Create control panel
        statusLabel = new JLabel("No dataset loaded");
        showRPMDataCheckBox = new JCheckBox("Show RPM Detail", false);
        maxRowsSpinner = new JSpinner(new SpinnerNumberModel(1000, 10, 10000, 100));
        fileSelector = new JComboBox<String>();
        // Use custom renderer to elide long filenames to control dropdown width
        fileSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value != null) {
                    String filename = value.toString();
                    // Elide long filenames to prevent combo box from being too wide
                    // Use a reasonable max length based on what's visible
                    int maxLength = 60;
                    String displayText = Strings.elide(filename, maxLength);
                    setText(displayText);
                    // Always show full filename in tooltip
                    setToolTipText(filename);
                }
                return this;
            }
        });
        showOnlyValidDataCheckBox = new JCheckBox("Show Only Valid Data", true);
        fileLabel = new JLabel("File:");

        // Add action listeners
        showRPMDataCheckBox.addActionListener(e -> refreshData());
        maxRowsSpinner.addChangeListener(e -> refreshData());
        fileSelector.addActionListener(e -> onFileSelectionChanged());
        showOnlyValidDataCheckBox.addActionListener(e -> refreshData());
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        // Create main horizontal panel for filter and visualization
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Left panel: Filter Parameters
        JPanel filterPanel = createFilterEditorPanel();
        mainPanel.add(filterPanel, BorderLayout.WEST);

        // Right panel: Data Visualization (full remaining width)
        JPanel visualizationPanel = createVisualizationPanel();
        mainPanel.add(visualizationPanel, BorderLayout.CENTER);

        // Add main panel to window (allows vertical growth)
        add(mainPanel, BorderLayout.CENTER);
    }

    private JPanel createFilterEditorPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Add padding to match visualization panel
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 0)); // Top, left, bottom, right padding

        // Create main form panel with BoxLayout for vertical stacking
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        // Add filter parameter fields in separate panels
        JTextField[] fields = {minRPM, maxRPM, minRPMRange, monotonicRPMfuzz,
                              minPedal, minThrottle, minAcceleration, minPoints,
                              accelMAW};

        // Engine Panel
        JPanel enginePanel = createParameterPanel("Engine",
            new int[]{FilterParameter.GEAR.getIndex(), FilterParameter.MIN_RPM.getIndex(), FilterParameter.MAX_RPM.getIndex(),
                     FilterParameter.MIN_RPM_RANGE.getIndex(), FilterParameter.MONOTONIC_RPM_FUZZ.getIndex(), FilterParameter.MIN_ACCEL.getIndex()}, fields);
        formPanel.add(enginePanel);

        // Throttle/Pedal Panel
        JPanel throttlePanel = createParameterPanel("Throttle/Pedal",
            new int[]{FilterParameter.MIN_PEDAL.getIndex(), FilterParameter.MIN_THROTTLE.getIndex()}, fields);
        formPanel.add(throttlePanel);

        // Data Quality Panel (including smoothing parameters)
        JPanel qualityPanel = createParameterPanel("Data Quality",
            new int[]{FilterParameter.MIN_POINTS.getIndex(), FilterParameter.ACCEL_MAW.getIndex()}, fields);
        formPanel.add(qualityPanel);

        // Add scroll pane to handle overflow
        JScrollPane scrollPane = new JScrollPane(formPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);

        panel.add(scrollPane, BorderLayout.NORTH);

        // Add buttons at the bottom of the filter panel (two-row layout)
        JPanel buttonPanel = createButtonPanel();
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel mainPanel = new JPanel(new java.awt.BorderLayout());

        JButton applyButton = new JButton("Apply");
        this.okButton = new JButton("OK");
        JButton restoreDefaultsButton = new JButton("Restore Defaults");
        JButton rangesButton = new JButton("Ranges...");
        JButton cancelButton = new JButton("Cancel");

        applyButton.addActionListener(e -> {
            // Keep window on top during the entire process
            setTopWindow();

            try {
                // Apply filter changes (WaitCursor will be handled by rebuild())
                applyFilterChanges();
            } catch (Exception ex) {
                logger.error("Exception in Apply button: ", ex);
                // Clear top status on error
                clearTopWindow();
            }
        });

        this.okButton.addActionListener(e -> {
            // Keep window on top during the entire process
            setTopWindow();

            try {
                // Apply filter changes (WaitCursor will be handled by rebuild())
                applyFilterChanges();
                // Clear top status before disposing
                clearTopWindow();
                dispose(); // Close window after applying
            } catch (Exception ex) {
                logger.error("Exception in OK button: ", ex);
                // Clear top status on error
                clearTopWindow();
            }
        });

        restoreDefaultsButton.addActionListener(e -> {
            // Keep window on top during the entire process
            setTopWindow();

            try {
                // Restore defaults and apply changes - rebuild() will handle WaitCursor on main window
                restoreDefaultsAndApply();
            } catch (Exception ex) {
                logger.error("Exception in Restore Defaults button: ", ex);
                // Clear top status on error
                clearTopWindow();
            }
        });

        rangesButton.addActionListener(e -> {
            // Open Range Selector window
            withEplot(plot -> plot.openRangeSelectorWindow());
        });

        cancelButton.addActionListener(e -> {
            // Cancel closes the window without applying changes
            dispose();
        });

        // First row: Restore Defaults and Ranges buttons (pack West)
        JPanel firstRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        firstRow.add(restoreDefaultsButton);
        firstRow.add(rangesButton);

        // Second row: OK, Apply, Cancel buttons (pack West)
        JPanel secondRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        secondRow.add(this.okButton);
        secondRow.add(applyButton);
        secondRow.add(cancelButton);

        mainPanel.add(firstRow, java.awt.BorderLayout.NORTH);
        mainPanel.add(secondRow, java.awt.BorderLayout.SOUTH);

        // Set OK button as default (most common action)
        this.getRootPane().setDefaultButton(this.okButton);

        return mainPanel;
    }

    private JPanel createParameterPanel(String title, int[] fieldIndices, JTextField[] fields) {
        JPanel panel = new JPanel(new BorderLayout());
        // Create bold title border
        javax.swing.border.TitledBorder titledBorder = BorderFactory.createTitledBorder(null, title,
            javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP);
        titledBorder.setTitleFont(titledBorder.getTitleFont().deriveFont(Font.BOLD));
        panel.setBorder(titledBorder);

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);

        for (int i = 0; i < fieldIndices.length; i++) {
            int fieldIndex = fieldIndices[i];

            gbc.gridx = 0; gbc.gridy = i;
            gbc.weightx = 1.0; // Allow labels column to expand
            gbc.fill = GridBagConstraints.NONE; // Don't fill horizontally
            gbc.anchor = GridBagConstraints.EAST; // Right-align labels
            FilterParameter param = FilterParameter.fromIndex(fieldIndex);
            if (param == null) {
                continue; // Skip invalid indices
            }
            JLabel label = new JLabel(param.getLabel() + ":");
            formPanel.add(label, gbc);

            gbc.gridx = 1; gbc.gridy = i;
            gbc.weightx = 0.0; // Don't expand fields column
            gbc.fill = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.WEST; // Left-align fields

            // Handle gear field specially (it's a JSpinner, not JTextField)
            // Widths tuned to windows rendering
            if (param == FilterParameter.GEAR) {
                gear.setPreferredSize(new Dimension(60, gear.getPreferredSize().height));
                String tooltip = getTooltipForField(param);
                if (tooltip != null) {
                    gear.setToolTipText(tooltip);
                }
                formPanel.add(gear, gbc);
            } else {
                // Map pairs index to fields array index
                // pairs: [0:gear(not in fields), 1:minRPM(0), 2:maxRPM(1), ... 8:accelMAW(7), 9:minPoints(8)]
                int fieldsIndex = fieldIndex - 1; // Subtract 1 because gear is not in fields array
                fields[fieldsIndex].setColumns(5);

                // Set tooltips based on field type
                String tooltip = getTooltipForField(param);
                if (tooltip != null) {
                    fields[fieldsIndex].setToolTipText(tooltip);
                }

                formPanel.add(fields[fieldsIndex], gbc);
            }
        }

        panel.add(formPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Get tooltip text for a filter parameter field
     * @param param The FilterParameter enum value
     * @return Tooltip text, or null if no tooltip available
     */
    private String getTooltipForField(FilterParameter param) {
        if (param == null) {
            return null;
        }
        int index = param.getIndex();
        if (index >= 0 && index < FILTER_PARAMETER_TOOLTIPS.length) {
            return FILTER_PARAMETER_TOOLTIPS[index];
        }
        return null;
    }

    private JPanel createVisualizationPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        // Create bold title border
        javax.swing.border.TitledBorder titledBorder = BorderFactory.createTitledBorder(null, "Data Visualization",
            javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP);
        titledBorder.setTitleFont(titledBorder.getTitleFont().deriveFont(Font.BOLD));
        panel.setBorder(titledBorder);

        // Add padding to match filter panel
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(5, 0, 5, 5), // Top, left, bottom, right padding
            titledBorder));

        // Control panel at top
        JPanel controlPanel = new JPanel(new BorderLayout());

        // Top row with main controls - use BorderLayout for better control
        JPanel topRow = new JPanel(new BorderLayout());

        // Left side: file label and selector
        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filePanel.add(fileLabel);
        filePanel.add(fileSelector);

        topRow.add(filePanel, BorderLayout.WEST);
        topRow.add(statusLabel, BorderLayout.EAST);

        // Bottom row with checkboxes and spinner
        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomRow.add(showOnlyValidDataCheckBox);
        bottomRow.add(showRPMDataCheckBox);
        bottomRow.add(new JLabel("Max Rows:"));
        bottomRow.add(maxRowsSpinner);

        controlPanel.add(topRow, BorderLayout.NORTH);
        controlPanel.add(bottomRow, BorderLayout.SOUTH);

        // Main content area
        JPanel contentPanel = new JPanel(new BorderLayout());

        // Data table with scroll pane
        JScrollPane tableScrollPane = new JScrollPane(dataTable);

        contentPanel.add(tableScrollPane, BorderLayout.CENTER);

        panel.add(controlPanel, BorderLayout.NORTH);
        panel.add(contentPanel, BorderLayout.CENTER);

        return panel;
    }

    private void processFilterChanges() {
        // Process filter parameter changes
        processPairs(this.filter);

        // Enable the filter when settings are applied
        this.filter.enabled(true);

        // Update the filter checkbox in OptionsMenu to reflect that filter is enabled
        if (this.eplot != null && this.eplot.optionsMenu != null) {
            this.eplot.optionsMenu.updateFilterCheckBox();
        }
    }

    private void setFilterIntValueFromTextField(JTextField textField, java.util.function.IntConsumer setter) {
        try {
            setter.accept(Integer.parseInt(textField.getText()));
        } catch (NumberFormatException e) {
            // Handle invalid input gracefully - keep current value
        }
    }

    private void setFilterDoubleValueFromTextField(JTextField textField, java.util.function.DoubleConsumer setter) {
        try {
            String text = textField.getText();
            double value = Double.parseDouble(text);
            setter.accept(value);
        } catch (NumberFormatException e) {
            // Handle invalid input gracefully - keep current value
        }
    }

    private void setFilterValueFromSpinner(JSpinner spinner, java.util.function.IntConsumer setter) {
        String selectedValue = spinner.getValue().toString();
        int value;
        if ("Any".equals(selectedValue)) {
            value = -1;
        } else {
            value = Integer.parseInt(selectedValue);
        }
        setter.accept(value);
    }

    private void processPairs(Filter filter) {
        setFilterValueFromSpinner(gear, filter::gear);
        setFilterIntValueFromTextField(minRPM, filter::minRPM);
        setFilterIntValueFromTextField(maxRPM, filter::maxRPM);
        setFilterIntValueFromTextField(minRPMRange, filter::minRPMRange);
        setFilterIntValueFromTextField(minPedal, filter::minPedal);
        setFilterIntValueFromTextField(minThrottle, filter::minThrottle);
        setFilterIntValueFromTextField(minAcceleration, filter::minAcceleration);
        setFilterIntValueFromTextField(minPoints, filter::minPoints);

        // Double fields
        setFilterDoubleValueFromTextField(monotonicRPMfuzz, filter::monotonicRPMfuzz);
        setFilterDoubleValueFromTextField(accelMAW, filter::accelMAW);
    }

    private void updateDialog() {
        updateDialog(this.filter);
    }

    private void updateDialog(Filter filter) {
        // Set gear spinner value
        int gearValue = filter.gear();
        if (gearValue == -1) {
            gear.setValue("Any");
        } else {
            gear.setValue(String.valueOf(gearValue));
        }
        minRPM.setText(String.valueOf(filter.minRPM()));
        maxRPM.setText(String.valueOf(filter.maxRPM()));
        minRPMRange.setText(String.valueOf(filter.minRPMRange()));
        monotonicRPMfuzz.setText(String.format("%.0f", filter.monotonicRPMfuzz()));
        minPedal.setText(String.valueOf(filter.minPedal()));
        minThrottle.setText(String.valueOf(filter.minThrottle()));
        minAcceleration.setText(String.valueOf(filter.minAcceleration()));
        accelMAW.setText(String.valueOf(filter.accelMAW()));
        minPoints.setText(String.valueOf(filter.minPoints()));
    }

    public void setDataset(ECUxDataset dataset) {
        this.dataset = dataset;
        refreshData();
    }

    public void setFileDatasets(TreeMap<String, ECUxDataset> fileDatasets) {
        this.fileDatasets = fileDatasets;
        updateFileSelector();

        // Set the first dataset as current if available
        if (fileDatasets != null && !fileDatasets.isEmpty()) {
            String firstFile = fileDatasets.firstKey();
            this.dataset = fileDatasets.get(firstFile);
        }
    }

    private void updateFileSelector() {
        fileSelector.removeAllItems();
        if (fileDatasets != null) {
            for (String filename : fileDatasets.keySet()) {
                fileSelector.addItem(filename);
            }
            if (!fileDatasets.isEmpty()) {
                fileSelector.setSelectedIndex(0);
                onFileSelectionChanged();
            }
        }
    }

    private void onFileSelectionChanged() {
        String selectedFile = (String) fileSelector.getSelectedItem();
        if (selectedFile != null && fileDatasets != null) {
            dataset = fileDatasets.get(selectedFile);
            refreshData();
        }
    }

    private void refreshData() {
        if (dataset == null) {
            statusLabel.setText("No dataset loaded");
            tableModel.setRowCount(0);
            return;
        }

        statusLabel.setText("Loading data...");

        // Refresh the data visualization
        refreshVisualization();
    }

    public void refreshVisualization() {
        if (dataset == null) return;

        try {
            // Clear existing data
            tableModel.setRowCount(0);
            rowIndexMapping = new ArrayList<>();

            int maxRows = (Integer) maxRowsSpinner.getValue();
            boolean showRPMData = showRPMDataCheckBox.isSelected();
            boolean showOnlyValid = showOnlyValidDataCheckBox.isSelected();

            // Get data columns - use base RPM and filter acceleration to match what dataValid() checks
            Dataset.Column timeCol = dataset.get("TIME");
            // Use base RPM to match what dataValid() uses for filtering
            Dataset.Column rpmCol = null;
            if (dataset instanceof ECUxDataset) {
                rpmCol = ((ECUxDataset)dataset).getBaseRpmColumn();
            }
            if (rpmCol == null) {
                // Fallback to final RPM if base not available
                rpmCol = dataset.get("RPM");
            }
            // Get both native velocity (if available) and calculated velocity
            // Native velocity may not be available in all logs
            Dataset.Column nativeMphCol = null;
            if (dataset instanceof ECUxDataset) {
                nativeMphCol = ((ECUxDataset)dataset).getColumnInUnits("VehicleSpeed", UnitConstants.UNIT_MPH);
            }
            Dataset.Column calcMphCol = dataset.get("Calc Velocity");
            // Don't use Acceleration (RPM/s) column - we'll calculate filter acceleration inline
            // to match what dataValid() uses
            Dataset.Column pedalCol = dataset.get(DataLogger.pedalField());
            Dataset.Column throttleCol = dataset.get(DataLogger.throttleField());
            Dataset.Column gearCol = dataset.get(DataLogger.gearField());

            if (timeCol == null || timeCol.data.size() == 0) {
                return;
            }

            int totalRows = timeCol.data.size();
            ArrayList<Dataset.Range> ranges = dataset.getRanges();
            int validCount = ranges.stream().mapToInt(r -> r.size()).sum();

            // Determine which rows to show
            ArrayList<Integer> rowsToShow = new ArrayList<>();
            if (showOnlyValid) {
                // Show only valid data points from ranges
                int added = 0;
                for (Dataset.Range range : ranges) {
                    for (int i = range.start; i <= range.end && added < maxRows; i++) {
                        rowsToShow.add(i);
                        added++;
                    }
                    if (added >= maxRows) break;
                }
            } else {
                // Show all data points up to maxRows
                for (int i = 0; i < Math.min(totalRows, maxRows); i++) {
                        rowsToShow.add(i);
                }
            }

            // Update column visibility based on RPM data setting and data availability
            boolean hasNativeVelocity = nativeMphCol != null;
            boolean hasCalcVelocity = calcMphCol != null;
            updateColumnVisibility(showRPMData, hasNativeVelocity, hasCalcVelocity, pedalCol, throttleCol, gearCol);

            // Populate table
            boolean hasVelocityData = (nativeMphCol != null || calcMphCol != null) && showRPMData;
            for (int rowIndex : rowsToShow) {
                rowIndexMapping.add(rowIndex);
                Object[] rowData = createRowData(rowIndex, timeCol, rpmCol, nativeMphCol, calcMphCol, dataset, pedalCol, throttleCol, gearCol, ranges, hasVelocityData, hasNativeVelocity);
                tableModel.addRow(rowData);
            }

            if (showOnlyValid) {
                statusLabel.setText(String.format("Showing %d/%d rows", validCount, totalRows));
            } else {
                statusLabel.setText(String.format("Showing %d/%d rows", rowsToShow.size(), totalRows));
            }

            // Force table update and repaint to ensure UI reflects changes
            tableModel.fireTableDataChanged();
            if (dataTable != null) {
                dataTable.revalidate();
                dataTable.repaint();
            }

        } catch (Exception e) {
            statusLabel.setText("Error loading data: " + e.getMessage());
            logger.error("Error refreshing visualization data", e);
        }
    }

    private void setColumnVisibility(Column column, boolean visible) {
        int columnIndex = column.getIndex();
        dataTable.getColumnModel().getColumn(columnIndex).setMinWidth(0);
        dataTable.getColumnModel().getColumn(columnIndex).setMaxWidth(visible ? Integer.MAX_VALUE : 0);
        dataTable.getColumnModel().getColumn(columnIndex).setPreferredWidth(visible ? column.getWidth() : 0);
    }

    private void updateColumnVisibility(boolean showRPMData, boolean hasNativeVelocity, boolean hasCalcVelocity, Dataset.Column pedalCol, Dataset.Column throttleCol, Dataset.Column gearCol) {
        // Hide/show columns based on RPM data setting and data availability
        for (Column col : Column.values()) {
            if (col.requiresRPMData()) {
                // Check if this column also requires native velocity
                boolean shouldShow = showRPMData;
                if (col.requiresNativeVelocity()) {
                    // Columns like RAW_MPH and MPH_DIFF_PERCENT require native velocity
                    shouldShow = showRPMData && hasNativeVelocity;
                }
                // Columns that don't require native velocity (like CALC_MPH, DELTA_MPH) show when showRPMData is true
                // They don't need to check hasCalcVelocity because the column will be calculated when needed
                setColumnVisibility(col, shouldShow);
            }
        }

        // Hide/show pedal, throttle, gear columns based on data availability
        boolean hasPedalData = pedalCol != null && pedalCol.data.size() > 0;
        boolean hasThrottleData = throttleCol != null && throttleCol.data.size() > 0;
        boolean hasGearData = gearCol != null && gearCol.data.size() > 0;

        setColumnVisibility(Column.PEDAL, hasPedalData);
        setColumnVisibility(Column.THROTTLE, hasThrottleData);
        setColumnVisibility(Column.GEAR, hasGearData);
    }

    private Object[] createRowData(int rowIndex, Dataset.Column timeCol, Dataset.Column rpmCol,
                                 Dataset.Column nativeMphCol, Dataset.Column calcMphCol, Dataset dataset,
                                 Dataset.Column pedalCol, Dataset.Column throttleCol, Dataset.Column gearCol,
                                 ArrayList<Dataset.Range> ranges, boolean showRPMData, boolean hasNativeVelocity) {
        Object[] row = new Object[Column.getColumnCount()];

        try {
            // Sample (absolute index)
            row[Column.idx(Column.SAMPLE)] = String.valueOf(rowIndex);

            // Time
            row[Column.idx(Column.TIME)] = timeCol != null && rowIndex < timeCol.data.size() ?
                String.format("%.2f", timeCol.data.get(rowIndex)) : "N/A";

            // RPM and Delta RPM (always show)
            row[Column.idx(Column.RPM)] = rpmCol != null && rowIndex < rpmCol.data.size() ?
                String.format("%.0f", rpmCol.data.get(rowIndex)) : "N/A";

            // Delta RPM
            double deltaRPM = 0;
            if (rpmCol != null && rowIndex > 0 && rowIndex < rpmCol.data.size()) {
                deltaRPM = rpmCol.data.get(rowIndex) - rpmCol.data.get(rowIndex - 1);
            }
            row[Column.idx(Column.DELTA_RPM)] = String.format("%.1f", deltaRPM);

            // Acceleration (always show) - use the same acceleration calculation that dataValid() uses
            double acceleration = 0;
            if (dataset instanceof ECUxDataset) {
                acceleration = ((ECUxDataset)dataset).getFilterAcceleration(rowIndex);
            }
            row[Column.idx(Column.ACCELERATION)] = String.format("%.1f", acceleration);

            // MPH-related columns (only populate if showRPMData is true)
            if (showRPMData) {
                // Raw MPH - show native velocity if available
                if (hasNativeVelocity && nativeMphCol != null && rowIndex < nativeMphCol.data.size()) {
                    row[Column.idx(Column.RAW_MPH)] = String.format("%.1f", nativeMphCol.data.get(rowIndex));
                } else {
                    row[Column.idx(Column.RAW_MPH)] = "N/A";
                }

                // Calc MPH - show calculated velocity (convert from m/s to MPH)
                if (calcMphCol != null && rowIndex < calcMphCol.data.size()) {
                    // Calc Velocity is in m/s, convert to MPH for display
                    double velocityMph = calcMphCol.data.get(rowIndex) / UnitConstants.MPS_PER_MPH;
                    row[Column.idx(Column.CALC_MPH)] = String.format("%.1f", velocityMph);
                } else {
                    row[Column.idx(Column.CALC_MPH)] = "N/A";
                }

                // MPH Diff Percent - compare native vs calculated
                if (hasNativeVelocity && nativeMphCol != null && calcMphCol != null &&
                    rowIndex < nativeMphCol.data.size() && rowIndex < calcMphCol.data.size()) {
                    double nativeSpeed = nativeMphCol.data.get(rowIndex);
                    // Calc Velocity is in m/s, convert to MPH
                    double calcSpeed = calcMphCol.data.get(rowIndex) / UnitConstants.MPS_PER_MPH;
                    double diff = 0;
                    if (nativeSpeed != 0) {
                        diff = ((calcSpeed - nativeSpeed) / nativeSpeed) * 100;
                    }
                    row[Column.idx(Column.MPH_DIFF_PERCENT)] = String.format("%.1f%%", diff);
                } else {
                    row[Column.idx(Column.MPH_DIFF_PERCENT)] = "N/A";
                }

                // Delta MPH - use calculated velocity (convert from m/s to MPH)
                double deltaMPH = 0;
                if (calcMphCol != null && rowIndex > 0 && rowIndex < calcMphCol.data.size()) {
                    double currentVel = calcMphCol.data.get(rowIndex) / UnitConstants.MPS_PER_MPH; // Convert m/s to MPH
                    double prevVel = calcMphCol.data.get(rowIndex - 1) / UnitConstants.MPS_PER_MPH;
                    deltaMPH = currentVel - prevVel;
                }
                row[Column.idx(Column.DELTA_MPH)] = String.format("%.1f", deltaMPH);
            } else {
                // Clear MPH-related columns when not showing RPM data
                row[Column.idx(Column.RAW_MPH)] = "";
                row[Column.idx(Column.CALC_MPH)] = "";
                row[Column.idx(Column.MPH_DIFF_PERCENT)] = "";
                row[Column.idx(Column.DELTA_MPH)] = "";
            }

            // Pedal, Throttle, and Gear (show if data is available)
            row[Column.idx(Column.PEDAL)] = pedalCol != null && rowIndex < pedalCol.data.size() ?
                String.format("%.1f", pedalCol.data.get(rowIndex)) : "";

            row[Column.idx(Column.THROTTLE)] = throttleCol != null && rowIndex < throttleCol.data.size() ?
                String.format("%.1f", throttleCol.data.get(rowIndex)) : "";

            row[Column.idx(Column.GEAR)] = gearCol != null && rowIndex < gearCol.data.size() ?
                String.format("%.0f", gearCol.data.get(rowIndex)) : "";

            // Filter status and reasons
            // Check if individual point passes filter (not just if it's in a range)
            boolean pointValid = false;
            ArrayList<String> filterReasons = new ArrayList<String>();
            if (dataset instanceof ECUxDataset) {
                filterReasons = ((ECUxDataset) dataset).getFilterReasonsForRow(rowIndex);
                pointValid = filterReasons.isEmpty();
            } else {
                pointValid = isRowInRange(rowIndex, ranges);
            }

            boolean isInRange = isRowInRange(rowIndex, ranges);
            boolean isValid = pointValid && isInRange;
            row[Column.idx(Column.FILTER_STATUS)] = isValid ? "PASS" : "FAIL";

            // Range number only
            String rangeValue = "";
            if (isInRange) {
                int rangeNumber = getRangeNumber(rowIndex, ranges);
                rangeValue = String.valueOf(rangeNumber);
            }
            row[Column.idx(Column.RANGE)] = rangeValue;

            // Show filter reasons - prioritize actual filter failure reasons over range status
            if (!filterReasons.isEmpty()) {
                // Point failed dataValid() checks - show the actual reasons
                row[Column.idx(Column.FILTER_REASONS)] = String.join(", ", filterReasons);
            } else if (!isInRange) {
                // Point passed dataValid() but is not in any valid range
                // Try to get the actual range failure reason
                ArrayList<String> rangeFailureReasons = new ArrayList<String>();
                if (dataset instanceof ECUxDataset) {
                    rangeFailureReasons = ((ECUxDataset) dataset).getRangeFailureReasons(rowIndex);
                }
                if (!rangeFailureReasons.isEmpty()) {
                    // Show why the range failed (e.g., "pts 10 < 15", "rpm 3500 < 3000+2000")
                    row[Column.idx(Column.FILTER_REASONS)] = String.join(", ", rangeFailureReasons);
                } else {
                    // No specific reason available, show generic message
                    row[Column.idx(Column.FILTER_REASONS)] = "Not in valid range";
                }
            } else {
                row[Column.idx(Column.FILTER_REASONS)] = "";
            }

        } catch (Exception e) {
            // Fill with N/A if data is not available
            for (int i = 0; i < row.length; i++) {
                row[i] = "N/A";
            }
        }

        return row;
    }

    private boolean isRowInRange(int rowIndex, ArrayList<Dataset.Range> ranges) {
        for (Dataset.Range range : ranges) {
            if (rowIndex >= range.start && rowIndex <= range.end) {
                return true;
            }
        }
        return false;
    }

    private int getRangeNumber(int rowIndex, ArrayList<Dataset.Range> ranges) {
        for (int i = 0; i < ranges.size(); i++) {
            Dataset.Range range = ranges.get(i);
            if (rowIndex >= range.start && rowIndex <= range.end) {
                return i + 1; // Range numbers start from 1
            }
        }
        return 0; // Not in any range
    }

    private void copyTableData() {
        int[] selectedRows = dataTable.getSelectedRows();
        if (selectedRows.length == 0) {
            return;
        }

        StringBuilder sb = new StringBuilder();

        // Add headers
        for (int col = 0; col < tableModel.getColumnCount(); col++) {
            if (col > 0) sb.append("\t");
            sb.append(tableModel.getColumnName(col));
        }
        sb.append("\n");

        // Add selected rows
        for (int row : selectedRows) {
            for (int col = 0; col < tableModel.getColumnCount(); col++) {
                if (col > 0) sb.append("\t");
                Object value = tableModel.getValueAt(row, col);
                sb.append(value != null ? value.toString() : "");
            }
            sb.append("\n");
        }

        // Copy to clipboard
        StringSelection selection = new StringSelection(sb.toString());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, null);
    }

    // Custom renderer for color coding based on filter status
    private class RangeColorRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                // Color code based on filter status
                Object statusValue = tableModel.getValueAt(row, Column.idx(Column.FILTER_STATUS));
                if (statusValue != null) {
                    String status = statusValue.toString();
                    if ("PASS".equals(status)) {
                        c.setBackground(new Color(200, 255, 200)); // Light green
                    } else if ("FAIL".equals(status)) {
                        c.setBackground(new Color(255, 200, 200)); // Light red
                    } else {
                        c.setBackground(Color.WHITE);
                    }
                } else {
                    c.setBackground(Color.WHITE);
                }
            }

            return c;
        }
    }

    /**
     * Apply filter changes and rebuild the plot
     * @throws Exception if any error occurs during the process
     */
    private void applyFilterChanges() throws Exception {
        // Actually read the values from the text fields and apply them
        processFilterChanges();

        // Window is already set as top by the button action listener

        try {
            if (this.eplot != null) {
                this.eplot.rebuild(() -> {
                    // Refresh the FilterWindow table AFTER rebuild completes
                    refreshData();
                    // Rebuild FATS since filter changed
                    if (this.eplot != null) {
                        this.eplot.rebuildFATS();
                        // Refresh Range Selector if open (ranges may have changed)
                        this.eplot.refreshRangeSelector();
                    }
                }, this);
            }
        } finally {
            // Clear top status after rebuild completes
            clearTopWindow();
        }
    }

    /**
     * Restore defaults and apply changes
     * @throws Exception if any error occurs during the process
     */
    private void restoreDefaultsAndApply() throws Exception {
        // Reset filter to default values, but preserve HPMAW and ZeitMAW
        // (those are managed by SmoothingWindow)
        double savedHPMAW = this.filter.HPMAW();
        double savedZeitMAW = this.filter.ZeitMAW();

        // Reset all defaults
        this.filter.resetToDefaults();

        // Restore HPMAW and ZeitMAW (don't reset these)
        this.filter.HPMAW(savedHPMAW);
        this.filter.ZeitMAW(savedZeitMAW);

        // Update the dialog to show default values
        updateDialog();

        // Process the filter changes (same as Apply button)
        processFilterChanges();

        if (this.eplot != null) {
            this.eplot.rebuild(() -> {
                // Refresh the FilterWindow table AFTER the main plot rebuild
                refreshData();
                // Rebuild FATS since filter changed
                if (this.eplot != null) {
                    this.eplot.rebuildFATS();
                    // Refresh Range Selector if open (ranges may have changed)
                    this.eplot.refreshRangeSelector();
                }
                // Clear top status after rebuild completes
                clearTopWindow();
            }, this);
        }
    }

    private java.awt.Dimension windowSize() {
        int width = ECUxPlot.getPreferences().getInt("FilterWindowWidth", 1000);
        int height = ECUxPlot.getPreferences().getInt("FilterWindowHeight", 550);
        return new java.awt.Dimension(width, height);
    }

    private void putWindowSize() {
        int originalWidth = this.getWidth();
        int originalHeight = this.getHeight();

        // Validate window size before saving to prevent unreasonable sizes
        int width = Math.max(originalWidth, 800);  // Minimum width
        int height = Math.max(originalHeight, 475); // Minimum height

        // Also set reasonable maximums to prevent extremely large windows
        width = Math.min(width, 2000);
        height = Math.min(height, 1500);

        ECUxPlot.getPreferences().putInt("FilterWindowWidth", width);
        ECUxPlot.getPreferences().putInt("FilterWindowHeight", height);
    }
}

// vim: set sw=4 ts=8 expandtab:
