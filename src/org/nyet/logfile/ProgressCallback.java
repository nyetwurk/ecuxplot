package org.nyet.logfile;

/**
 * Callback interface for reporting progress during file loading operations.
 */
public interface ProgressCallback {
    /**
     * Report progress for a file being loaded.
     * @param fileName the name of the file being loaded
     * @param stage the current loading stage (e.g., "Reading file", "Parsing CSV", "Building ranges")
     * @param current the current progress value (e.g., lines read, rows processed)
     * @param total the total expected value (e.g., total lines, total rows), or -1 if unknown
     */
    void reportProgress(String fileName, String stage, long current, long total);
}

// vim: set sw=4 ts=8 expandtab:
