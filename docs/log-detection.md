# ECUxPlot Log Detection and Parsing System

## Overview

ECUxPlot uses a YAML-driven configuration system for detecting and parsing various automotive logger formats. The system automatically detects logger types based on comment signatures and field patterns, then applies appropriate parsing logic for each format.

## Architecture

### Core Components

- **`loggers.yaml`**: YAML configuration defining detection patterns and parsing parameters
- **`loggers.xml`**: Generated XML file (converted from YAML during build)
- **`DataLogger.java`**: Loads configuration and provides detection/parsing utilities
- **`ECUxDataset.java`**: Contains parsing logic with logger-specific case statements
- **`scripts/yaml_to_xml.py`**: Converts YAML to XML during build process

### Detection Process

- **Comment Detection**: Scan comment lines for regex patterns defined in `comment_signatures`
- **Field Detection**: Scan CSV header lines for regex patterns defined in `field_signatures`
- **Logger Selection**: Use detected logger type or fall back to DEFAULT
- **Parsing**: Apply logger-specific parsing logic based on detected type

## YAML Configuration Structure

### Field Categories (Shared)

```yaml
field_categories:
  pedal: ["AcceleratorPedalPosition", "AccelPedalPosition", ...]
  throttle: ["ThrottlePlateAngle", "Throttle Angle", ...]
  gear: ["Gear", "SelectedGear", "Engaged Gear"]
```

### Logger Definitions

```yaml
loggers:
  LOGGER_NAME:
    # Detection Configuration
    comment_signatures: []     # Patterns to match in comment lines
    field_signatures: []      # Patterns to match in CSV field lines

    # Parsing Configuration
    skip_lines: 0             # Lines to skip after detection
    skip_regex: []            # Dynamic line skipping using regex patterns
    header_format: "id"       # Header structure format

    # Timing Configuration
    time_ticks_per_sec: 1000  # Time resolution for this logger

    # Field Mapping
    aliases:                  # Field name mappings
      - ["^pattern$", "replacement"]
```

## Detection Signatures

### Comment Signatures

Match patterns in comment lines (lines starting with `#` or other comment markers):

```yaml
comment_signatures:
  - regex: ".*VCDS Version:.*"
  - regex: ".*ME7-Logger.*"
```

### Field Signatures

Match patterns in CSV header fields:

```yaml
field_signatures:
  - regex: "^TIME$"                    # Match any column
  - regex: "^Firmware$"               # Match any column
    column: 2                         # Match specific column (optional)
```

**Column Index**:

- `column: -1` or omitted: Check any column (default)
- `column: 0`: Check first column
- `column: 1`: Check second column, etc.

## Parsing Configuration

### Line Skipping

#### Fixed Line Skipping (`skip_lines`)

```yaml
skip_lines: 5  # Skip exactly 5 non-empty lines
```

#### Dynamic Line Skipping (`skip_regex`)

```yaml
skip_regex:
  - regex: "^TimeStamp$"              # Find line where any column is "TimeStamp"
  - regex: "^sec\\.ms"                # Find line where any column starts with "sec.ms"
  - regex: "^.*$"                     # Find line where any column has content
    column: 1                         # Check specific column
```

**Combined Usage**:

```yaml
skip_lines: 20                        # Maximum lines to check
skip_regex:
  - regex: "^TimeStamp$"              # Exit early if pattern found
```

### Header Format

Describes the structure of CSV headers:

```yaml
header_format: "id"        # Single header line with field names (default)
header_format: "id,u"      # Field names + units (2 header lines)
header_format: "id,u,id2" # Field names + units + aliases (3 header lines)
header_format: "id2,id,u" # Custom order for specific loggers
```

**Tokens**:

- `id`: Field names (original field names from CSV)
- `u`: Units (units for each field)
- `id2`: Secondary field information (varies by logger type)

### ID2 Field Processing

The `id2` field serves different purposes depending on the logger type and configuration:

#### Default Behavior (No Explicit id2 Token)

When `header_format` doesn't include an `id2` token, the system automatically populates `id2` with a copy of the original field names (`id`) to preserve them before aliases are applied.

#### Logger-Specific ID2 Usage

**VCDS Logger** (`header_format: "id2,id,u"`):

- `id2` = Group line (e.g., "Group 24", "Group 115")
- Used for Group/Block detection and special field processing
- Example: Group 24 blacklist logic for "Accelerator position" → "AcceleratorPedalPosition (G024)"

**ME7LOGGER** (`header_format: "id2,u,id"`):

- `id2` = Raw ME7L variable names (e.g., "dwkrz_0", "ldkfms")
- `id` = Canonical field names (e.g., "RPM", "BoostPressureActual")
- Used for field transformations when `if_empty: true`

**VOLVOLOGGER** (with `unit_regex`):

- `id2` = ME7L variable names extracted from complex field formats
- Example: `"Boost Pressure(mBar) BoostPressure"` → `id2 = "BoostPressure"`

#### Field Transformations with ID2

When `field_transformations` has `if_empty: true`, empty field names use the original `id2` value for transformations:

- Empty field: `""` + `id2: "dwkrz_0"` → `"ME7L dwkrz_0"`
- Non-empty field: `"RPM"` → `"RPM"` (unchanged)

### Field Aliases

Map original field names to standardized names:

```yaml
aliases:
  - ["^TimeStamp$", "TIME"]
  - ["^N$", "RPM"]
  - ["^MAP$", "BoostPressureActual"]
  - ["^Engine [Ss]peed.*", "RPM"]  # Regex patterns supported
```

## Logger-Specific Parsing

### Current Implementation Strategy

The system has largely migrated to YAML-driven configuration, with minimal logger-specific code remaining:

- **YAML Configuration**: Handles detection, parsing parameters, field aliases, and transformations
- **Minimal Case Statements**: Only VCDS retains complex parsing logic that cannot be expressed in YAML

### Remaining Logger-Specific Code

#### VCDS (Complex Multi-Header Processing)

The main logger-specific case statement handles VCDS's complex header processing:

```java
case "VCDS": {
    // VCDS uses header_format: "id2,id,u" which gives us:
    // id2 = Group line (for Group/Block detection)
    // id = Field names line (clean field names)
    // u = Units line (actual units)

    for (int i = 0; i < id.length; i++) {
        String g = (id2 != null && i < id2.length) ? id2[i] : null;
        if (id2 != null && i < id2.length) {
            id2[i] = id[i]; // id2 gets copy of original field names
        }

        // VCDS TIME field detection
        if (u != null && i < u.length) {
            if (id[i] != null && id[i].matches("^(TIME|Zeit|Time|STAMP|MARKE)$")) {
                id[i] = "TIME";
                u[i] = "s";
            } else if ((id[i] == null || id[i].trim().isEmpty()) &&
                      u[i] != null && u[i].matches("^(STAMP|MARKE)$")) {
                id[i] = "TIME";
                u[i] = "s";
            }
        }

        // Group 24 blacklist logic
        if (g != null && g.matches("^Group 24.*") &&
            id[i].equals("Accelerator position")) {
            id[i] = "AcceleratorPedalPosition (G024)";
        }
    }
    break;
}
```

**Why VCDS Still Needs Special Handling**:

- **Complex Group/Block Logic**: VCDS files have Group lines that affect field processing
- **TIME Field Detection**: Special logic for detecting TIME fields from field names or units
- **Group 24 Blacklist**: Specific business logic for Group 24 accelerator position fields
- **Header Format Variations**: VCDS files have inconsistent header structures that require special handling

### Migration Status

**✅ Fully Migrated to YAML**:

- ME7LOGGER: Dynamic header finding via `skip_regex`
- ZEITRONIX: Field prefixing via `field_transformations`
- JB4, COBB_AP, ECUX, EVOSCAN, LOGWORKS, VOLVOLOGGER, SWCOMM: All parsing handled by YAML configuration

**🔄 Partially Migrated**:

- VCDS: Detection, aliases, and basic parsing in YAML; complex header processing remains in Java

#### Other Logger-Specific Conditionals

While most parsing has moved to YAML, several logger-specific conditionals remain in the UI and calculation code:

**Zeitronix Field Processing** (`AxisMenu.java`, `ECUxDataset.java`):

```java
// Zeitronix field prefixing and conversions
if(id.matches("^Zeitronix.*")) {
    if(id.matches("^Zeitronix Boost")) {
        this.add("Zeitronix Boost (PSI)");
        addToSubmenu("Calc Boost", "Boost Spool Rate Zeit (RPM)");
    }
    if(id.matches("^Zeitronix AFR")) {
        this.add("Zeitronix AFR (lambda)");
    }
    if(id.matches("^Zeitronix Lambda")) {
        this.add("Zeitronix Lambda (AFR)");
    }
    addToSubmenu("Zeitronix", item);
}
```

**ME7LOGGER-Specific Calculations** (`AxisMenu.java`):

```java
// ME7LOGGER-specific calculated fields
if (dsid.type.equals("ME7LOGGER")) {
    addToSubmenu("Calc MAF", "Sim Load");
    addToSubmenu("Calc MAF", "Sim Load Corrected");
    addToSubmenu("Calc MAF", "Sim MAF");
}

if (dsid.type.equals("ME7LOGGER")) {
    addToSubmenu("Calc IAT", "Sim evtmod");
    addToSubmenu("Calc IAT", "Sim ftbr");
    addToSubmenu("Calc IAT", "Sim BoostIATCorrection");
}
```

**JB4-Specific Field Handling** (`AxisMenu.java`):

```java
// JB4 boost pressure calculation
if(id.matches("BoostPressureDesiredDelta")) {
    this.add(new DatasetId("BoostPressureDesired", null, units));
}
```

**ME7L Field Processing** (`ECUxDataset.java`, `AxisMenu.java`):

```java
// ME7L-specific field references
final DoubleArray ps_w = super.get("ME7L ps_w").data;
if(id.matches("^ME7L.*")) {
    addToSubmenu("ME7 Logger", item);
    if(id.matches("ME7L ps_w")) {
        addToSubmenu("Calc Boost", "Sim pspvds");
        addToSubmenu("Boost", "ps_w error");
    }
}
```

**Why These Conditionals Remain**:

- **UI Organization**: Zeitronix fields need special menu organization and unit conversions
- **Logger-Specific Calculations**: ME7LOGGER has unique calculated fields not available in other loggers
- **Field References**: Some calculations depend on logger-specific field names (e.g., "ME7L ps_w")
- **Unit Conversions**: Different loggers use different units (PSI vs mBar, °F vs °C) requiring special handling

## Build Integration

### YAML to XML Conversion

The build process automatically converts YAML to XML:

```bash
python scripts/yaml_to_xml.py src/org/nyet/ecuxplot/loggers.yaml build/loggers.xml
```

### Makefile Integration

```makefile
convert-loggers:
        python scripts/yaml_to_xml.py src/org/nyet/ecuxplot/loggers.yaml build/loggers.xml

compile: convert-loggers
        # Compile Java code
```

## Adding New Logger Types

### 1. Add YAML Definition

```yaml
NEW_LOGGER:
  comment_signatures:
    - regex: ".*New Logger.*"
  field_signatures:
    - regex: "^UniqueField$"
  skip_lines: 2
  aliases:
    - ["^OriginalField$", "StandardField"]
```

### 2. Add Java Constant

```java
public static final String LOG_NEW_LOGGER = "NEW_LOGGER";
```

### 3. Add Case Statement (if needed)

```java
case "NEW_LOGGER": {
    // Logger-specific parsing logic
    break;
}
```

### 4. Add Test Data

- Create test file in `test-data/new-logger.csv`
- Add expectations to `test-data/test-expectations.xml`

## Current Status

### ✅ Implemented Features

- YAML-driven logger detection
- Dynamic line skipping with regex patterns
- Multi-header format support
- Field aliasing system
- Build integration with YAML-to-XML conversion
- Comprehensive test suite

### ✅ Supported Logger Types

- **VCDS**: German and English variants with complex header processing
- **ME7LOGGER**: Dynamic header finding with VARS/UNITS/ALIASES
- **JB4**: Fixed line skipping with field aliases
- **COBB_AP**: Single header with unit extraction from field names
- **ECUX**: Basic single header format
- **EVOSCAN**: Simple field signature detection
- **LOGWORKS**: Multi-header with unit extraction from parentheses
- **VOLVOLOGGER**: Regex unit extraction with field transformations
- **ZEITRONIX**: Field prefixing for overlay compatibility
- **SWCOMM**: Multi-header with complex field processing

### 🔄 Future Enhancements

- Complete migration of complex case statements to YAML
- Position-aware parsing (know exact detection line)
- More sophisticated field transformation patterns
- Unit extraction patterns in YAML

## Key Design Principles

- **YAML-Driven**: All configuration in YAML, minimal hardcoded logic
- **All Fields Optional**: Every field has sensible defaults
- **Hybrid Approach**: YAML for simple cases, case statements for complex logic
- **Backward Compatible**: Existing functionality preserved
- **Test-Driven**: All changes validated against comprehensive test suite
- **Build Integration**: Automatic YAML-to-XML conversion during build
