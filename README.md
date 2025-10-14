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
