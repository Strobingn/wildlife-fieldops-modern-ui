package com.strobingn.wildlifefieldops.ai.camera

import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Cheap Y-plane sharpness / brightness probe for live guidance (no VLM).
 *
 * Samsung (and other) CameraX YUV_420_888 buffers often have rowStride >> width and
 * may pad the plane. Neighbor sampling MUST never use negative indices — the prior
 * `y = 0` loop with `y - stepY` produced IndexOutOfBoundsException (e.g. index=-5112)
 * and crashed Live Capture on analyze.
 */
object LumaQualityProbe {
    fun probe(
        image: ImageProxy,
        subjectCoverage: Float = 0f,
        labelHints: List<String> = emptyList()
    ): CaptureQualitySignals {
        return try {
            probeUnsafe(image, subjectCoverage, labelHints)
        } catch (_: Throwable) {
            // Never crash the CameraX analyzer thread on a bad/odd YUV layout.
            CaptureQualitySignals(0f, 0f, subjectCoverage, labelHints)
        }
    }

    private fun probeUnsafe(
        image: ImageProxy,
        subjectCoverage: Float,
        labelHints: List<String>
    ): CaptureQualitySignals {
        val plane = image.planes.firstOrNull()
            ?: return CaptureQualitySignals(0f, 0f, subjectCoverage, labelHints)
        val buf = plane.buffer.duplicate()
        val rowStride = plane.rowStride.coerceAtLeast(1)
        val pixelStride = plane.pixelStride.coerceAtLeast(1)
        val width = image.width
        val height = image.height
        // Prefer limit(); coerce against capacity for OEM buffers that report odd limits.
        val bound = maxOf(buf.limit(), 0).coerceAtMost(maxOf(buf.capacity(), 0)).let { lim ->
            if (lim > 0) lim else maxOf(buf.capacity(), 0)
        }
        if (width < 8 || height < 8 || bound <= 0) {
            return CaptureQualitySignals(0f, 0f, subjectCoverage, labelHints)
        }
        // Subsample for speed. Start at stepX/stepY so neighbor reads stay non-negative.
        val stepX = (width / 80).coerceAtLeast(2)
        val stepY = (height / 60).coerceAtLeast(2)
        if (height <= stepY * 2 || width <= stepX * 2) {
            return CaptureQualitySignals(0f, 0f, subjectCoverage, labelHints)
        }
        var sum = 0L
        var count = 0
        var lapSum = 0.0
        var lapSq = 0.0
        var lapN = 0
        var y = stepY
        while (y < height - stepY) {
            var x = stepX
            while (x < width - stepX) {
                val v = safeGet(buf, bound, y, x, rowStride, pixelStride)
                val left = safeGet(buf, bound, y, x - stepX, rowStride, pixelStride)
                val right = safeGet(buf, bound, y, x + stepX, rowStride, pixelStride)
                val up = safeGet(buf, bound, y - stepY, x, rowStride, pixelStride)
                val down = safeGet(buf, bound, y + stepY, x, rowStride, pixelStride)
                if (v != null && left != null && right != null && up != null && down != null) {
                    sum += v
                    count++
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

    /** Returns null instead of throwing when the computed index is out of range / negative. */
    private fun safeGet(
        buf: ByteBuffer,
        bound: Int,
        y: Int,
        x: Int,
        rowStride: Int,
        pixelStride: Int
    ): Int? {
        if (y < 0 || x < 0 || bound <= 0) return null
        // Long math avoids overflow on large Samsung strides before the range check.
        val idxLong = y.toLong() * rowStride.toLong() + x.toLong() * pixelStride.toLong()
        if (idxLong < 0L || idxLong >= bound.toLong()) return null
        val idx = idxLong.toInt()
        return try {
            buf.get(idx).toInt() and 0xff
        } catch (_: Throwable) {
            null
        }
    }
}
