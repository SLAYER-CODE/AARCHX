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
            append("mkdir -p $CHROOT_BASE/dev/pts $CHROOT_BASE/dev/net $CHROOT_BASE/proc $CHROOT_BASE/sys $CHROOT_BASE/tmp $CHROOT_BASE/data/data/org.aarchdroid; ")
            append("mount -t proc proc $CHROOT_BASE/proc 2>/dev/null; ")
            append("mount -t sysfs sys $CHROOT_BASE/sys 2>/dev/null; ")
            append("mount -o bind /dev $CHROOT_BASE/dev 2>/dev/null; ")
            append("mount -t devpts devpts $CHROOT_BASE/dev/pts 2>/dev/null; ")
            append("chmod 1777 $CHROOT_BASE/tmp 2>/dev/null; ")
            append("mount -o bind /data/data/org.aarchdroid $CHROOT_BASE/data/data/org.aarchdroid 2>/dev/null")
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
