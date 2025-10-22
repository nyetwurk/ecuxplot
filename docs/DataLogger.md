# DataLogger Configuration

The `loggers.yaml` file defines logger detection patterns, field aliases, and configuration parameters for all supported logger types.

## YAML Structure

```yaml
loggers:
  LOGGER_NAME:
    type: LOG_TYPE_CONSTANT
    comment_signatures:
      - regex: "PATTERN"
    field_signatures:
      - regex: "PATTERN"
        column_index: 0  # optional
    aliases:
      - ["^ORIGINAL_FIELD$", "STANDARD_FIELD"]
    time_ticks_per_sec: 1000  # optional
    skip_lines: 2  # optional
    header_format: "id"  # optional (default: "id")
```

## Configuration Parameters

### Detection Signatures

- **`comment_signatures`**: Regex patterns matched against comment lines
- **`field_signatures`**: Regex patterns matched against CSV header fields
  - **`column_index`**: Optional specific column to check (null = any column)

### Field Processing

- **`aliases`**: Array of [original_field_regex, standard_field_name] pairs
- **`header_format`**: Header structure format ("id", "id,u", "id,u,id2")
- **`unit_regex`**: Regex pattern to extract field names and units from complex header formats
- **`field_transformations`**: Apply prepend/append transformations to field names

### Line Skipping

- **`skip_lines`**: Number of non-empty lines to skip before CSV parsing
- **Note**: Empty lines do not count towards skip_lines

### Timing Configuration

- **`time_ticks_per_sec`**: Time resolution for timestamp conversion (default: 1000)

## Field Processing Details

### Field Aliasing

Aliases map original field names to standardized names across all logger types:

```yaml
aliases:
  - ["^TimeStamp$", "TIME"]
  - ["^N$", "RPM"]
  - ["^MAP$", "BoostPressureActual"]
```

**Pattern Matching**:

- Uses Java regex syntax
- Case-sensitive matching
- Anchored patterns (^ and $) for exact matches

### Unit Regex Processing

The `unit_regex` feature allows extraction of field names, units, and additional information from complex header formats using a single regex pattern:

```yaml
unit_regex: "([\\S\\s]+)\\(([\\S\\s]+)\\)\\s*(.*)"
```

**How it works**:

- **Group 1**: Extracted field name (assigned to `id`)
- **Group 2**: Extracted unit (assigned to `u`)
- **Group 3**: Additional information (assigned to `id2`)

**Example** (VOLVOLOGGER):

- **Input**: `"Boost Pressure(mBar) BoostPressure"`
- **Regex**: `"([\\S\\s]+)\\(([\\S\\s]+)\\)\\s*(.*)"`
- **Output**:
  - `id[2]` = `"Boost Pressure"` (field name)
  - `u[2]` = `"mBar"` (unit)
  - `id2[2]` = `"BoostPressure"` (ME7L variable)

**Benefits**:

- Eliminates need for complex case statements in Java code
- YAML-driven configuration for complex field parsing
- Reusable across different logger types with similar formats

### Field Transformations

The `field_transformations` feature allows applying prepend/append operations to field names with optional conditional logic:

```yaml
field_transformations:
  prepend: "PREFIX "
  if_empty: true  # optional
  append: " SUFFIX"
  exclude_fields: ["FIELD1", "FIELD2"]  # optional
```

**Configuration Options**:

- **`prepend`**: Add text to the beginning of field names
- **`append`**: Add text to the end of field names
- **`exclude_fields`**: Array of field names to skip during transformations
- **`if_empty`**: Only apply transformations to empty/null field names

**Conditional Logic**:

- **`if_empty: true`**: Transformations only apply to empty or null field names
- **`if_empty: false` or omitted**: Transformations apply to all fields (default behavior)

**Example** (ME7LOGGER):

```yaml
field_transformations:
  prepend: "ME7L "
  if_empty: true
```

**How it works**:

- **Empty fields**: Get prefixed with "ME7L " (e.g., `""` → `"ME7L dwkrz_0"`)
- **Non-empty fields**: Remain unchanged (e.g., `"RPM"` → `"RPM"`)
- **Excluded fields**: Skipped entirely regardless of empty status

**Benefits**:

- YAML-driven field name transformations
- Conditional logic for selective application
- Eliminates need for hardcoded case statements
- Flexible configuration for different logger requirements

## Build Integration

The YAML file is converted to XML during build via `scripts/yaml_to_xml.py`:

```bash
python scripts/yaml_to_xml.py src/org/nyet/ecuxplot/loggers.yaml build/loggers.xml
```

The generated XML is loaded by `DataLogger.java` at runtime for dynamic configuration.

## Adding New Logger Types

- **Add YAML definition**:

```yaml
NEW_LOGGER:
  type: LOG_NEW_LOGGER
  comment_signatures:
    - regex: "PATTERN"
  aliases:
    - ["^FIELD$", "STANDARD_FIELD"]
```

- **Add Java constant**:

```java
public static final String LOG_NEW_LOGGER = "NEW_LOGGER";
```

- **Update build process**:
  - Ensure `loggers.xml` is generated
  - Add to `Makefile` dependencies

- **Add test data**:
  - Create test file in `test-data/`
  - Add expectations to `test-expectations.xml`

## Configuration Validation

The system validates:

- Logger type detection accuracy
- Field alias mapping correctness
- Unit parsing consistency
- Data integrity preservation
