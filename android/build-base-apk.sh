#!/usr/bin/env bash
set -euo pipefail

android_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
cd -- "$android_dir"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}" ./gradlew assembleRelease -Pmkxpz.baseApk=true
mkdir -p build-games
cp -- app/build/outputs/apk/release/app-release-unsigned.apk build-games/mkxpz-android-base.apk
printf 'Base APK: %s/build-games/mkxpz-android-base.apk\n' "$android_dir"
