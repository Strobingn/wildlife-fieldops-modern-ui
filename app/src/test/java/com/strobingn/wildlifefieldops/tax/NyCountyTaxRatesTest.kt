package com.strobingn.wildlifefieldops.tax

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NyCountyTaxRatesTest {

    // ── normalizeCountyName ───────────────────────────────────────────────────────

    @Test
    fun `normalizeCountyName strips 'County' suffix`() {
        assertEquals("orange", NyCountyTaxRates.normalizeCountyName("Orange County"))
    }

    @Test
    fun `normalizeCountyName handles lowercase input`() {
        assertEquals("orange", NyCountyTaxRates.normalizeCountyName("orange county"))
    }

    @Test
    fun `normalizeCountyName handles trailing ny suffix`() {
        assertEquals("orange", NyCountyTaxRates.normalizeCountyName("orange county, ny"))
    }

    @Test
    fun `normalizeCountyName handles 'New York' state suffix`() {
        assertEquals("orange", NyCountyTaxRates.normalizeCountyName("Orange County, New York"))
    }

    @Test
    fun `normalizeCountyName handles plain name without suffix`() {
        assertEquals("orange", NyCountyTaxRates.normalizeCountyName("Orange"))
    }

    @Test
    fun `normalizeCountyName handles UPPERCASE input`() {
        assertEquals("orange", NyCountyTaxRates.normalizeCountyName("ORANGE"))
    }

    @Test
    fun `normalizeCountyName handles mixed case with state`() {
        assertEquals("westchester", NyCountyTaxRates.normalizeCountyName("Westchester County, NY"))
    }

    // ── taxRatePercentForCounty ───────────────────────────────────────────────────

    @Test
    fun `Orange County returns 8_125`() {
        assertEquals(8.125, NyCountyTaxRates.taxRatePercentForCounty("Orange")!!, 0.0001)
    }

    @Test
    fun `'Orange County' string returns same rate as bare name`() {
        assertEquals(
            NyCountyTaxRates.taxRatePercentForCounty("Orange"),
            NyCountyTaxRates.taxRatePercentForCounty("Orange County")
        )
    }

    @Test
    fun `'orange county, ny' returns same rate`() {
        assertEquals(
            NyCountyTaxRates.taxRatePercentForCounty("Orange"),
            NyCountyTaxRates.taxRatePercentForCounty("orange county, ny")
        )
    }

    @Test
    fun `Rockland County returns 8_375`() {
        assertEquals(8.375, NyCountyTaxRates.taxRatePercentForCounty("Rockland")!!, 0.0001)
    }

    @Test
    fun `Ulster County returns 8_0`() {
        assertEquals(8.0, NyCountyTaxRates.taxRatePercentForCounty("Ulster")!!, 0.0001)
    }

    @Test
    fun `Dutchess County returns 8_125`() {
        assertEquals(8.125, NyCountyTaxRates.taxRatePercentForCounty("Dutchess")!!, 0.0001)
    }

    @Test
    fun `Putnam County returns 8_375`() {
        assertEquals(8.375, NyCountyTaxRates.taxRatePercentForCounty("Putnam")!!, 0.0001)
    }

    @Test
    fun `Westchester County returns 8_375`() {
        assertEquals(8.375, NyCountyTaxRates.taxRatePercentForCounty("Westchester")!!, 0.0001)
    }

    @Test
    fun `Sullivan County returns 8_0`() {
        assertEquals(8.0, NyCountyTaxRates.taxRatePercentForCounty("Sullivan")!!, 0.0001)
    }

    @Test
    fun `unknown county returns default NY rate 8_0`() {
        assertEquals(8.0, NyCountyTaxRates.taxRatePercentForCounty("Nonexistent County")!!, 0.0001)
    }

    @Test
    fun `non-NY state returns null`() {
        assertNull(NyCountyTaxRates.taxRatePercentForCounty("Orange", state = "CA"))
    }

    @Test
    fun `'New York' state string accepted`() {
        assertNotNull(NyCountyTaxRates.taxRatePercentForCounty("Orange", state = "New York"))
    }

    @Test
    fun `case-insensitive state matching`() {
        assertNotNull(NyCountyTaxRates.taxRatePercentForCounty("Orange", state = "ny"))
        assertNotNull(NyCountyTaxRates.taxRatePercentForCounty("Orange", state = "NY"))
        assertNotNull(NyCountyTaxRates.taxRatePercentForCounty("Orange", state = "Ny"))
    }

    // ── displayName ──────────────────────────────────────────────────────────────

    @Test
    fun `displayName appends County to bare name`() {
        assertEquals("Orange County", NyCountyTaxRates.displayName("Orange"))
    }

    @Test
    fun `displayName does not duplicate County suffix`() {
        assertEquals("Orange County", NyCountyTaxRates.displayName("Orange County"))
    }

    @Test
    fun `displayName title-cases the name`() {
        assertEquals("Orange County", NyCountyTaxRates.displayName("orange county, ny"))
    }

    @Test
    fun `displayName works for Westchester`() {
        assertEquals("Westchester County", NyCountyTaxRates.displayName("WESTCHESTER"))
    }
}
