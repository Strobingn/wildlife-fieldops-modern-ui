package com.strobingn.wildlifefieldops.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

/**
 * Shared Wildlife Whisperer LLC branding for PDF exports and UI.
 * Prefer the circular company logo — never the FieldOps neon squirrel mark.
 */
object WildlifeWhispererBrand {
    const val COMPANY = "Wildlife Whisperer LLC"
    const val COMPANY_UPPER = "WILDLIFE WHISPERER LLC"
    const val ADDRESS = "210 Willow Avenue, Cornwall, New York 12518"
    const val PHONE = "(845) 751-8448"
    const val EMAIL = "austin@wildlifewhispererllc.com"
    const val TAGLINE = "Nuisance Wildlife Control · Cornwall, NY"

    /**
     * Load the circular Wildlife Whisperer logo.
     * Order: PNG asset → PNG base64 → webp base64 → drawable resource.
     */
    fun loadLogo(context: Context): Bitmap? {
        decodeAssetPng(context, "wildlife_whisperer_logo.png")?.let { return it }
        decodeAssetBase64(context, "wildlife_whisperer_logo.png.b64")?.let { return it }
        decodeAssetBase64(context, "wildlife_whisperer_logo.webp.b64")?.let { return it }
        return decodeDrawable(context, "wildlife_whisperer_logo")
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
