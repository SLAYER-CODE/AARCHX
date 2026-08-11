package org.aarchdroid.dragonterminal.ui.term.tab

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import de.mrapp.android.tabswitcher.Tab
import de.mrapp.android.tabswitcher.TabSwitcher
import de.mrapp.android.tabswitcher.TabSwitcherDecorator
import de.mrapp.android.tabswitcher.R as TabSwitcherR
import org.aarchdroid.AArchDroidApp
import org.aarchdroid.dragonterminal.Globals
import org.aarchdroid.dragonterminal.NeoGLView
import org.aarchdroid.R
import org.aarchdroid.dragonterminal.component.colorscheme.ColorSchemeComponent
import org.aarchdroid.dragonterminal.frontend.completion.listener.OnAutoCompleteListener
import org.aarchdroid.dragonterminal.frontend.component.ComponentManager
import org.aarchdroid.dragonterminal.frontend.config.DefaultValues
import org.aarchdroid.dragonterminal.frontend.config.NeoPreference
import org.aarchdroid.dragonterminal.frontend.session.shell.client.TermCompleteListener
import org.aarchdroid.dragonterminal.backend.AcControlServer
import org.aarchdroid.dragonterminal.backend.CameraControlServer
import org.aarchdroid.dragonterminal.backend.CanvasSocketServer
import org.aarchdroid.dragonterminal.backend.FlexAudioServer
import org.aarchdroid.dragonterminal.backend.MicServer
import org.aarchdroid.dragonterminal.backend.TerminalSession
import org.aarchdroid.dragonterminal.frontend.terminal.CanvasOverlayView
import org.aarchdroid.dragonterminal.frontend.terminal.TerminalView
import org.aarchdroid.dragonterminal.frontend.terminal.extrakey.ExtraKeysView
import org.aarchdroid.dragonterminal.ui.term.NeoTermActivity
import org.aarchdroid.dragonterminal.utils.TerminalUtils
import de.mrapp.android.tabswitcher.SwipeAnimation

/**
 * @author kiva
 */
class NeoTabDecorator(val context: NeoTermActivity) : TabSwitcherDecorator() {
    companion object {
        private var VIEW_TYPE_COUNT = 0
        private val VIEW_TYPE_TERM = VIEW_TYPE_COUNT++
        private val VIEW_TYPE_X = VIEW_TYPE_COUNT++
        private val VIEW_TYPE_CANVAS = VIEW_TYPE_COUNT++
        private val VIEW_TYPE_WEB = VIEW_TYPE_COUNT++

        @Volatile
        private var cameraControlServer: CameraControlServer? = null

        fun startCameraServer(context: Context) {
            if (cameraControlServer != null) return
            CameraControlServer(context.applicationContext).also {
                cameraControlServer = it
                it.start()
            }
        }

        fun retryCamera() {
            cameraControlServer?.retryCamera()
        }

        fun stopCameraServer() {
            cameraControlServer?.stop()
            cameraControlServer = null
        }
    }

    private fun setViewLayerType(view: View?) = view?.setLayerType(View.LAYER_TYPE_NONE, null)

    override fun onInflateView(inflater: LayoutInflater, parent: ViewGroup?, viewType: Int): View {
        return when (viewType) {
            VIEW_TYPE_TERM -> {
                val view = inflater.inflate(R.layout.ui_term, parent, false)
                val terminalView = view.findViewById<TerminalView>(R.id.terminal_view)
                val extraKeysView = view.findViewById<ExtraKeysView>(R.id.extra_keys)
                TerminalUtils.setupTerminalView(terminalView)
                TerminalUtils.setupExtraKeysView(extraKeysView)

                val colorSchemeManager = ComponentManager.getComponent<ColorSchemeComponent>()
                colorSchemeManager.applyColorScheme(terminalView, extraKeysView,
                        colorSchemeManager.getCurrentColorScheme())
                view
            }

            VIEW_TYPE_X -> {
                inflater.inflate(R.layout.ui_xorg, parent, false)
            }

            VIEW_TYPE_CANVAS -> {
                FrameLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(Color.BLACK)
                }
            }

            VIEW_TYPE_WEB -> {
                FrameLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(Color.BLACK)
                }
            }

            else -> {
                android.util.Log.w("NeoTabDecor", "Unknown view type: $viewType, inflating term")
                inflater.inflate(R.layout.ui_term, parent, false)
            }
        }
    }

    override fun onShowTab(context: Context, tabSwitcher: TabSwitcher,
                           view: View, tab: Tab, index: Int, viewType: Int, savedInstanceState: Bundle?) {
        android.util.Log.d("NeoTabDecor", "onShowTab idx=$index viewType=$viewType shown=${tabSwitcher.isSwitcherShown}")

        val toolbar = this@NeoTabDecorator.context.toolbar
        toolbar.title = if (tabSwitcher.isSwitcherShown) null else tab.title

        val isQuickPreview = tabSwitcher.selectedTabIndex != index

        when (viewType) {
            VIEW_TYPE_TERM -> {
                // Restore toolbar when leaving canvas
                toolbar.visibility = View.VISIBLE
                if (tab !is TermTab) return
                val termTab = tab
                termTab.toolbar = toolbar
                val terminalView =  findViewById<TerminalView>(R.id.terminal_view)
                if (isQuickPreview || tabSwitcher.isSwitcherShown) {
                    view.findViewById<ExtraKeysView>(R.id.extra_keys)?.visibility = View.GONE
                    bindTerminalView(termTab, terminalView, null, view)
                } else {
                    val extraKeysView = view.findViewById<ExtraKeysView>(R.id.extra_keys)
                    extraKeysView?.visibility = View.VISIBLE
                    extraKeysView?.tabCount = tabSwitcher.count
                    bindTerminalView(termTab, terminalView, extraKeysView, view)
                    terminalView.requestFocus()
                }

                val session = termTab.termData.termSession
                val childContainer = view.parent as? ViewGroup
                val rootLayout = childContainer?.parent as? ViewGroup
                val titleContainer = rootLayout?.getChildAt(0) as? ViewGroup
                if (titleContainer != null) {
                    Log.d("NeoTabDecor", "titleContainer found")
                    var floatBtn = titleContainer.findViewWithTag<TextView>("float_button_tag")
                    if (floatBtn == null) {
                        Log.d("NeoTabDecor", "creating float button")
                        floatBtn = TextView(context)
                        floatBtn.tag = "float_button_tag"
                        floatBtn.text = "⬈"
                        floatBtn.setTextColor(Color.parseColor("#FF08FF00"))
                        floatBtn.textSize = 18f
                        floatBtn.gravity = Gravity.CENTER
                        floatBtn.layoutParams = LinearLayout.LayoutParams(
                            context.resources.getDimensionPixelSize(TabSwitcherR.dimen.tab_title_container_height),
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        floatBtn.contentDescription = context.getString(R.string.float_up)
                        val ta = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
                        floatBtn.background = ta.getDrawable(0)
                        ta.recycle()
                        val closeBtn = titleContainer.findViewById<View>(TabSwitcherR.id.close_tab_button)
                        val closeIdx = closeBtn?.let { titleContainer.indexOfChild(it) } ?: titleContainer.childCount
                        titleContainer.addView(floatBtn, closeIdx)
                        Log.d("NeoTabDecor", "float button added at index $closeIdx, childCount=${titleContainer.childCount}")
                    } else {
                        Log.d("NeoTabDecor", "float button reused")
                    }
                    if (tabSwitcher.isSwitcherShown && session != null) {
                        floatBtn.visibility = View.VISIBLE
                        Log.d("NeoTabDecor", "float button VISIBLE for handle=${session.mHandle}")
                        floatBtn.setOnClickListener {
                            Log.d("NeoTabDecor", "float button clicked, handle=${session.mHandle}")
                            val act = this@NeoTabDecorator.context
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(act)) {
                                act.pendingFloatHandle = session.mHandle
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${act.packageName}")
                                )
                                act.startActivity(intent)
                                return@setOnClickListener
                            }
                            act.transferringHandle = session.mHandle
                            tabSwitcher.removeTab(tab)
                        }
                    } else {
                        floatBtn.visibility = View.GONE
                        floatBtn.setOnClickListener(null)
                    }

                    // Red indicator when overlay is in fullscreen
                    val ctx = this@NeoTabDecorator.context
                    val container = ctx.findViewById<FrameLayout>(R.id.terminal_container)
                    if (container != null && session != null) {
                        val fullscreenOv = (0 until container.childCount)
                            .map { idx -> container.getChildAt(idx) }
                            .filterIsInstance<CanvasOverlayView>()
                            .firstOrNull { ov -> ov.isFullscreen && ov.overlaySession === session }
                        // Red indicator when overlay is in fullscreen
                    // (now by checking if a CanvasTab has isFullscreen)

                    // Exit any canvas fullscreen ONLY when a terminal tab is selected.
                    // If tab is a CanvasTab, do NOT exit — the block above already
                    // entered fullscreen for it.  Exiting here would immediately
                    // undo that, which is the root cause of the black-screen bug.
                    // Also restore a lost CanvasTab on activity resume.
                    if (!tabSwitcher.isSwitcherShown && !isQuickPreview && tab !is CanvasTab) {
                        var foundCanvas = false
                        for (i in 0 until tabSwitcher.count) {
                            val tab = tabSwitcher.getTab(i)
                            if (tab is CanvasTab) {
                                foundCanvas = true
                                val ov = tab.overlayView
                                // Only exit if overlay is in container (stale fullscreen state).
                                // If overlay is inside the CanvasTab's view hierarchy, it's
                                // properly in fullscreen — don't undo it.
                                if (ov.isFullscreen && ov.parent == container) {
                                    ov.exitFullscreenTab()
                                    ov.onToggleFullscreen?.invoke(false)
                                }
                            }
                        }
                        // Restore CanvasTab if overlay thinks it's fullscreen but tab is gone
                        if (!foundCanvas && CanvasOverlayView.wasFullscreen) {
                            for (i in 0 until container.childCount) {
                                val ov = container.getChildAt(i)
                                if (ov is CanvasOverlayView && ov.isFullscreen) {
                                    ov.onToggleFullscreen?.invoke(true)
                                    break
                                }
                            }
                        }
                    }
                    }
                } else {
                    Log.d("NeoTabDecor", "titleContainer NOT found — childContainer=$childContainer rootLayout=$rootLayout")
                }
            }

            VIEW_TYPE_X -> {
                toolbar.visibility = View.GONE
                bindXSessionView(tab as XSessionTab)
            }

            VIEW_TYPE_CANVAS -> {
                val canvasTab = tab as CanvasTab
                val ov = canvasTab.overlayView
                // Always move overlay into the tab's content view
                if (ov.parent != view) {
                    (ov.parent as? ViewGroup)?.removeView(ov)
                    (view as? ViewGroup)?.addView(ov, ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ))
                }
                ov.enterFullscreenTab()
            }

            VIEW_TYPE_WEB -> {
                val webTab = tab as AcTab
                val wv = webTab.webView
                // Move the browser into the tab's content view
                if (wv.parent != view) {
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    (view as? ViewGroup)?.addView(wv, ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ))
                }
            }
        }
    }

    private fun bindXSessionView(tab: XSessionTab) {
        val sessionData = tab.sessionData ?: return

        if (sessionData.videoLayout == null) {
            val videoLayout = findViewById<FrameLayout>(R.id.xorg_video_layout)
            sessionData.videoLayout = videoLayout
            setViewLayerType(videoLayout)
        }

        val videoLayout = sessionData.videoLayout!!

        if (sessionData.glView == null) {
            val client = sessionData.client ?: return
            Thread {
                client.runOnUiThread {
                    sessionData.glView = NeoGLView(client)
                    sessionData.glView?.isFocusableInTouchMode = true
                    sessionData.glView?.isFocusable = true
                    sessionData.glView?.requestFocus()

                    setViewLayerType(sessionData.glView)
                    videoLayout.addView(sessionData.glView,
                            FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT))

                    if (Globals.HideSystemMousePointer
                            && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        sessionData.glView?.pointerIcon =
                                android.view.PointerIcon.getSystemIcon(context,
                                        android.view.PointerIcon.TYPE_NULL)
                    }

                    val r = Rect()
                    videoLayout.getWindowVisibleDisplayFrame(r)
                    sessionData.glView?.callNativeScreenVisibleRect(r.left, r.top, r.right, r.bottom)
                    videoLayout.viewTreeObserver.addOnGlobalLayoutListener({
                        val r = Rect()
                        videoLayout.getWindowVisibleDisplayFrame(r)
                        val heightDiff = videoLayout.rootView.height - videoLayout.height // Take system bar into consideration
                        val widthDiff = videoLayout.rootView.width - videoLayout.width // Nexus 5 has system bar at the right side
                        Log.v("SDL", "Main window visible region changed: " + r.left + ":" + r.top + ":" + r.width() + ":" + r.height())
                        videoLayout.postDelayed({
                            sessionData.glView?.callNativeScreenVisibleRect(r.left + widthDiff, r.top + heightDiff, r.width(), r.height())
                        }, 300)
                        videoLayout.postDelayed({
                            sessionData.glView?.callNativeScreenVisibleRect(r.left + widthDiff, r.top + heightDiff, r.width(), r.height())
                        }, 600)
                    })
                }
            }.start()
        }
    }

    private fun bindTerminalView(tab: TermTab, view: TerminalView?,
                                 extraKeysView: ExtraKeysView?,
                                 rootView: View? = null) {
        val termView = view ?: return
        val termData = tab.termData

        termData.initializeViewWith(tab, termView, extraKeysView)
        termView.setEnableWordBasedIme(termData.profile?.enableWordBasedIme ?: DefaultValues.enableWordBasedIme)
        termView.setCursorBlinkEnabled(NeoPreference.isCursorBlinkEnabled())
        termView.setTerminalViewClient(termData.viewClient)
        termView.attachSession(termData.termSession)

        // Wire Canvas overlay — singleton server, global multi-overlay container
        val session = termData.termSession
        if (session != null) {
            val socketServer = CanvasSocketServer.getInstance()

            // onNewConnection: cada conexión obtiene su propio overlay view.
            // Se resuelve la activity VIVA al momento de la conexión (holder
            // en NeoTermActivity) en vez de capturar `context` aquí: si la
            // activity se recrea sin que onShowTab re-corra, un ctx capturado
            // apuntaría a una jerarquía muerta y el overlay quedaría invisible.
            socketServer.onNewConnection = lambda@{ connId ->
                val act = NeoTermActivity.currentNeoTermActivity ?: return@lambda null
                val latch = java.util.concurrent.CountDownLatch(1)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    val container = act.findViewById<FrameLayout>(R.id.terminal_container) ?: return@post
                    val tag = "overlay_$connId"
                    var v = container.findViewWithTag<CanvasOverlayView>(tag)
                    if (v == null) {
                        v = CanvasOverlayView(act)
                        container.addView(v, FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        ))
                        v.initialScale = 1f
                        v.initialOffsetX = 20f
                        v.initialOffsetY = 20f
                        v.tag = tag
                        v.onToggleFullscreen = { enter ->
                            val ts = act.findViewById<TabSwitcher>(R.id.tab_switcher)
                            val container = act.findViewById<FrameLayout>(R.id.terminal_container)
                            if (enter) {
                                val imm = act.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                imm.hideSoftInputFromWindow(act.window?.decorView?.windowToken, 0)
                                act.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

                                val ct = CanvasTab("Canvas", v)
                                ts.addTab(ct, 0, SwipeAnimation.Builder().create())
                                ts.selectTab(ct)
                            } else {
                                if (v.parent != container) {
                                    (v.parent as? ViewGroup)?.removeView(v)
                                    container?.addView(v, FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT
                                    ))
                                }
                                var wasSelected = false
                                for (i in 0 until ts.count) {
                                    val tab = ts.getTab(i)
                                    if (tab is CanvasTab && tab.overlayView === v) {
                                        wasSelected = ts.selectedTab === tab
                                        ts.removeTab(tab)
                                        break
                                    }
                                }
                                if (wasSelected) {
                                    for (i in 0 until ts.count) {
                                        val tab = ts.getTab(i)
                                        if (tab is TermTab) {
                                            ts.selectTab(tab)
                                            break
                                        }
                                    }
                                }
                                container?.postInvalidate()
                            }
                        }
                    }
                    v.overlaySession = session
                    v.connId = connId
                    latch.countDown()
                }
                latch.await()
                val container = act.findViewById<FrameLayout>(R.id.terminal_container)
                val ov = container?.findViewWithTag<CanvasOverlayView>("overlay_$connId") ?: return@lambda null
                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                object : CanvasSocketServer.CanvasFrameListener {
                    override fun onStart(width: Int, height: Int, scale: Float) {
                        Log.w("CanvasSocket", "[#$connId] onStart called: ${width}x${height} scale=$scale")
                        mainHandler.post { ov.show(width, height, scale) }
                    }
                    override fun onFrame(frameId: Int, argbPixels: IntArray, width: Int, height: Int) {
                        mainHandler.post { ov.setFrame(argbPixels, width, height) }
                    }
                    override fun onEnd() {
                        mainHandler.post {
                            Log.w("CanvasSocket", "[#$connId] onEnd — hiding overlay")
                            ov.hide()
                        }
                    }
                }
            }

            // Start server AFTER setting callback para evitar race condition
            socketServer.start()
        }

        // Start camera control server (iris se conecta a cam-ctrl → restartCamera)
        startCameraServer(context)

        // Start audio server (flex/echo envían PCM s16le al socket @flex_audio)
        FlexAudioServer.getInstance().start()

        // Start mic server (mifo recibe PCM s16le del socket @mic-0)
        MicServer.getInstance().start(context)

        // Start ac control server (tool del chroot comanda la WebView via @ac-webview)
        AcControlServer.getInstance().start()

        if (NeoPreference.loadBoolean(R.string.key_general_auto_completion, false)) {
            if (termData.onAutoCompleteListener == null) {
                termData.onAutoCompleteListener = createAutoCompleteListener(termView)
            }
            termView.onAutoCompleteListener = termData.onAutoCompleteListener
        }

        if (termData.termSession != null) {
            termData.viewClient?.updateExtraKeys(termData.termSession?.title, true)
        }
    }

    private fun createAutoCompleteListener(view: TerminalView): OnAutoCompleteListener? {
        return TermCompleteListener(view)
    }

    override fun getViewTypeCount(): Int {
        return VIEW_TYPE_COUNT
    }

    override fun getViewType(tab: Tab, index: Int): Int {
        return when (tab) {
            is TermTab -> VIEW_TYPE_TERM
            is XSessionTab -> VIEW_TYPE_X
            is CanvasTab -> VIEW_TYPE_CANVAS
            is AcTab -> VIEW_TYPE_WEB
            else -> VIEW_TYPE_TERM
        }
    }
}