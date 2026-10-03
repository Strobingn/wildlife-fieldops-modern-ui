package com.strobingn.wildlifefieldops.util

import com.strobingn.wildlifefieldops.ai.fieldops.NwcoLogRecord
import com.strobingn.wildlifefieldops.ai.fieldops.NwcoOperatorProfile
import com.strobingn.wildlifefieldops.ai.fieldops.WarrantyTracker
import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.pricing.ExclusionPointRecord
import com.strobingn.wildlifefieldops.pricing.JobPaymentRecord
import com.strobingn.wildlifefieldops.pricing.Money
import com.strobingn.wildlifefieldops.pricing.PaymentMethod
import com.strobingn.wildlifefieldops.pricing.effectiveTotal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class InspectionReportFields(
    val customerName: String = "",
    val inspectorName: String = "",
    val inspectionType: String = "",
    val inspectionDate: Long = 0L,
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

/**
 * Everything the shared renderer needs for one job. Typed money fields are
 * printed as given — a tax amount of 0.00 stays 0.00.
 */
data class StandardJobPacket(
    val job: Job,
    val profile: BusinessProfile = BusinessProfile.defaults(),
    val lineItems: List<InvoiceLineItem> = emptyList(),
    val subtotal: Double = 0.0,
    val taxRatePercent: Double = 0.0,
    val taxAmount: Double = 0.0,
    val discountAmount: Double = 0.0,
    val total: Double = 0.0,
    val amountPaid: Double? = null,
    val balanceDue: Double? = null,
    val notes: String = "",
    val terms: String = "",
    val documentNumber: String = "",
    val technicianName: String = "",
    val invoiceDateMillis: Long? = null,
    val dueDateMillis: Long? = null,
    val payments: List<JobPaymentRecord> = emptyList(),
    val customerPhone: String = "",
    val customerEmail: String = "",
    val inspection: InspectionReportFields? = null,
    val nwcoOperator: NwcoOperatorProfile? = null,
    val nwcoRows: List<NwcoLogRecord> = emptyList(),
    val nwcoLicenseYear: Int = 0,
    val earningsCsv: String = "",
    val nowMillis: Long = System.currentTimeMillis(),
    val signerName: String = "",
    val signedAtMillis: Long? = null,
    val customerSignatureBase64: String = "",
    val companySignatureBase64: String = "",
    val includeQr: Boolean = false
)

object StandardDocuments {
    const val ACCEPTANCE =
        "Wildlife Whisperer LLC only recommends exclusion/repairs necessary to prevent future wildlife damage. " +
            "Fees and conditions are as explained by our representative. I have read and fully understand this " +
            "service contract and accept the recommendations and fees proposed by Wildlife Whisperer LLC."

    fun buildAll(packet: StandardJobPacket): List<StandardDocument> =
        DocumentKind.entries.map { build(it, packet) }

    fun build(kind: DocumentKind, packet: StandardJobPacket): StandardDocument = when (kind) {
        DocumentKind.ESTIMATE -> moneyDoc(kind, packet, invoiceFields = false, acceptance = true)
        DocumentKind.INVOICE -> moneyDoc(kind, packet, invoiceFields = true, acceptance = true)
        DocumentKind.CONTRACT -> moneyDoc(kind, packet, invoiceFields = false, acceptance = true)
        DocumentKind.RECEIPT -> receipt(packet)
        DocumentKind.INSPECTION -> inspection(packet)
        DocumentKind.EXCLUSION -> exclusion(packet)
        DocumentKind.WARRANTY -> warranty(packet)
        DocumentKind.NWCO_LOG -> nwco(packet)
        DocumentKind.EARNINGS_TAX -> earnings(packet)
    }

    private fun moneyDoc(
        kind: DocumentKind,
        packet: StandardJobPacket,
        invoiceFields: Boolean,
        acceptance: Boolean
    ): StandardDocument {
        val blocks = mutableListOf<BodyBlock>()
        blocks += BodyBlock(
            title = "SERVICE & ANIMAL FEES",
            lines = feeLines(mapFeeRows(packet.lineItems))
        )
        if (packet.lineItems.isNotEmpty()) {
            blocks += BodyBlock(
                title = "LINE ITEMS",
                table = lineTable(packet.lineItems)
            )
        }
        if (invoiceFields && packet.payments.isNotEmpty()) {
            blocks += BodyBlock(title = "PAYMENTS", table = paymentTable(packet.payments))
        }
        val work = workText(packet)
        val terms = buildString {
            if (invoiceFields && packet.terms.isNotBlank()) append(packet.terms.trim())
            if (acceptance) {
                if (isNotEmpty()) append("\n\n")
                append(ACCEPTANCE)
            }
            if (!invoiceFields && packet.terms.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(packet.terms.trim())
            }
        }
        val paid = packet.amountPaid ?: packet.payments.sumOf { it.amount }
        val totals = mutableListOf(
            TotalLine("Sub-Total:", Money.formatUsd(packet.subtotal)),
        )
        if (packet.discountAmount != 0.0) {
            totals += TotalLine("Discount:", Money.formatUsd(-packet.discountAmount))
        }
        totals += TotalLine(taxLabel(packet.taxRatePercent), Money.formatUsd(packet.taxAmount))
        totals += TotalLine("Grand-Total:", Money.formatUsd(packet.total), bold = true)
        if (invoiceFields) {
            totals += TotalLine("Amount Paid:", Money.formatUsd(paid))
            val balance = packet.balanceDue ?: (packet.total - paid)
            totals += TotalLine("Balance Due:", Money.formatUsd(balance), bold = true)
        }
        return base(kind, packet).copy(
            showChoiceBoxes = true,
            estimateChecked = kind == DocumentKind.ESTIMATE,
            invoiceChecked = kind == DocumentKind.INVOICE,
            meta = metaLines(kind, packet, invoiceFields),
            blocks = blocks,
            totals = totals,
            notes = work,
            terms = terms,
            signatures = signatureSlots(packet)
        )
    }

    private fun receipt(packet: StandardJobPacket): StandardDocument {
        val paid = packet.amountPaid ?: packet.payments.sumOf { it.amount }
        val balance = packet.balanceDue ?: (packet.total - paid)
        val noteLines = packet.payments.mapNotNull { row ->
            row.note.trim().takeIf { it.isNotBlank() }?.let { note ->
                PaymentMethod.label(row.method) + ": " + note
            }
        }
        return base(packet = packet, kind = DocumentKind.RECEIPT).copy(
            meta = listOf("Date: ${day(packet.invoiceDateMillis ?: packet.nowMillis)}"),
            blocks = listOf(BodyBlock(title = "PAYMENTS", table = paymentTable(packet.payments))),
            totals = listOf(
                TotalLine("Invoice total:", Money.formatUsd(packet.total)),
                TotalLine("Amount Paid:", Money.formatUsd(paid)),
                TotalLine("Balance Due:", Money.formatUsd(balance), bold = true)
            ),
            notes = (listOf(packet.notes.trim()) + noteLines).filter { it.isNotBlank() }.joinToString("\n"),
            terms = packet.terms.trim(),
            signatures = signatureSlots(packet)
        )
    }

    private fun inspection(packet: StandardJobPacket): StandardDocument {
        val fields = packet.inspection ?: InspectionReportFields(
            customerName = packet.job.customerName,
            jobTitle = packet.job.title,
            jobAddress = packet.job.address,
            species = packet.job.confirmedSpecies.ifBlank { packet.job.type },
            findings = packet.job.notes,
            notes = packet.job.legalNotes,
            recommendations = packet.job.nextStep,
            inspectionDate = packet.nowMillis
        )
        val blocks = mutableListOf<BodyBlock>()
        if (fields.severity.isNotBlank()) {
            blocks += BodyBlock(title = "SEVERITY", lines = listOf(fields.severity), minRuledLines = 1)
        }
        blocks += BodyBlock(title = "Species Identified", lines = listOf(fields.species), minRuledLines = 2)
        blocks += BodyBlock(title = "Findings", lines = listOf(fields.findings), minRuledLines = 3)
        blocks += BodyBlock(title = "Entry Points", lines = listOf(fields.entryPoints), minRuledLines = 2)
        blocks += BodyBlock(title = "Damage Assessment", lines = listOf(fields.damage), minRuledLines = 2)
        blocks += BodyBlock(title = "Recommendations", lines = listOf(fields.recommendations), minRuledLines = 3)
        if (fields.weather.isNotBlank()) {
            blocks += BodyBlock(title = "Weather", lines = listOf(fields.weather), minRuledLines = 1)
        }
        if (fields.followUpRequired) {
            blocks += BodyBlock(title = "Follow-up", lines = listOf("Follow-up required: Yes"), minRuledLines = 1)
        }
        val whenMillis = fields.inspectionDate.takeIf { it > 0L } ?: packet.nowMillis
        val meta = mutableListOf("Date: ${day(whenMillis)}")
        if (fields.inspectionType.isNotBlank()) meta += "Type: ${fields.inspectionType}"
        val tech = fields.inspectorName.ifBlank { packet.technicianName.ifBlank { packet.job.assignedTo } }
        if (tech.isNotBlank()) meta += "Tech: $tech"
        val inspector = signatureSlots(packet).toMutableList()
        if (fields.inspectorName.isNotBlank() && inspector.none { it.name == fields.inspectorName }) {
            inspector += SignatureSlot("Inspector Signature", name = fields.inspectorName, dateText = day(whenMillis))
        }
        return base(DocumentKind.INSPECTION, packet).copy(
            meta = meta,
            customerRows = customerRows(
                packet.copy(
                    job = packet.job.copy(
                        customerName = fields.customerName.ifBlank { packet.job.customerName },
                        address = fields.jobAddress.ifBlank { packet.job.address },
                        title = fields.jobTitle.ifBlank { packet.job.title }
                    )
                )
            ),
            blocks = blocks,
            notes = fields.notes.trim(),
            terms = "",
            signatures = inspector,
            includeQr = packet.includeQr
        )
    }

    private fun exclusion(packet: StandardJobPacket): StandardDocument {
        val points = packet.job.pricing.exclusionPoints
        val rows = points.map { point ->
            listOf(
                point.location,
                point.size,
                point.material,
                exclusionDescription(point),
                point.photoPath,
                Money.formatUsd(exclusionAmount(point))
            )
        }
        val sum = points.sumOf { exclusionAmount(it) }
        val detail = points.joinToString("\n") { point ->
            listOf(point.location, point.size, point.material, exclusionDescription(point), point.photoPath)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        }
        return base(DocumentKind.EXCLUSION, packet).copy(
            meta = listOf("Date: ${day(packet.nowMillis)}"),
            blocks = listOf(
                BodyBlock(
                    title = "OPENINGS",
                    table = DocTable(
                        columns = listOf("Location", "Size", "Material", "Description", "Photo", "Price"),
                        rows = rows,
                        weights = listOf(1.2f, 0.7f, 1f, 1.6f, 1.2f, 0.8f),
                        aligns = listOf("left", "left", "left", "left", "left", "right")
                    )
                )
            ),
            totals = listOf(TotalLine("Openings total:", Money.formatUsd(sum), bold = true)),
            notes = listOf(detail, packet.notes.trim()).filter { it.isNotBlank() }.joinToString("\n"),
            terms = packet.terms.trim(),
            signatures = signatureSlots(packet)
        )
    }

    private fun warranty(packet: StandardJobPacket): StandardDocument {
        val pricing = packet.job.pricing
        val plan = WarrantyTracker.fromJob(pricing.warrantyStartAt, pricing.warrantyTermMonths, pricing.warrantyCovered)
        val lines = listOf(
            "Start: " + (plan.startAt?.let { day(it) } ?: ""),
            "Term months: ${pricing.warrantyTermMonths}",
            "Expires: " + (plan.expiresAt?.let { day(it) } ?: ""),
            "Covered: ${pricing.warrantyCovered}"
        )
        return base(DocumentKind.WARRANTY, packet).copy(
            meta = listOf("Date: ${day(packet.nowMillis)}"),
            blocks = listOf(BodyBlock(title = "WARRANTY", lines = lines, minRuledLines = 4)),
            notes = listOf(pricing.warrantyCovered.trim(), packet.notes.trim()).filter { it.isNotBlank() }.distinct()
                .joinToString("\n"),
            terms = packet.terms.trim(),
            signatures = signatureSlots(packet)
        )
    }

    private fun nwco(packet: StandardJobPacket): StandardDocument {
        val operator = packet.nwcoOperator ?: NwcoOperatorProfile()
        val year = packet.nwcoLicenseYear
        val duration = if (year > 0) {
            "License duration October 1 $year to September 30 ${year + 1}"
        } else {
            ""
        }
        val rows = packet.nwcoRows.filterNot { it.deleted }.map { row ->
            listOf(
                row.complainant,
                row.datesPerformed.ifBlank { day(row.workDate) },
                row.species,
                row.complaintType,
                row.abatementMethod,
                row.areaOfComplaint,
                row.trapsSet,
                row.speciesAndNumberTaken,
                row.disposition
            )
        }
        val extra = customerRows(packet) + listOf(
            "1 Name:" to operator.displayName(),
            "2 NWCO license #:" to operator.licenseNumber,
            "3 DEC region:" to operator.decRegion,
            "4 County of residence:" to operator.countyOfResidence,
            "Licensee phone:" to operator.phone,
            "Licensee address:" to operator.address
        )
        return base(DocumentKind.NWCO_LOG, packet).copy(
            landscape = true,
            meta = listOfNotNull(
                duration.takeIf { it.isNotBlank() },
                "Keep weekly. Submit with NWCO renewal. Codes: complaint A–D · method A–H · area A–C · disposition E / R / T."
            ),
            customerRows = extra,
            blocks = listOf(
                BodyBlock(
                    title = "NUISANCE WILDLIFE CONTROL LOG",
                    table = DocTable(
                        columns = listOf(
                            "5 Complainant",
                            "6 Date(s)",
                            "7 Species",
                            "8 Type",
                            "9 Method",
                            "10 Area",
                            "11 Traps",
                            "12 Taken",
                            "13 Disposition"
                        ),
                        rows = rows,
                        weights = listOf(1.5f, 1f, 1.1f, 0.8f, 1f, 0.8f, 0.7f, 1f, 1.2f)
                    )
                )
            ),
            notes = packet.notes,
            terms = "False statements are punishable as a Class A misdemeanor (Penal Law 210.45).",
            signatures = listOf(
                SignatureSlot(
                    label = "Applicant Signature",
                    name = operator.displayName(),
                    dateText = ""
                )
            ) + signatureSlots(packet)
        )
    }

    private fun earnings(packet: StandardJobPacket): StandardDocument {
        val lines = packet.earningsCsv.replace("\r", "").lineSequence().toList()
        return base(DocumentKind.EARNINGS_TAX, packet).copy(
            meta = listOf("Date: ${day(packet.nowMillis)}"),
            blocks = listOf(BodyBlock(title = "EARNINGS AND NY SALES TAX", lines = lines, minRuledLines = 1)),
            notes = packet.notes,
            terms = packet.terms,
            signatures = signatureSlots(packet)
        )
    }

    private fun base(kind: DocumentKind, packet: StandardJobPacket): StandardDocument {
        return StandardDocument(
            kind = kind,
            profile = packet.profile,
            customerRows = customerRows(packet),
            customerSignatureBase64 = packet.customerSignatureBase64,
            companySignatureBase64 = packet.companySignatureBase64
        )
    }

    private fun customerRows(packet: StandardJobPacket): List<Pair<String, String>> {
        val (street, city) = splitAddress(packet.job.address)
        return listOf(
            "Owner/Manager:" to packet.job.customerName,
            "Address:" to street,
            "City/State/ZIP:" to city,
            "Phone:" to packet.customerPhone,
            "Email:" to packet.customerEmail,
            "Job:" to packet.job.title
        )
    }

    private fun metaLines(kind: DocumentKind, packet: StandardJobPacket, invoiceFields: Boolean): List<String> {
        val tech = packet.technicianName.ifBlank { packet.job.assignedTo }
        val lines = mutableListOf(
            "# ${documentNumber(kind, packet)}",
            "Date: ${day(packet.invoiceDateMillis ?: packet.nowMillis)}"
        )
        if (invoiceFields) {
            val due = packet.dueDateMillis ?: (packet.nowMillis + 30L * 86_400_000L)
            lines += "Due: ${day(due)}"
        }
        lines += if (tech.isBlank()) "Tech:" else "Tech: $tech"
        return lines
    }

    private fun documentNumber(kind: DocumentKind, packet: StandardJobPacket): String {
        if (packet.documentNumber.isNotBlank()) return packet.documentNumber.trim()
        val prefix = when (kind) {
            DocumentKind.ESTIMATE -> "EST"
            DocumentKind.INVOICE -> "INV"
            DocumentKind.CONTRACT -> "CTR"
            DocumentKind.RECEIPT -> "RCT"
            else -> "DOC"
        }
        return "$prefix-${packet.nowMillis % 100000}"
    }

    private fun workText(packet: StandardJobPacket): String = buildString {
        if (packet.notes.isNotBlank()) append(packet.notes.trim())
        if (packet.job.description.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append(packet.job.description.trim())
        }
        if (packet.job.notes.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append(packet.job.notes.trim())
        }
    }

    private fun signatureSlots(packet: StandardJobPacket): List<SignatureSlot> {
        val date = packet.signedAtMillis?.takeIf { it > 0L }?.let { day(it) }.orEmpty()
        return listOf(
            SignatureSlot(
                label = "Owner Signature",
                name = packet.signerName,
                dateText = date,
                imageTag = if (packet.customerSignatureBase64.isNotBlank()) "customer" else ""
            ),
            SignatureSlot(
                label = "Company Signature",
                imageTag = if (packet.companySignatureBase64.isNotBlank()) "company" else ""
            )
        )
    }

    private fun lineTable(items: List<InvoiceLineItem>): DocTable = DocTable(
        columns = listOf("Description", "Qty", "Unit", "Rate", "Amount"),
        rows = items.map { item ->
            listOf(
                item.description,
                trimNum(item.quantity),
                item.unit,
                Money.formatUsd(item.unitPrice),
                Money.formatUsd(item.effectiveTotal())
            )
        },
        weights = listOf(2.4f, 0.6f, 0.6f, 0.9f, 0.9f),
        aligns = listOf("left", "right", "left", "right", "right")
    )

    private fun paymentTable(payments: List<JobPaymentRecord>): DocTable = DocTable(
        columns = listOf("Method", "Date", "Amount", "Note"),
        rows = payments.map { row ->
            val method = PaymentMethod.label(row.method)
            val check = if (row.method == PaymentMethod.CHECK && row.checkNumber.isNotBlank()) {
                "$method #${row.checkNumber}"
            } else {
                method
            }
            listOf(check, day(row.paidAt), Money.formatUsd(row.amount), row.note.trim())
        },
        weights = listOf(1.3f, 1f, 0.9f, 2f),
        aligns = listOf("left", "left", "right", "left")
    )

    private fun feeLines(rows: List<Pair<String, Double?>>): List<String> = rows.map { (label, amount) ->
        val money = if (amount != null) Money.formatUsd(amount) else "$" + "_".repeat(8)
        "$label    $money"
    }

    private fun taxLabel(rate: Double): String {
        if (rate <= 0.0) return "Tax:"
        val shown = String.format(Locale.US, "%.3f", rate).trimEnd('0').trimEnd('.')
        return "Tax ($shown%):"
    }

    private fun exclusionDescription(point: ExclusionPointRecord): String =
        point.description.ifBlank {
            listOf(point.size, point.location, point.material).filter { it.isNotBlank() }.joinToString(" ")
        }

    private fun exclusionAmount(point: ExclusionPointRecord): Double =
        point.totalOverride ?: Money.times(point.quantity, point.unitPrice)

    private fun splitAddress(address: String): Pair<String, String> {
        if (address.isBlank()) return "" to ""
        val parts = address.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return when {
            parts.size >= 3 -> parts.dropLast(2).joinToString(", ") to parts.takeLast(2).joinToString(", ")
            parts.size == 2 -> parts[0] to parts[1]
            else -> address to ""
        }
    }

    private fun day(millis: Long): String =
        SimpleDateFormat("MM/dd/yyyy", Locale.US).format(Date(millis))

    private fun trimNum(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

    /**
     * Classic five fee rows. Unmatched lines roll into Other so their amounts
     * are still on the page; the line-item table keeps every description.
     */
    fun mapFeeRows(lineItems: List<InvoiceLineItem>): List<Pair<String, Double?>> {
        if (lineItems.isEmpty()) {
            return listOf(
                "Trap Service Fee" to null,
                "Per Animal Captured Fee" to null,
                "Non-Target Animal Captured Fee" to null,
                "Inspection Fee" to null,
                "Other / Exclusion & Repairs" to null
            )
        }
        val used = mutableSetOf<String>()
        fun take(vararg keys: String): Double? {
            val match = lineItems.firstOrNull { item ->
                if (item.id in used) return@firstOrNull false
                val description = item.description.lowercase(Locale.US)
                keys.any { key -> description.contains(key) }
            } ?: return null
            used.add(match.id)
            return match.effectiveTotal()
        }
        val trap = take("trap service", "trap fee", "trap setup", "trap set")
        val perAnimal = take("per animal", "animal captured", "capture fee", "animal fee")
        val nonTarget = take("non-target", "nontarget", "non target")
        val inspection = take("inspection")
        val otherMatched = take("exclusion", "repair", "sealing", "entry point", "materials", "labor", "other")
        val leftover = lineItems.filter { it.id !in used }
        val otherTotal = (otherMatched ?: 0.0) + leftover.sumOf { it.effectiveTotal() }
        val otherLabel = if (leftover.isNotEmpty() && leftover.size <= 2 && leftover.all { item ->
                item.description.isNotBlank() && item.description.lowercase(Locale.US).let { description ->
                    !description.contains("exclusion") && !description.contains("repair") && !description.contains("other")
                }
            }
        ) {
            val descriptions = leftover.joinToString("; ") { it.description.take(28) }
            "Other / Exclusion & Repairs ($descriptions)"
        } else {
            "Other / Exclusion & Repairs"
        }
        val otherAmount: Double? = when {
            leftover.isNotEmpty() || otherMatched != null -> otherTotal
            else -> null
        }
        return listOf(
            "Trap Service Fee" to trap,
            "Per Animal Captured Fee" to perAnimal,
            "Non-Target Animal Captured Fee" to nonTarget,
            "Inspection Fee" to inspection,
            otherLabel to otherAmount
        )
    }
}
