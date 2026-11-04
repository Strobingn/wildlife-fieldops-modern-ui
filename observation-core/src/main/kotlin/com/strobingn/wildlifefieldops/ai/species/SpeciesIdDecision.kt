package com.strobingn.wildlifefieldops.ai.species

data class LabelScore(val text: String, val confidence: Float)

data class SpeciesAlternate(val species: String, val confidence: Float)

/**
 * One identification result, before it is shown or confirmed.
 * [confident] is false for "Unsure - pick species" and for weak guesses.
 */
data class SpeciesIdDecision(
    val displayLabel: String,
    val species: String,
    val confidence: Float,
    val confident: Boolean,
    val alternates: List<SpeciesAlternate>,
    val evidenceNotes: String,
) {
    val isUnsure: Boolean get() = displayLabel == WildlifeSpeciesCatalog.UNSURE

    fun toSuggestion(backendTag: String): SpeciesSuggestion? {
        if (displayLabel.isBlank()) return null
        val primaryScore = if (confident) {
            confidence.coerceIn(0.55f, 1f)
        } else {
            confidence.coerceIn(0.22f, 0.49f)
        }
        val hits = LinkedHashMap<String, Float>()
        hits[displayLabel] = primaryScore
        alternates.forEach { alt ->
            if (alt.species.equals(displayLabel, ignoreCase = true)) return@forEach
            if (hits.containsKey(alt.species)) return@forEach
            val reported = alt.confidence
            if (!reported.isFinite() || reported <= 0f) return@forEach
            // Keep the chosen species first. A higher alternate is capped just under it
            // instead of being replaced with a rank-based score.
            val capped = if (reported >= primaryScore) {
                (primaryScore - 0.01f).coerceAtLeast(0.01f)
            } else {
                reported
            }
            hits[alt.species] = capped
        }
        val suggestion = SpeciesRecognition.suggest(hits, backendTag) ?: return null
        return suggestion.copy(evidenceNotes = evidenceNotes, confident = confident)
    }
}
