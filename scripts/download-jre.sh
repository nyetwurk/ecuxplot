#!/bin/bash
# Helper script to download JRE for cross-platform builds

set -e

JAVA_TARGET_VER=${1:-18}
PLATFORM=${2:-}
RUNTIME_REPO="adoptium/temurin${JAVA_TARGET_VER}-binaries"
RUNTIME_API="https://api.github.com/repos/${RUNTIME_REPO}/releases"

# Map platform names to GitHub asset names
case "$PLATFORM" in
    "Linux")
        ASSET_PATTERN="linux"
        ;;
    "Darwin")
        ASSET_PATTERN="mac"
        RELEASE_FILE="Contents/Home/release"
        ;;
    "CYGWIN_NT")
        ASSET_PATTERN="windows"
        ;;
    *)
        echo "Error: Unknown platform '$PLATFORM'"
        echo "Usage: $0 <java_version> <platform>"
        echo "Platforms: Linux, Darwin, CYGWIN_NT"
        exit 1
        ;;
esac

# Get API response from GitHub
echo "Downloading manifest from $RUNTIME_API"
API_RESPONSE=$(curl -sL "$RUNTIME_API")

# Verify API response is valid JSON
if ! echo "$API_RESPONSE" | jq empty 2>/dev/null; then
    echo "Error: API response is not valid JSON"
    echo "First few lines of response:"
    echo "$API_RESPONSE" | head -5
    exit 1
fi

# Verify API response is an array (expected GitHub releases format)
if ! echo "$API_RESPONSE" | jq -e 'type == "array"' >/dev/null 2>&1; then
    echo "Error: API response is not an array as expected"
    echo "Response type: $(echo "$API_RESPONSE" | jq -r 'type')"

    # Check if it's a GitHub API error with a message field
    if echo "$API_RESPONSE" | jq -e '.message' >/dev/null 2>&1; then
        echo "GitHub API Error: $(echo "$API_RESPONSE" | jq -r '.message')"
    else
        echo "First few lines of response:"
        echo "$API_RESPONSE" | head -5
    fi
    exit 1
fi

# Check if array is empty
if ! echo "$API_RESPONSE" | jq -e 'length > 0' >/dev/null 2>&1; then
    echo "Error: API response contains no releases"
    echo "Response:"
    echo "$API_RESPONSE"
    exit 1
fi

# Set up variables for cleaner code
RUNTIME_DIR="runtime/$PLATFORM"
STAMP_FILE="$RUNTIME_DIR/java-${JAVA_TARGET_VER}.stamp"

# Find JRE files (x64 architecture, tar.gz or zip format)
DOWNLOAD_URL=""
RELEASE_FILTER=".[] | select(.tag_name | startswith(\"jdk-${JAVA_TARGET_VER}\")) | .tag_name"

echo "Searching for releases..."
for release in $(echo "$API_RESPONSE" | jq -r "$RELEASE_FILTER" | head -5); do
    ASSET_FILTER=".[] | select(.tag_name == \"$release\") | .assets[]? | .name"
    ASSETS=$(echo "$API_RESPONSE" | jq -r "$ASSET_FILTER" 2>/dev/null | \
        grep -E "jre.*x64.*${ASSET_PATTERN}.*\.(tar\.gz|zip)$" | \
        head -1)

    if [ -n "$ASSETS" ]; then
        URL_FILTER=".[] | select(.tag_name == \"$release\") | .assets[]? | select(.name == \"$ASSETS\") | .browser_download_url"
        DOWNLOAD_URL=$(echo "$API_RESPONSE" | jq -r "$URL_FILTER" 2>/dev/null)
        echo "Found download URL: $DOWNLOAD_URL"
        break
    fi
done

if [ -z "$DOWNLOAD_URL" ] || [ "$DOWNLOAD_URL" = "null" ]; then
    echo "Error: Failed to get download URL from GitHub API"
    exit 1
fi

# Download the file
FILENAME=$(basename "$DOWNLOAD_URL")
wget -q -O "runtime/$FILENAME" "$DOWNLOAD_URL"

echo "Extracting $FILENAME..."

rm -rf "$RUNTIME_DIR"
mkdir -p "$RUNTIME_DIR"

if [[ "$FILENAME" == *.zip ]]; then
    unzip -q "runtime/$FILENAME" -d "$RUNTIME_DIR"
else
    # For tar.gz files, extract without stripping components first
    tar -xzf "runtime/$FILENAME" -C "$RUNTIME_DIR"
fi

# Find the extracted directory and move its contents up
# Note: We keep macOS JDK structure as-is (Contents/Home) - MacOS.mk handles flattening
cd "$RUNTIME_DIR"
for dir in jdk-*; do
    if [ -d "$dir" ]; then
	mv "$dir"/* .
        rm -rf "$dir"
        break
    fi
done
cd - > /dev/null # Go back to original directory

# Create release file
[ -n "$RELEASE_FILE" ] && cp "$RUNTIME_DIR/$RELEASE_FILE" "$RUNTIME_DIR/release"
touch "$STAMP_FILE"

# Clean up downloaded file
rm -f "runtime/$FILENAME"
echo Success.
