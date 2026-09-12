package com.strobingn.wildlifefieldops.ai

import kotlin.math.ceil

/**
 * Maps Live Capture AR/manual entry-hole span + evidence tags → repair-scope autofill
 * for job notes / capture packs (Wildlife Whisperer FieldOps vocabulary).
 */
object RepairScopeHelper {

    data class RepairScopeDraft(
        val title: String,
        val summary: String,
        val materials: List<String>,
        val laborHoursEstimate: Double,
        val stampedNotes: String
    )

    fun fromMeasurement(
        inches: Float,
        entryTags: List<String> = emptyList(),
        species: List<String> = emptyList(),
        damageTags: List<String> = emptyList(),
        equipmentTags: List<String> = emptyList(),
        planeType: String = "manual",
        confidence: Float = 0.6f
    ): RepairScopeDraft {
        val span = inches.coerceIn(1f, 120f)
        val primaryEntry = entryTags.firstOrNull()?.lowercase().orEmpty()
        val primarySpecies = species.firstOrNull()?.lowercase().orEmpty()

        val (title, materials, labor) = when {
            primaryEntry.contains("chimney") -> Triple(
                "Chimney exclusion / cap",
                listOf("chimney cap", "stainless mesh", "fasteners", "sealant"),
                2.5 + (span / 24.0)
            )
            primaryEntry.contains("soffit") || primaryEntry.contains("fascia") -> Triple(
                "Soffit / fascia seal",
                listOf("1/4\" hardware cloth", "exterior screws", "sealant", "trim if needed"),
                1.5 + (span / 18.0)
            )
            primaryEntry.contains("roof vent") || primaryEntry.contains("vent") -> Triple(
                "Vent screen / exclusion",
                listOf("vent cover or custom screen", "fasteners", "sealant"),
                1.25 + (span / 20.0)
            )
            primaryEntry.contains("foundation") || primaryEntry.contains("crawl") -> Triple(
                "Foundation / crawl gap repair",
                listOf("hardware cloth or concrete patch", "anchors", "sealant"),
                2.0 + (span / 16.0)
            )
            primaryEntry.contains("dryer") || primaryEntry.contains("utility") -> Triple(
                "Utility / dryer vent exclusion",
                listOf("pest-proof vent cover", "clamps", "sealant"),
                1.0
            )
            primaryEntry.contains("attic") || primaryEntry.contains("gable") -> Triple(
                "Attic / gable opening repair",
                listOf("plywood or HC panel", "screws", "flashing / sealant"),
                2.0 + (span / 18.0)
            )
            span >= 24f -> Triple(
                "Large opening structural repair",
                listOf("framing lumber as needed", "hardware cloth", "siding/trim match", "sealant"),
                3.0 + (span / 12.0)
            )
            else -> Triple(
                "Entry-point sealing & repair",
                listOf("1/4\" hardware cloth", "exterior screws", "polyurethane sealant"),
                1.5 + (span / 20.0)
            )
        }

        val speciesNote = when {
            primarySpecies.contains("bat") -> "Bat-safe one-way / seasonal timing; no daytime attic seal with bats present."
            primarySpecies.contains("raccoon") -> "Confirm young/vacancy before permanent seal; consider eviction door first."
            primarySpecies.contains("squirrel") -> "Check for litter; one-way or trapping before final seal as needed."
            primarySpecies.contains("bird") || primarySpecies.contains("pigeon") -> "Netting / spike / exclusion per site; avoid nest disturbance where restricted."
            else -> null
        }

        val damageExtra = damageTags.take(3).joinToString().takeIf { it.isNotBlank() }
        val gear = equipmentTags.take(4).joinToString().takeIf { it.isNotBlank() }
        val laborRounded = (ceil(labor * 4.0) / 4.0).coerceIn(0.5, 8.0)

        val summary = buildString {
            append("Seal/repair \"")
            append(entryTags.firstOrNull() ?: "entry opening")
            append("\" spanning ~")
            append(String.format("%.0f", span))
            append(" in")
            if (primarySpecies.isNotBlank()) {
                append(" (species cue: ")
                append(species.first())
                append(")")
            }
            append(".")
            speciesNote?.let { append(" "); append(it) }
            damageExtra?.let { append(" Damage cues: "); append(it); append(".") }
            gear?.let { append(" Field gear seen: "); append(it); append(".") }
        }

        val stamped = buildString {
            append("REPAIR SCOPE (Wildlife Whisperer autofill)\n")
            append("Title: ").append(title).append('\n')
            append("Span: ").append(String.format("%.1f", span)).append(" in")
            append(" · plane=").append(planeType)
            append(" · conf=").append(String.format("%.0f", confidence * 100)).append("%\n")
            append("Materials: ").append(materials.joinToString(", ")).append('\n')
            append("Labor est: ~").append(laborRounded).append(" h\n")
            append(summary)
        }

        return RepairScopeDraft(
            title = title,
            summary = summary,
            materials = materials,
            laborHoursEstimate = laborRounded,
            stampedNotes = stamped
        )
    }

    fun fromMeasurementResult(
        result: ARMeasurementHelper.MeasurementResult,
        entryTags: List<String> = emptyList(),
        species: List<String> = emptyList(),
        damageTags: List<String> = emptyList(),
        equipmentTags: List<String> = emptyList()
    ): RepairScopeDraft = fromMeasurement(
        inches = result.inches,
        entryTags = entryTags,
        species = species,
        damageTags = damageTags,
        equipmentTags = equipmentTags,
        planeType = result.planeType,
        confidence = result.confidence
    )

    fun stampLine(draft: RepairScopeDraft): String =
        "${draft.title} · ~${String.format("%.0f", draft.laborHoursEstimate)}h · ${draft.materials.take(3).joinToString()}"
}
