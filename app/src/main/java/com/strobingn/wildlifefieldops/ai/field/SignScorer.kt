package com.strobingn.wildlifefieldops.ai.field

data class SignHit(val species: String, val score: Int, val why: List<String>)

object SignScorer {
    fun score(
        grapeDroppings: Boolean = false,
        marbleDroppings: Boolean = false,
        riceDroppings: Boolean = false,
        holeInches: Double = 0.0,
        night: Boolean = false,
        day: Boolean = false,
        chirp: Boolean = false,
        latrine: Boolean = false,
        tornFascia: Boolean = false,
        location: String = ""
    ): List<SignHit> {
        val loc = location.lowercase()
        val hits = mutableListOf<SignHit>()

        fun add(name: String, pts: Int, why: MutableList<String>) {
            if (pts > 0) hits += SignHit(name, pts, why)
        }

        val raccoon = mutableListOf<String>()
        var r = 0
        if (grapeDroppings) { r += 40; raccoon += "grape-size droppings" }
        if (latrine) { r += 25; raccoon += "latrine" }
        if (tornFascia) { r += 20; raccoon += "torn fascia" }
        if (holeInches >= 3.5) { r += 20; raccoon += "${holeInches}\" opening" }
        if (night) { r += 10; raccoon += "night activity" }
        if (loc.contains("chimney") || loc.contains("soffit") || loc.contains("deck")) {
            r += 8; raccoon += loc
        }
        add("Raccoon", r, raccoon)

        val squirrel = mutableListOf<String>()
        var s = 0
        if (marbleDroppings) { s += 35; squirrel += "marble-size droppings" }
        if (day) { s += 25; squirrel += "daytime" }
        if (holeInches in 1.5..3.0) { s += 20; squirrel += "${holeInches}\" opening" }
        if (loc.contains("gable") || loc.contains("soffit") || loc.contains("ridge")) {
            s += 10; squirrel += loc
        }
        add("Squirrel", s, squirrel)

        val bat = mutableListOf<String>()
        var b = 0
        if (riceDroppings) { b += 40; bat += "rice-grain droppings / staining" }
        if (night) { b += 15; bat += "dusk/night" }
        if (chirp) { b += 25; bat += "chirping in structure" }
        if (loc.contains("ridge") || loc.contains("gable") || loc.contains("attic") || loc.contains("soffit")) {
            b += 12; bat += loc
        }
        if (holeInches > 0.0 && holeInches <= 1.25) { b += 15; bat += "small gap" }
        add("Bat", b, bat)

        val skunk = mutableListOf<String>()
        var k = 0
        if (grapeDroppings) { k += 10; skunk += "larger droppings" }
        if (night) { k += 10; skunk += "night" }
        if (loc.contains("deck") || loc.contains("shed") || loc.contains("crawl")) {
            k += 30; skunk += loc
        }
        add("Skunk", k, skunk)

        return hits.sortedByDescending { it.score }.filter { it.score >= 10 }
    }

    fun fromNotes(notes: String): List<SignHit> {
        val n = notes.lowercase()
        return score(
            grapeDroppings = listOf("grape", "latrine", "coon scat").any { it in n },
            marbleDroppings = listOf("marble", "walnut", "squirrel scat").any { it in n },
            riceDroppings = listOf("rice", "guano", "staining").any { it in n },
            holeInches = Regex("""(\d+(\.\d+)?)\s*(in|\"|inch)""").find(n)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0,
            night = listOf("night", "dusk", "dawn").any { it in n },
            day = listOf("daytime", "day time", "afternoon").any { it in n },
            chirp = listOf("chirp", "pups", "squeak").any { it in n },
            latrine = "latrine" in n,
            tornFascia = listOf("torn", "fascia", "ripped").any { it in n },
            location = listOf("fascia", "gable", "ridge", "vent", "chimney", "soffit", "attic", "deck", "crawl").firstOrNull { it in n }.orEmpty()
        )
    }
}
