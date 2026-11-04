package com.strobingn.wildlifefieldops.pricing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DepositDueTest {
    @Test
    fun halfUpExamples() {
        assertEquals(617.29, DepositDue.amount(1234.57), 0.0)
        assertEquals(500.01, DepositDue.amount(1000.01), 0.0)
        assertEquals(500.00, DepositDue.amount(1000.00), 0.0)
        assertEquals(250.00, DepositDue.amount(500.0), 0.0)
        // 0.005 of a dollar rounds away from zero.
        assertEquals(0.01, DepositDue.amount(0.01), 0.0)
        assertEquals(5.01, DepositDue.amount(10.01), 0.0)
    }

    @Test
    fun lineShowsOnlyWhenTotalIsStrictlyOverOneThousand() {
        assertFalse(DepositDue.shows(0.0))
        assertFalse(DepositDue.shows(500.0))
        assertFalse(DepositDue.shows(1000.0))
        assertFalse(DepositDue.shows(1000.004))
        assertTrue(DepositDue.shows(1000.01))
        assertTrue(DepositDue.shows(1234.57))

        assertNull(DepositDue.notice(0.0))
        assertNull(DepositDue.notice(500.0))
        assertNull(DepositDue.notice(1000.0))
        assertEquals(
            "50% deposit due before work starts: $617.29",
            DepositDue.notice(1234.57)
        )
        assertEquals(
            "50% deposit due before work starts: $500.01",
            DepositDue.notice(1000.01)
        )
    }
}
