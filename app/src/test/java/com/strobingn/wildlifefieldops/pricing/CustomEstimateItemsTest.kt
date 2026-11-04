package com.strobingn.wildlifefieldops.pricing

import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.util.DocumentKind
import com.strobingn.wildlifefieldops.util.DocumentText
import com.strobingn.wildlifefieldops.util.StandardDocuments
import com.strobingn.wildlifefieldops.util.StandardJobPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomEstimateItemsTest {

    @Test
    fun parserReadsDollarAndBarePricesFromOneNote() {
        val items = CustomItemNoteParser.parse("gutter cleaning $250, gutter guards 400")
        assertEquals(listOf("gutter cleaning", "gutter guards"), items.map { it.name })
        assertEquals(250.0, items[0].price)
        assertEquals(400.0, items[1].price)
        assertEquals(650.0, items.sumOf { it.lineTotal() }, 0.0)
    }

    @Test
    fun parserReadsAColonDollarAmountWithCommas() {
        val items = CustomItemNoteParser.parse("Gutter Cleaning: $1,250.50")
        assertEquals("Gutter Cleaning", items.single().name)
        assertEquals(1250.50, items.single().price)
    }

    @Test
    fun parserLeavesANamedServiceUnpricedWhenNoAmountIsWritten() {
        val items = CustomItemNoteParser.parse("Please add soffit screen")
        assertEquals("soffit screen", items.single().name)
        assertNull(items.single().price)
        assertEquals(0.0, items.single().lineTotal(), 0.0)
        assertTrue(items.single().needsPrice())
    }

    @Test
    fun parserDoesNotInventItemsFromANarrativeSentence() {
        assertTrue(CustomItemNoteParser.parse("Customer has squirrels in the attic").isEmpty())
    }

    @Test
    fun unpricedMentionUsesRememberedPriceAndDoesNotDuplicate() {
        val remembered = listOf(RememberedCustomItem(name = "Gutter cleaning", price = 250.0))
        val first = CustomEstimateItems.resolve(
            existing = emptyList(),
            aiItems = emptyList(),
            noteText = "Please add gutter cleaning",
            remembered = remembered
        )
        assertEquals("gutter cleaning", first.single().name)
        assertEquals(250.0, first.single().price)

        val again = CustomEstimateItems.resolve(
            existing = first,
            aiItems = listOf(CustomEstimateItem(name = "gutter cleaning", price = 999.0)),
            noteText = "gutter cleaning $999",
            remembered = remembered
        )
        assertEquals(1, again.size)
        assertEquals(250.0, again.single().price)
    }

    @Test
    fun dedupeMatchesNormalizedNamesAndKeepsATypedPrice() {
        val existing = listOf(CustomEstimateItem(id = "kept", name = "Gutter  cleaning", price = 200.0))
        val resolved = CustomEstimateItems.resolve(
            existing = existing,
            aiItems = listOf(
                CustomEstimateItem(name = "GUTTER CLEANING", price = 250.0),
                CustomEstimateItem(name = "Gutter guards", description = "full house", price = 400.0)
            ),
            noteText = "gutter cleaning $250, gutter guards 400"
        )
        assertEquals(listOf("Gutter  cleaning", "gutter guards"), resolved.map { it.name })
        assertEquals("kept", resolved[0].id)
        assertEquals(200.0, resolved[0].price)
        assertEquals(400.0, resolved[1].price)
        assertEquals("full house", resolved[1].description)
    }

    @Test
    fun blankPriceIsNotFilledWithAGuessWhenNothingIsRemembered() {
        val resolved = CustomEstimateItems.resolve(
            existing = emptyList(),
            aiItems = listOf(CustomEstimateItem(name = "Gutter guards", price = null)),
            noteText = "add gutter guards",
            remembered = emptyList()
        )
        assertEquals("gutter guards", resolved.single().name)
        assertNull(resolved.single().price)
    }

    @Test
    fun mixedBuiltInAndCustomLinesSumPricesThenBronxTax() {
        val pricing = JobPricing(
            laborHours = 2.0,
            laborRate = 85.0,
            materialsQty = 1.0,
            materialsPrice = 40.0,
            equipmentCost = 0.0,
            permitCost = 0.0,
            disposalCost = 0.0,
            mileage = 0.0,
            taxRatePercent = 8.875,
            customItems = listOf(
                CustomEstimateItem(name = "Gutter cleaning", price = 250.0),
                CustomEstimateItem(name = "Gutter guards", price = 400.0),
                CustomEstimateItem(name = "Soffit screen", price = null)
            )
        )
        val result = PricingCalculator.compute(pricing)
        assertEquals(170.0, result.laborTotal.effective, 0.0)
        assertEquals(40.0, result.materialsTotal.effective, 0.0)
        assertEquals(860.0, result.subtotal.effective, 0.0)
        assertEquals(76.33, result.taxAmount.effective, 0.0)
        assertEquals(936.33, result.total.effective, 0.0)

        val lines = EstimateInvoiceCarry.lineItems(pricing, result)
        assertEquals(
            listOf("Gutter cleaning", "Gutter guards", "Soffit screen", "Labor / Trap Service", "Materials / Exclusion & Repairs"),
            lines.map { it.description }
        )
        assertTrue(lines.take(3).all { it.priceOnly })
        assertEquals(0.0, lines[2].effectiveTotal(), 0.0)
        val invoiced = PricingCalculator.computeInvoice(
            InvoicePricingInputs(
                lineItems = lines,
                taxRatePercent = pricing.taxRatePercent,
                discountPercent = 0.0
            )
        )
        assertEquals(860.0, invoiced.subtotal.effective, 0.0)
        assertEquals(76.33, invoiced.taxAmount.effective, 0.0)
        assertEquals(936.33, invoiced.total.effective, 0.0)
    }

    @Test
    fun customRowsPrintInTheFeeTableWithoutChangingLabor() {
        val lines = listOf(
            InvoiceLineItem(
                id = "labor",
                description = "Labor / Trap Service",
                quantity = 2.0,
                unit = "hr",
                unitPrice = 85.0,
                total = 170.0
            ),
            InvoiceLineItem(
                id = "gutter",
                description = "Gutter cleaning",
                quantity = 0.0,
                unit = "",
                unitPrice = 250.0,
                total = 250.0,
                priceOnly = true
            ),
            InvoiceLineItem(
                id = "guards",
                description = "Gutter guards",
                quantity = 0.0,
                unit = "",
                unitPrice = 0.0,
                total = 0.0,
                priceOnly = true,
                priceMissing = true
            )
        )
        val rows = StandardDocuments.mapFeeRows(lines)
        assertEquals("Labor / Trap Service Fee" to 170.0, rows[0])
        assertEquals(5, rows.indexOfFirst { it.first == "Gutter cleaning" })
        assertEquals(250.0, rows[5].second)
        assertEquals("Gutter guards" to null, rows[6])
        assertTrue(rows.none { it.first.contains("Gutter") && it.first.startsWith("Other") })

        val printed = StandardDocuments.feeTableRows(lines)
        assertEquals(listOf("Labor / Trap Service Fee", "$170.00"), printed[0])
        assertEquals(listOf("Gutter cleaning", "$250.00"), printed[5])
        assertEquals(listOf("Gutter guards", DocumentText.EM_DASH), printed[6])

        val document = StandardDocuments.build(
            DocumentKind.ESTIMATE,
            StandardJobPacket(
                job = Job(title = "Gutters"),
                lineItems = lines,
                subtotal = 420.0,
                taxRatePercent = 8.875,
                taxAmount = 37.28,
                total = 457.28
            )
        )
        val lineRows = document.blocks.first { it.title == "LINE ITEMS" }.table!!.rows
        val gutter = lineRows.first { it[0] == "Gutter cleaning" }
        assertEquals(listOf("Gutter cleaning", "", "", "", "$250.00"), gutter)
        assertEquals("2", lineRows.first { it[0] == "Labor / Trap Service" }[1])
    }

    @Test
    fun oldPricingJsonStillReadsAndCustomItemsRoundTrip() {
        val legacy = PricingJson.decode("""{"laborHours":2.0}""")
        assertTrue(legacy.customItems.isEmpty())
        assertTrue(PricingJson.decode("{}").isEmptyWorksheet())
        val saved = JobPricing(
            customItems = listOf(CustomEstimateItem(id = "a", name = "Gutter guards", description = "aluminum", price = 400.0))
        )
        val decoded = PricingJson.decode(PricingJson.encode(saved))
        assertEquals("Gutter guards", decoded.customItems.single().name)
        assertEquals("aluminum", decoded.customItems.single().description)
        assertEquals(400.0, decoded.customItems.single().price)
        assertTrue(!decoded.isEmptyWorksheet())
    }
}
