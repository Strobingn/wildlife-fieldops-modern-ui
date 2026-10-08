package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Invoice
import com.strobingn.wildlifefieldops.data.model.InvoiceStatus
import com.strobingn.wildlifefieldops.pricing.JobPaymentRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.TimeZone

class FieldOpsReviewFixesTest {

    private val newYork = ZoneId.of("America/New_York")

    private fun ms(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, newYork).toInstant().toEpochMilli()

    private fun <T> inNewYork(block: () -> T): T {
        val saved = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        try {
            return block()
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    // ---- DST day / week bounds ----

    @Test
    fun dayBoundsCoverTheWholeLocalDayOnFallBackAndSpringForward() { inNewYork {
        // Nov 1 2026 is 25 hours long; Mar 8 2026 is 23 hours long.
        val fall = EarningsRollup.dayBounds(ms(2026, 11, 1, 12))
        assertEquals(ms(2026, 11, 1), fall.first)
        assertEquals(ms(2026, 11, 2), fall.second)

        val spring = EarningsRollup.dayBounds(ms(2026, 3, 8, 12))
        assertEquals(ms(2026, 3, 8), spring.first)
        assertEquals(ms(2026, 3, 9), spring.second)
    }
    }

    @Test
    fun weekBoundsEndAtLocalMondayMidnightAcrossDst() { inNewYork {
        // Week of Mon Oct 26 2026 contains the fall-back Sunday.
        val week = EarningsRollup.weekBounds(ms(2026, 10, 28, 12))
        assertEquals(ms(2026, 10, 26), week.first)
        assertEquals(ms(2026, 11, 2), week.second)
    }
    }

    @Test
    fun trapDueTodayCoversLateEveningOnFallBackDay() { inNewYork {
        val now = ms(2026, 11, 1, 8)
        val lateTonight = ms(2026, 11, 1, 23, 30)
        assertEquals(TrapDueState.DUE_TODAY, TrapCheckPlanner.dueState(lateTonight, now))
        assertEquals(ms(2026, 11, 2), TrapCheckPlanner.dayEnd(now))
        assertEquals(TrapDueState.UPCOMING, TrapCheckPlanner.dueState(ms(2026, 11, 2, 0, 30), now))
    }
    }

    // ---- mileage rounding ----

    @Test
    fun mileageRowAmountsAreWholeCentsAndTotalMatchesRows() {
        val rows = List(3) { MileageLogEntry(id = "m$it", miles = 0.5, rate = 0.65) }
        // 0.5 mi * 65 cents = 32.5 cents -> 33 cents per printed row.
        rows.forEach { assertEquals(0.33, it.amount, 0.0) }
        assertEquals(0.99, MileageTaxLog.totalAmount(rows), 0.0)
    }

    // ---- invoice balance ----

    @Test
    fun withStatusBalanceHasNoBinaryDrift() {
        val invoice = Invoice(totalAmount = 0.3, amountPaid = 0.1, status = InvoiceStatus.SENT)
        val updated = InvoiceReminder.withStatus(invoice, InvoiceStatus.SENT, now = 5L)
        assertEquals(0.2, updated.balanceDue, 0.0)
    }

    // ---- payment ids ----

    @Test
    fun paymentAddedAfterADeleteDoesNotOverwriteAnotherPayment() {
        val day = 1_000L
        var list = emptyList<JobPaymentRecord>()
        list = PaymentLedger.upsert(list, JobPaymentRecord(amount = 10.0, paidAt = day))
        list = PaymentLedger.upsert(list, JobPaymentRecord(amount = 20.0, paidAt = day))
        list = PaymentLedger.remove(list, list[0].id)
        list = PaymentLedger.upsert(list, JobPaymentRecord(amount = 30.0, paidAt = day))
        assertEquals(2, list.size)
        assertEquals(2, list.map { it.id }.toSet().size)
        assertEquals(50.0, PaymentLedger.totalPaid(list), 0.0)
    }

    // ---- estimate line suggestions ----

    @Test
    fun batAndRatMatchWholeWordsOnly() {
        val bathroom = EstimateLineSuggester.suggest(
            EstimateLineContext(species = "Raccoon", notes = "Heard it in the bathroom exhaust")
        )
        assertFalse(bathroom.any { it.description.startsWith("Bat ") })
        assertTrue(bathroom.any { it.description.contains("raccoon", ignoreCase = true) })

        val moderate = EstimateLineSuggester.suggest(
            EstimateLineContext(species = "Opossum", notes = "Moderate damage by the crate")
        )
        assertFalse(moderate.any { it.description.startsWith("Rodent snap") })

        val bats = EstimateLineSuggester.suggest(EstimateLineContext(notes = "Bats at the ridge vent"))
        assertTrue(bats.any { it.description.startsWith("Bat one-way") })
        val rats = EstimateLineSuggester.suggest(EstimateLineContext(notes = "Rats in the attic"))
        assertTrue(rats.any { it.description.startsWith("Rodent snap") })
    }

    // ---- locale ----

    @Test
    fun customerMessageMoneyUsesDotDecimalInAnyLocale() {
        val saved = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            val message = CustomerMessageDraft.draft(
                CustomerMessageKind.ESTIMATE,
                customerName = "Pat",
                jobTitle = "Attic",
                address = "1 Main St",
                amount = 12.5
            )
            assertTrue(message.body, message.body.contains("$12.50"))
        } finally {
            Locale.setDefault(saved)
        }
    }
}
