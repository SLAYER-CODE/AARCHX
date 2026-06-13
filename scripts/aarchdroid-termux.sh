#!/system/bin/sh
# Enter AArchDroid chroot from Termux
# Usage:  sh ~/aarchdroid-termux.sh
# Run directly from Termux (handles su internally)

CHROOT=/data/local/aarchdroid

# Re-exec as root if needed
if [ "$(id -u)" != "0" ]; then
  exec su -c "sh $0"
fi

# Remount /data with needed flags (idempotent)
mount -o remount,exec,suid,dev,rw /data 2>/dev/null

# Ensure directories and device nodes exist
mkdir -p $CHROOT/tmp 2>/dev/null
mkdir -p $CHROOT/dev/pts 2>/dev/null
mknod -m 666 $CHROOT/dev/null c 1 3 2>/dev/null
mknod -m 666 $CHROOT/dev/zero c 1 5 2>/dev/null
mknod -m 666 $CHROOT/dev/random c 1 8 2>/dev/null
mknod -m 666 $CHROOT/dev/urandom c 1 9 2>/dev/null
mknod -m 666 $CHROOT/dev/ptmx c 5 2 2>/dev/null

# Mount pseudo-filesystems (skip if already mounted)
mount | grep -q "$CHROOT/proc"  || mount -t proc proc $CHROOT/proc 2>/dev/null
mount | grep -q "$CHROOT/sys"   || mount -t sysfs sys $CHROOT/sys 2>/dev/null
mount | grep -q "$CHROOT/dev/pts" || mount -o bind /dev/pts $CHROOT/dev/pts 2>/dev/null

chmod 1777 $CHROOT/tmp 2>/dev/null

# Enter chroot (interactive shell)
exec chroot $CHROOT /bin/bash --rcfile /root/.bashrc
