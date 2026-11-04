package com.strobingn.wildlifefieldops.ai.detection

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Samples a still down to the 8×8 average hash used by correction memory. */
object FrameAverageHashEncoder {
    fun fromBitmap(bitmap: Bitmap): List<Float> {
        if (bitmap.width <= 0 || bitmap.height <= 0) return emptyList()
        val scaled = Bitmap.createScaledBitmap(bitmap, FrameAverageHash.EDGE, FrameAverageHash.EDGE, true)
        val gray = IntArray(FrameAverageHash.LENGTH)
        for (y in 0 until FrameAverageHash.EDGE) {
            for (x in 0 until FrameAverageHash.EDGE) {
                val color = scaled.getPixel(x, y)
                val red = (color shr 16) and 0xff
                val green = (color shr 8) and 0xff
                val blue = color and 0xff
                gray[y * FrameAverageHash.EDGE + x] = (red * 30 + green * 59 + blue * 11) / 100
            }
        }
        if (scaled !== bitmap) scaled.recycle()
        return FrameAverageHash.fromGrid(gray)
    }

    fun fromFile(path: String): List<Float> {
        val file = File(path)
        if (!file.isFile || file.length() <= 0L) return emptyList()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return emptyList()
        var sample = 1
        while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) sample *= 2
        val decoded = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return emptyList()
        val vector = fromBitmap(decoded)
        if (!decoded.isRecycled) decoded.recycle()
        return vector
    }
}
