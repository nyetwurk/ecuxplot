package test.java;

import org.nyet.ecuxplot.ECUxDataset;
import org.nyet.ecuxplot.UnitConstants;
import org.nyet.logfile.Dataset;

/**
 * Orthogonal unit tests for unit conversion functionality (Issue #103)
 * Tests core unit conversion logic without full CSV parsing overhead
 */
public class UnitConversionTest {

    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("=== Unit Conversion Tests (Issue #103) ===");
        System.out.println();

        try {
            // Create minimal test dataset
            ECUxDataset dataset = new ECUxDataset("test-data/unit-test-minimal.csv", null, null, 0);

            // Test 1: Column Storage with targetId
            testColumnStorageWithTargetId(dataset);

            // Test 2: getData(Key, Range) Routing
            testGetDataRouting(dataset);

            // Test 3: Both Native and Converted Simultaneously
            testNativeAndConvertedTogether(dataset);

            // Test 4: Recursion Safety
            testRecursionSafety(dataset);

            // Test 5: findUnits() Simulation
            testFindUnits(dataset);

        } catch (Exception e) {
            System.out.println("❌ Failed to create test dataset: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        // Print results
        System.out.println();
        System.out.println("=== Test Results ===");
        System.out.println("Tests run: " + testsRun);
        System.out.println("Tests passed: " + testsPassed);
        System.out.println("Tests failed: " + testsFailed);

        if (testsFailed == 0) {
            System.out.println("✅ All tests passed!");
            System.exit(0);
        } else {
            System.out.println("❌ " + testsFailed + " tests failed!");
            System.exit(1);
        }
    }

    private static void assertTest(String testName, boolean condition) {
        testsRun++;
        if (condition) {
            testsPassed++;
        } else {
            testsFailed++;
            System.out.println("  ❌ " + testName);
        }
    }

    /**
     * Test 1: Verify converted columns are stored with correct targetId
     */
    private static void testColumnStorageWithTargetId(ECUxDataset dataset) {
        System.out.println("Test 1: Column Storage with targetId");

        // Get native column first (should exist)
        Dataset.Column nativeCol = dataset.get("VehicleSpeed");
        assertTest("Native VehicleSpeed exists", nativeCol != null);
        if (nativeCol == null) return;

        // Verify native has correct units (should be km/h for ECUX)
        String nativeUnit = nativeCol.getUnits();
        assertTest("Native VehicleSpeed has units", nativeUnit != null && nativeUnit.length() > 0);

        // Request converted column - should be stored with full ID
        Dataset.Column convertedCol = dataset.get("VehicleSpeed (mph)");
        assertTest("Converted VehicleSpeed (mph) exists", convertedCol != null);
        if (convertedCol == null) return;

        // Verify converted column has correct ID (full ID, not base ID)
        assertTest("Converted column ID is 'VehicleSpeed (mph)'",
            convertedCol.getId().equals("VehicleSpeed (mph)"));

        // Verify converted column has correct units
        assertTest("Converted column units are 'mph'",
            convertedCol.getUnits().equals(UnitConstants.UNIT_MPH));

        // Verify they are different columns
        assertTest("Native and converted are different columns",
            nativeCol != convertedCol);
    }

    /**
     * Test 2: Verify getData(Key, Range) routes through unit conversion
     */
    private static void testGetDataRouting(ECUxDataset dataset) {
        System.out.println("Test 2: getData(Key, Range) Routing");

        // Create Key with full ID (unit conversion format)
        Dataset.Key key = dataset.new Key("VehicleSpeed (mph)", dataset);
        // Range is a non-static inner class, so must use dataset instance
        Dataset.Range range = dataset.new Range(0, Math.min(10, dataset.length() - 1));

        // Call getData - should route through unit conversion
        double[] data = dataset.getData(key, range);
        assertTest("getData returns data for converted Key", data != null && data.length > 0);
        if (data == null || data.length == 0) return;

        // Verify data is actually converted (compare with native)
        Dataset.Key nativeKey = dataset.new Key("VehicleSpeed", dataset);
        double[] nativeData = dataset.getData(nativeKey, range);
        assertTest("Native getData returns data", nativeData != null && nativeData.length > 0);
        if (nativeData == null || nativeData.length == 0) return;

        // Converted should be different from native (mph vs km/h)
        // mph should be approximately kmh / 1.609
        if (data.length > 0 && nativeData.length > 0) {
            double expectedMph = nativeData[0] / UnitConstants.KMH_PER_MPH;
            double actualMph = data[0];
            double tolerance = 0.01; // Allow small floating point errors
            assertTest("Converted data is in mph (expected ~" + expectedMph + ", got " + actualMph + ")",
                Math.abs(actualMph - expectedMph) < tolerance);
        }
    }

    /**
     * Test 3: Verify both native and converted can exist simultaneously
     */
    private static void testNativeAndConvertedTogether(ECUxDataset dataset) {
        System.out.println("Test 3: Both Native and Converted Together");

        // Get both columns
        Dataset.Column nativeCol = dataset.get("VehicleSpeed");
        Dataset.Column convertedCol = dataset.get("VehicleSpeed (mph)");
        Dataset.Column convertedCol2 = dataset.get("VehicleSpeed (mph)"); // Second request

        assertTest("Native column exists", nativeCol != null);
        assertTest("Converted column exists", convertedCol != null);
        assertTest("Second request returns same converted column (cached)",
            convertedCol == convertedCol2);

        // Both should have correct units
        if (nativeCol != null) {
            assertTest("Native has base units", nativeCol.getUnits() != null);
        }
        if (convertedCol != null) {
            assertTest("Converted has mph units", convertedCol.getUnits().equals(UnitConstants.UNIT_MPH));
        }

        // Test with different field
        Dataset.Column boostNative = dataset.get("BoostPressureActual");
        Dataset.Column boostPsi = dataset.get("BoostPressureActual (PSI)");
        assertTest("Boost native exists", boostNative != null);
        assertTest("Boost converted (PSI) exists", boostPsi != null);
        if (boostNative != null && boostPsi != null) {
            assertTest("Boost native and converted are different", boostNative != boostPsi);
            assertTest("Boost converted ID is 'BoostPressureActual (PSI)'",
                boostPsi.getId().equals("BoostPressureActual (PSI)"));
        }
    }

    /**
     * Test 4: Verify recursion safety - no infinite loops
     */
    private static void testRecursionSafety(ECUxDataset dataset) {
        System.out.println("Test 4: Recursion Safety");

        // Request converted column - should complete without infinite recursion
        Dataset.Column converted = null;
        try {
            converted = dataset.get("VehicleSpeed (mph)");
            assertTest("Unit conversion completes without recursion", true);
        } catch (StackOverflowError e) {
            assertTest("Unit conversion causes stack overflow (recursion issue)", false);
            return;
        }

        // Verify result is correct
        if (converted != null) {
            assertTest("Converted column has correct ID after recursion",
                converted.getId().equals("VehicleSpeed (mph)"));
        }

        // Test with calculated column that might trigger recursion
        // (if we request WHP in different units, it should still work)
        try {
            Dataset.Column whp = dataset.get("WHP");
            if (whp != null) {
                // If WHP exists, try requesting it with unit conversion
                // This would trigger recursion if there's an issue
                Dataset.Column whpHp = dataset.get("WHP (HP)");
                assertTest("Calculated column unit conversion doesn't recurse", whpHp != null || whp == null);
            }
        } catch (StackOverflowError e) {
            assertTest("Calculated column unit conversion causes recursion", false);
        }
    }

    /**
     * Test 5: Simulate findUnits() behavior
     */
    private static void testFindUnits(ECUxDataset dataset) {
        System.out.println("Test 5: findUnits() Simulation");

        // Create Keys with different formats
        Dataset.Key nativeKey = dataset.new Key("VehicleSpeed", dataset);
        Dataset.Key convertedKey = dataset.new Key("VehicleSpeed (mph)", dataset);

        // Simulate findUnits() lookup - get column and check units
        String nativeLookupId = nativeKey.getString();
        String convertedLookupId = convertedKey.getString();

        // Get columns to check units (simulating findUnits logic)
        Dataset.Column nativeCol = dataset.get(nativeLookupId);
        Dataset.Column convertedCol = dataset.get(convertedLookupId);

        assertTest("Native column exists for units lookup", nativeCol != null);
        assertTest("Converted column exists for units lookup", convertedCol != null);
        if (nativeCol != null) {
            String nativeUnits = nativeCol.getUnits();
            assertTest("Native Key units lookup works", nativeUnits != null && nativeUnits.length() > 0);
        }
        if (convertedCol != null) {
            String convertedUnits = convertedCol.getUnits();
            assertTest("Converted Key units lookup works", convertedUnits != null && convertedUnits.length() > 0);
            if (convertedUnits != null) {
                assertTest("Converted units are 'mph'", convertedUnits.equals(UnitConstants.UNIT_MPH));
            }
        }

        // Verify units are different (native vs converted)
        if (nativeCol != null && convertedCol != null) {
            String nativeUnits = nativeCol.getUnits();
            String convertedUnits = convertedCol.getUnits();
            if (nativeUnits != null && convertedUnits != null) {
                assertTest("Native and converted units are different",
                    !nativeUnits.equals(convertedUnits));
            }
        }
    }
}

// vim: set sw=4 ts=8 expandtab:

