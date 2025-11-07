# ECUxPlot Log Detection and Parsing System

## Overview

ECUxPlot uses a YAML-driven configuration system for detecting and parsing various automotive logger formats. The system automatically detects logger types based on comment signatures and field patterns, then applies appropriate parsing logic for each format.

## Architecture

### Core Components

- **`loggers.yaml`**: YAML configuration defining detection patterns and parsing parameters
- **`loggers.xml`**: Generated XML file (converted from YAML during build)
- **`DataLogger.java`**: Loads configuration and provides detection/parsing utilities
- **`ECUxDataset.java`**: Uses DataLogger configuration to process headers (no logger-specific code). See [System Architecture](system-architecture.md) for details on ECUxDataset structure and filtering.
- **`VCDSHeaderProcessor.java`**: Handles VCDS-specific header processing via registered header processor
- **`scripts/yaml_to_xml.py`**: Converts YAML to XML during build process

### Detection Process

The system uses a two-phase detection mechanism that scans comment lines first, then CSV header fields.

**Process Flow** (See `ECUxDataset.detectLoggerType()`):

1. Check if logger type is already detected (skip if already known)
2. **Phase 1**: Scan comment lines for comment signatures (raw text, not CSV)
   - Location: `ECUxDataset.detectLoggerType()` calls `DataLogger.detectComment(String comment)`
   - Checks each comment line against regex patterns in `comment_signatures`
   - Returns first logger name that matches
3. **Phase 2**: Scan CSV header fields for field signatures (fallback if Phase 1 fails)
   - Location: `ECUxDataset.detectLoggerType()` calls `DataLogger.detectField(String[] fields)`
   - Checks each field against regex patterns in `field_signatures`
   - Supports any-column or specific-column matching via `column_index`
4. Default to UNKNOWN if no match found

## YAML Configuration Structure

### Filter Associations (Shared)

Filter associations define canonical field names for common filter categories. These are single canonical field names (not arrays) that logger-specific field variations are aliased to via `loggers.yaml`.

```yaml
filter_associations:
  pedal: "AccelPedalPosition"
  throttle: "ThrottlePlateAngle"
  gear: "Gear"
```

**Usage**: The Filter uses these via `DataLogger.pedalField()`, `DataLogger.throttleField()`, and `DataLogger.gearField()` to ensure consistent column access across all log formats.

### Global Required Columns (Shared)

Global required columns define fundamental columns that should exist in all log formats. These columns are used across multiple presets and calculations.

```yaml
global_required_columns:
  - "RPM"
  - "TIME"
```

**Usage**: Accessible via `DataLogger.getGlobalRequiredColumns()`. Unit tests verify these columns exist in each log format.

### Axis Preset Categories (Shared)

Axis preset categories define reusable groups of columns for preset testing. Categories prevent duplication when defining preset expectations.

```yaml
axis_preset_categories:
  boost_both:
    - "BoostPressureDesired (PSI)"
    - "BoostPressureActual (PSI)"
  boost_actual:
    - "BoostPressureActual (PSI)"
  maf:
    - "MassAirFlow"
  timing_all:
    - "EngineLoad"
    - "IgnitionTimingAngleOverall"
    - "IgnitionTimingAngleOverallDesired"
  compressor_map_base:
    - "BaroPressure"
```

**Usage**: Accessible via `DataLogger.getAxisPresetCategory(categoryName)`. Categories are referenced by preset support profiles to build reusable preset expectation patterns.

### Preset Defaults (Shared)

Preset defaults define canonical column names for default axis presets. These ensure consistency between default axis presets and canonical column names across all log formats.

```yaml
preset_defaults:
  Power:
    xkey: "RPM"
    ykeys0: ["WHP", "WTQ", "HP", "TQ"]
    ykeys1: ["BoostPressureDesired (PSI)", "BoostPressureActual (PSI)"]
  Timing:
    xkey: "RPM"
    ykeys0: ["EngineLoad"]
    ykeys1: ["IgnitionTimingAngleOverall", "IgnitionTimingAngleOverallDesired"]
    scatter: true
  Fueling:
    xkey: "RPM"
    ykeys0: ["Zeitronix AFR", "Sim AFR"]
    ykeys1: ["Zeitronix Boost (PSI)", "BoostPressureDesired (PSI)", "BoostPressureActual (PSI)"]
```

**Preset Default Fields**:
- **`xkey`** (string, required): Canonical column name for the X-axis
- **`ykeys0`** (array, optional): Array of canonical column names for the first Y-axis
- **`ykeys1`** (array, optional): Array of canonical column names for the second Y-axis
- **`scatter`** (boolean, optional): Whether this preset should use scatter plot mode (default: `false`)

**Separation of Concerns**:
- **Preset defaults** (in `loggers.yaml`): Define which canonical columns each preset uses
- **Test expectations** (in `test-data/test-expectations.xml`): Define which preset columns are available in each log format via `<expected_preset_columns>` sections

**Usage**: Default presets are created by `ECUxPreset.createDefaultECUxPresets()` using these canonical names via `DataLogger.getPresetDefault()` and `DataLogger.getPresetDefaultNames()`. Unit tests verify that preset columns exist in each log format based on the expectations in `test-expectations.xml`, keeping preset configuration separate from format-specific test expectations.

### Preset Support Profiles (Shared)

Preset support profiles define reusable preset column expectations that can be referenced in test expectations. Profiles group common patterns to reduce duplication.

```yaml
preset_support_profiles:
  full_timing:
    Timing:
      - category: timing_all
  partial_timing:
    Timing:
      - column: IgnitionTimingAngleOverall
  full_fueling:
    Fueling:
      - category: boost_both
      - category: maf
      - column: EffInjectorDutyCycle
      - column: AirFuelRatioActual
  boost_presets:
    Power:
      - category: boost_both
    "Spool Rate":
      - category: boost_actual
```

**Profile Items**:
- **`category: "category_name"`**: References an `axis_preset_categories` entry (expands to multiple columns)
- **`column: "ColumnName"`**: Direct column reference

**Usage**: Profiles are referenced in `test-expectations.xml` via `<profile_ref name="profile_name"/>`. The test framework expands profiles using `DataLogger.expandProfilePreset()`. This approach reduces duplication when many loggers share similar preset support patterns.

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

- `g`: Group line (used by VCDS/VCDS_LEGACY for group markers)
- `id`: Field names (original field names from CSV)
- `u`: Units (units for each field)
- `id2`: Secondary field information (varies by logger type)
- `u2`: Secondary units line (used by VCDS_LEGACY)

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

Aliases map logger-specific field names to standardized canonical names. The system uses a multi-tier approach:

**Processing Order** (Location: `DataLogger.HeaderData.processAliases()`):

1. **ME7_ALIASES** - Fast O(1) hash map lookup for ME7L variable names (201 aliases)
   - Exact string matches only
   - Example: `"dwkrz_0"` → `"IgnitionRetardCyl$1"` (note: `$1` is literal, not a placeholder)
2. **Logger-Specific Aliases** - Regex patterns specific to detected logger type
3. **DEFAULT Aliases** - Common aliases applied to all loggers

**Note**: Aliases are applied after unit regex extraction but before logger-specific header processing (e.g., VCDS group handling).

**Configuration**:

```yaml
ME7_ALIASES:
  aliases:
    - ["dwkrz_0", "IgnitionRetardCyl1"]
    - ["nmot_w", "RPM"]
    - ["wped_w", "AccelPedalPosition"]

JB4:
  aliases:
    - ["timestamp", "TIME"]
    - ["rpm", "RPM"]

DEFAULT:
  aliases:
    - ["[Tt]ime", "TIME"]
    - ["[Ee]ngine [Ss]peed", "RPM"]
```

**Unique Column Handling**: The alias process ensures all columns have unique names by appending a number suffix if duplicates are detected.

## Unit Determination

Unit determination uses a multi-step fallback strategy to extract and normalize units from log files.

### Unit Processing Order

**Location**: `DataLogger.processHeaders()` - executed in this order:

1. **Parse Header Format** - Extract header lines based on `header_format` tokens (`g`, `id`, `u`, `u2`, `id2`)
   - Units from `header_format` tokens (`u`, `u2`) are populated into the `u` array during parsing
   - This happens before unit processing steps below
2. **Unit Regex Extraction** (`unit_regex`) - Extract units from complex field formats BEFORE aliasing
   - Extracts field names, units, and ME7L variables from complex formats
   - Example: `"Boost Pressure(mBar) BoostPressure"` → `id: "Boost Pressure"`, `u: "mBar"`, `id2: "BoostPressure"`
   - Used by VOLVOLOGGER
3. **Apply Aliases** - Generate canonical field names
   - ME7_ALIASES → Logger-specific aliases → DEFAULT aliases
4. **Logger-Specific Processing** - Custom logic via registered header processors (e.g., VCDS group handling via `VCDSHeaderProcessor.java`)
   - Runs after aliasing but before general unit parsing
   - For VCDS/VCDS_LEGACY: Handles group disambiguation, STAMP→TIME conversion
   - Only loggers with registered header processors use this step (most loggers skip it)
5. **General Unit Parsing** - Extract units from field names in format `"FieldName (unit)"`
   - Only triggered when logger doesn't have `header_format` with `u` token
   - If logger has `u` token, units were already extracted during header format parsing
6. **Field Transformations** - Apply prepend/append
   - Applied after general unit parsing so transformed field names can be used for unit inference
7. **Unit Normalization and Inference** - Process and normalize units (`Units.normalize()` and `Units.find()`)
   - Normalizes unit strings: `"(sec)"` → `"sec"`, `"rpm"` → `"RPM"`, `"degC"` → `"°C"`, `"hPa"` → `"mBar"`
   - Infers units from field names when normalization returns empty
8. **Ensure Unique Names** - Make columns unique LAST (after all processing complete)

**Implementation**: See `DataLogger.java` and `Units.java` for full implementation details.

### Unit Validation

All extracted units are validated globally to prevent descriptive text from being incorrectly treated as units.

**Validation Process**:

1. **Initial Validation** - Units extracted via `unit_regex` are validated immediately after extraction
2. **Unit Extraction Validation** - Units extracted from parentheses in field names (e.g., `"FieldName (unit)"`) are validated before being applied
3. **Validation Criteria**:
   - Uses `Units.normalize()` to check if the unit is recognized
   - Rejects units that normalize to empty (invalid units)
   - Rejects units that contain spaces or are longer than 15 characters (descriptive text)
   - Accepts short, recognized units (e.g., `"V"`, `"PSI"`, `"AFR"`, `"λ"`)

**Invalid Unit Handling**:

- Invalid units are cleared (set to `null`) and passed to `Units.find()` for inference
- This allows the system to infer correct units from field names (e.g., lambda fields → `"λ"`)

**Example**:

```text
Field: "O2 sensor lambda wide range (current probe)  (Bank 1  Sensor 1)"
unit_regex extracts: unit = "Bank 1  Sensor 1"
Validation: "Bank 1  Sensor 1" contains spaces → invalid → cleared
processUnits() infers: "lambda" field name → unit = "λ"
Result: Correct unit "λ" instead of descriptive text
```

## Logger-Specific Parsing

### Current Implementation Strategy

The system is fully YAML-driven with registered header processors for special cases:

- **YAML Configuration**: Handles detection, parsing parameters, field aliases, and transformations
- **Registered Header Processors**: VCDS/VCDS_LEGACY use `VCDSHeaderProcessor` registered via `registerHeaderProcessor()` (not case statements)

### Remaining Logger-Specific Code

#### VCDS (Complex Multi-Header Processing)

VCDS header processing is handled by `VCDSHeaderProcessor.processVCDSHeader()` which is registered as a header processor (not a case statement):

**Why VCDS Still Needs Special Handling**:

- **Complex Group/Block Logic**: VCDS files have Group lines that affect field processing
- **TIME Field Detection**: Special logic for detecting TIME fields from field names or units
- **Group 24 Blacklist**: Specific business logic for Group 24 accelerator position fields
- **Header Format Variations**: VCDS files have inconsistent header structures that require special handling

#### VCDS Deep Dive: Group Disambiguation

VCDS log files can have multiple data groups (e.g., Group 115, Group 118) with duplicate field names across groups and within the same group. This section describes the implementation of group-based disambiguation that uses group IDs to make duplicate field names unique.

**Key Behavior**: The first global occurrence of a field name stays unchanged. Subsequent occurrences in different groups get a group suffix `[GroupID]`. Duplicates within the same group are numbered first (e.g., "Field1", "Field2"), then group suffixes are added for cross-group duplicates.

##### Example from `vcds-115-118.csv`

**Line 4 (Group line)**:

```csv
,Group A:,'115,,,,Group B:,'118,,,,Group C:, Not Running
```

**Line 5 (Field names)**:

```csv
,,RPM,Load,Absolute Pres.,Absolute Pres.,,RPM,Temperature,Load,Absolute Pres.,,,,,
```

**Actual behavior** (after aliasing): Columns are named:

- Column 1: `TIME` (first group, first occurrence globally)
- Column 2: `RPM` (first group, first occurrence globally)
- Column 3: `EngineLoad` (aliased from `Load`, first group, first occurrence globally)
- Column 4: `BoostPressureDesired` (aliased from `Absolute Pres.1`, group 115)
- Column 5: `BoostPressureActual` (aliased from `Absolute Pres.2`, group 115)
- Column 6: `TIME [118]` (not first occurrence, gets group suffix)
- Column 7: `RPM [118]` (not first occurrence, gets group suffix)
- Column 8: `IntakeAirTemperature` (aliased from `Temperature`, unique globally, no suffix)
- Column 9: `WastegateDutyCycle` (aliased from `Load`, unique globally after aliasing, no suffix)
- Column 10: `BoostPressureBeforeThrottle` (aliased from `Absolute Pres.`, unique globally after aliasing, no suffix)
- Column 11: `TIME [Not Running]` (inactive group, still gets suffix)

**Notes**:

- Same-group duplicates are numbered first (e.g., "Absolute Pres." → "Absolute Pres.1", "Absolute Pres.2")
- Group suffixes use brackets `[115]` not parentheses, and use the group ID number directly (not "G115")
- Group suffixes are only added to fields that appear in multiple groups AND are not the first global occurrence
- Post-disambiguation aliasing is applied after same-group numbering but before group suffix addition

##### File Structure Analysis

**Header Format**: `header_format: "g,id,u,u2"` for VCDS_LEGACY (uses `g` token for group line)

1. **Line 4 (g - Group line)**: `,Group A:,'115,,,,Group B:,'118,,,,Group C:, Not Running`
   - Column 0: `Group A:` (group marker)
   - Column 1: `'115` (group ID extracted from here for VCDS_LEGACY)
   - Columns 2-5: empty (part of Group 115, fields follow)
   - Column 6: `Group B:` (group marker)
   - Column 7: `'118` (group ID)
   - Columns 8-10: empty (part of Group 118, fields follow)
   - Column 12: `Group C:` (group marker)
   - Column 13: `Not Running` (Group C inactive)

2. **Line 5 (id - Field names)**: `,,RPM,Load,Absolute Pres.,Absolute Pres.,,RPM,Temperature,Load,Absolute Pres.,,,,`
   - Columns 1-5: Fields for Group 115 (TIME, RPM, Load, Absolute Pres., Absolute Pres.)
   - Columns 7-10: Fields for Group 118 (TIME, RPM, Temperature, Load, Absolute Pres.)

3. **Line 6 (u - Units line 1)**: `,TIME,,,,,TIME,,,,,TIME,,,,`
   - TIME fields (empty fields become "TIME" via STAMP→TIME hack when unit is STAMP)

4. **Line 7 (u2 - Units line 2)**: `Marker,STAMP, /min, %, mbar, mbar,STAMP, /min,C, %, mbar,STAMP,,,,`
   - Real units line (STAMP/MARKE triggers TIME field name hack)

##### Group Mapping Strategy

For **VCDS_LEGACY** format (which `vcds-115-118.csv` uses):

- Group marker at column `i` in `g` array
- Group ID is extracted from column `i+1` (next column after marker)
- The group ID is stored in `markerPositions.put(i, groupId)` - marker column position gets the group ID
- In Phase 2, all columns starting from the marker column get assigned this group ID until the next marker

For **VCDS** format (non-legacy):

- Uses look-ahead logic: group marker at column `i` means column `i-1` gets the group ID
- This is handled by `markerPositions.put(i-1, groupId)` in Phase 1
- All columns starting from `i-1` get assigned this group ID until the next marker

**Group 115 (vcds-115-118.csv - VCDS_LEGACY)**:

- Group marker: Column 0 (`Group A:`) in `g` array
- Group ID: Extracted from Column 1 (`'115`)
- Assignment: `markerPositions.put(0, "115")` - marker column position gets the group
- Field columns in `id` array: Columns 1-5 (TIME, RPM, Load, Absolute Pres., Absolute Pres.)
- All columns from 0 onwards get group "115" until next marker

**Group 118**:

- Group marker: Column 6 (`Group B:`) in `g` array
- Group ID: Extracted from Column 7 (`'118`)
- Assignment: `markerPositions.put(6, "118")`
- Field columns in `id` array: Columns 7-10 (TIME, RPM, Temperature, Load, Absolute Pres.)
- All columns from 6 onwards get group "118" until next marker

**Group C**:

- Group marker: Column 12 (`Group C:`) in `g` array
- Group ID: `Not Running`
- Assignment: `markerPositions.put(12, "Not Running")`
- Field columns: Column 13 only (inactive group)

##### Implementation

###### Group ID Extraction

**Location**: `VCDSHeaderProcessor.extractGroupIdMap()` called from `processVCDSHeader()`

**Algorithm**:

1. Scan `g` array (group line) for group markers
2. For **VCDS_LEGACY**: Group marker at column `i` means column `i` gets the group ID from column `i+1`
3. For **VCDS**: Group marker at column `i` means column `i-1` (look-ahead) gets the group ID
4. Build a mapping of column indices to group ID strings (e.g., "115", "118", "Not Running")

**Pattern Detection**:

- Group markers: `Group [A-Z]:` (VCDS_LEGACY) or `Group (\d+)` (VCDS)
- Group ID pattern: Quoted numbers like `'115` or `'118`, or numeric patterns for VCDS format
- Inactive groups: "Not Running" or "Läuft nicht" (German)

**Implementation**: See `VCDSHeaderProcessor.java` - the `extractGroupIdMap()` method handles both VCDS and VCDS_LEGACY formats with different group assignment logic.

###### Disambiguation Algorithm

**Location**: `VCDSHeaderProcessor.processVCDSHeader()` - runs after initial aliasing but before final unit processing

**Multi-Pass Algorithm**:

1. **Pass 1**: Build `ColumnInfo` array with field names and group IDs for all columns
2. **Pass 2**: Compute duplicate information (debug logging only)
3. **Pass 3a**: Handle same-group duplicates - add numbers directly (no space): "Field1", "Field2", etc.
4. **Pass 3.5**: Apply post-disambiguation aliasing rules (regex-based with group ID matching)
5. **Pass 3b**: Handle cross-group duplicates - add group suffix `[GroupID]` to non-first occurrences

**Disambiguation Rules**:

- **RULE 1 (Same Group)**: All duplicates in the same group get numbered: "Absolute Pres." → "Absolute Pres.1", "Absolute Pres.2"
- **RULE 2 (Across Groups)**: First global occurrence stays unchanged; subsequent occurrences get group suffix: "TIME" → "TIME [118]"
- **RULE 3**: Fields unique globally (even in different groups) don't get group suffixes after aliasing

**Format**:

- Group suffixes use brackets: `[115]` not parentheses `(G115)`
- Group ID is the numeric value directly: `[115]` not `[G115]`
- Same-group numbering has no space: `"Field1"` not `"Field 1"`

###### Example Output

###### vcds-115-118.csv Processing Steps

**Initial field names** (from header):

- Column 1: empty → becomes `TIME` (STAMP→TIME hack)
- Column 2: `RPM`
- Column 3: `Load`
- Column 4: `Absolute Pres.`
- Column 5: `Absolute Pres.`
- Column 6: empty → becomes `TIME` (STAMP→TIME hack)
- Column 7: `RPM`
- Column 8: `Temperature`
- Column 9: `Load`
- Column 10: `Absolute Pres.`
- Column 11: empty → becomes `TIME` (STAMP→TIME hack)

**After Pass 3a (Same-group numbering)**:

- Column 4: `Absolute Pres.` → `Absolute Pres.1`
- Column 5: `Absolute Pres.` → `Absolute Pres.2`
- (Other fields unchanged)

**After Pass 3.5 (Post-disambiguation aliasing)**:

- Column 3: `Load` → `EngineLoad`
- Column 4: `Absolute Pres.1` → `BoostPressureDesired`
- Column 5: `Absolute Pres.2` → `BoostPressureActual`
- Column 8: `Temperature` → `IntakeAirTemperature`
- Column 9: `Load` → `WastegateDutyCycle` (via group-specific alias)
- Column 10: `Absolute Pres.` → `BoostPressureBeforeThrottle`

**After Pass 3b (Cross-group suffixes)**:

- Column 6: `TIME` → `TIME [118]` (not first occurrence)
- Column 7: `RPM` → `RPM [118]` (not first occurrence)
- Column 11: `TIME` → `TIME [Not Running]` (inactive group)

**Final output**:

- Column 1: `TIME` (first occurrence, group 115)
- Column 2: `RPM` (first occurrence, group 115)
- Column 3: `EngineLoad` (unique globally after aliasing)
- Column 4: `BoostPressureDesired` (aliased, group 115)
- Column 5: `BoostPressureActual` (aliased, group 115)
- Column 6: `TIME [118]` (duplicate, gets group suffix)
- Column 7: `RPM [118]` (duplicate, gets group suffix)
- Column 8: `IntakeAirTemperature` (unique globally, no suffix)
- Column 9: `WastegateDutyCycle` (unique globally after aliasing, no suffix)
- Column 10: `BoostPressureBeforeThrottle` (unique globally after aliasing, no suffix)
- Column 11: `TIME [Not Running]` (inactive group, still gets suffix)

###### Edge Cases Handled

#### Case 1: Empty/Null Fields

- Fields with null or empty field names are skipped (no disambiguation)
- Fields without group IDs are skipped

#### Case 2: VCDS vs VCDS_LEGACY Format

- **VCDS_LEGACY**: Uses "Group A:" markers, group ID in next column
- **VCDS**: Uses "Group 23" format, look-ahead logic assigns group to previous column

#### Case 3: Inactive Groups

- Groups with "Not Running" or "Läuft nicht" still get group IDs assigned
- Fields in inactive groups still get group suffixes if they duplicate other groups

#### Case 4: No Group IDs Found

- If no group markers are detected, the disambiguation algorithm still runs but no group suffixes are added
- Falls back to same-group numbering only (if duplicates exist within groups)

#### Case 5: Post-Disambiguation Aliasing

- Aliasing rules can match against numbered fields (e.g., "TIME2" → "WastegateOnTime")
- Group ID is explicitly checked in alias rules for group-specific aliases
- Aliasing happens after same-group numbering but before cross-group suffix addition

##### Backward Compatibility

- Non-VCDS loggers: No change in behavior (numeric suffixes)
- VCDS files without group IDs: Falls back to numeric suffixes
- Existing VCDS files: Enhanced naming but maintains functionality

###### Current Implementation Status

✅ **Implemented**:

- Group ID extraction for VCDS and VCDS_LEGACY formats
- Same-group duplicate numbering ("Field1", "Field2")
- Cross-group duplicate disambiguation with group suffixes ("Field [115]")
- Post-disambiguation aliasing with group ID matching
- Look-ahead group assignment for VCDS format
- Support for inactive groups ("Not Running")

###### Future Enhancements

- **YAML Configuration**: Make group disambiguation configurable per logger type
- **Group Filtering**: Allow filtering by group ID in UI
- **Group Metadata**: Store group information in DatasetId for UI display
- **Pattern Expansion**: Support additional VCDS group formats beyond "Group A:" and "Group 23"

### Migration Status

**✅ Fully Migrated to YAML**:

- ME7LOGGER: Dynamic header finding via `skip_regex`
- ZEITRONIX: Field prefixing via `field_transformations`
- JB4, COBB_AP, ECUX, EVOSCAN, LOGWORKS, VOLVOLOGGER, SWCOMM: All parsing handled by YAML configuration

**🔄 Partially Migrated**:

- **VCDS/VCDS_LEGACY**: Detection, aliases, and basic parsing in YAML; complex header processing (group disambiguation, STAMP→TIME conversion) handled by `VCDSHeaderProcessor.java` (separated from `DataLogger.java` in 1.1.4)

#### Other Logger-Specific Conditionals

While most parsing has moved to YAML, several logger-specific conditionals remain in the UI and calculation code:

**Zeitronix Field Processing** (`AxisMenu.java`, `ECUxDataset.java`): Handles field prefixing and unit conversions for Zeitronix fields.

**ME7LOGGER-Specific Calculations** (`AxisMenu.java`): Provides unique calculated fields not available in other loggers.

**JB4-Specific Field Handling** (`AxisMenu.java`): Handles boost pressure calculation for JB4 format.

**ME7L Field Processing** (`ECUxDataset.java`, `AxisMenu.java`): Processes ME7L-specific field references.

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

### Add YAML Definition

Add logger configuration to `loggers.yaml` with detection patterns, parsing parameters, and field aliases.

### Add Java Constant (if needed)

If the logger needs a Java constant for reference, add it to the appropriate class.

### Add Header Processor (if needed)

Only if the logger requires special processing that cannot be expressed in YAML, create a header processor class and register it via `DataLogger.registerHeaderProcessor()`.

### Add Test Data

- Create test file in `test-data/new-logger.csv`
- Add expectations to `test-data/test-expectations.xml`

## ✅ Supported Logger Types

- **VCDS**: German and English variants with complex header processing (uses `VCDSHeaderProcessor.java`)
- **VCDS_LEGACY**: Legacy VCDS format with "g" and "u2" header columns (uses `VCDSHeaderProcessor.java`)
- **ME7LOGGER**: Dynamic header finding with VARS/UNITS/ALIASES
- **JB4**: Fixed line skipping with field aliases
- **COBB_AP**: Single header with unit extraction from field names
- **ECUX**: Basic single header format
- **EVOSCAN**: Simple field signature detection
- **LOGWORKS**: Multi-header with unit extraction from parentheses
- **VOLVOLOGGER**: Regex unit extraction with field transformations
- **ZEITRONIX**: Field prefixing for overlay compatibility
- **SWCOMM**: Multi-header with complex field processing
- **SIMOSTOOLS**: SimosTools Android app format (extensive support added in 1.1.4)
- **WINLOG**: Winlog logger format (added in 1.1.4)

## 🔄 Future Enhancements

- Position-aware parsing (know exact detection line)
- More sophisticated field transformation patterns
- Unit extraction patterns in YAML

## Field Transformations

Field transformations allow prepending or appending text to field names with optional conditional logic.

### Processing Flow

**Location**: `DataLogger.applyFieldTransformations()` - applied after aliases, logger-specific processing, and general unit parsing, but before unit normalization/inference (`processUnits()`)

**Process Order**:

1. Check exclusion list
2. Check `if_empty` condition
3. Apply prepend/append

### Configuration

```yaml
field_transformations:
  prepend: "PREFIX "         # Text to prepend
  append: " SUFFIX"          # Text to append
  if_empty: true             # Optional: only transform empty fields
  exclude_fields:           # Optional: fields to skip
    - "FIELD1"
```

### Use Cases

- **ME7LOGGER**: Conditional prepend - `if_empty: true` prepends "ME7L " to empty fields using `id2` value
- **ZEITRONIX**: Prepend "Zeitronix " to all fields except RPM (excluded)
- **VCDS**: Group disambiguation handled in `VCDSHeaderProcessor.java`, not via field transformations

**Important**: Transformations happen BEFORE final unit processing so unit inference can work with transformed field names.

## Complete Processing Pipeline

```text
1. DETECTION
   ├── Comment signatures → Logger type
   └── Field signatures → Logger type (fallback)
        ↓
2. HEADER PARSING
   ├── Skip lines/regex → Find header lines
   ├── Parse header format → Extract id, u, id2, u2, g arrays
        ↓
3. FIELD PROCESSING
   ├── Unit regex → Extract units from complex formats FIRST
   ├── Aliases → Map to canonical names (ME7_ALIASES → Logger → DEFAULT)
   ├── Logger-specific processing → Custom logic (VCDS in VCDSHeaderProcessor.java)
   ├── General unit parsing → Extract from field names (if needed)
   ├── Field transformations → Prepend/append
   ├── Unit processing → Normalize → Infer
   └── Ensure unique names → Make columns unique LAST
        ↓
4. FINAL RESULT
   └── DatasetId[] → Ready for data loading
```

## Key Design Principles

- **YAML-Driven**: Most configuration in YAML, minimal hardcoded logic
- **Multi-Tier Fallbacks**: Units and aliases use multiple fallback strategies
- **Flexible Header Formats**: Universal `header_format` tokens handle any structure
- **Conditional Transformations**: Smart prepend/append with exclusion and empty-field logic
- **Performance Optimized**: ME7_ALIASES uses hash map for O(1) lookup
- **Backward Compatible**: Defaults ensure existing files continue to work
