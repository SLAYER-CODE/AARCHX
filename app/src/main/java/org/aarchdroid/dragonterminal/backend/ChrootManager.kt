package org.aarchdroid.dragonterminal.backend

import android.os.SystemClock
import java.io.File
import java.io.IOException

object ChrootManager {

    private const val CHROOT_BASE = "/data/local/aarchdroid"
    private const val CHROOT_PROC = "$CHROOT_BASE/proc"
    private const val MOUNT_CACHE_TTL_MS = 500L

    private var lastMountCheckTime = 0L
    private var lastMountResult = false

    private val SETUP_COMMANDS: String by lazy {
        buildString {
            append("setenforce 0 2>/dev/null; ")
            append("mount -o remount,exec,suid,dev,rw /data 2>/dev/null; ")
            append("mkdir -p $CHROOT_BASE/dev $CHROOT_BASE/proc $CHROOT_BASE/sys $CHROOT_BASE/tmp $CHROOT_BASE/data/data/org.aarchdroid; ")
            append("mount -t proc proc $CHROOT_BASE/proc 2>/dev/null; ")
            append("mount -t sysfs sys $CHROOT_BASE/sys 2>/dev/null; ")
            // tmpfs /dev instead of bind-mounting host /dev (avoid leaking binder nodes)
            append("mount -t tmpfs tmpfs $CHROOT_BASE/dev 2>/dev/null; ")
            append("mkdir -p $CHROOT_BASE/dev/pts $CHROOT_BASE/dev/shm $CHROOT_BASE/dev/net 2>/dev/null; ")
            append("mknod $CHROOT_BASE/dev/null c 1 3 2>/dev/null; ")
            append("mknod $CHROOT_BASE/dev/zero c 1 5 2>/dev/null; ")
            append("mknod $CHROOT_BASE/dev/random c 1 8 2>/dev/null; ")
            append("mknod $CHROOT_BASE/dev/urandom c 1 9 2>/dev/null; ")
            append("mknod $CHROOT_BASE/dev/tty c 5 0 2>/dev/null; ")
            append("mknod $CHROOT_BASE/dev/ptmx c 5 2 2>/dev/null; ")
            append("chmod 666 $CHROOT_BASE/dev/null $CHROOT_BASE/dev/zero $CHROOT_BASE/dev/random $CHROOT_BASE/dev/urandom $CHROOT_BASE/dev/tty $CHROOT_BASE/dev/ptmx 2>/dev/null; ")
            append("mknod $CHROOT_BASE/dev/net/tun c 10 200 2>/dev/null; ")
            append("mknod $CHROOT_BASE/dev/ppp c 108 0 2>/dev/null; ")
            append("chmod 600 $CHROOT_BASE/dev/net/tun $CHROOT_BASE/dev/ppp 2>/dev/null; ")
            append("ln -sf /proc/self/fd $CHROOT_BASE/dev/fd 2>/dev/null; ")
            append("ln -sf /proc/self/fd/0 $CHROOT_BASE/dev/stdin 2>/dev/null; ")
            append("ln -sf /proc/self/fd/1 $CHROOT_BASE/dev/stdout 2>/dev/null; ")
            append("ln -sf /proc/self/fd/2 $CHROOT_BASE/dev/stderr 2>/dev/null; ")
            append("mount -t devpts devpts $CHROOT_BASE/dev/pts 2>/dev/null; ")
            append("mount -t tmpfs tmpfs $CHROOT_BASE/dev/shm 2>/dev/null; ")
            append("chmod 1777 $CHROOT_BASE/tmp $CHROOT_BASE/dev/shm 2>/dev/null; ")
            append("umount $CHROOT_BASE/data/data/org.aarchdroid 2>/dev/null; mount -o bind /data/data/org.aarchdroid $CHROOT_BASE/data/data/org.aarchdroid")
        }
    }

    fun isMounted(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastMountCheckTime < MOUNT_CACHE_TTL_MS) {
            return lastMountResult
        }
        lastMountCheckTime = now
        lastMountResult = try {
            CHROOT_PROC in File("/proc/mounts").readText()
        } catch (e: Exception) {
            false
        }
        return lastMountResult
    }

    fun ensureMounted(): Boolean {
        if (isMounted()) return true
        return runSetup()
    }

    private fun runSetup(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-M", "-c", "$SETUP_COMMANDS && echo OK"))
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            (exitCode == 0 && output == "OK") || isMounted()
        } catch (e: IOException) {
            false
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    fun getEntryCommand(): String {
        return "exec chroot $CHROOT_BASE /bin/bash --rcfile /root/.bashrc"
    }

    fun getSuEntryArgs(): Array<String> {
        return arrayOf("su", "-M", "-c", getEntryCommand())
    }
}
