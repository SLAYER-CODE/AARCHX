package org.aarchdroid.dragonterminal.ui.term.tab

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
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
import org.aarchdroid.dragonterminal.frontend.terminal.TerminalView
import org.aarchdroid.dragonterminal.frontend.terminal.extrakey.ExtraKeysView
import org.aarchdroid.dragonterminal.ui.term.NeoTermActivity
import org.aarchdroid.dragonterminal.utils.TerminalUtils

/**
 * @author kiva
 */
class NeoTabDecorator(val context: NeoTermActivity) : TabSwitcherDecorator() {
    companion object {
        private var VIEW_TYPE_COUNT = 0
        private val VIEW_TYPE_TERM = VIEW_TYPE_COUNT++
        private val VIEW_TYPE_X = VIEW_TYPE_COUNT++
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

            else -> {
                throw RuntimeException("Unknown view type")
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
                val termTab = tab as TermTab
                termTab.toolbar = toolbar
                val terminalView =  findViewById<TerminalView>(R.id.terminal_view)
                if (isQuickPreview || tabSwitcher.isSwitcherShown) {
                    view.findViewById<ExtraKeysView>(R.id.extra_keys)?.visibility = View.GONE
                    bindTerminalView(termTab, terminalView, null)
                } else {
                    val extraKeysView = view.findViewById<ExtraKeysView>(R.id.extra_keys)
                    bindTerminalView(termTab, terminalView, extraKeysView)
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
                        floatBtn.text = "↗"
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
                            act.transferringHandle = session.mHandle
                            tabSwitcher.removeTab(tab)
                        }
                    } else {
                        floatBtn.visibility = View.GONE
                        floatBtn.setOnClickListener(null)
                    }
                } else {
                    Log.d("NeoTabDecor", "titleContainer NOT found — childContainer=$childContainer rootLayout=$rootLayout")
                }
            }

            VIEW_TYPE_X -> {
                toolbar.visibility = View.GONE
                bindXSessionView(tab as XSessionTab)
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
                                 extraKeysView: ExtraKeysView?) {
        val termView = view ?: return
        val termData = tab.termData

        termData.initializeViewWith(tab, termView, extraKeysView)
        termView.setEnableWordBasedIme(termData.profile?.enableWordBasedIme ?: DefaultValues.enableWordBasedIme)
        termView.setTerminalViewClient(termData.viewClient)
        termView.attachSession(termData.termSession)

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
        if (tab is TermTab) {
            return VIEW_TYPE_TERM
        } else if (tab is XSessionTab) {
            return VIEW_TYPE_X
        }
        return -1
    }
}