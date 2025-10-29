package org.nyet.ecuxplot;

import java.util.prefs.Preferences;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

/**
 * Filter class manages two concerns:
 * 1. Range detection parameters (RPM thresholds, pedal, throttle, etc.) - used by FilterWindow
 * 2. Per-file range selection (selected ranges from Range Selector) - used by RangeSelectorWindow
 */
public class Filter {
    public static final String PREFS_TAG = "filter";

    private static final boolean defaultEnabled = true;
    private static final boolean defaultShowAllRanges = true;
    private static final boolean defaultMonotonicRPM = true;
    private static final int defaultMonotonicRPMfuzz = 100;
    private static final int defaultMinRPM = 2000;
    private static final int defaultMaxRPM = 8000;
    private static final int defaultMinRPMRange = 1200;
    private static final int defaultMinPedal = 95;
    private static final int defaultMinThrottle = 40;   // allow for bad throttle cut
    private static final int defaultMinAcceleration = 100;      // RPM/s minimum acceleration
    private static final int defaultGear = 3;
    private static final int defaultMinPoints = 5;
    private static final double defaultAccelMAW = 1.5;         // acceleration moving average window (seconds)
    private static final double defaultHPTQMAW = 1.5;         // hp/tq moving average window (seconds)
    private static final double defaultZeitMAW = 1.5;          // zeitronix MAW (seconds)

    private final Preferences prefs;

    public Filter (Preferences prefs) {
        this.prefs = prefs.node(PREFS_TAG);
        migrateSmoothingPreferences();
    }

    /**
     * Migrate old int-based smoothing preferences to new double-based (seconds) preferences
     */
    private void migrateSmoothingPreferences() {
        try {
            // Check if we have old int values stored
            int oldAccelMAW = this.prefs.getInt("accelMAW", -1);
            if (oldAccelMAW > 0) {
                // Old system: 5 was default, equivalent to ~0.5 seconds at 10 Hz
                // Convert to seconds (assuming typical 10 Hz = 0.1s per sample)
                double newValue = oldAccelMAW / 10.0;
                this.prefs.putDouble("accelMAW_sec", newValue);
                this.prefs.remove("accelMAW");
            }
        } catch (Exception e) {
            // ignore and use defaults
        }

        try {
            int oldHPTQMAW = this.prefs.getInt("HPTQMAW", -1);
            if (oldHPTQMAW > 0) {
                // Old default was 5, equivalent to 0.5 seconds at 10 Hz
                double newValue = oldHPTQMAW / 10.0;
                this.prefs.putDouble("HPTQMAW_sec", newValue);
                this.prefs.remove("HPTQMAW");
            }
        } catch (Exception e) {
            // ignore and use defaults
        }

        try {
            int oldZeitMAW = this.prefs.getInt("ZeitMAW", -1);
            if (oldZeitMAW > 0) {
                // Old default was 30, convert to seconds
                double newValue = oldZeitMAW;
                this.prefs.putDouble("ZeitMAW_sec", newValue);
                this.prefs.remove("ZeitMAW");
            }
        } catch (Exception e) {
            // ignore and use defaults
        }
    }

    public static boolean enabled(Preferences prefs) {
        return prefs.node(PREFS_TAG).getBoolean("enabled", defaultEnabled);
    }

    public boolean enabled() {
        return this.prefs.getBoolean("enabled", defaultEnabled);
    }
    public void enabled(boolean val) {
        this.prefs.putBoolean("enabled", val);
    }

    private int currentRange = 0;

    public int getCurrentRange() {
        return this.currentRange;
    }

    public void setCurrentRange(int currentRange) {
        this.currentRange = currentRange;
    }

    // Per-file range selection support
    private Map<String, Set<Integer>> selectedRanges = new HashMap<>();

    /**
     * Get the selected ranges for a specific file
     * @param filename The filename to get ranges for
     * @return Set of selected range indices, or empty set if none selected
     */
    public Set<Integer> getSelectedRanges(String filename) {
        return selectedRanges.getOrDefault(filename, new HashSet<>());
    }

    /**
     * Set the selected ranges for a specific file
     * @param filename The filename to set ranges for
     * @param ranges Set of selected range indices
     */
    public void setSelectedRanges(String filename, Set<Integer> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            selectedRanges.remove(filename);
        } else {
            selectedRanges.put(filename, new HashSet<>(ranges));
        }
    }

    /**
     * Clear all range selections
     */
    public void clearAllRangeSelections() {
        selectedRanges.clear();
    }

    /**
     * Check if any file has range selections
     * @return true if any file has selected ranges
     */
    public boolean hasAnyRangeSelections() {
        return !selectedRanges.isEmpty();
    }

    // Per-file selection support (for filter disabled mode)
    private Set<String> selectedFiles = new HashSet<>();

    /**
     * Get the selected files (for filter disabled mode)
     * @return Set of selected filenames
     */
    public Set<String> getSelectedFiles() {
        return new HashSet<>(selectedFiles);
    }

    /**
     * Set the selected files (for filter disabled mode)
     * @param files Set of selected filenames
     */
    public void setSelectedFiles(Set<String> files) {
        if (files == null || files.isEmpty()) {
            selectedFiles.clear();
        } else {
            selectedFiles = new HashSet<>(files);
        }
    }

    /**
     * Clear all file selections
     */
    public void clearAllFileSelections() {
        selectedFiles.clear();
    }

    /**
     * Check if a file is selected (for filter disabled mode)
     * @param filename The filename to check
     * @return true if file is selected
     */
    public boolean isFileSelected(String filename) {
        // If no files explicitly selected, default to all selected (for initial state)
        if (selectedFiles.isEmpty()) {
            return true;
        }
        return selectedFiles.contains(filename);
    }

    public static boolean showAllRanges(Preferences prefs) {
    return prefs.node(PREFS_TAG).getBoolean("showAllRanges", defaultShowAllRanges);
    }

    public boolean showAllRanges() {
    return this.prefs.getBoolean("showAllRanges", defaultShowAllRanges);
    }
    public void showAllRanges(boolean val) {
    this.prefs.putBoolean("showAllRanges", val);
    }

    public boolean monotonicRPM() {
        return this.prefs.getBoolean("monotonicRPM", defaultMonotonicRPM);
    }
    public void monotonicRPM(boolean val) {
        this.prefs.putBoolean("monotonicRPM", val);
    }

    public int monotonicRPMfuzz() {
        return this.prefs.getInt("monotonicRPMfuzz", defaultMonotonicRPMfuzz);
    }
    public void monotonicRPMfuzz(Integer val) {
        this.prefs.putInt("monotonicRPMfuzz", val);
    }

    public int minRPM() {
        return this.prefs.getInt("minRPM", defaultMinRPM);
    }
    public void minRPM(Integer val) {
        this.prefs.putInt("minRPM", val);
    }

    public int maxRPM() {
        return this.prefs.getInt("maxRPM", defaultMaxRPM);
    }
    public void maxRPM(Integer val) {
        this.prefs.putInt("maxRPM", val);
    }

    public int minRPMRange() {
        return this.prefs.getInt("minRPMRange", defaultMinRPMRange);
    }
    public void minRPMRange(Integer val) {
        this.prefs.putInt("minRPMRange", val);
    }
    public int minPedal() {
        return this.prefs.getInt("minPedal", defaultMinPedal);
    }
    public void minPedal(Integer val) {
        this.prefs.putInt("minPedal", val);
    }

    public int minThrottle() {
        return this.prefs.getInt("minThrottle", defaultMinThrottle);
    }
    public void minThrottle(Integer val) {
        this.prefs.putInt("minThrottle", val);
    }

    public int minAcceleration() {
        return this.prefs.getInt("minAcceleration", defaultMinAcceleration);
    }
    public void minAcceleration(Integer val) {
        this.prefs.putInt("minAcceleration", val);
    }

    public double accelMAW() {
        // Use new key name to avoid type mismatch with old int values
        return this.prefs.getDouble("accelMAW_sec", defaultAccelMAW);
    }
    public void accelMAW(Double val) {
        this.prefs.putDouble("accelMAW_sec", val);
    }

    public int gear() {
        return this.prefs.getInt("gear", defaultGear);
    }
    public void gear(Integer val) {
        this.prefs.putInt("gear", val);
    }

    public int minPoints() {
        return this.prefs.getInt("minPoints", defaultMinPoints);
    }
    public void minPoints(Integer val) {
        this.prefs.putInt("minPoints", val);
    }

    public double HPTQMAW() {
        // Use new key name to avoid type mismatch with old int values
        return this.prefs.getDouble("HPTQMAW_sec", defaultHPTQMAW);
    }
    public void HPTQMAW(Double val) {
        this.prefs.putDouble("HPTQMAW_sec", val);
    }

    public double ZeitMAW() {
        // Use new key name to avoid type mismatch with old int values
        return this.prefs.getDouble("ZeitMAW_sec", defaultZeitMAW);
    }
    public void ZeitMAW(Double val) {
        this.prefs.putDouble("ZeitMAW_sec", val);
    }

    public void resetToDefaults() {
        this.gear(defaultGear);
        this.minRPM(defaultMinRPM);
        this.maxRPM(defaultMaxRPM);
        this.minRPMRange(defaultMinRPMRange);
        this.monotonicRPMfuzz(defaultMonotonicRPMfuzz);
        this.minPedal(defaultMinPedal);
        this.minThrottle(defaultMinThrottle);
        this.minAcceleration(defaultMinAcceleration);
        this.accelMAW(1.5);
        this.minPoints(defaultMinPoints);
        this.HPTQMAW(1.5);
        this.ZeitMAW(1.5);
    }
}

// vim: set sw=4 ts=8 expandtab:
