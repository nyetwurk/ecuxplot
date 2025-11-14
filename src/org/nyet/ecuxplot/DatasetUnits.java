package org.nyet.ecuxplot;

import org.nyet.logfile.Dataset;
import org.nyet.util.DoubleArray;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Dataset unit conversion utilities.
 * Handles conversion of Column data between different units.
 */
public class DatasetUnits {

    /**
     * Converter function interface for unit conversions
     */
    @FunctionalInterface
    private interface UnitConverter {
        ConversionResult convert(DoubleArray data, Supplier<Double> getAmbientPressure);
    }

    /**
     * Result of a unit conversion
     */
    private static class ConversionResult {
        final DoubleArray data;
        final String newUnit;
        ConversionResult(DoubleArray data, String newUnit) {
            this.data = data;
            this.newUnit = newUnit;
        }
    }

    /**
     * Registry of unit converters, keyed by "targetUnit:baseUnit"
     */
    private static final Map<String, UnitConverter> CONVERTERS = new HashMap<>();

    static {
        // Air-Fuel Ratio: lambda <-> AFR
        CONVERTERS.put(key(UnitConstants.UNIT_AFR, UnitConstants.UNIT_LAMBDA),
            (data, ambient) -> new ConversionResult(
                data.mult(UnitConstants.STOICHIOMETRIC_AFR), UnitConstants.UNIT_AFR));
        CONVERTERS.put(key(UnitConstants.UNIT_LAMBDA, UnitConstants.UNIT_AFR),
            (data, ambient) -> new ConversionResult(
                data.mult(UnitConstants.LAMBDA_PER_AFR), UnitConstants.UNIT_LAMBDA));

        // Temperature, Pressure (PSI<->mBar), Speed, and Torque conversions
        // are now handled by ConvertibleUnit enum (see convertUnits() method)
        // Only keeping conversions not handled by ConvertibleUnit here

        // Pressure: kPa <-> mBar
        // Note: Both kPa and mBar are absolute pressure units (1 kPa = 10 mBar)
        CONVERTERS.put(key(UnitConstants.UNIT_MBAR, UnitConstants.UNIT_KPA),
            (data, ambient) -> new ConversionResult(
                data.mult(UnitConstants.MBAR_PER_KPA), UnitConstants.UNIT_MBAR));
        CONVERTERS.put(key(UnitConstants.UNIT_KPA, UnitConstants.UNIT_MBAR),
            (data, ambient) -> new ConversionResult(
                data.mult(UnitConstants.KPA_PER_MBAR), UnitConstants.UNIT_KPA));

        // Pressure: kPa <-> PSI
        // Note: These conversions are handled by chaining through mBar:
        //   kPa -> mBar (simple conversion above) -> PSI (via ConvertibleUnit.PRESSURE_BOOST)
        //   PSI -> mBar (via ConvertibleUnit.PRESSURE_BOOST) -> kPa (simple conversion above)
        // The convertUnits() method explicitly handles this chaining for kPa↔PSI conversions

        // Speed conversions are now handled by ConvertibleUnit.SPEED

        // Mass flow: g/sec <-> kg/hr
        CONVERTERS.put(key(UnitConstants.UNIT_KGH, UnitConstants.UNIT_GPS),
            (data, ambient) -> new ConversionResult(
                data.mult(UnitConstants.KGH_PER_GPS), UnitConstants.UNIT_KGH));
        CONVERTERS.put(key(UnitConstants.UNIT_GPS, UnitConstants.UNIT_KGH),
            (data, ambient) -> new ConversionResult(
                data.mult(UnitConstants.GPS_PER_KGH), UnitConstants.UNIT_GPS));

        // Torque conversions are now handled by ConvertibleUnit.TORQUE
    }

    /**
     * Create a lookup key for unit conversions
     */
    private static String key(String targetUnit, String baseUnit) {
        return targetUnit + ":" + baseUnit;
    }

    /**
     * Convert a column to a target unit
     * @param dataset The dataset instance (needed to create new Column instances, since Column is an inner class)
     * @param baseColumn The column to convert
     * @param targetUnit The target unit (from UnitConstants)
     * @param getAmbientPressure Function to get ambient pressure in mBar (for PSI conversions), or null to use standard
     * @param columnType The type to assign to the converted column (inherits from base column type)
     * @return New Column with converted data, or original column if no conversion needed
     */
    public static Dataset.Column convertUnits(Dataset dataset, Dataset.Column baseColumn, String targetUnit,
            Supplier<Double> getAmbientPressure, Dataset.ColumnType columnType) {
        return convertUnits(dataset, baseColumn, targetUnit, getAmbientPressure, columnType, null);
    }

    /**
     * Create a new column with converted data.
     * Helper method to avoid repeating column creation logic.
     */
    private static Dataset.Column createConvertedColumn(Dataset dataset, Dataset.Column baseColumn,
            String targetUnit, DoubleArray convertedData, Dataset.ColumnType columnType, String targetId) {
        String columnId = (targetId != null && !targetId.isEmpty()) ? targetId : baseColumn.getId();
        // Preserve id2 (original field name) from base column when creating converted column
        String id2 = baseColumn.getId2();
        return dataset.new Column(columnId, id2, targetUnit, convertedData, columnType);
    }

    /**
     * Check if a chained conversion succeeded.
     * Returns true if the intermediate column was created and has the expected unit.
     */
    private static boolean isChainedConversionValid(Dataset.Column intermediateColumn, Dataset.Column baseColumn, String expectedUnit) {
        return intermediateColumn != baseColumn && intermediateColumn.getUnits().equals(expectedUnit);
    }

    /**
     * Attempt to chain a conversion through an intermediate unit.
     * Returns the final converted column if successful, or null if chaining failed.
     */
    private static Dataset.Column tryChainedConversion(Dataset dataset, Dataset.Column baseColumn,
            String intermediateUnit, String targetUnit, Supplier<Double> getAmbientPressure,
            Dataset.ColumnType columnType, String targetId) {
        Dataset.Column intermediateColumn = convertUnits(dataset, baseColumn, intermediateUnit,
            getAmbientPressure, columnType, null);
        if (isChainedConversionValid(intermediateColumn, baseColumn, intermediateUnit)) {
            return convertUnits(dataset, intermediateColumn, targetUnit, getAmbientPressure, columnType, targetId);
        }
        return null;
    }

    public static Dataset.Column convertUnits(Dataset dataset, Dataset.Column baseColumn, String targetUnit,
            Supplier<Double> getAmbientPressure, Dataset.ColumnType columnType, String targetId) {
        // Use native unit (u2) for conversion logic, not display unit (getUnits())
        String baseUnit = baseColumn.getNativeUnits();

        // Early return if no conversion needed
        if (targetUnit == null || targetUnit.isEmpty() || targetUnit.equals(baseUnit)) {
            return baseColumn;
        }

        // First, try to use ConvertibleUnit enum for conversions it supports
        ConvertibleUnit convertibleUnit = ConvertibleUnit.fromUnit(baseUnit);
        if (convertibleUnit != null) {
            // Check if target unit matches this convertible unit
            if ((baseUnit.equals(convertibleUnit.usCustomary) && targetUnit.equals(convertibleUnit.metric)) ||
                (baseUnit.equals(convertibleUnit.metric) && targetUnit.equals(convertibleUnit.usCustomary))) {
                // This is a ConvertibleUnit conversion
                DoubleArray convertedData = convertibleUnit.convertTo(baseColumn.data, baseUnit, targetUnit, getAmbientPressure);
                if (convertedData != baseColumn.data) {
                    return createConvertedColumn(dataset, baseColumn, targetUnit, convertedData, columnType, targetId);
                }
            }
        }

        // Fall back to CONVERTERS map for conversions not handled by ConvertibleUnit
        // (e.g., Air-Fuel Ratio, kPa conversions, mass flow)
        String lookupKey = key(targetUnit, baseUnit);
        UnitConverter converter = CONVERTERS.get(lookupKey);

        if (converter != null) {
            ConversionResult result = converter.convert(baseColumn.data, getAmbientPressure);
            // If data was modified, create new column
            if (result.data != baseColumn.data) {
                return createConvertedColumn(dataset, baseColumn, result.newUnit, result.data, columnType, targetId);
            }
        }

        // Try chaining conversions through intermediate units
        // Example: kPa -> PSI via mBar (kPa -> mBar -> PSI)
        if (baseUnit.equals(UnitConstants.UNIT_KPA) && targetUnit.equals(UnitConstants.UNIT_PSI)) {
            Dataset.Column result = tryChainedConversion(dataset, baseColumn, UnitConstants.UNIT_MBAR,
                UnitConstants.UNIT_PSI, getAmbientPressure, columnType, targetId);
            if (result != null) {
                return result;
            }
        } else if (baseUnit.equals(UnitConstants.UNIT_PSI) && targetUnit.equals(UnitConstants.UNIT_KPA)) {
            Dataset.Column result = tryChainedConversion(dataset, baseColumn, UnitConstants.UNIT_MBAR,
                UnitConstants.UNIT_KPA, getAmbientPressure, columnType, targetId);
            if (result != null) {
                return result;
            }
        }

        // No conversion matched, return base column as-is
        return baseColumn;
    }

    /**
     * Normalize barometric pressure column to mBar.
     * Handles conversion from kPa or PSI to mBar.
     *
     * @param baro Barometric pressure column
     * @return Pressure in mBar, or UnitConstants.MBAR_PER_ATM if column is null/empty
     */
    static double normalizeBaroToMbar(Dataset.Column baro) {
        if (baro == null || baro.data == null || baro.data.size() == 0) {
            return UnitConstants.MBAR_PER_ATM;
        }
        double ambient = baro.data.get(0);
        // Use native unit (u2) for conversion logic
        String baroUnit = baro.getNativeUnits();
        if (baroUnit != null && baroUnit.equals(UnitConstants.UNIT_KPA)) {
            return ambient * UnitConstants.MBAR_PER_KPA;
        } else if (baroUnit != null && baroUnit.equals(UnitConstants.UNIT_PSI)) {
            // PSI gauge to mBar absolute (BaroPressure should never be PSI, but handle it)
            return ambient * UnitConstants.MBAR_PER_PSI + UnitConstants.MBAR_PER_ATM;
        }
        // If mBar or null, use as-is
        return ambient;
    }

}

// vim: set sw=4 ts=8 expandtab:

