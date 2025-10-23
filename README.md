# ECUxPlot

[![Build and Release](https://github.com/nyetwurk/ecuxplot/actions/workflows/build-and-release.yml/badge.svg)](https://github.com/nyetwurk/ecuxplot/actions/workflows/build-and-release.yml)
[![Release](https://github.com/nyetwurk/ecuxplot/actions/workflows/release.yml/badge.svg)](https://github.com/nyetwurk/ecuxplot/actions/workflows/release.yml)
[![Java](https://img.shields.io/badge/Java-18-orange.svg)](https://openjdk.java.net/)
[![License](https://img.shields.io/badge/License-GPL%203.0-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-lightgrey.svg)](https://github.com/nyetwurk/ecuxplot)

**Downloads**: [Latest Release](https://github.com/nyetwurk/ecuxplot/releases/latest) | [Nightly Build](https://github.com/nyetwurk/ecuxplot/releases/tag/latest)

> **Note**: This README describes features in the `master` branch, which may not be available in the latest release or nightly build.

ECUxPlot is a data analysis tool for automotive ECU (Engine Control Unit) log files. It provides visualization and analysis capabilities for engine performance data, including power estimation, boost control, and fuel system analysis.

## Features

- **Data Visualization**: Plot engine parameters from CSV log files
- **Performance Analysis**: Calculate horsepower estimates
- **FATS Analysis**: "For the Advancement of the S4" ETs for 4200-6500 RPM (60-90 mph)
- **Advanced Filtering**: Acceleration-based filtering with wheel spin detection
- **Debug Tools**: Real-time filter debugging and log analysis
- **Multi-Platform**: Runs on Windows, MacOS, and Linux
- **Customizable**: Vehicle profiles and configurable parameters

## Quick Start

1. **Installation**: See [INSTALL.md](INSTALL.md) for detailed installation instructions
2. **Load Data**: Import your ECU log file (CSV format)
3. **Configure**: Set up vehicle profiles and parameters
4. **Analyze**: Use filters and visualization tools to analyze your data

## Troubleshooting

### Filter Issues

**Problem**: With the filter on, I don't see anything!

**Solution**: The filter now uses acceleration-based filtering to find valid acceleration runs. If you don't meet the criteria, you won't see any data.

- Use **"Options → Show Filter Debug Panel"** to see exactly why data points are being filtered out
- Adjust filter parameters in "Options → Configure filter" menu
- Check minimum acceleration threshold (default: 100 RPM/s)
- Ensure you're logging gear and accelerator pedal position
- Check that your run starts low enough and has sufficient RPM range
- Use **"Options → Show Debug Logs"** to see detailed filter analysis
- If problems persist, post your log file for assistance

### Performance Calculations

**Problem**: My HP estimate is way off!

**Solution**: Adjust the numbers in "Vehicle Profiles → Edit Constants"

**Problem**: My flow estimates (compressor map, calc AFR etc.) are way off!

**Solution**: Adjust the numbers in "Vehicle Profiles → Edit Fueling"

### MAF Configuration

**Problem**: MAF parameter confusion

**Solution**: The "MAF" parameter sets a correction to MAF values calculated by the ECU.

- **Stock intake**: Leave parameter alone if your tune properly calibrates MAF readings
- **Non-stock intake**: Increase MAF diameter if ECU uses underscaled MAF values
- **Verification**: Check the correction value shown in the box under the MAF parameter
- **Calibration**: Compare Calc AFR with wideband data if unsure

### FATS Issues

**Problem**: FATS calculation fails or shows no results!

**Solution**: FATS requires valid acceleration runs that meet filter criteria.

- Use **"Options → Show Filter Debug Panel"** to verify data quality
- Check that acceleration threshold is met (default: 100 RPM/s)
- Ensure RPM range covers the configured FATS range
- Verify `rpm_per_mph` constant is correct for your vehicle
- Use **"Options → Show Debug Logs"** to see detailed FATS calculation logs
- Check that filter is enabled and finding valid ranges

**Problem**: FATS results seem inconsistent between runs!

**Solution**: This may indicate data quality issues or wheel spin.

- Check for wheel spin using Filter Debug Panel (look for negative boost pressure)
- Verify consistent acceleration patterns across runs
- Ensure similar starting conditions (gear, throttle position)
- Compare raw vs calculated MPH values for consistency

### FATS (For the Advancement of the S4)

FATS measures elapsed time for acceleration runs, providing consistent performance comparisons across different vehicles and conditions.

#### FATS Features

- **Dual Mode Operation**:
  - **RPM Mode**: Direct RPM range measurement (e.g., 4200-6500 RPM)
  - **MPH Mode**: Speed-based measurement (e.g., 60-90 mph) with automatic RPM conversion
- **Advanced Filtering**: Acceleration-based filtering eliminates slow runs and wheel spin
- **Real-time Analysis**: Live calculation with detailed logging and error reporting
- **Multi-file Support**: Compare FATS results across multiple log files
- **Debug Tools**: Filter debugging panel for troubleshooting data quality

#### Configuration

FATS can be configured in two ways:

1. **RPM Mode**: Set start and end RPM values directly
2. **MPH Mode**: Set start and end speed values (automatically converted to RPM using `rpm_per_mph`)

The `rpm_per_mph` constant in your vehicle profile determines the RPM-to-speed conversion ratio.

#### Apples-to-Apples Comparison

For accurate performance comparisons between different vehicles, adjust the FATS range based on each vehicle's `rpm_per_mph` to ensure all vehicles are measured over the same speed range.

**Examples**:

**B5S4 (Audi S4)** - `rpm_per_mph = 72.1`:

- 60 mph = 60 × 72.1 = 4326 RPM (~4300 RPM)
- 90 mph = 90 × 72.1 = 6489 RPM (~6500 RPM)
- **Standard FATS Range**: 4300-6500 RPM (covers 60-90 mph)

**Example Vehicle** - `rpm_per_mph = 67`:

- 60 mph = 60 × 67 = 4020 RPM (~4000 RPM)
- 90 mph = 90 × 67 = 6030 RPM (~6000 RPM)
- **Adjusted FATS Range**: 4000-6000 RPM (correctly covers 60-90 mph)

#### Usage

1. **Load Data**: Import your ECU log file
2. **Configure Filter**: Set minimum acceleration threshold (default: 100 RPM/s)
3. **Open FATS**: Use "Options → Show FATS Chart" menu
4. **Set Range**: Configure RPM or MPH range for measurement
5. **Analyze**: View FATS results with detailed logging

### Debug Tools

ECUxPlot includes comprehensive debugging tools to help troubleshoot data quality and filter issues:

#### Filter Debug Panel

Access via "Options → Show Filter Debug Panel" to:

- **Real-time Analysis**: View filtered data points with color-coded ranges
- **Data Validation**: See which data points pass/fail filter criteria
- **Column Comparison**: Compare raw vs calculated MPH values
- **Multi-file Support**: Switch between multiple loaded datasets
- **Clipboard Export**: Copy selected data for external analysis

#### Debug Log Window

Access via "Options → Show Debug Logs" to:

- **Live Logging**: Real-time display of application logs
- **Level Filtering**: Filter by log level (TRACE, DEBUG, INFO, WARN, ERROR)
- **Search Function**: Find specific log entries
- **Export Capability**: Save logs to file for analysis
- **Auto-scroll**: Automatically follow new log entries

## Getting Help

If you encounter issues not covered here, please post your log file for assistance. In some cases, ECUxPlot may not detect pedal/gear data properly from the CSV header and may require adding your CSV format to the application.

## Supported Log Formats

ECUxPlot automatically detects and parses the following automotive ECU logging formats:

- **VCDS** - [Ross-Tech VAG-COM Diagnostic System](https://www.ross-tech.com/)
- **ME7-Logger** - [ME7Logger](http://nefariousmotorsports.com/forum/index.php/topic,837.0title,.html)
- **SWComm/ECUTools** - [SWComm ECUTools](https://www.ecutools.com/)
- **JB4** - [Burger Motorsports JB4](https://burgertuning.com/)
- **Cobb Accessport** - [Cobb Tuning Accessport](https://www.cobbtuning.com/)
- **ECUx** - [APR ECUx](https://www.goapr.com/)
- **Zeitronix** - [Zeitronix wideband logging](https://www.zeitronix.com/)
- **Evoscan** - [Mitsubishi EvoScan](https://www.evoscan.com/)
- **LogWorks** - [Innovate Motorsports LogWorks](https://www.innovatemotorsports.com/)
- **VolvoLogger** - [VolvoTools](https://github.com/prometey1982/VolvoTools)
- **M-Tuner** - [M-Engineering M-Tuner](https://www.m-engineering.us/collections/m-tuner)

All formats are automatically mapped to standardized field names (based on ECUx, for better or for worse). If your format isn't supported, ECUxPlot will attempt to parse it using basic field mapping.

## Data Sources

### Zeitronix Logs

If you plan to use Zeitronix logs, make sure to check the **"Include initial summary"** box when exporting to .csv from the Zeitronix Data Logger application. This ensures that ECUxPlot can properly parse and analyze your Zeitronix log files.

## Installation

For detailed installation instructions, see [INSTALL.md](INSTALL.md).

For build instructions and development information, see [BUILD.md](BUILD.md).
