# Adding Fields to Axis Menu

This guide explains how to add new fields to the axis menu system.

## Architecture Overview

The axis menu system has three main components:

- **`AxisMenu.java`**: Handles UI/presentation (menu item creation, submenu management)
  - Switch/case for exact matches (RPM, TIME, Sample only)
  - Delegates to `AxisMenuItems` for pattern routing
- **`AxisMenuItems.java`**: Contains data structures (field names, routing patterns, calculated field mappings)
  - `MENU_CALCULATED_FIELDS`: Maps base fields to their calculated fields
  - `SUBMENU_PATTERNS`: Simple regex patterns for field routing
  - `PATTERN_ROUTING_HANDLERS`: Complex regex patterns with handlers
  - All `SUBMENU_*` constants
- **`AxisMenuHandlers.java`**: Handles calculation logic (field calculations, dependencies, smoothing)
  - Handler methods that create calculated columns
  - Method references used in `AxisMenuItems.MenuCalculatedField`

**Separation of Concerns**: UI logic (`AxisMenu`), data (`AxisMenuItems`), and calculation logic (`AxisMenuHandlers`) are cleanly separated.

## Adding a New Field

### 1. Determine Field Type

**CSV Base Field**: Field exists in the CSV log file
- Add to pattern routing in `AxisMenuItems.java` or switch/case in `AxisMenu.java` (for RPM/TIME/Sample)
- No handler needed (field is read directly from CSV)

**Calculated Field**: Field is computed from other fields
- Add handler method in `AxisMenuHandlers.java`
- Add to `MENU_CALCULATED_FIELDS` in `AxisMenuItems.java` (data-driven approach)
- Or add to pattern routing handler in `AxisMenuItems.java` (for simple cases)

### 2. Add Menu Routing

Fields are routed to submenus using one of three methods (checked in order):

#### 1. Switch/Case (RPM, TIME, Sample Only)

For the three fundamental fields, add to switch/case in `AxisMenu.add()`:

```java
case "YourField": {
    addToSubmenu(AxisMenuItems.SUBMENU_YOUR_SUBMENU, dsid);
    // Add calculated fields, unit conversions, etc.
    handled = true;
    break;
}
```

**When to use switch/case**:
- Only for RPM, TIME, Sample (fundamental fields with special handling)
- Field needs calculated fields added (e.g., `RPM` → adds WHP, HP, WTQ, etc.)
- Field needs conditional logic (e.g., debug-only fields)

#### 2. Pattern Routing (Most Fields)

For fields that need calculated fields or special logic, add to `PATTERN_ROUTING_HANDLERS` in `AxisMenuItems.java`:

```java
new PatternRoutingPair("YourField",
    (menu, id, dsid, item) -> {
        menu.addToSubmenu(SUBMENU_YOUR_SUBMENU, dsid);
        if (id.equals("YourField")) {
            menu.addCalculatedFieldsFromData("YourField", dsid.type);
        }
        return true;
    }),
```

For simple routing without calculated fields, add to `SUBMENU_PATTERNS` in `AxisMenuItems.java`:

```java
new PatternSubmenuPair(".*YourField.*", SUBMENU_YOUR_SUBMENU),
```

**Regex Pattern Conventions**:
- `Pattern.matcher().matches()` requires the entire string to match
- `^` and `$` anchors are redundant/ignored (do NOT use them)
- Use `.*` at start/end to match anywhere: `.*EGT.*`
- Patterns without `.*` match from start: `RPM.*`, `TIME \\[Range\\]`

**Examples**:
- `.*EGT.*` → matches "EGTbank1", "EGTbank2", etc.
- `.*(Cam|NWS|Valve|VV).*` → matches VVT fields
- `RPM.*` → matches "RPM", "RPM - raw", etc.

**Important**: Switch/case is checked first, then pattern routing, then simple pattern table. Order matters for pattern routing handlers (most specific first).

### 3. Add Calculated Fields (When Base Field Detected)

Calculated fields are added when a base field is detected. Use the data-driven approach in `AxisMenuItems.java`:

#### Data-Driven Approach (Recommended)

Add to `MENU_CALCULATED_FIELDS` array in `AxisMenuItems.java`:

```java
// YourBaseField -> calculated fields
new BaseFieldCalculatedFields("YourBaseField", Arrays.asList(
    new MenuCalculatedField("Field1", SUBMENU_YOUR_SUBMENU, AxisMenuHandlers::getYourFieldColumn),  // submenu item
    new MenuCalculatedField("Field2", null, AxisMenuHandlers::getYourFieldColumn),  // standalone item (submenu == null)
    new MenuCalculatedField("Field3 (unit)", null, null),  // unit conversion (handlerMethod == null)
    new MenuCalculatedField("Field4", SUBMENU_YOUR_SUBMENU, AxisMenuHandlers::getYourFieldColumn, true),  // ME7LOGGER only
), null),
```

**MenuCalculatedField parameters**:
- `fieldName`: Name of the calculated field
- `submenu`: Submenu name (use `AxisMenuItems.SUBMENU_*` constants), or `null` for standalone items
- `handlerMethod`: Method reference to handler (e.g., `AxisMenuHandlers::getYourFieldColumn`), or `null` for unit conversions
- `me7loggerOnly`: `true` if field should only appear for ME7LOGGER (optional, defaults to `false`)

**Then call it from pattern routing handler**:

```java
new PatternRoutingPair("YourBaseField",
    (menu, id, dsid, item) -> {
        menu.addToSubmenu(SUBMENU_YOUR_SUBMENU, dsid);
        menu.addCalculatedFieldsFromData("YourBaseField", dsid.type);
        return true;
    }),
```

Or from switch/case in `AxisMenu.java`:

```java
case "YourBaseField": {
    addToSubmenu(AxisMenuItems.SUBMENU_YOUR_SUBMENU, dsid);
    addCalculatedFieldsFromData("YourBaseField", dsid.type);
    handled = true;
    break;
}
```

**For RPM-triggered fields**, add to `RPM_CALCULATED_FIELDS` in `AxisMenuItems.java`:

```java
new RpmCalculatedField("YourField", SUBMENU_YOUR_SUBMENU,
    new String[]{"Step 1", "Step 2"},  // tooltip steps
    "smoothingType",  // range-aware smoothing
    false,  // debug only
    null),  // unit conversion
```

### 4. Add Handler (For Calculated Fields)

If the field is calculated, add a handler method in `AxisMenuHandlers.java`:

```java
public static Column getYourFieldColumn(ECUxDataset dataset, Comparable<?> id) {
    String idStr = id.toString();
    switch (idStr) {
        case "YourField": {
            // Calculation logic
            final DoubleArray result = /* ... */;
            return dataset.createColumn(id, "unit", result, ColumnType.OTHER_RUNTIME);
        }
        default:
            return null;
    }
}
```

Then add the handler to `tryAllHandlers()`:

```java
result = getYourFieldColumn(dataset, id);
if (result != null) return result;
```

**Important**: Use method references (e.g., `AxisMenuHandlers::getYourFieldColumn`) in `MenuCalculatedField`, not string names. This provides type safety and avoids reflection.

### 5. Update Test Expectations

Add the field to test expectations in `test-data/axis-menu-expectations.xml`:

```xml
<field name="YourField" submenu="YourSubmenu"/>
```

For calculated fields:

```xml
<calculated_field name="YourField" submenu="YourSubmenu"/>
```

And in the handler tests section:

```xml
<handler name="getYourFieldColumn">
    <field>YourField</field>
</handler>
```

### 6. Add to Test CSV (If Needed)

If testing requires the field, add it to `test-data/comprehensive-test-dataset.csv`:

1. Add to id2 line (raw logger field name)
2. Add unit to units line
3. Add to ID line (canonical field name)
4. Add data values to all data rows

## Common Patterns

### Temperature Fields

```java
// Simple pattern (in SUBMENU_PATTERNS)
new PatternSubmenuPair(".*(Coolant|Water|Oil|Exhaust|EGT|Cat|MainCat|Ambient|Transmission).*(Temperature|Temp).*", SUBMENU_TEMPERATURE),

// Pattern routing (if needs calculated fields, in PATTERN_ROUTING_HANDLERS)
new PatternRoutingPair("IntakeAirTemperature",
    (menu, id, dsid, item) -> {
        menu.addToSubmenu(SUBMENU_TEMPERATURE, dsid);
        menu.addCalculatedFieldsFromData("IntakeAirTemperature", dsid.type);
        return true;
    }),
```

### Torque Fields

```java
// Simple pattern (in SUBMENU_PATTERNS)
new PatternSubmenuPair(".*Torque.*(Actual|Clutch|Requested|Corrected|Limit).*", SUBMENU_TORQUE),

// Pattern routing (if needs derived fields, in PATTERN_ROUTING_HANDLERS)
new PatternRoutingPair("TorqueDesired",
    (menu, id, dsid, item) -> {
        menu.add(item);  // standalone item
        menu.addCalculatedFieldsFromData("TorqueDesired", dsid.type);
        return true;
    }),
```

### MAF Fields

```java
// Pattern routing (in PATTERN_ROUTING_HANDLERS)
new PatternRoutingPair(".*(Intake.*Air|MAF|Mass.*Air|Air.*Mass|Mass.*Flow|Intake.*Flow|Airflow).*",
    (menu, id, dsid, item) -> {
        menu.addToSubmenu(SUBMENU_MAF, dsid);
        if (id.equals("MassAirFlow")) {
            menu.addCalculatedFieldsFromData("MassAirFlow", dsid.type);
            menu.addToSubmenu(SUBMENU_CALC_MAF, new javax.swing.JSeparator());
        }
        return true;
    }),
```

## Submenu Constants

Use these constants (defined in `AxisMenuItems.java`) instead of string literals:

- `SUBMENU_RPM`, `SUBMENU_TIME`, `SUBMENU_SAMPLE`
- `SUBMENU_MAF`, `SUBMENU_FUEL`, `SUBMENU_BOOST`
- `SUBMENU_THROTTLE`, `SUBMENU_IGNITION`, `SUBMENU_TEMPERATURE`
- `SUBMENU_VVT`, `SUBMENU_CATS`, `SUBMENU_EGT`
- `SUBMENU_IDLE`, `SUBMENU_KNOCK`, `SUBMENU_MISFIRES`
- `SUBMENU_O2_SENSORS`, `SUBMENU_SPEED`, `SUBMENU_TORQUE`
- `SUBMENU_POWER`, `SUBMENU_ACCELERATION`
- `SUBMENU_ZEITRONIX`, `SUBMENU_ME7_LOGGER`, `SUBMENU_EVOSCAN`

## Testing

After adding a field:

1. Run tests: `make test`
2. Verify field appears in correct submenu
3. Verify calculated fields work (if applicable)
4. Check test output for any failures

Tests validate:
- Field routing to correct submenu
- Calculated field creation
- Handler methods return correct columns
- Consistency between `AxisMenu`, `AxisMenuItems`, and `AxisMenuHandlers`

## Related Documentation

- [System Architecture](system-architecture.md) - Overall system architecture
- [Smoothing Architecture](smoothing.md) - Smoothing system details
