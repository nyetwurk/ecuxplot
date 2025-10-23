# ECUxPlot Build Guide

This document explains how to build ECUxPlot installers and packages for different platforms.

For installation instructions, see [INSTALL.md](INSTALL.md).

## Quick Start

```bash
make help        # Show available targets
make installers  # Build platform-appropriate installers (recommended)
make all         # Build everything for current platform
```

## Prerequisites

### Required Tools

- **Java Development Kit (JDK) 18** or later
- **Make** (GNU Make)
- **Ant** (Apache Ant)

### Platform-Specific Tools

- **macOS**: Xcode Command Line Tools (for `hdiutil`)
- **Linux/Windows**: NSIS, Launch4j, MinGW-w64

## Build Targets

| Target | Description | Platforms | Output |
|--------|-------------|-----------|--------|
| `all` | Build JAR files and core application | All | Platform-specific |
| `archive` | Create compressed archive | All | `build/$(TARGET).tar.gz` |
| `installers` | Build platform-appropriate installer | All | Auto-detects platform |
| `dmg` | Create macOS DMG installer | macOS only | `build/$(TARGET).dmg` |
| `exes` | Create Windows executables | Linux only | `build/CYGWIN_NT/*.exe` |

## Output Files

### macOS

- `ECUxPlot-$(VERSION)-MacOS.zip` - ZIP archive (bare app, requires system Java)
- `ECUxPlot-$(VERSION).dmg` - DMG installer (bundled app with Java runtime)

### Windows

- `ECUxPlot-$(VERSION)-setup.exe` - NSIS installer
- `CYGWIN_NT/ECUxPlot.exe` - Main executable
- `CYGWIN_NT/mapdump.exe` - Map dump utility

### Cross-Platform

- `ECUxPlot-$(VERSION).tar.gz` - Archive package (Unix/Linux/macOS)

## Installation Instructions

For detailed installation instructions, see [INSTALL.md](INSTALL.md).

### macOS DMG Installer

1. Open the DMG file
2. Drag ECUxPlot.app to the Applications folder
3. Eject the DMG

### macOS ZIP Archive

1. Extract the ZIP file
2. Drag ECUxPlot.app to Applications folder

### Windows NSIS Installer

1. Run the ECUxPlot-$(VERSION)-setup.exe file
2. Follow the installation wizard

### Cross-Platform Archive

1. Extract the tar.gz file
2. Run `./ECUxPlot.sh` (Unix/Linux/macOS)

## Runtime Requirements

### Windows Builds

- Downloads JRE automatically during build
- No system Java required

### macOS/Linux Builds

- Uses system JDK (no download needed)
- Requires Java 18+ installed

## Troubleshooting

### Platform Errors

If you see errors like "DMG creation only supported on macOS", you're trying to run a platform-specific target on the wrong platform. Use `make installers` instead.

### Missing Tools

- **macOS**: Install Xcode Command Line Tools: `xcode-select --install`
- **Linux**: Install build tools: `sudo apt-get install build-essential`
- **Windows**: Install NSIS and Launch4j

### macOS DMG Creation Issues

ECUxPlot uses `jpackage` for macOS app bundle creation, which provides better compatibility. If you encounter issues:

1. Ensure Xcode Command Line Tools are installed
2. Check available disk space
3. Verify Java 18+ is installed and accessible

## Development Notes

### Build Process

1. **JAR Creation**: Ant builds the main application JAR files
2. **Runtime Setup**: Windows downloads JRE, macOS/Linux use system JDK
3. **Package Creation**: Platform-specific tools create installers

### CI/CD

- **Build and Release Workflow**: Runs on every push to any branch and latest, builds all platforms using `build-matrix.yml` → `build-common.yml` with `installers` target
- **Release Workflow**: Runs on tags, creates full installers using `build-matrix.yml` → `build-common.yml`
- **Build Common**: Reusable workflow for single-platform builds + tests
- **Build Matrix**: Shared multi-platform build orchestration
- **Create Release**: Shared workflow for consistent release creation across build and release workflows
- **Caching**: Windows runtime directories cached between runs

### Build Artifacts

The build process creates different artifacts depending on the platform and build type:

**Note**: GitHub Actions preserves the directory structure when uploading artifacts. Files uploaded with `build/` prefix are stored in `build/` subdirectories, while files uploaded from root are stored directly in the artifact root. Simple installer names are created in the root level for easy access.

#### Latest Build Artifacts

```text
GitHub Actions Artifacts (30-day retention):
├── macos-builds/
│   ├── build/                      # Latest files
│   │   ├── ECUxPlot-latest.dmg    # Latest DMG installer
│   │   └── ECUxPlot-latest-MacOS.zip # Latest ZIP archive
│   └── ECUxPlot.dmg                # Simple DMG installer (root level)
└── linux-windows-builds/
    ├── build/                      # Latest files
    │   ├── ECUxPlot-latest.tar.gz # Latest Linux archive
    │   └── ECUxPlot-latest-setup.exe # Latest Windows installer
    ├── ECUxPlot.jar              # Simple JAR file
    ├── mapdump.jar               # Map dump utility
    └── ECUxPlot-setup.exe          # Simple Windows installer (root level)

GitHub Release (latest):
├── ECUxPlot.dmg                    # Simple macOS installer
├── ECUxPlot-setup.exe              # Simple Windows installer
├── ECUxPlot-latest.dmg            # Latest macOS installer
├── ECUxPlot-latest.tar.gz         # Latest Linux archive
├── ECUxPlot-latest-setup.exe      # Latest Windows installer
├── ECUxPlot-latest-MacOS.zip      # Latest macOS ZIP
├── ECUxPlot-latest.jar            # Latest JAR file
├── ECUxPlot.jar                    # Simple JAR file
└── mapdump.jar                     # Map dump utility
```

#### Release Build Artifacts

```text
GitHub Actions Artifacts (90-day retention):
├── macos-builds/
│   ├── build/                      # Versioned files
│   │   ├── ECUxPlot-*.dmg          # Versioned DMG installer
│   │   └── ECUxPlot-*-MacOS.zip    # Versioned ZIP archive
│   └── ECUxPlot.dmg                # Simple DMG installer (root level)
└── linux-windows-builds/
    ├── build/                      # Versioned files
    │   ├── ECUxPlot-*.tar.gz       # Versioned Linux archive
    │   └── ECUxPlot-*-setup.exe    # Versioned Windows installer
    ├── ECUxPlot.jar                # Simple JAR file
    ├── mapdump.jar                 # Map dump utility
    └── ECUxPlot-setup.exe          # Simple Windows installer (root level)

GitHub Release (tagged version):
├── ECUxPlot.dmg                    # Simple macOS installer
├── ECUxPlot-setup.exe              # Simple Windows installer
├── ECUxPlot-*.dmg                  # Versioned macOS installer
├── ECUxPlot-*.tar.gz               # Versioned Linux archive
├── ECUxPlot-*-setup.exe            # Versioned Windows installer
└── ECUxPlot-*-MacOS.zip            # Versioned macOS ZIP
```

#### Local Build Artifacts

```text
build/
├── Darwin/                      # macOS builds
│   ├── ECUxPlot.app/            # Full app bundle (jpackage)
│   ├── ECUxPlot-bare.app/       # Bare app bundle (no runtime)
│   ├── *.zip                    # ZIP archives
│   └── *.dmg                    # DMG installers
├── CYGWIN_NT/                   # Windows builds
│   ├── ECUxPlot.exe             # Main executable
│   └── mapdump.exe              # Map dump utility
├── *.tar.gz                     # Cross-platform archives
└── *.jar                        # JAR files
```

## Advanced Usage

### Custom Builds

```bash
make clean          # Clean build artifacts
make vars           # Show build variables
make tag VER=1.2.3  # Create git tag
```

### Platform-Specific Builds

```bash
# On macOS
make dmg                  # Create DMG only
make archive             # Create tar.gz archive

# On Linux
make exes                # Create Windows executables
make installers          # Create Windows installers
```

For more detailed information, see the source code and build scripts in the `scripts/` directory.
