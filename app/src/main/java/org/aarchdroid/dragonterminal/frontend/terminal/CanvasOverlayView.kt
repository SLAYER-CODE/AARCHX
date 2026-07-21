package org.aarchdroid.dragonterminal.frontend.terminal

import android.content.Context
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
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.hypot
import org.aarchdroid.R
import org.aarchdroid.dragonterminal.backend.HiddenOverlayRegistry
import org.aarchdroid.dragonterminal.backend.OverlayButtonState
import org.aarchdroid.dragonterminal.backend.TerminalSession
import org.aarchdroid.dragonterminal.frontend.session.shell.client.event.OverlayHiddenEvent
import org.greenrobot.eventbus.EventBus

class CanvasOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var frameBitmap: Bitmap? = null
    private var frameWidth = 0
    private var frameHeight = 0

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
    private var isActive = false
    private var touchOwned = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    var overlaySession: TerminalSession? = null
    val createdAt: Long = System.currentTimeMillis()

    var isFullscreen = false

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

    private fun createScaleDetector(): ScaleGestureDetector {
        return ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cx = offsetX + frameWidth * scaleFactor / 2f
                val cy = offsetY + frameHeight * scaleFactor / 2f
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.1f, 10f)
                offsetX = cx - frameWidth * scaleFactor / 2f
                offsetY = cy - frameHeight * scaleFactor / 2f
                updateTransform()
                postInvalidateOnAnimation()
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
        exitFullscreen()
        isActive = false
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
        setBackgroundColor(Color.BLACK)
        visibility = VISIBLE
        isActive = true
        bringToFront()
        if (frameWidth > 0 && frameHeight > 0) {
            val p = parent as? View ?: return
            val pw = p.width
            val ph = p.height
            if (pw > 0 && ph > 0) {
                val fitScale = minOf(
                    pw.toFloat() / (frameWidth.coerceAtLeast(1) + 40),
                    ph.toFloat() / (frameHeight.coerceAtLeast(1) + 40)
                )
                scaleFactor = fitScale
                offsetX = (pw - frameWidth * fitScale) / 2f
                offsetY = (ph - frameHeight * fitScale) / 2f
            }
        }
        updateTransform()
        postInvalidateOnAnimation()
    }

    fun exitFullscreenTab() {
        if (!isFullscreen) return
        isFullscreen = false
        setBackgroundColor(Color.TRANSPARENT)
        scaleFactor = initialScale
        offsetX = initialOffsetX
        offsetY = initialOffsetY
        updateTransform()
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
            // enterFullscreenTab() is called by onShowTab(VIEW_TYPE_CANVAS)
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
        frameBitmap?.recycle()
        frameBitmap = Bitmap.createBitmap(argbPixels, w, h, Bitmap.Config.ARGB_8888)
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isActive) return false

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                if (isFullscreen) {
                    touchOwned = true
                    startLongPress()
                    return true
                }
                if (!canvasScreenRect.contains(event.x, event.y) &&
                    !(btnVisible && btnScreenRect.contains(event.x, event.y))) {
                    touchOwned = false
                    return false
                }
                touchOwned = true
                bringToFront()
                downX = event.x
                downY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                scaleDetector.onTouchEvent(event)
                startLongPress()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touchOwned) return false
                if (isFullscreen || longPressFired) return true
                scaleDetector.onTouchEvent(event)
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y
                if (hypot(event.x - downX, event.y - downY) > touchSlop) {
                    cancelPendingLongPress()
                }
                if (!scaleDetector.isInProgress) {
                    offsetX += dx
                    offsetY += dy
                    updateTransform()
                    postInvalidateOnAnimation()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                cancelPendingLongPress()
                longPressFired = false
                if (!touchOwned) return false
                if (isFullscreen) {
                    touchOwned = false
                    return true
                }
                scaleDetector.onTouchEvent(event)
                touchOwned = false

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
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelPendingLongPress()
                longPressFired = false
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
