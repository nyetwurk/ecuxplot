package org.nyet.ecuxplot;

import java.util.prefs.Preferences;
import java.util.function.Function;

public class FATS {
    public static final String PREFS_TAG = "FATS";

    /**
     * Interface for handling speed unit operations
     */
    public interface SpeedUnitHandler {
        double getStartValue(FATS fats);
        double getEndValue(FATS fats);
        void setStartValue(FATS fats, double value);
        void setEndValue(FATS fats, double value);
        int speedToRpm(double speed, double rpmPerSpeed);
        double rpmToSpeed(int rpm, double rpmPerSpeed);
        double getRpmConversionFactor(Constants constants);
        String getDisplayName();
        String getAbbreviation();
        boolean requiresRpmConversionFields();
    }

    public enum SpeedUnit {
        RPM("RPM"),
        mph("mph"),
        kmh("km/h");

        private final String displayName;

        SpeedUnit(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        /**
         * Get the handler for this speed unit
         * @return The appropriate SpeedUnitHandler
         */
        public SpeedUnitHandler getHandler() {
            return getSpeedUnitHandler(this);
        }
    }

    // Singleton instances
    private static final SpeedUnitHandler RPM_HANDLER = new SpeedUnitHandler() {
        public double getStartValue(FATS fats) { return fats.start(); }
        public double getEndValue(FATS fats) { return fats.end(); }
        public void setStartValue(FATS fats, double value) { fats.start((int) Math.round(value)); }
        public void setEndValue(FATS fats, double value) { fats.end((int) Math.round(value)); }
        public int speedToRpm(double speed, double rpmPerSpeed) { return (int) Math.round(speed); }
        public double rpmToSpeed(int rpm, double rpmPerSpeed) { return rpm; }
        public double getRpmConversionFactor(Constants constants) { return 1.0; }
        public String getDisplayName() { return "RPM"; }
        public String getAbbreviation() { return "rpm"; }
        public boolean requiresRpmConversionFields() { return false; }
    };

    private static final SpeedUnitHandler MPH_HANDLER = new SpeedUnitHandler() {
        public double getStartValue(FATS fats) { return fats.startMph(); }
        public double getEndValue(FATS fats) { return fats.endMph(); }
        public void setStartValue(FATS fats, double value) { fats.startMph(value); }
        public void setEndValue(FATS fats, double value) { fats.endMph(value); }
        public int speedToRpm(double speed, double rpmPerSpeed) { return (int) Math.round(speed * rpmPerSpeed); }
        public double rpmToSpeed(int rpm, double rpmPerSpeed) { return rpm / rpmPerSpeed; }
        public double getRpmConversionFactor(Constants constants) { return constants.rpm_per_mph(); }
        public String getDisplayName() { return "mph"; }
        public String getAbbreviation() { return "mph"; }
        public boolean requiresRpmConversionFields() { return true; }
    };

    private static final SpeedUnitHandler KPH_HANDLER = new SpeedUnitHandler() {
        public double getStartValue(FATS fats) { return fats.startKph(); }
        public double getEndValue(FATS fats) { return fats.endKph(); }
        public void setStartValue(FATS fats, double value) { fats.startKph(value); }
        public void setEndValue(FATS fats, double value) { fats.endKph(value); }
        public int speedToRpm(double speed, double rpmPerSpeed) { return (int) Math.round(speed * rpmPerSpeed); }
        public double rpmToSpeed(int rpm, double rpmPerSpeed) { return rpm / rpmPerSpeed; }
        public double getRpmConversionFactor(Constants constants) { return constants.rpm_per_kph(); }
        public String getDisplayName() { return "km/h"; }
        public String getAbbreviation() { return "km/h"; }
        public boolean requiresRpmConversionFields() { return true; }
    };

    /**
     * Get the appropriate handler for the given speed unit
     * @param speedUnit The speed unit
     * @return The corresponding handler
     */
    public static SpeedUnitHandler getSpeedUnitHandler(SpeedUnit speedUnit) {
        switch (speedUnit) {
            case RPM: return RPM_HANDLER;
            case mph: return MPH_HANDLER;
            case kmh: return KPH_HANDLER;
            default: throw new IllegalArgumentException("Unsupported speed unit: " + speedUnit);
        }
    }

    // Default values
    private static final int defaultStart = 4200;
    private static final int defaultEnd = 6500;
    private static final SpeedUnit defaultSpeedUnit = SpeedUnit.RPM;
    private static final double defaultStartMph = 60.0;
    private static final double defaultEndMph = 90.0;
    private static final double defaultStartKph = 100.0;
    private static final double defaultEndKph = 150.0;

    private final Preferences prefs;

    public FATS(Preferences prefs) {
        this.prefs = prefs.node(PREFS_TAG);
    }

    // Functional preference accessors
    private <T> T getPref(String key, T defaultValue, Function<String, T> parser) {
        String value = this.prefs.get(key, defaultValue.toString());
        try {
            return parser.apply(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void setPref(String key, Object value) {
        this.prefs.put(key, value.toString());
    }

    // RPM methods
    public int start() { return getPref("start", defaultStart, Integer::parseInt); }
    public void start(int val) { setPref("start", val); }
    public int end() { return getPref("end", defaultEnd, Integer::parseInt); }
    public void end(int val) { setPref("end", val); }

    // Speed unit method
    public SpeedUnit speedUnit() {
        return getPref("speed_unit", defaultSpeedUnit, s -> SpeedUnit.valueOf(s));
    }
    public void speedUnit(SpeedUnit val) { setPref("speed_unit", val.name()); }

    // MPH methods
    public double startMph() { return getPref("start_mph", defaultStartMph, Double::parseDouble); }
    public void startMph(double val) { setPref("start_mph", val); }
    public double endMph() { return getPref("end_mph", defaultEndMph, Double::parseDouble); }
    public void endMph(double val) { setPref("end_mph", val); }

    // KPH methods
    public double startKph() { return getPref("start_kph", defaultStartKph, Double::parseDouble); }
    public void startKph(double val) { setPref("start_kph", val); }
    public double endKph() { return getPref("end_kph", defaultEndKph, Double::parseDouble); }
    public void endKph(double val) { setPref("end_kph", val); }

    // Conversion methods (functional style)
    public int mphToRpm(double mph, double rpmPerMph) { return (int) Math.round(mph * rpmPerMph); }
    public int kphToRpm(double kph, double rpmPerKph) { return (int) Math.round(kph * rpmPerKph); }
    public double rpmToMph(int rpm, double rpmPerMph) { return rpm / rpmPerMph; }
    public double rpmToKph(int rpm, double rpmPerKph) { return rpm / rpmPerKph; }
}

// vim: set sw=4 ts=8 expandtab:
