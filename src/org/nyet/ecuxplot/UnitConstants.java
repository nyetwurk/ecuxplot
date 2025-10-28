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
    /** Conversion factor from miles per hour to kilometers per hour */
    public static final double MPH_PER_KPH = 1.0 / KMH_PER_MPH;
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
    /** Conversion factor from kilograms per hour to grams per second */
    public static final double KGH_PER_GPS = 1.0 / GPS_PER_KGH;

    // Torque conversions
    /** Conversion factor from Newton-meters to foot-pounds */
    public static final double NM_PER_FTLB = 1.356;

    // Air-fuel ratio conversions
    /** Stoichiometric air-fuel ratio for gasoline */
    public static final double STOICHIOMETRIC_AFR = 14.7;
    /** Conversion factor from AFR to lambda */
    public static final double LAMBDA_PER_AFR = 1.0 / STOICHIOMETRIC_AFR;

    // Acceleration conversions
    /** Standard gravity acceleration in m/s² */
    public static final double STANDARD_GRAVITY = 9.80665;

    // Fluid properties
    /** Standard air density at sea level in kg/m³ */
    public static final double AIR_DENSITY_STANDARD = 1.293;

    // Fuel flow conversions
    /** Conversion factor from cc/min to grams/sec for fuel flow */
    public static final double GPS_PER_CCMIN = 0.0114;

    // Unit strings
    /** Unit string for air-fuel ratio */
    public static final String UNIT_AFR = "AFR";
    /** Unit string for lambda */
    public static final String UNIT_LAMBDA = "lambda";
    /** Unit string for pounds per square inch */
    public static final String UNIT_PSI = "PSI";
    /** Unit string for millibar */
    public static final String UNIT_MBAR = "mBar";
    /** Unit string for miles per hour */
    public static final String UNIT_MPH = "mph";
    /** Unit string for kilometers per hour */
    public static final String UNIT_KMH = "km/h";
    /** Unit string for degrees Fahrenheit */
    public static final String UNIT_FAHRENHEIT = "\u00B0F";
    /** Unit string for degrees Celsius */
    public static final String UNIT_CELSIUS = "\u00B0C";
    /** Unit string for degrees */
    public static final String UNIT_DEGREES = "\u00B0";
    /** Unit string for revolutions per minute */
    public static final String UNIT_RPM = "RPM";
    /** Unit string for seconds */
    public static final String UNIT_SECONDS = "s";
    /** Unit string for grams per second */
    public static final String UNIT_GPS = "g/sec";
    /** Unit string for kilograms per hour */
    public static final String UNIT_KGH = "kg/hr";
    /** Unit string for percent */
    public static final String UNIT_PERCENT = "%";
    /** Unit string for voltage */
    public static final String UNIT_VOLTS = "V";
    /** Unit string for milliseconds */
    public static final String UNIT_MS = "ms";
    /** Unit string for horsepower */
    public static final String UNIT_HP = "HP";
    /** Unit string for foot-pounds */
    public static final String UNIT_FTLB = "ft-lb";
    /** Unit string for meters per second */
    public static final String UNIT_MPS = "m/s";
    /** Unit string for RPM per second */
    public static final String UNIT_RPS = "RPM/s";
    /** Unit string for g (acceleration) */
    public static final String UNIT_G = "g";
    /** Unit string for pounds per minute */
    public static final String UNIT_LBMIN = "lb/min";
    /** Unit string for pounds per minute */
    public static final String UNIT_PR = "PR";
    /** Unit string for Kelvin */
    public static final String UNIT_KELVIN = "K";
    /** Unit string for sample number */
    public static final String UNIT_SAMPLE = "#";

    // Private constructor to prevent instantiation
    private UnitConstants() {
        throw new UnsupportedOperationException("UnitConstants is a utility class and cannot be instantiated");
    }
}

// vim: set sw=4 ts=8 expandtab:
