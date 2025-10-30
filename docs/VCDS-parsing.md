# VCDS Parsing

## Overview

VCDS log files can have multiple data groups (e.g., Group 115, Group 118) with duplicate field names across groups and within the same group. This document describes the implementation of group-based disambiguation that uses group IDs to make duplicate field names unique.

**Key Behavior**: The first global occurrence of a field name stays unchanged. Subsequent occurrences in different groups get a group suffix `[GroupID]`. Duplicates within the same group are numbered first (e.g., "Field1", "Field2"), then group suffixes are added for cross-group duplicates.

### Example from `vcds-115-118.csv`

**Line 4 (Group line)**:

```csv
,Group A:,'115,,,,Group B:,'118,,,,Group C:, Not Running
```

**Line 5 (Field names)**:

```csv
,,RPM,Load,Absolute Pres.,Absolute Pres.,,RPM,Temperature,Load,Absolute Pres.,,,,,
```

**Current behavior**: Columns would be named:

- Column 2: "RPM"
- Column 7: "RPM 2"
- Column 3: "Load"
- Column 9: "Load 2"
- Column 4: "Absolute Pres."
- Column 5: "Absolute Pres. 2"
- Column 10: "Absolute Pres. 3"

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

## File Structure Analysis

### vcds-115-118.csv Structure

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

### Group Mapping Strategy

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

## Implementation

### Group ID Extraction

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

### Disambiguation Algorithm

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

## Example Output

### vcds-115-118.csv Processing Steps

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

## Edge Cases

### Edge Cases Handled

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

## Backward Compatibility

- Non-VCDS loggers: No change in behavior (numeric suffixes)
- VCDS files without group IDs: Falls back to numeric suffixes
- Existing VCDS files: Enhanced naming but maintains functionality

## Current Implementation Status

✅ **Implemented**:

- Group ID extraction for VCDS and VCDS_LEGACY formats
- Same-group duplicate numbering ("Field1", "Field2")
- Cross-group duplicate disambiguation with group suffixes ("Field [115]")
- Post-disambiguation aliasing with group ID matching
- Look-ahead group assignment for VCDS format
- Support for inactive groups ("Not Running")

## Future Enhancements

- **YAML Configuration**: Make group disambiguation configurable per logger type
- **Group Filtering**: Allow filtering by group ID in UI
- **Group Metadata**: Store group information in DatasetId for UI display
- **Pattern Expansion**: Support additional VCDS group formats beyond "Group A:" and "Group 23"
