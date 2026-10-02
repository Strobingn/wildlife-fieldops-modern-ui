package com.strobingn.wildlifefieldops.ai.fieldops

enum class FollowUpKind {
    WARRANTY,
    EXCLUSION,
    TRAP_PULL
}

data class FollowUpInput(
    val status: String = "",
    val species: String = "",
    val jobType: String = "",
    val notes: String = "",
    val completedAt: Long? = null,
    val hasActiveTraps: Boolean = false,
    val now: Long = System.currentTimeMillis()
)

data class FollowUpDraft(
    val kind: FollowUpKind,
    val title: String,
    val notes: String,
    val dueAt: Long
)

object FollowUpPlanner {

    private const val DAY = 86_400_000L

    fun suggest(input: FollowUpInput): FollowUpDraft {
        val species = (input.species + " " + input.jobType).lowercase()
        val notes = input.notes.lowercase()
        val base = input.completedAt ?: input.now

        return when {
            input.hasActiveTraps || notes.contains("trap") ||
                species.contains("raccoon") || species.contains("skunk") ||
                species.contains("groundhog") ->
                FollowUpDraft(
                    kind = FollowUpKind.TRAP_PULL,
                    title = "Trap pull / 24-hour check",
                    notes = "Pull or reset every set trap. Log catch or empty, refresh bait, and write the DEC row.",
                    dueAt = input.now + DAY
                )
            species.contains("bat") ->
                FollowUpDraft(
                    kind = FollowUpKind.EXCLUSION,
                    title = "Bat exclusion follow-up",
                    notes = "Confirm the one-ways stayed open the required nights, then permanently close entries after vacancy.",
                    dueAt = base + 7 * DAY
                )
            species.contains("squirrel") || notes.contains("exclusion") || notes.contains("one-way") ->
                FollowUpDraft(
                    kind = FollowUpKind.EXCLUSION,
                    title = "Exclusion follow-up",
                    notes = "Walk sealed entries, photograph flashing, and confirm no new chew.",
                    dueAt = base + 7 * DAY
                )
            else ->
                FollowUpDraft(
                    kind = FollowUpKind.WARRANTY,
                    title = "Warranty walk-through",
                    notes = "Re-inspect sealed work, photograph the job, and note any callback.",
                    dueAt = base + 14 * DAY
                )
        }
    }

    fun label(kind: FollowUpKind): String = when (kind) {
        FollowUpKind.WARRANTY -> "Warranty"
        FollowUpKind.EXCLUSION -> "Exclusion"
        FollowUpKind.TRAP_PULL -> "Trap pull"
    }

    fun parseKind(raw: String): FollowUpKind? = when (raw.trim().uppercase()) {
        "WARRANTY" -> FollowUpKind.WARRANTY
        "EXCLUSION" -> FollowUpKind.EXCLUSION
        "TRAP_PULL", "TRAP-PULL", "TRAPPULL" -> FollowUpKind.TRAP_PULL
        else -> null
    }
}
