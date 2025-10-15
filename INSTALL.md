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

## Runtime Management System

ECUxPlot uses a self-contained runtime management system that automatically creates Java runtimes for cross-platform builds without requiring external dependencies.

### Runtime Strategy

The system uses a **file-based dependency approach** where every target creates actual files, ensuring robust dependency tracking:

- **Current Platform**: Uses `jlink` to create a custom runtime locally from system Java
- **Other Platforms**: Automatically downloads JDKs and creates runtimes from scratch
- **No External Dependencies**: Everything is created locally - no hosted runtime server needed

### Runtime Creation Process

The system follows a clean dependency chain:

1. **JDK Download** (`runtime/jdk-%.$(FILE_EXT_%)`):
   - Downloads latest JDK from Adoptium GitHub releases
   - Uses platform-specific file extensions (`.tar.gz` for Linux/macOS, `.zip` for Windows)
   - Fetches actual version dynamically from GitHub API

2. **Runtime Creation** (`runtime/%/release`):
   - Extracts downloaded JDK
   - Creates custom runtime using `jlink` with minimal modules
   - Generates `release` file with version and module information

### Platform Support

The system supports three platforms defined in `UNAMES`:

| Platform | JDK File | Runtime Directory | Creation Method |
|----------|----------|-------------------|-----------------|
| Linux | `runtime/jdk-Linux.tar.gz` | `runtime/Linux/` | Download JDK + `jlink` |
| Darwin (macOS) | `runtime/jdk-Darwin.tar.gz` | `runtime/Darwin/` | Download JDK + `jlink` |
| CYGWIN_NT (Windows) | `runtime/jdk-CYGWIN_NT.zip` | `runtime/CYGWIN_NT/` | Download JDK + `jlink` |

### Runtime Management Targets

| Target | Description | Usage |
|--------|-------------|-------|
| `runtime/%/release` | Create runtime for specific platform | `make runtime/Linux/release` |
| `runtime/jdk-%.$(FILE_EXT_%)` | Download JDK for specific platform | `make runtime/jdk-Darwin.tar.gz` |

### Runtime Compatibility

The system automatically ensures compatibility:

- **JAVA_TARGET_VER**: 18 (configurable in Makefile)
- **Downloaded JDKs**: Latest available from Adoptium (automatically fetched)
- **Runtime versions**: Actual versions from downloaded JDKs (not hardcoded)

### Technical Implementation

The system uses **Make pattern rules** with **proper variable expansion**:

```makefile
# Platform-specific variables
FILE_EXT_Linux:=tar.gz
FILE_EXT_CYGWIN_NT:=zip
FILE_EXT_Darwin:=tar.gz
PLATFORM_NAME_Linux:=linux
PLATFORM_NAME_CYGWIN_NT:=windows
PLATFORM_NAME_Darwin:=mac

# Pattern rules with variable expansion
runtime/jdk-%.$(FILE_EXT_%):  # Downloads JDK
runtime/%/release: runtime/jdk-%.$(FILE_EXT_%)  # Creates runtime
```

This approach eliminates shell conditionals and uses Make's built-in capabilities for clean, maintainable code.

### Benefits of Self-Contained System

- **No External Dependencies**: No need for hosted runtime server
- **Always Up-to-Date**: Automatically fetches latest JDK versions
- **Cross-Platform**: Works on any platform that can run Make
- **Reliable**: No network dependencies for runtime creation
- **Simple**: Single `make installers` command handles everything

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
- **Caches**: Runtime directories (`runtime/*/bin`, `runtime/*/lib`, `runtime/*/release`, `runtime/*/java-*.stamp`)
- **Purpose**: Release builds with full installer creation

#### **Cache Strategy**

- **What's Cached**: Runtime directories (created runtimes)
- **What's Not Cached**: JDK downloads (downloaded fresh each time)
- **Cache Invalidation**: Automatic when Makefile or jpackage.mk changes
- **Fallback**: If cache miss, runtimes are recreated automatically

#### **CI Benefits**

- **Fast Builds**: Runtime directories cached between runs
- **Reliable**: No external dependencies, works offline
- **Cross-Platform**: Linux CI builds Windows, macOS CI builds macOS
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
