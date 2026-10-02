package com.strobingn.wildlifefieldops.ai.fieldops

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ChecklistItemRecord(
    val id: String = UUID.randomUUID().toString(),
    val species: String = "",
    val label: String = "",
    val done: Boolean = false,
    val notes: String = ""
)

object SpeciesChecklist {
    fun templateFor(species: String): List<ChecklistItemRecord> {
        val key = normalize(species)
        val labels = when {
            key.contains("bat") -> bat
            key.contains("squirrel") -> squirrel
            key.contains("skunk") -> skunk
            key.contains("rodent") || key.contains("rat") || key.contains("mouse") -> rodent
            else -> raccoon
        }
        val name = displayName(key)
        return labels.map { ChecklistItemRecord(species = name, label = it) }
    }

    fun normalize(species: String): String = species.trim().lowercase()

    fun displayName(species: String): String = when {
        species.contains("bat") -> "bat"
        species.contains("squirrel") -> "squirrel"
        species.contains("skunk") -> "skunk"
        species.contains("rodent") || species.contains("rat") || species.contains("mouse") -> "rodent"
        else -> "raccoon"
    }

    fun completion(items: List<ChecklistItemRecord>): Pair<Int, Int> =
        items.count { it.done } to items.size

    /** Adds template rows whose labels are missing. Hand-added checks and notes stay. */
    fun mergeMissing(existing: List<ChecklistItemRecord>, species: String): List<ChecklistItemRecord> {
        val have = existing.map { it.label.trim().lowercase() }.toSet()
        val missing = templateFor(species).filter { it.label.trim().lowercase() !in have }
        return existing + missing
    }

    private val raccoon = listOf(
        "Inspect chimney, deck voids, and soffits",
        "Confirm no kits before eviction",
        "Set live cage on the travel path",
        "Cap chimney and screen vents",
        "Sanitize latrine / droppings"
    )
    private val bat = listOf(
        "Confirm vacancy / maternity timing",
        "Do not seal an occupied roost",
        "Install one-ways at primary exits",
        "Seal remaining gaps after 5–7 days",
        "Document DEC notes"
    )
    private val squirrel = listOf(
        "Walk soffits, vents, and ridge",
        "Check for kits before one-ways",
        "Install one-way at active hole",
        "Repair chew and screen vents",
        "Trim branch bridges"
    )
    private val skunk = listOf(
        "Check deck / shed skirt",
        "Confirm no kits in the den",
        "Set cage on the run",
        "Install skirt / exclusion after pull",
        "Note odor and soil disturbance"
    )
    private val rodent = listOf(
        "Find entry gaps at sill and vents",
        "Set interior monitors",
        "Seal 1/4-in gaps with steel",
        "Remove food attractants",
        "Schedule follow-up bait / snap check"
    )
}
