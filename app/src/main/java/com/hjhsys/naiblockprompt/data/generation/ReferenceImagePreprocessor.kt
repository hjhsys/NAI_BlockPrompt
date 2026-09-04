package com.hjhsys.naiblockprompt.data.generation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import java.io.ByteArrayOutputStream

object ReferenceImagePreprocessor {
    fun preciseReferencePng(source: ByteArray): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(source, 0, source.size)
            ?: throw IllegalArgumentException("Reference image could not be decoded")
        val (targetWidth, targetHeight) = targetSize(bitmap.width, bitmap.height)
        val scale = minOf(targetWidth.toFloat() / bitmap.width, targetHeight.toFloat() / bitmap.height)
        val scaledWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            drawColor(Color.BLACK)
            drawBitmap(scaled, (targetWidth - scaledWidth) / 2f, (targetHeight - scaledHeight) / 2f, null)
        }
        return ByteArrayOutputStream().use { stream ->
            output.compress(Bitmap.CompressFormat.PNG, 100, stream)
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            output.recycle()
            stream.toByteArray()
        }
    }

    fun targetSize(width: Int, height: Int): Pair<Int, Int> = when {
        width > height -> 1536 to 1024
        height > width -> 1024 to 1536
        else -> 1472 to 1472
    }
}
