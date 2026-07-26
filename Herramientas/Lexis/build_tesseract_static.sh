#!/bin/bash
# build_tesseract_static.sh — Cross-compile Tesseract + deps statically for aarch64
# Output: libtesseract.a + libleptonica.a + headers in /tmp/tesseract_build/install/
set -e

WORKDIR="/tmp/tesseract_build"
PREFIX="${WORKDIR}/install"
SYSROOT="/usr/aarch64-linux-gnu"
CC="aarch64-linux-gnu-gcc"
CXX="aarch64-linux-gnu-g++"
AR="aarch64-linux-gnu-ar"
RANLIB="aarch64-linux-gnu-ranlib"
STRIP="aarch64-linux-gnu-strip"
NPROC=$(nproc)

export CC CXX AR RANLIB

echo "=== Tesseract Static Build for aarch64 ==="
echo "Workdir: ${WORKDIR}"
echo "Prefix:  ${PREFIX}"
echo "Jobs:    ${NPROC}"
echo ""

mkdir -p "${WORKDIR}/src" "${WORKDIR}/tessdata" "${PREFIX}/lib" "${PREFIX}/include"
export PKG_CONFIG_PATH="${PREFIX}/lib/pkgconfig"
export CFLAGS="-I${PREFIX}/include -Os -ffunction-sections -fdata-sections"
export CPPFLAGS="-I${PREFIX}/include"
export LDFLAGS="-L${PREFIX}/lib"

cd "${WORKDIR}/src"

# ── 1. zlib ───────────────────────────────────────────────────────
echo "[1/7] zlib 1.3.2..."
if [ ! -f "${PREFIX}/lib/libz.a" ]; then
    curl -sLO "https://zlib.net/zlib-1.3.2.tar.gz"
    tar xf zlib-1.3.2.tar.gz
    cd zlib-1.3.2
    CHOST=aarch64-linux-gnu ./configure --static --prefix="${PREFIX}"
    make -j${NPROC}
    make install
    cd ..
fi
echo "  zlib OK"

# ── 2. libjpeg-turbo ─────────────────────────────────────────────
echo "[2/7] libjpeg-turbo 3.2.0..."
if [ ! -f "${PREFIX}/lib/libjpeg.a" ]; then
    curl -sLO "https://github.com/libjpeg-turbo/libjpeg-turbo/releases/download/3.2.0/libjpeg-turbo-3.2.0.tar.gz"
    tar xf libjpeg-turbo-3.2.0.tar.gz
    cd libjpeg-turbo-3.2.0
    mkdir -p build && cd build
    cmake .. \
        -DCMAKE_SYSTEM_NAME=Linux \
        -DCMAKE_SYSTEM_PROCESSOR=aarch64 \
        -DCMAKE_C_COMPILER=${CC} \
        -DCMAKE_INSTALL_PREFIX="${PREFIX}" \
        -DCMAKE_BUILD_TYPE=Release \
        -DENABLE_SHARED=OFF -DENABLE_STATIC=ON \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON
    make -j${NPROC}
    make install
    cd ../..
fi
echo "  libjpeg-turbo OK"

# ── 3. libpng ─────────────────────────────────────────────────────
echo "[3/7] libpng 1.6.44..."
if [ ! -f "${PREFIX}/lib/libpng16.a" ]; then
    curl -sL "https://sourceforge.net/projects/libpng/files/libpng16/1.6.44/libpng-1.6.44.tar.gz/download" -o libpng-1.6.44.tar.gz
    tar xf libpng-1.6.44.tar.gz
    cd libpng-1.6.44
    CHOST=aarch64-linux-gnu ./configure \
        --host=aarch64-linux-gnu \
        --prefix="${PREFIX}" \
        --enable-static --disable-shared \
        --disable-tests
    # IMPORTANT: LIBS=-lz is needed for libpng to find zlib in the right order
    make -j${NPROC} LIBS="-lz"
    make install
    cd ..
fi
echo "  libpng OK"

# ── 4. libtiff ────────────────────────────────────────────────────
echo "[4/7] libtiff 4.7.0..."
if [ ! -f "${PREFIX}/lib/libtiff.a" ]; then
    curl -sLO "https://download.osgeo.org/libtiff/tiff-4.7.0.tar.gz"
    tar xf tiff-4.7.0.tar.gz
    cd tiff-4.7.0
    ./configure \
        --host=aarch64-linux-gnu \
        --prefix="${PREFIX}" \
        --enable-static --disable-shared \
        --disable-tests --disable-tools --disable-docs
    make -j${NPROC}
    make install
    cd ..
fi
echo "  libtiff OK"

# ── 5. giflib ─────────────────────────────────────────────────────
echo "[5/7] giflib 5.2.2..."
if [ ! -f "${PREFIX}/lib/libgif.a" ]; then
    curl -sL "https://sourceforge.net/projects/giflib/files/giflib-5.2.2.tar.gz/download" -o giflib-5.2.2.tar.gz
    tar xf giflib-5.2.2.tar.gz
    cd giflib-5.2.2
    # giflib has no autotools; build libgif.a manually
    make libgif.a CC="${CC}" CFLAGS="${CFLAGS}" AR="${AR}" RANLIB="${RANLIB}"
    # Manual install since make install may fail for cross-compile
    mkdir -p "${PREFIX}/lib" "${PREFIX}/include/gif_lib.h"
    cp libgif.a "${PREFIX}/lib/"
    cp gif_lib.h "${PREFIX}/include/gif_lib.h" 2>/dev/null || cp lib/gif_lib.h "${PREFIX}/include/" 2>/dev/null
    cd ..
fi
echo "  giflib OK"

# ── 6. leptonica ──────────────────────────────────────────────────
echo "[6/7] leptonica 1.87.0..."
if [ ! -f "${PREFIX}/lib/libleptonica.a" ]; then
    curl -sLO "https://github.com/DanBloomberg/leptonica/releases/download/v1.87.0/leptonica-1.87.0.tar.gz"
    tar xf leptonica-1.87.0.tar.gz
    cd leptonica-1.87.0
    # Hide host webp headers to prevent leptonica from picking them up
    if [ -d /usr/include/webp ]; then
        echo "  Hiding /usr/include/webp temporarily..."
        sudo mv /usr/include/webp /usr/include/webp.bak
        WEBP_RESTORE=1
    fi
    ./configure \
        --host=aarch64-linux-gnu \
        --prefix="${PREFIX}" \
        --enable-static --disable-shared \
        --without-libopenjpeg \
        --without-libwebp \
        --without-libwebpmux \
        --disable-programs
    make -j${NPROC}
    # Copy the real static lib (not the libtool wrapper)
    cp src/.libs/libleptonica.a "${PREFIX}/lib/libleptonica.a"
    # Install headers
    mkdir -p "${PREFIX}/include/leptonica"
    cp src/*.h "${PREFIX}/include/leptonica/" 2>/dev/null || true
    # Restore webp headers
    if [ "${WEBP_RESTORE}" = "1" ]; then
        echo "  Restoring /usr/include/webp..."
        sudo mv /usr/include/webp.bak /usr/include/webp
    fi
    cd ..
fi
echo "  leptonica OK"

# ── 7. tesseract ──────────────────────────────────────────────────
echo "[7/7] tesseract 5.5.0..."
if [ ! -f "${PREFIX}/lib/libtesseract.a" ]; then
    curl -sLO "https://github.com/tesseract-ocr/tesseract/archive/refs/tags/5.5.0.tar.gz"
    tar xf 5.5.0.tar.gz
    cd tesseract-5.5.0
    ./autogen.sh
    ./configure \
        --host=aarch64-linux-gnu \
        --prefix="${PREFIX}" \
        --enable-static --disable-shared \
        --disable-libtool \
        --disable-graphics \
        --without-curl \
        --without-archive \
        --disable-tessdata \
        --disable-training \
        CPPFLAGS="-I${PREFIX}/include" \
        LDFLAGS="-L${PREFIX}/lib" \
        PKG_CONFIG_PATH="${PREFIX}/lib/pkgconfig"
    # Build ONLY the library (not the CLI binary which needs libarchive)
    make -j${NPROC} libtesseract.la
    # Copy the real static lib (not the libtool wrapper)
    cp .libs/libtesseract.a "${PREFIX}/lib/libtesseract.a"
    cp .libs/libtesseract_neon.a "${PREFIX}/lib/libtesseract_neon.a" 2>/dev/null || true
    cp .libs/libtesseract_native.a "${PREFIX}/lib/libtesseract_native.a" 2>/dev/null || true
    aarch64-linux-gnu-ranlib "${PREFIX}/lib/libtesseract.a"
    # Install headers
    mkdir -p "${PREFIX}/include/tesseract"
    cp include/tesseract/*.h "${PREFIX}/include/tesseract/" 2>/dev/null || true
    cd ..
fi
echo "  tesseract OK"

# ── 8. tessdata ───────────────────────────────────────────────────
echo "[8/8] tessdata_fast..."
if [ ! -f "${WORKDIR}/tessdata/eng.traineddata" ]; then
    curl -sL -o "${WORKDIR}/tessdata/eng.traineddata" \
        "https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata"
fi
echo "  tessdata OK"

# ── Strip & report ────────────────────────────────────────────────
echo ""
echo "=== Stripping libraries ==="
${STRIP} --strip-unneeded "${PREFIX}/lib/libtesseract.a" 2>/dev/null || true
${STRIP} --strip-unneeded "${PREFIX}/lib/libleptonica.a" 2>/dev/null || true
${RANLIB} "${PREFIX}/lib/libtesseract.a" 2>/dev/null || true
${RANLIB} "${PREFIX}/lib/libleptonica.a" 2>/dev/null || true

echo ""
echo "=== Build complete ==="
echo ""
echo "Libraries:"
ls -lh "${PREFIX}/lib/"*.a
echo ""
echo "Headers:"
ls "${PREFIX}/include/tesseract/" 2>/dev/null
echo ""
echo "Tessdata:"
ls -lh "${WORKDIR}/tessdata/"
echo ""
echo "=== Link command for test ==="
echo "  ${CXX} -o test_tess test.cpp \\"
echo "    -I${PREFIX}/include \\"
echo "    -L${PREFIX}/lib \\"
echo "    -ltesseract -l:libtesseract_neon.a -l:libtesseract_native.a \\"
echo "    -l:libleptonica.a -l:libtiff.a -l:libjpeg.a -l:libpng16.a -l:libz.a -l:libgif.a \\"
echo "    -fopenmp -lm -lpthread --static"

