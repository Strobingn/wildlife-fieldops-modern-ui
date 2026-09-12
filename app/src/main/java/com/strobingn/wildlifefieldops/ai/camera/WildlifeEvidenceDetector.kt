package com.strobingn.wildlifefieldops.ai.camera

import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.objects.DetectedObject
import kotlin.math.max

/**
 * FieldOps-tuned evidence detector on top of ML Kit labels/objects (+ optional custom model hits).
 * Maps generic vision tags → NY wildlife species + entry/damage/equipment evidence with scores.
 * Deterministic; does not own capture accept/reject.
 */
data class WildlifeEvidenceHit(
    val kind: Kind,
    val label: String,
    val score: Float,
    val source: String
) {
    enum class Kind { SPECIES, ENTRY, DAMAGE, ACTIVITY, EQUIPMENT, OTHER }
}

data class WildlifeEvidenceResult(
    val species: List<WildlifeEvidenceHit>,
    val entries: List<WildlifeEvidenceHit>,
    val damage: List<WildlifeEvidenceHit>,
    val activity: List<WildlifeEvidenceHit>,
    val equipment: List<WildlifeEvidenceHit> = emptyList(),
    val topSummary: String,
    val rawLabels: List<String>
) {
    val primarySpecies: String? get() = species.maxByOrNull { it.score }?.label
    val primaryEntry: String? get() = entries.maxByOrNull { it.score }?.label
    val primaryEquipment: String? get() = equipment.maxByOrNull { it.score }?.label
}

object WildlifeEvidenceDetector {
    /**
     * New York / Northeast nuisance wildlife lexicon (Whisperer FieldOps).
     * Keys are display labels; values are ML Kit / free-text match tokens.
     */
    private val speciesLexicon = mapOf(
        "raccoon" to listOf("raccoon", "procyon"),
        "bat" to listOf("bat", "chiroptera", "flying fox"),
        "big brown bat" to listOf("big brown"),
        "little brown bat" to listOf("little brown", "myotis"),
        "gray squirrel" to listOf("gray squirrel", "grey squirrel", "squirrel"),
        "flying squirrel" to listOf("flying squirrel", "glider"),
        "chipmunk" to listOf("chipmunk"),
        "opossum" to listOf("opossum", "possum", "didelphis"),
        "norway rat" to listOf("norway rat", "brown rat", "rat"),
        "roof rat" to listOf("roof rat", "black rat"),
        "house mouse" to listOf("house mouse", "mouse"),
        "vole" to listOf("vole", "meadow mouse"),
        "mole" to listOf("mole"),
        "snake" to listOf("snake", "reptile", "garter"),
        "pigeon" to listOf("pigeon", "rock dove", "dove"),
        "starling" to listOf("starling"),
        "sparrow" to listOf("sparrow", "house sparrow"),
        "woodpecker" to listOf("woodpecker"),
        "owl" to listOf("owl"),
        "canada goose" to listOf("canada goose", "goose", "geese"),
        "seagull" to listOf("seagull", "gull"),
        "crow" to listOf("crow", "raven"),
        "turkey" to listOf("turkey", "wild turkey"),
        "bird" to listOf("bird", "avian"),
        "skunk" to listOf("skunk", "mephitis"),
        "groundhog" to listOf("groundhog", "woodchuck", "marmot"),
        "beaver" to listOf("beaver"),
        "muskrat" to listOf("muskrat"),
        "mink" to listOf("mink"),
        "fisher" to listOf("fisher"),
        "coyote" to listOf("coyote"),
        "fox" to listOf("fox", "red fox", "gray fox"),
        "deer" to listOf("deer", "white-tailed", "whitetail"),
        "black bear" to listOf("black bear", "bear"),
        "feral cat" to listOf("feral cat", "stray cat"),
        "dog" to listOf("dog", "canine")
    )

    private val entryLexicon = mapOf(
        "soffit / fascia gap" to listOf("roof", "eaves", "soffit", "fascia", "overhang"),
        "roof vent" to listOf("roof vent", "vent", "turbine", "ridge vent"),
        "gable vent" to listOf("gable"),
        "chimney" to listOf("chimney", "fireplace", "flue"),
        "dryer / utility vent" to listOf("dryer", "exhaust", "utility vent"),
        "foundation gap" to listOf("foundation", "crawlspace", "crawl space", "sill plate"),
        "attic opening" to listOf("attic", "hatch", "scuttle"),
        "hole / chew opening" to listOf("hole", "opening", "gap", "chew hole"),
        "ridge / valley" to listOf("ridge", "valley", "flashing"),
        "window / door gap" to listOf("window", "door frame", "threshold"),
        "deck / porch understructure" to listOf("deck", "porch", "skirt")
    )

    private val damageLexicon = mapOf(
        "chew / gnaw damage" to listOf("chew", "gnaw", "bite", "gnawed"),
        "claw / scratch" to listOf("scratch", "claw", "scuff"),
        "insulation disturbance" to listOf("insulation", "fiberglass", "cellulose"),
        "droppings / guano" to listOf("dropping", "guano", "feces", "poop", "stain", "urine"),
        "nesting material" to listOf("nest", "bedding", "leaves", "twig"),
        "wiring damage" to listOf("wire", "wiring", "cable chew"),
        "siding / trim damage" to listOf("siding", "trim damage", "fascia damage"),
        "water / odor stain" to listOf("stain", "odor", "ammonia", "urine stain")
    )

    private val activityLexicon = mapOf(
        "tracks / trail" to listOf("track", "trail", "footprint", "print"),
        "live animal" to listOf("animal", "mammal", "wildlife"),
        "noise / activity cue" to listOf("movement", "runway"),
        "feeding sign" to listOf("feeding", "cache", "shell", "corn cob")
    )

    /** Trap / field equipment recognition for Live Capture stamp notes. */
    private val equipmentLexicon = mapOf(
        "live cage trap" to listOf("cage trap", "live trap", "havahart", "tomahawk", "trap cage"),
        "one-way door / eviction" to listOf("one-way", "one way", "eviction door", "exclusion door"),
        "bat cone / valve" to listOf("bat cone", "bat valve", "check valve"),
        "snap trap" to listOf("snap trap", "rat trap", "mouse trap"),
        "glue board" to listOf("glue board", "glue trap", "sticky board"),
        "chimney cap" to listOf("chimney cap", "animal guard"),
        "exclusion netting" to listOf("netting", "bird net", "exclusion net"),
        "hardware cloth / screen" to listOf("hardware cloth", "wire mesh", "screening", "mesh"),
        "IR / trail camera" to listOf("trail camera", "game camera", "infrared camera", "ir camera"),
        "endoscope / inspection camera" to listOf("endoscope", "borescope", "inspection camera"),
        "ladder" to listOf("ladder", "extension ladder", "step ladder"),
        "respirator / PPE" to listOf("respirator", "n95", "ppe", "tyvek"),
        "attic staging / light" to listOf("work light", "headlamp", "staging")
    )

    fun detect(
        labels: List<ImageLabel>,
        objects: List<DetectedObject> = emptyList(),
        checklistHint: String? = null,
        customHits: List<WildlifeEvidenceHit> = emptyList()
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
            matchLexicon(equipmentLexicon, t, confidence, source, WildlifeEvidenceHit.Kind.EQUIPMENT)?.let { scored += it }
        }

        labels.forEach { consider(it.text, it.confidence, "label") }
        objects.forEach { obj ->
            obj.labels.forEach { consider(it.text, it.confidence, "object") }
        }
        scored += customHits

        val hint = checklistHint?.lowercase().orEmpty()
        val adjusted = scored.map { hit ->
            val boost = when {
                hint.contains("entry") && hit.kind == WildlifeEvidenceHit.Kind.ENTRY -> 0.08f
                hint.contains("drop") && hit.label.contains("dropping") -> 0.1f
                hint.contains("chew") && hit.label.contains("chew") -> 0.1f
                hint.contains("nest") && hit.label.contains("nest") -> 0.1f
                hint.contains("animal") && hit.kind == WildlifeEvidenceHit.Kind.SPECIES -> 0.08f
                hint.contains("trap") && hit.kind == WildlifeEvidenceHit.Kind.EQUIPMENT -> 0.1f
                hint.contains("equipment") && hit.kind == WildlifeEvidenceHit.Kind.EQUIPMENT -> 0.1f
                else -> 0f
            }
            hit.copy(score = (hit.score + boost).coerceAtMost(1f))
        }

        return fromHits(adjusted, raw)
    }

    /** Merge hits from multiple frames (walkthrough) into one ranked result. */
    fun aggregateHits(hits: List<WildlifeEvidenceHit>): WildlifeEvidenceResult =
        fromHits(hits, hits.map { it.label.lowercase() })

    private fun fromHits(
        adjusted: List<WildlifeEvidenceHit>,
        raw: List<String>
    ): WildlifeEvidenceResult {
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
        val equipment = top(WildlifeEvidenceHit.Kind.EQUIPMENT)

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
            equipment.firstOrNull()?.let {
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
            equipment = equipment,
            topSummary = summary,
            rawLabels = raw.distinct().take(16)
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
