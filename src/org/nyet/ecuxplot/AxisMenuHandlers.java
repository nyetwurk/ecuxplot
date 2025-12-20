package org.nyet.ecuxplot;

import org.nyet.logfile.Dataset;
import org.nyet.logfile.Dataset.Column;
import org.nyet.logfile.Dataset.ColumnType;
import org.nyet.util.DoubleArray;
import org.nyet.util.Smoothing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Handler functions for ECUxDataset calculated columns.
 *
 * These handlers are extracted from ECUxDataset._get() to improve maintainability
 * and prepare for future consolidation with AxisMenu.
 *
 * Each handler function takes an ECUxDataset instance and a column ID,
 * and returns a Column if it matches, null otherwise.
 */
public class AxisMenuHandlers {
    private static final Logger logger = LoggerFactory.getLogger(AxisMenuHandlers.class);

    /**
     * Functional interface for column handlers.
     * Each handler takes a dataset and an ID and returns a Column if it matches, null otherwise.
     */
    @FunctionalInterface
    public interface ColumnHandler {
        Column handle(ECUxDataset dataset, Comparable<?> id);
    }

    /**
     * Pattern-to-handler mapping for regex-based routing.
     * Patterns are checked in order (most specific first).
     * Handlers should use switch/case internally for exact field matching.
     *
     * This allows for pattern-based routing (e.g., "Sim *" -> getMiscSimColumn)
     * while keeping exact matches efficient with switch/case.
     */
    private static class PatternHandlerPair {
        final Pattern pattern;
        final java.util.function.BiFunction<ECUxDataset, Comparable<?>, Column> handler;

        PatternHandlerPair(String patternStr, java.util.function.BiFunction<ECUxDataset, Comparable<?>, Column> handler) {
            this.pattern = Pattern.compile(patternStr);
            this.handler = handler;
        }
    }

    /**
     * Regex patterns for pattern-based handler routing.
     * Patterns are checked BEFORE individual handler switch/case statements.
     * Used as an optimization to route patterns directly to handlers without trying all handlers.
     *
     * Patterns are ordered from most specific to least specific.
     * Only patterns that can uniquely identify a handler should be included here.
     */
    private static final PatternHandlerPair[] PATTERN_HANDLERS = {
        // LDR fields (all in getBoostZeitronixColumn)
        new PatternHandlerPair("^LDR .*", AxisMenuHandlers::getBoostZeitronixColumn),
        // Boost Spool Rate fields (all in getBoostZeitronixColumn)
        new PatternHandlerPair("^Boost Spool Rate .*", AxisMenuHandlers::getBoostZeitronixColumn),
        // Acceleration fields (all in getVelocityAccelerationColumn)
        new PatternHandlerPair("^Acceleration .*", AxisMenuHandlers::getVelocityAccelerationColumn),
    };

    /**
     * Try pattern-based routing before exact match handlers.
     * Returns the first handler that matches the pattern, or null if no pattern matches.
     *
     * @param dataset The ECUxDataset instance
     * @param id The column ID to route
     * @return Column if a pattern handler matches, null otherwise
     */
    public static Column tryPatternRouting(ECUxDataset dataset, Comparable<?> id) {
        String idStr = id.toString();
        for (PatternHandlerPair pair : PATTERN_HANDLERS) {
            if (pair.pattern.matcher(idStr).matches()) {
                return pair.handler.apply(dataset, id);
            }
        }
        return null;
    }

    /**
     * Array of column handlers, checked in order.
     * Each handler may return null if it doesn't match the ID.
     * First handler to return non-null wins.
     *
     * IMPORTANT: Handlers are ORDER-INDEPENDENT.
     * - Each handler matches unique field names (no conflicts)
     * - Dependencies are resolved recursively via dataset.get() which uses the same handler array
     * - Recursion protection in _get() ensures columns are only calculated once
     * - Handler order can be changed without affecting functionality
     *
     * Handler organization (logical grouping, not evaluation order):
     * 1. Fundamental - Range, Velocity/Acceleration
     * 2. Power/Torque - Power/Torque calculations, Diagnostic derivatives
     * 3. Fuel/Air Systems - MAF/Fuel, AFR, Injector Duty Cycle
     * 4. Engine Control - Boost/Zeitronix, Ignition Timing
     * 5. Engine Output - Engine Torque/HP
     * 6. Miscellaneous - Misc Sim calculations
     */
    private static final ColumnHandler[] ALL_HANDLERS = {
        // ========== FUNDAMENTAL ==========
        // Range calculations (TIME [Range], Sample [Range])
        AxisMenuHandlers::getRangeColumn,
        // Velocity and acceleration calculations
        AxisMenuHandlers::getVelocityAccelerationColumn,

        // ========== POWER/TORQUE ==========
        // Power/torque handler (WHP, HP, WTQ, TQ, Drag, and unit conversions)
        AxisMenuHandlers::getPowerTorqueColumn,
        // Diagnostic columns handler (debug-only derivatives, not in AxisMenu)
        AxisMenuHandlers::getSmoothingDiagnosticColumn,

        // ========== FUEL/AIR SYSTEMS ==========
        // MAF and fuel calculations
        AxisMenuHandlers::getMafFuelColumn,
        // Air-fuel ratio calculations
        AxisMenuHandlers::getAfrColumn,
        // Injector duty cycle calculations
        AxisMenuHandlers::getInjectorDutyCycleColumn,

        // ========== ENGINE CONTROL ==========
        // Boost pressure and Zeitronix calculations
        AxisMenuHandlers::getBoostZeitronixColumn,
        // Ignition timing calculations
        AxisMenuHandlers::getIgnitionTimingColumn,

        // ========== ENGINE OUTPUT ==========
        // Engine torque and HP calculations
        AxisMenuHandlers::getEngineTorqueHpColumn,

        // ========== MISCELLANEOUS ==========
        // Miscellaneous Sim calculations
        AxisMenuHandlers::getMiscSimColumn,
    };

    /**
     * Get BaroPressure column normalized to mBar.
     * Handles conversion from kPa, PSI, or other units to mBar.
     *
     * @param dataset The ECUxDataset instance
     * @return BaroPressure column in mBar, or null if not found
     */
    private static Column getBaroPressureMbar(ECUxDataset dataset) {
        Column baro = dataset.getCsvColumn("BaroPressure");
        if (baro != null) {
            return DatasetUnits.convertUnits(dataset, baro, UnitConstants.UNIT_MBAR, null, baro.getColumnType());
        }
        return null;
    }

    /**
     * Try all registered handlers in order.
     * Returns the first handler that matches and creates a column, or null if no handler matches.
     *
     * @param dataset The ECUxDataset instance
     * @param id The column ID to handle
     * @return Column if a handler matches, null otherwise
     */
    public static Column tryAllHandlers(ECUxDataset dataset, Comparable<?> id) {
        for (ColumnHandler handler : ALL_HANDLERS) {
            Column c = handler.handle(dataset, id);
            if (c != null) {
                return c;  // Handler matched and created column
            }
        }
        return null;  // No handler matched
    }

    /**
     * Handle velocity and acceleration calculations.
     *
     * Fields handled:
     * - Calc Velocity
     * - Acceleration (RPM/s)
     * - Acceleration (RPM/s) - raw
     * - Acceleration (RPM/s) - from base RPM
     * - Acceleration (m/s^2) - raw
     * - Acceleration (m/s^2)
     * - Acceleration (g)
     *
     * @param dataset The ECUxDataset instance
     * @param id The column ID to handle
     * @return Column if it's a velocity/acceleration field, null otherwise
     */
    public static Column getVelocityAccelerationColumn(ECUxDataset dataset, Comparable<?> id) {
        String idStr = id.toString();

        switch (idStr) {
            case "Calc Velocity": {
                // Calculate vehicle speed from RPM and gear ratio (more accurate than VehicleSpeed sensor)
                // Uses user-specified rpm_per_mph for calibration
                final DoubleArray rpm = dataset.get("RPM").data;
                return dataset.createColumn(id, UnitConstants.UNIT_MPS, rpm.div(dataset.getEnv().c.rpm_per_mph()).
                    mult(UnitConstants.MPS_PER_MPH), ColumnType.VEHICLE_CONSTANTS);
            }
            case "Acceleration (RPM/s)": {
                // Smoothed RPM acceleration - uses smoothed RPM with AccelMAW() smoothing applied via range-aware smoothing
                // Smoothing window: AccelMAW() (typically 5-10 samples) - applied in getData() via range-aware smoothing
                // NOTE: We do NOT apply smoothing during derivative calculation here to avoid double-smoothing and edge loss.
                // Instead, smoothing is applied only via range-aware smoothing in getData(), which handles edges correctly with padding.
                final DoubleArray y = dataset.get("RPM").data;
                final DoubleArray x = dataset.get("TIME").data;
                final DoubleArray derivative = y.derivative(x).max(0);  // No smoothing during derivative - will be smoothed in getData()
                Column c = dataset.createColumn(id, UnitConstants.UNIT_RPS, derivative, ColumnType.PROCESSED_VARIANT);
                // Register for range-aware smoothing to prevent edge artifacts when viewing truncated ranges
                dataset.registerSmoothingWindow(idStr, dataset.getFilter().accelMAW());
                return c;
            }
            case "Acceleration (RPM/s) - raw": {
                // Use smoothed RPM (not raw) to reduce quantization noise before differentiation
                // "Raw" refers to unsmoothed derivative, not unsmoothed input
                // Smoothing window: none (derivative without smoothing window)
                final DoubleArray y = dataset.get("RPM").data;
                final DoubleArray x = dataset.get("TIME").data;
                final DoubleArray derivative = y.derivative(x).max(0);
                return dataset.createColumn(id, UnitConstants.UNIT_RPS, derivative, ColumnType.PROCESSED_VARIANT);
            }
            case "Acceleration (RPM/s) - from base RPM": {
                // Debug column: acceleration from base RPM input (uses AccelMAW smoothing on input)
                // Uses base RPM (SG smoothing only) instead of final RPM (adaptive smoothing)
                // Applies AccelMAW smoothing to base RPM input before derivative calculation
                // This allows comparison of base RPM vs final RPM effects on acceleration
                final Column baseRpmCol = dataset.getBaseRpm();
                final DoubleArray y = (baseRpmCol != null) ? baseRpmCol.data : null;
                if (y == null) {
                    logger.warn("_get('{}'): baseRpm is null, cannot calculate acceleration", id);
                    return null;
                }
                final DoubleArray x = dataset.get("TIME").data;
                // Apply AccelMAW smoothing to base RPM, then calculate derivative
                // Convert accelMAW from seconds to samples for derivative smoothing
                final int accelMAW = (int)Math.round(dataset.getSamplesPerSec() * dataset.getFilter().accelMAW());
                DoubleArray derivative;
                if (accelMAW > 0 && dataset.getSamplesPerSec() > 0) {
                    final double[] baseRpmArray = y.toArray();
                    final Smoothing smoother = new Smoothing(accelMAW);
                    final double[] smoothedRpm = smoother.applyToRange(baseRpmArray, 0, baseRpmArray.length - 1);
                    derivative = new DoubleArray(smoothedRpm).derivative(x).max(0);
                } else {
                    derivative = y.derivative(x).max(0);
                }
                return dataset.createColumn(id, UnitConstants.UNIT_RPS, derivative, ColumnType.PROCESSED_VARIANT);
            }
            case "Acceleration (m/s^2) - raw": {
                // Raw (unsmoothed) acceleration in m/s^2 - calculated directly from RPM (same approach as RPM/s)
                // "Raw" refers to unsmoothed derivative, not unsmoothed input
                // Smoothing window: none (derivative without smoothing window)
                final DoubleArray y = dataset.get("RPM").data;
                final DoubleArray x = dataset.get("TIME").data;
                final DoubleArray derivative = y.derivative(x).max(0);
                // Convert RPM/s to m/s^2: derivative (RPM/s) / rpm_per_mph * MPS_PER_MPH
                final DoubleArray accel = derivative.div(dataset.getEnv().c.rpm_per_mph()).
                    mult(UnitConstants.MPS_PER_MPH);
                return dataset.createColumn(id, "m/s^2", accel, ColumnType.VEHICLE_CONSTANTS);
            }
            case "Acceleration (m/s^2)": {
                // Smoothed acceleration in m/s^2 - calculated directly from RPM (same approach as Acceleration (RPM/s))
                // Uses smoothed RPM directly, derivative with AccelMAW() smoothing applied via range-aware smoothing
                // Smoothing window: AccelMAW() (typically 5-10 samples) - applied in getData() via range-aware smoothing
                // NOTE: We do NOT apply smoothing during derivative calculation here to avoid double-smoothing and edge loss.
                // Instead, smoothing is applied only via range-aware smoothing in getData(), which handles edges correctly with padding.
                // This ensures consistent smoothing between RPM/s and m/s^2 acceleration
                final DoubleArray y = dataset.get("RPM").data;
                // Log values from middle of dataset to verify we're using smoothed RPM (not CSV)
                // Also compare with CSV RPM at same indices to verify smoothing is applied
                final Column csvRpmCol = dataset.getCsvRpm();
                if (y != null && y.size() > 20 && csvRpmCol != null && csvRpmCol.data.size() == y.size()) {
                    final int middleStart = y.size() / 2 - 5;
                    final int middleEnd = middleStart + 10;
                    final StringBuilder sb = new StringBuilder();
                    sb.append("RPM data used for acceleration (indices ").append(middleStart).append("-").append(middleEnd - 1).append("): ");
                    for (int j = middleStart; j < middleEnd && j < y.size(); j++) {
                        if (j > middleStart) sb.append(", ");
                        sb.append(String.format("%.1f", y.get(j)));
                    }
                    logger.debug("_get('{}'): {}", id, sb.toString());
                    // Compare with CSV RPM at same indices
                    final StringBuilder sbCsv = new StringBuilder();
                    sbCsv.append("CSV RPM at same indices (for comparison): ");
                    for (int j = middleStart; j < middleEnd && j < csvRpmCol.data.size(); j++) {
                        if (j > middleStart) sbCsv.append(", ");
                        sbCsv.append(String.format("%.1f", csvRpmCol.data.get(j)));
                    }
                    logger.debug("_get('{}'): {}", id, sbCsv.toString());
                }
                final DoubleArray x = dataset.get("TIME").data;
                final DoubleArray derivative = y.derivative(x).max(0);  // No smoothing during derivative - will be smoothed in getData()
                // Convert RPM/s to m/s^2: derivative (RPM/s) / rpm_per_mph * MPS_PER_MPH
                final DoubleArray accel = derivative.div(dataset.getEnv().c.rpm_per_mph()).
                    mult(UnitConstants.MPS_PER_MPH);
                Column c = dataset.createColumn(id, "m/s^2", accel, ColumnType.VEHICLE_CONSTANTS);
                // Register for range-aware smoothing to prevent edge artifacts when viewing truncated ranges
                dataset.registerSmoothingWindow(idStr, dataset.getFilter().accelMAW());
                return c;
            }
            case "Acceleration (g)": {
                // Depends on Acceleration (m/s^2) which uses RPM_PER_MPH
                final DoubleArray a = dataset.get("Acceleration (m/s^2)").data;
                return dataset.createColumn(id, UnitConstants.UNIT_G, a.div(UnitConstants.STANDARD_GRAVITY), ColumnType.VEHICLE_CONSTANTS);
            }
            default:
                return null; // Not a velocity/acceleration field
        }
    }

    /**
     * Handle MAF and fuel-related calculations.
     *
     * Fields handled:
     * - Sim Load
     * - Sim Load Corrected
     * - Sim MAF
     * - MassAirFlow df/dt
     * - Turbo Flow
     * - Turbo Flow (lb/min)
     * - Sim Fuel Mass
     *
     * @param dataset The ECUxDataset instance
     * @param id The column ID to handle
     * @return Column if it's a MAF/fuel field, null otherwise
     */
    public static Column getMafFuelColumn(ECUxDataset dataset, Comparable<?> id) {
        String idStr = id.toString();

        switch (idStr) {
            case "Sim Load": {
                // g/sec to kg/hr
                // Use getCsvColumn() to get raw CSV data without triggering calculations
                final DoubleArray a = dataset.getCsvColumn("MassAirFlow").data.mult(UnitConstants.KGH_PER_GPS);
                final DoubleArray b = dataset.getCsvColumn("RPM").data.smooth();
                // KUMSRL
                return dataset.createColumn(id, UnitConstants.UNIT_PERCENT, a.div(b).div(.001072));
            }
            case "Sim Load Corrected": {
                // g/sec to kg/hr
                final DoubleArray a = dataset.get("Sim MAF").data.mult(UnitConstants.KGH_PER_GPS);
                final DoubleArray b = dataset.get("RPM").data;
                // KUMSRL
                return dataset.createColumn(id, UnitConstants.UNIT_PERCENT, a.div(b).div(.001072));
            }
            case "Sim MAF": {
                // mass in g/sec
                final DoubleArray a = dataset.getCsvColumn("MassAirFlow").data.
                    mult(dataset.getEnv().f.MAF_correction()).add(dataset.getEnv().f.MAF_offset());
                return dataset.createColumn(id, UnitConstants.UNIT_GPS, a, ColumnType.OTHER_RUNTIME);
            }
            case "MassAirFlow df/dt": {
                // mass in g/sec
                final DoubleArray maf = dataset.getCsvColumn("MassAirFlow").data;
                final DoubleArray time = dataset.get("TIME").data;
                return dataset.createColumn(id, "g/sec^s", maf.derivative(time).max(0));
            }
            case "Turbo Flow": {
                final DoubleArray a = dataset.get("Sim MAF").data;
                return dataset.createColumn(id, "m^3/sec", a.div(1225*dataset.getEnv().f.turbos()), ColumnType.OTHER_RUNTIME);
            }
            case "Turbo Flow (lb/min)": {
                final DoubleArray a = dataset.get("Sim MAF").data;
                return dataset.createColumn(id, "lb/min", a.div(7.55*dataset.getEnv().f.turbos()), ColumnType.OTHER_RUNTIME);
            }
            case "Sim Fuel Mass": {
                // based on te (effective injection time)
                final double gps = dataset.getEnv().f.injector()*UnitConstants.GPS_PER_CCMIN;
                final double cylinders = dataset.getEnv().f.cylinders();
                final Column bank1 = dataset.get("EffInjectorDutyCycle");
                final Column bank2 = dataset.get("EffInjectorDutyCycleBank2");
                DoubleArray duty = bank1.data;
                /* average two duties for overall mass */
                if (bank2!=null) duty = duty.add(bank2.data).div(2);
                final DoubleArray a = duty.mult(cylinders*gps/100);
                return dataset.createColumn(id, "g/sec", a, ColumnType.OTHER_RUNTIME);
            }
            default:
                return null; // Not a MAF/fuel field
        }
    }

    /**
     * Handle air-fuel ratio calculations.
     *
     * Fields handled:
     * - Sim AFR
     * - Sim lambda
     * - Sim lambda error
     *
     * @param dataset The ECUxDataset instance
     * @param id The column ID to handle
     * @return Column if it's an AFR field, null otherwise
     */
    public static Column getAfrColumn(ECUxDataset dataset, Comparable<?> id) {
        String idStr = id.toString();

        // Note: AFR conversions (lambda to AFR) are now handled by generic unit conversion handler
        switch (idStr) {
            case "Sim AFR": {
                final DoubleArray a = dataset.get("Sim MAF").data;
                final DoubleArray b = dataset.get("Sim Fuel Mass").data;
                return dataset.createColumn(id, UnitConstants.UNIT_AFR, a.div(b));
            }
            case "Sim lambda": {
                final DoubleArray a = dataset.get("Sim AFR").data.div(UnitConstants.STOICHIOMETRIC_AFR);
                return dataset.createColumn(id, UnitConstants.UNIT_LAMBDA, a);
            }
            case "Sim lambda error": {
                final DoubleArray a = dataset.getCsvColumn("AirFuelRatioDesired").data;
                final DoubleArray b = dataset.get("Sim lambda").data;
                return dataset.createColumn(id, UnitConstants.UNIT_PERCENT, a.div(b).mult(-1).add(1).mult(100).
                    max(-25).min(25));
            }
            default:
                return null; // Not an AFR field
        }
    }

    /**
     * Handle injector duty cycle calculations.
     *
     * Fields handled:
     * - FuelInjectorDutyCycle
     * - EffInjectorDutyCycle
     * - EffInjectorDutyCycleBank2
     *
     * @param dataset The ECUxDataset instance
     * @param id The column ID to handle
     * @return Column if it's an injector duty cycle field, null otherwise
     */
    public static Column getInjectorDutyCycleColumn(ECUxDataset dataset, Comparable<?> id) {
        String idStr = id.toString();

        switch (idStr) {
            case "FuelInjectorDutyCycle": {
                // ti - injector on time
                final DoubleArray a = dataset.getCsvColumn("FuelInjectorOnTime").data.
                    div(60*1000);   /* assumes injector on time is in ms */
                final DoubleArray b = dataset.get("RPM").data.div(2); // 1/2 cycle
                return dataset.createColumn(id, UnitConstants.UNIT_PERCENT, a.mult(b).mult(100)); // convert to %
            }
            case "EffInjectorDutyCycle": {
                // te - effective injection time
                final DoubleArray a = dataset.getCsvColumn("EffInjectionTime").data.
                    div(60*1000);   /* assumes injector on time is in ms */
                final DoubleArray b = dataset.get("RPM").data.div(2); // 1/2 cycle
                return dataset.createColumn(id, UnitConstants.UNIT_PERCENT, a.mult(b).mult(100)); // convert to %
            }
            case "EffInjectorDutyCycleBank2": {
                // te - effective injection time (bank 2)
                final DoubleArray a = dataset.getCsvColumn("EffInjectionTimeBank2").data.
                    div(60*1000);   /* assumes injector on time is in ms */
                final DoubleArray b = dataset.get("RPM").data.div(2); // 1/2 cycle
                return dataset.createColumn(id, UnitConstants.UNIT_PERCENT, a.mult(b).mult(100)); // convert to %
            }
            default:
                return null; // Not an injector duty cycle field
        }
    }

    /**
     * Handle range-related calculations (TIME [Range], Sample [Range]).
     *
     * @param dataset The ECUxDataset instance
     * @param id The column ID to handle
     * @return Column if it's a range field, null otherwise
     */
    public static Column getRangeColumn(ECUxDataset dataset, Comparable<?> id) {
        String idStr = id.toString();

        switch (idStr) {
            case "TIME [Range]": {
                // Relative time to start of range (if filter enabled), otherwise just TIME
                if (!dataset.getFilter().enabled()) {
                    // Filter disabled: return base TIME data
                    final DoubleArray time = dataset.get("TIME").data;
                    return dataset.createColumn(id, UnitConstants.UNIT_SECONDS, time, ColumnType.PROCESSED_VARIANT);
                } else {
                    // Filter enabled: calculate relative time to range start
                    final DoubleArray time = dataset.get("TIME").data;
                    final ArrayList<Dataset.Range> ranges = dataset.getRanges();
                    final double[] result = new double[dataset.length()];
                    for (int i = 0; i < dataset.length(); i++) {
                        final Dataset.Range range = dataset.findRangeForIndex(ranges, i);
                        if (range != null) {
                            result[i] = time.get(i) - time.get(range.start);
                        } else {
                            // Not in any range: use NaN so point is excluded from plots
                            result[i] = Double.NaN;
                        }
                    }
                    // Use OTHER_RUNTIME because this column depends on filter ranges (runtime parameter).
                    // Unlike PROCESSED_VARIANT (processed/transformed native data), this column's values
                    // change when filter settings change, so it needs to be recalculated when ranges are rebuilt.
                    return dataset.createColumn(id, UnitConstants.UNIT_SECONDS, new DoubleArray(result), ColumnType.OTHER_RUNTIME);
                }
            }
            case "Sample [Range]": {
                // Relative sample to start of range (if filter enabled), otherwise just Sample
                if (!dataset.getFilter().enabled()) {
                    // Filter disabled: return base Sample data
                    final double[] idx = new double[dataset.length()];
                    for (int i = 0; i < dataset.length(); i++) {
                        idx[i] = i;
                    }
                    return dataset.createColumn(id, UnitConstants.UNIT_SAMPLE, new DoubleArray(idx), ColumnType.PROCESSED_VARIANT);
                } else {
                    // Filter enabled: calculate relative sample to range start
                    final ArrayList<Dataset.Range> ranges = dataset.getRanges();
                    final double[] result = new double[dataset.length()];
                    for (int i = 0; i < dataset.length(); i++) {
                        final Dataset.Range range = dataset.findRangeForIndex(ranges, i);
                        if (range != null) {
                            result[i] = i - range.start;
                        } else {
                            // Not in any range: use NaN so point is excluded from plots
                            result[i] = Double.NaN;
                        }
                    }
                    // Use OTHER_RUNTIME because this column depends on filter ranges (runtime parameter).
                    // Unlike PROCESSED_VARIANT (processed/transformed native data), this column's values
                    // change when filter settings change, so it needs to be recalculated when ranges are rebuilt.
                    return dataset.createColumn(id, UnitConstants.UNIT_SAMPLE, new DoubleArray(result), ColumnType.OTHER_RUNTIME);
                }
            }
            default:
                return null; // Not a range field
        }
    }

    /**
     * Handle boost pressure and Zeitronix-related calculations.
     *
     * Fields handled:
     * - BoostPressureDesired
     * - BoostDesired PR
     * - BoostActual PR
     * - Zeitronix Boost (base field; unit conversions like "Zeitronix Boost (PSI)" handled by generic handler)
     * - Sim BoostIATCorrection
     * - Sim BoostPressureDesired
     * - Boost Spool Rate (RPM)
     * - Boost Spool Rate Zeit (RPM)
     * - Boost Spool Rate (time)
     * - ps_w error
     * - Sim evtmod
     * - Sim ftbr
     * - LDR error
     * - LDR de/dt
     * - LDR I e dt
     * - LDR PID
     * - Sim pspvds
     *
     * Note: Zeitronix AFR (lambda) and Zeitronix Lambda (AFR) are handled before the switch statement
     * (formula conversions, not simple unit conversions)
     *
     * @param dataset The ECUxDataset instance
     * @param id The column ID to handle
     * @return Column if it's a boost/zeitronix field, null otherwise
     */
    public static Column getBoostZeitronixColumn(ECUxDataset dataset, Comparable<?> id) {
        String idStr = id.toString();

        // Handle lambda/AFR conversions (these need formula conversions, not just unit conversions)
        if (idStr.equals(ECUxDataset.idWithUnitHelper("Zeitronix AFR", UnitConstants.UNIT_LAMBDA))) {
            final DoubleArray abs = dataset.getCsvColumn("Zeitronix AFR").data;
            return dataset.createColumn(id, UnitConstants.UNIT_LAMBDA, abs.div(UnitConstants.STOICHIOMETRIC_AFR));
        } else if (idStr.equals(ECUxDataset.idWithUnitHelper("Zeitronix Lambda", UnitConstants.UNIT_AFR))) {
            final DoubleArray abs = dataset.getCsvColumn("Zeitronix Lambda").data;
            return dataset.createColumn(id, UnitConstants.UNIT_AFR, abs.mult(UnitConstants.STOICHIOMETRIC_AFR));
        }

        // Handle exact matches with switch/case
        switch (idStr) {
            case "BoostPressureDesired": {
                final Column delta = dataset.getCsvColumn("BoostPressureDesiredDelta");
                if (delta != null) {
                    final Column ecu = dataset.getCsvColumn("ECUBoostPressureDesired");
                    if (ecu != null) {
                        return dataset.createColumn(id, UnitConstants.UNIT_PSI, ecu.data.add(delta.data));
                    }
                }
                return null;
            }
            case "BoostDesired PR": {
                final Column act = dataset.getCsvColumn("BoostPressureDesired");
                try {
                    Column baroMbar = getBaroPressureMbar(dataset);
                    if (baroMbar != null) {
                        return dataset.createColumn(id, "PR", act.data.div(baroMbar.data));
                    }
                } catch (final Exception e) {
                    // Fallback to default
                }
                // Fallback: use standard atmospheric pressure
                if (act.getUnits().matches(UnitConstants.UNIT_PSI))
                    return dataset.createColumn(id, "PR", act.data.div(UnitConstants.STOICHIOMETRIC_AFR));
                else
                    return dataset.createColumn(id, "PR", act.data.div(UnitConstants.MBAR_PER_ATM));
            }
            case "BoostActual PR": {
                final Column act = dataset.getCsvColumn("BoostPressureActual");
                try {
                    Column baroMbar = getBaroPressureMbar(dataset);
                    if (baroMbar != null) {
                        return dataset.createColumn(id, "PR", act.data.div(baroMbar.data));
                    }
                } catch (final Exception e) {
                    // Fallback to default
                }
                // Fallback: use standard atmospheric pressure
                if (act.getUnits().matches(UnitConstants.UNIT_PSI))
                    return dataset.createColumn(id, "PR", act.data.div(UnitConstants.STOICHIOMETRIC_AFR));
                else
                    return dataset.createColumn(id, "PR", act.data.div(UnitConstants.MBAR_PER_ATM));
            }
            case "Zeitronix Boost": {
                // Get base column directly from map (no calculations) and convert units directly
                Column baseCol = dataset.getCsvColumn("Zeitronix Boost");
                if (baseCol == null) {
                    logger.warn("_get('Zeitronix Boost'): Base column not found in map");
                    return null;
                }
                // Convert to PSI using DatasetUnits (bypasses getColumnInUnits to avoid recursion)
                java.util.function.Supplier<Double> ambientSupplier = () -> {
                    Column baro = dataset.getCsvColumn("BaroPressure");
                    if (baro != null) {
                        return DatasetUnits.normalizeBaroToMbar(baro);
                    }
                    return null;
                };
                ColumnType colType = baseCol.getColumnType();
                if (colType == ColumnType.CSV_NATIVE) {
                    colType = ColumnType.COMPILE_TIME_CONSTANTS;
                }
                Column psiCol = DatasetUnits.convertUnits(dataset, baseCol, UnitConstants.UNIT_PSI, ambientSupplier, colType);
                final DoubleArray boost = psiCol.data;
                Column c = dataset.createColumn(id, UnitConstants.UNIT_MBAR, boost.mult(UnitConstants.MBAR_PER_PSI).add(UnitConstants.MBAR_PER_ATM));
                // Register smoothing on base field so unit conversions (e.g., "Zeitronix Boost (PSI)") can inherit it
                dataset.registerSmoothingWindow(idStr, dataset.getFilter().ZeitMAW());
                return c;
            }
            case "Sim BoostIATCorrection": {
                final DoubleArray ftbr = dataset.get("Sim ftbr").data;
                return dataset.createColumn(id, "", ftbr.inverse());
            }
            case "Sim BoostPressureDesired": {
                final boolean SY_BDE = false;
                final boolean SY_AGR = true;
                DoubleArray load;
                DoubleArray ps;

                try {
                    load = dataset.getCsvColumn("EngineLoadRequested").data; // rlsol
                } catch (final Exception e) {
                    load = dataset.getCsvColumn("EngineLoadCorrected").data; // rlmax
                }

                try {
                    ps = dataset.getCsvColumn("ME7L ps_w").data;
                } catch (final Exception e) {
                    ps = dataset.getCsvColumn("BoostPressureActual").data;
                }

                DoubleArray ambient = ps.ident(UnitConstants.MBAR_PER_ATM); // pu
                try {
                    Column baroMbar = getBaroPressureMbar(dataset);
                    if (baroMbar != null) {
                        ambient = baroMbar.data;
                    }
                } catch (final Exception e) { }

                DoubleArray fupsrl = load.ident(0.1037); // KFURL
                try {
                    final DoubleArray ftbr = dataset.get("Sim ftbr").data;
                    // fupsrl = KFURL * ftbr
                    fupsrl = fupsrl.mult(ftbr);
                } catch (final Exception e) {}

                // pirg = fho * KFPRG = (pu/UnitConstants.MBAR_PER_ATM) * 70
                final DoubleArray pirg = ambient.mult(70/UnitConstants.MBAR_PER_ATM);

                if (!SY_BDE) {
                    load = load.max(0);     // rlfgs
                    if (SY_AGR) {
                        // pbr = ps * fpbrkds
                        // rfges = (pbr-pirg).max(0)*fupsrl
                        final DoubleArray rfges = (ps.mult(1.106)).sub(pirg).max(0).mult(fupsrl);
                        // psagr = 250??
                        // rfagr = rfges * psagr/ps
                        // load = rlfgs + rfagr;
                        load = load.add(rfges.mult(250).div(ps));
                    }
                }

                DoubleArray boost = load.div(fupsrl);

                if (SY_BDE) {
                    boost = boost.add(pirg);
                }

                // fpbrkds from KFPBRK/KFPBRKNW
                boost = boost.div(1.016);   // pssol

                // vplsspls from KFVPDKSD/KFVPDKSDSE
                boost = boost.div(1.016);   // plsol

                return dataset.createColumn(id, UnitConstants.UNIT_MBAR, boost.max(ambient));
            }
            case "Boost Spool Rate (RPM)": {
                final DoubleArray abs = dataset.getCsvColumn("BoostPressureActual").data.smooth();
                final DoubleArray rpm = dataset.get("RPM").data;
                return dataset.createColumn(id, "mBar/RPM", abs.derivative(rpm).max(0));
            }
            case "Boost Spool Rate Zeit (RPM)": {
                final DoubleArray boost = dataset.get("Zeitronix Boost").data.smooth();
                final DoubleArray rpm = dataset.get("RPM").data;
                return dataset.createColumn(id, "mBar/RPM", boost.derivative(rpm).max(0));
            }
            case "Boost Spool Rate (time)": {
                // Get base column directly from map and convert units directly (avoid recursion)
                Column baseCol = dataset.getCsvColumn("BoostPressureActual");
                if (baseCol == null) {
                    logger.warn("_get('Boost Spool Rate (time)'): BoostPressureActual not found in map");
                    return null;
                }
                java.util.function.Supplier<Double> ambientSupplier = () -> {
                    Column baro = dataset.getCsvColumn("BaroPressure");
                    if (baro != null) {
                        return DatasetUnits.normalizeBaroToMbar(baro);
                    }
                    return null;
                };
                ColumnType colType = baseCol.getColumnType();
                if (colType == ColumnType.CSV_NATIVE) {
                    colType = ColumnType.COMPILE_TIME_CONSTANTS;
                }
                Column psiCol = DatasetUnits.convertUnits(dataset, baseCol, UnitConstants.UNIT_PSI, ambientSupplier, colType);
                final DoubleArray abs = psiCol.data.smooth();
                final DoubleArray time = dataset.get("TIME").data;
                final DoubleArray derivative = abs.derivative(time).max(0);
                Column c = dataset.createColumn(id, "PSI/sec", derivative);
                // Need to consider what "register for smoothing" means for non HP data
                dataset.registerSmoothingWindow(idStr, 1.0); // For now, fixed at 1 second
                return c;
            }
            case "ps_w error": {
                final DoubleArray abs = dataset.getCsvColumn("BoostPressureActual").data.max(900);
                final DoubleArray ps_w = dataset.getCsvColumn("ME7L ps_w").data.max(900);
                return dataset.createColumn(id, UnitConstants.UNIT_LAMBDA, ps_w.div(abs));
            }
            case "Sim evtmod": {
                // Get base column directly from map and convert units directly (avoid recursion)
                Column baseCol = dataset.getCsvColumn("IntakeAirTemperature");
                if (baseCol == null) {
                    logger.warn("_get('Sim evtmod'): IntakeAirTemperature not found in map");
                    return null;
                }
                Column celsiusCol = DatasetUnits.convertUnits(dataset, baseCol, UnitConstants.UNIT_CELSIUS, null, baseCol.getColumnType());
                final DoubleArray tans = celsiusCol.data;
                DoubleArray tmot = tans.ident(95);
                try {
                    tmot = dataset.get("CoolantTemperature").data;
                } catch (final Exception e) {}

                // KFFWTBR=0.02
                // evtmod = tans + (tmot-tans)*KFFWTBR
                final DoubleArray evtmod = tans.add((tmot.sub(tans)).mult(0.02));
                return dataset.createColumn(id, "\u00B0C", evtmod);
            }
            case "Sim ftbr": {
                // Get base column directly from map and convert units directly (avoid recursion)
                Column baseCol = dataset.getCsvColumn("IntakeAirTemperature");
                if (baseCol == null) {
                    logger.warn("_get('Sim ftbr'): IntakeAirTemperature not found in map");
                    return null;
                }
                Column celsiusCol = DatasetUnits.convertUnits(dataset, baseCol, UnitConstants.UNIT_CELSIUS, null, baseCol.getColumnType());
                final DoubleArray tans = celsiusCol.data;
                final DoubleArray evtmod = dataset.get("Sim evtmod").data;
                // linear fit to stock FWFTBRTA
                // fwtf = (tans+673.425)/731.334
                final DoubleArray fwft = tans.add(673.425).div(731.334);

                // ftbr = 273/(tans+273) * fwft
                // ftbr=273/(evtmod-273) * fwft
                return dataset.createColumn(id, "", evtmod.ident(273).div(evtmod.add(273)).mult(fwft));
            }

            case "LDR error": {
                final DoubleArray set = dataset.getCsvColumn("BoostPressureDesired").data;
                final DoubleArray out = dataset.getCsvColumn("BoostPressureActual").data;
                return dataset.createColumn(id, "100mBar", set.sub(out).div(100));
            }
            case "LDR de/dt": {
                final DoubleArray set = dataset.getCsvColumn("BoostPressureDesired").data;
                final DoubleArray out = dataset.getCsvColumn("BoostPressureActual").data;
                final DoubleArray t = dataset.get("TIME").data;
                final DoubleArray o = set.sub(out).derivative(t);
                Column c = dataset.createColumn(id,"100mBar",o.mult(dataset.getEnv().pid.time_constant).div(100));
                // Need to consider what "register for smoothing" means for non HP data
                dataset.registerSmoothingWindow(idStr, 1.0); // For now, fixed at 1 second
                return c;
            }
            case "LDR I e dt": {
                final DoubleArray set = dataset.getCsvColumn("BoostPressureDesired").data;
                final DoubleArray out = dataset.getCsvColumn("BoostPressureActual").data;
                final DoubleArray t = dataset.get("TIME").data;
                final DoubleArray o = set.sub(out).
                    integral(t,0,dataset.getEnv().pid.I_limit/dataset.getEnv().pid.I*100);
                return dataset.createColumn(id,"100mBar",o.div(dataset.getEnv().pid.time_constant).div(100));
            }
            case "LDR PID": {
                final DoubleArray.TransferFunction fP =
                    new DoubleArray.TransferFunction() {
                        @Override
                        public final double f(double x, double y) {
                            if(Math.abs(x)<dataset.getEnv().pid.P_deadband/100) return 0;
                            return x*dataset.getEnv().pid.P;
                        }
                };
                final DoubleArray.TransferFunction fD =
                    new DoubleArray.TransferFunction() {
                        @Override
                        public final double f(double x, double y) {
                            y=Math.abs(y);
                            if(y<3) return x*dataset.getEnv().pid.D[0];
                            if(y<5) return x*dataset.getEnv().pid.D[1];
                            if(y<7) return x*dataset.getEnv().pid.D[2];
                            return x*dataset.getEnv().pid.D[3];
                        }
                };
                final DoubleArray E = dataset.get("LDR error").data;
                final DoubleArray P = E.func(fP);
                final DoubleArray I = dataset.get("LDR I e dt").data.mult(dataset.getEnv().pid.I);
                final DoubleArray D = dataset.get("LDR de/dt").data.func(fD,E);
                return dataset.createColumn(id, "%", P.add(I).add(D).max(0).min(95));
            }
            case "Sim pspvds": {
                final DoubleArray ps_w = dataset.getCsvColumn("ME7L ps_w").data;
                final DoubleArray pvdkds = dataset.getCsvColumn("BoostPressureActual").data;
                return dataset.createColumn(id,"",ps_w.div(pvdkds));
            }
            default:
                return null; // Not a boost/zeitronix field
        }
    }

    /**
     * Handle ignition timing calculations.
     *
     * Fields handled:
     * - IgnitionTimingAngleOverall
     * - IgnitionTimingAngleOverallDesired
     *
     * @param dataset The ECUxDataset instance
     * @param id The column ID to handle
     * @return Column if it's an ignition timing field, null otherwise
     */
    public static Column getIgnitionTimingColumn(ECUxDataset dataset, Comparable<?> id) {
        String idStr = id.toString();

        switch (idStr) {
            case "IgnitionTimingAngleOverall": {
                // Calculate from per-cylinder timing angles if Overall not directly available
                // This supports loggers like JB4 that only log per-cylinder timing, not overall timing
                // Note: _get() already handles recursion protection - if calculated version exists, it returns early
                final Column overall = dataset.getCsvColumn("IgnitionTimingAngleOverall");
                if(overall != null && overall.getColumnType() == ColumnType.CSV_NATIVE) {
                    // Exists as CSV column, use it directly
                    return overall;
                } else {
                    // Calculate average of available per-cylinder timing angles
                    DoubleArray avetiming = null;
                    int count = 0;
                    for(int i=1; i<=8; i++) {
                        final Column timing = dataset.get("IgnitionTimingAngle" + i);
                        if(timing != null) {
                            if(avetiming == null) avetiming = timing.data;
                            else avetiming = avetiming.add(timing.data);
                            count++;
                        }
                    }
                    if(count > 0) {
                        return dataset.createColumn(id, "\u00B0", avetiming.div(count));
                    } else {
                        // Handler matched but cannot create column - must return null explicitly
                        return null;
                    }
                }
            }
            case "IgnitionTimingAngleOverallDesired": {
                DoubleArray averetard = null;
                int count=0;
                for(int i=0;i<8;i++) {
                    final Column retard = dataset.get("IgnitionRetardCyl" + i);
                    if(retard!=null) {
                        if(averetard==null) averetard = retard.data;
                        else averetard = averetard.add(retard.data);
                        count++;
                    }
                }
                // Fallback to AverageIgnitionRetard if no per-cylinder retard fields found
                if(count == 0) {
                    final Column avgRetard = dataset.get("AverageIgnitionRetard");
                    if(avgRetard != null) {
                        averetard = avgRetard.data;
                        count = 1; // Use count=1 to indicate we have average retard
                    }
                }
                DoubleArray out = dataset.get("IgnitionTimingAngleOverall").data;
                if(count>0) {
                    // assume retard is always positive... some loggers log it negative
                    // abs it to normalize
                    out = out.add(averetard.div(count).abs());
                }
                return dataset.createColumn(id, "\u00B0", out);
            }
            default:
                return null; // Not an ignition timing field
        }
    }

    /**
     * Handle engine torque and HP calculations.
     *
     * Fields handled:
     * - Engine torque (ft-lb)
     * - Engine HP
     *
     * @param dataset The ECUxDataset instance
     * @param id The column ID to handle
     * @return Column if it's an engine torque/HP field, null otherwise
     */
    public static Column getEngineTorqueHpColumn(ECUxDataset dataset, Comparable<?> id) {
        String idStr = id.toString();

        switch (idStr) {
            case "Engine torque (ft-lb)": {
                // if log contains Engine torque / converts TorqueDesired (Nm) to ft-lb and calculates HP
                // See MenuHandlerRegistry.REGISTRY["Engine torque (ft-lb)"], etc.
                final DoubleArray tq = dataset.get("TorqueDesired").data;
                final DoubleArray value = tq.mult(UnitConstants.NM_PER_FTLB);       // nm to ft-lb
                return dataset.createColumn(id, UnitConstants.UNIT_FTLB, value);
            }
            case "Engine HP": {
                final DoubleArray tq = dataset.get("Engine torque (ft-lb)").data;
                final DoubleArray rpm = dataset.get("RPM").data;
                final DoubleArray value = tq.div(UnitConstants.HP_CALCULATION_FACTOR).mult(rpm);
                return dataset.createColumn(id, UnitConstants.UNIT_HP, value);
            }
            default:
                return null; // Not an engine torque/HP field
        }
    }

    /**
     * Handle miscellaneous Sim calculations.
     *
     * Fields handled:
     * - Sim LoadSpecified correction
     *
     * Note: Most "Sim *" fields are handled by other specialized handlers:
     * - Sim Load, Sim Load Corrected, Sim MAF, Sim Fuel Mass -> getMafFuelColumn()
     * - Sim AFR, Sim lambda, Sim lambda error -> getAfrColumn()
     * - Sim evtmod, Sim ftbr, Sim BoostIATCorrection, Sim BoostPressureDesired, Sim pspvds -> getBoostZeitronixColumn()
     *
     * @param dataset The ECUxDataset instance
     * @param id The column ID to handle
     * @return Column if it's a miscellaneous Sim field, null otherwise
     */
    public static Column getMiscSimColumn(ECUxDataset dataset, Comparable<?> id) {
        String idStr = id.toString();

        switch (idStr) {
            case "Sim LoadSpecified correction": {
                final DoubleArray cs = dataset.getCsvColumn("EngineLoadCorrected").data;
                final DoubleArray s = dataset.getCsvColumn("EngineLoadSpecified").data;
                return dataset.createColumn(id, "K", cs.div(s));
            }
            default:
                return null; // Not a miscellaneous Sim field
        }
    }

    /**
     * Calculate aerodynamic drag power.
     * Helper function moved from ECUxDataset to AxisMenuHandlers.
     *
     * @param dataset The ECUxDataset instance
     * @param v Velocity array
     * @return Drag power array
     */
    private static DoubleArray drag(ECUxDataset dataset, DoubleArray v) {
        final DoubleArray windDrag = v.pow(3).mult(0.5 * UnitConstants.AIR_DENSITY_STANDARD * dataset.getEnv().c.Cd() *
            dataset.getEnv().c.FA());

        final DoubleArray rollingDrag = v.mult(dataset.getEnv().c.rolling_drag() *
            dataset.getEnv().c.mass() * UnitConstants.STANDARD_GRAVITY);

        return windDrag.add(rollingDrag);
    }

    /**
     * Handle power and torque calculations (WHP, HP, WTQ, TQ, Drag, and unit conversions).
     * Consolidates all power/torque related handlers into a single function.
     *
     * @param dataset The ECUxDataset instance
     * @param id The column ID to handle
     * @return Column if it's a power/torque field, null otherwise
     */
    public static Column getPowerTorqueColumn(ECUxDataset dataset, Comparable<?> id) {
        String idStr = id.toString();

        switch (idStr) {
            case "WHP": {
                // Uses: mass, Cd, FA, rolling_drag (via drag()), rpm_per_mph (via Calc Velocity)
                // Depends on: Acceleration (m/s^2) [smoothed with AccelMAW()], Calc Velocity [from smoothed RPM]
                // Smoothing: Applied in getData() using HPMAW() window
                Column accelCol = dataset.get("Acceleration (m/s^2)");
                Column velocityCol = dataset.get("Calc Velocity");
                if (accelCol == null || velocityCol == null) {
                    logger.warn("_get('WHP'): Missing dependencies - Acceleration (m/s^2)={}, Calc Velocity={}",
                        accelCol != null, velocityCol != null);
                    return null;
                }
                final DoubleArray a = accelCol.data;
                final DoubleArray v = velocityCol.data;
                final DoubleArray whp = a.mult(v).mult(dataset.getEnv().c.mass()).
                    add(drag(dataset, v));      // in watts

                DoubleArray value = whp.mult(1.0 / UnitConstants.HP_PER_WATT);
                String l = UnitConstants.UNIT_HP;
                if(dataset.getEnv().sae.enabled()) {
                    value = value.mult(dataset.getEnv().sae.correction());
                    l += " (SAE)";
                }
                // Store unsmoothed data and record smoothing requirement
                // Smoothing will be applied in getData() using MAW() window
                Column c = dataset.createColumn(id, l, value, ColumnType.VEHICLE_CONSTANTS);
                dataset.registerSmoothingWindow(idStr, dataset.getFilter().HPMAW());
                return c;
            }
            case "HP": {
                // Uses: driveline_loss, static_loss, plus all WHP dependencies
                // Depends on: WHP (raw, unsmoothed data)
                // Calculate HP directly from raw WHP using driveline loss formula.
                // HP = WHP / (1 - driveline_loss) + static_loss
                Column whpCol = dataset.get("WHP");
                if (whpCol == null) {
                    logger.warn("_get('HP'): Missing dependency - WHP");
                    return null;
                }
                // Calculate HP from raw WHP (no smoothing applied here)
                final DoubleArray value = whpCol.data.div((1-dataset.getEnv().c.driveline_loss())).
                        add(dataset.getEnv().c.static_loss());
                String l = UnitConstants.UNIT_HP;
                if(dataset.getEnv().sae.enabled()) l += " (SAE)";
                // Register for range-aware smoothing in getData() (same as WHP)
                Column c = dataset.createColumn(id, l, value, ColumnType.VEHICLE_CONSTANTS);
                dataset.registerSmoothingWindow(idStr, dataset.getFilter().HPMAW());
                return c;
            }
            case "WTQ": {
                // Depends on WHP (which uses all WHP constants)
                // Calculate WTQ from raw WHP (no smoothing applied here)
                // Smoothing will be applied in getData() via range-aware smoothing
                return calculateTorque(dataset, "WTQ", "WHP");
            }
            case "TQ": {
                // TQ - Engine Torque (calculated from HP)
                // Depends on HP (which uses all HP/WHP constants)
                // Calculate TQ from raw HP (no smoothing applied here)
                // Smoothing will be applied in getData() via range-aware smoothing
                return calculateTorque(dataset, "TQ", "HP");
            }
            case "Drag": {
                // Drag - Aerodynamic drag power
                // Uses: Cd, FA, rolling_drag, mass (via drag()), rpm_per_mph (via Calc Velocity)
                final DoubleArray v = dataset.get("Calc Velocity").data;
                final DoubleArray dragPower = drag(dataset, v);
                return dataset.createColumn(id, "HP", dragPower.mult(1.0 / UnitConstants.HP_PER_WATT), ColumnType.VEHICLE_CONSTANTS);
            }
            default: {
                // Check for unit conversions (WTQ (Nm), TQ (Nm))
                String wtqNm = ECUxDataset.idWithUnitHelper("WTQ", UnitConstants.UNIT_NM);
                String tqNm = ECUxDataset.idWithUnitHelper("TQ", UnitConstants.UNIT_NM);
                if (idStr.equals(wtqNm)) {
                    // Depends on WTQ (which uses WHP constants)
                    // Note: This handler is typically bypassed by the generic unit conversion handler,
                    // which automatically inherits smoothing registration from the base "WTQ" column
                    final DoubleArray wtq = dataset.get("WTQ").data;
                    final DoubleArray value = wtq.mult(UnitConstants.NM_PER_FTLB); // ft-lb to Nm
                    String l = UnitConstants.UNIT_NM;
                    if(dataset.getEnv().sae.enabled()) l += " (SAE)";
                    Column c = dataset.createColumn(id, l, value, ColumnType.VEHICLE_CONSTANTS);
                    // Smoothing registration inherited automatically via getColumnInUnits() when generic handler runs
                    return c;
                } else if (idStr.equals(tqNm)) {
                    // TQ (Nm) - Unit conversion
                    // Depends on TQ (which uses all HP/WHP constants)
                    // Note: This handler is typically bypassed by the generic unit conversion handler,
                    // which automatically inherits smoothing registration from the base "TQ" column
                    final DoubleArray tq = dataset.get("TQ").data;
                    final DoubleArray value = tq.mult(UnitConstants.NM_PER_FTLB); // ft-lb to Nm
                    String l = UnitConstants.UNIT_NM;
                    if(dataset.getEnv().sae.enabled()) l += " (SAE)";
                    Column c = dataset.createColumn(id, l, value, ColumnType.VEHICLE_CONSTANTS);
                    // Smoothing registration inherited automatically via getColumnInUnits() when generic handler runs
                    return c;
                }
                // Not a power/torque field
                return null;
            }
        }
    }

    /**
     * Calculate torque from smoothed power data.
     * Creates a column with torque calculated from smoothed power (HP or WHP) and RPM.
     *
     * @param dataset The ECUxDataset instance
     * @param torqueId The ID for the torque column (e.g., "TQ" or "WTQ")
     * @param powerColumnName The name of the power column (e.g., "HP" or "WHP")
     * @return Column with calculated torque, or null if power/RPM data is unavailable
     */
    private static Column calculateTorque(ECUxDataset dataset, String torqueId, String powerColumnName) {
        // Calculate torque from raw power data (no smoothing applied here)
        // Smoothing will be applied in getData() if needed
        Column powerCol = dataset.get(powerColumnName);
        Column rpmCol = dataset.get("RPM");
        if (powerCol == null || rpmCol == null) {
            logger.error("_get('{}'): Failed to get {} or RPM column - {}={}, RPM={}",
                torqueId, powerColumnName, powerColumnName, powerCol != null, rpmCol != null);
            return null;
        }
        final DoubleArray power = powerCol.data;
        final DoubleArray rpm = rpmCol.data;
        if (power.size() != rpm.size()) {
            logger.error("_get('{}'): Power and RPM data length mismatch - {}={}, RPM={}",
                torqueId, powerColumnName, power.size(), rpm.size());
            return null;
        }
        // Calculate torque from raw power data: TQ = HP * HP_CALCULATION_FACTOR / RPM
        final DoubleArray torque = power.mult(UnitConstants.HP_CALCULATION_FACTOR).div(rpm);
        String label = UnitConstants.UNIT_FTLB;
        if(dataset.getEnv().sae.enabled()) label += " (SAE)";
        // Register for range-aware smoothing in getData() (same as power columns)
        Column c = dataset.createColumn(torqueId, label, torque, ColumnType.VEHICLE_CONSTANTS);
        dataset.registerSmoothingWindow(torqueId, dataset.getFilter().HPMAW());
        return c;
    }

    /**
     * Handle smoothing diagnostic columns (debug-only, not in AxisMenu).
     * These columns are time derivatives and sample differences for debugging smoothing behavior.
     *
     * Fields handled:
     * - dRPM/dt - raw, dRPM/dt - base, dRPM/dt
     * - dVelocity/dt, dAccel/dt, dWHP/dt, dHP/dt
     * - Δ RPM - raw, Δ RPM - base, Δ RPM
     * - Δ Velocity, Δ Acceleration, Δ WHP, Δ HP
     *
     * @param dataset The ECUxDataset instance
     * @param id The column ID to handle
     * @return Column if it's a diagnostic field, null otherwise
     */
    public static Column getSmoothingDiagnosticColumn(ECUxDataset dataset, Comparable<?> id) {
        String idStr = id.toString();

        switch (idStr) {
            case "dRPM/dt - raw": {
                // Time derivative of CSV RPM
                final DoubleArray y = (dataset.getCsvRpm() != null) ? dataset.getCsvRpm().data : null;
                if (y == null) {
                    logger.warn("_get('{}'): csvRpm is null, cannot calculate derivative", id);
                    return null;
                }
                final DoubleArray x = dataset.get("TIME").data;
                final DoubleArray derivative = y.derivative(x);
                return dataset.createColumn(id, UnitConstants.UNIT_RPS, derivative, ColumnType.PROCESSED_VARIANT);
            }
            case "dRPM/dt - base": {
                // Time derivative of Base RPM
                final DoubleArray y = (dataset.getBaseRpm() != null) ? dataset.getBaseRpm().data : null;
                if (y == null) {
                    logger.warn("_get('{}'): baseRpm is null, cannot calculate derivative", id);
                    return null;
                }
                final DoubleArray x = dataset.get("TIME").data;
                final DoubleArray derivative = y.derivative(x);
                return dataset.createColumn(id, UnitConstants.UNIT_RPS, derivative, ColumnType.PROCESSED_VARIANT);
            }
            case "dRPM/dt": {
                // Time derivative of Final RPM
                final DoubleArray y = dataset.get("RPM").data;
                final DoubleArray x = dataset.get("TIME").data;
                final DoubleArray derivative = y.derivative(x);
                return dataset.createColumn(id, UnitConstants.UNIT_RPS, derivative, ColumnType.PROCESSED_VARIANT);
            }
            case "dVelocity/dt": {
                // Time derivative of Calc Velocity
                final DoubleArray y = dataset.get("Calc Velocity").data;
                final DoubleArray x = dataset.get("TIME").data;
                final DoubleArray derivative = y.derivative(x);
                return dataset.createColumn(id, "m/s^2", derivative, ColumnType.VEHICLE_CONSTANTS);
            }
            case "dAccel/dt": {
                // Time derivative of Acceleration (m/s²) - this is "jerk"
                final DoubleArray y = dataset.get("Acceleration (m/s^2)").data;
                final DoubleArray x = dataset.get("TIME").data;
                final DoubleArray derivative = y.derivative(x);
                return dataset.createColumn(id, "m/s^3", derivative, ColumnType.VEHICLE_CONSTANTS);
            }
            case "dWHP/dt": {
                // Time derivative of WHP
                final DoubleArray y = dataset.get("WHP").data;
                final DoubleArray x = dataset.get("TIME").data;
                final DoubleArray derivative = y.derivative(x);
                return dataset.createColumn(id, "HP/s", derivative, ColumnType.VEHICLE_CONSTANTS);
            }
            case "dHP/dt": {
                // Time derivative of HP
                final DoubleArray y = dataset.get("HP").data;
                final DoubleArray x = dataset.get("TIME").data;
                final DoubleArray derivative = y.derivative(x);
                return dataset.createColumn(id, "HP/s", derivative, ColumnType.VEHICLE_CONSTANTS);
            }
            case "Δ RPM - raw": {
                // Sample difference of CSV RPM
                final DoubleArray y = (dataset.getCsvRpm() != null) ? dataset.getCsvRpm().data : null;
                if (y == null) {
                    logger.warn("_get('{}'): csvRpm is null, cannot calculate difference", id);
                    return null;
                }
                final DoubleArray difference = y.difference();
                return dataset.createColumn(id, UnitConstants.UNIT_RPM, difference, ColumnType.PROCESSED_VARIANT);
            }
            case "Δ RPM - base": {
                // Sample difference of Base RPM
                final DoubleArray y = (dataset.getBaseRpm() != null) ? dataset.getBaseRpm().data : null;
                if (y == null) {
                    logger.warn("_get('{}'): baseRpm is null, cannot calculate difference", id);
                    return null;
                }
                final DoubleArray difference = y.difference();
                return dataset.createColumn(id, UnitConstants.UNIT_RPM, difference, ColumnType.PROCESSED_VARIANT);
            }
            case "Δ RPM": {
                // Sample difference of Final RPM
                final DoubleArray y = dataset.get("RPM").data;
                final DoubleArray difference = y.difference();
                return dataset.createColumn(id, UnitConstants.UNIT_RPM, difference, ColumnType.PROCESSED_VARIANT);
            }
            case "Δ Velocity": {
                // Sample difference of Calc Velocity
                final DoubleArray y = dataset.get("Calc Velocity").data;
                final DoubleArray difference = y.difference();
                return dataset.createColumn(id, UnitConstants.UNIT_MPS, difference, ColumnType.VEHICLE_CONSTANTS);
            }
            case "Δ Acceleration": {
                // Sample difference of Acceleration (m/s²)
                final DoubleArray y = dataset.get("Acceleration (m/s^2)").data;
                final DoubleArray difference = y.difference();
                return dataset.createColumn(id, "m/s^2", difference, ColumnType.VEHICLE_CONSTANTS);
            }
            case "Δ WHP": {
                // Sample difference of WHP - use raw (unsmoothed) WHP data
                // This shows the raw difference between samples, not smoothed differences
                final DoubleArray y = dataset.get("WHP").data;
                final DoubleArray difference = y.difference();
                return dataset.createColumn(id, UnitConstants.UNIT_HP, difference, ColumnType.VEHICLE_CONSTANTS);
            }
            case "Δ HP": {
                // Sample difference of HP
                // HP is already calculated from smoothed WHP, so this difference is consistent
                final DoubleArray y = dataset.get("HP").data;
                final DoubleArray difference = y.difference();
                return dataset.createColumn(id, UnitConstants.UNIT_HP, difference, ColumnType.VEHICLE_CONSTANTS);
            }
            default:
                // Not a diagnostic column
                return null;
        }
    }
}
