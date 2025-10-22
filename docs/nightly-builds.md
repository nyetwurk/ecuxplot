# ECUxPlot Nightly Build System

Automatically creates installers (DMG for macOS and EXE for Windows) every night.

## Overview

The nightly build system runs every night at 2 AM UTC and builds the latest code.

### System Components

- **GitHub Actions Workflows**: `nightly-build.yml`, `release.yml`, `build-common.yml`
- **Management Script**: `scripts/nightly-build.sh` for manual operations
- **Multi-platform**: macOS (DMG), Windows (EXE), Linux (tar.gz)

### Management Commands

```bash
# Trigger manual build
./scripts/nightly-build.sh trigger

# Check build status
./scripts/nightly-build.sh status

# List artifacts
./scripts/nightly-build.sh list-artifacts
```

## How It Works

- **Schedule**: Runs every night at 2 AM UTC
- **Platforms**: macOS (DMG), Windows (EXE), Linux (tar.gz)
- **Releases**: Creates/updates "latest-nightly" release for direct downloads
- **Artifacts**: 30-day retention for CI purposes

## Workflows

### Nightly Build (`nightly-build.yml`)

**Triggers:**

- Scheduled: Every night at 2 AM UTC
- Manual: GitHub Actions UI

**Process:**

1. Builds latest code for all platforms
2. Creates platform-specific installers using `build-common.yml`
3. Uploads artifacts for CI purposes
4. Creates/updates "latest-nightly" release for direct downloads

### Release Management (`release.yml`)

**Triggers:**

- Push to version tags (`v*`)
- Manual with tag input

**Purpose:**

- Creates GitHub releases for tagged versions
- Builds installers for all platforms using matrix strategy
- 90-day artifact retention

## Downloads

### Nightly Builds

**Direct Download**: [Latest Nightly Build](https://github.com/nyetwurk/ecuxplot/releases/tag/latest-nightly)

The nightly build creates a "latest-nightly" release that gets updated with each build, providing:

- **macOS**: ECUxPlot.dmg
- **Linux**: ECUxPlot.tar.gz
- **Windows**: ECUxPlot.exe

### Stable Releases

**Releases Page**: [GitHub Releases](https://github.com/nyetwurk/ecuxplot/releases)

Tagged releases are created for stable versions with 90-day artifact retention.

## Requirements

- GitHub Actions enabled
- GitHub CLI (`gh`) for management script
- Java 18 (handled by `build-common.yml`)

## Configuration

### Schedule Adjustment

Modify cron expression in `nightly-build.yml`:

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
./scripts/nightly-build.sh status

# List available artifacts
./scripts/nightly-build.sh list-artifacts

# Trigger manual build
./scripts/nightly-build.sh trigger
```

## Benefits

- **Always Current**: Builds latest code every night
- **Multi-platform**: macOS, Windows, Linux support
- **Automated**: Runs without manual intervention
- **User-friendly**: Direct download links via releases
- **Manageable**: Tools for monitoring and manual operations
- **DRY**: Reuses build logic across workflows
