package org.aarchdroid.neovim

import android.content.Context
import android.util.Log
import java.io.File

class NeovimLauncher(private val context: Context) {
    companion object {
        private const val TAG = "NeovimLauncher"
        private const val PORT = 9999
        private const val HOST = "127.0.0.1"
        private const val CHROOT_DIR = "/data/local/aarchdroid"
    }

    data class LaunchedProcess(
        val process: Process,
        val host: String,
        val port: Int
    )

    private var launched: LaunchedProcess? = null

    val isRunning: Boolean get() = launched != null
    fun getHost(): String = HOST
    fun getPort(): Int = PORT

    suspend fun launch(): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val nvimPath = findNvim()
                if (nvimPath == null) {
                    Log.e(TAG, "nvim not found")
                    return@withContext false
                }

                // Kill any existing nvim on our port
                Runtime.getRuntime().exec(arrayOf("sh", "-c", "kill -9 \$(lsof -ti:$PORT) 2>/dev/null")).waitFor()

                val proc = if (nvimPath.startsWith(CHROOT_DIR)) {
                    launchInChroot(nvimPath)
                } else {
                    launchDirect(nvimPath)
                }

                Thread.sleep(500)

                if (proc.isAlive) {
                    launched = LaunchedProcess(proc, HOST, PORT)
                    Log.d(TAG, "nvim launched from $nvimPath")
                    true
                } else {
                    Log.e(TAG, "nvim exited immediately")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch nvim: ${e.message}")
                false
            }
        }
    }

    private fun launchInChroot(fullPath: String): Process {
        // Extract path relative to chroot root
        val relPath = fullPath.removePrefix(CHROOT_DIR)
        val cmd = "su -c \"mount -o remount,exec,suid,dev,rw /data 2>/dev/null; " +
                "exec chroot $CHROOT_DIR $relPath --headless --listen $HOST:$PORT " +
                "-c 'set notermguicolors' -c 'highlight Normal ctermbg=NONE'\""
        Log.d(TAG, "Launching via chroot: $cmd")
        val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
        // Set env via process environment — not directly accessible for Runtime.exec
        // nvim inside chroot gets its env from the chroot shell
        return proc
    }

    private fun launchDirect(nvimPath: String): Process {
        val pb = ProcessBuilder(
            nvimPath,
            "--headless",
            "--listen", "$HOST:$PORT",
            "-c", "set notermguicolors",
            "-c", "highlight Normal ctermbg=NONE"
        )
        pb.environment()["NVIM_LISTEN_ADDRESS"] = "$HOST:$PORT"
        pb.environment()["TERM"] = "xterm-256color"

        // If path is under Termux, add its lib path
        if (nvimPath.contains("com.termux")) {
            pb.environment()["LD_LIBRARY_PATH"] = "/data/data/com.termux/files/usr/lib"
        }

        pb.redirectErrorStream(true)
        Log.d(TAG, "Launching direct: $nvimPath")
        return pb.start()
    }

    fun shutdown() {
        try {
            launched?.process?.destroy()
            // Kill any lingering nvim on our port
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "kill -9 \$(lsof -ti:$PORT) 2>/dev/null")).waitFor()
        } catch (_: Exception) {}
        launched = null
    }

    private fun findNvim(): String? {
        // 1. Try direct File.exists() (works for Termux, system paths, app-private)
        val paths = listOf(
            "$CHROOT_DIR/usr/bin/nvim",
            "$CHROOT_DIR/usr/bin/vim",
            "/system/bin/nvim",
            "/system/bin/vim",
            "/data/data/com.termux/files/usr/bin/nvim",
            "/data/data/com.termux/files/usr/bin/vim",
            "/data/data/org.aarchdroid/files/usr/bin/nvim",
            "/data/data/org.aarchdroid/files/usr/bin/vim"
        )
        for (p in paths) {
            val f = File(p)
            if (f.exists()) {
                Log.d(TAG, "Found nvim at: $p")
                return p
            }
        }

        // 2. Try via su (chroot paths may be invisible to app process)
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c",
                "test -f $CHROOT_DIR/usr/bin/nvim && echo nvim || test -f $CHROOT_DIR/usr/bin/vim && echo vim"))
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            if (out == "nvim") {
                Log.d(TAG, "Found nvim via su at $CHROOT_DIR/usr/bin/nvim")
                return "$CHROOT_DIR/usr/bin/nvim"
            }
            if (out == "vim") {
                Log.d(TAG, "Found vim via su at $CHROOT_DIR/usr/bin/vim")
                return "$CHROOT_DIR/usr/bin/vim"
            }
        } catch (_: Exception) {}

        // 3. Try which via system shell
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "command -v nvim 2>/dev/null || which nvim 2>/dev/null"))
            val path = proc.inputStream.bufferedReader().readText().trim()
            if (path.isNotEmpty() && File(path).exists()) {
                Log.d(TAG, "Found nvim via shell: $path")
                path
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
