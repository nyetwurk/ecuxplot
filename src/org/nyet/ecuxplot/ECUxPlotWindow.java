package org.nyet.ecuxplot;

import java.util.TreeMap;
import javax.swing.JFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for ECUxPlot windows that need top window management.
 * Provides helper methods for managing window z-order through ECUxPlot.
 */
public abstract class ECUxPlotWindow extends JFrame {
    private static final long serialVersionUID = 1L;

    /**
     * Reference to the main ECUxPlot instance
     */
    protected ECUxPlot eplot;

    /**
     * Filter instance - shared by windows that work with filter settings
     */
    protected final Filter filter;

    /**
     * Logger for this window class
     */
    protected final Logger logger;

    /**
     * Dirty flag to track whether user has made changes since last apply.
     * Used to avoid redundant rebuilds when Apply/OK/Restore Defaults is
     * clicked multiple times without intervening user changes.
     */
    private boolean dirty = false;

    /**
     * File datasets - shared by windows that display file-based data
     */
    protected TreeMap<String, ECUxDataset> fileDatasets;

    /**
     * Constructor - creates a window with the specified title
     */
    protected ECUxPlotWindow(String title, Filter filter, ECUxPlot eplot) {
        super(title);
        this.filter = filter;
        this.eplot = eplot;
        // Initialize logger with the actual subclass name for proper log context
        this.logger = LoggerFactory.getLogger(this.getClass());
    }

    /**
     * Helper method to set this window as top window (with null check)
     */
    protected void setTopWindow() {
        if(this.eplot != null) {
            this.eplot.setTopWindow(this);
        }
    }

    /**
     * Helper method to clear top window status (with null check)
     */
    protected void clearTopWindow() {
        if(this.eplot != null) {
            this.eplot.clearTopWindow(this);
        }
    }

    /**
     * Helper method to safely call a method on eplot if it exists.
     * Use this for one-off calls instead of storing the reference.
     */
    protected void withEplot(java.util.function.Consumer<ECUxPlot> action) {
        if(this.eplot != null) {
            action.accept(this.eplot);
        }
    }

    /**
     * Check if filter is enabled (with null check)
     */
    protected boolean isFilterEnabled() {
        return filter != null && filter.enabled();
    }

    /**
     * Check if filter is disabled (with null check)
     */
    protected boolean isFilterDisabled() {
        return filter != null && !filter.enabled();
    }

    /**
     * Mark this window as dirty (user has made changes).
     * Call this from change listeners on UI controls.
     */
    protected void markDirty() {
        this.dirty = true;
    }

    /**
     * Clear the dirty flag (changes have been applied).
     * Call this after Apply/OK/Restore Defaults successfully completes.
     */
    protected void clearDirty() {
        this.dirty = false;
    }

    /**
     * Check if the window has unapplied user changes.
     * @return true if the user has made changes since the last apply
     */
    protected boolean isDirty() {
        return this.dirty;
    }
}

// vim: set sw=4 ts=8 expandtab:

