package com.strobingn.wildlifefieldops.ai.fieldops

import java.util.Calendar

enum class SeasonalKind {
    SPRING_BATS,
    SPRING_SQUIRRELS,
    FALL_RODENTS,
    FALL_EXCLUSION
}

data class SeasonalDraft(
    val kind: SeasonalKind,
    val title: String,
    val notes: String,
    val dueAt: Long
)

object SeasonalReminder {
    fun suggest(species: String, now: Long = System.currentTimeMillis()): SeasonalDraft {
        val s = species.lowercase()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val year = cal.get(Calendar.YEAR)
        return when {
            s.contains("bat") -> draft(SeasonalKind.SPRING_BATS, year, Calendar.MARCH, 15, now,
                "Spring bat exclusion check",
                "Confirm vacancy / maternity timing before one-ways. Do not seal an occupied roost.")
            s.contains("squirrel") -> draft(SeasonalKind.SPRING_SQUIRRELS, year, Calendar.MARCH, 1, now,
                "Spring squirrel exclusion",
                "Walk soffits and vents; install one-ways if kits may be inside.")
            s.contains("raccoon") || s.contains("skunk") -> draft(SeasonalKind.FALL_EXCLUSION, year, Calendar.SEPTEMBER, 15, now,
                "Fall exclusion walk",
                "Check chimney caps and deck voids before winter denning.")
            else -> draft(SeasonalKind.FALL_RODENTS, year, Calendar.OCTOBER, 1, now,
                "Fall rodent proofing",
                "Seal gaps and set interior monitors before first freeze.")
        }
    }

    fun label(kind: SeasonalKind): String = when (kind) {
        SeasonalKind.SPRING_BATS -> "Spring bats"
        SeasonalKind.SPRING_SQUIRRELS -> "Spring squirrels"
        SeasonalKind.FALL_RODENTS -> "Fall rodents"
        SeasonalKind.FALL_EXCLUSION -> "Fall exclusion"
    }

    private fun draft(kind: SeasonalKind, year: Int, month: Int, day: Int, now: Long, title: String, notes: String): SeasonalDraft {
        var due = Calendar.getInstance().apply {
            set(year, month, day, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (due < now) {
            due = Calendar.getInstance().apply {
                set(year + 1, month, day, 9, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        return SeasonalDraft(kind, title, notes, due)
    }
}
