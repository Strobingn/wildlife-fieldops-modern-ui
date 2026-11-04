package com.strobingn.wildlifefieldops.ai.species

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WildlifeSpeciesIdTest {

    @Test
    fun raccoonPhoto_dogMammalCarnivore_isNotConfidentDog() {
        val bytes = resourceBytes("raccoon.jpg")
        assertTrue(bytes.size > 1000)
        assertEquals(0xFF, bytes[0].toInt() and 0xFF)
        assertEquals(0xD8, bytes[1].toInt() and 0xFF)
        val decision = GenericLabelSpeciesMapper.map(labelsFrom("raccoon-labels.json"))
        assertNotNull(decision)
        assertEquals(WildlifeSpeciesCatalog.UNSURE, decision!!.displayLabel)
        assertFalse(decision.confident)
        assertNotEquals("Dog", decision.species)
        assertNotEquals("Dog", decision.displayLabel)
        val suggestion = decision.toSuggestion("mlkit")
        assertNotNull(suggestion)
        assertEquals(WildlifeSpeciesCatalog.UNSURE, suggestion!!.primaryLabel)
        assertFalse(suggestion.confident)
        assertNotEquals("Dog", suggestion.primaryLabel)
        assertTrue(suggestion.alternatives.any { it.label == "Raccoon" })
        assertTrue(suggestion.alternatives.any { it.label == "Dog" })
        assertTrue(suggestion.evidenceNotes.contains("raccoon", ignoreCase = true))
    }

    @Test
    fun skunkPhoto_dogAndMammal_staysSkunk() {
        assertJpeg("skunk.jpg")
        val decision = GenericLabelSpeciesMapper.map(labelsFrom("skunk-labels.json"))!!
        assertEquals("Skunk", decision.species)
        assertNotEquals("Dog", decision.displayLabel)
        assertFalse(decision.confident && decision.species == "Dog")
    }

    @Test
    fun squirrelPhoto_genericSquirrel_asksWhichSquirrel() {
        assertJpeg("squirrel.jpg")
        val decision = GenericLabelSpeciesMapper.map(labelsFrom("squirrel-labels.json"))!!
        assertEquals(WildlifeSpeciesCatalog.UNSURE, decision.displayLabel)
        assertFalse(decision.confident)
        val names = decision.alternates.map { it.species }
        assertTrue(names.contains("Gray squirrel"))
        assertTrue(names.contains("Red squirrel"))
        assertTrue(names.contains("Flying squirrel"))
    }

    @Test
    fun opossumPhoto_explicitLabel_isOpossum() {
        assertJpeg("opossum.jpg")
        val decision = GenericLabelSpeciesMapper.map(labelsFrom("opossum-labels.json"))!!
        assertEquals("Opossum", decision.species)
        assertTrue(decision.confident)
        val suggestion = decision.toSuggestion("mlkit")!!
        assertEquals("Opossum", suggestion.primaryLabel)
        assertEquals("mlkit", suggestion.backendTag)
    }

    @Test
    fun petDogBreed_isDog() {
        val decision = GenericLabelSpeciesMapper.map(
            listOf(LabelScore("Dog", 0.91f), LabelScore("Dalmatian", 0.62f))
        )!!
        assertEquals("Dog", decision.species)
        assertTrue(decision.confident)
    }

    @Test
    fun petCueWithoutMammalAmbiguity_isDog() {
        val decision = GenericLabelSpeciesMapper.map(
            listOf(LabelScore("Dog", 0.8f), LabelScore("Pet", 0.7f))
        )!!
        assertEquals("Dog", decision.species)
        assertTrue(decision.confident)
    }

    @Test
    fun bareDog_isUnsure() {
        val decision = GenericLabelSpeciesMapper.map(listOf(LabelScore("Dog", 0.93f)))!!
        assertEquals(WildlifeSpeciesCatalog.UNSURE, decision.displayLabel)
        assertFalse(decision.confident)
    }

    @Test
    fun dogPlusFur_isUnsure() {
        val decision = GenericLabelSpeciesMapper.map(
            listOf(LabelScore("Dog", 0.88f), LabelScore("Fur", 0.6f))
        )!!
        assertEquals(WildlifeSpeciesCatalog.UNSURE, decision.displayLabel)
        assertFalse(decision.toSuggestion("mlkit")!!.confident)
    }

    @Test
    fun hotDogFood_isNotAPet() {
        assertNull(GenericLabelSpeciesMapper.map(listOf(LabelScore("Hot dog", 0.95f))))
        assertTrue(GenericLabelSpeciesMapper.blocksConfidentDog(listOf("Hot dog")))
    }

    @Test
    fun graySquirrelAndFlyingFox_mapToFieldSpecies() {
        val gray = GenericLabelSpeciesMapper.map(listOf(LabelScore("Eastern gray squirrel", 0.7f)))!!
        assertEquals("Gray squirrel", gray.species)
        assertTrue(gray.confident)
        assertTrue(gray.alternates.any { it.species == "Red squirrel" })
        val bat = GenericLabelSpeciesMapper.map(listOf(LabelScore("Flying fox", 0.8f)))!!
        assertEquals("Bat", bat.species)
        assertNotEquals("Fox", bat.species)
    }

    @Test
    fun cloudJson_raccoon_parsesToRaccoon() {
        val raw = """
            ```json
            {"species":"Raccoon","confidence":0.91,"alternates":[{"species":"Opossum","confidence":0.06},{"species":"Skunk","confidence":0.02}],"evidence_notes":"Black mask and ringed tail"}
            ```
        """.trimIndent()
        val decision = CloudSpeciesIdParser.parse(raw)!!
        assertEquals("Raccoon", decision.species)
        assertEquals(0.91f, decision.confidence, 0.001f)
        assertTrue(decision.confident)
        assertEquals("Black mask and ringed tail", decision.evidenceNotes)
        assertEquals(listOf("Opossum", "Skunk"), decision.alternates.map { it.species })
        val suggestion = decision.toSuggestion("grok")!!
        assertEquals("Raccoon", suggestion.primaryLabel)
        assertEquals("grok", suggestion.backendTag)
        assertTrue(suggestion.confident)
        assertEquals(91, suggestion.confidencePercent)
        assertEquals(0.06f, suggestion.alternatives.first { it.label == "Opossum" }.weight, 0.001f)
        assertEquals(0.02f, suggestion.alternatives.first { it.label == "Skunk" }.weight, 0.001f)
    }

    @Test
    fun lexiconRewrite_bareSquirrel_isNotConfidentGraySquirrel() {
        val decision = GenericLabelSpeciesMapper.map(
            listOf(
                LabelScore("squirrel", 0.82f),
                LabelScore("gray squirrel", 0.82f),
            )
        )!!
        assertEquals(WildlifeSpeciesCatalog.UNSURE, decision.displayLabel)
        assertFalse(decision.confident)
        assertNotEquals("Gray squirrel", decision.species)
        assertTrue(decision.alternates.any { it.species == "Gray squirrel" })
    }

    @Test
    fun lexiconRewrite_bareGoose_isNotConfidentCanadaGoose() {
        val decision = GenericLabelSpeciesMapper.map(
            listOf(
                LabelScore("goose", 0.77f),
                LabelScore("canada goose", 0.77f),
            )
        )!!
        assertEquals(WildlifeSpeciesCatalog.UNSURE, decision.displayLabel)
        assertFalse(decision.confident)
        assertNotEquals("Canada goose", decision.species)
        assertTrue(decision.alternates.any { it.species == "Canada goose" })
    }

    @Test
    fun specificSquirrelAndCanadaGoose_staySpecific() {
        val gray = GenericLabelSpeciesMapper.map(listOf(LabelScore("eastern gray squirrel", 0.8f)))!!
        assertEquals("Gray squirrel", gray.species)
        assertTrue(gray.confident)
        val goose = GenericLabelSpeciesMapper.map(listOf(LabelScore("Canada goose", 0.8f)))!!
        assertEquals("Canada goose", goose.species)
        assertTrue(goose.confident)
    }

    @Test
    fun cloudJson_outsideList_isUnsure() {
        val decision = CloudSpeciesIdParser.parse(
            """{"species":"Wolf","confidence":0.8,"alternates":["Coyote","Dog"],"evidence_notes":"Large canine"}"""
        )!!
        assertEquals(WildlifeSpeciesCatalog.UNSURE, decision.displayLabel)
        assertFalse(decision.confident)
        assertNotEquals("Dog", decision.species)
        assertTrue(decision.alternates.any { it.species == "Coyote" })
    }

    @Test
    fun cloudJson_unknown_isUnsure() {
        val decision = CloudSpeciesIdParser.parse(
            """{"species":"Unknown","confidence":0.2,"alternates":[],"evidence_notes":"Too dark"}"""
        )!!
        assertEquals(WildlifeSpeciesCatalog.UNSURE, decision.displayLabel)
        assertFalse(decision.confident)
        assertTrue(decision.evidenceNotes.contains("dark", ignoreCase = true))
    }

    @Test
    fun cloudJson_easternCottontail_staysOnTheFieldList() {
        val decision = CloudSpeciesIdParser.parse(
            """{"species":"Eastern cottontail","confidence":0.8,"alternates":[],"evidence_notes":"White tail, brown coat"}"""
        )!!
        assertEquals("Eastern cottontail", decision.species)
        assertTrue(decision.confident)
    }

    @Test
    fun referenceLibraryLine_namesTheSpeciesAndCount() {
        assertEquals(
            "Matched to reference library: Raccoon (4 similar photos)",
            ReferenceLibraryLine.format(" Raccoon ", 4),
        )
        assertNull(ReferenceLibraryLine.format("", 4))
        assertNull(ReferenceLibraryLine.format("Raccoon", 0))
    }

    @Test
    fun promptListsFieldSpeciesAndDogRule() {
        val prompt = WildlifeSpeciesCatalog.visionSystemPrompt()
        assertTrue(prompt.contains("Raccoon"))
        assertTrue(prompt.contains("Canada goose"))
        assertTrue(prompt.contains("Hornets"))
        assertTrue(prompt.contains("Eastern cottontail"))
        assertTrue(prompt.contains("Do not call a raccoon a dog"))
        assertTrue(prompt.contains("evidence_notes"))
        assertFalse(prompt.contains("XAI_API_KEY"))
    }

    private fun assertJpeg(name: String) {
        val bytes = resourceBytes(name)
        assertTrue(bytes.size > 1000)
        assertEquals(0xFF, bytes[0].toInt() and 0xFF)
        assertEquals(0xD8, bytes[1].toInt() and 0xFF)
    }

    private fun resourceBytes(name: String): ByteArray =
        javaClass.getResourceAsStream("/wildlife-id/$name")!!.use { it.readBytes() }

    private fun labelsFrom(name: String): List<LabelScore> {
        val json = javaClass.getResourceAsStream("/wildlife-id/$name")!!.bufferedReader().readText()
        return Regex(""""text"\s*:\s*"([^"]+)"\s*,\s*"confidence"\s*:\s*([0-9.]+)""")
            .findAll(json)
            .map { LabelScore(it.groupValues[1], it.groupValues[2].toFloat()) }
            .toList()
    }
}
