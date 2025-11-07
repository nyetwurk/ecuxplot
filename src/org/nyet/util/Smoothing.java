package org.nyet.util;

import vec_math.LinearSmoothing;
import vec_math.SavitzkyGolaySmoothing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.nyet.logfile.Dataset;

public class Smoothing extends LinearSmoothing
{
    /**
     * Padding method for smoothing operations.
     */
    public enum Padding {
        NONE("None"),
        MIRROR("Mirror"),
        DATA("Data");

        private final String value;

        Padding(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }

        public static Padding fromString(String s) {
            if (s == null) return NONE;
            // Case-insensitive matching for backward compatibility
            String sLower = s.toLowerCase();
            for (Padding method : values()) {
                if (method.value.toLowerCase().equals(sLower)) {
                    return method;
                }
            }
            return NONE;
        }
    }

    /**
     * Smoothing strategy for post-differentiation data.
     */
    public enum Strategy {
        MAW("MAW"),
        SG("SG");

        private final String value;

        Strategy(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static Strategy fromString(String s) {
            if (s == null) return MAW;
            for (Strategy strategy : values()) {
                if (strategy.value.equals(s)) {
                    return strategy;
                }
            }
            return MAW;
        }
    }

    /**
     * Get default left padding for a given strategy.
     * MAW works best with DATA, SG works best with NONE.
     */
    public static Padding getDefaultLeftPadding(Strategy strategy) {
        if (strategy == Strategy.MAW) {
            return Padding.DATA;
        }
        // SG defaults to NONE
        return Padding.NONE;
    }

    /**
     * Get default right padding for a given strategy.
     * MAW works best with DATA, SG works best with MIRROR.
     */
    public static Padding getDefaultRightPadding(Strategy strategy) {
        if (strategy == Strategy.MAW) {
            return Padding.DATA;
        }
        // SG defaults to MIRROR
        return Padding.MIRROR;
    }

    /**
     * Encapsulates left and right padding configuration.
     */
    public static class PaddingConfig {
        public final Padding left;
        public final Padding right;

        public PaddingConfig(Padding left, Padding right) {
            this.left = left;
            this.right = right;
        }

        /**
         * Create PaddingConfig with default padding for the given strategy.
         */
        public static PaddingConfig forStrategy(Strategy strategy) {
            return new PaddingConfig(
                getDefaultLeftPadding(strategy),
                getDefaultRightPadding(strategy)
            );
        }

        /**
         * Check if both sides use the same padding method.
         */
        public boolean isSymmetric() {
            return left == right;
        }

        @Override
        public String toString() {
            return left.getValue() + "/" + right.getValue();
        }
    }

    /**
     * Simple range with start and end indices.
     */
    public static class Range {
        public final int start;
        public final int end;
        public final int size;

        public Range(int start, int end) {
            this.start = start;
            this.end = end;
            this.size = end - start + 1;
        }
    }

    /**
     * Encapsulates padded data array and range information.
     */
    public static class PaddedRange {
        public final double[] paddedData;
        public final Range range;

        PaddedRange(double[] paddedData, int rangeStartInPadded, int rangeEndInPadded) {
            this.paddedData = paddedData;
            this.range = new Range(rangeStartInPadded, rangeEndInPadded);
        }
    }

    /**
     * Encapsulates all smoothing configuration.
     */
    public static class SmoothingContext {
        public final int originalWindow;
        public final int effectiveWindow;
        public final Strategy strategy;
        public final Padding leftPad;
        public final Padding rightPad;
        public final int paddingNeeded;

        SmoothingContext(int originalWindow, int effectiveWindow, Strategy strategy,
                         Padding leftPad, Padding rightPad, int paddingNeeded) {
            this.originalWindow = originalWindow;
            this.effectiveWindow = effectiveWindow;
            this.strategy = strategy;
            this.leftPad = leftPad;
            this.rightPad = rightPad;
            this.paddingNeeded = paddingNeeded;
        }
    }

    /**
     * Encapsulates smoothed data result.
     */
    public static class SmoothingResult {
        public final double[] smoothedData;
        public final int rangeStartInResult;
        public final int rangeSize;

        SmoothingResult(double[] smoothedData, int rangeStartInResult, int rangeSize) {
            this.smoothedData = smoothedData;
            this.rangeStartInResult = rangeStartInResult;
            this.rangeSize = rangeSize;
        }

        public double[] extractRange() {
            double[] result = new double[rangeSize];
            System.arraycopy(smoothedData, rangeStartInResult, result, 0, rangeSize);
            return result;
        }
    }

    /**
     * Metadata for range-aware smoothing configuration.
     */
    public static class Metadata {
        public final int windowSize;

        public Metadata(int windowSize) {
            this.windowSize = windowSize;
        }
    }

    /**
     * Encapsulates window boundary calculations for a smoothing operation.
     */
    private static class WindowBoundaries {
        final int minWindowStart;
        final int maxWindowEnd;
        final int arrayFirstSmoothable;
        final int arrayLastSmoothable;

        WindowBoundaries(Range range, SmoothingContext ctx, int windowSize, int nk, int arrayLength) {
            this.minWindowStart = (ctx.leftPad != Padding.NONE) ? 0 : range.start;
            this.maxWindowEnd = (ctx.rightPad != Padding.NONE)
                ? range.end + ctx.paddingNeeded
                : range.end;
            this.arrayFirstSmoothable = nk >= 0 ? 0 : -nk;
            this.arrayLastSmoothable = arrayLength - windowSize - nk - 1;
        }

        boolean isRightSidePoint(int pointIdx, int windowSize, Range range) {
            return pointIdx >= range.end - windowSize;
        }

        int pointMinWindowStart(boolean isRightSide, SmoothingContext ctx, Range range) {
            return (!isRightSide && ctx.leftPad != Padding.NONE)
                ? minWindowStart
                : range.start;
        }

        int pointMaxWindowEnd(boolean isRightSide, SmoothingContext ctx, Range range) {
            return (isRightSide && ctx.rightPad != Padding.NONE)
                ? maxWindowEnd
                : range.end;
        }

        int pointLastSmoothable(boolean isRightSide, SmoothingContext ctx, Range range,
                               int windowSize, int nk) {
            if (isRightSide && ctx.rightPad != Padding.NONE) {
                return Math.min(arrayLastSmoothable,
                    Math.max(range.end, pointMaxWindowEnd(isRightSide, ctx, range) - windowSize - nk));
            } else {
                return Math.min(arrayLastSmoothable, range.end - windowSize - nk);
            }
        }
    }

    /**
     * Encapsulates per-point smoothing calculations and checks.
     */
    private static class PointSmoothingInfo {
        final int pointIdx;
        final int windowStart;
        final int windowEnd;
        final boolean isRightSide;
        final boolean canSmooth;

        PointSmoothingInfo(int i, Range range, WindowBoundaries bounds, SmoothingContext ctx,
                          int windowSize, int nk, int arrayLength) {
            this.pointIdx = range.start + i;
            this.windowStart = pointIdx + nk;
            this.windowEnd = pointIdx + nk + windowSize - 1;
            this.isRightSide = bounds.isRightSidePoint(pointIdx, windowSize, range);

            final int pointMinWindowStart = bounds.pointMinWindowStart(isRightSide, ctx, range);
            final int pointMaxWindowEnd = bounds.pointMaxWindowEnd(isRightSide, ctx, range);
            final int pointLastSmoothable = bounds.pointLastSmoothable(isRightSide, ctx, range, windowSize, nk);

            this.canSmooth = pointIdx >= bounds.arrayFirstSmoothable &&
                           pointIdx <= pointLastSmoothable &&
                           windowStart >= pointMinWindowStart &&
                           windowEnd < arrayLength &&
                           windowEnd <= pointMaxWindowEnd;
        }
    }

    /**
     * Encapsulates SG-specific boundary calculations.
     */
    private static class SGBoundaries {
        final int sgMaxEnd;
        final WindowBoundaries base;

        SGBoundaries(PaddedRange padded, SmoothingContext ctx) {
            this.sgMaxEnd = padded.paddedData.length - SG_WINDOW_SIZE + SG_NK;
            this.base = new WindowBoundaries(padded.range, ctx, SG_WINDOW_SIZE, SG_NK, padded.paddedData.length);
        }

        boolean canUseSG(int pointIdx, int windowStart, int windowEnd, Range range,
                        SmoothingContext ctx, boolean isRightSide) {
            final int pointMinWindowStart = base.pointMinWindowStart(isRightSide, ctx, range);
            final int pointMaxWindowEnd = base.pointMaxWindowEnd(isRightSide, ctx, range);
            final boolean windowRespectsBoundaries = isRightSide
                ? (windowStart >= range.start)
                : (windowEnd <= range.end);
            final int arrayLength = base.arrayLastSmoothable + SG_WINDOW_SIZE + SG_NK + 1;

            return pointIdx >= SG_MIN_START &&
                   (pointIdx <= sgMaxEnd || (isRightSide && ctx.rightPad != Padding.NONE)) &&
                   windowStart >= pointMinWindowStart &&
                   windowEnd < arrayLength &&
                   windowEnd <= pointMaxWindowEnd &&
                   windowRespectsBoundaries;
        }
    }

    /**
     * Encapsulates padding application logic.
     */
    private static class PaddingApplier {
        static void applyLeft(double[] dataToSmooth, double[] rangeData, double[] fullData,
                             Dataset.Range range, Padding leftPad, int leftPadSize, int rangeSize) {
            if (leftPadSize == 0) return;

            if (leftPad == Padding.MIRROR) {
                for (int i = 0; i < leftPadSize; i++) {
                    int srcIdx = Math.min(i, rangeSize - 1);
                    dataToSmooth[leftPadSize - 1 - i] = rangeData[srcIdx];
                }
            } else if (leftPad == Padding.DATA) {
                for (int i = 0; i < leftPadSize; i++) {
                    int srcIdx = Math.max(0, range.start - leftPadSize + i);
                    dataToSmooth[i] = fullData[srcIdx];
                }
            }
        }

        static void applyRight(double[] dataToSmooth, double[] rangeData, double[] fullData,
                              Dataset.Range range, Padding rightPad, int rightPadSize,
                              int leftPadSize, int rangeSize) {
            if (rightPadSize == 0) return;

            if (rightPad == Padding.MIRROR) {
                for (int i = 0; i < rightPadSize; i++) {
                    int srcIdx = Math.max(rangeSize - 1 - i, 0);
                    dataToSmooth[leftPadSize + rangeSize + i] = rangeData[srcIdx];
                }
            } else if (rightPad == Padding.DATA) {
                for (int i = 0; i < rightPadSize; i++) {
                    int srcIdx = Math.min(fullData.length - 1, range.end + 1 + i);
                    dataToSmooth[leftPadSize + rangeSize + i] = fullData[srcIdx];
                }
            }
        }
    }

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
    private static final int CLAMP_RATIO_DENOMINATOR = 2;
    private static final int SG_WINDOW_SIZE = 11;
    private static final int SG_NK = -5;
    private static final int SG_MIN_START = 5;

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

    /**
     * Apply MAW smoothing to entire data array.
     */
    public double[] apply(double[] input) {
        return applyToRange(input, 0, input.length - 1);
    }

    /**
     * Apply MAW smoothing to a sub-range of data.
     * Simple version without padding - window cannot extend beyond range boundaries.
     */
    public double[] applyToRange(double[] input, int rangeStart, int rangeEnd) {
        SmoothingResult result = applyToPaddedRange(
            new PaddedRange(input, rangeStart, rangeEnd),
            new SmoothingContext(0, this.cn.length, Strategy.MAW,
                                Padding.NONE, Padding.NONE, 0));
        return result.extractRange();
    }

    /**
     * Apply MAW smoothing to padded range.
     */
    public SmoothingResult applyToPaddedRange(PaddedRange padded, SmoothingContext ctx) {
        final double[] input = padded.paddedData;
        final Range range = padded.range;
        final int windowSize = this.cn.length;
        final WindowBoundaries bounds = new WindowBoundaries(range, ctx, windowSize, this.nk, input.length);
        final double[] result = new double[range.size];

        for (int i = 0; i < range.size; i++) {
            final PointSmoothingInfo info = new PointSmoothingInfo(i, range, bounds, ctx, windowSize, this.nk, input.length);
            if (info.canSmooth) {
                result[i] = this.smoothAt(input, null, info.pointIdx, info.pointIdx);
            } else if (info.pointIdx < input.length) {
                result[i] = input[info.pointIdx];
            }
        }

        return new SmoothingResult(result, 0, range.size);
    }

    /**
     * Apply SG smoothing to padded range.
     */
    public static SmoothingResult applySGToPaddedRange(PaddedRange padded, SmoothingContext ctx) {
        final SavitzkyGolaySmoothing sg = new SavitzkyGolaySmoothing(5, 5);
        final Range range = padded.range;
        final SGBoundaries bounds = new SGBoundaries(padded, ctx);
        final double[] result = new double[range.size];
        int mawFallbackCount = 0;
        final int mawFallbackWindow = Math.min(7, ctx.effectiveWindow);
        final Smoothing maw = new Smoothing(mawFallbackWindow);

        for (int i = 0; i < range.size; i++) {
            final int pointIdx = range.start + i;
            final int windowStart = pointIdx + SG_NK;
            final int windowEnd = pointIdx + SG_NK + SG_WINDOW_SIZE - 1;
            final boolean isRightSide = bounds.base.isRightSidePoint(pointIdx, SG_WINDOW_SIZE, range);

            if (bounds.canUseSG(pointIdx, windowStart, windowEnd, range, ctx, isRightSide)) {
                result[i] = sg.smoothAll(padded.paddedData, pointIdx, pointIdx)[0];
            } else {
                result[i] = fallbackToMAW(maw, padded, range, ctx, isRightSide, mawFallbackWindow, i);
                mawFallbackCount++;
            }
        }

        if (mawFallbackCount > 0) {
            logger.debug("applySGToPaddedRange: SG falling back to MAW at {} of {} points",
                mawFallbackCount, range.size);
        }

        return new SmoothingResult(result, 0, range.size);
    }

    private static double fallbackToMAW(Smoothing maw, PaddedRange padded, Range range,
                                       SmoothingContext ctx, boolean isRightSide, int mawFallbackWindow, int i) {
        final Smoothing.Padding mawLeftPad = (!isRightSide && ctx.leftPad != Padding.NONE) ? ctx.leftPad : Padding.NONE;
        final Smoothing.Padding mawRightPad = (isRightSide && ctx.rightPad != Padding.NONE) ? ctx.rightPad : Padding.NONE;
        final SmoothingContext mawCtx = new SmoothingContext(
            mawFallbackWindow, mawFallbackWindow, Strategy.MAW,
            mawLeftPad, mawRightPad, ctx.paddingNeeded);
        final PaddedRange mawPadded = new PaddedRange(padded.paddedData, range.start, range.end);
        return maw.applyToPaddedRange(mawPadded, mawCtx).extractRange()[i];
    }

    /**
     * Apply smoothing strategy to padded range.
     */
    public static SmoothingResult applyStrategyToPaddedRange(
            PaddedRange padded,
            SmoothingContext ctx,
            String columnName) {

        if (ctx.strategy == Strategy.MAW) {
            final Smoothing smoother = new Smoothing(ctx.effectiveWindow);
            return smoother.applyToPaddedRange(padded, ctx);
        }

        final boolean useSG = (ctx.strategy == Strategy.SG)
            && padded.range.size >= SG_MIN_SAMPLES;

        if (!useSG) {
            final Smoothing smoother = new Smoothing(ctx.effectiveWindow);
            return smoother.applyToPaddedRange(padded, ctx);
        }

        return applySGToPaddedRange(padded, ctx);
    }

    /**
     * Prepare padded range from full dataset and range.
     */
    public static PaddedRange preparePaddedRange(
            double[] fullData,
            Dataset.Range range,
            Padding leftPad,
            Padding rightPad,
            int paddingNeeded) {

        final int rangeSize = range.end - range.start + 1;
        final int leftPadSize = (leftPad != Padding.NONE && paddingNeeded > 0) ? paddingNeeded : 0;
        final int rightPadSize = (rightPad != Padding.NONE && paddingNeeded > 0) ? paddingNeeded : 0;
        final boolean needsPadding = leftPadSize > 0 || rightPadSize > 0;

        if (!needsPadding) {
            return new PaddedRange(fullData, range.start, range.end);
        }

        if (leftPad == Padding.DATA && rightPad == Padding.DATA) {
            return new PaddedRange(fullData, range.start, range.end);
        }

        final int totalPadSize = leftPadSize + rightPadSize;
        final double[] dataToSmooth = new double[rangeSize + totalPadSize];
        final double[] rangeData = new double[rangeSize];
        System.arraycopy(fullData, range.start, rangeData, 0, rangeSize);
        System.arraycopy(rangeData, 0, dataToSmooth, leftPadSize, rangeSize);

        PaddingApplier.applyLeft(dataToSmooth, rangeData, fullData, range, leftPad, leftPadSize, rangeSize);
        PaddingApplier.applyRight(dataToSmooth, rangeData, fullData, range, rightPad, rightPadSize, leftPadSize, rangeSize);

        return new PaddedRange(dataToSmooth, leftPadSize, leftPadSize + rangeSize - 1);
    }

    /**
     * Create smoothing context from metadata and configuration.
     */
    public static SmoothingContext createSmoothingContext(
            Metadata metadata,
            int rangeSize,
            Strategy strategy,
            Padding leftPadding,
            Padding rightPadding) {

        final int originalWindow = metadata.windowSize;
        final int effectiveWindow = clampWindow(metadata.windowSize, rangeSize);
        final Padding leftPad = leftPadding;
        final Padding rightPad = rightPadding;
        final int paddingNeeded = (strategy == Strategy.SG)
            ? 5
            : (effectiveWindow - 1) / 2;

        return new SmoothingContext(originalWindow, effectiveWindow, strategy,
                                   leftPad, rightPad, paddingNeeded);
    }

    /**
     * Main entry point.
     */
    public static double[] applySmoothing(
            Dataset.Column column,
            String columnName,
            Dataset.Range r,
            Metadata metadata,
            Strategy smoothingStrategy,
            Padding leftPadding,
            Padding rightPadding,
            Logger logger) {

        if (metadata == null || metadata.windowSize <= 0) {
            return column.data.toArray(r.start, r.end);
        }

        final int rangeSize = r.end - r.start + 1;
        final int effectiveWindow = clampWindow(metadata.windowSize, rangeSize);
        if (effectiveWindow != metadata.windowSize) {
            logger.debug("getData('{}'): Clamped smoothing window from {} to {} (range size {})",
                columnName, metadata.windowSize, effectiveWindow, rangeSize);
        }

        if (rangeSize < effectiveWindow) {
            logger.warn("getData('{}'): Skipping smoothing - range size {} is smaller than window {}",
                columnName, rangeSize, effectiveWindow);
            return column.data.toArray(r.start, r.end);
        }

        SmoothingContext ctx = createSmoothingContext(
            metadata, rangeSize, smoothingStrategy, leftPadding, rightPadding);

        PaddedRange padded = preparePaddedRange(
            column.data.toArray(), r, ctx.leftPad, ctx.rightPad, ctx.paddingNeeded);

        SmoothingResult result = applyStrategyToPaddedRange(padded, ctx, columnName);

        return result.extractRange();
    }

    // ========== ADAPTIVE SMOOTHING (for RPM) ==========

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
    public static int clampWindow(int window, int dataSize) {
        final int maxWindow = Math.max(1, dataSize / CLAMP_RATIO_DENOMINATOR);
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

        final int originalMaWindow = maWindow;
        maWindow = clampWindow(maWindow, datasetSize);
        if (maWindow != originalMaWindow) {
            logger.debug("smoothAdaptive('{}'): Clamped adaptive MA window from {} to {} (1/2 of dataset size {})",
                columnName, originalMaWindow, maWindow, datasetSize);
        }

        if (avgQuantizationRun >= MIN_QUANTIZATION_RUN) {
            // Quantization detected: use MA then SG (MA reduces quantization steps, SG preserves trends)
            // Note: applyToRange() will automatically clamp the window to half the data size
            final int minSizeForMA = 2 * maWindow;  // Require 2x MA window size
            if (datasetSize >= minSizeForMA) {
                // Step 1: Apply MA to reduce quantization steps
                // IMPORTANT: When ranges are provided, avoid using data from before range starts to prevent
                // artifacts where idle/deceleration data pulls down values at range start. However, still
                // smooth the full dataset - just don't use pre-range data when smoothing within ranges.
                // Right edge can extend beyond range end if data is available.
                final Smoothing maSmoother = new Smoothing(maWindow);
                double[] maSmoothed;

                if (ranges != null && !ranges.isEmpty()) {
                    // Initialize with raw data (will be smoothed per-range)
                    maSmoothed = new double[rawData.length];
                    System.arraycopy(rawData, 0, maSmoothed, 0, rawData.length);

                    // For each range: apply MA smoothing (applyToRange will automatically clamp window to range size)
                    for (Dataset.Range r : ranges) {
                        final double[] rangeSmoothed = maSmoother.applyToRange(rawData, r.start, r.end);
                        System.arraycopy(rangeSmoothed, 0, maSmoothed, r.start, r.end - r.start + 1);
                    }
                } else {
                    // No ranges: apply MA smoothing to full dataset (applyToRange will automatically clamp window)
                    maSmoothed = maSmoother.applyToRange(rawData, 0, rawData.length - 1);
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
