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
STRIP := $(TOOLCHAIN)/bin/llvm-strip

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
AUTOGEN   := $(CONFIGURE_ENV) ./autogen.sh $(CONFIGURE_ARGS)
CMAKE     := $(CONFIGURE_ENV) cmake -G "Unix Makefiles" .. $(CMAKE_ARGS)

RUBY_CONFIGURE_ARGS := \
	--enable-install-static-library \
	--disable-shared \
	--disable-install-doc \
	--with-out-ext=openssl,readline,dbm,gdbm,fiddle \
	--with-static-linked-ext \
	--disable-rubygems \
	--without-gmp \
	--with-baseruby="$(BASERUBY)"

# Host interpreter used to generate sources for the target build. Required for
# cross-compiling MRI; a host ruby >= 2.7 is enough.
BASERUBY ?= $(shell command -v ruby)

default: everything

.PHONY: check-toolchain
check-toolchain:
	@test -f "$(TOOLCHAIN_FILE)" || { \
		echo "NDK not found at $(NDK)"; \
		echo "Set ANDROID_NDK_HOME or NDK=/path/to/ndk"; exit 1; }
	@test -x "$(CC)" || { echo "Missing cross compiler: $(CC)"; exit 1; }
	@test -n "$(BASERUBY)" || echo "warning: no host ruby, 'ruby' target will fail (needs --with-baseruby)"

# zlib
zlib: init_dirs check-toolchain $(LIBDIR)/libz.a

$(LIBDIR)/libz.a: $(DOWNLOADS)/zlib/cmakebuild/Makefile
	cd $(DOWNLOADS)/zlib/cmakebuild; \
	make -j$(NPROC); make install; \
	rm -f $(LIBDIR)/libz.so $(LIBDIR)/libz.so.*; \
	$(call pc_fixup)

$(DOWNLOADS)/zlib/cmakebuild/Makefile: $(DOWNLOADS)/zlib/CMakeLists.txt
	cd $(DOWNLOADS)/zlib; \
	mkdir -p cmakebuild; cd cmakebuild; \
	$(CMAKE) -DBUILD_SHARED_LIBS=off -DZLIB_BUILD_EXAMPLES=off \
	-DINSTALL_PKGCONFIG_DIR="$(LIBDIR)/pkgconfig"

$(DOWNLOADS)/zlib/CMakeLists.txt:
	$(CLONE) $(GITHUB)/madler/zlib -b v1.3.1 $(DOWNLOADS)/zlib

# libpng
libpng: init_dirs zlib $(LIBDIR)/libpng.a

$(LIBDIR)/libpng.a: $(LIBDIR)/libz.a $(DOWNLOADS)/libpng/cmakebuild/Makefile
	cd $(DOWNLOADS)/libpng/cmakebuild; \
	make -j$(NPROC); make install; \
	cd $(LIBDIR)/pkgconfig; \
	test -e libpng.pc || ln -sf libpng16.pc libpng.pc

$(DOWNLOADS)/libpng/cmakebuild/Makefile: $(DOWNLOADS)/libpng/CMakeLists.txt
	cd $(DOWNLOADS)/libpng; \
	mkdir -p cmakebuild; cd cmakebuild; \
	$(CMAKE) \
	-DPNG_SHARED=off -DPNG_STATIC=on -DPNG_TESTS=off -DPNG_TOOLS=off \
	-DZLIB_INCLUDE_DIR="$(INCLUDEDIR)" -DZLIB_LIBRARY="$(LIBDIR)/libz.a"

$(DOWNLOADS)/libpng/CMakeLists.txt:
	$(CLONE) $(GITHUB)/pnggroup/libpng -b v1.6.50 $(DOWNLOADS)/libpng

# Ogg, dependency of Vorbis
libogg: init_dirs check-toolchain $(LIBDIR)/libogg.a

$(LIBDIR)/libogg.a: $(DOWNLOADS)/ogg/Makefile
	cd $(DOWNLOADS)/ogg; \
	make -j$(NPROC); make install

$(DOWNLOADS)/ogg/Makefile: $(DOWNLOADS)/ogg/configure
	cd $(DOWNLOADS)/ogg; \
	$(CONFIGURE) --enable-static=true --enable-shared=false

$(DOWNLOADS)/ogg/configure: $(DOWNLOADS)/ogg/configure.ac
	cd $(DOWNLOADS)/ogg; ./autogen.sh

$(DOWNLOADS)/ogg/configure.ac:
	$(CLONE) $(GITHUB)/xiph/ogg -b v1.3.6 $(DOWNLOADS)/ogg

# Vorbis
libvorbis: init_dirs libogg $(LIBDIR)/libvorbis.a

$(LIBDIR)/libvorbis.a: $(LIBDIR)/libogg.a $(DOWNLOADS)/vorbis/cmakebuild/Makefile
	cd $(DOWNLOADS)/vorbis/cmakebuild; \
	make -j$(NPROC); make install

$(DOWNLOADS)/vorbis/cmakebuild/Makefile: $(DOWNLOADS)/vorbis/CMakeLists.txt
	cd $(DOWNLOADS)/vorbis; \
	mkdir -p cmakebuild; cd cmakebuild; \
	$(CMAKE) -DBUILD_SHARED_LIBS=no \
	-DOGG_INCLUDE_DIR="$(INCLUDEDIR)" -DOGG_LIBRARY="$(LIBDIR)/libogg.a"

$(DOWNLOADS)/vorbis/CMakeLists.txt:
	$(CLONE) $(GITHUB)/xiph/vorbis -b v1.3.7 $(DOWNLOADS)/vorbis

# Freetype (dependency of SDL2_ttf)
# CMake build does not install a .pc, so we write freetype2.pc ourselves below.
freetype: init_dirs libpng $(LIBDIR)/libfreetype.a

$(LIBDIR)/libfreetype.a: $(LIBDIR)/libpng.a $(DOWNLOADS)/freetype/cmakebuild/Makefile
	cd $(DOWNLOADS)/freetype/cmakebuild; \
	make -j$(NPROC); make install; \
	test -e $(LIBDIR)/pkgconfig/freetype2.pc || \
	$(call write_pc,freetype2,2.13.3,freetype,-lz -lpng16,/freetype2)

$(DOWNLOADS)/freetype/cmakebuild/Makefile: $(DOWNLOADS)/freetype/CMakeLists.txt
	cd $(DOWNLOADS)/freetype; \
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

$(LIBDIR)/libphysfs.a: $(DOWNLOADS)/physfs/cmakebuild/Makefile
	cd $(DOWNLOADS)/physfs/cmakebuild; \
	make -j$(NPROC); make install

$(DOWNLOADS)/physfs/cmakebuild/Makefile: $(DOWNLOADS)/physfs/CMakeLists.txt
	cd $(DOWNLOADS)/physfs; \
	mkdir -p cmakebuild; cd cmakebuild; \
	$(CMAKE) -DPHYSFS_BUILD_STATIC=on -DPHYSFS_BUILD_SHARED=off \
	-DPHYSFS_BUILD_TEST=off -DPHYSFS_BUILD_DOCS=off

$(DOWNLOADS)/physfs/CMakeLists.txt:
	$(CLONE) $(GITHUB)/icculus/physfs -b release-3.2.0 $(DOWNLOADS)/physfs

# SDL2 is SHARED on Android: SDLActivity loads libSDL2.so via System.loadLibrary.
sdl2: init_dirs check-toolchain $(LIBDIR)/libSDL2.so

$(LIBDIR)/libSDL2.so: $(DOWNLOADS)/sdl2/cmakebuild/Makefile
	cd $(DOWNLOADS)/sdl2/cmakebuild; \
	make -j$(NPROC); make install

$(DOWNLOADS)/sdl2/cmakebuild/Makefile: $(DOWNLOADS)/sdl2/CMakeLists.txt
	cd $(DOWNLOADS)/sdl2; \
	mkdir -p cmakebuild; cd cmakebuild; \
	$(CMAKE) -DBUILD_SHARED_LIBS=on -DSDL_SHARED=on -DSDL_STATIC=off \
	-DSDL_TESTS=off -DSDL_DBUS=off

$(DOWNLOADS)/sdl2/CMakeLists.txt:
	$(CLONE) $(GITHUB)/mkxp-z/SDL $(DOWNLOADS)/sdl2 -b mkxp-z-2.28.1

# SDL_image
# JXL is disabled on Android: vendored libjxl/highway almost doubles the build
# time and none of the game assets need it.
sdl2image: init_dirs sdl2 libpng $(LIBDIR)/libSDL2_image.a

$(LIBDIR)/libSDL2_image.a: $(LIBDIR)/libSDL2.so $(LIBDIR)/libpng.a $(DOWNLOADS)/sdl2_image/cmakebuild/Makefile
	cd $(DOWNLOADS)/sdl2_image/cmakebuild; \
	make -j$(NPROC); make install

$(DOWNLOADS)/sdl2_image/cmakebuild/Makefile: $(DOWNLOADS)/sdl2_image/CMakeLists.txt
	cd $(DOWNLOADS)/sdl2_image; mkdir -p cmakebuild; cd cmakebuild; \
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
	cd $(DOWNLOADS)/sdl2_image; \
	./external/download.sh

# SDL2 (ttf)
sdl2ttf: init_dirs sdl2 freetype $(LIBDIR)/libSDL2_ttf.a

$(LIBDIR)/libSDL2_ttf.a: $(LIBDIR)/libSDL2.so $(LIBDIR)/libfreetype.a $(DOWNLOADS)/sdl2_ttf/cmakebuild/Makefile
	cd $(DOWNLOADS)/sdl2_ttf/cmakebuild; \
	make -j$(NPROC); make install

$(DOWNLOADS)/sdl2_ttf/cmakebuild/Makefile: $(DOWNLOADS)/sdl2_ttf/CMakeLists.txt
	cd $(DOWNLOADS)/sdl2_ttf; mkdir -p cmakebuild; cd cmakebuild; \
	$(CMAKE) -DBUILD_SHARED_LIBS=off \
	-DFREETYPE_INCLUDE_DIRS="$(INCLUDEDIR)/freetype2" \
	-DFREETYPE_LIBRARY="$(LIBDIR)/libfreetype.a" \
	-DFREETYPE_INCLUDE_DIR="$(INCLUDEDIR)/freetype2"

$(DOWNLOADS)/sdl2_ttf/CMakeLists.txt:
	$(CLONE) $(GITHUB)/mkxp-z/sdl_ttf $(DOWNLOADS)/sdl2_ttf

# SDL_sound
sdlsound: init_dirs sdl2 libogg libvorbis $(LIBDIR)/libSDL2_sound.a

$(LIBDIR)/libSDL2_sound.a: $(LIBDIR)/libSDL2.so $(LIBDIR)/libvorbis.a $(DOWNLOADS)/sdl_sound/cmakebuild/Makefile
	cd $(DOWNLOADS)/sdl_sound/cmakebuild; \
	make -j$(NPROC); make install

$(DOWNLOADS)/sdl_sound/cmakebuild/Makefile: $(DOWNLOADS)/sdl_sound/CMakeLists.txt
	cd $(DOWNLOADS)/sdl_sound; mkdir -p cmakebuild; cd cmakebuild; \
	$(CMAKE) \
	-DSDLSOUND_BUILD_SHARED=off \
	-DSDLSOUND_BUILD_TEST=off \
	-DSDLSOUND_DECODER_MIDI=off \
	-DOGG_INCLUDE_DIR="$(INCLUDEDIR)" -DOGG_LIBRARY="$(LIBDIR)/libogg.a" \
	-DVORBIS_INCLUDE_DIR="$(INCLUDEDIR)" -DVORBIS_LIBRARY="$(LIBDIR)/libvorbis.a" \
	-DVORBISFILE_LIBRARY="$(LIBDIR)/libvorbisfile.a"

$(DOWNLOADS)/sdl_sound/CMakeLists.txt:
	$(CLONE) $(GITHUB)/mkxp-z/SDL_sound $(DOWNLOADS)/sdl_sound -b git

# OpenAL Soft, OpenSL ES + AAudio backends (everything desktop is off)
openal: init_dirs check-toolchain $(LIBDIR)/libopenal.a

$(LIBDIR)/libopenal.a: $(DOWNLOADS)/openal/cmakebuild/Makefile
	cd $(DOWNLOADS)/openal/cmakebuild; \
	make -j$(NPROC); make install

$(DOWNLOADS)/openal/cmakebuild/Makefile: $(DOWNLOADS)/openal/CMakeLists.txt
	cd $(DOWNLOADS)/openal; mkdir -p cmakebuild; cd cmakebuild; \
	$(CMAKE) -DLIBTYPE=STATIC -DALSOFT_EXAMPLES=off -DALSOFT_UTILS=off -DALSOFT_TESTS=off \
	-DALSOFT_BACKEND_OPENSL=on -DALSOFT_BACKEND_AAUDIO=on \
	-DALSOFT_BACKEND_ALSA=off -DALSOFT_BACKEND_PULSEAUDIO=off \
	-DALSOFT_BACKEND_PIPEWIRE=off -DALSOFT_BACKEND_JACK=off \
	-DALSOFT_BACKEND_PORTAUDIO=off -DALSOFT_BACKEND_SDL2=off \
	-DALSOFT_BACKEND_WAVE=on $(OPENAL_FLAGS)

$(DOWNLOADS)/openal/CMakeLists.txt:
	$(CLONE) $(GITHUB)/kcat/openal-soft -b 1.24.3 $(DOWNLOADS)/openal

# MRI Ruby, static, no shared lib: the engine links libruby-static.a and the
# extensions are linked in (--with-static-linked-ext).
# Ruby does not install a .pc file, so ruby-3.1.pc is generated here.
ruby: init_dirs zlib $(LIBDIR)/libruby-static.a

$(LIBDIR)/libruby-static.a: $(LIBDIR)/libz.a $(DOWNLOADS)/ruby/Makefile
	cd $(DOWNLOADS)/ruby; \
	make -j$(NPROC); make install; \
	$(call write_ruby_pc)

$(DOWNLOADS)/ruby/Makefile: $(DOWNLOADS)/ruby/configure
	cd $(DOWNLOADS)/ruby; \
	export $(CONFIGURE_ENV); \
	export CFLAGS="-std=gnu99 $$CFLAGS"; \
	./configure $(CONFIGURE_ARGS) $(RUBY_CONFIGURE_ARGS) $(RUBY_FLAGS)

$(DOWNLOADS)/ruby/configure: $(DOWNLOADS)/ruby/configure.ac
	cd $(DOWNLOADS)/ruby; autoreconf -i

$(DOWNLOADS)/ruby/configure.ac:
	$(CLONE) $(GITHUB)/mkxp-z/ruby $(DOWNLOADS)/ruby --single-branch --branch mkxp-z-3.1.3 --depth 1

# -std=gnu99 is needed with GCC 15+ / clang defaulting to newer C dialects, for
# Ruby versions that aren't valid C23 (3.1.x is one of those).

# ====
# Hand-written .pc file for libs whose build system does not ship one.
# $(1) name, $(2) version, $(3) -l name, $(4) Libs.private, $(5) Cflags suffix.
# Move .pc files that installed outside lib/pkgconfig into the prefix's
# lib/pkgconfig, which is what PKG_CONFIG_LIBDIR points at (zlib uses
# share/pkgconfig by default).
define pc_fixup
	mkdir -p $(LIBDIR)/pkgconfig; \
	for f in $(BUILD_PREFIX)/share/pkgconfig/*.pc; do \
	  [ -e "$$f" ] && cp -f "$$f" $(LIBDIR)/pkgconfig/; \
	done; true
endef

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
	  'Libs: -L$${libdir} -l$(3)' \
	  'Libs.private: $(4)' \
	  'Cflags: -I$${includedir}$(5)' \
	  > $(LIBDIR)/pkgconfig/$(1).pc
endef

# Same, but the Cflags include path is discovered at recipe time (Ruby 3.1.0).
define write_ruby_pc
	mkdir -p $(LIBDIR)/pkgconfig; \
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
	  'Libs.private: -lm -ldl' \
	  'Cflags: -I$${includedir}/'"$$(ls $(INCLUDEDIR) | grep '^ruby-3\.' | head -1)" \
	  > $(LIBDIR)/pkgconfig/ruby-3.1.pc
endef

init_dirs:
	@mkdir -p $(LIBDIR) $(INCLUDEDIR)

powerwash: clean-downloads

clean-downloads:
	-rm -rf downloads

deps-core: zlib libpng libogg libvorbis freetype physfs sdl2 sdl2image sdlsound sdl2ttf openal
everything: deps-core ruby
