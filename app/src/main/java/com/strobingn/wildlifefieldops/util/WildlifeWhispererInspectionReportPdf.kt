package com.strobingn.wildlifefieldops.util

import android.content.Context
import android.graphics.Bitmap

/**
 * Inspection report on the shared document template.
 * [ReportFields] stays the call-site shape used by the inspection form.
 */
object WildlifeWhispererInspectionReportPdf {
    fun generate(
        context: Context,
        fields: InspectionReportFields,
        qr: Bitmap? = null,
        profile: BusinessProfile? = null
    ): String {
        val job = com.strobingn.wildlifefieldops.data.model.Job(
            customerName = fields.customerName,
            title = fields.jobTitle,
            address = fields.jobAddress,
            notes = fields.findings,
            legalNotes = fields.notes,
            nextStep = fields.recommendations,
            confirmedSpecies = fields.species,
            assignedTo = fields.inspectorName
        )
        val packet = StandardJobPacket(
            job = job,
            profile = profile ?: BusinessProfileStore.load(context),
            technicianName = fields.inspectorName,
            inspection = fields,
            notes = fields.notes,
            includeQr = qr != null,
            nowMillis = fields.inspectionDate.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
        val safe = fields.customerName.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "inspection" }
        return StandardDocumentPdf.write(
            context = context,
            document = StandardDocuments.build(DocumentKind.INSPECTION, packet),
            fileName = "inspection_${safe}_${System.currentTimeMillis()}.pdf",
            qr = qr
        )
    }

    fun share(context: Context, path: String, chooserTitle: String = "Share Inspection Report") {
        StandardDocumentPdf.share(context, path, chooserTitle)
    }

    fun view(context: Context, path: String) {
        StandardDocumentPdf.view(context, path)
    }

    fun print(context: Context, path: String) {
        StandardDocumentPdf.print(context, path, "Inspection report")
    }
}
