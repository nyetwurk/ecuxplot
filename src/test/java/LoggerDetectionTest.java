package test.java;

import java.io.File;
import java.io.FileInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.nyet.ecuxplot.ECUxDataset;
import org.nyet.logfile.Dataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import ch.qos.logback.classic.Level;

/**
 * Test framework for logger detection and parsing testing
 * Supports both full testing and detection-only testing modes
 */
public class LoggerDetectionTest {

    private static final Logger logger = LoggerFactory.getLogger(LoggerDetectionTest.class);

    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static int testsFailed = 0;
    private static int fileTestsFailed = 0;

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

        // Check if detection-only mode
        boolean detectionOnly = args.length > 0 && "detection".equals(args[0]);

        if (detectionOnly) {
            logger.info("=== Logger Detection Only Tests ===");
        } else {
            logger.info("=== Logger Detection Tests ===");
        }
        logger.info("");

        // Test comprehensive expectations from XML
        testComprehensiveExpectations(detectionOnly);

        // Print results
        logger.info("");
        logger.info("=== Test Results ===");
        logger.info("Tests run:  {}", testsRun);
        logger.info("Tests passed:  {}", testsPassed);
        logger.info("Tests failed:  {}", testsFailed);

        if (testsFailed == 0) {
            logger.info("✅ All tests passed!");
            System.exit(0);
        } else {
            logger.info("❌  {}", testsFailed + " tests failed!");
            System.exit(1);
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
            logger.info("  ❌  {}", testName);
        }
    }

    private static void testComprehensiveExpectations(boolean detectionOnly) {
        if (detectionOnly) {
            logger.info("Testing detection expectations from XML...");
        } else {
            logger.info("Testing comprehensive expectations from XML...");
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
                    logger.info("❌  {}", fileName);
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

                        // Test Units (normalized - DatasetId.unit)
                        testExpectedUnits(testCase, dataset, fileName);

                        // Test Native Units (original unit intent - DatasetId.u2)
                        testExpectedNativeUnits(testCase, dataset, fileName);

                        // Test category mappings
                        testExpectedCategories(testCase, dataset, fileName);

                        // Test global required columns (tested once per logger, not per preset)
                        testGlobalRequiredColumns(dataset, fileName);

                        // Test preset defaults (verify canonical preset columns exist per log format expectations)
                        testExpectedPresets(testCase, dataset, fileName);

                        // Test sanity check (first data cell)
                        testSanityCheck(testCase, dataset, fileName);
                    }

                } catch (Exception e) {
                    if (detectionOnly) {
                        assertTest("Can detect logger type for: " + fileName, false);
                    } else {
                        assertTest("Can parse file: " + fileName, false);
                    }
                    logger.error("  Error {} {}: {}", (detectionOnly ? "detecting" : "parsing"), fileName, e.getMessage());
                }

                // Print file summary
                if (fileTestsFailed == 0) {
                    logger.info("✅  {}", fileName);
                } else {
                    logger.info("❌  {}", fileName);
                    // Fail on first file that has errors
                    logger.info("STOPPING AT FIRST FILE WITH ERRORS FOR DEBUGGING");
                    System.exit(1);
                }
            }

        } catch (Exception e) {
            assertTest("Load XML expectations", false);
            logger.error("  Error loading XML: {}", e.getMessage());
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

    /**
     * Test normalized units (DatasetId.unit).
     *
     * expected_us_units only lists columns that were normalized.
     * For columns not listed, assume unit == u2 (no normalization occurred).
     *
     * This allows:
     * 1. Separate expected_us_units / expected_metric_units sections for different unit preferences (US_CUSTOMARY vs METRIC)
     * 2. Minimal duplication (only list what changed)
     * 3. Native units as source of truth (exhaustive in expected_units)
     */
    private static void testExpectedUnits(Element testCase, ECUxDataset dataset, String fileName) {
        NodeList expectedNormalizedUnitsList = testCase.getElementsByTagName("expected_us_units");
        if (expectedNormalizedUnitsList.getLength() == 0) return;

        Element normalizedUnitsElement = (Element) expectedNormalizedUnitsList.item(0);
        NodeList unitNodes = normalizedUnitsElement.getElementsByTagName("unit");

        // Build map of expected normalized units (only for columns that were normalized)
        java.util.Map<Integer, String> expectedNormalizedUnits = new java.util.HashMap<>();
        for (int i = 0; i < unitNodes.getLength(); i++) {
            Element unitElement = (Element) unitNodes.item(i);
            int index = Integer.parseInt(unitElement.getAttribute("index"));
            String expectedUnit = unitElement.getTextContent();
            expectedNormalizedUnits.put(index, expectedUnit);
        }

        // Get expected native units to determine which columns should be normalized
        NodeList expectedUnitsList = testCase.getElementsByTagName("expected_units");
        if (expectedUnitsList.getLength() == 0) return;

        Element nativeUnitsElement = (Element) expectedUnitsList.item(0);
        NodeList nativeUnitNodes = nativeUnitsElement.getElementsByTagName("unit");

        // Build map of expected native units (exhaustive - all columns)
        java.util.Map<Integer, String> expectedNativeUnitsMap = new java.util.HashMap<>();
        for (int i = 0; i < nativeUnitNodes.getLength(); i++) {
            Element nativeUnitElement = (Element) nativeUnitNodes.item(i);
            int index = Integer.parseInt(nativeUnitElement.getAttribute("index"));
            String expectedNativeUnit = nativeUnitElement.getTextContent();
            expectedNativeUnitsMap.put(index, expectedNativeUnit);
        }

        // Test normalized units
        // For columns in expected_us_units: verify normalized unit matches
        // For columns NOT in expected_us_units: verify unit == u2 (no normalization)
        for (int index = 0; index < dataset.getIds().length; index++) {
            if (!expectedNativeUnitsMap.containsKey(index)) {
                continue; // Skip columns not in expected_units
            }

            String actualUnit = dataset.getIds()[index].unit;
            String actualU2 = dataset.getIds()[index].u2;

            if (expectedNormalizedUnits.containsKey(index)) {
                // Column was normalized - verify normalized unit matches
                String expectedUnit = expectedNormalizedUnits.get(index);
                assertTest("Unit[" + index + "] for " + fileName + ": expected '" + expectedUnit + "', got '" + actualUnit + "'",
                    expectedUnit.equals(actualUnit));
            } else {
                // Column was NOT normalized - verify unit == u2
                assertTest("Unit[" + index + "] for " + fileName + ": expected unit == u2 (no normalization), got unit='" + actualUnit + "', u2='" + actualU2 + "'",
                    actualUnit == actualU2 || (actualUnit != null && actualUnit.equals(actualU2)));
            }
        }
    }

    /**
     * Test native units (DatasetId.u2).
     *
     * EXHAUSTIVE APPROACH: expected_units lists ALL columns (source of truth from CSV).
     * This is the exhaustive list - every column should have a native unit entry.
     */
    private static void testExpectedNativeUnits(Element testCase, ECUxDataset dataset, String fileName) {
        NodeList expectedUnitsList = testCase.getElementsByTagName("expected_units");
        if (expectedUnitsList.getLength() == 0) return;

        Element nativeUnitsElement = (Element) expectedUnitsList.item(0);
        NodeList nativeUnitNodes = nativeUnitsElement.getElementsByTagName("unit");

        for (int i = 0; i < nativeUnitNodes.getLength(); i++) {
            Element nativeUnitElement = (Element) nativeUnitNodes.item(i);
            int index = Integer.parseInt(nativeUnitElement.getAttribute("index"));
            String expectedNativeUnit = nativeUnitElement.getTextContent();

            if (index < dataset.getIds().length) {
                String actualNativeUnit = dataset.getIds()[index].u2;
                assertTest("NativeUnit[" + index + "] for " + fileName + ": expected '" + expectedNativeUnit + "', got '" + actualNativeUnit + "'",
                    expectedNativeUnit.equals(actualNativeUnit));
            }
        }
    }

    private static void testExpectedCategories(Element testCase, ECUxDataset dataset, String fileName) {
        NodeList expectedCategories = testCase.getElementsByTagName("expected_filter_columns");
        if (expectedCategories.getLength() == 0) return;

        Element categoriesElement = (Element) expectedCategories.item(0);
        NodeList categoryNodes = categoriesElement.getElementsByTagName("category");

        for (int i = 0; i < categoryNodes.getLength(); i++) {
            Element categoryElement = (Element) categoryNodes.item(i);
            String categoryName = categoryElement.getAttribute("name");
            String expectedIndexStr = categoryElement.getTextContent();

            // Get the actual column from the category
            Dataset.Column actualColumn = null;
            if ("pedal".equals(categoryName)) {
                actualColumn = dataset.get(org.nyet.ecuxplot.DataLogger.pedalField());
            } else if ("throttle".equals(categoryName)) {
                actualColumn = dataset.get(org.nyet.ecuxplot.DataLogger.throttleField());
            } else if ("gear".equals(categoryName)) {
                actualColumn = dataset.get(org.nyet.ecuxplot.DataLogger.gearField());
            }

            // Find the column index of the actual column
            Integer actualIndex = null;
            if (actualColumn != null) {
                java.util.ArrayList<Dataset.Column> columns = dataset.getColumns();
                for (int j = 0; j < columns.size(); j++) {
                    if (columns.get(j) == actualColumn) {
                        actualIndex = j;
                        break;
                    }
                }
            }

            // Handle "null", empty strings, and null (from self-closing XML tags) as equivalent
            Integer expectedIndex = null;
            String trimmed = (expectedIndexStr != null ? expectedIndexStr.trim() : "");
            if (!"null".equalsIgnoreCase(trimmed) && !trimmed.isEmpty()) {
                try {
                    expectedIndex = Integer.parseInt(trimmed);
                } catch (NumberFormatException e) {
                    // Invalid index, will fail test
                }
            }

            assertTest("Category[" + categoryName + "] for " + fileName + ": expected column index " + (expectedIndex == null ? "null" : expectedIndex.toString()) + ", got " + (actualIndex == null ? "null" : actualIndex.toString()),
                (expectedIndex == null && actualIndex == null) || (expectedIndex != null && expectedIndex.equals(actualIndex)));
        }
    }

    private static void testGlobalRequiredColumns(ECUxDataset dataset, String fileName) {
        // Read from DataLogger, not test-expectations.xml
        String[] globalColumns = org.nyet.ecuxplot.DataLogger.getGlobalRequiredColumns();
        if (globalColumns.length == 0) {
            return;
        }

        for (String columnName : globalColumns) {
            Dataset.Column column = dataset.get(columnName);
            assertTest("Global required column for " + fileName + ": '" + columnName + "' should exist",
                    column != null);
        }
    }

    private static void expandProfileReferences(Element expectedPresetsElement, java.util.Map<String, java.util.Set<String>> expectedColumnsByPreset) {
        // Find profile references
        NodeList profileRefs = expectedPresetsElement.getElementsByTagName("profile_ref");
        for (int i = 0; i < profileRefs.getLength(); i++) {
            Element profileRef = (Element) profileRefs.item(i);
            String profileName = profileRef.getAttribute("name");

            // Get profile from DataLogger
            java.util.Map<String, java.util.List<org.nyet.ecuxplot.DataLogger.ProfileItem>> profile =
                org.nyet.ecuxplot.DataLogger.getPresetSupportProfile(profileName);
            if (profile == null) {
                continue;
            }

            // For each preset in the profile, expand it and add to expected columns
            for (java.util.Map.Entry<String, java.util.List<org.nyet.ecuxplot.DataLogger.ProfileItem>> entry : profile.entrySet()) {
                String presetName = entry.getKey();
                java.util.Set<String> columns = org.nyet.ecuxplot.DataLogger.expandProfilePreset(profileName, presetName);

                // Merge with existing expected columns for this preset
                java.util.Set<String> existing = expectedColumnsByPreset.getOrDefault(presetName, new java.util.HashSet<>());
                existing.addAll(columns);
                expectedColumnsByPreset.put(presetName, existing);
            }
        }
    }

    private static void expandCategories(Element presetElement, java.util.Set<String> expectedColumns) {
        // Find category references in preset
        NodeList categoryRefs = presetElement.getElementsByTagName("category");
        for (int i = 0; i < categoryRefs.getLength(); i++) {
            Element categoryRef = (Element) categoryRefs.item(i);
            String categoryName = categoryRef.getAttribute("name");

            // Read from DataLogger, not test-expectations.xml
            String[] categoryColumns = org.nyet.ecuxplot.DataLogger.getAxisPresetCategory(categoryName);
            if (categoryColumns.length > 0) {
                for (String column : categoryColumns) {
                    expectedColumns.add(column);
                }
            }
        }
    }

    private static void testExpectedPresets(Element testCase, ECUxDataset dataset, String fileName) {
        // Get all preset defaults from DataLogger
        String[] presetNames = org.nyet.ecuxplot.DataLogger.getPresetDefaultNames();
        if (presetNames.length == 0) {
            // No preset defaults defined, skip test
            return;
        }

        // Check if test expectations define which preset columns should exist for this log format
        NodeList expectedPresets = testCase.getElementsByTagName("expected_preset_columns");
        if (expectedPresets.getLength() == 0) {
            // No preset expectations defined for this log format, skip test
            return;
        }

        Element expectedPresetsElement = (Element) expectedPresets.item(0);

        // Build map of expected columns per preset
        java.util.Map<String, java.util.Set<String>> expectedColumnsByPreset = new java.util.HashMap<>();

        // First, expand profile references
        expandProfileReferences(expectedPresetsElement, expectedColumnsByPreset);

        // Then, expand preset elements with categories and direct columns
        NodeList presetNodes = expectedPresetsElement.getElementsByTagName("preset");
        for (int i = 0; i < presetNodes.getLength(); i++) {
            Element presetElement = (Element) presetNodes.item(i);
            String presetName = presetElement.getAttribute("name");
            java.util.Set<String> expectedColumns = expectedColumnsByPreset.getOrDefault(presetName, new java.util.HashSet<>());

            // Expand categories first
            expandCategories(presetElement, expectedColumns);

            // Then add direct columns
            NodeList columnNodes = presetElement.getElementsByTagName("column");
            for (int j = 0; j < columnNodes.getLength(); j++) {
                String columnName = columnNodes.item(j).getTextContent().trim();
                expectedColumns.add(columnName);
            }
            expectedColumnsByPreset.put(presetName, expectedColumns);
        }

        // Test presets against expectations
        for (String presetName : presetNames) {
            org.nyet.ecuxplot.DataLogger.PresetDefault presetDefault = org.nyet.ecuxplot.DataLogger.getPresetDefault(presetName);
            if (presetDefault == null) {
                continue;
            }

            java.util.Set<String> expectedColumns = expectedColumnsByPreset.get(presetName);
            if (expectedColumns == null) {
                // No expectations for this preset in this log format, skip
                continue;
            }

            // Check xkey exists if expected
            if (expectedColumns.contains(presetDefault.xkey)) {
                Dataset.Column xkeyColumn = dataset.get(presetDefault.xkey);
                assertTest("Preset[" + presetName + "] xkey for " + fileName + ": '" + presetDefault.xkey + "' should exist",
                        xkeyColumn != null);
            }

            // Check ykeys0 exist if expected
            for (String ykey : presetDefault.ykeys0) {
                if (expectedColumns.contains(ykey)) {
                    Dataset.Column ykeyColumn = dataset.get(ykey);
                    // Skip test for calculated columns (HP, TQ, WHP, WTQ are calculated from RPM/Boost)
                    boolean isCalculated = ykey.matches("^(HP|TQ|WHP|WTQ|Sim .*|Calc .*|Boost Spool Rate .*)$");
                    if (!isCalculated) {
                        assertTest("Preset[" + presetName + "] ykeys0 for " + fileName + ": '" + ykey + "' should exist",
                                ykeyColumn != null);
                    }
                }
            }

            // Check ykeys1 exist if expected
            for (String ykey : presetDefault.ykeys1) {
                if (expectedColumns.contains(ykey)) {
                    Dataset.Column ykeyColumn = dataset.get(ykey);
                    boolean isCalculated = ykey.matches("^(HP|TQ|WHP|WTQ|Sim .*|Calc .*|Boost Spool Rate .*)$");
                    if (!isCalculated) {
                        assertTest("Preset[" + presetName + "] ykeys1 for " + fileName + ": '" + ykey + "' should exist",
                                ykeyColumn != null);
                    }
                }
            }

            // Check for pattern matches (patterns are prefixed with "pattern:" in expectedColumns)
            // Patterns are used when a preset needs ANY column matching a pattern (e.g., IgnitionRetardCyl.*)
            for (String expectedColumn : expectedColumns) {
                if (expectedColumn.startsWith("pattern:")) {
                    String pattern = expectedColumn.substring(8); // Remove "pattern:" prefix
                    // Check if any column in dataset matches the pattern
                    boolean found = false;
                    for (Dataset.DatasetId datasetId : dataset.getIds()) {
                        if (datasetId != null && datasetId.id != null && datasetId.id.matches(pattern)) {
                            found = true;
                            break;
                        }
                    }
                    assertTest("Preset[" + presetName + "] pattern for " + fileName + ": pattern '" + pattern + "' should match at least one column",
                            found);
                }
            }
        }
    }

    private static void testSanityCheck(Element testCase, ECUxDataset dataset, String fileName) {
        NodeList sanityChecks = testCase.getElementsByTagName("sanity_check");
        if (sanityChecks.getLength() == 0) return;

        Element sanityElement = (Element) sanityChecks.item(0);
        int row = Integer.parseInt(sanityElement.getAttribute("row"));
        int col = Integer.parseInt(sanityElement.getAttribute("col"));
        String expectedValue = sanityElement.getTextContent();

        // Debug printing
        logger.info("DEBUG sanity check for {}:", fileName);
        logger.info("  Expected: row={}, col={}, value='{}'", row, col, expectedValue);
        logger.info("  Dataset has {} columns", dataset.getColumns().size());
        if (dataset.getColumns().size() > 0) {
            for (int i = 0; i < Math.min(dataset.getColumns().size(), 5); i++) {
                Dataset.Column c = dataset.getColumns().get(i);
                String colName = dataset.getIds()[i].id;
                if (c.data.size() > 0) {
                    logger.info("  Column {}: name='{}', data size={}, first value={}", i, colName, c.data.size(), c.data.get(0));
                } else {
                    logger.info("  Column {}: name='{}', data size={}", i, colName, c.data.size());
                }
            }
        }

        try {
            if (dataset.getColumns().size() > col) {
                Dataset.Column column = dataset.getColumns().get(col);
                logger.info("  Column {} data size: {}", col, column.data.size());
                if (column.data.size() > row) {
                    String actualValue = String.valueOf(column.data.get(row));
                    logger.info("  Actual value at [{},{}]: '{}'", row, col, actualValue);
                    assertTest("Sanity check [" + row + "," + col + "] for " + fileName + ": expected '" + expectedValue + "', got '" + actualValue + "'",
                        expectedValue.equals(actualValue));
                } else {
                    logger.error("  ERROR: Column {} only has {} rows, need row {}", col, column.data.size(), row);
                }
            } else {
                logger.error("  ERROR: Dataset only has {} columns, need column {}", dataset.getColumns().size(), col);
            }
        } catch (Exception e) {
            logger.error("  EXCEPTION: {}", e.getMessage(), e);
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
