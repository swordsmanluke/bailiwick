package com.perfectlunacy.bailiwick.views

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs

/**
 * ImageView that supports pinch-to-zoom and pan gestures.
 * Double-tap to reset zoom.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    companion object {
        private const val MIN_SCALE = 1.0f
        private const val MAX_SCALE = 5.0f
    }

    private val imageMatrix = Matrix()
    private val savedMatrix = Matrix()

    private var mode = Mode.NONE
    private var currentScale = 1.0f

    private val start = PointF()
    private val mid = PointF()
    private var oldDist = 1f

    private enum class Mode {
        NONE, DRAG, ZOOM
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val newScale = currentScale * scaleFactor

            if (newScale in MIN_SCALE..MAX_SCALE) {
                currentScale = newScale
                imageMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                setImageMatrix(imageMatrix)
            }
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            resetZoom()
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
        setImageMatrix(imageMatrix)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(imageMatrix)
                start.set(event.x, event.y)
                mode = Mode.DRAG
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                oldDist = spacing(event)
                if (oldDist > 10f) {
                    savedMatrix.set(imageMatrix)
                    midPoint(mid, event)
                    mode = Mode.ZOOM
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (mode == Mode.DRAG && currentScale > 1.0f) {
                    imageMatrix.set(savedMatrix)
                    val dx = event.x - start.x
                    val dy = event.y - start.y
                    imageMatrix.postTranslate(dx, dy)
                    setImageMatrix(imageMatrix)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                mode = Mode.NONE
            }
        }

        return true
    }

    private fun spacing(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt((x * x + y * y).toDouble()).toFloat()
    }

    private fun midPoint(point: PointF, event: MotionEvent) {
        if (event.pointerCount < 2) return
        val x = event.getX(0) + event.getX(1)
        val y = event.getY(0) + event.getY(1)
        point.set(x / 2, y / 2)
    }

    /**
     * Reset zoom to original scale.
     */
    fun resetZoom() {
        currentScale = 1.0f
        imageMatrix.reset()
        setImageMatrix(imageMatrix)
    }

    override fun setImageMatrix(matrix: Matrix?) {
        super.setImageMatrix(matrix)
        this.imageMatrix.set(matrix)
    }
}
