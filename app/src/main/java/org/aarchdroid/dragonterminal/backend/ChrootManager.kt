package org.aarchdroid.dragonterminal.backend

import java.io.File
import java.io.IOException

object ChrootManager {

    private const val CHROOT_BASE = "/data/local/aarchdroid"
    private const val CHROOT_PROC = "$CHROOT_BASE/proc"

    private val SETUP_COMMANDS: String by lazy {
        buildString {
            append("setenforce 0 2>/dev/null; ")
            append("mount -o remount,exec,suid,dev,rw /data 2>/dev/null; ")
            append("mkdir -p $CHROOT_BASE/data/data/org.aarchdroid $CHROOT_BASE/dev $CHROOT_BASE/dev/pts $CHROOT_BASE/proc $CHROOT_BASE/sys $CHROOT_BASE/tmp; ")
            append("mount -t proc proc $CHROOT_BASE/proc 2>/dev/null; ")
            append("mount -t sysfs sys $CHROOT_BASE/sys 2>/dev/null; ")
            append("mount -o bind /dev $CHROOT_BASE/dev 2>/dev/null; ")
            append("mount -t devpts devpts $CHROOT_BASE/dev/pts 2>/dev/null; ")
            append("chmod 1777 $CHROOT_BASE/tmp 2>/dev/null; ")
            append("mount -o bind /data/data/org.aarchdroid $CHROOT_BASE/data/data/org.aarchdroid 2>/dev/null")
        }
    }

    fun isMounted(): Boolean {
        return try {
            val mounts = File("/proc/mounts").readText()
            CHROOT_PROC in mounts
        } catch (e: Exception) {
            false
        }
    }

    fun ensureMounted(): Boolean {
        if (isMounted()) return true
        return runSetup()
    }

    private fun runSetup(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "$SETUP_COMMANDS && echo OK"))
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
}
