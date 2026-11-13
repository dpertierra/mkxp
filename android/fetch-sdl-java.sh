#!/usr/bin/env bash
#
# Fetches the SDL2 Java glue (org.libsdl.app) from a pinned SDL2 release.
#
# SDL's Android Java sources are NOT vendored in this repository: they are
# copied at build time so the Java side always matches the SDL2 native
# version that ships inside the APK.
#
# Usage:
#   ./fetch-sdl-java.sh            # fetch if needed
#   ./fetch-sdl-java.sh --force    # re-download and re-copy
#
set -euo pipefail

SDL_VERSION="2.28.1"
SDL_TAG="release-${SDL_VERSION}"
SDL_SHA256="4977ceba5c0054dbe6c2f114641aced43ce3bf2b41ea64b6a372d6ba129cb15d"
SDL_URL="https://github.com/libsdl-org/SDL/releases/download/${SDL_TAG}/SDL2-${SDL_VERSION}.tar.gz"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEST_DIR="${SCRIPT_DIR}/app/src/main/java/org/libsdl/app"
CACHE_DIR="${MKXPZ_CACHE_DIR:-${HOME}/.cache/mkxpz/sdl}"
TARBALL="${CACHE_DIR}/SDL2-${SDL_VERSION}.tar.gz"

FORCE=0
if [ "${1:-}" = "--force" ]; then
    FORCE=1
fi

mkdir -p "${CACHE_DIR}"

if [ "${FORCE}" = "1" ] || [ ! -f "${TARBALL}" ]; then
    echo "Downloading SDL2 ${SDL_VERSION}…"
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL -o "${TARBALL}.part" "${SDL_URL}"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "${TARBALL}.part" "${SDL_URL}"
    else
        echo "error: neither curl nor wget is available" >&2
        exit 1
    fi
    mv "${TARBALL}.part" "${TARBALL}"
fi

echo "Verifying SDL2 ${SDL_VERSION}…"
if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL_SHA="$(sha256sum "${TARBALL}" | cut -d' ' -f1)"
elif command -v shasum >/dev/null 2>&1; then
    ACTUAL_SHA="$(shasum -a 256 "${TARBALL}" | cut -d' ' -f1)"
else
    echo "error: no sha256sum/shasum available" >&2
    exit 1
fi

if [ "${ACTUAL_SHA}" != "${SDL_SHA256}" ]; then
    echo "error: checksum mismatch for ${TARBALL}" >&2
    echo "       expected ${SDL_SHA256}" >&2
    echo "       got      ${ACTUAL_SHA}" >&2
    exit 1
fi

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

echo "Extracting SDL2 Java sources…"
tar xzf "${TARBALL}" -C "${WORK_DIR}" \
    "SDL2-${SDL_VERSION}/android-project/app/src/main/java/org/libsdl/app"

SRC_DIR="${WORK_DIR}/SDL2-${SDL_VERSION}/android-project/app/src/main/java/org/libsdl/app"

mkdir -p "${DEST_DIR}"
cp "${SRC_DIR}"/*.java "${DEST_DIR}/"

echo "Copied $(ls -1 "${DEST_DIR}"/*.java | wc -l | tr -d ' ') Java files to ${DEST_DIR}"
