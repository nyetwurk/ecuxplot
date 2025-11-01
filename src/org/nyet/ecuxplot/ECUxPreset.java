package org.nyet.ecuxplot;

import java.util.prefs.Preferences;

import org.nyet.util.Strings;

public class ECUxPreset extends Preset {

    private static boolean detectOldPrefs(Preferences prefs) {
        String[] names = null;
        boolean ret=false;
        try { names = prefs.childrenNames(); }
        catch (final Exception e) { return false; }
        for (final String s : names) {
            final Preferences n = prefs.node(s);
            try {
                if (n.nodeExists("ykeys") || n.nodeExists("ykeys2")) {
                    n.removeNode();
                    ret=true;
                }
            } catch (final Exception e) {}
        }
        if (ret) {
            createDefaultECUxPresets();
            MessageDialog.showMessageDialog(null, "Old presets detected. Resetting to default.");
        }
        return ret;
    }

    public static Preferences getPreferencesStatic() {
        final Preferences p=Preferences.userNodeForPackage(ECUxPlot.class).node("presets");
        detectOldPrefs(p);
        return p;
    }

    @Override
    public Preferences getPreferences() {
        return getPreferencesStatic();
    }

    public static void createDefaultECUxPresets() {
        // Load preset defaults from loggers.yaml (canonical column names)
        String[] presetNames = DataLogger.getPresetDefaultNames();
        for (String presetName : presetNames) {
            DataLogger.PresetDefault presetDefault = DataLogger.getPresetDefault(presetName);
            if (presetDefault != null) {
                // Convert String[] to Comparable<?>[] for ykeys
                Comparable<?>[] ykeys0 = new Comparable[presetDefault.ykeys0.length];
                for (int i = 0; i < presetDefault.ykeys0.length; i++) {
                    ykeys0[i] = presetDefault.ykeys0[i];
                }
                Comparable<?>[] ykeys1 = new Comparable[presetDefault.ykeys1.length];
                for (int i = 0; i < presetDefault.ykeys1.length; i++) {
                    ykeys1[i] = presetDefault.ykeys1[i];
                }

                // Use appropriate constructor based on array lengths
                if (ykeys0.length == 0 && ykeys1.length == 0) {
                    // No Y keys - just X key (shouldn't happen, but handle gracefully)
                    new ECUxPreset(presetName, presetDefault.xkey, new Comparable<?>[0]);
                } else if (ykeys0.length == 1 && ykeys1.length == 0) {
                    // Single Y key - use single ykey constructor
                    new ECUxPreset(presetName, presetDefault.xkey, ykeys0[0]);
                } else if (ykeys0.length == 1 && ykeys1.length == 1) {
                    // Two single Y keys - use ykey, ykey2 constructor
                    new ECUxPreset(presetName, presetDefault.xkey, ykeys0[0], ykeys1[0]);
                } else {
                    // Arrays - use array constructors with scatter
                    new ECUxPreset(presetName, presetDefault.xkey, ykeys0, ykeys1, presetDefault.scatter);
                }
            }
        }
    }

    public ECUxPreset(Comparable<?> name) { super(name);}
    public ECUxPreset(Comparable<?> name, Comparable<?> xkey, Comparable<?>[] ykeys) {
        this(name, xkey, ykeys, new Comparable [] {});
    }
    public ECUxPreset(Comparable<?> name, Comparable<?> xkey, Comparable<?> ykey) {
        this(name, xkey, new Comparable [] {ykey}, new Comparable<?>[] {});
    }
    public ECUxPreset(Comparable<?> name, Comparable<?> xkey, Comparable<?> ykey,
        Comparable<?> ykey2) {
        this(name, xkey, new Comparable [] {ykey}, new Comparable<?>[] {ykey2});
    }
    public ECUxPreset(Comparable<?> name, Comparable<?> xkey, Comparable<?>[] ykeys,
        Comparable<?>[] ykeys2) {
        this(name, xkey, ykeys, ykeys2, false);
    }
    public ECUxPreset(Comparable<?> name, Comparable<?> xkey, Comparable<?>[] ykeys,
        Comparable<?>[] ykeys2, boolean scatter)
    {
        super(name);
        this.xkey(xkey);
        this.ykeys(0,ykeys);
        this.ykeys(1,ykeys2);
        this.scatter(scatter);
    }

    // GETS
    public String tag() { return this.prefs.get("tag", this.prefs.name()); } // for Undo
    public Comparable<?> xkey() { return this.prefs.get("xkey", null); }
    public Comparable<?>[] ykeys(int which) { return this.getArray(which==0?"ykeys0":"ykeys1"); }
    public Boolean scatter() { return this.prefs.getBoolean("scatter", false); }

    // PUTS
    public void tag(String tag) { this.prefs.put("tag", tag); } // for Undo
    public void xkey(Comparable<?> xkey) { this.prefs.put("xkey", xkey.toString()); }
    public void ykeys(int which, Comparable<?>[] ykeys) { this.putArray(which==0?"ykeys0":"ykeys1", ykeys); }
    public void scatter(Boolean scatter) { this.prefs.putBoolean("scatter", scatter); }

    // misc
    @Override
    public String toString() {
        return this.prefs.name() + ": \"" +
            this.xkey() + "\" vs \"" +
            Strings.join(", ", this.ykeys(0)) + "\" and \"" +
            Strings.join(", ", this.ykeys(1)) + "\"";
    }
    public static String[] getPresets() {
        String [] ret = null;
        try {
            ret = getPreferencesStatic().childrenNames();
        } catch (final Exception e) {
            // If we can't read preferences, return empty array
            return new String[0];
        }
        if (ret!=null && ret.length>0) return ret;

        // Only create default presets if none exist and we haven't already tried
        // This prevents infinite recursion when xkey() calls getPresets()
        ECUxPreset.createDefaultECUxPresets();

        // Try one more time after creating defaults
        try {
            ret = getPreferencesStatic().childrenNames();
        } catch (final Exception e) {
            return new String[0];
        }
        return ret != null ? ret : new String[0];
    }
}

// vim: set sw=4 ts=8 expandtab:
