package org.aarchdroid.dragonterminal.frontend.web

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import org.aarchdroid.R
import org.aarchdroid.dragonterminal.backend.AcControlServer

/**
 * API ac — navegador en overlay.
 *
 * Ventana que embebe un WebView (Chromium real de Android: HTML5, CSS, JS y
 * video con decodificación por hardware) junto a una barra de URL flotante.
 * Es la fase "motor" del navegador: el motor pesado vive aquí, en la
 * app, y las tools del chroot lo controlan via el socket [AcControlServer].
 *
 * Cuando se usa como ventana flotante (directa sobre terminal_container):
 *  - barra superior: icono de redimensionar (arrastrar ajusta el tamaño;
 *    hacia arriba-izquierda achica, abajo-derecha agranda) + campo URL + "✕"
 *    (limpia y abre el teclado) + ir,
 *  - barra inferior delgada: "◀", "▶", "–", "⛶", recargar y a la derecha del
 *    todo la agarradera para mover la ventana.
 *
 * No hay botón de cerrar: el navegador se cierra con el comando `close` de la
 * tool o cuando la terminal que lo lanzó (Ctrl+C) se desconecta.
 */
class AcWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var userAgent: String

    var onMinimize: (() -> Unit)? = null
    var onExpand: (() -> Unit)? = null

    private var dragX = 0f
    private var dragY = 0f
    private var startW = 0
    private var startH = 0

    init {
        setBackgroundColor(Color.BLACK)

        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.mediaPlaybackRequiresUserGesture = false
            // Multi-ventana habilitado para poder interceptar y bloquear
            // popups (window.open / target=_blank) desde onCreateWindow.
            settings.setSupportMultipleWindows(true)
            setBackgroundColor(Color.BLACK)
            webViewClient = createClient()
            webChromeClient = object : WebChromeClient() {
                // Bloquea ventanas emergentes (window.open / target=_blank).
                override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean = false
            }
        }
        userAgent = webView.settings.userAgentString
        val webParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        addView(webView, webParams)

        val bar = createToolbar(context)
        val barParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dp(48)
        )
        barParams.gravity = Gravity.TOP
        addView(bar, barParams)

        val sec = createSecondaryBar(context)
        val secParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dp(36)
        )
        secParams.gravity = Gravity.BOTTOM
        addView(sec, secParams)

        // WebView en medio: debajo de la barra superior, encima de la inferior
        webParams.topMargin = dp(48)
        webParams.bottomMargin = dp(36)
    }

    private fun createClient(): WebViewClient {
        return object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (url != null) urlBar.setText(url)
                AcControlServer.getInstance().notifyUrl(url ?: "")
                AcControlServer.getInstance().notifyLoading(true)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (url != null) urlBar.setText(url)
                val t = view.title ?: ""
                AcControlServer.getInstance().notifyTitle(t)
                AcControlServer.getInstance().notifyUrl(url ?: "")
                AcControlServer.getInstance().notifyLoading(false)
                injectAdFiltering()
            }

            // Bloqueo de anuncios por red + reescritura del HTML de YouTube
            // (poda de respuesta estilo uBlock/Brave: el anuncio nunca se agenda).
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                if (AcAdBlocker.shouldBlock(url)) return blockedResponse()
                if (request.isForMainFrame && AcAdBlocker.isYouTubeHost(request.url.host)) {
                    rewrittenYouTubeDocument(view, url)?.let { return it }
                }
                return null
            }
        }
    }

    private fun blockedResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))

    /**
     * Re-fetchea el HTML del frame principal de YouTube, le aplica los
     * reemplazos de uBlock (adPlacements→no_ads) e inyecta el script de poda
     * en el `<head>` (document-start, ANTES del bundle de YouTube).
     */
    private fun rewrittenYouTubeDocument(view: WebView, url: String): WebResourceResponse? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", userAgent)
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                conn.setRequestProperty("Accept-Encoding", "identity")
                val cookies = CookieManager.getInstance().getCookie(url)
                if (!cookies.isNullOrEmpty()) conn.setRequestProperty("Cookie", cookies)
                val status = conn.responseCode
                val enc = conn.getHeaderField("Content-Encoding")
                if (status != HttpURLConnection.HTTP_OK) return null
                var input = conn.inputStream
                if ("gzip".equals(enc, ignoreCase = true)) input = GZIPInputStream(input)
                val html = input.readBytes().toString(Charsets.UTF_8)
                val rewritten = AcAdBlocker.youtubeHtmlReplacements(html)
                    .let { injectHeadScript(it, AcAdBlocker.youtubePruneScriptTag()) }
                android.util.Log.d("AcCtl", "[rewrite] status=$status html=${html.length}B -> ${rewritten.length}B adsPruned=${html.contains("adPlacements")}")
                WebResourceResponse(
                    "text/html", "utf-8",
                    ByteArrayInputStream(rewritten.toByteArray(Charsets.UTF_8))
                )
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.e("AcCtl", "[rewrite] ERROR: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** Inserta [script] justo después de la apertura de `<head>` (o al inicio). */
    private fun injectHeadScript(html: String, script: String): String {
        val head = html.indexOf("<head")
        if (head >= 0) {
            val gt = html.indexOf('>', head)
            if (gt >= 0) return html.substring(0, gt + 1) + script + html.substring(gt + 1)
        }
        return script + html
    }

    /** Inyecta el filtrado cosmético (CSS) + scriptlet de saltar anuncios. */
    private fun injectAdFiltering() {
        val js = "(function(){if(document.getElementById('ac-adblock'))return;" +
            "var s=document.createElement('style');s.id='ac-adblock';" +
            "s.textContent=" + jsStr(AcAdBlocker.cosmeticCss()) + ";" +
            "(document.head||document.documentElement).appendChild(s);" +
            AcAdBlocker.youtubeSkipJs() + "})();"
        webView.evaluateJavascript(js, null)
    }

    /** String literal JS a partir de un texto arbitrario. */
    private fun jsStr(s: String): String {
        val b = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '\\' -> b.append("\\\\")
                '"' -> b.append("\\\"")
                '\n' -> b.append("\\n")
                '\r' -> b.append("\\r")
                else -> b.append(c)
            }
        }
        return b.append("\"").toString()
    }

    private fun createToolbar(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF10141A.toInt())

            // Arrastrar el fondo de la barra mueve la ventana flotante
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dragX = e.rawX
                        dragY = e.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - dragX).toInt()
                        val dy = (e.rawY - dragY).toInt()
                        if (dx != 0 || dy != 0) moveBy(dx, dy)
                        dragX = e.rawX
                        dragY = e.rawY
                        true
                    }
                    else -> true
                }
            }

            addView(createResizeHandle())

            val bar = EditText(context).apply {
                isSingleLine = true
                hint = "https://..."
                setTextColor(Color.WHITE)
                setHintTextColor(0xFF888888.toInt())
                textSize = 13f
                background = null
                imeOptions = EditorInfo.IME_ACTION_GO
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_GO) {
                        loadUrl(text?.toString() ?: "")
                        true
                    } else false
                }
            }
            urlBar = bar
            addView(bar, LinearLayout.LayoutParams(0, dp(40), 1f))

            addView(iconButton(R.drawable.ic_ac_clear, "Limpiar URL") {
                urlBar.setText("")
                urlBar.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(urlBar, InputMethodManager.SHOW_IMPLICIT)
            })
            addView(iconButton(R.drawable.ic_ac_send, "Ir") {
                loadUrl(urlBar.text?.toString() ?: "")
            })
        }
    }

    private fun createSecondaryBar(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF0C1016.toInt())
            addView(iconButton(R.drawable.ic_ac_back, "Atrás", onClick = { goBack() }, compact = true))
            addView(iconButton(R.drawable.ic_ac_forward, "Adelante", onClick = { goForward() }, compact = true))
            addView(iconButton(R.drawable.ic_ac_min, "Minimizar", onClick = { onMinimize?.invoke() }, compact = true))
            addView(iconButton(R.drawable.ic_ac_expand, "Expandir", onClick = { onExpand?.invoke() }, compact = true))
            addView(iconButton(R.drawable.ic_ac_refresh, "Recargar", onClick = { reload() }, compact = true))
            addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            addView(createMoveGrip())
        }
    }

    private fun createMoveGrip(): ImageView {
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_ac_move)
            setColorFilter(0xFF39FF14.toInt(), PorterDuff.Mode.SRC_IN)
            contentDescription = "Mover ventana"
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dragX = e.rawX
                        dragY = e.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - dragX).toInt()
                        val dy = (e.rawY - dragY).toInt()
                        if (dx != 0 || dy != 0) moveBy(dx, dy)
                        dragX = e.rawX
                        dragY = e.rawY
                        true
                    }
                    else -> true
                }
            }
        }
    }

    private fun createResizeHandle(): ImageView {
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_ac_resize)
            setColorFilter(0xFF39FF14.toInt(), PorterDuff.Mode.SRC_IN)
            contentDescription = "Redimensionar"
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        // El layoutParams que cambiamos es el de la ventana
                        // (AcWebView), no el de este icono.
                        val lp = this@AcWebView.layoutParams as? FrameLayout.LayoutParams
                            ?: return@setOnTouchListener false
                        if (lp.width == FrameLayout.LayoutParams.MATCH_PARENT ||
                            lp.height == FrameLayout.LayoutParams.MATCH_PARENT
                        ) {
                            return@setOnTouchListener true
                        }
                        dragX = e.rawX
                        dragY = e.rawY
                        startW = lp.width
                        startH = lp.height
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dw = (e.rawX - dragX).toInt()
                        val dh = (e.rawY - dragY).toInt()
                        if (dw != 0 || dh != 0) resizeBy(dw, dh)
                        dragX = e.rawX
                        dragY = e.rawY
                        true
                    }
                    else -> true
                }
            }
        }
    }

    private fun moveBy(dx: Int, dy: Int) {
        val lp = layoutParams as? FrameLayout.LayoutParams ?: return
        lp.leftMargin += dx
        lp.topMargin += dy
        layoutParams = lp
    }

    private fun resizeBy(dw: Int, dh: Int) {
        val lp = layoutParams as? FrameLayout.LayoutParams ?: return
        val dm = resources.displayMetrics
        val newW = (startW + dw).coerceIn(dp(280), dm.widthPixels)
        val newH = (startH + dh).coerceIn(dp(240), dm.heightPixels)
        if (newW != lp.width || newH != lp.height) {
            lp.width = newW
            lp.height = newH
            layoutParams = lp
        }
    }

    private fun iconButton(
        resId: Int,
        description: String,
        compact: Boolean = false,
        onClick: () -> Unit
    ): ImageView {
        return ImageView(context).apply {
            setImageResource(resId)
            setColorFilter(0xFF39FF14.toInt(), PorterDuff.Mode.SRC_IN)
            contentDescription = description
            val padV = if (compact) dp(4) else dp(8)
            setPadding(dp(10), padV, dp(10), padV)
            setOnClickListener { onClick() }
        }
    }

    fun loadUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            // Sin URL: página de inicio (comportamiento Firefox).
            val u = "https://www.google.com"
            urlBar.setText(u)
            webView.loadUrl(u)
            return
        }
        val u = if (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
            trimmed.startsWith("file://") || trimmed.startsWith("about:")) trimmed
        else if (isLikelyUrl(trimmed)) "https://$trimmed"
        else "https://www.google.com/search?q=${Uri.encode(trimmed)}"
        urlBar.setText(u)
        webView.loadUrl(u)
    }

    /** true si el texto parece un dominio (sin espacios y con extensión TLD). */
    private fun isLikelyUrl(text: String): Boolean {
        if (text.any { it.isWhitespace() }) return false
        val lastDot = text.lastIndexOf('.')
        if (lastDot <= 0 || lastDot == text.length - 1) return false
        return text.substring(lastDot + 1).length in 2..6
    }

    fun goBack() {
        if (webView.canGoBack()) webView.goBack()
    }

    fun goForward() {
        if (webView.canGoForward()) webView.goForward()
    }

    fun reload() {
        webView.reload()
    }

    fun stopLoading() {
        webView.stopLoading()
    }

    /**
     * Ejecuta JS en la página (code ya decodificado). El resultado de
     * evaluateJavascript (JSON-encoded) se entrega en [callback].
     */
    fun execJs(code: String, callback: (String) -> Unit) {
        post {
            webView.evaluateJavascript(code, object : android.webkit.ValueCallback<String> {
                override fun onReceiveValue(value: String?) {
                    callback(value ?: "null")
                }
            })
        }
    }

    fun currentUrl(): String = webView.url ?: ""

    fun currentTitle(): String = webView.title ?: ""

    /**
     * Captura el viewport de la WebView como PNG en base64. Fuerza una capa
     * software temporal (la aceleración HW no dibuja en canvas de software) y
     * comprime en un hilo de fondo. callback("") si no hay tamaño.
     */
    fun captureScreenshot(callback: (String) -> Unit) {
        post {
            val w = webView.width
            val h = webView.height
            if (w <= 0 || h <= 0) {
                callback("")
                return@post
            }
            val prevLayer = webView.layerType
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            webView.postDelayed({
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                webView.draw(canvas)
                webView.setLayerType(prevLayer, null)
                Thread {
                    try {
                        val baos = java.io.ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
                        val b64 = android.util.Base64.encodeToString(
                            baos.toByteArray(), android.util.Base64.NO_WRAP
                        )
                        callback(b64)
                    } catch (e: Exception) {
                        android.util.Log.w("AcShot", "error: ${e.message}")
                        callback("")
                    }
                }.start()
            }, 150)
        }
    }

    // ── Lifecycle (delegado desde AcTab / NeoTermActivity) ────
    fun pauseWebView() {
        webView.onPause()
    }

    fun resumeWebView() {
        webView.onResume()
    }

    fun destroyWebView() {
        webView.destroy()
    }

    private fun dp(v: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
