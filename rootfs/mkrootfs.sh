#!/bin/bash
# mkrootfs.sh - Generate AArchDroid rootfs tarball
# Requires: pacstrap, qemu-user-static (for ARM emulation)
# 
# Usage: sudo ./mkrootfs.sh [output-dir]
#
# This script creates a minimal Arch Linux ARM rootfs with:
#   - Base system (core, extra, community)
#   - BlackArch repos configured
#   - command_not_found_handle for auto-install
#   - Network tools pre-installed (base group)

set -e

OUTDIR="${1:-./output}"
ROOTFS="${OUTDIR}/rootfs"
TARBALL="${OUTDIR}/AArchDroid-rootfs-aarch64.tar.xz"
ARCH="aarch64"

echo "=== AArchDroid Rootfs Generator ==="
echo "Target: ${ARCH}"
echo "Output: ${TARBALL}"
echo ""

# Check prerequisites
if [ "$(id -u)" -ne 0 ]; then
    echo "ERROR: Must run as root (for pacstrap)"
    exit 1
fi

if ! command -v pacstrap &>/dev/null; then
    echo "ERROR: pacstrap not found. Install arch-install-scripts."
    exit 1
fi

# Clean old
rm -rf "${ROOTFS}" "${OUTDIR}"
mkdir -p "${ROOTFS}"

echo "[1/5] Pacstrapping base system..."
pacstrap -c -M "${ROOTFS}" base base-devel bash pacman glibc coreutils systemd 2>&1 | tail -5

echo "[2/5] Configuring pacman..."
cp pacman.conf "${ROOTFS}/etc/pacman.conf"

# Setup mirrorlist
cat > "${ROOTFS}/etc/pacman.d/mirrorlist" << 'MIRRORS'
## Arch Linux ARM official mirrors
Server = http://mirror.archlinuxarm.org/$arch/$repo
Server = http://eu.mirror.archlinuxarm.org/$arch/$repo
Server = http://us.mirror.archlinuxarm.org/$arch/$repo
MIRRORS

echo "[3/5] Adding BlackArch repo..."
arch-chroot "${ROOTFS}" /bin/bash << 'CHROOT'
    pacman-key --init 2>/dev/null
    pacman-key --populate archlinuxarm 2>/dev/null
    
    # Add BlackArch keyring
    curl -sL "https://blackarch.org/keyring/blackarch-keyring.pkg.tar.xz" -o /tmp/blackarch-keyring.pkg.tar.xz 2>/dev/null
    if [ -f /tmp/blackarch-keyring.pkg.tar.xz ]; then
        pacman -U --noconfirm /tmp/blackarch-keyring.pkg.tar.xz 2>/dev/null || true
        rm -f /tmp/blackarch-keyring.pkg.tar.xz
    fi
    
    pacman -Sy --noconfirm 2>/dev/null || true
CHROOT

echo "[4/5] Installing essential tools + auto-install hook..."
arch-chroot "${ROOTFS}" /bin/bash << 'CHROOT'
    # Install network and basic tools
    pacman -S --noconfirm --needed \
        nmap \
        curl \
        wget \
        git \
        openssh \
        python3 \
        python-pip \
        perl \
        ruby \
        go \
        vim \
        nano \
        sudo \
        file \
        which \
        man-db \
        man-pages \
        2>&1 | tail -5 || true
CHROOT

# Copy our bashrc with command_not_found_handle
cp ../app/src/main/assets/all/scripts/bashrc-aarchdroid "${ROOTFS}/root/.bashrc"

# Setup root user
echo "root:root" | chpasswd -R "${ROOTFS}" 2>/dev/null || true

echo "[5/5] Creating tarball..."
cd "${ROOTFS}"
tar -cpJf "${TARBALL}" . 2>&1 | tail -3
cd - >/dev/null

echo ""
echo "=== Done! ==="
echo "Rootfs tarball: ${TARBALL}"
echo "Size: $(du -h "${TARBALL}" | cut -f1)"
echo ""
echo "To use:"
echo "  1. Copy to phone: adb push ${TARBALL} /sdcard/Download/"
echo "  2. Open AArchDroid app"
echo "  3. The bootstrap will detect it automatically"
