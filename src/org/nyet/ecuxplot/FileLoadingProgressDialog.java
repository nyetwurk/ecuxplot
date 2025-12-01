package org.nyet.ecuxplot;

import javax.swing.*;
import java.awt.*;
import org.nyet.logfile.ProgressCallback;

/**
 * Progress dialog for file loading operations.
 * Shows progress when loading multiple files at startup.
 * Implements ProgressCallback to receive progress updates from Dataset loading.
 */
public class FileLoadingProgressDialog extends JDialog implements ProgressCallback {
    private static final long serialVersionUID = 1L;

    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel fileNameLabel;
    private JLabel stageLabel;

    private int currentFileIndex = 0;
    private int totalFiles = 0;
    private String currentFileName = "";

    // Stage weights: each stage contributes a portion of a file's progress
    private static final double STAGE_WEIGHT_READING = 0.10;  // 10% of file progress
    private static final double STAGE_WEIGHT_PARSING = 0.40;  // 40% of file progress (10-50%)
    private static final double STAGE_WEIGHT_FILTERING = 0.50; // 50% of file progress (50-100%)

    // Track progress per file (0.0 to 1.0) to ensure smooth updates
    private java.util.Map<String, Double> fileProgressMap = new java.util.HashMap<>();

    public FileLoadingProgressDialog(Frame parent) {
        super(parent, "Loading Files", false);
        initializeDialog();
    }

    public FileLoadingProgressDialog() {
        super((Frame) null, "Loading Files", false);
        initializeDialog();
    }

    private void initializeDialog() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(false);
        setModal(false);

        JPanel mainPanel = new JPanel(new BorderLayout(15, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        statusLabel = new JLabel("Preparing...");
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(statusLabel);
        contentPanel.add(Box.createVerticalStrut(8));

        fileNameLabel = new JLabel(" ");
        fileNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(fileNameLabel);
        contentPanel.add(Box.createVerticalStrut(8));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("0%");
        progressBar.setIndeterminate(false);
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(progressBar);
        contentPanel.add(Box.createVerticalStrut(8));

        stageLabel = new JLabel(" ");
        stageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(stageLabel);

        mainPanel.add(contentPanel, BorderLayout.CENTER);
        add(mainPanel);

        // Pack to natural size - let the content determine the size
        pack();
        setLocationRelativeTo(getParent());

        // Ensure dialog is always on top and visible
        setAlwaysOnTop(true);
    }

    /**
     * Set the total number of files to load.
     */
    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
        this.currentFileIndex = 0;
        this.fileProgressMap.clear();
    }

    /**
     * Set the current file being loaded.
     */
    public void setCurrentFile(int fileIndex, String fileName) {
        this.currentFileIndex = fileIndex;
        this.currentFileName = fileName != null ? fileName : "";
        SwingUtilities.invokeLater(() -> {
            fileNameLabel.setText(this.currentFileName);
            statusLabel.setText(String.format("File %d of %d", fileIndex, totalFiles));
        });
    }

    /**
     * Mark loading as complete and automatically close the dialog.
     */
    public void setComplete() {
        SwingUtilities.invokeLater(() -> dispose());
    }

    /**
     * Implementation of ProgressCallback interface.
     * Called by Dataset during file loading to report progress.
     */
    @Override
    public void reportProgress(String fileName, String stage, long current, long total) {
        if (fileName != null && !fileName.isEmpty()) {
            this.currentFileName = fileName;
        }

        SwingUtilities.invokeLater(() -> {
            if (stage != null && !stage.isEmpty()) {
                stageLabel.setText(stage);
            }
            fileNameLabel.setText(this.currentFileName);

            if (totalFiles == 0 || currentFileIndex == 0) {
                if (totalFiles > 0 && !progressBar.isIndeterminate()) {
                    progressBar.setIndeterminate(true);
                }
                repaint();
                return;
            }

            String fileKey = !currentFileName.isEmpty() ? currentFileName : "file" + currentFileIndex;
            double previousFileProgress = fileProgressMap.getOrDefault(fileKey, 0.0);
            double fileProgress = previousFileProgress;

            if (total > 0) {
                // Determine stage base and weight
                double stageBase = 0.0;
                double stageWeight = 0.0;

                if (stage != null) {
                    if (stage.equals("Reading file")) {
                        stageWeight = STAGE_WEIGHT_READING;
                    } else if (stage.equals("Parsing CSV")) {
                        stageBase = STAGE_WEIGHT_READING;
                        stageWeight = STAGE_WEIGHT_PARSING;
                    } else if (stage.equals("Filtering data")) {
                        stageBase = STAGE_WEIGHT_READING + STAGE_WEIGHT_PARSING;
                        stageWeight = STAGE_WEIGHT_FILTERING;
                    } else if (stage.equals("Complete")) {
                        fileProgress = 1.0;
                    }
                }

                if (stage == null || !stage.equals("Complete")) {
                    double stageProgress = Math.min((double)current / total, 1.0);
                    fileProgress = stageBase + (stageProgress * stageWeight);
                    fileProgress = Math.min(fileProgress, 1.0);
                }

                fileProgress = Math.max(fileProgress, previousFileProgress);
                fileProgressMap.put(fileKey, fileProgress);
            }

            double overallProgress = (currentFileIndex - 1 + fileProgress) / totalFiles;
            int overallPercent = Math.min(Math.max((int)Math.round(overallProgress * 100.0), 0), 100);

            progressBar.setIndeterminate(false);
            progressBar.setMaximum(100);
            progressBar.setValue(overallPercent);
            progressBar.setString(String.format("%d%%", overallPercent));
            repaint();
        });
    }


    /**
     * Create and show a progress dialog for file loading.
     * The dialog is shown synchronously and will be visible immediately.
     * @param parent the parent frame (can be null)
     * @param totalFiles the total number of files to load
     * @return the progress dialog instance
     */
    public static FileLoadingProgressDialog showDialog(Frame parent, int totalFiles) {
        FileLoadingProgressDialog dialog = parent != null
            ? new FileLoadingProgressDialog(parent)
            : new FileLoadingProgressDialog();
        dialog.setTotalFiles(totalFiles);
        dialog.setVisible(true);
        dialog.toFront();
        return dialog;
    }
}

// vim: set sw=4 ts=8 expandtab:
