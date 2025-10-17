package org.nyet.ecuxplot;

import java.awt.BorderLayout;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.swing.*;
import javax.swing.text.DefaultCaret;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Debug Logging Window - Real-time log streaming for debugging FATS and filter issues
 */
public class DebugLogWindow extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final int MAX_LOG_LINES = 1000;

    private JTextArea logTextArea;
    private JScrollPane scrollPane;
    private JComboBox<Level> levelFilter;
    private JTextField searchField;
    private JButton clearButton;
    private JButton exportButton;
    private JCheckBox autoScrollCheckBox;

    private LogAppender logAppender;
    private BlockingQueue<String> logQueue;
    private Thread logProcessor;
    private boolean isVisible = false;

    public DebugLogWindow() {
        super("Debug Logs");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(1200, 400);
        setLocationRelativeTo(null);

        initializeComponents();
        setupLayout();
        setupLogging();
        setupEventHandlers();

        // Start log processing thread
        startLogProcessor();
    }

    private void initializeComponents() {
        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));

        scrollPane = new JScrollPane(logTextArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        // Auto-scroll to bottom by default
        DefaultCaret caret = (DefaultCaret) logTextArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        levelFilter = new JComboBox<>(new Level[]{
            Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR
        });
        levelFilter.setSelectedItem(Level.DEBUG);

        searchField = new JTextField(20);
        searchField.setToolTipText("Search log messages");

        clearButton = new JButton("Clear");
        exportButton = new JButton("Export");
        autoScrollCheckBox = new JCheckBox("Auto-scroll", true);

        logQueue = new LinkedBlockingQueue<>();
    }

    private void setupLayout() {
        setLayout(new BorderLayout());

        // Top panel with controls
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));

        topPanel.add(new JLabel("Level:"));
        topPanel.add(levelFilter);
        topPanel.add(Box.createHorizontalStrut(10));

        topPanel.add(new JLabel("Search:"));
        topPanel.add(searchField);
        topPanel.add(Box.createHorizontalStrut(10));

        topPanel.add(clearButton);
        topPanel.add(exportButton);
        topPanel.add(autoScrollCheckBox);

        topPanel.add(Box.createHorizontalGlue());

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void setupLogging() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        logAppender = new LogAppender();
        logAppender.setContext(context);
        logAppender.start();

        // Add appender to ECUxPlot logger
        ch.qos.logback.classic.Logger ecuxLogger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.nyet.ecuxplot");
        ecuxLogger.addAppender(logAppender);
    }

    private void setupEventHandlers() {
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                hideWindow();
            }
        });

        clearButton.addActionListener(e -> {
            logTextArea.setText("");
            logQueue.clear();
        });

        exportButton.addActionListener(e -> exportLogs());

        levelFilter.addActionListener(e -> {
            // Level filtering is handled in the log processor
        });

        searchField.addActionListener(e -> {
            String searchText = searchField.getText().trim();
            if (!searchText.isEmpty()) {
                searchLogs(searchText);
            }
        });

        autoScrollCheckBox.addActionListener(e -> {
            DefaultCaret caret = (DefaultCaret) logTextArea.getCaret();
            if (autoScrollCheckBox.isSelected()) {
                caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
            } else {
                caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
            }
        });
    }

    private void startLogProcessor() {
        logProcessor = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String logEntry = logQueue.take();
                    SwingUtilities.invokeLater(() -> {
                        if (isVisible) {
                            appendLogEntry(logEntry);
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        logProcessor.setDaemon(true);
        logProcessor.start();
    }

    private void appendLogEntry(String logEntry) {
        Level selectedLevel = (Level) levelFilter.getSelectedItem();
        String searchText = searchField.getText().trim();

        // Parse log level from entry
        Level entryLevel = parseLogLevel(logEntry);

        // Apply level filter
        if (entryLevel != null && entryLevel.levelInt < selectedLevel.levelInt) {
            return;
        }

        // Apply search filter
        if (!searchText.isEmpty() && !logEntry.toLowerCase().contains(searchText.toLowerCase())) {
            return;
        }

        logTextArea.append(logEntry);

        // Limit number of lines
        String[] lines = logTextArea.getText().split("\n");
        if (lines.length > MAX_LOG_LINES) {
            StringBuilder sb = new StringBuilder();
            for (int i = lines.length - MAX_LOG_LINES; i < lines.length; i++) {
                sb.append(lines[i]).append("\n");
            }
            logTextArea.setText(sb.toString());
        }

        // Auto-scroll if enabled
        if (autoScrollCheckBox.isSelected()) {
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
        }
    }

    private Level parseLogLevel(String logEntry) {
        if (logEntry.contains(" TRACE ")) return Level.TRACE;
        if (logEntry.contains(" DEBUG ")) return Level.DEBUG;
        if (logEntry.contains(" INFO ")) return Level.INFO;
        if (logEntry.contains(" WARN ")) return Level.WARN;
        if (logEntry.contains(" ERROR ")) return Level.ERROR;
        return null;
    }

    private void searchLogs(String searchText) {
        String content = logTextArea.getText();
        int index = content.toLowerCase().indexOf(searchText.toLowerCase());
        if (index >= 0) {
            logTextArea.setCaretPosition(index);
            logTextArea.getCaret().setSelectionVisible(true);
        }
    }

    private void exportLogs() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("ecuxplot-debug-" +
            new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date()) + ".log"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (FileWriter writer = new FileWriter(fileChooser.getSelectedFile())) {
                writer.write(logTextArea.getText());
                JOptionPane.showMessageDialog(this, "Logs exported successfully!",
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Failed to export logs: " + e.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void showWindow() {
        isVisible = true;
        setVisible(true);
        toFront();
    }

    public void hideWindow() {
        isVisible = false;
        setVisible(false);
    }

    public boolean isWindowVisible() {
        return isVisible;
    }

    @Override
    public void dispose() {
        if (logProcessor != null) {
            logProcessor.interrupt();
        }
        if (logAppender != null) {
            logAppender.stop();
        }
        super.dispose();
    }

    /**
     * Custom log appender that captures log events and queues them for display
     */
    private class LogAppender extends AppenderBase<ILoggingEvent> {
        @Override
        protected void append(ILoggingEvent event) {
            if (!isStarted()) return;

            String formattedMessage = String.format("%s [%s] %-5s %s - %s%n",
                new SimpleDateFormat("HH:mm:ss.SSS").format(new Date(event.getTimeStamp())),
                event.getThreadName(),
                event.getLevel().toString(),
                event.getLoggerName(),
                event.getFormattedMessage()
            );

            // Add exception stack trace if present
            if (event.getThrowableProxy() != null) {
                formattedMessage += "Exception: " + event.getThrowableProxy().getMessage() + "\n";
            }

            logQueue.offer(formattedMessage);
        }
    }
}
