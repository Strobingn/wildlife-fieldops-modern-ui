package com.strobingn.wildlifefieldops.ai.fieldops

data class WeatherAdviceInput(
    val condition: String = "",
    val description: String = "",
    val tempF: Int? = null,
    val windMph: Float? = null,
    val humidity: Int? = null,
    val species: String = "",
    val trapCount: Int = 0,
    val hasCatch: Boolean = false
)

data class WeatherAdviceDraft(
    val text: String,
    val skipUnsafe: Boolean = false,
    val source: AiRuntimeMode = AiRuntimeMode.HEURISTIC
)

object WeatherTrapAdvice {

    fun suggest(input: WeatherAdviceInput): WeatherAdviceDraft {
        val cond = (input.condition + " " + input.description).lowercase()
        val temp = input.tempF
        val wind = input.windMph ?: 0f
        val species = input.species.lowercase()
        val storm = listOf("thunder", "storm", "tornado", "hail").any { it in cond }
        val rain = listOf("rain", "drizzle", "shower").any { it in cond }
        val snow = listOf("snow", "sleet", "ice", "blizzard").any { it in cond }
        val extremeHeat = temp != null && temp >= 90
        val freeze = temp != null && temp <= 20
        val highWind = wind >= 25f

        val skipUnsafe = storm || (highWind && rain) || (temp != null && temp <= 5)

        val text = buildString {
            when {
                storm -> append(
                    "Skip the check until the storm passes — lightning and wind make cage work unsafe. Recheck as soon as it clears; soaked bait will need a refresh."
                )
                skipUnsafe && freeze -> append(
                    "Ice / extreme cold: do not stay in the weather longer than needed. Mid-day check only, swap frozen bait, and confirm the door still drops."
                )
                rain && highWind -> append(
                    "Rain plus gusts will wash bait and slam doors. Check after the front, dry the pan, and reset."
                )
                snow -> append(
                    "Snow will bury bait and tracks. Break a path to each set, refresh bait on a dry board, and log empty vs catch."
                )
                extremeHeat -> append(
                    "Heat stress: check early morning. Provide shade if a live animal is in the cage and move it promptly — do not leave a catch in full sun."
                )
                freeze -> append(
                    "Cold set: bait can freeze. Check mid-day, use a greasy/high-calorie bait, and confirm the trigger is not iced shut."
                )
                rain -> append(
                    "Wet weather: bait will dilute. Check on schedule (NY: within 24 hours), refresh bait, and keep the trap from sitting in a puddle."
                )
                highWind -> append(
                    "Gusty: stake or weight the cage so it cannot roll. Recheck the door latch after the wind."
                )
                else -> append(
                    "Fair conditions. Check every set trap within 24 hours, log empty or catch, and refresh bait if it is dried out."
                )
            }
            if (input.hasCatch) {
                append(" A catch is already logged — prioritize that pin first and complete the DEC row (species, disposition, method).")
            } else if (species.contains("raccoon") || species.contains("skunk") || species.contains("fox")) {
                append(" Rabies-vector job: PPE on, do not handle a live animal bare-handed.")
            }
            if (input.trapCount > 1) {
                append(" ${input.trapCount} traps are on this job — work them in one loop.")
            }
        }

        return WeatherAdviceDraft(text = text.trim(), skipUnsafe = skipUnsafe)
    }
}
