#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/build_aarch64"

export CC=aarch64-linux-gnu-gcc
export CXX=aarch64-linux-gnu-g++

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

cd "$BUILD_DIR"
cmake "$SCRIPT_DIR" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_COMPILER="$CC" \
    -DCMAKE_CXX_COMPILER="$CXX" \
    -DCMAKE_FIND_ROOT_PATH=/usr/aarch64-linux-gnu \
    -DCMAKE_SYSROOT=/usr/aarch64-linux-gnu \
    -DIRIS_USE_SKIA=OFF

make -j$(nproc)

echo ""
echo "=== libiris_static.a built ==="
echo "Output: $BUILD_DIR/libiris_static.a"
