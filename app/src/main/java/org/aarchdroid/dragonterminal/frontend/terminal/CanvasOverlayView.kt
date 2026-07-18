package org.aarchdroid.dragonterminal.frontend.terminal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
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

    private val btnSize = 34f
    private val btnFrameRect = RectF()
    private val btnScreenRect = RectF()
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var btnVisible = false
    private var scaleDetector = createScaleDetector()
    private var gestureDetector = createGestureDetector()

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

    private fun createGestureDetector(): GestureDetector {
        return GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                minimize()
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
        isActive = false
        frameBitmap = null
        visibility = GONE
        touchOwned = false
        HiddenOverlayRegistry.unregister(this)
        postInvalidateOnAnimation()
    }

    fun minimize() {
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
        gestureDetector = createGestureDetector()
        touchOwned = false
        HiddenOverlayRegistry.unregister(this)
        EventBus.getDefault().post(OverlayHiddenEvent())
        updateTransform()
        postInvalidateOnAnimation()
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
                if (!canvasScreenRect.contains(event.x, event.y) &&
                    !(btnVisible && btnScreenRect.contains(event.x, event.y))) {
                    touchOwned = false
                    return false
                }
                touchOwned = true
                bringToFront()
                lastTouchX = event.x
                lastTouchY = event.y
                scaleDetector.onTouchEvent(event)
                gestureDetector.onTouchEvent(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touchOwned) return false
                scaleDetector.onTouchEvent(event)
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
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!touchOwned) return false
                scaleDetector.onTouchEvent(event)
                gestureDetector.onTouchEvent(event)
                touchOwned = false
                if (btnVisible && btnScreenRect.contains(event.x, event.y)) {
                    minimize()
                }
                return true
            }
            else -> {
                if (!touchOwned) return false
                scaleDetector.onTouchEvent(event)
                gestureDetector.onTouchEvent(event)
                return true
            }
        }
    }

    fun dismiss() {
        hide()
    }
}
