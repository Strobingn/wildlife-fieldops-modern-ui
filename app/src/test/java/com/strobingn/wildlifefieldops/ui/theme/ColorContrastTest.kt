package com.strobingn.wildlifefieldops.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorContrastTest {

    @Test
    fun lightBodyTextMeetsAaOnPaper() {
        val bg = FieldSwatch.Light.Background
        val card = FieldSwatch.Light.Card
        assertTrue(Contrast.passesAa(FieldSwatch.Light.OnSurface, bg))
        assertTrue(Contrast.passesAa(FieldSwatch.Light.OnSurface, card))
        assertTrue(Contrast.passesAa(FieldSwatch.Light.OnSurfaceVariant, bg))
        assertTrue(Contrast.passesAa(FieldSwatch.Light.OnSurfaceVariant, card))
        assertTrue(Contrast.passesAa(FieldSwatch.Light.OnSurfaceMuted, bg))
        assertTrue(Contrast.passesAa(FieldSwatch.Light.OnSurfaceMuted, card))
        assertTrue(Contrast.passesAa(FieldSwatch.Light.Primary, bg))
        assertTrue(Contrast.passesAa(FieldSwatch.Light.Error, card))
        assertTrue(Contrast.passesAa(FieldSwatch.Light.AccentAmber, card))
        assertTrue(Contrast.passesAa(FieldSwatch.Light.StatusPending, card))
        assertTrue(Contrast.passesAa(FieldSwatch.Light.StatusInProgress, card))
        assertTrue(Contrast.passesAa(FieldSwatch.Light.StatusCompleted, card))
        assertTrue(Contrast.passesAa(FieldSwatch.Light.StatusCancelled, card))
        assertTrue(Contrast.passesAa(FieldSwatch.Light.StatusUrgent, card))
        assertTrue(Contrast.passesAa(FieldSwatch.Light.OnPrimary, FieldSwatch.Light.Primary))
        assertTrue(Contrast.passesAa(FieldSwatch.Light.OnPrimaryContainer, FieldSwatch.Light.PrimaryContainer))
    }

    @Test
    fun darkBodyTextMeetsAaOnChrome() {
        val bg = FieldSwatch.Dark.Background
        val card = FieldSwatch.Dark.Card
        assertTrue(Contrast.passesAa(FieldSwatch.Dark.OnSurface, bg))
        assertTrue(Contrast.passesAa(FieldSwatch.Dark.OnSurface, card))
        assertTrue(Contrast.passesAa(FieldSwatch.Dark.OnSurfaceVariant, bg))
        assertTrue(Contrast.passesAa(FieldSwatch.Dark.OnSurfaceVariant, card))
        assertTrue(Contrast.passesAa(FieldSwatch.Dark.OnSurfaceMuted, card))
        assertTrue(Contrast.passesAa(FieldSwatch.Dark.Error, card))
        assertTrue(Contrast.passesAa(FieldSwatch.Dark.AccentAmber, card))
        assertTrue(Contrast.passesAa(FieldSwatch.Dark.StatusPending, card))
        assertTrue(Contrast.passesAa(FieldSwatch.Dark.StatusInProgress, card))
        assertTrue(Contrast.passesAa(FieldSwatch.Dark.Primary, card))
        assertTrue(Contrast.passesAa(FieldSwatch.Dark.OnPrimary, FieldSwatch.Dark.Primary))
        assertTrue(Contrast.passesAa(FieldSwatch.Dark.OnPrimaryContainer, FieldSwatch.Dark.PrimaryContainer))
    }

    @Test
    fun primaryAndAccentsAreNeutralGray() {
        listOf(
            FieldSwatch.Light.Primary,
            FieldSwatch.Light.PrimaryDark,
            FieldSwatch.Light.PrimaryLight,
            FieldSwatch.Light.PrimaryContainer,
            FieldSwatch.Light.AccentBlue,
            FieldSwatch.Light.AccentPurple,
            FieldSwatch.Light.AccentOrange,
            FieldSwatch.Light.AccentCyan,
            FieldSwatch.Light.AccentPink,
            FieldSwatch.Light.AccentAmber,
            FieldSwatch.Light.GradientStart,
            FieldSwatch.Light.GradientMid,
            FieldSwatch.Light.GradientEnd,
            FieldSwatch.Dark.Primary,
            FieldSwatch.Dark.PrimaryDark,
            FieldSwatch.Dark.PrimaryLight,
            FieldSwatch.Dark.PrimaryContainer,
            FieldSwatch.Dark.AccentBlue,
            FieldSwatch.Dark.AccentPurple,
            FieldSwatch.Dark.AccentOrange,
            FieldSwatch.Dark.AccentCyan,
            FieldSwatch.Dark.AccentPink,
            FieldSwatch.Dark.AccentAmber,
            FieldSwatch.Dark.GradientStart,
            FieldSwatch.Dark.GradientMid,
            FieldSwatch.Dark.GradientEnd
        ).forEach { argb ->
            val r = ((argb shr 16) and 0xFF).toInt()
            val g = ((argb shr 8) and 0xFF).toInt()
            val b = (argb and 0xFF).toInt()
            assertEquals("expected achromatic gray for ${argb.toString(16)}", r, g)
            assertEquals("expected achromatic gray for ${argb.toString(16)}", g, b)
        }
    }

    @Test
    fun semanticColorsStayChromatic() {
        assertTrue(isGreen(FieldSwatch.Light.Success))
        assertTrue(isGreen(FieldSwatch.Dark.Success))
        assertTrue(isGreen(FieldSwatch.Light.StatusCompleted))
        assertTrue(isRed(FieldSwatch.Light.Error))
        assertTrue(isRed(FieldSwatch.Dark.Error))
        assertTrue(isAmber(FieldSwatch.Light.StatusUrgent))
        assertTrue(isAmber(FieldSwatch.Dark.StatusUrgent))
    }

    @Test
    fun cameraOverlayStaysLightOnDark() {
        assertTrue(Contrast.passesAa(FieldSwatch.OverlayOnDark, FieldSwatch.OverlayScrim))
        assertTrue(Contrast.passesAa(FieldSwatch.OverlayOnDarkMuted, FieldSwatch.OverlayScrim))
        assertTrue(Contrast.passesAa(FieldSwatch.OnPaper, FieldSwatch.Paper))
    }

    @Test
    fun knownPairHasDocumentedRatio() {
        val ratio = Contrast.ratio(FieldSwatch.Light.OnSurface, FieldSwatch.Light.Background)
        assertTrue("onSurface/background ratio was $ratio", ratio >= 12.0)
    }

    private fun isGreen(argb: Long): Boolean {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return g > r && g > b
    }

    private fun isRed(argb: Long): Boolean {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return r > g && r > b
    }

    private fun isAmber(argb: Long): Boolean {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return r > b && g > b && r >= g
    }
}
