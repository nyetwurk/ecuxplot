<!-- markdownlint-disable MD024 -->
# Changelog

## [Unreleased]

### Added

- Tooltips for menu options (Enable filter, Scatter plot, Original names, Apply SAE, Filter, SAE constants, Edit PID)

### Changed

- Renamed "Alt column names" menu option to "Original names" for clarity (preference key unchanged for backward compatibility)
- RPM and Time smoothing revamped for high data rate logs and logs with excessive jitter ([Issue #86](https://github.com/nyetwurk/ecuxplot/issues/86))

### Fixed

- Fix filter behavior when ranges are empty or selections missing
- Changing vehicle constants now updates all dependent visualizations ([Issue #95](https://github.com/nyetwurk/ecuxplot/issues/95))
  - Main chart display updates with recalculated values when constants change
  - FATS window recalculates FATS times with new constants
  - Filter data visualization table shows updated calculated values (e.g., Calc Velocity, Calc MPH)
  - Range Selector window tooltips and node labels update with new power values
- Main chart display uses original column names instead of aliases when "Original names" preference is enabled
- FATS restore defaults now restores mph/kph range defaults as well ([Issue #97](https://github.com/nyetwurk/ecuxplot/issues/97))

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
- Fix bug where ranges that should not be visible are visible on startup and when enabling filter ([Issue #86](https://github.com/nyetwurk/ecuxplot/issues/86))

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

- Apply moving average per-range to prevent filtered data interference in HP/TQ plots ([Issue #32](https://github.com/nyetwurk/ecuxplot/issues/32))
- Fix "Restore Defaults" setting wrong smoothing values due to UI field mapping bug
- Fix FilterWindow Calc MPH display and add support for native MPH velocity data
- Add protections to avoid invalid smoothing values (window size validation)
- Vehicle speed and temperature field conversion menu items now appear correctly
- Lambda control fields correctly identified as unitless
- EGT sensor fields now properly detect temperature units
- VCDS vehicle speed alias mapping
- Fix regression in FATS window that caused FATS times to disappear
- Do not show "Empty" in chart titles ([Issue #89](https://github.com/nyetwurk/ecuxplot/issues/89))
- Fix file loading and drag-and-drop for multiple files into Filter, Range, and FATS windows correctly ([Issue #88](https://github.com/nyetwurk/ecuxplot/issues/88))
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

- Adding files (including via drag and drop) now updates FATS, Ranges and Filters windows ([Issue #88](https://github.com/nyetwurk/ecuxplot/issues/88))
- Multiple file drag-and-drop on macOS now loads all files instead of just the first
- File drop support added to Range Selector window
- Filter initialization when adding files to existing datasets
- URI parsing for file drag-and-drop using proper Paths.get() handling
- URI parsing error under cygwin ([Issue #84](https://github.com/nyetwurk/ecuxplot/issues/84))
- FilterWindow sizing issue ([Issue #87](https://github.com/nyetwurk/ecuxplot/issues/87))
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
- Fixed custom axis range calculation for negative values to include proper padding above zero
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
