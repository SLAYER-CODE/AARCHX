#!/bin/bash
# Lexis — Build & Deploy script
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/build"
CROSS_COMPILE="aarch64-linux-gnu-"
TESSERACT_DIR="/tmp/tesseract_build/install"
IRIS_DIR="${SCRIPT_DIR}/../../app/src/main/cpp/lib"

# Deploy target
DEPLOY_DIR="/data/local/aarchdroid/root/lexis"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() { echo -e "${GREEN}[Lexis]${NC} $1"; }
warn() { echo -e "${YELLOW}[Lexis]${NC} $1"; }
err() { echo -e "${RED}[Lexis]${NC} $1"; }

# Parse args
DEPLOY=0
STRIP=1
for arg in "$@"; do
    case "$arg" in
        --deploy) DEPLOY=1 ;;
        --no-strip) STRIP=0 ;;
        --help|-h)
            echo "Usage: $0 [--deploy] [--no-strip]"
            echo "  --deploy    Push to device after build"
            echo "  --no-strip  Don't strip binary"
            exit 0
            ;;
    esac
done

log "Building Lexis..."

# Create build directory
mkdir -p "$BUILD_DIR"

# Configure
cd "$BUILD_DIR"
cmake .. \
    -DCMAKE_C_COMPILER=${CROSS_COMPILE}gcc \
    -DCMAKE_CXX_COMPILER=${CROSS_COMPILE}g++ \
    -DCMAKE_BUILD_TYPE=Release \
    -DTESSERACT_STATIC_DIR=${TESSERACT_DIR} \
    -DSHARED_IRIS_LIB_DIR=${IRIS_DIR}/build_aarch64

# Build
make -j$(nproc)

# Strip
if [ "$STRIP" -eq 1 ]; then
    log "Stripping binary..."
    ${CROSS_COMPILE}strip lexis -o lexis_stripped
    BINARY="lexis_stripped"
    SIZE=$(du -h lexis_stripped | cut -f1)
    log "Binary: ${BINARY} (${SIZE})"
else
    BINARY="lexis"
fi

# Deploy
if [ "$DEPLOY" -eq 1 ]; then
    log "Deploying to device..."
    
    # Check device
    if ! adb devices | grep -q "device$"; then
        err "No device connected"
        exit 1
    fi
    
    # Create directory
    adb shell "su -c 'mkdir -p ${DEPLOY_DIR}'"
    adb shell "su -c 'mkdir -p ${DEPLOY_DIR}/tessdata'"
    
    # Push binary
    adb push "${BUILD_DIR}/${BINARY}" /data/local/tmp/lexis
    adb shell "su -c 'dd if=/data/local/tmp/lexis of=${DEPLOY_DIR}/lexis bs=1M'"
    adb shell "su -c 'chmod 755 ${DEPLOY_DIR}/lexis'"
    
    # Push tessdata if available
    if [ -d "${TESSERACT_DIR}/../tessdata" ]; then
        adb push "${TESSERACT_DIR}/../tessdata/eng.traineddata" /data/local/tmp/eng.traineddata
        adb shell "su -c 'dd if=/data/local/tmp/eng.traineddata of=${DEPLOY_DIR}/tessdata/eng.traineddata bs=1M'"
    fi
    
    log "Deployed to ${DEPLOY_DIR}"
    log "Run: su -c 'aarchrun.sh lexis --tesseract-data=${DEPLOY_DIR}/tessdata'"
fi

log "Build complete!"
