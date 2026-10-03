package com.strobingn.wildlifefieldops.util

import com.strobingn.wildlifefieldops.ai.fieldops.NwcoLogRecord
import com.strobingn.wildlifefieldops.ai.fieldops.NwcoOperatorProfile
import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.pricing.ExclusionPointRecord
import com.strobingn.wildlifefieldops.pricing.JobPaymentRecord
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

class StandardDocumentTemplateTest {
    @Test
    fun blankBusinessFieldStaysOffTheHeaderAndMissingFieldsUseDefaults() {
        val defaults = BusinessProfileResolve.fromStored(emptyMap())
        assertEquals(WildlifeWhispererIdentity.COMPANY, defaults.name)
        assertEquals(WildlifeWhispererIdentity.PHONE, defaults.phone)
        assertEquals(WildlifeWhispererIdentity.EMAIL, defaults.email)
        assertEquals(WildlifeWhispererIdentity.ADDRESS, defaults.address)
        assertTrue(defaults.headerLines().any { it.contains("WILDLIFE WHISPERER LLC") })
        assertTrue(defaults.headerLines().any { it.contains("austin@wildlifewhispererllc.com") })

        val cleared = BusinessProfileResolve.fromStored(
            mapOf(
                BusinessProfileResolve.KEY_EMAIL to "",
                BusinessProfileResolve.KEY_WEBSITE to "",
                BusinessProfileResolve.KEY_LICENSE to ""
            )
        )
        assertEquals("", cleared.email)
        assertFalse(cleared.headerLines().any { it.contains("@") })
        assertTrue(cleared.headerLines().any { it.contains(WildlifeWhispererIdentity.PHONE) })
        assertEquals(emptyList<String>(), BusinessProfile("", "", "", "", "", "").headerLines())
    }

    @Test
    fun everyDocumentTypeUsesTheSharedTemplateForTheSameJob() {
        val packet = samplePacket()
        val documents = StandardDocuments.buildAll(packet)
        assertEquals(DocumentKind.entries.toList(), documents.map { it.kind })
        val header = documents.first().headerLines
        assertTrue(header.any { it.contains("WILDLIFE WHISPERER LLC") })
        documents.forEach { document ->
            val layout = document.layout()
            assertEquals(StandardDocumentTemplate.ID, document.templateId)
            assertTrue(document.drawsLogo)
            assertEquals(header, document.headerLines)
            assertEquals(StandardDocumentTemplate.sectionOrder, document.sectionOrder)
            assertEquals(StandardDocumentTemplate.sectionOrder, layout.sections)
            assertTrue(layout.pages.first().ops.any { it.kind == "logo" })
            val text = layout.pages.flatMap { page -> page.ops.filter { it.kind == "text" }.map { it.text } }.joinToString("\n")
            val flat = text.replace("\n", " ")
            header.forEach { line -> assertTrue(flat.contains(line)) }
            assertTrue(text.contains("Ada Lovelace"))
            assertFalse(document.showChoiceBoxes)
            assertFalse(layout.pages.any { page -> page.ops.any { it.kind == "text" && (it.text == "Estimate" || it.text == "Invoice") } })
            assertTrue(layout.pages.all { page -> pageHasBand(page, document.kind) })
            assertTrue(layout.pages.all { page -> pageHasAccent(page, document.kind) })
            val titleOp = layout.pages.first().ops.first {
                it.kind == "text" && it.text == document.title && it.align == "left" && it.color == "white"
            }
            assertEquals("white", titleOp.color)
            assertTrue(titleOp.bold)
            assertTrue(titleOp.size >= 13f)
            assertTrue(layout.pages.first().ops.any { it.kind == "text" && it.text == document.kind.badge && it.color == document.kind.bandColor })
            when (document.kind) {
                DocumentKind.ESTIMATE -> {
                    assertEquals("ESTIMATE", document.title)
                    assertTrue(text.contains("Trap Service Fee"))
                    assertTrue(text.contains("Close the soffit"))
                    assertTrue(text.contains("fully understand"))
                }
                DocumentKind.INVOICE -> {
                    assertEquals("INVOICE", document.title)
                    assertTrue(text.contains("INV-100"))
                    assertTrue(text.contains("Due:"))
                    assertTrue(text.contains("Tax:"))
                    assertTrue(text.contains("$0.00"))
                    assertTrue(text.contains("Balance Due:"))
                    assertTrue(text.contains("$10.00"))
                    assertTrue(text.contains("Payment due within 30 days."))
                    assertTrue(text.contains("Trap service"))
                    assertTrue(text.contains("Deposit"))
                }
                DocumentKind.RECEIPT -> {
                    assertTrue(text.contains("Cash"))
                    assertTrue(text.contains("Deposit"))
                    assertTrue(text.contains("Balance Due:"))
                }
                DocumentKind.INSPECTION -> {
                    assertTrue(text.contains("Entry at the soffit"))
                    assertTrue(text.contains("Gray squirrel"))
                    assertTrue(text.contains("Moderate"))
                    assertTrue(text.contains("Attic is clear"))
                }
                DocumentKind.CONTRACT -> {
                    assertEquals("SERVICE CONTRACT", document.title)
                    assertTrue(text.contains("fully understand"))
                    assertTrue(text.contains("Trap Service Fee"))
                }
                DocumentKind.EXCLUSION -> {
                    assertTrue(text.contains("North soffit"))
                    assertTrue(text.contains("Hardware cloth"))
                    assertTrue(text.contains("photos/soffit.jpg"))
                }
                DocumentKind.WARRANTY -> {
                    assertTrue(text.contains("Soffit seal"))
                    assertTrue(text.contains("Term months: 12"))
                }
                DocumentKind.NWCO_LOG -> {
                    assertTrue(text.contains("5 Complainant"))
                    assertTrue(text.contains("Penal Law 210.45"))
                    assertTrue(text.contains("NWCO-9"))
                    assertTrue(text.contains("complaint A–D"))
                }
                DocumentKind.EARNINGS_TAX -> {
                    assertTrue(text.contains("Orange"))
                    assertTrue(text.contains("10.00"))
                }
            }
        }
        documents.forEach { document ->
            assertEquals("${document.kind} stays on one page for this job", 1, document.layout().pages.size)
        }
        writePngArtifacts(documents)
    }

    @Test
    fun eachDocumentTypeHasAUniqueBandAndWhiteTextClearsContrast() {
        val kinds = DocumentKind.entries
        assertEquals(9, kinds.size)
        assertEquals(kinds.size, kinds.map { it.bandColor }.toSet().size)
        assertEquals(kinds.size, kinds.map { it.title }.toSet().size)
        assertEquals(kinds.size, kinds.map { it.badge }.toSet().size)
        assertEquals(kinds.size, kinds.map { it.pattern }.toSet().size)
        kinds.forEach { kind ->
            assertTrue("${kind.title} ${kind.bandColor}", whiteContrast(kind.bandColor) >= 4.5)
            assertFalse("$kind looks blue", isBlue(kind.bandColor))
            assertFalse("$kind looks yellow", isYellow(kind.bandColor))
        }
        assertTrue(whiteContrast(DocumentPalette.PAID) >= 4.5)
    }

    @Test
    fun statusStampUsesTypedAmountsAndDates() {
        val packet = samplePacket()
        val day = SimpleDateFormat("MM/dd/yyyy", Locale.US)
        val documents = StandardDocuments.buildAll(packet)
        val estimate = documents.first { it.kind == DocumentKind.ESTIMATE }
        val validUntil = day.format(Date((packet.nowMillis) + 30L * 86_400_000L))
        assertEquals(listOf("ESTIMATE", "VALID UNTIL", validUntil), estimate.stamp.lines)
        val typedUntil = 1_800_000_000_000L
        val typedEstimate = StandardDocuments.build(DocumentKind.ESTIMATE, packet.copy(dueDateMillis = typedUntil))
        assertEquals(day.format(Date(typedUntil)), typedEstimate.stamp.lines.last())

        val invoice = documents.first { it.kind == DocumentKind.INVOICE }
        assertEquals(listOf("BALANCE DUE", "$6.00"), invoice.stamp.lines)
        assertEquals("", invoice.stamp.fill)
        val paid = StandardDocuments.build(
            DocumentKind.INVOICE,
            packet.copy(total = 10.0, amountPaid = 10.0, balanceDue = 0.0)
        )
        assertEquals(listOf("PAID"), paid.stamp.lines)
        assertEquals(DocumentPalette.PAID, paid.stamp.fill)
        val typedBalance = StandardDocuments.build(
            DocumentKind.INVOICE,
            packet.copy(total = 10.0, amountPaid = 10.0, balanceDue = 2.5)
        )
        assertEquals(listOf("BALANCE DUE", "$2.50"), typedBalance.stamp.lines)

        val receipt = documents.first { it.kind == DocumentKind.RECEIPT }
        assertEquals(listOf("RECEIPT", "PAID $4.00", "DUE $6.00"), receipt.stamp.lines)
        val paidReceipt = StandardDocuments.build(
            DocumentKind.RECEIPT,
            packet.copy(total = 10.0, amountPaid = 10.0, balanceDue = 0.0)
        )
        assertEquals(listOf("PAID"), paidReceipt.stamp.lines)
        assertEquals(DocumentPalette.PAID, paidReceipt.stamp.fill)

        val warranty = documents.first { it.kind == DocumentKind.WARRANTY }
        assertEquals("EXPIRES", warranty.stamp.lines.first())
        val contract = documents.first { it.kind == DocumentKind.CONTRACT }
        assertEquals("SIGNED", contract.stamp.lines.first())
        assertTrue(documents.first { it.kind == DocumentKind.INSPECTION }.stamp.lines.contains("MODERATE"))
        assertTrue(documents.first { it.kind == DocumentKind.INSPECTION }.stamp.lines.contains("FOLLOW-UP"))
        assertEquals("1 OPENING", documents.first { it.kind == DocumentKind.EXCLUSION }.stamp.lines.first())
        assertTrue(documents.first { it.kind == DocumentKind.NWCO_LOG }.stamp.lines.first().contains("NWCO"))
        assertEquals("NY SALES TAX", documents.first { it.kind == DocumentKind.EARNINGS_TAX }.stamp.lines.first())
    }

    @Test
    fun signatureBlockStaysOnTheFirstPageWhenItIsTheOnlyOverflow() {
        val packet = samplePacket()
        val estimate = StandardDocuments.build(DocumentKind.ESTIMATE, packet)
        val pages = estimate.layout().pages
        assertEquals(1, pages.size)
        val text = pages.first().ops.filter { it.kind == "text" }.map { it.text }
        assertTrue(text.any { it.startsWith("Owner Signature") })
        assertTrue(text.any { it.startsWith("Company Signature") })
    }

    @Test
    fun signatureNamesStayBelowTheTermsLine() {
        val page = StandardDocuments.build(DocumentKind.NWCO_LOG, samplePacket()).layout().pages.first()
        val termsBottom = page.ops.filter { it.kind == "text" && it.text.contains("Penal Law") }.maxOf { it.y }
        val signer = page.ops.first { it.kind == "text" && it.italic && it.text.contains("Strobing") }
        assertTrue("signer y ${signer.y} terms $termsBottom", signer.y >= termsBottom + 8f)
    }

    @Test
    fun aLongDocumentKeepsItsOverflowPage() {
        val rows = (1..70).joinToString("\n") { "County $it,1.00" }
        val document = StandardDocuments.build(
            DocumentKind.EARNINGS_TAX,
            samplePacket().copy(earningsCsv = rows, notes = "", terms = "")
        )
        val pages = document.layout().pages
        assertTrue(pages.size >= 2)
        val last = pages.last()
        val contentBottom = last.ops
            .filter { it.kind == "text" && it.y < last.height - 48f && !it.text.startsWith("Page ") }
            .maxOf { it.y }
        assertTrue(contentBottom > last.height * 0.45f)
        assertTrue(last.ops.any { it.kind == "fillrect" && it.color == DocumentKind.EARNINGS_TAX.bandColor && it.x2 >= last.width - 1f })
    }

    private fun writePngArtifacts(documents: List<StandardDocument>) {
        val dir = File("/opt/cursor/artifacts/standard-documents")
        dir.mkdirs()
        val dump = File(dir, "layout.txt")
        dump.writeText(documents.joinToString("\n") { document ->
            document.layout().pages.mapIndexed { index, page ->
            val name = if (index == 0) document.kind.filePrefix else "${document.kind.filePrefix}-p${index + 1}"
            buildString {
                append("PAGE\t")
                append(name)
                append('\t')
                append(escape(document.title))
                append('\t')
                append(page.width)
                append('\t')
                append(page.height)
                append('\n')
                page.ops.forEach { op ->
                    when (op.kind) {
                        "text" -> append(
                            "text\t${escape(op.text)}\t${op.x}\t${op.y}\t${op.size}\t${if (op.bold) 1 else 0}\t${if (op.italic) 1 else 0}\t${op.align}\t${op.color}\n"
                        )
                        "line" -> append("line\t${op.x}\t${op.y}\t${op.x2}\t${op.y2}\t${op.size}\t${op.color}\n")
                        "rect", "fillrect" -> append("${op.kind}\t${op.x}\t${op.y}\t${op.x2}\t${op.y2}\t${op.color}\n")
                        "logo" -> append("logo\t${op.x}\t${op.y}\t${op.x2}\n")
                    }
                }
            }
            }.joinToString("\n")
        })
        val renderer = locate("tools/render-standard-document-pages.java")
        val logo = locate("app/src/main/assets/wildlife_whisperer_logo.png.b64")
        val classDir = File(dir, "classes")
        classDir.mkdirs()
        val javac = File(System.getProperty("java.home"), "bin/javac")
        val java = File(System.getProperty("java.home"), "bin/java")
        check(run(javac.absolutePath, "-d", classDir.absolutePath, renderer.absolutePath) == 0) {
            "javac failed for the document PNG renderer"
        }
        check(
            run(
                java.absolutePath,
                "-cp",
                classDir.absolutePath,
                "render_standard_document_pages",
                dump.absolutePath,
                logo.absolutePath,
                dir.absolutePath
            ) == 0
        ) { "PNG renderer failed" }
        assertTrue(File(dir, "all-documents.png").length() > 1000)
        assertTrue(File(dir, "all-documents-grayscale.png").length() > 1000)
        documents.forEach { document ->
            assertTrue(File(dir, "${document.kind.filePrefix}.png").length() > 1000)
            assertTrue(File(dir, "${document.kind.filePrefix}-grayscale.png").length() > 1000)
        }
    }

    private fun pageHasBand(page: PageLayout, kind: DocumentKind): Boolean =
        page.ops.any { op ->
            op.kind == "fillrect" &&
                op.color == kind.bandColor &&
                op.x <= 0.5f &&
                op.x2 >= page.width - 1f &&
                op.y2 - op.y >= 30f
        }

    private fun pageHasAccent(page: PageLayout, kind: DocumentKind): Boolean =
        page.ops.any { op ->
            op.kind == "fillrect" &&
                op.color == kind.bandColor &&
                op.x <= 0.5f &&
                op.x2 <= 12f &&
                op.y2 - op.y >= page.height - 2f
        }

    private fun whiteContrast(hex: String): Double {
        val clean = hex.removePrefix("#")
        val r = clean.substring(0, 2).toInt(16) / 255.0
        val g = clean.substring(2, 4).toInt(16) / 255.0
        val b = clean.substring(4, 6).toInt(16) / 255.0
        fun linear(channel: Double) = if (channel <= 0.03928) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
        val luminance = 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
        return 1.05 / (luminance + 0.05)
    }

    private fun isBlue(hex: String): Boolean {
        val rgb = DocumentPalette.rgb(hex)
        return rgb[2] > 140 && rgb[2] > rgb[0] + 40 && rgb[2] > rgb[1] + 20 && rgb[0] < 100
    }

    private fun isYellow(hex: String): Boolean {
        val rgb = DocumentPalette.rgb(hex)
        return rgb[0] > 180 && rgb[1] > 140 && rgb[2] < 90
    }

    private fun locate(relative: String): File {
        val candidates = listOf(
            File(relative),
            File("../$relative"),
            File("/workspace/$relative")
        )
        return candidates.firstOrNull { it.exists() } ?: candidates.last()
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "")

    private fun run(vararg command: String): Int {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        if (code != 0) error(output.ifBlank { "command failed: ${command.joinToString(" ")}" })
        return code
    }

    private fun samplePacket(): StandardJobPacket {
        val job = Job(
            customerName = "Ada Lovelace",
            address = "1 Harbor Lane, Cornwall, NY 12518",
            title = "Squirrel exclusion",
            description = "Close the soffit",
            notes = "Gate code 1234",
            confirmedSpecies = "Gray squirrel",
            legalNotes = "Attic is clear",
            nextStep = "Install a one-way door",
            assignedTo = "Austin",
            pricing = JobPricing(
                warrantyStartAt = 1_700_000_000_000L,
                warrantyTermMonths = 12,
                warrantyCovered = "Soffit seal",
                exclusionPoints = listOf(
                    ExclusionPointRecord(
                        location = "North soffit",
                        size = "2 in",
                        material = "Hardware cloth",
                        description = "Seal north soffit",
                        photoPath = "photos/soffit.jpg",
                        unitPrice = 180.0
                    )
                )
            )
        )
        return StandardJobPacket(
            job = job,
            lineItems = listOf(
                InvoiceLineItem(
                    id = "line-1",
                    description = "Trap service",
                    quantity = 1.0,
                    unit = "ea",
                    unitPrice = 225.0,
                    total = 225.0
                )
            ),
            subtotal = 225.0,
            taxRatePercent = 0.0,
            taxAmount = 0.0,
            discountAmount = 0.0,
            total = 10.0,
            amountPaid = 4.0,
            balanceDue = 6.0,
            notes = "Typed note",
            terms = "Payment due within 30 days.",
            documentNumber = "INV-100",
            technicianName = "Austin",
            payments = listOf(
                JobPaymentRecord(
                    method = PaymentMethod.CASH,
                    amount = 4.0,
                    paidAt = 1_700_000_000_000L,
                    note = "Deposit"
                )
            ),
            customerPhone = "845-555-0100",
            customerEmail = "ada@example.com",
            inspection = InspectionReportFields(
                customerName = "Ada Lovelace",
                inspectorName = "Austin",
                inspectionType = "Full",
                inspectionDate = 1_700_000_000_000L,
                jobTitle = "Squirrel exclusion",
                jobAddress = "1 Harbor Lane, Cornwall, NY 12518",
                species = "Gray squirrel",
                findings = "Entry at the soffit",
                entryPoints = "North soffit gap",
                damage = "Chewed fascia",
                recommendations = "Install a one-way door",
                severity = "Moderate",
                notes = "Attic is clear",
                weather = "Clear, 55 F",
                followUpRequired = true
            ),
            nwcoOperator = NwcoOperatorProfile(
                lastName = "Strobing",
                firstName = "Austin",
                licenseNumber = "NWCO-9",
                decRegion = "3",
                countyOfResidence = "Orange",
                phone = "(845) 751-8448",
                address = "210 Willow Avenue, Cornwall, NY 12518"
            ),
            nwcoRows = listOf(
                NwcoLogRecord(
                    complainant = "Ada Lovelace",
                    species = "Gray squirrel",
                    complaintType = "A",
                    abatementMethod = "B",
                    areaOfComplaint = "A",
                    trapsSet = "2",
                    speciesAndNumberTaken = "Gray squirrel 1",
                    disposition = "E",
                    datesPerformed = "10/03/2026"
                )
            ),
            nwcoLicenseYear = 2025,
            earningsCsv = "county,taxable\nOrange,10.00",
            nowMillis = 1_700_000_000_000L,
            signerName = "Ada Lovelace",
            signedAtMillis = 1_700_000_100_000L
        )
    }

}
