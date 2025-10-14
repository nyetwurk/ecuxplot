#!/bin/bash
# Script to get the latest Launch4j version
# This can be used by Dependabot or manual updates

set -e

# Get the latest Launch4j version from SourceForge
LATEST_VERSION=$(wget -qO- https://sourceforge.net/projects/launch4j/files/launch4j-3/ | grep -o 'launch4j-3.[0-9]*' | sort -V | tail -1)

if [ -z "$LATEST_VERSION" ]; then
    echo "Error: Could not determine latest Launch4j version"
    exit 1
fi

echo "Latest Launch4j version: $LATEST_VERSION"

# Update the version in workflow files
for workflow in .github/workflows/*.yml; do
    if grep -q "LAUNCH4J_VERSION=" "$workflow"; then
        echo "Updating $workflow with version $LATEST_VERSION"
        sed -i "s/LAUNCH4J_VERSION=.*/LAUNCH4J_VERSION=$LATEST_VERSION/" "$workflow"
    fi
done

echo "Launch4j version updated to $LATEST_VERSION"
