package com.strobingn.wildlifefieldops.util

import com.strobingn.wildlifefieldops.pricing.DepositDue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DepositDueLayoutTest {
    @Test
    fun estimateAndInvoicePlaceTheNoticeDirectlyUnderTheTotal() {
        val packet = CityIslandSample.packet()
        assertTrue(packet.total > 1000.0)
        val expected = DepositDue.notice(packet.total)
        assertEquals("50% deposit due before work starts: $710.23", expected)

        listOf(DocumentKind.ESTIMATE, DocumentKind.INVOICE).forEach { kind ->
            val document = StandardDocuments.build(kind, packet)
            val ops = document.layout().pages.flatMap { it.ops }.filter { it.kind == "text" }
            val total = ops.first { it.text == "Grand-Total:" }
            val notice = ops.first { it.text == expected }
            assertEquals(DepositDue.COLOR, notice.color)
            assertTrue(notice.bold)
            assertTrue("notice ${notice.size} should exceed body ${StandardDocuments.MONEY_TEXT}", notice.size > StandardDocuments.MONEY_TEXT)
            assertTrue(notice.size > total.size)
            assertEquals("right", notice.align)
            val amount = ops.first { it.text == "\$1420.45" && it.y == total.y }
            assertEquals(amount.x, notice.x, 0.01f)
            assertTrue(notice.y > total.y)

            val between = ops.filter { it.y > total.y && it.y < notice.y && it.text.isNotBlank() }
            assertTrue("nothing between total and deposit: ${between.map { it.text }}", between.isEmpty())

            if (kind == DocumentKind.INVOICE) {
                val paid = ops.first { it.text == "Amount Paid:" }
                val balance = ops.first { it.text == "Balance Due:" }
                assertTrue(paid.y > notice.y)
                assertTrue(balance.y > paid.y)
                assertEquals(listOf("BALANCE DUE", "\$1420.45"), document.stamp.lines)
                assertEquals("", document.stamp.fill)
            } else {
                assertEquals("ESTIMATE", document.stamp.lines.first())
                assertFalse(ops.any { it.text == "Amount Paid:" })
                assertFalse(ops.any { it.text == "Balance Due:" })
            }
        }
    }

    @Test
    fun paidStampStaysWhenTheDepositLineIsShown() {
        val packet = CityIslandSample.packet().copy(amountPaid = 1420.45, balanceDue = 0.0)
        val document = StandardDocuments.build(DocumentKind.INVOICE, packet)
        assertEquals(listOf("PAID"), document.stamp.lines)
        assertEquals(com.strobingn.wildlifefieldops.util.DocumentPalette.PAID, document.stamp.fill)
        val ops = document.layout().pages.flatMap { it.ops }
        assertTrue(ops.any { it.text == DepositDue.notice(packet.total) && it.color == DepositDue.COLOR })
    }

    @Test
    fun hiddenAtOrBelowOneThousandAndOnOtherDocuments() {
        listOf(0.0, 500.0, 1000.0).forEach { total ->
            assertNull(DepositDue.notice(total))
            val packet = CityIslandSample.packet().copy(total = total, balanceDue = total)
            listOf(DocumentKind.ESTIMATE, DocumentKind.INVOICE).forEach { kind ->
                val ops = StandardDocuments.build(kind, packet).layout().pages.flatMap { it.ops }
                assertFalse(
                    "$kind at $total",
                    ops.any { it.text.startsWith("50% deposit due") }
                )
            }
        }
        val over = CityIslandSample.packet()
        listOf(
            DocumentKind.CONTRACT,
            DocumentKind.RECEIPT,
            DocumentKind.INSPECTION,
            DocumentKind.EXCLUSION,
            DocumentKind.WARRANTY,
            DocumentKind.NWCO_LOG,
            DocumentKind.EARNINGS_TAX
        ).forEach { kind ->
            val ops = StandardDocuments.build(kind, over).layout().pages.flatMap { it.ops }
            assertFalse(kind.name, ops.any { it.text.startsWith("50% deposit due") })
        }
    }
}
