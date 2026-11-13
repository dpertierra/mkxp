# Cross-compile MKXP-Z dependencies for Android (arm64-v8a).
#
# Modeled on linux/Makefile: same pinned versions, same "clone into downloads/,
# build, install into build-<arch>/" flow. Everything installs into a
# self-contained prefix (lib/, include/, lib/pkgconfig/) so the engine's Meson
# only needs PKG_CONFIG_LIBDIR (see vars.sh).
#
# Usage:
#   make -C android/deps                 # everything (deps-core + ruby)
#   make -C android/deps deps-core       # everything except Ruby
#   make -C android/deps sdl2 libpng     # individual libs
#
# Requirements: NDK r26+ (default path below), cmake, autoconf/automake/libtool,
# pkg-config, patch. Not verified: make with Ninja (uses Unix Makefiles).
#
# NOTE SDL2 is built SHARED (libSDL2.so) on purpose: SDLActivity dlopen()s it and
# its Java-side bootstrap expects a shared library. Every other lib is static.
#
# TODO (out of scope for the first stage, see PLAN_PORT_ANDROID.md Fase 1):
#   OpenSSL (HTTPS/HTTPLite), Theora (video), FluidSynth (MIDI).
#   MiniFFI stays off (fiddle is excluded from Ruby), Steamworks is excluded.

ARCH ?= arm64
ABI ?= arm64-v8a
API ?= 26

NDK ?= $(if $(ANDROID_NDK_HOME),$(ANDROID_NDK_HOME),$(HOME)/Android/Sdk/ndk/30.0.16248370)
HOST_TAG ?= linux-x86_64
TOOLCHAIN := $(NDK)/toolchains/llvm/prebuilt/$(HOST_TAG)
TARGET := aarch64-linux-android
TOOLCHAIN_FILE := $(NDK)/build/cmake/android.toolchain.cmake

CC := $(TOOLCHAIN)/bin/$(TARGET)$(API)-clang
CXX := $(TOOLCHAIN)/bin/$(TARGET)$(API)-clang++
AR := $(TOOLCHAIN)/bin/llvm-ar
RANLIB := $(TOOLCHAIN)/bin/llvm-ranlib

OPENAL_FLAGS := -DALSOFT_CPUEXT_NEON=no ${OPENAL_FLAGS}
RUBY_FLAGS := ${RUBY_FLAGS}

BUILD_PREFIX := $(CURDIR)/build-$(ARCH)
LIBDIR := $(BUILD_PREFIX)/lib
INCLUDEDIR := $(BUILD_PREFIX)/include
DOWNLOADS := $(CURDIR)/downloads/$(ARCH)
NPROC := $(shell nproc)

CFLAGS := -I$(INCLUDEDIR) -fPIC -O3 ${ARCH_CFLAGS}
LDFLAGS := -L$(LIBDIR)
PKG_CONFIG_LIBDIR := $(LIBDIR)/pkgconfig

GIT := git
CLONE := $(GIT) clone -q
GITHUB := https://github.com

CONFIGURE_ENV := \
	CMAKE_POLICY_VERSION_MINIMUM=3.10 \
	PKG_CONFIG_LIBDIR=$(PKG_CONFIG_LIBDIR) \
	CC="$(CC)" CXX="$(CXX)" AR="$(AR)" RANLIB="$(RANLIB)" \
	CFLAGS="$(CFLAGS)" CXXFLAGS="$(CFLAGS)" LDFLAGS="$(LDFLAGS)"

CONFIGURE_ARGS := --prefix="$(BUILD_PREFIX)" --host="$(TARGET)"

CMAKE_ARGS := \
	-DCMAKE_TOOLCHAIN_FILE="$(TOOLCHAIN_FILE)" \
	-DANDROID_ABI=$(ABI) \
	-DANDROID_PLATFORM=android-$(API) \
	-DANDROID_STL=c++_shared \
	-DCMAKE_INSTALL_PREFIX="$(BUILD_PREFIX)" \
	-DCMAKE_PREFIX_PATH="$(BUILD_PREFIX)" \
	-DCMAKE_FIND_ROOT_PATH="$(BUILD_PREFIX)" \
	-DCMAKE_C_FLAGS="$(CFLAGS)" \
	-DCMAKE_CXX_FLAGS="$(CFLAGS)" \
	-DCMAKE_BUILD_TYPE=Release

CONFIGURE := $(CONFIGURE_ENV) ./configure $(CONFIGURE_ARGS)
CMAKE     := $(CONFIGURE_ENV) cmake -G "Unix Makefiles" .. $(CMAKE_ARGS)

# Host interpreter used to generate sources for the target build. Required for
# cross-compiling MRI; a host ruby >= 2.7 is enough. Must be defined before
# RUBY_CONFIGURE_ARGS (immediately expanded), or --with-baseruby ends up empty.
BASERUBY ?= $(shell command -v ruby)

# Ruby's build scripts (tool/insns2vm.rb, tool/generic_erb.rb) need the `erb`
# library, but configure appends --disable=gems to BASERUBY so user/bundled gems
# are not visible. Resolve erb through rubygems here and add its lib dir to
# --with-baseruby; host ruby without erb -> `gem install erb` (or the distro's
# ruby-erb package).
ERB_LIB := $(shell $(BASERUBY) -e 'begin; require "erb"; print File.dirname($$LOADED_FEATURES.grep(%r{/erb\.rb$$}).first); rescue LoadError; end' 2>/dev/null)

RUBY_CONFIGURE_ARGS := \
	--enable-install-static-library \
	--disable-shared \
	--disable-install-doc \
	--with-out-ext=openssl,readline,dbm,gdbm,fiddle \
	--with-static-linked-ext \
	--disable-rubygems \
	--without-gmp \
	--with-opt-dir="$(BUILD_PREFIX)" \
	--with-baseruby="$(BASERUBY)$(if $(ERB_LIB), -I$(ERB_LIB))"

default: everything

.PHONY: check-toolchain
check-toolchain:
	@test -f "$(TOOLCHAIN_FILE)" || { \
		echo "NDK not found at $(NDK)"; \
		echo "Set ANDROID_NDK_HOME or NDK=/path/to/ndk"; exit 1; }
	@test -x "$(CC)" || { echo "Missing cross compiler: $(CC)"; exit 1; }
	@test -n "$(BASERUBY)" || echo "warning: no host ruby, 'ruby' target will fail (needs --with-baseruby)"
	@test -n "$(ERB_LIB)" || echo "warning: host ruby has no erb; 'ruby' target will fail (gem install erb)"

# The configure-step products (cmakebuild/Makefile, autotools Makefile) are
# ORDER-ONLY prerequisites of the installed libraries: they must exist before the
# build, but a freshly generated configure output is often timestamped a few
# milliseconds after the install that follows it, which would otherwise make
# every `make` reinstall everything.
# zlib
zlib: init_dirs check-toolchain $(LIBDIR)/libz.a

$(LIBDIR)/libz.a: | $(DOWNLOADS)/zlib/cmakebuild/Makefile
	set -e; cd $(DOWNLOADS)/zlib/cmakebuild; \
	make -j$(NPROC); make install; \
	rm -f $(LIBDIR)/libz.so $(LIBDIR)/libz.so.*; \
	$(call pc_fixup)

$(DOWNLOADS)/zlib/cmakebuild/Makefile: $(DOWNLOADS)/zlib/CMakeLists.txt
	set -e; cd $(DOWNLOADS)/zlib; \
	mkdir -p cmakebuild; cd cmakebuild; \
	$(CMAKE) -DBUILD_SHARED_LIBS=off -DZLIB_BUILD_EXAMPLES=off \
	-DINSTALL_PKGCONFIG_DIR="$(LIBDIR)/pkgconfig"

$(DOWNLOADS)/zlib/CMakeLists.txt:
	$(CLONE) $(GITHUB)/madler/zlib -b v1.3.1 $(DOWNLOADS)/zlib

# Opus decoding is used by both streaming audio and sound effects.
libopus: init_dirs check-toolchain $(LIBDIR)/libopus.a

$(LIBDIR)/libopus.a: | $(DOWNLOADS)/opus/cmakebuild/Makefile
	set -e; cd $(DOWNLOADS)/opus/cmakebuild; make -j$(NPROC); make install

$(DOWNLOADS)/opus/cmakebuild/Makefile: $(DOWNLOADS)/opus/CMakeLists.txt
	set -e; cd $(DOWNLOADS)/opus; mkdir -p cmakebuild; cd cmakebuild; \
	$(CMAKE) -DBUILD_SHARED_LIBS=off -DOPUS_BUILD_PROGRAMS=off -DOPUS_BUILD_TESTING=off

$(DOWNLOADS)/opus/CMakeLists.txt:
	$(CLONE) $(GITHUB)/xiph/opus -b v1.5.2 $(DOWNLOADS)/opus

libopusfile: init_dirs libogg libopus $(LIBDIR)/libopusfile.a

$(LIBDIR)/libopusfile.a: $(LIBDIR)/libogg.a $(LIBDIR)/libopus.a | $(DOWNLOADS)/opusfile/Makefile
	set -e; cd $(DOWNLOADS)/opusfile; make -j$(NPROC); make install

$(DOWNLOADS)/opusfile/Makefile: $(DOWNLOADS)/opusfile/configure
	set -e; cd $(DOWNLOADS)/opusfile; \
	$(CONFIGURE) --enable-static --disable-shared --disable-http --disable-doc --disable-examples

$(DOWNLOADS)/opusfile/configure: $(DOWNLOADS)/opusfile/autogen.sh
	set -e; cd $(DOWNLOADS)/opusfile; ./autogen.sh

$(DOWNLOADS)/opusfile/autogen.sh:
	$(CLONE) $(GITHUB)/xiph/opusfile -b v0.12 $(DOWNLOADS)/opusfile

pixman: init_dirs check-toolchain $(LIBDIR)/libpixman-1.a

$(LIBDIR)/libpixman-1.a: | $(DOWNLOADS)/pixman/Makefile
	set -e; cd $(DOWNLOADS)/pixman; make -j$(NPROC); make install

$(DOWNLOADS)/pixman/Makefile: $(DOWNLOADS)/pixman/autogen.sh
	set -e; cd $(DOWNLOADS)/pixman; $(CONFIGURE_ENV) ./autogen.sh $(CONFIGURE_ARGS) \
	--enable-static --disable-shared --disable-gtk --disable-libpng --disable-openmp --disable-arm-a64-neon

$(DOWNLOADS)/pixman/autogen.sh:
	$(CLONE) https://gitlab.freedesktop.org/pixman/pixman -b pixman-0.42.2 $(DOWNLOADS)/pixman

uchardet: init_dirs check-toolchain $(LIBDIR)/libuchardet.a

$(LIBDIR)/libuchardet.a: | $(DOWNLOADS)/uchardet/cmakebuild/Makefile
	set -e; cd $(DOWNLOADS)/uchardet/cmakebuild; make -j$(NPROC); make install; \
	sed -i 's/-lstdc++/-lc++_shared/g' $(LIBDIR)/pkgconfig/uchardet.pc

$(DOWNLOADS)/uchardet/cmakebuild/Makefile: $(DOWNLOADS)/uchardet/CMakeLists.txt
	set -e; cd $(DOWNLOADS)/uchardet; mkdir -p cmakebuild; cd cmakebuild; \
	$(CMAKE) -DBUILD_SHARED_LIBS=off -DBUILD_BINARY=off

$(DOWNLOADS)/uchardet/CMakeLists.txt:
	$(CLONE) https://gitlab.freedesktop.org/uchardet/uchardet -b v0.0.8 $(DOWNLOADS)/uchardet

iconv: init_dirs check-toolchain $(LIBDIR)/libiconv.a

$(LIBDIR)/libiconv.a: | $(DOWNLOADS)/libiconv-1.18/Makefile
	set -e; cd $(DOWNLOADS)/libiconv-1.18; make -j$(NPROC); make install; \
	$(call write_pc,iconv,1.18,iconv,,,,)

$(DOWNLOADS)/libiconv-1.18/Makefile: $(DOWNLOADS)/libiconv-1.18/configure
	set -e; cd $(DOWNLOADS)/libiconv-1.18; $(CONFIGURE) --enable-static --disable-shared

$(DOWNLOADS)/libiconv-1.18/configure: | $(DOWNLOADS)/libiconv-1.18.tar.gz
	tar -xzf $< -C $(DOWNLOADS)

$(DOWNLOADS)/libiconv-1.18.tar.gz:
	mkdir -p $(DOWNLOADS)
	curl -fL https://ftp.gnu.org/pub/gnu/libiconv/libiconv-1.18.tar.gz -o $@.part
	echo '3b08f5f4f9b4eb82f151a7040bfd6fe6c6fb922efe4b1659c66ea933276965e8  $@.part' | sha256sum -c -
	mv $@.part $@

# libpng
libpng: init_dirs zlib $(LIBDIR)/libpng.a

$(LIBDIR)/libpng.a: $(LIBDIR)/libz.a | $(DOWNLOADS)/libpng/cmakebuild/Makefile
	set -e; cd $(DOWNLOADS)/libpng/cmakebuild; \
	make -j$(NPROC); make install; \
	cd $(LIBDIR)/pkgconfig; \
	test -e libpng.pc || ln -sf libpng16.pc libpng.pc

$(DOWNLOADS)/libpng/cmakebuild/Makefile: $(DOWNLOADS)/libpng/CMakeLists.txt
	set -e; cd $(DOWNLOADS)/libpng; \
	mkdir -p cmakebuild; cd cmakebuild; \
	$(CMAKE) \
	-DPNG_SHARED=off -DPNG_STATIC=on -DPNG_TESTS=off -DPNG_TOOLS=off \
	-DZLIB_INCLUDE_DIR="$(INCLUDEDIR)" -DZLIB_LIBRARY="$(LIBDIR)/libz.a"

$(DOWNLOADS)/libpng/CMakeLists.txt:
	$(CLONE) $(GITHUB)/pnggroup/libpng -b v1.6.50 $(DOWNLOADS)/libpng

# Ogg, dependency of Vorbis
libogg: init_dirs check-toolchain $(LIBDIR)/libogg.a

$(LIBDIR)/libogg.a: | $(DOWNLOADS)/ogg/Makefile
	set -e; cd $(DOWNLOADS)/ogg; \
	make -j$(NPROC); make install

$(DOWNLOADS)/ogg/Makefile: $(DOWNLOADS)/ogg/configure
	set -e; cd $(DOWNLOADS)/ogg; \
	$(CONFIGURE) --enable-static=true --enable-shared=false

$(DOWNLOADS)/ogg/configure: $(DOWNLOADS)/ogg/configure.ac
	set -e; cd $(DOWNLOADS)/ogg; ./autogen.sh

$(DOWNLOADS)/ogg/configure.ac:
	$(CLONE) $(GITHUB)/xiph/ogg -b v1.3.6 $(DOWNLOADS)/ogg

# Vorbis
libvorbis: init_dirs libogg $(LIBDIR)/libvorbis.a

$(LIBDIR)/libvorbis.a: $(LIBDIR)/libogg.a | $(DOWNLOADS)/vorbis/cmakebuild/Makefile
	set -e; cd $(DOWNLOADS)/vorbis/cmakebuild; \
	make -j$(NPROC); make install

$(DOWNLOADS)/vorbis/cmakebuild/Makefile: $(DOWNLOADS)/vorbis/CMakeLists.txt
	set -e; cd $(DOWNLOADS)/vorbis; \
	mkdir -p cmakebuild; cd cmakebuild; \
	$(CMAKE) -DBUILD_SHARED_LIBS=no \
	-DOGG_INCLUDE_DIR="$(INCLUDEDIR)" -DOGG_LIBRARY="$(LIBDIR)/libogg.a"

$(DOWNLOADS)/vorbis/CMakeLists.txt:
	$(CLONE) $(GITHUB)/xiph/vorbis -b v1.3.7 $(DOWNLOADS)/vorbis

# Freetype (dependency of SDL2_ttf)
# CMake build does not install a .pc, so we write freetype2.pc ourselves below
# (version kept in sync with the cloned mkxp-z/freetype2 branch).
freetype: init_dirs libpng $(LIBDIR)/libfreetype.a

$(LIBDIR)/libfreetype.a: $(LIBDIR)/libpng.a | $(DOWNLOADS)/freetype/cmakebuild/Makefile
	set -e; cd $(DOWNLOADS)/freetype/cmakebuild; \
	make -j$(NPROC); make install; \
	test -e $(LIBDIR)/pkgconfig/freetype2.pc || \
	$(call write_pc,freetype2,2.13.0,freetype,-lz -lpng16,/freetype2,)

$(DOWNLOADS)/freetype/cmakebuild/Makefile: $(DOWNLOADS)/freetype/CMakeLists.txt
	set -e; cd $(DOWNLOADS)/freetype; \
	mkdir -p cmakebuild; cd cmakebuild; \
	$(CMAKE) \
	-DBUILD_SHARED_LIBS=off \
	-DFT_DISABLE_HARFBUZZ=TRUE -DFT_DISABLE_BZIP2=TRUE -DFT_DISABLE_BROTLI=TRUE \
	-DFT_DISABLE_PNG=FALSE -DFT_DISABLE_ZLIB=FALSE \
	-DPNG_INCLUDE_DIRS="$(INCLUDEDIR)" -DPNG_LIBRARIES="$(LIBDIR)/libpng16.a" \
	-DZLIB_INCLUDE_DIRS="$(INCLUDEDIR)" -DZLIB_LIBRARIES="$(LIBDIR)/libz.a"

$(DOWNLOADS)/freetype/CMakeLists.txt:
	$(CLONE) $(GITHUB)/mkxp-z/freetype2 $(DOWNLOADS)/freetype

# PhysFS
physfs: init_dirs check-toolchain $(LIBDIR)/libphysfs.a

$(LIBDIR)/libphysfs.a: | $(DOWNLOADS)/physfs/cmakebuild/Makefile
	set -e; cd $(DOWNLOADS)/physfs/cmakebuild; \
	make -j$(NPROC); make install

$(DOWNLOADS)/physfs/cmakebuild/Makefile: $(DOWNLOADS)/physfs/CMakeLists.txt
	set -e; cd $(DOWNLOADS)/physfs; \
	mkdir -p cmakebuild; cd cmakebuild; \
	$(CMAKE) -DPHYSFS_BUILD_STATIC=on -DPHYSFS_BUILD_SHARED=off \
	-DPHYSFS_BUILD_TEST=off -DPHYSFS_BUILD_DOCS=off

$(DOWNLOADS)/physfs/CMakeLists.txt:
	$(CLONE) $(GITHUB)/icculus/physfs -b release-3.2.0 $(DOWNLOADS)/physfs

# SDL2 is SHARED on Android: SDLActivity loads libSDL2.so via System.loadLibrary.
sdl2: init_dirs check-toolchain $(LIBDIR)/libSDL2.so

$(LIBDIR)/libSDL2.so: | $(DOWNLOADS)/sdl2/cmakebuild/Makefile
	@# NDK r30 removed ALooper_pollAll(); upstream SDL (2.30.10+) calls pollOnce here.
	@if grep -q ALooper_pollAll $(DOWNLOADS)/sdl2/src/sensor/android/SDL_androidsensor.c; then \
	  sed -i 's/ALooper_pollAll/ALooper_pollOnce/g' \
	    $(DOWNLOADS)/sdl2/src/sensor/android/SDL_androidsensor.c; \
	  echo "patched SDL_androidsensor.c: ALooper_pollAll -> ALooper_pollOnce"; fi
	set -e; cd $(DOWNLOADS)/sdl2/cmakebuild; \
	make -j$(NPROC); make install

$(DOWNLOADS)/sdl2/cmakebuild/Makefile: $(DOWNLOADS)/sdl2/CMakeLists.txt
	set -e; cd $(DOWNLOADS)/sdl2; \
	mkdir -p cmakebuild; cd cmakebuild; \
	$(CMAKE) -DBUILD_SHARED_LIBS=on -DSDL_SHARED=on -DSDL_STATIC=off \
	-DSDL_TESTS=off -DSDL_DBUS=off

$(DOWNLOADS)/sdl2/CMakeLists.txt:
	$(CLONE) $(GITHUB)/mkxp-z/SDL $(DOWNLOADS)/sdl2 -b mkxp-z-2.28.1

# SDL_image
# JXL is disabled on Android: vendored libjxl/highway almost doubles the build
# time and none of the game assets need it.
sdl2image: init_dirs sdl2 libpng $(LIBDIR)/libSDL2_image.a

$(LIBDIR)/libSDL2_image.a: $(LIBDIR)/libSDL2.so $(LIBDIR)/libpng.a | $(DOWNLOADS)/sdl2_image/cmakebuild/Makefile
	set -e; cd $(DOWNLOADS)/sdl2_image/cmakebuild; \
	make -j$(NPROC); make install; \
	test -e $(LIBDIR)/pkgconfig/SDL2_image.pc || \
	$(call write_pc,SDL2_image,2.6.3,SDL2_image,,,sdl2)

$(DOWNLOADS)/sdl2_image/cmakebuild/Makefile: $(DOWNLOADS)/sdl2_image/CMakeLists.txt
	set -e; cd $(DOWNLOADS)/sdl2_image; mkdir -p cmakebuild; cd cmakebuild; \
	$(CMAKE) \
	-DBUILD_SHARED_LIBS=off \
	-DSDL2IMAGE_VENDORED=on \
	-DSDL2IMAGE_PNG=on -DSDL2IMAGE_PNG_SHARED=off \
	-DSDL2IMAGE_JPG=on -DSDL2IMAGE_JPG_SHARED=off \
	-DSDL2IMAGE_JPG_SAVE=on -DSDL2IMAGE_PNG_SAVE=on \
	-DSDL2IMAGE_JXL=off \
	-DSDL2IMAGE_SAMPLES=off

$(DOWNLOADS)/sdl2_image/CMakeLists.txt:
	$(CLONE) $(GITHUB)/mkxp-z/SDL_image $(DOWNLOADS)/sdl2_image -b mkxp-z; \
	set -e; cd $(DOWNLOADS)/sdl2_image; \
	./external/download.sh

# SDL2 (ttf)
sdl2ttf: init_dirs sdl2 freetype $(LIBDIR)/libSDL2_ttf.a

$(LIBDIR)/libSDL2_ttf.a: $(LIBDIR)/libSDL2.so $(LIBDIR)/libfreetype.a | $(DOWNLOADS)/sdl2_ttf/cmakebuild/Makefile
	set -e; cd $(DOWNLOADS)/sdl2_ttf/cmakebuild; \
	make -j$(NPROC); make install; \
	pc=$(LIBDIR)/pkgconfig/SDL2_ttf.pc; \
	sed -i -e 's/^Version:.*/Version: 2.0.15/' \
	       -e 's/^Requires:.*/Requires: sdl2 >= 2.0.0/' "$$pc"; \
	grep -q '^Requires.private:' "$$pc" || \
	  sed -i '/^Requires:/a Requires.private: freetype2' "$$pc"; \
	echo "patched SDL2_ttf.pc: freetype2 is a static dependency"

$(DOWNLOADS)/sdl2_ttf/cmakebuild/Makefile: $(DOWNLOADS)/sdl2_ttf/CMakeLists.txt
	@# The mkxp-z sdl_ttf fork assumes the SDL Android.mk flow (vendored freetype)
	@# when ANDROID is set; our deps are cross-built like desktop, so take the
	@# find_package(SDL2)/find_package(Freetype) path instead.
	@if grep -q '^if (ANDROID)$$' $(DOWNLOADS)/sdl2_ttf/CMakeLists.txt; then \
	  sed -i '0,/^if (ANDROID)$$/s//if (FALSE) # android\/deps: use cross SDL2+freetype/' \
	    $(DOWNLOADS)/sdl2_ttf/CMakeLists.txt; \
	  echo "patched sdl2_ttf/CMakeLists.txt: skip Android.mk path"; fi
	set -e; cd $(DOWNLOADS)/sdl2_ttf; mkdir -p cmakebuild; cd cmakebuild; \
	$(CMAKE) -DBUILD_SHARED_LIBS=off \
	-DFREETYPE_INCLUDE_DIRS="$(INCLUDEDIR)/freetype2" \
	-DFREETYPE_LIBRARY="$(LIBDIR)/libfreetype.a" \
	-DFREETYPE_INCLUDE_DIR="$(INCLUDEDIR)/freetype2"

$(DOWNLOADS)/sdl2_ttf/CMakeLists.txt:
	$(CLONE) $(GITHUB)/mkxp-z/sdl_ttf $(DOWNLOADS)/sdl2_ttf

# SDL_sound
sdlsound: init_dirs sdl2 libogg libvorbis $(LIBDIR)/libSDL2_sound.a

$(LIBDIR)/libSDL2_sound.a: $(LIBDIR)/libSDL2.so $(LIBDIR)/libvorbis.a | $(DOWNLOADS)/sdl_sound/cmakebuild/Makefile
	set -e; cd $(DOWNLOADS)/sdl_sound/cmakebuild; \
	make -j$(NPROC); make install; \
	test -e $(LIBDIR)/pkgconfig/SDL2_sound.pc || \
	$(call write_pc,SDL2_sound,2.0.1,SDL2_sound,-lm,,sdl2)

$(DOWNLOADS)/sdl_sound/cmakebuild/Makefile: $(DOWNLOADS)/sdl_sound/CMakeLists.txt
	set -e; cd $(DOWNLOADS)/sdl_sound; mkdir -p cmakebuild; cd cmakebuild; \
	$(CMAKE) \
	-DSDLSOUND_BUILD_SHARED=off \
	-DSDLSOUND_BUILD_TEST=off \
	-DSDLSOUND_DECODER_MIDI=off \
	-DOGG_INCLUDE_DIR="$(INCLUDEDIR)" -DOGG_LIBRARY="$(LIBDIR)/libogg.a" \
	-DVORBIS_INCLUDE_DIR="$(INCLUDEDIR)" -DVORBIS_LIBRARY="$(LIBDIR)/libvorbis.a" \
	-DVORBISFILE_LIBRARY="$(LIBDIR)/libvorbisfile.a"

$(DOWNLOADS)/sdl_sound/CMakeLists.txt:
	$(CLONE) $(GITHUB)/mkxp-z/SDL_sound $(DOWNLOADS)/sdl_sound -b git

# OpenAL Soft with the OpenSL ES backend.
# AAudio is NOT available: upstream openal-soft has no AAudio backend (checked
# through 1.25.2), and -DALSOFT_BACKEND_AAUDIO is ignored by CMake. OpenSL ES
# is the only Android audio backend. OpenAL Soft itself stays LGPL-2.1+ (Fase 0
# of the plan); it is statically linked, which the LGPL allows only with
# relinkable objects — revisit if the license review demands a shared libopenal.so.
openal: init_dirs check-toolchain $(LIBDIR)/libopenal.a

$(LIBDIR)/libopenal.a: | $(DOWNLOADS)/openal/cmakebuild/Makefile
	set -e; cd $(DOWNLOADS)/openal/cmakebuild; \
	make -j$(NPROC); make install

$(DOWNLOADS)/openal/cmakebuild/Makefile: $(DOWNLOADS)/openal/CMakeLists.txt
	set -e; cd $(DOWNLOADS)/openal; mkdir -p cmakebuild; cd cmakebuild; \
	$(CMAKE) -DLIBTYPE=STATIC -DALSOFT_EXAMPLES=off -DALSOFT_UTILS=off -DALSOFT_TESTS=off \
	-DALSOFT_BACKEND_OPENSL=on \
	-DALSOFT_BACKEND_ALSA=off -DALSOFT_BACKEND_PULSEAUDIO=off \
	-DALSOFT_BACKEND_PIPEWIRE=off -DALSOFT_BACKEND_JACK=off \
	-DALSOFT_BACKEND_PORTAUDIO=off -DALSOFT_BACKEND_SDL2=off \
	-DALSOFT_BACKEND_WAVE=on \
	-DCMAKE_CXX_FLAGS="$(CFLAGS) -include cstdlib" $(OPENAL_FLAGS)

$(DOWNLOADS)/openal/CMakeLists.txt:
	$(CLONE) $(GITHUB)/kcat/openal-soft -b 1.24.3 $(DOWNLOADS)/openal

# MRI Ruby, static, no shared lib: the engine links libruby-static.a and the
# extensions are linked in (--with-static-linked-ext).
# Ruby does not install a .pc file, so ruby-3.1.pc is generated here.
# `make install` copies the archives with `install -p`, preserving the build
# timestamp, which is older than the libs installed afterwards (libz.a); the
# touch keeps this target from looking stale on the next run.
ruby: init_dirs zlib $(LIBDIR)/libruby-static.a

$(LIBDIR)/libruby-static.a: $(LIBDIR)/libz.a | $(DOWNLOADS)/ruby/Makefile
	@test -n "$(ERB_LIB)" || { \
	  echo "host ruby has no erb: install it (gem install erb) and retry"; exit 1; }
	set -e; cd $(DOWNLOADS)/ruby; \
	make -j$(NPROC); \
	make install; \
	touch $(LIBDIR)/libruby-static.a; \
	$(call merge_ruby_ext); \
	$(call write_ruby_pc)

$(DOWNLOADS)/ruby/Makefile: $(DOWNLOADS)/ruby/configure
	set -e; cd $(DOWNLOADS)/ruby; \
	export $(CONFIGURE_ENV); \
	export RUBYLIB="$(ERB_LIB)$${RUBYLIB:+:$$RUBYLIB}"; \
	export CFLAGS="-std=gnu99 $$CFLAGS"; \
	./configure $(CONFIGURE_ARGS) $(RUBY_CONFIGURE_ARGS) $(RUBY_FLAGS)

$(DOWNLOADS)/ruby/configure: $(DOWNLOADS)/ruby/configure.ac
	set -e; cd $(DOWNLOADS)/ruby; autoreconf -i

$(DOWNLOADS)/ruby/configure.ac:
	$(CLONE) $(GITHUB)/mkxp-z/ruby $(DOWNLOADS)/ruby --single-branch --branch mkxp-z-3.1.3 --depth 1

# -std=gnu99 is needed with GCC 15+ / clang defaulting to newer C dialects, for
# Ruby versions that aren't valid C23 (3.1.x is one of those).

# ====
# Move .pc files that installed outside lib/pkgconfig into the prefix's
# lib/pkgconfig, which is what PKG_CONFIG_LIBDIR points at (zlib uses
# share/pkgconfig by default).
define pc_fixup
	mkdir -p $(LIBDIR)/pkgconfig; \
	for f in $(BUILD_PREFIX)/share/pkgconfig/*.pc; do \
	  [ -e "$$f" ] && cp -f "$$f" $(LIBDIR)/pkgconfig/; \
	done; true
endef

# Hand-written .pc file for libs whose build system does not ship one.
# $(1) name, $(2) version, $(3) -l name, $(4) Libs.private, $(5) Cflags suffix,
# $(6) Requires.
define write_pc
	mkdir -p $(LIBDIR)/pkgconfig; \
	printf '%s\n' \
	  'prefix=$(BUILD_PREFIX)' \
	  'exec_prefix=$${prefix}' \
	  'libdir=$${prefix}/lib' \
	  'includedir=$${prefix}/include' \
	  '' \
	  'Name: $(1)' \
	  'Description: $(1) for Android arm64 (cross-built by android/deps/Makefile)' \
	  'Version: $(2)' \
	  'Requires: $(6)' \
	  'Libs: -L$${libdir} -l$(3)' \
	  'Libs.private: $(4)' \
	  'Cflags: -I$${includedir}$(5)' \
	  > $(LIBDIR)/pkgconfig/$(1).pc
endef

# Same, but the include paths are discovered at recipe time (Ruby installs
# headers in ruby-3.1.0/ and the arch-specific config.h in a sibling dir).
define write_ruby_pc
	mkdir -p $(LIBDIR)/pkgconfig; \
	hdr=$$(ls $(INCLUDEDIR) | grep '^ruby-3\.' | head -1); \
	arch=$$(ls $(INCLUDEDIR)/$$hdr | grep -v '\.' | head -1); \
	printf '%s\n' \
	  'prefix=$(BUILD_PREFIX)' \
	  'exec_prefix=$${prefix}' \
	  'libdir=$${prefix}/lib' \
	  'includedir=$${prefix}/include' \
	  '' \
	  'Name: ruby-3.1' \
	  'Description: MRI Ruby 3.1 for Android arm64 (static, cross-built by android/deps/Makefile)' \
	  'Version: 3.1.3' \
	  'Libs: -L$${libdir} -lruby-static' \
	  'Libs.private: -lruby-ext -lz -lm -ldl -llog' \
	  'Cflags: -I$${includedir}/'"$$hdr"' -I$${includedir}/'"$$hdr"'/'"$$arch" \
	  > $(LIBDIR)/pkgconfig/ruby-3.1.pc
endef

# --with-static-linked-ext compiles every extension into its own ext/**/<ext>.a
# but does not put those objects in libruby-static.a. extinit.o (inside the
# archive) references Init_<ext>, so the engine link needs them: merge every ext
# archive into one libruby-ext.a next to libruby-static.a.
define merge_ruby_ext
	rm -rf $(DOWNLOADS)/ruby/.extmerge; \
	mkdir -p $(DOWNLOADS)/ruby/.extmerge; \
	for a in $$(find $(DOWNLOADS)/ruby/ext -name '*.a'); do \
	  ( cd $(DOWNLOADS)/ruby/.extmerge && $(AR) x "$$a" ); \
	done; \
	rm -f $(LIBDIR)/libruby-ext.a; \
	$(AR) rcs $(LIBDIR)/libruby-ext.a $(DOWNLOADS)/ruby/.extmerge/*.o; \
	rm -rf $(DOWNLOADS)/ruby/.extmerge; \
	echo "merged $$($(AR) t $(LIBDIR)/libruby-ext.a | wc -l) extension objects into libruby-ext.a"
endef

init_dirs:
	@mkdir -p $(LIBDIR) $(INCLUDEDIR)

powerwash: clean-downloads

clean-downloads:
	-rm -rf downloads

deps-core: zlib libpng libogg libvorbis libopusfile pixman uchardet iconv freetype physfs sdl2 sdl2image sdlsound sdl2ttf openal
everything: deps-core ruby
