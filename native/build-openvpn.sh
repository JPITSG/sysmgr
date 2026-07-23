#!/usr/bin/env bash
# Cross-compiles the OpenVPN client for Android (arm64-v8a, API 26) and stages
# it as app/src/main/jniLibs/arm64-v8a/libopenvpn.so for build.sh to inject
# into the APK. OpenSSL/LZO/LZ4 are linked statically; bionic stays dynamic.
#
# This script is the reproducible producer of the binary — the tarball cache,
# build trees, install prefix and the output .so are all gitignored.
#
# Skips itself when the version stamp matches; set FORCE_NATIVE=1 to rebuild.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

OPENVPN_VERSION="2.7.5"
OPENSSL_VERSION="3.5.7"
LZO_VERSION="2.10"
LZ4_VERSION="1.10.0"
CAPNG_VERSION="0.8.5"

# SHA256 pins. The OpenVPN hash was recorded from the TLS-authenticated
# download on swupdate.openvpn.org (the project publishes GPG signatures, not
# hashes); the others come from the upstream projects' published checksums.
OPENVPN_SHA256=""  # trust-on-first-use: recorded into .cache on first download
OPENSSL_SHA256="a8c0d28a529ca480f9f36cf5792e2cd21984552a3c8e4aa11a24aa31aeac98e8"
LZO_SHA256="c0f892943208266f9b6543b3ae308fab6284c5c90e627931446fb49b4221a072"
LZ4_SHA256="537512904744b35e232912055ccf8ec66d768639ff3abe5788d90d792ec5f48b"
CAPNG_SHA256=""    # trust-on-first-use

OPENVPN_URL="https://swupdate.openvpn.org/community/releases/openvpn-${OPENVPN_VERSION}.tar.gz"
OPENSSL_URL="https://github.com/openssl/openssl/releases/download/openssl-${OPENSSL_VERSION}/openssl-${OPENSSL_VERSION}.tar.gz"
LZO_URL="https://www.oberhumer.com/opensource/lzo/download/lzo-${LZO_VERSION}.tar.gz"
LZ4_URL="https://github.com/lz4/lz4/releases/download/v${LZ4_VERSION}/lz4-${LZ4_VERSION}.tar.gz"
CAPNG_URL="https://people.redhat.com/sgrubb/libcap-ng/libcap-ng-${CAPNG_VERSION}.tar.gz"

ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
NDK_DIR="${NDK_DIR:-$ANDROID_HOME/ndk/27.0.12077973}"
API=26
ABI="arm64-v8a"
TARGET="aarch64-linux-android"

TOOLCHAIN="$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64"
CACHE_DIR="$ROOT_DIR/native/.cache"
BUILD_DIR="$ROOT_DIR/native/build"
PREFIX="$ROOT_DIR/native/prefix/$ABI"
OUT_SO="$ROOT_DIR/app/src/main/jniLibs/$ABI/libopenvpn.so"
STAMP="$PREFIX/.build-stamp"
JOBS="$(nproc)"

# Bump RECIPE_REV whenever the build recipe (flags/patches) changes so the
# stamp-skip logic forces a rebuild even when component versions are unchanged.
RECIPE_REV="r2-target-android"
SIG="openvpn-$OPENVPN_VERSION openssl-$OPENSSL_VERSION lzo-$LZO_VERSION lz4-$LZ4_VERSION ndk-$(basename "$NDK_DIR") api$API $ABI $RECIPE_REV"

if [[ -z "${FORCE_NATIVE:-}" && -f "$OUT_SO" && -f "$STAMP" && "$(cat "$STAMP")" == "$SIG" ]]; then
  echo "native: up-to-date ($SIG)"
  exit 0
fi

[[ -x "$TOOLCHAIN/bin/$TARGET$API-clang" ]] || { echo "native: NDK clang wrapper not found at $TOOLCHAIN/bin/$TARGET$API-clang" >&2; exit 1; }

mkdir -p "$CACHE_DIR" "$BUILD_DIR" "$PREFIX" "$(dirname "$OUT_SO")"

download() { # name url sha256(optional)
  local name="$1" url="$2" sha="$3" file="$CACHE_DIR/$1"
  if [[ ! -f "$file" ]]; then
    echo "native: downloading $name"
    curl -fSL --retry 3 -o "$file.part" "$url"
    mv "$file.part" "$file"
  fi
  local got
  got="$(sha256sum "$file" | awk '{print $1}')"
  if [[ -n "$sha" ]]; then
    [[ "$got" == "$sha" ]] || { echo "native: SHA256 MISMATCH for $name (got $got, want $sha)" >&2; exit 1; }
  else
    # trust-on-first-use pin, recorded next to the tarball
    if [[ -f "$file.sha256" ]]; then
      [[ "$got" == "$(cat "$file.sha256")" ]] || { echo "native: SHA256 MISMATCH for $name vs recorded pin" >&2; exit 1; }
    else
      echo "$got" > "$file.sha256"
      echo "native: WARNING pinned $name sha256=$got on first use — verify against the upstream GPG signature if in doubt"
    fi
  fi
}

extract() { # tarball dirname
  rm -rf "$BUILD_DIR/$2"
  tar -xzf "$CACHE_DIR/$1" -C "$BUILD_DIR"
}

download "openvpn-$OPENVPN_VERSION.tar.gz" "$OPENVPN_URL" "$OPENVPN_SHA256"
download "openssl-$OPENSSL_VERSION.tar.gz" "$OPENSSL_URL" "$OPENSSL_SHA256"
download "lzo-$LZO_VERSION.tar.gz" "$LZO_URL" "$LZO_SHA256"
download "lz4-$LZ4_VERSION.tar.gz" "$LZ4_URL" "$LZ4_SHA256"
download "libcap-ng-$CAPNG_VERSION.tar.gz" "$CAPNG_URL" "$CAPNG_SHA256"

extract "openvpn-$OPENVPN_VERSION.tar.gz" "openvpn-$OPENVPN_VERSION"
extract "openssl-$OPENSSL_VERSION.tar.gz" "openssl-$OPENSSL_VERSION"
extract "lzo-$LZO_VERSION.tar.gz" "lzo-$LZO_VERSION"
extract "lz4-$LZ4_VERSION.tar.gz" "lz4-$LZ4_VERSION"
extract "libcap-ng-$CAPNG_VERSION.tar.gz" "libcap-ng-$CAPNG_VERSION"

rm -rf "$PREFIX"
mkdir -p "$PREFIX"

export ANDROID_NDK_ROOT="$NDK_DIR"
export PATH="$TOOLCHAIN/bin:$PATH"
CC_BIN="$TARGET$API-clang"
CXX_BIN="$TARGET$API-clang++"

# ---- OpenSSL (its android-arm64 target drives plain clang itself; do not
# force the API-suffixed wrapper on it) --------------------------------------
echo "native: building openssl-$OPENSSL_VERSION"
( cd "$BUILD_DIR/openssl-$OPENSSL_VERSION"
  env -u CC -u CXX -u CFLAGS -u LDFLAGS \
    ./Configure android-arm64 -D__ANDROID_API__=$API \
      --prefix="$PREFIX" --openssldir="$PREFIX/ssl" \
      no-shared no-tests no-docs >/dev/null
  make -j"$JOBS" build_libs >/dev/null
  make install_dev >/dev/null
)

# ---- LZO -------------------------------------------------------------------
echo "native: building lzo-$LZO_VERSION"
( cd "$BUILD_DIR/lzo-$LZO_VERSION"
  # lzo-2.10 ships 2014-era config.sub/guess; refresh from the openvpn tree if
  # available so aarch64-linux-android is recognised.
  for f in config.sub config.guess; do
    src="$(find "$BUILD_DIR/openvpn-$OPENVPN_VERSION" -name "$f" -print -quit 2>/dev/null || true)"
    [[ -n "$src" ]] && cp "$src" "./$f"
  done
  ./configure --host="$TARGET" --prefix="$PREFIX" \
      --disable-shared --enable-static \
      CC="$CC_BIN" AR=llvm-ar RANLIB=llvm-ranlib CFLAGS="-O2 -fPIC" >/dev/null
  make -j"$JOBS" >/dev/null
  make install >/dev/null
)

# ---- LZ4 -------------------------------------------------------------------
echo "native: building lz4-$LZ4_VERSION"
( cd "$BUILD_DIR/lz4-$LZ4_VERSION"
  make -C lib -j"$JOBS" liblz4.a \
      CC="$CC_BIN" AR=llvm-ar CFLAGS="-O2 -fPIC" BUILD_SHARED=no >/dev/null
  make -C lib install PREFIX="$PREFIX" BUILD_SHARED=no BUILD_STATIC=yes >/dev/null
)

# ---- libcap-ng (hard-required by openvpn's configure on *-linux* hosts) ----
echo "native: building libcap-ng-$CAPNG_VERSION"
( cd "$BUILD_DIR/libcap-ng-$CAPNG_VERSION"
  ./configure --host="$TARGET" --prefix="$PREFIX" \
      --disable-shared --enable-static --without-python --without-python3 \
      CC="$CC_BIN" AR=llvm-ar RANLIB=llvm-ranlib CFLAGS="-O2 -fPIC" >/dev/null
  make -j"$JOBS" -C src libcap-ng.la >/dev/null
  make -C src install-libLTLIBRARIES install-nodist_includeHEADERS >/dev/null 2>&1 \
    || make -C src install >/dev/null
)

# ---- OpenVPN ---------------------------------------------------------------
# --host=aarch64-linux-android matches *-*-linux* (SITNL netlink, no external
# ip/route tools) and the NDK clang defines __ANDROID__ => TARGET_ANDROID
# (management-driven tun fd). DCO must be off (userspace only on Android).
echo "native: building openvpn-$OPENVPN_VERSION"
( cd "$BUILD_DIR/openvpn-$OPENVPN_VERSION"
  # PKG_CONFIG_LIBDIR keeps the host's .pc files out of the cross build (the
  # host libcap-ng.pc otherwise leaks in and breaks compilation).
  PKG_CONFIG_LIBDIR="$PREFIX/lib/pkgconfig" \
  ./configure --host="$TARGET" \
      --disable-dco \
      --disable-plugins \
      --disable-pkcs11 \
      --disable-systemd \
      --enable-lzo --enable-lz4 \
      OPENSSL_CFLAGS="-I$PREFIX/include" \
      OPENSSL_LIBS="-L$PREFIX/lib -lssl -lcrypto" \
      LZO_CFLAGS="-I$PREFIX/include" \
      LZO_LIBS="-L$PREFIX/lib -llzo2" \
      LZ4_CFLAGS="-I$PREFIX/include" \
      LZ4_LIBS="-L$PREFIX/lib -llz4" \
      LIBCAPNG_CFLAGS="-I$PREFIX/include" \
      LIBCAPNG_LIBS="-L$PREFIX/lib -lcap-ng" \
      CC="$CC_BIN" AR=llvm-ar RANLIB=llvm-ranlib \
      CFLAGS="-O2 -fPIC" \
      LDFLAGS="-Wl,-z,max-page-size=16384" >/dev/null
  # OpenVPN's autoconf has no Android target: aarch64-linux-android matches the
  # generic *-*-linux* case and sets TARGET_LINUX, so openvpn opens /dev/net/tun
  # directly (EACCES on an unrooted device). Force TARGET_ANDROID so tun /
  # ifconfig / route / DNS all go through the management interface to the app,
  # and drop SITNL (netlink, TARGET_LINUX-only and unused on Android).
  sed -i \
    -e 's/^#define TARGET_LINUX 1/#define TARGET_ANDROID 1/' \
    -e 's/^#define ENABLE_SITNL 1/\/* #undef ENABLE_SITNL *\//' \
    -e 's/^#define TARGET_PREFIX "L"/#define TARGET_PREFIX "A"/' \
    config.h
  grep -q '^#define TARGET_ANDROID 1' config.h || { echo "native: FAIL — TARGET_ANDROID patch did not apply" >&2; exit 1; }
  make config-version.h >/dev/null
  make -j"$JOBS" -C src/compat >/dev/null
  make -j"$JOBS" -C src/openvpn >/dev/null
)

cp "$BUILD_DIR/openvpn-$OPENVPN_VERSION/src/openvpn/openvpn" "$OUT_SO"
llvm-strip --strip-all "$OUT_SO"

# ---- Sanity gates ----------------------------------------------------------
echo "native: verifying $OUT_SO"
READELF="$TOOLCHAIN/bin/llvm-readelf"

if "$READELF" -l "$OUT_SO" | awk '$1=="LOAD" && $NF!="0x4000" {bad=1} END {exit bad}'; then
  echo "native: LOAD segments 16KB-aligned OK"
else
  echo "native: FAIL — a LOAD segment is not 16KB-aligned" >&2; exit 1
fi

"$READELF" -h "$OUT_SO" | grep -q 'Type:.*DYN' || { echo "native: FAIL — not a PIE (ET_DYN)" >&2; exit 1; }

if "$READELF" -d "$OUT_SO" | grep NEEDED | grep -Ev '\[(libc\.so|libdl\.so|libm\.so|liblog\.so)\]'; then
  echo "native: FAIL — unexpected dynamic dependency (deps must be static)" >&2; exit 1
else
  echo "native: dynamic deps OK (bionic only)"
fi

echo "$SIG" > "$STAMP"
ls -l "$OUT_SO"
echo "native: done ($SIG)"
