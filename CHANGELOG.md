<!-- markdownlint-disable MD024 -->
# Changelog

## [Unreleased]

### Added

- Calc torque in Nm option ([Issue #1](https://github.com/nyetwurk/ecuxplot/issues/1))
- Unit conversion system for axis menu based on actual units rather than column names ([Issue #57](https://github.com/nyetwurk/ecuxplot/issues/57))
- Automatic generation of unit conversion menu items (e.g., mph/km/h for speed, °F/°C for temperature)

### Changed

- Changed smoothing parameters from sample-based to time-based (now in seconds, default 1.5s)
- Generalize WaitCursor handling in rebuild() to support multiple windows
- Standardized unit conventions throughout codebase (mph/km/h, °F/°C)
- Simplified field category system from arrays to single values
- Renamed field categories to field preferences
- Aligned field names with canonical aliases from `me7_alias.map`

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
