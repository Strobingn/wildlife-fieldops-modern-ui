package com.strobingn.wildlifefieldops.ai.fieldops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationFieldExtractorTest {

    @Test
    fun labeledCustomerAddressAndPhoneLandInTheirOwnFields() {
        val f = DictationFieldExtractor.extract(
            "customer name is john smith address is 123 Main Street Albany phone 518 555 0142 " +
                "raccoon in the attic entering at the soffit"
        )
        assertEquals("John Smith", f.customerName)
        assertEquals("123 Main Street Albany", f.serviceAddress)
        assertEquals("518-555-0142", f.customerPhone)
        assertEquals("raccoon in the attic entering at the soffit", f.remainder)
    }

    @Test
    fun unlabeledNameAndStreetAreStillFound() {
        val f = DictationFieldExtractor.extract(
            "inspection for Jane Doe at 45 Oak Lane squirrels chewing at the fascia"
        )
        assertEquals("Jane Doe", f.customerName)
        assertEquals("45 Oak Lane", f.serviceAddress)
        assertFalse(f.remainder.contains("Oak Lane"))
        assertFalse(f.remainder.contains("Jane"))
        assertTrue(f.remainder.contains("squirrels chewing"))
    }

    @Test
    fun parenthesizedPhoneIsNormalized() {
        val f = DictationFieldExtractor.extract("my phone number is (518) 555-0142 and the bats are back")
        assertEquals("518-555-0142", f.customerPhone)
        assertFalse(f.remainder.contains("555"))
        assertTrue(f.remainder.contains("bats are back"))
    }

    @Test
    fun nothingIsInventedWhenNothingWasSpoken() {
        val said = "raccoon in the attic droppings and chewed insulation"
        val f = DictationFieldExtractor.extract(said)
        assertEquals("", f.customerName)
        assertEquals("", f.customerPhone)
        assertEquals("", f.serviceAddress)
        assertEquals("", f.severity)
        assertEquals(said, f.remainder)
        assertEquals(DictatedFields(), DictationFieldExtractor.extract("   "))
    }

    @Test
    fun ownerWordInsideASentenceIsNotTreatedAsAName() {
        val f = DictationFieldExtractor.extract("the owner says he heard noises at night")
        assertEquals("", f.customerName)
    }

    @Test
    fun spokenSeverityIsCapturedButAbsentSeverityStaysBlank() {
        assertEquals("HIGH", DictationFieldExtractor.extract("severity is high squirrels in the soffit").severity)
        assertEquals("MODERATE", DictationFieldExtractor.extract("severity medium").severity)
        assertEquals("CRITICAL", DictationFieldExtractor.extract("critical damage to the wiring").severity)
        assertEquals("", DictationFieldExtractor.extract("squirrels in the soffit").severity)
    }

    @Test
    fun explicitSeverityPhraseIsRemovedButDamageDescriptionStays() {
        val labeled = DictationFieldExtractor.extract("severity is high squirrels in the soffit")
        assertEquals("squirrels in the soffit", labeled.remainder)
        val descriptive = DictationFieldExtractor.extract("moderate damage to the fascia")
        assertEquals("moderate damage to the fascia", descriptive.remainder)
    }

    @Test
    fun scrubSentencesDropsOnlyTheSentencesThatRepeatContactDetails() {
        val notes = "Customer lives at 123 Main Street Albany. Gate code is on the fence. Call before arriving."
        val scrubbed = DictationFieldExtractor.scrubSentences(notes, listOf("123 Main Street Albany", "", "Pat"))
        assertEquals("Gate code is on the fence. Call before arriving.", scrubbed)
        assertEquals("keep this", DictationFieldExtractor.scrubSentences("keep this", emptyList()))
    }

    @Test
    fun remainderKeepsContactDetailsOutOfOfflineFindings() {
        val fields = DictationFieldExtractor.extract(
            "customer name is john smith address is 123 Main Street Albany " +
                "raccoon in the attic entering at the soffit droppings and chewed insulation"
        )
        val draft = InspectionNarrativeEngine.fromDictation(
            transcript = fields.remainder,
            customerName = fields.customerName,
            jobAddress = fields.serviceAddress
        )
        assertFalse(draft.findings.contains("123 Main"))
        assertFalse(draft.findings.contains("John Smith", ignoreCase = true))
        assertEquals("", draft.notes)
        assertEquals("raccoon", draft.speciesIdentified)
        assertTrue(draft.entryPoints.contains("soffit"))
        assertTrue(draft.damageAssessment.contains("droppings"))
    }
}
