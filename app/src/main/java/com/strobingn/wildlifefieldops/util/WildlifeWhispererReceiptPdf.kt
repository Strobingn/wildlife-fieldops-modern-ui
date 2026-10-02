package com.strobingn.wildlifefieldops.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.strobingn.wildlifefieldops.ai.fieldops.PaymentLedger
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.pricing.JobPaymentRecord
import com.strobingn.wildlifefieldops.pricing.Money
import com.strobingn.wildlifefieldops.pricing.PaymentMethod
import java.io.File
import java.io.FileOutputStream
import java.time.ZoneId

/** Shareable payment receipt. Uses the circular Wildlife Whisperer logo. */
object WildlifeWhispererReceiptPdf {
    fun generate(
        context: Context,
        job: Job,
        invoiceTotal: Double,
        payments: List<JobPaymentRecord>
    ): String {
        val pdf = PdfDocument()
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(612, 792, 1).create())
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)
        val ink = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(20, 20, 22)
            textSize = 11f
        }
        val title = Paint(ink).apply {
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val muted = Paint(ink).apply {
            textSize = 9f
            color = Color.rgb(90, 90, 90)
        }
        WildlifeWhispererBrand.drawLogo(canvas, context, 48f, 36f, 72)
        canvas.drawText(WildlifeWhispererBrand.COMPANY_UPPER, 136f, 58f, title)
        canvas.drawText("Payment receipt", 136f, 78f, muted)
        canvas.drawText(WildlifeWhispererBrand.ADDRESS, 136f, 94f, muted)
        var y = 140f
        canvas.drawText(job.customerName.ifBlank { job.title }, 48f, y, ink)
        y += 16f
        if (job.address.isNotBlank()) {
            canvas.drawText(job.address, 48f, y, muted)
            y += 16f
        }
        y += 8f
        payments.forEach { row ->
            val method = PaymentMethod.label(row.method)
            val check = if (row.method == PaymentMethod.CHECK && row.checkNumber.isNotBlank()) " #${row.checkNumber}" else ""
            val day = PaymentLedger.formatDay(row.paidAt, ZoneId.systemDefault())
            val note = row.note.trim()
            val line = listOf("$method$check", day, Money.formatUsd(row.amount), note)
                .filter { it.isNotBlank() }
                .joinToString("  ·  ")
            canvas.drawText(line.take(90), 48f, y, ink)
            y += 16f
        }
        y += 8f
        canvas.drawText("Invoice total  ${Money.formatUsd(invoiceTotal)}", 48f, y, ink)
        y += 16f
        canvas.drawText("Paid  ${Money.formatUsd(PaymentLedger.totalPaid(payments))}", 48f, y, ink)
        y += 18f
        canvas.drawText(
            "Balance due  ${Money.formatUsd(PaymentLedger.balanceDue(invoiceTotal, payments))}",
            48f,
            y,
            title
        )
        pdf.finishPage(page)
        val safe = job.customerName.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "customer" }
        val file = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "receipt_${safe}_${System.currentTimeMillis()}.pdf"
        )
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file.absolutePath
    }
}
