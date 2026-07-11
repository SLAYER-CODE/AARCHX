package org.aarchdroid.dragonterminal.frontend.terminal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import org.aarchdroid.dragonterminal.backend.MandelaSocketServer

class MandelaOverlayView @JvmOverloads constructor(
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
    private var downX = 0f
    private var downY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isActive = false
    private var touchOwned = false

    private val btnSize = 34f
    private val btnMargin = 8f
    private val btnRect = RectF()
    private lateinit var btnBorderPaint: Paint
    private lateinit var btnBgPaint: Paint
    private lateinit var btnMinusPaint: Paint

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.1f, 10f)
            updateTransform()
            postInvalidateOnAnimation()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            offsetX -= dx
            offsetY -= dy
            updateTransform()
            postInvalidateOnAnimation()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            scaleFactor = 1f
            offsetX = 0f
            offsetY = 0f
            updateTransform()
            postInvalidateOnAnimation()
            return true
        }
    })

    init {
        setBackgroundColor(Color.TRANSPARENT)
        visibility = GONE
        btnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFCC3333.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCC000000.toInt()
            setShadowLayer(3f, 1f, 1f, 0x80000000.toInt())
        }
        btnMinusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFCC3333.toInt()
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
        }
    }

    fun setOnMinimizeListener(cb: () -> Unit) {
        onMinimize = cb
    }

    private var onMinimize: (() -> Unit)? = null

    fun show(width: Int, height: Int) {
        frameWidth = width
        frameHeight = height
        if (!isActive) {
            val srv = MandelaSocketServer.getInstanceOpt()
            if (srv != null) {
                scaleFactor = srv.persistedScale
                offsetX = srv.persistedOffsetX
                offsetY = srv.persistedOffsetY
            } else {
                scaleFactor = 1f
                offsetX = 0f
                offsetY = 0f
            }
        }
        isActive = true
        visibility = VISIBLE
        bringToFront()
        updateTransform()
        postInvalidateOnAnimation()
    }

    fun hide() {
        val srv = MandelaSocketServer.getInstanceOpt()
        srv?.let {
            it.persistedScale = scaleFactor
            it.persistedOffsetX = offsetX
            it.persistedOffsetY = offsetY
        }
        isActive = false
        frameBitmap = null
        visibility = GONE
        touchOwned = false
        postInvalidateOnAnimation()
    }

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
        transformMatrix.postScale(scaleFactor, scaleFactor)
        transformMatrix.postTranslate(offsetX, offsetY)

        MandelaSocketServer.getInstanceOpt()?.let {
            it.persistedScale = scaleFactor
            it.persistedOffsetX = offsetX
            it.persistedOffsetY = offsetY
        }

        val src = RectF(0f, 0f, frameWidth.toFloat(), frameHeight.toFloat())
        transformMatrix.mapRect(canvasScreenRect, src)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = frameBitmap ?: return
        if (!isActive) return

        canvas.drawRoundRect(canvasScreenRect, 8f, 8f, backdropPaint)
        canvas.drawBitmap(bmp, transformMatrix, paint)

        // Minimize button (—) — square peeking out from top-right of the canvas rect
        val btnRight = canvasScreenRect.right + btnSize / 3
        val btnTop = canvasScreenRect.top - btnSize / 3
        btnRect.set(btnRight - btnSize, btnTop, btnRight, btnTop + btnSize)
        canvas.drawRect(btnRect, btnBgPaint)
        canvas.drawRect(btnRect, btnBorderPaint)
        val cx = btnRect.centerX()
        val cy = btnRect.centerY()
        canvas.drawLine(cx - btnSize * 0.28f, cy, cx + btnSize * 0.28f, cy, btnMinusPaint)
    }

    private val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xBB111111.toInt()
        setShadowLayer(12f, 0f, 0f, 0x80000000.toInt())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isActive) return false

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                if (!canvasScreenRect.contains(event.x, event.y) &&
                    !btnRect.contains(event.x, event.y)) {
                    touchOwned = false
                    return false  // pass through to terminal
                }
                touchOwned = true
                downX = event.x
                downY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                scaleDetector.onTouchEvent(event)
                gestureDetector.onTouchEvent(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touchOwned) return false
                scaleDetector.onTouchEvent(event)
                gestureDetector.onTouchEvent(event)
                if (!scaleDetector.isInProgress) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    offsetX += dx
                    offsetY += dy
                    lastTouchX = event.x
                    lastTouchY = event.y
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
                val upX = event.x
                val upY = event.y
                if (btnRect.contains(upX, upY)) {
                    onMinimize?.invoke()
                    hide()
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
