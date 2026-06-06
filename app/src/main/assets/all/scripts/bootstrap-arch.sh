#!/system/bin/sh
# AArchDroid - Bootstrap script
# Downloads and configures Arch Linux ARM rootfs with BlackArch repos

CHROOT_DIR="/data/data/org.aarchdroid"
ROOTFS_DIR="${CHROOT_DIR}/ARCHDROID"
BUSYBOX="${CHROOT_DIR}/files/bin/busybox"
TARBALL="/sdcard/Download/AArchDroid-rootfs-aarch64.tar.xz"
TARBALL_URL="https://github.com/Tiopaz/AArchDroid/releases/download/v1.0/AArchDroid-rootfs-aarch64.tar.xz"

echo "[AArchDroid] === Bootstrap ==="

mkdir -p "${ROOTFS_DIR}"

if [ -f "${TARBALL}" ]; then
    echo "[AArchDroid] Usando tarball local: ${TARBALL}"
elif [ -f "/sdcard/Download/AArchDroid-rootfs-aarch64.tar.xz" ]; then
    TARBALL="/sdcard/Download/AArchDroid-rootfs-aarch64.tar.xz"
else
    echo "[AArchDroid] Coloca AArchDroid-rootfs-aarch64.tar.xz en /sdcard/Download/"
    echo "[AArchDroid] Genera el rootfs con: rootfs/mkrootfs.sh"
    exit 1
fi

echo "[AArchDroid] Extrayendo rootfs..."
${BUSYBOX} tar -xJpf "${TARBALL}" -C "${ROOTFS_DIR}" 2>/dev/null
if [ $? -ne 0 ]; then
    echo "[AArchDroid] ERROR: Fallo al extraer rootfs"
    exit 1
fi

# Setup mounts
mkdir -p "${ROOTFS_DIR}/proc" "${ROOTFS_DIR}/dev" "${ROOTFS_DIR}/sys" "${ROOTFS_DIR}/sdcard"
${BUSYBOX} mount -t proc none "${ROOTFS_DIR}/proc" 2>/dev/null
${BUSYBOX} mount -o bind /dev "${ROOTFS_DIR}/dev" 2>/dev/null

# Configure pacman
echo "en_US.UTF-8 UTF-8" > "${ROOTFS_DIR}/etc/locale.gen"
cat > "${ROOTFS_DIR}/etc/pacman.d/mirrorlist" << 'MIRRORS'
Server = http://mirror.archlinuxarm.org/$arch/$repo
Server = http://eu.mirror.archlinuxarm.org/$arch/$repo
MIRRORS

echo "[AArchDroid] Inicializando pacman keyring..."
${BUSYBOX} chroot "${ROOTFS_DIR}" /bin/bash -c "
    pacman-key --init 2>/dev/null
    pacman-key --populate archlinuxarm 2>/dev/null
    pacman -Sy --noconfirm 2>/dev/null
" 2>/dev/null

# Copy bashrc with auto-install hook
cp "${CHROOT_DIR}/files/scripts/bashrc-aarchdroid" "${ROOTFS_DIR}/root/.bashrc" 2>/dev/null

echo "[AArchDroid] ✓ Rootfs instalado correctamente!"
