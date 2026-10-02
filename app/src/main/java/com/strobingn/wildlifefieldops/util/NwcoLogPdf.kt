package com.strobingn.wildlifefieldops.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.strobingn.wildlifefieldops.ai.fieldops.NwcoLogRecord
import com.strobingn.wildlifefieldops.ai.fieldops.NwcoOperatorProfile
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Letter landscape PDF matching the official NYS DEC Nuisance Wildlife Control Log
 * column order (2024-08 form). We generate our own layout rather than embedding
 * the DEC PDF.
 */
object NwcoLogPdf {
    private const val PAGE_W = 792
    private const val PAGE_H = 612
    private const val MARGIN = 28f
    private val INK = Color.rgb(20, 20, 22)
    private val MUTED = Color.rgb(90, 90, 94)
    private val RULE = Color.rgb(160, 160, 164)

    fun generate(
        context: Context,
        operator: NwcoOperatorProfile,
        rows: List<NwcoLogRecord>,
        licenseStartYear: Int
    ): File {
        val pdf = PdfDocument()
        val visible = rows.filterNot { it.deleted }
        val perPage = 8
        val pages = (visible.size + perPage - 1).coerceAtLeast(1) / perPage
        val pageCount = pages.coerceAtLeast(1)
        repeat(pageCount) { index ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, index + 1).create())
            drawPage(
                canvas = page.canvas,
                operator = operator,
                rows = visible.drop(index * perPage).take(perPage),
                pageNum = index + 1,
                pageCount = pageCount,
                licenseStartYear = licenseStartYear
            )
            pdf.finishPage(page)
        }
        val file = File(context.cacheDir, "nys-dec-nwco-log.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    private fun drawPage(
        canvas: android.graphics.Canvas,
        operator: NwcoOperatorProfile,
        rows: List<NwcoLogRecord>,
        pageNum: Int,
        pageCount: Int,
        licenseStartYear: Int
    ) {
        val anti = Paint().apply { isAntiAlias = true }
        val title = Paint(anti).apply {
            textSize = 13f
            color = INK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val small = Paint(anti).apply { textSize = 8f; color = MUTED }
        val label = Paint(anti).apply { textSize = 8.5f; color = INK; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val body = Paint(anti).apply { textSize = 8f; color = INK }
        val rule = Paint().apply { color = RULE; strokeWidth = 0.8f }

        var y = MARGIN
        canvas.drawText("NYS DEC  ·  Nuisance Wildlife Control Log", MARGIN, y, title)
        y += 14f
        canvas.drawText(
            "License duration October 1 $licenseStartYear  to  September 30 ${licenseStartYear + 1}     Page $pageNum of $pageCount",
            MARGIN,
            y,
            small
        )
        y += 12f
        canvas.drawText("Keep weekly. Submit with NWCO renewal. Codes: complaint A–D · method A–H · area A–C · disposition E / R / T.", MARGIN, y, small)
        y += 16f
        canvas.drawText("1–4 Applicant", MARGIN, y, label)
        y += 12f
        canvas.drawText("1 Name (Last, First, MI): ${operator.displayName()}", MARGIN, y, body)
        canvas.drawText("2 NWCO license #: ${operator.licenseNumber}", 420f, y, body)
        y += 12f
        canvas.drawText("3 DEC region: ${operator.decRegion}", MARGIN, y, body)
        canvas.drawText("4 County of residence: ${operator.countyOfResidence}", 220f, y, body)
        canvas.drawText(operator.phone.takeIf { it.isNotBlank() }?.let { "Phone: $it" }.orEmpty(), 480f, y, body)
        y += 12f
        if (operator.address.isNotBlank()) {
            canvas.drawText(operator.address, MARGIN, y, small)
            y += 12f
        }
        y += 4f
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
        y += 14f

        val cols = listOf(
            5 to "5 Complainant",
            6 to "6 Date(s)",
            7 to "7 Species",
            8 to "8 Type",
            9 to "9 Method",
            10 to "10 Area",
            11 to "11 Traps",
            12 to "12 Taken",
            13 to "13 Disposition"
        )
        val widths = floatArrayOf(130f, 70f, 80f, 70f, 80f, 60f, 46f, 80f, 120f)
        var x = MARGIN
        cols.forEachIndexed { i, pair ->
            canvas.drawText(pair.second, x, y, label)
            x += widths[i]
        }
        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
        y += 12f
        val rowH = 42f
        rows.forEach { row ->
            val values = listOf(
                row.complainant,
                row.datesPerformed.ifBlank { SimpleDateFormat("MM/dd/yyyy", Locale.US).format(Date(row.workDate)) },
                row.species,
                row.complaintType,
                row.abatementMethod,
                row.areaOfComplaint,
                row.trapsSet,
                row.speciesAndNumberTaken,
                row.disposition
            )
            var cx = MARGIN
            values.forEachIndexed { i, value ->
                drawWrapped(canvas, value, cx, y, widths[i] - 4f, body)
                cx += widths[i]
            }
            y += rowH
            canvas.drawLine(MARGIN, y - 8f, PAGE_W - MARGIN, y - 8f, rule)
        }
        canvas.drawText(
            "False statements are punishable as a Class A misdemeanor (Penal Law 210.45).  Applicant signature ______________  Date ________",
            MARGIN,
            PAGE_H - 18f,
            small
        )
    }

    private fun drawWrapped(canvas: android.graphics.Canvas, text: String, x: Float, y: Float, width: Float, paint: Paint) {
        val words = text.replace('\n', ' ').split(" ")
        var line = ""
        var cy = y
        var lines = 0
        words.forEach { word ->
            val next = if (line.isBlank()) word else "$line $word"
            if (paint.measureText(next) > width && line.isNotBlank()) {
                canvas.drawText(line, x, cy, paint)
                cy += 10f
                line = word
                lines++
                if (lines >= 3) return
            } else {
                line = next
            }
        }
        if (line.isNotBlank() && lines < 3) canvas.drawText(line, x, cy, paint)
    }

    fun licenseStartYear(now: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val year = cal.get(Calendar.YEAR)
        return if (cal.get(Calendar.MONTH) >= Calendar.OCTOBER) year else year - 1
    }
}
