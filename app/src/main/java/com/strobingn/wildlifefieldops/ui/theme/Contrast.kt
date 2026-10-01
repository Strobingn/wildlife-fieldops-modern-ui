package com.strobingn.wildlifefieldops.ui.theme

import kotlin.math.pow

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
