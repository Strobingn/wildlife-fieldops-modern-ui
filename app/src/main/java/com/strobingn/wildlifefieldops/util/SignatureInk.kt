package com.strobingn.wildlifefieldops.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

object SignatureInk {
    fun encodePng(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        val scaled = if (bitmap.width > 640) {
            val height = (bitmap.height * (640f / bitmap.width)).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, 640, height, true)
        } else {
            bitmap
        }
        scaled.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    fun decodePng(raw: String): Bitmap? {
        val text = raw.trim()
        if (text.isBlank()) return null
        return runCatching {
            val bytes = Base64.decode(text, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
}
