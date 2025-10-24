package org.nyet.ecuxplot;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Custom exception dialog with better styling and branding.
 * Provides a more professional appearance than the default JOptionPane.
 */
public class CustomExceptionDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private JButton okButton;
    private JLabel iconLabel;
    private JLabel messageLabel;
    private JTextArea detailsArea;
    private boolean detailsVisible = false;

    public CustomExceptionDialog(Frame parent, String title, String message, String details) {
        super(parent, title, true);
        initializeDialog(message, details);
    }

    public CustomExceptionDialog(Dialog parent, String title, String message, String details) {
        super(parent, title, true);
        initializeDialog(message, details);
    }

    private void initializeDialog(String message, String details) {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        // Create main panel with custom styling
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        mainPanel.setBackground(Color.WHITE);

        // Create icon panel
        JPanel iconPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        iconPanel.setBackground(Color.WHITE);

        // Load the application icon
        ImageIcon appIcon = null;
        try {
            java.net.URL iconURL = getClass().getResource("icons/ECUxPlot2-64.png");
            if (iconURL != null) {
                appIcon = new ImageIcon(iconURL);
            }
        } catch (Exception e) {
            // If icon can't be loaded, just don't show an icon
        }

        if (appIcon != null) {
            iconLabel = new JLabel(appIcon);
        } else {
            // No icon if we can't load the app icon
            iconLabel = new JLabel();
        }
        iconPanel.add(iconLabel);

        // Create message panel
        JPanel messagePanel = new JPanel(new BorderLayout());
        messagePanel.setBackground(Color.WHITE);

        // Main message
        messageLabel = new JLabel("<html><div style='width: 400px;'>" +
            message.replace("\n", "<br>") + "</div></html>");
        messageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        messageLabel.setForeground(new Color(60, 60, 60));
        messageLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        messagePanel.add(messageLabel, BorderLayout.NORTH);

        // Details area (initially hidden)
        if (details != null && !details.trim().isEmpty()) {
            detailsArea = new JTextArea(details);
            detailsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            detailsArea.setBackground(new Color(248, 248, 248));
            detailsArea.setForeground(new Color(80, 80, 80));
            detailsArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));
            detailsArea.setEditable(false);
            detailsArea.setLineWrap(true);
            detailsArea.setWrapStyleWord(true);
            detailsArea.setRows(6);
            detailsArea.setVisible(false);

            JScrollPane detailsScroll = new JScrollPane(detailsArea);
            detailsScroll.setPreferredSize(new Dimension(400, 120));
            detailsScroll.setBorder(null);
            detailsScroll.setVisible(false);

            messagePanel.add(detailsScroll, BorderLayout.CENTER);
        }

        // Create button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(Color.WHITE);

        // Details toggle button (if details exist)
        if (details != null && !details.trim().isEmpty()) {
            JButton detailsButton = new JButton("Show Details");
            detailsButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            detailsButton.setPreferredSize(new Dimension(100, 30));
            detailsButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    toggleDetails();
                }
            });
            buttonPanel.add(detailsButton);
        }

        // OK button
        okButton = new JButton("OK");
        okButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        okButton.setPreferredSize(new Dimension(80, 30));
        okButton.setBackground(new Color(0, 120, 215));
        okButton.setForeground(Color.WHITE);
        okButton.setFocusPainted(false);
        okButton.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        buttonPanel.add(okButton);

        // Assemble the dialog
        mainPanel.add(iconPanel, BorderLayout.WEST);
        mainPanel.add(messagePanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);

        // Set default button
        getRootPane().setDefaultButton(okButton);

        // Pack and center
        pack();
        setLocationRelativeTo(getParent());

        // Set minimum size
        setMinimumSize(new Dimension(500, 150));
    }

    private void toggleDetails() {
        if (detailsArea != null) {
            detailsVisible = !detailsVisible;
            detailsArea.setVisible(detailsVisible);

            // Find and update the details scroll pane
            Component[] components = ((JPanel) getContentPane().getComponent(0)).getComponents();
            for (Component comp : components) {
                if (comp instanceof JPanel) {
                    JPanel centerPanel = (JPanel) comp;
                    Component[] centerComponents = centerPanel.getComponents();
                    for (Component centerComp : centerComponents) {
                        if (centerComp instanceof JScrollPane) {
                            centerComp.setVisible(detailsVisible);
                            break;
                        }
                    }
                }
            }

            pack();
            setLocationRelativeTo(getParent());
        }
    }


    /**
     * Show a custom exception dialog.
     * @param parent the parent component
     * @param title the dialog title
     * @param message the main message
     * @param details optional details (can be null)
     */
    public static void showDialog(Component parent, String title, String message, String details) {
        CustomExceptionDialog dialog;

        if (parent instanceof Frame) {
            dialog = new CustomExceptionDialog((Frame) parent, title, message, details);
        } else if (parent instanceof Dialog) {
            dialog = new CustomExceptionDialog((Dialog) parent, title, message, details);
        } else {
            // Fallback to frame-less dialog
            dialog = new CustomExceptionDialog((Frame) null, title, message, details);
        }

        dialog.setVisible(true);
    }

    /**
     * Show a custom exception dialog with just a message.
     * @param parent the parent component
     * @param title the dialog title
     * @param message the message
     */
    public static void showDialog(Component parent, String title, String message) {
        showDialog(parent, title, message, null);
    }
}

// vim: set sw=4 ts=8 expandtab:
