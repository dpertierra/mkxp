ARCH := x86_64
HOST := $(ARCH)-apple-darwin
MINIMUM_REQUIRED := 10.13

SDL_FLAGS := ${SDL_FLAGS}
SDL2_IMAGE_FLAGS := ${SDL2_IMAGE_FLAGS}
SDL2_TTF_FLAGS := ${SDL2_TTF_FLAGS}
OPENAL_FLAGS := ${OPENAL_FLAGS}
OPENSSL_FLAGS := darwin64-$(ARCH)-cc
RUBY_FLAGS := ${RUBY_FLAGS}
# On Apple Silicon this build is a cross-compile, which needs a runnable host Ruby
# of the same version: reuse the arm64 Ruby that setup.sh builds first.
ifeq "$(strip $(shell uname -m))" "arm64"
RUBY_FLAGS += --with-baseruby=${PWD}/build-macosx-arm64/bin/ruby
endif



include common.make
