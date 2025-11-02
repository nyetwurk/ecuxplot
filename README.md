# ECUxPlot

[![Build and Release](https://github.com/nyetwurk/ecuxplot/actions/workflows/build-and-release.yml/badge.svg)](https://github.com/nyetwurk/ecuxplot/actions/workflows/build-and-release.yml)
[![Release](https://github.com/nyetwurk/ecuxplot/actions/workflows/release.yml/badge.svg)](https://github.com/nyetwurk/ecuxplot/actions/workflows/release.yml)
[![Java](https://img.shields.io/badge/Java-18-orange.svg)](https://openjdk.java.net/)
[![License](https://img.shields.io/badge/License-GPL%203.0-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-lightgrey.svg)](https://github.com/nyetwurk/ecuxplot)

**Downloads**: [Latest Release](https://github.com/nyetwurk/ecuxplot/releases/latest) | [All Downloads](https://github.com/nyetwurk/ecuxplot/releases)

> **Note**: This README may describe features in the `master` branch which may not be available in the latest release or nightly build.

## What is ECUxPlot?

ECUxPlot is a free, open-source tool for analyzing and visualizing automotive ECU (Engine Control Unit) log files. Whether you're tuning your car, diagnosing issues, or just curious about your engine's performance, ECUxPlot helps you make sense of the data your ECU collects.

**ECUxPlot lets you:**

- 📊 Visualize engine data from your ECU logs
- 🔧 Analyze power, boost, and fuel system performance
- 📈 Compare different logs to track tuning progress
- ⚡ Measure acceleration performance (FATS timing)
- 🎯 Filter data to focus on clean acceleration runs

ECUxPlot works with log files from ME7Logger, VCDS, JB4, Cobb Accessport, Zeitronix, and many other popular ECU logging systems.

## Quick Start

### 1. Download & Install

**macOS**: Download the `.dmg` file → See [INSTALL.md](INSTALL.md) for macOS security steps

**Windows**: Download `ECUxPlot-*-setup.exe` → Run the installer

**Linux**: Download `.tar.gz` → Extract and run `./ECUxPlot.sh`

📝 **New to ECUxPlot?** Start with [INSTALL.md](INSTALL.md) for detailed setup instructions.

### 2. Open Your Log File

- Launch ECUxPlot
- Choose **File → Open** (or drag & drop)
- Select your CSV log file from VCDS, JB4, Cobb Accessport, etc.

### 3. View Your Data

- ECUxPlot automatically detects your log format
- Data is plotted on customizable charts
- Use the axis menus to select what to display

## System Requirements

- **Operating System**: Windows 10+, macOS 10.15+, or Linux
- **Java**:
  - Windows: Included (no installation needed)
  - macOS: Included (DMG installer) or [install separately](https://adoptium.net/) for ZIP
  - Linux: Java 18+
    - Debian/Ubuntu: `sudo apt-get install openjdk-21-jdk`
    - Other distros: [Download Java](https://adoptium.net/)
- **Memory**: 2GB RAM minimum
- **Storage**: 256MB for installation

## Detailed Features

### FATS (For the Advancement of the S4)

FATS measures elapsed time for acceleration runs, providing consistent performance comparisons across different vehicles and conditions.

#### FATS Features

- **Dual Mode Operation**:
  - **RPM Mode**: Direct RPM range measurement (e.g., 4200-6500 RPM)
  - **mph & km/h Mode**: Speed-based measurement (e.g., 60-90 mph) with automatic RPM conversion
- **Advanced Filtering**: Acceleration-based filtering eliminates incomplete and part throttle runs
- **Multi-file Support**: Compare FATS results across multiple log files
- **Integrated Filter Refinement Tools**: Filter parameter refinement tools integrated into the filter configuration window

#### Configuration

FATS can be configured in two ways:

1. **RPM Mode**: Set start and end RPM values directly
2. **mph & km/h Mode**: Set start and end speed values (automatically converted to RPM using `rpm_per_mph`)

The `rpm_per_mph` constant in your vehicle profile determines the RPM-to-speed conversion ratio.

#### Apples-to-Apples Comparison

For accurate performance comparisons between different vehicles, adjust the FATS range based on each vehicle's `rpm_per_mph` to ensure all vehicles are measured over the same speed range.

> **Note**: Vehicle speed data in logs is often not accurate. FATS exclusively uses RPM data to calculate FATS time for this reason!

**Examples**:

**B5S4 (Audi S4)** - `rpm_per_mph = 72.1`:

- 60 mph = 60 × 72.1 = 4326 RPM (~4300 RPM)
- 90 mph = 90 × 72.1 = 6489 RPM (~6500 RPM)
- **Standard FATS Range**: 4300-6500 RPM (covers 60-90 mph)

**Example Vehicle** - `rpm_per_mph = 67`:

- 60 mph = 60 × 67 = 4020 RPM (~4000 RPM)
- 90 mph = 90 × 67 = 6030 RPM (~6000 RPM)
- **Adjusted FATS Range**: 4000-6000 RPM (correctly covers 60-90 mph)

#### Using FATS

1. **Load Data**: Import your ECU log file
2. **Configure Filter**: Set minimum acceleration threshold (default: 100 RPM/s)
3. **Open FATS**: Use **"Options → Show FATS"** menu
4. **Set Range**: Configure RPM or MPH range for measurement
5. **Analyze**: View FATS results with detailed logging

### Range Selector

The Range Selector provides a convenient interface for selecting which files and ranges to display on the chart, making it easy to compare specific runs across multiple data files.

#### Range Selector Features

- **File and Range Selection**: Check/uncheck individual files and ranges to display
- **Visual Organization**: Tree-based display groups files by common prefixes
- **Performance Awards**: Visual indicators show best FATS and power runs
- **Multi-file Support**: Manage selections across multiple loaded log files

#### Using the Range Selector

1. **Load Data**: Import multiple log files (or a single file with multiple ranges)
2. **Open Range Selector**: Use **"Options → Ranges..."** menu
3. **Select Items**: Check the files/ranges you want to display
4. **Apply**: Click "OK" or "Apply" to update the chart
5. **Compare**: Use "Select All" or "Select None" for quick comparisons

#### Award Icons

The Range Selector uses award icons to highlight top-performing runs:

- 🏆 **Best FATS overall**: Fastest FATS time across all data
- ⚡ **Best power overall**: Highest power output across all data
- ⭐ **Best power in group**: Highest power within a file group
- 🥇 **Best FATS in group**: Fastest FATS within a file group
- ⚠ **Incomplete/Poor quality**: Data with insufficient points or RPM range

These awards help you quickly identify your best runs for analysis and comparison.

### Filter and Analysis Tools

ECUxPlot includes comprehensive tools to help refine and tune filter parameters for optimal data analysis:

#### Filter Configuration

Access via **"Options → Filter"** to:

- **Real-time Filter Refinement Analysis**: View filtered data points with detailed filtering information, as you make changes to the filter parameters
- **Data Validation**: See which data points pass/fail filter criteria
- **Parameter Tuning**: Adjust filter thresholds to match your driving patterns
- **Multi-file Support**: Switch between multiple loaded datasets
- **Integrated Interface**: All filter configuration and refinement in one window

#### Event Window

Access via **"Options → Show Events"** to:

- **Live Events**: Real-time display of application events
- **Level Filtering**: Filter by event level (TRACE, DEBUG, INFO, WARN, ERROR)
- **Search Function**: Find specific event entries
- **Export Capability**: Save events to file for analysis

## Troubleshooting

### Common Issues

#### With the filter on, I don't see anything

- The filter requires valid acceleration runs (100 RPM/s minimum)
- Open **"Options → Filter"** to see why data is filtered out
- Check minimum acceleration threshold
- Ensure you're logging gear and accelerator pedal position
- Your run needs sufficient RPM range to show up

#### My power/torque estimates are wrong

- Adjust values in **"Vehicle Profiles → Edit Constants"**
- The vehicle profile settings directly affect calculations

### My HP/TQ graphs are all super wiggly

- This is often caused by excessive jitter in the data. You can try increasing the smoothing window(s) in the **"Options → Filter"** menu.
- If all else fails, report the issue on the [GitHub Issues](https://github.com/nyetwurk/ecuxplot/issues) tracker **with a sample of your log file**.

#### FATS calculation shows no results

- Your log needs valid acceleration runs that meet filter criteria
- Open **"Options → Filter"** to debug data quality
- Check that your RPM range covers the configured FATS range
- Ensure the filter is enabled and finding valid ranges

#### FATS results vary between runs

- May indicate wheel spin or data quality issues
- Check the filter dialog for negative boost pressure (indicates wheel spin)
- Verify consistent acceleration patterns and starting conditions

#### ECUxPlot doesn't recognize my log format

- ECUxPlot supports 15+ formats automatically
- If detection fails, ECUxPlot will still try to parse generic CSV
- Please post your log file on the issue tracker for format support

### Getting Help

- 📁 **Post your log file** for assistance with detection or parsing issues
- 🐛 **Found a bug?** Open an issue on [GitHub Issues](https://github.com/nyetwurk/ecuxplot/issues)
- ❓ **Need help?** Check the [troubleshooting section in INSTALL.md](INSTALL.md#troubleshooting-installation)

> **Note**: Issues without a sample log file may be closed without investigation.

## Supported Log Formats

ECUxPlot automatically detects and parses the following automotive ECU logging formats:

- **VCDS** - [Ross-Tech VAG-COM Diagnostic System](https://www.ross-tech.com/)
- **ME7-Logger** - [ME7Logger](http://nefariousmotorsports.com/forum/index.php/topic,837.0title,.html)
- **OBDLINK** - [OBDLink/OBD-II scan tools](https://www.obdlink.com/) (OBD-II PID data from scan tools)
- **SWComm/ECUTools** - [SWComm ECUTools](https://www.ecutools.com/)
- **JB4** - [Burger Motorsports JB4](https://burgertuning.com/)
- **Cobb Accessport** - [Cobb Tuning Accessport](https://www.cobbtuning.com/)
- **ECUx** - [APR ECUx](https://www.goapr.com/)
- **Zeitronix** - [Zeitronix wideband logging](https://www.zeitronix.com/)
- **Evoscan** - [Mitsubishi EvoScan](https://www.evoscan.com/)
- **LogWorks** - [Innovate Motorsports LogWorks](https://www.innovatemotorsports.com/)
- **VolvoLogger** - [VolvoTools](https://github.com/prometey1982/VolvoTools)
- **M-Tuner** - [M-Engineering M-Tuner](https://www.m-engineering.us/collections/m-tuner)
- **SimosTools** - [SimosTools Android app](https://play.google.com/store/apps/details?id=com.app.simostools) ([GitHub](https://github.com/Switchleg1/SimosTools))

All formats are automatically mapped to standardized field names (based on ECUx, for better or for worse).

> **Note**: If your format isn't supported, ECUxPlot will attempt to parse it using basic field mapping. If you want to see the original field names in your graph, you can enable the **"Options → Original names"** option.

## Data Sources

### Zeitronix Logs

If you plan to use Zeitronix logs, make sure to check the **"Include initial summary"** box when exporting to `.csv` from the Zeitronix Data Logger application. This ensures that ECUxPlot can properly parse and analyze your Zeitronix log files.

## Support & Contributing

ECUxPlot is free and open source. If you find it useful, contributions are appreciated!

**Support the project:**

[![Donate](https://www.paypalobjects.com/en_US/i/btn/btn_donate_LG.gif)](https://www.paypal.com/donate/?hosted_button_id=FFD8L7RNYQ5B2)

**Contribute:**

- 🐛 **Report bugs** or request features on [GitHub Issues](https://github.com/nyetwurk/ecuxplot/issues)
- 💻 **Contribute code** via pull requests
- 📝 **Improve documentation**
- 🔗 **Spread the word** to others who might benefit from ECUxPlot

## Installation

For detailed installation instructions, see [INSTALL.md](INSTALL.md).

For build instructions and development information, see [BUILD.md](BUILD.md).
