# ECUxPlot Installation Guide

Welcome! This guide will help you install ECUxPlot on your computer. Choose your platform below and follow the step-by-step instructions.

## Quick Start

**New to ECUxPlot?** Here's the fastest way to get started:

1. **Download**: Go to the [releases page](https://github.com/nyetwurk/ECUxPlot/releases)
2. **Pick your platform**: See the sections below for your operating system
3. **Install**: Follow the simple steps for your platform
4. **Run**: Launch ECUxPlot and load your first log file!

**Platform Not Sure?**

- **macOS** (Apple computers): See "macOS Installation" below
- **Windows** (PC): See "Windows Installation" below
- **Linux**: See "Linux Installation" below

## Platform-Specific Installation

### macOS Installation

> **⚠️ Important for macOS Users**: ECUxPlot is currently unsigned due to code signing costs, as it is an open-source/freeware project. This is expected. See the "macOS Security Steps" section below for instructions.

#### Option 1: DMG Installer (Recommended - includes Java runtime)

1. **Download** the `.dmg` file from the [releases page](https://github.com/nyetwurk/ECUxPlot/releases)
2. **Clear quarantine** (required step):
   - Open **Terminal** (press Cmd+Space, type "Terminal", press Enter)
   - Type: `cd ~/Downloads` (or wherever you saved the file)
   - Type: `xattr -c ECUxPlot-*.dmg`
   - Press Enter
3. **Double-click** the `.dmg` file to open it
4. **Drag** ECUxPlot.app to the Applications folder shortcut
5. **Eject** the DMG from your desktop

#### Option 2: ZIP Archive (lighter - requires Java installed separately)

1. **Download** the `.zip` file from the releases page
2. **Extract** the ZIP file (double-click it)
3. **Drag** ECUxPlot.app to Applications folder

#### macOS Security Steps (Required First Time)

**When you first try to run ECUxPlot**, macOS will say it's "damaged". This is normal! Follow these steps:

1. **macOS will show**: *"'ECUxPlot' is damaged and can't be opened"*
2. **Click "Cancel"** (NOT "Move to Trash"!)
3. Open **System Settings** → **Privacy & Security**
   - Older macOS: **System Preferences** → **Security & Privacy**
4. Scroll to the bottom of the page
5. Look for a message about ECUxPlot being blocked
6. Click **"Allow Anyway"** or **"Open Anyway"**
7. Try running ECUxPlot again - it should work now!

**If you don't see the "Allow Anyway" button**, you may need to clear attributes first:

```bash
sudo xattr -rc /Applications/ECUxPlot.app
```

Then try running the app again and return to Privacy & Security settings.

### Windows Installation

> ✅ **Easy install**: Windows installation includes Java, so you're all set!

#### Recommended: `setup.exe` Installer

1. **Download** the `ECUxPlot-*-setup.exe` file from the [releases page](https://github.com/nyetwurk/ECUxPlot/releases)
2. **Double-click** the installer to run it
3. **Click "Next"** through the installation wizard
4. ECUxPlot will be installed to `C:\Program Files\ECUxPlot`

**That's it!** Launch ECUxPlot from the Start menu or desktop shortcut.

#### Alternative: ZIP Archive

If you prefer a portable version:

1. Download the `.zip` file from the releases page
2. Extract it to a folder of your choice
3. Run `ECUxPlot.exe` from that folder

### Linux Installation

> 📝 **Note**: Linux users need Java 18+ installed first.

**Installing Java:**

- **Debian/Ubuntu**: `sudo apt-get update && sudo apt-get install openjdk-21-jdk`
- **Other distros**: Download from [Adoptium](https://adoptium.net/)

#### Install from Archive

1. **Download** the `.tar.gz` file from the [releases page](https://github.com/nyetwurk/ECUxPlot/releases)
2. **Extract** the archive:

   ```bash
   tar -xzf ECUxPlot-*.tar.gz
   ```

3. **Run** the application:

   ```bash
   ./ECUxPlot.sh
   ```

**First time running?** You may need to make the script executable:

```bash
chmod +x ECUxPlot.sh
```

## System Requirements

**All Platforms:**

- **Memory**: 2GB RAM minimum
- **Storage**: 500MB for installation

**Java Requirements:**

- **Windows**: ✅ Java included (no installation needed)
- **macOS**: Requires Java 18+ ([Download Java](https://adoptium.net/))
- **Linux**: Requires Java 18+
  - Debian/Ubuntu: `sudo apt-get install openjdk-21-jdk`
  - Other distros: [Download Java](https://adoptium.net/)

**Operating System:**

- Windows 10 or newer
- macOS 10.15 (Catalina) or newer
- Linux with modern kernel (Ubuntu 20.04+, Fedora 34+, etc.)

## Troubleshooting Installation

### Common Installation Issues

#### "Java not found" error on macOS/Linux

**macOS:**

- Download and install Java 18+ from [Adoptium](https://adoptium.net/)
- Verify installation by running `java -version` in Terminal
- Make sure you downloaded the correct version for your system (Intel/Apple Silicon)

**Linux (Debian/Ubuntu):**

- Install via package manager: `sudo apt-get install openjdk-21-jdk`
- Verify installation: `java -version`

**Linux (other distros):**

- Download and install Java 18+ from [Adoptium](https://adoptium.net/)
- Verify installation: `java -version`

#### "Permission denied" on Linux/macOS

- Make scripts executable: `chmod +x ECUxPlot.sh`
- Or run with: `bash ECUxPlot.sh`

#### Antivirus software is blocking ECUxPlot

- Add ECUxPlot to your antivirus exceptions/exclusions list
- This is a false positive - ECUxPlot is open source, if in doubt, build yourself!

#### App won't launch (Windows)

- Try running the installer as Administrator
- Check that Java is properly installed
- Review any error messages for clues

#### Still having problems?

- Check the [README troubleshooting section](README.md#troubleshooting)
- Post your issue on [GitHub Issues](https://github.com/nyetwurk/ecuxplot/issues)
- Include your operating system version and any error messages

---

## For Developers

Want to build ECUxPlot from source or contribute? See [BUILD.md](BUILD.md) for detailed build instructions.
