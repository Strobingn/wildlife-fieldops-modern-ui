package com.strobingn.wildlifefieldops.pricing

import com.strobingn.wildlifefieldops.ai.fieldops.toInvoice
import com.strobingn.wildlifefieldops.ai.fieldops.toSynced
import com.strobingn.wildlifefieldops.data.model.Invoice
import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import com.strobingn.wildlifefieldops.data.model.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EstimateInvoiceCarryTest {

    @Test
    fun estimateItemsAppearOnANewInvoiceForTheSameJob() {
        val pricing = JobPricing(
            laborHours = 3.0,
            laborRate = 95.0,
            materialsQty = 2.0,
            materialsPrice = 40.0,
            equipmentCost = 25.0,
            taxRatePercent = 8.125,
            taxRateManual = true,
            discountPercent = 10.0,
            notes = "Deck soffit exclusion",
            rationale = "Two entry points",
            totalOverride = 500.0,
            photoLineItems = listOf(
                InvoiceLineItem(description = "Soffit cone", quantity = 4.0, unit = "ea", unitPrice = 18.0)
            )
        )
        val job = Job(id = "job-1", title = "Cornwall raccoon", pricing = pricing, estimatedValue = 500.0)
        val plan = EstimateInvoiceCarry.resolveOpen(job.pricing, job.estimatedValue, saved = null)

        assertEquals(EstimateInvoiceCarry.OpenKind.ESTIMATE, plan.kind)
        val descriptions = plan.form.lineItems.map { it.description }
        assertEquals(
            listOf(
                "Soffit cone",
                "Labor / Trap Service",
                "Materials / Exclusion & Repairs",
                "Equipment"
            ),
            descriptions
        )
        val labor = plan.form.lineItems[1]
        assertEquals(3.0, labor.quantity, 0.0)
        assertEquals(95.0, labor.unitPrice, 0.0)
        assertEquals("hr", labor.unit)
        assertEquals(8.125, plan.form.taxRate, 0.0)
        assertTrue(plan.form.taxRateManual)
        assertEquals(10.0, plan.form.discountPercent, 0.0)
        assertEquals("Deck soffit exclusion\nTwo entry points", plan.form.notes)
        assertEquals(500.0, plan.form.totalOverride)
        assertFalse(plan.form.manuallyEdited)
        assertFalse(descriptions.contains("Wildlife Inspection"))
    }

    @Test
    fun typedInvoiceValuesAreNeverOverwritten() {
        val original = JobPricing(
            laborHours = 2.0,
            laborRate = 85.0,
            notes = "Original estimate"
        )
        val saved = invoiceFrom(original).copy(
            notes = "Sir typed this note",
            taxRate = 4.0,
            taxRateManual = true,
            discountPercent = 5.0,
            totalOverride = 222.0,
            lineItems = listOf(
                InvoiceLineItem(description = "Hand-priced exclusion", quantity = 1.0, unit = "ea", unitPrice = 222.0)
            ),
            manuallyEdited = true
        )
        val laterEstimate = JobPricing(
            laborHours = 9.0,
            laborRate = 120.0,
            notes = "AI rewrote the estimate",
            taxRatePercent = 8.125,
            totalOverride = 999.0
        )

        val plan = EstimateInvoiceCarry.resolveOpen(laterEstimate, estimatedValue = 999.0, saved = saved)

        assertEquals(EstimateInvoiceCarry.OpenKind.MANUAL, plan.kind)
        assertEquals("Sir typed this note", plan.form.notes)
        assertEquals(4.0, plan.form.taxRate, 0.0)
        assertTrue(plan.form.taxRateManual)
        assertEquals(5.0, plan.form.discountPercent, 0.0)
        assertEquals(222.0, plan.form.totalOverride)
        assertEquals(listOf("Hand-priced exclusion"), plan.form.lineItems.map { it.description })
        assertEquals(1, plan.form.lineItems.size)
    }

    @Test
    fun clearedFieldsStayBlankThroughReloadAndSync() {
        val estimate = JobPricing(
            laborHours = 2.0,
            laborRate = 85.0,
            notes = "Should not come back",
            taxRatePercent = 8.125
        )
        val cleared = Invoice(
            id = "inv-cleared",
            jobId = "job-1",
            invoiceNumber = "INV-1",
            lineItems = emptyList(),
            notes = "",
            terms = "",
            taxRate = 0.0,
            taxRateManual = true,
            discountPercent = 0.0,
            subtotalOverride = null,
            taxAmountOverride = 0.0,
            totalOverride = null,
            manuallyEdited = true
        )

        val reopened = EstimateInvoiceCarry.resolveOpen(estimate, estimatedValue = 170.0, saved = cleared)
        assertEquals(EstimateInvoiceCarry.OpenKind.MANUAL, reopened.kind)
        assertTrue(reopened.form.lineItems.isEmpty())
        assertEquals("", reopened.form.notes)
        assertEquals("", reopened.form.terms)
        assertEquals(0.0, reopened.form.taxRate, 0.0)
        assertTrue(reopened.form.taxRateManual)
        assertNull(reopened.form.totalOverride)
        assertEquals(0.0, reopened.form.taxAmountOverride)

        val job = Job(id = "job-1", customerName = "Ada", address = "1 Ridge Rd")
        val roundTrip = cleared.toSynced().toInvoice(job)
        assertTrue(roundTrip.lineItems.isEmpty())
        assertEquals("", roundTrip.notes)
        assertEquals("", roundTrip.terms)
        assertEquals(0.0, roundTrip.taxRate, 0.0)
        assertTrue(roundTrip.taxRateManual)
        assertEquals(0.0, roundTrip.taxAmountOverride)
        assertTrue(roundTrip.manuallyEdited)
        assertNull(roundTrip.totalOverride)

        val encoded = PricingJson.decode(
            PricingJson.encode(JobPricing(invoiceRecords = listOf(cleared.toSynced())))
        )
        val fromJson = encoded.invoiceRecords.single().toInvoice(job)
        assertTrue(fromJson.lineItems.isEmpty())
        assertEquals("", fromJson.notes)
        assertEquals(0.0, fromJson.taxRate, 0.0)
        assertTrue(fromJson.taxRateManual)
        assertTrue(fromJson.manuallyEdited)
    }

    @Test
    fun untouchedInvoiceFollowsTheLatestEstimate() {
        val first = JobPricing(laborHours = 1.0, laborRate = 80.0, notes = "First pass")
        val untouched = invoiceFrom(first).copy(manuallyEdited = false)
        val revised = JobPricing(laborHours = 4.0, laborRate = 90.0, notes = "Added the gable")

        val plan = EstimateInvoiceCarry.resolveOpen(revised, estimatedValue = 0.0, saved = untouched)

        assertEquals(EstimateInvoiceCarry.OpenKind.ESTIMATE, plan.kind)
        assertEquals(untouched.id, plan.existingId)
        assertEquals(listOf("Labor / Trap Service"), plan.form.lineItems.map { it.description })
        assertEquals(4.0, plan.form.lineItems.single().quantity, 0.0)
        assertEquals(90.0, plan.form.lineItems.single().unitPrice, 0.0)
        assertEquals("Added the gable", plan.form.notes)
        assertFalse(plan.form.manuallyEdited)
    }

    @Test
    fun copyFromEstimateFillsAddsOrReplacesAndCancelLeavesLines() {
        val estimateLines = EstimateInvoiceCarry.lineItems(
            JobPricing(
                laborHours = 2.0,
                laborRate = 85.0,
                materialsQty = 1.0,
                materialsPrice = 45.0
            )
        )
        assertFalse(EstimateInvoiceCarry.needsCopyConfirm(emptyList()))
        val filled = EstimateInvoiceCarry.applyCopy(emptyList(), estimateLines, EstimateInvoiceCarry.CopyChoice.REPLACE)
        assertEquals(listOf("Labor / Trap Service", "Materials / Exclusion & Repairs"), filled.map { it.description })

        val existing = listOf(
            InvoiceLineItem(description = "Hand-priced exclusion", quantity = 1.0, unit = "ea", unitPrice = 222.0),
            filled.first().copy(id = "already-there")
        )
        assertTrue(EstimateInvoiceCarry.needsCopyConfirm(existing))

        val cancelled = EstimateInvoiceCarry.applyCopy(existing, estimateLines, EstimateInvoiceCarry.CopyChoice.CANCEL)
        assertEquals(existing, cancelled)

        val added = EstimateInvoiceCarry.applyCopy(existing, estimateLines, EstimateInvoiceCarry.CopyChoice.ADD)
        assertEquals(
            listOf("Hand-priced exclusion", "Labor / Trap Service", "Materials / Exclusion & Repairs"),
            added.map { it.description }
        )
        assertEquals("already-there", added[1].id)

        val replaced = EstimateInvoiceCarry.applyCopy(existing, estimateLines, EstimateInvoiceCarry.CopyChoice.REPLACE)
        assertEquals(listOf("Labor / Trap Service", "Materials / Exclusion & Repairs"), replaced.map { it.description })
        assertTrue(replaced.none { it.id == "already-there" })
    }

    @Test
    fun manualRemoteDoesNotClobberANewerManualAndUntouchedDoesNotClobberManual() {
        val local = Invoice(
            id = "inv",
            notes = "kept",
            manuallyEdited = true,
            updatedAt = 50L,
            lineItems = listOf(InvoiceLineItem(description = "kept line", unitPrice = 10.0))
        )
        val untouchedRemote = local.toSynced().copy(manuallyEdited = false, updatedAt = 80L, notes = "estimate")
        assertFalse(EstimateInvoiceCarry.shouldReplaceWithRemote(local, untouchedRemote))

        val olderManual = local.toSynced().copy(updatedAt = 40L, notes = "older")
        assertFalse(EstimateInvoiceCarry.shouldReplaceWithRemote(local, olderManual))

        val newerManual = local.toSynced().copy(updatedAt = 90L, notes = "newer typed")
        assertTrue(EstimateInvoiceCarry.shouldReplaceWithRemote(local, newerManual))
    }

    private fun invoiceFrom(pricing: JobPricing): Invoice {
        val form = EstimateInvoiceCarry.formFromEstimate(pricing)
        return EstimateInvoiceCarry.buildInvoice(
            job = Job(id = "job-1", customerName = "Ada", address = "1 Ridge Rd"),
            form = form,
            existing = null,
            invoiceNumber = "INV-1",
            now = 10L,
            manuallyEdited = true
        )
    }
}
