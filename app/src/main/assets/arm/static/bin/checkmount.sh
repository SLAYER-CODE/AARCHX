#!/system/bin/sh

CHROOT_DIR="/data/local/aarchdroid"

if [ -f "${CHROOT_DIR}/.aarchdroid_chroot" ]; then
    exit 0
else
    exit 1
fi
