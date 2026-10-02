package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Invoice
import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.pricing.CustomerSignatureRecord
import com.strobingn.wildlifefieldops.pricing.ExclusionPointRecord
import com.strobingn.wildlifefieldops.pricing.EstimateInvoiceCarry
import com.strobingn.wildlifefieldops.pricing.JobPaymentRecord
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.Money
import com.strobingn.wildlifefieldops.pricing.PaymentMethod
import com.strobingn.wildlifefieldops.pricing.PricingCalculator
import com.strobingn.wildlifefieldops.pricing.TrapRouteSlot
import com.strobingn.wildlifefieldops.pricing.effectiveTotal
import com.strobingn.wildlifefieldops.pricing.isManual
import com.strobingn.wildlifefieldops.pricing.markManual
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Batch 6 field rules. Auto-fill only writes an empty, non-manual field.
 * A value Sir clears stays blank through save, reload, recompute, and sync.
 * Nothing here changes a job status unless the caller applies a suggestion.
 */
object SignatureRules {
    const val ESTIMATE = "estimate"
    const val CONTRACT = "contract"

    fun manualKey(document: String): String =
        if (document == CONTRACT) ManualField.SIGNER_CONTRACT else ManualField.SIGNER_ESTIMATE

    fun find(pricing: JobPricing, document: String): CustomerSignatureRecord? =
        pricing.customerSignatures.firstOrNull { it.document == document }

    /**
     * Name shown in the editor. A manual blank stays blank. Otherwise an empty
     * stored name may show the customer name until Sir saves or clears it.
     */
    fun editorName(record: CustomerSignatureRecord?, customerName: String, manual: Boolean): String {
        val stored = record?.signerName.orEmpty()
        if (manual) return stored
        return OperatorWins.suggest(stored, customerName, manual = false)
    }

    fun save(
        pricing: JobPricing,
        document: String,
        signerName: String,
        signedAt: Long,
        pngBase64: String
    ): JobPricing {
        val name = signerName.trim()
        val ink = pngBase64.trim()
        val record = CustomerSignatureRecord(
            document = document,
            signerName = name,
            signedAt = if (name.isBlank() && ink.isBlank()) 0L else signedAt,
            pngBase64 = ink,
            typedOnly = ink.isBlank() && name.isNotBlank()
        )
        val rest = pricing.customerSignatures.filterNot { it.document == document }
        val next = pricing.copy(customerSignatures = rest + record)
        return if (name.isBlank()) next.markManual(manualKey(document)) else next
    }

    fun hasInk(record: CustomerSignatureRecord?): Boolean =
        !record?.pngBase64.isNullOrBlank()

    fun embedCaption(record: CustomerSignatureRecord?, zone: ZoneId = ZoneId.systemDefault()): String {
        if (record == null) return ""
        val name = record.signerName.trim()
        if (name.isBlank() && record.pngBase64.isBlank()) return ""
        val whenText = formatStamp(record.signedAt, zone)
        return listOf(name, whenText).filter { it.isNotBlank() }.joinToString(" · ")
    }

    fun formatStamp(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (millis <= 0L) return ""
        val zoned = Instant.ofEpochMilli(millis).atZone(zone)
        return DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm a", Locale.US).format(zoned)
    }
}

object PaymentLedger {
    /**
     * Invoice total the way [EstimateInvoiceCarry] saves it. A manually edited
     * invoice wins over the estimate. A cleared total stays cleared.
     */
    fun invoiceTotal(job: Job): Double {
        val saved = latestInvoice(job)
        val plan = EstimateInvoiceCarry.resolveOpen(job.pricing, job.estimatedValue, saved)
        if (saved == null && plan.kind == EstimateInvoiceCarry.OpenKind.BLANK) {
            return job.estimatedValue
        }
        return EstimateInvoiceCarry.buildInvoice(
            job = job,
            form = plan.form,
            existing = saved,
            invoiceNumber = saved?.invoiceNumber.orEmpty(),
            now = saved?.updatedAt ?: 0L,
            manuallyEdited = plan.form.manuallyEdited
        ).totalAmount
    }

    private fun latestInvoice(job: Job): Invoice? {
        val record = job.pricing.invoiceRecords.maxByOrNull { it.updatedAt } ?: return null
        val empty = record.id.isBlank() &&
            record.lineItems.isEmpty() &&
            !record.manuallyEdited &&
            record.totalAmount == 0.0 &&
            record.totalOverride == null &&
            record.subtotalOverride == null
        if (empty) return null
        return record.toInvoice(job)
    }

    fun totalPaid(payments: List<JobPaymentRecord>): Double =
        payments.fold(0.0) { acc, row -> Money.plus(acc, row.amount) }

    fun balanceDue(invoiceTotal: Double, payments: List<JobPaymentRecord>): Double =
        Money.minus(invoiceTotal, totalPaid(payments)).coerceAtLeast(0.0)

    fun upsert(payments: List<JobPaymentRecord>, row: JobPaymentRecord): List<JobPaymentRecord> {
        val id = row.id.ifBlank { "pay-${row.paidAt}-${payments.size}" }
        val next = row.copy(id = id, method = row.method.ifBlank { PaymentMethod.OTHER }, checkNumber = row.checkNumber.trim())
        val index = payments.indexOfFirst { it.id == id }
        if (index < 0) return payments + next
        return payments.toMutableList().apply { this[index] = next }
    }

    fun remove(payments: List<JobPaymentRecord>, id: String): List<JobPaymentRecord> =
        payments.filterNot { it.id == id }

    /**
     * Payment rows are the ledger. [JobPricing.paidAmount] follows the sum
     * unless Sir typed or cleared that profit field.
     */
    fun apply(pricing: JobPricing, payments: List<JobPaymentRecord>): JobPricing {
        val sum = totalPaid(payments)
        val paid = if (pricing.isManual(ManualField.PAID_AMOUNT)) pricing.paidAmount else sum
        return pricing.copy(payments = payments, paidAmount = paid)
    }

    fun formatDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (millis <= 0L) return ""
        return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()
    }

    fun parseDay(text: String, zone: ZoneId = ZoneId.systemDefault()): Long {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return 0L
        val date = runCatching { LocalDate.parse(trimmed) }.getOrNull() ?: return 0L
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun receiptLines(
        customer: String,
        invoiceTotal: Double,
        payments: List<JobPaymentRecord>,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<String> {
        val lines = mutableListOf<String>()
        lines += "Receipt"
        if (customer.isNotBlank()) lines += customer
        payments.forEach { row ->
            val method = PaymentMethod.label(row.method)
            val check = if (row.method == PaymentMethod.CHECK && row.checkNumber.isNotBlank()) " #${row.checkNumber}" else ""
            val day = formatDay(row.paidAt, zone)
            val note = row.note.trim()
            lines += listOf("$method$check", day, Money.formatUsd(row.amount), note)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        }
        lines += "Paid ${Money.formatUsd(totalPaid(payments))}"
        lines += "Balance due ${Money.formatUsd(balanceDue(invoiceTotal, payments))}"
        return lines
    }
}

object ExclusionEstimate {
    const val SEAL_PREFIX = "seal-"

    fun lineId(pointId: String): String = "$SEAL_PREFIX$pointId"

    fun suggestedDescription(point: ExclusionPointRecord): String {
        val size = point.size.trim()
        val location = point.location.trim()
        val material = point.material.trim()
        if (location.isBlank() && size.isBlank() && material.isBlank()) return ""
        val where = listOf(size, location).filter { it.isNotBlank() }.joinToString(" ")
        val seal = if (where.isBlank()) "Seal opening" else "Seal $where"
        return if (material.isBlank()) seal else "$seal with $material"
    }

    /** Blank stays blank once Sir owns the line. Otherwise fill only an empty description. */
    fun lineDescription(point: ExclusionPointRecord): String {
        if (point.descriptionManual) return point.description
        return OperatorWins.suggest(point.description, suggestedDescription(point), manual = false)
    }

    fun lineItem(point: ExclusionPointRecord): InvoiceLineItem {
        val desc = lineDescription(point)
        return InvoiceLineItem(
            id = lineId(point.id),
            description = desc,
            quantity = point.quantity,
            unit = "ea",
            unitPrice = point.unitPrice,
            total = point.totalOverride ?: Money.times(point.quantity, point.unitPrice),
            totalOverride = point.totalOverride
        )
    }

    fun isSealLine(id: String): Boolean = id.startsWith(SEAL_PREFIX)

    /**
     * Push openings into estimate lines. Existing non-seal lines stay.
     * A cleared description is written through as blank.
     */
    fun pushToEstimate(pricing: JobPricing): JobPricing {
        val sealIds = pricing.exclusionPoints.map { lineId(it.id) }.toSet()
        val kept = pricing.photoLineItems.filter { !isSealLine(it.id) || it.id in sealIds }
        val merged = pricing.exclusionPoints.fold(kept) { lines, point ->
            val line = lineItem(point)
            val index = lines.indexOfFirst { it.id == line.id }
            if (index < 0) lines + line else lines.toMutableList().apply { this[index] = line }
        }
        return pricing.copy(photoLineItems = merged)
    }

    /** Estimate edits of a seal line write back onto the opening. A blank line stays blank. */
    fun pullFromEstimate(pricing: JobPricing): JobPricing {
        val points = pricing.exclusionPoints.map { point ->
            val line = pricing.photoLineItems.firstOrNull { it.id == lineId(point.id) } ?: return@map point
            val cleared = line.description.isBlank()
            point.copy(
                description = line.description,
                quantity = line.quantity,
                unitPrice = line.unitPrice,
                totalOverride = line.totalOverride,
                descriptionManual = point.descriptionManual || cleared || line.description != suggestedDescription(point)
            )
        }
        return pricing.copy(exclusionPoints = points)
    }
}

data class RouteStop(
    val id: String,
    val kind: String,
    val title: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val whenMillis: Long,
    val parentJobId: String,
    val routeIndex: Int? = null
)

data class RouteTrap(
    val id: String,
    val jobId: String,
    val label: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?,
    val checkDate: Long,
    val nextCheckDate: Long?
)

object TodayRouteEngine {
    const val KIND_JOB = "JOB"
    const val KIND_TRAP = "TRAP"

    fun dayKey(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString()

    fun sameDay(millis: Long?, day: String, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (millis == null || millis <= 0L || day.isBlank()) return false
        return dayKey(millis, zone) == day
    }

    fun collect(jobs: List<Job>, traps: List<RouteTrap>, day: String, zone: ZoneId = ZoneId.systemDefault()): List<RouteStop> {
        val jobStops = jobs.mapNotNull { job ->
            if (!sameDay(job.scheduledDate, day, zone)) return@mapNotNull null
            val index = if (job.pricing.routeDay == day) job.pricing.routeIndex else null
            RouteStop(
                id = "job:${job.id}",
                kind = KIND_JOB,
                title = job.title.ifBlank { job.customerName.ifBlank { "Job" } },
                address = job.address,
                latitude = job.latitude,
                longitude = job.longitude,
                whenMillis = job.scheduledDate ?: 0L,
                parentJobId = job.id,
                routeIndex = index
            )
        }
        val trapStops = traps.mapNotNull { trap ->
            val onDay = sameDay(trap.checkDate, day, zone) || sameDay(trap.nextCheckDate, day, zone)
            if (!onDay) return@mapNotNull null
            val parent = jobs.firstOrNull { it.id == trap.jobId }
            val slot = parent?.pricing?.trapRoute?.firstOrNull { it.trapId == trap.id && it.day == day }
            val lat = trap.latitude ?: parent?.latitude
            val lng = trap.longitude ?: parent?.longitude
            RouteStop(
                id = "trap:${trap.id}",
                kind = KIND_TRAP,
                title = trap.label.ifBlank { "Trap check" },
                address = trap.address.ifBlank { parent?.address.orEmpty() },
                latitude = lat,
                longitude = lng,
                whenMillis = if (sameDay(trap.checkDate, day, zone)) trap.checkDate else trap.nextCheckDate ?: trap.checkDate,
                parentJobId = trap.jobId,
                routeIndex = slot?.index
            )
        }
        return (jobStops + trapStops).distinctBy { it.id }
    }

    /**
     * Any saved index for this day wins for the whole list. Otherwise nearest-neighbor
     * from the current GPS fix. Stops with no coordinates stay at the end.
     */
    fun order(
        stops: List<RouteStop>,
        originLat: Double?,
        originLng: Double?
    ): List<RouteStop> {
        if (stops.any { it.routeIndex != null }) {
            return stops.sortedWith(compareBy<RouteStop> { it.routeIndex ?: Int.MAX_VALUE }.thenBy { it.whenMillis })
        }
        if (originLat == null || originLng == null) {
            return stops.sortedBy { it.whenMillis }
        }
        val located = stops.filter { it.latitude != null && it.longitude != null }.toMutableList()
        val missing = stops.filter { it.latitude == null || it.longitude == null }.sortedBy { it.title }
        val ordered = mutableListOf<RouteStop>()
        var lat: Double = originLat
        var lng: Double = originLng
        while (located.isNotEmpty()) {
            val next = located.minBy { miles(lat, lng, it.latitude ?: lat, it.longitude ?: lng) }
            ordered += next
            lat = next.latitude ?: lat
            lng = next.longitude ?: lng
            located.remove(next)
        }
        return ordered + missing
    }

    fun reorder(stops: List<RouteStop>, fromIndex: Int, toIndex: Int): List<RouteStop> {
        if (fromIndex !in stops.indices || toIndex !in stops.indices || fromIndex == toIndex) {
            return stops.mapIndexed { index, stop -> stop.copy(routeIndex = index) }
        }
        val mutable = stops.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        return mutable.mapIndexed { index, stop -> stop.copy(routeIndex = index) }
    }

    fun applyOrder(jobs: List<Job>, ordered: List<RouteStop>, day: String): List<Job> {
        val byParent = ordered.groupBy { it.parentJobId }
        return jobs.mapNotNull { job ->
            val mine = byParent[job.id] ?: return@mapNotNull null
            val jobStop = mine.firstOrNull { it.kind == KIND_JOB }
            val trapSlots = mine.filter { it.kind == KIND_TRAP }.map {
                TrapRouteSlot(trapId = it.id.removePrefix("trap:"), day = day, index = it.routeIndex ?: 0)
            }
            val otherDays = job.pricing.trapRoute.filterNot { it.day == day }
            job.copy(
                pricing = job.pricing.copy(
                    routeDay = if (jobStop != null) day else job.pricing.routeDay,
                    routeIndex = if (jobStop != null) jobStop.routeIndex else job.pricing.routeIndex,
                    trapRoute = otherDays + trapSlots
                ),
                isSynced = false
            )
        }
    }

    fun clearManual(jobs: List<Job>, day: String): List<Job> =
        jobs.mapNotNull { job ->
            val touches = job.pricing.routeDay == day || job.pricing.trapRoute.any { it.day == day }
            if (!touches) return@mapNotNull null
            job.copy(
                pricing = job.pricing.copy(
                    routeIndex = if (job.pricing.routeDay == day) null else job.pricing.routeIndex,
                    trapRoute = job.pricing.trapRoute.filterNot { it.day == day }
                ),
                isSynced = false
            )
        }

    fun singleMapsUrl(stop: RouteStop): String {
        val dest = pin(stop)
        if (dest.isBlank()) return ""
        return "https://www.google.com/maps/dir/?api=1&destination=${encode(dest)}&travelmode=driving"
    }

    fun multiMapsUrl(stops: List<RouteStop>): String {
        val usable = stops.filter { pin(it).isNotBlank() }
        if (usable.isEmpty()) return ""
        if (usable.size == 1) return singleMapsUrl(usable.first())
        val destination = pin(usable.last())
        val waypoints = usable.dropLast(1).joinToString("|") { pin(it) }
        return "https://www.google.com/maps/dir/?api=1&destination=${encode(destination)}&waypoints=${encode(waypoints)}&travelmode=driving"
    }

    fun miles(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earth = 3958.7613
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLng / 2).let { it * it }
        return earth * 2.0 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1.0 - a))
    }

    private fun pin(stop: RouteStop): String {
        val lat = stop.latitude
        val lng = stop.longitude
        if (lat != null && lng != null) return "$lat,$lng"
        return stop.address.trim()
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}

object OnMyWay {
    /**
     * Prefill only. The phone's messaging app sends if Sir taps send there.
     */
    fun message(customerName: String, eta: String): String {
        val who = customerName.trim().ifBlank { "there" }
        val whenText = eta.trim()
        val base = "Hi $who, this is Wildlife Whisperer. I'm on my way."
        return if (whenText.isBlank()) base else "$base ETA $whenText."
    }
}

data class CustomerHistoryRow(
    val jobId: String,
    val dateMillis: Long,
    val species: String,
    val statusLabel: String,
    val amount: Double
)

object RepeatCustomerHistory {
    fun normalizeAddress(raw: String): String =
        raw.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")

    fun rows(current: Job, jobs: List<Job>): List<CustomerHistoryRow> {
        val address = normalizeAddress(current.address)
        return jobs.mapNotNull { other ->
            if (other.id == current.id) return@mapNotNull null
            val sameCustomer = current.customerId.isNotBlank() && current.customerId == other.customerId
            val sameAddress = address.isNotBlank() && address == normalizeAddress(other.address)
            if (!sameCustomer && !sameAddress) return@mapNotNull null
            val total = PricingCalculator.compute(other.pricing).total.effective
            CustomerHistoryRow(
                jobId = other.id,
                dateMillis = other.scheduledDate ?: other.createdAt,
                species = other.confirmedSpecies.ifBlank { other.type },
                statusLabel = JobStatusPipeline.label(other.status),
                amount = if (total > 0.0) total else other.estimatedValue
            )
        }.distinctBy { it.jobId }
            .sortedByDescending { it.dateMillis }
    }
}

object JobStatusPipeline {
    val stages: List<JobStatus> = listOf(
        JobStatus.LEAD,
        JobStatus.ESTIMATE_SENT,
        JobStatus.SCHEDULED,
        JobStatus.IN_PROGRESS,
        JobStatus.TRAPPING,
        JobStatus.EXCLUSION,
        JobStatus.INVOICED,
        JobStatus.PAID,
        JobStatus.CLOSED
    )

    fun label(status: JobStatus): String = when (status) {
        JobStatus.LEAD, JobStatus.PENDING -> "Lead"
        JobStatus.ESTIMATE_SENT -> "Estimate sent"
        JobStatus.SCHEDULED -> "Scheduled"
        JobStatus.IN_PROGRESS -> "In progress"
        JobStatus.TRAPPING -> "Trapping"
        JobStatus.EXCLUSION -> "Exclusion"
        JobStatus.INVOICED -> "Invoiced"
        JobStatus.PAID -> "Paid"
        JobStatus.CLOSED, JobStatus.COMPLETED -> "Closed"
        JobStatus.CANCELLED -> "Cancelled"
    }

    fun matches(jobStatus: JobStatus, filter: JobStatus): Boolean = when (filter) {
        JobStatus.LEAD, JobStatus.PENDING -> jobStatus == JobStatus.LEAD || jobStatus == JobStatus.PENDING
        JobStatus.CLOSED, JobStatus.COMPLETED ->
            jobStatus == JobStatus.CLOSED || jobStatus == JobStatus.COMPLETED
        else -> jobStatus == filter
    }

    fun fromPipeline(raw: String?): JobStatus? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return JobStatus.entries.firstOrNull { it.name == value }
    }

    fun indexOf(status: JobStatus): Int = when (status) {
        JobStatus.PENDING, JobStatus.LEAD -> 0
        JobStatus.ESTIMATE_SENT -> 1
        JobStatus.SCHEDULED -> 2
        JobStatus.IN_PROGRESS -> 3
        JobStatus.TRAPPING -> 4
        JobStatus.EXCLUSION -> 5
        JobStatus.INVOICED -> 6
        JobStatus.PAID -> 7
        JobStatus.COMPLETED, JobStatus.CLOSED, JobStatus.CANCELLED -> 8
    }

    /**
     * One forward hint from data already on the job. Null when there is nothing
     * ahead of the status Sir set. Callers show this as a tap target only.
     */
    fun suggest(job: Job): JobStatus? {
        val current = job.status
        val total = PricingCalculator.compute(job.pricing).total.effective
            .takeIf { it > 0.0 } ?: job.estimatedValue
        val paid = if (job.pricing.payments.isEmpty()) job.pricing.paidAmount
        else PaymentLedger.totalPaid(job.pricing.payments)
        val candidate = when {
            job.pricing.payments.isNotEmpty() && total > 0.0 && paid + 0.009 >= total -> JobStatus.PAID
            job.pricing.invoiceRecords.isNotEmpty() -> JobStatus.INVOICED
            job.pricing.exclusionPoints.isNotEmpty() -> JobStatus.EXCLUSION
            job.pricing.trapRecords.isNotEmpty() -> JobStatus.TRAPPING
            job.scheduledDate != null &&
                (current == JobStatus.LEAD || current == JobStatus.PENDING || current == JobStatus.ESTIMATE_SENT) ->
                JobStatus.SCHEDULED
            !job.pricing.isEmptyWorksheet() &&
                (current == JobStatus.LEAD || current == JobStatus.PENDING) -> JobStatus.ESTIMATE_SENT
            else -> null
        } ?: return null
        if (candidate == current) return null
        if (indexOf(candidate) <= indexOf(current)) return null
        return candidate
    }

    fun stamp(pricing: JobPricing, status: JobStatus): JobPricing =
        pricing.copy(pipelineStatus = status.name).markManual(ManualField.PIPELINE_STATUS)
}

data class FieldRecord(
    val id: String,
    val updatedAt: Long,
    val payload: String
)

object FieldDataExchange {
    const val FORMAT = "wildlife-whisperer-field-data-v1"

    /**
     * One row per id. The later [FieldRecord.updatedAt] wins. A tie keeps the
     * local row so a blank Sir cleared is not replaced by an older copy.
     */
    fun mergeById(local: List<FieldRecord>, incoming: List<FieldRecord>): List<FieldRecord> {
        val byId = LinkedHashMap<String, FieldRecord>()
        local.forEach { row -> if (row.id.isNotBlank()) byId[row.id] = row }
        incoming.forEach { row ->
            if (row.id.isBlank()) return@forEach
            val existing = byId[row.id]
            if (existing == null || row.updatedAt > existing.updatedAt) {
                byId[row.id] = row
            }
        }
        return byId.values.toList()
    }
}
