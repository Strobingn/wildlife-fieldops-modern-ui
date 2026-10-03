package com.strobingn.wildlifefieldops.util

/**
 * One letterhead, one section order, one table/totals/notes/signature/footer
 * treatment. Document kinds differ by [StandardDocument.title] and [StandardDocument.blocks].
 */
enum class DocumentKind(val title: String, val filePrefix: String) {
    ESTIMATE("ESTIMATE", "estimate"),
    INVOICE("INVOICE", "invoice"),
    RECEIPT("RECEIPT", "receipt"),
    INSPECTION("INSPECTION REPORT", "inspection"),
    CONTRACT("SERVICE CONTRACT", "contract"),
    EXCLUSION("EXCLUSION / REPAIR", "exclusion"),
    WARRANTY("WARRANTY", "warranty"),
    NWCO_LOG("NYS DEC NWCO LOG", "nwco-log"),
    EARNINGS_TAX("EARNINGS & NY SALES TAX", "earnings-tax")
}

object StandardDocumentTemplate {
    const val ID = "wildlife-whisperer-standard-v1"
    val sectionOrder: List<String> = listOf(
        "logo",
        "header",
        "title",
        "meta",
        "customer",
        "body",
        "totals",
        "notes",
        "terms",
        "signature",
        "footer"
    )
    const val PAGE_W = 612
    const val PAGE_H = 792
    const val LANDSCAPE_W = 792
    const val LANDSCAPE_H = 612
    const val MARGIN = 48f
    const val LOGO = 88f
}

data class DocTable(
    val columns: List<String>,
    val rows: List<List<String>>,
    val weights: List<Float> = emptyList(),
    val aligns: List<String> = emptyList()
)

data class BodyBlock(
    val title: String,
    val lines: List<String> = emptyList(),
    val table: DocTable? = null,
    val minRuledLines: Int = 0
)

data class TotalLine(
    val label: String,
    val amount: String,
    val bold: Boolean = false
)

data class SignatureSlot(
    val label: String,
    val name: String = "",
    val dateText: String = "",
    /** "customer", "company", or empty when the line is blank. */
    val imageTag: String = ""
)

data class DrawOp(
    val kind: String,
    val text: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val x2: Float = 0f,
    val y2: Float = 0f,
    val size: Float = 9.5f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val align: String = "left",
    val color: String = "ink",
    val tag: String = ""
)

data class PageLayout(
    val width: Int,
    val height: Int,
    val ops: List<DrawOp>
)

data class LayoutResult(
    val pages: List<PageLayout>,
    val sections: List<String>
)

data class StandardDocument(
    val kind: DocumentKind,
    val profile: BusinessProfile,
    val title: String = kind.title,
    val meta: List<String> = emptyList(),
    val showChoiceBoxes: Boolean = false,
    val estimateChecked: Boolean = false,
    val invoiceChecked: Boolean = false,
    val customerRows: List<Pair<String, String>> = emptyList(),
    val blocks: List<BodyBlock> = emptyList(),
    val totals: List<TotalLine> = emptyList(),
    val notes: String = "",
    val terms: String = "",
    val signatures: List<SignatureSlot> = emptyList(),
    val landscape: Boolean = false,
    val customerSignatureBase64: String = "",
    val companySignatureBase64: String = "",
    val includeQr: Boolean = false
) {
    val templateId: String = StandardDocumentTemplate.ID
    val headerLines: List<String> = profile.headerLines()
    val sectionOrder: List<String> get() = StandardDocumentTemplate.sectionOrder
    val drawsLogo: Boolean = true

    fun layout(): LayoutResult = StandardDocumentLayout.layout(this)
}

object StandardDocumentLayout {
    fun layout(doc: StandardDocument): LayoutResult = Engine(doc).run()

    private class Engine(private val doc: StandardDocument) {
        private val pageW = if (doc.landscape) StandardDocumentTemplate.LANDSCAPE_W else StandardDocumentTemplate.PAGE_W
        private val pageH = if (doc.landscape) StandardDocumentTemplate.LANDSCAPE_H else StandardDocumentTemplate.PAGE_H
        private val margin = StandardDocumentTemplate.MARGIN
        private val contentRight = pageW - margin
        private val contentWidth = contentRight - margin
        private val pages = mutableListOf<PageLayout>()
        private val ops = mutableListOf<DrawOp>()
        private val sections = mutableListOf<String>()
        private var y = margin
        private var pageIndex = 0
        private var continued = false

        fun run(): LayoutResult {
            startPage(false)
            mark("body")
            doc.blocks.forEach { block ->
                if (block.title.isNotBlank()) sectionTitle(block.title)
                if (block.lines.isNotEmpty() || block.minRuledLines > 0) {
                    ruled(block.lines, block.minRuledLines)
                }
                block.table?.let { table(it) }
            }
            mark("totals")
            totals(doc.totals)
            mark("notes")
            prose("NOTES", doc.notes, 9.5f, "ink")
            mark("terms")
            prose("TERMS", doc.terms, 7.5f, "muted")
            mark("signature")
            signatures(doc.signatures)
            if (doc.includeQr) qr()
            mark("footer")
            closePage()
            stampPageNumbers()
            return LayoutResult(pages.toList(), sections.toList())
        }

        private fun mark(name: String) {
            if (name !in sections) sections += name
        }

        private fun startPage(isContinued: Boolean) {
            continued = isContinued
            y = 32f
            if (!isContinued) {
                mark("logo")
                mark("header")
                mark("title")
            }
            ops += DrawOp("logo", x = margin, y = y, x2 = StandardDocumentTemplate.LOGO)
            var rightY = y + 18f
            val textLeft = margin + StandardDocumentTemplate.LOGO + 14f
            val lines = doc.profile.headerLines()
            lines.forEachIndexed { index, line ->
                val nameLine = index == 0
                ops += DrawOp(
                    kind = "text",
                    text = line,
                    x = textLeft,
                    y = rightY,
                    size = if (nameLine) 13f else 9f,
                    bold = nameLine,
                    color = if (nameLine) "ink" else "muted"
                )
                rightY += if (nameLine) 15f else 12f
            }
            y = maxOf(y + StandardDocumentTemplate.LOGO + 10f, rightY + 8f)
            rule(margin, y, contentRight, y, "rule", 0.8f)
            y += 16f
            val title = if (isContinued) doc.title + " (continued)" else doc.title
            ops += DrawOp("text", title, pageW / 2f, y, size = 12f, bold = true, align = "center")
            y += 18f
            if (!isContinued) {
                mark("meta")
                if (doc.showChoiceBoxes) choiceBoxes()
                doc.meta.forEach { line ->
                    ensure(14f)
                    ops += DrawOp("text", line, margin, y, size = 9.5f)
                    y += 13f
                }
                rule(margin, y, contentRight, y, "light", 0.65f)
                y += 16f
                mark("customer")
                sectionTitle("BILL TO / OWNER")
                doc.customerRows.forEach { (label, value) -> labeled(label, value) }
                y += 4f
            }
        }

        private fun choiceBoxes() {
            ensure(16f)
            box(margin, doc.estimateChecked)
            ops += DrawOp("text", "Estimate", margin + 14f, y, size = 9.5f)
            box(margin + 90f, doc.invoiceChecked)
            ops += DrawOp("text", "Invoice", margin + 104f, y, size = 9.5f)
            y += 16f
        }

        private fun box(x: Float, checked: Boolean) {
            ops += DrawOp("rect", x = x, y = y - 8f, x2 = x + 9f, y2 = y + 1f, color = "ink")
            if (checked) {
                ops += DrawOp("fillrect", x = x + 2f, y = y - 6f, x2 = x + 7f, y2 = y - 1f, color = "ink")
            }
        }

        private fun sectionTitle(title: String) {
            ensure(18f)
            ops += DrawOp("text", title.uppercase(), margin, y, size = 10f, bold = true)
            y += 14f
        }

        private fun labeled(label: String, value: String) {
            ensure(16f)
            ops += DrawOp("text", label, margin, y, size = 9.5f)
            val labelW = measure(label, 9.5f, false) + 6f
            val valueLeft = margin + labelW + 4f
            val lines = if (value.isBlank()) listOf("") else wrap(value, 9.5f, false, contentRight - valueLeft)
            lines.forEachIndexed { index, line ->
                if (index > 0) {
                    y += 13f
                    ensure(14f)
                }
                rule(margin + labelW, y + 1f, contentRight, y + 1f, "light", 0.65f)
                if (line.isNotBlank()) {
                    ops += DrawOp("text", line, valueLeft, y, size = 9.5f)
                }
            }
            y += 16f
        }

        private fun ruled(lines: List<String>, minLines: Int) {
            val wrapped = lines.flatMap { line ->
                if (line.isBlank()) listOf("") else wrap(line, 9.5f, false, contentWidth)
            }
            val count = maxOf(wrapped.size, minLines)
            repeat(count) { index ->
                ensure(14f)
                val line = wrapped.getOrNull(index).orEmpty()
                if (line.isNotBlank()) ops += DrawOp("text", line, margin, y, size = 9.5f)
                rule(margin, y + 2f, contentRight, y + 2f, "light", 0.65f)
                y += 14f
            }
            y += 4f
        }

        private fun table(table: DocTable) {
            val count = table.columns.size
            if (count == 0) return
            val weights = if (table.weights.size == count) table.weights else List(count) { 1f }
            val weightSum = weights.sum().takeIf { it > 0f } ?: count.toFloat()
            val widths = weights.map { contentWidth * (it / weightSum) }
            val aligns = table.aligns
            ensure(18f)
            var x = margin
            table.columns.forEachIndexed { index, column ->
                val align = aligns.getOrElse(index) { "left" }
                val anchor = if (align == "right") x + widths[index] - 2f else x
                ops += DrawOp("text", column, anchor, y, size = 8.5f, bold = true, align = align)
                x += widths[index]
            }
            y += 6f
            rule(margin, y, contentRight, y, "rule", 0.8f)
            y += 12f
            table.rows.forEach { row ->
                val cellLines = (0 until count).map { index ->
                    val cell = row.getOrElse(index) { "" }
                    val width = (widths[index] - 6f).coerceAtLeast(12f)
                    if (cell.isBlank()) listOf("") else wrap(cell, 8f, false, width).ifEmpty { listOf("") }
                }
                val rowHeight = cellLines.maxOf { it.size } * 11f + 6f
                ensure(rowHeight)
                var cx = margin
                cellLines.forEachIndexed { index, lines ->
                    val align = aligns.getOrElse(index) { "left" }
                    lines.forEachIndexed { lineIndex, line ->
                        if (line.isNotBlank()) {
                            val anchor = if (align == "right") cx + widths[index] - 2f else cx
                            ops += DrawOp(
                                "text",
                                line,
                                anchor,
                                y + lineIndex * 11f,
                                size = 8f,
                                align = align
                            )
                        }
                    }
                    cx += widths[index]
                }
                y += rowHeight
                rule(margin, y - 4f, contentRight, y - 4f, "light", 0.65f)
            }
            y += 8f
        }

        private fun totals(lines: List<TotalLine>) {
            if (lines.isEmpty()) return
            val labelX = contentRight - 200f
            lines.forEach { line ->
                ensure(16f)
                val size = if (line.bold) 10.5f else 9.5f
                ops += DrawOp("text", line.label, labelX, y, size = size, bold = line.bold)
                ops += DrawOp("text", line.amount, contentRight, y, size = size, bold = line.bold, align = "right")
                y += if (line.bold) 15f else 13f
            }
            y += 6f
        }

        private fun prose(title: String, text: String, size: Float, color: String) {
            if (text.isBlank()) return
            sectionTitle(title)
            wrap(text, size, false, contentWidth).forEach { line ->
                ensure(size + 3f)
                ops += DrawOp("text", line, margin, y, size = size, color = color)
                y += size + 2.5f
            }
            y += 6f
        }

        private fun signatures(slots: List<SignatureSlot>) {
            val drawn = slots.ifEmpty {
                listOf(SignatureSlot("Owner Signature"), SignatureSlot("Company Signature"))
            }
            drawn.forEach { slot ->
                ensure(36f)
                if (slot.imageTag.isNotBlank()) {
                    ops += DrawOp("image", x = margin + 110f, y = y - 36f, x2 = 160f, y2 = 36f, tag = slot.imageTag)
                }
                val label = slot.label + ":"
                ops += DrawOp("text", label, margin, y, size = 9.5f)
                val start = margin + measure(label, 9.5f, false) + 6f
                rule(start, y + 1f, start + 200f, y + 1f, "light", 0.65f)
                if (slot.name.isNotBlank()) {
                    ops += DrawOp("text", slot.name, start + 4f, y - 10f, size = 12f, italic = true)
                }
                val dateLabelX = start + 214f
                ops += DrawOp("text", "Date:", dateLabelX, y, size = 9.5f)
                val dateLine = dateLabelX + measure("Date:", 9.5f, false) + 4f
                rule(dateLine, y + 1f, dateLine + 80f, y + 1f, "light", 0.65f)
                if (slot.dateText.isNotBlank()) {
                    ops += DrawOp("text", slot.dateText, dateLine, y - 8f, size = 8f, color = "muted")
                }
                y += 28f
            }
        }

        private fun qr() {
            ensure(86f)
            ops += DrawOp("text", "Scan in FieldOps", contentRight - 72f, y, size = 8f, color = "muted")
            y += 4f
            ops += DrawOp("image", x = contentRight - 72f, y = y, x2 = 72f, y2 = 72f, tag = "qr")
            y += 76f
        }

        private fun ensure(need: Float) {
            val limit = pageH - 56f
            if (y + need <= limit) return
            if (need >= limit - 120f) return
            closePage()
            startPage(true)
        }

        private fun closePage() {
            val footer = doc.profile.footerLine()
            rule(margin, pageH - 36f, contentRight, pageH - 36f, "rule", 0.6f)
            if (footer.isNotBlank()) {
                ops += DrawOp(
                    "text",
                    footer,
                    pageW / 2f,
                    pageH - 22f,
                    size = 7f,
                    align = "center",
                    color = "muted"
                )
            }
            pages += PageLayout(pageW, pageH, ops.toList())
            ops.clear()
            pageIndex += 1
        }

        private fun stampPageNumbers() {
            if (pages.size <= 1) return
            pages.indices.forEach { index ->
                val page = pages[index]
                val extra = page.ops + DrawOp(
                    "text",
                    "Page ${index + 1} of ${pages.size}",
                    pageW / 2f,
                    pageH - 12f,
                    size = 7f,
                    align = "center",
                    color = "muted"
                )
                pages[index] = page.copy(ops = extra)
            }
        }

        private fun rule(x1: Float, y1: Float, x2: Float, y2: Float, color: String, stroke: Float) {
            ops += DrawOp("line", x = x1, y = y1, x2 = x2, y2 = y2, size = stroke, color = color)
        }

        private fun measure(text: String, size: Float, bold: Boolean): Float {
            if (text.isEmpty()) return 0f
            val factor = if (bold) 0.56f else 0.50f
            return text.length * size * factor
        }

        private fun wrap(text: String, size: Float, bold: Boolean, maxWidth: Float): List<String> {
            val out = mutableListOf<String>()
            text.replace("\r", "").split('\n').forEach { paragraph ->
                var remaining = paragraph.trim()
                if (remaining.isEmpty()) {
                    out += ""
                    return@forEach
                }
                while (remaining.isNotEmpty()) {
                    if (measure(remaining, size, bold) <= maxWidth) {
                        out += remaining
                        break
                    }
                    var breakAt = remaining.length
                    while (breakAt > 1 && measure(remaining.substring(0, breakAt), size, bold) > maxWidth) {
                        breakAt--
                    }
                    val space = remaining.lastIndexOf(' ', (breakAt - 1).coerceAtLeast(0))
                    if (space > 0) breakAt = space
                    if (breakAt <= 0) breakAt = 1
                    out += remaining.substring(0, breakAt).trimEnd()
                    remaining = remaining.substring(breakAt).trimStart()
                }
            }
            return out
        }
    }
}
