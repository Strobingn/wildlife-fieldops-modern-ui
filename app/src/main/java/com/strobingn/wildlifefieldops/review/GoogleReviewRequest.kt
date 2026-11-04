package com.strobingn.wildlifefieldops.review

import com.strobingn.wildlifefieldops.ai.fieldops.PaymentLedger
import com.strobingn.wildlifefieldops.data.model.JobCustomerDraft
import com.strobingn.wildlifefieldops.pricing.JobPaymentRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.ZoneId

/**
 * Google review request the owner sends by hand.
 * The app only opens the phone's messaging, email, or share composer.
 */
object GoogleReviewRequest {
    const val ACTION = "Request a Google review"
    const val SUBJECT = "A quick Google review?"
    const val DEFAULT_TEMPLATE =
        "Hi {firstName}, thank you for having us out. If you have a minute, a Google review helps our small business a lot: {reviewLink}"
    const val MISSING_LINK =
        "Add your Google review link in Settings, then request the review from this job."
    const val OWNER_SENDS = "You press Send. The note is only filled in."
    const val NO_APP = "No app on this phone can open that. Nothing was sent."

    const val ACTION_SENDTO = "android.intent.action.SENDTO"
    const val ACTION_SEND = "android.intent.action.SEND"
    const val EXTRA_TEXT = "android.intent.extra.TEXT"
    const val EXTRA_SUBJECT = "android.intent.extra.SUBJECT"
    const val SMS_BODY = "sms_body"

    private val COMPANY_TAG = Regex("""\s*\([^)]*\)""")
    private val FIRST_NAME_TOKEN = Regex("""\{firstName\}""", RegexOption.IGNORE_CASE)
    private val REVIEW_LINK_TOKEN = Regex("""\{reviewLink\}""", RegexOption.IGNORE_CASE)

    fun firstName(customerName: String): String {
        val stripped = COMPANY_TAG.replace(customerName, " ").trim()
        val (first, _) = JobCustomerDraft.parsePersonName(stripped)
        return first.ifBlank { "there" }
    }

    /** Payments cover a real invoice total. A zero total is not "paid in full". */
    fun coveredByPayments(invoiceTotal: Double, payments: List<JobPaymentRecord>): Boolean {
        if (invoiceTotal <= 0.0 || payments.isEmpty()) return false
        return PaymentLedger.balanceDue(invoiceTotal, payments) == 0.0
    }

    fun becameFullyPaid(
        invoiceTotal: Double,
        before: List<JobPaymentRecord>,
        after: List<JobPaymentRecord>
    ): Boolean = !coveredByPayments(invoiceTotal, before) && coveredByPayments(invoiceTotal, after)

    fun shouldOffer(
        invoiceTotal: Double,
        payments: List<JobPaymentRecord>,
        invoiceStatuses: List<String> = emptyList()
    ): Boolean {
        if (coveredByPayments(invoiceTotal, payments)) return true
        return invoiceStatuses.any { it.equals("PAID", ignoreCase = true) }
    }

    fun message(template: String, customerName: String, reviewLink: String): String {
        val link = reviewLink.trim()
        val source = template.trim().ifBlank { DEFAULT_TEMPLATE }
        val withLink = if (mentionsLink(source, link)) source else "$source\n{reviewLink}"
        return REVIEW_LINK_TOKEN.replace(
            FIRST_NAME_TOKEN.replace(withLink, firstName(customerName)),
            link
        ).trim()
    }

    fun hasReviewLink(reviewLink: String): Boolean = reviewLink.trim().isNotEmpty()

    fun composer(
        channel: ReviewChannel,
        customerName: String,
        phone: String,
        email: String,
        reviewLink: String,
        template: String
    ): ReviewComposer? {
        if (!hasReviewLink(reviewLink)) return null
        return ReviewComposer(
            channel = channel,
            phone = phone.trim(),
            email = email.trim(),
            subject = SUBJECT,
            body = message(template, customerName, reviewLink)
        )
    }

    /**
     * Composer payload only. Actions are share/send-to, which wait for the owner.
     */
    fun outbound(composer: ReviewComposer): ReviewOutbound = when (composer.channel) {
        ReviewChannel.TEXT -> ReviewOutbound(
            action = ACTION_SENDTO,
            data = "smsto:${composer.phone}",
            extras = mapOf(SMS_BODY to composer.body, EXTRA_TEXT to composer.body),
            chooser = false
        )
        ReviewChannel.EMAIL -> ReviewOutbound(
            action = ACTION_SENDTO,
            data = "mailto:${composer.email}",
            extras = mapOf(EXTRA_SUBJECT to composer.subject, EXTRA_TEXT to composer.body),
            chooser = false
        )
        ReviewChannel.SHARE -> ReviewOutbound(
            action = ACTION_SEND,
            data = null,
            extras = mapOf(EXTRA_SUBJECT to composer.subject, EXTRA_TEXT to composer.body),
            chooser = true
        )
    }

    fun sentLabel(sentAt: Long?, zone: ZoneId = ZoneId.systemDefault()): String {
        if (sentAt == null || sentAt <= 0L) return ""
        val day = PaymentLedger.formatDay(sentAt, zone)
        if (day.isBlank()) return ""
        return "Review request sent $day"
    }

    private fun mentionsLink(source: String, link: String): Boolean {
        if (REVIEW_LINK_TOKEN.containsMatchIn(source)) return true
        return link.isNotEmpty() && source.contains(link)
    }
}

enum class ReviewChannel { TEXT, EMAIL, SHARE }

data class ReviewComposer(
    val channel: ReviewChannel,
    val phone: String,
    val email: String,
    val subject: String,
    val body: String
)

data class ReviewOutbound(
    val action: String,
    val data: String?,
    val extras: Map<String, String>,
    val chooser: Boolean
)

@Serializable
data class GoogleReviewPrefs(
    val link: String = "",
    val template: String = "",
    val sentAtByJob: Map<String, Long> = emptyMap()
)

object ReviewSentLogCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        val parsed = runCatching { json.decodeFromString<Map<String, Long>>(raw) }.getOrDefault(emptyMap())
        return parsed.filterKeys { it.isNotBlank() }.filterValues { it > 0L }
    }

    fun encode(sentAtByJob: Map<String, Long>): String =
        json.encodeToString(sentAtByJob.filterKeys { it.isNotBlank() }.filterValues { it > 0L })
}
