# Unit Normalization System

## Overview

The unit normalization system ensures consistent unit display across multiple datasets. When multiple log files are loaded with the same canonical column name but different base units (e.g., `BoostPressureActual` in PSI vs kPa), the system automatically normalizes units to a standard based on global preference.

## Key Concepts

### DatasetId Fields

- **`DatasetId.unit`**: Normalized unit (preference-based, for menu/display)
- **`DatasetId.u2`**: Native unit (processed/normalized, but before preference conversion)
  - Note: `u2` is NOT the raw CSV unit - it's the processed unit after `Units.normalize()` and `Units.find()`, but before preference-based conversion
  - This is the "original unit intent" used by the unit conversion system

### Column Methods

- **`Column.getUnits()`**: Returns normalized unit (`DatasetId.unit`) for all columns
  - Used for display (charts, axis labels)
  - Always returns the normalized unit, even for CSV_NATIVE columns

- **`Column.getNativeUnits()`**: Returns native unit (`DatasetId.u2`) for CSV_NATIVE columns
  - Used by unit conversion logic
  - Returns normalized unit for calculated columns (fallback)

## Data Flow

### Load Time

1. CSV file loaded → units parsed via `Units.normalize()` and `Units.find()`
2. `normalizeUnitsToStandard()` determines standard unit based on preference
3. If standard unit differs from native:
   - Store native unit in `nativeUnits` map
   - Store normalized unit in `normalizedUnits` map
4. `DatasetId.unit` set to normalized unit (for menu)
5. `DatasetId.u2` set to native unit (for unit conversion)
6. Base CSV_NATIVE columns store data in **native units** (preserved)

### Runtime: Column Access

#### Base Column Request (`_get("VehicleSpeed")`)

1. `_get()` checks if column is CSV_NATIVE and normalized (`unit != u2`)
2. If normalized:
   - Calls `convertColumnToNormalizedUnits()` to convert data on-demand
   - Returns converted column (data in normalized units)
   - **Does NOT store** converted column (preserves base column for unit conversion)
3. If not normalized:
   - Returns base column as-is

**Important**: Base columns are **never replaced** - they remain in native units. Normalization creates a temporary converted column for display.

#### Unit Conversion Request (`_get("VehicleSpeed (mph)")`)

1. `Units.parseUnitConversion()` extracts base field and target unit
2. `getColumnInUnits()` is called with base field and target unit
3. `getColumnInUnits()` uses `super.get()` to access **raw base column** (bypasses normalization)
4. Gets native unit via `column.getNativeUnits()` (from `DatasetId.u2`)
5. If native unit differs from target unit:
   - Converts using `DatasetUnits.convertUnits()`
   - Returns converted column with full ID (e.g., `"VehicleSpeed (mph)"`)
6. If units match:
   - Returns base column (no conversion needed)

**Critical**: `getColumnInUnits()` uses `super.get()` to avoid normalization recursion. It needs the raw CSV_NATIVE column with native data.

## Implementation Details

### Normalization Detection

**Location**: `ECUxDataset._get()`

```java
Column baseColumn = super.get(id);
if (baseColumn != null && baseColumn.getColumnType() == Dataset.ColumnType.CSV_NATIVE && !isUnitConversionRequest) {
    String normalizedUnit = baseColumn.getUnits();
    String nativeUnit = baseColumn.getNativeUnits();
    if (normalizedUnit != null && nativeUnit != null && !normalizedUnit.equals(nativeUnit)) {
        // Column is normalized - convert data to normalized units for display
        Column converted = convertColumnToNormalizedUnits(baseColumn, nativeUnit, normalizedUnit, idStr);
        if (converted != null && converted != baseColumn) {
            return converted; // Return converted, don't store
        }
    }
}
```

**Key Points**:
- Only applies to CSV_NATIVE columns
- Skips normalization for unit conversion requests (pattern `"FieldName (unit)"`)
- Returns converted column without storing (base column preserved)

### Unit Conversion

**Location**: `ECUxDataset.getColumnInUnits()`

```java
// Get base column - use super.get() to get raw CSV_NATIVE column
// (normalization is handled in _get(), so we need the raw column for unit conversion)
Column column = super.get(columnName);

// Use native unit (u2) for conversion logic, not display unit (getUnits())
String currentUnit = column.getNativeUnits();
if(currentUnit == null || currentUnit.isEmpty() || currentUnit.equals(targetUnit)) {
    return column;
}

// Convert using DatasetUnits
Column converted = DatasetUnits.convertUnits(this, column, targetUnit, ...);
```

**Key Points**:
- Uses `super.get()` to bypass normalization (needs native data)
- Uses `getNativeUnits()` to get native unit (not `getUnits()`)
- Converts from native unit to target unit

### Helper Method: `convertColumnToNormalizedUnits()`

**Location**: `ECUxDataset.convertColumnToNormalizedUnits()`

Converts a CSV_NATIVE column from native units to normalized units. Used by `_get()` for display normalization.

**Behavior**:
- Does NOT call `_get()` or `getColumnInUnits()` (avoids recursion)
- Uses `DatasetUnits.convertUnits()` directly
- Inherits smoothing registration from base column
- Returns converted column (not stored)

## Special Cases

### BaroPressure

Always normalized to `mBar/kPa` regardless of preference (config override in `loggers.yaml`).

### Torque Columns

Follow normal preference rules:
- US_CUSTOMARY → `ft-lb`
- METRIC → `Nm`

## Common Patterns

### Accessing Native Data

```java
// For unit conversion logic - use super.get() to bypass normalization
Column baseColumn = super.get("VehicleSpeed");
String nativeUnit = baseColumn.getNativeUnits(); // Gets DatasetId.u2
```

### Accessing Normalized Data

```java
// For display - use get() which applies normalization
Column displayColumn = get("VehicleSpeed");
String displayUnit = displayColumn.getUnits(); // Gets DatasetId.unit (normalized)
double[] displayData = getData(new Key("VehicleSpeed", this), range); // Normalized data
```

### Requesting Unit-Converted Columns

```java
// Request unit-converted column
Column converted = get("VehicleSpeed (mph)");
// This:
// 1. Parses "VehicleSpeed (mph)" → base="VehicleSpeed", target="mph"
// 2. Gets base column via super.get() (native data)
// 3. Converts from native unit to "mph"
// 4. Returns converted column
```

### Preset/Preference Key Mapping

**Location**: `Units.mapUnitConversionToBaseField()`

When presets or preferences store unit-converted column names (e.g., `"BoostPressureActual (PSI)"`), but the menu only contains base field names (e.g., `"BoostPressureActual"`) because PSI is already the normalized unit, the system automatically maps unit-converted keys to base fields.

**Usage**:

```java
String mappedKey = Units.mapUnitConversionToBaseField(keyStr, (baseField) -> getNormalizedUnitForField(baseField));
```

**Behavior**:
- If key is not unit-converted (no `(unit)` pattern), returns original key
- If normalized unit matches requested unit, maps to base field name
- Otherwise, returns original key (no mapping applies)

**Used by**:
- `AxisMenu.makeMenuItem()` - Maps unit-converted `initialChecked` elements during menu creation
- `AxisMenu.setOnlySelected()` - Maps unit-converted preset keys when updating menu selections
- `ECUxPlot.loadPreset()` - Maps unit-converted keys before calling `editChartY()`

## Testing

### Test Expectations

**File**: `test-data/test-expectations.xml`

- **`<expected_units>`**: Exhaustive list of all columns with native units (from CSV)
- **`<expected_us_units>`**: Only columns that were normalized (where `unit != u2`)

### Unit Conversion Tests

Tests verify both behaviors:
1. **Normalization**: Base column requests return normalized data
2. **Unit Conversion**: Converting to different units works correctly from native data

## Debugging

### Column Has Wrong Units

- Check `DatasetId.unit` (normalized) vs `DatasetId.u2` (native)
- Verify `normalizeUnitsToStandard()` was called during load
- Check `loggers.yaml` for special case overrides

### Unit Conversion Not Working

- Verify `getColumnInUnits()` uses `super.get()` (not `_get()`)
- Check that `getNativeUnits()` is used (not `getUnits()`)
- Ensure base column is not replaced (should remain in native units)

### Normalization Recursion

- `convertColumnToNormalizedUnits()` must NOT call `_get()` or `getColumnInUnits()`
- `getColumnInUnits()` must use `super.get()` to access raw base column
- Unit conversion requests (pattern `"FieldName (unit)"`) skip normalization in `_get()`

## Related Files

- `src/org/nyet/ecuxplot/ECUxDataset.java`: Normalization and unit conversion logic
- `src/org/nyet/logfile/Dataset.java`: `DatasetId` and `Column` classes
- `src/org/nyet/ecuxplot/DataLogger.java`: `createDatasetIds()` method
- `src/org/nyet/ecuxplot/Units.java`: Unit preference mapping and preset key mapping (`mapUnitConversionToBaseField()`)
- `src/org/nyet/ecuxplot/DatasetUnits.java`: Unit conversion engine
- `src/org/nyet/ecuxplot/loggers.yaml`: Configuration (special cases)
- `src/org/nyet/ecuxplot/AxisMenu.java`: Menu creation and selection logic (uses mapping for presets)
- `src/org/nyet/ecuxplot/ECUxPlot.java`: Preset loading (uses mapping before adding series)
