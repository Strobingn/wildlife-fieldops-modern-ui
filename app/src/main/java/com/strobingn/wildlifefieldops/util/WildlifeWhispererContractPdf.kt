package com.strobingn.wildlifefieldops.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.core.content.FileProvider
import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import com.strobingn.wildlifefieldops.data.model.Job
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ContractDocumentType {
    ESTIMATE,
    INVOICE
}

/**
 * Classic Wildlife Whisperer service-contract / estimate / invoice PDF.
 * Matches the clean one-page paper template (logo + letterhead, ruled fees,
 * signatures, short acceptance). Never prints mutual-obligations boilerplate.
 */
object WildlifeWhispererContractPdf {
    private const val PAGE_W = 612
    private const val PAGE_H = 792
    private const val MARGIN = 48f
    private const val CONTENT_RIGHT = PAGE_W - MARGIN
    private const val CONTENT_WIDTH = CONTENT_RIGHT - MARGIN

    private val INK = Color.rgb(20, 20, 22)
    private val MUTED = Color.rgb(95, 95, 100)
    private val RULE = Color.rgb(175, 175, 180)
    private val LIGHT_RULE = Color.rgb(210, 210, 214)

    /** Short page-1 acceptance only — never mutual obligations / lawyer back matter. */
    private val acceptanceBlurb =
        "Wildlife Whisperer LLC only recommends exclusion/repairs necessary to prevent future wildlife damage. " +
            "Fees and conditions are as explained by our representative. I have read and fully understand this " +
            "service contract and accept the recommendations and fees proposed by Wildlife Whisperer LLC."

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
        customerSignature: Bitmap? = null
    ): String {
        val pdf = PdfDocument()
        val anti = Paint().apply { isAntiAlias = true }

        val companyNamePaint = Paint(anti).apply {
            textSize = 14f
            color = INK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val companyDetailPaint = Paint(anti).apply {
            textSize = 9f
            color = MUTED
            textAlign = Paint.Align.LEFT
        }
        val titlePaint = Paint(anti).apply {
            textSize = 12f
            color = INK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val sectionPaint = Paint(anti).apply {
            textSize = 10f
            color = INK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val labelPaint = Paint(anti).apply {
            textSize = 9.5f
            color = INK
        }
        val smallPaint = Paint(anti).apply {
            textSize = 8f
            color = MUTED
        }
        val moneyPaint = Paint(labelPaint).apply { textAlign = Paint.Align.RIGHT }
        val hairline = Paint(anti).apply {
            color = RULE
            strokeWidth = 0.8f
            style = Paint.Style.STROKE
        }
        val lightHairline = Paint(anti).apply {
            color = LIGHT_RULE
            strokeWidth = 0.65f
            style = Paint.Style.STROKE
        }
        val checkPaint = Paint(anti).apply {
            color = INK
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        val checkFill = Paint(anti).apply {
            color = INK
            style = Paint.Style.FILL
        }

        val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        val docNo = documentNumber.ifBlank {
            val prefix = if (documentType == ContractDocumentType.ESTIMATE) "EST" else "INV"
            "$prefix-${System.currentTimeMillis() % 100000}"
        }
        val tech = technicianName.ifBlank { job.assignedTo }
        val dateStr = dateFormat.format(Date())

        val feeRows = mapFeeRows(lineItems)
        val workText = buildWorkText(notes, job)
        val workLines = if (workText.isBlank()) {
            List(5) { "" }
        } else {
            wrapTextToWidth(workText, labelPaint, CONTENT_WIDTH)
        }

        // Estimate how much fits on page 1; spill only overflow work / extra fee rows.
        val page1WorkBudget = 5
        val page1Work = workLines.take(page1WorkBudget)
        val overflowWork = workLines.drop(page1WorkBudget)
        val page1Fees = feeRows
        val needsOverflowPage = overflowWork.isNotEmpty()

        var pageIndex = 1
        val totalPagesEstimate = if (needsOverflowPage) 2 else 1

        val page1 = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageIndex).create())
        val c = page1.canvas
        c.drawColor(Color.WHITE)
        var y = 32f

        // —— Letterhead: circular logo LEFT, company block RIGHT (clean one-page template) ——
        val logoSize = 88
        WildlifeWhispererBrand.drawLogo(c, context, MARGIN, y, logoSize)
        var rightY = y + 22f
        val textLeft = MARGIN + logoSize + 14f
        c.drawText(WildlifeWhispererBrand.COMPANY_UPPER, textLeft, rightY, companyNamePaint)
        rightY += 14f
        c.drawText(WildlifeWhispererBrand.ADDRESS, textLeft, rightY, companyDetailPaint)
        rightY += 12f
        c.drawText(
            "${WildlifeWhispererBrand.PHONE} · ${WildlifeWhispererBrand.EMAIL}",
            textLeft,
            rightY,
            companyDetailPaint
        )
        y = maxOf(y + logoSize + 8f, rightY + 12f)
        c.drawLine(MARGIN, y, CONTENT_RIGHT, y, hairline)
        y += 16f

        // —— Title ——
        c.drawText("SERVICE CONTRACT · ESTIMATE · INVOICE", PAGE_W / 2f, y, titlePaint)
        y += 18f

        // —— Estimate / Invoice checkboxes + # / Date / Tech ——
        fun drawCheckbox(x: Float, checked: Boolean) {
            val s = 9f
            c.drawRect(x, y - 8f, x + s, y + 1f, checkPaint)
            if (checked) {
                c.drawRect(x + 2f, y - 6f, x + s - 2f, y - 1f, checkFill)
            }
        }
        val isEstimate = documentType == ContractDocumentType.ESTIMATE
        val isInvoice = documentType == ContractDocumentType.INVOICE
        drawCheckbox(MARGIN, isEstimate)
        c.drawText("Estimate", MARGIN + 14f, y, labelPaint)
        drawCheckbox(MARGIN + 90f, isInvoice)
        c.drawText("Invoice", MARGIN + 104f, y, labelPaint)

        val metaPaint = Paint(labelPaint)
        c.drawText("# $docNo", MARGIN + 200f, y, metaPaint)
        c.drawText("Date $dateStr", MARGIN + 330f, y, metaPaint)
        val techLabel = if (tech.isBlank()) "Tech ________" else "Tech $tech"
        c.drawText(techLabel.take(28), MARGIN + 440f, y, metaPaint)
        y += 10f
        c.drawLine(MARGIN, y, CONTENT_RIGHT, y, lightHairline)
        y += 16f

        // —— BILL TO / OWNER ——
        c.drawText("BILL TO / OWNER", MARGIN, y, sectionPaint)
        y += 14f

        val (street, cityZip) = splitAddress(job.address)
        val billRows = listOf(
            "Owner/Manager:" to job.customerName,
            "Address:" to street,
            "City/State/ZIP:" to cityZip,
            "Phone:" to "",
            "Email:" to ""
        )
        billRows.forEach { (label, value) ->
            c.drawText(label, MARGIN, y, labelPaint)
            val labelW = labelPaint.measureText(label) + 6f
            val lineStart = MARGIN + labelW
            c.drawLine(lineStart, y + 1f, CONTENT_RIGHT, y + 1f, lightHairline)
            if (value.isNotBlank()) {
                c.drawText(value.take(70), lineStart + 4f, y, labelPaint)
            }
            y += 16f
        }
        y += 4f

        // —— SERVICE & ANIMAL FEES ——
        c.drawText("SERVICE & ANIMAL FEES", MARGIN, y, sectionPaint)
        y += 12f
        page1Fees.forEach { (label, amount) ->
            c.drawText(label, MARGIN, y, labelPaint)
            val amountText = if (amount != null) {
                "$" + String.format(Locale.US, "%.2f", amount)
            } else {
                "$" + "_".repeat(14)
            }
            c.drawText(amountText, CONTENT_RIGHT, y, moneyPaint)
            y += 5f
            c.drawLine(MARGIN, y, CONTENT_RIGHT, y, lightHairline)
            y += 13f
        }
        y += 4f

        // —— WORK PERFORMED / PROPOSED ——
        c.drawText("WORK PERFORMED / PROPOSED", MARGIN, y, sectionPaint)
        y += 14f
        val workSlots = maxOf(page1Work.size, 5)
        repeat(workSlots) { i ->
            val line = page1Work.getOrNull(i).orEmpty()
            if (line.isNotBlank()) {
                c.drawText(line.take(95), MARGIN, y, labelPaint)
            }
            c.drawLine(MARGIN, y + 2f, CONTENT_RIGHT, y + 2f, lightHairline)
            y += 14f
        }
        y += 6f

        // —— Totals (right-aligned) ——
        fun drawMoneyRow(label: String, value: Double, bold: Boolean = false) {
            val lp = Paint(if (bold) sectionPaint else labelPaint).apply {
                textAlign = Paint.Align.LEFT
                textSize = if (bold) 10.5f else 9.5f
            }
            val rp = Paint(lp).apply { textAlign = Paint.Align.RIGHT }
            val labelX = 360f
            c.drawText(label, labelX, y, lp)
            c.drawText("$" + String.format(Locale.US, "%.2f", value), CONTENT_RIGHT, y, rp)
            y += if (bold) 15f else 13f
        }
        drawMoneyRow("Sub-Total:", subtotal)
        if (discountAmount > 0.0) drawMoneyRow("Discount:", -discountAmount)
        val taxLabel = if (taxRate > 0.0) {
            "Tax (" + String.format(Locale.US, "%.3f", taxRate).trimEnd('0').trimEnd('.') + "%):"
        } else {
            "Tax:"
        }
        drawMoneyRow(taxLabel, taxAmount)
        y += 1f
        c.drawLine(360f, y - 3f, CONTENT_RIGHT, y - 3f, hairline)
        y += 2f
        drawMoneyRow("Grand-Total:", total, bold = true)
        y += 10f

        // —— Signatures ——
        val sigY = y.coerceAtMost(PAGE_H - 120f)
        y = sigY
        val sigLineW = 220f
        val dateLineW = 90f

        if (customerSignature != null) {
            val scaled = Bitmap.createScaledBitmap(customerSignature, 160, 42, true)
            c.drawBitmap(scaled, MARGIN + 100f, y - 44f, null)
        }
        c.drawText("Owner Signature:", MARGIN, y, labelPaint)
        val ownerLineStart = MARGIN + labelPaint.measureText("Owner Signature:") + 6f
        c.drawLine(ownerLineStart, y + 1f, ownerLineStart + sigLineW, y + 1f, lightHairline)
        c.drawText("Date:", ownerLineStart + sigLineW + 12f, y, labelPaint)
        val ownerDateStart = ownerLineStart + sigLineW + 12f + labelPaint.measureText("Date:") + 4f
        c.drawLine(ownerDateStart, y + 1f, ownerDateStart + dateLineW, y + 1f, lightHairline)
        y += 28f

        if (technicianSignature != null) {
            val scaled = Bitmap.createScaledBitmap(technicianSignature, 160, 42, true)
            c.drawBitmap(scaled, MARGIN + 110f, y - 44f, null)
        }
        c.drawText("Company Signature:", MARGIN, y, labelPaint)
        val coLineStart = MARGIN + labelPaint.measureText("Company Signature:") + 6f
        c.drawLine(coLineStart, y + 1f, coLineStart + sigLineW - 10f, y + 1f, lightHairline)
        c.drawText("Date:", coLineStart + sigLineW + 2f, y, labelPaint)
        val coDateStart = coLineStart + sigLineW + 2f + labelPaint.measureText("Date:") + 4f
        c.drawLine(coDateStart, y + 1f, coDateStart + dateLineW, y + 1f, lightHairline)
        y += 22f

        // —— Short acceptance (page 1 only) ——
        val blurbPaint = Paint(smallPaint).apply {
            textSize = 7f
            color = MUTED
        }
        wrapTextToWidth(acceptanceBlurb, blurbPaint, CONTENT_WIDTH).forEach { line ->
            if (y < PAGE_H - 42f) {
                c.drawText(line, MARGIN, y, blurbPaint)
                y += 9f
            }
        }

        drawFooter(c, pageIndex, totalPagesEstimate)
        pdf.finishPage(page1)

        // —— Optional page 2 ONLY for overflow work text (never lawyer terms) ——
        if (needsOverflowPage) {
            pageIndex = 2
            val page2 = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageIndex).create())
            val c2 = page2.canvas
            c2.drawColor(Color.WHITE)
            var y2 = 48f
            val contTitle = Paint(anti).apply {
                textSize = 11f
                color = INK
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            c2.drawText("WORK PERFORMED / PROPOSED (continued)", PAGE_W / 2f, y2, contTitle)
            y2 += 10f
            c2.drawLine(MARGIN, y2, CONTENT_RIGHT, y2, hairline)
            y2 += 16f
            overflowWork.forEach { line ->
                if (y2 < PAGE_H - 48f) {
                    c2.drawText(line.take(95), MARGIN, y2, labelPaint)
                    y2 += 5f
                    c2.drawLine(MARGIN, y2, CONTENT_RIGHT, y2, lightHairline)
                    y2 += 13f
                }
            }
            drawFooter(c2, pageIndex, 2)
            pdf.finishPage(page2)
        }

        val prefix = if (documentType == ContractDocumentType.ESTIMATE) "estimate" else "invoice"
        val safeName = job.customerName.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "customer" }
        val fileName = "${prefix}_${safeName}_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file.absolutePath
    }

    fun share(context: Context, path: String, chooserTitle: String = "Share PDF") {
        val file = File(path)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun view(context: Context, path: String) {
        val file = File(path)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun drawFooter(canvas: Canvas, page: Int, totalPages: Int) {
        val footer = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = 7f
            color = MUTED
        }
        val rule = Paint().apply {
            color = RULE
            strokeWidth = 0.6f
        }
        canvas.drawLine(MARGIN, PAGE_H - 36f, CONTENT_RIGHT, PAGE_H - 36f, rule)
        canvas.drawText(
            "${WildlifeWhispererBrand.COMPANY_UPPER} · 210 Willow Avenue, Cornwall, NY 12518 · ${WildlifeWhispererBrand.PHONE}",
            PAGE_W / 2f,
            PAGE_H - 22f,
            footer
        )
        if (totalPages > 1) {
            canvas.drawText("Page $page of $totalPages", PAGE_W / 2f, PAGE_H - 12f, footer)
        }
    }

    private fun buildWorkText(notes: String, job: Job): String {
        return buildString {
            if (notes.isNotBlank()) append(notes.trim())
            if (job.description.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(job.description.trim())
            }
            if (job.notes.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(job.notes.trim())
            }
        }
    }

    private fun splitAddress(address: String): Pair<String, String> {
        if (address.isBlank()) return "" to ""
        val parts = address.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return when {
            parts.size >= 3 -> parts.dropLast(2).joinToString(", ") to parts.takeLast(2).joinToString(", ")
            parts.size == 2 -> parts[0] to parts[1]
            else -> address to ""
        }
    }

    /**
     * Map invoice line items into the classic five fee rows when possible.
     * Unmatched amounts roll into Other / Exclusion & Repairs (with description notes
     * appended into the Other label when needed).
     */
    private fun mapFeeRows(lineItems: List<InvoiceLineItem>): List<Pair<String, Double?>> {
        if (lineItems.isEmpty()) {
            return listOf(
                "Trap Service Fee" to null,
                "Per Animal Captured Fee" to null,
                "Non-Target Animal Captured Fee" to null,
                "Inspection Fee" to null,
                "Other / Exclusion & Repairs" to null
            )
        }

        val used = mutableSetOf<String>()
        fun take(vararg keys: String): Double? {
            val match = lineItems.firstOrNull { item ->
                if (item.id in used) return@firstOrNull false
                val d = item.description.lowercase(Locale.US)
                keys.any { k -> d.contains(k) }
            } ?: return null
            used.add(match.id)
            return match.calculateTotal()
        }

        val trap = take("trap service", "trap fee", "trap setup", "trap set")
        val perAnimal = take("per animal", "animal captured", "capture fee", "animal fee")
        val nonTarget = take("non-target", "nontarget", "non target")
        val inspection = take("inspection")
        val otherMatched = take(
            "exclusion", "repair", "sealing", "entry point", "materials", "labor", "other"
        )

        val leftover = lineItems.filter { it.id !in used }
        val otherTotal = (otherMatched ?: 0.0) + leftover.sumOf { it.calculateTotal() }
        val otherLabel = if (leftover.isNotEmpty() && leftover.size <= 2 && leftover.all {
                it.description.isNotBlank() && !it.description.lowercase(Locale.US).let { d ->
                    d.contains("exclusion") || d.contains("repair") || d.contains("other")
                }
            }
        ) {
            val descs = leftover.joinToString("; ") { it.description.take(28) }
            "Other / Exclusion & Repairs ($descs)"
        } else {
            "Other / Exclusion & Repairs"
        }
        val otherAmount: Double? = when {
            leftover.isNotEmpty() || otherMatched != null -> otherTotal
            else -> null
        }

        return listOf(
            "Trap Service Fee" to trap,
            "Per Animal Captured Fee" to perAnimal,
            "Non-Target Animal Captured Fee" to nonTarget,
            "Inspection Fee" to inspection,
            otherLabel to otherAmount
        )
    }

    private fun wrapTextToWidth(text: String, paint: Paint, maxWidth: Float): List<String> {
        val out = mutableListOf<String>()
        text.replace("\r", "").split('\n').forEach { paragraph ->
            var remaining = paragraph.trim()
            if (remaining.isEmpty()) {
                out.add("")
                return@forEach
            }
            while (remaining.isNotEmpty()) {
                if (paint.measureText(remaining) <= maxWidth) {
                    out.add(remaining)
                    break
                }
                var breakAt = remaining.length
                while (breakAt > 0 && paint.measureText(remaining.substring(0, breakAt)) > maxWidth) {
                    breakAt--
                }
                val space = remaining.lastIndexOf(' ', breakAt)
                if (space > 0) breakAt = space
                if (breakAt <= 0) breakAt = remaining.length.coerceAtMost(1)
                out.add(remaining.substring(0, breakAt).trimEnd())
                remaining = remaining.substring(breakAt).trimStart()
            }
        }
        return out
    }
}
