#!/system/bin/sh
# AArchDroid - Login Shell
# Enters Arch Linux ARM chroot

CHROOT_DIR="/data/data/org.aarchdroid"
ROOTFS_DIR="${CHROOT_DIR}/ARCHDROID"
BUSYBOX="${CHROOT_DIR}/files/bin/busybox"

# Check if rootfs exists
if [ ! -d "${ROOTFS_DIR}/bin" ]; then
    echo "[AArchDroid] Rootfs no encontrado en ${ROOTFS_DIR}"
    echo "[AArchDroid] Ejecutando bootstrap inicial..."
    sh "${CHROOT_DIR}/files/scripts/bootstrap-arch.sh"
    if [ $? -ne 0 ]; then
        echo "[AArchDroid] ERROR: No se pudo instalar el rootfs"
        exit 1
    fi
fi

# Mount necessary filesystems
${BUSYBOX} mount -t proc none "${ROOTFS_DIR}/proc" 2>/dev/null
${BUSYBOX} mount -t sysfs none "${ROOTFS_DIR}/sys" 2>/dev/null
${BUSYBOX} mount -o bind /dev "${ROOTFS_DIR}/dev" 2>/dev/null
${BUSYBOX} mount -o bind /dev/pts "${ROOTFS_DIR}/dev/pts" 2>/dev/null
${BUSYBOX} mount -o bind /sdcard "${ROOTFS_DIR}/sdcard" 2>/dev/null

# Setup DNS
echo "nameserver 8.8.8.8" > "${ROOTFS_DIR}/etc/resolv.conf"
echo "nameserver 1.1.1.1" >> "${ROOTFS_DIR}/etc/resolv.conf"

# Copy our bashrc with auto-install hook
cp "${CHROOT_DIR}/files/scripts/bashrc-aarchdroid" "${ROOTFS_DIR}/root/.bashrc" 2>/dev/null

# Enter chroot as root
if [ $# -ge 1 ]; then
    exec ${BUSYBOX} chroot "${ROOTFS_DIR}" /bin/bash -c "source /root/.bashrc; $@"
else
    exec ${BUSYBOX} chroot "${ROOTFS_DIR}" /bin/bash --rcfile /root/.bashrc
fi
