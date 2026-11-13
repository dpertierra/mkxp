# Android dependencies (arm64)

Cross-compiles every native library MKXP-Z needs into a self-contained prefix,
modeled on `linux/Makefile` (same pinned versions and flags where they apply).

Output prefix: `android/deps/build-arm64/` with `lib/`, `include/` and
`lib/pkgconfig/`. `build-*/` and `downloads/` are git-ignored; the prefix is
regenerated from source, never committed.

## Requirements

- Android NDK r26+ (default `$HOME/Android/Sdk/ndk/30.0.16248370`, override with
  `ANDROID_NDK_HOME` or `NDK=/path`), API 26, `arm64-v8a`.
- Host tools: `cmake`, `make`, `git`, `pkg-config`, autoconf/automake/libtool
  (for libogg and Ruby), `patch`-capable `sed`.
- Host Ruby for cross-building MRI (`BASERUBY`, defaults to `ruby` on `PATH`).
  It must also provide the `erb` library: Ruby's own build scripts
  (`tool/insns2vm.rb`, `tool/generic_erb.rb`) `require 'erb'` while running with
  `--disable=gems`, so a host install without erb fails with
  `cannot load such file -- erb (LoadError)`. Fix with `gem install erb` (or the
  distro package, e.g. Arch `ruby-erb`). The Makefile finds erb through
  rubygems and passes its directory with `-I<erb>`.

## Usage

```sh
make -C android/deps            # everything (deps-core + ruby)
make -C android/deps deps-core  # everything except Ruby
make -C android/deps sdl2 libpng
```

If a prefix from the initial dependency build already exists, only add the
libraries the engine also needs (no Ruby/SDL rebuild):

```sh
make -C android/deps libopusfile pixman uchardet iconv \
  BUILD_PREFIX=/absolute/path/to/build-arm64 \
  DOWNLOADS=/absolute/path/to/existing/downloads/arm64
```

Keep `DOWNLOADS` pointing at the existing source/build trees when reusing a
prefix; the Makefile's order-only configure prerequisites still need them.

Then, for the engine build:

```sh
./android/build-native.sh
```

`vars.sh` points `PKG_CONFIG_LIBDIR` at `build-arm64/lib/pkgconfig` and clears
`PKG_CONFIG_PATH` so host `.pc` files never leak into the Android build.

## What gets built

| Library | Prefix artifact | Notes |
|---|---|---|
| zlib 1.3.1 | `libz.a` | `.pc` installed under `share/pkgconfig`, copied to `lib/pkgconfig` |
| libpng 1.6.50 | `libpng.a`, `libpng16.a` | CMake; `libpng.pc` symlinked to `libpng16.pc` |
| libogg 1.3.6 | `libogg.a` | autotools |
| libvorbis 1.3.7 | `libvorbis.a`, `libvorbisfile.a`, `libvorbisenc.a` | against our libogg |
| Opus 1.5.2 + opusfile 0.12 | `libopus.a`, `libopusfile.a` | streaming audio and sound effects; opusfile HTTP off |
| Pixman 0.42.2 | `libpixman-1.a` | bitmap regions; GTK/PNG/OpenMP off; AArch64 assembly off because its GNU assembler directives fail with NDK Clang |
| uchardet 0.0.8 | `libuchardet.a` | charset detection; `.pc` corrected to link `c++_shared`, not Android's system `stdc++` stub |
| GNU libiconv 1.18 | `libiconv.a` | legacy text conversion on API 26; no dependency on API 28's bionic iconv |
| FreeType (mkxp-z fork) | `libfreetype.a` | CMake; harfbuzz/bzip2/brotli off, PNG on. CMake does not install a `.pc`, so `freetype2.pc` is generated (version hardcoded, keep in sync with the branch) |
| PhysFS 3.2.0 | `libphysfs.a` | static, no test/docs |
| SDL2 (mkxp-z fork, mkxp-z-2.28.1) | **`libSDL2.so` (shared)** | SDLActivity `System.loadLibrary`s it, so shared is correct here; only SDL2 is shared |
| SDL2_image (mkxp-z fork) | `libSDL2_image.a` | vendored stb_image backend (no external libpng/libjpeg); JXL off |
| SDL2_ttf (mkxp-z fork) | `libSDL2_ttf.a` | fork patches ANDROID to the SDL Android.mk flow, patched to the desktop CMake path |
| SDL_sound (mkxp-z fork) | `libSDL2_sound.a` | vendored stb_vorbis/libmodplug, so no vorbis/modplug link deps; MIDI off |
| OpenAL Soft 1.24.3 | `libopenal.a` | **OpenSL ES only**; AAudio is not a backend upstream (verified through 1.25.2). C++ library, so consumers must link with the C++ driver (`libc++_shared`) |
| MRI Ruby 3.1.3 (mkxp-z fork) | `libruby-static.a` + `libruby-ext.a` | see below |

`.pc` files are provided for every dependency, including the ones whose build
systems do not generate them (`SDL2_image`, `SDL2_sound`, `freetype2`,
`ruby-3.1`), and the ones that ship an incomplete one (`SDL2_ttf` misses its
static FreeType dependency).

Every CMake dependency is configured with `-DANDROID_STL=c++_shared`, so the APK
must ship `libc++_shared.so` (the Gradle/native build side owns that).

### Ruby specifics

Configured with `--host=aarch64-linux-android --disable-shared
--disable-install-doc --with-static-linked-ext --disable-rubygems
--with-out-ext=openssl,readline,dbm,gdbm,fiddle`.

Two non-obvious parts:

- `--with-static-linked-ext` builds one `ext/**/<ext>.a` per extension but does
  not put those objects into `libruby-static.a`, while `extinit.o` (which *is* in
  the archive) references `Init_<ext>`. The Makefile merges all ext archives
  into `lib/libruby-ext.a`; `ruby-3.1.pc` lists it in `Libs.private` after
  `-lruby-static`.
- Headers land in `include/ruby-3.1.0/` plus an arch directory
  (`include/ruby-3.1.0/aarch64-linux-android-android/` for `config.h`); the `.pc`
  exposes both.

Smoke test (host compile, no execution):

```sh
export PKG_CONFIG_LIBDIR=$PWD/build-arm64/lib/pkgconfig
$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang \
  -o /tmp/rubytest test.c $(pkg-config --cflags --libs --static ruby-3.1)
```

## Deliberately out of scope (Fase 1 decisions)

- **OpenSSL**: omitted; build with `enable-https=false` until the game needs it.
  Adding it means cloning openssl and appending `-lssl -lcrypto` to the engine.
- **Theora**: omitted (no video playback in the first stage).
- **FluidSynth**: omitted (MIDI not required yet).
- **MiniFFI/fiddle**: `fiddle` is excluded from Ruby; `use_miniffi=false`.
- **Steamworks**: excluded.
- **armeabi-v7a / x86_64**: only `arm64-v8a` is wired up; `ARCH`/`ABI` are
  variables, but `vars.sh` and the prefix name assume arm64.

## Licensing notes

MKXP-Z is GPLv2; SDL2/SDL2_image/SDL2_ttf/SDL_sound are zlib-licensed, PhysFS
zlib, libpng/libogg/libvorbis BSD-like, FreeType FTL/GPLv2, Ruby Ruby-license/
BSD, OpenAL Soft **LGPL-2.1+**. OpenAL Soft is statically linked here, which for
LGPL requires shipping relinkable objects; revisit (shared `libopenal.so` or
object files) before distributing an APK.
