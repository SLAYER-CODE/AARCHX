package org.aarchdroid.dragonterminal.frontend.terminal.extrakey

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.util.AttributeSet
import android.view.*
import android.widget.GridLayout
import android.widget.LinearLayout
import org.aarchdroid.AArchDroidApp
import org.aarchdroid.R
import org.aarchdroid.dragonterminal.component.extrakey.ExtraKeyComponent
import org.aarchdroid.dragonterminal.frontend.component.ComponentManager
import org.aarchdroid.dragonterminal.frontend.config.NeoPreference
import org.aarchdroid.dragonterminal.frontend.config.NeoTermPath
import org.aarchdroid.dragonterminal.frontend.session.shell.client.event.*
import org.aarchdroid.dragonterminal.frontend.terminal.TerminalView
import org.aarchdroid.dragonterminal.frontend.terminal.extrakey.button.ControlButton
import org.aarchdroid.dragonterminal.frontend.terminal.extrakey.button.IExtraButton
import org.aarchdroid.dragonterminal.frontend.terminal.extrakey.button.StatedControlButton
import org.aarchdroid.dragonterminal.frontend.terminal.extrakey.impl.ArrowButton
import org.greenrobot.eventbus.EventBus
import java.io.File

class ExtraKeysView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {

    companion object {
        private val ESC = ControlButton(IExtraButton.KEY_ESC)
        private val CTRL_R = object : ControlButton(IExtraButton.KEY_TAB) {
            init {
                displayText = "↺"
            }
            override fun onClick(view: View) {
                val tv = (view.parent as? View)?.findViewById<TerminalView>(R.id.terminal_view)
                    ?: view.findViewById(R.id.terminal_view)
                tv?.currentSession?.write("\u0012")
            }
        }
        private val TOGGLE_HISTORY = object : ControlButton(IExtraButton.KEY_PAGE_UP) {
            init {
                displayText = "⇄"
            }
            override fun onClick(view: View) {
                EventBus.getDefault().post(ToggleHistoryEvent())
            }
        }
        private val OPEN_FLOAT = object : ControlButton(IExtraButton.KEY_PAGE_DOWN) {
            init {
                displayText = "⊞"
            }
            override fun onClick(view: View) {
                EventBus.getDefault().post(OpenFloatEvent())
            }
        }
        private val PREV_SESSION = object : ControlButton(IExtraButton.KEY_ARROW_LEFT) {
            init {
                displayText = "◀"
            }
            override fun onClick(view: View) {
                EventBus.getDefault().post(SwitchSessionEvent(toNext = false))
            }
        }
        private val SELECT_ALL = object : ControlButton(IExtraButton.KEY_ARROW_DOWN) {
            init {
                displayText = "▣"
            }
            override fun onClick(view: View) {
                EventBus.getDefault().post(SelectAllEvent())
            }
        }
        private val NEXT_SESSION = object : ControlButton(IExtraButton.KEY_ARROW_RIGHT) {
            init {
                displayText = "▶"
            }
            override fun onClick(view: View) {
                EventBus.getDefault().post(SwitchSessionEvent(toNext = true))
            }
        }
        private val TOGGLE_IME = object : ControlButton(IExtraButton.KEY_TOGGLE_IME) {
            init {
                displayText = "⌨"
            }
            override fun onClick(view: View) {
                EventBus.getDefault().post(ToggleImeEvent())
            }
        }
        private val NEW_SESSION = object : ControlButton(IExtraButton.KEY_HOME) {
            init {
                displayText = "+"
            }
            override fun onClick(view: View) {
                EventBus.getDefault().post(CreateNewSessionEvent())
            }
        }
        private val CLEAR_TERMINAL = object : ControlButton(IExtraButton.KEY_ARROW_UP) {
            init {
                displayText = "⌧"
            }
            override fun onClick(view: View) {
                val tv = (view.parent as? View)?.findViewById<TerminalView>(R.id.terminal_view)
                    ?: view.findViewById(R.id.terminal_view)
                tv?.currentSession?.write("\u000c")
            }
        }
        private val FLOAT_CURRENT = object : ControlButton(IExtraButton.KEY_END) {
            init {
                displayText = "↗"
            }
            override fun onClick(view: View) {
                EventBus.getDefault().post(FloatCurrentTerminalEvent())
            }
        }

        private val MAX_BUTTONS_PER_LINE = 7
        private val DEFAULT_ALPHA = 0.8f
        private val EXPANDED_ALPHA = 0.7f
        private val USER_KEYS_BUTTON_LINE_START = 2
    }

    private val builtinKeys = mutableListOf<IExtraButton>()
    private val userKeys = mutableListOf<IExtraButton>()

    private val buttonStateMap = mutableMapOf<IExtraButton, View>()

    var tabCount: Int = 0
        set(value) {
            field = value
            refreshButtonStates()
        }

    private val buttonBars: MutableList<LinearLayout> = mutableListOf()
    private var typeface: Typeface? = null

    // Initialize StatedControlButton here
    // For avoid memory and context leak.
    private val TOGGLE_SWITCHER = object : ControlButton(IExtraButton.KEY_CTRL) {
        init {
            displayText = "↔"
        }
        override fun onClick(view: View) {
            EventBus.getDefault().post(ToggleTerminalSwitcherEvent())
        }
    }
    private val KILL = object : ControlButton(IExtraButton.KEY_ALT) {
        init {
            displayText = "✕"
        }
        override fun onClick(view: View) {
            EventBus.getDefault().post(KillTerminalEvent())
        }
    }

    private var buttonPanelExpanded = false
    private val EXPAND_BUTTONS = object : ControlButton(IExtraButton.KEY_SHOW_ALL_BUTTONS) {
        override fun onClick(view: View) {
            expandButtonPanel()
        }
    }

    private val extraKeyComponent: ExtraKeyComponent

    init {
        alpha = DEFAULT_ALPHA
        gravity = Gravity.TOP
        orientation = LinearLayout.VERTICAL
        typeface = Typeface.createFromAsset(context.assets, "eks_font.ttf")
        extraKeyComponent = ComponentManager.getComponent<ExtraKeyComponent>()

        initBuiltinKeys()
        loadDefaultUserKeys()
        updateButtons()
        refreshButtonStates()
        expandButtonPanel(false)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_DOWN) {
            if (buttonPanelExpanded) {
                expandButtonPanel()
                return true
            }
            return false
        }
        return super.onKeyDown(keyCode, event)
    }

    fun setTextColor(textColor: Int) {
        IExtraButton.NORMAL_TEXT_COLOR = textColor
        updateButtons()
    }

    fun setTypeface(typeface: Typeface?) {
        this.typeface = typeface
        updateButtons()
    }

    fun readControlButton(): Boolean {
        return false
    }

    fun readAltButton(): Boolean {
        return false
    }

    fun addUserKey(button: IExtraButton) {
        addKeyButton(userKeys, button)
    }

    fun addBuiltinKey(button: IExtraButton) {
        addKeyButton(builtinKeys, button)
    }

    fun clearUserKeys() {
        userKeys.clear()
    }

    fun loadDefaultUserKeys() {
        clearUserKeys()
        val defaultConfig = extraKeyComponent.loadConfigure(File(AArchDroidApp.get().filesDir.absolutePath+"/home/.neoterm/eks/default.nl"))
        if (defaultConfig != null) {
            userKeys.addAll(defaultConfig.shortcutKeys)
        }
    }

    fun updateButtons() {
        buttonBars.forEach { it.removeAllViews() }

        var targetButtonBarIndex = 0
        builtinKeys.plus(userKeys).forEachIndexed { index, button ->
            addKeyButton(getButtonBarOrNew(targetButtonBarIndex), button)
            targetButtonBarIndex = (index + 1) / MAX_BUTTONS_PER_LINE
        }
        updateButtonBars()
    }

    private fun updateButtonBars() {
        removeAllViews()

        buttonBars.asReversed()
                .forEach { addView(it) }
    }

    private fun expandButtonPanel(forceSetExpanded: Boolean? = null) {
        if (buttonBars.size <= 2) {
            return
        }

        buttonPanelExpanded = forceSetExpanded ?: !buttonPanelExpanded
        val visibility = if (buttonPanelExpanded) View.VISIBLE else View.GONE
        alpha = if (buttonPanelExpanded) EXPANDED_ALPHA else DEFAULT_ALPHA

        IntRange(USER_KEYS_BUTTON_LINE_START, buttonBars.size - 1)
                .map { buttonBars[it] }
                .forEach { it.visibility = visibility }
    }

    private fun createNewButtonBar(): LinearLayout {
        val line = LinearLayout(context)

        val layoutParams =
                if (NeoPreference.isExplicitExtraKeysWeightEnabled())
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                else
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT)

        layoutParams.setMargins(0, 0, 0, 0)
        line.setPadding(0, 0, 0, 0)
        line.gravity = Gravity.START
        line.orientation = LinearLayout.HORIZONTAL
        line.layoutParams = layoutParams
        return line
    }

    private fun getButtonBarOrNew(position: Int): LinearLayout {
        if (position >= buttonBars.size) {
            for (i in 0..(position - buttonBars.size + 1)) {
                buttonBars.add(createNewButtonBar())
            }
        }
        return buttonBars[position]
    }

    private fun addKeyButton(buttons: MutableList<IExtraButton>?, button: IExtraButton) {
        if (buttons != null && !buttons.contains(button)) {
            buttons.add(button)
        }
    }

    private fun addKeyButton(contentView: LinearLayout, extraButton: IExtraButton) {
        val outerButton = extraButton.makeButton(context, null, android.R.attr.buttonBarButtonStyle)

        val param = GridLayout.LayoutParams()
        param.setGravity(Gravity.CENTER)
        param.width = calculateButtonWidth()
        param.height = context.resources.getDimensionPixelSize(R.dimen.eks_height)
        param.setMargins(0, 0, 0, 0)

        outerButton.layoutParams = param
        outerButton.maxLines = 1
        outerButton.typeface = typeface
        outerButton.text = extraButton.displayText
        outerButton.setPadding(0, 0, 0, 0)
        outerButton.setTextColor(0xFF00FF00.toInt())
        outerButton.setAllCaps(false)

        outerButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> outerButton.setTextColor(0xFFFF0000.toInt())
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val enabled = isButtonEnabled(extraButton)
                    outerButton.setTextColor(if (enabled) 0xFF00FF00.toInt() else 0xFF005500.toInt())
                }
            }
            false
        }

        outerButton.setOnClickListener {
            outerButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            extraButton.onClick(this@ExtraKeysView)
        }
        contentView.addView(outerButton)
        buttonStateMap[extraButton] = outerButton
        outerButton.alpha = if (isButtonEnabled(extraButton)) 1.0f else 0.4f
    }

    private fun initBuiltinKeys() {
        addBuiltinKey(CTRL_R)
        addBuiltinKey(CLEAR_TERMINAL)
        addBuiltinKey(OPEN_FLOAT)
        addBuiltinKey(SELECT_ALL)
        addBuiltinKey(KILL)
        addBuiltinKey(NEW_SESSION)
        addBuiltinKey(TOGGLE_IME)

        addBuiltinKey(ESC)
        addBuiltinKey(EXPAND_BUTTONS)
        addBuiltinKey(TOGGLE_HISTORY)
        addBuiltinKey(FLOAT_CURRENT)
        addBuiltinKey(PREV_SESSION)
        addBuiltinKey(NEXT_SESSION)
        addBuiltinKey(TOGGLE_SWITCHER)
    }

    private fun isButtonEnabled(button: IExtraButton): Boolean {
        return when (button) {
            TOGGLE_HISTORY -> !NeoPreference.isLoggingDisabled()
            KILL -> tabCount > 0
            TOGGLE_SWITCHER, PREV_SESSION, NEXT_SESSION -> tabCount > 1
            OPEN_FLOAT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else true
            }
            else -> true
        }
    }

    fun refreshButtonStates() {
        for ((button, view) in buttonStateMap) {
            val enabled = isButtonEnabled(button)
            view.alpha = if (enabled) 1.0f else 0.6f
            (view as? android.widget.Button)?.setTextColor(if (enabled) 0xFF00FF00.toInt() else 0xFF005500.toInt())
        }
    }

    private fun calculateButtonWidth(): Int {
        return context.resources.displayMetrics.widthPixels / ExtraKeysView.MAX_BUTTONS_PER_LINE
    }
}
