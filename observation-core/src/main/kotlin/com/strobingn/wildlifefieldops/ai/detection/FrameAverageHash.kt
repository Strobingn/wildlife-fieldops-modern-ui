package com.strobingn.wildlifefieldops.ai.detection

import kotlin.math.sqrt

/**
 * 8×8 average hash. Present on device, so correction memory can require a
 * similar frame instead of trusting the model label alone.
 * A missing hash falls back to the label match in [DetectionCorrectionMemory].
 */
object FrameAverageHash {
    const val EDGE = 8
    const val LENGTH = EDGE * EDGE

    /** [gray] is row-major luminance, length [LENGTH]. Values above the mean are +1. */
    fun fromGrid(gray: IntArray): List<Float> {
        if (gray.size != LENGTH) return emptyList()
        val mean = gray.average()
        return List(LENGTH) { index -> if (gray[index] > mean) 1f else -1f }
    }

    fun cosine(left: List<Float>, right: List<Float>): Float {
        if (left.isEmpty() || left.size != right.size) return 0f
        var dot = 0.0
        var leftNorm = 0.0
        var rightNorm = 0.0
        for (index in left.indices) {
            val a = left[index].toDouble()
            val b = right[index].toDouble()
            dot += a * b
            leftNorm += a * a
            rightNorm += b * b
        }
        if (leftNorm <= 0.0 || rightNorm <= 0.0) return 0f
        return (dot / (sqrt(leftNorm) * sqrt(rightNorm))).toFloat()
    }

    fun encode(vector: List<Float>): String =
        if (vector.size != LENGTH) "" else vector.joinToString(",") { if (it >= 0f) "1" else "0" }

    fun decode(raw: String): List<Float> {
        val parts = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size != LENGTH) return emptyList()
        return parts.map { token ->
            when (token) {
                "1", "+1", "1.0" -> 1f
                "0", "-1", "-1.0" -> -1f
                else -> return emptyList()
            }
        }
    }
}
