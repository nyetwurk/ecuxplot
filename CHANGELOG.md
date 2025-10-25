<!-- markdownlint-disable MD024 -->
# Changelog

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
