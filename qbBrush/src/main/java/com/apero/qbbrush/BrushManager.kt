package com.apero.qbbrush

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.MotionEvent
import java.util.Stack
import kotlin.math.abs

class BrushManager {

    private var editableBitmap: Bitmap? = null
    private var editableBitmapCanvas: Canvas? = null
    private var brushPaint: Paint = Paint()
    private var brushColor = Color.RED
    private val eraseMode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private val drawMode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    val touchCirclePaint: Paint = Paint()

    private val drawnPaths = mutableListOf<DrawablePath>()
    private val undoPathStack = Stack<DrawablePath>()

    private val lastTouchPoint = Point(0, 0)
    private val currentTouchPoint = Point(0, 0)
    private val systemWidth = Resources.getSystem().displayMetrics.widthPixels
    private var maxBrushSize = systemWidth * 0.24f
    private var minBrushSize = systemWidth * 0.06f
    private var currentBrushSize = ((maxBrushSize - minBrushSize) / 2).toFloat()
    private val brushAlpha = (255 * 0.4).toInt()
    var touchCircleRadius = currentBrushSize / 2
        private set

    private var isActionDown = false
    var isEraseEnable = false

    init {
        brushPaint.apply {
            strokeWidth = currentBrushSize
            isDither = true
            color = brushColor
            xfermode = drawMode
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.BEVEL
            isAntiAlias = true
        }

        touchCirclePaint.apply {
            strokeWidth = 3f
            isDither = true
            color = Color.WHITE
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
    }

    fun setBrushValueRange(min: Float, max: Float) {
        maxBrushSize = max
        minBrushSize = min
        currentBrushSize = (max + min) / 2
    }

    fun setBrushSize(percent: Double) {
        currentBrushSize = ((maxBrushSize - minBrushSize) * percent / 100 + minBrushSize).toFloat()
        touchCircleRadius = currentBrushSize / 2
        brushPaint.strokeWidth = currentBrushSize
    }

    fun undo() {
        if (canUndo().not()) {
            return
        }
        undoPathStack.push(drawnPaths.removeLast())
        invalidateEditableBitmap()
    }

    fun redo() {
        if (canRedo().not()) {
            return
        }
        drawnPaths.add(undoPathStack.pop())
        invalidateEditableBitmap()
    }

    fun isUserActionDown(): Boolean {
        return isActionDown
    }

    private fun invalidateEditableBitmap() {
        editableBitmapCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        for (path in drawnPaths) {
            brushPaint.strokeWidth = path.size
            brushPaint.xfermode = null
            if (path.isClearPath) {
                brushPaint.xfermode = eraseMode
                brushPaint.color = Color.RED
                editableBitmapCanvas?.drawPath(path.path, brushPaint)
            } else {
                brushPaint.xfermode = drawMode
                brushPaint.color = brushColor
                brushPaint.alpha = brushAlpha
                editableBitmapCanvas?.drawPath(path.path, brushPaint)
            }
        }
    }

    fun drawTouchDownCircle(canvas: Canvas) {
        canvas.drawCircle(
            currentTouchPoint.x.toFloat(),
            currentTouchPoint.y.toFloat(),
            touchCircleRadius,
            touchCirclePaint,
        )
    }

    fun onTouchUp(event: MotionEvent) {
        lastTouchPoint.x = event.x.toInt()
        lastTouchPoint.y = event.y.toInt()
        isActionDown = false
        val lastPath = drawnPaths.lastOrNull()
        lastPath?.path?.lineTo(event.x, event.y)
        drawPath(lastPath)
    }

    fun onMove(event: MotionEvent) {
        val dx: Float = abs(event.x - lastTouchPoint.x)
        val dy: Float = abs(event.y - lastTouchPoint.y)
        if (dx >= 4f || dy >= 4f) {
            val centerPointX = (event.x + lastTouchPoint.x) / 2f
            val centerPointY = (event.y + lastTouchPoint.y) / 2f
            currentTouchPoint.x = centerPointX.toInt()
            currentTouchPoint.y = centerPointY.toInt()
            val lastPath = drawnPaths.lastOrNull()
            lastPath?.path?.quadTo(
                lastTouchPoint.x.toFloat(),
                lastTouchPoint.y.toFloat(),
                centerPointX,
                centerPointY
            )
            lastTouchPoint.x = event.x.toInt()
            lastTouchPoint.y = event.y.toInt()
            drawPath(lastPath)
        }
    }

    fun onTouchDown(event: MotionEvent) {
        currentTouchPoint.x = event.x.toInt()
        currentTouchPoint.y = event.y.toInt()
        isActionDown = true
        if (undoPathStack.isNotEmpty()) {
            undoPathStack.removeAllElements()
        }
        drawnPaths.add(DrawablePath.newInstance().apply {
            isClearPath = isEraseEnable
            size = this@BrushManager.currentBrushSize
        })
        val lastPath = drawnPaths.lastOrNull()
        lastPath?.path?.moveTo(event.x, event.y)
        lastTouchPoint.x = event.x.toInt()
        lastTouchPoint.y = event.y.toInt()
        drawPath(lastPath)
    }

    private fun drawPath(lastPath: DrawablePath?) {
        if (lastPath?.isClearPath == true) {
            brushPaint.xfermode = eraseMode
        } else {
            brushPaint.xfermode = drawMode
            brushPaint.color = brushColor
            brushPaint.alpha = brushAlpha
        }
        brushPaint.strokeWidth = currentBrushSize
        lastPath?.let {
            editableBitmapCanvas?.drawPath(it.path, brushPaint)
        }
    }

    fun canUndo(): Boolean {
        return drawnPaths.isNotEmpty()
    }

    fun canRedo(): Boolean {
        return undoPathStack.isNotEmpty()
    }

    fun initializeEditLayer(width: Int, height: Int) {
        if (editableBitmap == null) {
            editableBitmap =
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        if (editableBitmapCanvas == null) {
            editableBitmapCanvas = Canvas(editableBitmap!!)
        }
    }

    fun getEditBitmap(): Bitmap? {
        return editableBitmap
    }

    fun getBlackWhiteBitmap(): Bitmap? {
        if (editableBitmap == null) {
            return null
        }
        val bitmap = Bitmap.createBitmap(editableBitmap!!)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val paint = Paint().apply {
            strokeWidth = currentBrushSize
            isDither = true
            color = brushColor
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.BEVEL
            isAntiAlias = true
        }
        for (path in drawnPaths) {
            brushPaint.strokeWidth = path.size
            if (path.isClearPath) {
                paint.color = Color.BLACK
                canvas.drawPath(path.path, paint)
            } else {
                paint.color = Color.WHITE
                canvas.drawPath(path.path, paint)
            }
        }
        return bitmap
    }
}