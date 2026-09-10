package com.strobingn.wildlifefieldops.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

object WildlifeWhispererContractPdf {
    private const val COMPANY = "WILDLIFE WHISPERER LLC"
    private const val ADDRESS = "210 Willow Avenue, Cornwall, New York 12518"
    private const val PHONE = "(845) 751-8448"
    private const val EMAIL = "austin@wildlifewhispererllc.com"
    private const val PAGE_W = 612
    private const val PAGE_H = 792

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
        val paint = Paint().apply { isAntiAlias = true }
        val titlePaint = Paint(paint).apply {
            textSize = 13f
            color = Color.BLACK
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val companyPaint = Paint(paint).apply {
            textSize = 14f
            color = Color.BLACK
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val headerPaint = Paint(paint).apply {
            textSize = 11f
            color = Color.BLACK
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val normalPaint = Paint(paint).apply {
            textSize = 10f
            color = Color.DKGRAY
        }
        val smallPaint = Paint(paint).apply {
            textSize = 8f
            color = Color.GRAY
        }
        val linePaint = Paint(paint).apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        val moneyPaint = Paint(normalPaint).apply { textAlign = Paint.Align.RIGHT }

        val page1 = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val c = page1.canvas
        var y = 28f

        // Logo
        val logo = loadLogo(context)
        if (logo != null) {
            val logoSize = 88
            val left = (PAGE_W - logoSize) / 2f
            val scaled = Bitmap.createScaledBitmap(logo, logoSize, logoSize, true)
            c.drawBitmap(scaled, left, y, null)
            if (scaled !== logo) scaled.recycle()
            y += logoSize + 8f
        }

        val typeLabel = when (documentType) {
            ContractDocumentType.ESTIMATE -> "ESTIMATE"
            ContractDocumentType.INVOICE -> "INVOICE"
        }
        c.drawText("SERVICE CONTRACT / $typeLabel", PAGE_W / 2f, y, titlePaint)
        y += 16f
        c.drawText(COMPANY, PAGE_W / 2f, y, companyPaint)
        y += 14f
        c.drawText(ADDRESS, PAGE_W / 2f, y, smallPaint.apply { textAlign = Paint.Align.CENTER })
        y += 11f
        c.drawText("$PHONE  ·  $EMAIL", PAGE_W / 2f, y, smallPaint)
        smallPaint.textAlign = Paint.Align.LEFT
        y += 14f
        c.drawLine(40f, y, PAGE_W - 40f, y, linePaint)
        y += 16f

        val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        val docNo = documentNumber.ifBlank {
            val prefix = if (documentType == ContractDocumentType.ESTIMATE) "EST" else "INV"
            "$prefix-${System.currentTimeMillis() % 100000}"
        }
        val tech = technicianName.ifBlank { job.assignedTo }.ifBlank { "____________________" }

        c.drawText("Document type: $typeLabel", 40f, y, normalPaint)
        c.drawText("# $docNo", 280f, y, normalPaint)
        c.drawText("Date: ${dateFormat.format(Date())}", 430f, y, normalPaint)
        y += 14f
        c.drawText("Technician: $tech", 40f, y, normalPaint)
        y += 16f
        c.drawLine(40f, y, PAGE_W - 40f, y, linePaint)
        y += 16f

        c.drawText("Billing Information", 40f, y, headerPaint)
        y += 14f
        c.drawText("Owner / Manager: ${job.customerName.ifBlank { "____________________" }}", 40f, y, normalPaint)
        y += 13f
        c.drawText("Address: ${job.address.ifBlank { "____________________" }}", 40f, y, normalPaint)
        y += 13f
        c.drawText("Job: ${job.title.ifBlank { "____________________" }}", 40f, y, normalPaint)
        y += 16f
        c.drawLine(40f, y, PAGE_W - 40f, y, linePaint)
        y += 16f

        c.drawText("Service and Animal Fees", 40f, y, headerPaint)
        y += 14f

        val feeRows = mapFeeRows(lineItems)
        feeRows.forEach { (label, amount) ->
            c.drawText(label, 40f, y, normalPaint)
            val amountText = if (amount != null) "$${String.format(Locale.US, "%.2f", amount)}" else ("$" + "______________")
            c.drawText(amountText, PAGE_W - 40f, y, moneyPaint)
            y += 13f
        }

        // Remaining line items not mapped into named fee rows
        val mapped = feeRows.mapNotNull { it.second }.size
        if (lineItems.isNotEmpty()) {
            y += 4f
            c.drawText("Line items / work detail", 40f, y, headerPaint)
            y += 13f
            lineItems.take(8).forEach { item ->
                val desc = item.description.ifBlank { "(item)" }.take(48)
                val line = String.format(
                    Locale.US,
                    "%s  qty %.1f @ $%.2f = $%.2f",
                    desc,
                    item.quantity,
                    item.unitPrice,
                    item.calculateTotal()
                )
                c.drawText(line.take(90), 40f, y, smallPaint)
                y += 11f
            }
            if (lineItems.size > 8) {
                c.drawText("… +${lineItems.size - 8} more", 40f, y, smallPaint)
                y += 11f
            }
        }

        y += 6f
        c.drawText("Exclusion, Trapping and/or Repairs performed or proposed:", 40f, y, headerPaint)
        y += 13f
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
        }.ifBlank { "________________________________________________________________" }

        wrapText(workText, 95).take(5).forEach { line ->
            c.drawText(line, 40f, y, smallPaint)
            y += 11f
        }
        y += 8f
        c.drawLine(40f, y, PAGE_W - 40f, y, linePaint)
        y += 16f

        fun drawMoneyRow(label: String, value: Double, bold: Boolean = false) {
            val lp = if (bold) headerPaint else normalPaint
            val rp = Paint(if (bold) headerPaint else normalPaint).apply { textAlign = Paint.Align.RIGHT }
            c.drawText(label, 360f, y, lp)
            c.drawText("$${String.format(Locale.US, "%.2f", value)}", PAGE_W - 40f, y, rp)
            y += 14f
        }

        drawMoneyRow("Sub-Total:", subtotal)
        if (discountAmount > 0.0) drawMoneyRow("Discount:", -discountAmount)
        drawMoneyRow("Tax (${String.format(Locale.US, "%.3f", taxRate).trimEnd('0').trimEnd('.')}%):", taxAmount)
        drawMoneyRow("Grand-Total:", total, bold = true)
        // silence unused mapped warning pattern
        @Suppress("UNUSED_EXPRESSION")
        mapped

        y += 10f
        c.drawLine(40f, y, PAGE_W - 40f, y, linePaint)
        y += 28f

        // Signatures
        val sigBase = y.coerceAtMost(PAGE_H - 140f)
        if (customerSignature != null) {
            val scaled = Bitmap.createScaledBitmap(customerSignature, 150, 48, true)
            c.drawBitmap(scaled, 40f, sigBase - 48f, null)
        }
        c.drawLine(40f, sigBase, 220f, sigBase, linePaint)
        c.drawText("Owner Signature", 40f, sigBase + 12f, smallPaint)
        c.drawText("Date: ____________", 40f, sigBase + 24f, smallPaint)

        if (technicianSignature != null) {
            val scaled = Bitmap.createScaledBitmap(technicianSignature, 150, 48, true)
            c.drawBitmap(scaled, 340f, sigBase - 48f, null)
        }
        c.drawLine(340f, sigBase, 520f, sigBase, linePaint)
        c.drawText("Company Signature", 340f, sigBase + 12f, smallPaint)
        c.drawText("Date: ____________", 340f, sigBase + 24f, smallPaint)

        var blurbY = sigBase + 42f
        wrapText(acceptanceBlurb, 100).forEach { line ->
            if (blurbY < PAGE_H - 36f) {
                c.drawText(line, 40f, blurbY, smallPaint)
                blurbY += 10f
            }
        }
        drawFooter(c, smallPaint)
        pdf.finishPage(page1)

        // Page 2 — mutual obligations (condensed)
        val page2 = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 2).create())
        val c2 = page2.canvas
        var y2 = 48f
        c2.drawText("MUTUAL OBLIGATIONS AND DISCLAIMERS", PAGE_W / 2f, y2, titlePaint)
        y2 += 20f
        legalTerms.forEach { term ->
            wrapText(term, 98).forEach { line ->
                if (y2 < PAGE_H - 50f) {
                    c2.drawText(line, 40f, y2, smallPaint)
                    y2 += 10f
                }
            }
            y2 += 4f
        }
        drawFooter(c2, smallPaint)
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

    private fun drawFooter(canvas: Canvas, paint: Paint) {
        val footer = Paint(paint).apply {
            textAlign = Paint.Align.CENTER
            textSize = 7f
        }
        canvas.drawText(
            "$COMPANY · $ADDRESS · $PHONE",
            PAGE_W / 2f,
            PAGE_H - 24f,
            footer
        )
    }

    private fun loadLogo(context: Context): Bitmap? {
        // Prefer bundled base64 asset (reliable across builds); fall back to drawable.
        try {
            context.assets.open("wildlife_whisperer_logo.webp.b64").bufferedReader().use { reader ->
                val bytes = android.util.Base64.decode(reader.readText().trim(), android.util.Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) return bmp
            }
        } catch (_: Exception) {
            // fall through
        }
        return try {
            val id = context.resources.getIdentifier(
                "wildlife_whisperer_logo",
                "drawable",
                context.packageName
            )
            if (id != 0) BitmapFactory.decodeResource(context.resources, id) else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Map line items into the paper contract's named fee rows when descriptions match;
     * otherwise leave amount blank for that row (still show the label).
     */
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
