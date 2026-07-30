package org.aarchdroid.dragonterminal.frontend.terminal

import android.app.Activity
import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.view.WindowManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.doOnLayout
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import org.aarchdroid.R
import org.aarchdroid.dragonterminal.backend.HiddenOverlayRegistry
import org.aarchdroid.dragonterminal.backend.CanvasSocketServer
import org.aarchdroid.dragonterminal.backend.OverlayButtonState
import org.aarchdroid.dragonterminal.backend.TerminalSession
import org.aarchdroid.dragonterminal.frontend.session.shell.client.event.OverlayHiddenEvent
import org.greenrobot.eventbus.EventBus

class CanvasOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        @Volatile
        var wasFullscreen = false
    }

    private var frameBitmap: Bitmap? = null
    var frameWidth = 0
        private set
    var frameHeight = 0
        private set

    private val transformMatrix = Matrix()
    private val canvasScreenRect = RectF()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        isDither = true
    }

    private var scaleFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    var initialOffsetX: Float = 0f
    var initialOffsetY: Float = 0f
    var initialScale: Float = 1f
    var isActive = false
        private set
    private var touchOwned = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    var overlaySession: TerminalSession? = null
    val createdAt: Long = System.currentTimeMillis()

    /** Connection ID from CanvasSocketServer — used for targeted sendToClient() */
    var connId: Int = -1

    var isFullscreen = false

    /** While true, onDraw hides the stale bitmap until the native tool responds with a matching frame */
    private var waitingNativeFrame = false

    /** Original frame dimensions from native tool — used to restore after fullscreen */
    var originalFrameWidth: Int = 0
        private set
    var originalFrameHeight: Int = 0
        private set

    /** Tracks parent size to detect keyboard open/close during fullscreen */
    private var fullscreenParentWidth = 0
    private var fullscreenParentHeight = 0
    private val fullscreenLayoutListener = OnLayoutChangeListener { _, _, _, _, _, oldLeft, oldTop, oldRight, oldBottom ->
        if (!isFullscreen) return@OnLayoutChangeListener
        val parent = parent as? ViewGroup ?: return@OnLayoutChangeListener
        val pw = parent.width
        val ph = parent.height
        if (pw > 0 && ph > 0 && (pw != fullscreenParentWidth || ph != fullscreenParentHeight)) {
            Log.d("CanvasOV", "layoutListener ${fullscreenParentWidth}x${fullscreenParentHeight} -> ${pw}x${ph}")
            fullscreenParentWidth = pw
            fullscreenParentHeight = ph
            if (frameWidth == pw && frameHeight == ph) {
                scaleFactor = 1.0f
            } else {
                val scaleX = pw.toFloat() / frameWidth.coerceAtLeast(1)
                val scaleY = ph.toFloat() / frameHeight.coerceAtLeast(1)
                scaleFactor = minOf(scaleX, scaleY)
                if (connId >= 0) {
                    CanvasSocketServer.getInstance().sendToClient(connId, "resize ${pw}x${ph}")
                }
            }
            offsetX = (pw - frameWidth * scaleFactor) / 2f
            offsetY = (ph - frameHeight * scaleFactor) / 2f
            updateTransform()
            postInvalidateOnAnimation()
        }
    }

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var longPressFired = false
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val btnSize = 34f
    private val btnFrameRect = RectF()
    private val btnScreenRect = RectF()
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var btnVisible = false
    private var scaleDetector = createScaleDetector()

    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    /** Rotation gesture state (2-finger rotate like Google Maps) */
    private var prevAngle = 0.0
    private var rotationActive = false

    /** Throttle touch move forwarding to ~60fps */
    private var lastMoveSendTime = 0L
    private val MOVE_THROTTLE_MS = 16L

    private fun createScaleDetector(): ScaleGestureDetector {
        return ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (isFullscreen) {
                    // Forward pinch to native tool via socket
                    val fx = mapScreenToFrameX(detector.focusX)
                    val fy = mapScreenToFrameY(detector.focusY)
                    if (connId >= 0) {
                        CanvasSocketServer.getInstance().sendToClient(
                            connId, "pinch ${detector.scaleFactor} ${fx.toInt()} ${fy.toInt()}"
                        )
                    }
                } else {
                    val cx = offsetX + frameWidth * scaleFactor / 2f
                    val cy = offsetY + frameHeight * scaleFactor / 2f
                    scaleFactor *= detector.scaleFactor
                    scaleFactor = scaleFactor.coerceIn(0.1f, 10f)
                    offsetX = cx - frameWidth * scaleFactor / 2f
                    offsetY = cy - frameHeight * scaleFactor / 2f
                    updateTransform()
                    postInvalidateOnAnimation()
                }
                return true
            }
        })
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        visibility = GONE
        btnPaint.apply {
            isAntiAlias = true
        }
        OverlayButtonState.observe { visible, x, y ->
            btnVisible = visible
            if (x >= 0 && y >= 0) {
                btnFrameRect.set(
                    x.toFloat() - btnSize / 2f,
                    y.toFloat() - btnSize / 2f,
                    x.toFloat() + btnSize / 2f,
                    y.toFloat() + btnSize / 2f
                )
            }
            postInvalidateOnAnimation()
        }
    }

    fun show(width: Int, height: Int, scale: Float = initialScale) {
        exitFullscreen()
        frameWidth = width
        frameHeight = height
        if (originalFrameWidth <= 0 || originalFrameHeight <= 0) {
            originalFrameWidth = width
            originalFrameHeight = height
        }
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        if (!isActive) {
            scaleFactor = scale
            offsetX = initialOffsetX
            offsetY = initialOffsetY
        }
        isActive = true
        visibility = VISIBLE
        bringToFront()
        updateTransform()
        postInvalidateOnAnimation()
    }

    fun hide() {
        isActive = false
        exitFullscreen()
        frameBitmap = null
        visibility = GONE
        touchOwned = false
        HiddenOverlayRegistry.unregister(this)
        postInvalidateOnAnimation()
    }

    fun minimize() {
        if (isFullscreen) exitFullscreen()
        isActive = false
        visibility = GONE
        touchOwned = false
        HiddenOverlayRegistry.register(this)
        EventBus.getDefault().post(OverlayHiddenEvent())
        postInvalidateOnAnimation()
    }

    /** Minimize to carousel without triggering onToggleFullscreen cascade.
     *  Safe to call from onTabRemoved (tab is already being removed). */
    fun minimizeToCarousel() {
        exitFullscreenTab()
        isActive = false
        visibility = GONE
        touchOwned = false
        HiddenOverlayRegistry.register(this)
        EventBus.getDefault().post(OverlayHiddenEvent())
        postInvalidateOnAnimation()
    }

    fun restore() {
        isActive = true
        visibility = VISIBLE
        bringToFront()
        scaleDetector = createScaleDetector()
        touchOwned = false
        HiddenOverlayRegistry.unregister(this)
        EventBus.getDefault().post(OverlayHiddenEvent())
        updateTransform()
        postInvalidateOnAnimation()
    }

    var onToggleFullscreen: ((enterFullscreen: Boolean) -> Unit)? = null

    fun enterFullscreenTab() {
        if (isFullscreen) return
        isFullscreen = true
        wasFullscreen = true
        setBackgroundColor(Color.BLACK)
        visibility = VISIBLE
        isActive = true
        bringToFront()

        val act = context as? Activity
        act?.let { a ->
            val imm = a.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(a.window?.decorView?.windowToken, 0)
            a.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }

        val parent = parent as? ViewGroup
        if (parent != null && frameWidth > 0 && frameHeight > 0) {
            val pw = parent.width
            val ph = parent.height
            Log.d("CanvasOV", "enterFST parent=${pw}x${ph} frame=${frameWidth}x${frameHeight}")
            if (pw > 0 && ph > 0) {
                fullscreenParentWidth = pw
                fullscreenParentHeight = ph
                val scaleX = pw.toFloat() / frameWidth.coerceAtLeast(1)
                val scaleY = ph.toFloat() / frameHeight.coerceAtLeast(1)
                scaleFactor = minOf(scaleX, scaleY)
                offsetX = (pw - frameWidth * scaleFactor) / 2f
                offsetY = (ph - frameHeight * scaleFactor) / 2f
                // Always suggest native tool to render at full resolution
                if (connId >= 0) {
                    CanvasSocketServer.getInstance().sendToClient(connId, "resize ${pw}x${ph}")
                }
                waitingNativeFrame = true
                updateTransform()
                postInvalidateOnAnimation()
            } else {
                Log.d("CanvasOV", "enterFST parent not laid out, doOnLayout")
                parent.doOnLayout {
                    val w2 = parent.width
                    val h2 = parent.height
                    if (w2 > 0 && h2 > 0) {
                        Log.d("CanvasOV", "enterFST doOnLayout parent=${w2}x${h2}")
                        fullscreenParentWidth = w2
                        fullscreenParentHeight = h2
                        if (frameWidth == w2 && frameHeight == h2) {
                            scaleFactor = 1.0f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            val scaleX = w2.toFloat() / frameWidth.coerceAtLeast(1)
                            val scaleY = h2.toFloat() / frameHeight.coerceAtLeast(1)
                            scaleFactor = minOf(scaleX, scaleY)
                            offsetX = (w2 - frameWidth * scaleFactor) / 2f
                            offsetY = (h2 - frameHeight * scaleFactor) / 2f
                            if (connId >= 0) {
                                CanvasSocketServer.getInstance().sendToClient(connId, "resize ${w2}x${h2}")
                            }
                        }
                        waitingNativeFrame = true
                        updateTransform()
                        postInvalidateOnAnimation()
                    }
                }
            }
            parent.addOnLayoutChangeListener(fullscreenLayoutListener)
        } else if (frameWidth > 0 && frameHeight > 0) {
            Log.d("CanvasOV", "enterFST no parent, use self ${width}x${height}")
            val scaleX = width.toFloat() / frameWidth.coerceAtLeast(1)
            val scaleY = height.toFloat() / frameHeight.coerceAtLeast(1)
            scaleFactor = minOf(scaleX, scaleY)
            offsetX = (width - frameWidth * scaleFactor) / 2f
            offsetY = (height - frameHeight * scaleFactor) / 2f
        } else {
            Log.d("CanvasOV", "enterFST no parent no frame")
        }
        updateTransform()
        postInvalidateOnAnimation()
    }

    fun exitFullscreenTab() {
        if (!isFullscreen) return
        isFullscreen = false
        wasFullscreen = false
        waitingNativeFrame = false
        (parent as? ViewGroup)?.removeOnLayoutChangeListener(fullscreenLayoutListener)
        setBackgroundColor(Color.TRANSPARENT)
        // Restore original resolution in native tool for floating mode
        if (connId >= 0 && originalFrameWidth > 0 && originalFrameHeight > 0) {
            CanvasSocketServer.getInstance().sendToClient(connId, "resize ${originalFrameWidth}x${originalFrameHeight}")
        }
        scaleFactor = initialScale
        offsetX = initialOffsetX
        offsetY = initialOffsetY
        updateTransform()
        val act = context as? Activity
        act?.let { a ->
            a.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        postInvalidateOnAnimation()
    }

    private fun exitFullscreen() {
        exitFullscreenTab()
        onToggleFullscreen?.invoke(false)
    }

    fun toggleFullscreen() {
        if (isFullscreen) {
            exitFullscreenTab()
            onToggleFullscreen?.invoke(false)
        } else {
            onToggleFullscreen?.invoke(true)
            // enterFullscreenTab() is called directly by onShowTab(VIEW_TYPE_CANVAS)
            // after the overlay has been moved to the tab's FrameLayout
        }
    }

    private fun cancelPendingLongPress() {
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun startLongPress() {
        cancelPendingLongPress()
        longPressRunnable = Runnable {
            longPressFired = true
            toggleFullscreen()
        }
        longPressHandler.postDelayed(longPressRunnable!!, 700L)
    }

    fun getFrameBitmap(): Bitmap? = frameBitmap

    fun setFrame(argbPixels: IntArray, w: Int, h: Int) {
        if (!isActive) return
        frameWidth = w
        frameHeight = h
        Log.d("CanvasOV", "setFrame ${w}x${h} active=$isActive fs=$isFullscreen scale=$scaleFactor")
        frameBitmap?.recycle()
        frameBitmap = Bitmap.createBitmap(argbPixels, w, h, Bitmap.Config.ARGB_8888)

        // Recalculate scale/offset in fullscreen mode to fit frame to parent
        if (isFullscreen) {
            val parent = parent as? ViewGroup
            val pw = parent?.width ?: 0
            val ph = parent?.height ?: 0
            if (pw > 0 && ph > 0 && w == pw && h == ph) {
                Log.d("CanvasOV", "setFrame NATIVE MATCH -> scale=1.0")
                waitingNativeFrame = false
                scaleFactor = 1.0f
                offsetX = 0f
                offsetY = 0f
            } else if (pw > 0 && ph > 0) {
                // Non-matching frame in fullscreen → scale to fill parent
                val sx = pw.toFloat() / w.coerceAtLeast(1)
                val sy = ph.toFloat() / h.coerceAtLeast(1)
                scaleFactor = minOf(sx, sy)
                offsetX = (pw - w * scaleFactor) / 2f
                offsetY = (ph - h * scaleFactor) / 2f
            }
        }

        updateTransform()
        postInvalidateOnAnimation()
    }

    private fun updateTransform() {
        transformMatrix.reset()
        if (frameWidth <= 0 || frameHeight <= 0) return
        transformMatrix.postTranslate(offsetX, offsetY)
        transformMatrix.postScale(scaleFactor, scaleFactor, offsetX, offsetY)
        canvasScreenRect.set(offsetX, offsetY,
            offsetX + frameWidth * scaleFactor,
            offsetY + frameHeight * scaleFactor)
        // Map button from frame coords to screen coords
        val (bfx, bfy) = if (btnFrameRect.left > 0 || btnFrameRect.top > 0)
            Pair(btnFrameRect.left, btnFrameRect.top)
        else
            Pair(frameWidth.toFloat() - btnSize - 6f, 6f)
        btnScreenRect.set(
            offsetX + bfx * scaleFactor,
            offsetY + bfy * scaleFactor,
            offsetX + (bfx + btnSize) * scaleFactor,
            offsetY + (bfy + btnSize) * scaleFactor
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = frameBitmap ?: return
        if (!isActive) return
        if (waitingNativeFrame) return

        canvas.drawBitmap(bmp, transformMatrix, paint)

        if (btnVisible) {
            btnPaint.color = 0xCC000000.toInt()
            btnPaint.style = Paint.Style.FILL
            canvas.drawRoundRect(btnScreenRect, 8f, 8f, btnPaint)
            btnPaint.color = 0xFFCC3333.toInt()
            btnPaint.style = Paint.Style.STROKE
            btnPaint.strokeWidth = 2f
            canvas.drawRoundRect(btnScreenRect, 8f, 8f, btnPaint)
            btnPaint.color = 0xFFCC3333.toInt()
            btnPaint.style = Paint.Style.FILL
            btnPaint.strokeWidth = 3f
            val cx = btnScreenRect.centerX()
            val cy = btnScreenRect.centerY()
            canvas.drawLine(cx - btnSize * 0.28f, cy, cx + btnSize * 0.28f, cy, btnPaint)
        }
    }

    /** Map screen X to frame pixel coordinate (0..frameWidth) */
    private fun mapScreenToFrameX(screenX: Float): Float {
        if (scaleFactor == 0f) return 0f
        return ((screenX - offsetX) / scaleFactor).coerceIn(0f, frameWidth.toFloat())
    }

    /** Map screen Y to frame pixel coordinate (0..frameHeight) */
    private fun mapScreenToFrameY(screenY: Float): Float {
        if (scaleFactor == 0f) return 0f
        return ((screenY - offsetY) / scaleFactor).coerceIn(0f, frameHeight.toFloat())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isActive) return false

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                val inFrame = canvasScreenRect.contains(event.x, event.y)
                val inBtn = btnVisible && btnScreenRect.contains(event.x, event.y)
                if (!inFrame && !inBtn) {
                    return false
                }
                touchOwned = true
                bringToFront()
                downX = event.x
                downY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                lastMoveSendTime = 0L
                rotationActive = false
                startLongPress()
                // Always feed scaleDetector (for pinch detection even in fullscreen)
                scaleDetector.onTouchEvent(event)
                if (isFullscreen) {
                    // Forward touch down to native tool
                    val fx = mapScreenToFrameX(event.x)
                    val fy = mapScreenToFrameY(event.y)
                    if (connId >= 0) {
                        CanvasSocketServer.getInstance().sendToClient(connId, "touch down ${fx.toInt()} ${fy.toInt()}")
                    }
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!touchOwned) return false
                scaleDetector.onTouchEvent(event)
                if (event.pointerCount >= 2) {
                    val dx = (event.getX(1) - event.getX(0)).toDouble()
                    val dy = (event.getY(1) - event.getY(0)).toDouble()
                    prevAngle = Math.toDegrees(atan2(dy, dx))
                    rotationActive = true
                }
                bringToFront()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                scaleDetector.onTouchEvent(event)
                if (event.pointerCount <= 2) rotationActive = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touchOwned) return false
                // Always feed scaleDetector for pinch detection
                scaleDetector.onTouchEvent(event)
                if (hypot(event.x - downX, event.y - downY) > touchSlop) {
                    cancelPendingLongPress()
                }
                if (isFullscreen) {
                    // Forward touch move to native tool (throttled)
                    val now = SystemClock.uptimeMillis()
                    if (now - lastMoveSendTime >= MOVE_THROTTLE_MS) {
                        lastMoveSendTime = now
                        val fx = mapScreenToFrameX(event.x)
                        val fy = mapScreenToFrameY(event.y)
                        if (connId >= 0) {
                            CanvasSocketServer.getInstance().sendToClient(connId, "touch move ${fx.toInt()} ${fy.toInt()}")
                        }
                    }
                    // Rotation detection (2 fingers) — like Google Maps
                    if (rotationActive && event.pointerCount >= 2) {
                        val dx = (event.getX(1) - event.getX(0)).toDouble()
                        val dy = (event.getY(1) - event.getY(0)).toDouble()
                        val angle = Math.toDegrees(atan2(dy, dx))
                        var delta = angle - prevAngle
                        if (delta > 180.0) delta -= 360.0
                        if (delta < -180.0) delta += 360.0
                        if (abs(delta) > 0.1) {
                            val mx = mapScreenToFrameX((event.getX(0) + event.getX(1)) / 2f)
                            val my = mapScreenToFrameY((event.getY(0) + event.getY(1)) / 2f)
                            if (connId >= 0) {
                                CanvasSocketServer.getInstance().sendToClient(connId, String.format(Locale.US, "rotate %.1f %d %d", delta, mx.toInt(), my.toInt()))
                            }
                        }
                        prevAngle = angle
                    } else if (event.pointerCount < 2) {
                        rotationActive = false
                    }
                } else {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    lastTouchX = event.x
                    lastTouchY = event.y
                    if (!scaleDetector.isInProgress) {
                        offsetX += dx
                        offsetY += dy
                        updateTransform()
                        postInvalidateOnAnimation()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                cancelPendingLongPress()
                longPressFired = false
                rotationActive = false
                if (!touchOwned) return false
                // Always feed scaleDetector
                scaleDetector.onTouchEvent(event)
                if (isFullscreen) {
                    // Forward touch up to native tool
                    val fx = mapScreenToFrameX(event.x)
                    val fy = mapScreenToFrameY(event.y)
                    if (connId >= 0) {
                        CanvasSocketServer.getInstance().sendToClient(connId, "touch up ${fx.toInt()} ${fy.toInt()}")
                    }
                } else {
                    val now = SystemClock.uptimeMillis()
                    val dt = now - lastTapTime
                    val dTouch = hypot(event.x - lastTapX, event.y - lastTapY)
                    lastTapTime = now
                    lastTapX = event.x
                    lastTapY = event.y

                    if (dt < ViewConfiguration.getDoubleTapTimeout() && dTouch < touchSlop * 3) {
                        minimize()
                        return true
                    }

                    if (btnVisible && btnScreenRect.contains(event.x, event.y)) {
                        minimize()
                    }
                }
                touchOwned = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelPendingLongPress()
                longPressFired = false
                rotationActive = false
                if (isFullscreen && connId >= 0) {
                    val fx = mapScreenToFrameX(event.x)
                    val fy = mapScreenToFrameY(event.y)
                    CanvasSocketServer.getInstance().sendToClient(connId, "touch up ${fx.toInt()} ${fy.toInt()}")
                }
                touchOwned = false
                return true
            }
            else -> {
                if (!touchOwned) return false
                scaleDetector.onTouchEvent(event)
                return true
            }
        }
    }

    fun dismiss() {
        hide()
    }
}
