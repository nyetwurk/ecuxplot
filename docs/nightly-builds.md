# ECUxPlot Build and Release System

Automatically creates installers (DMG for macOS and EXE for Windows) on every push and nightly.

## Overview

The build and release system runs on every push to any branch and every night at 2 AM UTC.

### System Components

- **GitHub Actions Workflows**: `build-and-release.yml`, `release.yml`, `build-common.yml`, `build-matrix.yml`, `create-release.yml`
- **Management Script**: `scripts/build-management.sh` for manual operations
- **Multi-platform**: macOS (ZIP/DMG), Windows (EXE), Linux (tar.gz)

### Management Commands

```bash
# Trigger manual build
./scripts/build-management.sh trigger

# Check build status
./scripts/build-management.sh status

# List artifacts
./scripts/build-management.sh list-artifacts
```

## How It Works

- **Schedule**: Runs every night at 2 AM UTC
- **Push**: Runs on every push to any branch
- **Manual**: Can be triggered manually via GitHub Actions UI (creates releases)
- **Platforms**: macOS (ZIP/DMG), Windows (EXE), Linux (tar.gz)
- **Releases**: Creates/updates "latest-nightly" release for direct downloads
- **Artifacts**: 30-day retention for CI purposes

## Build Behavior

### Scheduled Runs (Every night at 2 AM UTC)

- ✅ **Builds**: All platforms, runs tests, creates artifacts
- ✅ **Creates Release**: Updates "latest-nightly" release for users

### Manual Triggers (GitHub Actions UI or script)

- ✅ **Builds**: All platforms, runs tests, creates artifacts
- ✅ **Creates Release**: Updates "latest-nightly" release (useful for testing)

### Push Triggers (Every push to any branch)

- ✅ **Builds**: All platforms, runs tests, creates artifacts
- ❌ **No Release**: Only creates GitHub Actions artifacts (CI testing)

## Workflows

### Build and Release (`build-and-release.yml`)

**Triggers:**

- Scheduled: Every night at 2 AM UTC
- Push: Every push to any branch
- Manual: GitHub Actions UI (creates releases)

**Process:**

1. Builds latest code for all platforms using `build-matrix.yml` → `build-common.yml`
2. Creates platform-specific installers (macOS ZIP/DMG, Linux/Windows artifacts)
3. Uploads artifacts for CI purposes
4. Creates/updates "latest-nightly" release using shared `create-release.yml` (scheduled and manual triggers only)

### Release Management (`release.yml`)

**Triggers:**

- Push to version tags (`v*`)
- Manual with tag input

**Purpose:**

- Creates GitHub releases for tagged versions
- Builds installers for all platforms using `build-matrix.yml` → `build-common.yml`
- Uses shared `create-release.yml` for consistent release creation
- 90-day artifact retention

## Downloads

### Nightly Builds

**Direct Download**: [Latest Nightly Build](https://github.com/nyetwurk/ecuxplot/releases/tag/latest-nightly)

The nightly build creates a "latest-nightly" release that gets updated with each build, providing:

- **macOS**: ECUxPlot-nightly.dmg, ECUxPlot-nightly-MacOS.zip
- **Linux**: ECUxPlot-nightly.tar.gz
- **Windows**: ECUxPlot-nightly-setup.exe
- **Portable**: ECUxPlot-nightly.jar, ECUxPlot.jar, mapdump.jar

### Stable Releases

**Releases Page**: [GitHub Releases](https://github.com/nyetwurk/ecuxplot/releases)

Tagged releases are created for stable versions with 90-day artifact retention.

## Requirements

- GitHub Actions enabled
- GitHub CLI (`gh`) for management script
- Java 18 (handled by `build-common.yml`)

## Configuration

### Schedule Adjustment

Modify cron expression in `build-and-release.yml`:

```yaml
schedule:
  - cron: '0 2 * * *'  # 2 AM UTC daily
```

### Retention Policy

Adjust artifact retention in workflow files:

```yaml
retention-days: 30  # Change as needed
```

## Troubleshooting

### Common Issues

- **Build not triggering**: Check workflow status in GitHub Actions
- **Build failures**: Check GitHub Actions logs and dependencies
- **Download issues**: Verify release page and artifact availability

### Debugging

```bash
# Check latest build status
./scripts/build-management.sh status

# List available artifacts
./scripts/build-management.sh list-artifacts

# Trigger manual build
./scripts/build-management.sh trigger
```

## Benefits

- **Always Current**: Builds latest code on every push and nightly
- **Multi-platform**: macOS, Windows, Linux support
- **Automated**: Runs without manual intervention
- **User-friendly**: Direct download links via releases
- **Manageable**: Tools for monitoring and manual operations
- **DRY**: Reuses build logic across workflows with shared components
- **Consistent**: Both build and release workflows use identical release creation
