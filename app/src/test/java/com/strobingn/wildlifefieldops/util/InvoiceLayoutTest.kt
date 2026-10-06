package com.strobingn.wildlifefieldops.util

import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.CustomerNames
import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import com.strobingn.wildlifefieldops.data.model.JobCustomerDraft
import com.strobingn.wildlifefieldops.data.remote.toLocal
import com.strobingn.wildlifefieldops.data.remote.toRemoteDto
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.SyncedInvoiceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class InvoiceLayoutTest {
    private val moneyKinds = listOf(DocumentKind.ESTIMATE, DocumentKind.INVOICE, DocumentKind.CONTRACT)
    private val dash = DocumentText.EM_DASH

    private fun texts(document: StandardDocument): List<DrawOp> =
        document.layout().pages.flatMap { page -> page.ops.filter { it.kind == "text" } }

    // ---- 1. Fee section ----

    @Test
    fun feeRowsNeverContainUnderscoresAndShowEmDashForMissingAmounts() {
        val rows = StandardDocuments.feeTableRows(CityIslandSample.lineItems)
        assertEquals(
            listOf(
                listOf("Labor / Trap Service Fee", "$370.00"),
                listOf("Per Animal Captured Fee", dash),
                listOf("Non-Target Animal Captured Fee", dash),
                listOf("Inspection Fee", dash),
                listOf("Other / Exclusion & Repairs (Yellow jacket nest removal; Mileage)", "$934.66")
            ),
            rows
        )
        val empty = StandardDocuments.feeTableRows(emptyList())
        assertEquals(5, empty.size)
        assertTrue(empty.all { it[1] == dash })
        (rows + empty).flatten().forEach { cell -> assertFalse(cell, cell.contains('_')) }
    }

    @Test
    fun everyMoneyDocumentRendersTheFeeSectionAsATableWithNoBlanks() {
        moneyKinds.forEach { kind ->
            listOf(CityIslandSample.packet(), CityIslandSample.packet().copy(lineItems = emptyList())).forEach { packet ->
                val document = StandardDocuments.build(kind, packet)
                val fees = document.blocks.first { it.title == "SERVICE & ANIMAL FEES" }
                assertTrue("$kind fee block is a table", fees.lines.isEmpty())
                val table = requireNotNull(fees.table)
                assertEquals(listOf("Fee", "Amount"), table.columns)
                assertEquals(StandardDocuments.MONEY_TEXT, table.textSize)
                val ops = texts(document)
                assertFalse("$kind prints underscores", ops.any { it.text.contains("__") || it.text.contains("\$_") })
                assertTrue(ops.any { it.text == "Fee" && it.bold })
                assertTrue(ops.any { it.text == "Labor / Trap Service Fee" })
                assertTrue(ops.count { it.text == dash && it.align == "right" } >= 3)
            }
        }
    }

    @Test
    fun longFeeLabelsWrapInsideTheirCell() {
        val label = "Other / Exclusion & Repairs (Chimney cap with stainless mesh; ridge vent screen; " +
            "gable vent screen; soffit return seal; dryer vent guard; attic fan cover)"
        val document = StandardDocument(
            kind = DocumentKind.INVOICE,
            profile = BusinessProfile.defaults(),
            blocks = listOf(
                BodyBlock(
                    title = "SERVICE & ANIMAL FEES",
                    table = DocTable(
                        columns = listOf("Fee", "Amount"),
                        rows = listOf(listOf(label, "$934.66"), listOf("Inspection Fee", dash)),
                        weights = listOf(4.5f, 0.9f),
                        aligns = listOf("left", "right"),
                        textSize = StandardDocuments.MONEY_TEXT
                    )
                )
            )
        )
        val ops = document.layout().pages.first().ops.filter { it.kind == "text" }
        val fee = ops.first { it.text == "Fee" }
        val inspection = ops.first { it.text == "Inspection Fee" }
        val wrapped = ops.filter { it.x == fee.x && it.y > fee.y && it.y < inspection.y }
        assertTrue("label wraps: ${wrapped.map { it.text }}", wrapped.size >= 2)
        assertEquals(label, wrapped.joinToString(" ") { it.text })
        val columnRight = fee.x + 516f * (4.5f / 5.4f)
        wrapped.forEach { op -> assertTrue(op.text, op.x + op.text.length * op.size * 0.5f <= columnRight) }
        // The amount sits on the first line of its row; the next row starts below the wrapped label.
        assertEquals(wrapped.first().y, ops.first { it.text == "$934.66" }.y, 0.01f)
        assertTrue(inspection.y > wrapped.last().y + StandardDocuments.MONEY_TEXT)
    }

    // ---- Labor / Trap Service Fee ----

    @Test
    fun laborLineItemsMapToTheLaborTrapServiceFeeRow() {
        fun item(id: String, description: String, unit: String = "ea", total: Double = 100.0) =
            InvoiceLineItem(id = id, description = description, quantity = 1.0, unit = unit, unitPrice = total, total = total)
        val rows = StandardDocuments.mapFeeRows(CityIslandSample.lineItems)
        assertEquals("Labor / Trap Service Fee" to 370.0, rows[0])

        listOf("Labor", "labor / trap service", "Trap service", "Trap setup", "Labour").forEach { description ->
            assertEquals(description, 100.0, StandardDocuments.mapFeeRows(listOf(item("a", description)))[0].second)
        }
        // Hourly labor by unit, and two labor lines add up in the one row.
        val hourly = StandardDocuments.mapFeeRows(listOf(item("a", "Technician time", unit = "hr", total = 185.0), item("b", "Labor", total = 50.0)))
        assertEquals(235.0, hourly[0].second!!, 0.001)
        assertEquals(null, hourly[4].second)
        // Labor never falls into Other; materials still do.
        val split = StandardDocuments.mapFeeRows(listOf(item("a", "Exclusion labor", total = 120.0), item("b", "Materials", total = 30.0)))
        assertEquals(120.0, split[0].second!!, 0.001)
        assertEquals(30.0, split[4].second!!, 0.001)
        // A non-target capture is not taken by the per-animal row.
        val captures = StandardDocuments.mapFeeRows(listOf(item("a", "Non-target animal captured", total = 40.0), item("b", "Per animal captured", total = 75.0)))
        assertEquals(75.0, captures[1].second!!, 0.001)
        assertEquals(40.0, captures[2].second!!, 0.001)
        moneyKinds.forEach { kind ->
            val ops = texts(StandardDocuments.build(kind, CityIslandSample.packet()))
            val label = ops.first { it.text == "Labor / Trap Service Fee" }
            assertTrue(kind.name, ops.any { it.text == "$370.00" && abs(it.y - label.y) < 0.5f })
            assertFalse(ops.any { it.text == "Trap Service Fee" })
        }
    }

    // ---- 2. Phone / Email ----

    @Test
    fun blankPhoneAndEmailFallBackToTheLinkedCustomerRecord() {
        val raw = CityIslandSample.rawPacket()
        assertEquals("", raw.customerPhone)
        val filled = JobContactFallback.apply(raw, CityIslandSample.customer)
        assertEquals(CityIslandSample.PHONE, filled.customerPhone)
        assertEquals(CityIslandSample.EMAIL, filled.customerEmail)

        val typed = JobContactFallback.apply(raw.copy(customerPhone = "845-555-0000"), CityIslandSample.customer)
        assertEquals("845-555-0000", typed.customerPhone)
        assertEquals(CityIslandSample.EMAIL, typed.customerEmail)

        val altOnly = JobContactFallback.apply(raw, Customer(id = "c", alternatePhone = "914-555-0101"))
        assertEquals("914-555-0101", altOnly.customerPhone)

        val invoiceEmail = raw.copy(
            job = raw.job.copy(
                pricing = JobPricing(invoiceRecords = listOf(SyncedInvoiceRecord(id = "i", issueDate = 5L, customerEmail = "billing@example.com")))
            )
        )
        assertEquals("billing@example.com", JobContactFallback.apply(invoiceEmail, null).customerEmail)

        moneyKinds.forEach { kind ->
            val rows = StandardDocuments.build(kind, filled).customerRows.toMap()
            assertEquals(CityIslandSample.PHONE, rows["Phone:"])
            assertEquals(CityIslandSample.EMAIL, rows["Email:"])
        }
        DocumentKind.entries.forEach { kind ->
            val rows = StandardDocuments.build(kind, JobContactFallback.apply(raw, null)).customerRows.toMap()
            assertEquals("$kind phone", dash, rows["Phone:"])
            assertEquals("$kind email", dash, rows["Email:"])
        }
    }

    // ---- Name tag, tech, state, notes, tax ----

    @Test
    fun companyTagIsNeverShownTwice() {
        assertEquals("Pam Johnston (TSO)", CustomerNames.dedupeSuffixes("Pam Johnston (TSO) (TSO) (TSO)"))
        assertEquals("Pam Johnston (TSO)", CustomerNames.withCompany("Pam Johnston (TSO)", "TSO"))
        assertEquals("Pam Johnston (TSO)", CityIslandSample.customer.fullName)
        assertEquals("Pam Johnston (TSO)", JobCustomerDraft(name = "Pam Johnston", companyName = "TSO").displayName())
        assertEquals("Acme", Customer(companyName = "Acme").fullName)
        assertEquals("Ada Lovelace", Customer(firstName = "Ada", lastName = "Lovelace").fullName)
        // Sync round trip used to grow "(TSO)" each pass.
        var local = CityIslandSample.customer
        repeat(3) { local = local.toRemoteDto().toLocal(local) }
        assertEquals("Pam Johnston (TSO)", local.fullName)
        assertEquals("Johnston", local.lastName)
        val corrupted = CityIslandSample.customer.toRemoteDto().copy(name = "Pam Johnston (TSO) (TSO) (TSO)")
        assertEquals("Pam Johnston (TSO)", corrupted.toLocal(CityIslandSample.customer).fullName)

        val rows = StandardDocuments.build(DocumentKind.INVOICE, CityIslandSample.packet()).customerRows.toMap()
        assertEquals("Pam Johnston (TSO)", rows["Owner/Manager:"])
    }

    @Test
    fun techLineFallsBackToAssignedTechThenBusinessOwner() {
        fun tech(packet: StandardJobPacket) =
            StandardDocuments.build(DocumentKind.INVOICE, packet).meta.first { it.startsWith("Tech:") }
        val packet = CityIslandSample.packet()
        assertEquals("Tech: Dirk Diggler", tech(packet))
        assertEquals("Tech: Austin", tech(packet.copy(job = packet.job.copy(assignedTo = "Austin"))))
        assertEquals("Tech: Sam", tech(packet.copy(technicianName = "Sam", job = packet.job.copy(assignedTo = "Austin"))))
        assertEquals("Tech:", tech(packet.copy(profile = BusinessProfile.defaults())))

        val fromSettings = BusinessProfileResolve.fromStored(mapOf(BusinessProfileResolve.KEY_TECHNICIAN to "Dirk Diggler"))
        assertEquals("Dirk Diggler", fromSettings.ownerName)
        val fromNwco = BusinessProfileResolve.fromStored(mapOf(BusinessProfileResolve.KEY_NWCO_NAME to "Diggler, Dirk"))
        assertEquals("Diggler, Dirk", fromNwco.ownerName)
    }

    @Test
    fun stateIsUppercaseOnDocumentsOnly() {
        assertEquals("Bronx, NY 10464", DocumentText.upperState("Bronx, ny 10464"))
        assertEquals("Bronx, NY", DocumentText.upperState("Bronx, ny"))
        assertEquals("Cornwall, NY 12518", DocumentText.upperState("Cornwall, NY 12518"))
        assertEquals("Bronx", DocumentText.upperState("Bronx"))
        assertEquals("Port Jervis 12771", DocumentText.upperState("Port Jervis 12771"))
        val packet = CityIslandSample.packet()
        val rows = StandardDocuments.build(DocumentKind.INVOICE, packet).customerRows.toMap()
        assertEquals("Bronx, NY 10464", rows["City/State/ZIP:"])
        assertEquals("33 Tier Street, Bronx, ny 10464", packet.job.address)
    }

    @Test
    fun notesNeverPrintTheSameParagraphTwice() {
        val document = StandardDocuments.build(DocumentKind.INVOICE, CityIslandSample.packet())
        val notes = document.notes
        assertEquals(1, Regex("High-priority yellow jacket removal").findAll(notes).count())
        assertTrue(notes.contains("Labor 4.5 hrs"))
        assertTrue(notes.contains("Gate is on the left side"))
        assertEquals("Same note", DocumentText.mergeNotes(listOf("Same note", "  same note. ")))
        assertEquals("Seal the soffit and the ridge vent", DocumentText.mergeNotes(listOf("Seal the soffit", "Seal the soffit and the ridge vent")))
        assertEquals("Alpha job\nBeta notes", DocumentText.mergeNotes(listOf("Alpha job", "Beta notes")))
        assertEquals("Investigate attic\nGate", DocumentText.mergeNotes(listOf("Investigate attic", "Gate")))
    }

    @Test
    fun taxLabelAlwaysStatesTheRateUsedInTheMath() {
        assertEquals("Tax (8.875%):", StandardDocuments.taxLabel(8.875, 1304.66, 0.0, 115.79))
        assertEquals("Tax (8.125%):", StandardDocuments.taxLabel(8.125, 1000.0, 0.0, 81.25))
        assertEquals("Tax (8.125%):", StandardDocuments.taxLabel(8.125, 1000.0, 100.0, 73.13))
        assertEquals("Tax:", StandardDocuments.taxLabel(0.0, 225.0, 0.0, 0.0))
        // Rate says 8.125 but the amount was computed at 8.875: print what the math used.
        assertEquals("Tax (8.875%):", StandardDocuments.taxLabel(8.125, 1304.66, 0.0, 115.79))
        // Tax zeroed out on a taxable job: no rate is claimed.
        assertEquals("Tax:", StandardDocuments.taxLabel(8.875, 500.0, 0.0, 0.0))
        val ops = texts(StandardDocuments.build(DocumentKind.INVOICE, CityIslandSample.packet()))
        assertTrue(ops.any { it.text == "Tax (8.875%):" })
    }

    // ---- 3. Layout above the line items ----

    @Test
    fun areaAboveLineItemsLinesUpWithOneBodySize() {
        listOf(DocumentKind.ESTIMATE, DocumentKind.INVOICE).forEach { kind ->
            val page = StandardDocuments.build(kind, CityIslandSample.packet()).layout().pages.first()
            val ops = page.ops.filter { it.kind == "text" }
            val billTo = ops.first { it.text == "BILL TO / OWNER" }
            val lineHead = ops.first { it.text == "LINE ITEMS" }
            val labels = listOf("Owner/Manager:", "Address:", "City/State/ZIP:", "Phone:", "Email:", "Job:")
            val labelOps = labels.map { label -> ops.first { it.text == label } }
            val valueOps = labelOps.map { label -> ops.first { it.y == label.y && it.x > label.x } }
            assertEquals("$kind customer values share one column", 1, valueOps.map { it.x }.toSet().size)
            val body = StandardDocuments.MONEY_TEXT
            (labelOps + valueOps).forEach { assertEquals(it.text, body, it.size) }
            val feeHead = ops.first { it.text == "Fee" }
            val feeOps = ops.filter { it.y >= feeHead.y && it.y < lineHead.y }
            feeOps.forEach { assertEquals(it.text, body, it.size) }
            val totalsTop = ops.first { it.text == "Sub-Total:" }
            val itemCells = ops.filter { it.y > lineHead.y && it.y < totalsTop.y }
            itemCells.forEach { assertEquals(it.text, body, it.size) }
            assertEquals(body, totalsTop.size)
            // Fee Amount, line-item Amount and the totals amounts share one right edge.
            val totalsOps = ops.filter { it.y >= totalsTop.y && it.y < totalsTop.y + 80f && it.color == "ink" }
            val moneyRight = (feeOps + itemCells + totalsOps)
                .filter { it.align == "right" && (it.text.startsWith("$") || it.text == dash) && it.x > 500f }
                .map { it.x }.toSet()
            assertEquals("$kind money column right edges $moneyRight", 1, moneyRight.size)
            assertTrue(feeOps.count { it.align == "right" && it.x in moneyRight } == 6)
            assertFalse(ops.any { it.text.contains("___") })
        }
    }

    @Test
    fun tableRowRulesNeverStrikeThroughText() {
        val page = StandardDocuments.build(DocumentKind.INVOICE, CityIslandSample.packet()).layout().pages.first()
        val rules = page.ops.filter { it.kind == "line" && it.y == it.y2 }
        page.ops.filter { it.kind == "text" && it.color != "white" }.forEach { op ->
            val capTop = op.y - op.size * 0.72f
            rules.filter { it.x <= op.x && it.x2 >= op.x }.forEach { rule ->
                assertFalse("rule at ${rule.y} crosses '${op.text}' (${capTop}..${op.y})", rule.y > capTop + 0.5f && rule.y < op.y - 0.5f)
            }
        }
    }

    @Test
    fun documentTextClearsContrastAndIsNeverYellow() {
        DocumentKind.entries.forEach { kind ->
            val document = StandardDocuments.build(kind, CityIslandSample.packet())
            document.layout().pages.forEach { page ->
                page.ops.filter { it.kind == "text" && it.color != "white" }.forEach { op ->
                    val hex = DocumentPalette.tokenHex(op.color)
                    assertTrue("$kind '${op.text}' $hex", contrastOnWhite(hex) >= 4.5)
                    val rgb = DocumentPalette.rgb(hex)
                    assertFalse("$kind '${op.text}' is yellow/amber", rgb[0] > 160 && rgb[1] > 110 && rgb[2] < 100)
                    assertFalse("$kind '${op.text}' is blue", rgb[2] > 140 && rgb[2] > rgb[0] + 40 && rgb[2] > rgb[1] + 20)
                }
            }
        }
        val labels = setOf("Owner/Manager:", "Address:", "City/State/ZIP:", "Phone:", "Email:", "Job:")
        val ops = texts(StandardDocuments.build(DocumentKind.INVOICE, CityIslandSample.packet()))
        ops.filter { it.text in labels }.forEach { assertTrue(it.text, contrastOnWhite(DocumentPalette.tokenHex(it.color)) >= 4.5) }
    }

    @Test
    fun estimateAndInvoiceShareLogoHeaderAndLayoutWithTheirOwnBandAndStamp() {
        val packet = CityIslandSample.packet()
        listOf(packet, packet.copy(notes = "", job = packet.job.copy(description = "", notes = ""))).forEach { sample ->
            val estimate = StandardDocuments.build(DocumentKind.ESTIMATE, sample)
            val invoice = StandardDocuments.build(DocumentKind.INVOICE, sample)
            val e = estimate.layout()
            val i = invoice.layout()
            val ep = e.pages.first().ops
            val ip = i.pages.first().ops
            assertEquals(ep.first { it.kind == "logo" }, ip.first { it.kind == "logo" })
            assertEquals(StandardDocumentTemplate.LOGO, ip.first { it.kind == "logo" }.x2)
            assertEquals(estimate.headerLines, invoice.headerLines)
            fun header(ops: List<DrawOp>) = ops.filter { it.kind == "text" && it.text in estimate.headerLines }
            assertEquals(header(ep), header(ip))
            fun band(ops: List<DrawOp>, kind: DocumentKind) =
                ops.first { it.kind == "fillrect" && it.color == kind.bandColor && it.x <= 0.5f && it.x2 >= 611f }
            val eb = band(ep, DocumentKind.ESTIMATE)
            val ib = band(ip, DocumentKind.INVOICE)
            assertEquals(eb.y, ib.y, 0.01f)
            assertEquals(eb.y2, ib.y2, 0.01f)
            assertEquals(e.sections, i.sections)
            fun order(ops: List<DrawOp>) = ops.filter {
                it.kind == "text" && it.text in setOf("BILL TO / OWNER", "SERVICE & ANIMAL FEES", "LINE ITEMS", "Sub-Total:", "Grand-Total:")
            }.map { it.text }
            assertEquals(order(ep), order(ip))
        }
        val invoice = StandardDocuments.build(DocumentKind.INVOICE, packet)
        val estimate = StandardDocuments.build(DocumentKind.ESTIMATE, packet)
        assertEquals(listOf("BALANCE DUE", "$1420.45"), invoice.stamp.lines)
        assertEquals("ESTIMATE", estimate.stamp.lines.first())
        assertTrue(DocumentKind.ESTIMATE.bandColor != DocumentKind.INVOICE.bandColor)
    }

    @Test
    fun generatedNotesNeverStateATaxRate() {
        val note = CityIslandSample.workNote
        val cleaned = com.strobingn.wildlifefieldops.pricing.GeneratedNoteText.withoutTaxRate(note)
        assertFalse(cleaned, cleaned.contains("8.125"))
        assertTrue(cleaned.contains("Mileage 59.2 one-way @ \$0.67. No discount."))
        assertTrue(cleaned.startsWith("Labor 4.5 hrs @ \$185"))
        listOf("Sales tax 8.125% applied.", "Includes 8.875% NY sales tax.", "taxRate: 8.125%", "Tax rate of 8.125 percent.")
            .forEach { assertFalse(it, com.strobingn.wildlifefieldops.pricing.GeneratedNoteText.withoutTaxRate(it).contains("8.")) }
        val untouched = "Labor 2 hr @ \$185. Mileage 12 mi."
        assertEquals(untouched, com.strobingn.wildlifefieldops.pricing.GeneratedNoteText.withoutTaxRate(untouched))
        // AI inputs keep the locked manual rate and drop the stale rate from the prose.
        val locked = com.strobingn.wildlifefieldops.pricing.JobPricing(taxRatePercent = 8.875, taxRateManual = true)
        val filled = com.strobingn.wildlifefieldops.pricing.PricingCalculator.applyAiInputs(
            current = locked, laborHours = 4.5, laborRate = 185.0, materialsCost = 95.0, equipmentCost = 0.0,
            permitCost = 0.0, disposalCost = 0.0, mileage = 59.2, mileageRate = 0.67, taxRatePercent = 8.125,
            discountPercent = 0.0, rationale = "Bronx job. Tax 8.125%.", notes = note
        )
        assertEquals(8.875, filled.taxRatePercent, 0.0001)
        assertFalse(filled.notes.contains("8.125"))
        assertFalse(filled.rationale.contains("8.125"))
        assertEquals("Bronx job.", filled.rationale)
    }

    private fun contrastOnWhite(hex: String): Double {
        val rgb = DocumentPalette.rgb(hex)
        fun linear(channel: Int): Double {
            val c = channel / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val luminance = 0.2126 * linear(rgb[0]) + 0.7152 * linear(rgb[1]) + 0.0722 * linear(rgb[2])
        return 1.05 / (luminance + 0.05)
    }
}
