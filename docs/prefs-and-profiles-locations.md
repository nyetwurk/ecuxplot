# Preferences and Profiles Locations

ECUxPlot stores user data in two ways: **profiles** (saved vehicle/profile files on disk) and **preferences** (application settings via the Java Preferences API). Locations are platform-specific.

## Profiles (saved profiles on disk)

Profiles are stored in a `profiles` subdirectory of the application data directory. This is where user-saved vehicle profiles appear (e.g. from **Vehicle Profiles → Save profile**).

| Platform | Data directory | Profiles path |
|----------|-----------------|---------------|
| **macOS** | `~/.ECUxPlot` | `~/.ECUxPlot/profiles` |
| **Linux** | `~/.ECUxPlot` | `~/.ECUxPlot/profiles` |
| **Windows** | `%USERPROFILE%\Application Data\ECUxPlot` | `%USERPROFILE%\Application Data\ECUxPlot\profiles` |

On Windows Vista and later, `Application Data` is a junction to `AppData\Roaming`. The effective profiles path is `%USERPROFILE%\AppData\Roaming\ECUxPlot\profiles`.

Shipped profiles load from the `profiles` directory next to the application (the app bundle or install directory). Only the custom path in the table above is user-writable.

## Preferences (application settings)

Application preferences use the Java Preferences API:

`Preferences.userNodeForPackage(ECUxPlot.class)`

That node holds window sizes, last files, axis choices, filter settings, vehicle constants, and presets. Where it is written depends on the platform.

| Platform | Where preferences are stored |
|----------|------------------------------|
| **macOS** | `~/Library/Preferences/org.nyet.ecuxplot.plist` |
| **Linux** | `~/.java/.userPrefs/org/nyet/ecuxplot/` |
| **Windows** | `HKEY_CURRENT_USER\Software\JavaSoft\Prefs\org\nyet\ecuxplot` |

- **macOS**: Some JVMs write under `~/.java/.userPrefs/` instead of the plist.
- **Linux**: Some JVMs encode the directory names under `~/.java/.userPrefs/`.

## Clearing preferences (reset / fix corrupted prefs)

If preferences become corrupted or you want a full reset:

1. **Quit ECUxPlot** completely (all windows).
2. Remove the preferences for this application:
   - **macOS**
     - Delete `~/Library/Preferences/org.nyet.ecuxplot.plist`.
     - If that file is missing, look under `~/.java/.userPrefs/` for `org`, then `nyet`, then `ecuxplot`.
     - Encoded node names are possible.
   - **Linux**
     - Delete the ECUxPlot node under `~/.java/.userPrefs/`.
     - Look for `org`, then `nyet`, then `ecuxplot`, or for encoded node names.
   - **Windows**: In `regedit`, delete `HKEY_CURRENT_USER\Software\JavaSoft\Prefs\org\nyet\ecuxplot`.
3. Start ECUxPlot again. It will use default settings and recreate preference storage.

To remove only saved vehicle profiles, delete or edit files in the profiles path for your platform (see the table above). That does not clear application preferences such as window size or last files. Use the steps above for a preferences reset.
