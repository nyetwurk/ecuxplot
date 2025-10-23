#!/bin/bash
# build-management.sh - ECUxPlot Build Management Script
# This script helps manage the build system and provides utilities
# for checking build status, triggering builds, and managing artifacts.

set -e

# Configuration
REPO_OWNER="nyetwurk"
REPO_NAME="ecuxplot"
GITHUB_API="https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if GitHub CLI is installed
check_gh_cli() {
    if ! command -v gh &> /dev/null; then
        log_error "GitHub CLI (gh) is not installed. Please install it first:"
        echo "  macOS: brew install gh"
        echo "  Linux: https://github.com/cli/cli/releases"
        exit 1
    fi

    if ! gh auth status &> /dev/null; then
        log_error "GitHub CLI is not authenticated. Please run: gh auth login"
        exit 1
    fi
}

# Get the last build hash from the repository
get_last_build_hash() {
    local hash_file=".github/last-build-hash"
    if [ -f "$hash_file" ]; then
        cat "$hash_file"
    else
        echo ""
    fi
}

# Get current HEAD hash
get_current_hash() {
    git rev-parse HEAD
}

# Check if there are changes since last build
check_changes() {
    local last_hash=$(get_last_build_hash)
    local current_hash=$(get_current_hash)

    if [ -z "$last_hash" ] || [ "$last_hash" != "$current_hash" ]; then
        echo "true"
    else
        echo "false"
    fi
}

# Trigger a manual build
trigger_build() {
    log_info "Triggering build and release workflow..."

    check_gh_cli

    gh workflow run build-and-release.yml
    log_success "Build workflow triggered"

    # Show workflow status
    sleep 5
    gh run list --workflow=build-and-release.yml --limit=1
}

# Check the status of the latest build
check_build_status() {
    log_info "Checking latest build status..."

    check_gh_cli

    local latest_run=$(gh run list --workflow=build-and-release.yml --limit=1 --json status,conclusion,createdAt,headSha --jq '.[0]')

    if [ "$latest_run" = "null" ]; then
        log_warning "No builds found"
        return 1
    fi

    local status=$(echo "$latest_run" | jq -r '.status')
    local conclusion=$(echo "$latest_run" | jq -r '.conclusion')
    local created=$(echo "$latest_run" | jq -r '.createdAt')
    local sha=$(echo "$latest_run" | jq -r '.headSha')

    echo "Latest build:"
    echo "  Status: $status"
    echo "  Conclusion: $conclusion"
    echo "  Created: $created"
    echo "  Commit: $sha"

    if [ "$status" = "completed" ] && [ "$conclusion" = "success" ]; then
        log_success "Latest build completed successfully"
        return 0
    elif [ "$status" = "completed" ] && [ "$conclusion" = "failure" ]; then
        log_error "Latest build failed"
        return 1
    else
        log_warning "Latest build is still running"
        return 2
    fi
}

# List available artifacts from recent builds
list_artifacts() {
    log_info "Listing available artifacts..."

    check_gh_cli

    gh run list --workflow=build-and-release.yml --limit=5 --json databaseId,status,conclusion,createdAt,headSha --jq '.[] | select(.status == "completed" and .conclusion == "success") | {id: .databaseId, created: .createdAt, sha: .headSha}' | while read -r run_info; do
        if [ -n "$run_info" ]; then
            local run_id=$(echo "$run_info" | jq -r '.id')
            local created=$(echo "$run_info" | jq -r '.created')
            local sha=$(echo "$run_info" | jq -r '.sha')

            echo "Run ID: $run_id (Created: $created, SHA: $sha)"
            gh run view "$run_id" --log-failed
            echo ""
        fi
    done
}

# Download artifacts from a specific build
download_artifacts() {
    local run_id="$1"

    if [ -z "$run_id" ]; then
        log_error "Please provide a run ID"
        echo "Usage: $0 download-artifacts <run-id>"
        echo "Use '$0 list-artifacts' to see available runs"
        exit 1
    fi

    log_info "Downloading artifacts from run $run_id..."

    check_gh_cli

    gh run download "$run_id"
    log_success "Artifacts downloaded to current directory"
}

# Update the last build hash (for testing)
update_build_hash() {
    local hash="$1"

    if [ -z "$hash" ]; then
        hash=$(get_current_hash)
    fi

    log_info "Updating last build hash to: $hash"

    mkdir -p .github
    echo "$hash" > .github/last-build-hash

    git add .github/last-build-hash
    git commit -m "Update last build hash: $hash" || true

    log_success "Last build hash updated"
}

# Show help
show_help() {
    echo "ECUxPlot Build Management Script"
    echo ""
    echo "Usage: $0 <command> [options]"
    echo ""
    echo "Commands:"
    echo "  trigger              Trigger a manual build"
    echo "  status               Check the status of the latest build"
    echo "  list-artifacts       List available artifacts from recent builds"
    echo "  download-artifacts   Download artifacts from a specific build"
    echo "  check-changes        Check if there are changes since last build"
    echo "  update-hash [hash]   Update the last build hash (for testing)"
    echo "  help                 Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 trigger"
    echo "  $0 status"
    echo "  $0 download-artifacts 1234567890"
    echo "  $0 check-changes"
}

# Main script logic
main() {
    case "${1:-help}" in
        trigger)
            trigger_build
            ;;
        status)
            check_build_status
            ;;
        list-artifacts)
            list_artifacts
            ;;
        download-artifacts)
            download_artifacts "$2"
            ;;
        check-changes)
            if [ "$(check_changes)" = "true" ]; then
                log_info "Changes detected since last build"
                echo "Last build hash: $(get_last_build_hash)"
                echo "Current hash: $(get_current_hash)"
            else
                log_info "No changes detected since last build"
            fi
            ;;
        update-hash)
            update_build_hash "$2"
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            log_error "Unknown command: $1"
            show_help
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"
