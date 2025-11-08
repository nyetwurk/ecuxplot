package test.java;

import java.awt.Component;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

import javax.swing.AbstractButton;
import javax.swing.JMenu;
import javax.swing.JSeparator;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.nyet.ecuxplot.AxisMenu;
import org.nyet.ecuxplot.ECUxDataset;
import org.nyet.ecuxplot.Env;
import org.nyet.ecuxplot.Filter;
import org.nyet.logfile.Dataset.DatasetId;
import org.nyet.util.SubActionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import ch.qos.logback.classic.Level;

/**
 * Test framework for AxisMenu validation.
 * Tests menu structure, submenu routing, calculated field creation, and special cases.
 */
public class AxisMenuTest {

    private static final Logger logger = LoggerFactory.getLogger(AxisMenuTest.class);

    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static int testsFailed = 0;

    // Dummy listener for testing
    private static final SubActionListener dummyListener = new SubActionListener() {
        @Override
        public void actionPerformed(java.awt.event.ActionEvent e) {
            // No-op for testing (ActionListener interface)
        }
        @Override
        public void actionPerformed(java.awt.event.ActionEvent e, Comparable<?> id) {
            // No-op for testing (SubActionListener interface)
        }
    };

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

        logger.info("=== AxisMenu Validation Tests ===");
        logger.info("");

        try {
            // Create Env and Filter for testing (needed for Zeitronix Boost and other fields)
            Preferences testPrefs = Preferences.userNodeForPackage(AxisMenuTest.class).node("test");
            Env env = new Env(testPrefs);
            Filter filter = new Filter(testPrefs);
            filter.resetToDefaults(); // Ensure default values

            // Load comprehensive test dataset
            ECUxDataset dataset = new ECUxDataset("test-data/comprehensive-test-dataset.csv", env, filter, 0);
            logger.info("✅ Loaded comprehensive test dataset");
            logger.info("   Logger type: {}", dataset.getLogDetected());
            logger.info("");

            // Get all DatasetIds from the dataset
            Set<DatasetId> idSet = new HashSet<>();
            for (DatasetId id : dataset.getIds()) {
                if (id != null) {
                    idSet.add(id);
                }
            }
            DatasetId[] ids = idSet.toArray(new DatasetId[0]);
            logger.info("   Found {} fields in dataset", ids.length);
            logger.info("");

            // Create AxisMenu for Y-axis (not X-axis, so we can test calc menus)
            AxisMenu yAxisMenu = new AxisMenu("Y Axis", ids, dummyListener, false, new Comparable<?>[0]);

            // Load expectations from XML
            Map<String, String> fieldRouting = loadFieldRoutingExpectations();
            Map<String, String> calculatedFields = loadCalculatedFieldExpectations();

            // Run tests using XML expectations
            testFieldRouting(yAxisMenu, fieldRouting);
            testCalculatedFieldCreation(yAxisMenu, calculatedFields);
            testSubmenuStructure(yAxisMenu);

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
     * Load field routing expectations from XML.
     * @return Map of field name -> expected submenu name
     */
    private static Map<String, String> loadFieldRoutingExpectations() {
        Map<String, String> routing = new HashMap<>();
        try {
            File xmlFile = new File("test-data/axis-menu-expectations.xml");
            if (!xmlFile.exists()) {
                logger.warn("⚠️  XML expectations file not found, skipping field routing tests");
                return routing;
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
                logger.warn("⚠️  Test case for comprehensive-test-dataset.csv not found in XML");
                return routing;
            }

            // Load field_routing expectations
            NodeList fieldRoutingNodes = testCase.getElementsByTagName("field_routing");
            if (fieldRoutingNodes.getLength() > 0) {
                Element fieldRouting = (Element) fieldRoutingNodes.item(0);
                NodeList fields = fieldRouting.getElementsByTagName("field");
                for (int i = 0; i < fields.getLength(); i++) {
                    Element field = (Element) fields.item(i);
                    String name = field.getAttribute("name");
                    String submenu = field.getAttribute("submenu");
                    if (name != null && submenu != null && !name.isEmpty() && !submenu.isEmpty()) {
                        routing.put(name, submenu);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("⚠️  Error loading field routing expectations: {}", e.getMessage());
        }
        return routing;
    }

    /**
     * Load calculated field expectations from XML.
     * @return Map of calculated field name -> expected submenu name
     */
    private static Map<String, String> loadCalculatedFieldExpectations() {
        Map<String, String> calculated = new HashMap<>();
        try {
            File xmlFile = new File("test-data/axis-menu-expectations.xml");
            if (!xmlFile.exists()) {
                return calculated;
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
                return calculated;
            }

            // Load calculated_fields expectations
            NodeList calculatedFieldsNodes = testCase.getElementsByTagName("calculated_fields");
            if (calculatedFieldsNodes.getLength() > 0) {
                Element calculatedFields = (Element) calculatedFieldsNodes.item(0);
                NodeList fields = calculatedFields.getElementsByTagName("calculated_field");
                for (int i = 0; i < fields.getLength(); i++) {
                    Element field = (Element) fields.item(i);
                    String name = field.getAttribute("name");
                    String submenu = field.getAttribute("submenu");
                    if (name != null && submenu != null && !name.isEmpty() && !submenu.isEmpty()) {
                        calculated.put(name, submenu);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("⚠️  Error loading calculated field expectations: {}", e.getMessage());
        }
        return calculated;
    }

    /**
     * Test field routing using expectations from XML.
     * Note: Field routing patterns are defined in AxisMenu.java (SUBMENU_PATTERNS).
     * This test validates that the patterns work correctly for specific test cases.
     */
    private static void testFieldRouting(AxisMenu menu, Map<String, String> expectations) {
        logger.info("=== Test: Field Routing (from XML) ===");
        logger.info("  Note: Patterns are defined in AxisMenu.java (SUBMENU_PATTERNS)");
        logger.info("  This validates pattern matching for test dataset fields");

        if (expectations.isEmpty()) {
            logger.warn("  ⚠️  No field routing expectations loaded from XML");
            logger.info("");
            return;
        }

        for (Map.Entry<String, String> entry : expectations.entrySet()) {
            String fieldName = entry.getKey();
            String expectedSubmenu = entry.getValue();
            assertTest(fieldName + " in " + expectedSubmenu + " submenu",
                findMenuItemInSubmenu(menu, fieldName, expectedSubmenu));
        }

        logger.info("");
    }

    /**
     * Test calculated field creation using expectations from XML.
     * Note: Calculated fields are defined in AxisMenu.java (RPM_CALCULATED_FIELDS)
     * and AxisMenuHandlers.java (handler methods).
     * This test validates that calculated fields appear in menus when base fields are detected.
     */
    private static void testCalculatedFieldCreation(AxisMenu menu, Map<String, String> expectations) {
        logger.info("=== Test: Calculated Field Creation (from XML) ===");
        logger.info("  Note: Calculated fields defined in AxisMenu.RPM_CALCULATED_FIELDS");
        logger.info("  and AxisMenuHandlers handler methods");

        if (expectations.isEmpty()) {
            logger.warn("  ⚠️  No calculated field expectations loaded from XML");
            logger.info("");
            return;
        }

        for (Map.Entry<String, String> entry : expectations.entrySet()) {
            String fieldName = entry.getKey();
            String expectedSubmenu = entry.getValue();
            // Note: Calculated fields may not appear if dependencies are missing (e.g., Env is null)
            // But we still test that the menu items exist when they should
            assertTest(fieldName + " calculated field in " + expectedSubmenu + " submenu",
                findMenuItemInSubmenu(menu, fieldName, expectedSubmenu));
        }

        logger.info("");
    }

    /**
     * Load submenu names to check from XML.
     * @return List of submenu names
     */
    private static Set<String> loadSubmenusToCheck() {
        Set<String> submenus = new HashSet<>();
        try {
            File xmlFile = new File("test-data/axis-menu-expectations.xml");
            if (!xmlFile.exists()) {
                return submenus;
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
                return submenus;
            }

            // Load submenus_to_check
            NodeList testConfigNodes = testCase.getElementsByTagName("test_config");
            if (testConfigNodes.getLength() > 0) {
                Element testConfig = (Element) testConfigNodes.item(0);
                NodeList submenuNodes = testConfig.getElementsByTagName("submenus_to_check");
                if (submenuNodes.getLength() > 0) {
                    Element submenusElement = (Element) submenuNodes.item(0);
                    NodeList submenuList = submenusElement.getElementsByTagName("submenu");
                    for (int i = 0; i < submenuList.getLength(); i++) {
                        Element submenu = (Element) submenuList.item(i);
                        String name = submenu.getTextContent().trim();
                        if (!name.isEmpty()) {
                            submenus.add(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("⚠️  Error loading submenus to check: {}", e.getMessage());
        }
        return submenus;
    }

    /**
     * Test submenu structure (verify menus exist and have expected organization).
     */
    private static void testSubmenuStructure(AxisMenu menu) {
        logger.info("=== Test: Submenu Structure ===");

        Set<String> submenusToCheck = loadSubmenusToCheck();
        if (submenusToCheck.isEmpty()) {
            logger.warn("  ⚠️  No submenu expectations loaded from XML");
            logger.info("");
            return;
        }

        for (String submenuName : submenusToCheck) {
            assertTest(submenuName + " submenu exists", findSubmenu(menu, submenuName) != null);
        }

        logger.info("");
    }

    /**
     * Find a menu item by name in a specific submenu.
     * @param menu The parent menu
     * @param itemName The name of the menu item to find
     * @param submenuName The name of the submenu to search in
     * @return true if the item is found in the submenu
     */
    private static boolean findMenuItemInSubmenu(AxisMenu menu, String itemName, String submenuName) {
        JMenu submenu = findSubmenu(menu, submenuName);
        if (submenu == null) {
            return false;
        }

        // Search through all components in the submenu
        for (int i = 0; i < submenu.getMenuComponentCount(); i++) {
            Component comp = submenu.getMenuComponent(i);
            if (comp instanceof JSeparator) {
                continue;
            }
            if (comp instanceof AbstractButton) {
                AbstractButton button = (AbstractButton) comp;
                if (itemName.equals(button.getText())) {
                    return true;
                }
            }
            if (comp instanceof JMenu) {
                // Recursively search submenus
                if (findMenuItemInSubmenu((AxisMenu) comp, itemName, "")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Find a submenu by name.
     * @param menu The parent menu
     * @param submenuName The name of the submenu to find
     * @return The submenu if found, null otherwise
     */
    private static JMenu findSubmenu(AxisMenu menu, String submenuName) {
        // Search through all components in the menu
        for (int i = 0; i < menu.getMenuComponentCount(); i++) {
            Component comp = menu.getMenuComponent(i);
            if (comp instanceof JSeparator) {
                continue;
            }
            if (comp instanceof JMenu) {
                JMenu submenu = (JMenu) comp;
                // Check if this is the submenu we're looking for
                // Submenu names may have "..." appended
                String menuText = submenu.getText();
                if (menuText.equals(submenuName) || menuText.equals(submenuName + "...")) {
                    return submenu;
                }
                // Recursively search submenus
                JMenu found = findSubmenu((AxisMenu) submenu, submenuName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}

