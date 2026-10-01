package com.strobingn.wildlifefieldops.ui.theme

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
        assertTrue(Contrast.passesAa(FieldSwatch.OnPrimary, FieldSwatch.Light.Primary))
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
        assertTrue(Contrast.passesAa(FieldSwatch.OnPrimary, FieldSwatch.Dark.Primary))
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
}
