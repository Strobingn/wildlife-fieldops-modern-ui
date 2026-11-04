package com.strobingn.wildlifefieldops.ai.species

/**
 * Parses the vision JSON from the Supabase `ai-assistant` function:
 * {"species","confidence","alternates","evidence_notes"}.
 * Species outside the field list become "Unsure - pick species".
 */
object CloudSpeciesIdParser {

    fun parse(raw: String): SpeciesIdDecision? {
        val json = extractObject(raw) ?: return null
        val withoutAlternates = stripArray(json, "alternates")
        val speciesRaw = stringField(withoutAlternates, "species") ?: return null
        val confidence = (floatField(withoutAlternates, "confidence") ?: 0f).coerceIn(0f, 1f)
        val notes = unescape(stringField(json, "evidence_notes").orEmpty())
        val alternates = parseAlternates(json)
            .distinctBy { it.species }
            .take(6)
        val canonical = WildlifeSpeciesCatalog.canonicalize(speciesRaw)
        if (canonical == null || canonical == WildlifeSpeciesCatalog.UNKNOWN) {
            val mentioned = if (canonical == null && speciesRaw.isNotBlank()) {
                "Model said \"${speciesRaw.trim()}\", which is outside the field list."
            } else {
                "Model could not tell the species."
            }
            return SpeciesIdDecision(
                displayLabel = WildlifeSpeciesCatalog.UNSURE,
                species = "",
                confidence = confidence.coerceAtMost(0.4f).coerceAtLeast(0.22f),
                confident = false,
                alternates = alternates,
                evidenceNotes = notes.ifBlank { mentioned },
            )
        }
        val kept = alternates.filter { !it.species.equals(canonical.display, ignoreCase = true) }
        return SpeciesIdDecision(
            displayLabel = canonical.display,
            species = canonical.display,
            confidence = confidence,
            confident = confidence >= 0.55f,
            alternates = kept,
            evidenceNotes = notes,
        )
    }

    private fun parseAlternates(json: String): List<SpeciesAlternate> {
        val array = arrayBody(json, "alternates") ?: return emptyList()
        val objects = Regex(""""species"\s*:\s*"((?:\\.|[^"\\])*)"\s*,\s*"confidence"\s*:\s*(-?[0-9.]+)""")
            .findAll(array)
            .mapNotNull { match ->
                val name = WildlifeSpeciesCatalog.canonicalize(unescape(match.groupValues[1])) ?: return@mapNotNull null
                if (name == WildlifeSpeciesCatalog.UNKNOWN) return@mapNotNull null
                SpeciesAlternate(name.display, match.groupValues[2].toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f)
            }
            .toList()
        if (objects.isNotEmpty()) return objects
        return Regex(""""((?:\\.|[^"\\])*)"""").findAll(array).mapNotNull { match ->
            val name = WildlifeSpeciesCatalog.canonicalize(unescape(match.groupValues[1])) ?: return@mapNotNull null
            if (name == WildlifeSpeciesCatalog.UNKNOWN) return@mapNotNull null
            SpeciesAlternate(name.display, 0.2f)
        }.toList()
    }

    private fun extractObject(raw: String): String? {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return cleaned.substring(start, end + 1)
    }

    private fun stripArray(json: String, field: String): String {
        val pattern = Regex(""""$field"\s*:\s*\[.*?\]""", RegexOption.DOT_MATCHES_ALL)
        return pattern.replace(json, "")
    }

    private fun arrayBody(json: String, field: String): String? {
        val pattern = Regex(""""$field"\s*:\s*(\[.*?])""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun stringField(json: String, field: String): String? {
        val match = Regex(""""$field"\s*:\s*"((?:\\.|[^"\\])*)"""").find(json) ?: return null
        return unescape(match.groupValues[1])
    }

    private fun floatField(json: String, field: String): Float? {
        val match = Regex(""""$field"\s*:\s*(-?[0-9]+(?:\.[0-9]+)?)""").find(json) ?: return null
        return match.groupValues[1].toFloatOrNull()
    }

    private fun unescape(value: String): String =
        value.replace("\\\"", "\"").replace("\\n", " ").replace("\\\\", "\\").trim()
}
