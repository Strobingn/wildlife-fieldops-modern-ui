package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.pricing.CustomerSignatureRecord
import com.strobingn.wildlifefieldops.pricing.JobPricing

/**
 * Technician (Sir's) signature per document, stored in job pricing next to
 * the customer signature so it syncs the same way (jobs.pricing jsonb).
 *
 * - No record yet: the signature saved in Settings fills the box.
 * - Sir re-signs: that ink is stored for this document only.
 * - Sir clears: a record with [CustomerSignatureRecord.cleared] is stored, so
 *   the box stays blank through save, reload and sync and is never refilled.
 */
object TechSignatureRules {
    const val ESTIMATE = SignatureRules.ESTIMATE
    const val CONTRACT = SignatureRules.CONTRACT
    const val INVOICE = "invoice"
    const val INSPECTION = "inspection"

    data class Resolved(
        val pngBase64: String,
        val name: String,
        val signedAt: Long,
        /** True when the box shows the Settings signature, not a per-document one. */
        val fromSaved: Boolean,
        val cleared: Boolean
    ) {
        val hasInk: Boolean get() = pngBase64.isNotBlank()
    }

    fun find(pricing: JobPricing, document: String): CustomerSignatureRecord? =
        pricing.technicianSignatures.firstOrNull { it.document == document }

    fun resolve(
        record: CustomerSignatureRecord?,
        saved: TechnicianSignature,
        now: Long = System.currentTimeMillis()
    ): Resolved = when {
        record == null && saved.isSet ->
            Resolved(saved.pngBase64, saved.name, now, fromSaved = true, cleared = false)
        record == null -> Resolved("", "", 0L, fromSaved = false, cleared = false)
        record.cleared -> Resolved("", "", 0L, fromSaved = false, cleared = true)
        record.pngBase64.isBlank() && saved.pngBase64.isNotBlank() ->
            // Keep the record's first stamped time; only a never-stamped record gets now().
            Resolved(saved.pngBase64, record.signerName.ifBlank { saved.name }, record.signedAt.takeIf { it > 0L } ?: now, fromSaved = true, cleared = false)
        else -> Resolved(record.pngBase64, record.signerName, record.signedAt, fromSaved = false, cleared = false)
    }

    fun resolve(pricing: JobPricing, document: String, saved: TechnicianSignature, now: Long = System.currentTimeMillis()): Resolved =
        resolve(find(pricing, document), saved, now)

    /** Blank name and ink = cleared (stays blank). */
    fun save(pricing: JobPricing, document: String, name: String, signedAt: Long, pngBase64: String): JobPricing {
        val n = name.trim()
        val ink = pngBase64.trim()
        val cleared = n.isBlank() && ink.isBlank()
        val record = CustomerSignatureRecord(
            document = document,
            signerName = n,
            signedAt = if (cleared) 0L else signedAt,
            pngBase64 = ink,
            typedOnly = ink.isBlank() && n.isNotBlank(),
            cleared = cleared
        )
        return pricing.copy(technicianSignatures = pricing.technicianSignatures.filterNot { it.document == document } + record)
    }

    fun clear(pricing: JobPricing, document: String): JobPricing = save(pricing, document, "", 0L, "")
}

/** Sir's saved signature from Settings. */
data class TechnicianSignature(
    val pngBase64: String = "",
    val name: String = "",
    val savedAt: Long = 0L
) {
    val isSet: Boolean get() = pngBase64.isNotBlank() || name.isNotBlank()
}
