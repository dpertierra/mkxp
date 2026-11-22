# Source this before running Meson/Ninja for the Android build:
#
#   source android/deps/vars.sh
#   meson setup --cross-file android/meson-android-arm64.txt android/build-arm64

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
DEPS_ARCH="${ARCH:-arm64}"
DEPS_PREFIX="$DIR/build-${DEPS_ARCH}"

ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$HOME/Android/Sdk/ndk/30.0.16248370}"
ANDROID_TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"

export ANDROID_DEP_PREFIX="$DEPS_PREFIX"
# Reset PKG_CONFIG_PATH so host .pc files never leak into the Android build.
export PKG_CONFIG_PATH=""
export PKG_CONFIG_LIBDIR="$DEPS_PREFIX/lib/pkgconfig"
export CFLAGS="-I$DEPS_PREFIX/include"
export CXXFLAGS="$CFLAGS"
export LDFLAGS="-L$DEPS_PREFIX/lib"
export PATH="$ANDROID_TOOLCHAIN:$DEPS_PREFIX/bin:$PATH"
export CC="$ANDROID_TOOLCHAIN/aarch64-linux-android26-clang"
export CXX="$ANDROID_TOOLCHAIN/aarch64-linux-android26-clang++"
export AR="$ANDROID_TOOLCHAIN/llvm-ar"
