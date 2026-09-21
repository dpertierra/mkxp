#!/bin/bash
BINARY=$1
ARCH=$2
STEAM=$3
function get_dep()
{
echo "Copying $1..."
DEP=$(ldd $BINARY | grep $1 | sed -r 's/	\w.+ => (\/.+) .+$/\1/g')
cp "$DEP" "${MESON_INSTALL_PREFIX}/lib$ARCH"
}

mkdir -p ${MESON_INSTALL_PREFIX}/lib$ARCH


# Required by Ubuntu
get_dep ruby

# Required by Fedora & Manjaro
get_dep libcrypt
get_dep libbsd

# Bundled libs must resolve their own deps (libruby -> libcrypt.so.1) from the
# same folder; the executable's RUNPATH does not apply to indirect deps.
for f in "${MESON_INSTALL_PREFIX}"/lib$ARCH/libruby.so* "${MESON_INSTALL_PREFIX}"/lib$ARCH/libbsd.so*; do
  [ -f "$f" ] && patchelf --set-rpath '$ORIGIN' "$f"
done

if [ -n "$STEAM" ]; then
  echo "Copying steam_api..."
  cp "$STEAM/libsteam_api.so" "${MESON_INSTALL_PREFIX}/usr/lib"
fi
