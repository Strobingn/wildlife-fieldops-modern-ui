package com.strobingn.wildlifefieldops.ui.theme

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * WCAG 2.x relative-luminance contrast. Used by tests and to document AA targets
 * for FieldOps light/dark tokens. Packed ARGB as `0xAARRGGBB` longs.
 */
object Contrast {
    const val AA_NORMAL = 4.5
    const val AA_LARGE = 3.0
    const val AA_UI = 3.0

    fun ratio(foregroundArgb: Long, backgroundArgb: Long): Double {
        val lighter = maxOf(luminance(foregroundArgb), luminance(backgroundArgb))
        val darker = minOf(luminance(foregroundArgb), luminance(backgroundArgb))
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun passesAa(foregroundArgb: Long, backgroundArgb: Long, minRatio: Double = AA_NORMAL): Boolean =
        ratio(foregroundArgb, backgroundArgb) + 1e-9 >= minRatio

    /**
     * Src-over composite of an opaque [srcArgb] at [srcAlpha] onto opaque [dstArgb].
     * Matches a Compose color drawn with `copy(alpha = srcAlpha)` over a solid fill.
     */
    fun composite(srcArgb: Long, dstArgb: Long, srcAlpha: Double): Long {
        require(srcAlpha in 0.0..1.0)
        fun channel(src: Int, dst: Int): Int =
            (src * srcAlpha + dst * (1.0 - srcAlpha)).roundToInt().coerceIn(0, 255)
        val r = channel(((srcArgb shr 16) and 0xFF).toInt(), ((dstArgb shr 16) and 0xFF).toInt())
        val g = channel(((srcArgb shr 8) and 0xFF).toInt(), ((dstArgb shr 8) and 0xFF).toInt())
        val b = channel((srcArgb and 0xFF).toInt(), (dstArgb and 0xFF).toInt())
        return (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    }

    /** Linear blend from [startArgb] at t=0 to [endArgb] at t=1. */
    fun blend(startArgb: Long, endArgb: Long, t: Double): Long = composite(endArgb, startArgb, t)

    /**
     * True for yellow, amber, gold, orange, and lime hues.
     * Near-grays and reds (hue under 12° or over 105°) are not flagged.
     */
    fun isYellowAmberOrangeOrLime(argb: Long): Boolean {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        if (delta < 24) return false
        val sector = when (max) {
            r -> {
                val raw = (g - b).toDouble() / delta
                if (raw < 0) raw + 6 else raw
            }
            g -> (b - r).toDouble() / delta + 2
            else -> (r - g).toDouble() / delta + 4
        }
        return sector * 60.0 in 12.0..105.0
    }

    fun luminance(argb: Long): Double {
        fun channel(value: Int): Double {
            val s = value / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        val r = channel(((argb shr 16) and 0xFF).toInt())
        val g = channel(((argb shr 8) and 0xFF).toInt())
        val b = channel((argb and 0xFF).toInt())
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
}
