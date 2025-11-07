package org.nyet.ecuxplot;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.TreeMap;
import javax.swing.*;
import javax.swing.table.*;

import org.nyet.logfile.Dataset;
import org.nyet.util.Strings;
import org.nyet.util.Smoothing;
import static org.nyet.util.Smoothing.Padding;
import static org.nyet.util.Smoothing.Strategy;

/**
 * Smoothing Window
 * Visualizes the entire smoothing chain from RPM → acceleration → HP
 * Shows derivatives, differences, padding regions, and boundary effects
 */
public class SmoothingWindow extends ECUxPlotWindow {
    private static final long serialVersionUID = 1L;

    // Table components
    private JTable dataTable;
    private DefaultTableModel tableModel;
    private ArrayList<Integer> rowIndexMapping;

    // Control components
    private JComboBox<String> fileSelector;
    private JComboBox<String> rangeSelector;
    private JLabel statusLabel;
    private JSpinner maxRowsSpinner;
    private JCheckBox showOnlyValidDataCheckBox;

    // Smoothing configuration controls (for testing variants)
    private JComboBox<Strategy> smoothingStrategyCombo;
    private JComboBox<Padding> leftPaddingCombo;
    private JComboBox<Padding> rightPaddingCombo;
    private JTextField HPMAW;
    private JTextField ZeitMAW;
    private JButton okButton; // Store reference to OK button

    // Data
    private TreeMap<String, ECUxDataset> fileDatasets;
    private ECUxDataset currentDataset;
    private ArrayList<Dataset.Range> currentRanges;

    // Column definitions - focused on HP and Acceleration m/s² pre/post getData
    // Grouped: deltas first, then raw/pre, then post, then diagnostic info
    private enum Column {
        SAMPLE(0, 80),
        SAMPLE_RANGE(1, 90),
        // Delta columns first
        DELTA_HP_PRE(2, 90),     // Δ HP raw
        DELTA_HP_POST(3, 90),    // Δ HP smoothed
        DELTA_ACCEL_RAW(4, 100), // Δ Acceleration m/s² raw
        DELTA_ACCEL_POST(5, 100), // Δ Acceleration m/s² smoothed
        // Raw/Pre columns
        HP_PRE(6, 80),           // HP raw (from column.data, before getData smoothing)
        ACCEL_M_S2_RAW(7, 100),  // Acceleration m/s² raw (from column.data)
        // Post columns
        HP_POST(8, 80),          // HP smoothed (from getData())
        ACCEL_M_S2_POST(9, 100), // Acceleration m/s² smoothed (from getData())
        // Diagnostic columns
        WINDOW_SIZE(10, 80),      // Effective smoothing window size (after clamping)
        RANGE_SIZE(11, 80);      // Number of samples in the range

        private final int index;
        private final int width;

        Column(int index, int width) {
            this.index = index;
            this.width = width;
        }

        public int getIndex() { return index; }
        public int getWidth() { return width; }
        public static int getColumnCount() { return values().length; }
        public static int idx(Column col) { return col.getIndex(); }
    }

    public SmoothingWindow(Filter filter, ECUxPlot eplot,
                                     TreeMap<String, ECUxDataset> fileDatasets) {
        super("Smoothing", filter, eplot);
        this.fileDatasets = fileDatasets;

        // Set minimum size first to ensure loaded preferences are enforced
        setMinimumSize(new Dimension(900, 300));
        setSize(this.windowSize());
        setLocationRelativeTo(null);

        initializeComponents();
        setupLayout();

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


    /**
     * Set the file datasets for multi-file support.
     * Updates the file selector and refreshes the table.
     */
    public void setFileDatasets(TreeMap<String, ECUxDataset> fileDatasets) {
        this.fileDatasets = fileDatasets;

        // Update file selector
        if (fileSelector != null) {
            String currentSelection = (String) fileSelector.getSelectedItem();
            fileSelector.removeAllItems();
            if (fileDatasets != null) {
                for (String filename : fileDatasets.keySet()) {
                    fileSelector.addItem(filename);
                }
            }
            // Restore selection if it still exists
            if (currentSelection != null) {
                for (int i = 0; i < fileSelector.getItemCount(); i++) {
                    if (fileSelector.getItemAt(i).equals(currentSelection)) {
                        fileSelector.setSelectedIndex(i);
                        loadDataset(currentSelection);
                        return;
                    }
                }
            }
            // If no selection or selection not found, load first file
            if (fileSelector.getItemCount() > 0) {
                String firstFile = fileSelector.getItemAt(0);
                fileSelector.setSelectedItem(firstFile);
                loadDataset(firstFile);
            }
        }
    }

    private void initializeComponents() {
        initializeTable();
        createControlPanel();

        // Initialize with first file if available
        if (fileDatasets != null && !fileDatasets.isEmpty()) {
            String firstFile = fileDatasets.firstKey();
            fileSelector.setSelectedItem(firstFile);
            loadDataset(firstFile);
        }
    }

    private void initializeTable() {
        // Create table model with columns - deltas first, then raw/pre, then post, then diagnostic
        String[] columnNames = {
            "sample", "sample[range]",
            "ΔHP (pre)", "ΔHP (post)",
            "ΔAccel (raw)", "ΔAccel (post)",
            "HP (pre)", "Accel m/s² (raw)",
            "HP (post)", "Accel m/s² (post)",
            "window", "samples"
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

        // Set up keyboard shortcuts (when table has focus)
        dataTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ctrl C"), "copy");
        dataTable.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("meta C"), "copy");

        // Add context menu for copy
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.addActionListener(e -> copyTableData());
        popupMenu.add(copyItem);
        dataTable.setComponentPopupMenu(popupMenu);

        // Set custom renderer for color coding
        dataTable.setDefaultRenderer(Object.class, new RangeColorRenderer());

        // Force repaint to ensure renderer is applied
        dataTable.setOpaque(true);

        // Set column widths
        TableColumnModel columnModel = dataTable.getColumnModel();
        for (Column col : Column.values()) {
            columnModel.getColumn(col.getIndex()).setPreferredWidth(col.getWidth());
        }

        rowIndexMapping = new ArrayList<>();
    }

    private void createControlPanel() {
        // File selector
        fileSelector = new JComboBox<>();
        if (fileDatasets != null) {
            for (String filename : fileDatasets.keySet()) {
                fileSelector.addItem(filename);
            }
        }
        // Use custom renderer to elide long filenames
        fileSelector.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value != null) {
                    String filename = value.toString();
                    String displayText = Strings.elide(filename, 60);
                    setText(displayText);
                    setToolTipText(filename);
                }
                return this;
            }
        });
        fileSelector.addActionListener(e -> {
            String selected = (String) fileSelector.getSelectedItem();
            if (selected != null) {
                loadDataset(selected);
            }
        });

        // Range selector
        rangeSelector = new JComboBox<>();
        rangeSelector.addItem("All Ranges");
        rangeSelector.addActionListener(e -> refreshTable());

        // Status label
        statusLabel = new JLabel("No dataset loaded");

        // Max rows spinner
        maxRowsSpinner = new JSpinner(new SpinnerNumberModel(1000, 10, 10000, 100));
        maxRowsSpinner.addChangeListener(e -> refreshTable());

        // Show only valid data checkbox
        showOnlyValidDataCheckBox = new JCheckBox("Show Only Valid Data", true);
        showOnlyValidDataCheckBox.addActionListener(e -> refreshTable());

        // Smoothing configuration controls
        smoothingStrategyCombo = new JComboBox<>(Strategy.values());
        smoothingStrategyCombo.setToolTipText(
            "<html>Smoothing strategy:<br/>" +
            "• <b>MAW</b>: Moving Average Window (default, smooths out noise and inflection points)<br/>" +
            "• <b>SG</b>: Savitzky-Golay filter (preserves inflection points, better for derivatives)</html>"
        );
        // Note: Strategy and padding changes are applied via Apply/OK buttons
        // No auto-rebuild listeners here - changes are batched and applied together

        leftPaddingCombo = new JComboBox<>(Padding.values());
        leftPaddingCombo.setToolTipText("Left padding method: none, mirror (reflect), or data (extend from full dataset)");

        rightPaddingCombo = new JComboBox<>(Padding.values());
        rightPaddingCombo.setToolTipText("Right padding method: none, mirror (reflect), or data (extend from full dataset)");

        // HPMAW control
        HPMAW = new JTextField(10);
        HPMAW.setToolTipText("HP/TQ Smoothing window (seconds). Affects: WHP, HP, WTQ, TQ - range-aware smoothing");

        // ZeitMAW control
        ZeitMAW = new JTextField(10);
        ZeitMAW.setToolTipText("Zeitronix boost pressure smoothing window (seconds). Affects: Zeitronix Boost (PSI) - range-aware smoothing");
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        // Create main horizontal panel for controls and visualization
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Left panel: Smoothing Controls
        JPanel controlPanel = createControlEditorPanel();
        mainPanel.add(controlPanel, BorderLayout.WEST);

        // Right panel: Data Visualization (full remaining width)
        JPanel visualizationPanel = createVisualizationPanel();
        mainPanel.add(visualizationPanel, BorderLayout.CENTER);

        // Add main panel to window
        add(mainPanel, BorderLayout.CENTER);
    }

    private JPanel createControlEditorPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Add padding to match visualization panel
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 0)); // Top, left, bottom, right padding

        // Create main form panel with BoxLayout for vertical stacking
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));

        // Smoothing configuration panel - single column layout with right-aligned labels
        JPanel smoothingPanel = new JPanel(new BorderLayout());
        // Create bold title border
        javax.swing.border.TitledBorder titledBorder = BorderFactory.createTitledBorder(null, "Smoothing Parameters",
            javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP);
        titledBorder.setTitleFont(titledBorder.getTitleFont().deriveFont(Font.BOLD));
        smoothingPanel.setBorder(titledBorder);

        JPanel smoothingFormPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);

        // Strategy row
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST; // Right-align label
        smoothingFormPanel.add(new JLabel("Strategy:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0;
        gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.WEST; // Left-align field
        smoothingStrategyCombo.setPreferredSize(new Dimension(100, smoothingStrategyCombo.getPreferredSize().height));
        smoothingFormPanel.add(smoothingStrategyCombo, gbc);

        // Left Pad row
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.EAST;
        smoothingFormPanel.add(new JLabel("Left Pad:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1;
        gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.WEST;
        leftPaddingCombo.setPreferredSize(new Dimension(100, leftPaddingCombo.getPreferredSize().height));
        smoothingFormPanel.add(leftPaddingCombo, gbc);

        // Right Pad row
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.EAST;
        smoothingFormPanel.add(new JLabel("Right Pad:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2;
        gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.WEST;
        rightPaddingCombo.setPreferredSize(new Dimension(100, rightPaddingCombo.getPreferredSize().height));
        smoothingFormPanel.add(rightPaddingCombo, gbc);

        // HP/TQ Smoothing row
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.EAST;
        smoothingFormPanel.add(new JLabel("HP/TQ Smoothing (s):"), gbc);
        gbc.gridx = 1; gbc.gridy = 3;
        gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.WEST;
        HPMAW.setColumns(5);
        smoothingFormPanel.add(HPMAW, gbc);

        // Zeitronix Smoothing row
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.EAST;
        smoothingFormPanel.add(new JLabel("Zeitronix Smoothing (s):"), gbc);
        gbc.gridx = 1; gbc.gridy = 4;
        gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.WEST;
        ZeitMAW.setColumns(5);
        smoothingFormPanel.add(ZeitMAW, gbc);

        smoothingPanel.add(smoothingFormPanel, BorderLayout.CENTER);

        formPanel.add(smoothingPanel);

        // Add scroll pane to handle overflow
        JScrollPane scrollPane = new JScrollPane(formPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);

        panel.add(scrollPane, BorderLayout.NORTH);

        // Add buttons at the bottom of the control panel
        JPanel buttonPanel = createButtonPanel();
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());

        JButton applyButton = new JButton("Apply");
        this.okButton = new JButton("OK");
        JButton restoreDefaultsButton = new JButton("Restore Defaults");
        JButton filterButton = new JButton("Filter...");
        JButton cancelButton = new JButton("Cancel");

        applyButton.addActionListener(e -> {
            setTopWindow();
            try {
                applySmoothingChanges();
            } catch (Exception ex) {
                logger.error("Exception in Apply button: ", ex);
                clearTopWindow();
            }
        });

        this.okButton.addActionListener(e -> {
            setTopWindow();
            try {
                applySmoothingChanges();
                clearTopWindow();
                dispose(); // Close window after applying
            } catch (Exception ex) {
                logger.error("Exception in OK button: ", ex);
                clearTopWindow();
            }
        });

        restoreDefaultsButton.addActionListener(e -> {
            setTopWindow();
            try {
                restoreDefaultsAndApply();
            } catch (Exception ex) {
                logger.error("Exception in Restore Defaults button: ", ex);
                clearTopWindow();
            }
        });

        filterButton.addActionListener(e -> {
            // Open Filter window
            withEplot(plot -> plot.openFilterWindow());
        });

        cancelButton.addActionListener(e -> {
            // Cancel closes the window without applying changes
            dispose();
        });

        // First row: Restore Defaults and Filter buttons (pack West)
        JPanel firstRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        firstRow.add(restoreDefaultsButton);
        firstRow.add(filterButton);

        // Second row: OK, Apply, Cancel buttons (pack West)
        JPanel secondRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        secondRow.add(this.okButton);
        secondRow.add(applyButton);
        secondRow.add(cancelButton);

        mainPanel.add(firstRow, BorderLayout.NORTH);
        mainPanel.add(secondRow, BorderLayout.SOUTH);

        // Set OK button as default (most common action)
        this.getRootPane().setDefaultButton(this.okButton);

        return mainPanel;
    }

    private JPanel createVisualizationPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        // Create bold title border
        javax.swing.border.TitledBorder titledBorder = BorderFactory.createTitledBorder(null, "Data Visualization",
            javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP);
        titledBorder.setTitleFont(titledBorder.getTitleFont().deriveFont(Font.BOLD));

        // Add padding to match control panel
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(5, 0, 5, 5), // Top, left, bottom, right padding
            titledBorder));

        // Control panel at top
        JPanel controlPanel = new JPanel(new BorderLayout());

        // Top row with main controls - use BorderLayout for better control
        JPanel topRow = new JPanel(new BorderLayout());

        // Left side: file label, selector, range label, selector
        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filePanel.add(new JLabel("File:"));
        filePanel.add(fileSelector);
        filePanel.add(new JLabel("Range:"));
        filePanel.add(rangeSelector);

        topRow.add(filePanel, BorderLayout.WEST);
        topRow.add(statusLabel, BorderLayout.EAST);

        // Bottom row with checkboxes and spinner
        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomRow.add(showOnlyValidDataCheckBox);
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

    private void loadDataset(String filename) {
        currentDataset = fileDatasets.get(filename);
        if (currentDataset == null) {
            statusLabel.setText("Error: Dataset not found");
            return;
        }

        // Sync UI controls with current dataset settings
        smoothingStrategyCombo.setSelectedItem(currentDataset.postDiffSmoothingStrategy);
        leftPaddingCombo.setSelectedItem(currentDataset.padding.left);
        rightPaddingCombo.setSelectedItem(currentDataset.padding.right);
        HPMAW.setText(String.valueOf(filter.HPMAW()));
        ZeitMAW.setText(String.valueOf(filter.ZeitMAW()));

        // Get ranges first (needed for updateSmoothingStatus)
        currentRanges = currentDataset.getRanges();
        if (currentRanges == null || currentRanges.isEmpty()) {
            currentRanges = new ArrayList<>();
            // Create a single range covering all data if filter is disabled
            if (!isFilterEnabled() && currentDataset.length() > 0) {
                currentRanges.add(currentDataset.new Range(0, currentDataset.length() - 1));
            }
        }

        // Update range selector
        rangeSelector.removeAllItems();
        if (currentRanges.isEmpty()) {
            rangeSelector.addItem("No ranges");
        } else {
            rangeSelector.addItem("All Ranges");
            for (int i = 0; i < currentRanges.size(); i++) {
                rangeSelector.addItem("Range " + i);
            }
        }

        // Update status after ranges are loaded
        updateSmoothingStatus();

        refreshTable();
    }

    private void refreshTable() {
        if (currentDataset == null) {
            return;
        }

        try {
            // Ensure currentRanges is initialized
            if (currentRanges == null) {
                currentRanges = currentDataset.getRanges();
                if (currentRanges == null || currentRanges.isEmpty()) {
                    currentRanges = new ArrayList<>();
                }
            }

            // Clear existing data
            tableModel.setRowCount(0);
            rowIndexMapping = new ArrayList<>();

            int maxRows = (Integer) maxRowsSpinner.getValue();
            boolean showOnlyValid = showOnlyValidDataCheckBox.isSelected();

            // Get TIME column
            Dataset.Column timeCol = currentDataset.get("TIME");
            if (timeCol == null || timeCol.data.size() == 0) {
                statusLabel.setText("No time data available");
                return;
            }

            int totalRows = timeCol.data.size();

            // For display purposes, if ranges is empty, create a single range covering all data
            // This ensures background coloring works even when no filter ranges exist
            ArrayList<Dataset.Range> ranges = currentRanges;
            if (ranges.isEmpty() && totalRows > 0) {
                ranges = new ArrayList<>();
                ranges.add(currentDataset.new Range(0, totalRows - 1));
            }

            // Determine which rows to show
            ArrayList<Integer> rowsToShow = new ArrayList<>();
            if (showOnlyValid && !ranges.isEmpty()) {
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

            // Get all diagnostic columns
            Dataset.Column[] columns = getDiagnosticColumns();

            // Populate table
            for (int rowIndex : rowsToShow) {
                rowIndexMapping.add(rowIndex);
                Object[] rowData = createRowData(rowIndex, timeCol, columns, ranges);
                tableModel.addRow(rowData);
            }

            // Update status with smoothing information
            updateSmoothingStatus();

            // Force table update
            tableModel.fireTableDataChanged();
            if (dataTable != null) {
                dataTable.revalidate();
                dataTable.repaint();
                // Force repaint of all cells to ensure renderer is applied
                for (int i = 0; i < dataTable.getRowCount(); i++) {
                    for (int j = 0; j < dataTable.getColumnCount(); j++) {
                        dataTable.prepareRenderer(dataTable.getCellRenderer(i, j), i, j);
                    }
                }
            }

        } catch (Exception e) {
            statusLabel.setText("Error loading data: " + e.getMessage());
            logger.error("Error refreshing table data", e);
        }

        // Repack window width after table refresh (in case column widths changed)
    }

    private void updateSmoothingStatus() {
        if (currentDataset == null) {
            statusLabel.setText("No dataset loaded");
            return;
        }

        final Strategy strategy = currentDataset.postDiffSmoothingStrategy;

        // Check if "Show only valid data" is checked - if not, there's no concept of "current range"
        boolean showOnlyValid = showOnlyValidDataCheckBox.isSelected();

        if (!showOnlyValid || currentRanges == null || currentRanges.isEmpty()) {
            // No range concept - show general smoothing configuration
            // Get HP smoothing info with full dataset size as reference
            int fullDatasetSize = currentDataset.length();
            int[] windowInfo = currentDataset.getSmoothingWindowInfo("HP", fullDatasetSize);

            if (windowInfo == null) {
                // Calculate padding size based on strategy
                int paddingSize = (strategy == Strategy.SG) ? 5 : 0; // For MAW, need window size which we don't have
                statusLabel.setText(String.format("Padding Size: %d | Showing all data (no range filtering)", paddingSize));
                return;
            }

            final int originalWindow = windowInfo[0];
            final int effectiveWindow = windowInfo[1];
            final boolean clamped = (effectiveWindow != originalWindow);

            // Calculate padding size: SG = 5, MAW = (effectiveWindow - 1) / 2
            final int paddingSize = (strategy == Strategy.SG) ? 5 : (effectiveWindow - 1) / 2;

            StringBuilder status = new StringBuilder();
            status.append(String.format("Padding Size: %d", paddingSize));
            if (clamped) {
                status.append(String.format(" (window: %d → %d)", originalWindow, effectiveWindow));
            }
            status.append(" | Showing all data (no range filtering)");

            statusLabel.setText(status.toString());
            return;
        }

        // Get current range (only when showing only valid data)
        Dataset.Range currentRange = null;
        String selectedRange = (String) rangeSelector.getSelectedItem();
        if (selectedRange != null && !selectedRange.equals("All Ranges") && !selectedRange.equals("No ranges")) {
            // Parse range index from "Range N" format
            try {
                int rangeIndex = Integer.parseInt(selectedRange.replace("Range ", "")) - 1;
                if (rangeIndex >= 0 && rangeIndex < currentRanges.size()) {
                    currentRange = currentRanges.get(rangeIndex);
                }
            } catch (NumberFormatException e) {
                // Ignore
            }
        } else if (selectedRange != null && selectedRange.equals("All Ranges") && !currentRanges.isEmpty()) {
            // Use first range for "All Ranges" as representative
            currentRange = currentRanges.get(0);
        }

        if (currentRange == null) {
            // Calculate padding size based on strategy (use default window estimate)
            int paddingSize = (strategy == Strategy.SG) ? 5 : 0;
            statusLabel.setText(String.format("Padding Size: %d | No specific range selected", paddingSize));
            return;
        }

        final int rangeSize = currentRange.end - currentRange.start + 1;

        // Get HP smoothing info (as the primary column we're tracking)
        int[] windowInfo = currentDataset.getSmoothingWindowInfo("HP", rangeSize);
        if (windowInfo == null) {
            // Calculate padding size based on strategy (use default window estimate)
            int paddingSize = (strategy == Strategy.SG) ? 5 : 0;
            statusLabel.setText(String.format("Range: %d samples | Padding Size: %d",
                rangeSize, paddingSize));
            return;
        }

        final int originalWindow = windowInfo[0];
        final int effectiveWindow = windowInfo[1];
        final boolean clamped = (effectiveWindow != originalWindow);

        // Calculate padding size: SG = 5, MAW = (effectiveWindow - 1) / 2
        final int paddingSize = (strategy == Strategy.SG) ? 5 : (effectiveWindow - 1) / 2;

        // Build status text
        StringBuilder status = new StringBuilder();
        status.append(String.format("Range: %d samples | Padding Size: %d", rangeSize, paddingSize));
        if (clamped) {
            status.append(String.format(" (window: %d → %d)", originalWindow, effectiveWindow));
        }

        statusLabel.setText(status.toString());
    }

    private Dataset.Column[] getDiagnosticColumns() {
        // Return only HP and Acceleration m/s² columns (raw data)
        // Smoothed data will be retrieved via getData() in createRowData()
        return new Dataset.Column[] {
            currentDataset.get("HP"),
            currentDataset.get("Acceleration (m/s^2)")
        };
    }

    private Object[] createRowData(int rowIndex, Dataset.Column timeCol,
                                 Dataset.Column[] columns, ArrayList<Dataset.Range> ranges) {
        Object[] row = new Object[Column.getColumnCount()];

        try {
            // Sample (absolute index)
            row[Column.idx(Column.SAMPLE)] = String.valueOf(rowIndex);

            // Sample[range] (index within the current range)
            Dataset.Range range = findRangeForIndex(ranges, rowIndex);
            int sampleInRange = -1;
            if (range != null) {
                sampleInRange = rowIndex - range.start;
                row[Column.idx(Column.SAMPLE_RANGE)] = String.valueOf(sampleInRange);
            } else {
                row[Column.idx(Column.SAMPLE_RANGE)] = "";
            }

            // Get HP and Acceleration columns
            Dataset.Column hpCol = columns[0];  // HP
            Dataset.Column accelCol = columns[1];  // Acceleration (m/s^2)

            // HP: Pre (raw) and Post (smoothed via getData)
            if (hpCol != null && rowIndex < hpCol.data.size()) {
                // HP pre (raw from column.data)
                double hpPre = hpCol.data.get(rowIndex);
                row[Column.idx(Column.HP_PRE)] = formatValue(hpPre, "HP");

                // HP post (smoothed via getData)
                if (range != null && sampleInRange >= 0) {
                    double[] hpPostData = currentDataset.getData("HP", range);
                    if (hpPostData != null && sampleInRange < hpPostData.length) {
                        double hpPost = hpPostData[sampleInRange];
                        row[Column.idx(Column.HP_POST)] = formatValue(hpPost, "HP");
                    } else {
                        row[Column.idx(Column.HP_POST)] = "N/A";
                    }
                } else {
                    row[Column.idx(Column.HP_POST)] = "N/A";
                }

                // Δ HP pre (sample-to-sample difference of raw)
                if (rowIndex > 0 && rowIndex < hpCol.data.size()) {
                    double deltaHpPre = hpCol.data.get(rowIndex) - hpCol.data.get(rowIndex - 1);
                    row[Column.idx(Column.DELTA_HP_PRE)] = formatValue(deltaHpPre, "DELTA");
                } else {
                    row[Column.idx(Column.DELTA_HP_PRE)] = "";
                }

                // Δ HP post (sample-to-sample difference of smoothed)
                if (range != null && sampleInRange > 0) {
                    double[] hpPostData = currentDataset.getData("HP", range);
                    if (hpPostData != null && sampleInRange < hpPostData.length) {
                        double deltaHpPost = hpPostData[sampleInRange] - hpPostData[sampleInRange - 1];
                        row[Column.idx(Column.DELTA_HP_POST)] = formatValue(deltaHpPost, "DELTA");
                    } else {
                        row[Column.idx(Column.DELTA_HP_POST)] = "N/A";
                    }
                } else {
                    row[Column.idx(Column.DELTA_HP_POST)] = "";
                }
            } else {
                row[Column.idx(Column.HP_PRE)] = "N/A";
                row[Column.idx(Column.HP_POST)] = "N/A";
                row[Column.idx(Column.DELTA_HP_PRE)] = "N/A";
                row[Column.idx(Column.DELTA_HP_POST)] = "N/A";
            }

            // Acceleration m/s²: Raw and Post (smoothed via getData)
            if (accelCol != null && rowIndex < accelCol.data.size()) {
                // Accel raw (from column.data)
                double accelRaw = accelCol.data.get(rowIndex);
                row[Column.idx(Column.ACCEL_M_S2_RAW)] = formatValue(accelRaw, "ACCEL");

                // Accel post (smoothed via getData)
                if (range != null && sampleInRange >= 0) {
                    double[] accelPostData = currentDataset.getData("Acceleration (m/s^2)", range);
                    if (accelPostData != null && sampleInRange < accelPostData.length) {
                        double accelPost = accelPostData[sampleInRange];
                        row[Column.idx(Column.ACCEL_M_S2_POST)] = formatValue(accelPost, "ACCEL");
                    } else {
                        row[Column.idx(Column.ACCEL_M_S2_POST)] = "N/A";
                    }
                } else {
                    row[Column.idx(Column.ACCEL_M_S2_POST)] = "N/A";
                }

                // Δ Accel raw (sample-to-sample difference of raw)
                if (rowIndex > 0 && rowIndex < accelCol.data.size()) {
                    double deltaAccelRaw = accelCol.data.get(rowIndex) - accelCol.data.get(rowIndex - 1);
                    row[Column.idx(Column.DELTA_ACCEL_RAW)] = formatValue(deltaAccelRaw, "DELTA");
                } else {
                    row[Column.idx(Column.DELTA_ACCEL_RAW)] = "";
                }

                // Δ Accel post (sample-to-sample difference of smoothed)
                if (range != null && sampleInRange > 0) {
                    double[] accelPostData = currentDataset.getData("Acceleration (m/s^2)", range);
                    if (accelPostData != null && sampleInRange < accelPostData.length) {
                        double deltaAccelPost = accelPostData[sampleInRange] - accelPostData[sampleInRange - 1];
                        row[Column.idx(Column.DELTA_ACCEL_POST)] = formatValue(deltaAccelPost, "DELTA");
                    } else {
                        row[Column.idx(Column.DELTA_ACCEL_POST)] = "N/A";
                    }
                } else {
                    row[Column.idx(Column.DELTA_ACCEL_POST)] = "";
                }
            } else {
                row[Column.idx(Column.ACCEL_M_S2_RAW)] = "N/A";
                row[Column.idx(Column.ACCEL_M_S2_POST)] = "N/A";
                row[Column.idx(Column.DELTA_ACCEL_RAW)] = "N/A";
                row[Column.idx(Column.DELTA_ACCEL_POST)] = "N/A";
            }

            // Window size and range size (diagnostic columns)
            if (range != null) {
                int rangeSize = range.end - range.start + 1;
                row[Column.idx(Column.RANGE_SIZE)] = String.valueOf(rangeSize);

                // Get effective window size for HP (the main column we're tracking)
                int[] windowInfo = ((ECUxDataset) currentDataset).getSmoothingWindowInfo("HP", rangeSize);
                if (windowInfo != null) {
                    // Show effective window size (after clamping)
                    row[Column.idx(Column.WINDOW_SIZE)] = String.valueOf(windowInfo[1]);
                } else {
                    row[Column.idx(Column.WINDOW_SIZE)] = "0";
                }
            } else {
                row[Column.idx(Column.WINDOW_SIZE)] = "";
                row[Column.idx(Column.RANGE_SIZE)] = "";
            }

        } catch (Exception e) {
            logger.debug("Error creating row data for index {}: {}", rowIndex, e.getMessage());
        }

        return row;
    }

    private String formatValue(double value, String type) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (type.equals("HP")) {
            return String.format("%.1f", value);
        } else if (type.equals("ACCEL")) {
            return String.format("%.2f", value);
        } else if (type.equals("DELTA")) {
            return String.format("%.2f", value);
        }
        return String.format("%.2f", value);
    }

    private Dataset.Range findRangeForIndex(ArrayList<Dataset.Range> ranges, int index) {
        if (ranges == null) {
            return null;
        }
        for (Dataset.Range range : ranges) {
            if (index >= range.start && index <= range.end) {
                return range;
            }
        }
        return null;
    }

    /**
     * Apply smoothing changes and rebuild the plot
     * @throws Exception if any error occurs during the process
     */
    private void applySmoothingChanges() throws Exception {
        // Process smoothing changes from UI controls
        processSmoothingChanges();

        // Window is already set as top by the button action listener

        try {
            if (this.eplot != null) {
                this.eplot.rebuild(() -> {
                    // Refresh the SmoothingWindow table AFTER rebuild completes
                    refreshTable();
                }, this);
            }
        } finally {
            // Clear top status after rebuild completes
            clearTopWindow();
        }
    }

    /**
     * Process smoothing changes from UI controls
     */
    private void processSmoothingChanges() {
        // Update strategy and padding from combos
        if (currentDataset != null && eplot != null) {
            Strategy newStrategy = (Strategy) smoothingStrategyCombo.getSelectedItem();
            for (ECUxDataset dataset : fileDatasets.values()) {
                dataset.postDiffSmoothingStrategy = newStrategy;
                dataset.padding = new Smoothing.PaddingConfig(
                    (Padding) leftPaddingCombo.getSelectedItem(),
                    (Padding) rightPaddingCombo.getSelectedItem()
                );
            }
        }

        // Update HPMAW and ZeitMAW from text fields
        if (filter != null) {
            try {
                double hpMawValue = Double.parseDouble(HPMAW.getText());
                filter.HPMAW(hpMawValue);
            } catch (NumberFormatException ex) {
                // Invalid input, restore previous value
                HPMAW.setText(String.valueOf(filter.HPMAW()));
            }

            try {
                double zeitMawValue = Double.parseDouble(ZeitMAW.getText());
                filter.ZeitMAW(zeitMawValue);
            } catch (NumberFormatException ex) {
                // Invalid input, restore previous value
                ZeitMAW.setText(String.valueOf(filter.ZeitMAW()));
            }
        }
    }

    /**
     * Restore defaults and apply changes
     * Restores strategy, padding, HPMAW and ZeitMAW to their default values
     * @throws Exception if any error occurs during the process
     */
    private void restoreDefaultsAndApply() throws Exception {
        // Defaults: Strategy=MAW, Padding=DATA/DATA, HPMAW=1.5, ZeitMAW=1.5
        final Strategy defaultStrategy = Strategy.MAW;
        final Smoothing.PaddingConfig defaultPadding = Smoothing.PaddingConfig.forStrategy(defaultStrategy);

        // Restore HPMAW and ZeitMAW to defaults
        if (filter != null) {
            filter.HPMAW(1.5); // defaultHPMAW
            filter.ZeitMAW(1.5); // defaultZeitMAW
        }

        // Update UI controls to show default values
        smoothingStrategyCombo.setSelectedItem(defaultStrategy);
        leftPaddingCombo.setSelectedItem(defaultPadding.left);
        rightPaddingCombo.setSelectedItem(defaultPadding.right);
        if (filter != null) {
            HPMAW.setText(String.valueOf(filter.HPMAW()));
            ZeitMAW.setText(String.valueOf(filter.ZeitMAW()));
        }

        // Apply defaults to all datasets
        if (currentDataset != null && eplot != null) {
            for (ECUxDataset dataset : fileDatasets.values()) {
                dataset.postDiffSmoothingStrategy = defaultStrategy;
                dataset.padding = defaultPadding;
            }
        }

        // Process the smoothing changes (same as Apply button)
        processSmoothingChanges();

        if (this.eplot != null) {
            this.eplot.rebuild(() -> {
                // Refresh the SmoothingWindow table AFTER the main plot rebuild
                refreshTable();
            }, this);
        }
    }

    private void copyTableData() {
        int[] selectedRows = dataTable.getSelectedRows();
        if (selectedRows.length == 0) {
            selectedRows = new int[dataTable.getRowCount()];
            for (int i = 0; i < selectedRows.length; i++) {
                selectedRows[i] = i;
            }
        }

        StringBuilder sb = new StringBuilder();

        // Copy headers
        for (int col = 0; col < tableModel.getColumnCount(); col++) {
            if (col > 0) sb.append("\t");
            sb.append(tableModel.getColumnName(col));
        }
        sb.append("\n");

        // Copy data
        for (int row : selectedRows) {
            for (int col = 0; col < tableModel.getColumnCount(); col++) {
                if (col > 0) sb.append("\t");
                Object value = tableModel.getValueAt(row, col);
                sb.append(value != null ? value.toString() : "");
            }
            sb.append("\n");
        }

        StringSelection selection = new StringSelection(sb.toString());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, null);
    }

    // Range color renderer with problematic value highlighting
    private class RangeColorRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        private static final int BOUNDARY_SAMPLES = 5; // Highlight first/last N samples of range

        /**
         * Check if this cell is part of a sign reversal pattern.
         * Detects patterns: +-+, -+-, +--+, -++-
         * Returns true if values show an abrupt sign reversal.
         */
        private boolean hasSignReversal(DefaultTableModel model, int row, int column) {
            try {
                // Get current value
                Object currentObj = model.getValueAt(row, column);
                if (currentObj == null || currentObj.toString().equals("N/A") ||
                    currentObj.toString().equals("NaN") || currentObj.toString().isEmpty()) {
                    return false;
                }
                double current = Double.parseDouble(currentObj.toString());

                // Check for 3-value reversal patterns: +-+ or -+-
                if (row > 0 && row < model.getRowCount() - 1) {
                    Object prevObj = model.getValueAt(row - 1, column);
                    Object nextObj = model.getValueAt(row + 1, column);

                    if (prevObj != null && nextObj != null &&
                        !prevObj.toString().equals("N/A") && !nextObj.toString().equals("N/A") &&
                        !prevObj.toString().equals("NaN") && !nextObj.toString().equals("NaN") &&
                        !prevObj.toString().isEmpty() && !nextObj.toString().isEmpty()) {

                        double prev = Double.parseDouble(prevObj.toString());
                        double next = Double.parseDouble(nextObj.toString());

                        // Pattern: + to - to + (positive, negative, positive)
                        if (prev > 0 && current < 0 && next > 0) {
                            return true;
                        }
                        // Pattern: - to + to - (negative, positive, negative)
                        if (prev < 0 && current > 0 && next < 0) {
                            return true;
                        }
                    }
                }

                // Check for 4-value reversal patterns: +--+ or -++-
                // Pattern +--+: positive, negative, negative, positive
                if (row > 0 && row < model.getRowCount() - 2) {
                    Object prevObj = model.getValueAt(row - 1, column);
                    Object nextObj = model.getValueAt(row + 1, column);
                    Object next2Obj = model.getValueAt(row + 2, column);

                    if (prevObj != null && nextObj != null && next2Obj != null &&
                        !prevObj.toString().equals("N/A") && !nextObj.toString().equals("N/A") &&
                        !next2Obj.toString().equals("N/A") &&
                        !prevObj.toString().equals("NaN") && !nextObj.toString().equals("NaN") &&
                        !next2Obj.toString().equals("NaN") &&
                        !prevObj.toString().isEmpty() && !nextObj.toString().isEmpty() &&
                        !next2Obj.toString().isEmpty()) {

                        double prev = Double.parseDouble(prevObj.toString());
                        double next = Double.parseDouble(nextObj.toString());
                        double next2 = Double.parseDouble(next2Obj.toString());

                        // Pattern: + to - to - to + (positive, negative, negative, positive)
                        if (prev > 0 && current < 0 && next < 0 && next2 > 0) {
                            return true;
                        }
                        // Pattern: - to + to + to - (negative, positive, positive, negative)
                        if (prev < 0 && current > 0 && next > 0 && next2 < 0) {
                            return true;
                        }
                    }
                }

                // Check for 4-value reversal patterns from previous row perspective: +--+ or -++-
                // Pattern +--+: positive, negative, negative, positive (checking from row-1)
                if (row > 1 && row < model.getRowCount() - 1) {
                    Object prev2Obj = model.getValueAt(row - 2, column);
                    Object prevObj = model.getValueAt(row - 1, column);
                    Object nextObj = model.getValueAt(row + 1, column);

                    if (prev2Obj != null && prevObj != null && nextObj != null &&
                        !prev2Obj.toString().equals("N/A") && !prevObj.toString().equals("N/A") &&
                        !nextObj.toString().equals("N/A") &&
                        !prev2Obj.toString().equals("NaN") && !prevObj.toString().equals("NaN") &&
                        !nextObj.toString().equals("NaN") &&
                        !prev2Obj.toString().isEmpty() && !prevObj.toString().isEmpty() &&
                        !nextObj.toString().isEmpty()) {

                        double prev2 = Double.parseDouble(prev2Obj.toString());
                        double prev = Double.parseDouble(prevObj.toString());
                        double next = Double.parseDouble(nextObj.toString());

                        // Pattern: + to - to - to + (positive, negative, negative, positive)
                        if (prev2 > 0 && prev < 0 && current < 0 && next > 0) {
                            return true;
                        }
                        // Pattern: - to + to + to - (negative, positive, positive, negative)
                        if (prev2 < 0 && prev > 0 && current > 0 && next < 0) {
                            return true;
                        }
                    }
                }
            } catch (NumberFormatException e) {
                // Not a number, ignore
            }
            return false;
        }

        /**
         * Check if any derivative/difference column in this row has a sign reversal.
         * Used to highlight sample/sample[range] columns when the row contains problematic data.
         */
        private boolean hasAnySignReversalInRow(DefaultTableModel model, int row) {
            int columnCount = model.getColumnCount();
            for (int col = 0; col < columnCount; col++) {
                String colName = model.getColumnName(col);
                // Only check delta columns (skip sample columns themselves)
                if (colName.contains("Δ") &&
                    !colName.equals("sample") && !colName.equals("sample[range]")) {
                    if (hasSignReversal(model, row, col)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Check if any raw data (pre/raw) derivative/difference column in this row has a sign reversal.
         * Used to determine if sample/sample[range] columns should be yellow (raw) or red (post).
         */
        private boolean hasAnySignReversalInRawData(DefaultTableModel model, int row) {
            int columnCount = model.getColumnCount();
            for (int col = 0; col < columnCount; col++) {
                String colName = model.getColumnName(col);
                // Only check raw data delta columns (pre or raw)
                if (colName.contains("Δ") &&
                    (colName.contains("(pre)") || colName.contains("(raw)"))) {
                    if (hasSignReversal(model, row, col)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (isSelected) {
                return c; // Use default selection color
            }

            if (rowIndexMapping == null || row >= rowIndexMapping.size()) {
                c.setBackground(Color.WHITE);
                return c;
            }

            // Ensure currentRanges is initialized
            if (currentRanges == null) {
                if (currentDataset != null) {
                    currentRanges = currentDataset.getRanges();
                    if (currentRanges == null) {
                        currentRanges = new ArrayList<>();
                    }
                } else {
                    c.setBackground(Color.WHITE);
                    return c;
                }
            }

            int dataIndex = rowIndexMapping.get(row);

            // For display purposes, if ranges is empty, create a single range covering all data
            // This ensures background coloring works even when no filter ranges exist
            ArrayList<Dataset.Range> displayRanges = currentRanges;
            if (displayRanges.isEmpty() && currentDataset != null && currentDataset.length() > 0) {
                displayRanges = new ArrayList<>();
                displayRanges.add(currentDataset.new Range(0, currentDataset.length() - 1));
            }

            Dataset.Range range = findRangeForIndex(displayRanges, dataIndex);

            // Base background color by range - make more visible
            Color baseColor = Color.WHITE;
            if (range != null) {
                int rangeIndex = displayRanges.indexOf(range);
                if (rangeIndex % 2 == 0) {
                    baseColor = new Color(230, 230, 255); // More visible light blue
                } else {
                    baseColor = new Color(255, 255, 230); // More visible light yellow
                }
            }

            // Check if this is a boundary sample
            boolean isBoundary = false;
            if (range != null) {
                int samplesFromStart = dataIndex - range.start;
                int samplesFromEnd = range.end - dataIndex;
                isBoundary = (samplesFromStart < BOUNDARY_SAMPLES) || (samplesFromEnd < BOUNDARY_SAMPLES);
            }

            // Check for problematic sign reversals in derivative/difference columns
            boolean isProblematic = false;
            boolean isRawData = false;
            String columnName = tableModel.getColumnName(column);

            // Check if this is a sample/sample[range] column
            boolean isSampleColumn = columnName.equals("sample") || columnName.equals("sample[range]");

            if (isSampleColumn) {
                // For sample columns, check if any other column in this row has a sign reversal
                isProblematic = hasAnySignReversalInRow(tableModel, row);
                // For sample columns, check if any raw data column has a sign reversal
                isRawData = hasAnySignReversalInRawData(tableModel, row);
            } else {
                // Only check delta columns for sign reversals
                if (columnName.contains("Δ")) {
                    isProblematic = hasSignReversal(tableModel, row, column);
                    // Check if this is a raw data column (pre or raw)
                    isRawData = columnName.contains("(pre)") || columnName.contains("(raw)");
                }
            }

            // Debug: log first few problematic cells
            if (isProblematic && row < 3) {
                logger.debug("Problematic cell: row={}, col={}, columnName={}, value={}",
                    row, column, columnName, tableModel.getValueAt(row, column));
            }

            // Apply color coding: problematic > boundary > range
            if (c instanceof JComponent) {
                ((JComponent) c).setOpaque(true);
            }

            if (isProblematic) {
                if (isRawData) {
                    // Yellow for sign reversals in raw data
                    c.setBackground(new Color(255, 255, 150)); // Bright yellow
                } else {
                    // Bright red for sign reversals in smoothed/post data - very visible
                    c.setBackground(new Color(255, 150, 150)); // Bright red
                }
                c.setForeground(Color.BLACK);
            } else if (isBoundary) {
                // Orange/yellow tint for boundary samples - very visible
                if (baseColor.equals(Color.WHITE)) {
                    c.setBackground(new Color(255, 240, 200)); // Light orange for white background
                } else {
                    // Shift base color toward orange
                    Color boundaryColor = new Color(
                        Math.min(255, baseColor.getRed() + 60),
                        Math.min(255, baseColor.getGreen() + 40),
                        Math.max(0, baseColor.getBlue() - 30)
                    );
                    c.setBackground(boundaryColor);
                }
                c.setForeground(Color.BLACK);
            } else {
                c.setBackground(baseColor);
                c.setForeground(Color.BLACK);
            }

            return c;
        }
    }

    private java.awt.Dimension windowSize() {
        int width = ECUxPlot.getPreferences().getInt("SmoothingWindowWidth", 1000);
        int height = ECUxPlot.getPreferences().getInt("SmoothingWindowHeight", 450);
        return new java.awt.Dimension(width, height);
    }

    private void putWindowSize() {
        int originalWidth = this.getWidth();
        int originalHeight = this.getHeight();

        // Validate window size before saving to prevent unreasonable sizes
        int width = Math.max(originalWidth, 900);  // Minimum width
        int height = Math.max(originalHeight, 300); // Minimum height

        // Also set reasonable maximums to prevent extremely large windows
        width = Math.min(width, 1000);
        height = Math.min(height, 500);

        ECUxPlot.getPreferences().putInt("SmoothingWindowWidth", width);
        ECUxPlot.getPreferences().putInt("SmoothingWindowHeight", height);
    }
}

// vim: set sw=4 ts=8 expandtab:
