#!/bin/bash

# ECUxPlot macOS Sanity Check Script
# This script performs comprehensive checks on the macOS app bundle structure
# and provides detailed information about layouts at different stages.

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check file existence and permissions
check_file() {
    local file="$1"
    local description="$2"

    if [ -f "$file" ]; then
        local perms=$(ls -l "$file" | awk '{print $1}')
        local size=$(ls -lh "$file" | awk '{print $5}')
        print_success "$description: $file ($perms, $size)"
        return 0
    else
        print_error "$description: $file (NOT FOUND)"
        return 1
    fi
}

# Function to check directory existence
check_dir() {
    local dir="$1"
    local description="$2"

    if [ -d "$dir" ]; then
        local count=$(find "$dir" -type f | wc -l)
        print_success "$description: $dir ($count files)"
        return 0
    else
        print_error "$description: $dir (NOT FOUND)"
        return 1
    fi
}

# Function to detect version from build.properties
detect_version() {
    local mac_app="$1"

    # Try build.properties first (most accurate)
    if [ -f "build/build.properties" ]; then
        local target=$(grep "^TARGET=" build/build.properties | cut -d'=' -f2)
        if [ -n "$target" ]; then
            # Extract version from TARGET (e.g., ECUxPlot-v1.0.4-1-gdac4-dirty -> v1.0.4-1-gdac4-dirty)
            echo "$target" | sed 's/ECUxPlot-//'
            return 0
        fi
    fi

    # Fallback to version.txt files
    local version_file="$mac_app/Contents/app/version.txt"
    if [ -f "$version_file" ]; then
        cat "$version_file"
        return 0
    fi

    # Final fallback to build/version.txt
    if [ -f "build/version.txt" ]; then
        cat "build/version.txt"
        return 0
    fi

    echo "unknown"
}

# Function to get JAR versions from build.properties
get_jar_versions() {
    if [ -f "build/build.properties" ]; then
        # Extract JAR names and versions
        local ecuxplot_jars=$(grep "^ECUXPLOT_JARS=" build/build.properties | cut -d'=' -f2)
        local common_jars=$(grep "^COMMON_JARS=" build/build.properties | cut -d'=' -f2)

        echo "ECUXPLOT_JARS: $ecuxplot_jars"
        echo "COMMON_JARS: $common_jars"
    else
        echo "build.properties not found"
    fi
}

# Function to check JAR files using build.properties
check_jars_from_build_properties() {
    local app_lib="$1"

    if [ ! -f "build/build.properties" ]; then
        print_error "build.properties not found - cannot check JAR files"
        return 1
    fi

    # Get JAR lists from build.properties
    local ecuxplot_jars=$(grep "^ECUXPLOT_JARS=" build/build.properties | cut -d'=' -f2)
    local common_jars=$(grep "^COMMON_JARS=" build/build.properties | cut -d'=' -f2)

    # Check ECUxPlot JARs
    print_info "Checking ECUxPlot JARs:"
    for jar in $ecuxplot_jars; do
        check_file "$app_lib/$jar" "ECUxPlot JAR: $jar"
    done

    # Check Common JARs
    print_info "Checking Common JARs:"
    for jar in $common_jars; do
        check_file "$app_lib/$jar" "Common JAR: $jar"
    done
}

# Function to perform jpackage stage checks
check_jpackage() {
    local mac_app="$1"
    local contents_dir="$mac_app/Contents"
    local macos_dir="$contents_dir/MacOS"
    local resources_dir="$contents_dir/Resources"

    # Basic structure check
    print_info "=== Basic Structure ==="
    check_dir "$mac_app" "App bundle root"
    check_dir "$contents_dir" "Contents directory"
    check_file "$contents_dir/Info.plist" "Info.plist"
    check_dir "$macos_dir" "MacOS directory"
    check_dir "$resources_dir" "Resources directory"
    echo

    # Executable check
    local executable="$macos_dir/ECUxPlot"
    print_info "=== Executable ==="
    check_file "$executable" "Main executable"
    if [ -f "$executable" ]; then
        local arch=$(file "$executable" | grep -o 'x86_64\|arm64')
        print_info "Architecture: $arch"
    fi
    echo

    # App structure check
    local app_dir="$contents_dir/app"
    print_info "=== App Structure ==="
    check_dir "$app_dir" "App directory"
    if [ -d "$app_dir" ]; then
        local main_jar=$(find "$app_dir" -name "ECUxPlot-*.jar" | head -1)
        check_file "$main_jar" "Main JAR"
        check_file "$app_dir/mapdump.jar" "mapdump JAR"
        check_file "$app_dir/ECUxPlot.cfg" "ECUxPlot.cfg"

        local jar_count=$(find "$app_dir/lib" -name "*.jar" 2>/dev/null | wc -l)
        print_info "Library JARs: $jar_count"
    fi
}

# Function to perform runtime stage checks
check_runtime() {
    local mac_app="$1"
    local contents_dir="$mac_app/Contents"
    local runtime_dir="$contents_dir/runtime"

    print_info "=== Runtime ==="
    check_dir "$runtime_dir" "Runtime directory"
    if [ -d "$runtime_dir" ]; then
        local runtime_home_dir="$runtime_dir/Contents/Home"
	check_file "$runtime_dir/Contents/Info.plist" "Runtime contents Info.plist"
        check_dir "$runtime_home_dir" "Runtime Home directory"
        check_dir "$runtime_home_dir/conf" "Runtime Home conf directory"
        check_dir "$runtime_home_dir/lib" "Runtime Home lib directory"
        check_file "$runtime_home_dir/lib/libjli.dylib" "libjli.dylib"
    fi
}

# Main sanity check function
sanity_check() {
    local stage="$1"
    local mac_app="$2"

    # Detect version dynamically
    local version=$(detect_version "$mac_app")
    print_info "=== Starting sanity check for stage: $stage ==="
    print_info "Detected version: $version"
    print_info "Checking app bundle: $mac_app"
    echo

    # Call appropriate check function based on stage
    if [ "$stage" = "jpackage" ]; then
        check_jpackage "$mac_app"
    elif [ "$stage" = "runtime" ]; then
        check_runtime "$mac_app"
    else
        print_error "Unknown stage: $stage"
        return 1
    fi
    print_info "=== Sanity check complete for stage: $stage ==="
    echo
}

# Main execution
if [ $# -lt 2 ]; then
    echo "Usage: $0 <stage> <mac_app_path>"
    echo "  stage: jpackage, runtime"
    echo "  mac_app_path: path to ECUxPlot.app bundle"
    echo ""
    echo "Examples:"
    echo "  $0 jpackage build/Darwin/ECUxPlot.app"
    echo "  $0 runtime build/Darwin/ECUxPlot.app"
    exit 1
fi

sanity_check "$1" "$2"
