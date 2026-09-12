package com.strobingn.wildlifefieldops.ai.camera

import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.objects.DetectedObject
import kotlin.math.max

/**
 * FieldOps-tuned evidence detector on top of ML Kit labels/objects.
 * Maps generic vision tags → wildlife species + entry/damage evidence with scores.
 * Deterministic; does not own capture accept/reject.
 */
data class WildlifeEvidenceHit(
    val kind: Kind,
    val label: String,
    val score: Float,
    val source: String
) {
    enum class Kind { SPECIES, ENTRY, DAMAGE, ACTIVITY, OTHER }
}

data class WildlifeEvidenceResult(
    val species: List<WildlifeEvidenceHit>,
    val entries: List<WildlifeEvidenceHit>,
    val damage: List<WildlifeEvidenceHit>,
    val activity: List<WildlifeEvidenceHit>,
    val topSummary: String,
    val rawLabels: List<String>
) {
    val primarySpecies: String? get() = species.maxByOrNull { it.score }?.label
    val primaryEntry: String? get() = entries.maxByOrNull { it.score }?.label
}

object WildlifeEvidenceDetector {
    private val speciesLexicon = mapOf(
        "raccoon" to listOf("raccoon", "procyon"),
        "bat" to listOf("bat", "chiroptera"),
        "squirrel" to listOf("squirrel", "chipmunk"),
        "opossum" to listOf("opossum", "possum"),
        "rat" to listOf("rat", "rodent"),
        "mouse" to listOf("mouse"),
        "snake" to listOf("snake", "reptile"),
        "bird" to listOf("bird", "pigeon", "sparrow", "starling", "owl"),
        "skunk" to listOf("skunk"),
        "groundhog" to listOf("groundhog", "woodchuck", "marmot")
    )

    private val entryLexicon = mapOf(
        "soffit / fascia gap" to listOf("roof", "eaves", "soffit", "fascia"),
        "roof vent" to listOf("vent", "turbine"),
        "chimney" to listOf("chimney", "fireplace"),
        "dryer / utility vent" to listOf("dryer", "exhaust"),
        "foundation gap" to listOf("foundation", "crawlspace", "crawl space"),
        "attic opening" to listOf("attic", "gable"),
        "hole / chew opening" to listOf("hole", "opening", "gap")
    )

    private val damageLexicon = mapOf(
        "chew / gnaw damage" to listOf("chew", "gnaw", "bite"),
        "claw / scratch" to listOf("scratch", "claw"),
        "insulation disturbance" to listOf("insulation", "fiberglass"),
        "droppings / guano" to listOf("dropping", "guano", "feces", "poop", "stain"),
        "nesting material" to listOf("nest", "bedding", "leaves")
    )

    private val activityLexicon = mapOf(
        "tracks / trail" to listOf("track", "trail", "footprint"),
        "live animal" to listOf("animal", "mammal", "wildlife"),
        "noise / activity cue" to listOf("movement")
    )

    fun detect(
        labels: List<ImageLabel>,
        objects: List<DetectedObject> = emptyList(),
        checklistHint: String? = null
    ): WildlifeEvidenceResult {
        val raw = mutableListOf<String>()
        val scored = mutableListOf<WildlifeEvidenceHit>()

        fun consider(text: String, confidence: Float, source: String) {
            val t = text.lowercase().trim()
            if (t.isEmpty()) return
            raw += t
            matchLexicon(speciesLexicon, t, confidence, source, WildlifeEvidenceHit.Kind.SPECIES)?.let { scored += it }
            matchLexicon(entryLexicon, t, confidence, source, WildlifeEvidenceHit.Kind.ENTRY)?.let { scored += it }
            matchLexicon(damageLexicon, t, confidence, source, WildlifeEvidenceHit.Kind.DAMAGE)?.let { scored += it }
            matchLexicon(activityLexicon, t, confidence, source, WildlifeEvidenceHit.Kind.ACTIVITY)?.let { scored += it }
        }

        labels.forEach { consider(it.text, it.confidence, "label") }
        objects.forEach { obj ->
            obj.labels.forEach { consider(it.text, it.confidence, "object") }
        }

        // Checklist bias: boost matching category slightly for ranking
        val hint = checklistHint?.lowercase().orEmpty()
        val adjusted = scored.map { hit ->
            val boost = when {
                hint.contains("entry") && hit.kind == WildlifeEvidenceHit.Kind.ENTRY -> 0.08f
                hint.contains("drop") && hit.label.contains("dropping") -> 0.1f
                hint.contains("chew") && hit.label.contains("chew") -> 0.1f
                hint.contains("nest") && hit.label.contains("nest") -> 0.1f
                hint.contains("animal") && hit.kind == WildlifeEvidenceHit.Kind.SPECIES -> 0.08f
                else -> 0f
            }
            hit.copy(score = (hit.score + boost).coerceAtMost(1f))
        }

        fun top(kind: WildlifeEvidenceHit.Kind) =
            adjusted.filter { it.kind == kind }
                .groupBy { it.label }
                .map { (_, hits) -> hits.maxBy { it.score } }
                .sortedByDescending { it.score }
                .take(4)

        val species = top(WildlifeEvidenceHit.Kind.SPECIES)
        val entries = top(WildlifeEvidenceHit.Kind.ENTRY)
        val damage = top(WildlifeEvidenceHit.Kind.DAMAGE)
        val activity = top(WildlifeEvidenceHit.Kind.ACTIVITY)

        val summary = buildString {
            species.firstOrNull()?.let { append(it.label) }
            entries.firstOrNull()?.let {
                if (isNotEmpty()) append(" · ")
                append(it.label)
            }
            damage.firstOrNull()?.let {
                if (isNotEmpty()) append(" · ")
                append(it.label)
            }
            if (isEmpty()) append("No FieldOps evidence tags yet")
        }

        return WildlifeEvidenceResult(
            species = species,
            entries = entries,
            damage = damage,
            activity = activity,
            topSummary = summary,
            rawLabels = raw.distinct().take(12)
        )
    }

    private fun matchLexicon(
        lexicon: Map<String, List<String>>,
        text: String,
        confidence: Float,
        source: String,
        kind: WildlifeEvidenceHit.Kind
    ): WildlifeEvidenceHit? {
        var best: WildlifeEvidenceHit? = null
        for ((label, keys) in lexicon) {
            if (keys.any { text.contains(it) }) {
                val score = max(confidence, 0.45f)
                if (best == null || score > best.score) {
                    best = WildlifeEvidenceHit(kind, label, score, source)
                }
            }
        }
        return best
    }
}
