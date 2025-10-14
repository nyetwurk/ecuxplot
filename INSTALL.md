# ECUxPlot Installation Guide

This document explains how to build ECUxPlot installers and packages for different platforms.

## Overview

ECUxPlot supports building installers and packages for multiple platforms:

- **macOS**: ZIP archive and DMG installer
- **Windows**: NSIS installer and executable files
- **Linux**: Archive package

## Prerequisites

### Required Tools

- **Java Development Kit (JDK) 18** or later
- **Make** (GNU Make)
- **Ant** (Apache Ant)

### Platform-Specific Tools

#### macOS

- **Xcode Command Line Tools** (for `hdiutil`)
- **Homebrew** (recommended for installing Make/Ant)

#### Linux/Windows

- **NSIS** (Nullsoft Scriptable Install System)
- **Launch4j** (for Windows executables)
- **MinGW-w64** (cross-compilation tools)

## Build Targets

### Core Targets

| Target | Description | Platforms |
|--------|-------------|-----------|
| `all` | Build JAR files and core application | All |
| `archive` | Create Linux archive package | All |
| `installers` | Build platform-appropriate installer | All (auto-detects) |

### Platform-Specific Targets

#### macOS Targets

| Target | Description | Output |
|--------|-------------|--------|
| `mac-zip` | Create ZIP archive of app bundle | `build/Darwin/$(TARGET)-MacOS.zip` |
| `dmg` | Create DMG installer with Applications folder | `build/Darwin/$(TARGET).dmg` |

#### Windows Targets

| Target | Description | Output |
|--------|-------------|--------|
| `win-installer` | Create NSIS Windows installer | `build/$(TARGET)-setup.exe` |
| `exes` | Create Windows executable files | `build/CYGWIN_NT/*.exe` |

#### Cross-Platform Targets

| Target | Description | Output |
|--------|-------------|--------|
| `archive` | Create compressed archive (Unix/Linux/macOS) | `build/$(TARGET).tar.gz` |

## Platform Support Matrix

| Target | macOS | Linux | Windows |
|--------|-------|-------|---------|
| `all` | ✅ | ✅ | ✅ |
| `archive` | ✅ | ✅ | ✅ |
| `installers` | ✅ | ✅ | ✅ |
| `mac-zip` | ✅ | ❌ | ❌ |
| `dmg` | ✅ | ❌ | ❌ |
| `win-installer` | ❌ | ✅ | ✅ |
| `exes` | ❌ | ✅ | ✅ |

**Note**: Targets automatically check platform compatibility and will show clear error messages if run on unsupported platforms.

## Quick Start

### Build Everything (Recommended)

```bash
make all
make installers
```

### Build Specific Platform Installers

#### macOS Build Commands

```bash
make mac-zip    # Creates ZIP file
make dmg        # Creates DMG installer
```

#### Windows (from Linux)

```bash
make win-installer  # Creates NSIS installer
make exes           # Creates executable files
```

#### Cross-Platform Archive

```bash
make archive    # Creates tar.gz archive
```

## Output Files

After building, you'll find the following files in the `build/` directory:

### macOS Output Files

- `Darwin/ECUxPlot-$(VERSION)-MacOS.zip` - ZIP archive
- `Darwin/ECUxPlot-$(VERSION).dmg` - DMG installer

### Windows Output Files

- `ECUxPlot-$(VERSION)-setup.exe` - NSIS installer
- `CYGWIN_NT/ECUxPlot.exe` - Main executable
- `CYGWIN_NT/mapdump.exe` - Map dump utility

### Cross-Platform Archive Files

- `ECUxPlot-$(VERSION).tar.gz` - Archive package (Unix/Linux/macOS)

## Installation Instructions

### macOS DMG Installer

1. Open the DMG file
2. Drag ECUxPlot.app to the Applications folder
3. Eject the DMG

### macOS ZIP Archive

1. Extract the ZIP file
2. Drag ECUxPlot.app to Applications folder

### Windows NSIS Installer

1. Run the `.exe` installer
2. Follow the installation wizard
3. ECUxPlot will be installed to Program Files

### Cross-Platform Archive Installation

1. Extract the tar.gz file:

   ```bash
   tar -xzf ECUxPlot-$(VERSION).tar.gz
   ```

2. Run the application:

   ```bash
   ./ECUxPlot/ECUxPlot.sh
   ```

## Troubleshooting

### Platform Compatibility Errors

If you see errors like "DMG creation only supported on macOS", you're trying to run a platform-specific target on the wrong platform. Use `make installers` instead, which automatically detects your platform.

### Missing Tools

- **Java**: Install JDK 18+ and ensure `JAVA_HOME` is set
- **Make**: Install GNU Make (usually available via package manager)
- **Ant**: Install Apache Ant
- **NSIS**: Install from [nsis.sourceforge.io](https://nsis.sourceforge.io/)
- **Launch4j**: Install from [launch4j.sourceforge.net](https://launch4j.sourceforge.net/)

### Build Failures

- Ensure all prerequisites are installed
- Check that `JAVA_HOME` points to JDK 18+
- Verify platform-specific tools are available in PATH
- Run `make vars` to see build configuration

## Advanced Usage

### Custom Build Configuration

You can override build variables:

```bash
make JAVA_TARGET_VER=18 all
```

### Clean Build

```bash
make clean      # Remove build artifacts
make binclean   # Remove only binary files
```

### Debug Information

```bash
make vars       # Show build configuration
```

## File Structure

```text
build/
├── Darwin/                 # macOS builds
│   ├── ECUxPlot.app/      # App bundle
│   ├── *.zip              # ZIP archives
│   └── *.dmg              # DMG installers
├── CYGWIN_NT/             # Windows builds
│   └── *.exe              # Executable files
└── *.tar.gz               # Cross-platform archives
```
