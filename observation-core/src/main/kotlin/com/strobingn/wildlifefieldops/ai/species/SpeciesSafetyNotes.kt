package com.strobingn.wildlifefieldops.ai.species

/**
 * On-device safety copy shown with a species suggestion.
 *
 * These notes are conservative New York / Northeast field-ops reminders, not a
 * permit determination. A suggestion is never operational until a technician
 * confirms it.
 */
object SpeciesSafetyNotes {

    const val GENERIC_VERIFY =
        "On-device ID is a suggestion only. Confirm the animal or plant on site before handling, exclusion, or reporting."

    const val NOT_OPERATIONAL =
        "Do not treat this ID as operational until a technician explicitly confirms it."

    data class SafetyCard(
        val label: String,
        val notes: List<String>,
        val handlingRisk: Risk = Risk.MODERATE,
    ) {
        enum class Risk { LOW, MODERATE, HIGH, PROTECTED }
    }

    fun forLabel(label: String): SafetyCard {
        val key = normalize(label)
        val known = CATALOG.entries.firstOrNull { (aliases, _) ->
            aliases.any { alias -> key == alias || key.contains(alias) || alias.contains(key) }
        }?.value
        val notes = buildList {
            add(GENERIC_VERIFY)
            add(NOT_OPERATIONAL)
            if (known != null) addAll(known.notes) else add(
                "Unknown or low-confidence taxon — photograph scale, habitat, and distinguishing marks and confirm before acting."
            )
        }
        return SafetyCard(
            label = label.trim().ifBlank { "unknown" },
            notes = notes.distinct(),
            handlingRisk = known?.handlingRisk ?: SafetyCard.Risk.MODERATE,
        )
    }

    fun normalize(label: String): String =
        label.trim().lowercase().replace('_', ' ').replace('-', ' ')

    private data class Entry(
        val notes: List<String>,
        val handlingRisk: SafetyCard.Risk,
    )

    private val CATALOG: Map<List<String>, Entry> = mapOf(
        listOf("raccoon", "procyon") to Entry(
            notes = listOf(
                "Rabies-vector species. Use gloves and eye protection; do not handle a sick or unusually tame animal.",
                "Check for dependent young in attics and chimneys before eviction or trapping.",
            ),
            handlingRisk = SafetyCard.Risk.HIGH,
        ),
        listOf("bat", "big brown bat", "little brown bat", "myotis", "chiroptera") to Entry(
            notes = listOf(
                "Rabies-vector and often protected. Never handle a bat with bare hands.",
                "Confirm maternity-season and exclusion timing before sealing roosts.",
            ),
            handlingRisk = SafetyCard.Risk.PROTECTED,
        ),
        listOf("skunk", "mephitis") to Entry(
            notes = listOf(
                "Rabies-vector species. Expect spray; keep pets and bystanders upwind.",
                "Look for kits under decks and sheds before setting a trap.",
            ),
            handlingRisk = SafetyCard.Risk.HIGH,
        ),
        listOf("squirrel", "gray squirrel", "grey squirrel", "flying squirrel", "chipmunk") to Entry(
            notes = listOf(
                "Chew risk on wiring and fascia. Confirm nest / young before one-way doors.",
                "Flying squirrels are nocturnal — a day inspection can miss occupancy.",
            ),
            handlingRisk = SafetyCard.Risk.MODERATE,
        ),
        listOf("groundhog", "woodchuck", "marmot") to Entry(
            notes = listOf(
                "Burrows can undermine foundations, walkways, and septic lines.",
                "Confirm occupied vs abandoned holes before backfill.",
            ),
            handlingRisk = SafetyCard.Risk.MODERATE,
        ),
        listOf("opossum", "possum", "didelphis") to Entry(
            notes = listOf(
                "Generally low rabies risk but will bite if cornered. Use a catch pole or cage trap.",
            ),
            handlingRisk = SafetyCard.Risk.MODERATE,
        ),
        listOf("norway rat", "roof rat", "rat", "house mouse", "mouse", "vole", "mole") to Entry(
            notes = listOf(
                "Do not dry-sweep droppings. Wet-clean and use a respirator in heavy infestations.",
                "Identify harborage and food sources; snap or bait programs need customer consent.",
            ),
            handlingRisk = SafetyCard.Risk.MODERATE,
        ),
        listOf("bird", "pigeon", "starling", "sparrow", "woodpecker", "owl", "canada goose", "goose", "seagull", "gull", "crow", "turkey") to Entry(
            notes = listOf(
                "Most native birds and active nests are protected. Confirm nest / egg status before exclusion.",
                "Pigeon and starling work still needs droppings PPE (histoplasmosis risk).",
            ),
            handlingRisk = SafetyCard.Risk.PROTECTED,
        ),
        listOf("snake", "garter") to Entry(
            notes = listOf(
                "Do not handle until venomous vs non-venomous is confirmed by a qualified person.",
            ),
            handlingRisk = SafetyCard.Risk.HIGH,
        ),
        listOf("fox", "red fox", "gray fox", "coyote", "black bear", "bear", "deer") to Entry(
            notes = listOf(
                "Large-mammal / rabies-vector encounter. Keep distance, secure pets, and follow DEC handling rules.",
            ),
            handlingRisk = SafetyCard.Risk.HIGH,
        ),
        listOf("beaver", "muskrat", "mink", "fisher") to Entry(
            notes = listOf(
                "Furbearer rules apply. Confirm season, permit, and dispatch / release method before acting.",
            ),
            handlingRisk = SafetyCard.Risk.HIGH,
        ),
        listOf("feral cat", "stray cat", "dog", "canine") to Entry(
            notes = listOf(
                "Domestic or feral companion animal — check for ID / TNR partners before treating as wildlife.",
            ),
            handlingRisk = SafetyCard.Risk.MODERATE,
        ),
    )
}
