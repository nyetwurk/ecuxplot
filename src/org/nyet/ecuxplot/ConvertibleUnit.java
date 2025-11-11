package org.nyet.ecuxplot;

import org.nyet.util.DoubleArray;
import java.util.function.Supplier;

/**
 * Enum for units that have both US Customary and Metric variants.
 * Encapsulates conversion logic, especially for complex conversions.
 */
public enum ConvertibleUnit {
    // Pressure (boost/relative - ambient pressure handled separately)
    PRESSURE_BOOST(UnitConstants.UNIT_PSI, UnitConstants.UNIT_MBAR) {
        @Override
        public DoubleArray convertTo(DoubleArray data, String fromUnit, String toUnit,
                Supplier<Double> getAmbientPressure) {
            // Complex: PSI is gauge, mBar is absolute - requires ambient pressure
            if (toUnit.equals(UnitConstants.UNIT_PSI)) {
                // mBar absolute -> PSI gauge
                double ambient = getAmbientPressureMbar(getAmbientPressure);
                return data.sub(ambient).div(UnitConstants.MBAR_PER_PSI);
            } else {
                // PSI gauge -> mBar absolute
                double ambient = getAmbientPressureMbar(getAmbientPressure);
                return data.mult(UnitConstants.MBAR_PER_PSI).add(ambient);
            }
        }
    },
    PRESSURE_BOOST_GAUGE(UnitConstants.UNIT_PSI, UnitConstants.UNIT_MBAR_GAUGE) {
        @Override
        public DoubleArray convertTo(DoubleArray data, String fromUnit, String toUnit,
                Supplier<Double> getAmbientPressure) {
            // Simple: both are gauge pressure, just unit conversion
            if (toUnit.equals(UnitConstants.UNIT_PSI)) {
                return data.div(UnitConstants.MBAR_PER_PSI);
            } else {
                return data.mult(UnitConstants.MBAR_PER_PSI);
            }
        }
    },

    // Speed - simple conversion
    SPEED(UnitConstants.UNIT_MPH, UnitConstants.UNIT_KMH) {
        @Override
        public DoubleArray convertTo(DoubleArray data, String fromUnit, String toUnit,
                Supplier<Double> getAmbientPressure) {
            if (toUnit.equals(UnitConstants.UNIT_MPH)) {
                return data.mult(UnitConstants.MPH_PER_KPH);
            } else {
                return data.mult(UnitConstants.KMH_PER_MPH);
            }
        }
    },

    // Torque - simple conversion
    TORQUE(UnitConstants.UNIT_FTLB, UnitConstants.UNIT_NM) {
        @Override
        public DoubleArray convertTo(DoubleArray data, String fromUnit, String toUnit,
                Supplier<Double> getAmbientPressure) {
            if (toUnit.equals(UnitConstants.UNIT_FTLB)) {
                return data.mult(UnitConstants.FTLB_PER_NM);
            } else {
                return data.mult(UnitConstants.NM_PER_FTLB);
            }
        }
    },

    // Temperature - complex conversion (offset + factor)
    TEMPERATURE(UnitConstants.UNIT_FAHRENHEIT, UnitConstants.UNIT_CELSIUS) {
        @Override
        public DoubleArray convertTo(DoubleArray data, String fromUnit, String toUnit,
                Supplier<Double> getAmbientPressure) {
            if (toUnit.equals(UnitConstants.UNIT_FAHRENHEIT)) {
                // Celsius -> Fahrenheit: (C * 1.8) + 32
                return data.mult(UnitConstants.CELSIUS_TO_FAHRENHEIT_FACTOR)
                    .add(UnitConstants.CELSIUS_TO_FAHRENHEIT_OFFSET);
            } else {
                // Fahrenheit -> Celsius: (F - 32) / 1.8
                return data.add(-UnitConstants.CELSIUS_TO_FAHRENHEIT_OFFSET)
                    .mult(1.0 / UnitConstants.CELSIUS_TO_FAHRENHEIT_FACTOR);
            }
        }
    };

    final String usCustomary;
    final String metric;

    ConvertibleUnit(String usCustomary, String metric) {
        this.usCustomary = usCustomary;
        this.metric = metric;
    }

    /**
     * Convert data from one unit to another.
     * All enum values override this method to provide their specific conversion logic.
     *
     * @param data The data to convert
     * @param fromUnit The source unit (must be one of usCustomary or metric)
     * @param toUnit The target unit (must be one of usCustomary or metric)
     * @param getAmbientPressure Supplier for ambient pressure (for gauge pressure conversions)
     * @return Converted data
     */
    public abstract DoubleArray convertTo(DoubleArray data, String fromUnit, String toUnit,
            Supplier<Double> getAmbientPressure);

    /**
     * Helper to get ambient pressure in mBar.
     * Made public so DatasetUnits can use it to avoid duplication.
     */
    public static double getAmbientPressureMbar(Supplier<Double> getAmbientPressure) {
        if (getAmbientPressure != null) {
            Double ambient = getAmbientPressure.get();
            if (ambient != null) {
                return ambient;
            }
        }
        return UnitConstants.MBAR_PER_ATM;
    }

    /**
     * Find the ConvertibleUnit enum for a given unit string.
     * @param unit The unit string to find
     * @return The ConvertibleUnit, or null if not found
     */
    public static ConvertibleUnit fromUnit(String unit) {
        for (ConvertibleUnit cu : values()) {
            if (cu.usCustomary.equals(unit) || cu.metric.equals(unit)) {
                return cu;
            }
        }
        return null;
    }
}

// vim: set sw=4 ts=8 expandtab:

