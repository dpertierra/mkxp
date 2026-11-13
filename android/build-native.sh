#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
DEPS_PREFIX="${ANDROID_DEP_PREFIX:-$SCRIPT_DIR/deps/build-arm64}"
NDK="${ANDROID_NDK_HOME:-$HOME/Android/Sdk/ndk/30.0.16248370}"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64"
BUILD_DIR="${MKXPZ_ANDROID_BUILD_DIR:-$SCRIPT_DIR/build-native}"
DEST_DIR="$SCRIPT_DIR/app/src/main/jniLibs/arm64-v8a"

fail() {
    echo "error: $*" >&2
    exit 1
}

[[ -d "$DEPS_PREFIX/lib/pkgconfig" ]] || fail "Missing deps: $DEPS_PREFIX (see android/deps/README.md)"
DEPS_PREFIX="$(cd "$DEPS_PREFIX" && pwd)"
[[ -x "$TOOLCHAIN/bin/aarch64-linux-android26-clang++" ]] || fail "Missing NDK toolchain: $TOOLCHAIN"
export PATH="$TOOLCHAIN/bin:$PATH"
export PKG_CONFIG_LIBDIR="$DEPS_PREFIX/lib/pkgconfig"
export PKG_CONFIG_PATH=""
unset PKG_CONFIG_SYSROOT_DIR

for tool in meson ninja pkg-config xxd llvm-readelf llvm-nm llvm-strip; do
    command -v "$tool" >/dev/null || fail "Missing host tool: $tool"
done
pkg-config --exists ruby-3.1 physfs openal sdl2 SDL2_sound SDL2_ttf \
    SDL2_image freetype2 pixman-1 libpng zlib uchardet vorbisfile opusfile \
    || fail "Incomplete deps prefix: $DEPS_PREFIX (run make -C android/deps)"
[[ "$(pkg-config --modversion sdl2)" == 2.28.1 ]] \
    || fail "Native SDL must match fetch-sdl-java.sh (2.28.1)"
[[ -f "$DEPS_PREFIX/lib/libruby-ext.a" && -f "$DEPS_PREFIX/lib/libiconv.a" ]] \
    || fail "Missing libruby-ext.a or libiconv.a in $DEPS_PREFIX/lib"

mkdir -p "$BUILD_DIR"
BUILD_DIR="$(cd "$BUILD_DIR" && pwd)"
SETUP_ARGS=()
if [[ -f "$BUILD_DIR/meson-private/coredata.dat" ]]; then
    SETUP_ARGS+=(--reconfigure --clearcache)
fi
meson setup "${SETUP_ARGS[@]}" "$BUILD_DIR" "$ROOT_DIR" \
    --cross-file "$SCRIPT_DIR/meson-cross-arm64.ini" --prefix=/ --libdir=lib \
    -Dbuildtype=release -Dstrip=true -Dmri_version=3.1 -Dgfx_backend=gles \
    -Denable-https=false -Duse_miniffi=false -Dshared_fluid=false
ninja -C "$BUILD_DIR" -j "${JOBS:-$(nproc)}"
meson install -C "$BUILD_DIR" --destdir "$BUILD_DIR/stage" --no-rebuild

LIBRARIES=("$BUILD_DIR/stage/lib/libmain.so" "$DEPS_PREFIX/lib/libSDL2.so"
    "$TOOLCHAIN/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so")
for library in "${LIBRARIES[@]}"; do
    [[ -f "$library" ]] || fail "Missing library: $library"
    HEADER="$(llvm-readelf -h "$library")"
    grep -q 'Machine:.*AArch64' <<< "$HEADER" || fail "Not arm64: $library"
    DYNAMIC="$(llvm-readelf -d "$library")"
    if grep -Eq '\((RUNPATH|RPATH)\)' <<< "$DYNAMIC"; then
        fail "Host library search path in $library"
    fi
    echo "$(basename "$library") NEEDED:"
    while IFS= read -r needed; do
        case "$needed" in
            libSDL2.so|libc++_shared.so|liblog.so|libandroid.so|libGLESv1_CM.so|libGLESv2.so|libEGL.so|libOpenSLES.so|libm.so|libdl.so|libc.so|libz.so) ;;
            *) fail "Unexpected dependency in $library: $needed" ;;
        esac
        echo "  $needed"
    done < <(sed -n 's/.*(NEEDED).*\[\(.*\)\].*/\1/p' <<< "$DYNAMIC")
done

MAIN_SYMBOLS="$(llvm-nm -D --defined-only "${LIBRARIES[0]}" | awk '{print $NF}')"
grep -Fx SDL_main <<< "$MAIN_SYMBOLS" >/dev/null || fail "libmain.so does not export SDL_main"
SDL_SYMBOLS="$(llvm-nm -D --defined-only "${LIBRARIES[1]}" | awk '{print $NF}')"
for symbol in JNI_OnLoad Java_org_libsdl_app_SDLActivity_nativeSetupJNI \
    Java_org_libsdl_app_SDLActivity_nativeRunMain \
    Java_org_libsdl_app_SDLAudioManager_nativeSetupJNI \
    Java_org_libsdl_app_SDLControllerManager_nativeSetupJNI; do
    grep -Fx "$symbol" <<< "$SDL_SYMBOLS" >/dev/null || fail "libSDL2.so missing $symbol"
done

mkdir -p "$DEST_DIR"
cp "${LIBRARIES[@]}" "$DEST_DIR/"
llvm-strip --strip-unneeded "$DEST_DIR/libmain.so" "$DEST_DIR/libSDL2.so" "$DEST_DIR/libc++_shared.so"
echo "Native libraries ready in $DEST_DIR"
