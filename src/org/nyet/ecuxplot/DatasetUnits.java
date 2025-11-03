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

        // Temperature: Celsius <-> Fahrenheit
        CONVERTERS.put(key(UnitConstants.UNIT_FAHRENHEIT, UnitConstants.UNIT_CELSIUS),
            (data, ambient) -> new ConversionResult(
                toFahrenheit(data), UnitConstants.UNIT_FAHRENHEIT));
        CONVERTERS.put(key(UnitConstants.UNIT_CELSIUS, UnitConstants.UNIT_FAHRENHEIT),
            (data, ambient) -> new ConversionResult(
                toCelcius(data), UnitConstants.UNIT_CELSIUS));

        // Pressure: mBar <-> PSI
        // Note: PSI is typically gauge pressure, mBar is absolute pressure
        CONVERTERS.put(key(UnitConstants.UNIT_PSI, UnitConstants.UNIT_MBAR),
            (data, ambient) -> new ConversionResult(
                toPSI(data, ambient), UnitConstants.UNIT_PSI));
        CONVERTERS.put(key(UnitConstants.UNIT_MBAR, UnitConstants.UNIT_PSI),
            (data, ambient) -> {
                double a = getAmbientPressureMbar(ambient);
                return new ConversionResult(
                    data.mult(UnitConstants.MBAR_PER_PSI).add(a), UnitConstants.UNIT_MBAR);
            });

        // Pressure: mBar gauge <-> PSI
        // Note: mBar gauge is already relative pressure, convert units directly
        CONVERTERS.put(key(UnitConstants.UNIT_PSI, UnitConstants.UNIT_MBAR_GAUGE),
            (data, ambient) -> new ConversionResult(
                data.div(UnitConstants.MBAR_PER_PSI), UnitConstants.UNIT_PSI));
        CONVERTERS.put(key(UnitConstants.UNIT_MBAR_GAUGE, UnitConstants.UNIT_PSI),
            (data, ambient) -> new ConversionResult(
                data.mult(UnitConstants.MBAR_PER_PSI), UnitConstants.UNIT_MBAR_GAUGE));

        // Pressure: kPa <-> mBar
        // Note: Both kPa and mBar are absolute pressure units (1 kPa = 10 mBar)
        CONVERTERS.put(key(UnitConstants.UNIT_MBAR, UnitConstants.UNIT_KPA),
            (data, ambient) -> new ConversionResult(
                data.mult(UnitConstants.MBAR_PER_KPA), UnitConstants.UNIT_MBAR));
        CONVERTERS.put(key(UnitConstants.UNIT_KPA, UnitConstants.UNIT_MBAR),
            (data, ambient) -> new ConversionResult(
                data.mult(UnitConstants.KPA_PER_MBAR), UnitConstants.UNIT_KPA));

        // Pressure: kPa <-> PSI
        // Note: PSI is typically gauge pressure, kPa is absolute pressure
        CONVERTERS.put(key(UnitConstants.UNIT_PSI, UnitConstants.UNIT_KPA),
            (data, ambient) -> {
                // kPa absolute to PSI gauge: convert kPa -> mBar -> PSI
                // Use actual ambient pressure for accuracy (especially at different altitudes)
                double a = getAmbientPressureMbar(ambient);
                return new ConversionResult(
                    data.mult(UnitConstants.MBAR_PER_KPA).sub(a).mult(UnitConstants.PSI_PER_MBAR),
                    UnitConstants.UNIT_PSI);
            });
        CONVERTERS.put(key(UnitConstants.UNIT_KPA, UnitConstants.UNIT_PSI),
            (data, ambient) -> {
                // PSI gauge to kPa absolute: convert PSI -> mBar -> kPa
                double a = getAmbientPressureMbar(ambient);
                return new ConversionResult(
                    data.mult(UnitConstants.MBAR_PER_PSI).add(a).mult(UnitConstants.KPA_PER_MBAR),
                    UnitConstants.UNIT_KPA);
            });

        // Speed: mph <-> km/h
        CONVERTERS.put(key(UnitConstants.UNIT_MPH, UnitConstants.UNIT_KMH),
            (data, ambient) -> new ConversionResult(
                data.mult(UnitConstants.MPH_PER_KPH), UnitConstants.UNIT_MPH));
        CONVERTERS.put(key(UnitConstants.UNIT_KMH, UnitConstants.UNIT_MPH),
            (data, ambient) -> new ConversionResult(
                data.mult(UnitConstants.KMH_PER_MPH), UnitConstants.UNIT_KMH));

        // Mass flow: g/sec <-> kg/hr
        CONVERTERS.put(key(UnitConstants.UNIT_KGH, UnitConstants.UNIT_GPS),
            (data, ambient) -> new ConversionResult(
                data.mult(UnitConstants.KGH_PER_GPS), UnitConstants.UNIT_KGH));
        CONVERTERS.put(key(UnitConstants.UNIT_GPS, UnitConstants.UNIT_KGH),
            (data, ambient) -> new ConversionResult(
                data.mult(UnitConstants.GPS_PER_KGH), UnitConstants.UNIT_GPS));

        // Torque: ft-lb <-> Nm
        CONVERTERS.put(key(UnitConstants.UNIT_NM, UnitConstants.UNIT_FTLB),
            (data, ambient) -> new ConversionResult(
                data.mult(UnitConstants.FTLB_PER_NM), UnitConstants.UNIT_NM));
        CONVERTERS.put(key(UnitConstants.UNIT_FTLB, UnitConstants.UNIT_NM),
            (data, ambient) -> new ConversionResult(
                data.mult(UnitConstants.NM_PER_FTLB), UnitConstants.UNIT_FTLB));
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

    public static Dataset.Column convertUnits(Dataset dataset, Dataset.Column baseColumn, String targetUnit,
            Supplier<Double> getAmbientPressure, Dataset.ColumnType columnType, String targetId) {
        String baseUnit = baseColumn.getUnits();

        // Early return if no conversion needed
        if (targetUnit == null || targetUnit.isEmpty() || targetUnit.equals(baseUnit)) {
            return baseColumn;
        }

        // Lookup converter
        String lookupKey = key(targetUnit, baseUnit);
        UnitConverter converter = CONVERTERS.get(lookupKey);

        if (converter != null) {
            ConversionResult result = converter.convert(baseColumn.data, getAmbientPressure);
            // If data was modified, create new column
            if (result.data != baseColumn.data) {
                // Use targetId if provided (e.g., "BoostPressure (PSI)"), otherwise use base column ID
                String columnId = (targetId != null && !targetId.isEmpty()) ? targetId : baseColumn.getId();
                return dataset.new Column(columnId, result.newUnit, result.data, columnType);
            }
        }

        // No conversion matched, return base column as-is
        return baseColumn;
    }

    /**
     * Get ambient pressure in mBar, normalizing from whatever unit it's stored in
     * @param getAmbientPressure Function to get ambient pressure, or null
     * @param dataset Dataset to get BaroPressure from if function is null
     * @return Ambient pressure in mBar
     */
    public static double getAmbientPressureMbar(java.util.function.Supplier<Double> getAmbientPressure,
            Dataset dataset) {
        if (getAmbientPressure != null) {
            Double ambient = getAmbientPressure.get();
            if (ambient != null) {
                return ambient;
            }
        }

        // Fallback: try to get from dataset
        if (dataset != null) {
            try {
                Dataset.Column baro = dataset.get("BaroPressure");
                if (baro != null && baro.data != null && baro.data.size() > 0) {
                    double ambient = baro.data.get(0);
                    // Normalize to mBar if needed
                    String baroUnit = baro.getUnits();
                    if (baroUnit != null && baroUnit.equals(UnitConstants.UNIT_KPA)) {
                        ambient = ambient * UnitConstants.MBAR_PER_KPA;
                    } else if (baroUnit != null && baroUnit.equals(UnitConstants.UNIT_PSI)) {
                        // PSI gauge to mBar absolute
                        ambient = ambient * UnitConstants.MBAR_PER_PSI + UnitConstants.MBAR_PER_ATM;
                    }
                    // If mBar or null, use as-is
                    return ambient;
                }
            } catch (Exception e) {
                // Fall through to default
            }
        }

        // Use standard atmospheric pressure
        return UnitConstants.MBAR_PER_ATM;
    }

    /**
     * Get ambient pressure in mBar using only the supplier (no dataset fallback)
     */
    private static double getAmbientPressureMbar(java.util.function.Supplier<Double> getAmbientPressure) {
        if (getAmbientPressure != null) {
            Double ambient = getAmbientPressure.get();
            if (ambient != null) {
                return ambient;
            }
        }
        return UnitConstants.MBAR_PER_ATM;
    }

    /**
     * Convert mBar absolute to PSI gauge
     */
    private static DoubleArray toPSI(DoubleArray abs, java.util.function.Supplier<Double> getAmbientPressure) {
        double ambient = getAmbientPressureMbar(getAmbientPressure);
        if (ambient == UnitConstants.MBAR_PER_ATM && getAmbientPressure == null) {
            // No ambient provided, use standard
            return abs.add(-UnitConstants.MBAR_PER_ATM).div(UnitConstants.MBAR_PER_PSI);
        }
        return abs.sub(ambient).div(UnitConstants.MBAR_PER_PSI);
    }

    /**
     * Convert Fahrenheit to Celsius
     */
    private static DoubleArray toCelcius(DoubleArray f) {
        return f.add(-UnitConstants.CELSIUS_TO_FAHRENHEIT_OFFSET).mult(1.0/UnitConstants.CELSIUS_TO_FAHRENHEIT_FACTOR);
    }

    /**
     * Convert Celsius to Fahrenheit
     */
    private static DoubleArray toFahrenheit(DoubleArray c) {
        return c.mult(UnitConstants.CELSIUS_TO_FAHRENHEIT_FACTOR).add(UnitConstants.CELSIUS_TO_FAHRENHEIT_OFFSET);
    }
}

// vim: set sw=4 ts=8 expandtab:

