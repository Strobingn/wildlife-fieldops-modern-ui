package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.PricingJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CustomerFieldOpsEnginesTest {

    @Test
    fun deductNeverGoesNegativeAndFlagsLowStock() {
        assertEquals(0.0, InventoryDeduct.nextOnHand(2.0, 5.0), 0.0)
        assertEquals(3.0, InventoryDeduct.nextOnHand(5.0, 2.0), 0.0)
        assertTrue(InventoryDeduct.isLow(2.0, 3.0))
        assertFalse(InventoryDeduct.isLow(4.0, 3.0))
        assertFalse(InventoryDeduct.isLow(0.0, 0.0))
        assertEquals(28.0, JobMaterialUsage(name = "Cone", quantity = 2.0, unitCost = 14.0).amount, 0.0)
        assertEquals(6.0, InventoryDeduct.restore(4.0, 2.0), 0.0)
        assertEquals(3.0, InventoryDeduct.adjust(5.0, 4.0, 6.0), 0.0)
    }

    @Test
    fun warrantyExpiryAndWindow() {
        val start = Calendar.getInstance().apply {
            set(2025, Calendar.OCTOBER, 1, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val plan = WarrantyTracker.fromJob(start, 12, "Exclusion")
        val exp = plan.expiresAt!!
        val justBefore = exp - 10 * 86_400_000L
        assertTrue(plan.isExpiring(justBefore, withinDays = 30))
        assertFalse(plan.isExpired(justBefore))
        assertTrue(plan.isExpired(exp + 1))
        assertEquals("Exclusion", plan.covered)
    }

    @Test
    fun seasonalSuggestUsesSpeciesAndRollsPastDates() {
        val bats = SeasonalReminder.suggest("Little brown bat", now = 1_700_000_000_000L)
        assertEquals(SeasonalKind.SPRING_BATS, bats.kind)
        assertTrue(bats.title.lowercase().contains("bat"))
        val rodents = SeasonalReminder.suggest("mouse", now = 1_700_000_000_000L)
        assertEquals(SeasonalKind.FALL_RODENTS, rodents.kind)
        assertTrue(rodents.dueAt > 1_700_000_000_000L)
    }

    @Test
    fun customerMessageDraftsAreEditableCopies() {
        val estimate = CustomerMessageDraft.draft(
            CustomerMessageKind.ESTIMATE,
            "Pat",
            "Attic raccoon",
            "12 Oak St",
            amount = 850.0
        )
        assertTrue(estimate.subject.contains("Attic raccoon"))
        assertTrue(estimate.body.contains("850"))
        val way = CustomerMessageDraft.draft(CustomerMessageKind.ON_THE_WAY, "Pat", "Attic raccoon", "12 Oak St")
        assertTrue(way.body.contains("on the way"))
    }

    @Test
    fun duplicateDetectsPhoneAddressNameAndMergesBlanks() {
        val a = Customer(id = "a", firstName = "Pat", lastName = "Lee", phone = "(845) 555-1212", address = "12 Oak St", city = "Cornwall", zipCode = "12518", email = "")
        val b = Customer(id = "b", firstName = "Pat", lastName = "Lee", phone = "8455551212", address = "12 Oak St", city = "Cornwall", zipCode = "12518", email = "pat@example.com")
        val pairs = DuplicateCustomer.findPairs(listOf(a, b))
        assertEquals(1, pairs.size)
        assertTrue(pairs.first().reasons.contains("same phone"))
        assertTrue(pairs.first().reasons.contains("same address"))
        val merged = DuplicateCustomer.mergeKeepLeft(a, b)
        assertEquals("pat@example.com", merged.email)
        assertEquals("a", merged.id)
        assertFalse(merged.isSynced)
    }

    @Test
    fun batch4ExtrasStayInsidePricingWorksheet() {
        val pricing = JobPricing(
            materialUsages = listOf(JobMaterialUsage(id = "u1", name = "One-way", quantity = 1.0, unitCost = 28.0)),
            warrantyStartAt = 10L,
            warrantyTermMonths = 18,
            warrantyCovered = "Exclusion",
            seasonalKind = "FALL_RODENTS",
            seasonalDueAt = 20L
        )
        val decoded = PricingJson.decode(PricingJson.encode(pricing))
        assertEquals("One-way", decoded.materialUsages.first().name)
        assertEquals(18, decoded.warrantyTermMonths)
        assertEquals("FALL_RODENTS", decoded.seasonalKind)
        assertTrue(decoded.isEmptyWorksheet())
    }
}
