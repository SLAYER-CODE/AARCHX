#!/system/bin/sh
# AArchDroid - Login Shell
# Enters Arch Linux ARM chroot
# v5 — configs pre-cargadas en rootfs.tgz, sin fixups runtime

CHROOT_DIR="/data/local/aarchdroid"
ROOTFS_DIR="${CHROOT_DIR}"
BUSYBOX="/data/data/org.aarchdroid/files/bin/busybox"
LOG_FILE="/sdcard/aarchdroid_chroot.log"

# Prefer system toybox; fall back to deployed busybox
TOOLBOX="busybox"
if [ -f "/system/bin/toybox" ]; then
    TOOLBOX="/system/bin/toybox"
elif [ -x "/system/bin/toybox" ]; then
    TOOLBOX="toybox"
elif [ -f "$BUSYBOX" ]; then
    TOOLBOX="$BUSYBOX"
fi

log() {
    echo "[AArchDroid] $(date) $*" >> "$LOG_FILE" 2>/dev/null || true
}

# Some devices limit what the /data partition can do
${TOOLBOX} mount -o remount,exec,suid,dev,rw /data >> "$LOG_FILE" 2>&1

# Check if rootfs exists
if [ ! -d "${ROOTFS_DIR}/bin" ]; then
    log "Rootfs no encontrado en ${ROOTFS_DIR}"
    log "Ejecutando bootstrap inicial..."
    sh "/data/data/org.aarchdroid/files/scripts/bootstrap-arch.sh"
    if [ $? -ne 0 ]; then
        log "ERROR: No se pudo instalar el rootfs"
        exit 1
    fi
fi

export PACMAN_DISABLE_SANDBOX=1

# --- ALWAYS ensure device nodes exist (even if already mounted) ---
# This fixes the case where /proc/mounts has old entries but /dev is broken.
log "Verificando dispositivos en ${ROOTFS_DIR}/dev..."
mkdir -p "${ROOTFS_DIR}/dev" 2>/dev/null

_dev() {
    local p="$1" m="$2" mj="$3" mn="$4"
    if [ ! -c "$p" ]; then
        log "Creando/ reparando dispositivo: $p ($mj:$mn)"
        rm -f "$p" 2>/dev/null
        ${TOOLBOX} mknod -m "$m" "$p" c "$mj" "$mn" >> "$LOG_FILE" 2>&1
        if [ ! -c "$p" ]; then
            log "mknod fallo para $p, usando bind desde /dev"
            local src="/dev/$(basename $p)"
            if [ -e "$src" ]; then
                mkdir -p "$(dirname $p)" 2>/dev/null
                ${TOOLBOX} mount -o bind "$src" "$p" >> "$LOG_FILE" 2>&1
                if [ ! -e "$p" ]; then
                    log "ERROR: bind mount tambien fallo para $p"
                else
                    log "bind mount exitoso para $p"
                fi
            else
                log "ERROR: origen $src no existe en el host"
            fi
        else
            log "mknod exitoso para $p"
        fi
    fi
}
_dev "${ROOTFS_DIR}/dev/null"    666 1 3
_dev "${ROOTFS_DIR}/dev/zero"    666 1 5
_dev "${ROOTFS_DIR}/dev/random"  644 1 8
_dev "${ROOTFS_DIR}/dev/urandom" 644 1 9
_dev "${ROOTFS_DIR}/dev/tty"     666 5 0
_dev "${ROOTFS_DIR}/dev/ptmx"    666 5 2

# /dev/net/tun
mkdir -p "${ROOTFS_DIR}/dev/net" 2>/dev/null
_dev "${ROOTFS_DIR}/dev/net/tun" 600 10 200

# --- Mount filesystems (individually, skip if already mounted) ---
log "Montando sistemas de archivos..."

mkdir -p "${ROOTFS_DIR}/proc" "${ROOTFS_DIR}/sys" "${ROOTFS_DIR}/dev/pts" "${ROOTFS_DIR}/sdcard" 2>/dev/null

if ! grep -q "${ROOTFS_DIR}/proc" /proc/mounts 2>/dev/null; then
    ${TOOLBOX} mount -t proc none "${ROOTFS_DIR}/proc" >> "$LOG_FILE" 2>&1 || log "WARN: mount proc fallo"
fi
if ! grep -q "${ROOTFS_DIR}/sys" /proc/mounts 2>/dev/null; then
    ${TOOLBOX} mount -t sysfs none "${ROOTFS_DIR}/sys" >> "$LOG_FILE" 2>&1 || log "WARN: mount sysfs fallo"
fi
if ! grep -q "${ROOTFS_DIR}/dev/pts" /proc/mounts 2>/dev/null; then
    ${TOOLBOX} mount -o bind /dev/pts "${ROOTFS_DIR}/dev/pts" >> "$LOG_FILE" 2>&1 || log "WARN: mount devpts fallo"
fi
if ! grep -q "${ROOTFS_DIR}/sdcard" /proc/mounts 2>/dev/null; then
    ${TOOLBOX} mount -o bind /sdcard "${ROOTFS_DIR}/sdcard" >> "$LOG_FILE" 2>&1 || log "WARN: mount sdcard fallo"
fi

# Mount /data inside chroot so pacman can resolve f_fsid (f2fs statfs issue)
if ! grep -q "${ROOTFS_DIR}/data" /proc/mounts 2>/dev/null; then
    mkdir -p "${ROOTFS_DIR}/data" 2>/dev/null
    ${TOOLBOX} mount -o bind /data "${ROOTFS_DIR}/data" >> "$LOG_FILE" 2>&1 || log "WARN: bind mount /data fallo"
fi

# /dev/fd symlink needed by pacman-key
if [ ! -L "${ROOTFS_DIR}/dev/fd" ] && [ ! -e "${ROOTFS_DIR}/dev/fd" ]; then
    ln -s /proc/self/fd "${ROOTFS_DIR}/dev/fd" 2>/dev/null || log "WARN: no se pudo crear /dev/fd symlink"
fi

log "Montaje completado"

# First-time pacman keyring initialization (solo se ejecuta UNA vez)
if [ ! -f "${ROOTFS_DIR}/etc/pacman.d/gnupg/trustdb.gpg" ]; then
    log "Inicializando pacman keyring por primera vez..."
    CMD="export PATH=/usr/bin:/bin:/usr/sbin:/sbin && pacman-key --init && pacman-key --populate archlinuxarm"
    ${TOOLBOX} chroot "${ROOTFS_DIR}" /bin/sh -c "$CMD" >> "$LOG_FILE" 2>&1
    log "pacman keyring inicializado"
fi

# Enter chroot
log "Entrando al chroot..."
export PATH="/usr/local/sbin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"
if [ $# -ge 1 ]; then
    ${TOOLBOX} chroot "${ROOTFS_DIR}" /bin/bash -c "source /root/.bashrc; export PATH=\"${PATH}\"; $@"
else
    ${TOOLBOX} chroot "${ROOTFS_DIR}" /bin/bash --rcfile /root/.bashrc
fi

RC=$?
log "Chroot terminado (exit: $RC)"
exit $RC
