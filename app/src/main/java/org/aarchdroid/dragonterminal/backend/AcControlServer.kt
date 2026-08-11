package org.aarchdroid.dragonterminal.backend

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * API ac — servidor de control AF_UNIX abstracto "ac-webview".
 *
 * La tool del chroot se conecta a este socket y comanda la WebView
 * (el motor del navegador vive en la app). Protocolo por líneas de texto:
 *
 *   tool → app:  open <url>
 *                back | forward | reload | close
 *                mirror on [w] [h] | mirror off
 *
 *   app → tool:  title <text>  |  url <text>  |  loading 0|1
 *
 * Mismo patrón singleton que [FlexAudioServer].
 */
class AcControlServer private constructor() {

    interface Listener {
        fun onOpen(url: String)
        fun onBack()
        fun onForward()
        fun onReload()
        fun onStop()
        fun onExec(js: String)
        fun onQueryUrl()
        fun onQueryTitle()
        fun onStatus()
        fun onScreenshot()
        fun onMirror(enabled: Boolean, w: Int, h: Int)
        fun onClose()
    }

    companion object {
        private const val TAG = "AcCtl"
        private const val SOCKET_NAME = "ac-webview"

        @Volatile
        private var instance: AcControlServer? = null

        fun getInstance(): AcControlServer {
            return instance ?: synchronized(this) {
                instance ?: AcControlServer().also { instance = it }
            }
        }
    }

    /** Lo registra la activity para recibir los comandos de la tool */
    @Volatile
    var listener: Listener? = null

    private val isRunning = AtomicBoolean(false)
    private val clients = CopyOnWriteArrayList<android.net.LocalSocket>()
    private val clientOutputs = ConcurrentHashMap<Int, OutputStream>()
    private val nextClientId = AtomicInteger(0)
    private var serverSocket: LocalServerSocketExt? = null
    private var acceptThread: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Cliente que emitió `open`. Cuando ese cliente se desconecta (p.ej. Ctrl+C
     * en la terminal que lanzó el navegador), éste se cierra.
     */
    private val openerId = AtomicInteger(-1)

    fun start() {
        if (!isRunning.compareAndSet(false, true)) {
            Log.w(TAG, "start() called but already running")
            return
        }
        val t = Thread {
            try {
                Log.w(TAG, "Creating LocalServerSocket on '$SOCKET_NAME'...")
                var bound = false
                for (attempt in 1..10) {
                    try {
                        serverSocket = LocalServerSocketExt(SOCKET_NAME)
                        bound = true
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "Bind retry $attempt: ${e.message}")
                        Thread.sleep(200)
                    }
                }
                if (!bound) {
                    Log.e(TAG, "AcControlServer failed to bind after retries")
                    isRunning.set(false)
                    return@Thread
                }
                Log.w(TAG, "Server listening on abstract socket: $SOCKET_NAME")
                while (isRunning.get()) {
                    val client = serverSocket?.accept() ?: break
                    val connId = nextClientId.getAndIncrement()
                    clients.add(client)
                    try {
                        clientOutputs[connId] = client.outputStream
                    } catch (_: Exception) {}
                    val h = Thread { handleClient(client, connId) }
                    h.name = "AcCtl-$connId"
                    h.start()
                }
            } catch (e: Exception) {
                if (isRunning.get()) Log.e(TAG, "AcControlServer failed: ${e.message}", e)
                isRunning.set(false)
            } finally {
                cleanup()
            }
        }
        t.name = "AcCtlAccept"
        acceptThread = t
        t.start()
    }

    /** Envía una respuesta de texto a todos los clientes conectados. */
    fun sendToAll(command: String) {
        val payload = (command + "\n").toByteArray(Charsets.UTF_8)
        val dead = mutableListOf<Int>()
        for ((id, out) in clientOutputs) {
            try {
                out.write(payload)
                out.flush()
            } catch (e: Exception) {
                dead.add(id)
            }
        }
        for (id in dead) clientOutputs.remove(id)
    }

    fun notifyTitle(title: String) = sendToAll("title $title")
    fun notifyUrl(url: String) = sendToAll("url $url")
    fun notifyLoading(loading: Boolean) = sendToAll("loading ${if (loading) 1 else 0}")
    fun notifyStatus(title: String, url: String, loading: Boolean) {
        notifyTitle(title)
        notifyUrl(url)
        notifyLoading(loading)
    }

    /** Resultado de exec: base64 (evita saltos de línea en el protocolo). */
    fun notifyResult(raw: String) {
        val b64 = android.util.Base64.encodeToString(
            raw.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP
        )
        sendToAll("result $b64")
    }

    /** Captura de pantalla: PNG en base64. */
    fun notifyScreenshot(pngBase64: String) {
        sendToAll("shot $pngBase64")
    }

    private fun dispatch(action: (Listener) -> Unit) {
        mainHandler.post {
            val l = listener
            if (l != null) action(l)
        }
    }

    private fun handleClient(client: android.net.LocalSocket, connId: Int) {
        try {
            val reader = BufferedReader(InputStreamReader(client.inputStream))
            while (isRunning.get()) {
                val line = reader.readLine() ?: break
                val cmd = line.trim()
                Log.d(TAG, "[#$connId] << $cmd")
                when {
                    cmd.startsWith("open ") -> {
                        openerId.set(connId)
                        val url = cmd.substring(5).trim()
                        dispatch { it.onOpen(url) }
                    }
                    cmd == "back" -> dispatch { it.onBack() }
                    cmd == "forward" -> dispatch { it.onForward() }
                    cmd == "reload" -> dispatch { it.onReload() }
                    cmd == "stop" -> dispatch { it.onStop() }
                    cmd == "url" -> dispatch { it.onQueryUrl() }
                    cmd == "title" -> dispatch { it.onQueryTitle() }
                    cmd == "status" -> dispatch { it.onStatus() }
                    cmd == "screenshot" -> dispatch { it.onScreenshot() }
                    cmd.startsWith("exec ") -> {
                        val b64 = cmd.substring(5).trim()
                        val js = try {
                            String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8)
                        } catch (e: Exception) {
                            b64
                        }
                        dispatch { it.onExec(js) }
                    }
                    cmd == "close" -> {
                        openerId.set(-1)
                        dispatch { it.onClose() }
                    }
                    cmd.startsWith("mirror ") -> {
                        val parts = cmd.split(Regex("\\s+"))
                        when {
                            parts.size >= 2 && parts[1] == "on" -> {
                                val w = if (parts.size >= 3) parts[2].toIntOrNull() ?: 0 else 0
                                val h = if (parts.size >= 4) parts[3].toIntOrNull() ?: 0 else 0
                                dispatch { it.onMirror(true, w, h) }
                            }
                            parts.size >= 2 && parts[1] == "off" -> dispatch { it.onMirror(false, 0, 0) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (isRunning.get()) Log.w(TAG, "[#$connId] client error: ${e.message}")
        } finally {
            clientOutputs.remove(connId)
            clients.remove(client)
            try { client.close() } catch (_: Exception) {}
            // La terminal que lanzó el navegador se desconectó (Ctrl+C):
            // cerrar el navegador, igual que el comando `close`.
            if (openerId.get() == connId) {
                openerId.set(-1)
                Log.w(TAG, "[#$connId] opener disconnected -> close browser")
                dispatch { it.onClose() }
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        for (c in clients) {
            try { c.close() } catch (_: Exception) {}
        }
        clients.clear()
        clientOutputs.clear()
        cleanup()
        acceptThread?.interrupt()
        acceptThread = null
    }

    private fun cleanup() {
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }
}
