#!/system/bin/sh
# AArchDroid - Legacy ANDRAX Shell (adapted)

CHROOT_DIR="/data/local/aarchdroid"
BUSYBOX="/data/data/org.aarchdroid/files/bin/busybox"

if [ ! -d "${CHROOT_DIR}/bin" ]; then
    echo "[AArchDroid] Rootfs not installed."
    exit 1
fi

if [ $# -gt 0 ]; then
    unset TMP TEMP TMPDIR LD_PRELOAD LD_DEBUG
    exec ${BUSYBOX} chroot "${CHROOT_DIR}" /bin/bash -c "source /root/.bashrc; $@"
else
    unset TMP TEMP TMPDIR LD_PRELOAD LD_DEBUG
    exec ${BUSYBOX} chroot "${CHROOT_DIR}" /bin/bash --rcfile /root/.bashrc
fi
