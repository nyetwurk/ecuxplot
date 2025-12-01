package org.nyet.ecuxplot;

import org.nyet.logfile.ProgressCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Progress callback implementation that logs progress to the logger.
 * Used when --no-gui mode is enabled or when no GUI progress dialog is available.
 */
public class LoggerProgressCallback implements ProgressCallback {
    private static final Logger logger = LoggerFactory.getLogger(LoggerProgressCallback.class);
    private final String fileName;

    public LoggerProgressCallback(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public void reportProgress(String fileName, String stage, long current, long total) {
        if (total > 0) {
            long percent = (current * 100) / total;
            logger.debug("{}: {} - {}% ({} / {})", this.fileName, stage, percent, current, total);
        } else {
            logger.debug("{}: {} - {}", this.fileName, stage, current > 0 ? current : "");
        }
    }
}

// vim: set sw=4 ts=8 expandtab:
