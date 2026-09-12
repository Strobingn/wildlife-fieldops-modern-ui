package com.strobingn.wildlifefieldops.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
 * Wildlife Whisperer LLC inspection report PDF.
 * Same classic letterhead family as the service-contract template (no dark badge chrome).
 */
object WildlifeWhispererInspectionReportPdf {
    private const val PAGE_W = 612
    private const val PAGE_H = 792
    private const val MARGIN = 48f
    private const val CONTENT_RIGHT = PAGE_W - MARGIN
    private const val CONTENT_WIDTH = CONTENT_RIGHT - MARGIN

    private val INK = Color.rgb(20, 20, 22)
    private val MUTED = Color.rgb(95, 95, 100)
    private val RULE = Color.rgb(175, 175, 180)
    private val LIGHT_RULE = Color.rgb(210, 210, 214)

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
            strokeWidth = 0.8f
            style = Paint.Style.STROKE
        }
        val lightHairline = Paint(anti).apply {
            color = LIGHT_RULE
            strokeWidth = 0.65f
            style = Paint.Style.STROKE
        }

        val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val c = page.canvas
        c.drawColor(Color.WHITE)
        var y = 32f

        // Same letterhead as contract (logo left + company right)
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

        c.drawText("INSPECTION REPORT", PAGE_W / 2f, y, titlePaint)
        y += 14f

        val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        val dateStr = dateFormat.format(Date(fields.inspectionDate))
        c.drawText("Date: $dateStr", MARGIN, y, labelPaint)
        if (fields.inspectionType.isNotBlank()) {
            c.drawText("Type: ${fields.inspectionType}", MARGIN + 200f, y, labelPaint)
        }
        if (fields.inspectorName.isNotBlank()) {
            c.drawText("Tech: ${fields.inspectorName}", MARGIN + 380f, y, labelPaint)
        }
        y += 10f
        c.drawLine(MARGIN, y, CONTENT_RIGHT, y, lightHairline)
        y += 16f

        fun drawLabeledLine(label: String, value: String) {
            c.drawText(label, MARGIN, y, labelPaint)
            val labelW = labelPaint.measureText(label) + 6f
            c.drawLine(MARGIN + labelW, y + 1f, CONTENT_RIGHT, y + 1f, lightHairline)
            if (value.isNotBlank()) {
                c.drawText(value.take(70), MARGIN + labelW + 4f, y, bodyPaint)
            }
            y += 16f
        }

        c.drawText("CUSTOMER & SITE", MARGIN, y, sectionPaint)
        y += 14f
        drawLabeledLine("Customer:", fields.customerName)
        drawLabeledLine("Address:", fields.jobAddress)
        drawLabeledLine("Job:", fields.jobTitle)
        drawLabeledLine("Inspector:", fields.inspectorName)
        y += 4f

        fun drawSection(title: String, content: String, minLines: Int = 2, maxLines: Int = 6) {
            if (y > PAGE_H - 70f) return
            c.drawText(title.uppercase(Locale.US), MARGIN, y, sectionPaint)
            y += 13f
            val lines = if (content.isBlank()) {
                List(minLines) { "" }
            } else {
                wrapTextToWidth(content, bodyPaint, CONTENT_WIDTH).take(maxLines)
            }
            val slots = maxOf(lines.size, minLines)
            repeat(slots) { i ->
                val line = lines.getOrNull(i).orEmpty()
                if (line.isNotBlank()) {
                    c.drawText(line, MARGIN, y, bodyPaint)
                }
                c.drawLine(MARGIN, y + 2f, CONTENT_RIGHT, y + 2f, lightHairline)
                y += 13f
                if (y > PAGE_H - 55f) return
            }
            y += 6f
        }

        if (fields.severity.isNotBlank()) {
            c.drawText("SEVERITY", MARGIN, y, sectionPaint)
            y += 13f
            c.drawText(fields.severity, MARGIN, y, labelPaint)
            y += 5f
            c.drawLine(MARGIN, y, CONTENT_RIGHT, y, lightHairline)
            y += 14f
        }

        drawSection("Species Identified", fields.species, 2, 3)
        drawSection("Findings", fields.findings, 3, 7)
        drawSection("Entry Points", fields.entryPoints, 2, 4)
        drawSection("Damage Assessment", fields.damage, 2, 5)
        drawSection("Recommendations", fields.recommendations, 3, 6)

        val extras = buildString {
            if (fields.weather.isNotBlank()) appendLine("Weather: ${fields.weather}")
            if (fields.followUpRequired) appendLine("Follow-up required: Yes")
            if (fields.notes.isNotBlank()) append(fields.notes.trim())
        }.trim()
        if (extras.isNotBlank()) {
            drawSection("Notes", extras, 2, 5)
        }

        if (y < PAGE_H - 70f) {
            y = maxOf(y + 6f, PAGE_H - 88f)
            c.drawText("Inspector Signature:", MARGIN, y, labelPaint)
            val sigStart = MARGIN + labelPaint.measureText("Inspector Signature:") + 6f
            c.drawLine(sigStart, y + 1f, sigStart + 200f, y + 1f, lightHairline)
            c.drawText("Date:", sigStart + 214f, y, labelPaint)
            val dateStart = sigStart + 214f + labelPaint.measureText("Date:") + 4f
            c.drawLine(dateStart, y + 1f, dateStart + 90f, y + 1f, lightHairline)
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
            "${WildlifeWhispererBrand.COMPANY_UPPER} · 210 Willow Avenue, Cornwall, NY 12518 · ${WildlifeWhispererBrand.PHONE}",
            PAGE_W / 2f,
            PAGE_H - 22f,
            footer
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
