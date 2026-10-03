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
        val dump = File(dir, "layout.txt")
        dump.writeText(documents.joinToString("\n") { document ->
            val page = document.layout().pages.first()
            buildString {
                append("PAGE\t")
                append(document.kind.filePrefix)
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
        documents.forEach { document ->
            assertTrue(File(dir, "${document.kind.filePrefix}.png").exists())
        }
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
