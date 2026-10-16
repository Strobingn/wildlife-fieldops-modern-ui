package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import com.strobingn.wildlifefieldops.pricing.effectiveTotal

data class EstimateLineContext(
    val species: String = "",
    val jobType: String = "",
    val notes: String = "",
    val entryPoints: String = "",
    val damage: String = "",
    val photoTags: List<String> = emptyList(),
    val photoNotes: List<String> = emptyList()
)

/**
 * Catalog-backed estimate lines for a one-man NY wildlife shop.
 * Prices are starting points Sir can change before the quote locks.
 */
object EstimateLineSuggester {

    fun suggest(context: EstimateLineContext): List<InvoiceLineItem> {
        val blob = listOf(
            context.species,
            context.jobType,
            context.notes,
            context.entryPoints,
            context.damage,
            context.photoTags.joinToString(" "),
            context.photoNotes.joinToString(" ")
        ).joinToString(" ").lowercase()

        // "bat" and "rat" are substrings of bath, combat, rate, separate, operation...
        // so those two match whole words only.
        val words = blob.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
        val hasBat = "bat" in words || "bats" in words
        val hasRat = "rat" in words || "rats" in words || "mice" in words

        val lines = mutableListOf<InvoiceLineItem>()
        lines += line("Site inspection & activity confirmation", 1.0, "ea", 125.0)

        val species = context.species.ifBlank { context.jobType }
        when {
            hasBat -> {
                lines += line("Bat one-way exclusion cone / valve", 1.0, "ea", 85.0)
                lines += line("Vent / ridge-cap screening (hardware cloth)", 8.0, "lf", 18.0)
                lines += line("Guano cleanup / drop cloth containment", 1.0, "ea", 175.0)
            }
            blob.contains("raccoon") -> {
                lines += line("Live cage trap set & check (raccoon)", 1.0, "ea", 95.0)
                lines += line("Chimney cap / damper repair", 1.0, "ea", 220.0)
                lines += line("Roof / fascia flashing patch", 4.0, "lf", 28.0)
            }
            blob.contains("squirrel") -> {
                lines += line("Squirrel one-way door", 1.0, "ea", 65.0)
                lines += line("Soffit / fascia wood repair", 6.0, "lf", 22.0)
                lines += line("Attic vent screening", 2.0, "ea", 45.0)
            }
            blob.contains("skunk") -> {
                lines += line("Skunk cage trap & bait", 1.0, "ea", 90.0)
                lines += line("Under-deck exclusion skirt (buried cloth)", 16.0, "lf", 16.0)
            }
            blob.contains("groundhog") || blob.contains("woodchuck") -> {
                lines += line("Groundhog trap set", 1.0, "ea", 90.0)
                lines += line("Burrow fill + hardware-cloth apron", 12.0, "lf", 14.0)
            }
            hasRat || blob.contains("mouse") || blob.contains("rodent") -> {
                lines += line("Rodent snap stations (customer-approved)", 6.0, "ea", 12.0)
                lines += line("Gap seal (steel wool + metal)", 10.0, "ea", 8.0)
            }
            else -> {
                lines += line("Humane removal / trap set — ${species.ifBlank { "wildlife" }}", 1.0, "ea", 95.0)
                lines += line("Exclusion materials (hardware cloth / flashing)", 8.0, "lf", 18.0)
            }
        }

        if (blob.contains("soffit") || blob.contains("fascia")) {
            addUnique(lines, line("Soffit / fascia close-up (1x8 + screen)", 4.0, "lf", 24.0))
        }
        if (blob.contains("dryer")) {
            addUnique(lines, line("Dryer-vent wildlife guard", 1.0, "ea", 35.0))
        }
        if (blob.contains("chimney")) {
            addUnique(lines, line("Chimney cap install", 1.0, "ea", 240.0))
        }
        if (blob.contains("insulation") || blob.contains("guano") || blob.contains("droppings")) {
            addUnique(lines, line("Contaminated insulation bag-out", 1.0, "ea", 250.0))
        }
        if (blob.contains("wiring") || blob.contains("chew")) {
            addUnique(lines, line("Chew-damage wood / screen patch", 3.0, "lf", 20.0))
        }

        return lines
    }

    fun merge(
        existing: List<InvoiceLineItem>,
        suggested: List<InvoiceLineItem>,
        replace: Boolean
    ): List<InvoiceLineItem> {
        if (replace || existing.isEmpty()) return suggested
        val have = existing.map { normalize(it.description) }.toSet()
        return existing + suggested.filter { normalize(it.description) !in have }
    }

    fun applyAcceptedToMaterials(currentMaterials: Double, lines: List<InvoiceLineItem>): Double {
        val extra = lines.sumOf { it.effectiveTotal() }
        return if (extra > 0.0) extra else currentMaterials
    }

    private fun line(description: String, qty: Double, unit: String, price: Double) = InvoiceLineItem(
        description = description,
        quantity = qty,
        unit = unit,
        unitPrice = price,
        total = qty * price
    )

    private fun addUnique(into: MutableList<InvoiceLineItem>, item: InvoiceLineItem) {
        val key = normalize(item.description)
        if (into.none { normalize(it.description) == key }) into += item
    }

    private fun normalize(value: String): String =
        value.trim().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
}
