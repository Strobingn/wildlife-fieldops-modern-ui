package com.strobingn.wildlifefieldops.ai.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Optional on-device custom wildlife evidence model (TFLite).
 *
 * Loads `assets/models/wildlife_evidence.tflite` when present. When the asset is
 * missing, TFLite is not on the classpath, or inference fails, returns empty and
 * never throws — callers must fall back to ML Kit + [WildlifeEvidenceDetector] lexicon.
 *
 * This is a compile-safe stub: no hard dependency on org.tensorflow.lite.Interpreter.
 * When a future build ships TFLite + a trained model, [tryInfer] can be swapped to a
 * real Interpreter path without changing call sites.
 */
object CustomEvidenceModel {
    private const val TAG = "CustomEvidenceModel"
    const val ASSET_PATH = "models/wildlife_evidence.tflite"

    @Volatile
    private var probed: Boolean = false

    @Volatile
    private var assetPresent: Boolean = false

    fun isAssetPresent(context: Context): Boolean {
        if (probed) return assetPresent
        synchronized(this) {
            if (probed) return assetPresent
            assetPresent = try {
                context.assets.open(ASSET_PATH).use { true }
            } catch (_: Throwable) {
                false
            }
            probed = true
            if (!assetPresent) {
                Log.i(TAG, "No custom model at assets/$ASSET_PATH — using ML Kit + lexicon")
            }
            return assetPresent
        }
    }

    /**
     * Best-effort custom inference. Always safe: empty list on any failure.
     * Current stub never runs native TFLite; reserved for when model + runtime ship.
     */
    fun tryInfer(
        context: Context,
        bitmap: Bitmap? = null,
        labelHints: List<String> = emptyList()
    ): List<WildlifeEvidenceHit> {
        return try {
            if (!isAssetPresent(context)) return emptyList()
            // Asset exists but Interpreter runtime is not wired in this pack.
            // Keep the file cached for future use; do not crash Live Capture.
            cacheAssetQuietly(context)
            Log.i(
                TAG,
                "Custom model asset present (${bitmap?.width ?: 0}x${bitmap?.height ?: 0}, " +
                    "hints=${labelHints.size}) — Interpreter stub inactive; lexicon fallback"
            )
            emptyList()
        } catch (t: Throwable) {
            Log.w(TAG, "CustomEvidenceModel tryInfer failed (falling back): ${t.message}")
            emptyList()
        }
    }

    private fun cacheAssetQuietly(context: Context) {
        try {
            val dest = File(context.filesDir, "wildlife_evidence.tflite")
            if (dest.exists() && dest.length() > 0L) return
            context.assets.open(ASSET_PATH).use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
        } catch (t: Throwable) {
            Log.i(TAG, "Could not cache custom model asset: ${t.message}")
        }
    }
}
