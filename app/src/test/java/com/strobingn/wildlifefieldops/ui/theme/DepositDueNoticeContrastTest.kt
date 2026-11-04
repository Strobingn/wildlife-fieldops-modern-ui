package com.strobingn.wildlifefieldops.ui.theme

import com.strobingn.wildlifefieldops.pricing.DepositDue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DepositDueNoticeContrastTest {
    @Test
    fun onScreenRedClearsAaOnTheTotalsCardAndPdfInkStaysPrintRed() {
        assertEquals("#C62828", DepositDue.COLOR)
        val cards = listOf(
            "dark" to Contrast.composite(FieldSwatch.Dark.Primary, FieldSwatch.Dark.Background, 0.10),
            "light" to Contrast.composite(FieldSwatch.Light.Primary, FieldSwatch.Light.Background, 0.10)
        )
        val inks = listOf(
            "dark" to FieldSwatch.Dark.Error,
            "light" to FieldSwatch.Light.Error
        )
        cards.zip(inks).forEach { (card, ink) ->
            assertEquals(card.first, ink.first)
            val ratio = Contrast.ratio(ink.second, card.second)
            assertTrue("$card ratio $ratio", ratio >= Contrast.AA_NORMAL)
            assertFalse(Contrast.isYellowAmberOrangeOrLime(ink.second))
        }
    }
}
