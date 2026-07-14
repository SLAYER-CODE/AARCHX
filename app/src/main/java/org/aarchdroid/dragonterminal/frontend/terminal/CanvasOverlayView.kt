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

    private val btnSize = 34f
    private val btnRect = RectF()
    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
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

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
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
        btnPaint.apply {
            isAntiAlias = true
        }
        backdropPaint.apply {
            color = 0xBB111111.toInt()
            setShadowLayer(12f, 0f, 0f, 0x80000000.toInt())
        }
    }

    private var onMinimize: (() -> Unit)? = null

    fun setOnMinimizeListener(cb: () -> Unit) {
        onMinimize = cb
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
        transformMatrix.postTranslate(offsetX, offsetY)
        transformMatrix.postScale(scaleFactor, scaleFactor, offsetX, offsetY)
        canvasScreenRect.set(offsetX, offsetY,
            offsetX + frameWidth * scaleFactor,
            offsetY + frameHeight * scaleFactor)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = frameBitmap ?: return
        if (!isActive) return

        canvas.drawRoundRect(canvasScreenRect, 8f, 8f, backdropPaint)
        canvas.drawBitmap(bmp, transformMatrix, paint)

        // Minimize button (—) at top-right of the canvas rect
        btnRect.set(canvasScreenRect.right - btnSize, canvasScreenRect.top,
            canvasScreenRect.right, canvasScreenRect.top + btnSize)
        btnPaint.color = 0xCC000000.toInt()
        btnPaint.style = Paint.Style.FILL
        canvas.drawRect(btnRect, btnPaint)
        btnPaint.color = 0xFFCC3333.toInt()
        btnPaint.style = Paint.Style.STROKE
        btnPaint.strokeWidth = 2f
        canvas.drawRect(btnRect, btnPaint)
        btnPaint.color = 0xFFCC3333.toInt()
        btnPaint.style = Paint.Style.FILL
        btnPaint.strokeWidth = 3f
        val cx = btnRect.centerX()
        val cy = btnRect.centerY()
        canvas.drawLine(cx - btnSize * 0.28f, cy, cx + btnSize * 0.28f, cy, btnPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isActive) return false

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                if (!canvasScreenRect.contains(event.x, event.y) &&
                    !btnRect.contains(event.x, event.y)) {
                    touchOwned = false
                    return false
                }
                touchOwned = true
                lastTouchX = event.x
                lastTouchY = event.y
                scaleDetector.onTouchEvent(event)
                gestureDetector.onTouchEvent(event)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touchOwned) return false
                scaleDetector.onTouchEvent(event)
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
                if (btnRect.contains(event.x, event.y)) {
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
