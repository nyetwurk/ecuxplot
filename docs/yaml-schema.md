# ECUxPlot YAML Schema Documentation

## Overview

This document describes the complete YAML schema for ECUxPlot's logger configuration system. The schema defines how logger types are detected, parsed, and processed.

## Schema Description

### Logger Definition Structure

```yaml
loggers:
  LOGGER_NAME:
    # Detection Configuration
    comment_signatures: []     # Patterns to match in comment lines
    field_signatures: []      # Patterns to match in CSV field lines

    # Parsing Configuration
    skip_lines: 0             # Lines to skip after detection
    header_format: "id"      # Header structure format

    # Timing Configuration
    time_ticks_per_sec: 1000  # Time resolution for this logger

    # Field Mapping
    aliases:                  # Field name mappings
      - ["^pattern$", "replacement"]

    # Complex Field Processing
    unit_regex: "pattern"    # Extract field names and units from complex formats

    # Field Transformations
    field_transformations:   # Apply prepend/append with conditional logic
      prepend: "PREFIX "
      if_empty: true  # optional
      append: " SUFFIX"
      exclude_fields: ["FIELD1", "FIELD2"]  # optional
```

### Detection Field Descriptions

- **`comment_signatures`** (array): List of regex patterns to match in comment lines
  - Format: `["regex_pattern"]`
  - Example: `["^VCDS.*", ".*ME7-Logger.*"]`
  - Required: No (empty array if not used)

- **`field_signatures`** (array): List of regex patterns to match in CSV field lines
  - Format: `["regex_pattern"]` or `[{"regex": "pattern", "column_index": 0}]`
  - Example: `["^TIME$", {"regex": "^Firmware$", "column_index": 2}]`
  - Required: No (empty array if not used)
  - **`column_index`** (integer, optional): Specific column to check for the pattern

#### Parsing Fields

- **`skip_lines`** (integer): Maximum number of non-empty lines to skip after detection
  - Default: `0`
  - Example: `5` (skip up to 5 non-empty lines after detection)
  - Required: No
  - **Note**: Empty lines do not count towards `skip_lines`. Only lines with actual content are counted.
  - **Implementation**: Default value set during XML parsing/construction phase
  - **Works with `skip_regex`**: `skip_lines` sets the maximum limit, `skip_regex` can exit early

- **`skip_regex`** (array): Dynamic line skipping using regex patterns to find specific header lines
  - Default: `[]` (empty array - no regex skipping)
  - Example: `[{"regex": "^TimeStamp$"}]` (find line where any column is "TimeStamp")
  - Example: `[{"regex": "^sec\\.ms"}]` (find line where any column starts with "sec.ms")
  - Example: `[{"regex": "^.*$", "column": 1}]` (find line where second column has any content)
  - **Format**: Array of objects with `regex` (required) and `column` (optional, defaults to -1 for any column)
  - **Implementation**: Searches through lines until regex matches specified column (or any column if column=-1)
  - **Note**: More robust than `skip_lines` for files with variable metadata line counts
  - **Use case**: ME7L logs where VARS/UNITS/ALIASES sections can have different positions
  - **Works with `skip_lines`**: Can exit early if pattern matches before reaching `skip_lines` limit
  - Required: No

#### Timing Fields

- **`time_ticks_per_sec`** (integer): Time resolution for this logger
  - Default: `1000`
  - Example: `10` (10 samples per second)
  - Required: No

#### Field Mapping Fields

- **`aliases`** (array): Field name mappings
  - Format: `[["pattern", "replacement"]]`
  - Example: `[["^[Tt]ime$", "TIME"], ["^RPM$", "RPM"]]`
  - Required: No (empty array if not used)

- **`unit_regex`** (string): Regex pattern to extract field names and units from complex header formats
  - Format: `"regex_pattern"`
  - Example: `"([\\S\\s]+)\\(([\\S\\s]+)\\)\\s*(.*)"`
  - **Group 1**: Extracted field name (assigned to `id`)
  - **Group 2**: Extracted unit (assigned to `u`)
  - **Group 3**: Additional information (assigned to `id2`)
  - Required: No
  - **Use case**: Complex field formats like VOLVOLOGGER's `"Boost Pressure(mBar) BoostPressure"`

## Header Format Schema (Implemented)

### Universal Header Format Field

The `header_format` field provides a universal way to describe any CSV header structure:

```yaml
# Header structure (implemented)
header_format: "id"        # Default: single header line with field names
header_format: "id,u"      # Field names + units (2 header lines)
header_format: "id,u,id2"  # Field names + units + aliases (3 header lines)
header_format: "g,id,u"    # Group line + field names + units (VCDS format)
header_format: "g,id,u,u2" # Group line + field names + units + secondary units (VCDS_LEGACY format)
```

#### Header Format Field Descriptions

- **`header_format`** (string): Describes the header structure using tokens
  - **Default**: `"id"` (single header line with field names)
  - **`"id,u"`**: Field names + units (2 header lines)
  - **`"id,u,id2"`**: Field names + units + aliases (3 header lines)
  - **`"g,id,u"`**: Group line + field names + units (VCDS format)
  - **`"g,id,u,u2"`**: Group line + field names + units + secondary units (VCDS_LEGACY format)
  - **Required**: No (defaults to `"id"` if not specified)
  - **Implementation**: Default value set during XML parsing/construction phase

#### Header Format Tokens

- **`g`**: Group line (used by VCDS/VCDS_LEGACY for group markers, e.g., "Group A:", "Group 115")
- **`id`**: Field names (original field names from CSV)
- **`u`**: Units (units for each field, first units line)
- **`u2`**: Secondary units line (used by VCDS_LEGACY for second units line)
- **`id2`**: Secondary field information (aliases, ME7L variable names, or group lines depending on logger type)

#### How Header Format Works

The parser finds field-like lines in sequence and assigns them to tokens:

- **1st occurrence** of field-like data → assigned to 1st token
- **2nd occurrence** of field-like data → assigned to 2nd token
- **3rd occurrence** of field-like data → assigned to 3rd token

This approach works regardless of absolute line numbers, blank lines, or metadata variations.

### Field Transformation Fields

These fields define transformations to apply to field names:

```yaml
# Field transformations (implemented)
field_transformations:
  prepend: "ME7L "
  if_empty: true  # optional
  append: " (G024)"
  exclude_fields: ["FIELD1", "FIELD2"]  # optional
```

#### Field Transformation Descriptions

- **`field_transformations`** (object): Field name transformations
  - **`prepend`** (string): Text to prepend to field names
    - Example: `"ME7L "` (add "ME7L " prefix)
  - **`append`** (string): Text to append to field names
    - Example: `" (G024)"` (add group suffix)
  - **`exclude_fields`** (array): Field names to skip during transformations
    - Example: `["RPM", "TIME"]` (skip these fields)
  - **`if_empty`** (boolean, optional): Only apply transformations to empty/null field names
    - Example: `true` (only transform empty fields)
    - Default: `false` (apply to all fields)
  - Required: No

## Complete Schema

```yaml
loggers:
  LOGGER_NAME:
    # Detection Configuration
    comment_signatures: []     # Patterns to match in comment lines
    field_signatures: []      # Patterns to match in CSV field lines

    # Parsing Configuration
    skip_lines: 0             # Lines to skip after detection

    # Header Structure (only specify if NOT default)
    header_format: "id,u"     # Optional: "id,u" or "id,u,id2"
                               # Default: "id" (single header line)

    # Timing Configuration
    time_ticks_per_sec: 1000  # Time resolution for this logger

    # Field Mapping
    aliases:                  # Field name mappings
      - ["^pattern$", "replacement"]

    # Complex Field Processing
    unit_regex: "pattern"    # Extract field names and units from complex formats

    # Field Transformations
    field_transformations:   # Apply prepend/append with conditional logic
      prepend: "PREFIX "
      if_empty: true  # optional
      append: " SUFFIX"
      exclude_fields: ["FIELD1", "FIELD2"]  # optional
```

## Implementation Notes

### Backward Compatibility

The `header_format` field is optional and maintains backward compatibility:

- Existing YAML files will continue to work
- Default behavior is `header_format: "id"` (single header line)
- Only specify `header_format` when you need multi-header parsing

### Field Validation

- **Regex patterns**: Must be valid Java regex patterns
- **Header format**: Must be one of `"id"`, `"id,u"`, or `"id,u,id2"`
- **Skip values**: Must be valid integers (skip_lines) or booleans
- **Aliases**: Must be valid regex patterns and replacement strings

### Error Handling

- Invalid regex patterns: Log warning, skip pattern
- Invalid header format: Log warning, use default `"id"`
- Invalid skip values: Log warning, use default values
- Invalid aliases: Log warning, skip alias

### Header Format Logic

The parser automatically determines `skip_parse_units` based on `header_format`:

- `header_format: "id"` → `skip_parse_units: false` (parse units from field names)
- `header_format: "id,u"` → `skip_parse_units: true` (units are in header)
- `header_format: "id,u,id2"` → `skip_parse_units: true` (units are in header)

## Examples

### VCDS Logger (Multi-Header with Units)

```yaml
VCDS:
  field_signatures:
    - regex: ".*VCDS Version:.*"
  skip_lines: 5
  header_format: "id,u"  # Field names + units (2 header lines)
  aliases:
    - ["^Zeit$", "TIME"]
    - ["^Boost Pressure \\(actual\\)$", "BoostPressureActual"]
    - ["^Engine [Ss]peed.*", "RPM"]
```

### ME7LOGGER (Multi-Header with Units and Aliases)

```yaml
ME7LOGGER:
  field_signatures:
    - regex: ".*ME7-Logger.*"
  skip_lines: 20  # Maximum lines to skip (safety limit)
  skip_regex:
    - regex: "^TimeStamp$"
  header_format: "id2,u,id"  # Field names + units + aliases (3 header lines)
  field_transformations:
    prepend: "ME7L "
    if_empty: true
  aliases:
    - ["^Engine[Ss]peed$", "RPM"]
    - ["^BoostPressureSpecified$", "BoostPressureDesired"]
```

**Note**: The `skip_regex` approach dynamically finds the VARS, UNITS, and ALIASES sections regardless of metadata line count variations, making it more robust than fixed `skip_lines`. The `skip_lines: 20` provides a safety limit - if the regex pattern isn't found within 20 lines, the parser will stop searching. **Both `skip_regex` and `skip_lines` work together for maximum flexibility.**

#### Skip Lines and Skip Regex Use Cases

#### Case 1: Only `skip_lines` (Fixed Skipping)

```yaml
skip_lines: 5
# skip_regex: []  # empty or omitted
```

- Skips exactly 5 non-empty lines
- Use when you know the exact number of lines to skip

#### Case 2: Only `skip_regex` (Dynamic Search)

```yaml
skip_lines: 0  # or omit
skip_regex:
  - regex: "^TimeStamp$"
```

- Searches until regex pattern matches
- Use when you need to find a specific pattern

#### Case 3: Both Together (Smart Search with Safety Limit)

```yaml
skip_lines: 20
skip_regex:
  - regex: "^TimeStamp$"
```

- Searches up to 20 lines, exits early if pattern found
- Use when you want intelligent search with a safety limit

### JB4 Logger (Fixed Skipping)

```yaml
JB4:
  field_signatures:
    - regex: "^Firmware$"
  skip_lines: 3
  time_ticks_per_sec: 10
  # header_format: "id"  # Default - not needed to specify
  aliases:
    - ["^timestamp$", "TIME"]
    - ["^rpm$", "RPM"]
```

### COBB_AP Logger (Default Single Header)

```yaml
COBB_AP:
  field_signatures:
    - regex: "^AP Info:.*"
  # header_format: "id"  # Default - not needed to specify
  aliases:
    - ["^Time.*", "TIME"]
    - ["^Engine Speed.*", "RPM"]
    - ["^Accel Pedal Position.*", "AcceleratorPedalPosition"]
```

### VOLVOLOGGER Logger (Unit Regex Processing)

```yaml
VOLVOLOGGER:
  field_signatures:
    - regex: "^Time \\(sec\\)$"
  unit_regex: "([\\S\\s]+)\\(([\\S\\s]+)\\)\\s*(.*)"
  aliases:
    - ["^Time$", "TIME"]
    - ["^Engine Speed.*", "RPM"]
    - ["^Boost Pressure.*", "BoostPressureActual"]
    - ["^Throttle Pos.*", "ThrottlePlateAngle"]
    - ["^Vehicle Speed.*", "VehicleSpeed"]
```

**How VOLVOLOGGER unit_regex works**:

- **Input**: `"Boost Pressure(mBar) BoostPressure"`
- **Regex**: `"([\\S\\s]+)\\(([\\S\\s]+)\\)\\s*(.*)"`
- **Output**:
  - `id[2]` = `"Boost Pressure"` (field name)
  - `u[2]` = `"mBar"` (unit)
  - `id2[2]` = `"BoostPressure"` (ME7L variable)
- **After aliases**: `id[2]` = `"BoostPressureActual"`

This schema provides a universal foundation for describing any CSV log format using the `header_format` field and `unit_regex` for complex field parsing, while maintaining simplicity and backward compatibility.

## Build Integration

The YAML file is converted to XML during build via `scripts/yaml_to_xml.py`:

```bash
python scripts/yaml_to_xml.py src/org/nyet/ecuxplot/loggers.yaml build/loggers.xml
```

The generated XML is loaded by `DataLogger.java` at runtime for dynamic configuration.
