# Test Data Files

This directory contains test files for validating logger detection and parsing functionality. All files have been truncated to contain only the essential header information and a few sample data rows for testing purposes.

## Test Expectations

The `test-expectations.xml` file defines expected results for each test file, including:

### XML Structure

```xml
<test_expectations>
    <test_case file="filename.csv">
        <expected_type>LOGGER_TYPE</expected_type>
        <expected_ids>
            <id index="0">FIELD_NAME</id>
            <!-- ... more fields ... -->
        </expected_ids>
        <expected_id2s>
            <id2 index="0">ORIGINAL_FIELD_NAME</id2>
            <!-- ... more fields ... -->
        </expected_id2s>
        <expected_units>
            <unit index="0">UNIT</unit>
            <!-- ... more fields ... -->
        </expected_units>
        <sanity_check row="0" col="0">EXPECTED_VALUE</sanity_check>
    </test_case>
</test_expectations>
```

### Field Definitions

- **`expected_type`**: Logger type string (e.g., "SWCOMM", "COBB_AP", "JB4")
- **`expected_ids`**: Aliased field names after processing (e.g., "TIME", "RPM", "BoostPressure")
- **`expected_id2s`**: Original field names before aliasing (e.g., "TimeStamp", "N", "MAP")
- **`expected_units`**: Parsed units for each field (e.g., "s", "RPM", "mBar")
- **`sanity_check`**: Expected value at row 0, column 0 for data validation

### Test Validation

The test framework validates:

1. **Logger Detection**: Confirms correct logger type is detected
2. **Field Aliasing**: Verifies original field names are mapped to standard names
3. **Unit Parsing**: Checks that units are correctly extracted from headers
4. **Data Integrity**: Validates first data cell matches expected value

### Current Test Status

- **Total Tests**: 188
- **Passed**: 130
- **Failed**: 58

**Common Failure Patterns**:

- **ID2 Fields**: All returning `null` instead of expected original field names
- **Sanity Checks**: Some expectations don't match actual parsed data
- **Unit Parsing**: Inconsistent unit extraction across logger types

## Available Test Files

### Original Test Files (truncated and renamed)

These files were copied from the original `test/` directory, truncated to essential data, and renamed for clarity:

- `swcomm.csv` - SWComm ECUTools format
- `cobb-ap.csv` - Cobb Accessport format
- `jb4.csv` - JB4 format
- `me7l.csv` - ME7-Logger format (2.7L V6/5VT engine, 121 bytes)
- `vcds.csv` - VCDS format (English)
- `vcds-german.csv` - VCDS format (German)
- `m-tuner.csv` - M-Tuner format
- `m-tuner-speed.csv` - M-Tuner format with vehicle speed
- `zeitronix.csv` - Zeitronix format
- `ecux.csv` - ECUx format

### Generated Test Files (truncated)

`evoscan.csv` - Evoscan format is the only remaning "guess" (detection: `^LogID$`)

## File Format Details

### VCDS Format (`vcds.csv`)

The English VCDS log file demonstrates the standard VCDS structure:

1. **Line 1**: Detection line - `Sunday,11,October,2009,18:29:08` (day of week)
2. **Line 2**: ECU type line - `8K0 907 551 A,ADVMB,3.0T SIMOS84  H06 0001,`
3. **Line 3**: Block headers - `,,G023,F0,G024,F0,G035,F0,G332,F0,G485,F0,G487,F0,G500,F0,G501,F0,G508,F0,G652,F0,G693,F0,G694,F0,`
4. **Line 4**: Empty line
5. **Line 5**: Field descriptions - `Marker,TIME,Group 23 - Field 0,TIME,Group 24 - Field 0,TIME,Group 35 - Field 0,...`
6. **Line 6**: Units - `,STAMP,Vehicle speed,STAMP,Accelerator position,STAMP,Engine Speed,...`
7. **Line 7**: Units - `,, km/h,, %,, /min,, %,, /min,, /min,, hPa,, hPa,, %,, Nm,, km/h,, km/h,`
8. **Line 8+**: Data rows

**Key Features**:

- Detection pattern: `^.*(day|tag)$` (matches day of week)
- Skip lines: 2 (skips detection and ECU type lines)
- English field names: "Vehicle speed", "Accelerator position", "Engine Speed", etc.
- Group-based structure: G023, G024, G035, etc.

### ECUx Format (`ecux.csv`)

The ECUx log file demonstrates the standard ECUx structure:

**Key Features**:

- Detection pattern: `^TIME$` (first column must be exactly "TIME")
- Simple CSV format with quoted fields
- Time ticks per second: 1000 (milliseconds)
- Standard field names: "RPM", "ThrottlePlateAngle", "MassAirFlow", "BoostPressureDesired", etc.
- No special header parsing required - direct CSV format

### ME7-Logger Format (`me7l.csv`)

The ME7-Logger format demonstrates the standard ME7-Logger structure:

**Key Features**:

- Detection pattern: `.*ME7-Logger.*` (contains "ME7-Logger" anywhere in the file)
- Complex 3-section format: VARS → UNITS → ALIASES
- Time ticks per second: 1000 (milliseconds)
- Field names prefixed with "ME7L " (e.g., "ME7L IgnitionRetardCyl1")
- Different ECU configurations result in different parameter sets and packet sizes

**Note**: The original test data included `custom60.csv` and `custom65.csv` which were actually ME7-Logger files with different ECU definitions (Skoda Octavia 1.8L R4/5VT vs Audi 2.7L V6/5VT). These were removed as they're redundant with the existing `me7l.csv` file.

## File Format Notes

- All files have been truncated to contain only essential header information and 3-5 sample data rows
- Files are named descriptively to indicate the logger type they represent
- Detection patterns are based on the original code's regex patterns
- Both English and German VCDS formats are available for testing

## Usage

Run `make test` to test logger detection and parsing for all files in this directory.
