package org.nyet.ecuxplot;

import javax.swing.JOptionPane;
import java.awt.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for showing message dialogs that respects the --no-gui flag.
 * When --no-gui is enabled, messages are logged instead of showing dialogs.
 */
public class MessageDialog {
    private static final Logger logger = LoggerFactory.getLogger(MessageDialog.class);
    private static boolean nogui = false;

    /**
     * Set the no-gui mode. When true, dialogs will be logged instead of shown.
     * @param nogui true to suppress GUI dialogs
     */
    public static void setNoGui(boolean nogui) {
        MessageDialog.nogui = nogui;
    }

    /**
     * Show an information message dialog or log it if --no-gui is enabled.
     * @param parentComponent the parent component for the dialog
     * @param message the message to display
     */
    public static void showMessageDialog(Component parentComponent, Object message) {
        if (nogui) {
            logger.info("Message: {}", message);
        } else {
            JOptionPane.showMessageDialog(parentComponent, message);
        }
    }

    /**
     * Show an information message dialog with title or log it if --no-gui is enabled.
     * @param parentComponent the parent component for the dialog
     * @param message the message to display
     * @param title the title for the dialog
     * @param messageType the type of message (JOptionPane.INFORMATION_MESSAGE, etc.)
     */
    public static void showMessageDialog(Component parentComponent, Object message,
                                       String title, int messageType) {
        // Always log messages for debugging, regardless of GUI mode
        String level = getLogLevel(messageType);
        switch (level) {
            case "ERROR":
                logger.error("{}: {}", title, message);
                break;
            case "WARN":
                logger.warn("{}: {}", title, message);
                break;
            default:
                logger.info("{}: {}", title, message);
                break;
        }

        if (nogui) {
            // Already logged above, nothing else to do
            return;
        }

        // Use custom dialog for error messages, JOptionPane for others
        if (messageType == JOptionPane.ERROR_MESSAGE) {
            CustomExceptionDialog.showDialog(parentComponent, title, message.toString());
        } else {
            JOptionPane.showMessageDialog(parentComponent, message, title, messageType);
        }
    }

    /**
     * Show a confirmation dialog or log it if --no-gui is enabled.
     * @param parentComponent the parent component for the dialog
     * @param message the message to display
     * @param title the title for the dialog
     * @param optionType the type of options (JOptionPane.YES_NO_OPTION, etc.)
     * @param messageType the type of message
     * @return the user's choice, or YES_OPTION if --no-gui is enabled
     */
    public static int showConfirmDialog(Component parentComponent, Object message,
                                      String title, int optionType, int messageType) {
        if (nogui) {
            logger.info("Confirm dialog (assuming YES): {} - {}", title, message);
            return JOptionPane.YES_OPTION;
        } else {
            return JOptionPane.showConfirmDialog(parentComponent, message, title,
                                               optionType, messageType);
        }
    }

    /**
     * Show an input dialog or log it if --no-gui is enabled.
     * @param message the message to display
     * @return the user's input, or null if --no-gui is enabled
     */
    public static String showInputDialog(Object message) {
        if (nogui) {
            logger.info("Input dialog (no input): {}", message);
            return null;
        } else {
            return JOptionPane.showInputDialog(message);
        }
    }

    /**
     * Show an input dialog with initial value or log it if --no-gui is enabled.
     * @param parentComponent the parent component for the dialog
     * @param message the message to display
     * @param initialSelectionValue the initial value
     * @return the user's input, or initialSelectionValue if --no-gui is enabled
     */
    public static String showInputDialog(Component parentComponent, Object message,
                                       Object initialSelectionValue) {
        if (nogui) {
            logger.info("Input dialog (using initial value): {} - {}", message, initialSelectionValue);
            return initialSelectionValue != null ? initialSelectionValue.toString() : null;
        } else {
            return JOptionPane.showInputDialog(parentComponent, message, initialSelectionValue);
        }
    }

    /**
     * Convert JOptionPane message type to log level.
     * @param messageType the JOptionPane message type
     * @return the corresponding log level
     */
    private static String getLogLevel(int messageType) {
        switch (messageType) {
            case JOptionPane.ERROR_MESSAGE:
                return "ERROR";
            case JOptionPane.WARNING_MESSAGE:
                return "WARN";
            case JOptionPane.QUESTION_MESSAGE:
                return "INFO";
            case JOptionPane.INFORMATION_MESSAGE:
            case JOptionPane.PLAIN_MESSAGE:
            default:
                return "INFO";
        }
    }
}

// vim: set sw=4 ts=8 expandtab:
