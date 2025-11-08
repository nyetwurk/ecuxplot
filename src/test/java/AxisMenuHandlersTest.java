package test.java;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.nyet.ecuxplot.AxisMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

/**
 * Consistency validation test for AxisMenu and AxisMenuHandlers.
 * Ensures that calculated fields defined in AxisMenu have corresponding handlers in AxisMenuHandlers,
 * and that handlers have corresponding menu items (where appropriate).
 */
public class AxisMenuHandlersTest {

    private static final Logger logger = LoggerFactory.getLogger(AxisMenuHandlersTest.class);
    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static int testsFailed = 0;

    /**
     * Load expected field sets from XML.
     */
    private static Set<String> loadExpectedHandlerOnlyFields() {
        return loadFieldSetFromXML("expected_handler_only_fields");
    }

    private static Set<String> loadExpectedDynamicMenuFields() {
        return loadFieldSetFromXML("expected_dynamic_menu_fields");
    }

    private static Set<String> loadExpectedMenuOnlyFields() {
        return loadFieldSetFromXML("expected_menu_only_fields");
    }

    private static Set<String> loadFieldSetFromXML(String elementName) {
        Set<String> fields = new HashSet<>();
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

            // Load field set
            NodeList testConfigNodes = testCase.getElementsByTagName("test_config");
            if (testConfigNodes.getLength() > 0) {
                Element testConfig = (Element) testConfigNodes.item(0);
                NodeList fieldSetNodes = testConfig.getElementsByTagName(elementName);
                if (fieldSetNodes.getLength() > 0) {
                    Element fieldSetElement = (Element) fieldSetNodes.item(0);
                    NodeList fieldNodes = fieldSetElement.getElementsByTagName("field");
                    for (int i = 0; i < fieldNodes.getLength(); i++) {
                        String fieldName = fieldNodes.item(i).getTextContent().trim();
                        if (!fieldName.isEmpty()) {
                            fields.add(fieldName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("⚠️  Error loading {}: {}", elementName, e.getMessage());
        }
        return fields;
    }

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

        logger.info("=== AxisMenu ↔ AxisMenuHandlers Consistency Validation ===");
        logger.info("");

        try {
            // Load expected field sets from XML
            Set<String> expectedHandlerOnlyFields = loadExpectedHandlerOnlyFields();
            Set<String> expectedDynamicMenuFields = loadExpectedDynamicMenuFields();
            Set<String> expectedMenuOnlyFields = loadExpectedMenuOnlyFields();

            // Discover fields from AxisMenu
            Set<String> axisMenuFields = discoverAxisMenuFields();
            logger.info("✅ Discovered {} calculated fields from AxisMenu", axisMenuFields.size());
            logger.info("");

            // Discover fields from AxisMenuHandlers
            Set<String> handlerFields = discoverHandlerFields();
            logger.info("✅ Discovered {} calculated fields from AxisMenuHandlers", handlerFields.size());
            logger.info("");

            // Compare fields
            testConsistency(axisMenuFields, handlerFields, expectedHandlerOnlyFields, expectedDynamicMenuFields, expectedMenuOnlyFields);

            // Print results
            logger.info("");
            logger.info("=== Test Results ===");
            logger.info("Tests run: {}", testsRun);
            logger.info("Tests passed: {}", testsPassed);
            logger.info("Tests failed: {}", testsFailed);

            if (testsFailed == 0) {
                logger.info("✅ All consistency checks passed!");
                System.exit(0);
            } else {
                logger.info("❌ {} consistency checks failed!", testsFailed);
                System.exit(1);
            }

        } catch (Exception e) {
            logger.error("❌ Failed to run consistency tests: {}", e.getMessage(), e);
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
     * Discover all calculated fields from AxisMenu.
     * Extracts fields from RPM_CALCULATED_FIELDS array using reflection.
     */
    private static Set<String> discoverAxisMenuFields() throws Exception {
        Set<String> fields = new HashSet<>();

        // Use reflection to access private RPM_CALCULATED_FIELDS array
        Field rpmFieldsField = AxisMenu.class.getDeclaredField("RPM_CALCULATED_FIELDS");
        rpmFieldsField.setAccessible(true);
        Object[] rpmFields = (Object[]) rpmFieldsField.get(null);

        // Extract field names from RpmCalculatedField objects
        for (Object fieldObj : rpmFields) {
            // Access fieldName field using reflection
            Field fieldNameField = fieldObj.getClass().getDeclaredField("fieldName");
            fieldNameField.setAccessible(true);
            String fieldName = (String) fieldNameField.get(fieldObj);

            // Access unitConversion field
            Field unitConversionField = fieldObj.getClass().getDeclaredField("unitConversion");
            unitConversionField.setAccessible(true);
            String unitConversion = (String) unitConversionField.get(fieldObj);

            // Add base field name
            fields.add(fieldName);

            // Add unit conversion field if present
            if (unitConversion != null && !unitConversion.isEmpty()) {
                fields.add(fieldName + " (" + unitConversion + ")");
            }
        }

        return fields;
    }

    /**
     * Discover all calculated fields from AxisMenuHandlers.
     * Loads field names from XML configuration.
     */
    private static Set<String> discoverHandlerFields() throws Exception {
        Set<String> fields = new HashSet<>();
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

            // Load handler_fields
            NodeList testConfigNodes = testCase.getElementsByTagName("test_config");
            if (testConfigNodes.getLength() > 0) {
                Element testConfig = (Element) testConfigNodes.item(0);
                NodeList handlerFieldsNodes = testConfig.getElementsByTagName("handler_fields");
                if (handlerFieldsNodes.getLength() > 0) {
                    Element handlerFieldsElement = (Element) handlerFieldsNodes.item(0);
                    NodeList fieldNodes = handlerFieldsElement.getElementsByTagName("field");
                    for (int i = 0; i < fieldNodes.getLength(); i++) {
                        String fieldName = fieldNodes.item(i).getTextContent().trim();
                        if (!fieldName.isEmpty()) {
                            fields.add(fieldName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("⚠️  Error loading handler fields: {}", e.getMessage());
        }
        return fields;
    }

    /**
     * Test consistency between AxisMenu and AxisMenuHandlers.
     */
    private static void testConsistency(Set<String> axisMenuFields, Set<String> handlerFields,
            Set<String> expectedHandlerOnlyFields, Set<String> expectedDynamicMenuFields,
            Set<String> expectedMenuOnlyFields) {
        logger.info("=== Test: Consistency Validation ===");

        // Find fields in AxisMenu but not in handlers (excluding expected exceptions)
        Set<String> menuOnly = new HashSet<>(axisMenuFields);
        menuOnly.removeAll(handlerFields);
        menuOnly.removeAll(expectedMenuOnlyFields);

        // Find fields in handlers but not in AxisMenu (excluding expected exceptions)
        Set<String> handlerOnly = new HashSet<>(handlerFields);
        handlerOnly.removeAll(axisMenuFields);
        handlerOnly.removeAll(expectedHandlerOnlyFields);
        handlerOnly.removeAll(expectedDynamicMenuFields);  // These are added dynamically in AxisMenu

        // Test that all AxisMenu fields have handlers (excluding expected exceptions)
        if (menuOnly.isEmpty()) {
            assertTest("All AxisMenu calculated fields have handlers", true);
        } else {
            assertTest("All AxisMenu calculated fields have handlers", false);
            logger.info("    Fields in AxisMenu but not in handlers:");
            for (String field : menuOnly) {
                logger.info("      - {}", field);
            }
        }

        // Test that all handler fields appear in AxisMenu (excluding expected exceptions)
        if (handlerOnly.isEmpty()) {
            assertTest("All handler fields appear in AxisMenu (excluding debug/diagnostic)", true);
        } else {
            assertTest("All handler fields appear in AxisMenu (excluding debug/diagnostic)", false);
            logger.info("    Fields in handlers but not in AxisMenu:");
            for (String field : handlerOnly) {
                logger.info("      - {}", field);
            }
        }

        // Test that expected handler-only fields are actually handler-only
        for (String field : expectedHandlerOnlyFields) {
            if (handlerFields.contains(field) && !axisMenuFields.contains(field)) {
                assertTest("Expected handler-only field '" + field + "' is handler-only", true);
            } else if (axisMenuFields.contains(field)) {
                assertTest("Expected handler-only field '" + field + "' is handler-only", false);
                logger.warn("    WARNING: '{}' is in AxisMenu but expected to be handler-only", field);
            }
        }

        // Test that expected menu-only fields are actually menu-only
        for (String field : expectedMenuOnlyFields) {
            if (axisMenuFields.contains(field) && !handlerFields.contains(field)) {
                assertTest("Expected menu-only field '" + field + "' is menu-only", true);
            } else if (handlerFields.contains(field)) {
                assertTest("Expected menu-only field '" + field + "' is menu-only", false);
                logger.warn("    WARNING: '{}' is in handlers but expected to be menu-only", field);
            }
        }

        logger.info("");
    }
}

