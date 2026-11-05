package com.strobingn.wildlifefieldops.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(Contrast.passesAa(FieldSwatch.Light.OnPrimary, FieldSwatch.Light.Error))
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
        val surfaces = listOf(
            FieldSwatch.Dark.Background,
            FieldSwatch.Dark.Surface,
            FieldSwatch.Dark.Elevated,
            FieldSwatch.Dark.NavBar,
            FieldSwatch.Dark.Card,
            FieldSwatch.Dark.SurfaceVariant,
            FieldSwatch.Dark.SurfaceBright,
            FieldSwatch.Dark.PrimaryContainer
        )
        val inks = listOf(
            FieldSwatch.Dark.OnSurface,
            FieldSwatch.Dark.OnSurfaceVariant,
            FieldSwatch.Dark.OnSurfaceMuted,
            FieldSwatch.Dark.Primary,
            FieldSwatch.Dark.Error,
            FieldSwatch.Dark.StatusUrgent,
            FieldSwatch.Dark.StatusCancelled,
            FieldSwatch.Dark.AccentAmber,
            FieldSwatch.Dark.StatusPending,
            FieldSwatch.Dark.StatusInProgress,
            FieldSwatch.Dark.Success
        )
        surfaces.forEach { bg ->
            inks.forEach { fg ->
                val ratio = Contrast.ratio(fg, bg)
                assertTrue(
                    "AA ${fg.toString(16)} on ${bg.toString(16)} was $ratio",
                    Contrast.passesAa(fg, bg)
                )
            }
        }
        val card = FieldSwatch.Dark.Card
        assertTrue(Contrast.passesAa(FieldSwatch.Dark.OnPrimary, FieldSwatch.Dark.Error))
        assertTrue(Contrast.passesAa(FieldSwatch.Dark.OnPrimary, FieldSwatch.Dark.Primary))
        assertTrue(Contrast.passesAa(FieldSwatch.Dark.OnPrimary, FieldSwatch.Dark.PrimaryDark))
        assertTrue(Contrast.passesAa(FieldSwatch.Dark.OnPrimaryContainer, FieldSwatch.Dark.PrimaryContainer))
        assertTrue(Contrast.passesAa(FieldSwatch.Dark.Primary, FieldSwatch.Dark.NavIndicator))
        assertTrue(Contrast.passesAa(FieldSwatch.Dark.OnSurface, FieldSwatch.Dark.NavIndicator))
        assertTrue(Contrast.passesAa(FieldSwatch.Dark.OnSurfaceVariant, FieldSwatch.Dark.NavBar))
        // Priority and status badges draw the ink at 15% over the card.
        assertTrue(Contrast.passesAa(FieldSwatch.Dark.Error, Contrast.composite(FieldSwatch.Dark.Error, card, 0.15)))
        assertTrue(
            Contrast.passesAa(
                FieldSwatch.Dark.StatusUrgent,
                Contrast.composite(FieldSwatch.Dark.StatusUrgent, card, 0.15)
            )
        )
        assertTrue(
            Contrast.passesAa(
                FieldSwatch.Dark.Success,
                Contrast.composite(FieldSwatch.Dark.Success, card, 0.16)
            )
        )
    }

    @Test
    fun darkSurfacesStepOffTheBackground() {
        assertEquals(0xFF000000, FieldSwatch.Dark.Background)
        assertEquals(0xFF0C0C0C, FieldSwatch.Dark.NavBar)
        assertEquals(0xFF121212, FieldSwatch.Dark.Surface)
        assertEquals(0xFF121212, FieldSwatch.Dark.Elevated)
        assertEquals(0xFF1A1A1A, FieldSwatch.Dark.Card)
        assertEquals(0xFF1A1A1A, FieldSwatch.Dark.SurfaceVariant)
        assertEquals(0xFF262626, FieldSwatch.Dark.SurfaceBright)
        assertEquals(0xFF616161, FieldSwatch.Dark.Outline)
        assertEquals(0xFF4ADE80, FieldSwatch.Dark.Primary)
        val background = (FieldSwatch.Dark.Background and 0xFF).toInt()
        listOf(
            FieldSwatch.Dark.NavBar,
            FieldSwatch.Dark.Surface,
            FieldSwatch.Dark.Card,
            FieldSwatch.Dark.SurfaceBright,
            FieldSwatch.Dark.Outline
        ).forEach { argb ->
            val channel = (argb and 0xFF).toInt()
            assertTrue("${argb.toString(16)} should be lighter than the page", channel > background)
        }
        // Cards still step off the nav bar and the page.
        assertTrue((FieldSwatch.Dark.Card and 0xFF) > (FieldSwatch.Dark.NavBar and 0xFF))
    }

    @Test
    fun darkOutlineMeetsUiContrastOnThePage() {
        val page = FieldSwatch.Dark.Background
        listOf(FieldSwatch.Dark.Outline, FieldSwatch.Dark.OutlineVariant).forEach { outline ->
            val ratio = Contrast.ratio(outline, page)
            assertTrue(
                "dark outline ${outline.toString(16)} on page was $ratio",
                ratio >= Contrast.AA_UI
            )
        }
    }

    @Test
    fun lightPaperSurfacesAreUnchanged() {
        assertEquals(0xFFF5F5F5, FieldSwatch.Light.Background)
        assertEquals(0xFFFFFFFF, FieldSwatch.Light.Card)
        assertEquals(0xFFEEEEEE, FieldSwatch.Light.Elevated)
        assertEquals(0xFFE6E6E6, FieldSwatch.Light.SurfaceVariant)
        assertEquals(0xFFFFFFFF, FieldSwatch.Light.SurfaceBright)
        assertEquals(0xFF74777F, FieldSwatch.Light.Outline)
        assertEquals(0xFFC4C4C4, FieldSwatch.Light.OutlineVariant)
        assertEquals(0xFF93000A, FieldSwatch.Light.StatusUrgent)
    }

    @Test
    fun accentsAreBrightDistinctHuesAndNeverYellowOrOrange() {
        listOf(
            "Dark" to listOf(
                FieldSwatch.Dark.Primary,
                FieldSwatch.Dark.AccentBlue,
                FieldSwatch.Dark.AccentPurple,
                FieldSwatch.Dark.AccentOrange,
                FieldSwatch.Dark.AccentCyan,
                FieldSwatch.Dark.AccentPink,
                FieldSwatch.Dark.AccentAmber,
                FieldSwatch.Dark.StatusPending,
                FieldSwatch.Dark.StatusInProgress
            ),
            "Light" to listOf(
                FieldSwatch.Light.Primary,
                FieldSwatch.Light.AccentBlue,
                FieldSwatch.Light.AccentPurple,
                FieldSwatch.Light.AccentOrange,
                FieldSwatch.Light.AccentCyan,
                FieldSwatch.Light.AccentPink,
                FieldSwatch.Light.AccentAmber,
                FieldSwatch.Light.StatusPending,
                FieldSwatch.Light.StatusInProgress
            )
        ).forEach { (theme, colors) ->
            colors.forEach { argb ->
                val r = ((argb shr 16) and 0xFF).toInt()
                val g = ((argb shr 8) and 0xFF).toInt()
                val b = (argb and 0xFF).toInt()
                val max = maxOf(r, g, b)
                val spread = max - minOf(r, g, b)
                // HSV saturation of at least 40%: a real color, not the old neutral gray.
                assertTrue("$theme ${argb.toString(16)} should be a saturated color, not gray", spread * 5 >= max * 2)
                assertFalse("$theme ${argb.toString(16)} is yellow/orange", Contrast.isYellowAmberOrangeOrLime(argb))
            }
        }
        // Inspections (blue), scheduled (cyan), in progress (purple) and primary (green) read apart.
        val dark = setOf(
            FieldSwatch.Dark.Primary,
            FieldSwatch.Dark.AccentBlue,
            FieldSwatch.Dark.StatusPending,
            FieldSwatch.Dark.StatusInProgress
        )
        assertEquals(4, dark.size)
        // Dark accents are bright: each reads at least 7:1 on the black page.
        listOf(
            FieldSwatch.Dark.Primary,
            FieldSwatch.Dark.AccentBlue,
            FieldSwatch.Dark.AccentCyan,
            FieldSwatch.Dark.AccentPurple,
            FieldSwatch.Dark.AccentOrange
        ).forEach { argb ->
            assertTrue(Contrast.ratio(argb, FieldSwatch.Dark.Background) >= 7.0)
        }
    }

    @Test
    fun lightAccentsMeetAaOnCards() {
        listOf(
            FieldSwatch.Light.AccentBlue,
            FieldSwatch.Light.AccentPurple,
            FieldSwatch.Light.AccentOrange,
            FieldSwatch.Light.AccentCyan,
            FieldSwatch.Light.AccentPink,
            FieldSwatch.Light.AccentAmber
        ).forEach { argb ->
            assertTrue(
                "light accent ${argb.toString(16)} on card",
                Contrast.passesAa(argb, FieldSwatch.Light.Card)
            )
        }
        assertTrue(Contrast.passesAa(FieldSwatch.Light.OnPrimaryContainer, FieldSwatch.Light.SecondaryContainer))
    }

    @Test
    fun semanticColorsStayChromatic() {
        assertTrue(isGreen(FieldSwatch.Light.Success))
        assertTrue(isGreen(FieldSwatch.Dark.Success))
        assertTrue(isGreen(FieldSwatch.Light.StatusCompleted))
        assertTrue(isRed(FieldSwatch.Light.Error))
        assertTrue(isRed(FieldSwatch.Dark.Error))
        assertTrue(isRed(FieldSwatch.Light.StatusUrgent))
        assertTrue(isRed(FieldSwatch.Dark.StatusUrgent))
        assertTrue(isRed(FieldSwatch.Light.OnHeroWarning))
        assertTrue(isRed(FieldSwatch.Dark.OnHeroWarning))
        assertFalse(Contrast.isYellowAmberOrangeOrLime(FieldSwatch.Light.StatusUrgent))
        assertFalse(Contrast.isYellowAmberOrangeOrLime(FieldSwatch.Dark.StatusUrgent))
        assertFalse(Contrast.isYellowAmberOrangeOrLime(FieldSwatch.Light.OnHeroWarning))
        assertFalse(Contrast.isYellowAmberOrangeOrLime(FieldSwatch.Dark.OnHeroWarning))
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

}
