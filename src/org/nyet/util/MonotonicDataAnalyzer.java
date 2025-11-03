package org.nyet.util;

/**
 * Utility class for time data analysis and smoothing.
 */
public class MonotonicDataAnalyzer {
    /**
     * Calculate sample rate from time data.
     *
     * @param timeData Array of time values in seconds
     * @return Sample rate in samples per second, or 0.0 if calculation failed
     */
    public static double calculateSampleRate(double[] timeData) {
        if (timeData == null || timeData.length < 2) {
            return 0.0;
        }
        double totalTimeSpan = timeData[timeData.length - 1] - timeData[0];
        if (totalTimeSpan > 0) {
            return (timeData.length - 1) / totalTimeSpan;
        }
        return 0.0;
    }

    /**
     * Smooth time data by smoothing deltas (intervals) rather than absolute time to prevent drift.
     * Applies smoothing if samplesPerSec >= 5.0.
     * This preserves the starting time and total elapsed time while smoothing jitter.
     *
     * @param timeData Array of time values in seconds
     * @param samplesPerSec Sample rate
     * @return Smoothed time array, or original array if smoothing not applied
     */
    public static double[] smoothTimeByDeltas(double[] timeData, double samplesPerSec) {
        if (timeData == null || timeData.length < 2 || samplesPerSec < 5.0) {
            return timeData;
        }

        // Calculate time deltas (intervals between samples)
        double[] deltas = new double[timeData.length - 1];
        for (int i = 0; i < deltas.length; i++) {
            deltas[i] = timeData[i + 1] - timeData[i];
        }

        // Need sufficient deltas for smoothing to work
        // DoubleArray.smooth() uses:
        //   - movingAverage for sp < 10 (window = sp/4, requires window >= 1 and window < sp)
        //   - SavitzkyGolay(5,5) for sp >= 10 (requires 11 coefficients, so at least 11 data points)
        // To avoid errors with SG smoothing, require at least 11 deltas (which means 12 time points)
        if (deltas.length < 11) {
            return timeData;
        }

        // Smooth the deltas (not the absolute time values) to prevent drift
        DoubleArray deltaArray = new DoubleArray(deltas);
        DoubleArray smoothedDeltas = deltaArray.smooth();

        // Reconstruct time series from smoothed deltas
        // Start with original first time value to preserve absolute time
        double[] smoothedTime = new double[timeData.length];
        smoothedTime[0] = timeData[0];
        for (int i = 0; i < smoothedDeltas.size(); i++) {
            smoothedTime[i + 1] = smoothedTime[i] + smoothedDeltas.get(i);
        }

        return smoothedTime;
    }
}
