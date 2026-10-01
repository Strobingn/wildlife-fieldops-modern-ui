package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.PricingJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JobFieldOpsCodecTest {

    @Test
    fun mergeWritesExtrasIntoPricingJson() {
        val job = Job(
            title = "Cornwall raccoon",
            confirmedSpecies = "raccoon",
            legalNotes = "Gloves on. DEC log tonight.",
            nextStep = "Check the deck trap",
            nextStepDueAt = 50L,
            nextStepSource = "heuristic",
            aiRuntime = "heuristic"
        )
        val saved = JobFieldOpsCodec.mergeForSave(job)
        assertEquals("raccoon", saved.pricing.confirmedSpecies)
        assertEquals("Check the deck trap", saved.pricing.nextStep)
        val roundTrip = PricingJson.decode(PricingJson.encode(saved.pricing))
        assertEquals("raccoon", roundTrip.confirmedSpecies)
        assertEquals(50L, roundTrip.nextStepDueAt)
    }

    @Test
    fun applyFromPricingFillsBlankJobColumns() {
        val pricing = JobPricing(
            confirmedSpecies = "bat",
            nextStep = "One-way at the ridge",
            photoLineItems = listOf(InvoiceLineItem(description = "Cone", quantity = 1.0, unitPrice = 85.0))
        )
        val job = JobFieldOpsCodec.applyFromPricing(Job(pricing = pricing))
        assertEquals("bat", job.confirmedSpecies)
        assertEquals("One-way at the ridge", job.nextStep)
        assertEquals(1, job.pricing.photoLineItems.size)
    }

    @Test
    fun typedJobColumnsWinOverPricing() {
        val job = Job(
            confirmedSpecies = "Sir confirmed skunk",
            pricing = JobPricing(confirmedSpecies = "AI said raccoon")
        )
        val saved = JobFieldOpsCodec.mergeForSave(job)
        assertEquals("Sir confirmed skunk", saved.confirmedSpecies)
        assertEquals("Sir confirmed skunk", saved.pricing.confirmedSpecies)
        assertTrue(saved.pricing.isEmptyWorksheet())
    }
}
