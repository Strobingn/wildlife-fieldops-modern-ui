package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.backup.CustomerSnapshot
import com.strobingn.wildlifefieldops.data.backup.FieldDataBundle
import com.strobingn.wildlifefieldops.data.backup.FieldDataCodec
import com.strobingn.wildlifefieldops.data.backup.JobSnapshot
import com.strobingn.wildlifefieldops.data.backup.toJob
import com.strobingn.wildlifefieldops.data.backup.toSnapshot
import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.data.remote.toLocal
import com.strobingn.wildlifefieldops.data.remote.toRemoteDto
import com.strobingn.wildlifefieldops.data.remote.toRemoteStatus
import com.strobingn.wildlifefieldops.pricing.CustomerSignatureRecord
import com.strobingn.wildlifefieldops.pricing.ExclusionPointRecord
import com.strobingn.wildlifefieldops.pricing.JobPaymentRecord
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.SyncedInvoiceRecord
import com.strobingn.wildlifefieldops.pricing.PaymentMethod
import com.strobingn.wildlifefieldops.pricing.PricingCalculator
import com.strobingn.wildlifefieldops.pricing.PricingJson
import com.strobingn.wildlifefieldops.pricing.isManual
import com.strobingn.wildlifefieldops.pricing.markManual
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class Batch6FieldOpsTest {
    private val zone = ZoneId.of("America/New_York")
    private val allowedRemote = setOf(
        "Active", "Scheduled", "In Progress", "Needs Follow-up", "Closed", "Cancelled"
    )

    @Test
    fun typedNameSignatureStaysBlankAfterClearAndJsonReload() {
        val saved = SignatureRules.save(
            pricing = JobPricing(customerSignatures = listOf(CustomerSignatureRecord(document = SignatureRules.ESTIMATE, signerName = "Pat"))),
            document = SignatureRules.ESTIMATE,
            signerName = "",
            signedAt = 1_700_000_000_000L,
            pngBase64 = ""
        )
        assertEquals("", SignatureRules.find(saved, SignatureRules.ESTIMATE)?.signerName)
        assertTrue(saved.isManual(ManualField.SIGNER_ESTIMATE))
        val reloaded = PricingJson.decode(PricingJson.encode(saved))
        assertEquals("", SignatureRules.find(reloaded, SignatureRules.ESTIMATE)?.signerName)
        assertTrue(reloaded.isManual(ManualField.SIGNER_ESTIMATE))
        assertEquals("", SignatureRules.editorName(SignatureRules.find(reloaded, SignatureRules.ESTIMATE), "Pat Customer", manual = true))
        val filled = SignatureRules.save(JobPricing(), SignatureRules.CONTRACT, "Pat Customer", 1_700_000_000_000L, "")
        val record = SignatureRules.find(filled, SignatureRules.CONTRACT)
        assertTrue(record!!.typedOnly)
        assertTrue(SignatureRules.embedCaption(record, zone).contains("Pat Customer"))
        assertFalse(SignatureRules.hasInk(record))
    }

    @Test
    fun partialPaymentsKeepBalanceAndClearedAmount() {
        val first = JobPaymentRecord(id = "a", method = PaymentMethod.CHECK, checkNumber = "1042", amount = 100.0, paidAt = 1_700_000_000_000L)
        val second = JobPaymentRecord(id = "b", method = PaymentMethod.CASH, amount = 0.0, paidAt = 0L)
        val pricing = PaymentLedger.apply(JobPricing(totalOverride = 250.0), listOf(first, second))
        assertEquals(100.0, PaymentLedger.totalPaid(pricing.payments), 0.0)
        assertEquals(150.0, PaymentLedger.balanceDue(250.0, pricing.payments), 0.0)
        assertEquals(100.0, pricing.paidAmount, 0.0)
        val cleared = PaymentLedger.apply(
            pricing.markManual(ManualField.PAID_AMOUNT).copy(paidAmount = 0.0),
            pricing.payments
        )
        assertEquals(0.0, cleared.paidAmount, 0.0)
        assertEquals("", PaymentLedger.formatDay(0L, zone))
        val lines = PaymentLedger.receiptLines("Oak St", 250.0, pricing.payments, zone)
        assertTrue(lines.any { it.contains("#1042") })
        assertTrue(lines.any { it.contains("Balance due") })
        val removed = PaymentLedger.remove(pricing.payments, "a")
        assertEquals(1, removed.size)
        assertEquals("b", removed.first().id)
    }

    @Test
    fun paymentsReadTheSavedInvoiceNotTheEstimate() {
        val manual = SyncedInvoiceRecord(
            id = "inv-1",
            manuallyEdited = true,
            lineItems = listOf(
                InvoiceLineItem(description = "Hand-priced exclusion", quantity = 1.0, unit = "ea", unitPrice = 222.0, total = 222.0)
            ),
            totalOverride = 222.0,
            totalAmount = 222.0,
            taxRate = 0.0,
            updatedAt = 20L
        )
        val job = Job(
            id = "job-1",
            estimatedValue = 500.0,
            pricing = JobPricing(
                laborHours = 4.0,
                laborRate = 85.0,
                totalOverride = 500.0,
                invoiceRecords = listOf(manual)
            )
        )
        assertEquals(222.0, PaymentLedger.invoiceTotal(job), 0.0)
        val cleared = job.copy(
            pricing = job.pricing.copy(
                invoiceRecords = listOf(
                    manual.copy(totalOverride = 0.0, totalAmount = 0.0, lineItems = emptyList(), updatedAt = 30L)
                )
            )
        )
        assertEquals(0.0, PaymentLedger.invoiceTotal(cleared), 0.0)
        val carried = Job(
            estimatedValue = 180.0,
            pricing = JobPricing(laborHours = 2.0, laborRate = 90.0, taxRatePercent = 0.0)
        )
        assertEquals(180.0, PaymentLedger.invoiceTotal(carried), 0.0)
    }

    @Test
    fun exclusionLineFillsOnlyWhenBlankAndStaysEditable() {
        val point = ExclusionPointRecord(id = "e1", location = "soffit", size = "2 in", material = "hardware cloth")
        assertEquals("Seal 2 in soffit with hardware cloth", ExclusionEstimate.lineDescription(point))
        val cleared = point.copy(description = "", descriptionManual = true)
        assertEquals("", ExclusionEstimate.lineDescription(cleared))
        val priced = ExclusionEstimate.pushToEstimate(JobPricing(exclusionPoints = listOf(cleared)))
        assertEquals("", priced.photoLineItems.single().description)
        val filled = ExclusionEstimate.pushToEstimate(JobPricing(exclusionPoints = listOf(point)))
        assertEquals("Seal 2 in soffit with hardware cloth", filled.photoLineItems.single().description)
        val edited = filled.copy(
            photoLineItems = listOf(filled.photoLineItems.single().copy(description = "Sir's line", unitPrice = 40.0))
        )
        val pulled = ExclusionEstimate.pullFromEstimate(edited)
        assertEquals("Sir's line", pulled.exclusionPoints.single().description)
        assertTrue(pulled.exclusionPoints.single().descriptionManual)
        val quote = PricingCalculator.compute(filled.copy(laborHours = 0.0, laborRate = 0.0, taxRatePercent = 0.0, exclusionPoints = listOf(point.copy(unitPrice = 25.0))).let { ExclusionEstimate.pushToEstimate(it.copy(photoLineItems = emptyList())) })
        assertEquals(25.0, quote.subtotal.effective, 0.0)
        val kept = PricingCalculator.pricingForEditor(
            JobPricing(exclusionPoints = listOf(cleared)),
            estimatedValue = 0.0
        )
        assertEquals("", kept.exclusionPoints.single().description)
        assertTrue(kept.exclusionPoints.single().descriptionManual)
    }

    @Test
    fun manualRouteOrderBeatsDistanceAndBuildsMapsUrl() {
        val day = "2026-10-02"
        val noon = LocalDate.parse(day).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val jobs = listOf(
            Job(id = "near", title = "Near", latitude = 41.44, longitude = -74.02, scheduledDate = noon, pricing = JobPricing(routeDay = day, routeIndex = 1)),
            Job(id = "far", title = "Far", latitude = 41.50, longitude = -74.20, scheduledDate = noon, pricing = JobPricing(routeDay = day, routeIndex = 0))
        )
        val stops = TodayRouteEngine.collect(jobs, emptyList(), day, zone)
        val ordered = TodayRouteEngine.order(stops, 41.44, -74.02)
        assertEquals("job:far", ordered.first().id)
        val byDistance = TodayRouteEngine.order(stops.map { it.copy(routeIndex = null) }, 41.44, -74.02)
        assertEquals("job:near", byDistance.first().id)
        val dragged = TodayRouteEngine.reorder(byDistance, 0, 1)
        assertEquals(0, dragged.first().routeIndex)
        assertEquals("job:far", dragged.first().id)
        val saved = TodayRouteEngine.applyOrder(jobs, dragged, day)
        assertEquals(0, saved.first { it.id == "far" }.pricing.routeIndex)
        val url = TodayRouteEngine.multiMapsUrl(dragged)
        assertTrue(url.startsWith("https://www.google.com/maps/dir/"))
        assertTrue(url.contains("waypoints="))
        assertTrue(TodayRouteEngine.singleMapsUrl(dragged.first()).contains("destination="))
        val cleared = TodayRouteEngine.clearManual(saved, day)
        assertNull(cleared.first { it.id == "far" }.pricing.routeIndex)
    }

    @Test
    fun onMyWayPrefillsEtaAndDoesNotInventOne() {
        val withEta = OnMyWay.message("Pat", "20 minutes")
        assertTrue(withEta.contains("ETA 20 minutes"))
        assertTrue(withEta.contains("Wildlife Whisperer"))
        val cleared = OnMyWay.message("Pat", "  ")
        assertFalse(cleared.contains("ETA"))
        assertTrue(cleared.contains("Hi Pat"))
        assertEquals("Hi there, this is Wildlife Whisperer. I'm on my way.", OnMyWay.message("", ""))
    }

    @Test
    fun repeatHistoryMatchesCustomerAndAddressWithoutDuplicating() {
        val current = Job(id = "now", customerId = "c1", address = "12 Oak St", customerName = "Pat")
        val byCustomer = Job(id = "old", customerId = "c1", address = "Other", confirmedSpecies = "Raccoon", status = JobStatus.PAID, estimatedValue = 80.0, createdAt = 20L)
        val byAddress = Job(id = "addr", customerId = "other", address = "12  oak st", type = "Squirrel", status = JobStatus.LEAD, estimatedValue = 40.0, createdAt = 30L)
        val rows = RepeatCustomerHistory.rows(current, listOf(current, byCustomer, byAddress, byCustomer))
        assertEquals(listOf("addr", "old"), rows.map { it.jobId })
        assertEquals("Raccoon", rows.first { it.jobId == "old" }.species)
        assertEquals("Completed", rows.first { it.jobId == "old" }.statusLabel)
        assertEquals(80.0, rows.first { it.jobId == "old" }.amount, 0.0)
        assertTrue(RepeatCustomerHistory.rows(Job(id = "x"), listOf(Job(id = "y"))).isEmpty())
    }

    @Test
    fun statusHintNeverOverridesALaterManualStatusAndSyncKeepsIt() {
        val lead = Job(status = JobStatus.LEAD, scheduledDate = 50L, pricing = JobPricing(laborHours = 2.0))
        assertEquals(JobStatus.SCHEDULED, JobStatusPipeline.suggest(lead))
        val closed = lead.copy(status = JobStatus.CLOSED, pricing = JobPricing(trapRecords = listOf(com.strobingn.wildlifefieldops.pricing.SyncedTrapRecord(id = "t"))))
        assertNull(JobStatusPipeline.suggest(closed))
        assertEquals(JobStatus.CLOSED, closed.status)
        val stamped = JobStatusPipeline.stamp(JobPricing(), JobStatus.TRAPPING)
        val job = Job(status = JobStatus.TRAPPING, pricing = stamped)
        val remote = job.toRemoteDto()
        assertEquals("In Progress", remote.status)
        assertTrue(remote.status in allowedRemote)
        JobStatus.entries.forEach { assertTrue(it.toRemoteStatus() in allowedRemote) }
        val pulled = remote.toLocal(existing = Job(status = JobStatus.LEAD))
        assertEquals(JobStatus.TRAPPING, pulled.status)
        assertEquals("TRAPPING", pulled.pricing.pipelineStatus)
        assertTrue(JobStatusPipeline.matches(JobStatus.PENDING, JobStatus.LEAD))
        assertEquals("Scheduled", JobStatusPipeline.label(JobStatus.PENDING))
        assertEquals("Completed", JobStatusPipeline.label(JobStatus.COMPLETED))
    }

    @Test
    fun fieldDataImportDedupesByIdAndKeepsNewerBlank() {
        val local = JobSnapshot(id = "job-1", title = "", notes = "", updatedAt = 50L, confirmedSpecies = "")
        val older = local.copy(title = "AI title", notes = "AI notes", updatedAt = 10L, confirmedSpecies = "raccoon")
        val newer = local.copy(title = "Sir's title", updatedAt = 80L)
        val merged = FieldDataCodec.mergeJobs(listOf(local), listOf(older, newer))
        assertEquals(1, merged.size)
        assertEquals("Sir's title", merged.single().title)
        assertEquals("", merged.single().confirmedSpecies)
        val tie = FieldDataCodec.mergeJobs(listOf(local.copy(notes = "")), listOf(older.copy(updatedAt = 50L, notes = "filled")))
        assertEquals("", tie.single().notes)
        val customers = FieldDataCodec.mergeCustomers(
            listOf(CustomerSnapshot(id = "c", phone = "", updatedAt = 5L)),
            listOf(CustomerSnapshot(id = "c", phone = "845", updatedAt = 9L), CustomerSnapshot(id = "d", phone = "", updatedAt = 1L))
        )
        assertEquals(2, customers.size)
        assertEquals("845", customers.first { it.id == "c" }.phone)
        val bundle = FieldDataBundle(
            jobs = listOf(Job(id = "j", title = "Attic", confirmedSpecies = "").toSnapshot()),
            settings = mapOf("company_name" to "Wildlife Whisperer LLC")
        )
        val bytes = FieldDataCodec.zipBytes(bundle)
        val read = FieldDataCodec.readBytes(bytes)
        assertEquals("", read.jobs.single().toJob().confirmedSpecies)
        assertEquals("Attic", read.jobs.single().title)
        val ids = FieldDataExchange.mergeById(
            listOf(FieldRecord("a", 1L, "local")),
            listOf(FieldRecord("a", 1L, "incoming"), FieldRecord("b", 2L, "new"))
        )
        assertEquals("local", ids.first { it.id == "a" }.payload)
        assertEquals(setOf("a", "b"), ids.map { it.id }.toSet())
    }
}
