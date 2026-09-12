package com.strobingn.wildlifefieldops.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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
 * Professional letter-page service contract PDF for estimates and invoices.
 * Classic invoice layout: logo left / company block right, ruled tables, hairlines.
 */
object WildlifeWhispererContractPdf {
    private const val PAGE_W = 612
    private const val PAGE_H = 792
    private const val MARGIN = 44f
    private const val CONTENT_RIGHT = PAGE_W - MARGIN

    private val INK = Color.rgb(28, 28, 30)
    private val MUTED = Color.rgb(90, 90, 95)
    private val RULE = Color.rgb(200, 200, 205)
    private val LIGHT_FILL = Color.rgb(247, 247, 248)
    private val BADGE_BG = Color.rgb(36, 36, 40)

    private val acceptanceBlurb =
        "Wildlife Whisperer LLC only recommends the exclusion and/or repairs that are necessary to prevent " +
            "future wildlife damage. The fees, specifications and conditions of this service contract are " +
            "satisfactory and the same as those explained to me by the Wildlife Whisperer LLC representative. " +
            "I have read and fully understand all the information on this service contract and accept the " +
            "recommendations and fees proposed by Wildlife Whisperer LLC. Payments will be made as outlined " +
            "in this service contract."

    private val legalTerms = listOf(
        "I. OWNER'S OBLIGATIONS:",
        "A. NUISANCE WILDLIFE INSPECTION FEE: Covers the initial exterior inspection and recommended exclusion and/or repair estimate. Due in full upon completion of inspection.",
        "B. TRAP SERVICE FEE AND ANIMAL FEES: Non-refundable trap service fee for trap(s) set up and retainer for up to 7 days, or until damage stops, whichever comes first, regardless of whether animals are trapped. Per-animal and non-target fees are additional and do not include exclusion/repairs.",
        "C. RECOMMENDED EXCLUSION AND/OR REPAIRS: Only necessary work is recommended. Materials remain property of Wildlife Whisperer LLC until paid in full. Payment due upon completion.",
        "D. ACCESS: Owner shall permit free entrance/exit with materials and equipment at reasonable times.",
        "E. FINES: Outstanding balances after completion accrue finance charges as stated in the paper contract; NSF checks incur a \$35 fee plus bank charges; owner pays collection costs including reasonable attorney fees.",
        "II. WILDLIFE WHISPERER LLC'S OBLIGATIONS:",
        "A. LICENSES: Full-time employees are Nuisance Wildlife Control Operators licensed through NYS DEC.",
        "B. WORKMANSHIP: Services rendered in a good workmanlike manner; greatest care taken to avoid property damage.",
        "C. ANIMAL DISPOSITION: Per applicable federal, state, and local law. Non-target animals released on owner's property unless otherwise agreed in writing.",
        "III. EXCLUSION AND REPAIR LIMITED WARRANTY: 3-year limited warranty on exclusions/repairs performed by Wildlife Whisperer LLC as stated in the service contract. Not a guarantee against all wildlife entry or damage.",
        "IV. DISCLAIMERS: Traps and wildlife may be hazardous. Wildlife Whisperer LLC is not liable for injuries or damages from traps, captured animals, or animals on the premises as set forth in the full service contract.",
        "V. RIGHT TO REMOVE TRAPS: Wildlife Whisperer LLC may remove traps for safety, public resistance, or suspected tampering; owner remains responsible for fees owed to that point.",
        "VI. STOLEN TRAPS: Owner is responsible for traps on the property and may be charged replacement fees for stolen traps."
    )

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
            textSize = 13f
            color = INK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val companyDetailPaint = Paint(anti).apply {
            textSize = 8.5f
            color = MUTED
            textAlign = Paint.Align.RIGHT
        }
        val titlePaint = Paint(anti).apply {
            textSize = 11f
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val headerPaint = Paint(anti).apply {
            textSize = 10.5f
            color = INK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val normalPaint = Paint(anti).apply {
            textSize = 9.5f
            color = INK
        }
        val smallPaint = Paint(anti).apply {
            textSize = 8f
            color = MUTED
        }
        val tableHeaderPaint = Paint(anti).apply {
            textSize = 8.5f
            color = MUTED
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val moneyPaint = Paint(normalPaint).apply { textAlign = Paint.Align.RIGHT }
        val hairline = Paint(anti).apply {
            color = RULE
            strokeWidth = 0.75f
            style = Paint.Style.STROKE
        }
        val fillPaint = Paint(anti).apply {
            color = LIGHT_FILL
            style = Paint.Style.FILL
        }
        val badgePaint = Paint(anti).apply {
            color = BADGE_BG
            style = Paint.Style.FILL
        }

        val blankAmount = "$" + "____________"
        val blankLine = "____________________"

        val page1 = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val c = page1.canvas
        c.drawColor(Color.WHITE)
        var y = 36f

        // —— Header: logo LEFT, company RIGHT ——
        val logoSize = 110
        WildlifeWhispererBrand.drawLogo(c, context, MARGIN, y, logoSize)

        var rightY = y + 18f
        c.drawText(WildlifeWhispererBrand.COMPANY_UPPER, CONTENT_RIGHT, rightY, companyNamePaint)
        rightY += 13f
        c.drawText(WildlifeWhispererBrand.ADDRESS, CONTENT_RIGHT, rightY, companyDetailPaint)
        rightY += 11f
        c.drawText(WildlifeWhispererBrand.PHONE, CONTENT_RIGHT, rightY, companyDetailPaint)
        rightY += 11f
        c.drawText(WildlifeWhispererBrand.EMAIL, CONTENT_RIGHT, rightY, companyDetailPaint)

        y = maxOf(y + logoSize + 10f, rightY + 14f)
        c.drawLine(MARGIN, y, CONTENT_RIGHT, y, hairline)
        y += 14f

        // —— Meta row: badge + doc # + date + tech ——
        val typeLabel = when (documentType) {
            ContractDocumentType.ESTIMATE -> "ESTIMATE"
            ContractDocumentType.INVOICE -> "INVOICE"
        }
        val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        val docNo = documentNumber.ifBlank {
            val prefix = if (documentType == ContractDocumentType.ESTIMATE) "EST" else "INV"
            "$prefix-${System.currentTimeMillis() % 100000}"
        }
        val tech = technicianName.ifBlank { job.assignedTo }.ifBlank { blankLine }

        val badgeW = 88f
        val badgeH = 20f
        c.drawRoundRect(RectF(MARGIN, y - 13f, MARGIN + badgeW, y + 7f), 3f, 3f, badgePaint)
        c.drawText(typeLabel, MARGIN + badgeW / 2f, y + 1f, titlePaint)

        normalPaint.textAlign = Paint.Align.LEFT
        c.drawText("# $docNo", MARGIN + badgeW + 14f, y, normalPaint)
        c.drawText("Date: ${dateFormat.format(Date())}", 300f, y, normalPaint)
        c.drawText("Tech: $tech", 430f, y, smallPaint)
        y += 18f
        c.drawLine(MARGIN, y, CONTENT_RIGHT, y, hairline)
        y += 14f

        // —— Billed-to box ——
        headerPaint.textSize = 9f
        c.drawText("BILLED TO", MARGIN, y, headerPaint)
        y += 6f
        val boxTop = y
        val boxPad = 10f
        val owner = job.customerName.ifBlank { blankLine }
        val addr = job.address.ifBlank { blankLine }
        val jobTitle = job.title.ifBlank { blankLine }
        val boxLines = listOf(
            "Owner / Manager: $owner",
            "Service address: $addr",
            "Job: $jobTitle"
        )
        val boxH = boxPad * 2 + boxLines.size * 13f
        c.drawRoundRect(RectF(MARGIN, boxTop, CONTENT_RIGHT, boxTop + boxH), 4f, 4f, fillPaint)
        c.drawRoundRect(RectF(MARGIN, boxTop, CONTENT_RIGHT, boxTop + boxH), 4f, 4f, hairline)
        var by = boxTop + boxPad + 10f
        boxLines.forEach { line ->
            c.drawText(line, MARGIN + boxPad, by, normalPaint)
            by += 13f
        }
        y = boxTop + boxH + 16f

        // —— Fee section (2-column ruled table) ——
        headerPaint.textSize = 10.5f
        c.drawText("Service & Animal Fees", MARGIN, y, headerPaint)
        y += 10f

        val feeRows = mapFeeRows(lineItems)
        val colMid = 420f
        // header rule
        c.drawRect(MARGIN, y, CONTENT_RIGHT, y + 16f, fillPaint)
        c.drawText("Description", MARGIN + 8f, y + 11f, tableHeaderPaint)
        tableHeaderPaint.textAlign = Paint.Align.RIGHT
        c.drawText("Amount", CONTENT_RIGHT - 8f, y + 11f, tableHeaderPaint)
        tableHeaderPaint.textAlign = Paint.Align.LEFT
        y += 16f
        c.drawLine(MARGIN, y, CONTENT_RIGHT, y, hairline)

        feeRows.forEach { (label, amount) ->
            y += 14f
            c.drawText(label, MARGIN + 8f, y, normalPaint)
            val amountText = if (amount != null) {
                "$" + String.format(Locale.US, "%.2f", amount)
            } else {
                blankAmount
            }
            c.drawText(amountText, CONTENT_RIGHT - 8f, y, moneyPaint)
            y += 4f
            c.drawLine(MARGIN, y, CONTENT_RIGHT, y, hairline)
        }
        y += 14f

        // —— Line items table ——
        if (lineItems.isNotEmpty()) {
            headerPaint.textSize = 10.5f
            c.drawText("Line Items", MARGIN, y, headerPaint)
            y += 10f
            c.drawRect(MARGIN, y, CONTENT_RIGHT, y + 16f, fillPaint)
            c.drawText("Description", MARGIN + 8f, y + 11f, tableHeaderPaint)
            tableHeaderPaint.textAlign = Paint.Align.RIGHT
            c.drawText("Qty", colMid - 40f, y + 11f, tableHeaderPaint)
            c.drawText("Rate", colMid + 30f, y + 11f, tableHeaderPaint)
            c.drawText("Amount", CONTENT_RIGHT - 8f, y + 11f, tableHeaderPaint)
            tableHeaderPaint.textAlign = Paint.Align.LEFT
            y += 16f
            c.drawLine(MARGIN, y, CONTENT_RIGHT, y, hairline)

            val maxItems = 7
            lineItems.take(maxItems).forEach { item ->
                y += 13f
                val desc = item.description.ifBlank { "(item)" }.take(42)
                c.drawText(desc, MARGIN + 8f, y, smallPaint.apply { color = INK })
                smallPaint.color = MUTED
                moneyPaint.textSize = 8.5f
                moneyPaint.color = INK
                c.drawText(String.format(Locale.US, "%.1f", item.quantity), colMid - 40f, y, moneyPaint)
                c.drawText("$" + String.format(Locale.US, "%.2f", item.unitPrice), colMid + 30f, y, moneyPaint)
                c.drawText("$" + String.format(Locale.US, "%.2f", item.calculateTotal()), CONTENT_RIGHT - 8f, y, moneyPaint)
                moneyPaint.textSize = 9.5f
                y += 3f
                c.drawLine(MARGIN, y, CONTENT_RIGHT, y, hairline)
            }
            if (lineItems.size > maxItems) {
                y += 12f
                c.drawText("… +${lineItems.size - maxItems} more items", MARGIN + 8f, y, smallPaint)
                y += 4f
            }
            y += 12f
        }

        // —— Work notes ——
        val workText = buildString {
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
        if (workText.isNotBlank()) {
            headerPaint.textSize = 10f
            c.drawText("Exclusion, Trapping and/or Repairs", MARGIN, y, headerPaint)
            y += 12f
            wrapText(workText, 92).take(4).forEach { line ->
                c.drawText(line, MARGIN, y, smallPaint)
                y += 10f
            }
            y += 6f
        }

        c.drawLine(MARGIN, y, CONTENT_RIGHT, y, hairline)
        y += 16f

        // —— Totals (right-aligned block) ——
        fun drawMoneyRow(label: String, value: Double, bold: Boolean = false) {
            val lp = Paint(if (bold) headerPaint else normalPaint).apply {
                textAlign = Paint.Align.LEFT
                textSize = if (bold) 11f else 9.5f
            }
            val rp = Paint(lp).apply { textAlign = Paint.Align.RIGHT }
            c.drawText(label, 380f, y, lp)
            c.drawText("$" + String.format(Locale.US, "%.2f", value), CONTENT_RIGHT, y, rp)
            y += if (bold) 16f else 13f
        }

        drawMoneyRow("Sub-Total", subtotal)
        if (discountAmount > 0.0) drawMoneyRow("Discount", -discountAmount)
        val taxLabel = "Tax (" + String.format(Locale.US, "%.3f", taxRate).trimEnd('0').trimEnd('.') + "%)"
        drawMoneyRow(taxLabel, taxAmount)
        y += 2f
        c.drawLine(380f, y - 4f, CONTENT_RIGHT, y - 4f, hairline)
        drawMoneyRow("Grand-Total", total, bold = true)

        y += 8f
        c.drawLine(MARGIN, y, CONTENT_RIGHT, y, hairline)
        y += 28f

        // —— Signatures ——
        val sigBase = y.coerceAtMost(PAGE_H - 150f)
        val leftSigX = MARGIN
        val rightSigX = 330f
        val sigWidth = 180f

        if (customerSignature != null) {
            val scaled = Bitmap.createScaledBitmap(customerSignature, 150, 48, true)
            c.drawBitmap(scaled, leftSigX, sigBase - 48f, null)
        }
        c.drawLine(leftSigX, sigBase, leftSigX + sigWidth, sigBase, hairline)
        c.drawText("Owner Signature", leftSigX, sigBase + 12f, smallPaint)
        c.drawText("Date: " + "____________", leftSigX, sigBase + 24f, smallPaint)

        if (technicianSignature != null) {
            val scaled = Bitmap.createScaledBitmap(technicianSignature, 150, 48, true)
            c.drawBitmap(scaled, rightSigX, sigBase - 48f, null)
        }
        c.drawLine(rightSigX, sigBase, rightSigX + sigWidth, sigBase, hairline)
        c.drawText("Company Signature", rightSigX, sigBase + 12f, smallPaint)
        c.drawText("Date: " + "____________", rightSigX, sigBase + 24f, smallPaint)

        var blurbY = sigBase + 40f
        val blurbPaint = Paint(smallPaint).apply {
            textSize = 7f
            color = MUTED
        }
        wrapText(acceptanceBlurb, 108).forEach { line ->
            if (blurbY < PAGE_H - 40f) {
                c.drawText(line, MARGIN, blurbY, blurbPaint)
                blurbY += 9f
            }
        }
        drawFooter(c, 1, 2)
        pdf.finishPage(page1)

        // —— Page 2: mutual obligations ——
        val page2 = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 2).create())
        val c2 = page2.canvas
        c2.drawColor(Color.WHITE)
        var y2 = 48f
        val page2Title = Paint(anti).apply {
            textSize = 12f
            color = INK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        c2.drawText("MUTUAL OBLIGATIONS AND DISCLAIMERS", PAGE_W / 2f, y2, page2Title)
        y2 += 8f
        c2.drawLine(MARGIN, y2, CONTENT_RIGHT, y2, hairline)
        y2 += 16f

        val termPaint = Paint(anti).apply {
            textSize = 8f
            color = MUTED
        }
        val termBold = Paint(termPaint).apply {
            color = INK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        legalTerms.forEach { term ->
            val isHeading = term.startsWith("I.") || term.startsWith("II.") ||
                term.startsWith("III.") || term.startsWith("IV.") ||
                term.startsWith("V.") || term.startsWith("VI.")
            val paint = if (isHeading) termBold else termPaint
            wrapText(term, 98).forEach { line ->
                if (y2 < PAGE_H - 48f) {
                    c2.drawText(line, MARGIN, y2, paint)
                    y2 += 10f
                }
            }
            y2 += if (isHeading) 4f else 3f
        }
        drawFooter(c2, 2, 2)
        pdf.finishPage(page2)

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
            WildlifeWhispererBrand.COMPANY + " · " +
                WildlifeWhispererBrand.ADDRESS + " · " +
                WildlifeWhispererBrand.PHONE,
            PAGE_W / 2f,
            PAGE_H - 22f,
            footer
        )
        canvas.drawText("Page $page of $totalPages", PAGE_W / 2f, PAGE_H - 12f, footer)
    }

    private fun mapFeeRows(lineItems: List<InvoiceLineItem>): List<Pair<String, Double?>> {
        fun find(vararg keys: String): Double? {
            val match = lineItems.firstOrNull { item ->
                val d = item.description.lowercase(Locale.US)
                keys.any { k -> d.contains(k) }
            }
            return match?.calculateTotal()
        }
        return listOf(
            "Trap Service Fee" to find("trap service", "trap fee", "trap setup"),
            "Per Animal Captured Fee" to find("per animal", "animal captured", "capture fee"),
            "Non-Target Animal Captured Fee" to find("non-target", "nontarget", "non target"),
            "Inspection Fee" to find("inspection"),
            "Other / Exclusion & Repairs" to find("exclusion", "repair", "sealing", "entry point", "materials", "labor")
        )
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        val out = mutableListOf<String>()
        text.replace("\r", "").split('\n').forEach { paragraph ->
            var remaining = paragraph.trim()
            if (remaining.isEmpty()) {
                out.add("")
                return@forEach
            }
            while (remaining.length > maxChars) {
                var breakAt = remaining.lastIndexOf(' ', maxChars)
                if (breakAt <= 0) breakAt = maxChars
                out.add(remaining.substring(0, breakAt).trimEnd())
                remaining = remaining.substring(breakAt).trimStart()
            }
            if (remaining.isNotEmpty()) out.add(remaining)
        }
        return out
    }
}
