package com.strobingn.wildlifefieldops.util

/**
 * One letterhead, one section order, one table/totals/notes/signature/footer
 * treatment. Document kinds differ by [StandardDocument.title] and [StandardDocument.blocks].
 */
/**
 * Shared template, distinct bands. Colors are dark and print-friendly:
 * no blue, no yellow or amber. White title text is checked at >= 4.5:1.
 * [badge] and [pattern] keep each type readable in grayscale.
 */
enum class DocumentKind(
    val title: String,
    val filePrefix: String,
    val bandColor: String,
    val badge: String,
    val pattern: String
) {
    ESTIMATE("ESTIMATE", "estimate", "#006660", "E", "stripes-h"),
    INVOICE("INVOICE", "invoice", "#80202C", "I", "diagonal"),
    RECEIPT("RECEIPT", "receipt", "#0E6B38", "R", "dots"),
    INSPECTION("INSPECTION REPORT", "inspection", "#543468", "P", "stripes-v"),
    CONTRACT("SERVICE CONTRACT", "contract", "#683A18", "C", "cross"),
    EXCLUSION("EXCLUSION / REPAIR", "exclusion", "#465412", "X", "chevron"),
    WARRANTY("WARRANTY", "warranty", "#782456", "W", "dashes"),
    NWCO_LOG("NYS DEC NWCO LOG", "nwco-log", "#141416", "L", "frame"),
    EARNINGS_TAX("EARNINGS & NY SALES TAX", "earnings-tax", "#3E3E42", "T", "grid")
}

/** Rubber-stamp status in the top-right. Blank [fill] uses the document band color. */
data class StatusStamp(
    val lines: List<String> = emptyList(),
    val fill: String = ""
)

object DocumentPalette {
    /** Dark green used when a balance is zero. White text clears 4.5:1. */
    const val PAID = "#0E6B38"

    fun lighten(hex: String, amount: Float): String {
        val rgb = rgb(hex)
        fun channel(value: Int): Int = (value + (255 - value) * amount).toInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", channel(rgb[0]), channel(rgb[1]), channel(rgb[2]))
    }

    fun rgb(hex: String): IntArray {
        val clean = hex.removePrefix("#")
        return intArrayOf(
            clean.substring(0, 2).toInt(16),
            clean.substring(2, 4).toInt(16),
            clean.substring(4, 6).toInt(16)
        )
    }
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
    /** Kept so older callers compile. The title band replaces these boxes, so layout ignores them. */
    val showChoiceBoxes: Boolean = false,
    val estimateChecked: Boolean = false,
    val invoiceChecked: Boolean = false,
    val stamp: StatusStamp = StatusStamp(),
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
        private var compact = false

        fun run(): LayoutResult {
            val natural = pass(false)
            if (!tailIsNearEmpty(natural)) return natural
            val squeezed = pass(true)
            return if (squeezed.pages.size < natural.pages.size) squeezed else natural
        }

        private fun pass(squeeze: Boolean): LayoutResult {
            compact = squeeze
            pages.clear()
            ops.clear()
            sections.clear()
            pageIndex = 0
            continued = false
            y = margin
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

        /** A last page that only carries a short tail (terms line, signatures, footer). */
        private fun tailIsNearEmpty(result: LayoutResult): Boolean {
            if (result.pages.size < 2) return false
            val last = result.pages.last()
            val footerTop = last.height - 48f
            val contentBottom = last.ops
                .filter { it.kind == "text" && it.y < footerTop && !it.text.startsWith("Page ") }
                .maxOfOrNull { it.y } ?: 0f
            return contentBottom < last.height * 0.55f
        }

        private fun logoSize(): Float = if (compact) 68f else StandardDocumentTemplate.LOGO

        private fun contentFloor(): Float = pageH - if (compact) 42f else 56f

        private fun mark(name: String) {
            if (name !in sections) sections += name
        }

        private fun startPage(isContinued: Boolean) {
            continued = isContinued
            y = if (compact) 24f else 32f
            if (!isContinued) {
                mark("logo")
                mark("header")
                mark("title")
            }
            drawAccent()
            val logo = logoSize()
            ops += DrawOp("logo", x = margin, y = y, x2 = logo)
            val stampBottom = drawStamp()
            var rightY = y + if (compact) 16f else 18f
            val textLeft = margin + logo + 14f
            val headerRight = (stampLeft() - 8f).coerceAtLeast(textLeft + 80f)
            val lines = doc.profile.headerLines()
            lines.forEachIndexed { index, line ->
                val nameLine = index == 0
                val size = if (nameLine) 13f else 9f
                val wrapped = wrap(line, size, nameLine, headerRight - textLeft)
                wrapped.forEach { piece ->
                    ops += DrawOp(
                        kind = "text",
                        text = piece,
                        x = textLeft,
                        y = rightY,
                        size = size,
                        bold = nameLine,
                        color = if (nameLine) "ink" else "muted"
                    )
                    rightY += if (nameLine) if (compact) 13f else 15f else if (compact) 11f else 12f
                }
            }
            y = maxOf(y + logo + if (compact) 6f else 10f, rightY + 6f, stampBottom + 6f)
            drawBand(isContinued)
            if (!isContinued) {
                mark("meta")
                doc.meta.forEach { line ->
                    ensure(14f)
                    ops += DrawOp("text", line, margin, y, size = 9.5f)
                    y += if (compact) 12f else 13f
                }
                rule(margin, y, contentRight, y, "light", 0.65f)
                y += if (compact) 10f else 16f
                mark("customer")
                sectionTitle("BILL TO / OWNER")
                doc.customerRows.forEach { (label, value) -> labeled(label, value) }
                y += if (compact) 2f else 4f
            }
        }

        private fun drawAccent() {
            ops += DrawOp(
                "fillrect",
                x = 0f,
                y = 0f,
                x2 = 8f,
                y2 = pageH.toFloat(),
                color = doc.kind.bandColor
            )
        }

        private fun stampLines(): List<String> = doc.stamp.lines.map { it.trim() }.filter { it.isNotEmpty() }

        private fun stampWidth(): Float = 150f

        private fun stampLeft(): Float = pageW - margin - stampWidth()

        /** @return bottom of the stamp, or the top of the page when there is no stamp. */
        private fun drawStamp(): Float {
            val lines = stampLines()
            if (lines.isEmpty()) return 24f
            val width = stampWidth()
            val pitch = 13f
            val top = 22f
            val height = 12f + lines.size * pitch
            val x = stampLeft()
            val fill = doc.stamp.fill.ifBlank { doc.kind.bandColor }
            ops += DrawOp("fillrect", x = x, y = top, x2 = x + width, y2 = top + height, color = fill)
            ops += DrawOp("rect", x = x + 3f, y = top + 3f, x2 = x + width - 3f, y2 = top + height - 3f, color = "white")
            lines.forEachIndexed { index, line ->
                val size = when {
                    line.length > 18 -> 8f
                    index == 0 -> 11f
                    else -> 9f
                }
                ops += DrawOp(
                    "text",
                    line,
                    x + width / 2f,
                    top + 16f + index * pitch,
                    size = size,
                    bold = true,
                    align = "center",
                    color = "white"
                )
            }
            return top + height
        }

        private fun drawBand(continued: Boolean) {
            val color = doc.kind.bandColor
            val top = y
            val height = if (compact) 36f else 44f
            ops += DrawOp("fillrect", x = 0f, y = top, x2 = pageW.toFloat(), y2 = top + height, color = color)
            val badge = 30f
            val badgeX = margin
            val badgeY = top + (height - badge) / 2f
            val title = if (continued) doc.title + " (continued)" else doc.title
            val titleSize = if (title.length > 26) 13f else 16f
            val titleX = badgeX + badge + 10f
            val titleW = measure(title, titleSize, true) * 1.12f
            val patternLeft = titleX + titleW + 8f
            val patternRight = pageW - 12f
            if (patternRight - patternLeft > 28f) {
                drawPattern(doc.kind.pattern, patternLeft, top + 5f, patternRight, top + height - 5f, color)
            }
            ops += DrawOp("fillrect", x = 8f, y = top, x2 = minOf(patternLeft, pageW.toFloat()), y2 = top + height, color = color)
            ops += DrawOp("fillrect", x = badgeX, y = badgeY, x2 = badgeX + badge, y2 = badgeY + badge, color = "white")
            ops += DrawOp(
                "text",
                doc.kind.badge,
                badgeX + badge / 2f,
                badgeY + badge * 0.72f,
                size = 16f,
                bold = true,
                align = "center",
                color = color
            )
            ops += DrawOp(
                "text",
                title,
                titleX,
                top + height * 0.68f,
                size = titleSize,
                bold = true,
                color = "white"
            )
            y = top + height + if (compact) 8f else 14f
        }

        private fun drawPattern(
            pattern: String,
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            base: String
        ) {
            val ink = DocumentPalette.lighten(base, 0.48f)
            when (pattern) {
                "stripes-h" -> {
                    var lineY = y1 + 2f
                    while (lineY < y2) {
                        rule(x1, lineY, x2, lineY, ink, 1.3f)
                        lineY += 5f
                    }
                }
                "stripes-v" -> {
                    var lineX = x1
                    while (lineX < x2) {
                        rule(lineX, y1, lineX, y2, ink, 1.3f)
                        lineX += 5f
                    }
                }
                "dots" -> {
                    var dotY = y1 + 1f
                    while (dotY < y2 - 1f) {
                        var dotX = x1
                        while (dotX < x2 - 1f) {
                            ops += DrawOp("fillrect", x = dotX, y = dotY, x2 = dotX + 2.4f, y2 = dotY + 2.4f, color = ink)
                            dotX += 7f
                        }
                        dotY += 7f
                    }
                }
                "diagonal" -> diagonal(x1, y1, x2, y2, ink, downRight = true)
                "cross" -> {
                    diagonal(x1, y1, x2, y2, ink, downRight = true)
                    diagonal(x1, y1, x2, y2, ink, downRight = false)
                }
                "chevron" -> {
                    var lineX = x1
                    val mid = (y1 + y2) / 2f
                    while (lineX + 6f < x2) {
                        rule(lineX, y1 + 1f, lineX + 5f, mid, ink, 1.2f)
                        rule(lineX + 5f, mid, lineX, y2 - 1f, ink, 1.2f)
                        lineX += 9f
                    }
                }
                "dashes" -> {
                    var lineY = y1 + 3f
                    while (lineY < y2) {
                        var lineX = x1
                        while (lineX < x2) {
                            rule(lineX, lineY, minOf(lineX + 7f, x2), lineY, ink, 1.5f)
                            lineX += 11f
                        }
                        lineY += 6f
                    }
                }
                "grid" -> {
                    var lineY = y1
                    while (lineY < y2) {
                        rule(x1, lineY, x2, lineY, ink, 0.9f)
                        lineY += 6f
                    }
                    var lineX = x1
                    while (lineX < x2) {
                        rule(lineX, y1, lineX, y2, ink, 0.9f)
                        lineX += 6f
                    }
                }
                else -> {
                    ops += DrawOp("rect", x = x1, y = y1, x2 = x2, y2 = y2, color = ink)
                    ops += DrawOp("rect", x = x1 + 4f, y = y1 + 4f, x2 = x2 - 4f, y2 = y2 - 4f, color = ink)
                }
            }
        }

        private fun diagonal(x1: Float, y1: Float, x2: Float, y2: Float, color: String, downRight: Boolean) {
            val rise = y2 - y1
            var lineX = x1
            while (lineX < x2) {
                val endX = minOf(lineX + rise, x2)
                val traveled = endX - lineX
                if (downRight) {
                    rule(lineX, y1, endX, y1 + traveled, color, 1.15f)
                } else {
                    rule(lineX, y2, endX, y2 - traveled, color, 1.15f)
                }
                lineX += 6f
            }
        }

        private fun sectionTitle(title: String) {
            ensure(if (compact) 16f else 18f)
            ops += DrawOp("text", title.uppercase(), margin, y, size = 10f, bold = true)
            y += if (compact) 12f else 14f
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
            y += if (compact) 13f else 16f
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
                y += if (compact) 12f else 14f
            }
            y += if (compact) 2f else 4f
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
                val rowPitch = if (compact) 10f else 11f
                val rowHeight = cellLines.maxOf { it.size } * rowPitch + if (compact) 4f else 6f
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
                                y + lineIndex * rowPitch,
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
                y += if (line.bold) if (compact) 13f else 15f else if (compact) 12f else 13f
            }
            y += if (compact) 4f else 6f
        }

        private fun prose(title: String, text: String, size: Float, color: String) {
            if (text.isBlank()) return
            sectionTitle(title)
            wrap(text, size, false, contentWidth).forEach { line ->
                ensure(size + 3f)
                ops += DrawOp("text", line, margin, y, size = size, color = color)
                y += size + if (compact) 2.2f else 3.5f
            }
            y += if (compact) 8f else 14f
        }

        private fun signatures(slots: List<SignatureSlot>) {
            val drawn = slots.ifEmpty {
                listOf(SignatureSlot("Owner Signature"), SignatureSlot("Company Signature"))
            }
            val floor = pageH - 40f
            val room = floor - y
            val normal = drawn.sumOf { signatureHeight(it, tight = false).toDouble() }.toFloat()
            val tight = drawn.sumOf { signatureHeight(it, tight = true).toDouble() }.toFloat()
            if (room + 0.5f < tight) {
                closePage()
                startPage(true)
                paintSignatures(drawn, tight = false)
            } else {
                paintSignatures(drawn, tight = room + 0.5f < normal)
            }
        }

        private fun signatureGap(slot: SignatureSlot, tight: Boolean): Float = when {
            slot.imageTag.isNotBlank() -> if (tight) 22f else 18f
            tight && slot.name.isNotBlank() -> 18f
            tight -> 6f
            else -> 16f
        }

        private fun signatureAfter(tight: Boolean): Float = if (tight) 18f else 28f

        private fun signatureHeight(slot: SignatureSlot, tight: Boolean): Float =
            signatureGap(slot, tight) + signatureAfter(tight)

        private fun paintSignatures(slots: List<SignatureSlot>, tight: Boolean) {
            slots.forEach { slot ->
                val gap = signatureGap(slot, tight)
                val after = signatureAfter(tight)
                if (!tight) ensure(if (slot.imageTag.isNotBlank()) 52f else 44f)
                y += gap
                if (slot.imageTag.isNotBlank()) {
                    val imageHeight = if (tight) 18f else 36f
                    ops += DrawOp(
                        "image",
                        x = margin + 110f,
                        y = y - imageHeight,
                        x2 = if (tight) 120f else 160f,
                        y2 = imageHeight,
                        tag = slot.imageTag
                    )
                }
                val label = slot.label + ":"
                ops += DrawOp("text", label, margin, y, size = 9.5f)
                val start = margin + measure(label, 9.5f, false) + 6f
                rule(start, y + 1f, start + 200f, y + 1f, "light", 0.65f)
                if (slot.name.isNotBlank()) {
                    val lift = if (tight) 11f else 13f
                    ops += DrawOp("text", slot.name, start + 4f, y - lift, size = if (tight) 11f else 12f, italic = true)
                }
                val dateLabelX = start + 214f
                ops += DrawOp("text", "Date:", dateLabelX, y, size = 9.5f)
                val dateLine = dateLabelX + measure("Date:", 9.5f, false) + 4f
                rule(dateLine, y + 1f, dateLine + 80f, y + 1f, "light", 0.65f)
                if (slot.dateText.isNotBlank()) {
                    ops += DrawOp("text", slot.dateText, dateLine, y - 8f, size = 8f, color = "muted")
                }
                y += after
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
            val limit = contentFloor()
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
