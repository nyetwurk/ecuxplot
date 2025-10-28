package test.java;

import java.io.File;
import java.io.FileInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.nyet.ecuxplot.ECUxDataset;
import org.nyet.logfile.Dataset;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test framework for logger detection and parsing testing
 * Supports both full testing and detection-only testing modes
 */
public class LoggerDetectionTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int fileTestsFailed = 0;

    public static void main(String[] args) {
        // Configure logging to INFO level to reduce verbosity during tests
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Logger ecuxLogger = (Logger) LoggerFactory.getLogger("org.nyet.ecuxplot");
        rootLogger.setLevel(Level.INFO);
        ecuxLogger.setLevel(Level.INFO);

        // Check if detection-only mode
        boolean detectionOnly = args.length > 0 && "detection".equals(args[0]);

        if (detectionOnly) {
            System.out.println("=== Logger Detection Only Tests ===");
        } else {
            System.out.println("=== Logger Detection Tests ===");
        }
        System.out.println();

        // Test comprehensive expectations from XML
        testComprehensiveExpectations(detectionOnly);

        // Print results
        System.out.println();
        System.out.println("=== Test Results ===");
        System.out.println("Tests run: " + testsRun);
        System.out.println("Tests passed: " + testsPassed);
        System.out.println("Tests failed: " + testsFailed);

        if (testsFailed == 0) {
            System.out.println("✅ All tests passed!");
        } else {
            System.out.println("❌ " + testsFailed + " tests failed!");
        }
    }


    private static void assertTest(String testName, boolean condition) {
        testsRun++;
        if (condition) {
            testsPassed++;
            // Don't print success messages - only failures
        } else {
            testsFailed++;
            fileTestsFailed++;
            System.out.println("  ❌ " + testName);
        }
    }

    private static void testComprehensiveExpectations(boolean detectionOnly) {
        if (detectionOnly) {
            System.out.println("Testing detection expectations from XML...");
        } else {
            System.out.println("Testing comprehensive expectations from XML...");
        }

        try {
            // Load XML expectations
            File xmlFile = new File("test-data/test-expectations.xml");
            if (!xmlFile.exists()) {
                assertTest("XML expectations file exists", false);
                return;
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new FileInputStream(xmlFile));
            document.getDocumentElement().normalize();

            NodeList testCases = document.getElementsByTagName("test_case");
            for (int i = 0; i < testCases.getLength(); i++) {
                Element testCase = (Element) testCases.item(i);
                String fileName = testCase.getAttribute("file");

                // Reset file failure counter
                fileTestsFailed = 0;

                // Test file exists
                File testFile = new File("test-data/" + fileName);
                assertTest("Test file exists: " + fileName, testFile.exists());

                if (!testFile.exists()) {
                    System.out.println("❌ " + fileName);
                    continue;
                }

                try {
                    // Test logger type detection
                    String expectedType = getTextContent(testCase, "expected_type");
                    String actualType = testDetectionOnly("test-data/" + fileName);
                    assertTest("Detection for " + fileName + ": expected " + expectedType + ", got " + actualType,
                        expectedType.equals(actualType));

                    // Only run full tests if not in detection-only mode
                    if (!detectionOnly) {
                        // Create dataset and test expectations
                        ECUxDataset dataset = new ECUxDataset("test-data/" + fileName, null, null, 0);

                        // Test IDs
                        testExpectedIds(testCase, dataset, fileName);

                        // Test ID2s
                        testExpectedId2s(testCase, dataset, fileName);

                        // Test Units
                        testExpectedUnits(testCase, dataset, fileName);

                        // Test category mappings
                        testExpectedCategories(testCase, dataset, fileName);

                        // Test sanity check (first data cell)
                        testSanityCheck(testCase, dataset, fileName);
                    }

                } catch (Exception e) {
                    if (detectionOnly) {
                        assertTest("Can detect logger type for: " + fileName, false);
                    } else {
                        assertTest("Can parse file: " + fileName, false);
                    }
                    System.out.println("  Error " + (detectionOnly ? "detecting" : "parsing") + " " + fileName + ": " + e.getMessage());
                }

                // Print file summary
                if (fileTestsFailed == 0) {
                    System.out.println("✅ " + fileName);
                } else {
                    System.out.println("❌ " + fileName);
                    // Fail on first file that has errors
                    System.out.println("STOPPING AT FIRST FILE WITH ERRORS FOR DEBUGGING");
                    System.exit(1);
                }
            }

        } catch (Exception e) {
            assertTest("Load XML expectations", false);
            System.out.println("  Error loading XML: " + e.getMessage());
        }
    }

    /**
     * Test detection only without parsing headers or data
     * This is much faster and focuses only on the detection logic
     */
    private static String testDetectionOnly(String filePath) throws Exception {
        // Create a minimal dataset that only runs detection
        ECUxDataset dataset = new ECUxDataset(filePath, null, null, 0);

        // Return the detected logger type
        return dataset.getLogDetected();
    }

    private static void testExpectedIds(Element testCase, ECUxDataset dataset, String fileName) {
        NodeList expectedIds = testCase.getElementsByTagName("expected_ids");
        if (expectedIds.getLength() == 0) return;

        Element idsElement = (Element) expectedIds.item(0);
        NodeList idNodes = idsElement.getElementsByTagName("id");

        for (int i = 0; i < idNodes.getLength(); i++) {
            Element idElement = (Element) idNodes.item(i);
            int index = Integer.parseInt(idElement.getAttribute("index"));
            String expectedId = idElement.getTextContent();

            if (index < dataset.getIds().length) {
                String actualId = dataset.getIds()[index].id;
                assertTest("ID[" + index + "] for " + fileName + ": expected '" + expectedId + "', got '" + actualId + "'",
                    expectedId.equals(actualId));
            }
        }
    }

    private static void testExpectedId2s(Element testCase, ECUxDataset dataset, String fileName) {
        NodeList expectedId2s = testCase.getElementsByTagName("expected_id2s");
        if (expectedId2s.getLength() == 0) return;

        Element id2sElement = (Element) expectedId2s.item(0);
        NodeList id2Nodes = id2sElement.getElementsByTagName("id2");

        for (int i = 0; i < id2Nodes.getLength(); i++) {
            Element id2Element = (Element) id2Nodes.item(i);
            int index = Integer.parseInt(id2Element.getAttribute("index"));
            String expectedId2 = id2Element.getTextContent();

            if (index < dataset.getIds().length) {
                String actualId2 = dataset.getIds()[index].id2;
                assertTest("ID2[" + index + "] for " + fileName + ": expected '" + expectedId2 + "', got '" + actualId2 + "'",
                    expectedId2.equals(actualId2));
            }
        }
    }

    private static void testExpectedUnits(Element testCase, ECUxDataset dataset, String fileName) {
        NodeList expectedUnits = testCase.getElementsByTagName("expected_units");
        if (expectedUnits.getLength() == 0) return;

        Element unitsElement = (Element) expectedUnits.item(0);
        NodeList unitNodes = unitsElement.getElementsByTagName("unit");

        for (int i = 0; i < unitNodes.getLength(); i++) {
            Element unitElement = (Element) unitNodes.item(i);
            int index = Integer.parseInt(unitElement.getAttribute("index"));
            String expectedUnit = unitElement.getTextContent();

            if (index < dataset.getIds().length) {
                String actualUnit = dataset.getIds()[index].unit;
                assertTest("Unit[" + index + "] for " + fileName + ": expected '" + expectedUnit + "', got '" + actualUnit + "'",
                    expectedUnit.equals(actualUnit));
            }
        }
    }

    private static void testExpectedCategories(Element testCase, ECUxDataset dataset, String fileName) {
        NodeList expectedCategories = testCase.getElementsByTagName("expected_category_mappings");
        if (expectedCategories.getLength() == 0) return;

        Element categoriesElement = (Element) expectedCategories.item(0);
        NodeList categoryNodes = categoriesElement.getElementsByTagName("category");

        for (int i = 0; i < categoryNodes.getLength(); i++) {
            Element categoryElement = (Element) categoryNodes.item(i);
            String categoryName = categoryElement.getAttribute("name");
            String expectedField = categoryElement.getTextContent();

            // Get the actual field from the category
            Dataset.Column actualColumn = null;
            if ("pedal".equals(categoryName)) {
                actualColumn = dataset.get(org.nyet.ecuxplot.DataLogger.pedalField());
            } else if ("throttle".equals(categoryName)) {
                actualColumn = dataset.get(org.nyet.ecuxplot.DataLogger.throttleField());
            } else if ("gear".equals(categoryName)) {
                actualColumn = dataset.get(org.nyet.ecuxplot.DataLogger.gearField());
            }

            String actualField = (actualColumn != null) ? actualColumn.getId() : null;
            // Handle "null" as string vs actual null
            String expectedValue = "null".equals(expectedField) ? null : expectedField;
            String actualValue = actualField == null ? null : actualField;
            assertTest("Category[" + categoryName + "] for " + fileName + ": expected '" + (expectedValue == null ? "null" : expectedValue) + "', got '" + (actualValue == null ? "null" : actualValue) + "'",
                (expectedValue == null && actualValue == null) || (expectedValue != null && expectedValue.equals(actualValue)));
        }
    }

    private static void testSanityCheck(Element testCase, ECUxDataset dataset, String fileName) {
        NodeList sanityChecks = testCase.getElementsByTagName("sanity_check");
        if (sanityChecks.getLength() == 0) return;

        Element sanityElement = (Element) sanityChecks.item(0);
        int row = Integer.parseInt(sanityElement.getAttribute("row"));
        int col = Integer.parseInt(sanityElement.getAttribute("col"));
        String expectedValue = sanityElement.getTextContent();

        try {
            if (dataset.getColumns().size() > col) {
                Dataset.Column column = dataset.getColumns().get(col);
                if (column.data.size() > row) {
                    String actualValue = String.valueOf(column.data.get(row));
                    assertTest("Sanity check [" + row + "," + col + "] for " + fileName + ": expected '" + expectedValue + "', got '" + actualValue + "'",
                        expectedValue.equals(actualValue));
                }
            }
        } catch (Exception e) {
            assertTest("Sanity check for " + fileName, false);
        }
    }

    private static String getTextContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return "";
    }
}

// vim: set sw=4 ts=8 expandtab:
