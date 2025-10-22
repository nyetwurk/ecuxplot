# ECUxPlot Installation Guide

This document explains how to install ECUxPlot on different platforms.

## Quick Start

1. **Download**: Get the appropriate installer for your platform from the [releases page](https://github.com/your-repo/releases)
2. **Install**: Follow the platform-specific instructions below
3. **Run**: Launch ECUxPlot and start analyzing your ECU data

## Platform-Specific Installation

### macOS Installation

#### DMG Installer (Recommended)

1. Download the `.dmg` file from the releases page
2. **Important**: Clear quarantine flags immediately after download:

   ```bash
   xattr -c ECUxPlot-*.dmg
   ```

3. Open the DMG file
4. Drag ECUxPlot.app to the Applications folder
5. Eject the DMG

#### ZIP Archive

1. Download the `.zip` file from the releases page
2. Extract the ZIP file
3. Drag ECUxPlot.app to Applications folder

#### macOS Security Issues

**Problem**: "Application is damaged" error when trying to install or run ECUxPlot

**Solution**: This is a macOS security feature that blocks unsigned applications.

##### During installation

You **must** do this immediately after you download the `.dmg`:

- Open Terminal and run: `xattr -c ECUxPlot-*.dmg` (or wherever you downloaded the dmg to)
- Now open the `dmg` as usual, and drag the ECUxPlot icon to Applications

##### Running after installation

- Open Terminal and run: `sudo xattr -rc /Applications/ECUxPlot.app`
- When you first try to run ECUxPlot, macOS will show a dialog saying: **"'ECUxPlot' is damaged and can't be opened. You should move it to the Trash."**
- **Important**: Click **"Cancel"** - do NOT click "Move to Trash"
- Go to **System Settings** → **Privacy & Security** (Note: On macOS Monterey and earlier, this is **System Preferences** → **Security & Privacy**)
- Scroll down to the bottom of the page
- Look for a message about ECUxPlot being blocked
- Click **"Allow Anyway"** or **"Open Anyway"**
- If you don't see the message, run `xattr -c /Applications/ECUxPlot.app` then try running the application again and then check Privacy & Security settings

**Note**: After clicking **"Cancel"**, macOS will show a message telling you that running the app was refused. You can then go to Privacy & Security settings to override this restriction.

### Windows Installation

#### NSIS Installer (Recommended)

1. Download the `ECUxPlot-*-setup.exe` file from the releases page
2. Run the installer executable
3. Follow the installation wizard
4. ECUxPlot will be installed to `C:\Program Files\ECUxPlot`

#### Manual Installation

1. Download the JAR files and scripts
2. Extract to a directory of your choice
3. Run `ECUxPlot.exe` from the installation directory

### Linux Installation

#### Archive Package

1. Download the `.tar.gz` file from the releases page
2. Extract the archive:

   ```bash
   tar -xzf ECUxPlot-*.tar.gz
   ```

3. Run the application:

   ```bash
   ./ECUxPlot.sh
   ```

## Runtime Requirements

### Java Requirements

- **Windows**: JRE is automatically bundled with the installer
- **macOS/Linux**: Requires Java 18+ installed on your system

### System Requirements

- **Minimum RAM**: 2GB
- **Disk Space**: 500MB for installation
- **Operating System**: Windows 10+, macOS 10.15+, or Linux with modern kernel

## Building from Source

For developers who want to build ECUxPlot from source code, see [BUILD.md](BUILD.md) for detailed build instructions.

## Troubleshooting Installation

### Common Issues

- **Java not found**: Install Java 18+ from [Adoptium](https://adoptium.net/) or your system package manager
- **Permission denied**: On Unix systems, ensure the scripts have execute permissions: `chmod +x *.sh`
- **Antivirus blocking**: Some antivirus software may flag the application; add it to your exceptions list

### Getting Help

If you encounter installation issues not covered here, please:

1. Check the [troubleshooting section in README.md](README.md#troubleshooting)
2. Post your issue on the project's issue tracker
3. Include your operating system version and any error messages
