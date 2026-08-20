package com.strobingn.wildlifefieldops.ai.field

data class QuoteLine(
    val name: String,
    val qty: Double,
    val unit: String,
    val unitPrice: Double
) {
    val total: Double get() = qty * unitPrice
}

enum class FieldAction { REMOVE, REPLACE_REPAIR, EXCLUSION }

data class FieldQuote(
    val species: String,
    val action: FieldAction,
    val location: String,
    val lines: List<QuoteLine>,
    val laborHours: Double,
    val laborRate: Double,
    val subtotal: Double,
    val tax: Double,
    val total: Double,
    val flags: List<String>,
    val workOrder: List<String>
) {
    fun asText(): String = buildString {
        appendLine("WILDLIFE WHISPERER — FIELD QUOTE")
        appendLine("${action.label()} · ${species.ifBlank { "species TBD" }} · ${location.ifBlank { "location TBD" }}")
        appendLine()
        lines.forEach { line ->
            appendLine("${line.name}  ${fmt(line.qty)} ${line.unit} x ${money(line.unitPrice)} = ${money(line.total)}")
        }
        appendLine()
        appendLine("Labor ${fmt(laborHours)} hr @ ${money(laborRate)}")
        appendLine("Subtotal ${money(subtotal)}")
        appendLine("Tax ${money(tax)}")
        appendLine("TOTAL ${money(total)}")
        if (flags.isNotEmpty()) {
            appendLine()
            appendLine("FLAGS")
            flags.forEach { appendLine("- $it") }
        }
        appendLine()
        appendLine("WORK ORDER")
        workOrder.forEachIndexed { i, step -> appendLine("${i + 1}. $step") }
    }

    companion object {
        fun money(v: Double): String = "$" + String.format("%.2f", v)
        fun fmt(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)
    }
}

fun FieldAction.label(): String = when (this) {
    FieldAction.REMOVE -> "Remove"
    FieldAction.REPLACE_REPAIR -> "Replace / Repair"
    FieldAction.EXCLUSION -> "Exclusion"
}

object QuoteBuilder {
    fun build(
        species: String,
        action: FieldAction,
        location: String,
        openings: Int,
        widthIn: Double,
        heightIn: Double,
        notes: String = ""
    ): FieldQuote {
        val mats = RepairCalc.forOpenings(openings, widthIn, heightIn, species, location)
        val lines = mutableListOf<QuoteLine>()
        val flags = mutableListOf<String>()
        SeasonRules.flags().forEach { flag ->
            if (flag.level != SeasonFlag.Level.OK) flags += "${flag.title}: ${flag.detail}"
        }
        if (species.contains("bat", true) && !SeasonRules.canExcludeBats()) {
            flags += "HOLD bat exclusion — maternity window. Inspect and monitor only."
        }

        lines += QuoteLine("Inspection / site walk", 1.0, "ea", PriceBook.inspection)
        lines += QuoteLine("Trip", 1.0, "ea", PriceBook.trip)

        when (action) {
            FieldAction.REMOVE -> {
                if (species.contains("bat", true)) {
                    flags += "Bats are exclusion-only. Do not trap/kill."
                } else {
                    lines += QuoteLine("Remove — live trap / eviction visit", 1.0, "ea", 185.0)
                }
            }
            FieldAction.REPLACE_REPAIR, FieldAction.EXCLUSION -> { /* materials below */ }
        }

        if (mats.clothFeet > 0 && action != FieldAction.REMOVE) {
            val rate = if (mats.clothKind.startsWith("1/2")) PriceBook.clothHalfInchPerFt else PriceBook.clothQuarterInchPerFt
            lines += QuoteLine("Replace/repair cloth (${mats.clothKind})", mats.clothFeet, "ft", rate)
            lines += QuoteLine("Mechanical fasteners", mats.screws.toDouble(), "ea", PriceBook.screwEach)
        }
        if (mats.flashingFeet > 0 && action != FieldAction.REMOVE) {
            lines += QuoteLine("Flashing for replace/repair", mats.flashingFeet, "ft", PriceBook.flashingPerFt)
        }
        if (mats.oneWays > 0 && action != FieldAction.REPLACE_REPAIR) {
            lines += QuoteLine("One-way device", mats.oneWays.toDouble(), "ea", PriceBook.oneWayDoor)
        }
        if (mats.chimneyCaps > 0) {
            lines += QuoteLine("Chimney cap", mats.chimneyCaps.toDouble(), "ea", PriceBook.chimneyCap)
        }

        val hours = when (action) {
            FieldAction.REMOVE -> 1.5
            FieldAction.REPLACE_REPAIR -> mats.laborHours
            FieldAction.EXCLUSION -> mats.laborHours + 0.75
        }
        lines += QuoteLine("Labor", hours, "hr", PriceBook.laborRate)

        val sub = lines.sumOf { it.total }
        val tax = sub * PriceBook.taxRate
        val work = workOrder(species, action, location, openings, flags, notes)

        return FieldQuote(
            species = species,
            action = action,
            location = location,
            lines = lines,
            laborHours = hours,
            laborRate = PriceBook.laborRate,
            subtotal = sub,
            tax = tax,
            total = sub + tax,
            flags = flags.distinct(),
            workOrder = work
        )
    }

    fun fromNotes(notes: String, speciesHint: String = "", snapshot: String = ""): FieldQuote {
        val blob = "$notes $speciesHint $snapshot"
        val signs = SignScorer.fromNotes(blob)
        val species = speciesHint.ifBlank { signs.firstOrNull()?.species.orEmpty() }
        val action = when {
            blob.contains("repair") || blob.contains("replace") || blob.contains("seal") -> FieldAction.REPLACE_REPAIR
            blob.contains("exclu") || blob.contains("one-way") || blob.contains("one way") -> FieldAction.EXCLUSION
            else -> FieldAction.REMOVE
        }
        val location = listOf("fascia", "gable", "ridge", "vent", "chimney", "soffit", "attic", "deck").firstOrNull {
            blob.contains(it, true)
        }.orEmpty()
        val openings = Regex("""(\d+)\s*(hole|opening|gap)s?""").find(blob.lowercase())?.groupValues?.get(1)?.toIntOrNull() ?: 1
        val width = Regex("""(\d+(\.\d+)?)\s*(in|\"|inch)""").find(blob.lowercase())?.groupValues?.get(1)?.toDoubleOrNull() ?: 4.0
        return build(species, action, location, openings, width, width)
    }

    private fun workOrder(
        species: String,
        action: FieldAction,
        location: String,
        openings: Int,
        flags: List<String>,
        notes: String
    ): List<String> {
        val steps = mutableListOf<String>()
        steps += "Photo every opening with a tape. ${openings.coerceAtLeast(1)} opening(s) at ${location.ifBlank { "structure" }}."
        if (flags.any { it.startsWith("HOLD") || it.contains("maternity") || it.contains("young") }) {
            steps += "STOP if occupied maternity / kits. Do not seal. Document and reschedule."
        }
        when (action) {
            FieldAction.REMOVE -> {
                steps += "Remove using live-trap or eviction only. No kill methods."
                steps += "Check traps every 12 hours in heat, otherwise within 24 hours."
                steps += "After the animal is out, book replace/repair of the same opening the same week."
            }
            FieldAction.REPLACE_REPAIR -> {
                steps += "Replace/repair with 16-ga hardware cloth, mechanically fastened. No foam-only."
                steps += "Overlap 2 in on every edge. Fasten every 4–6 in."
                steps += "If raccoon: 1/2 in cloth. If bat/squirrel: 1/4 in cloth."
            }
            FieldAction.EXCLUSION -> {
                steps += "Install one-way on the active hole. Seal every other hole the same visit."
                steps += "Wait 3–5 quiet nights, then remove the device and permanent-seal."
                steps += "Replace/repair the opening with cloth + fasteners, not foam."
            }
        }
        if (species.contains("bat", true)) {
            steps += "P100/N95 for guano. Do not dry-sweep."
        }
        if (species.contains("raccoon", true) || species.contains("skunk", true)) {
            steps += "Rabies vector — bite gloves, no bare-handed handling."
        }
        if (notes.isNotBlank()) steps += "Tech notes: ${notes.take(200)}"
        steps += "Customer gets this quote + photos before work starts."
        return steps
    }
}
