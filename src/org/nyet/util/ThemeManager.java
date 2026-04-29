package org.nyet.util;

import java.awt.Color;
import java.util.prefs.Preferences;

import javax.swing.UIManager;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatDarkLaf;

/**
 * Manages UI theme (light/dark) using FlatLaf.
 * Supports "auto" (follow OS), "light", and "dark" modes.
 */
public class ThemeManager {

    public static final String THEME_AUTO = "auto";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    private static final String PREF_KEY = "theme";
    private static final String PALETTE_PREF_KEY = "colorPalette";

    private static String currentMode = THEME_AUTO;
    private static boolean darkActive = false;
    private static String currentPalette = "classic";

    // --- Color Palette Definitions ---
    // Each palette has 2 axis groups of 4 colors each (8 total series colors)

    /** Classic: the original ECUxPlot palette. */
    private static final Color[][] PALETTE_CLASSIC_LIGHT = {
        { new Color(0xff, 0x00, 0x00), new Color(0x00, 0x16, 0xff),
          new Color(0x00, 0xe0, 0xff), new Color(0xff, 0xc8, 0x00) },
        { new Color(0xb9, 0x00, 0xff), new Color(0x00, 0xff, 0x48),
          new Color(0xb2, 0xff, 0x00), new Color(0xff, 0x70, 0x00) }
    };
    private static final Color[][] PALETTE_CLASSIC_DARK = PALETTE_CLASSIC_LIGHT;

    /** Vivid: bright saturated colors inspired by modern dark dashboards. */
    private static final Color[][] PALETTE_VIVID_LIGHT = {
        { new Color(0xE8, 0x4D, 0x60), new Color(0x2D, 0x7D, 0xD2),
          new Color(0x17, 0xBE, 0xBB), new Color(0xF5, 0xA6, 0x23) },
        { new Color(0x9B, 0x59, 0xB6), new Color(0x27, 0xAE, 0x60),
          new Color(0xE6, 0x7E, 0x22), new Color(0x34, 0x98, 0xDB) }
    };
    private static final Color[][] PALETTE_VIVID_DARK = {
        { new Color(0xFF, 0x6B, 0x6B), new Color(0x4E, 0xC9, 0xF5),
          new Color(0x2E, 0xE6, 0xD6), new Color(0xFF, 0xD9, 0x3D) },
        { new Color(0xC3, 0x7D, 0xF5), new Color(0x6B, 0xE8, 0x8C),
          new Color(0xFF, 0x9F, 0x43), new Color(0x74, 0xB9, 0xFF) }
    };

    /** Neon: high-contrast glowing colors for dark backgrounds. */
    private static final Color[][] PALETTE_NEON_LIGHT = {
        { new Color(0xE0, 0x3E, 0x7A), new Color(0x18, 0x6F, 0xCC),
          new Color(0x00, 0xB8, 0x9C), new Color(0xD4, 0xAC, 0x0D) },
        { new Color(0x8E, 0x44, 0xAD), new Color(0x28, 0xA7, 0x45),
          new Color(0xE0, 0x6C, 0x15), new Color(0x2E, 0x86, 0xC1) }
    };
    private static final Color[][] PALETTE_NEON_DARK = {
        { new Color(0xFF, 0x00, 0x80), new Color(0x00, 0xD4, 0xFF),
          new Color(0x00, 0xFF, 0xBF), new Color(0xFF, 0xE6, 0x00) },
        { new Color(0xBF, 0x40, 0xFF), new Color(0x39, 0xFF, 0x14),
          new Color(0xFF, 0x6E, 0xFF), new Color(0x40, 0xE0, 0xD0) }
    };

    /** Cool: blue/teal/purple tones. */
    private static final Color[][] PALETTE_COOL_LIGHT = {
        { new Color(0x2C, 0x73, 0xD2), new Color(0x00, 0x89, 0x97),
          new Color(0x6C, 0x5B, 0xBE), new Color(0x44, 0x8A, 0xFF) },
        { new Color(0x84, 0x5E, 0xC2), new Color(0x08, 0xB2, 0xB2),
          new Color(0x36, 0x54, 0x86), new Color(0x5A, 0x9E, 0x6F) }
    };
    private static final Color[][] PALETTE_COOL_DARK = {
        { new Color(0x55, 0x9F, 0xFF), new Color(0x00, 0xCC, 0xBB),
          new Color(0x9D, 0x8C, 0xF5), new Color(0x6E, 0xC6, 0xFF) },
        { new Color(0xB0, 0x8C, 0xEE), new Color(0x20, 0xE3, 0xCE),
          new Color(0x64, 0x9E, 0xD6), new Color(0x77, 0xDD, 0x77) }
    };

    /** All palette names in display order. */
    public static final String[] PALETTE_NAMES = { "Classic", "Vivid", "Neon", "Cool" };
    private static final String[] PALETTE_KEYS = { "classic", "vivid", "neon", "cool" };

    /**
     * Initialize the theme at application startup.
     * Must be called before any Swing components are created.
     */
    public static void initialize(Preferences prefs) {
        currentMode = prefs.get(PREF_KEY, THEME_AUTO);
        currentPalette = prefs.get(PALETTE_PREF_KEY, "classic");
        applyThemeInternal();
    }

    /**
     * Apply a theme mode and save the preference.
     * @param mode one of THEME_AUTO, THEME_LIGHT, THEME_DARK
     * @param prefs preferences node to save to
     */
    public static void applyTheme(String mode, Preferences prefs) {
        currentMode = mode;
        prefs.put(PREF_KEY, mode);
        applyThemeInternal();
    }

    private static void applyThemeInternal() {
        try {
            boolean wantDark;
            switch (currentMode) {
                case THEME_DARK:
                    wantDark = true;
                    break;
                case THEME_LIGHT:
                    wantDark = false;
                    break;
                default: // auto
                    wantDark = detectOSDarkMode();
                    break;
            }

            if (wantDark) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            darkActive = wantDark;
        } catch (Exception e) {
            System.err.println("Error setting FlatLaf theme: " + e);
            // Fall back to system L&F
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                // ignore
            }
            darkActive = false;
        }
    }

    private static boolean detectOSDarkMode() {
        // FlatLaf provides OS dark mode detection
        return FlatLaf.isLafDark() || isSystemDarkMode();
    }

    private static boolean isSystemDarkMode() {
        // macOS: check "AppleInterfaceStyle" via defaults
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            try {
                Process p = Runtime.getRuntime().exec(
                    new String[]{"defaults", "read", "-g", "AppleInterfaceStyle"});
                int exitCode = p.waitFor();
                if (exitCode == 0) {
                    byte[] buf = p.getInputStream().readAllBytes();
                    return new String(buf).trim().equalsIgnoreCase("Dark");
                }
            } catch (Exception e) {
                // not dark
            }
        }
        // Windows/Linux: FlatLaf handles via isLafDark() above
        return false;
    }

    /** Returns true if the active theme is dark. */
    public static boolean isDark() {
        return darkActive;
    }

    /** Returns the current mode setting ("auto", "light", or "dark"). */
    public static String getCurrentMode() {
        return currentMode;
    }

    /** Returns the current palette key (e.g., "classic", "vivid"). */
    public static String getCurrentPalette() {
        return currentPalette;
    }

    /**
     * Set the active color palette and save the preference.
     * @param paletteKey one of "classic", "vivid", "neon", "cool"
     * @param prefs preferences node to save to
     */
    public static void setPalette(String paletteKey, Preferences prefs) {
        currentPalette = paletteKey;
        prefs.put(PALETTE_PREF_KEY, paletteKey);
    }

    /**
     * Returns the series color palette for the current theme and palette setting.
     * @return Color[2][4] — two axis groups of 4 colors each
     */
    public static Color[][] getSeriesColors() {
        switch (currentPalette) {
            case "vivid":
                return darkActive ? PALETTE_VIVID_DARK : PALETTE_VIVID_LIGHT;
            case "neon":
                return darkActive ? PALETTE_NEON_DARK : PALETTE_NEON_LIGHT;
            case "cool":
                return darkActive ? PALETTE_COOL_DARK : PALETTE_COOL_LIGHT;
            default: // classic
                return darkActive ? PALETTE_CLASSIC_DARK : PALETTE_CLASSIC_LIGHT;
        }
    }

    // --- Semantic colors for custom renderers ---

    /** Chart plot background. */
    public static Color getChartBackground() {
        return darkActive ? new Color(43, 43, 43) : Color.WHITE;
    }

    /** Chart gridline color. */
    public static Color getChartGridlineColor() {
        return darkActive ? new Color(80, 80, 80) : new Color(220, 220, 220);
    }

    /** Chart axis label and tick label color. */
    public static Color getChartAxisLabelColor() {
        return darkActive ? new Color(200, 200, 200) : Color.BLACK;
    }

    /** Chart legend background. */
    public static Color getChartLegendBackground() {
        return darkActive ? new Color(50, 50, 50) : Color.WHITE;
    }

    /** Chart legend text color. */
    public static Color getChartLegendTextColor() {
        return darkActive ? new Color(210, 210, 210) : Color.BLACK;
    }

    /** FilterWindow: PASS status row background. */
    public static Color getPassBackground() {
        return darkActive ? new Color(40, 80, 40) : new Color(200, 255, 200);
    }

    /** FilterWindow: FAIL status row background. */
    public static Color getFailBackground() {
        return darkActive ? new Color(100, 40, 40) : new Color(255, 200, 200);
    }

    /** SmoothingWindow: even range background. */
    public static Color getRangeEvenBackground() {
        return darkActive ? new Color(40, 40, 65) : new Color(230, 230, 255);
    }

    /** SmoothingWindow: odd range background. */
    public static Color getRangeOddBackground() {
        return darkActive ? new Color(60, 60, 40) : new Color(255, 255, 230);
    }

    /** SmoothingWindow: boundary sample background (on non-white base). */
    public static Color getBoundaryBackground() {
        return darkActive ? new Color(80, 65, 40) : new Color(255, 240, 200);
    }

    /** SmoothingWindow: raw data sign reversal background. */
    public static Color getSignReversalRawBackground() {
        return darkActive ? new Color(100, 100, 40) : new Color(255, 255, 150);
    }

    /** SmoothingWindow: smoothed data sign reversal background. */
    public static Color getSignReversalSmoothedBackground() {
        return darkActive ? new Color(120, 50, 50) : new Color(255, 150, 150);
    }

    /** Default table cell background (replaces Color.WHITE). */
    public static Color getTableBackground() {
        return darkActive
            ? UIManager.getColor("Table.background")
            : Color.WHITE;
    }

    /** Default text foreground (replaces Color.BLACK). */
    public static Color getTextForeground() {
        return darkActive
            ? UIManager.getColor("Table.foreground")
            : Color.BLACK;
    }

    /** RangeSelectorWindow: group node text. */
    public static Color getGroupNodeColor() {
        return darkActive ? new Color(100, 200, 100) : new Color(0, 100, 0);
    }

    /** RangeSelectorWindow: file node text. */
    public static Color getFileNodeColor() {
        return darkActive ? new Color(210, 210, 210) : Color.BLACK;
    }

    /** RangeSelectorWindow: range node text. */
    public static Color getRangeNodeColor() {
        return darkActive ? new Color(100, 150, 255) : Color.BLUE;
    }

    /** Exception dialog: main panel background. */
    public static Color getDialogBackground() {
        return darkActive
            ? UIManager.getColor("Panel.background")
            : Color.WHITE;
    }

    /** Exception dialog: message text color. */
    public static Color getDialogMessageColor() {
        return darkActive ? new Color(200, 200, 200) : new Color(60, 60, 60);
    }

    /** Exception dialog: details area background. */
    public static Color getDialogDetailsBackground() {
        return darkActive ? new Color(50, 50, 50) : new Color(248, 248, 248);
    }

    /** Exception dialog: details area text color. */
    public static Color getDialogDetailsTextColor() {
        return darkActive ? new Color(180, 180, 180) : new Color(80, 80, 80);
    }

    /** Exception dialog: border color. */
    public static Color getDialogBorderColor() {
        return darkActive ? new Color(80, 80, 80) : new Color(200, 200, 200);
    }

    /** Exception dialog: OK button background. */
    public static Color getDialogButtonBackground() {
        return darkActive ? new Color(60, 130, 200) : new Color(0, 120, 215);
    }

    /** Exception dialog: OK button foreground. */
    public static Color getDialogButtonForeground() {
        return Color.WHITE;
    }
}

// vim: set sw=4 ts=8 expandtab:
