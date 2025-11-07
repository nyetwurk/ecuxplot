package test.java;

import java.util.prefs.Preferences;

import org.nyet.ecuxplot.ECUxDataset;
import org.nyet.ecuxplot.Env;
import org.nyet.ecuxplot.Filter;
import org.nyet.logfile.Dataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

/**
 * Test to verify comprehensive-test-dataset.csv loads correctly.
 * This is a quick validation test, not a full unit test suite.
 */
public class ComprehensiveDatasetTest {

    private static final Logger logger = LoggerFactory.getLogger(ComprehensiveDatasetTest.class);
    private static int testsFailed = 0;

    public static void main(String[] args) {
        // Configure logging level based on VERBOSITY environment variable or system property
        // Default to INFO for CI, can be set to DEBUG for development
        String verbosity = System.getProperty("VERBOSITY", System.getenv("VERBOSITY"));
        if (verbosity == null) verbosity = "INFO";
        Level logLevel = Level.toLevel(verbosity, Level.INFO);

        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.classic.Logger ecuxLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.nyet.ecuxplot");
        rootLogger.setLevel(logLevel);
        ecuxLogger.setLevel(logLevel);

        logger.info("=== Comprehensive Dataset CSV Load Test ===");
        logger.info("");

        try {
            // Create Env and Filter for testing (needed for Zeitronix Boost and other fields)
            Preferences testPrefs = Preferences.userNodeForPackage(ComprehensiveDatasetTest.class).node("test");
            Env env = new Env(testPrefs);
            Filter filter = new Filter(testPrefs);
            filter.resetToDefaults(); // Ensure default values

            // Load the comprehensive test dataset
            ECUxDataset dataset = new ECUxDataset("test-data/comprehensive-test-dataset.csv", env, filter, 0);

            logger.info("✅ CSV file loaded successfully!");
            logger.info("");

            // Check logger detection
            String logType = dataset.getLogDetected();
            logger.info("Logger type detected: {}", logType);
            logger.info("");

            // Check that key fields are present
            logger.info("Checking key fields:");
            checkField(dataset, "TIME", "Base field");
            checkField(dataset, "RPM", "Base field");
            checkField(dataset, "Sample", "Base field");
            checkField(dataset, "MassAirFlow", "MAF field");
            checkField(dataset, "BoostPressureActual", "Boost field");
            checkField(dataset, "Zeitronix Boost", "Zeitronix field");
            checkField(dataset, "ME7L ps_w", "ME7L field");
            checkField(dataset, "LogID", "EvoScan field");
            checkField(dataset, "VehicleSpeed", "Speed field");
            checkField(dataset, "IntakeAirTemperature", "Temperature field");
            checkField(dataset, "ThrottlePlateAngle", "Throttle field");
            checkField(dataset, "IgnitionTimingAngleOverall", "Ignition field");
            logger.info("");

            // Check pattern-based fields
            logger.info("Checking pattern-based fields:");
            checkField(dataset, "ExhaustCamPosition", "VVT pattern");
            checkField(dataset, "CatTemperature", "Cats pattern");
            checkField(dataset, "EGTbank1", "EGT pattern");
            checkField(dataset, "IdleSpeed", "Idle pattern");
            checkField(dataset, "KnockVoltCyl1", "Knock pattern");
            checkField(dataset, "MisfireCyl1", "Misfires pattern");
            checkField(dataset, "OXSVoltS1B1", "O2 Sensors pattern");
            checkField(dataset, "TorqueActual", "Torque pattern");
            logger.info("");

            // Check calculated fields can be requested (they should be created on demand)
            logger.info("Checking calculated field creation:");
            Dataset.Column whp = dataset.get("WHP");
            if (whp != null) {
                logger.info("  ✅ WHP calculated field created");
            } else {
                logger.info("  ⚠️  WHP calculated field not created (may need RPM and dependencies)");
            }

            Dataset.Column calcVelocity = dataset.get("Calc Velocity");
            if (calcVelocity != null) {
                logger.info("  ✅ Calc Velocity calculated field created");
            } else {
                logger.info("  ⚠️  Calc Velocity calculated field not created (may need RPM and dependencies)");
            }
            logger.info("");

            // Print summary
            logger.info("=== Test Summary ===");
            if (testsFailed == 0) {
                logger.info("✅ CSV file loads successfully");
                logger.info("✅ Logger type detected: {}", logType);
                logger.info("✅ Key fields are accessible");
                logger.info("");
                logger.info("The comprehensive test dataset is ready for use in unit tests!");
                System.exit(0);
            } else {
                logger.info("❌ {} field(s) not found!", testsFailed);
                System.exit(1);
            }

        } catch (Exception e) {
            logger.error("❌ Failed to load CSV file: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private static void checkField(ECUxDataset dataset, String fieldName, String category) {
        Dataset.Column col = dataset.get(fieldName);
        if (col != null) {
            logger.info("  ✅ {} ({})", fieldName, category);
        } else {
            testsFailed++;
            logger.info("  ❌ {} ({}) - NOT FOUND", fieldName, category);
        }
    }
}

