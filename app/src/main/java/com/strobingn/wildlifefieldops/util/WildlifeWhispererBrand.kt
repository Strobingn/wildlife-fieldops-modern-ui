package com.strobingn.wildlifefieldops.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Base64

/**
 * Shared Wildlife Whisperer LLC branding for PDF exports and UI.
 * Prefer the circular company logo (transparent outside the circle) —
 * never the FieldOps neon squirrel mark.
 */
object WildlifeWhispererBrand {
    const val COMPANY = "Wildlife Whisperer LLC"
    const val COMPANY_UPPER = "WILDLIFE WHISPERER LLC"
    const val ADDRESS = "210 Willow Avenue, Cornwall, New York 12518"
    const val PHONE = "(845) 751-8448"
    const val EMAIL = "austin@wildlifewhispererllc.com"
    const val TAGLINE = "Nuisance Wildlife Control · Cornwall, NY"

    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /**
     * Load the circular Wildlife Whisperer logo (ARGB, transparent corners).
     * Order: PNG asset → PNG base64 → webp base64 → drawable resource.
     */
    fun loadLogo(context: Context): Bitmap? {
        decodeAssetPng(context, "wildlife_whisperer_logo.png")?.let { return ensureArgb(it) }
        decodeAssetBase64(context, "wildlife_whisperer_logo.png.b64")?.let { return ensureArgb(it) }
        decodeAssetBase64(context, "wildlife_whisperer_logo.webp.b64")?.let { return ensureArgb(it) }
        return decodeDrawable(context, "wildlife_whisperer_logo")?.let { ensureArgb(it) }
    }

    /** Draw the circular logo into a PDF/UI canvas with bilinear filtering. */
    fun drawLogo(canvas: Canvas, context: Context, left: Float, top: Float, sizePx: Int) {
        val logo = loadLogo(context) ?: return
        val scaled = if (logo.width == sizePx && logo.height == sizePx) {
            logo
        } else {
            Bitmap.createScaledBitmap(logo, sizePx, sizePx, true)
        }
        canvas.drawBitmap(scaled, left, top, logoPaint)
        if (scaled !== logo) scaled.recycle()
    }

    private fun ensureArgb(src: Bitmap): Bitmap {
        if (src.config == Bitmap.Config.ARGB_8888) return src
        val copy = src.copy(Bitmap.Config.ARGB_8888, false)
        if (copy !== src) src.recycle()
        return copy
    }

    private fun decodeAssetPng(context: Context, assetName: String): Bitmap? {
        return try {
            context.assets.open(assetName).use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeAssetBase64(context: Context, assetName: String): Bitmap? {
        return try {
            context.assets.open(assetName).bufferedReader().use { reader ->
                val bytes = Base64.decode(reader.readText().trim(), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeDrawable(context: Context, name: String): Bitmap? {
        return try {
            val id = context.resources.getIdentifier(name, "drawable", context.packageName)
            if (id != 0) BitmapFactory.decodeResource(context.resources, id) else null
        } catch (_: Exception) {
            null
        }
    }
}
