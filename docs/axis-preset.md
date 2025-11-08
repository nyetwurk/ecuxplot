# Axis Preset Support - Current State & Unexpected Behaviors

## Overview

This document describes the current state of axis preset column support, focusing on unexpected behaviors and limitations that developers and users should be aware of.

**Related**: See [System Architecture - Preset Loading Architecture](system-architecture.md#preset-loading-architecture) for implementation details.

---

## Current State

**Preset Support**:
- Timing preset: Calculation logic implemented (`IgnitionTimingAngleOverallDesired` from retard fields)
- Unit conversion: Automatic mBar/kPa → PSI when `(PSI)` suffix requested
- Test framework: Pattern matching supports partial/flexible column matching

---

## Unexpected Behaviors & Limitations

### Unit Conversion Behavior

**Behavior**: When presets request `BoostPressureDesired (PSI)` or `BoostPressureActual (PSI)`, the system automatically converts from base units (mBar/kPa).

**Expected**: Fields like `BoostPressureActual` in loggers.yaml alias to canonical name.

**Unexpected**:
- SIMOS_TOOLS logs boost in kPa (`MAP (kPa)`), automatically converts to PSI when `(PSI)` suffix requested
- SWCOMM logs boost in mBar, automatically converts to PSI when `(PSI)` suffix requested
- Conversion requires `BaroPressure` for accurate gauge pressure calculation

Unit conversion handled automatically via `ECUxDataset._get()` → `parseUnitConversion()` → `getColumnInUnits()`. No explicit alias needed.

---

### Timing Preset Calculation Behavior

**Behavior**: `IgnitionTimingAngleOverallDesired` is calculated from retard fields when not directly available.

**Formula**: `IgnitionTimingAngleOverallDesired = IgnitionTimingAngleOverall + average(abs(IgnitionRetardCyl0-7))`

**Implementation Note**: The code uses 0-indexed loop (`for(int i=0;i<8;i++)`) looking for `IgnitionRetardCyl0` through `IgnitionRetardCyl7`, but canonical field names from aliases are 1-indexed (`IgnitionRetardCyl1` through `IgnitionRetardCyl8`). Since `Dataset.get()` uses exact string matching, this loop will not find 1-indexed canonical fields. The calculation may only work via the `AverageIgnitionRetard` fallback for loggers that don't provide per-cylinder retard fields.

**Unexpected Cases**:

#### Partial Cylinder Support (SWCOMM)
- Only logs 3 of 6 cylinders: `IGA_ADJ_KNK[0,3,5]` → `IgnitionRetardCyl1,4,6` (canonical 1-indexed names)
- **Implementation Note**: The calculation code looks for `IgnitionRetardCyl0-7` (0-indexed), but canonical names are `IgnitionRetardCyl1-8` (1-indexed). Since `Dataset.get()` uses exact string matching, this loop will not find these 1-indexed fields. The calculation may rely on fallback to `AverageIgnitionRetard` if available.

#### Average Retard Fallback (JB4)
- No per-cylinder retard fields
- Falls back to `AverageIgnitionRetard` when available
- Calculation: `IgnitionTimingAngleOverall + abs(AverageIgnitionRetard)`

#### Zero Retard Values
- Zero values are included in average calculation
- Example: ECUX Row 0 has retards `[3, 2.25, 3, 2.25, 0, 0]` → average = 1.75 (not 2.19)

Timing preset may show slightly different desired timing values depending on how many cylinders are logged. Pattern matching in test framework uses regex `"IgnitionRetardCyl.*|AverageIgnitionRetard"`.

---

### Logger-Specific Limitations

#### SIMOS_TOOLS - Has EngineLoad
- **Current State**: SIMOS_TOOLS has both `EngineLoad` (index 39) and `MassAirFlowPerStroke`
- **Preset Configuration**: Timing preset ykeys0 accepts either `EngineLoad` OR `MassAirFlowPerStroke` (defined in `loggers.yaml`)
- **Impact**: Timing preset can use either field depending on availability

#### SWCOMM - Partial Cylinder Logging
- **Unexpected**: Only logs 3 of 6 cylinders (1, 4, 6), not sequential 1-6
- **Impact**: Timing preset calculation uses only available cylinders

#### VCDS - Conditional Timing Support
- **Unexpected**: Timing preset support depends on VCDS group content, not just logger type
- **Details**:
  - Only files with Group 020/021 containing "Knock Reg." fields support timing
  - Group 020 with "Idle Stabilization" does NOT support timing
  - Group 026/027 contains voltage sensors, not ignition retard
- **Impact**: Same logger type (VCDS) may or may not support timing depending on logged groups

#### WINLOG - Test Data Limitation
- **Unexpected**: Logger type CAN support timing (via ME7_ALIASES), but test file doesn't
- **Details**: `winlog.csv` only has `wkrm` (average), not per-cylinder `wkr_*` fields
- **Impact**: Real WINLOG files with `wkr_*` fields would support timing, test file doesn't

#### VOLVOLOGGER - Missing BoostPressureDesired
- **Unexpected**: Only logs actual boost, not desired/specified boost
- **Impact**: Power preset cannot show boost target vs actual comparison

#### VCDS - Missing BaroPressure
- **Unexpected**: No barometric pressure field available
- **Impact**: Boost calculations requiring barometric corrections may be unavailable. Unit conversion uses standard atmospheric pressure fallback when `BaroPressure` missing

---

### Fueling Preset Limitations

**Legacy Design**: Current Fueling preset designed for older MPI-only vehicles with external wideband sensors.

**Unexpected Limitations**:
- **SIMOS_TOOLS**: Has both DI and MPI injector fields (`FuelInj PW DI`, `FuelInj PW MPI`) - cannot map to standard `EffInjectorDutyCycle` or `FuelInjectorDutyCycle`
- **Zeitronix**: Requires external wideband sensor (no longer relevant for modern vehicles)
- **Modern Engines**: Would require complete preset rewrite for DI/MPI hybrid with onboard sensors

**Impact**: Fueling preset has limited support for modern vehicles. Legacy vehicles with external wideband still supported.

---

### Pattern Matching in Test Framework

**Behavior**: Test framework uses regex patterns to match columns.

**Unexpected**: Pattern `"IgnitionRetardCyl.*|AverageIgnitionRetard"` matches:
- Any number of cylinders (not limited to 8)
- Partial cylinder sets (e.g., SWCOMM's 1,4,6)
- Average retard fallback (JB4)

---

### Blacklisted Fields Behavior

**COBB_AP**: Last column (index 49, "AP Info") is intentionally blacklisted.

**Behavior**:
- Alias `["AP Info:.*", ""]` maps to empty string
- Field is still parsed and appears in dataset, but with empty ID
- Unit regex extracts values incorrectly (parses unit as "CXCA 5G09C0BB01")

**Unexpected**:
- Blacklisted field still appears in debug output with parsed unit
- Test expectations intentionally exclude this index (0-48 only, not 0-49)

Blacklisted fields are filtered but parsing still happens - this is expected behavior for metadata fields.

---

## Calculation Edge Cases

### IgnitionTimingAngleOverallDesired Calculation

**Location**: `AxisMenuHandlers.java`

**Edge Cases**:
1. **No retard fields**: Returns `IgnitionTimingAngleOverall` unchanged
2. **Negative retard values**: Code uses `.abs()` - values normalized to positive
3. **Missing IgnitionTimingAngleOverall**: Should fail gracefully (test framework verifies exists)
4. **Zero retard values**: Included in average (verified in ECUX test data)

### IgnitionTimingAngleOverall Calculation

**Location**: `AxisMenuHandlers.java`

**Edge Case**:
- If not directly available, calculates from per-cylinder timing angles (`IgnitionTimingAngle1-8`)
- Supports loggers like JB4 that only log per-cylinder timing
- Averages available per-cylinder angles (handles partial cylinder counts)

---

## References

- `loggers.yaml`: Logger definitions, aliases, preset categories
- `AxisMenuHandlers.java`: Calculation logic for derived fields (ignition timing, etc.)
- `ECUxDataset.java`: Dataset management and column retrieval
- `test-expectations.xml`: Expected columns per logger
- `DatasetUnits.java`: Unit conversion logic
