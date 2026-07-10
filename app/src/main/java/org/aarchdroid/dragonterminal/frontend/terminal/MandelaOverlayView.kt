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

class MandelaOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var frameBitmap: Bitmap? = null
    private var frameWidth = 0
    private var frameHeight = 0

    private val transformMatrix = Matrix()
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
    }

    fun show(width: Int, height: Int) {
        frameWidth = width
        frameHeight = height
        scaleFactor = 1f
        offsetX = 0f
        offsetY = 0f
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
        postInvalidateOnAnimation()
    }

    fun setFrame(argbPixels: IntArray, w: Int, h: Int) {
        if (!isActive) return
        frameWidth = w
        frameHeight = h
        // BGRA → ARGB swap: Skia escribe BGRA en little-endian, Android espera ARGB_8888
        for (i in argbPixels.indices) {
            val p = argbPixels[i]
            argbPixels[i] = (p and 0xFF00FF00.toInt()) or ((p shr 16) and 0xFF) or ((p shl 16) and 0xFF0000.toInt())
        }
        frameBitmap?.recycle()
        frameBitmap = Bitmap.createBitmap(argbPixels, w, h, Bitmap.Config.ARGB_8888)
        updateTransform()
        postInvalidateOnAnimation()
    }

    private fun updateTransform() {
        transformMatrix.reset()
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (frameWidth <= 0 || frameHeight <= 0 || viewW <= 0 || viewH <= 0) return

        val baseScale = Math.min(viewW / frameWidth, viewH / frameHeight)
        val cx = viewW / 2f
        val cy = viewH / 2f
        transformMatrix.postTranslate(-frameWidth / 2f, -frameHeight / 2f)
        transformMatrix.postScale(baseScale * scaleFactor, baseScale * scaleFactor, 0f, 0f)
        transformMatrix.postTranslate(cx + offsetX, cy + offsetY)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = frameBitmap ?: return
        if (!isActive) return

        // Subtle backdrop only behind the graph, not full screen
        val src = RectF(0f, 0f, frameWidth.toFloat(), frameHeight.toFloat())
        val dst = RectF()
        transformMatrix.mapRect(dst, src)
        canvas.drawRoundRect(dst, 8f, 8f, backdropPaint)
        canvas.drawBitmap(bmp, transformMatrix, paint)
    }

    private val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xBB111111.toInt()
        setShadowLayer(12f, 0f, 0f, 0x80000000.toInt())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isActive) return false
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
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
                val dx = event.x - downX
                val dy = event.y - downY
                val dist = Math.sqrt((dx * dx + dy * dy).toDouble())
                if (dist < 10.0) {
                    // Single tap = dismiss overlay
                    hide()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun dismiss() {
        hide()
    }
}
