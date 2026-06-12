package org.aarchdroid.neovim

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

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

                killExistingOnPort()

                val proc = if (nvimPath.startsWith(CHROOT_DIR)) {
                    launchInChroot(nvimPath)
                } else {
                    launchDirect(nvimPath)
                }

                // Check for nvim by TCP port (process may have detached)
                for (i in 1..3) {
                    Thread.sleep(800)
                    if (checkPortOpen(HOST, PORT)) {
                        launched = LaunchedProcess(proc, HOST, PORT)
                        Log.d(TAG, "nvim launched from $nvimPath (attempt $i)")
                        return@withContext true
                    }
                }

                // Port still not open — kill any stale process and retry once
                Log.e(TAG, "nvim not responding on $HOST:$PORT after 3 attempts, retrying...")
                killExistingOnPort()
                Thread.sleep(500)

                val proc2 = if (nvimPath.startsWith(CHROOT_DIR)) {
                    launchInChroot(nvimPath)
                } else {
                    launchDirect(nvimPath)
                }

                Thread.sleep(1500)
                if (checkPortOpen(HOST, PORT)) {
                    launched = LaunchedProcess(proc2, HOST, PORT)
                    Log.d(TAG, "nvim launched on retry")
                    return@withContext true
                }

                Log.e(TAG, "nvim failed to launch after retry")
                false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch nvim: ${e.message}")
                false
            }
        }
    }

    private fun launchInChroot(fullPath: String): Process {
        val relPath = fullPath.removePrefix(CHROOT_DIR)
        val cmd = "mount -o remount,exec,suid,dev,rw /data 2>/dev/null; " +
                "exec chroot $CHROOT_DIR $relPath --headless --listen $HOST:$PORT " +
                "-c 'set notermguicolors' -c 'highlight Normal ctermbg=NONE'"
        Log.d(TAG, "Launching via chroot: su -c $cmd")
        return Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
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
            killExistingOnPort()
        } catch (_: Exception) {}
        launched = null
    }

    private fun checkPortOpen(host: String, port: Int, timeout: Int = 1000): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeout)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun killExistingOnPort() {
        try {
            val cmds = listOf(
                arrayOf("su", "-c", "fuser -k ${PORT}/tcp 2>/dev/null"),
                arrayOf("su", "-c", "lsof -ti:$PORT 2>/dev/null | xargs kill -9 2>/dev/null"),
                arrayOf("sh", "-c", "fuser -k ${PORT}/tcp 2>/dev/null"),
                arrayOf("sh", "-c", "lsof -ti:$PORT 2>/dev/null | xargs kill -9 2>/dev/null")
            )
            for (cmd in cmds) {
                try {
                    val p = Runtime.getRuntime().exec(cmd)
                    p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
                } catch (_: Exception) {}
            }
            Thread.sleep(300)
        } catch (_: Exception) {}
    }

    private fun findNvim(): String? {
        Log.d(TAG, "--- findNvim ---")

        // 0. Diagnostics: check chroot dir exists
        try {
            val diag = Runtime.getRuntime().exec(arrayOf("su", "-c",
                "echo CHROOT_CHECK:; test -d $CHROOT_DIR && echo 'DIR_EXISTS' || echo 'DIR_MISSING'; test -f $CHROOT_DIR/usr/bin/nvim && echo 'NVIM_EXISTS' || echo 'NVIM_MISSING'; ls $CHROOT_DIR/usr/bin/ 2>/dev/null | head -20"))
            val diagOut = diag.inputStream.bufferedReader().readText().trim()
            diag.waitFor()
            Log.d(TAG, "Diagnostic (su): $diagOut")
            val errDiag = String(ByteArrayOutputStream().also { diag.errorStream.copyTo(it) }.toByteArray())
            if (errDiag.isNotEmpty()) Log.e(TAG, "Diagnostic stderr: $errDiag")
        } catch (e: Exception) {
            Log.e(TAG, "Diagnostic failed: ${e.message}")
        }

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
            Log.d(TAG, "Checking $p -> exists=${f.exists()}, canRead=${f.canRead()}, canExecute=${f.canExecute()}")
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
            val exitCode = proc.waitFor()
            val errOut = try { String(ByteArrayOutputStream().also { proc.errorStream.copyTo(it) }.toByteArray()) } catch (_: Exception) { "" }
            Log.d(TAG, "su test exit=$exitCode out='$out' err='$errOut'")
            if (out == "nvim") {
                Log.d(TAG, "Found nvim via su at $CHROOT_DIR/usr/bin/nvim")
                return "$CHROOT_DIR/usr/bin/nvim"
            }
            if (out == "vim") {
                Log.d(TAG, "Found vim via su at $CHROOT_DIR/usr/bin/vim")
                return "$CHROOT_DIR/usr/bin/vim"
            }
        } catch (e: Exception) {
            Log.e(TAG, "su test exception: ${e.message}")
        }

        // 3. Try which via system shell
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "command -v nvim 2>/dev/null || which nvim 2>/dev/null"))
            val path = proc.inputStream.bufferedReader().readText().trim()
            val exitCode = proc.waitFor()
            Log.d(TAG, "which/command exit=$exitCode path='$path'")
            if (path.isNotEmpty() && File(path).exists()) {
                Log.d(TAG, "Found nvim via shell: $path")
                return path
            }
        } catch (e: Exception) {
            Log.e(TAG, "which exception: ${e.message}")
        }

        Log.e(TAG, "nvim NOT FOUND after all attempts")
        return null
    }
}
