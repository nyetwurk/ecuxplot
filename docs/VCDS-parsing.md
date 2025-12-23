# VCDS Parsing: Current Implementation State

## Overview

ECUxPlot supports two VCDS logger types:
- **VCDS_LEGACY**: Older format with alphabetic group markers and line continuation
- **VCDS**: Newer format with numeric group markers, no line continuation

Both formats use the same header processing logic in `VCDSHeaderProcessor.java` but have different configurations in `loggers.yaml`.

## Format Variations

### VCDS_LEGACY Format

**Structure:**
- Groups line: Alphabetic markers (`Group A:`, `Block A:`) followed by quoted group IDs (`'002`)
- Field names: Split across two lines (line continuation)
- Header format: `"g,id,id2,u"`

**Example (vcds-002-031.csv):**
```csv
Line 4: ,Group A:,'002,,,,Group B:,'031,,,,Group C:, Not Running
Line 5: ,,RPM,Load,Inj. On Time,Mass Flow,,Voltage,Voltage,Voltage,Voltage,,,,,
Line 6: ,TIME,,,,,TIME,,,,,TIME,,,,
Line 7: Marker,STAMP, /min, %, ms, g/s,STAMP, V, V, V, V,STAMP,,,,
```

**Configuration (`loggers.yaml`):**
- `field_signatures`: `".*(Group|Block) [A-Z]:.*"` (alphabetic markers)
- `skip_regex`:
  - `".*(Group|Block) [A-Z]:.*"` (group markers)
  - `"G[0-9]+"` (G### format groups, e.g., G002, G003)
- `header_format`: `"g,id,id2,u"` (groups, first part of field names, second part, units)

**Group Extraction:**
- Pattern: `^(?:(?:Group|Block)\s+([A-Z0-9]+)|G([0-9]+))`
- For `Group A:` format: Extract marker, get group ID from next column (quoted number pattern `'?([0-9]+)`)
- For `G###` format: Extract group ID directly (e.g., `G002` → `002`)

### VCDS Format

**Structure:**
- Groups line: Numeric groups (`Group 23`) or G### format (`G002, G003`) or location identifiers (`Loc. IDE00021`)
- Field names: Single line (no continuation)
- Header format: `"g,id,u"`

#### Variation 1: Standard Numeric Groups (vcds-115-118.csv)
```csv
Line 4: ,Group A:,'115,,,,Group B:,'118,,,,
Line 5: ,,RPM,Load,Absolute Pres.,Absolute Pres.,,RPM,Temperature,Load,Absolute Pres.,,,,,
```

#### Variation 2: G### Format with Location Identifiers (vcds-IDE-german.csv)
```csv
Line 3: ,,G004,F0,G026,F0,G027,F0,G035,F0,G204,F0,G206,F0,G207,F0,G208,F0,...
Line 5: Markierung,Zeit,Loc. IDE00021,Zeit,Loc. IDE00190,Zeit,Loc. IDE00191,...
Line 6: ,MARKE,Motordrehzahl,MARKE,Ladedruck: Sollwert,MARKE,Ladedruck: Istwert,...
Line 7: ,, /min,, hPa,, hPa,, g/s,...
```

#### Variation 3: G### Format with Placeholder Lines (vcds-01-02D-03C-114F.csv)
```csv
Line 3: ,,G002,F0,G002,F2,G002,F3,G003,F2,G003,F3,G114,F0,G114,F1,G114,F2,G114,F3,...
Line 5: Marker,TIME,Group 2 - Field 0,TIME,Group 2 - Field 2,TIME,Group 2 - Field 3,...
Line 6: ,STAMP,Engine - Speed,STAMP,Fuel Injectors - Average Time,STAMP,Air - Mass Flow,...
Line 7: ,, /min,, ms,, g/s,,°,,°BTDC,, ms,, ms,, ms,, %,...
```

**Configuration (`loggers.yaml`):**
- `field_signatures`:
  - `".*Group \\d+.*"` (numeric groups)
  - `".*Loc\\. IDE\\d+.*"` (location identifiers)
- `skip_regex`:
  - `".*Group \\d+ - Field.*"` (placeholder lines like "Group 2 - Field 0")
  - `".*Loc\\. IDE\\d+.*"` (location identifier lines)
- `header_format`: `"g,id,u"` (groups, field names, units)

**Group Extraction:**
- Pattern: `^(?:(?:Group|Block)\s+([A-Z0-9]+)|G([0-9]+))`
- For `Group 23` format: Extract group ID directly, use look-ahead (marker at position i means column i-1 gets the group)
- For `G###` format: Extract group ID directly (e.g., `G002` → `002`)

## Configuration Details

### Field Signatures

Used to detect logger type from file content:

**VCDS_LEGACY:**
- `".*(Group|Block) [A-Z]:.*"` - Matches alphabetic group markers

**VCDS:**
- `".*Group \\d+.*"` - Matches numeric groups (e.g., "Group 23")
- `".*Loc\\. IDE\\d+.*"` - Matches location identifiers (German IDE format)

### Skip Regex

Used in `processSkipLines()` to find the first header line (`matchedLine`). The first matching line becomes the groups line.

**VCDS_LEGACY:**
- `".*(Group|Block) [A-Z]:.*"` - Matches group marker lines
- `"G[0-9]+"` - Matches G### format groups (must come first to match groups line before placeholder lines)

**VCDS:**
- `".*Group \\d+ - Field.*"` - Matches placeholder lines (should be skipped, not used as matchedLine)
- `".*Loc\\. IDE\\d+.*"` - Matches location identifier lines (should be skipped)

**Note:** `skip_regex` patterns are also checked during `parseHeaderFormat()` when reading the `id` token to skip lines matching these patterns.

### Header Format Tokens

**VCDS_LEGACY: `"g,id,id2,u"`**
- `g`: Groups line (line 4)
- `id`: First part of field names (line 5) - prefix/continuation part
- `id2`: Second part of field names (line 6) - main part (combined with `id`)
- `u`: Units line (line 7)

Line continuation: `id` and `id2` are combined into complete field names before aliasing.

**VCDS: `"g,id,u"`**
- `g`: Groups line (line 3 or 4)
- `id`: Field names line (line 5 or 6, after skipping placeholder/location lines)
- `u`: Units line (line 7)

## Header Processing Flow

### 1. Skip Lines (`processSkipLines()`)

- Reads lines from CSV, skipping empty lines
- Checks each line against `skip_regex` patterns
- First matching line becomes `matchedLine` (used as groups line)
- Stops when match found

### 2. Parse Header Format (`parseHeaderFormat()`)

- Reads header lines sequentially based on `header_format` tokens
- `matchedLine` from step 1 becomes `headerLines[0]` (groups line)
- For each token in format:
  - `g`: Uses `matchedLine` as groups
  - `id`: Reads next line (skips if matches `skip_regex`)
  - `id2`: Reads next line (for VCDS_LEGACY line continuation)
  - `u`: Reads next line for units
- Handles missing `id2` token gracefully (adjusts line index for units)

### 3. Line Continuation (VCDS_LEGACY only)

- Combines `id` (first part) + `id2` (second part) → complete field name in `id`
- Example: "Engine" (id) + "Speed" (id2) → "Engine Speed" (id)
- Happens before aliasing, so aliases match complete field names

### 4. Apply Aliases

- Field names converted to canonical names using patterns in `loggers.yaml`
- Original field names preserved in `id_orig`
- Aliases shared between VCDS and VCDS_LEGACY via `aliases_from: "VCDS"`

### 5. VCDS Header Processing (`VCDSHeaderProcessor.processVCDSHeader()`)

Runs after aliasing, handles VCDS-specific logic:

**STAMP→TIME Conversion:**
- If field name is empty and unit is "STAMP" or "MARKE", set field name to "TIME"

**Duplicate Field Name Disambiguation:**

**Pass 1:** Build `ColumnInfo` array with field names and group IDs

**Pass 2:** Compute duplicate information:
- `hasIdenticalInSameGroup()`: Multiple fields with same name in same group
- `appearsInOtherGroups()`: Field appears in multiple groups
- `isFirstGlobally()`: First occurrence of field name (by column index)

#### Pass 3a: Same-group duplicates
- Number duplicates within same group: "Field1", "Field2", etc.
- Exception: First global TIME occurrence stays as "TIME" (not numbered)
- Subsequent TIME fields in same group: "TIME2", "TIME3", etc.
- Other duplicate fields: All numbered starting from 1

#### Pass 3.5: Post-disambiguation aliases
- Apply aliases after numbering (allows matching numbered fields like "Voltage1")

#### Pass 3b: Cross-group duplicates
- First global occurrence: Keep unchanged
- Subsequent occurrences: Add group suffix (e.g., "Field [118]")
- TIME fields: Blacklist excess TIME columns (set to empty string)

### 6. Unit Processing

- Units normalized via `Units.normalize()` and `Units.find()`
- Handles corrupted degree symbols (U+FFFD, etc.) → normalized to "°"
- Example: Corrupted `°BTDC` → normalized to "°"

### 7. Ensure Unique Field Names

- Final pass to ensure all column names are unique
- Adds numeric suffixes if needed (after all other processing)

## Known Issues

### Multiple Header Sections Mid-File

**Status:** Known bug (not yet fixed)

VCDS can append new header sections in the middle of data files. Current parser only processes headers once at the beginning.

**Impact:** Files with multiple header sections may fail to parse correctly.

**Current Workaround:** None. The generic hook `Dataset.shouldSkipDataLine()` exists but VCDS-specific logic is not yet implemented.

## File Examples

### vcds-002-031.csv (VCDS_LEGACY)
- Format: Alphabetic groups (`Group A:`, `Group B:`)
- Line continuation: Yes
- Groups: 002, 031, Not Running
- TIME appears in different groups → stays as "TIME" (first global), others get group suffixes or blacklisted

### vcds-01-02D-03C-114F.csv (VCDS)
- Format: G### groups (`G002`, `G003`, `G114`)
- Line continuation: No
- Placeholder line (line 5): "Group 2 - Field 0" → skipped via skip_regex
- TIME appears multiple times in same group → First stays as "TIME", others become "TIME2", "TIME3", etc.

### vcds-IDE-german.csv (VCDS)
- Format: G### groups (`G004`, `G026`, etc.)
- Line continuation: No
- Location identifiers (line 5): "Loc. IDE00021" → skipped via skip_regex
- German field names and units

## Implementation Files

- **Configuration:** `src/org/nyet/ecuxplot/loggers.yaml`
- **Header Parsing:** `src/org/nyet/ecuxplot/DataLogger.java`
- **VCDS Processing:** `src/org/nyet/ecuxplot/VCDSHeaderProcessor.java`
- **Unit Normalization:** `src/org/nyet/ecuxplot/Units.java`
