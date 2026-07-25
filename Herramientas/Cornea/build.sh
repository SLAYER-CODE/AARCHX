#!/bin/bash
# Build Cornea: static binary for aarch64 with Tesseract OCR
# Uso: ./build.sh [--clean] [--deploy]
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/build"
DEPLOY_DIR="/tmp/cornea_deploy"
TESSERACT_STATIC="${SCRIPT_DIR}/deps/tesseract_build/install"
DEPS_DIR="${SCRIPT_DIR}/deps"
IRIS_BUILD="${SCRIPT_DIR}/../../app/src/main/cpp/lib/build_aarch64"

# 1. Build libiris_static.a (if missing)
if [ ! -f "${IRIS_BUILD}/libiris_static.a" ]; then
    echo "[Cornea] Building libiris_static.a..."
    LIB_SRC="$(cd "$SCRIPT_DIR/../../app/src/main/cpp/lib" && pwd)"
    mkdir -p "$IRIS_BUILD"
    cd "$IRIS_BUILD"
    cmake "$LIB_SRC" \
        -DCMAKE_C_COMPILER=aarch64-linux-gnu-gcc \
        -DCMAKE_CXX_COMPILER=aarch64-linux-gnu-g++ \
        -DCMAKE_BUILD_TYPE=Release \
        -DIRIS_USE_SKIA=OFF
    make -j$(nproc)
    cd "$SCRIPT_DIR"
fi

# 2. Clean if requested
if [ "$1" = "--clean" ]; then
    rm -rf "$BUILD_DIR"
fi

# 3. Build Cornea
echo "[Cornea] Building..."
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"
cmake "$SCRIPT_DIR" \
    -DCMAKE_C_COMPILER=aarch64-linux-gnu-gcc \
    -DCMAKE_CXX_COMPILER=aarch64-linux-gnu-g++ \
    -DCMAKE_BUILD_TYPE=Release \
    -DTESSERACT_STATIC_DIR="$TESSERACT_STATIC" \
    -DSHARED_IRIS_LIB_DIR="$IRIS_BUILD"
make -j$(nproc)

# 4. Strip
aarch64-linux-gnu-strip --strip-unneeded "$BUILD_DIR/cornea" -o "$BUILD_DIR/cornea_stripped"

# 5. Package for deployment
echo "[Cornea] Packaging..."
rm -rf "$DEPLOY_DIR"
mkdir -p "$DEPLOY_DIR/tessdata"
cp "$BUILD_DIR/cornea_stripped" "$DEPLOY_DIR/cornea"
chmod +x "$DEPLOY_DIR/cornea"
cp "${SCRIPT_DIR}/deps/tesseract_build/tessdata/eng.traineddata" "$DEPLOY_DIR/tessdata/"

# Copy YOLO model if available
if [ -f "${DEPS_DIR}/yolov8n.param" ] && [ -f "${DEPS_DIR}/yolov8n.bin" ]; then
    cp "${DEPS_DIR}/yolov8n.param" "$DEPLOY_DIR/"
    cp "${DEPS_DIR}/yolov8n.bin" "$DEPLOY_DIR/"
    echo "  YOLO:    yolov8n.param + yolov8n.bin"
fi

echo ""
echo "[Cornea] Build complete!"
echo "  Binary:   $DEPLOY_DIR/cornea ($(du -h "$DEPLOY_DIR/cornea" | cut -f1))"
echo "  Tessdata: $DEPLOY_DIR/tessdata/eng.traineddata ($(du -h "$DEPLOY_DIR/tessdata/eng.traineddata" | cut -f1))"
echo ""

# 6. Deploy if requested
if [ "$1" = "--deploy" ] || [ "$2" = "--deploy" ]; then
    echo "[Cornea] Deploying to device..."
    adb wait-for-device
    adb push "$DEPLOY_DIR/cornea" /data/local/tmp/cornea
    adb push "$DEPLOY_DIR/tessdata/eng.traineddata" /data/local/tmp/eng.traineddata
    adb shell "su -c 'rm -rf /data/local/aarchdroid/root/cornea && mkdir -p /data/local/aarchdroid/root/cornea/tessdata'"
    adb shell "su -c 'dd if=/data/local/tmp/cornea of=/data/local/aarchdroid/root/cornea/cornea bs=1M'"
    adb shell "su -c 'dd if=/data/local/tmp/eng.traineddata of=/data/local/aarchdroid/root/cornea/tessdata/eng.traineddata bs=1M'"
    adb shell "su -c 'chmod 755 /data/local/aarchdroid/root/cornea/cornea'"
    
    # Deploy YOLO model if available
    if [ -f "$DEPLOY_DIR/yolov8n.param" ] && [ -f "$DEPLOY_DIR/yolov8n.bin" ]; then
        adb push "$DEPLOY_DIR/yolov8n.param" /data/local/tmp/yolov8n.param
        adb push "$DEPLOY_DIR/yolov8n.bin" /data/local/tmp/yolov8n.bin
        adb shell "su -c 'dd if=/data/local/tmp/yolov8n.param of=/data/local/aarchdroid/root/cornea/yolov8n.param bs=1M'"
        adb shell "su -c 'dd if=/data/local/tmp/yolov8n.bin of=/data/local/aarchdroid/root/cornea/yolov8n.bin bs=1M'"
        echo "  YOLO model deployed"
    fi
    
    echo "[Cornea] Deployed!"
    echo "  Run: su -c 'aarchrun.sh cornea --tesseract-data=/data/local/aarchdroid/root/cornea/tessdata'"
else
    echo "Deploy commands:"
    echo "  ./build.sh --deploy"
    echo ""
    echo "Or manually:"
    echo "  adb push $DEPLOY_DIR/cornea /data/local/tmp/cornea"
    echo "  adb push $DEPLOY_DIR/tessdata/eng.traineddata /data/local/tmp/eng.traineddata"
    echo "  adb shell su -c 'rm -rf /data/local/aarchdroid/root/cornea && mkdir -p /data/local/aarchdroid/root/cornea/tessdata'"
    echo "  adb shell su -c 'dd if=/data/local/tmp/cornea of=/data/local/aarchdroid/root/cornea/cornea bs=1M'"
    echo "  adb shell su -c 'dd if=/data/local/tmp/eng.traineddata of=/data/local/aarchdroid/root/cornea/tessdata/eng.traineddata bs=1M'"
fi

