package test.java;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.nyet.ecuxplot.AxisMenuHandlers;
import org.nyet.ecuxplot.ECUxDataset;
import org.nyet.ecuxplot.Env;
import org.nyet.ecuxplot.Filter;
import org.nyet.logfile.Dataset.Column;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import ch.qos.logback.classic.Level;

/**
 * Test framework for AxisMenuHandlers validation.
 * Tests handler methods, dependency resolution, smoothing registration, and error handling.
 */
public class AxisMenuItemsTest {

    private static final Logger logger = LoggerFactory.getLogger(AxisMenuItemsTest.class);
    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static int testsFailed = 0;

    // Test preferences node (isolated from user preferences)
    private static Preferences testPrefs;

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

        logger.info("=== AxisMenuHandlers Validation Tests ===");
        logger.info("");

        // Create test preferences (isolated from user preferences)
        testPrefs = Preferences.userNodeForPackage(AxisMenuItemsTest.class).node("test");

        try {
            // Create Env and Filter for testing
            Env env = new Env(testPrefs);
            Filter filter = new Filter(testPrefs);
            filter.resetToDefaults(); // Ensure default values

            // Load comprehensive test dataset
            ECUxDataset dataset = new ECUxDataset("test-data/comprehensive-test-dataset.csv", env, filter, 0);
            logger.info("✅ Loaded comprehensive test dataset");
            logger.info("   Logger type: {}", dataset.getLogDetected());
            logger.info("");

            // Load handler expectations from XML
            Map<String, HandlerExpectation> handlerExpectations = loadHandlerExpectations();

            // Run tests
            testHandlerMethods(dataset, handlerExpectations);
            testDependencyResolution(dataset);
            testSmoothingRegistration(dataset);
            testErrorHandling(dataset);

            // Print results
            logger.info("");
            logger.info("=== Test Results ===");
            logger.info("Tests run: {}", testsRun);
            logger.info("Tests passed: {}", testsPassed);
            logger.info("Tests failed: {}", testsFailed);

            if (testsFailed == 0) {
                logger.info("✅ All tests passed!");
                System.exit(0);
            } else {
                logger.info("❌ {} tests failed!", testsFailed);
                System.exit(1);
            }

        } catch (Exception e) {
            logger.error("❌ Failed to run tests: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private static void assertTest(String testName, boolean condition) {
        testsRun++;
        if (condition) {
            testsPassed++;
            logger.info("  ✅ {}", testName);
        } else {
            testsFailed++;
            logger.info("  ❌ {}", testName);
        }
    }

    /**
     * Handler expectation data structure.
     */
    private static class HandlerExpectation {
        @SuppressWarnings("unused")
        final String handlerName;
        @SuppressWarnings("unused")
        final String fieldName;
        @SuppressWarnings("unused")
        final String expectedUnit;
        final boolean shouldExist;

        HandlerExpectation(String handlerName, String fieldName, String expectedUnit, boolean shouldExist) {
            this.handlerName = handlerName;
            this.fieldName = fieldName;
            this.expectedUnit = expectedUnit;
            this.shouldExist = shouldExist;
        }
    }

    /**
     * Load handler expectations from XML.
     * @return Map of field name -> HandlerExpectation
     */
    private static Map<String, HandlerExpectation> loadHandlerExpectations() {
        Map<String, HandlerExpectation> expectations = new HashMap<>();
        try {
            File xmlFile = new File("test-data/axis-menu-expectations.xml");
            if (!xmlFile.exists()) {
                logger.info("⚠️  XML expectations file not found, will test handlers directly");
                return expectations;
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new FileInputStream(xmlFile));
            document.getDocumentElement().normalize();

            // Find test_case for comprehensive-test-dataset.csv
            NodeList testCases = document.getElementsByTagName("test_case");
            Element testCase = null;
            for (int i = 0; i < testCases.getLength(); i++) {
                Element tc = (Element) testCases.item(i);
                if ("comprehensive-test-dataset.csv".equals(tc.getAttribute("file"))) {
                    testCase = tc;
                    break;
                }
            }

            if (testCase == null) {
                return expectations;
            }

            // Load handler_expectations if present
            NodeList handlerExpectationsNodes = testCase.getElementsByTagName("handler_expectations");
            if (handlerExpectationsNodes.getLength() > 0) {
                Element handlerExpectations = (Element) handlerExpectationsNodes.item(0);
                NodeList handlers = handlerExpectations.getElementsByTagName("handler");
                for (int i = 0; i < handlers.getLength(); i++) {
                    Element handler = (Element) handlers.item(i);
                    String handlerName = handler.getAttribute("name");
                    NodeList fields = handler.getElementsByTagName("field");
                    for (int j = 0; j < fields.getLength(); j++) {
                        Element field = (Element) fields.item(j);
                        String fieldName = field.getAttribute("name");
                        String expectedUnit = field.getAttribute("unit");
                        boolean shouldExist = !"false".equals(field.getAttribute("should_exist"));
                        expectations.put(fieldName, new HandlerExpectation(handlerName, fieldName, expectedUnit, shouldExist));
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("⚠️  Error loading handler expectations: {}", e.getMessage());
        }
        return expectations;
    }

    /**
     * Load handler test cases from XML.
     * @return Map of handler name -> list of field names to test
     */
    private static Map<String, java.util.List<String>> loadHandlerTests() {
        Map<String, java.util.List<String>> handlerTests = new HashMap<>();
        try {
            File xmlFile = new File("test-data/axis-menu-expectations.xml");
            if (!xmlFile.exists()) {
                return handlerTests;
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new FileInputStream(xmlFile));
            document.getDocumentElement().normalize();

            // Find test_case for comprehensive-test-dataset.csv
            NodeList testCases = document.getElementsByTagName("test_case");
            Element testCase = null;
            for (int i = 0; i < testCases.getLength(); i++) {
                Element tc = (Element) testCases.item(i);
                if ("comprehensive-test-dataset.csv".equals(tc.getAttribute("file"))) {
                    testCase = tc;
                    break;
                }
            }

            if (testCase == null) {
                return handlerTests;
            }

            // Load handler_tests
            NodeList testConfigNodes = testCase.getElementsByTagName("test_config");
            if (testConfigNodes.getLength() > 0) {
                Element testConfig = (Element) testConfigNodes.item(0);
                NodeList handlerTestsNodes = testConfig.getElementsByTagName("handler_tests");
                if (handlerTestsNodes.getLength() > 0) {
                    Element handlerTestsElement = (Element) handlerTestsNodes.item(0);
                    NodeList handlers = handlerTestsElement.getElementsByTagName("handler");
                    for (int i = 0; i < handlers.getLength(); i++) {
                        Element handler = (Element) handlers.item(i);
                        String handlerName = handler.getAttribute("name");
                        java.util.List<String> fields = new java.util.ArrayList<>();
                        NodeList fieldNodes = handler.getElementsByTagName("field");
                        for (int j = 0; j < fieldNodes.getLength(); j++) {
                            Element field = (Element) fieldNodes.item(j);
                            String fieldName = field.getTextContent().trim();
                            if (!fieldName.isEmpty()) {
                                fields.add(fieldName);
                            }
                        }
                        if (!fields.isEmpty()) {
                            handlerTests.put(handlerName, fields);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("⚠️  Error loading handler tests: {}", e.getMessage());
        }
        return handlerTests;
    }

    /**
     * Test each handler method individually.
     */
    private static void testHandlerMethods(ECUxDataset dataset, Map<String, HandlerExpectation> expectations) {
        logger.info("=== Test: Handler Methods ===");
        logger.info("  Note: Handlers are defined in AxisMenuHandlers.java");
        logger.info("  Testing that handlers return correct columns for test dataset fields");

        Map<String, java.util.List<String>> handlerTests = loadHandlerTests();
        if (handlerTests.isEmpty()) {
            logger.info("  ⚠️  No handler tests loaded from XML");
            logger.info("");
            return;
        }

        for (Map.Entry<String, java.util.List<String>> entry : handlerTests.entrySet()) {
            String handlerName = entry.getKey();
            for (String fieldName : entry.getValue()) {
                testHandler(dataset, handlerName, fieldName, expectations);
            }
        }

        logger.info("");
    }

    /**
     * Test a specific handler method.
     */
    private static void testHandler(ECUxDataset dataset, String handlerName, String fieldName, Map<String, HandlerExpectation> expectations) {
        Column result = null;
        try {
            // Try the handler directly (we'll need to call the specific handler method)
            // For now, use tryAllHandlers which will route to the correct handler
            result = AxisMenuHandlers.tryAllHandlers(dataset, fieldName);

            HandlerExpectation expectation = expectations.get(fieldName);
            if (expectation != null && !expectation.shouldExist) {
                assertTest(handlerName + "(" + fieldName + ") should return null", result == null);
            } else {
                // Some fields may not be created if dependencies are missing (e.g., missing CSV columns)
                // This is expected behavior - handlers return null when dependencies are missing
                boolean shouldExist = (expectation == null || expectation.shouldExist);
                if (shouldExist) {
                    assertTest(handlerName + "(" + fieldName + ") returns column", result != null);
                } else {
                    // Field is expected to not exist (e.g., missing dependencies)
                    assertTest(handlerName + "(" + fieldName + ") returns null (expected - missing dependencies)", result == null);
                }
                // Note: Unit validation can be added later if needed
                // Column unit is not directly accessible, would need to check via getColumnInUnits or similar
            }
        } catch (Exception e) {
            assertTest(handlerName + "(" + fieldName + ") handles without exception", false);
            logger.debug("    Exception: {}", e.getMessage());
        }
    }

    /**
     * Load dependency tests from XML.
     * @return List of dependency test cases (field -> list of dependencies)
     */
    private static java.util.List<DependencyTest> loadDependencyTests() {
        java.util.List<DependencyTest> tests = new java.util.ArrayList<>();
        try {
            File xmlFile = new File("test-data/axis-menu-expectations.xml");
            if (!xmlFile.exists()) {
                return tests;
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new FileInputStream(xmlFile));
            document.getDocumentElement().normalize();

            // Find test_case for comprehensive-test-dataset.csv
            NodeList testCases = document.getElementsByTagName("test_case");
            Element testCase = null;
            for (int i = 0; i < testCases.getLength(); i++) {
                Element tc = (Element) testCases.item(i);
                if ("comprehensive-test-dataset.csv".equals(tc.getAttribute("file"))) {
                    testCase = tc;
                    break;
                }
            }

            if (testCase == null) {
                return tests;
            }

            // Load dependency_tests
            NodeList testConfigNodes = testCase.getElementsByTagName("test_config");
            if (testConfigNodes.getLength() > 0) {
                Element testConfig = (Element) testConfigNodes.item(0);
                NodeList dependencyTestsNodes = testConfig.getElementsByTagName("dependency_tests");
                if (dependencyTestsNodes.getLength() > 0) {
                    Element dependencyTestsElement = (Element) dependencyTestsNodes.item(0);
                    NodeList testNodes = dependencyTestsElement.getElementsByTagName("test");
                    for (int i = 0; i < testNodes.getLength(); i++) {
                        Element test = (Element) testNodes.item(i);
                        NodeList fieldNodes = test.getElementsByTagName("field");
                        if (fieldNodes.getLength() > 0) {
                            String fieldName = fieldNodes.item(0).getTextContent().trim();
                            java.util.List<String> dependencies = new java.util.ArrayList<>();
                            NodeList dependencyNodes = test.getElementsByTagName("dependency");
                            for (int j = 0; j < dependencyNodes.getLength(); j++) {
                                String dep = dependencyNodes.item(j).getTextContent().trim();
                                if (!dep.isEmpty()) {
                                    dependencies.add(dep);
                                }
                            }
                            if (!fieldName.isEmpty()) {
                                tests.add(new DependencyTest(fieldName, dependencies));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("⚠️  Error loading dependency tests: {}", e.getMessage());
        }
        return tests;
    }

    /**
     * Dependency test data structure.
     */
    private static class DependencyTest {
        final String fieldName;
        final java.util.List<String> dependencies;

        DependencyTest(String fieldName, java.util.List<String> dependencies) {
            this.fieldName = fieldName;
            this.dependencies = dependencies;
        }
    }

    /**
     * Test dependency resolution (e.g., WHP → Acceleration, Calc Velocity).
     */
    private static void testDependencyResolution(ECUxDataset dataset) {
        logger.info("=== Test: Dependency Resolution ===");

        java.util.List<DependencyTest> tests = loadDependencyTests();
        if (tests.isEmpty()) {
            logger.info("  ⚠️  No dependency tests loaded from XML");
            logger.info("");
            return;
        }

        for (DependencyTest test : tests) {
            Column field = dataset.get(test.fieldName);
            if (field != null) {
                assertTest(test.fieldName + " can be calculated (dependencies resolve)", true);
                // Verify dependencies were created
                for (String dep : test.dependencies) {
                    Column depColumn = dataset.get(dep);
                    assertTest(test.fieldName + " dependency: " + dep + " exists", depColumn != null);
                }
            } else {
                assertTest(test.fieldName + " can be calculated (dependencies resolve)", false);
            }
        }

        logger.info("");
    }

    /**
     * Load smoothing test fields from XML.
     * @return List of field names to test
     */
    private static java.util.List<String> loadSmoothingTests() {
        java.util.List<String> fields = new java.util.ArrayList<>();
        try {
            File xmlFile = new File("test-data/axis-menu-expectations.xml");
            if (!xmlFile.exists()) {
                return fields;
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new FileInputStream(xmlFile));
            document.getDocumentElement().normalize();

            // Find test_case for comprehensive-test-dataset.csv
            NodeList testCases = document.getElementsByTagName("test_case");
            Element testCase = null;
            for (int i = 0; i < testCases.getLength(); i++) {
                Element tc = (Element) testCases.item(i);
                if ("comprehensive-test-dataset.csv".equals(tc.getAttribute("file"))) {
                    testCase = tc;
                    break;
                }
            }

            if (testCase == null) {
                return fields;
            }

            // Load smoothing_tests
            NodeList testConfigNodes = testCase.getElementsByTagName("test_config");
            if (testConfigNodes.getLength() > 0) {
                Element testConfig = (Element) testConfigNodes.item(0);
                NodeList smoothingTestsNodes = testConfig.getElementsByTagName("smoothing_tests");
                if (smoothingTestsNodes.getLength() > 0) {
                    Element smoothingTestsElement = (Element) smoothingTestsNodes.item(0);
                    NodeList fieldNodes = smoothingTestsElement.getElementsByTagName("field");
                    for (int i = 0; i < fieldNodes.getLength(); i++) {
                        String fieldName = fieldNodes.item(i).getTextContent().trim();
                        if (!fieldName.isEmpty()) {
                            fields.add(fieldName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("⚠️  Error loading smoothing tests: {}", e.getMessage());
        }
        return fields;
    }

    /**
     * Test smoothing registration (HPMAW, AccelMAW, etc.).
     * Note: We can't directly access smoothingWindows, but we can verify that
     * fields that should have smoothing are created successfully.
     * Smoothing registration is tested indirectly by verifying the fields exist.
     */
    private static void testSmoothingRegistration(ECUxDataset dataset) {
        logger.info("=== Test: Smoothing Registration ===");
        logger.info("  Note: Smoothing registration verified indirectly");
        logger.info("  Fields with smoothing should be created successfully");

        java.util.List<String> fields = loadSmoothingTests();
        if (fields.isEmpty()) {
            logger.info("  ⚠️  No smoothing tests loaded from XML");
            logger.info("");
            return;
        }

        for (String fieldName : fields) {
            Column field = dataset.get(fieldName);
            if (field != null) {
                assertTest(fieldName + " created (smoothing should be registered)", true);
            } else {
                assertTest(fieldName + " created (smoothing should be registered)", false);
            }
        }

        logger.info("");
    }

    /**
     * Test error handling (missing dependencies return null).
     */
    private static void testErrorHandling(ECUxDataset dataset) {
        logger.info("=== Test: Error Handling ===");

        // Test handlers with missing dependencies
        // Note: Most handlers will return null if dependencies are missing
        // We can't easily test this without creating a dataset with missing fields,
        // but we can verify that handlers don't throw exceptions

        // Test that non-existent fields return null
        Column nonExistent = AxisMenuHandlers.tryAllHandlers(dataset, "NonExistentField");
        assertTest("Non-existent field returns null", nonExistent == null);

        // Test that handlers handle null gracefully
        try {
            @SuppressWarnings("unused")
            Column result = AxisMenuHandlers.tryAllHandlers(dataset, "InvalidFieldName");
            // Should return null, not throw exception
            assertTest("Invalid field name handled gracefully", true);
        } catch (Exception e) {
            assertTest("Invalid field name handled gracefully", false);
            logger.debug("    Exception: {}", e.getMessage());
        }

        logger.info("");
    }
}

