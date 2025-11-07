# ECUxPlot Developer Guide

This guide provides practical information for developers working on ECUxPlot. It explains the system architecture, key design patterns, and how to work with the codebase.

## Quick Start

### Core Architecture Overview

ECUxPlot uses a hierarchical approach to data consistency with clear sources of truth:

1. **Filter** (`Filter.java`) - Single source of truth for which ranges are visible
2. **Chart Datasets** (`DefaultXYDataset`) - Source of truth for what data is actually displayed
3. **Performance Caches** - Derived state, kept in sync with sources of truth

### Key Classes

- **`ECUxPlot`** - Main application class, manages chart, windows, and data flow
- **`ECUxDataset`** - Extends `Dataset`, contains log data and calculated fields. See [Log Detection](log-detection.md) for how log files are parsed, [Smoothing](smoothing.md) for RPM architecture, and [File Opening Behavior](file-opening-behavior.md) for dataset metadata usage.
- **`Filter`** - Manages range selection state and filter parameters
- **`FATSDataset`** - Manages FATS (time) calculations and caching
- **`ECUxPlotWindow`** - Base class for all application windows

---

## Core Concepts

### Data Flow

```text
User Action (Range Selector)
  → applySelection()
    → filter.setSelectedRanges() or filter.setSelectedFiles()
      → updateChartVisibility()
        → Iterate chart datasets directly (no cache)
          → renderer.setSeriesVisible()
      → FATSDataset.rebuild()
        → fatsDataMap (read all, display selected)
```

### Sources of Truth

**Rule**: Always maintain clear sources of truth. Derived state (caches) must be kept in sync.

1. **Filter State** - Stored in `Filter` class, persists across operations
   - Updated by Range Selector Window via `applySelection()`
   - Updated by FilterWindow when parameters change

2. **Chart Datasets** - JFreeChart `DefaultXYDataset` instances (one per axis)
   - Repopulated during `rebuild()` based on Filter state
   - Read directly by `updateChartVisibility()` - no cache needed

3. **Performance Caches** - Derived state, must be kept in sync
   - `fatsDataMap`: Fast lookup for FATS display in Range Selector tree

---

## Working with Chart Series Visibility

### How Visibility Works

Chart series visibility is determined by **direct iteration** over chart datasets. There is **no cache** for visibility state.

**Key Method**: `ECUxPlot.updateChartVisibility()`

**Rules**:
1. **Direct iteration** - Always read from chart datasets
2. **Filter is source of truth** - Visibility based on Filter state
3. **Defer during rebuild** - `isRebuilding` flag prevents updates during rebuild

**Why no cache?**
- Performance analysis shows iteration cost is negligible (~6μs for 50 series)
- Eliminates all cache sync complexity
- Eliminates cache invalidation race conditions
- Single source of truth (chart datasets)

### Implementation Details

The `seriesInfoMap` cache was removed because:
- Performance gain was negligible (O(1) array access vs map lookup)
- Required synchronization at 5+ different points
- Created race condition windows
- Cache could get out of sync, requiring recovery mechanisms

**Current approach**: `updateChartVisibility()` iterates chart datasets directly, checking visibility from Filter state and updating renderer visibility accordingly.

**Guard**: `isRebuilding` flag defers visibility updates during rebuild to avoid modifying chart while it's being rebuilt. This is defensive programming.

---

## Filter System

### Two-Level Filtering

The filtering system has two levels:

#### Level 1: Individual Point Checks (`dataValid()`)

**Location**: `ECUxDataset.dataValid(int i)`

**Note**: Uses `baseRpm` (SG-smoothed only) for range detection. See [Smoothing - Three-Tier RPM Architecture](smoothing.md#three-tier-rpm-architecture) for details.

Checks each data point individually for:
- Gear match
- Minimum pedal/throttle
- RPM within range (min/max)
- RPM monotonicity (no sudden drops)
- Boost pressure valid
- Acceleration minimum

**If a point fails**, it shows the specific reason (e.g., "rpm 2500<3000", "ped 45.2<50").

#### Level 2: Range Validation (`rangeValid()`)

**Location**: `ECUxDataset.rangeValid(Range r)`

After grouping consecutive valid points into ranges, each range is validated:
- **Minimum points**: Range must have at least X consecutive valid points
- **Minimum RPM span**: Range RPM must increase by at least Y (e.g., 2000 RPM)

**If a range fails**, the entire range is rejected (removed from `range_cache`).

### Understanding "Not in valid range"

**"Not in valid range"** means:
- ✅ The individual data point **passed** all `dataValid()` checks
- ❌ BUT the point is **not in any valid range** because the range it was part of **failed** `rangeValid()` checks

**Common reasons a range fails**:
1. **Too few points**: `pts 10 < 15`
2. **Insufficient RPM span**: `rpm 3500 < 3000+2000`

### Filter-Related Methods in ECUxDataset

1. **`dataValid(int i)`** - Primary data point validation
   - Checks gear, pedal, throttle, acceleration
   - Validates boost pressure (actual/desired)
   - Validates RPM (min/max/monotonicity)
   - Updates `lastFilterReasons` instance variable

2. **`rangeValid(Range r)`** - Range validation
   - Checks minimum points
   - Validates RPM range span
   - Updates `lastFilterReasons` and stores failure reasons in `rangeFailureReasons` map

3. **`getFilterReasonsForRow(int rowIndex)`** - Public API
   - Wrapper around `dataValid()` to expose filter reasons
   - Returns copy of `lastFilterReasons`

4. **`getRangeFailureReasons(int rowIndex)`** - Public API
   - Retrieves stored range failure reasons for a specific row
   - Returns reasons from `rangeFailureReasons` map
   - Used by FilterWindow to display why points are "not in valid range"

5. **Helper methods**:
   - `checkRPMMonotonicity(int i)` - Checks for sudden RPM drops
   - `calculateRangeDetectionAcceleration(int i)` - Calculates acceleration using `AccelMAW()`
   - `AccelMAW()` - Converts filter's acceleration smoothing window (seconds) to sample count

### Filter Code Location

**Location**: Filter methods are implemented directly in `ECUxDataset`.

**Structure**:
- Filter logic is integrated with dataset internals (column access, state management)
- Base class `Dataset.buildRanges()` calls protected methods `dataValid()` and `rangeValid()` (template method pattern)
- `lastFilterReasons` is shared state between base class and filter logic
- Filter methods are a manageable portion of ECUxDataset

**Working with Filter Code**:
- Modify individual point checks in `dataValid(int i)`
- Modify range validation in `rangeValid(Range r)`
- Filter logic is specific to ECUxDataset's structure and columns
- If refactoring, consider extracting pure validation logic (without dataset access) to static helper methods, keeping template method implementations in ECUxDataset

---

## Cache Management

### Cache Strategy

The application maintains a clear hierarchy:

1. **Chart Datasets** (source of truth) - What's actually displayed
2. **Filter state** (source of truth) - Which ranges should be visible
3. **Performance Caches** - Derived state, must be kept in sync

### Visibility Updates (No Cache)

**Approach**: Direct iteration over chart datasets

**Why no cache needed**:
- Performance analysis shows iteration cost is negligible (~6μs for 50 series)
- Eliminates all cache sync complexity
- Eliminates cache invalidation race conditions
- Single source of truth (chart datasets)

### fatsDataMap Cache

**Purpose**: Store ALL FATS values for Range Selector tree display, regardless of selection. See [Range Awards](range-awards.md) for how FATS values are used in award calculations.

**Key**: `filename` → `Map<rangeIndex, FATSValue>`

**Value**: Calculated FATS time in seconds

**Why it exists**:
- Range Selector tree shows FATS for ALL ranges (even unchecked)
- FATS chart shows only SELECTED ranges
- Avoids recalculating FATS every time selection changes

**Multiple Sources of Truth**:
1. **ECUxDataset** (source of truth) - Raw data for calculation
2. **fatsDataMap** (cache) - Pre-calculated values keyed by (filename, rangeIndex)
3. **DefaultCategoryDataset** (display) - Chart dataset showing only selected ranges

**Sync Points**:
- Populated in `FATSDataset.rebuild()` for missing values
- Cleared in `FATSDataset.rebuildAll()` when parameters change
- Chart dataset updated via `setValue()` based on Filter selections

**Cache Invalidation**:
- ✅ `rebuildAll()` clears and recalculates all
- ✅ `rebuild()` calculates only missing values (lazy population)
- ✅ Chart dataset filtered by Filter state on each `rebuild()`

**Intentional Design**: `fatsDataMap` contains ALL ranges (for tree), while chart dataset contains only SELECTED ranges (for display). This asymmetry is intentional.

### Cache Coherency Rules

#### Visibility Updates Coherency
1. **Direct iteration** - Always read from chart datasets
2. **Filter is source of truth** - Visibility based on Filter state
3. **Defer during rebuild** - `isRebuilding` flag prevents updates during rebuild

#### fatsDataMap Coherency
1. **Lazy population** - Only calculate missing values
2. **Cleared on parameter change** - When FATS settings change
3. **Never cleared on selection change** - Must retain all values for tree
4. **Chart dataset filtered by Filter** - Separate from cache

#### Filter Coherency
1. **Single source of truth** - All selections go through Filter
2. **Range Selector updates Filter** - User changes → Filter → Chart
3. **FilterWindow updates Filter** - Parameter changes → Filter → Rebuild

---

## Race Condition Mitigation

### Overall Strategy

The application uses a hierarchical approach to data consistency with defense in depth:

#### Primary Strategy: Maintain Source of Truth

1. **Filter (Range Selection State)** - Single source of truth
   - Stored in `Filter` class, persists across operations
   - Updated by Range Selector Window via `applySelection()`

2. **Chart Datasets** - Source of truth for displayed data
   - JFreeChart `DefaultXYDataset` instances (one per axis)
   - Repopulated during `rebuild()` based on Filter state
   - Read directly by `updateChartVisibility()` - no cache needed

3. **Performance Caches** - Derived state, must be kept in sync
   - `fatsDataMap`: Fast lookup for FATS display in Range Selector tree

#### Secondary Strategy: Defense in Depth

1. **Worker Cancellation** - Prevent concurrent rebuilds
   - When `rebuild()` starts, cancel any in-progress rebuild worker
   - Prevents overlapping operations that could corrupt state

2. **State Flags** - Track operation status
   - `isRebuilding`: Volatile flag indicating rebuild in progress
   - Used to defer visibility updates during rebuild

### Guards in Place

#### Guard 1: Concurrent Rebuild Prevention

**Location**: `ECUxPlot.rebuild()`

**Logic**: Synchronized check - if rebuild worker is in progress, cancel it and start new one

**Warning**: `RACE CONDITION: Cancelling previous rebuild worker - concurrent rebuild() calls detected`

**What it prevents**:
- Two `rebuild()` calls running simultaneously
- State corruption from overlapping operations
- Memory leaks from abandoned workers

#### Guard 2: Visibility Update During Rebuild

**Location**: `ECUxPlot.updateChartVisibility()`

**Logic**: If `isRebuilding` flag is true, defer visibility update and return early

**Warning**: `RACE CONDITION: updateChartVisibility() called during rebuild - deferring visibility update`

**What it prevents**:
- Visibility update running while chart is being rebuilt
- Attempting to update series that don't exist yet or are being modified
- State mismatch between visibility request and chart state

**Recovery**: Visibility will be correctly set when rebuild completes (rebuild sets visibility based on Filter state).

**Status**: ✅ Guard is needed and working correctly. The warning serves as an indicator for future improvements.

#### Guard 3: Worker Cancellation Check (Background)

**Location**: `ECUxPlot.rebuild().doInBackground()`

**Logic**: Check if worker was cancelled during background work

**Warning**: `[BACKGROUND] Rebuild cancelled during range building (processed {} datasets)`

**What it indicates**:
- A newer `rebuild()` was started, cancelling this one
- Operation terminated early to make way for new rebuild
- Prevents wasted computation on superseded rebuilds

#### Guard 4: Worker Cancellation Check (EDT)

**Location**: `ECUxPlot.rebuild().done()`

**Logic**: Check if worker was cancelled before UI updates

**Warning**: `[EDT] Rebuild was cancelled`

**What it indicates**:
- Worker was cancelled before `done()` could execute UI updates
- Prevents UI updates from a cancelled rebuild
- Ensures only the latest rebuild updates the UI

### Monitoring Guards

The guards emit **WARN** level messages when triggered. To determine if they're needed:

1. **Run with normal logging** (default log level) - WARN/ERROR will appear
2. **Use application normally** - Load files, change selections, apply filters
3. **Check logs for warnings**:
   - If **no warnings appear**: Guards may be unnecessary, can consider removing
   - If **warnings appear**: Guards are preventing issues, keep them

**Expected Warnings in Normal Use**:
- **None** - With proper implementation, warnings should be rare or non-existent
- If warnings appear, they indicate either:
  - A bug in the implementation (investigate and fix)
  - A necessary guard (keep it)
  - An edge case (may need additional handling)

### Common Race Condition Scenarios

#### Concurrent Rebuild Warnings

**Condition**: Multiple `rebuild()` calls happen in quick succession

**Likely Causes**:
1. Filter Window → Apply: User clicks Apply in Filter Window, which calls `rebuild()`
2. Rapid User Actions: User quickly loads file, then preset, then changes filter
3. Preset Load: Loading a preset calls `rebuild()`, user quickly does another action
4. Profile Load: Loading a profile calls `rebuild()`, user immediately loads another

**Fix**: Worker cancellation prevents this - only one rebuild runs at a time.

#### Visibility Update During Rebuild

**Condition**: `updateChartVisibility()` called while `rebuild()` is in progress

**Likely Causes**:
1. User clicks Apply quickly: Range Selector Apply clicked while rebuild happening
2. Auto-refresh trigger: Some operation triggers rebuild, another triggers visibility update
3. Callback chains: Rebuild callback triggers visibility update before rebuild completes

**Fix**: Guard detects this and defers visibility update. Rebuild will apply correct visibility when it completes.

**Note**: This is defensive programming - `applySelection()` doesn't trigger `rebuild()`, so this should be rare in normal use.

---

## Vehicle Constants Invalidation System

### Overview

Vehicle constants (mass, rpm_per_mph, rpm_per_kph, Cd, FA, rolling_drag, static_loss, driveline_loss) are stored in `Constants` class via Java Preferences API and accessed via `Env.c`.

**Constants**:
- `mass()` - Vehicle mass in kg
- `rpm_per_mph()` - RPM per MPH conversion factor
- `rpm_per_kph()` - RPM per KPH (derived from rpm_per_mph)
- `Cd()` - Coefficient of drag
- `FA()` - Frontal area (m²)
- `rolling_drag()` - Rolling drag coefficient
- `static_loss()` - Static driveline loss (HP)
- `driveline_loss()` - Driveline loss percentage

### How Constants Are Used

#### ECUxDataset Calculated Fields (CRITICAL - CACHED)

**Location**: `src/org/nyet/ecuxplot/ECUxDataset.java`

**Problem**: Calculated columns are cached in `getColumns()`. Once a column is created via `_get()`, it's added to the columns list and never recalculated.

**Fields Using Constants**:
1. **"Calc Velocity"** - Uses: `env.c.rpm_per_mph()`
2. **"Drag"** - Uses: `env.c.Cd()`, `env.c.FA()`, `env.c.rolling_drag()`, `env.c.mass()`
3. **"WHP"** - Uses: `env.c.mass()` (via acceleration calculation and drag)
4. **"HP"** - Uses: `env.c.driveline_loss()`, `env.c.static_loss()`
5. **"WTQ"** - Depends on: "WHP"
6. **"TQ"** - Depends on: "HP"
7. **"Acceleration (m/s^2)"** - Depends on: "Calc Velocity"
8. **"Acceleration (g)"** - Depends on: "Acceleration (m/s^2)"

#### FATS Calculations (CRITICAL)

**Location**: `src/org/nyet/ecuxplot/ECUxDataset.java` methods: `calcFATS()`, `calcFATSRPM()`

**Uses**: `env.c.rpm_per_mph()`, `env.c.rpm_per_kph()`

**Problem**: FATS values are cached in `FATSDataset.fatsDataMap`. When constants change, cached FATS values become incorrect.

#### Chart Data (CRITICAL)

**Location**: `src/org/nyet/ecuxplot/ECUxPlot.java`

**Problem**: Chart datasets are built from `ECUxDataset.get()` which returns cached columns. When constants change, chart shows stale calculated values.

### Implementation: ColumnType Enum Approach

**Approach**: ColumnType enum-based identification of constant-dependent columns

**ColumnType System**:
- `VEHICLE_CONSTANTS` type identifies columns that depend on vehicle constants
- All columns with `ColumnType.VEHICLE_CONSTANTS` are invalidated when constants change
- ColumnType is explicitly assigned when columns are created in `_get()`

**Benefits**:
- ✅ Type-safe identification (no string matching)
- ✅ Accurate - type assigned at creation time
- ✅ Maintainable - adding new constant-dependent columns requires explicit type assignment
- ✅ No risk of missing dependencies
- ✅ Simple and efficient - direct enum comparison

### Constants Invalidation Implementation

#### Column Invalidation

**Location**: `src/org/nyet/ecuxplot/ECUxDataset.java`

**Method**: `invalidateConstantDependentColumns()`
- Removes all columns with `ColumnType.VEHICLE_CONSTANTS` type
- Called during `rebuild().done()` after `buildRanges()` completes
- Ensures columns are recreated with new constants on next access

#### Centralized Update Logic

**Location**: `src/org/nyet/ecuxplot/ECUxPlot.java`

**Method**: `handleConstantsChange()`

**Key Features**:
- Centralized update coordination
- Proper sequencing: invalidation happens after `buildRanges()` but before chart rebuild
- Updates all affected windows via callback
- Prevents concurrent modification issues

#### ConstantsEditor Integration

**Location**: `src/org/nyet/ecuxplot/ConstantsEditor.java`

**Changes**: `Process()` method calls `this.eplot.handleConstantsChange()` instead of `super.Process()`

#### FATSDataset Cache Invalidation

**Location**: `src/org/nyet/ecuxplot/ECUxPlot.java` in `rebuild().done()`

**Implementation**: Call `fatsDataset.rebuildAll()` after column invalidation

### Update Sequence in rebuild().done()

1. `buildRanges()` completes (background thread)
2. Invalidate constant-dependent columns
3. Rebuild FATSDataset (clears cache, recalculates with new columns)
4. Rebuild chart datasets (columns recalculated on access via `_get()`)

### UI Elements Affected

1. **Main Chart Window** - All Y-axis series that display constant-dependent calculated fields
2. **FATS Chart Window** - FATS times displayed in bar chart
3. **Filter Window Table** - Calc MPH, Calc Err %, Δ MPH columns
4. **Range Selector Tree** - Node labels (FATS times), tooltips (power), awards
5. **Chart Axes** - Auto-ranging for calculated fields

### Related Files

- `src/org/nyet/ecuxplot/Constants.java` - Constants storage
- `src/org/nyet/ecuxplot/ConstantsEditor.java` - Constants UI editor
- `src/org/nyet/ecuxplot/ECUxDataset.java` - Dataset with calculated fields
- `src/org/nyet/ecuxplot/FATSDataset.java` - FATS data cache
- `src/org/nyet/ecuxplot/FATSChartFrame.java` - FATS window UI
- `src/org/nyet/ecuxplot/FilterWindow.java` - Filter window with data visualization
- `src/org/nyet/ecuxplot/RangeSelectorWindow.java` - Range selector tree window
- `src/org/nyet/ecuxplot/ECUxPlot.java` - Main application, chart management
- `src/org/nyet/logfile/Dataset.java` - Base dataset class with column cache

---

## Preset Loading Architecture

### Key Principle

Presets change columns only, not range selections

- Range Selector is the source of truth for range visibility
- Filter state must be preserved across preset loads
- `addDataset()` adds ALL ranges, Filter controls visibility via `updateChartVisibility()`

### Implementation

**Location**: `ECUxPlot.loadPreset()`

**Related**: See [Axis Preset Support](axis-preset.md) for preset column support details and limitations.

**Approach**: "Remove all, then add preset"

1. If X-axis changed: Update prefs, then call `rebuild()` (which handles everything)
2. If X-axis unchanged:
   - Remove all current Y-keys (in current array order - deterministic)
   - Add all preset Y-keys (in preset array order - deterministic)
   - Update prefs
   - **DO NOT call `initializeFilterSelections()`** - this would override Range Selector selections
   - Call `updateChartVisibility()` to apply existing Filter state (preserves Range Selector selections)

### Known Issues (Resolved)

#### Issue: Preset Loading and Filter Initialization Order

**Problem**: When loading a preset, `loadPreset()` called `removeAllY()` then `addChartYFromPrefs()`, which tried to add series before the Filter was initialized. If Filter had no selections, no series got added.

**Root Cause**: In `fileDatasetsChanged()`, `addChartYFromPrefs()` was called before `initializeFilterSelections()`, causing series to be added with Filter having 0 ranges selected.

**Fix**: ✅ RESOLVED - `addDataset()` now adds ALL ranges regardless of Filter state. Filter initialization is still needed but for visibility, not for adding series. Moved `initializeFilterSelections()` to execute BEFORE `addChartYFromPrefs()` in `fileDatasetsChanged()` to ensure visibility is correct.

**Status**: ✅ FIXED - Adding works regardless of Filter state; Filter initialization ensures correct visibility on initial load.

---

## DatasetUnits System

### Status: ✅ IMPLEMENTED

**Location**: `src/org/nyet/ecuxplot/DatasetUnits.java`

**Related**: See [Unit Conversion System](../plans/unit-conversion-system.md) for detailed documentation.

### Implementation: Map-Based Converter Registry

**Approach**: Simple Map with Converter Functions

Uses `HashMap<String, UnitConverter>` for converter registry:
- Key format: `"targetUnit:baseUnit"`
- Static initialization block populates all converters
- Supports all conversions: lambda/AFR, temperature, pressure (mBar/PSI/kPa), speed (mph/kmh), mass flow, torque

**Benefits**:
- ✅ Cleaner and more maintainable
- ✅ Easy to add new conversions (just add to map)
- ✅ No long if/else chain
- ✅ Clear separation of conversion logic
- ✅ Supports complex converters with ambient pressure
- ✅ Works with Java 8+ (no version requirement)

**Trade-offs**:
- Map lookup overhead is negligible (single hash map lookup per conversion)
- Static initialization happens once at class load time (no runtime cost)
- Code is significantly clearer and easier to maintain than if/else chains
- Adding new conversions is trivial (just add entry to static block)

---

## Common Development Tasks

### Adding a New Calculated Column

1. **Add column creation in `ECUxDataset._get()`**
   - Create the column with appropriate `ColumnType`
   - If it uses vehicle constants, use `ColumnType.VEHICLE_CONSTANTS`

2. **Handle invalidation** (if using constants)
   - Column will be automatically invalidated when constants change
   - Ensure `invalidateConstantDependentColumns()` handles it correctly

3. **Update UI** (if needed)
   - Add to axis menus if it should be selectable
   - Update any windows that display calculated fields

### Modifying Filter Logic

1. **Individual point checks**: Modify `ECUxDataset.dataValid(int i)`
2. **Range validation**: Modify `ECUxDataset.rangeValid(Range r)`
3. **Filter reasons**: Update `lastFilterReasons` or `rangeFailureReasons` as needed
4. **Test**: Verify filter reasons display correctly in FilterWindow

### Adding a New Unit Conversion

1. **Add converter to `DatasetUnits.java`**
   - Add entry to static initialization block
   - Key format: `"targetUnit:baseUnit"`
   - Provide converter function

2. **Test**: Verify conversion works in both directions if applicable

### Debugging Race Conditions

1. **Enable logging** - Run with default log level to see WARN messages
2. **Reproduce issue** - Use application normally, load files, change selections
3. **Check logs** - Look for race condition warnings
4. **Investigate** - If warnings appear, investigate root cause:
   - Bug in implementation (fix it)
   - Necessary guard (keep it)
   - Edge case (may need additional handling)

### Working with Caches

**Rule**: Always maintain cache coherency

1. **fatsDataMap**:
   - Populate in `FATSDataset.rebuild()` for missing values
   - Clear in `FATSDataset.rebuildAll()` when parameters change
   - Never clear on selection change (tree needs all values)

2. **Column cache** (in `Dataset.getColumns()`):
   - Automatically managed by base class
   - Use `invalidateConstantDependentColumns()` for constant-dependent columns
   - Columns are recreated on next access after invalidation

---

## Testing Checklists

### Race Condition Guards

To verify race condition guards work correctly:

- [ ] Load files → Load preset → Change ranges → Apply (verify no warnings)
- [ ] Rapidly click Apply multiple times in Range Selector
- [ ] Open Filter Window → Apply → Immediately open Range Selector → Apply
- [ ] Load multiple files in quick succession
- [ ] Remove all Y axes → Load preset → Change ranges → Apply

If any of these produce warnings, investigate the root cause and fix it.

### Vehicle Constants Invalidation

- [ ] Change `rpm_per_mph` → verify Calc Velocity updates in chart and Filter window
- [ ] Change `mass` → verify WHP, HP, WTQ, TQ all update
- [ ] Change `Cd` → verify Drag and WHP update
- [ ] Change `driveline_loss` → verify HP and TQ update
- [ ] Change constants with FATS window open → verify FATS times recalculate
- [ ] Change constants with Filter window open → verify table updates
- [ ] Change constants with Range Selector open → verify tooltips and labels update
- [ ] Change constants during chart rendering → verify no crashes
- [ ] Change constants multiple times rapidly → verify all updates correctly

### Cache Coherency

- [ ] Verify chart series visibility updates correctly after Filter changes
- [ ] Verify FATS tree shows all ranges even when unchecked
- [ ] Verify FATS chart shows only selected ranges
- [ ] Verify no cache sync warnings appear in logs
- [ ] Verify performance is acceptable with direct iteration (no measurable slowdown)

---

## Key Files Reference

### Core Classes

- `src/org/nyet/ecuxplot/ECUxPlot.java` - Main application, chart management
- `src/org/nyet/ecuxplot/ECUxDataset.java` - Dataset with calculated fields and filtering
- `src/org/nyet/ecuxplot/Filter.java` - Range selection state and filter parameters
- `src/org/nyet/ecuxplot/FATSDataset.java` - FATS data cache and chart dataset
- `src/org/nyet/ecuxplot/Constants.java` - Constants storage
- `src/org/nyet/logfile/Dataset.java` - Base dataset class with column cache

### UI Windows

- `src/org/nyet/ecuxplot/ECUxPlotWindow.java` - Base class for all windows
- `src/org/nyet/ecuxplot/FilterWindow.java` - Filter window with data visualization
- `src/org/nyet/ecuxplot/RangeSelectorWindow.java` - Range selector tree window
- `src/org/nyet/ecuxplot/FATSChartFrame.java` - FATS window UI
- `src/org/nyet/ecuxplot/ConstantsEditor.java` - Constants UI editor

### Utilities

- `src/org/nyet/ecuxplot/DatasetUnits.java` - Unit conversion system
