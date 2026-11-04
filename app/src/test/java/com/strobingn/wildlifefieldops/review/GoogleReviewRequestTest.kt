package com.strobingn.wildlifefieldops.review

import com.strobingn.wildlifefieldops.pricing.JobPaymentRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class GoogleReviewRequestTest {
    private val link = "https://g.page/r/wildlife-whisperer/review"
    private val zone = ZoneId.of("UTC")

    @Test fun firstNameUsesTheCustomersGivenName() {
        assertEquals("Pam", GoogleReviewRequest.firstName("Pam Johnston"))
        assertEquals("Pam", GoogleReviewRequest.firstName("Pam Johnston (TSO)"))
        assertEquals("Mary Ann", GoogleReviewRequest.firstName("Mary Ann Smith"))
        assertEquals("there", GoogleReviewRequest.firstName(""))
        assertEquals("there", GoogleReviewRequest.firstName("  (TSO)  "))
    }

    @Test fun messageUsesFirstNameAndReviewLink() {
        val body = GoogleReviewRequest.message("", "Pam Johnston (TSO)", link)
        assertTrue(body, body.startsWith("Hi Pam,"))
        assertTrue(body, body.contains(link))
        assertFalse(body.contains("{firstName}"))
        assertFalse(body.contains("{reviewLink}"))
    }

    @Test fun editedTemplateIsUsedAndLinkIsKept() {
        val custom = "Thanks {firstName}. We appreciate you."
        val body = GoogleReviewRequest.message(custom, "Sam Lee", link)
        assertEquals("Thanks Sam. We appreciate you.\n$link", body)
    }

    @Test fun templateThatAlreadyHasTheLinkIsNotDuplicated() {
        val custom = "Hi {firstName}, please review us: {reviewLink}"
        val body = GoogleReviewRequest.message(custom, "Sam Lee", link)
        assertEquals("Hi Sam, please review us: $link", body)
        assertEquals(1, body.split(link).size - 1)
    }

    @Test fun blankLinkDoesNotBuildAComposer() {
        assertNull(
            GoogleReviewRequest.composer(
                ReviewChannel.TEXT, "Pam Johnston", "845-555-0100", "pam@example.com", "  ", ""
            )
        )
        assertFalse(GoogleReviewRequest.hasReviewLink("  "))
    }

    @Test fun fullyPaidOnlyWhenPaymentsCoverARealTotal() {
        val full = listOf(pay(100.0))
        val part = listOf(pay(40.0))
        assertTrue(GoogleReviewRequest.coveredByPayments(100.0, full))
        assertTrue(GoogleReviewRequest.coveredByPayments(100.0, listOf(pay(40.0), pay(60.0))))
        assertTrue(GoogleReviewRequest.coveredByPayments(80.0, full))
        assertFalse(GoogleReviewRequest.coveredByPayments(100.0, part))
        assertFalse(GoogleReviewRequest.coveredByPayments(100.0, emptyList()))
        assertFalse(GoogleReviewRequest.coveredByPayments(0.0, full))
        assertTrue(GoogleReviewRequest.becameFullyPaid(100.0, part, full))
        assertFalse(GoogleReviewRequest.becameFullyPaid(100.0, full, listOf(pay(100.0), pay(20.0))))
        assertFalse(GoogleReviewRequest.becameFullyPaid(100.0, emptyList(), part))
    }

    @Test fun paidInvoiceStatusOffersTheActionWithoutALedgerRow() {
        assertTrue(GoogleReviewRequest.shouldOffer(250.0, emptyList(), listOf("PAID")))
        assertFalse(GoogleReviewRequest.shouldOffer(250.0, emptyList(), listOf("SENT")))
        assertTrue(GoogleReviewRequest.shouldOffer(250.0, listOf(pay(250.0)), emptyList()))
    }

    @Test fun outboundOpensAComposerTheOwnerSends() {
        val text = GoogleReviewRequest.composer(
            ReviewChannel.TEXT, "Pam Johnston", "8455550100", "pam@example.com", link, ""
        )!!
        val sms = GoogleReviewRequest.outbound(text)
        assertEquals(GoogleReviewRequest.ACTION_SENDTO, sms.action)
        assertEquals("smsto:8455550100", sms.data)
        assertEquals(text.body, sms.extras[GoogleReviewRequest.SMS_BODY])
        assertFalse(sms.chooser)

        val email = GoogleReviewRequest.outbound(
            text.copy(channel = ReviewChannel.EMAIL)
        )
        assertEquals(GoogleReviewRequest.ACTION_SENDTO, email.action)
        assertEquals("mailto:pam@example.com", email.data)
        assertEquals(GoogleReviewRequest.SUBJECT, email.extras[GoogleReviewRequest.EXTRA_SUBJECT])
        assertEquals(text.body, email.extras[GoogleReviewRequest.EXTRA_TEXT])

        val share = GoogleReviewRequest.outbound(text.copy(channel = ReviewChannel.SHARE))
        assertEquals(GoogleReviewRequest.ACTION_SEND, share.action)
        assertTrue(share.chooser)
        assertTrue(share.extras.values.all { it == text.body || it == GoogleReviewRequest.SUBJECT })
    }

    @Test fun sentDateIsMonthDayYear() {
        val at = Instant.parse("2026-11-04T18:00:00Z").toEpochMilli()
        assertEquals("Review request sent 11/04/2026", GoogleReviewRequest.sentLabel(at, zone))
        assertEquals("", GoogleReviewRequest.sentLabel(null, zone))
        assertEquals("", GoogleReviewRequest.sentLabel(0L, zone))
    }

    @Test fun sentLogRoundTripsByJob() {
        val raw = ReviewSentLogCodec.encode(mapOf("job-1" to 100L, " " to 5L, "job-2" to 0L))
        assertEquals(mapOf("job-1" to 100L), ReviewSentLogCodec.decode(raw))
        assertTrue(ReviewSentLogCodec.decode("").isEmpty())
        assertTrue(ReviewSentLogCodec.decode("{not json").isEmpty())
    }

    private fun pay(amount: Double) = JobPaymentRecord(
        id = "p$amount",
        method = "cash",
        amount = amount,
        paidAt = 1_700_000_000_000L
    )
}
