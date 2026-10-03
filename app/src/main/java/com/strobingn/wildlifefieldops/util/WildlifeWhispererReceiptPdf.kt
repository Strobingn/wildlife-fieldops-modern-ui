package com.strobingn.wildlifefieldops.util

import android.content.Context
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.pricing.JobPaymentRecord

/** Payment receipt on the shared Wildlife Whisperer document template. */
object WildlifeWhispererReceiptPdf {
    fun generate(
        context: Context,
        job: Job,
        invoiceTotal: Double,
        payments: List<JobPaymentRecord>,
        profile: BusinessProfile? = null,
        notes: String = "",
        terms: String = ""
    ): String {
        val packet = StandardJobPacket(
            job = job,
            profile = profile ?: BusinessProfileStore.load(context),
            total = invoiceTotal,
            payments = payments,
            notes = notes,
            terms = terms
        )
        val safe = job.customerName.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "customer" }
        return StandardDocumentPdf.write(
            context = context,
            document = StandardDocuments.build(DocumentKind.RECEIPT, packet),
            fileName = "receipt_${safe}_${System.currentTimeMillis()}.pdf"
        )
    }
}
