package com.strobingn.wildlifefieldops.util

import android.content.Context
import android.graphics.Bitmap
import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.pricing.JobPaymentRecord

enum class ContractDocumentType {
    ESTIMATE,
    INVOICE,
    CONTRACT
}

/**
 * Estimate, invoice, and service-contract PDFs. All three are painted by
 * [StandardDocumentPdf] from [StandardDocuments].
 */
object WildlifeWhispererContractPdf {
    fun generate(
        context: Context,
        documentType: ContractDocumentType,
        job: Job,
        lineItems: List<InvoiceLineItem>,
        subtotal: Double,
        taxRate: Double,
        taxAmount: Double,
        discountAmount: Double,
        total: Double,
        notes: String = "",
        technicianName: String = "",
        documentNumber: String = "",
        technicianSignature: Bitmap? = null,
        customerSignature: Bitmap? = null,
        customerSignerName: String = "",
        customerSignedAtMillis: Long? = null,
        balanceDue: Double? = null,
        terms: String = "",
        dueDateMillis: Long? = null,
        invoiceDateMillis: Long? = null,
        amountPaid: Double? = null,
        payments: List<JobPaymentRecord> = emptyList(),
        customerPhone: String = "",
        customerEmail: String = "",
        profile: BusinessProfile? = null
    ): String {
        val kind = when (documentType) {
            ContractDocumentType.ESTIMATE -> DocumentKind.ESTIMATE
            ContractDocumentType.INVOICE -> DocumentKind.INVOICE
            ContractDocumentType.CONTRACT -> DocumentKind.CONTRACT
        }
        val customerPng = customerSignature?.let { SignatureInk.encodePng(it) }.orEmpty()
        val companyPng = technicianSignature?.let { SignatureInk.encodePng(it) }.orEmpty()
        val packet = StandardJobPacket(
            job = job,
            profile = profile ?: BusinessProfileStore.load(context),
            lineItems = lineItems,
            subtotal = subtotal,
            taxRatePercent = taxRate,
            taxAmount = taxAmount,
            discountAmount = discountAmount,
            total = total,
            amountPaid = amountPaid,
            balanceDue = balanceDue,
            notes = notes,
            terms = terms,
            documentNumber = documentNumber,
            technicianName = technicianName,
            invoiceDateMillis = invoiceDateMillis,
            dueDateMillis = dueDateMillis,
            payments = payments,
            customerPhone = customerPhone,
            customerEmail = customerEmail,
            signerName = customerSignerName,
            signedAtMillis = customerSignedAtMillis,
            customerSignatureBase64 = customerPng,
            companySignatureBase64 = companyPng
        )
        val safe = job.customerName.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "customer" }
        val fileName = "${kind.filePrefix}_${safe}_${System.currentTimeMillis()}.pdf"
        return StandardDocumentPdf.write(
            context = context,
            document = StandardDocuments.build(kind, packet),
            fileName = fileName,
            customerSignature = customerSignature,
            companySignature = technicianSignature
        )
    }

    fun share(context: Context, path: String, chooserTitle: String = "Share PDF") {
        StandardDocumentPdf.share(context, path, chooserTitle)
    }

    fun view(context: Context, path: String) {
        StandardDocumentPdf.view(context, path)
    }

    fun print(context: Context, path: String, jobName: String) {
        StandardDocumentPdf.print(context, path, jobName)
    }
}
