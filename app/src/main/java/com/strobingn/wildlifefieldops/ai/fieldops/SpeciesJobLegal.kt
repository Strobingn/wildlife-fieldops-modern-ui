package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.ai.species.SpeciesSafetyNotes

data class SpeciesLegalCard(
    val species: String,
    val risk: String,
    val catalogNotes: List<String>,
    val decNotes: List<String>,
    val displayNotes: String
)

/**
 * Job-level NY legal / safety card. Catalog text is a starting draft;
 * [SpeciesLegalCard.displayNotes] is what Sir saved (or the catalog if blank).
 */
object SpeciesJobLegal {

    fun card(species: String, savedNotes: String = ""): SpeciesLegalCard {
        val safety = SpeciesSafetyNotes.forLabel(species.ifBlank { "unknown" })
        val dec = decNotes(species)
        val catalog = (safety.notes + dec).distinct()
        return SpeciesLegalCard(
            species = species.trim(),
            risk = safety.handlingRisk.name,
            catalogNotes = catalog,
            decNotes = dec,
            displayNotes = savedNotes.trim().ifBlank { catalog.joinToString("\n") }
        )
    }

    fun decNotes(species: String): List<String> {
        val key = species.trim().lowercase()
        val notes = mutableListOf<String>()
        notes += "NY DEC: keep a nuisance-wildlife record (species, date, location, method, disposition)."
        if (RABIES.any { key.contains(it) }) {
            notes += "Rabies-vector species. Bite/scratch = wash, seek medical care, and report. Do not release a sick animal into a neighborhood."
        }
        if (PROTECTED.any { key.contains(it) }) {
            notes += "Protected or season-restricted. Confirm maternity / nest status and DEC permit rules before eviction or lethal control."
        }
        if (key.contains("bat")) {
            notes += "Bat exclusions are timing-sensitive. Never seal an occupied maternity roost."
        }
        if (FURBEARER.any { key.contains(it) }) {
            notes += "Furbearer rules may apply (season, tag, dispatch). Confirm before setting a lethal set."
        }
        return notes
    }

    private val RABIES = listOf("raccoon", "skunk", "bat", "fox", "coyote", "woodchuck", "groundhog")
    private val PROTECTED = listOf("bat", "bird", "owl", "hawk", "eagle", "woodpecker", "goose", "myotis")
    private val FURBEARER = listOf("beaver", "mink", "fisher", "muskrat", "bobcat")
}
