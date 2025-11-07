# Smoothing Architecture and Implementation

## Overview

The smoothing system handles quantization noise, boundary artifacts, and circular dependencies through a three-tier RPM architecture and range-aware smoothing with configurable padding and strategies.

## Architecture

### Three-Tier RPM Architecture

Resolves a circular dependency where range detection requires smoothed RPM, but RPM smoothing requires ranges for quantization detection.

**Layers:**
1. **CSV RPM** (`csvRpm`): Raw data from CSV file, no smoothing
2. **Base RPM** (`baseRpm`): SG smoothing only, used for range detection (doesn't require ranges)
3. **Final RPM** (`rpm`): Adaptive smoothing (MAW+SG for quantized, SG for smooth), uses ranges for quantization detection

**Implementation:**
- `dataValid()` uses `baseRpm` (SG-only, no ranges needed) for range detection
- `buildRanges()` executes before final RPM creation
- Final RPM uses ranges for quantization detection, breaking the circular dependency
- Separation: `baseRpm` for filtering, final RPM for calculations/display

### Range-Aware Smoothing

Prevents edge artifacts when data windows are truncated by ranges. Applied in `Smoothing.applySmoothing()` (static method in `Smoothing.java`), which is called by `getData()` for display/rendering. See [getData() vs _get(): Smoothing Application](#getdata-vs-_get-smoothing-application) for details on when smoothing is applied.

**Execution Flow:**
1. Check if column is registered in `smoothingWindows` map
2. Clamp window size: `clampWindow(windowSize, rangeSize)` → max 1/2 of range size
3. Create `SmoothingContext` with strategy, padding, and padding size
4. Prepare padded range: `preparePaddedRange()` with configurable left/right padding
5. Apply smoothing strategy: `applyStrategyToPaddedRange()` (MAW or SG)
6. Extract range portion from smoothed result

**Key Classes:**
- `Smoothing.Metadata`: Stores window size (all registered columns are post-diff)
- `Smoothing.PaddingConfig`: Encapsulates left/right padding configuration
- `Smoothing.SmoothingContext`: Encapsulates all smoothing configuration
- `Smoothing.PaddedRange`: Encapsulates padded data array and range information
- `Smoothing.SmoothingResult`: Encapsulates smoothed data result
- `ECUxDataset.SmoothingWindowsMap`: Custom `HashMap<String, Metadata>` with `put(String, double)` overload for seconds-to-samples conversion

## Smoothing Algorithms

### DoubleArray.smooth()

Automatic smoothing selection based on dataset size:
- **Too few points** (`length < MIN_POINTS_FOR_SMOOTHING`): Returns original data (no smoothing)
- **Small datasets** (`MIN_POINTS_FOR_SMOOTHING <= length < SG_THRESHOLD`): Moving Average with window = `length / MA_WINDOW_DIVISOR`
- **Large datasets** (`length >= SG_THRESHOLD`): Savitzky-Golay (5,5) polynomial smoothing

### DoubleArray.derivative(x, window)

- **window = 0**: No smoothing, raw derivative
- **window > 0**: Moving average smoothing on derivative with specified window

### Adaptive RPM Smoothing

Handles quantization noise in RPM data by detecting quantization runs and applying appropriate smoothing.

**Location**: `Smoothing.smoothAdaptive()` (static method)

**Algorithm:**
1. Detect quantization: `detectAverageQuantizationRun()` - finds average run length of consecutive constant values
2. Calculate adaptive window: `calculateAdaptiveMAWindow()` - `avgRunLength * MA_QUANTIZATION_MULTIPLIER` (clamped to `MA_WINDOW_BASE`-`MA_WINDOW_MAX`)
3. Apply MAW smoothing per-range (if quantized) or skip (if smooth)
4. Apply SG smoothing to full dataset
5. Clamp adaptive window to 1/2 of dataset size

**Quantization Detection:**
- Detects consecutive values within `QUANTIZATION_TOLERANCE` (handles integer RPM with floating-point differences)
- Returns average run length, not magnitude
- Window size is proportional to **run length**, not quantization step size

### Range-Aware Smoothing (getData())

Applied to all registered post-diff columns via `Smoothing.applySmoothing()` when accessed through `getData()`. See [getData() vs _get(): Smoothing Application](#getdata-vs-_get-smoothing-application) for the distinction between `getData()` (smoothed) and `_get()`/`get()` (raw):
- **Strategy**: MAW (default) or SG (configurable)
- **Padding**: Configurable (NONE, MIRROR, DATA) per side
- **Window**: From `smoothingWindows` map (AccelMAW() or HPMAW())
- **Clamping**: 1/2 of range size (before padding)

### Time Delta Smoothing

Smooths time intervals (deltas) rather than absolute time. Prevents drift while reducing jitter. Used for: TIME column.

## Configuration and Constants

### User-Configurable Parameters

**In `ECUxDataset`**:
- `postDiffSmoothingStrategy`: `Strategy.MAW` (default) or `Strategy.SG`
- `padding`: `PaddingConfig` with left/right padding (default: DATA/DATA for MAW, NONE/MIRROR for SG)
- `filter.HPMAW()`: Window size in seconds for HP/WHP/TQ/WTQ smoothing (converted to samples internally)
- `filter.AccelMAW()`: Window size in seconds for Acceleration smoothing (converted to samples internally)

**Defaults:**
- **Strategy**: MAW
- **Padding (MAW)**: DATA/DATA
- **Padding (SG)**: NONE/MIRROR
- **Clamping**: 1/2 of range size (before padding)

### Smoothing Strategies

**Configurable Options** (via `Smoothing.Strategy` enum):
- `MAW`: Moving Average Window (default)
- `SG`: Savitzky-Golay filter (preserves inflection points)

**Strategy Selection:**
- Configured via `ECUxDataset.postDiffSmoothingStrategy`
- Applied in `Smoothing.applyStrategyToPaddedRange()`
- SG requires minimum `SG_MIN_SAMPLES` (11), falls back to MAW for smaller datasets

### Padding Strategies

**Configurable Options** (via `Smoothing.Padding` enum):
- `NONE`: No padding
- `MIRROR`: Reflect data at boundaries (reverse order)
- `DATA`: Extend from full dataset (use actual data beyond range)

**Default Padding** (via `Smoothing.PaddingConfig.forStrategy()`):
- **MAW**: `DATA/DATA` (left=data, right=data)
- **SG**: `NONE/MIRROR` (left=none, right=mirror)

**Padding Size Calculation:**
- **SG**: Fixed at 5 points (required for SG(5,5))
- **MAW**: `(effectiveWindow - 1) / 2`

**Implementation**: `Smoothing.preparePaddedRange()`
- Handles all padding combinations (NONE/NONE, MIRROR/MIRROR, DATA/DATA, mixed)
- Optimized path for DATA/DATA (uses full dataset directly)
- Creates separate padded array for MIRROR padding

### Window Clamping

**Implementation**: `Smoothing.clampWindow(int window, int dataSize)`
- **Ratio**: 1/2 of data size (`CLAMP_RATIO_DENOMINATOR = 2`)
- **Clamping based on**: Range size (before padding) to avoid circular dependency
- **Applied to**: All range-aware smoothing and adaptive RPM smoothing
- **Logic**: Clamps window to `max(1, dataSize / CLAMP_RATIO_DENOMINATOR)`, ensures odd

### Hard-Coded Constants

**Window Clamping** (`Smoothing.java`):
- `CLAMP_RATIO_DENOMINATOR = 2`: Maximum window size is 1/2 of data size (before padding)

**Adaptive RPM Smoothing** (`Smoothing.java`):
- `MA_WINDOW_BASE = 5`: Minimum adaptive MAW window size (samples)
- `MA_WINDOW_MAX = 50`: Maximum adaptive MAW window size (samples)
- `MA_QUANTIZATION_MULTIPLIER = 10`: Adaptive window = quantization run length × 10
- `MIN_QUANTIZATION_RUN = 3`: Minimum consecutive constant values to detect quantization
- `QUANTIZATION_TOLERANCE = 0.5`: Tolerance for detecting constant values (handles integer RPM with floating-point differences)

**Savitzky-Golay Filter** (`Smoothing.java`):
- `SG_WINDOW_SIZE = 11`: Window size for SG(5,5) filter (samples)
- `SG_NK = -5`: Negative offset for SG filter
- `SG_MIN_START = 5`: Minimum start index for SG filter
- `SG_MIN_SAMPLES = 11`: Minimum dataset size required for SG smoothing (falls back to MAW if smaller)

**DoubleArray.smooth()** (`DoubleArray.java`):
- `MIN_POINTS_FOR_SMOOTHING = 4`: Minimum points required for any smoothing (returns original data if smaller)
- `SG_THRESHOLD = 10`: Minimum points for SG smoothing (uses MAW if `sp < SG_THRESHOLD`)
- `MA_WINDOW_DIVISOR = 4`: MAW window for small datasets = `sp / MA_WINDOW_DIVISOR` (when `sp < SG_THRESHOLD`)

**Fixed-Time Smoothing Windows** (`ECUxDataset.java`):
- **Boost Spool Rate (time)**: `1.0` seconds (hard-coded, not user-configurable)
- **LDR de/dt**: `1.0` seconds (hard-coded, not user-configurable)

## Registered Columns

**All registered columns are post-differentiation data:**
- Acceleration (RPM/s)
- Acceleration (m/s^2)
- WHP (Wheel Horsepower)
- HP (Engine Horsepower)
- WTQ (Wheel Torque)
- TQ (Engine Torque)
- WTQ (Nm) / TQ (Nm)
- Zeitronix Boost
- Boost Spool Rate (time) - registered with 1.0 second window (hard-coded)
- LDR de/dt - registered with 1.0 second window (hard-coded)

**Registration**: `smoothingWindows.put(columnName, windowSizeInSeconds)`
- Uses custom `SmoothingWindowsMap` class that extends `HashMap<String, Metadata>`
- Provides `put(String, double)` overload that accepts window size in seconds and converts to samples internally
- Provides `put(String, int)` overload for backward compatibility (accepts samples directly)
- Window sizes: `AccelMAW()` or `HPMAW()` (user-configurable, specified in seconds)

**Unit Conversion Inheritance:**
- `getColumnInUnits()` automatically inherits smoothing registration from the base column when creating unit-converted columns (e.g., "WTQ (Nm)" inherits from "WTQ")
- Implementation: `getColumnInUnits()` checks `smoothingWindows.get(columnName)` and copies registration to `targetId` if found

## getData() vs _get(): Smoothing Application

**Critical Distinction:**
- `_get()` / `get()`: Creates/retrieves `Column` objects with raw or calculated data (no smoothing applied)
- `getData()`: Returns `double[]` arrays with range-aware smoothing applied if column is registered

**How It Works:**
1. `_get()` creates columns during column calculation (e.g., in `ECUxDataset._get()`)
   - Columns contain raw/calculated data in `Column.data` (DoubleArray)
   - Smoothing registration (`smoothingWindows.put()`) does NOT affect `_get()` behavior
   - Columns created by `_get()` are accessible via `get()` but contain unsmoothed data

2. `getData()` applies smoothing for display/rendering:
   - Calls `get()` to retrieve the Column (which may call `_get()` if not cached)
   - Checks if column is registered in `smoothingWindows` map
   - If registered: Applies range-aware smoothing via `Smoothing.applySmoothing()`
   - If not registered: Returns raw data from `Column.data.toArray()`
   - Returns `double[]` array (not a Column object)

**Key Points:**
- Registering a column for smoothing does NOT create a new column accessible by `_get()`
- Smoothing registration only affects `getData()` output (for display/rendering)
- Internal calculations use `get().data` (raw/unsmoothed) or `getData()` (smoothed) depending on purpose
- Columns remain accessible via `get()` with their original (unsmoothed) data

**Usage Examples:**

**Display/Rendering (uses `getData()` - smoothed):**
- Main chart rendering: `ECUxChartFactory.java` (lines 242-243)
- SmoothingWindow visualization: Shows pre/post smoothed values in table (lines 789, 810, 835, 856)
- RangeSelectorWindow: Calculates max power for rewards using smoothed data (lines 81-105)
- FATS calculations: Uses smoothed RPM data (line 1984)

**Internal Calculations (uses `get().data` - raw/unsmoothed):**
- Derivative calculations: `this.get("RPM").data.derivative(...)` (uses raw data)
- Column creation: `_get()` methods access other columns via `get().data` for calculations
- Internal processing: All `_get()` handlers use `get().data` to access raw column data

## Data Flow

### Pre-Differentiation (RPM)

1. **CSV RPM**: Raw data from CSV file
   - Source: `super.get("RPM")`
   - Type: `CSV_NATIVE`
   - No smoothing applied

2. **Base RPM**: SG smoothing only, for range detection
   - Method: `createBaseRpm()`
   - Smoothing: `this.csvRpm.data.smooth()` - SG smoothing only
   - Purpose: Used by `dataValid()` and `buildRanges()` to avoid circular dependency
   - No quantization detection, no range-aware smoothing

3. **Final RPM**: Adaptive smoothing (MAW+SG for quantized, SG for smooth)
   - Method: `_get("RPM")`
   - Smoothing: `Smoothing.smoothAdaptive(csvRpmData, ranges, "RPM")`
   - Algorithm: Detects quantization within valid ranges; if quantized: MAW (adaptive, 5-50 samples) + SG smoothing; if smooth: SG smoothing only
   - Purpose: Used for all calculations and display
   - **This is the RPM used for derivative calculations**

### Post-Differentiation (Acceleration, HP, Torque)

**All post-diff columns are registered for range-aware smoothing, which is applied in `getData()` (not during column creation in `_get()`).** See [getData() vs _get(): Smoothing Application](#getdata-vs-_get-smoothing-application) for the complete explanation of this distinction.

#### Acceleration Columns

1. **Acceleration (RPM/s)**
   - Input: `this.get("RPM").data` (final RPM, already smoothed)
   - Derivative: `y.derivative(x, 0)` - **NO smoothing during derivative**
   - Smoothing: Registered with `AccelMAW()` window
   - Applied in: `getData()` via `Smoothing.applySmoothing()`

2. **Acceleration (m/s^2)**
   - Input: `this.get("RPM").data` (final RPM, already smoothed)
   - Derivative: `y.derivative(x, 0)` - **NO smoothing during derivative**
   - Conversion: RPM/s → m/s² using `rpm_per_mph`
   - Smoothing: Registered with `AccelMAW()` window
   - Applied in: `getData()` via `Smoothing.applySmoothing()`

#### Power Columns

1. **WHP** (Wheel Horsepower)
   - Input: `Acceleration (m/s^2)` + `Calc Velocity` (both from smoothed RPM)
   - Calculation: `a * v * mass + drag(v)`
   - Smoothing: Registered with `HPMAW()` window
   - Applied in: `getData()` via `Smoothing.applySmoothing()`

2. **HP** (Engine Horsepower)
   - Input: `WHP` (raw, unsmoothed data from `_get()`)
   - Calculation: `WHP / (1 - driveline_loss) + static_loss`
   - Smoothing: Registered with `HPMAW()` window
   - Applied in: `getData()` via `Smoothing.applySmoothing()`

3. **WTQ/TQ** (Wheel/Engine Torque)
   - Input: `WHP/HP` (raw) + `RPM` (smoothed)
   - Calculation: `Power * HP_CALCULATION_FACTOR / RPM` (via `calculateTorque()`)
   - Smoothing: Registered with `HPMAW()` window
   - Applied in: `getData()` via `Smoothing.applySmoothing()`

4. **WTQ (Nm) / TQ (Nm)**
   - Input: `WTQ/TQ` (raw)
   - Calculation: Unit conversion (ft-lb to Nm)
   - Smoothing: **Inherited automatically** from base column via `getColumnInUnits()` inheritance mechanism
   - Applied in: `getData()` via `Smoothing.applySmoothing()`

## Design Principles

### Single Smoothing Point Per Architectural Layer

**Smoothing Application Rules:**
- Each architectural layer adds **ONE** smoothing step
- **Do NOT** apply smoothing multiple times at the same layer (e.g., smoothing in derivative AND range-aware smoothing)
- The three-stage RPM design provides appropriate smoothing at each stage. Double-smoothing causes:
  - Excessive smoothing (3-4 smoothing applications instead of 2-3)
  - Edge artifacts (smoothing already-smoothed data causes issues)
  - Inconsistent behavior (different smoothing paths for different columns)

**Correct Smoothing Chain for Acceleration:**
1. Final RPM: MAW+SG (quantized) or SG (smooth) - **one smoothing step**
2. Derivative calculation: `derivative(x, 0)` - **no smoothing** (derivative is inherently noisy)
3. Range-aware smoothing: AccelMAW() in getData() - **one smoothing step** (handles edges with padding). See [getData() vs _get(): Smoothing Application](#getdata-vs-_get-smoothing-application) for details.

**Incorrect (Double-Smoothing):**
1. Final RPM: MAW+SG (quantized) or SG (smooth)
2. Derivative calculation: `derivative(x, filter.AccelMAW())` - **smoothing #1**
3. Range-aware smoothing: AccelMAW() in getData() - **smoothing #2** (smoothing already-smoothed data)

## Implementation Details

### Padding Independence

Left and right padding are **completely independent**:
- Changing left padding does not affect right-side results
- Changing right padding does not affect left-side results
- Verified via unit tests (`SmoothingTest.testMAWPaddingIndependence`, `testSGPaddingIndependence`)

### Boundary Handling

**MAW:**
- Point-by-point boundary checks ensure window doesn't extend into opposite-side padding
- `minWindowStart` and `maxWindowEnd` calculated per point
- Window can extend into same-side padding but not opposite-side

**SG:**
- Fixed window size (`SG_WINDOW_SIZE` = 11 points for SG(5,5))
- Falls back to MAW at boundaries when window doesn't fit
- Boundary checks similar to MAW for independence

### Strategy Selection Logic

**In `applyStrategyToPaddedRange()`**:
1. If strategy is MAW → use MAW directly
2. If strategy is SG:
   - Check if dataset size >= `SG_MIN_SAMPLES` (11)
   - If yes → use SG
   - If no → fall back to MAW

**SG Fallback:**
- Also falls back to MAW at boundaries when window doesn't fit
- Creates independent `SmoothingContext` for MAW fallback (ensures padding independence)

## Spline Interpolation

Spline interpolation is used exclusively for FATS calculations:

1. **CubicSpline** (flanagan.interpolation.CubicSpline):
   - Created in `buildRanges()` for each range (RPM vs TIME splines)
   - Used in `calcFATSRPM()` to interpolate time values for given RPM values
   - Purpose: Calculate elapsed time between RPM points for FATS metric
   - FATS uses already-smoothed RPM data (from `getData("RPM", r)`), so splines operate on relatively clean data. See [getData() vs _get(): Smoothing Application](#getdata-vs-_get-smoothing-application) for usage examples.

2. **Spline** (ru.sscc.spline):
   - Methods exist in `DoubleArray`: `spline()`, `spline(int order)`, `spline(double[] mesh)`, `_splineDerivative()`
   - No actual calls to these methods found in the codebase. This is an upstream library, do not remove unused methods.

**Note**: Splines are not used in the smoothing pipeline:
- Primary goal is noise reduction in unreliable data
- Traditional splines assume noise-free data and will overfit noise
- Moving averages are specifically designed for noise reduction
- Splines are used only for FATS (interpolation of already-smoothed data)

## Key Files

- `src/org/nyet/util/Smoothing.java`: Core smoothing algorithms and utilities
- `src/org/nyet/ecuxplot/ECUxDataset.java`: Column registration and data flow
- `src/org/nyet/util/DoubleArray.java`: Basic smoothing and derivative operations
