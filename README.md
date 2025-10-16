# ECUxPlot

ECUxPlot is a data analysis tool for automotive ECU (Engine Control Unit) log files. It provides visualization and analysis capabilities for engine performance data, including power estimation, airflow calculations, and fuel system analysis.

## Features

- **Data Visualization**: Plot engine parameters from CSV log files
- **Performance Analysis**: Calculate horsepower estimates and airflow metrics
- **Filtering**: Advanced filtering for wide-open-throttle (WOT) runs
- **Multi-Platform**: Runs on Windows, macOS, and Linux
- **Customizable**: Vehicle profiles and configurable parameters

## Quick Start

1. **Installation**: See [INSTALL.md](INSTALL.md) for detailed installation instructions
2. **Load Data**: Import your ECU log file (CSV format)
3. **Configure**: Set up vehicle profiles and parameters
4. **Analyze**: Use filters and visualization tools to analyze your data

## Troubleshooting

### Filter Issues

**Problem**: With the filter on, I don't see anything!

**Solution**: The filter option searches for a WOT run of a certain length in 3rd gear. If you don't meet that criterion, you won't see any data if you enable the filter.

- Adjust filter parameters in "Options → Configure filter" menu
- Ensure you're logging gear and accelerator pedal position
- Check that your run starts low enough and has sufficient RPM range
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

### macOS Installation Issues

**Problem**: "Application is damaged" error when trying to run ECUxPlot

**Solution**: This is a macOS security feature that blocks unsigned applications. The application is likely not damaged.

**For macOS Mojave (10.14) and earlier**:

- Open Terminal and run: `xattr -c /Applications/ECUxPlot.app`
- Note: This method is less effective on macOS Catalina (10.15) and later

**For macOS Catalina (10.15) and later**:

1. When you first try to run ECUxPlot, macOS will show a dialog saying: **"'ECUxPlot' is damaged and can't be opened. You should move it to the Trash."**
2. **Important**: Click **"Cancel"** - do NOT click "Move to Trash"
3. Go to **System Settings** → **Privacy & Security** (Note: On macOS Monterey and earlier, this is **System Preferences** → **Security & Privacy**)
4. Scroll down to the bottom of the page
5. Look for a message about ECUxPlot being blocked
6. Click **"Allow Anyway"** or **"Open Anyway"**
7. If you don't see the message, run `xattr -c /Applications/ECUxPlot.app` then try running the application again and then check Privacy & Security settings

**Note**: After clicking "Cancel", macOS will show a message telling you that running the app was refused. You can then go to Privacy & Security settings to override this restriction.

### FATS (For the Advancement of the S4)

FATS is ET (Elapsed Time) from 4200-6500 RPM in 3rd gear, by B5S4 convention.

#### Apples-to-Apples Comparison

FATS is intended to approximate 60-90 mph ET in 3rd gear.

For accurate performance comparisons between different vehicles, the FATS RPM range should be adjusted based on each vehicle's `rpm_per_mph` to ensure all vehicles are measured over the same speed range.

**Examples**:

**B5S4 (Audi S4)** - `rpm_per_mph = 72.1`:

- 60 mph = 60 × 72.1 = 4326 RPM (~4300 RPM)
- 90 mph = 90 × 72.1 = 6489 RPM (~6500 RPM)
- **Standard FATS Range**: 4300-6500 RPM (intends to cover 60-90 mph)

**Example Vehicle** - `rpm_per_mph = 67`:

- 60 mph = 60 × 67 = 4020 RPM (~4000 RPM)
- 90 mph = 90 × 67 = 6030 RPM (~6000 RPM)
- **Adjusted FATS Range**: 4000-6000 RPM (correctly covers 60-90 mph)

## Getting Help

If you encounter issues not covered here, please post your log file for assistance. In some cases, ECUxPlot may not detect pedal/gear data properly from the CSV header and may require adding your CSV format to the application.

## Data Sources

### Zeitronix Logs

If you plan to use Zeitronix logs, make sure to check the **"Include initial summary"** box when exporting to .csv from the Zeitronix Data Logger application. This ensures that ECUxPlot can properly parse and analyze your Zeitronix log files.

## Installation

For detailed installation instructions, build targets, and platform-specific information, see [INSTALL.md](INSTALL.md).

**Note**: JRE download is only required for Windows builds. macOS and Linux builds use the system JDK.
