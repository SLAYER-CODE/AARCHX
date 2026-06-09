@file:SuppressLint("RtlHardcoded")

package org.aarchdroid.dragonterminal.floatui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.util.AttributeSet
import org.aarchdroid.R
import org.aarchdroid.dragonterminal.backend.TerminalSession
import org.aarchdroid.dragonterminal.frontend.terminal.TerminalView

class FloatWindowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    companion object {
        const val ALPHA_FOCUS = 0.95f
        const val ALPHA_NO_FOCUS = 0.70f
        const val ALPHA_MOVING = 0.50f
        private const val MIN_SIZE = 50
        private val SIZES = arrayOf(
            Pair(400, 200),
            Pair(600, 400),
            Pair(720, 550)
        )
    }

    private var currentSizeIndex = 0

    lateinit var service: FloatService
        private set

    private var displayWidth = 0
    private var displayHeight = 0

    val layoutParams = WindowManager.LayoutParams()
    lateinit var windowManager: WindowManager
        private set

    lateinit var terminalView: TerminalView
        private set
    private var windowControls: ViewGroup? = null
    private var keyboardButton: View? = null
    private var minimizeButton: View? = null
    private var resizeButton: View? = null
    private var exitButton: View? = null
    private var anchorButton: View? = null

    lateinit var bubbleManager: BubbleManager
        private set
    lateinit var viewClient: FloatViewClient
        private set
    lateinit var sessionClient: FloatSessionClient
        private set

    var session: TerminalSession? = null

    val preferences: FloatPreferences by lazy { FloatPreferences(context) }

    var overlayFocused = true
    var isDragging = false

    var initialX = 0
    var initialY = 0
    var initialTouchX = 0f
    var initialTouchY = 0f

    val location = IntArray(2)
    private val controlsLocation = IntArray(2)

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val w = (layoutParams.width + (detector.currentSpanX - detector.previousSpanX)).toInt()
            val h = (layoutParams.height + (detector.currentSpanY - detector.previousSpanY)).toInt()
            layoutParams.width = w.coerceAtLeast(MIN_SIZE)
            layoutParams.height = h.coerceAtLeast(MIN_SIZE)
            windowManager.updateViewLayout(this@FloatWindowView, layoutParams)
            preferences.windowWidth = layoutParams.width
            preferences.windowHeight = layoutParams.height
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {}
    })

    init {
        alpha = ALPHA_FOCUS
    }

    fun bindToService(svc: FloatService) {
        service = svc
        sessionClient = FloatSessionClient(svc, this)
        viewClient = FloatViewClient(this, sessionClient)
        terminalView.setTerminalViewClient(viewClient)
        viewClient.initFloatView()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        terminalView = findViewById(R.id.terminal_view)
        windowControls = findViewById(R.id.window_controls)
        keyboardButton = findViewById(R.id.keyboard_button)
        minimizeButton = findViewById(R.id.minimize_button)
        resizeButton = findViewById(R.id.resize_button)
        exitButton = findViewById(R.id.exit_button)
        anchorButton = findViewById(R.id.anchor_button)
        bubbleManager = BubbleManager(this)
        initControls()
    }

    private fun initControls() {
        windowControls?.setOnClickListener { gainFocus() }

        keyboardButton?.setOnClickListener { toggleKeyboard() }
        minimizeButton?.setOnClickListener { bubbleManager.toggle() }
        resizeButton?.setOnClickListener { cycleSize() }
        exitButton?.setOnClickListener { service.removeWindow(this@FloatWindowView) }
        anchorButton?.setOnClickListener { service.anchorWindow(this@FloatWindowView) }
    }

    private fun cycleSize() {
        val nextIndex = (currentSizeIndex + 1) % SIZES.size
        currentSizeIndex = nextIndex
        val (w, h) = SIZES[nextIndex]

        val cx = layoutParams.x + layoutParams.width / 2
        val cy = layoutParams.y + layoutParams.height / 2

        val density = resources.displayMetrics.density
        val statusBarH = (50 * density).toInt()

        val minX = 0
        val maxX = (displayWidth - w).coerceAtLeast(0)
        val minY = statusBarH
        val maxY = (displayHeight - h).coerceAtLeast(statusBarH)

        layoutParams.x = (cx - w / 2).coerceIn(minX, maxX)
        layoutParams.y = (cy - h / 2).coerceIn(minY, maxY)
        layoutParams.width = w
        layoutParams.height = h
        updateLayout()

        preferences.windowWidth = w
        preferences.windowHeight = h
        preferences.windowX = layoutParams.x
        preferences.windowY = layoutParams.y
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val display = context.getSystemService(Context.WINDOW_SERVICE).let {
            (it as WindowManager).defaultDisplay
        }
        val size = Point()
        display.getSize(size)
        displayWidth = size.x
        displayHeight = size.y
        sessionClient.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sessionClient.onDetachedFromWindow()
    }

    fun launchOverlay(tileX: Int? = null, tileY: Int? = null, tileW: Int? = null, tileH: Int? = null) {
        layoutParams.flags = computeFlags(true)
        layoutParams.type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        layoutParams.format = PixelFormat.RGBA_8888
        layoutParams.gravity = Gravity.TOP or Gravity.LEFT

        val prefs = preferences
        layoutParams.x = tileX ?: prefs.windowX.coerceAtLeast(0)
        layoutParams.y = tileY ?: prefs.windowY.coerceAtLeast(0)
        layoutParams.width = (tileW ?: prefs.windowWidth.coerceAtLeast(MIN_SIZE))
        layoutParams.height = (tileH ?: prefs.windowHeight.coerceAtLeast(MIN_SIZE))

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (windowToken == null) {
            windowManager.addView(this, layoutParams)
        }
        showKeyboard()
    }

    private fun computeFlags(focused: Boolean): Int {
        return if (focused) 0
        else WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
             WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (isDragging) return true

        getLocationOnScreen(location)
        val touchX = event.rawX
        val touchY = event.rawY

        if (didClickControls(touchX, touchY)) {
            if (event.action == MotionEvent.ACTION_DOWN && !isTouchOnButton(touchX, touchY)) {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                enterDragMode()
                return true
            }
            return false
        }

        val clickedInside = touchX >= location[0] &&
            touchX <= location[0] + layoutParams.width &&
            touchY >= location[1] &&
            touchY <= location[1] + layoutParams.height

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!clickedInside) loseFocus()
            }
            MotionEvent.ACTION_UP -> {
                if (clickedInside) {
                    gainFocus()
                    showKeyboard()
                }
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isDragging) {
            scaleDetector.onTouchEvent(event)
            if (scaleDetector.isInProgress) return true

            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    val newX = (initialX + (event.rawX - initialTouchX)).toInt()
                        .coerceIn(0, displayWidth - layoutParams.width)
                    val newY = (initialY + (event.rawY - initialTouchY)).toInt()
                        .coerceIn(0, displayHeight - layoutParams.height)
                    layoutParams.x = newX
                    layoutParams.y = newY
                    windowManager.updateViewLayout(this, layoutParams)
                    preferences.windowX = newX
                    preferences.windowY = newY
                }
                MotionEvent.ACTION_UP -> exitDragMode()
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun didClickControls(touchX: Float, touchY: Float): Boolean {
        if (windowControls?.visibility == View.GONE) return false
        windowControls?.getLocationOnScreen(controlsLocation) ?: return false
        val cw = windowControls?.width ?: 0
        val ch = windowControls?.height ?: 0
        return touchX >= controlsLocation[0] &&
            touchX <= controlsLocation[0] + cw &&
            touchY >= controlsLocation[1] &&
            touchY <= controlsLocation[1] + ch
    }

    private fun isTouchOnButton(rawX: Float, rawY: Float): Boolean {
        val coords = IntArray(2)
        for (btn in listOfNotNull(keyboardButton, minimizeButton, resizeButton, anchorButton, exitButton)) {
            if (btn.visibility != View.VISIBLE) continue
            btn.getLocationOnScreen(coords)
            if (rawX >= coords[0] && rawX <= coords[0] + btn.width &&
                rawY >= coords[1] && rawY <= coords[1] + btn.height) {
                return true
            }
        }
        return false
    }

    fun gainFocus() {
        if (isDragging) return
        if (bubbleManager.isMinimized()) {
            bubbleManager.toggle()
        }
        windowControls?.background = ColorDrawable(Color.parseColor("#FF0A2809"))
        if (overlayFocused) {
            showKeyboard()
            return
        }
        overlayFocused = true
        layoutParams.flags = computeFlags(true)
        updateLayout()
        alpha = ALPHA_FOCUS
    }

    fun loseFocus() {
        overlayFocused = false
        layoutParams.flags = computeFlags(false)
        windowControls?.background = ColorDrawable(Color.parseColor("#FF661111"))
        updateLayout()
        alpha = ALPHA_NO_FOCUS
    }

    fun enterDragMode() {
        isDragging = true
        windowControls?.background = ColorDrawable(Color.parseColor("#FF661111"))
        alpha = 1.0f
    }

    private fun exitDragMode() {
        isDragging = false
        alpha = if (overlayFocused) ALPHA_FOCUS else ALPHA_NO_FOCUS
    }

    fun showKeyboard() {
        terminalView.post {
            terminalView.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(terminalView, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun hideKeyboard() {
        terminalView.post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(terminalView.windowToken, 0)
        }
    }

    fun toggleKeyboard() {
        terminalView.post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.toggleSoftInput(0, 0)
        }
    }

    fun setWindowVisibility(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun isVisible(): Boolean = isAttachedToWindow && isShown

    fun closeOverlay() {
        if (windowToken != null) {
            windowManager.removeView(this)
        }
        bubbleManager.cleanup()
    }

    fun reloadStyling() {
        sessionClient.onReload()
    }

    private fun updateLayout() {
        try {
            windowManager.updateViewLayout(this, layoutParams)
        } catch (e: Exception) {
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val controls = windowControls ?: return
        val tv = terminalView

        if (controls.visibility != View.GONE) {
            controls.measure(
                MeasureSpec.makeMeasureSpec(r - l, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            controls.layout(0, b - t - controls.measuredHeight, r - l, b - t)

            tv.measure(
                MeasureSpec.makeMeasureSpec(r - l, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(b - t - controls.measuredHeight, MeasureSpec.EXACTLY)
            )
            tv.layout(0, 0, r - l, b - t - controls.measuredHeight)
        } else {
            tv.measure(
                MeasureSpec.makeMeasureSpec(r - l, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(b - t, MeasureSpec.EXACTLY)
            )
            tv.layout(0, 0, r - l, b - t)
        }
    }
}
