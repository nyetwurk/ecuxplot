#!/usr/bin/env bash
# Generate git-cliff release notes for a version tag.
#
# Uses an explicit PREV..TAG range instead of --current so promote-on-same-commit
# works when an RC tag shares the SHA (git describe + --current breaks then).
#
# Usage:
#   ./scripts/release-notes.sh v1.2.7
#   ./scripts/release-notes.sh v1.2.7-rc1 --output /tmp/notes.md
#   ./scripts/release-notes.sh v1.2.7 --print-args   # for GitHub Actions
#   ./scripts/release-notes.sh v1.2.7 --dry-run

set -euo pipefail

CONFIG="${CLIFF_CONFIG:-cliff.toml}"

usage() {
    cat <<'EOF'
usage: release-notes.sh <tag> [options]

Options:
  --output PATH     Write changelog to PATH (default: stdout)
  --print-args      Print "args=..." for GITHUB_OUTPUT (CI)
  --dry-run         Print the git-cliff command without running it
  --config PATH     cliff.toml path (default: cliff.toml)
  -h, --help        Show this help
EOF
}

tag_commit() {
    local tag="$1"
    if git rev-parse "refs/tags/${tag}^{commit}" >/dev/null 2>&1; then
        git rev-parse "refs/tags/${tag}^{commit}"
    else
        git rev-parse HEAD
    fi
}

# Previous version tag before TAG in v:refname order, skipping other tags on the
# same commit (e.g. v1.2.7-rc1 when releasing v1.2.7 on the same SHA).
find_prev() {
    local tag="$1" pattern="$2"
    local commit prev="" t c

    commit="$(tag_commit "$tag")"
    while IFS= read -r t; do
        if [[ "$t" == "$tag" ]]; then
            printf '%s\n' "$prev"
            return
        fi
        c="$(git rev-parse "refs/tags/${t}^{commit}" 2>/dev/null || true)"
        if [[ -z "$c" || "$c" == "$commit" ]]; then
            continue
        fi
        prev="$t"
    done < <(git tag -l --sort=v:refname | grep -E "$pattern" || true)
    printf '%s\n' "$prev"
}

tag_pattern() {
    local tag="$1"
    if [[ "$tag" == *"-rc"* || "$tag" == *"-beta"* || "$tag" == *"-alpha"* ]]; then
        printf '%s\n' '^v[0-9]+\.[0-9]+\.[0-9]+(-rc[0-9]+)?$'
    elif [[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        printf '%s\n' '^v[0-9]+\.[0-9]+\.[0-9]+$'
    else
        echo "release-notes.sh: unsupported tag name: $tag" >&2
        return 1
    fi
}

build_args_string() {
    local tag="$1" pattern prev
    pattern="$(tag_pattern "$tag")"
    prev="$(find_prev "$tag" "$pattern")"
    if [[ -n "$prev" ]]; then
        printf '%s\n' "--tag ${tag} --tag-pattern '${pattern}' ${prev}..${tag} --strip header"
    else
        printf '%s\n' "--tag ${tag} --tag-pattern '${pattern}' --strip header"
    fi
}

run_git_cliff() {
    local tag="$1" output="${2:-}" pattern prev
    pattern="$(tag_pattern "$tag")"
    prev="$(find_prev "$tag" "$pattern")"

    if [[ -n "$output" ]]; then
        if [[ -n "$prev" ]]; then
            git-cliff --config="$CONFIG" --tag "$tag" --tag-pattern "$pattern" \
                "${prev}..${tag}" --strip header -o "$output"
        else
            git-cliff --config="$CONFIG" --tag "$tag" --tag-pattern "$pattern" \
                --strip header -o "$output"
        fi
    else
        if [[ -n "$prev" ]]; then
            git-cliff --config="$CONFIG" --tag "$tag" --tag-pattern "$pattern" \
                "${prev}..${tag}" --strip header
        else
            git-cliff --config="$CONFIG" --tag "$tag" --tag-pattern "$pattern" \
                --strip header
        fi
    fi
}

main() {
    local tag="" output="" print_args=false dry_run=false

    if [[ $# -eq 0 ]]; then
        usage >&2
        exit 1
    fi

    while [[ $# -gt 0 ]]; do
        case "$1" in
            -h|--help)
                usage
                exit 0
                ;;
            --output)
                output="${2:?--output requires a path}"
                shift 2
                ;;
            --print-args)
                print_args=true
                shift
                ;;
            --dry-run)
                dry_run=true
                shift
                ;;
            --config)
                CONFIG="${2:?--config requires a path}"
                shift 2
                ;;
            -*)
                echo "release-notes.sh: unknown option: $1" >&2
                exit 1
                ;;
            *)
                if [[ -n "$tag" ]]; then
                    echo "release-notes.sh: unexpected argument: $1" >&2
                    exit 1
                fi
                tag="$1"
                shift
                ;;
        esac
    done

    if [[ -z "$tag" ]]; then
        echo "release-notes.sh: tag name required" >&2
        exit 1
    fi

    if $print_args; then
        echo "args=$(build_args_string "$tag")"
        exit 0
    fi

    if $dry_run; then
        local args
        args="$(build_args_string "$tag")"
        if [[ -n "$output" ]]; then
            echo "git-cliff --config=$CONFIG $args -o $output"
        else
            echo "git-cliff --config=$CONFIG $args"
        fi
        exit 0
    fi

    if ! command -v git-cliff >/dev/null 2>&1; then
        echo "release-notes.sh: git-cliff not found in PATH" >&2
        exit 1
    fi

    run_git_cliff "$tag" "$output"
}

main "$@"
