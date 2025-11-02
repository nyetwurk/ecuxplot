# Test Data Files

This directory contains test files for validating logger detection and parsing functionality. All files have been truncated to contain only the essential header information and a few sample data rows for testing purposes.

## Test Framework

The `test-expectations.xml` file defines expected results for each test file, including logger type detection, field aliasing, and data validation.

### Test Expectations XML Structure

The `test-expectations.xml` file contains comprehensive test definitions:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<test-expectations>
    <test-file name="filename.csv">
        <logger-type>EXPECTED_LOGGER_TYPE</logger-type>
        <field-expectations>
            <field original="OriginalFieldName" expected="StandardFieldName" unit="Unit"/>
            <field original="AnotherField" expected="AnotherStandard" unit="AnotherUnit"/>
        </field-expectations>
        <data-expectations>
            <field name="StandardFieldName" expected-value="123.45"/>
        </data-expectations>
    </test-file>
</test-expectations>
```

#### Field Definitions

- **`logger-type`**: Expected logger type that should be detected
- **`field-expectations`**: Maps original field names to standard field names with units
  - **`original`**: Original field name from the CSV file
  - **`expected`**: Standard field name after aliasing
  - **`unit`**: Unit extracted from the field name or header
- **`data-expectations`**: Validates first data cell values for specific fields
  - **`name`**: Standard field name to validate
  - **`expected-value`**: Expected value in the first data cell

#### XML Field Types

The test expectations XML supports several field types for comprehensive validation:

- **`type`**: Field type classification (e.g., "time", "rpm", "pressure")
- **`id`**: Original field identifier from the CSV
- **`units`**: Unit information extracted from headers or field names
- **`id2`**: Secondary identifier (often used for ME7L variable names)
- **`sanity-expectations`**: Validation rules for field values (min/max ranges, data types)

### Test Validation

The test framework validates:

- **Logger Detection**: Confirms correct logger type is detected
- **Field Aliasing**: Verifies original field names are mapped to standard names
- **Unit Parsing**: Checks that units are correctly extracted from headers
- **Data Integrity**: Validates first data cell matches expected value

## Available Test Files

Test files for various logger formats:

- `cobb-ap.csv` - Cobb Accessport format
- `ecux.csv` - ECUx format
- `evoscan.csv` - Evoscan format (speculative CSV, not actual data)
- `jb4.csv` - JB4 format
- `logworks.csv` - LogWorks format
- `m-tuner-speed.csv` - M-Tuner format with vehicle speed
- `m-tuner.csv` - M-Tuner format
- `me7l-1.csv` - ME7-Logger format variant
- `me7l-semicolons.csv` - ME7-Logger format with semicolon separators
- `me7l.csv` - ME7-Logger format
- `obdlink.csv` - OBDLink/OBD-II scan tool format
- `simostools.csv` - SimosTools Android app format
- `swcomm.csv` - SWComm ECUTools format
- `vcds-002-007-german.csv` - VCDS legacy format (German)
- `vcds-002-031.csv` - VCDS legacy format with "g" and "u2" header columns
- `vcds-003-020-026.csv` - VCDS legacy format with "g" and "u2" header columns
- `vcds-003-114-020.csv` - VCDS legacy format with "g" and "u2" header columns
- `vcds-115-118.csv` - VCDS legacy format with multiple groups (Group 115, Group 118)
- `vcds.csv` - VCDS format (English)
- `volvologger.csv` - Volvologger format (speculative CSV, not actual data)
- `winlog.csv` - Winlog logger format
- `zeitronix.csv` - Zeitronix format

**Note**: Both `evoscan.csv` and `volvologger.csv` are speculative CSVs created for testing purposes and do not contain actual logger data.

## Usage

Run `make test` to test logger detection and parsing for all files in this directory.
