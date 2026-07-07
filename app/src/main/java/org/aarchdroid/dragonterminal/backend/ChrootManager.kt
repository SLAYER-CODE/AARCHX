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
            // bind mount host /dev (NetHunter/Andrax style) — full device access
            append("mount -o bind /dev $CHROOT_BASE/dev 2>/dev/null; ")
            // mount pts, shm, tun over the bind
            append("mkdir -p $CHROOT_BASE/dev/pts $CHROOT_BASE/dev/shm $CHROOT_BASE/dev/net 2>/dev/null; ")
            append("ln -sf /proc/self/fd $CHROOT_BASE/dev/fd 2>/dev/null; ")
            append("ln -sf /proc/self/fd/0 $CHROOT_BASE/dev/stdin 2>/dev/null; ")
            append("ln -sf /proc/self/fd/1 $CHROOT_BASE/dev/stdout 2>/dev/null; ")
            append("ln -sf /proc/self/fd/2 $CHROOT_BASE/dev/stderr 2>/dev/null; ")
            append("mount -t devpts devpts $CHROOT_BASE/dev/pts 2>/dev/null; ")
            append("mount -t tmpfs tmpfs $CHROOT_BASE/dev/shm 2>/dev/null; ")
            append("chmod 1777 $CHROOT_BASE/tmp $CHROOT_BASE/dev/shm 2>/dev/null; ")
            // /run tmpfs for PID files, sockets, service runtime data
            append("mkdir -p $CHROOT_BASE/run 2>/dev/null; mount -t tmpfs tmpfs $CHROOT_BASE/run 2>/dev/null; chmod 1777 $CHROOT_BASE/run 2>/dev/null; ")
            // wpa_supplicant socket from host (for OneShot, wpa_cli, etc.)
            append("mkdir -p $CHROOT_BASE/var/run/wpa_supplicant 2>/dev/null; mount -o bind /data/vendor/wifi/wpa/sockets $CHROOT_BASE/var/run/wpa_supplicant 2>/dev/null; ")
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
        return if (isMounted()) true else runSetup()
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

    @JvmStatic
    fun runUnmount(): String {
        return try {
            val mounts = listOf(
                "$CHROOT_BASE/var/run/wpa_supplicant",
                "$CHROOT_BASE/root/Externa",
                "$CHROOT_BASE/root/Interna",
                "$CHROOT_BASE/storage",
                "$CHROOT_BASE/sdcard",
                "$CHROOT_BASE/data/data/org.aarchdroid",
                "$CHROOT_BASE/dev/shm",
                "$CHROOT_BASE/run",
                "$CHROOT_BASE/dev/pts",
                "$CHROOT_BASE/dev",
                "$CHROOT_BASE/sys",
                "$CHROOT_BASE/proc",
            )
            val sb = StringBuilder()
            for (m in mounts) {
                // Pre-consulta: verificar si está montado antes de intentar umount
                val checkProc = Runtime.getRuntime().exec(arrayOf("su", "-M", "-c", "grep -q ' $m ' /proc/mounts"))
                val isMounted = checkProc.waitFor() == 0
                if (!isMounted) {
                    sb.appendLine("[ ] No montado: $m")
                    continue
                }
                val cmd = "umount -l $m 2>&1"
                val p = Runtime.getRuntime().exec(arrayOf("su", "-M", "-c", cmd))
                val out = p.inputStream.bufferedReader().readText().trim()
                val exitCode = p.waitFor()
                val clean = out.replace("\n", " | ").take(120)
                if (exitCode == 0) {
                    sb.appendLine("[+] Desmontado: $m")
                } else {
                    sb.appendLine("[-] Fallo $m (exit=$exitCode): $clean")
                }
            }
            lastMountCheckTime = 0L
            lastMountResult = false
            sb.toString()
        } catch (e: Exception) {
            "[-] Error en unmount: ${e.message}"
        }
    }

    @JvmStatic
    fun writeHelperScript(context: android.content.Context): Boolean {
        return try {
            val D = "${'$'}"
            val script = """#!/system/bin/sh
# AArchDroid chroot launcher — generated by app
CHROOT_BASE="/data/local/aarchdroid"

if [ ! -f "${D}CHROOT_BASE/bin/bash" ]; then
    echo "Chroot not found at ${D}CHROOT_BASE"
    echo "Install chroot from the AArchDroid app first."
    exit 1
fi

grep -q "${D}CHROOT_BASE/proc" /proc/mounts 2>/dev/null || mount -t proc proc "${D}CHROOT_BASE/proc" 2>/dev/null
grep -q "${D}CHROOT_BASE/sys" /proc/mounts 2>/dev/null || mount -t sysfs sys "${D}CHROOT_BASE/sys" 2>/dev/null
grep -q " ${D}CHROOT_BASE/dev " /proc/mounts 2>/dev/null || mount -o bind /dev "${D}CHROOT_BASE/dev" 2>/dev/null
grep -q "${D}CHROOT_BASE/dev/pts" /proc/mounts 2>/dev/null || mount -t devpts devpts "${D}CHROOT_BASE/dev/pts" 2>/dev/null
grep -q "${D}CHROOT_BASE/run" /proc/mounts 2>/dev/null || mount -t tmpfs tmpfs "${D}CHROOT_BASE/run" 2>/dev/null
grep -q "${D}CHROOT_BASE/dev/shm" /proc/mounts 2>/dev/null || mount -t tmpfs tmpfs "${D}CHROOT_BASE/dev/shm" 2>/dev/null
grep -q "${D}CHROOT_BASE/data/data/org.aarchdroid" /proc/mounts 2>/dev/null || mount -o bind /data/data/org.aarchdroid "${D}CHROOT_BASE/data/data/org.aarchdroid" 2>/dev/null

grep -q "${D}CHROOT_BASE/var/run/wpa_supplicant" /proc/mounts 2>/dev/null || mount -o bind /data/vendor/wifi/wpa/sockets "${D}CHROOT_BASE/var/run/wpa_supplicant" 2>/dev/null

if [ ! -f "${D}CHROOT_BASE/etc/resolv.conf" ] || [ ! -s "${D}CHROOT_BASE/etc/resolv.conf" ]; then
    cp /system/etc/resolv.conf "${D}CHROOT_BASE/etc/resolv.conf" 2>/dev/null || echo "nameserver 8.8.8.8" > "${D}CHROOT_BASE/etc/resolv.conf"
fi

SHELL=${D}(grep "^root:" "${D}CHROOT_BASE/etc/passwd" | cut -d: -f7)
[ -z "${D}SHELL" ] && SHELL="/bin/bash"
[ ! -x "${D}CHROOT_BASE${D}SHELL" ] && SHELL="/bin/bash"
export HOME=/root
case "${D}SHELL" in
  */bash) exec chroot "${D}CHROOT_BASE" /bin/sh -c "cd /root && exec ${D}SHELL --rcfile /root/.bashrc";;
  *) exec chroot "${D}CHROOT_BASE" /bin/sh -c "cd /root && exec ${D}SHELL" 2>/dev/null;;
esac
"""
            val tmpFile = java.io.File(context.cacheDir, "aarchrun.sh")
            tmpFile.writeText(script)
            Runtime.getRuntime().exec(arrayOf("su", "-c", "cp " + tmpFile.absolutePath + " /data/local/aarchrun.sh && chmod 0755 /data/local/aarchrun.sh")).waitFor()
            tmpFile.delete()
            val ok = Runtime.getRuntime().exec(arrayOf("su", "-c", "test -f /data/local/aarchrun.sh")).waitFor() == 0
            android.util.Log.d("ChrootManager", "Helper script written to /data/local/aarchrun.sh — ok=$ok")
            ok
        } catch (e: Exception) {
            android.util.Log.w("ChrootManager", "Failed to write helper script: ${e.message}")
            false
        }
    }

    fun getEntryCommand(): String {
        return "export HOME=/root; SHELL=\$(grep \"^root:\" $CHROOT_BASE/etc/passwd | cut -d: -f7); [ -z \"\$SHELL\" ] && SHELL=/bin/bash; [ ! -x $CHROOT_BASE/\$SHELL ] && SHELL=/bin/bash; case \"\$SHELL\" in */bash) exec chroot $CHROOT_BASE /bin/sh -c \"cd /root && exec \$SHELL --rcfile /root/.bashrc\";; *) exec chroot $CHROOT_BASE /bin/sh -c \"cd /root && exec \$SHELL\";; esac"
    }

    fun getSuEntryArgs(): Array<String> {
        return arrayOf("su", "-M", "-c", getEntryCommand())
    }
}
