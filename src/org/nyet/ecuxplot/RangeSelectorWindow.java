package org.nyet.ecuxplot;

import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.tree.*;
import javax.swing.ToolTipManager;

import org.nyet.logfile.Dataset;
import org.nyet.util.FileDropListener;
import org.nyet.util.FileDropHost;

/**
 * Range Selector Window - Provides a browser-like interface for selecting
 * which files and ranges to display in the main chart.
 *
 * Replaces the previous "Next Range", "Previous Range", and "Show all ranges" controls
 * with a comprehensive file and range selection system.
 */
public class RangeSelectorWindow extends JFrame implements FileDropHost {
    private static final long serialVersionUID = 1L;

    // Core components
    private final Filter filter;
    private ECUxPlot eplot;
    private TreeMap<String, ECUxDataset> fileDatasets;
    private FATSDataset fatsDataset;

    // Track overall best ranges across all files
    private String bestPowerFilename;
    private int bestPowerRangeIndex;
    private String bestFATSFilename;
    private int bestFATSRangeIndex;

    // Emoji constants for awards
    private static final String AWARD_OF = "🏆"; // Overall FATS
    private static final String AWARD_GF = "🥇"; // Group FATS (also used for best FATS in file)
    private static final String AWARD_OP = "⚡"; // Overall Power
    private static final String AWARD_GP = "⭐"; // Group Power (also used for best power in file)
    private static final String AWARD_POOR = "⚠";  // Poor quality

    // Award description constants
    private static final String DESC_OF = "Best FATS overall";
    private static final String DESC_GF = "Best FATS in group";
    private static final String DESC_GFF = "Best FATS in file";
    private static final String DESC_OP = "Best power overall";
    private static final String DESC_GP = "Best power in group";
    private static final String DESC_GPF = "Best power in file";
    private static final String DESC_POOR = "Incomplete or poor quality";


    /**
     * Append an award icon to the tooltip if condition is true.
     * Handles the "Icons:" section header creation lazily.
     * @param tooltip The StringBuilder to append to
     * @param condition Whether to append the icon
     * @param icon The award icon (emoji)
     * @param description The award description
     * @param hasIcons Current state - true if icons section already started
     * @return New hasIcons state
     */
    private static boolean appendAwardIconIf(StringBuilder tooltip, boolean condition, String icon, String description, boolean hasIcons) {
        if (condition) {
            if (!hasIcons) {
                tooltip.append("<br><b>Icons:</b><br>");
            }
            tooltip.append(icon).append(" ").append(description).append("<br>");
            return true;
        }
        return hasIcons;
    }

    /**
     * Utility class for power analysis - shared by FileNode and RangeNode
     */
    private static class PowerAnalysis {
        static double getMaxPowerInRange(ECUxDataset dataset, Dataset.Range range) {
            try {
                Dataset.Column whpCol = dataset.get("WHP");
                if (whpCol == null) whpCol = dataset.get("HP");
                if (whpCol == null) whpCol = dataset.get("Engine HP");

                if (whpCol != null && range.start < whpCol.data.size() && range.end < whpCol.data.size()) {
                    double max = 0;
                    for (int i = range.start; i <= range.end; i++) {
                        if (i < whpCol.data.size()) {
                            max = Math.max(max, whpCol.data.get(i));
                        }
                    }
                    return max;
                }
            } catch (Exception e) {
                // Ignore errors
            }
            return 0;
        }

        static int getMaxPowerRPMInRange(ECUxDataset dataset, Dataset.Range range) {
            try {
                Dataset.Column whpCol = dataset.get("WHP");
                if (whpCol == null) whpCol = dataset.get("HP");
                if (whpCol == null) whpCol = dataset.get("Engine HP");
                Dataset.Column rpmCol = dataset.get("RPM");

                if (whpCol != null && rpmCol != null &&
                    range.start < whpCol.data.size() && range.end < whpCol.data.size() &&
                    range.start < rpmCol.data.size() && range.end < rpmCol.data.size()) {

                    double maxPower = 0;
                    int maxRPM = 0;
                    for (int i = range.start; i <= range.end; i++) {
                        if (i < whpCol.data.size() && i < rpmCol.data.size()) {
                            double power = whpCol.data.get(i);
                            if (power > maxPower) {
                                maxPower = power;
                                maxRPM = (int) rpmCol.data.get(i);
                            }
                        }
                    }
                    return maxRPM;
                }
            } catch (Exception e) {
                // Ignore errors
            }
            return 0;
        }
    }

    /**
     * Get all award icons for a file based on its analysis.
     * Returns a string with all applicable award icons.
     */
    private static String getFileAwardIcons(FileAnalysis analysis) {
        StringBuilder icons = new StringBuilder();
        for (FileAward award : getFileAwards(analysis)) {
            icons.append(award.icon);
        }
        return icons.toString();
    }

    /**
     * Get tooltip text for all awards on a file.
     */
    private static String getFileAwardTooltip(FileAnalysis analysis) {
        StringBuilder tooltip = new StringBuilder();
        for (FileAward award : getFileAwards(analysis)) {
            tooltip.append(award.icon).append(" ").append(award.description);
            if (award.isFileLevel) {
                tooltip.append(" (file)");
            }
            tooltip.append("<br>");
        }
        return tooltip.toString();
    }

    // UI Components
    private JTree rangeTree;
    private DefaultTreeModel treeModel;
    private JLabel statusLabel;
    private JLabel rangeCountLabel;
    private JButton applyButton;
    private JButton selectAllButton;
    private JButton selectNoneButton;
    private JButton okButton;
    private JButton cancelButton;


    /**
     * Base class for tree nodes
     */
    private static abstract class TreeNode extends DefaultMutableTreeNode {
        private boolean selected = true; // Default to selected

        public boolean isSelected() { return selected; }
        public void setSelected(boolean selected) { this.selected = selected; }

        public abstract String getFilename();
        public abstract int getRangeIndex();
    }

    /**
     * Tree node representing a file
     */
    private static class FileNode extends TreeNode {
        private final String filename;
        private final ECUxDataset dataset;
        private final RangeSelectorWindow window;
        private FileAnalysis cachedAnalysis = null;

        public FileNode(String filename, ECUxDataset dataset, RangeSelectorWindow window) {
            this.filename = filename;
            this.dataset = dataset;
            this.window = window;
        }

        @Override
        public String getFilename() { return filename; }

        @Override
        public int getRangeIndex() { return -1; } // Files don't have range indices

        public ECUxDataset getDataset() { return dataset; }

        @Override
        public String toString() {
            List<Dataset.Range> ranges = dataset.getRanges();
            String baseText;

            if (ranges.size() == 1) {
                // Add range summary for single-range files
                Dataset.Range range = ranges.get(0);
                baseText = String.format("%s (%d points", filename, range.size());

                // Try to add FATS time if available from fatsDataset
                double fatsTime = -1;
                if (window != null && window.fatsDataset != null) {
                    fatsTime = window.fatsDataset.getFATSValue(filename, 0);
                }

                if (fatsTime > 0) {
                    baseText += String.format(", %.2fs FATS", fatsTime);
                }

                baseText += ")";
            } else {
                baseText = filename;
            }

            // Add file-level performance icons at the end with no characters after
            // Note: On macOS, having non-emoji characters AFTER emojis causes rendering issues
            // Emojis AFTER text works fine, but emojis followed by non-emoji characters doesn't
            String icons = getFileIcons();
            return baseText + icons;
        }

        /**
         * Get performance icons for this file based on analysis
         * @return String containing relevant performance icons
         */
        private String getFileIcons() {
            try {
                FileAnalysis analysis = analyzeFile();
                return getFileAwardIcons(analysis);
            } catch (Exception e) {
                return AWARD_POOR; // If analysis fails
            }
        }

        /**
         * Analyze this file's performance metrics across all ranges
         * @return FileAnalysis object with file-level performance indicators
         */
        public FileAnalysis analyzeFile() {
            if (cachedAnalysis != null) {
                return cachedAnalysis;
            }

            FileAnalysis analysis = new FileAnalysis();

            try {
                List<Dataset.Range> ranges = dataset.getRanges();
                analysis.rangeCount = ranges.size();

                // Analyze all ranges in this file
                double maxPower = 0;
                int maxPowerRPM = 0;
                double bestFATS = -1;
                int totalPoints = 0;

                for (int i = 0; i < ranges.size(); i++) {
                    Dataset.Range range = ranges.get(i);
                    totalPoints += range.size();

                    // Get max power for this range using shared utility
                    double rangeMaxPower = PowerAnalysis.getMaxPowerInRange(dataset, range);
                    if (rangeMaxPower > maxPower) {
                        maxPower = rangeMaxPower;
                        maxPowerRPM = PowerAnalysis.getMaxPowerRPMInRange(dataset, range);
                    }

                    // Get FATS time for this range - use pre-calculated value for consistency
                    if (window != null && window.fatsDataset != null) {
                        double fatsTime = window.fatsDataset.getFATSValue(filename, i);
                        if (fatsTime > 0 && (bestFATS < 0 || fatsTime < bestFATS)) {
                            bestFATS = fatsTime;
                        }
                    }
                }

                analysis.maxPower = maxPower;
                analysis.maxPowerRPM = maxPowerRPM;
                analysis.bestFATSTime = bestFATS;
                analysis.totalDataPoints = totalPoints;

            } catch (Exception e) {
                // Analysis failed
            }

            cachedAnalysis = analysis;
            return analysis;
        }
    }

    /**
     * Tree node representing a range within a file
     */
    private static class RangeNode extends TreeNode {
        private final String filename;
        private final int rangeIndex;
        private final Dataset.Range range;
        private final ECUxDataset dataset;
        private final RangeSelectorWindow window;

        public RangeNode(String filename, int rangeIndex, Dataset.Range range, ECUxDataset dataset, RangeSelectorWindow window) {
            this.filename = filename;
            this.rangeIndex = rangeIndex;
            this.range = range;
            this.dataset = dataset;
            this.window = window;
        }

        @Override
        public String getFilename() { return filename; }

        @Override
        public int getRangeIndex() { return rangeIndex; }

        public Dataset.Range getRange() { return range; }

        private double getFATSTime() {
            if (window != null && window.fatsDataset != null) {
                try {
                    // Use the reliable lookup method that doesn't depend on row indexing
                    return window.fatsDataset.getFATSValue(filename, rangeIndex);
                } catch (Exception e) {
                    // FATS not available
                }
            }
            return -1;
        }

        @Override
        public String toString() {
            // Build base text with point count
            String baseText = String.format("Range %d: %d points", rangeIndex + 1, range.size());

            // Always try to add FATS time if available
            double fatsTime = getFATSTime();
            if (fatsTime > 0) {
                baseText += String.format(", %.2fs FATS", fatsTime);
            }

            // Add performance icons at the end with no characters after
            // Note: On macOS, having non-emoji characters AFTER emojis causes rendering issues
            // Emojis AFTER text works fine, but emojis followed by non-emoji characters doesn't
            String icons = getPerformanceIcons();
            return baseText + icons;
        }

        /**
         * Get performance icons for this range based on analysis
         * @return String containing relevant performance icons
         */
        private String getPerformanceIcons() {
            StringBuilder icons = new StringBuilder();

            try {
                // Analyze this range's performance
                RangeAnalysis analysis = analyzeRange();

                // IMPORTANT: Ranges show OVERALL awards only (🏆 for FATS, 🏁 for power)
                // DO NOT display isBestFATS or isBestPower (in-file awards) on ranges
                // Check if this range is the overall best across all files
                boolean isBestFATSOverall = (window != null && window.bestFATSFilename != null &&
                    window.bestFATSFilename.equals(filename) && window.bestFATSRangeIndex == rangeIndex);
                boolean isBestPowerOverall = (window != null && window.bestPowerFilename != null &&
                    window.bestPowerFilename.equals(filename) && window.bestPowerRangeIndex == rangeIndex);

                // Display order: FATS first, then Power, then file awards, then quality warnings
                if (isBestFATSOverall) icons.append(AWARD_OF);
                else if (analysis.isBestFATSInFile) icons.append(AWARD_GF); // Best FATS in file
                if (isBestPowerOverall) icons.append(AWARD_OP);
                else if (analysis.isBestPowerInFile) icons.append(AWARD_GP); // Best power in file
                if (analysis.isIncomplete) icons.append(AWARD_POOR);

            } catch (Exception e) {
                // If analysis fails, show warning icon
                icons.append(AWARD_POOR);
            }

            return icons.toString();
        }

        /**
         * Analyze this range's performance metrics
         * @return RangeAnalysis object with performance indicators
         */
        public RangeAnalysis analyzeRange() {
            RangeAnalysis analysis = new RangeAnalysis();

            try {
                // Analyze power metrics using shared utility
                analysis.maxPower = PowerAnalysis.getMaxPowerInRange(dataset, range);
                analysis.maxPowerRPM = PowerAnalysis.getMaxPowerRPMInRange(dataset, range);

                // Analyze FATS timing (if available)
                analysis.fatsTime = getFATSTime();

                // Analyze data quality
                analysis.dataPoints = range.size();
                analysis.rpmRange = getRPMRangeInRange();

                // Check if this is the best range in the file
                List<Dataset.Range> allRanges = dataset.getRanges();
                if (allRanges.size() > 1) {
                    double bestPower = 0;
                    double bestFATS = -1;

                    // Find the best power and FATS in this file
                    for (Dataset.Range otherRange : allRanges) {
                        double otherPower = PowerAnalysis.getMaxPowerInRange(dataset, otherRange);
                        if (otherPower > bestPower) {
                            bestPower = otherPower;
                        }
                    }

                    if (window != null && window.fatsDataset != null) {
                        for (int i = 0; i < allRanges.size(); i++) {
                            double otherFATS = window.fatsDataset.getFATSValue(filename, i);
                            if (otherFATS > 0 && (bestFATS < 0 || otherFATS < bestFATS)) {
                                bestFATS = otherFATS;
                            }
                        }
                    }

                    // Check if this range is best power in file
                    analysis.isBestPowerInFile = (analysis.maxPower == bestPower);

                    // Check if this range is best FATS in file (exact match, not tolerance-based)
                    if (bestFATS > 0 && analysis.fatsTime > 0) {
                        // Use exact match (not tolerance) since FATS times are calculated consistently
                        // Only one range should match the best FATS time exactly
                        analysis.isBestFATSInFile = (analysis.fatsTime == bestFATS);
                    }
                }

                // Set quality indicators
                analysis.isIncomplete = analysis.dataPoints < 50 || analysis.rpmRange < 1000; // Poor quality

            } catch (Exception e) {
                analysis.isIncomplete = true;
            }

            return analysis;
        }

        /**
         * Get RPM range span in this range
         */
        private double getRPMRangeInRange() {
            try {
                Dataset.Column rpmCol = dataset.get("RPM");
                if (rpmCol != null && range.start < rpmCol.data.size() && range.end < rpmCol.data.size()) {
                    double startRPM = rpmCol.data.get(range.start);
                    double endRPM = rpmCol.data.get(range.end);
                    return Math.abs(endRPM - startRPM);
                }
            } catch (Exception e) {
                // Ignore errors
            }
            return 0;
        }
    }

    /**
     * Data class to hold range analysis results
     */
    private static class RangeAnalysis {
        // Performance metrics
        double maxPower = 0;
        int maxPowerRPM = 0;
        double fatsTime = -1;
        int dataPoints = 0;
        double rpmRange = 0;

        // Quality indicators
        boolean isIncomplete = false;
        boolean isBestPowerInFile = false;  // Best power range within this file
        boolean isBestFATSInFile = false;   // Best FATS range within this file
    }

    /**
     * Data class to hold file analysis results
     */
    private static class FileAnalysis {
        // Performance metrics
        double maxPower = 0;
        int maxPowerRPM = 0;
        double bestFATSTime = -1;
        int totalDataPoints = 0;
        int rangeCount = 0;

        // Quality indicators
        boolean isBestPowerFile = false;  // Best overall across all files
        boolean isBestFATSFile = false;  // Best overall across all files
        boolean isBestPowerInGroup = false;  // Best within the group
        boolean isBestFATSInGroup = false;  // Best within the group
    }

    /**
     * Represents a single award for a file
     */
    private static class FileAward {
        final String icon;
        final String description;
        final boolean isFileLevel; // Shows "(file)" suffix in tooltip

        FileAward(String icon, String description, boolean isFileLevel) {
            this.icon = icon;
            this.description = description;
            this.isFileLevel = isFileLevel;
        }
    }

    /**
     * SINGLE SOURCE OF TRUTH: Determine which awards apply to this file
     * @return List of awards that apply, in display order
     *
     * Display order rule: FATS awards first, then Power awards
     * This ensures consistent emoji ordering across groups, files, and ranges
     */
    private static List<FileAward> getFileAwards(FileAnalysis analysis) {
        List<FileAward> awards = new ArrayList<>();

        // FATS awards come first
        if (analysis.isBestFATSFile) {
            awards.add(new FileAward(AWARD_OF, DESC_OF, true));
        } else if (analysis.isBestFATSInGroup) {
            awards.add(new FileAward(AWARD_GF, DESC_GF, false));
        }

        // Power awards come second
        if (analysis.isBestPowerFile) {
            awards.add(new FileAward(AWARD_OP, DESC_OP, true));
        } else if (analysis.isBestPowerInGroup) {
            awards.add(new FileAward(AWARD_GP, DESC_GP, false));
        }

        return awards;
    }

    /**
     * Tree node representing a file group
     */
    private static class GroupNode extends TreeNode {
        private final String groupName;
        private final List<FileNode> files = new ArrayList<>();

        public GroupNode(String groupName) {
            this.groupName = groupName;
        }

        @Override
        public String getFilename() { return groupName; }

        @Override
        public int getRangeIndex() { return -1; } // Groups don't have range indices

        public void addFile(FileNode file) {
            files.add(file);
            add(file);
        }

        public List<FileNode> getFiles() { return files; }

        @Override
        public String toString() {
            // Add group icons at the end with no characters after
            // Note: On macOS, having non-emoji characters AFTER emojis causes rendering issues
            // Emojis AFTER text works fine, but emojis followed by non-emoji characters doesn't
            String icons = getGroupIcons();
            return groupName + icons;
        }

        /**
         * Get icons for the group based on file performance
         */
        private String getGroupIcons() {
            StringBuilder icons = new StringBuilder();

            try {
                // Analyze group performance
                boolean hasBestPower = false;
                boolean hasBestFATS = false;

                for (FileNode file : files) {
                    FileAnalysis analysis = file.analyzeFile();
                    if (analysis.isBestPowerFile) hasBestPower = true;
                    if (analysis.isBestFATSFile) hasBestFATS = true;
                }

                // Add group-level icons - ALWAYS: FATS first, then Power
                if (hasBestFATS) icons.append(AWARD_OF);
                if (hasBestPower) icons.append(AWARD_OP);

            } catch (Exception e) {
                // Ignore errors
            }

            return icons.toString();
        }
    }

    public RangeSelectorWindow(Filter filter, ECUxPlot eplot) {
        super("Range Selector");
        // Hide window instead of dispose to preserve tree state (selections, expanded nodes, scroll position)
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        this.filter = filter;
        this.eplot = eplot;

        initializeComponents();
        setupLayout();
        updateList();

        // Expand all nodes first, then pack will size to fit
        expandAllNodes();

        // Pack the window to auto-size to fit the expanded tree
        // pack() will automatically enforce minimumSize
        setMinimumSize(new Dimension(500, 400));
        pack();

        setLocationRelativeTo(null);

        // No window size preferences - this window auto-sizes based on content

        // Set OK button as default (so Enter key triggers it)
        this.getRootPane().setDefaultButton(okButton);

        // Request focus on OK button to make it visually clear it's the default
        okButton.requestFocusInWindow();

        // Add file drop support
        new FileDropListener(this, this);
    }

    private void initializeComponents() {
        // Create tree model and tree
        treeModel = new DefaultTreeModel(new DefaultMutableTreeNode("Root"));
        rangeTree = new JTree(treeModel);
        rangeTree.setRootVisible(false);
        rangeTree.setShowsRootHandles(true);
        rangeTree.setCellRenderer(new RangeTreeCellRenderer(this));
        // Disable focus painting to remove the blue outline
        rangeTree.setFocusable(false);
        // Register tree with ToolTipManager to enable renderer-based tooltips
        // This allows JCheckBox tooltips without needing setToolTipText() on the tree
        // NEVER call setToolTipText(""). It produces empty tooltips that render as " "
        ToolTipManager.sharedInstance().registerComponent(rangeTree);

        // Add mouse listener for checkbox clicking
        rangeTree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                TreePath path = rangeTree.getPathForLocation(e.getPoint().x, e.getPoint().y);
                if (path != null) {
                    Object node = path.getLastPathComponent();
                    if (node instanceof TreeNode) {
                        TreeNode treeNode = (TreeNode) node;

                        boolean newSelection = !treeNode.isSelected();
                        treeNode.setSelected(newSelection);

                        // Propagate selection DOWN to all descendants (for any parent node)
                        propagateSelectionToDescendants(treeNode, newSelection);

                        // Propagate selection UP to ancestors based on selection state
                        propagateSelectionToAncestors(treeNode, newSelection);

                        rangeTree.repaint();
                        updateStatus();
                    }
                }
            }
        });


        // Create control buttons
        applyButton = new JButton("Apply");
        applyButton.addActionListener(e -> applySelection());

        selectAllButton = new JButton("Select All");
        selectAllButton.addActionListener(e -> selectAll());

        selectNoneButton = new JButton("Select None");
        selectNoneButton.addActionListener(e -> selectNone());


        okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            applySelection();
            setVisible(false);
        });

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> setVisible(false));

        // Create status labels
        statusLabel = new JLabel("No files loaded");
        rangeCountLabel = new JLabel("0 ranges selected");

        // Set initial button states
        updateButtonStates();
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        // Main panel with outside border padding
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5)); // 5px padding all around

        // Create "Select Ranges" panel with titled border
        JPanel treePanel = new JPanel(new BorderLayout());
        javax.swing.border.TitledBorder treeBorder = BorderFactory.createTitledBorder(null, "Select Ranges",
            javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP);
        treeBorder.setTitleFont(treeBorder.getTitleFont().deriveFont(Font.BOLD));
        treePanel.setBorder(treeBorder);

        // Status panel at top of tree panel
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(rangeCountLabel, BorderLayout.EAST);
        treePanel.add(statusPanel, BorderLayout.NORTH);

        // Tree panel with scroll pane - let pack() size it based on content
        JScrollPane treeScrollPane = new JScrollPane(rangeTree);
        treePanel.add(treeScrollPane, BorderLayout.CENTER);

        mainPanel.add(treePanel, BorderLayout.CENTER);

        // Bottom panel with legend and controls
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // Create "Legend" panel with titled border
        JPanel legendWrapper = new JPanel(new BorderLayout());
        javax.swing.border.TitledBorder legendBorder = BorderFactory.createTitledBorder(null, "Legend",
            javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP);
        legendBorder.setTitleFont(legendBorder.getTitleFont().deriveFont(Font.BOLD));
        legendWrapper.setBorder(legendBorder);

        // Legend panel (compact single line or two lines with better spacing)
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));

        // Add all legend items in one flow layout
        legendPanel.add(new JLabel(AWARD_OF));
        legendPanel.add(new JLabel("Best FATS overall"));
        legendPanel.add(new JLabel("•"));
        legendPanel.add(new JLabel(AWARD_GF));
        legendPanel.add(new JLabel("Best FATS in group"));
        legendPanel.add(new JLabel("•"));
        legendPanel.add(new JLabel(AWARD_OP));
        legendPanel.add(new JLabel("Best power overall"));
        legendPanel.add(new JLabel("•"));
        legendPanel.add(new JLabel(AWARD_GP));
        legendPanel.add(new JLabel("Best power in group"));
        legendPanel.add(new JLabel("•"));
        legendPanel.add(new JLabel(AWARD_POOR));
        legendPanel.add(new JLabel("Incomplete/Poor"));

        legendWrapper.add(legendPanel, BorderLayout.CENTER);
        bottomPanel.add(legendWrapper, BorderLayout.CENTER);

        // Control panel with two-row button layout
        JPanel controlPanel = createButtonPanel();
        bottomPanel.add(controlPanel, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new java.awt.Insets(5, 0, 5, 5); // Top, left, bottom, right padding

        // First row: Select All, then Select None
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1;
        gbc.weightx = 0.0; gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        buttonPanel.add(selectAllButton, gbc);

        gbc.gridx = 1;
        buttonPanel.add(selectNoneButton, gbc);

        // Second row: OK, Apply, Cancel
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        buttonPanel.add(okButton, gbc);

        gbc.gridx = 1;
        buttonPanel.add(applyButton, gbc);

        gbc.gridx = 2;
        buttonPanel.add(cancelButton, gbc);

        // Add a panel wrapper to match FilterWindow styling
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(buttonPanel, BorderLayout.WEST); // Left-align the button panel
        return wrapper;
    }

    /**
     * Custom tree cell renderer with checkboxes and tooltips
     */
    private static class RangeTreeCellRenderer extends JCheckBox implements TreeCellRenderer {
        private final RangeSelectorWindow window;

        public RangeTreeCellRenderer(RangeSelectorWindow window) {
            this.window = window;
        }
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {

            if (value instanceof TreeNode) {
                TreeNode node = (TreeNode) value;
                setSelected(node.isSelected());
                setText(node.toString());

                // Style based on node type
                if (node instanceof GroupNode) {
                    setFont(tree.getFont());
                    setForeground(new Color(0, 100, 0)); // Dark green
                    String tooltip = createGroupTooltip((GroupNode) node);
                    setToolTipText(tooltip != null && !tooltip.trim().isEmpty() ? tooltip : null);
                } else if (node instanceof FileNode) {
                    setFont(tree.getFont());
                    // All files are black
                    setForeground(Color.BLACK);
                    String tooltip = createFileTooltip((FileNode) node);
                    setToolTipText(tooltip != null && !tooltip.trim().isEmpty() ? tooltip : null);
                } else if (node instanceof RangeNode) {
                    setFont(tree.getFont());
                    setForeground(Color.BLUE);
                    String tooltip = createRangeTooltip((RangeNode) node);
                    setToolTipText(tooltip != null && !tooltip.trim().isEmpty() ? tooltip : null);
                }
            } else {
                setText(value.toString());
                setSelected(false);
                setFont(tree.getFont());
                // For non-TreeNode values, set tooltip to null to show no tooltip
                setToolTipText(null);
            }

            return this;
        }

        /**
         * Create detailed tooltip for range nodes
         */
        private String createRangeTooltip(RangeNode rangeNode) {
            try {
                RangeAnalysis analysis = rangeNode.analyzeRange();
                StringBuilder tooltip = new StringBuilder();

                tooltip.append("<html><body>");
                tooltip.append("<b>Range ").append(rangeNode.getRangeIndex() + 1).append("</b><br>");

                // Basic info
                Dataset.Range range = rangeNode.getRange();
                tooltip.append("Points: ").append(range.size()).append("<br>");

                // Performance metrics
                boolean hasPerformance = false;
                if (analysis.maxPower > 0) {
                    tooltip.append("<br><b>Performance:</b><br>");
                    tooltip.append("Max Power: ").append(String.format("%.0f WHP", analysis.maxPower));
                    if (analysis.maxPowerRPM > 0) {
                        tooltip.append(" @ ").append(analysis.maxPowerRPM).append(" RPM");
                    }
                    tooltip.append("<br>");
                    hasPerformance = true;
                }

                if (analysis.fatsTime > 0) {
                    if (!hasPerformance) tooltip.append("<br><b>Performance:</b><br>");
                    tooltip.append("FATS (3000-6000 RPM): ").append(String.format("%.2fs", analysis.fatsTime)).append("<br>");
                    hasPerformance = true;
                }

                // Icon explanations - ranges only show OVERALL awards
                boolean hasIcons = false;
                String rangeFilename = rangeNode.getFilename();
                int rangeIndex = rangeNode.getRangeIndex();
                boolean isBestFATSOverall = (window != null && window.bestFATSFilename != null &&
                    window.bestFATSFilename.equals(rangeFilename) && window.bestFATSRangeIndex == rangeIndex);
                boolean isBestPowerOverall = (window != null && window.bestPowerFilename != null &&
                    window.bestPowerFilename.equals(rangeFilename) && window.bestPowerRangeIndex == rangeIndex);

                hasIcons = appendAwardIconIf(tooltip, isBestFATSOverall, AWARD_OF, DESC_OF, hasIcons);
                if (!isBestFATSOverall) {
                    hasIcons = appendAwardIconIf(tooltip, analysis.isBestFATSInFile, AWARD_GF, DESC_GFF, hasIcons);
                }
                hasIcons = appendAwardIconIf(tooltip, isBestPowerOverall, AWARD_OP, DESC_OP, hasIcons);
                if (!isBestPowerOverall) {
                    hasIcons = appendAwardIconIf(tooltip, analysis.isBestPowerInFile, AWARD_GP, DESC_GPF, hasIcons);
                }
                hasIcons = appendAwardIconIf(tooltip, analysis.isIncomplete, AWARD_POOR, DESC_POOR, hasIcons);

                tooltip.append("</body></html>");
                String result = tooltip.toString();

                // Return null if tooltip is essentially empty (just HTML structure with basic info)
                if (!hasPerformance && !hasIcons) {
                    return null;
                }

                return result;

            } catch (Exception e) {
                return "Range " + (rangeNode.getRangeIndex() + 1) + " - Analysis failed";
            }
        }

        /**
         * Create tooltip for group nodes
         */
        private String createGroupTooltip(GroupNode groupNode) {
            StringBuilder tooltip = new StringBuilder();
            tooltip.append("<html><body>");
            tooltip.append("<b>").append(groupNode.getFilename()).append("</b><br>");

            try {
                List<FileNode> files = groupNode.getFiles();
                tooltip.append("Files: ").append(files.size()).append("<br>");

                // Show group performance summary
                double maxPower = 0;
                double bestFATS = -1;
                int totalRanges = 0;

                // Track which awards this group has
                boolean hasBestPower = false;
                boolean hasBestFATS = false;

                for (FileNode file : files) {
                    FileAnalysis analysis = file.analyzeFile();
                    if (analysis.maxPower > maxPower) {
                        maxPower = analysis.maxPower;
                    }
                    if (analysis.bestFATSTime > 0 && (bestFATS < 0 || analysis.bestFATSTime < bestFATS)) {
                        bestFATS = analysis.bestFATSTime;
                    }
                    totalRanges += analysis.rangeCount;

                    // Check for awards
                    if (analysis.isBestPowerFile) hasBestPower = true;
                    if (analysis.isBestFATSFile) hasBestFATS = true;
                }

                if (maxPower > 0) {
                    tooltip.append("<br><b>Group Performance:</b><br>");
                    tooltip.append("Best Power: ").append(String.format("%.0f WHP", maxPower)).append("<br>");
                }

                if (bestFATS > 0) {
                    tooltip.append("Best FATS: ").append(String.format("%.2fs", bestFATS)).append("<br>");
                }

                tooltip.append("Total Ranges: ").append(totalRanges).append("<br>");

                // Only show icon explanations for icons that are actually present
                if (hasBestPower || hasBestFATS) {
                    tooltip.append("<br><b>Icons:</b><br>");

                    if (hasBestPower) {
                        tooltip.append(AWARD_OP).append(" Contains best power file<br>");
                    }
                    if (hasBestFATS) {
                        tooltip.append(AWARD_OF).append(" Contains best FATS file<br>");
                    }
                }

            } catch (Exception e) {
                tooltip.append("Error analyzing group");
            }

            tooltip.append("</body></html>");
            return tooltip.toString();
        }

        /**
         * Create enhanced tooltip for file nodes
         */
        private String createFileTooltip(FileNode fileNode) {
            StringBuilder tooltip = new StringBuilder();
            tooltip.append("<html><body>");
            tooltip.append("<b>").append(fileNode.getFilename()).append("</b><br>");

            try {
                FileAnalysis analysis = fileNode.analyzeFile();
                List<Dataset.Range> ranges = fileNode.getDataset().getRanges();
                tooltip.append("Ranges: ").append(ranges.size()).append("<br>");
                tooltip.append("Total Points: ").append(analysis.totalDataPoints).append("<br>");

                // Performance metrics
                if (analysis.maxPower > 0) {
                    tooltip.append("<br><b>Performance:</b><br>");
                    tooltip.append("Max Power: ").append(String.format("%.0f WHP", analysis.maxPower));
                    if (analysis.maxPowerRPM > 0) {
                        tooltip.append(" @ ").append(analysis.maxPowerRPM).append(" RPM");
                    }
                    tooltip.append("<br>");
                }

                if (analysis.bestFATSTime > 0) {
                    tooltip.append("Best FATS: ").append(String.format("%.2fs", analysis.bestFATSTime)).append("<br>");
                }

                if (ranges.size() == 1) {
                    // Single range - show summary
                    Dataset.Range range = ranges.get(0);
                    Dataset.Column rpmCol = fileNode.getDataset().get("RPM");
                    if (rpmCol != null && range.start < rpmCol.data.size() && range.end < rpmCol.data.size()) {
                        double startRPM = rpmCol.data.get(range.start);
                        double endRPM = rpmCol.data.get(range.end);
                        tooltip.append("<br>RPM: ").append(String.format("%.0f-%.0f", startRPM, endRPM)).append("<br>");
                    }
                } else {
                    // Multiple ranges - show summary
                    tooltip.append("<br>Click to select/deselect all ranges<br>");
                    tooltip.append("Individual ranges shown below");
                }

                // Icon explanations - use shared function for consistency
                String awardText = getFileAwardTooltip(analysis);
                if (!awardText.isEmpty()) {
                    tooltip.append("<br><b>Icons:</b><br>").append(awardText);
                }

            } catch (Exception e) {
                tooltip.append("Error analyzing file");
            }

            tooltip.append("</body></html>");
            return tooltip.toString();
        }
    }

    /**
     * Update file datasets - only rebuilds tree if files have actually changed
     * This preserves tree state (selections, expanded nodes) when window is shown/hidden
     */
    void setFileDatasets(TreeMap<String, ECUxDataset> fileDatasets) {
        // Only rebuild if files have actually changed
        if (this.fileDatasets == fileDatasets || (fileDatasets != null && this.fileDatasets != null &&
            this.fileDatasets.keySet().equals(fileDatasets.keySet()))) {
            // Files haven't changed, just update reference to preserve tree state
            this.fileDatasets = fileDatasets;
            return;
        }

        this.fileDatasets = fileDatasets;
        updateList();
        updateButtonStates();
    }

    // FileDropHost implementation - delegate to parent ECUxPlot
    @Override
    public void loadFile(File file) {
        if (this.eplot != null) {
            this.eplot.loadFile(file);
        }
    }

    @Override
    public void loadFiles(List<File> fileList) {
        if (this.eplot != null) {
            this.eplot.loadFiles(fileList);
        }
    }

    void setFATSDataset(FATSDataset fatsDataset) {
        this.fatsDataset = fatsDataset;
        // Refresh all nodes to show FATS data
        refreshTreeNodes();
        updateButtonStates();
    }

    /**
     * Refresh all tree nodes to trigger repaint without losing selection
     */
    void refreshTreeNodes() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        Enumeration<?> enumeration = root.depthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            Object node = enumeration.nextElement();
            if (node instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) node;
                treeModel.nodeChanged(treeNode);
            }
        }
    }

    /**
     * Propagate selection state to ancestor nodes based on selection state
     * - If unselected: uncheck all ancestors
     * - If selected: check parent only if ALL siblings are also selected
     */
    private void propagateSelectionToAncestors(TreeNode node, boolean selected) {
        if (!selected) {
            // If this node is unselected, all ancestors must be unselected
            javax.swing.tree.TreeNode parentNode = node.getParent();
            while (parentNode != null && parentNode instanceof TreeNode) {
                TreeNode parent = (TreeNode) parentNode;
                parent.setSelected(false);
                parentNode = parent.getParent();
            }
        } else {
            // If this node is selected, check if all siblings are selected
            javax.swing.tree.TreeNode parentNode = node.getParent();
            while (parentNode != null && parentNode instanceof TreeNode) {
                TreeNode parent = (TreeNode) parentNode;

                // Check if all siblings are selected
                boolean allSiblingsSelected = true;
                Enumeration<?> siblings = parent.children();
                while (siblings.hasMoreElements()) {
                    Object sibling = siblings.nextElement();
                    if (sibling instanceof TreeNode) {
                        TreeNode siblingNode = (TreeNode) sibling;
                        if (!siblingNode.isSelected()) {
                            allSiblingsSelected = false;
                            break;
                        }
                    }
                }

                if (allSiblingsSelected) {
                    parent.setSelected(true);
                    parentNode = parent.getParent();
                } else {
                    // Not all siblings selected, stop propagating
                    break;
                }
            }
        }
    }

    /**
     * Propagate selection state to all descendant nodes
     */
    private void propagateSelectionToDescendants(TreeNode node, boolean selected) {
        Enumeration<?> children = node.children();
        while (children.hasMoreElements()) {
            Object child = children.nextElement();
            if (child instanceof TreeNode) {
                TreeNode childNode = (TreeNode) child;
                childNode.setSelected(selected);
                // Recursively propagate to grandchildren
                propagateSelectionToDescendants(childNode, selected);
            }
        }
    }

    private void updateList() {
        // Clear existing tree
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.removeAllChildren();

        if (fileDatasets == null || fileDatasets.isEmpty()) {
            statusLabel.setText("No files loaded");
            treeModel.reload();
            return;
        }

        // Group files by common patterns
        Map<String, List<FileNode>> groups = groupFilesByPattern();

        // Analyze all files to determine best performers
        analyzeFilePerformance(groups);

        // Build tree structure with groups
        for (Map.Entry<String, List<FileNode>> groupEntry : groups.entrySet()) {
            String groupName = groupEntry.getKey();
            List<FileNode> files = groupEntry.getValue();

            if (files.size() > 1) {
                // Create group node for multiple files
                if (groupName.trim().isEmpty()) {
                    throw new IllegalStateException("Attempting to create GroupNode with empty/whitespace name: '" + groupName + "'");
                }
                GroupNode groupNode = new GroupNode(groupName);
                root.add(groupNode);

                // Add files to group
                for (FileNode fileNode : files) {
                    groupNode.addFile(fileNode);

                    // Add range nodes as children only if file has multiple ranges
                    List<Dataset.Range> ranges = fileNode.getDataset().getRanges();
                    if (ranges.size() > 1) {
                        for (int i = 0; i < ranges.size(); i++) {
                            RangeNode rangeNode = new RangeNode(fileNode.getFilename(), i, ranges.get(i), fileNode.getDataset(), this);
                            fileNode.add(rangeNode);
                        }
                    }
                }
            } else {
                // Single file - add directly to root
                FileNode fileNode = files.get(0);
                root.add(fileNode);

                // Add range nodes as children only if file has multiple ranges
                List<Dataset.Range> ranges = fileNode.getDataset().getRanges();
                if (ranges.size() > 1) {
                    for (int i = 0; i < ranges.size(); i++) {
                        RangeNode rangeNode = new RangeNode(fileNode.getFilename(), i, ranges.get(i), fileNode.getDataset(), this);
                        fileNode.add(rangeNode);
                    }
                }
            }
        }

        treeModel.reload();

        // Automatically expand all nodes
        expandAllNodes();

        // Select all by default if this is the first time the tree is built
        // If window was just hidden, tree state is already preserved
        DefaultMutableTreeNode checkRoot = (DefaultMutableTreeNode) treeModel.getRoot();
        Enumeration<?> nodes = checkRoot.depthFirstEnumeration();
        boolean hasSelection = false;
        while (nodes.hasMoreElements()) {
            Object node = nodes.nextElement();
            if (node instanceof TreeNode && ((TreeNode) node).isSelected()) {
                hasSelection = true;
                break;
            }
        }
        if (!hasSelection) {
            selectAll();
        }

        updateStatus();
    }

    /**
     * Group files by hierarchical prefix analysis
     */
    private Map<String, List<FileNode>> groupFilesByPattern() {
        // First pass: create file nodes
        List<FileNode> allFiles = new ArrayList<>();
        for (Map.Entry<String, ECUxDataset> entry : fileDatasets.entrySet()) {
            ECUxDataset dataset = entry.getValue();
            String fileId = dataset.getFileId();
            if (fileId == null || fileId.trim().isEmpty()) {
                throw new IllegalStateException("File ID is null or whitespace-only: '" + fileId + "'");
            }
            FileNode fileNode = new FileNode(fileId, dataset, this);
            allFiles.add(fileNode);
        }

        // Second pass: create hierarchical groups based on prefixes
        return createHierarchicalGroups(allFiles);
    }

    /**
     * Create hierarchical groups based on common prefixes
     */
    private Map<String, List<FileNode>> createHierarchicalGroups(List<FileNode> allFiles) {
        Map<String, List<FileNode>> groups = new LinkedHashMap<>();

        // Group files by their longest common prefixes
        Map<String, List<FileNode>> prefixGroups = groupByLongestCommonPrefix(allFiles);

        // For each prefix group, create sub-groups if beneficial
        for (Map.Entry<String, List<FileNode>> entry : prefixGroups.entrySet()) {
            String prefix = entry.getKey();
            List<FileNode> files = entry.getValue();

            if (files.size() > 1) {
                // Try to create sub-groups within this prefix
                Map<String, List<FileNode>> subGroups = createSubGroups(prefix, files);

                if (subGroups.size() > 1) {
                    // Multiple sub-groups found - use hierarchical structure
                    for (Map.Entry<String, List<FileNode>> subEntry : subGroups.entrySet()) {
                        String subGroupName = cleanPrefixName(subEntry.getKey());
                        groups.put(subGroupName, subEntry.getValue());
                    }
                } else {
                    // No beneficial sub-groups - use original prefix
                    groups.put(cleanPrefixName(prefix), files);
                }
            } else {
                // Single file - use filename as group
                groups.put(files.get(0).getFilename(), files);
            }
        }

        return groups;
    }

    /**
     * Group files by their longest common prefixes
     */
    private Map<String, List<FileNode>> groupByLongestCommonPrefix(List<FileNode> allFiles) {
        Map<String, List<FileNode>> prefixGroups = new LinkedHashMap<>();
        Set<FileNode> processed = new HashSet<>();

        for (FileNode file : allFiles) {
            if (processed.contains(file)) {
                continue;
            }

            // Find the longest prefix this file shares with others
            String longestPrefix = findLongestCommonPrefix(file.getFilename(), allFiles, processed);

            if (longestPrefix != null && longestPrefix.length() > 3) {
                // Find all files that share this prefix
                List<FileNode> matchingFiles = new ArrayList<>();
                for (FileNode otherFile : allFiles) {
                    if (!processed.contains(otherFile) &&
                        otherFile.getFilename().startsWith(longestPrefix)) {
                        matchingFiles.add(otherFile);
                    }
                }

                if (matchingFiles.size() > 1) {
                    // Clean up the prefix by removing trailing separators
                    String cleanPrefix = cleanPrefixName(longestPrefix);
                    prefixGroups.put(cleanPrefix, matchingFiles);
                    processed.addAll(matchingFiles);
                } else {
                    // Single file - add individually
                    prefixGroups.put(file.getFilename(), Arrays.asList(file));
                    processed.add(file);
                }
            } else {
                // No good prefix found - add individually
                prefixGroups.put(file.getFilename(), Arrays.asList(file));
                processed.add(file);
            }
        }

        return prefixGroups;
    }

    /**
     * Find the longest prefix that this filename shares with other unprocessed files
     */
    private String findLongestCommonPrefix(String filename, List<FileNode> allFiles, Set<FileNode> processed) {
        String longestPrefix = null;
        int maxLength = 0;

        // Try different prefix lengths
        for (int len = 4; len <= filename.length() / 2; len++) {
            String candidatePrefix = filename.substring(0, len);

            // Count how many unprocessed files share this prefix
            int matchCount = 0;
            for (FileNode file : allFiles) {
                if (!processed.contains(file) && file.getFilename().startsWith(candidatePrefix)) {
                    matchCount++;
                }
            }

            // If this prefix matches multiple files and is longer than current best
            if (matchCount > 1 && len > maxLength) {
                // Check if this prefix ends at a logical boundary (underscore, dash, etc.)
                if (isLogicalPrefixBoundary(candidatePrefix)) {
                    longestPrefix = candidatePrefix;
                    maxLength = len;
                }
            }
        }

        return longestPrefix;
    }

    /**
     * Check if a prefix ends at a logical boundary (good place to split)
     */
    private boolean isLogicalPrefixBoundary(String prefix) {
        if (prefix.length() < 3) return false;

        // Check if prefix ends with common separators
        char lastChar = prefix.charAt(prefix.length() - 1);
        return lastChar == '_' || lastChar == '-' || lastChar == ' ';
    }

    /**
     * Create sub-groups within a prefix group
     */
    private Map<String, List<FileNode>> createSubGroups(String prefix, List<FileNode> files) {
        Map<String, List<FileNode>> subGroups = new LinkedHashMap<>();

        // Try to find sub-prefixes within this group
        for (FileNode file : files) {
            String filename = file.getFilename();

            // Look for the next logical grouping after the main prefix
            String remaining = filename.substring(prefix.length());
            String subPrefix = findNextLogicalPrefix(remaining);

            if (subPrefix != null && subPrefix.length() > 2) {
                String fullSubPrefix = prefix + subPrefix;

                // Count files that would be in this sub-group
                int matchCount = 0;
                for (FileNode f : files) {
                    if (f.getFilename().startsWith(fullSubPrefix)) {
                        matchCount++;
                    }
                }

                // Only create sub-group if it has multiple files
                if (matchCount > 1) {
                    subGroups.computeIfAbsent(fullSubPrefix, k -> new ArrayList<>()).add(file);
                }
            }
        }

        // Add files that didn't fit into any sub-group
        for (FileNode file : files) {
            boolean inSubGroup = false;
            for (List<FileNode> subGroup : subGroups.values()) {
                if (subGroup.contains(file)) {
                    inSubGroup = true;
                    break;
                }
            }

            if (!inSubGroup) {
                subGroups.put(file.getFilename(), Arrays.asList(file));
            }
        }

        return subGroups;
    }

    /**
     * Find the next logical prefix in the remaining part of a filename
     */
    private String findNextLogicalPrefix(String remaining) {
        if (remaining.length() < 3) return null;

        // Look for common patterns that indicate a logical grouping
        String[] patterns = {"Stage_", "Run_", "Test_", "Pull_", "E85", "E30", "95_", "91_", "87_"};

        for (String pattern : patterns) {
            if (remaining.startsWith(pattern)) {
                // Find the end of this pattern
                int endIndex = remaining.indexOf('_', pattern.length());
                if (endIndex > 0) {
                    return remaining.substring(0, endIndex + 1);
                } else {
                    return pattern;
                }
            }
        }

        // Look for the next underscore or dash
        int nextUnderscore = remaining.indexOf('_');
        int nextDash = remaining.indexOf('-');

        int endIndex = Math.min(
            nextUnderscore > 0 ? nextUnderscore + 1 : remaining.length(),
            nextDash > 0 ? nextDash + 1 : remaining.length()
        );

        if (endIndex > 2 && endIndex < remaining.length()) {
            return remaining.substring(0, endIndex);
        }

        return null;
    }

    /**
     * Clean up prefix names by removing trailing separators
     */
    private String cleanPrefixName(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return "";  // Return empty for null or whitespace-only
        }
        if (prefix.length() < 2) {
            return prefix;
        }

        // Remove trailing separators
        String cleaned = prefix;
        while (cleaned.length() > 0 &&
               (cleaned.endsWith("_") || cleaned.endsWith("-") || cleaned.endsWith(" "))) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        // Ensure we don't return an empty string
        return cleaned.length() > 0 ? cleaned : prefix;
    }

    /**
     * Analyze file performance to determine best performers at all levels
     * - Overall best across all files
     * - Best within each group
     */
    private void analyzeFilePerformance(Map<String, List<FileNode>> groups) {
        // Collect all files for overall comparison
        List<FileNode> allFiles = new ArrayList<>();
        for (List<FileNode> fileList : groups.values()) {
            allFiles.addAll(fileList);
        }

        // Find overall best performers - both files AND the specific range
        double bestPower = 0;
        double bestFATS = -1;
        FileNode bestPowerFile = null;
        FileNode bestFATSFile = null;
        int bestPowerRangeIdx = -1;
        int bestFATSRangeIdx = -1;

        for (FileNode file : allFiles) {
            ECUxDataset dataset = fileDatasets.get(file.filename);
            if (dataset == null) continue;

            List<Dataset.Range> ranges = dataset.getRanges();
            for (int i = 0; i < ranges.size(); i++) {
                Dataset.Range range = ranges.get(i);

                // Check power
                double rangePower = PowerAnalysis.getMaxPowerInRange(dataset, range);
                if (rangePower > bestPower) {
                    bestPower = rangePower;
                    bestPowerFile = file;
                    bestPowerRangeIdx = i;
                }

                // Check FATS
                double rangeFATS = fatsDataset != null ?
                    fatsDataset.getFATSValue(file.filename, i) : -1;
                if (rangeFATS > 0 && (bestFATS < 0 || rangeFATS < bestFATS)) {
                    bestFATS = rangeFATS;
                    bestFATSFile = file;
                    bestFATSRangeIdx = i;
                }
            }
        }

        // Store best range info
        this.bestPowerFilename = bestPowerFile != null ? bestPowerFile.filename : null;
        this.bestPowerRangeIndex = bestPowerRangeIdx;
        this.bestFATSFilename = bestFATSFile != null ? bestFATSFile.filename : null;
        this.bestFATSRangeIndex = bestFATSRangeIdx;

        // Mark overall best performers
        for (FileNode file : allFiles) {
            FileAnalysis analysis = file.analyzeFile();
            analysis.isBestPowerFile = (analysis.maxPower > 0 && Math.abs(analysis.maxPower - bestPower) < 0.1);

            // For FATS, check if this file contains the best FATS
            analysis.isBestFATSFile = false;
            if (bestFATSFile != null && bestFATSFile.filename.equals(file.filename) &&
                analysis.bestFATSTime > 0 && Math.abs(analysis.bestFATSTime - bestFATS) < 0.01) {
                analysis.isBestFATSFile = true;
            }
        }

        // Find best within each group
        for (Map.Entry<String, List<FileNode>> entry : groups.entrySet()) {
            List<FileNode> groupFiles = entry.getValue();
            if (groupFiles.size() > 1) {
                double groupBestPower = 0;
                double groupBestFATS = -1;

                for (FileNode file : groupFiles) {
                    FileAnalysis analysis = file.analyzeFile();
                    if (analysis.maxPower > groupBestPower) {
                        groupBestPower = analysis.maxPower;
                    }
                    // Use fatsDataset for consistency
                    ECUxDataset dataset = fileDatasets.get(file.filename);
                    if (dataset != null && fatsDataset != null) {
                        List<Dataset.Range> ranges = dataset.getRanges();
                        for (int i = 0; i < ranges.size(); i++) {
                            double rangeFATS = fatsDataset.getFATSValue(file.filename, i);
                            if (rangeFATS > 0 && (groupBestFATS < 0 || rangeFATS < groupBestFATS)) {
                                groupBestFATS = rangeFATS;
                            }
                        }
                    }
                }

                // Find which file has the best FATS in this group
                FileNode groupBestFATSFile = null;
                for (FileNode file : groupFiles) {
                    FileAnalysis analysis = file.analyzeFile();
                    if (analysis.bestFATSTime > 0 && Math.abs(analysis.bestFATSTime - groupBestFATS) < 0.01) {
                        groupBestFATSFile = file;
                        break;
                    }
                }

                // Mark best within group
                for (FileNode file : groupFiles) {
                    FileAnalysis analysis = file.analyzeFile();
                    analysis.isBestPowerInGroup = (analysis.maxPower > 0 && Math.abs(analysis.maxPower - groupBestPower) < 0.1);
                    analysis.isBestFATSInGroup = (groupBestFATSFile != null && groupBestFATSFile.filename.equals(file.filename));
                }
            }
        }
    }

    private void expandAllNodes() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        Enumeration<?> enumeration = root.depthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            Object node = enumeration.nextElement();
            if (node instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) node;
                TreePath path = new TreePath(treeNode.getPath());
                rangeTree.expandPath(path);
            }
        }
    }

    private void updateStatus() {
        if (fileDatasets == null || fileDatasets.isEmpty()) {
            statusLabel.setText("No files loaded");
            rangeCountLabel.setText("0 ranges selected");
            return;
        }

        int totalRanges = 0;
        int selectedRanges = 0;

        for (ECUxDataset dataset : fileDatasets.values()) {
            totalRanges += dataset.getRanges().size();
        }

        // Count selected ranges from tree
        selectedRanges = countSelectedRanges();

        statusLabel.setText(String.format("%d files, %d total ranges", fileDatasets.size(), totalRanges));
        rangeCountLabel.setText(String.format("%d ranges selected", selectedRanges));
    }

    private int countSelectedRanges() {
        int count = 0;
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        Enumeration<?> enumeration = root.depthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            Object node = enumeration.nextElement();
            if (node instanceof RangeNode) {
                RangeNode rangeNode = (RangeNode) node;
                if (rangeNode.isSelected()) {
                    count++;
                }
            }
        }
        return count;
    }

    private void selectAll() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        Enumeration<?> enumeration = root.depthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            Object node = enumeration.nextElement();
            if (node instanceof TreeNode) {
                TreeNode treeNode = (TreeNode) node;
                treeNode.setSelected(true);
            }
        }
        rangeTree.repaint();
        updateStatus();
    }

    private void selectNone() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        Enumeration<?> enumeration = root.depthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            Object node = enumeration.nextElement();
            if (node instanceof TreeNode) {
                TreeNode treeNode = (TreeNode) node;
                treeNode.setSelected(false);
            }
        }
        rangeTree.repaint();
        updateStatus();
    }

    private void applySelection() {
        // Build selection map from tree state
        Map<String, Set<Integer>> newSelection = new HashMap<>();

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        Enumeration<?> enumeration = root.depthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            Object node = enumeration.nextElement();
            if (node instanceof FileNode) {
                FileNode fileNode = (FileNode) node;
                String filename = fileNode.getFilename();
                ECUxDataset dataset = fileNode.getDataset();
                List<Dataset.Range> ranges = dataset.getRanges();

                if (fileNode.isSelected()) {
                    // File is selected - add all ranges
                    Set<Integer> rangeSet = new HashSet<>();
                    for (int i = 0; i < ranges.size(); i++) {
                        rangeSet.add(i);
                    }
                    newSelection.put(filename, rangeSet);
                } else if (ranges.size() > 1) {
                    // File is not selected but has multiple ranges - check individual range selections
                    Set<Integer> rangeSet = new HashSet<>();
                    Enumeration<?> children = fileNode.children();
                    while (children.hasMoreElements()) {
                        Object child = children.nextElement();
                        if (child instanceof RangeNode) {
                            RangeNode rangeNode = (RangeNode) child;
                            if (rangeNode.isSelected()) {
                                rangeSet.add(rangeNode.getRangeIndex());
                            }
                        }
                    }
                    if (!rangeSet.isEmpty()) {
                        newSelection.put(filename, rangeSet);
                    }
                }
                // For files with single range that are not selected, don't add anything
            }
        }

        // Update filter's per-file range selection
        filter.clearAllRangeSelections();
        for (Map.Entry<String, Set<Integer>> entry : newSelection.entrySet()) {
            filter.setSelectedRanges(entry.getKey(), entry.getValue());
        }

        // Update chart visibility and FATS window
        if (eplot != null) {
            eplot.updateChartVisibility();

            // Rebuild FATS to show only selected ranges
            if (eplot.fatsDataset != null) {
                eplot.fatsDataset.rebuild();
                if (eplot.fatsFrame != null) {
                    eplot.fatsFrame.refreshFromFATS();
                }
            }

            updateStatus();
        }
    }


    private void updateButtonStates() {
        boolean hasData = fileDatasets != null && !fileDatasets.isEmpty();
        applyButton.setEnabled(hasData);
        selectAllButton.setEnabled(hasData);
        selectNoneButton.setEnabled(hasData);
        okButton.setEnabled(hasData);
        cancelButton.setEnabled(true); // Always enabled
    }

    // No window size preferences - window auto-sizes based on tree content

}

// vim: set sw=4 ts=8 expandtab:
