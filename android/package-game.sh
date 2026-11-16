#!/usr/bin/env bash
set -euo pipefail

if (( $# < 3 )); then
    echo "Usage: $0 <gameDir> <applicationId> \"<App Name>\" [icon.png] [--] [extra Gradle args...]" >&2
    exit 1
fi

android_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
game_dir=$(realpath -e -- "$1")
application_id=$2
app_name=$3
shift 3

if [[ ! -d "$game_dir" || ! "$application_id" =~ ^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$ || -z "$app_name" ]]; then
    echo "Expected a game directory, a dotted Android applicationId and a nonempty app name." >&2
    exit 1
fi

icon_args=()
if (( $# )) && [[ "$1" != -* ]]; then
    icon=$(realpath -e -- "$1")
    [[ -f "$icon" && "${icon,,}" == *.png ]] || { echo "Icon must be a PNG file." >&2; exit 1; }
    icon_args=("-Pmkxpz.icon=$icon")
    shift
fi
if (( $# )) && [[ "$1" == -- ]]; then shift; fi

staging="$android_dir/build-games/$application_id"
if [[ "$staging/" == "$game_dir/"* || "$game_dir/" == "$staging/"* ]]; then
    echo "Game source and staging directory must not contain one another." >&2
    exit 1
fi
mkdir -p -- "$staging/game"
rsync -rtL --delete --delete-excluded \
    --exclude='.*' \
    --exclude='*.[eE][xX][eE]' --exclude='*.[dD][lL][lL]' \
    --exclude='*.[lL][nN][kK]' --exclude='*.[uU][rR][lL]' \
    --exclude='*.[dD][eE][sS][kK][tT][oO][pP]' --exclude='*.[zZ][iI][pP]' \
    --exclude='*.[aA][pP][kK]' --exclude='/_RELEASES/' \
    --exclude='lib64/' --exclude='Game_Linux' --exclude='graphify-out/' \
    --exclude='poke_updater/' --exclude='MapExporter/' \
    --exclude='AGENTS.md' --exclude='CLAUDE.md' --exclude='skills-lock.json' \
    --exclude='*.makerstudio' \
    -- "$game_dir/" "$staging/game/"

(
    cd -- "$staging"
    find game -type f -print0 | LC_ALL=C sort -z | xargs -0 -r sha256sum --zero | sha256sum | cut -d ' ' -f 1
) > "$staging/game.version"

cd -- "$android_dir"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}" ./gradlew assembleDebug "$@" \
    "-Pmkxpz.applicationId=$application_id" "-Pmkxpz.appName=$app_name" \
    "-Pmkxpz.gameAssets=$staging" "${icon_args[@]}"
cp -- app/build/outputs/apk/debug/app-debug.apk "$staging.apk"
printf 'APK: %s\n' "$staging.apk"
