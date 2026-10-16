package com.strobingn.wildlifefieldops.ai.fieldops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class UsDateTest {
    private fun day(t: String) = FieldDate.formatDay(FieldDate.parseDay(t).millis!!)

    @Test fun acceptsUsForms() {
        assertEquals("10/13/2026", day("10/13/2026"))
        assertEquals("10/13/2026", day("10/13/26"))
        assertEquals("01/05/2026", day("1/5/2026"))
        assertEquals("10/13/2026", day(" 10-13-2026 "))
    }

    @Test fun legacyIsoStillReads() = assertEquals("10/13/2026", day("2026-10-13"))

    @Test fun rejectsBadAndKeepsBlank() {
        assertEquals(FieldDate.ERROR, FieldDate.parseDay("13/10/2026").error)
        assertEquals(FieldDate.ERROR, FieldDate.parseDay("2/30/2026").error)
        assertEquals(FieldDate.ERROR, FieldDate.parseDay("tomorrow").error)
        val blank = FieldDate.parseDay("")
        assertNull(blank.millis); assertNull(blank.error)
    }

    @Test fun roundTripAndIsoStorageUnchanged() {
        val m = FieldDate.parseDay("10/13/2026").millis!!
        assertEquals(m, FieldDate.parseDay(FieldDate.formatDay(m)).millis)
        assertEquals("2026-10-13", FieldDate.formatIso(m))
    }

    @Test fun paymentDatesUs() {
        val z = ZoneId.of("America/New_York")
        val m = PaymentLedger.parseDay("10/13/2026", z)
        assertTrue(m > 0L)
        assertEquals("10/13/2026", PaymentLedger.formatDay(m, z))
        assertEquals(m, PaymentLedger.parseDay("2026-10-13", z))
    }
}
