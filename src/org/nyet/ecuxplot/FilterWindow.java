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
import org.nyet.util.WaitCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filter Window - Integrates filter parameter editing with real-time data visualization
 * Provides a unified interface for configuring filters and immediately seeing their impact on data
 */
public class FilterWindow extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(FilterWindow.class);
    private static final long serialVersionUID = 1L;

    // Filter editing components (from FilterEditor)
    private final Filter filter;
    private ECUxPlot eplot;
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
    private JTextField HPTQMAW;
    private JTextField ZeitMAW;

    // Data visualization components
    private ECUxDataset dataset;
    private TreeMap<String, ECUxDataset> fileDatasets;
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
        TIME(0, 80, false),
        RPM(1, 60, false),
        DELTA_RPM(2, 70, false),
        RAW_MPH(3, 80, true),
        CALC_MPH(4, 80, true),
        MPH_DIFF_PERCENT(5, 70, true),
        DELTA_MPH(6, 70, true),
        ACCELERATION(7, 100, false),
        PEDAL(8, 60, false),
        THROTTLE(9, 70, false),
        GEAR(10, 50, false),
        FILTER_STATUS(11, 80, false),
        RANGE(12, 50, false),
        FILTER_REASONS(13, 250, false);

        private final int index;
        private final int width;
        private final boolean requiresRPMData;

        Column(int index, int width, boolean requiresRPMData) {
            this.index = index;
            this.width = width;
            this.requiresRPMData = requiresRPMData;
        }

        public int getIndex() { return index; }
        public int getWidth() { return width; }
        public boolean requiresRPMData() { return requiresRPMData; }
        public static int getColumnCount() { return values().length; }

        public static int idx(Column col) { return col.getIndex(); }
    }


    private static final String [][] pairs = {
        {"Gear (-1 to ignore)", "gear"},
        {"Min RPM", "minRPM"},
        {"Max RPM", "maxRPM"},
        {"Min RPM Range", "minRPMRange"},
        {"RPM Fuzz Tolerance", "monotonicRPMfuzz"},
        {"Min Pedal (%)", "minPedal"},
        {"Min Throttle (%)", "minThrottle"},
        {"Min Acceleration (RPM/s)", "minAcceleration"},
        {"Min Points", "minPoints"},
        {"Acceleration Smoothing", "accelMAW"},
        {"HP/TQ Smoothing", "HPTQMAW"},
        {"Zeitronix Smoothing", "ZeitMAW"},
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
        super("Filter Window");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(this.windowSize());
        setLocationRelativeTo(null);

        this.filter = filter;
        this.eplot = eplot;

        initializeComponents();
        setupLayout();
        updateDialog();

        // Add window listener to save size when window is closed
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
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
        accelMAW = new JTextField(10);
        minPoints = new JTextField(10);
        HPTQMAW = new JTextField(10);
        ZeitMAW = new JTextField(10);

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
        HPTQMAW.addActionListener(refreshListener);
        ZeitMAW.addActionListener(refreshListener);
    }

    private void initializeVisualizationComponents() {
        // Create table model with columns
        String[] columnNames = {
            "Time", "RPM", "Δ RPM", "Raw MPH", "Calc MPH", "Diff %", "Δ MPH", "Accel (RPM/s)", "Pedal", "Throttle", "Gear",
            "Filter Status", "Range", "Filter Reasons"
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
        maxRowsSpinner = new JSpinner(new SpinnerNumberModel(500, 10, 1000, 10));
        fileSelector = new JComboBox<String>();
        fileSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value != null) {
                    String filename = value.toString();
                    if (filename.length() > 30) {
                        setText(filename.substring(0, 15) + "..." + filename.substring(filename.length() - 12));
                    } else {
                        setText(filename);
                    }
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

        // Create main form panel with GridBagLayout for better size control
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new java.awt.Insets(5, 0, 5, 0); // Top, left, bottom, right padding

        // Add filter parameter fields in separate panels
        JTextField[] fields = {minRPM, maxRPM, minRPMRange, monotonicRPMfuzz,
                              minPedal, minThrottle, minAcceleration, accelMAW,
                              minPoints, HPTQMAW, ZeitMAW};

        // Engine Panel
        JPanel enginePanel = createParameterPanel("Engine",
            new int[]{0, 1, 2, 3, 4}, fields);
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 1.0; gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;
        formPanel.add(enginePanel, gbc);

        // Throttle/Pedal Panel
        JPanel throttlePanel = createParameterPanel("Throttle/Pedal",
            new int[]{5, 6}, fields);
        gbc.gridy = 1;
        formPanel.add(throttlePanel, gbc);

        // Acceleration Panel
        JPanel accelPanel = createParameterPanel("Acceleration",
            new int[]{7, 8}, fields);
        gbc.gridy = 2;
        formPanel.add(accelPanel, gbc);

        // Data Quality Panel
        JPanel qualityPanel = createParameterPanel("Data Quality",
            new int[]{9, 10, 11}, fields);
        gbc.gridy = 3;
        formPanel.add(qualityPanel, gbc);

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
        JPanel buttonPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new java.awt.Insets(5, 0, 5, 5); // Top, left, bottom, right padding

        JButton applyButton = new JButton("Apply");
        this.okButton = new JButton("OK");
        JButton restoreDefaultsButton = new JButton("Restore Defaults");
        JButton cancelButton = new JButton("Cancel");

        applyButton.addActionListener(e -> {
            try {
                // Apply filter changes (WaitCursor will be handled by rebuild())
                applyFilterChanges();
            } catch (Exception ex) {
                logger.error("Exception in Apply button: ", ex);
            }
        });

        this.okButton.addActionListener(e -> {
            // Keep window on top during the entire process
            this.setAlwaysOnTop(true);

            try {
                // Apply filter changes (WaitCursor will be handled by rebuild())
                applyFilterChanges();
                dispose(); // Close window after applying
            } catch (Exception ex) {
                logger.error("Exception in OK button: ", ex);
            }
        });

        restoreDefaultsButton.addActionListener(e -> {
            // Keep window on top during the entire process
            this.setAlwaysOnTop(true);

            try {
                // Restore defaults and apply changes - rebuild() will handle WaitCursor on main window
                restoreDefaultsAndApply();
            } catch (Exception ex) {
                logger.error("Exception in Restore Defaults button: ", ex);
            }
        });

        cancelButton.addActionListener(e -> {
            // Cancel closes the window without applying changes
            dispose();
        });

        // First row: Restore Defaults button (spans two columns)
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        gbc.weightx = 0.0; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        buttonPanel.add(restoreDefaultsButton, gbc);

        // Second row: OK, Apply, Cancel buttons (left to right)
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        gbc.weightx = 0.0; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        buttonPanel.add(this.okButton, gbc);

        gbc.gridx = 1; gbc.weightx = 0.0; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        buttonPanel.add(applyButton, gbc);

        gbc.gridx = 2; gbc.weightx = 0.0; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        buttonPanel.add(cancelButton, gbc);

        // Set OK button as default (most common action)
        this.getRootPane().setDefaultButton(this.okButton);

        return buttonPanel;
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
            JLabel label = new JLabel(pairs[fieldIndex][0] + ":");
            formPanel.add(label, gbc);

            gbc.gridx = 1; gbc.gridy = i;
            gbc.weightx = 0.0; // Don't expand fields column
            gbc.fill = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.WEST; // Left-align fields

            // Handle gear field specially (it's a JSpinner, not JTextField)
            // Widths tuned to windows rendering
            if (fieldIndex == 0) { // gear is at index 0 in pairs array
                gear.setPreferredSize(new Dimension(45, gear.getPreferredSize().height));
                formPanel.add(gear, gbc);
            } else {
                fields[fieldIndex - 1].setColumns(5); // Adjust index since gear is not in fields array
                formPanel.add(fields[fieldIndex - 1], gbc);
            }
        }

        panel.add(formPanel, BorderLayout.CENTER);

        // Set maximum width to 600px for better Windows display
        panel.setMaximumSize(new Dimension(600, panel.getPreferredSize().height));

        return panel;
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
            BorderFactory.createEmptyBorder(5, 0, 5, 0), // Top, left, bottom, right padding
            titledBorder));

        // Control panel at top
        JPanel controlPanel = new JPanel(new BorderLayout());

        // Top row with main controls
        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topRow.add(fileLabel);
        topRow.add(fileSelector);
        topRow.add(statusLabel);

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
        processPairs(this.filter, pairs, Integer.class);

        // Enable the filter when settings are applied
        this.filter.enabled(true);

        // Filter checkbox is now in OptionsMenu, no need to update separately
    }

    private void setFilterValueFromTextField(JTextField textField, java.util.function.IntConsumer setter) {
        try {
            setter.accept(Integer.parseInt(textField.getText()));
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

    private void processPairs(Filter filter, String[][] pairs, Class<?> clazz) {
        if (clazz == Integer.class) {
            setFilterValueFromSpinner(gear, filter::gear);
            setFilterValueFromTextField(minRPM, filter::minRPM);
            setFilterValueFromTextField(maxRPM, filter::maxRPM);
            setFilterValueFromTextField(minRPMRange, filter::minRPMRange);
            setFilterValueFromTextField(monotonicRPMfuzz, filter::monotonicRPMfuzz);
            setFilterValueFromTextField(minPedal, filter::minPedal);
            setFilterValueFromTextField(minThrottle, filter::minThrottle);
            setFilterValueFromTextField(minAcceleration, filter::minAcceleration);
            setFilterValueFromTextField(accelMAW, filter::accelMAW);
            setFilterValueFromTextField(minPoints, filter::minPoints);
            setFilterValueFromTextField(HPTQMAW, filter::HPTQMAW);
            setFilterValueFromTextField(ZeitMAW, filter::ZeitMAW);
        }
    }

    private void updateDialog() {
        updateDialog(this.filter, pairs);
    }

    private void updateDialog(Filter filter, String[][] pairs) {
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
        monotonicRPMfuzz.setText(String.valueOf(filter.monotonicRPMfuzz()));
        minPedal.setText(String.valueOf(filter.minPedal()));
        minThrottle.setText(String.valueOf(filter.minThrottle()));
        minAcceleration.setText(String.valueOf(filter.minAcceleration()));
        accelMAW.setText(String.valueOf(filter.accelMAW()));
        minPoints.setText(String.valueOf(filter.minPoints()));
        HPTQMAW.setText(String.valueOf(filter.HPTQMAW()));
        ZeitMAW.setText(String.valueOf(filter.ZeitMAW()));
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

    private void refreshVisualization() {
        if (dataset == null) return;

        try {
            // Clear existing data
            tableModel.setRowCount(0);
            rowIndexMapping = new ArrayList<>();

            int maxRows = (Integer) maxRowsSpinner.getValue();
            boolean showRPMData = showRPMDataCheckBox.isSelected();
            boolean showOnlyValid = showOnlyValidDataCheckBox.isSelected();

            // Get data columns
            Dataset.Column timeCol = dataset.get("TIME");
            Dataset.Column rpmCol = dataset.get("RPM");
            Dataset.Column mphCol = dataset.get("VehicleSpeed (MPH)");
            if (mphCol == null) {
                mphCol = dataset.get("VehicleSpeed");
            }
            Dataset.Column pedalCol = dataset.get(DataLogger.pedal());
            Dataset.Column throttleCol = dataset.get(DataLogger.throttle());
            Dataset.Column gearCol = dataset.get(DataLogger.gear());


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

            // Update column visibility based on RPM data setting
            updateColumnVisibility(showRPMData, pedalCol, throttleCol, gearCol);

            // Populate table
            for (int rowIndex : rowsToShow) {
                rowIndexMapping.add(rowIndex);
                Object[] rowData = createRowData(rowIndex, timeCol, rpmCol, mphCol, pedalCol, throttleCol, gearCol, ranges, showRPMData);
                tableModel.addRow(rowData);
            }

            statusLabel.setText(String.format("Showing %d of %d rows (%d valid)",
                rowsToShow.size(), totalRows, validCount));

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

    private void updateColumnVisibility(boolean showRPMData, Dataset.Column pedalCol, Dataset.Column throttleCol, Dataset.Column gearCol) {
        // Hide/show columns based on RPM data setting
        for (Column col : Column.values()) {
            if (col.requiresRPMData()) {
                setColumnVisibility(col, showRPMData);
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
                                 Dataset.Column mphCol, Dataset.Column pedalCol,
                                 Dataset.Column throttleCol, Dataset.Column gearCol,
                                 ArrayList<Dataset.Range> ranges, boolean showRPMData) {
        Object[] row = new Object[Column.getColumnCount()];

        try {
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

            // Acceleration (always show)
            double acceleration = 0;
            if (rpmCol != null && rowIndex > 0 && rowIndex < rpmCol.data.size() &&
                timeCol != null && rowIndex < timeCol.data.size()) {
                double deltaTime = timeCol.data.get(rowIndex) - timeCol.data.get(rowIndex - 1);
                if (deltaTime > 0) {
                    acceleration = deltaRPM / deltaTime;
                }
            }
            row[Column.idx(Column.ACCELERATION)] = String.format("%.1f", acceleration);

            // MPH-related columns (only populate if showRPMData is true)
            if (showRPMData) {
            // Raw MPH
            row[Column.idx(Column.RAW_MPH)] = mphCol != null && rowIndex < mphCol.data.size() ?
                String.format("%.1f", mphCol.data.get(rowIndex)) : "N/A";

            // Calc MPH (same as raw for now)
            row[Column.idx(Column.CALC_MPH)] = mphCol != null && rowIndex < mphCol.data.size() ?
                String.format("%.1f", mphCol.data.get(rowIndex)) : "N/A";

            // MPH Diff Percent
            row[Column.idx(Column.MPH_DIFF_PERCENT)] = "0.0%";

            // Delta MPH
            double deltaMPH = 0;
            if (mphCol != null && rowIndex > 0 && rowIndex < mphCol.data.size()) {
                deltaMPH = mphCol.data.get(rowIndex) - mphCol.data.get(rowIndex - 1);
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
            boolean isValid = isRowInRange(rowIndex, ranges);
            row[Column.idx(Column.FILTER_STATUS)] = isValid ? "PASS" : "FAIL";

            // Range number only
            String rangeValue = "";
            if (isValid) {
                int rangeNumber = getRangeNumber(rowIndex, ranges);
                rangeValue = String.valueOf(rangeNumber);
            }
            row[Column.idx(Column.RANGE)] = rangeValue;

            row[Column.idx(Column.FILTER_REASONS)] = isValid ? "" : "Not in valid range";

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
     * Helper method to restore normal window behavior
     */
    private void restoreWindowBehavior() {
        this.setAlwaysOnTop(false);
        this.toFront();
        this.requestFocus();
    }

    /**
     * Helper method to execute rebuild with proper synchronization
     *
     * NOTE: This method implements a separate WaitCursor mechanism for FilterWindow
     * because the main ECUxPlot.rebuild() method only shows WaitCursor on the main window.
     * FilterWindow operations need their own spinner to provide visual feedback to the user
     * that the operation is in progress, especially since the FilterWindow may be the active
     * window and the main window spinner may not be visible.
     *
     * FIXME: This could be generalized by modifying ECUxPlot.rebuild() to accept a list
     * of windows to show WaitCursor on, eliminating the need for separate WaitCursor
     * management in child windows. The signature could be:
     * rebuild(Runnable callback, JFrame... additionalWindows)
     */
    private void executeRebuildWithCallback(Runnable callback) throws Exception {
        WaitCursor.startWaitCursor(this);
        try {
            if (this.eplot != null) {
                this.eplot.rebuild(() -> {
                    // This callback runs after rebuild completes
                    if (callback != null) {
                        callback.run();
                    }
                    WaitCursor.stopWaitCursor(FilterWindow.this);
                });
            } else {
                if (callback != null) {
                    callback.run();
                }
                WaitCursor.stopWaitCursor(this);
            }
        } catch (Exception e) {
            WaitCursor.stopWaitCursor(this);
            throw e;
        }
    }

    /**
     * Apply filter changes and rebuild the plot
     * @throws Exception if any error occurs during the process
     */
    private void applyFilterChanges() throws Exception {
        // Actually read the values from the text fields and apply them
        processFilterChanges();

        // Keep window on top during the entire process
        this.setAlwaysOnTop(true);

        try {
            executeRebuildWithCallback(() -> {
                // Refresh the FilterWindow table AFTER rebuild completes
                refreshData();
            });
        } finally {
            restoreWindowBehavior();
        }
    }

    /**
     * Restore defaults and apply changes
     * @throws Exception if any error occurs during the process
     */
    private void restoreDefaultsAndApply() throws Exception {
        // Reset filter to default values using Filter class method
        this.filter.resetToDefaults();

        // Update the dialog to show default values
        updateDialog();

        // Process the filter changes (same as Apply button)
        processFilterChanges();

        executeRebuildWithCallback(() -> {
            // Refresh the FilterWindow table AFTER the main plot rebuild
            refreshData();
        });
    }

    private java.awt.Dimension windowSize() {
        return new java.awt.Dimension(
            ECUxPlot.getPreferences().getInt("FilterWindowWidth", 1000),
            ECUxPlot.getPreferences().getInt("FilterWindowHeight", 550));
    }

    private void putWindowSize() {
        ECUxPlot.getPreferences().putInt("FilterWindowWidth", this.getWidth());
        ECUxPlot.getPreferences().putInt("FilterWindowHeight", this.getHeight());
    }
}

// vim: set sw=4 ts=8 expandtab:
