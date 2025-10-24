package org.nyet.ecuxplot;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import java.util.ArrayList;
import java.util.TreeMap;
import org.nyet.logfile.Dataset;

/**
 * Filter Debug Panel - Shows data grid with filter status and range analysis
 * Helps visualize what the filter is doing with the data
 */
public class FilterDebugPanel extends JFrame {
    private static final long serialVersionUID = 1L;

    private ECUxDataset dataset;
    private TreeMap<String, ECUxDataset> fileDatasets;
    private ArrayList<Integer> rowIndexMapping; // Maps table row to actual data row
    private JTable dataTable;
    private DefaultTableModel tableModel;
    private JTextArea rangeAnalysisArea;
    private JLabel statusLabel;
    private JButton refreshButton;
    private JCheckBox showAllDataCheckBox;
    private JSpinner maxRowsSpinner;
    private JComboBox<String> fileSelector;
    private JLabel fileLabel;
    private JCheckBox showOnlyValidDataCheckBox;

    // Column definitions with metadata
    private enum Column {
        TIME(0, 80),
        RPM(1, 60),
        DELTA_RPM(2, 70),
        RAW_MPH(3, 80),
        CALC_MPH(4, 80),
        MPH_DIFF_PERCENT(5, 70),
        DELTA_MPH(6, 70),
        ACCELERATION(7, 100),
        PEDAL(8, 60),
        THROTTLE(9, 70),
        GEAR(10, 50),
        FILTER_STATUS(11, 80),
        FILTER_REASONS(12, 200),
        IN_RANGE(13, 70);

        private final int index;
        private final int width;

        Column(int index, int width) {
            this.index = index;
            this.width = width;
        }

        public int getIndex() { return index; }
        public int getWidth() { return width; }
        public static int getColumnCount() { return values().length; }

        // Helper method to avoid verbose getIndex() calls
        public static int idx(Column col) { return col.getIndex(); }
    }

    public FilterDebugPanel() {
        super("Filter Debug Panel");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(1200, 600);
        setLocationRelativeTo(null);

        initializeComponents();
        setupLayout();
    }

    private void initializeComponents() {
        // Create table model with columns
        String[] columnNames = {
            "Time", "RPM", "Δ RPM", "Raw MPH", "Calc MPH", "Diff %", "Δ MPH", "Accel (RPM/s)", "Pedal", "Throttle", "Gear",
            "Filter Status", "Filter Reasons", "In Range"
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

        // Mark VehicleSpeed column header based on data availability
        updateVehicleSpeedHeader();

        // Hide columns that are entirely N/A
        hideEmptyColumns();

        // Create range analysis text area
        rangeAnalysisArea = new JTextArea(8, 50);
        rangeAnalysisArea.setEditable(false);
        rangeAnalysisArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        // Create control panel
        statusLabel = new JLabel("No dataset loaded");
        refreshButton = new JButton("Refresh Data");
        showAllDataCheckBox = new JCheckBox("Show All Data Points", false);
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
                    setToolTipText(filename); // Full filename on hover
                }
                return this;
            }
        });
        showOnlyValidDataCheckBox = new JCheckBox("Show Only Valid Data", false);
        fileLabel = new JLabel("File:");

        // Add action listeners
        refreshButton.addActionListener(e -> refreshData());
        showAllDataCheckBox.addActionListener(e -> refreshData());
        maxRowsSpinner.addChangeListener(e -> refreshData());
        fileSelector.addActionListener(e -> onFileSelectionChanged());
        showOnlyValidDataCheckBox.addActionListener(e -> {
            updateColumnVisibility();
            refreshData();
        });
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        // Control panel at top - use BorderLayout for better space management
        JPanel controlPanel = new JPanel(new BorderLayout());

        // Top row with main controls
        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topRow.add(fileLabel);
        topRow.add(fileSelector);
        topRow.add(statusLabel);

        // Bottom row with checkboxes, spinner, and refresh button
        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomRow.add(showOnlyValidDataCheckBox);
        bottomRow.add(showAllDataCheckBox);
        bottomRow.add(new JLabel("Max Rows:"));
        bottomRow.add(maxRowsSpinner);
        bottomRow.add(refreshButton);

        controlPanel.add(topRow, BorderLayout.NORTH);
        controlPanel.add(bottomRow, BorderLayout.SOUTH);

        // Main content area
        JPanel contentPanel = new JPanel(new BorderLayout());

        // Data table with scroll pane
        JScrollPane tableScrollPane = new JScrollPane(dataTable);
        tableScrollPane.setPreferredSize(new Dimension(800, 300));

        // Range analysis panel
        JPanel rangePanel = new JPanel(new BorderLayout());
        rangePanel.setBorder(BorderFactory.createTitledBorder("Range Analysis"));
        rangePanel.add(new JScrollPane(rangeAnalysisArea), BorderLayout.CENTER);

        // Split pane for table and range analysis
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            tableScrollPane, rangePanel);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.6);

        contentPanel.add(splitPane, BorderLayout.CENTER);

        add(controlPanel, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);
    }

    public void setDataset(ECUxDataset dataset) {
        this.dataset = dataset;
        if (dataset != null) {
            // Check data availability for all columns
            boolean hasDerivedMph = dataset.get("VehicleSpeed (MPH)") != null;
            boolean hasRpm = dataset.get("RPM") != null;
            boolean hasPedal = dataset.get(DataLogger.pedal()) != null;
            boolean hasThrottle = dataset.get(DataLogger.throttle()) != null;
            boolean hasGear = dataset.get(DataLogger.gear()) != null;
            boolean hasAcceleration = dataset.get("Acceleration (RPM/s)") != null;

            // Build status string with data availability
            StringBuilder statusBuilder = new StringBuilder();
            statusBuilder.append("Filter: ").append(dataset.getFilter().enabled() ? "Enabled" : "Disabled");

            // VehicleSpeed status - only show comparison if there's raw VehicleSpeed data
            boolean hasRawVehicleSpeed = dataset.get("VehicleSpeed") != null;
            if (hasRawVehicleSpeed && hasRpm) {
                // Both raw and calc MPH available - show comparison
                Dataset.Column rawMphCol = dataset.get("VehicleSpeed (MPH)");
                if (rawMphCol != null && rawMphCol.data.size() > 0) {
                    double rawMph = rawMphCol.data.get(0);
                    double calcMph = dataset.get("RPM").data.get(0) / dataset.getEnv().c.rpm_per_mph();
                    double diffPercent = ((calcMph - rawMph) / rawMph) * 100;
                    statusBuilder.append(String.format(" | VehicleSpeed: Raw=%.1f mph, Calc=%.1f mph, Diff=%+.1f%%",
                        rawMph, calcMph, diffPercent));
                } else {
                    statusBuilder.append(" | VehicleSpeed: Raw+Calc Available");
                }
            } else if (hasRawVehicleSpeed) {
                statusBuilder.append(" | VehicleSpeed: Raw Data Only");
            } else if (hasDerivedMph) {
                statusBuilder.append(" | VehicleSpeed: Derived from RPM");
            } else if (hasRpm) {
                statusBuilder.append(" | VehicleSpeed: Calculated from RPM");
            } else {
                statusBuilder.append(" | VehicleSpeed: Not Available");
            }

            // Other data availability
            if (!hasRpm) statusBuilder.append(" | No RPM");
            if (!hasPedal) statusBuilder.append(" | No Pedal");
            if (!hasThrottle) statusBuilder.append(" | No Throttle");
            if (!hasGear) statusBuilder.append(" | No Gear");
            if (!hasAcceleration) statusBuilder.append(" | No Acceleration");

            // Check for Zeitronix Boost (used by filter)
            boolean hasZboost = dataset.get("Zeitronix Boost") != null;
            if (hasZboost) statusBuilder.append(" | Zeitronix Boost");

            String statusText = statusBuilder.toString();

            // Only update status if it doesn't already contain filename (for single file case)
            String currentText = statusLabel.getText();
            if (!currentText.startsWith("File:")) {
                statusLabel.setText(statusText);
            } else {
                // Update just the status info, keep filename
                statusLabel.setText(currentText.replaceAll("\\| Filter:.*", "| " + statusText.substring(statusText.indexOf("Filter:"))));
            }
        } else {
            statusLabel.setText("No dataset loaded");
        }
        updateVehicleSpeedHeader();
        hideEmptyColumns();
        updateColumnVisibility();
        refreshData();
    }

    public void setDatasets(TreeMap<String, ECUxDataset> fileDatasets) {
        this.fileDatasets = fileDatasets;

        // Update file selector
        fileSelector.removeAllItems();
        if (fileDatasets != null && !fileDatasets.isEmpty()) {
            if (fileDatasets.size() == 1) {
                // Single file - hide dropdown and show filename in status
                String singleFile = fileDatasets.keySet().iterator().next();
                fileSelector.setVisible(false);
                fileLabel.setVisible(false);
                statusLabel.setText("File: " + singleFile + " | " + statusLabel.getText());
            } else {
                // Multiple files - show dropdown
                fileSelector.setVisible(true);
                fileLabel.setVisible(true);
                for (String filename : fileDatasets.keySet()) {
                    fileSelector.addItem(filename);
                }
                // Select first file by default
                fileSelector.setSelectedIndex(0);
            }
            onFileSelectionChanged();
        } else {
            fileSelector.setVisible(false);
            fileLabel.setVisible(false);
            statusLabel.setText("No datasets loaded");
            updateVehicleSpeedHeader();
            updateColumnVisibility();
            refreshData();
        }
    }

    private void onFileSelectionChanged() {
        if (fileDatasets != null && !fileDatasets.isEmpty()) {
            if (fileDatasets.size() == 1) {
                // Single file case - get the only dataset
                String singleFile = fileDatasets.keySet().iterator().next();
                ECUxDataset selectedDataset = fileDatasets.get(singleFile);
                setDataset(selectedDataset);
            } else if (fileSelector.getSelectedItem() != null) {
                // Multiple files case - use selected item
                String selectedFile = (String) fileSelector.getSelectedItem();
                ECUxDataset selectedDataset = fileDatasets.get(selectedFile);
                setDataset(selectedDataset);
            }
        }
    }

    private void updateVehicleSpeedHeader() {
        if (dataset != null && dataTable != null) {
            TableColumnModel columnModel = dataTable.getColumnModel();

            // Update Raw MPH column header
            TableColumn rawMphColumn = columnModel.getColumn(Column.idx(Column.RAW_MPH));
            rawMphColumn.setHeaderValue("Raw MPH");

            // Update Calc MPH column header
            TableColumn calcMphColumn = columnModel.getColumn(Column.idx(Column.CALC_MPH));
            calcMphColumn.setHeaderValue("Calc MPH");

            // Force table header to repaint
            dataTable.getTableHeader().repaint();
        }
    }

    private void updateColumnVisibility() {
        if (dataTable != null) {
            TableColumnModel columnModel = dataTable.getColumnModel();
            TableColumn filterReasonsColumn = columnModel.getColumn(Column.idx(Column.FILTER_REASONS));

            // Hide Filter Reasons column when showing only valid data
            boolean showOnlyValid = showOnlyValidDataCheckBox.isSelected();
            filterReasonsColumn.setMinWidth(showOnlyValid ? 0 : 200);
            filterReasonsColumn.setMaxWidth(showOnlyValid ? 0 : Integer.MAX_VALUE);
            filterReasonsColumn.setPreferredWidth(showOnlyValid ? 0 : 200);
            filterReasonsColumn.setResizable(!showOnlyValid);

            // Force table to repaint
            dataTable.getTableHeader().repaint();
        }
    }

    private void hideEmptyColumns() {
        if (dataset == null || dataTable == null) return;

        TableColumnModel columnModel = dataTable.getColumnModel();

        // Check each column for data availability
        boolean[] columnHasData = new boolean[Column.getColumnCount()];

        // TIME - always has data if dataset exists
        columnHasData[Column.idx(Column.TIME)] = true;

        // RPM - check if available in current dataset
        columnHasData[Column.idx(Column.RPM)] = dataset.get("RPM") != null;

        // Delta RPM - same as RPM
        columnHasData[Column.idx(Column.DELTA_RPM)] = columnHasData[Column.idx(Column.RPM)];

        // VehicleSpeed columns - check if ANY dataset has VehicleSpeed data
        boolean hasRawVehicleSpeed = false;
        if (fileDatasets != null) {
            for (ECUxDataset ds : fileDatasets.values()) {
                if (ds.get("VehicleSpeed") != null) {
                    hasRawVehicleSpeed = true;
                    break;
                }
            }
        }

        // Raw MPH - show if ANY dataset has raw VehicleSpeed data
        columnHasData[Column.idx(Column.RAW_MPH)] = hasRawVehicleSpeed;

        // Calc MPH - show if ANY dataset has raw VehicleSpeed data (for comparison)
        columnHasData[Column.idx(Column.CALC_MPH)] = hasRawVehicleSpeed;

        // Diff % - show if ANY dataset has raw VehicleSpeed data
        columnHasData[Column.idx(Column.MPH_DIFF_PERCENT)] = hasRawVehicleSpeed;

        // Delta MPH - show if ANY dataset has raw VehicleSpeed data
        columnHasData[Column.idx(Column.DELTA_MPH)] = hasRawVehicleSpeed;

        // Acceleration - check if available
        columnHasData[Column.idx(Column.ACCELERATION)] = dataset.get("Acceleration (RPM/s)") != null;

        // Pedal - check if available
        columnHasData[Column.idx(Column.PEDAL)] = dataset.get(DataLogger.pedal()) != null;

        // Throttle - check if available
        columnHasData[Column.idx(Column.THROTTLE)] = dataset.get(DataLogger.throttle()) != null;

        // Gear - check if available
        columnHasData[Column.idx(Column.GEAR)] = dataset.get(DataLogger.gear()) != null;

        // Filter Status - always available
        columnHasData[Column.idx(Column.FILTER_STATUS)] = true;

        // Filter Reasons - always available
        columnHasData[Column.idx(Column.FILTER_REASONS)] = true;

        // In Range - always available
        columnHasData[Column.idx(Column.IN_RANGE)] = true;

        // Hide columns that have no data
        for (int i = 0; i < columnHasData.length; i++) {
            if (!columnHasData[i]) {
                TableColumn column = columnModel.getColumn(i);
                column.setMinWidth(0);
                column.setMaxWidth(0);
                column.setPreferredWidth(0);
                column.setResizable(false);
            } else {
                // Reset column visibility if it was previously hidden
                TableColumn column = columnModel.getColumn(i);
                column.setResizable(true);
                // Restore original width from enum
                for (Column col : Column.values()) {
                    if (col.getIndex() == i) {
                        column.setPreferredWidth(col.getWidth());
                        break;
                    }
                }
            }
        }

        // Force table to repaint
        dataTable.getTableHeader().repaint();
    }

    private void refreshData() {
        if (dataset == null) {
            tableModel.setRowCount(0);
            rangeAnalysisArea.setText("No dataset loaded");
            return;
        }

        // Clear existing data
        tableModel.setRowCount(0);

        int maxRows = (Integer) maxRowsSpinner.getValue();
        boolean showAll = showAllDataCheckBox.isSelected();
        boolean showOnlyValid = showOnlyValidDataCheckBox.isSelected();

        // Get data columns
        Dataset.Column timeCol = dataset.get("TIME");
        Dataset.Column rpmCol = dataset.get("RPM");
        // Try to get the MPH version first, fall back to raw VehicleSpeed
        Dataset.Column mphCol = dataset.get("VehicleSpeed (MPH)");
        if (mphCol == null) {
            mphCol = dataset.get("VehicleSpeed");
        }
        Dataset.Column pedalCol = dataset.get(DataLogger.pedal());
        Dataset.Column throttleCol = dataset.get(DataLogger.throttle());
        Dataset.Column gearCol = dataset.get(DataLogger.gear());

        if (timeCol == null || timeCol.data.size() == 0) {
            rangeAnalysisArea.setText("No TIME data available");
            return;
        }

        int totalRows = timeCol.data.size();
        int rowsToShow = showAll ? totalRows : Math.min(maxRows, totalRows);

        // Get ranges for "In Range" column
        ArrayList<Dataset.Range> ranges = dataset.getRanges();

        // Initialize row mapping
        rowIndexMapping = new ArrayList<>();

        // Add data rows
        int rowsAdded = 0;
        for (int i = 0; i < rowsToShow && (!showOnlyValid || rowsAdded < maxRows); i++) {
            // Check if we should filter this row
            if (showOnlyValid) {
                // Check if this row is in any valid range
                boolean inRange = false;
                for (Dataset.Range range : ranges) {
                    if (i >= range.start && i <= range.end) {
                        inRange = true;
                        break;
                    }
                }
                if (!inRange) {
                    continue; // Skip this row - it's not in any valid range
                }
            }
            Object[] row = new Object[Column.getColumnCount()];

            // Time
            row[Column.idx(Column.TIME)] = String.format("%.2f", timeCol.data.get(i));

            // RPM
            if (rpmCol != null && i < rpmCol.data.size()) {
                row[Column.idx(Column.RPM)] = String.format("%.0f", rpmCol.data.get(i));
            } else {
                row[Column.idx(Column.RPM)] = "N/A";
            }

            // Raw MPH (from VehicleSpeed (MPH) - converted from raw VehicleSpeed)
            Dataset.Column rawMphCol = dataset.get("VehicleSpeed (MPH)");
            if (rawMphCol != null && i < rawMphCol.data.size()) {
                row[Column.idx(Column.RAW_MPH)] = String.format("%.2f", rawMphCol.data.get(i));
            } else {
                row[Column.idx(Column.RAW_MPH)] = "N/A";
            }

            // Calc MPH (calculated from RPM)
            if (rpmCol != null && i < rpmCol.data.size()) {
                double calcMph = rpmCol.data.get(i) / dataset.getEnv().c.rpm_per_mph();
                row[Column.idx(Column.CALC_MPH)] = String.format("%.2f", calcMph);
            } else {
                row[Column.idx(Column.CALC_MPH)] = "N/A";
            }

            // Diff % (percentage difference between raw and calc MPH)
            if (rawMphCol != null && rpmCol != null && i < rawMphCol.data.size() && i < rpmCol.data.size()) {
                double rawMph = rawMphCol.data.get(i);
                double calcMph = rpmCol.data.get(i) / dataset.getEnv().c.rpm_per_mph();
                if (calcMph > 0) {
                    double diffPercent = ((calcMph - rawMph) / rawMph) * 100;
                    row[Column.idx(Column.MPH_DIFF_PERCENT)] = String.format("%+.1f%%", diffPercent);
                } else {
                    row[Column.idx(Column.MPH_DIFF_PERCENT)] = "N/A";
                }
            } else {
                row[Column.idx(Column.MPH_DIFF_PERCENT)] = "N/A";
            }

            // Delta RPM from previous row (moved right after RPM)
            if (i > 0 && rpmCol != null && i < rpmCol.data.size() && (i-1) < rpmCol.data.size()) {
                double currentRPM = rpmCol.data.get(i);
                double prevRPM = rpmCol.data.get(i-1);
                double deltaRPM = currentRPM - prevRPM;
                row[Column.idx(Column.DELTA_RPM)] = String.format("%+.0f", deltaRPM);
            } else {
                row[Column.idx(Column.DELTA_RPM)] = "N/A";
            }

            // Delta MPH from previous row (use raw MPH if available, otherwise calc MPH)
            if (i > 0) {
                double currentMPH = 0, prevMPH = 0;
                boolean hasData = false;

                // Try raw MPH first
                if (rawMphCol != null && i < rawMphCol.data.size() && (i-1) < rawMphCol.data.size()) {
                    currentMPH = rawMphCol.data.get(i);
                    prevMPH = rawMphCol.data.get(i-1);
                    hasData = true;
                }
                // Fall back to calc MPH
                else if (rpmCol != null && i < rpmCol.data.size() && (i-1) < rpmCol.data.size()) {
                    currentMPH = rpmCol.data.get(i) / dataset.getEnv().c.rpm_per_mph();
                    prevMPH = rpmCol.data.get(i-1) / dataset.getEnv().c.rpm_per_mph();
                    hasData = true;
                }

                if (hasData) {
                    double deltaMPH = currentMPH - prevMPH;
                    row[Column.idx(Column.DELTA_MPH)] = String.format("%+.2f", deltaMPH);
                } else {
                    row[Column.idx(Column.DELTA_MPH)] = "N/A";
                }
            } else {
                row[Column.idx(Column.DELTA_MPH)] = "N/A";
            }

            // Acceleration (RPM/s)
            Dataset.Column accelCol = dataset.get("Acceleration (RPM/s)");
            if (accelCol != null && i < accelCol.data.size()) {
                row[Column.idx(Column.ACCELERATION)] = String.format("%.1f", accelCol.data.get(i));
            } else {
                row[Column.idx(Column.ACCELERATION)] = "N/A";
            }

            // Pedal
            if (pedalCol != null && i < pedalCol.data.size()) {
                row[Column.idx(Column.PEDAL)] = String.format("%.1f", pedalCol.data.get(i));
            } else {
                row[Column.idx(Column.PEDAL)] = "N/A";
            }

            // Throttle
            if (throttleCol != null && i < throttleCol.data.size()) {
                row[Column.idx(Column.THROTTLE)] = String.format("%.1f", throttleCol.data.get(i));
            } else {
                row[Column.idx(Column.THROTTLE)] = "N/A";
            }

            // Gear
            if (gearCol != null && i < gearCol.data.size()) {
                row[Column.idx(Column.GEAR)] = String.format("%.0f", gearCol.data.get(i));
            } else {
                row[Column.idx(Column.GEAR)] = "N/A";
            }

            // Filter Status and Reasons
            if (dataset.getFilter().enabled()) {
                boolean isValid = dataset.dataValid(i);
                row[Column.idx(Column.FILTER_STATUS)] = isValid ? "✓ Valid" : "✗ Rejected";

                if (!isValid && dataset.getLastFilterReasons() != null) {
                    row[Column.idx(Column.FILTER_REASONS)] = String.join(", ", dataset.getLastFilterReasons());
                } else {
                    row[Column.idx(Column.FILTER_REASONS)] = "";
                }
            } else {
                row[Column.idx(Column.FILTER_STATUS)] = "Filter Disabled";
                row[Column.idx(Column.FILTER_REASONS)] = "";
            }

            // In Range - which range this row belongs to
            int rangeNumber = -1;
            for (int r = 0; r < ranges.size(); r++) {
                Dataset.Range range = ranges.get(r);
                if (i >= range.start && i <= range.end) {
                    rangeNumber = r + 1; // 1-based for display
                    break;
                }
            }
            row[Column.idx(Column.IN_RANGE)] = rangeNumber > 0 ? "Range " + rangeNumber : "None";

            tableModel.addRow(row);
            rowIndexMapping.add(i); // Map table row to actual data row
            rowsAdded++;
        }

        // Update range analysis
        updateRangeAnalysis();
    }

    private void updateRangeAnalysis() {
        if (dataset == null) {
            rangeAnalysisArea.setText("No dataset loaded");
            return;
        }

        StringBuilder analysis = new StringBuilder();

        // Filter status
        analysis.append("FILTER STATUS:\n");
        analysis.append("  Enabled: ").append(dataset.getFilter().enabled()).append("\n");
        if (dataset.getFilter().enabled()) {
            analysis.append("  Min RPM: ").append(dataset.getFilter().minRPM()).append("\n");
            analysis.append("  Max RPM: ").append(dataset.getFilter().maxRPM()).append("\n");
            analysis.append("  Min RPM Range: ").append(dataset.getFilter().minRPMRange()).append("\n");
            analysis.append("  Min Pedal: ").append(dataset.getFilter().minPedal()).append("\n");
            analysis.append("  Min Throttle: ").append(dataset.getFilter().minThrottle()).append("\n");
            analysis.append("  Gear Filter: ").append(dataset.getFilter().gear()).append("\n");
            analysis.append("  Min Points: ").append(dataset.getFilter().minPoints()).append("\n");
            analysis.append("  Monotonic RPM: ").append(dataset.getFilter().monotonicRPM()).append("\n");
            analysis.append("  Monotonic RPM Fuzz: ").append(dataset.getFilter().monotonicRPMfuzz()).append("\n");
        }
        analysis.append("\n");

        // Range analysis
        ArrayList<Dataset.Range> ranges = dataset.getRanges();
        analysis.append("RANGE ANALYSIS:\n");
        analysis.append("  Total Ranges Found: ").append(ranges.size()).append("\n");

        if (ranges.size() > 0) {
            analysis.append("\n  Range Details:\n");
            for (int i = 0; i < ranges.size(); i++) {
                Dataset.Range range = ranges.get(i);
                analysis.append("    Range ").append(i + 1).append(": ")
                    .append(range.toString()).append(" (")
                    .append(range.size()).append(" points)\n");

                // Add RPM range info if available
                Dataset.Column rpmCol = dataset.get("RPM");
                if (rpmCol != null && range.start < rpmCol.data.size() && range.end < rpmCol.data.size()) {
                    double startRPM = rpmCol.data.get(range.start);
                    double endRPM = rpmCol.data.get(range.end);
                    analysis.append("      RPM Range: ").append(String.format("%.0f", startRPM))
                        .append(" - ").append(String.format("%.0f", endRPM))
                        .append(" (Δ").append(String.format("%.0f", endRPM - startRPM)).append(")\n");
                }

                // Add time range info
                Dataset.Column timeCol = dataset.get("TIME");
                if (timeCol != null && range.start < timeCol.data.size() && range.end < timeCol.data.size()) {
                    double startTime = timeCol.data.get(range.start);
                    double endTime = timeCol.data.get(range.end);
                    analysis.append("      Time Range: ").append(String.format("%.2f", startTime))
                        .append(" - ").append(String.format("%.2f", endTime))
                        .append(" (Δ").append(String.format("%.2f", endTime - startTime)).append("s)\n");
                }
            }
        } else {
            analysis.append("  No valid ranges found!\n");
            analysis.append("  Check filter settings or data quality.\n");
        }

        rangeAnalysisArea.setText(analysis.toString());
    }

    public void showWindow() {
        setVisible(true);
        toFront();
    }

    // Custom cell renderer for color coding ranges
    private class RangeColorRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        // Colors for different ranges
        private final Color[] rangeColors = {
            new Color(240, 248, 255), // Light blue
            new Color(255, 248, 240), // Light orange
            new Color(240, 255, 240), // Light green
            new Color(255, 240, 255), // Light magenta
            new Color(248, 248, 255), // Light lavender
            new Color(255, 255, 240), // Light yellow
            new Color(240, 255, 255), // Light cyan
            new Color(255, 240, 240)  // Light pink
        };

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            // Always respect selection - don't override selection colors
            if (isSelected) {
                return this; // Use default selection colors
            }

            if (dataset != null) {
                // Get the actual row index in the dataset (accounting for filtering)
                int actualRowIndex = getActualRowIndex(row);

                if (actualRowIndex >= 0) {
                    // Determine range and position
                    RangeInfo rangeInfo = getRangeInfo(actualRowIndex);

                    if (rangeInfo != null) {
                        // Set background color based on range
                        int colorIndex = rangeInfo.rangeNumber % rangeColors.length;
                        setBackground(rangeColors[colorIndex]);

                        // Make start/end rows bold
                        if (rangeInfo.isStart || rangeInfo.isEnd) {
                            setFont(getFont().deriveFont(Font.BOLD));
                        } else {
                            setFont(getFont().deriveFont(Font.PLAIN));
                        }

                        return this;
                    }
                }
            }

            // Default appearance for non-range rows
            setBackground(Color.WHITE);
            setFont(getFont().deriveFont(Font.PLAIN));
            return this;
        }

        private int getActualRowIndex(int tableRow) {
            if (rowIndexMapping != null && tableRow < rowIndexMapping.size()) {
                return rowIndexMapping.get(tableRow);
            }
            return tableRow; // Fallback to table row if mapping not available
        }

        private RangeInfo getRangeInfo(int rowIndex) {
            if (dataset == null) return null;

            ArrayList<Dataset.Range> ranges = dataset.getRanges();
            for (int i = 0; i < ranges.size(); i++) {
                Dataset.Range range = ranges.get(i);
                if (rowIndex >= range.start && rowIndex <= range.end) {
                    boolean isStart = (rowIndex == range.start);
                    boolean isEnd = (rowIndex == range.end);
                    return new RangeInfo(i + 1, isStart, isEnd);
                }
            }
            return null;
        }
    }

    /**
     * Copy selected table data to clipboard in tab-separated format
     */
    private void copyTableData() {
        int[] selectedRows = dataTable.getSelectedRows();
        int[] selectedCols = dataTable.getSelectedColumns();

        if (selectedRows.length == 0 || selectedCols.length == 0) {
            return; // Nothing selected
        }

        StringBuilder clipboardData = new StringBuilder();

        // Add header row
        for (int col : selectedCols) {
            String columnName = dataTable.getColumnModel().getColumn(col).getHeaderValue().toString();
            clipboardData.append(columnName);
            if (col < selectedCols.length - 1) {
                clipboardData.append("\t");
            }
        }
        clipboardData.append("\n");

        // Add data rows
        for (int row : selectedRows) {
            for (int col : selectedCols) {
                Object value = dataTable.getValueAt(row, col);
                clipboardData.append(value != null ? value.toString() : "");
                if (col < selectedCols.length - 1) {
                    clipboardData.append("\t");
                }
            }
            clipboardData.append("\n");
        }

        // Copy to clipboard
        StringSelection selection = new StringSelection(clipboardData.toString());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, null);
    }

    // Helper class to hold range information
    private static class RangeInfo {
        final int rangeNumber;
        final boolean isStart;
        final boolean isEnd;

        RangeInfo(int rangeNumber, boolean isStart, boolean isEnd) {
            this.rangeNumber = rangeNumber;
            this.isStart = isStart;
            this.isEnd = isEnd;
        }
    }
}

// vim: set sw=4 ts=8 expandtab:
