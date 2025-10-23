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
    public static final double KMH_PER_MPH = 1.609344;
    /** Conversion factor from miles per hour to meters per second */
    public static final double MPS_PER_MPH = 1.0 / 2.23693629;

    // Pressure conversions
    /** Conversion factor from bar to pounds per square inch */
    public static final double PSI_PER_BAR = 14.5038;
    /** Conversion factor from millibar to pounds per square inch */
    public static final double PSI_PER_MBAR = PSI_PER_BAR/1000.0;
    /** Conversion factor from pounds per square inch to millibar */
    public static final double MBAR_PER_PSI = 1000.0/PSI_PER_BAR;
    /** Standard atmospheric pressure in millibar */
    public static final double MBAR_PER_ATM = 1013.25;

    // Temperature conversions
    /** Multiplication factor for Celsius to Fahrenheit conversion */
    public static final double CELSIUS_TO_FAHRENHEIT_FACTOR = 1.8;
    /** Addition offset for Celsius to Fahrenheit conversion */
    public static final double CELSIUS_TO_FAHRENHEIT_OFFSET = 32.0;

    // Power conversions
    /** Factor used in horsepower calculation: HP = torque * rpm / HP_CALCULATION_FACTOR */
    public static final double HP_CALCULATION_FACTOR = 5252.0;
    /** Conversion factor from horsepower to watts */
    public static final double HP_PER_WATT = 745.699872;

    // Mass flow conversions
    /** Conversion factor from grams per second to kilograms per hour */
    public static final double GPS_PER_KGH = 3.6;

    // Torque conversions
    /** Conversion factor from Newton-meters to foot-pounds */
    public static final double NM_PER_FTLB = 1.356;

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
