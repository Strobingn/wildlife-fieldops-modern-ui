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
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Base64
import javax.imageio.ImageIO

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
            header.forEach { line -> assertTrue(text.contains(line)) }
            assertTrue(text.contains("Ada Lovelace"))
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
        writePngArtifacts(documents)
    }

    private fun writePngArtifacts(documents: List<StandardDocument>) {
        val dir = File("/opt/cursor/artifacts/standard-documents")
        dir.mkdirs()
        val logo = loadLogo()
        val pages = documents.map { document ->
            val image = renderPage(document.layout().pages.first(), logo)
            val file = File(dir, "${document.kind.filePrefix}.png")
            ImageIO.write(image, "png", file)
            document.kind.title to image
        }
        val sheet = contactSheet(pages)
        ImageIO.write(sheet, "png", File(dir, "all-documents.png"))
        assertTrue(File(dir, "all-documents.png").length() > 1000)
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

    private fun loadLogo(): BufferedImage? {
        val file = File("src/main/assets/wildlife_whisperer_logo.png.b64")
        if (!file.exists()) return null
        val bytes = Base64.getMimeDecoder().decode(file.readText().trim())
        return ImageIO.read(ByteArrayInputStream(bytes))
    }

    private fun renderPage(page: PageLayout, logo: BufferedImage?): BufferedImage {
        val image = BufferedImage(page.width, page.height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.fillRect(0, 0, page.width, page.height)
        page.ops.forEach { op -> paintOp(g, op, logo) }
        g.dispose()
        return image
    }

    private fun paintOp(g: Graphics2D, op: DrawOp, logo: BufferedImage?) {
        when (op.kind) {
            "text" -> {
                val style = when {
                    op.italic -> Font.ITALIC
                    op.bold -> Font.BOLD
                    else -> Font.PLAIN
                }
                g.font = Font(Font.SANS_SERIF, style, op.size.toInt().coerceAtLeast(6))
                g.color = awt(op.color)
                val width = g.fontMetrics.stringWidth(op.text)
                val x = when (op.align) {
                    "center" -> op.x - width / 2f
                    "right" -> op.x - width
                    else -> op.x
                }
                g.drawString(op.text, x, op.y)
            }
            "line" -> {
                g.color = awt(op.color)
                g.stroke = BasicStroke(op.size.coerceAtLeast(0.6f))
                g.drawLine(op.x.toInt(), op.y.toInt(), op.x2.toInt(), op.y2.toInt())
            }
            "rect" -> {
                g.color = awt(op.color)
                g.drawRect(op.x.toInt(), op.y.toInt(), (op.x2 - op.x).toInt(), (op.y2 - op.y).toInt())
            }
            "fillrect" -> {
                g.color = awt(op.color)
                g.fillRect(op.x.toInt(), op.y.toInt(), (op.x2 - op.x).toInt(), (op.y2 - op.y).toInt())
            }
            "logo" -> {
                val size = op.x2.toInt().coerceAtLeast(1)
                if (logo != null) {
                    g.drawImage(logo, op.x.toInt(), op.y.toInt(), size, size, null)
                } else {
                    g.color = Color(0x3A, 0x3A, 0x3A)
                    g.drawOval(op.x.toInt(), op.y.toInt(), size, size)
                }
            }
        }
    }

    private fun contactSheet(pages: List<Pair<String, BufferedImage>>): BufferedImage {
        val columns = 3
        val cellW = 320
        val cellH = 440
        val rows = (pages.size + columns - 1) / columns
        val sheet = BufferedImage(columns * cellW, rows * cellH, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        g.color = Color(0xF4, 0xF4, 0xF4)
        g.fillRect(0, 0, sheet.width, sheet.height)
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 14)
        pages.forEachIndexed { index, (title, image) ->
            val col = index % columns
            val row = index / columns
            val x = col * cellW
            val y = row * cellH
            g.color = Color(0x14, 0x14, 0x16)
            g.drawString(title, x + 12, y + 22)
            val targetW = cellW - 24
            val targetH = cellH - 40
            g.drawImage(image, x + 12, y + 30, targetW, targetH, null)
        }
        g.dispose()
        return sheet
    }

    private fun awt(token: String): Color = when (token) {
        "muted", "rule" -> Color(0x3A, 0x3A, 0x3A)
        "light" -> Color(0xBE, 0xBE, 0xBE)
        else -> Color(0x14, 0x14, 0x16)
    }
}
