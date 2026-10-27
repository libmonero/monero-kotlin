#!/bin/bash
# Cross-builds libmonero_c.so for Android arm64-v8a from the external/monero-c
# submodule, with the dependencies built from source at pinned versions, the
# same recipes as the Native workflow builds them for the other platforms.
#
# The Android specifics are the ones proven locally and documented in the AAR
# spike (others/bountireess/active/90-lws-kotlin/redteam/aar-spike-2026-10-25.md):
#   - the NDK toolchain file with the dependencies prefix on the find roots,
#     API level 28 (bionic iconv, which boost.locale needs, is __INTRODUCED_IN(28))
#   - -fPIC everywhere: the archives end up in a shared library
#   - boost built with -a: cxxflags is a free feature of Boost.Build, so a
#     change of it does not invalidate previously built objects
#   - monero-c and monero-cpp patched for the cross build (scripts/android/):
#     HIDAPI is optional because the USE_DEVICE_TREZOR=OFF closure has no
#     hid symbols, and the placeholders that find_package leaves behind are
#     cleared because the generate step treats them as real paths. The patches
#     fail loudly when the submodule moves them, instead of silently diverging
#
# The NDK is not downloaded here: it comes from ANDROID_NDK, which the workflow
# fills with sdkmanager. Everything this script builds lands under
# external/monero-c/build-android-arm64-v8a/, so a clean start is one rm -rf.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MONERO_C="$REPO_ROOT/external/monero-c"
ROOT="$MONERO_C/build-android-arm64-v8a"
DEPS_SRC="$ROOT/deps-src"
PREFIX="$ROOT/deps-prefix"
LOGS="$ROOT/logs"

API=28
ABI=arm64-v8a
TRIPLE=aarch64-linux-android
JOBS="${JOBS:-3}"

# the NDK prebuilt host tag (darwin-arm64, darwin-x86_64, linux-x86_64, ...)
if [ -z "${ANDROID_NDK:-}" ]; then
  echo "ERROR: ANDROID_NDK must point at the Android NDK (the workflow installs it with sdkmanager)" >&2
  exit 1
fi
NDK_HOST_TAG="$(ls "$ANDROID_NDK/toolchains/llvm/prebuilt/" | head -1)"
TC="$ANDROID_NDK/toolchains/llvm/prebuilt/$NDK_HOST_TAG"

# compilers baked with the API level, and the archiver of the NDK
export CC="$TC/bin/${TRIPLE}${API}-clang"
export CXX="$TC/bin/${TRIPLE}${API}-clang++"
export AR="$TC/bin/llvm-ar"
export RANLIB="$TC/bin/llvm-ranlib"
export STRIP="$TC/bin/llvm-strip"
export ANDROID_NDK_ROOT="$ANDROID_NDK"
# openssl's android target finds the NDK clang through PATH
export PATH="$TC/bin:$PATH"

# portable sha256 check: Linux runners have sha256sum, macOS has shasum
check_sha256() {
  local file="$1" expected="$2"
  # the Linux runners have GNU sha256sum; macOS has shasum and a sha256sum without -c
  if ! echo "$expected  $file" | sha256sum -c >/dev/null 2>&1; then
    echo "$expected  $file" | shasum -a 256 -c
  fi
}

# a patch is applied forward only: --forward never reverses and never asks
# (a prompt would read from the patch file itself, since that is the stdin),
# an already applied patch is skipped, one that does not apply fails the build
apply_patch() {
  local output
  if output=$(patch -p1 --forward --dry-run < "$1" 2>&1); then
    patch -p1 --silent --forward < "$1"
  elif echo "$output" | grep -qi "previously applied"; then
    echo "== $1 already applied =="
  else
    echo "ERROR: $1 does not apply (the submodule changed under it):" >&2
    echo "$output" >&2
    exit 1
  fi
}

download() {
  local url="$1" file="$2" sha256="$3"
  if [ ! -f "$file" ]; then
    curl --retry 5 --retry-delay 5 --retry-max-time 150 -fsSLO "$url"
  fi
  check_sha256 "$file" "$sha256"
}

mkdir -p "$DEPS_SRC" "$PREFIX" "$LOGS"
cd "$DEPS_SRC"

##############################################################################
# dependencies (the versions the Android build was proven with, sha256-pinned
# like the dep installs of the Native workflow)
##############################################################################

echo "== OpenSSL 3.5.0 =="
if [ ! -f "$PREFIX/lib/libcrypto.a" ]; then
  download https://github.com/openssl/openssl/releases/download/openssl-3.5.0/openssl-3.5.0.tar.gz \
    openssl-3.5.0.tar.gz 344d0a79f1a9b08029b0744e2cc401a43f9c90acd1044d09a530b4885a8e9fc0
  [ -d openssl-3.5.0 ] || tar xzf openssl-3.5.0.tar.gz
  ( cd openssl-3.5.0
    ./Configure android-arm64 -D__ANDROID_API__=$API -fPIC --prefix="$PREFIX" --openssldir="$PREFIX/ssl" no-shared no-tests no-docs \
      > "$LOGS/openssl-configure.log" 2>&1
    make -j"$JOBS" > "$LOGS/openssl-build.log" 2>&1
    make install_sw install_ssldirs > "$LOGS/openssl-install.log" 2>&1 )
fi

echo "== expat 2.6.2 =="
if [ ! -f "$PREFIX/lib/libexpat.a" ]; then
  download https://github.com/libexpat/libexpat/releases/download/R_2_6_2/expat-2.6.2.tar.gz \
    expat-2.6.2.tar.gz d4cf38d26e21a56654ffe4acd9cd5481164619626802328506a2869afab29ab3
  [ -d expat-2.6.2 ] || tar xzf expat-2.6.2.tar.gz
  ( cd expat-2.6.2
    ./configure --host=$TRIPLE --prefix="$PREFIX" --with-sysroot="$TC/sysroot" \
      --enable-static --disable-shared --without-examples --without-tests --without-docbook \
      CC="$CC" CXX="$CXX" AR="$AR" RANLIB="$RANLIB" CFLAGS="-O2 -fPIC" CXXFLAGS="-O2 -fPIC" \
      > "$LOGS/expat-configure.log" 2>&1
    make -j"$JOBS" > "$LOGS/expat-build.log" 2>&1
    make install > "$LOGS/expat-install.log" 2>&1 )
fi

echo "== unbound 1.22.0 =="
if [ ! -f "$PREFIX/lib/libunbound.a" ]; then
  download https://www.nlnetlabs.nl/downloads/unbound/unbound-1.22.0.tar.gz \
    unbound-1.22.0.tar.gz c5dd1bdef5d5685b2cedb749158dd152c52d44f65529a34ac15cd88d4b1b3d43
  [ -d unbound-1.22.0 ] || tar xzf unbound-1.22.0.tar.gz
  ( cd unbound-1.22.0
    ./configure --host=$TRIPLE --prefix="$PREFIX" --with-ssl="$PREFIX" --with-libexpat="$PREFIX" \
      --with-libevent=no --disable-shared --enable-static --disable-flto \
      --without-pythonmodule --without-pyunbound --with-pidfile=/dev/null --with-username="" --with-rootkey-file=/dev/null \
      CC="$CC" CXX="$CXX" AR="$AR" RANLIB="$RANLIB" CFLAGS="-O2 -fPIC" LDFLAGS="-L$PREFIX/lib" \
      > "$LOGS/unbound-configure.log" 2>&1
    make -j"$JOBS" > "$LOGS/unbound-build.log" 2>&1
    make install > "$LOGS/unbound-install.log" 2>&1 )
fi

echo "== libsodium 1.0.20 =="
if [ ! -f "$PREFIX/lib/libsodium.a" ]; then
  download https://github.com/jedisct1/libsodium/releases/download/1.0.20-RELEASE/libsodium-1.0.20.tar.gz \
    libsodium-1.0.20.tar.gz ebb65ef6ca439333c2bb41a0c1990587288da07f6c7fd07cb3a18cc18d30ce19
  [ -d libsodium-1.0.20 ] || tar xzf libsodium-1.0.20.tar.gz
  ( cd libsodium-1.0.20
    ./configure --host=$TRIPLE --prefix="$PREFIX" --with-sysroot="$TC/sysroot" \
      --disable-soname-versions --disable-pie --enable-static --disable-shared \
      CC="$CC" AR="$AR" RANLIB="$RANLIB" CFLAGS="-O2 -fPIC -march=armv8-a+crypto" \
      > "$LOGS/sodium-configure.log" 2>&1
    make -j"$JOBS" > "$LOGS/sodium-build.log" 2>&1
    make install > "$LOGS/sodium-install.log" 2>&1 )
fi

echo "== libzmq 4.3.5 =="
if [ ! -f "$PREFIX/lib/libzmq.a" ]; then
  download https://github.com/zeromq/libzmq/releases/download/v4.3.5/zeromq-4.3.5.tar.gz \
    zeromq-4.3.5.tar.gz 6653ef5910f17954861fe72332e68b03ca6e4d9c7160eb3a8de5a5a913bfab43
  [ -d zeromq-4.3.5 ] || tar xzf zeromq-4.3.5.tar.gz
  ( cd zeromq-4.3.5
    cmake -B build -S . -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
      -DANDROID_ABI=$ABI -DANDROID_PLATFORM=android-$API \
      -DCMAKE_INSTALL_PREFIX="$PREFIX" \
      -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
      -DBUILD_SHARED_LIBS=OFF \
      -DBUILD_TESTS=OFF -DBUILD_PERF_TESTS=OFF \
      -DWITH_LIBSODIUM=OFF -DWITH_LIBBSD=OFF \
      -DWITH_DOC=OFF -DWITH_DOCS=OFF \
      -DWITH_WERROR=OFF \
      > "$LOGS/zmq-configure.log" 2>&1
    cmake --build build -j"$JOBS" > "$LOGS/zmq-build.log" 2>&1
    cmake --install build > "$LOGS/zmq-install.log" 2>&1 )
fi

echo "== boost 1.92.0 =="
if [ ! -f "$PREFIX/lib/libboost_regex.a" ]; then
  download https://archives.boost.io/release/1.92.0/source/boost_1_92_0.tar.gz \
    boost_1_92_0.tar.gz c4a3b310ddd2472416e091067166b0713be97c63f38c212c484ada022fd296ce
  [ -d boost_1_92_0 ] || tar xzf boost_1_92_0.tar.gz
  ( cd boost_1_92_0
    # teach b2 the NDK clang; the API level is baked into the wrapper name
    cat > user-config.jam <<EOF
using clang : android : $CXX :
    <archiver>$AR
    <ranlib>$RANLIB
;
EOF
    # monero needs the first seven, monero-c requires regex on top of them
    ./bootstrap.sh --with-libraries=chrono,date_time,filesystem,locale,program_options,regex,serialization,thread \
      > "$LOGS/boost-bootstrap.log" 2>&1
    # -a: cxxflags is a free feature of Boost.Build, so its change alone would not rebuild the objects
    ./b2 -j"$JOBS" --user-config=user-config.jam --prefix="$PREFIX" \
      --with-chrono --with-date_time --with-filesystem --with-locale --with-program_options --with-regex --with-serialization --with-thread \
      toolset=clang-android target-os=android architecture=arm address-model=64 \
      link=static threading=multi runtime-link=shared cxxflags=-fPIC -a variant=release --layout=system install \
      > "$LOGS/boost-build.log" 2>&1 )
fi

##############################################################################
# the toolchain wrapper: the NDK toolchain + the dependencies prefix on the
# find roots, so find_package and find_library resolve the cross-built
# libraries while everything else comes from the NDK sysroot
##############################################################################

TOOLCHAIN_FILE="$ROOT/android-arm64-deps.cmake"
cat > "$TOOLCHAIN_FILE" <<EOF
include("$ANDROID_NDK/build/cmake/android.toolchain.cmake")
list(APPEND CMAKE_FIND_ROOT_PATH "$PREFIX")
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE ONLY)
EOF

##############################################################################
# monero-project: the wallet static library and the rest of the closure
##############################################################################

MONERO_PROJECT="$MONERO_C/external/monero-cpp/external/monero-project"
MONERO_BUILD="$MONERO_PROJECT/build/android-arm64-v8a"

# the env compilers are for the dependencies; the toolchain file picks its own,
# and the host builds inside monero's cmake (the translations) must not inherit
# the cross compilers
unset CC CXX AR RANLIB STRIP

echo "== monero-project (targets: wallet cryptonote_protocol) =="
if [ ! -f "$MONERO_BUILD/lib/libwallet.a" ]; then
  cmake -G "Unix Makefiles" -B "$MONERO_BUILD" -S "$MONERO_PROJECT" \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE" \
    -DANDROID_ABI=$ABI \
    -DANDROID_PLATFORM=android-$API \
    -DANDROID_STL=c++_static \
    -DSTATIC=ON -DARCH=armv8-a -DBUILD_64=ON -DCMAKE_BUILD_TYPE=Release \
    -DUSE_DEVICE_TREZOR=OFF -DUSE_READLINE=OFF -DBUILD_GUI_DEPS=ON -DBUILD_TESTS=OFF \
    2>&1 | tee "$LOGS/monero-configure.log"
  cmake --build "$MONERO_BUILD" --target wallet cryptonote_protocol -j"$JOBS" 2>&1 | tee "$LOGS/monero-build.log"
fi

##############################################################################
# monero-cpp and monero-c, patched for the cross build (see the header)
##############################################################################

cd "$REPO_ROOT"
apply_patch scripts/android/monero-cpp-cmake-android.patch
apply_patch scripts/android/monero-c-cmake-android.patch

MONERO_CPP="$MONERO_C/external/monero-cpp"
MONERO_CPP_BUILD="$ROOT/build-monero-cpp"
MONERO_C_BUILD="$ROOT/build-monero-c"

echo "== monero-cpp (target: monero-cpp-static) =="
if [ ! -f "$MONERO_CPP_BUILD/libmonero-cpp.a" ]; then
  cmake -G "Unix Makefiles" -B "$MONERO_CPP_BUILD" -S "$MONERO_CPP" \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE" \
    -DANDROID_ABI=$ABI \
    -DANDROID_PLATFORM=android-$API \
    -DANDROID_STL=c++_static \
    -DMONERO_PROJECT_BUILD="$MONERO_BUILD" \
    -DCMAKE_BUILD_TYPE=Release \
    2>&1 | tee "$LOGS/monero-cpp-configure.log"
  cmake --build "$MONERO_CPP_BUILD" --target monero-cpp-static -j"$JOBS" 2>&1 | tee "$LOGS/monero-cpp-build.log"
fi

echo "== monero-c (target: monero_c) =="
cmake -G "Unix Makefiles" -B "$MONERO_C_BUILD" -S "$MONERO_C" \
  -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE" \
  -DANDROID_ABI=$ABI \
  -DANDROID_PLATFORM=android-$API \
  -DANDROID_STL=c++_static \
  -DMONERO_CPP_BUILD="$MONERO_CPP_BUILD" \
  -DMONERO_PROJECT_BUILD="$MONERO_BUILD" \
  -DCMAKE_BUILD_TYPE=Release \
  2>&1 | tee "$LOGS/monero-c-configure.log"
cmake --build "$MONERO_C_BUILD" --target monero_c -j"$JOBS" 2>&1 | tee "$LOGS/monero-c-build.log"

##############################################################################
# verify what was built
##############################################################################

SO="$MONERO_C_BUILD/libmonero_c.so"
echo "== verifying $SO =="
file "$SO"
# every LOAD segment aligned to at least 16 KiB (the 16 KiB pages of Android 15)
LOADS="$("$TC/bin/llvm-readelf" -l "$SO" | grep "^  LOAD" || true)"
echo "$LOADS"
echo "$LOADS" | grep -Ev "0x(4000|10000)$" > /dev/null && { echo "ERROR: a LOAD segment is not 16 KiB aligned" >&2; exit 1; } || true
# only bionic may be needed at load time
NEEDED="$("$TC/bin/llvm-readelf" -d "$SO" | grep NEEDED)"
echo "$NEEDED"
echo "$NEEDED" | grep -Ev "libm.so|libdl.so|libc.so" && { echo "ERROR: a dependency beyond bionic is needed" >&2; exit 1; } || true
# the C ABI that the header documents, all of it
SYMBOLS="$MONERO_C/tests/c/abi/monero_c.symbols"
EXPORTED="$("$TC/bin/llvm-nm" -D --defined-only "$SO" | awk '$2=="T"||$2=="W"||$2=="B"||$2=="D" {print $3}' | sort -u)"
MISSING="$(comm -23 <(grep -v '^$' "$SYMBOLS" | sort -u) <(echo "$EXPORTED"))"
[ -z "$MISSING" ] || { echo "ERROR: missing C ABI symbols:"$'\n'"$MISSING" >&2; exit 1; }
echo "C ABI symbols: $(grep -cv '^$' "$SYMBOLS") listed, all exported"
# the NDK libc++ inline namespace, statically linked with c++_static
echo "std::__ndk1 symbols: $("$TC/bin/llvm-nm" "$SO" 2>/dev/null | grep -c __ndk1 || true)"

echo ""
echo "== built: $SO =="
ls -la "$SO"
