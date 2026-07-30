#!/bin/bash
# Build Cati: aarch64 image viewer for Canvas overlay
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/build"
IRIS_LIB_DIR="${SCRIPT_DIR}/../../app/src/main/cpp/lib/build_aarch64"
IRIS_INC_DIR="${IRIS_LIB_DIR}/../include"

# Download stb_image.h if missing
STB_DIR="${SCRIPT_DIR}/include/stb"
STB_FILE="${STB_DIR}/stb_image.h"
if [ ! -f "$STB_FILE" ]; then
    echo "[Cati] Downloading stb_image.h..."
    mkdir -p "$STB_DIR"
    wget -q "https://raw.githubusercontent.com/nothings/stb/master/stb_image.h" -O "$STB_FILE"
fi

# Build Iris lib if missing
if [ ! -f "${IRIS_LIB_DIR}/libiris_static.a" ]; then
    echo "[Cati] Building libiris_static.a..."
    cd "$(dirname "$IRIS_LIB_DIR")"
    aarch64-linux-gnu-g++ -c -O3 -I"$IRIS_INC_DIR" src/*.cpp
    ar rcs libiris_static.a *.o && rm *.o
    cd "$SCRIPT_DIR"
fi

echo "[Cati] Compiling..."
mkdir -p "$BUILD_DIR"
aarch64-linux-gnu-g++ -O3 -static-libstdc++ \
    -I"$IRIS_INC_DIR" \
    -I"${SCRIPT_DIR}/include" \
    src/main.cpp \
    -L"$IRIS_LIB_DIR" -liris_static \
    -lm -lpthread \
    -o "${BUILD_DIR}/cati"

aarch64-linux-gnu-strip --strip-unneeded "${BUILD_DIR}/cati" \
    -o "${BUILD_DIR}/cati_stripped"

echo "[Cati] Done: ${BUILD_DIR}/cati_stripped ($(du -h "${BUILD_DIR}/cati_stripped" | cut -f1))"
