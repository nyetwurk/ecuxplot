package org.nyet.ecuxplot;

/**
 * Standardized unit conversion constants for ECUxPlot
 * Centralizes all unit conversion factors to ensure consistency across the application
 *
 * This class provides a single source of truth for all unit conversion factors,
 * eliminating magic numbers and ensuring consistent conversions throughout the codebase.
 */
public class UnitConstants {

    // Speed conversions
    /** Conversion factor from kilometers per hour to miles per hour */
    public static final double KMH_TO_MPH = 0.621371192;

    // Pressure conversions
    /** Conversion factor from bar to pounds per square inch */
    public static final double BAR_TO_PSI = 14.5038;

    // Temperature conversions
    /** Multiplication factor for Celsius to Fahrenheit conversion */
    public static final double CELSIUS_TO_FAHRENHEIT_FACTOR = 1.8;
    /** Addition offset for Celsius to Fahrenheit conversion */
    public static final double CELSIUS_TO_FAHRENHEIT_OFFSET = 32.0;

    // Power conversions
    /** Factor used in horsepower calculation: HP = torque * rpm / HP_CALCULATION_FACTOR */
    public static final double HP_CALCULATION_FACTOR = 5252.0;

    // Mass flow conversions
    /** Conversion factor from grams per second to kilograms per hour */
    public static final double GPS_TO_KGH = 3.6;

    // Torque conversions
    /** Conversion factor from Newton-meters to foot-pounds */
    public static final double NM_TO_FTLB = 0.737562149;

    // Air-fuel ratio conversions
    /** Stoichiometric air-fuel ratio for gasoline */
    public static final double STOICHIOMETRIC_AFR = 14.7;

    // Acceleration conversions
    /** Standard gravity acceleration in m/s² */
    public static final double STANDARD_GRAVITY = 9.80665;

    // Private constructor to prevent instantiation
    private UnitConstants() {
        throw new UnsupportedOperationException("UnitConstants is a utility class and cannot be instantiated");
    }
}
