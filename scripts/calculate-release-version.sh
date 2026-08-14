#!/usr/bin/env bash

set -euo pipefail

release_type=${1:-}
tag_source=${2:-git}

case "$release_type" in
    patch|minor|major)
        ;;
    *)
        echo "Release type must be patch, minor, or major." >&2
        exit 1
        ;;
esac

tags=()
case "$tag_source" in
    git)
        mapfile -t tags < <(git tag --list)
        ;;
    --tags-from-stdin)
        mapfile -t tags
        ;;
    *)
        echo "Unknown tag source: $tag_source" >&2
        exit 1
        ;;
esac

semantic_tag_pattern='^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'
maximum_component=2147483646
found=false
highest_major=0
highest_minor=0
highest_patch=0

for tag in "${tags[@]}"; do
    if [[ ! "$tag" =~ $semantic_tag_pattern ]]; then
        continue
    fi

    major=${BASH_REMATCH[1]}
    minor=${BASH_REMATCH[2]}
    patch=${BASH_REMATCH[3]}

    for component in "$major" "$minor" "$patch"; do
        if (( ${#component} > 10 )) || (( 10#$component > maximum_component )); then
            echo "Semantic version component is too large to increment safely: $tag" >&2
            exit 1
        fi
    done

    major=$((10#$major))
    minor=$((10#$minor))
    patch=$((10#$patch))

    if [[ "$found" == false ]] \
        || (( major > highest_major )) \
        || (( major == highest_major && minor > highest_minor )) \
        || (( major == highest_major && minor == highest_minor && patch > highest_patch )); then
        found=true
        highest_major=$major
        highest_minor=$minor
        highest_patch=$patch
    fi
done

if [[ "$found" == true ]]; then
    previous_version="$highest_major.$highest_minor.$highest_patch"
else
    previous_version=none
fi

case "$release_type" in
    patch)
        highest_patch=$((highest_patch + 1))
        ;;
    minor)
        highest_minor=$((highest_minor + 1))
        highest_patch=0
        ;;
    major)
        highest_major=$((highest_major + 1))
        highest_minor=0
        highest_patch=0
        ;;
esac

version="$highest_major.$highest_minor.$highest_patch"

printf 'previous_version=%s\n' "$previous_version"
printf 'version=%s\n' "$version"
printf 'tag=v%s\n' "$version"
