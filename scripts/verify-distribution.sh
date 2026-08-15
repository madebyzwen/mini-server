#!/usr/bin/env bash

set -euo pipefail

archive=${1:-}
expected_version=${2:-}

if [[ -z "$archive" || ! -f "$archive" ]]; then
    echo "Distribution ZIP does not exist: $archive" >&2
    exit 1
fi

mapfile -t entries < <(unzip -Z1 "$archive")

if (( ${#entries[@]} == 0 )); then
    echo "Distribution ZIP is empty: $archive" >&2
    exit 1
fi

declare -A roots=()
for entry in "${entries[@]}"; do
    root=${entry%%/*}
    if [[ -n "$root" ]]; then
        roots["$root"]=1
    fi
done

if (( ${#roots[@]} != 1 )); then
    echo "Distribution ZIP must contain exactly one top-level directory." >&2
    exit 1
fi

root_names=("${!roots[@]}")
distribution_root=${root_names[0]}

contains_entry() {
    local required=$1
    local entry
    for entry in "${entries[@]}"; do
        if [[ "$entry" == "$required" ]]; then
            return 0
        fi
    done
    return 1
}

required_entries=(
    "$distribution_root/mini-server.jar"
    "$distribution_root/lib/"
    "$distribution_root/config/"
    "$distribution_root/config/start-sites.txt"
    "$distribution_root/www/"
    "$distribution_root/www/_shared/mini-api.js"
    "$distribution_root/www/example/"
    "$distribution_root/www/example/index.html"
    "$distribution_root/miniweb-template.zip"
    "$distribution_root/start.bat"
    "$distribution_root/stop.bat"
    "$distribution_root/README.txt"
)

for required in "${required_entries[@]}"; do
    if ! contains_entry "$required"; then
        echo "Distribution ZIP is missing required content: $required" >&2
        exit 1
    fi
done

shared_start_sites=$(unzip -p \
    "$archive" \
    "$distribution_root/config/start-sites.txt")
if ! awk '
    {
        line = $0
        sub(/^[[:space:]]+/, "", line)
        sub(/[[:space:]]+$/, "", line)
        if (line != "" && substr(line, 1, 1) != "#" && line == "example") {
            found = 1
        }
    }
    END { exit(found ? 0 : 1) }
' <<< "$shared_start_sites"; then
    echo "Shared start-site configuration must contain active entry: example" >&2
    exit 1
fi

start_site_file_count=0
for entry in "${entries[@]}"; do
    if [[ "$entry" == */start-sites.txt ]]; then
        start_site_file_count=$((start_site_file_count + 1))
        if [[ "$entry" != "$distribution_root/config/start-sites.txt" ]]; then
            echo "Distribution ZIP contains a pre-created non-Shared start-site file: $entry" >&2
            exit 1
        fi
    fi
done
if (( start_site_file_count != 1 )); then
    echo "Distribution ZIP must contain exactly one Shared start-site file." >&2
    exit 1
fi

start_launcher=$(unzip -p "$archive" "$distribution_root/start.bat")
stop_launcher=$(unzip -p "$archive" "$distribution_root/stop.bat")

if grep -Eiq '(^|[[:space:]])(pushd|popd)([[:space:]]|$)' \
        <<< "$start_launcher$stop_launcher"; then
    echo "Distribution BAT launchers must not depend on pushd or popd." >&2
    exit 1
fi
if ! grep -Fq \
        'start "" javaw -cp "%~dp0mini-server.jar;%~dp0lib\*" io.github.madebyzwen.miniserver.MiniServer' \
        <<< "$start_launcher"; then
    echo "start.bat must launch Mini Server through javaw with absolute batch-relative paths." >&2
    exit 1
fi
if grep -Eiq '(^|[[:space:]])java(.exe)?[[:space:]]+-cp' <<< "$start_launcher"; then
    echo "start.bat must not run the server synchronously through java.exe." >&2
    exit 1
fi
if ! grep -Fq \
        'java -cp "%~dp0mini-server.jar;%~dp0lib\*" io.github.madebyzwen.miniserver.MiniServer stop' \
        <<< "$stop_launcher"; then
    echo "stop.bat must invoke the Mini Server stop command with absolute batch-relative paths." >&2
    exit 1
fi

runtime_dependency_count=0
for entry in "${entries[@]}"; do
    relative=${entry#"$distribution_root"/}

    case "$relative" in
        startup.lock|*/startup.lock|instance.lock|*/instance.lock|instance.json|*/instance.json|MiniServerData|MiniServerData/*|*/MiniServerData|*/MiniServerData/*|target|target/*|src|src/*|template|template/*)
            echo "Distribution ZIP contains forbidden content: $entry" >&2
            exit 1
            ;;
    esac

    if [[ "$relative" == lib/*.jar ]]; then
        runtime_dependency_count=$((runtime_dependency_count + 1))
    fi
done

if (( runtime_dependency_count == 0 )); then
    echo "Distribution ZIP contains no runtime dependencies below lib/." >&2
    exit 1
fi

if [[ -n "$expected_version" ]]; then
    expected_archive="mini-server-$expected_version.zip"
    expected_root="mini-server-$expected_version"

    if [[ "$(basename "$archive")" != "$expected_archive" ]]; then
        echo "Release ZIP filename does not match the calculated version." >&2
        exit 1
    fi
    if [[ "$distribution_root" != "$expected_root" ]]; then
        echo "Release distribution directory does not match the calculated version." >&2
        exit 1
    fi
    if [[ "$(basename "$archive")" == *SNAPSHOT* || "$distribution_root" == *SNAPSHOT* ]]; then
        echo "Release distribution must not contain SNAPSHOT in its name." >&2
        exit 1
    fi
fi

printf 'Verified distribution: %s\n' "$archive"
printf 'Distribution root: %s\n' "$distribution_root"
printf 'Runtime dependencies: %d\n' "$runtime_dependency_count"
