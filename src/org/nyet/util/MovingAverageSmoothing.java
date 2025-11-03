package org.nyet.util;

import vec_math.LinearSmoothing;

public class MovingAverageSmoothing extends LinearSmoothing
{
    private boolean extrapolateAtEnd = true;

    public MovingAverageSmoothing(int window)
    {
        window |= 1; // make sure window is odd
        this.cn = new double[window];
        for(int i=0;i<window;i++) {
            this.cn[i]=1.0/window;
        }
        this.nk = (1-window)/2;
        setType();
    }

    /**
     * Enable linear extrapolation for right-side padding when insufficient data is available.
     * When enabled, if we can't pad enough at the end of the range, the missing values
     * will be extrapolated using linear extrapolation from the last few available points.
     * @param extrapolate true to enable extrapolation at end, false to use available data only
     */
    public void setExtrapolateAtEnd(boolean extrapolate) {
        this.extrapolateAtEnd = extrapolate;
    }

    @Override
    protected void setType() { this.type = FIR; }

    /**
     * Smooth a range of data with automatic padding to prevent edge artifacts.
     * Overrides parent method to always pad ranges before smoothing.
     *
     * @param input The complete data array
     * @param start Start index of the range to smooth (inclusive)
     * @param end End index of the range to smooth (inclusive)
     * @return Smoothed data array for the requested range only
     */
    @Override
    public double[] smoothAll(double[] input, int start, int end) {
        final int rangeSize = end - start + 1;
        // nk is negative, so -nk is the number of elements needed before the smoothing index
        // For window=45, nk=-22, so we need 22 elements before index 0 in the padded array
        final int leftPadding = -this.nk;
        // Right padding: window needs cn.length elements total, so right side needs (cn.length - 1 + nk)
        // For window=45: rightPadding = 45 - 1 + (-22) = 22
        final int rightPadding = this.cn.length - 1 + this.nk;
        final int paddedStart = Math.max(0, start - leftPadding);
        final int paddedEndAvailable = Math.min(input.length - 1, end + rightPadding);
        final int paddedEndNeeded = end + rightPadding;
        final boolean needsRightExtrapolation = this.extrapolateAtEnd && paddedEndAvailable < paddedEndNeeded;
        final int numExtrapolatedNeeded = paddedEndNeeded - paddedEndAvailable;

        // Disable extrapolation if we don't have enough points to extrapolate from
        // Need at least 2 points for linear extrapolation (3 for better stability)
        final int pointsAvailableForExtrapolation = paddedEndAvailable - paddedStart + 1;
        final boolean shouldDisableExtrapolation = needsRightExtrapolation && pointsAvailableForExtrapolation < Math.max(2, numExtrapolatedNeeded);

        // Extract padded range (may be extended with extrapolation below)
        final int paddedDataSize = paddedEndAvailable - paddedStart + 1 +
            (needsRightExtrapolation && !shouldDisableExtrapolation ? numExtrapolatedNeeded : 0);
        final double[] paddedData = new double[paddedDataSize];
        System.arraycopy(input, paddedStart, paddedData, 0, paddedEndAvailable - paddedStart + 1);

        // Apply linear extrapolation at end if needed and enabled and we have enough points
        if (needsRightExtrapolation && !shouldDisableExtrapolation) {
            final int extrapolationStart = paddedEndAvailable - paddedStart + 1;
            final int numExtrapolated = paddedEndNeeded - paddedEndAvailable;
            linearExtrapolate(paddedData, extrapolationStart, numExtrapolated, paddedEndAvailable - paddedStart);
        }

        // Calculate which indices in paddedData can be safely smoothed
        // smoothAt(i) requires i + nk >= 0 and i + nk + cn.length <= paddedData.length
        // So: i >= -nk and i <= paddedData.length - cn.length - nk
        final int smoothStart = Math.max(0, -this.nk);
        final int smoothEnd = Math.min(paddedData.length - 1, paddedData.length - this.cn.length - this.nk);

        final double[] smoothedPadded = new double[paddedData.length];
        // Copy original data for indices we can't smooth (edges)
        System.arraycopy(paddedData, 0, smoothedPadded, 0, paddedData.length);

        // Smooth only the valid range
        // smoothStart and smoothEnd are calculated to guarantee valid indices
        if (smoothStart <= smoothEnd) {
            for (int i = smoothStart; i <= smoothEnd; i++) {
                smoothedPadded[i] = this.smoothAt(paddedData, null, i, i);
            }
        }

        // Extract only the requested range from smoothed padded data
        final int offset = start - paddedStart;
        final double[] result = new double[rangeSize];
        System.arraycopy(smoothedPadded, offset, result, 0, rangeSize);
        return result;
    }

    /**
     * Perform linear extrapolation to fill missing values at the end of an array.
     * Uses the last 2-3 points to calculate slope and extrapolates forward.
     * Falls back to repeating the last value if insufficient points are available.
     *
     * @param data The array to extrapolate into (modified in place)
     * @param extrapolationStart The starting index where extrapolation should begin
     * @param numExtrapolated The number of values to extrapolate
     * @param lastDataIdx The index of the last available data point (before extrapolation)
     */
    private void linearExtrapolate(double[] data, int extrapolationStart, int numExtrapolated, int lastDataIdx) {
        // Use last 3 points for linear extrapolation (or fewer if not available)
        final int numPointsForSlope = Math.min(3, lastDataIdx + 1);
        if (numPointsForSlope >= 2) {
            final double lastValue = data[lastDataIdx];
            // Calculate slope from second-to-last to last point
            final double slope = lastDataIdx > 0 ? (data[lastDataIdx] - data[lastDataIdx - 1]) : 0.0;
            // If we have 3+ points, use average slope for better stability
            double avgSlope = slope;
            if (numPointsForSlope >= 3 && lastDataIdx >= 2) {
                final double slope1 = data[lastDataIdx] - data[lastDataIdx - 1];
                final double slope2 = data[lastDataIdx - 1] - data[lastDataIdx - 2];
                avgSlope = (slope1 + slope2) / 2.0;
            }
            // Extrapolate forward
            for (int i = 0; i < numExtrapolated; i++) {
                data[extrapolationStart + i] = lastValue + avgSlope * (i + 1);
            }
        } else {
            // Not enough points for extrapolation, just repeat last value
            final double lastValue = lastDataIdx >= 0 ? data[lastDataIdx] : 0.0;
            for (int i = 0; i < numExtrapolated; i++) {
                data[extrapolationStart + i] = lastValue;
            }
        }
    }
}

// vim: set sw=4 ts=8 expandtab:
