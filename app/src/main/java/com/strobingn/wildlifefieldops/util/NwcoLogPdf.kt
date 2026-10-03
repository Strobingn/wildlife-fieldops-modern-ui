package com.strobingn.wildlifefieldops.util

import android.content.Context
import com.strobingn.wildlifefieldops.ai.fieldops.NwcoLogRecord
import com.strobingn.wildlifefieldops.ai.fieldops.NwcoOperatorProfile
import com.strobingn.wildlifefieldops.data.model.Job
import java.io.File
import java.util.Calendar

/**
 * NYS DEC nuisance wildlife control log. Same letterhead as every other
 * document; the log columns, codes, and Penal Law line stay in the body.
 */
object NwcoLogPdf {
    fun generate(
        context: Context,
        operator: NwcoOperatorProfile,
        rows: List<NwcoLogRecord>,
        licenseStartYear: Int
    ): File {
        val profile = BusinessProfileStore.load(context)
        val packet = StandardJobPacket(
            job = Job(
                customerName = operator.displayName(),
                address = operator.address,
                title = "NYS DEC NWCO log"
            ),
            profile = profile,
            customerPhone = operator.phone,
            nwcoOperator = operator,
            nwcoRows = rows,
            nwcoLicenseYear = licenseStartYear
        )
        val path = StandardDocumentPdf.write(
            context = context,
            document = StandardDocuments.build(DocumentKind.NWCO_LOG, packet),
            fileName = "nys-dec-nwco-log.pdf",
            cache = true
        )
        return File(path)
    }

    fun licenseStartYear(now: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val year = cal.get(Calendar.YEAR)
        return if (cal.get(Calendar.MONTH) >= Calendar.OCTOBER) year else year - 1
    }
}
