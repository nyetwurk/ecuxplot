#!/usr/bin/env python3
"""
Version detection script for ECUxPlot builds.

This script determines the appropriate version strings based on the current git state:
- ECUXPLOT_VER: Full version string for display
- VERSION: Base semantic version (x.y.z)
- ASSET_VER: Version for asset filenames
- JPACKAGE_VER: Semantic version for jpackage (must be x.y.z format)
"""

import subprocess
import sys
import re
import os


def run_git_command(cmd):
    """Run a git command and return the output."""
    try:
        result = subprocess.run(cmd, shell=True, capture_output=True, text=True, check=True)
        return result.stdout.strip()
    except subprocess.CalledProcessError:
        return None

def get_git_describe():
    """Get git describe output with abbrev."""
    cmd = "git describe --tags --abbrev=4 --dirty --always"
    return run_git_command(cmd)

def get_last_semantic_tag():
    """Get the last real semantic tag."""
    cmd = "git describe --tags --match='v*' --abbrev=0"
    return run_git_command(cmd)


def extract_semantic_version(tag):
    """Extract semantic version (x.y.z) from a tag like 'v1.2.3'."""
    if not tag:
        return None

    # Remove 'v' prefix and extract version part before any dashes
    version_part = tag.lstrip('v').split('-')[0]

    # Check if it's a valid semantic version (x.y.z)
    if re.match(r'^\d+\.\d+\.\d+$', version_part):
        return version_part

    return None


def get_jpackage_ver():
    """Get the last semantic version (x.y.z) for jpackage compatibility."""
    tag = get_last_semantic_tag()
    if not tag:
        return None
    return extract_semantic_version(tag)


def get_javac_info(javac_path=None):
    """Get Java compiler information."""
    try:
        # Use provided javac path or find it
        if javac_path:
            javac_cmd = javac_path
        else:
            javac_cmd = 'javac'

        # Get javac version (redirect stderr to stdout to capture both)
        result = subprocess.run([javac_cmd, '-version'], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, check=True)
        javac_output = result.stdout.strip()

        # Extract version number from output like "javac 21.0.8"
        version_match = re.search(r'javac\s+(\d+)\.(\d+)\.(\d+)', javac_output)
        if version_match:
            major = version_match.group(1)
            minor = version_match.group(2)
            full_version = f"{major}.{minor}.{version_match.group(3)}"
        else:
            major = minor = "unknown"
            full_version = "unknown"

        return {
            'javac_ver': full_version,
            'javac_major_ver': major,
            'javac_minor_ver': minor
        }
    except (subprocess.CalledProcessError, FileNotFoundError):
        return {
            'javac_ver': 'unknown',
            'javac_major_ver': 'unknown',
            'javac_minor_ver': 'unknown'
        }


def get_jar_version(jar_name):
    """Get version from a JAR file in lib/ directory."""
    import glob
    import os

    pattern = f"lib/{jar_name}-*.jar"
    jars = glob.glob(pattern)
    if not jars:
        return "unknown"

    # Get the latest jar (by modification time)
    latest_jar = max(jars, key=os.path.getmtime)
    basename = os.path.basename(latest_jar)

    # Extract version from filename like "jcommon-1.0.23.jar" or "commons-cli-1.5.0.jar"
    parts = basename.replace('.jar', '').split('-')
    if len(parts) >= 2:
        return parts[-1]  # Return the last part (the version)
    return "unknown"


def main():
    """Main version detection logic."""
    import sys
    import os

    # Get javac path from command line argument if provided
    javac_path = sys.argv[1] if len(sys.argv) > 1 else None

    # Detect build type
    github_ref = os.environ.get('GITHUB_REF', '')
    github_event = os.environ.get('GITHUB_EVENT_NAME', '')

    is_ci = any(os.environ.get(var) for var in ['CI', 'GITHUB_ACTIONS', 'GITLAB_CI', 'TRAVIS', 'CIRCLECI'])
    is_triggered_build = github_event in ['schedule', 'workflow_dispatch']

    # Get JAR versions
    jar_names = [
        "jcommon", "jfreechart", "opencsv", "commons-cli",
        "commons-lang3", "slf4j-api",
        "logback-classic", "logback-core"
    ]

    jar_versions = {}
    for jar_name in jar_names:
        key = jar_name.replace('-', '_').upper() + '_VER'
        jar_versions[key] = get_jar_version(jar_name)

    # Get JAVAC information
    javac_info = get_javac_info(javac_path)

    # Output all variables in Makefile format
    print(f"ECUXPLOT_VER := {get_git_describe()}")
    print(f"SEM_VER ?= {get_jpackage_ver()}")
    print(f"ASSET_VER ?= {'latest' if (is_ci and is_triggered_build) else get_git_describe()}")
    print(f"JPACKAGE_VER := {get_jpackage_ver()}")
    print(f"RC := {get_git_describe().split('-', 1)[1] if '-' in get_git_describe() else ''}")
    for key, value in jar_versions.items():
        print(f"{key} := {value}")
    print(f"JAVAC_VER := {javac_info['javac_ver']}")
    print(f"JAVAC_MAJOR_VER := {javac_info['javac_major_ver']}")
    print(f"JAVAC_MINOR_VER := {javac_info['javac_minor_ver']}")


if __name__ == "__main__":
    main()
