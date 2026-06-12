package org.aarchdroid.neovim

import android.util.Log
import kotlinx.coroutines.*
import org.msgpack.core.MessagePack
import org.msgpack.core.MessageUnpacker
import org.msgpack.value.Value
import org.msgpack.value.ValueFactory
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer

class NeovimClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 9999
) {
    interface Callback {
        fun onConnected()
        fun onDisconnected()
        fun onRedraw(updates: List<RedrawEvent>)
        fun onError(error: String)
    }

    data class RedrawEvent(
        val name: String,
        val args: List<List<Value>>
    )

    private var socket: Socket? = null
    private var output: OutputStream? = null
    private var requestId = 1
    private var isConnected = false
    private val writeLock = Any()
    private var callback: Callback? = null
    private var readJob: Job? = null
    private var keepAliveJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun setCallback(cb: Callback) {
        callback = cb
    }

    suspend fun connect(timeoutMs: Long = 5000): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress(host, port), timeoutMs.toInt())
                sock.soTimeout = 45000
                socket = sock
                output = sock.getOutputStream()
                isConnected = true
                callback?.onConnected()
                startReader()
                startKeepAlive()
                Log.d("NeovimClient", "Connected to nvim at $host:$port")
                true
            } catch (e: Exception) {
                Log.e("NeovimClient", "Connection failed: ${e.message}")
                callback?.onError("Connection failed: ${e.message}")
                false
            }
        }
    }

    fun disconnect() {
        isConnected = false
        keepAliveJob?.cancel()
        readJob?.cancel()
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        output = null
        callback?.onDisconnected()
    }

    suspend fun request(method: String, vararg args: Any?) {
        withContext(Dispatchers.IO) {
            try {
                synchronized(writeLock) {
                    val packer = MessagePack.newDefaultBufferPacker()
                    val id = requestId++
                    packer.packArrayHeader(4)
                    packer.packInt(0)
                    packer.packInt(id)
                    packer.packString(method)
                    packArgs(packer, args.toList())
                    packer.close()
                    output?.write(packer.toByteArray())
                    output?.flush()
                }
            } catch (e: Exception) {
                Log.e("NeovimClient", "Request failed: ${e.message}")
                callback?.onError("Request failed: ${e.message}")
            }
        }
    }

    suspend fun notify(method: String, vararg args: Any?) {
        withContext(Dispatchers.IO) {
            try {
                synchronized(writeLock) {
                    val packer = MessagePack.newDefaultBufferPacker()
                    packer.packArrayHeader(3)
                    packer.packInt(2)
                    packer.packString(method)
                    packArgs(packer, args.toList())
                    packer.close()
                    output?.write(packer.toByteArray())
                    output?.flush()
                }
            } catch (e: Exception) {
                Log.e("NeovimClient", "Notify failed: ${e.message}")
            }
        }
    }

    suspend fun apiInfo() {
        request("nvim_get_api_info")
    }

    suspend fun uiAttach(width: Int, height: Int) {
        val options = mapOf(
            "rgb" to true,
            "ext_linegrid" to true,
            "ext_multigrid" to false
        )
        request("nvim_ui_attach", width, height, options)
    }

    suspend fun uiDetach() {
        request("nvim_ui_detach")
    }

    suspend fun input(keys: String) {
        request("nvim_input", keys)
    }

    suspend fun command(cmd: String) {
        request("nvim_command", cmd)
    }

    suspend fun eval(expr: String) {
        request("nvim_exec_lua", expr)
    }

    suspend fun openFile(path: String) {
        command("e $path")
    }

    suspend fun setClipboard(text: String) {
        command("let @+ = " + escapeString(text))
    }

    private fun escapeString(s: String): String {
        return "'" + s.replace("'", "'\"'\"'") + "'"
    }

    private fun packArgs(packer: org.msgpack.core.MessagePacker, args: List<Any?>) {
        packer.packArrayHeader(args.size)
        for (arg in args) {
            when (arg) {
                null -> packer.packNil()
                is Int -> packer.packInt(arg)
                is Long -> packer.packLong(arg)
                is String -> packer.packString(arg)
                is Boolean -> packer.packBoolean(arg)
                is Map<*, *> -> {
                    packer.packMapHeader(arg.size)
                    for ((k, v) in arg) {
                        packer.packString(k.toString())
                        packValue(packer, v)
                    }
                }
                is List<*> -> {
                    packer.packArrayHeader(arg.size)
                    for (v in arg) packValue(packer, v)
                }
                else -> packer.packString(arg.toString())
            }
        }
    }

    private fun packValue(packer: org.msgpack.core.MessagePacker, value: Any?) {
        when (value) {
            null -> packer.packNil()
            is Int -> packer.packInt(value)
            is Long -> packer.packLong(value)
            is String -> packer.packString(value)
            is Boolean -> packer.packBoolean(value)
            is Map<*, *> -> {
                packer.packMapHeader(value.size)
                for ((k, v) in value) {
                    packer.packString(k.toString())
                    packValue(packer, v)
                }
            }
            is List<*> -> {
                packer.packArrayHeader(value.size)
                for (v in value) packValue(packer, v)
            }
            else -> packer.packString(value.toString())
        }
    }

    private fun startReader() {
        readJob = scope.launch {
            val buf = ByteArray(8192)
            val tmp = ByteBuffer.allocate(65536)

            while (isConnected) {
                try {
                    val sock = socket ?: break
                    val n = sock.inputStream.read(buf)
                    if (n < 0) {
                        Log.d("NeovimClient", "Socket closed")
                        callback?.onDisconnected()
                        break
                    }

                    tmp.put(buf, 0, n)
                    tmp.flip()

                    while (tmp.remaining() > 0) {
                        tmp.mark()
                        try {
                            val unpacker = MessagePack.newDefaultUnpacker(ByteBufferInputStream(tmp))
                            val value = unpacker.unpackValue()
                            unpacker.close()
                            handleMessage(value)
                        } catch (e: org.msgpack.core.MessageInsufficientBufferException) {
                            tmp.reset()
                            break
                        } catch (e: Exception) {
                            Log.w("NeovimClient", "Parse error: ${e.message}")
                            tmp.reset()
                            break
                        }
                    }
                    tmp.compact()
                } catch (e: SocketTimeoutException) {
                    // Expected between messages — continue reading
                    continue
                } catch (e: Exception) {
                    if (isConnected) {
                        Log.e("NeovimClient", "Read error: ${e.message}")
                        callback?.onError("Read error: ${e.message}")
                    }
                    break
                }
            }
        }
    }

    private fun startKeepAlive() {
        keepAliveJob = scope.launch {
            while (isConnected) {
                delay(15000)
                try {
                    request("nvim_get_mode")
                } catch (_: Exception) {}
            }
        }
    }

    private fun handleMessage(value: Value) {
        if (value.isArrayValue) {
            val array = value.asArrayValue()
            val elements = array.list()
            if (elements.size >= 2) {
                val type = elements[0].asIntegerValue().toInt()
                when (type) {
                    1 -> handleResponse(elements)
                    2 -> handleNotification(elements)
                    3 -> handleNotification(elements)
                }
            }
        }
    }

    private fun handleResponse(elements: List<Value>) {
        if (elements.size >= 4) {
            val id = elements[1].asIntegerValue().toInt()
            val err = elements[2]
            val result = elements[3]
            Log.d("NeovimClient", "Response #$id: error=$err, result=$result")
        }
    }

    private var redrawCounter = 0
    private var logRawEvents = true

    private fun handleNotification(elements: List<Value>) {
        if (elements.size >= 3) {
            val method = elements[1].asStringValue().asString()
            val params = elements[2]

            when (method) {
                "redraw" -> {
                    if (params.isArrayValue) {
                        val rawList = params.asArrayValue().list()
                        val updates = rawList.map { event ->
                            val eventArr = event.asArrayValue()
                            val name = eventArr.list()[0].asStringValue().asString()
                            // Cada arg msgpack es su propio event.args[i]
                            val evtArgs = mutableListOf<List<Value>>()
                            for (i in 1 until eventArr.list().size) {
                                val v = eventArr.list()[i]
                                if (v.isArrayValue) {
                                    evtArgs.add(v.asArrayValue().list())
                                } else {
                                    evtArgs.add(listOf(v))
                                }
                            }
                            RedrawEvent(name, evtArgs)
                        }
                        // Log raw structure for first 5 redraw batches
                        if (logRawEvents && redrawCounter < 5) {
                            redrawCounter++
                            val eventNames = updates.map { it.name }
                            Log.d("NeovimClient", "redraw #$redrawCounter events=$eventNames")
                            for (ev in updates.take(3)) {
                                val argTypes = ev.args.flatten().map { v ->
                                    when {
                                        v.isArrayValue -> "Array(${v.asArrayValue().list().size})"
                                        v.isStringValue -> "Str"
                                        v.isIntegerValue -> "Int"
                                        v.isBooleanValue -> "Bool"
                                        v.isNilValue -> "Nil"
                                        v.isFloatValue -> "Float"
                                        else -> "Other"
                                    }
                                }
                                Log.d("NeovimClient", "  ${ev.name} args=${ev.args.size} types=$argTypes")
                                if (ev.name == "grid_line" && ev.args.isNotEmpty()) {
                                    val firstLine = ev.args[0]
                                    if (firstLine.size >= 4) {
                                        try {
                                            val cellsVal = firstLine[3]
                                            Log.d("NeovimClient", "    grid_line grid=${firstLine[0]} row=${firstLine[1]} colStart=${firstLine[2]} cellsType=${
                                                if (cellsVal.isArrayValue) "Array(${cellsVal.asArrayValue().list().size})" else cellsVal
                                            }")
                                            if (cellsVal.isArrayValue) {
                                                val celist = cellsVal.asArrayValue().list()
                                                celist.take(5).forEachIndexed { idx, c ->
                                                    Log.d("NeovimClient", "      cell[$idx]=${
                                                        if (c.isStringValue) "Str(${c.asStringValue().asString()})"
                                                        else if (c.isArrayValue) "Arr(${c.asArrayValue().list()})"
                                                        else c
                                                    }")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.d("NeovimClient", "    grid_line parse error: ${e.message}")
                                        }
                                    }
                                }
                            }
                        }
                        callback?.onRedraw(updates)
                    }
                }
                "mode_info_set" -> {
                    Log.d("NeovimClient", "Mode info set: $params")
                }
                "option_set" -> {
                    Log.d("NeovimClient", "Option set: $params")
                }
            }
        }
    }
}

private class ByteBufferInputStream(private val buffer: ByteBuffer) : java.io.InputStream() {
    override fun read(): Int {
        if (!buffer.hasRemaining()) return -1
        return buffer.get().toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val available = buffer.remaining()
        if (available == 0) return -1
        val toRead = minOf(available, len)
        buffer.get(b, off, toRead)
        return toRead
    }

    override fun available(): Int = buffer.remaining()
}

