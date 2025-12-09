<!-- markdownlint-disable MD024 -->
# Changelog

## [1.2.2] - 2025-12-09

### Fixed

- [#126](https://github.com/nyetwurk/ecuxplot/issues/126): Better VCDS support

## [1.2.1] - 2025-12-04

### Added

- [#124](https://github.com/nyetwurk/ecuxplot/issues/124): File loading progress dialog showing progress for reading, parsing, and filtering stages
- Progress reporting interface (`ProgressCallback`) for file loading operations

### Fixed

- [#125](https://github.com/nyetwurk/ecuxplot/issues/125): Files with no valid ranges are no longer shown in the "Ranges..." tree when filter is enabled
- Range selection visibility bug when filter parameters change

### Changed

- Filter performance optimized with caching
- Missing field warnings for sim/turbo/maf calculations changed from warn to info level
- Removed awt.useSystemAAFontSettings=gasp since antialiasing defaults should be fine for modern runtimes

## [1.2.0] - 2025-11-24

### Fixed

- If there is no series for an axis, hide the axis completely
- [#122](https://github.com/nyetwurk/ecuxplot/issues/122): Loading unit handling and stale reference bug
- [#120](https://github.com/nyetwurk/ecuxplot/issues/120): Properly re-compute `Sample[range]` and `Time[range]` on Filter/Range change

## [1.1.9] - 2025-11-15

### Fixed

- [#119](https://github.com/nyetwurk/ecuxplot/issues/119): AxisMenu tooltip now shows all original column names for a canonical column name
- [#118](https://github.com/nyetwurk/ecuxplot/issues/118): Allow log files with differing native CSV units to be compared using the same canonical column alias (e.g. BoostPressureActual) *and units* (e.g. PSI vs mBar)
- Ambient pressure calculation now uses normalized (and mBar) BaroPressure column instead of raw data which might have had the wrong units

## [1.1.8] - 2025-11-10

### Fixed

- [#112](https://github.com/nyetwurk/ecuxplot/issues/112): Unified logging in unit testing, default level INFO for CI
- [#111](https://github.com/nyetwurk/ecuxplot/issues/111): Unify AxisMenu and ECUxDataset into AxisMenuItems and AxisMenuHandlers
- Speed field incorrectly categorized as calculated menu item instead of base menu item

## [1.1.7] - 2025-11-07

### Fixed

- [#110](https://github.com/nyetwurk/ecuxplot/issues/110): Calc PID now requires both boost desired and boost actual fields
- [#109](https://github.com/nyetwurk/ecuxplot/issues/109): Resolved circular dependency between range detection and RPM smoothing using three-tier architecture
- Torque unit conversion (ft/lb to Nm) for accurate calculations
- Max HP calculation in Range Selector to use smoothed data instead of raw data
- Smoothing inheritance so unit-converted columns (e.g., WTQ (Nm)) automatically inherit smoothing from base columns
- Derivative calculation to handle duplicate/near-zero time deltas preventing division by zero errors
- Acceleration calculations to avoid double-smoothing artifacts
- FilterWindow to use base RPM for data visualization matching actual filter behavior
- [#108](https://github.com/nyetwurk/ecuxplot/issues/108): Preset loading now updates axis labels when X-axis is unchanged but Y-axes change
- [#106](https://github.com/nyetwurk/ecuxplot/issues/106): Improved Cobb Accessport boost pressure alias matching to handle variations in field names

### Changed

- **Major Refactoring**: Replaced `MovingAverageSmoothing` with unified `Smoothing` class
  - Removed automatic padding/extrapolation (simpler, more predictable behavior)
  - Simplified edge handling: returns original data at edges instead of padding
  - Added `smoothAdaptive()` for quantization-aware adaptive smoothing
- **Three-Tier RPM Architecture**: Implemented CSV/Base/Final RPM system to break circular dependency
  - CSV RPM: Raw data from CSV (no smoothing)
  - Base RPM: SG smoothing only (for range detection, no ranges needed)
  - Final RPM: Adaptive smoothing (MA+SG for quantized, SG for smooth) using ranges for quantization detection
- **Smoothing Improvements**:
  - Added edge clamping to prevent artifacts at range boundaries
  - Adaptive smoothing automatically detects quantization noise and chooses MA+SG vs SG-only
  - Adaptive window sizing based on detected quantization characteristics
  - Range-aware quantization detection to avoid false positives from idle/deceleration periods
- **Filter Parameter Rename**: Renamed `HPTQMAW` to `HPMAW` for clarity
  - Updated all UI labels, tooltips, and filter parameter names
  - Improved filter parameter tooltips with clearer descriptions of what each parameter affects
- **Axis Menu Reorganization**:
  - Organized RPM, TIME, and Sample columns into dedicated submenus
  - Added "Speed" submenu for velocity-related columns
  - Added "Acceleration" submenu for acceleration-related columns
  - Added calculation tooltips showing smoothing chain for power/torque/acceleration columns
  - Debug columns (RPM - base, Acceleration - raw, etc.) now only visible when verbose logging enabled
- **Acceleration Calculation Improvements**:
  - All acceleration calculations now use smoothed RPM input to reduce quantization noise
  - Removed double-smoothing: derivatives use `derivative(x, 0)` with range-aware smoothing in `getData()`
  - Added "Acceleration (RPM/s) - raw" and "Acceleration (m/s^2) - raw" variants
  - Acceleration (m/s^2) now calculated directly from RPM (same approach as RPM/s) for consistency
- **HP/TQ Calculation**:
  - HP now calculated from smoothed WHP during column creation to inherit smoothing without redundancy
  - HP smoothing applied to WHP data before calculating HP (avoids redundant smoothing)
- **Range-Aware Smoothing**:
  - Improved range-aware smoothing with proper padding handling
  - Applied to: Acceleration (RPM/s), Acceleration (m/s^2), WHP, HP, WTQ, TQ
- **New Columns**:
  - Added "Time [Range]" column: relative time to range start (when filter enabled)
  - Added "Sample [Range]" column: relative sample to range start (when filter enabled)
  - Added "Sample" column to FilterWindow for easier data navigation
  - Added "RPM - base" debug column (only visible with verbose logging)
- **Alias Improvements**: Added more Simos logger aliases and improved axis menu sorting
- Consolidated axis label update calls into `updateAllAxisLabels()` helper method
- Added wait cursor feedback for "Original names", "Scatter plot", and "Apply SAE" menu actions
- Enhanced preferences editor to preserve excluded keys when resetting to defaults (SAE editor preserves "enabled" state) ([Issue #107](https://github.com/nyetwurk/ecuxplot/issues/107))

### Added

- Added "Smoothing..." window for configuring smoothing parameters (HPMAW and ZeitMAW moved from Filter window)
- Smoothing window sizes now specified in seconds (converted to samples internally) for easier configuration
- Added `getVerbose()` method to `ECUxPlot` for UI elements to check debug logging level
- Added calculation tooltips to AxisMenu showing smoothing chain for calculated columns
- Added `getSmoothingWindow()` method to `ECUxDataset` to expose smoothing window sizes
- Added `getFilterAcceleration()` method to `ECUxDataset` for filter visualization
- Added `getCsvRpmColumn()` and `getBaseRpmColumn()` accessors for debug/debugging
- SAE checkbox state synchronization in OptionsMenu to reflect external state changes ([Issue #107](https://github.com/nyetwurk/ecuxplot/issues/107))

## [1.1.6] - 2025-11-03

### Added

- **OBDLINK Logger Support**: Added detection and parsing support for OBDLink/OBD-II scan tool CSV files
- Tooltips for menu options (Enable filter, Scatter plot, Original names, Apply SAE, Filter, SAE constants, Edit PID)
- Improved axis preset support for more logger types (Timing preset now works with additional loggers)

### Changed

- **Global Unit Validation**: All extracted units are now validated to prevent descriptive text from being treated as units
  - Uses `Units.normalize()` to validate extracted units
  - Invalid units (descriptive text with spaces/long strings) are cleared and inferred from field names
- Enhanced alias matching to support fallback to original field names (`id2`) when unit extraction removes information needed for aliasing
- Default axis presets now consistently use canonical column names
- Boost pressure automatically converts to PSI regardless of logger's native units (kPa or mBar)
- Renamed "Alt column names" menu option to "Original names" for clarity (preference key unchanged for backward compatibility)
- RPM and Time smoothing revamped for high data rate logs and logs with excessive jitter ([Issue #86](https://github.com/nyetwurk/ecuxplot/issues/86))
- Improved filter parameter tooltips with clearer, more concise descriptions

### Fixed

- [#103](https://github.com/nyetwurk/ecuxplot/issues/103): Y2 axis units now display correctly when adding datasets to the secondary Y axis
- Unit extraction now correctly handles fields with descriptive metadata in parentheses
  - Lambda fields now correctly show `"λ"` unit instead of descriptive text
  - Units from alias targets (e.g., `"AirFuelRatioDesired (AFR)"`) are now properly extracted
- Fix filter behavior when ranges are empty or selections missing
- [#99](https://github.com/nyetwurk/ecuxplot/issues/99): OS "Open with" now replaces auto-loaded preference files instead of adding to them
- [#95](https://github.com/nyetwurk/ecuxplot/issues/95): Changing vehicle constants now updates all dependent visualizations
  - Main chart display updates with recalculated values when constants change
  - FATS window recalculates FATS times with new constants
  - Filter data visualization table shows updated calculated values (e.g., Calc Velocity, Calc MPH)
  - Range Selector window tooltips and node labels update with new power values
- Main chart display uses original column names instead of aliases when "Original names" preference is enabled
- [#97](https://github.com/nyetwurk/ecuxplot/issues/97): FATS restore defaults now restores mph/kph range defaults as well
- [#104](https://github.com/nyetwurk/ecuxplot/issues/104): Time smoothing now uses interval smoothing instead of absolute time to prevent drift and accumulated errors
- Torque (TQ/WTQ) now calculated from smoothed HP/WHP for consistent power calculations

## [1.1.5] - 2025-10-31

### Added

- Extensive SimosTools support ([Issue #92](https://github.com/nyetwurk/ecuxplot/issues/92))
- VCDS legacy support with "g" and "u2" header columns
- VCDSHeaderProcessor.java - separate file for all VCDS-specific header processing logic
- Legacy VCDS test data files (vcds-002-031.csv, vcds-003-020-026.csv, vcds-003-114-020.csv, vcds-115-118.csv)

### Changed

- Changed RPM fuzz tolerance from absolute RPM to RPM/sec for consistency across logger rates ([Issue #93](https://github.com/nyetwurk/ecuxplot/issues/93))
  - Default changed from 100 RPM to 500 RPM/s (maintains same effective tolerance at 10 Hz)
  - Automatically migrates existing preferences (100 RPM → 500 RPM/s)
  - FilterWindow UI now shows "(RPM/s)" units and displays values as integers
  - Refactored RPM monotonicity check into helper function `checkRPMMonotonicity()`
  - Added FilterParameter enum (similar to Column enum) for better maintainability
  - Added tooltips to all filter parameter fields in FilterWindow
- Re-ordered header parsing pipeline:
  - Extract units from id FIRST via `unit_regex` (before aliasing)
  - Apply aliases to generate canonical names
  - Run logger-specific header processing (VCDS group handling, etc.)
  - General unit parsing from header tokens
  - Field transformations (prepend/append)
  - Unit normalization and inference
  - Ensure unique field names LAST
- Moved all VCDS-specific parsing logic from DataLogger.java to VCDSHeaderProcessor.java
- Updated filter column test cases to use column indices instead of field names

### Fixed

- Restore filter reasons in FilterWindow data visualization table
- Fix bug where which window was on top was confusing
- [#86](https://github.com/nyetwurk/ecuxplot/issues/86): Fix bug where ranges that should not be visible are visible on startup and when enabling filter

## [1.1.4] - 2025-10-29

### Added

- Winlog logger type support ([Issue #33](https://github.com/nyetwurk/ecuxplot/issues/33))
- Calc torque in Nm option ([Issue #1](https://github.com/nyetwurk/ecuxplot/issues/1))
- Unit conversion system for axis menu based on actual units rather than column names ([Issue #57](https://github.com/nyetwurk/ecuxplot/issues/57))
- Automatic generation of unit conversion menu items (e.g., mph/km/h for speed, °F/°C for temperature)
- With Filter disable, RangeSelect can be used as a file selector

### Changed

- Changed smoothing parameters from sample-based to time-based (now in seconds, default 1.5s)
- Generalize WaitCursor handling in rebuild() to support multiple windows
- Standardized unit conventions throughout codebase (mph/km/h, °F/°C)
- Simplified field category system from arrays to single values
- Renamed field categories to field preferences
- Aligned field names with canonical aliases from `me7_alias.map`
- Remove clickable axis feature: it broke too many things

### Fixed

- [#32](https://github.com/nyetwurk/ecuxplot/issues/32): Apply moving average per-range to prevent filtered data interference in HP/TQ plots
- Fix "Restore Defaults" setting wrong smoothing values due to UI field mapping bug
- Fix FilterWindow Calc MPH display and add support for native MPH velocity data
- Add protections to avoid invalid smoothing values (window size validation)
- Vehicle speed and temperature field conversion menu items now appear correctly
- Lambda control fields correctly identified as unitless
- EGT sensor fields now properly detect temperature units
- VCDS vehicle speed alias mapping
- Fix regression in FATS window that caused FATS times to disappear
- [#89](https://github.com/nyetwurk/ecuxplot/issues/89): Do not show "Empty" in chart titles
- [#88](https://github.com/nyetwurk/ecuxplot/issues/88): Fix file loading and drag-and-drop for multiple files into Filter, Range, and FATS windows correctly
- Fix macOS multiple file drag-and-drop: Read all files from URI list instead of just the first one
- Fix filter initialization: Initialize filter selections per-file when adding files to existing datasets instead of all-or-nothing
- Fix window updates: Ensure open windows (Filter, Range, FATS) are updated when files are added
- Bug which made the X-Axis RPM only
- Keep scatter box and filter checkbox consistent with app state

## [1.1.3] - 2025-10-28

### Added

- Range Selector window for file and range selection ([Issue #85](https://github.com/nyetwurk/ecuxplot/issues/85))
- Per-file range selection with checkboxes and tree browser
- Award icons for best FATS/power in file, group, and overall
- Smart elision for long file names in FilterWindow and main app
- "Ranges..." menu item in Options menu

### Changed

- FATS dataset now uses per-file range selection support
- Simplified axis range calculation for negative values with symmetric padding
- Removed zero-forcing logic that artificially expanded ranges

### Fixed

- [#88](https://github.com/nyetwurk/ecuxplot/issues/88): Adding files (including via drag and drop) now updates FATS, Ranges and Filters windows
- Multiple file drag-and-drop on macOS now loads all files instead of just the first
- File drop support added to Range Selector window
- Filter initialization when adding files to existing datasets
- URI parsing for file drag-and-drop using proper Paths.get() handling
- [#84](https://github.com/nyetwurk/ecuxplot/issues/84): URI parsing error under cygwin
- [#87](https://github.com/nyetwurk/ecuxplot/issues/87): FilterWindow sizing issue
- Preferences save bug
- Range controls availability logic when all files have only 1 range
- Axis range application avoiding recursion with proper prevention

## [1.1.2] - 2025-10-25

### Added

- Add axis click functionality for direct chart interaction
- User preference toggle between modern axis clicks and traditional dropdown menus
- Hand cursor hover feedback for clickable axis areas
- Comprehensive tooltips for menu items explaining requirements

### Changed

- Consolidated all filter, analysis tools, and preferences into single Options menu
- Removed redundant View menu and moved Event Viewer to File menu
- Custom axis range now only applies when beneficial (negative values) to avoid unnecessary overrides
- Simplified axis range logic with better recursion prevention

### Fixed

- Range controls now properly disabled when filter is off or only one range exists
- FATS window availability correctly tied to filter state
- Menu item naming conventions standardized throughout application
- Improved logical grouping of related functions
- Custom axis range calculation for negative values to include proper padding above zero
- Resolved infinite recursion issue in axis range application
- Improved axis scaling consistency for datasets with negative or zero values

## [1.1.1] - 2025-10-24

### Added

- Event Window with real-time application event monitoring
- Event filtering and search capabilities
- KPH support in FATS calculations
- Custom exception dialogs for better error reporting
- Wait cursor spinner for long operations
- Copy/save functionality in FATS window

### Changed

- Migrated entire project to standard Java indentation (4 spaces)
- Enhanced FilterWindow with gear selector and improved visual design
- Improved FATS error handling and UI
- Refactored header parsing from ECUxDataset to DataLogger
- Moved constants to centralized class

### Fixed

- Redundant logging between ECUxDataset and FATSDataset
- Filter state persistence issues
- Various UI state management problems
- Code quality issues (unused methods, import ordering)

### Infrastructure

- Enhanced GitHub Actions workflows
- Updated Java target to Java 17 LTS
- Improved CI/CD pipeline

## [1.1.0] - 2025-10-23

### Added

- Custom auto-scaling routine for negative number data
- Enhanced M-Tuner log support
- Vehicle speed calculation for all log types
- Better handling of logs without throttle data
- Negative boost data filtering

### Changed

- Complete rework of logger detection system
- Improved Zeitronix timestamp handling with rollover detection
- Enhanced FATS window with better visual grouping
- Improved axis menu organization

### Fixed

- HP calculation regression
- CubicSpline crashes
- Recursion issues in preset management
- File open error reporting
- Output file functionality (`-o` option)
- Various preset and filter state issues

## [1.0.6] - 2025-10-22

### Added

- Automated nightly build system
- Enhanced GitHub Actions workflows
- Split README into focused documentation files
- Improved error reporting and user feedback

### Changed

- Refactored header parsing system
- Moved AIR_DENSITY_STANDARD and GPS_PER_CCMIN to constants
- Enhanced CI/CD pipeline

### Fixed

- Various UI state management issues
- File open error reporting
- Version generation system

## [1.0.5] - 2025-10-16

### Added

- Core ECUxPlot functionality
- Basic filter system implementation
- FATS calculations
- Logger detection and parsing
- Foundation for future enhancements

---

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
