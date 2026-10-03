package com.strobingn.wildlifefieldops.util

import android.content.Context
import com.strobingn.wildlifefieldops.data.model.Job
import java.io.File

object EarningsTaxPdf {
    fun generate(context: Context, csv: String): File {
        val profile = BusinessProfileStore.load(context)
        val packet = StandardJobPacket(
            job = Job(customerName = profile.name, title = "Earnings & NY sales tax"),
            profile = profile,
            earningsCsv = csv
        )
        val path = StandardDocumentPdf.write(
            context = context,
            document = StandardDocuments.build(DocumentKind.EARNINGS_TAX, packet),
            fileName = "ny-earnings-sales-tax.pdf",
            cache = true
        )
        return File(path)
    }
}
