# Preferences and Profiles Locations

ECUxPlot stores user data in two ways: **profiles** (saved vehicle/profile files on disk) and **preferences** (application settings via the Java Preferences API). Locations are platform-specific.

## Profiles (saved profiles on disk)

Profiles are stored in a `profiles` subdirectory of the application data directory. This is where user-saved vehicle profiles appear (e.g. from **Vehicle Profiles → Save profile**).

| Platform | Data directory | Profiles path |
|----------|-----------------|---------------|
| **macOS** | `~/.ECUxPlot` | `~/.ECUxPlot/profiles` |
| **Linux** | `~/.ECUxPlot` | `~/.ECUxPlot/profiles` |
| **Windows** | `%USERPROFILE%\Application Data\ECUxPlot` | `%USERPROFILE%\Application Data\ECUxPlot\profiles` |

On Windows Vista and later, `Application Data` under the user profile is a junction to `AppData\Roaming`, so the effective path is often `%USERPROFILE%\AppData\Roaming\ECUxPlot\profiles`.

Built-in (shipped) profiles are loaded from the `profiles` directory next to the application (e.g. inside the app bundle or install directory); only the **custom** profiles path above is user-writable and documented here.

## Preferences (application settings)

Application preferences (window sizes, last files, axis choices, filter settings, vehicle constants, presets, etc.) are stored using the Java Preferences API (`Preferences.userNodeForPackage(ECUxPlot.class)`). The backing store is implementation-dependent.

| Platform | Where preferences are stored |
|----------|------------------------------|
| **macOS** | `~/Library/Preferences/org.nyet.ecuxplot.plist` (or under `~/.java/.userPrefs/` depending on JVM) |
| **Linux** | `~/.java/.userPrefs/` (package path under this; exact subdirs may be encoded) |
| **Windows** | Windows Registry: `HKEY_CURRENT_USER\Software\JavaSoft\Prefs\org\nyet\ecuxplot` |

The package used is `org.nyet.ecuxplot`. On Linux the logical path is under `~/.java/.userPrefs/org/nyet/ecuxplot/` (some JVMs use encoded directory names). On macOS, the Java Preferences API may use the plist path above instead of `.userPrefs/`.

## Clearing preferences (reset / fix corrupted prefs)

If preferences become corrupted or you want a full reset:

1. **Quit ECUxPlot** completely (all windows).
2. Remove the preferences for this application:
   - **macOS**: Delete `~/Library/Preferences/org.nyet.ecuxplot.plist`; if not present, check `~/.java/.userPrefs/` for `org` → `nyet` → `ecuxplot` (or encoded nodes).
   - **Linux**: Delete the ECUxPlot preferences under `~/.java/.userPrefs/` (look for `org` → `nyet` → `ecuxplot` or similarly named/encoded nodes).
   - **Windows**: Delete the registry key `HKEY_CURRENT_USER\Software\JavaSoft\Prefs\org\nyet\ecuxplot` (e.g. with `regedit`).
3. Start ECUxPlot again. It will use default settings and recreate preference storage.

To remove only **profiles** (saved vehicle profiles), delete or edit files in the **Profiles** path for your platform (see table above). Removing the `profiles` directory or its contents does not clear application preferences (window size, last files, etc.); use the steps above for that.
