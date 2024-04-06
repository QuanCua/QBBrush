package com.apero.qbbrush

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View

class QBBrushView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var bmOrigin: Bitmap? = null
    private var bmRemoved: Bitmap? = null

    private val brushManager = BrushManager()

    private val showingRect = RectF()
    private val clipPath = Path()

    private val showingPathGroup = clipPath

    private val objStrokePaint = Paint()
    private val objFillPaint = Paint()

    private var isDisableMask = false
    private var isShowOrigin = false

    /** ====================== Mini map (magnifier) ======================= */
    private var miniMapShader: BitmapShader? = null
    private val miniMapMatrix = Matrix()
    private val miniMapPaint = Paint()
    private val miniMapCenterPoint = PointF()
    private val miniMapSrcRect = RectF() // vị trí ngón tay
    private val miniMapDstRect = RectF() // vị trí tịnh tiến

    private var canvasWidth = 0
    private var canvasHeight = 0
    private var showingRectMargin = canvasWidth * 0.05f

    private val zoomValue = 2f //(200%)
    private var miniMapSize = 200f
    private var isShowMiniMap = false
    private var cacheBitmap: Bitmap? = null
    private var minimapBitmap: Bitmap? = null
    private var cacheBitmapCanvas: Canvas? = null

    private fun initMiniMap(bmOrigin: Bitmap) {
        miniMapShader = BitmapShader(bmOrigin, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        miniMapPaint.shader = miniMapShader
        miniMapPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
    }

    private fun updateMiniMap() {
        miniMapShader?.let {
            isShowMiniMap = true

            val widthRatio = bmRemoved!!.width.toFloat() / canvasWidth.toFloat()
            val heightRatio = bmRemoved!!.height.toFloat() / canvasHeight.toFloat()

            miniMapSrcRect.apply {
                this.left = miniMapCenterPoint.x - 50
                this.top = miniMapCenterPoint.y - 50
                this.right = miniMapCenterPoint.x + 50
                this.bottom = miniMapCenterPoint.y + 50
            }
            miniMapMatrix.reset()
            miniMapMatrix.postScale(
                zoomValue,
                zoomValue,
                miniMapCenterPoint.x * widthRatio,
                miniMapCenterPoint.y * heightRatio
            )
            miniMapMatrix.postTranslate(
                miniMapDstRect.centerX() - miniMapCenterPoint.x * widthRatio,
                miniMapDstRect.centerY() - miniMapCenterPoint.y * heightRatio
            )
            it.setLocalMatrix(miniMapMatrix)

            miniMapMatrix.setRectToRect(miniMapSrcRect, miniMapDstRect, Matrix.ScaleToFit.CENTER)
        }
    }

    /** ======================================================= */

    var onObjectDetectedClick: ((String, Boolean) -> Unit)? = null

    init {
        objStrokePaint.apply {
            isAntiAlias = true
            color = Color.parseColor("#94D18A")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isDither = true
        }

        objFillPaint.apply {
            isAntiAlias = true
            color = Color.parseColor("#F94343")
            style = Paint.Style.FILL
            alpha = (255f * 0.7f).toInt()
        }
    }

    fun setBrushSizePercent(sizePercent: Double) {
        brushManager.setBrushSize(sizePercent)
    }

    fun setBitmapOrigin(bmOrigin: Bitmap) {
        this.bmOrigin = bmOrigin
        this.bmRemoved = bmOrigin
        cacheBitmap = Bitmap.createBitmap(
            (bmRemoved!!.width + miniMapSize).toInt(),
            (bmRemoved!!.height + miniMapSize).toInt(),
            Bitmap.Config.ARGB_8888
        )
        cacheBitmapCanvas = Canvas(cacheBitmap!!)
        cacheBitmapCanvas!!.drawColor(Color.WHITE, PorterDuff.Mode.SRC)
        cacheBitmapCanvas!!.drawBitmap(bmOrigin, 100f, 100f, null)
        Log.i("TAG", "setBitmapRemoved: $cacheBitmap")
        initMiniMap(bmOrigin)

        requestLayout()
        invalidate()
    }

    fun setBitmapRemoved(bmRemoved: Bitmap) {
        cacheBitmap?.recycle()
        cacheBitmap = null
        this.bmRemoved = bmRemoved
        cacheBitmap = Bitmap.createBitmap(bmRemoved)
        Log.i("TAG", "setBitmapRemoved: $cacheBitmap")
        cacheBitmapCanvas = Canvas(cacheBitmap!!)
        requestLayout()
        invalidate()
    }

    fun setShowOrigin(isShow: Boolean) {
        isShowOrigin = isShow
        invalidate()
    }

    fun getPreviewBitmap(): Bitmap {
        disableMaskObj()
        val bitmap = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas)
        enableMaskObj()
        return bitmap
    }

    private fun disableMaskObj() {
        isDisableMask = true
        invalidate()
    }

    private fun enableMaskObj() {
        isDisableMask = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isShowOrigin) {
            drawOriginImage(canvas)
        } else {
            drawCurrentImage(canvas)
        }
    }

    private fun drawOriginImage(canvas: Canvas) {
        bmOrigin?.let {
            canvas.drawBitmap(it, 0F, 0F, null)
        }
    }

    private fun drawCurrentImage(canvas: Canvas) {
        drawImage(canvas)

        val brushBitmap = brushManager.getEditBitmap()
        if (brushBitmap != null) {
            canvas.drawBitmap(brushBitmap, 0f, 0f, null)
        }
        if (brushManager.isUserActionDown()) {
            brushManager.drawTouchDownCircle(canvas)
        }

        drawMiniMap(canvas)
    }

    private var previewRadius = miniMapSize / 2

    private fun drawMiniMap(canvas: Canvas) {
        if (isShowMiniMap) {
//            canvas.drawRect(miniMapSrcRect, miniMapPaint)
            /*canvas.drawRect(miniMapDstRect, miniMapPaint)*/
            if (minimapBitmap != null) {
                canvas.save()

                clipPath.apply {
                    reset()
                    addCircle(
                        miniMapDstRect.centerX() + showingRectMargin,
                        miniMapDstRect.centerY() + showingRectMargin,
                        previewRadius,
                        Path.Direction.CW
                    )
                }
                canvas.clipPath(clipPath)
                canvas.drawBitmap(minimapBitmap!!, showingRect.left, showingRect.top, null)
                canvas.restore()
                val x = (2 * showingRect.left + minimapBitmap!!.width) / 2
                val y = (2 * showingRect.top + minimapBitmap!!.height) / 2
                canvas.drawCircle(
                    x,
                    y,
                    previewRadius,
                    brushManager.touchCirclePaint
                )
                canvas.drawCircle(
                    x,
                    y,
                    brushManager.touchCircleRadius,
                    brushManager.touchCirclePaint
                )
            }
        }
    }

    private fun drawImage(canvas: Canvas) {
        bmRemoved?.let {
            canvas.drawBitmap(it, 0F, 0F, null)
        } ?: run {
            bmOrigin?.let {
                canvas.drawBitmap(it, 0F, 0F, null)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        miniMapCenterPoint.x = event.x
        miniMapCenterPoint.y = event.y
        when {
            event.action == MotionEvent.ACTION_UP -> {
                isShowMiniMap = false
                brushManager.onTouchUp(event)
                invalidate()

                performClick()
            }

            event.action == MotionEvent.ACTION_MOVE -> {
                if (bmRemoved != null && brushManager.getEditBitmap() != null && cacheBitmap != null) {
                    updateMinimapBitmap(event.x.toInt(), event.y.toInt())
                }
                updateDestinationRect(event)
                updateMiniMap()
                brushManager.onMove(event)
                invalidate()
            }

            event.action == MotionEvent.ACTION_DOWN -> {
                isShowMiniMap = true
                if (bmRemoved != null && brushManager.getEditBitmap() != null && cacheBitmap != null) {
                    updateMinimapBitmap(event.x.toInt(), event.y.toInt())
                }
                updateDestinationRect(event)
                updateMiniMap()
                brushManager.onTouchDown(event)
                invalidate()
            }
        }
        return true
    }

    private fun updateMinimapBitmap(x: Int, y: Int) {
        var eventX = x
        var eventY = y
        if (eventX + miniMapSize > cacheBitmap!!.width) {
            eventX = (cacheBitmap!!.width - miniMapSize).toInt()
        }
        if (eventY + miniMapSize > cacheBitmap!!.height) {
            eventY = (cacheBitmap!!.height - miniMapSize).toInt()
        }
        if (eventX < 0) {
            eventX = 0
        }
        if (eventY < 0) {
            eventY = 0
        }
        cacheBitmapCanvas?.drawColor(Color.WHITE, PorterDuff.Mode.SRC)
        cacheBitmapCanvas?.drawBitmap(bmRemoved!!, previewRadius, previewRadius, null)
        cacheBitmapCanvas?.drawBitmap(brushManager.getEditBitmap()!!, previewRadius, previewRadius, null)
        minimapBitmap?.recycle()
        minimapBitmap = null
        minimapBitmap = Bitmap.createBitmap(
            cacheBitmap!!,
            eventX,
            eventY,
            miniMapSize.toInt(),
            miniMapSize.toInt(),
        )
    }

    private fun updateDestinationRect(event: MotionEvent) {
        val touchPointInsidePreview = isTouchPointInsidePreview(event.x, event.y)
        if (touchPointInsidePreview) {
            if (miniMapDstRect.left == 0f) {
                miniMapDstRect.apply {
                    top = 0f
                    bottom = miniMapSize
                    right = showingRect.right
                    left = showingRect.right - miniMapSize
                }
            } else {
                miniMapDstRect.apply {
                    top = 0f
                    bottom = miniMapSize
                    right = miniMapSize
                    left = 0f
                }
            }
        }
    }

    private fun isTouchPointInsidePreview(x: Float, y: Float): Boolean {
        return x in miniMapDstRect.left..miniMapDstRect.right &&
                y in miniMapDstRect.top..miniMapDstRect.bottom
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        canvasWidth = right - left
        canvasHeight = bottom - top
        showingRectMargin = canvasWidth * 0.05f
        val sizeDependParent = canvasWidth / 3f
        if (sizeDependParent < miniMapSize) {
            miniMapSize = sizeDependParent
            previewRadius = miniMapSize / 2
            brushManager.setBrushValueRange(miniMapSize / 3, miniMapSize / 2)
        }
        bmOrigin?.let {
            showingRect.apply {
                this.left = this@QBBrushView.width.toFloat() / 2f - it.width.toFloat() / 2f + showingRectMargin
                this.top = this@QBBrushView.height.toFloat() / 2f - it.height.toFloat() / 2f + showingRectMargin
                this.right = this@QBBrushView.width.toFloat() / 2f + it.width.toFloat() / 2f + showingRectMargin
                this.bottom = this@QBBrushView.height.toFloat() / 2f + it.height.toFloat() / 2f + showingRectMargin
            }
            brushManager.initializeEditLayer(
                (showingRect.right - showingRect.left).toInt(),
                (showingRect.bottom - showingRect.top).toInt(),
            )
        }

        miniMapDstRect.apply {
            this.left = 0f
            this.top = 0f
            this.right = miniMapSize
            this.bottom = miniMapSize
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        bmOrigin?.let {
            setMeasuredDimension(it.width, it.height)
        }
    }

    fun undo() {
        brushManager.undo()
        invalidate()
    }

    fun redo() {
        brushManager.redo()
        invalidate()
    }

    fun getBlackWhiteBitmap(): Bitmap? {
        return brushManager.getBlackWhiteBitmap()
    }
}