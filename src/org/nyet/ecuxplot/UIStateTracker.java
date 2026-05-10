package org.nyet.ecuxplot;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;

/**
 * Tracks UI component state for change detection.
 * <p>
 * Register components with {@link #track(JComponent)} at creation time,
 * then use {@link #snapshot()} to save the baseline state and
 * {@link #hasChanged()} to detect whether the user has made changes.
 * <p>
 * Supports JTextField, JComboBox, JSpinner, JCheckBox, and JRadioButton.
 * Implements {@link Cloneable} so the tracker (and its snapshot) can be copied.
 */
public class UIStateTracker implements Cloneable {

    private final List<JComponent> tracked = new ArrayList<>();
    private List<Object> lastSnapshot;

    /** Register a UI component for state tracking. */
    public void track(JComponent component) {
        tracked.add(component);
    }

    /** Remove all tracked components and clear the snapshot. */
    public void clear() {
        tracked.clear();
        lastSnapshot = null;
    }

    /** Save the current state as the baseline for comparison. */
    public void snapshot() {
        lastSnapshot = captureValues();
    }

    /** Check if any tracked component has changed since the last snapshot. */
    public boolean hasChanged() {
        return lastSnapshot == null || !captureValues().equals(lastSnapshot);
    }

    private List<Object> captureValues() {
        List<Object> values = new ArrayList<>(tracked.size());
        for (JComponent c : tracked) {
            values.add(extractValue(c));
        }
        return values;
    }

    @SuppressWarnings("rawtypes")
    private static Object extractValue(JComponent c) {
        if (c instanceof JTextField)    return ((JTextField) c).getText();
        if (c instanceof JComboBox)     return ((JComboBox) c).getSelectedItem();
        if (c instanceof JSpinner)      return ((JSpinner) c).getValue();
        if (c instanceof JCheckBox)     return ((JCheckBox) c).isSelected();
        if (c instanceof JRadioButton)  return ((JRadioButton) c).isSelected();
        throw new IllegalArgumentException(
            "Unsupported component type: " + c.getClass().getName());
    }

    @Override
    public UIStateTracker clone() {
        try {
            UIStateTracker copy = (UIStateTracker) super.clone();
            if (lastSnapshot != null) {
                copy.lastSnapshot = new ArrayList<>(lastSnapshot);
            }
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }
}

// vim: set sw=4 ts=8 expandtab:
