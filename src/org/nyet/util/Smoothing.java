package org.nyet.util;

import vec_math.LinearSmoothing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.nyet.logfile.Dataset;

public class Smoothing extends LinearSmoothing
{
    private static final Logger logger = LoggerFactory.getLogger(Smoothing.class);

    // Smoothing constants for quantization noise reduction
    /** Base moving average window size (in samples) for quantized data smoothing */
    private static final int MA_WINDOW_BASE = 5;
    /** Maximum moving average window size (in samples) - safety limit */
    private static final int MA_WINDOW_MAX = 50;
    /** Multiplier for adaptive MA window size based on quantization run length */
    private static final int MA_QUANTIZATION_MULTIPLIER = 10;
    /** Minimum dataset size (in samples) required for Savitzky-Golay smoothing */
    private static final int SG_MIN_SAMPLES = 11;
    /** Minimum consecutive constant values to consider as quantization noise */
    private static final int MIN_QUANTIZATION_RUN = 3;

    public Smoothing(int window)
    {
        window |= 1; // make sure window is odd
        this.cn = new double[window];

        // Use unweighted (equal) weights: all samples have equal weight
        // For window size 5: weights = [1/5, 1/5, 1/5, 1/5, 1/5]
        final double weight = 1.0 / window;
        for(int i=0;i<window;i++) {
            this.cn[i] = weight;
        }

        // Keep centered window (symmetric) to minimize lag
        this.nk = (1-window)/2;
        setType();
    }

    @Override
    protected void setType() { this.type = FIR; }

    @Override
    public double[] smoothAll(double[] input, int start, int end) {
        final int actualEnd = Math.min(end, input.length - 1);
        final int rangeSize = actualEnd - start + 1;

        // Clamp window to half the range size to prevent issues when window is close to dataset size
        // This ensures we can actually smooth the data effectively without creating edge artifacts
        final int originalWindow = this.cn.length;
        final int effectiveWindow = clampWindowToHalfSize(originalWindow, rangeSize);

        // If window was clamped, create a smoother with the clamped window size
        // Otherwise use this smoother directly
        final Smoothing smoother = (effectiveWindow < originalWindow) ? new Smoothing(effectiveWindow) : this;
        final int windowSize = smoother.cn.length;

        // Calculate bounds using the effective window size
        final int lastSmoothable = input.length - windowSize - smoother.nk - 1;

        final double[] result = new double[rangeSize];

        for (int i = 0; i < rangeSize; i++) {
            final int idx = start + i;
            // Use effective window for bounds check - if window was clamped, fewer points will be smoothable
            // Existing edge handling will return original values for points that can't be smoothed
            if (idx <= lastSmoothable && idx + smoother.nk >= 0 && idx + smoother.nk + windowSize <= input.length) {
                result[i] = smoother.smoothAt(input, null, idx, idx);
            } else if (idx < input.length) {
                result[i] = input[idx];
            }
        }

        return result;
    }

    /**
     * Detect average quantization run length in data.
     * Finds the average length of consecutive constant (or near-constant) value sequences.
     *
     * @param data Array of values to analyze
     * @return Average run length of constant values (rounded to nearest int), or 0 if none found
     */
    private static int detectAverageQuantizationRun(double[] data) {
        if (data == null || data.length < MIN_QUANTIZATION_RUN) {
            return 0;
        }

        int totalRunLength = 0;
        int runCount = 0;
        int currentRun = 1;
        double lastValue = data[0];

        for (int i = 1; i < data.length; i++) {
            // For RPM (integer values), use exact equality with small tolerance for floating point errors
            // Tolerance of 0.5 allows for integer RPM values that might have slight floating point differences
            if (Math.abs(data[i] - lastValue) < 0.5) {
                currentRun++;
            } else {
                // Run ended - include if this was a quantization run
                if (currentRun >= MIN_QUANTIZATION_RUN) {
                    totalRunLength += currentRun;
                    runCount++;
                }
                currentRun = 1;
                lastValue = data[i];
            }
        }

        // Check final run
        if (currentRun >= MIN_QUANTIZATION_RUN) {
            totalRunLength += currentRun;
            runCount++;
        }

        // Calculate average (rounded to nearest int)
        // If no quantization runs found, return 0 (will use base MA window)
        if (runCount > 0) {
            final int avg = (int) Math.round((double) totalRunLength / runCount);
            // Log for debugging
            logger.trace("detectAverageQuantizationRun: {} samples analyzed, {} runs found, avg length: {}",
                data.length, runCount, avg);
            return avg;
        }
        logger.trace("detectAverageQuantizationRun: {} samples analyzed, no quantization runs (>= {}) found",
            data.length, MIN_QUANTIZATION_RUN);
        return 0;
    }

    /**
     * Clamp a smoothing window to half the data size to prevent issues when window is close to dataset size.
     * This ensures the window is never larger than half the data size, which prevents edge artifacts.
     *
     * @param window The window size to clamp
     * @param dataSize The size of the data (dataset or range)
     * @return Clamped window size (always odd, at most half the data size)
     */
    public static int clampWindowToHalfSize(int window, int dataSize) {
        final int maxWindow = Math.max(1, dataSize / 2);
        if (window > maxWindow) {
            window = maxWindow;
            // Ensure window is odd (required by Smoothing constructor)
            window |= 1;
        }
        return window;
    }

    /**
     * Calculate adaptive MA window size based on quantization detection.
     *
     * @param avgQuantizationRun Average quantization run length detected
     * @return Adaptive window size (clamped between MA_WINDOW_BASE and MA_WINDOW_MAX, always odd)
     */
    private static int calculateAdaptiveMAWindow(int avgQuantizationRun) {
        int maWindow = MA_WINDOW_BASE;
        if (avgQuantizationRun >= MIN_QUANTIZATION_RUN) {
            maWindow = avgQuantizationRun * MA_QUANTIZATION_MULTIPLIER;
            // Clamp to reasonable bounds and ensure window is odd (required by Smoothing)
            if (maWindow < MA_WINDOW_BASE) {
                maWindow = MA_WINDOW_BASE;
            } else if (maWindow > MA_WINDOW_MAX) {
                maWindow = MA_WINDOW_MAX;
            }
            maWindow |= 1;  // Ensure odd
        }
        return maWindow;
    }

    /**
     * Apply appropriate smoothing based on quantization detection.
     * Automatically detects quantization and chooses MA for quantized data, SG for smooth data.
     *
     * @param data Data to smooth (full dataset)
     * @param ranges Valid ranges to analyze for quantization (null = analyze full dataset)
     * @param columnName Column name for logging
     * @return Smoothed data
     */
    public static DoubleArray smoothAdaptive(DoubleArray data, java.util.ArrayList<? extends Dataset.Range> ranges,
                                             String columnName) {
        final double[] rawData = data.toArray();

        // Only detect quantization within valid ranges (if provided)
        // This prevents false positives from idle/deceleration periods where RPM is constant
        // Note: In acceleration runs, RPM is increasing, so quantization may appear as plateaus
        // during rapid increases rather than constant values
        final int avgQuantizationRun;
        if (ranges != null && !ranges.isEmpty()) {
            // Extract data only from valid ranges for quantization detection
            java.util.ArrayList<Double> rangeData = new java.util.ArrayList<>();
            for (Dataset.Range r : ranges) {
                for (int i = r.start; i <= r.end && i < rawData.length; i++) {
                    rangeData.add(rawData[i]);
                }
            }
            if (rangeData.isEmpty()) {
                avgQuantizationRun = 0;
            } else {
                final double[] rangeArray = new double[rangeData.size()];
                for (int i = 0; i < rangeData.size(); i++) {
                    rangeArray[i] = rangeData.get(i);
                }
                avgQuantizationRun = detectAverageQuantizationRun(rangeArray);
            }
        } else {
            // No ranges provided - analyze full dataset (fallback for filter disabled)
            avgQuantizationRun = detectAverageQuantizationRun(rawData);
        }

        int maWindow = calculateAdaptiveMAWindow(avgQuantizationRun);
        final int datasetSize = data.size();

        if (avgQuantizationRun >= MIN_QUANTIZATION_RUN) {
            // Quantization detected: use MA then SG (MA reduces quantization steps, SG preserves trends)
            // Note: smoothAll() will automatically clamp the window to half the data size
            final int minSizeForMA = 2 * maWindow;  // Require 2x MA window size
            if (datasetSize >= minSizeForMA) {
                // Step 1: Apply MA to reduce quantization steps
                // IMPORTANT: When ranges are provided, avoid using data from before range starts to prevent
                // artifacts where idle/deceleration data pulls down values at range start. However, still
                // smooth the full dataset - just don't use pre-range data when smoothing within ranges.
                // Right edge can extend beyond range end if data is available.
                final Smoothing maSmoother = new Smoothing(maWindow);
                final double[] input = rawData;
                double[] maSmoothed;

                if (ranges != null && !ranges.isEmpty()) {
                    // Initialize with raw data (will be smoothed per-range)
                    maSmoothed = new double[input.length];
                    System.arraycopy(input, 0, maSmoothed, 0, input.length);

                    // For each range: apply MA smoothing (smoothAll will automatically clamp window to range size)
                    for (Dataset.Range r : ranges) {
                        final double[] rangeSmoothed = maSmoother.smoothAll(input, r.start, r.end);
                        System.arraycopy(rangeSmoothed, 0, maSmoothed, r.start, r.end - r.start + 1);
                    }
                } else {
                    // No ranges: apply MA smoothing to full dataset (smoothAll will automatically clamp window)
                    maSmoothed = maSmoother.smoothAll(input, 0, input.length - 1);
                }

                // Step 2: Apply SG smoothing to preserve trends after MA
                final DoubleArray maSmoothedArray = new DoubleArray(maSmoothed);
                final DoubleArray finalSmoothed = maSmoothedArray.smooth();
                return finalSmoothed;
            }
            // If dataset size < 2*maWindow, fall back to SG only if possible
            if (datasetSize >= SG_MIN_SAMPLES) {
                return data.smooth();
            }
            // If dataset size < SG_MIN_SAMPLES, return original data (no smoothing)
            return data;
        } else {
            // No quantization detected: use SG only (preserves features better for smooth data)
            if (datasetSize >= SG_MIN_SAMPLES) {
                return data.smooth();
            }
            // If dataset size < SG_MIN_SAMPLES, return original data (no smoothing)
            return data;
        }
    }

}

// vim: set sw=4 ts=8 expandtab:

