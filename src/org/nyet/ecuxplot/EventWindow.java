package org.nyet.ecuxplot;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Event Window - Real-time event streaming for debugging FATS and filter issues
 *
 * This window captures and displays all application events in real-time,
 * providing a comprehensive view of what's happening during data processing.
 *
 * Features:
 * - Real-time event streaming
 * - Level-based filtering (TRACE, DEBUG, INFO, WARN, ERROR)
 * - Search functionality
 * - Export to file
 * - Memory management with configurable limits
 */
public class EventWindow extends JFrame {
    private static final Logger logger = LoggerFactory.getLogger(EventWindow.class);

    // Configuration constants
    private static final int MAX_LOG_LINES = 1000;
    private static final int MAX_QUEUE_SIZE = 10000;

    // UI Components
    private JTextArea logTextArea;
    private JComboBox<Level> levelFilter;
    private JTextField searchField;
    private JButton clearButton;
    private JButton exportButton;

    // Event infrastructure
    private LogAppender eventAppender;
    private BlockingQueue<String> eventQueue;
    private Thread eventProcessor;
    private boolean isVisible = false;

    // Event storage for filtering and buffering
    private java.util.List<String> bufferedEvents = new java.util.ArrayList<>();
    private java.util.List<String> allEvents = new java.util.ArrayList<>();

    public EventWindow() {
        super("Events");
        initializeUI();
        setupLogging();
        setupEventHandlers();
        startEventProcessor();
    }

    private void initializeUI() {
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setSize(1600, 800);
        setLocationRelativeTo(null);

        // Create main panel with border layout
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Create top panel with controls
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        // Level filter
        topPanel.add(new JLabel("Level:"));
        topPanel.add(Box.createHorizontalStrut(8));
        levelFilter = new JComboBox<>();
        levelFilter.addItem(Level.TRACE);
        levelFilter.addItem(Level.DEBUG);
        levelFilter.addItem(Level.INFO);
        levelFilter.addItem(Level.WARN);
        levelFilter.addItem(Level.ERROR);
        levelFilter.setSelectedItem(Level.INFO);
        topPanel.add(levelFilter);

        // Search field
        topPanel.add(Box.createHorizontalStrut(20));
        topPanel.add(new JLabel("Search:"));
        topPanel.add(Box.createHorizontalStrut(8));
        searchField = new JTextField(30);
        topPanel.add(searchField);

        // Buttons
        topPanel.add(Box.createHorizontalStrut(20));
        clearButton = new JButton("Clear");
        topPanel.add(clearButton);

        topPanel.add(Box.createHorizontalStrut(10));
        exportButton = new JButton("Export");
        topPanel.add(exportButton);

        // Create center panel with scroll pane
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(5, 15, 15, 15));

        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        logTextArea.setLineWrap(true);
        logTextArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(logTextArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        centerPanel.add(scrollPane, BorderLayout.CENTER);

        // Add panels to main panel
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Add main panel to frame
        add(mainPanel);

        // Handle window close
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                isVisible = false;
                setVisible(false);
            }
        });
    }

    private void setupLogging() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        eventAppender = new LogAppender();
        eventAppender.setContext(context);
        eventAppender.start();

        // Add appender to root logger to capture all log messages
        ch.qos.logback.classic.Logger rootLogger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(eventAppender);

        // Update dropdown based on current root logger level
        updateLevelFilterOptions();
    }

    private void updateLevelFilterOptions() {
        // Get current root logger level
        ch.qos.logback.classic.Logger rootLogger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        Level currentLevel = rootLogger.getLevel();

        // Remove all items and add only levels that are enabled
        levelFilter.removeAllItems();

        // Add levels based on current verbosity
        if (currentLevel.toInt() <= Level.TRACE_INT) {
            levelFilter.addItem(Level.TRACE);
        }
        if (currentLevel.toInt() <= Level.DEBUG_INT) {
            levelFilter.addItem(Level.DEBUG);
        }
        if (currentLevel.toInt() <= Level.INFO_INT) {
            levelFilter.addItem(Level.INFO);
        }
        if (currentLevel.toInt() <= Level.WARN_INT) {
            levelFilter.addItem(Level.WARN);
        }
        if (currentLevel.toInt() <= Level.ERROR_INT) {
            levelFilter.addItem(Level.ERROR);
        }

        // Set default selection to current level
        levelFilter.setSelectedItem(currentLevel);
    }

    private void setupEventHandlers() {
        // Level filter change
        levelFilter.addActionListener(e -> {
            // Refresh display when level filter changes
            refreshDisplay();
        });

        // Search field change
        searchField.addActionListener(e -> {
            // Refresh display when search changes
            refreshDisplay();
        });

        // Clear button
        clearButton.addActionListener(e -> {
            logTextArea.setText("");
            eventQueue.clear();
            synchronized (bufferedEvents) {
                bufferedEvents.clear();
            }
            synchronized (allEvents) {
                allEvents.clear();
            }
        });

        // Export button
        exportButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Export Events");
            fileChooser.setSelectedFile(new java.io.File("events_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".txt"));

            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try (FileWriter writer = new FileWriter(fileChooser.getSelectedFile())) {
                    writer.write(logTextArea.getText());
                    logger.info("Events exported to: {}", fileChooser.getSelectedFile().getAbsolutePath());
                } catch (IOException ex) {
                    logger.error("Failed to export events", ex);
                }
            }
        });
    }

    private void startEventProcessor() {
        eventQueue = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);

        eventProcessor = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String eventEntry = eventQueue.take();
                    SwingUtilities.invokeLater(() -> {
                        // Always store events in allEvents for filtering
                        synchronized (allEvents) {
                            allEvents.add(eventEntry);
                            // Keep only the most recent events to prevent memory issues
                            if (allEvents.size() > MAX_LOG_LINES) {
                                allEvents.remove(0);
                            }
                        }

                        if (isVisible) {
                            appendEventEntry(eventEntry);
                        } else {
                            // Buffer events when window is not visible
                            synchronized (bufferedEvents) {
                                bufferedEvents.add(eventEntry);
                                // Keep only the most recent events to prevent memory issues
                                if (bufferedEvents.size() > MAX_LOG_LINES) {
                                    bufferedEvents.remove(0);
                                }
                            }
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        eventProcessor.setDaemon(true);
        eventProcessor.start();
    }

    private void appendEventEntry(String eventEntry) {
        // Apply level filter
        Level selectedLevel = (Level) levelFilter.getSelectedItem();
        if (!shouldShowEventEntry(eventEntry, selectedLevel)) {
            return;
        }

        // Apply search filter
        String searchText = searchField.getText().trim();
        if (!searchText.isEmpty() && !eventEntry.toLowerCase().contains(searchText.toLowerCase())) {
            return;
        }

        // Append to text area
        logTextArea.append(eventEntry.trim() + "\n");

        // Auto-scroll to bottom
        logTextArea.setCaretPosition(logTextArea.getDocument().getLength());

        // Limit displayed lines
        String[] lines = logTextArea.getText().split("\n");
        if (lines.length > MAX_LOG_LINES) {
            StringBuilder sb = new StringBuilder();
            for (int i = lines.length - MAX_LOG_LINES; i < lines.length; i++) {
                sb.append(lines[i]).append("\n");
            }
            logTextArea.setText(sb.toString());
        }
    }

    private boolean shouldShowEventEntry(String eventEntry, Level selectedLevel) {
        // Extract level from event entry (format: "HH:mm:ss.SSS [thread] LEVEL logger -- message")
        try {
            String[] parts = eventEntry.split("\\s+");
            if (parts.length >= 3) {
                String levelStr = parts[2];
                Level entryLevel = Level.toLevel(levelStr);
                return entryLevel.toInt() >= selectedLevel.toInt();
            }
        } catch (Exception e) {
            // If we can't parse the level, show the entry
        }
        return true;
    }

    private void refreshDisplay() {
        // Clear current display and re-display all events with current filters
        logTextArea.setText("");

        // Re-process all stored events with current filters
        synchronized (allEvents) {
            for (String eventEntry : allEvents) {
                appendEventEntry(eventEntry);
            }
        }
    }

    private void displayBufferedEvents() {
        // Display all buffered events when window is first shown
        synchronized (bufferedEvents) {
            for (String eventEntry : bufferedEvents) {
                appendEventEntry(eventEntry);
            }
            bufferedEvents.clear(); // Clear buffer after displaying
        }
    }


    public void showWindow() {
        isVisible = true;
        setVisible(true);
        toFront();

        // Display all buffered events when window is first shown
        displayBufferedEvents();
    }


    @Override
    public void dispose() {
        if (eventProcessor != null) {
            eventProcessor.interrupt();
        }
        if (eventAppender != null) {
            eventAppender.stop();
        }
        super.dispose();
    }

    /**
     * Custom Logback appender that captures log events and queues them for display
     */
    private class LogAppender extends AppenderBase<ILoggingEvent> {
        @Override
        protected void append(ILoggingEvent event) {
            if (eventQueue != null) {
                String formattedMessage = String.format("%s [%s] %-5s %s -- %s%n",
                    new SimpleDateFormat("HH:mm:ss.SSS").format(new Date(event.getTimeStamp())),
                    event.getThreadName(),
                    event.getLevel().toString(),
                    event.getLoggerName(),
                    event.getFormattedMessage());

                // Try to add to queue, drop oldest if full
                if (!eventQueue.offer(formattedMessage)) {
                    // Queue is full, remove oldest entry and add new one
                    eventQueue.poll(); // Remove oldest
                    eventQueue.offer(formattedMessage); // Add new (should succeed now)
                }
            }
        }
    }
}

// vim: set sw=4 ts=8 expandtab:
