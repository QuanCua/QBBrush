package com.apero.qbbrush

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.apero.qbbrush.databinding.ActivityMainBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Glide.with(this)
            .asBitmap()
            .load(R.drawable.idol_ahihi)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    val bm = calculateBitmap(resource)
                    binding.qbBrush.setBitmapOrigin(bm)
                }

                override fun onLoadCleared(placeholder: Drawable?) {

                }

            })
    }

    fun calculateBitmap(oldBitmap: Bitmap): Bitmap {
        val ratio = oldBitmap.width.toFloat() / oldBitmap.height.toFloat()
        val widthScreen = Resources.getSystem().displayMetrics.widthPixels.toFloat()
        return if (oldBitmap.width > widthScreen || oldBitmap.height > widthScreen) {
            val bitmap = if (ratio > 1f) {
                resizeBitmap(
                    oldBitmap,
                    widthScreen,
                    widthScreen / ratio
                )
            } else {
                resizeBitmap(
                    oldBitmap,
                    widthScreen * ratio,
                    widthScreen
                )
            }
            bitmap
        } else
            oldBitmap
    }

    fun resizeBitmap(bitmap: Bitmap, newWidth: Float, newHeight: Float): Bitmap {
        val bitmapOrigin = bitmap.copy(bitmap.config, false)
        return if (newWidth > 0f && newHeight > 0f) {
            val width = bitmapOrigin.width.toFloat()
            val height = bitmapOrigin.height.toFloat()
            val scaleWidth = newWidth / width
            val scaleHeight = newHeight / height
            val matrix = Matrix()
            matrix.postScale(scaleWidth, scaleHeight)
            Bitmap.createBitmap(bitmapOrigin, 0, 0, width.toInt(), height.toInt(), matrix, true)
        } else {
            bitmap
        }
    }
}
