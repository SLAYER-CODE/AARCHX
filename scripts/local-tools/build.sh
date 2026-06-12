#!/bin/bash
# Build local-source tools for aarch64 and inject into rootfs.tgz
# Run from project root: ./scripts/local-tools/build.sh

set -e

SRC_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT="$(cd "$SRC_DIR/../.." && pwd)"
BUILD_DIR=$(mktemp -d)
ROOTFS_TGZ="$PROJECT/app/src/main/assets/rootfs.tgz"

echo "Building wbk (Go)..."
cd "$BUILD_DIR"
mkdir -p wbk
cp "$SRC_DIR/wbk.go" wbk/main.go
cp "$SRC_DIR/go.mod" wbk/go.mod
cd wbk
GOARCH=arm64 GOOS=linux go build -ldflags="-s -w" -o wbk.aarch64 .
aarch64-linux-gnu-strip wbk.aarch64 2>/dev/null || true

echo "Building udpfloodvlan (C + libnet)..."
cd "$BUILD_DIR"
mkdir -p udpfloodvlan
cp "$SRC_DIR/udpfloodvlan.c" udpfloodvlan/

LIBNET_DIR="$BUILD_DIR/libnet-aarch64"
if [ ! -f "$LIBNET_DIR/lib/libnet.a" ]; then
    echo "Cross-compiling libnet for aarch64..."
    cd "$BUILD_DIR"
    curl -sL "https://github.com/libnet/libnet/releases/download/v1.3/libnet-1.3.tar.gz" -o libnet.tar.gz
    tar xzf libnet.tar.gz
    cd libnet-1.3
    CC=aarch64-linux-gnu-gcc ./configure --host=aarch64-linux-gnu --enable-shared=no --enable-static=yes --prefix="$LIBNET_DIR" >/dev/null
    make -j$(nproc) >/dev/null
    make install >/dev/null
fi

cd "$BUILD_DIR/udpfloodvlan"
aarch64-linux-gnu-gcc -static \
    -I"$LIBNET_DIR/include" -L"$LIBNET_DIR/lib" \
    -o udpfloodvlan.aarch64 udpfloodvlan.c -lnet
aarch64-linux-gnu-strip udpfloodvlan.aarch64 2>/dev/null || true

echo "Injecting into rootfs.tgz..."
cd "$BUILD_DIR"
gunzip -c "$ROOTFS_TGZ" > rootfs.tar
mkdir -p inject/usr/bin
cp wbk/wbk.aarch64 inject/usr/bin/wbk
cp udpfloodvlan/udpfloodvlan.aarch64 inject/usr/bin/udpfloodvlan
chmod 755 inject/usr/bin/wbk inject/usr/bin/udpfloodvlan
tar rf rootfs.tar -C inject usr/bin/wbk usr/bin/udpfloodvlan
gzip -c rootfs.tar > rootfs.tgz
cp rootfs.tgz "$ROOTFS_TGZ"

echo "Done. $(ls -lh "$ROOTFS_TGZ" | awk '{print $5}')"
rm -rf "$BUILD_DIR"
