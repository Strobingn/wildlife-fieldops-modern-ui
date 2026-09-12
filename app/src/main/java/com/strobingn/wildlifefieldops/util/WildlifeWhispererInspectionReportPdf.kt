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
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Branded Wildlife Whisperer LLC inspection report PDF.
 */
object WildlifeWhispererInspectionReportPdf {
    private const val PAGE_W = 612
    private const val PAGE_H = 792
    private const val MARGIN = 44f
    private const val CONTENT_RIGHT = PAGE_W - MARGIN

    private val INK = Color.rgb(28, 28, 30)
    private val MUTED = Color.rgb(90, 90, 95)
    private val RULE = Color.rgb(200, 200, 205)
    private val LIGHT_FILL = Color.rgb(247, 247, 248)
    private val BADGE_BG = Color.rgb(36, 36, 40)

    data class ReportFields(
        val customerName: String = "",
        val inspectorName: String = "",
        val inspectionType: String = "",
        val inspectionDate: Long = System.currentTimeMillis(),
        val jobTitle: String = "",
        val jobAddress: String = "",
        val species: String = "",
        val findings: String = "",
        val entryPoints: String = "",
        val damage: String = "",
        val recommendations: String = "",
        val severity: String = "",
        val notes: String = "",
        val weather: String = "",
        val followUpRequired: Boolean = false
    )

    fun generate(context: Context, fields: ReportFields): String {
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
        val badgeText = Paint(anti).apply {
            textSize = 10f
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val headerPaint = Paint(anti).apply {
            textSize = 10.5f
            color = INK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sectionLabel = Paint(anti).apply {
            textSize = 8.5f
            color = MUTED
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(anti).apply {
            textSize = 9.5f
            color = INK
        }
        val smallPaint = Paint(anti).apply {
            textSize = 8f
            color = MUTED
        }
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

        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val c = page.canvas
        c.drawColor(Color.WHITE)
        var y = 36f

        val logoSize = 100
        WildlifeWhispererBrand.drawLogo(c, context, MARGIN, y, logoSize)

        var rightY = y + 16f
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

        val badgeW = 130f
        c.drawRoundRect(RectF(MARGIN, y - 12f, MARGIN + badgeW, y + 8f), 3f, 3f, badgePaint)
        c.drawText("INSPECTION REPORT", MARGIN + badgeW / 2f, y + 2f, badgeText)

        val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        val dateStr = dateFormat.format(Date(fields.inspectionDate))
        bodyPaint.textAlign = Paint.Align.LEFT
        c.drawText("Date: $dateStr", MARGIN + badgeW + 16f, y, bodyPaint)
        if (fields.inspectionType.isNotBlank()) {
            c.drawText("Type: ${fields.inspectionType}", 360f, y, bodyPaint)
        }
        y += 18f
        c.drawLine(MARGIN, y, CONTENT_RIGHT, y, hairline)
        y += 14f

        // Customer / job box
        c.drawText("CUSTOMER & JOB", MARGIN, y, sectionLabel)
        y += 6f
        val boxTop = y
        val boxPad = 10f
        val blank = "____________________"
        val metaLines = listOf(
            "Customer: ${fields.customerName.ifBlank { blank }}",
            "Address: ${fields.jobAddress.ifBlank { blank }}",
            "Job: ${fields.jobTitle.ifBlank { blank }}",
            "Inspector: ${fields.inspectorName.ifBlank { blank }}"
        )
        val boxH = boxPad * 2 + metaLines.size * 13f
        c.drawRoundRect(RectF(MARGIN, boxTop, CONTENT_RIGHT, boxTop + boxH), 4f, 4f, fillPaint)
        c.drawRoundRect(RectF(MARGIN, boxTop, CONTENT_RIGHT, boxTop + boxH), 4f, 4f, hairline)
        var by = boxTop + boxPad + 10f
        metaLines.forEach { line ->
            c.drawText(line, MARGIN + boxPad, by, bodyPaint)
            by += 13f
        }
        y = boxTop + boxH + 16f

        fun drawSection(title: String, content: String, maxLines: Int = 6) {
            if (y > PAGE_H - 80f) return
            c.drawText(title.uppercase(Locale.US), MARGIN, y, sectionLabel)
            y += 12f
            val text = content.ifBlank { "—" }
            wrapText(text, 95).take(maxLines).forEach { line ->
                if (y < PAGE_H - 48f) {
                    c.drawText(line, MARGIN, y, bodyPaint)
                    y += 11f
                }
            }
            y += 8f
            c.drawLine(MARGIN, y, CONTENT_RIGHT, y, hairline)
            y += 12f
        }

        if (fields.severity.isNotBlank()) {
            c.drawText("SEVERITY", MARGIN, y, sectionLabel)
            y += 12f
            c.drawText(fields.severity, MARGIN, y, headerPaint)
            y += 10f
            c.drawLine(MARGIN, y, CONTENT_RIGHT, y, hairline)
            y += 12f
        }

        drawSection("Species Identified", fields.species, 3)
        drawSection("Findings", fields.findings, 8)
        drawSection("Entry Points", fields.entryPoints, 4)
        drawSection("Damage Assessment", fields.damage, 5)
        drawSection("Recommendations", fields.recommendations, 6)

        if (fields.weather.isNotBlank() || fields.followUpRequired || fields.notes.isNotBlank()) {
            val extras = buildString {
                if (fields.weather.isNotBlank()) appendLine("Weather: ${fields.weather}")
                if (fields.followUpRequired) appendLine("Follow-up required: Yes")
                if (fields.notes.isNotBlank()) append(fields.notes.trim())
            }
            drawSection("Notes", extras.trim(), 5)
        }

        // Signature line
        if (y < PAGE_H - 70f) {
            y = maxOf(y + 8f, PAGE_H - 90f)
            c.drawLine(MARGIN, y, MARGIN + 200f, y, hairline)
            c.drawText("Inspector Signature", MARGIN, y + 12f, smallPaint)
            c.drawText("Date: " + "____________", MARGIN, y + 24f, smallPaint)
        }

        drawFooter(c)
        pdf.finishPage(page)

        val safeName = fields.customerName.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "inspection" }
        val fileName = "inspection_${safeName}_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file.absolutePath
    }

    fun share(context: Context, path: String, chooserTitle: String = "Share Inspection Report") {
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

    private fun drawFooter(canvas: Canvas) {
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
