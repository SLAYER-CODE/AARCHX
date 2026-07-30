#!/bin/bash
# Build Flex: aarch64 video player for Canvas overlay
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/build"
IRIS_LIB_DIR="${SCRIPT_DIR}/../../app/src/main/cpp/lib/build_aarch64"
IRIS_INC_DIR="${IRIS_LIB_DIR}/../include"

# Build Iris lib if missing
if [ ! -f "${IRIS_LIB_DIR}/libiris_static.a" ]; then
    echo "[Flex] Building libiris_static.a..."
    cd "$(dirname "$IRIS_LIB_DIR")"
    aarch64-linux-gnu-g++ -c -O3 -I"$IRIS_INC_DIR" src/*.cpp
    ar rcs libiris_static.a *.o && rm *.o
    cd "$SCRIPT_DIR"
fi

echo "[Flex] Compiling..."
mkdir -p "$BUILD_DIR"
aarch64-linux-gnu-g++ -O3 -static-libstdc++ \
    -I"$IRIS_INC_DIR" \
    src/main.cpp \
    -L"$IRIS_LIB_DIR" -liris_static \
    -lm -lpthread \
    -o "${BUILD_DIR}/flex"

aarch64-linux-gnu-strip --strip-unneeded "${BUILD_DIR}/flex" \
    -o "${BUILD_DIR}/flex_stripped"

echo "[Flex] Done: ${BUILD_DIR}/flex_stripped ($(du -h "${BUILD_DIR}/flex_stripped" | cut -f1))"
