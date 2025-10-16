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

### Available Targets

| Target | Platform | Description | Output |
|--------|----------|-------------|--------|
| `all` | macOS/Linux | Build everything for current platform | Platform-specific |
| `archive` | macOS/Linux | Create compressed archive (Unix/Linux/macOS) | `build/$(TARGET).tar.gz` |
| `installers` | macOS/Linux | Build platform-appropriate installer | Platform-specific |
| `dmg` | macOS | Create DMG installer with Applications folder | `build/$(TARGET).dmg` |
| `exes` | Linux | Create Windows executable files | `build/CYGWIN_NT/*.exe` |
| `sanity-check` | macOS | Run sanity checks on app bundle at different stages | Console output |

## Platform Support Matrix

| Build Platform | Target | macOS | Linux | Windows |
|----------------|--------|-------|-------|---------|
| macOS/Linux | `all` | ✅ | ✅ | ✅ |
|  | `archive` | ✅ | ✅ | ✅ |
| Linux | `installers` | ❌ | ✅ | ✅ |
|  | `exes` | ❌ | ✅ | ✅ |
| macOS | `installers` | ✅ | ❌ | ❌ |
|  | `dmg` | ✅ | ❌ | ❌ |
|  | `sanity-check` | ✅ | ❌ | ❌ |

**Note**: Targets automatically check platform compatibility and will show clear error messages if run on unsupported platforms.

## Quick Start

### Quick Help

```bash
make help    # Show summary of most commonly used targets
```

### Build Everything (Recommended)

```bash
make all
make installers
```

## Output Files

After building, you'll find the following files in the `build/` directory:

### macOS Output Files

- `ECUxPlot-$(VERSION)-MacOS.zip` - ZIP archive
- `ECUxPlot-$(VERSION).dmg` - DMG installer

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

### macOS DMG Creation Issues

ECUxPlot now uses `jpackage` for macOS app bundle creation, which provides better compatibility and eliminates many common issues.

**Key improvements**:

- Uses `jpackage` launcher instead of custom stub
- Automatic runtime management with proper Java module handling
- Simplified build process with better error detection
- Built-in sanity checking at each build stage

**For debugging**: Use `make sanity-check` to verify the build process at each stage.

**Common issues**:

- Ensure `JAVA_HOME` points to JDK 18+
- Verify Xcode Command Line Tools are installed
- Check that `hdiutil` is available in PATH

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

### Runtime Creation Process

1. **JRE Download** (`runtime/%/`):
   - Downloads latest JRE from Adoptium GitHub releases (Windows only)
   - Determines filename for a given version/platform pair using the GitHub API
   - macOS/Linux builds use system JDK (no download needed)

2. **Runtime Creation** (`runtime/%/java-$(JAVA_TARGET_VER).stamp`):
   - Extracts downloaded JDK (Windows only)
   - Creates `java-$(JAVA_TARGET_VER).stamp` file
   - macOS/Linux: Uses system JDK, no runtime download

### Runtime Compatibility

The system automatically ensures compatibility:

- **JAVA_TARGET_VER**: 18 (configurable in Makefile)
- **Downloaded JDKs**: Latest available from Adoptium (automatically fetched)
- **Runtime versions**: Windows uses downloaded JDK versions, macOS/Linux use system JDK
- **Platform support**: Windows downloads JRE, macOS/Linux use system JDK

### CI/CD Integration

The system is **CI-ready** with built-in GitHub Actions caching:

#### **Build Workflow** (`.github/workflows/build.yml`)

- **Linux Job**: Builds Linux + Windows executables (`make exes`)
- **macOS Job**: Builds macOS application (`make all`)
- **Caches**: Homebrew packages (macOS only)
- **Purpose**: Continuous integration testing

#### **Release Workflow** (`.github/workflows/release.yml`)

- **Linux Job**: Builds Linux + Windows installers (`make installers`)
- **macOS Job**: Builds macOS installers (`make dmg`)
- **Caches**: Runtime directories (`runtime/*`) (currently only CYGWIN_NT is needed)
- **Purpose**: Release builds with full installer creation

#### **Cache Strategy**

- **What's Cached**: Windows runtime directories (created runtimes)
- **What's Not Cached**: JDK downloads (downloaded fresh each time)
- **Cache Invalidation**: Automatic when Makefile or jpackage.mk changes
- **Fallback**: If cache miss, runtimes are recreated automatically

#### **CI Benefits**

- **Fast Builds**: Windows runtime directories cached between runs
- **Reliable**: No external dependencies for macOS/Linux (system JDK)
- **Cross-Platform**: Linux CI builds Linux+Windows, macOS CI builds macOS
- **Automatic**: No manual cache management needed

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
