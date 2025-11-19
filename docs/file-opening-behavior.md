# File Opening Behavior Requirements

## Scenarios

### Scenario A: OS launches ECUxPlot with CSV file (command-line)
**Required Behavior:** REPLACE

### Scenario B: OS "Open with" behavior via Desktop handler

The Desktop handler (`openFiles()`) is called when files are opened using "Open with" from the OS.
The behavior is context-aware based on what files are currently loaded:

#### Scenario B1: ECUxPlot is already running with manually loaded files (or mix of prefs + manual)
**Required Behavior:** ADD (preserves user's manual file choices)

#### Scenario B2: ECUxPlot is NOT running (fresh startup with all files from prefs)
**Required Behavior:** REPLACE (clears auto-loaded prefs files, loads only "Open with" file)

## Implementation

Implemented using metadata stored in each `ECUxDataset` object rather than global flags. See [System Architecture](system-architecture.md) for details on ECUxDataset structure.

1. **Dataset Metadata**: Each `ECUxDataset` has a `loadedFromPrefs` boolean field
   - Set to `true` when files are auto-loaded from preferences during startup
   - Remains `false` for all manually loaded files (menu, drag-and-drop, command-line)

2. **Desktop Handler Logic**: `openFiles()` checks dataset metadata to decide between B1 and B2:
   - If **ALL** current datasets have `loadedFromPrefs == true` → REPLACE (Scenario B2)
   - If **ANY** dataset has `loadedFromPrefs == false` → ADD (Scenario B1, preserve user's work)

3. **Other Handlers**: Menu and drag-and-drop handlers use their own explicit logic:
   - Menu "Open File": Always REPLACE (explicit user choice)
   - Menu "Add File": Always ADD (explicit user choice)
   - Drag-and-drop: Always ADD
