package com.strobingn.wildlifefieldops.ai.field

data class RepairMaterials(
    val clothFeet: Double,
    val clothKind: String,
    val screws: Int,
    val flashingFeet: Double,
    val oneWays: Int,
    val chimneyCaps: Int,
    val laborHours: Double
)

object RepairCalc {
    fun forOpenings(
        openings: Int,
        widthIn: Double,
        heightIn: Double,
        species: String,
        location: String = "",
        linearFeetExtra: Double = 0.0
    ): RepairMaterials {
        val n = openings.coerceAtLeast(0)
        val w = widthIn.coerceAtLeast(0.0) + 4.0
        val h = heightIn.coerceAtLeast(0.0) + 4.0
        val perOpeningFt = ((w / 12.0) * (h / 12.0)).coerceAtLeast(0.75)
        val clothFeet = n * perOpeningFt + linearFeetExtra * 1.15
        val raccoon = species.contains("raccoon", true) || species.contains("coon", true)
        val clothKind = if (raccoon) "1/2 in 16-ga cloth" else "1/4 in 16-ga cloth"
        val screws = ((clothFeet * 12.0) / 5.0).toInt().coerceAtLeast(n * 8)
        val flashing = if (
            location.contains("roof", true) ||
            location.contains("ridge", true) ||
            location.contains("chimney", true)
        ) {
            (n * 2.0) + linearFeetExtra
        } else 0.0
        val oneWays = when {
            species.contains("bat", true) -> n.coerceAtLeast(1)
            species.contains("squirrel", true) -> 1
            else -> if (n > 0) 1 else 0
        }
        val caps = if (location.contains("chimney", true)) 1 else 0
        val extraHours = (if (raccoon) 0.5 else 0.0) + (if (caps == 1) 0.75 else 0.0)
        val hours = 1.0 + n * 0.75 + extraHours
        return RepairMaterials(
            clothFeet = kotlin.math.ceil(clothFeet * 10.0) / 10.0,
            clothKind = clothKind,
            screws = screws,
            flashingFeet = kotlin.math.ceil(flashing * 10.0) / 10.0,
            oneWays = oneWays,
            chimneyCaps = caps,
            laborHours = kotlin.math.round(hours * 4.0) / 4.0
        )
    }
}
