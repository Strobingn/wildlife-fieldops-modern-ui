package com.strobingn.wildlifefieldops.util

import android.content.Context
import com.strobingn.wildlifefieldops.data.model.Job

object WildlifeWhispererExclusionPdf {
    fun generate(context: Context, job: Job, notes: String = ""): String {
        val packet = StandardJobPacket(
            job = job,
            profile = BusinessProfileStore.load(context),
            notes = notes
        )
        val safe = job.customerName.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "exclusion" }
        return StandardDocumentPdf.write(
            context = context,
            document = StandardDocuments.build(DocumentKind.EXCLUSION, packet),
            fileName = "exclusion_${safe}_${System.currentTimeMillis()}.pdf"
        )
    }
}
