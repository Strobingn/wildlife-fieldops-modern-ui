package com.strobingn.wildlifefieldops.ai.camera

import androidx.camera.core.ImageProxy
import kotlin.math.abs

/** Cheap Y-plane sharpness / brightness probe for live guidance (no VLM). */
object LumaQualityProbe {
    fun probe(image: ImageProxy, subjectCoverage: Float = 0f, labelHints: List<String> = emptyList()): CaptureQualitySignals {
        val plane = image.planes.firstOrNull() ?: return CaptureQualitySignals(0f, 0f, subjectCoverage, labelHints)
        val buf = plane.buffer.duplicate()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height
        // Subsample for speed
        val stepX = (width / 80).coerceAtLeast(2)
        val stepY = (height / 60).coerceAtLeast(2)
        var sum = 0L
        var count = 0
        var lapSum = 0.0
        var lapSq = 0.0
        var lapN = 0
        var y = 0
        while (y < height - stepY) {
            var x = stepX
            while (x < width - stepX) {
                val idx = y * rowStride + x * pixelStride
                if (idx < buf.limit()) {
                    val v = buf.get(idx).toInt() and 0xff
                    sum += v
                    count++
                    val left = buf.get(y * rowStride + (x - stepX) * pixelStride).toInt() and 0xff
                    val right = buf.get(y * rowStride + (x + stepX) * pixelStride).toInt() and 0xff
                    val up = buf.get((y - stepY) * rowStride + x * pixelStride).toInt() and 0xff
                    val down = buf.get((y + stepY) * rowStride + x * pixelStride).toInt() and 0xff
                    val lap = (abs(v - left) + abs(v - right) + abs(v - up) + abs(v - down)).toDouble()
                    lapSum += lap
                    lapSq += lap * lap
                    lapN++
                }
                x += stepX
            }
            y += stepY
        }
        val mean = if (count > 0) sum.toFloat() / count else 0f
        val sharpness = if (lapN > 1) {
            val m = lapSum / lapN
            ((lapSq / lapN) - m * m).toFloat().coerceAtLeast(0f)
        } else {
            0f
        }
        return CaptureQualitySignals(
            meanLuma = mean,
            sharpness = sharpness,
            subjectCoverage = subjectCoverage,
            labelHints = labelHints
        )
    }
}
