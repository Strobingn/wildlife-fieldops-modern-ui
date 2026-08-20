package com.strobingn.wildlifefieldops.ai.field

import java.time.LocalDate
import java.time.Month

data class SeasonFlag(
    val title: String,
    val level: Level,
    val detail: String
) {
    enum class Level { HOLD, WATCH, OK }
}

object SeasonRules {
    fun flags(date: LocalDate = LocalDate.now()): List<SeasonFlag> {
        val month = date.month
        val day = date.dayOfMonth
        val out = mutableListOf<SeasonFlag>()

        val batMaternity = month == Month.MAY || month == Month.JUNE || month == Month.JULY ||
            (month == Month.AUGUST && day <= 15)
        val lateBatYoung = month == Month.AUGUST && day in 16..31

        if (batMaternity) {
            out += SeasonFlag(
                "Bat maternity window",
                SeasonFlag.Level.HOLD,
                "NY: do not exclude occupied roosts May 1–Aug 15. Watch dusk fly-out. Sell inspect + monitor now."
            )
        } else if (lateBatYoung) {
            out += SeasonFlag(
                "Late bat young possible",
                SeasonFlag.Level.WATCH,
                "Maternity is closing. Confirm no chirping/pups before one-ways."
            )
        } else {
            out += SeasonFlag(
                "Bat exclusion open",
                SeasonFlag.Level.OK,
                "Outside maternity. One-ways on confirmed exits, then permanent seal after 3–5 quiet nights."
            )
        }

        if (month in setOf(Month.APRIL, Month.MAY, Month.JUNE, Month.JULY)) {
            out += SeasonFlag(
                "Raccoon young in structures",
                SeasonFlag.Level.HOLD,
                "Check for kits before any seal. Eviction / wait, then replace/repair the opening."
            )
        }

        if (month in setOf(Month.FEBRUARY, Month.MARCH, Month.AUGUST, Month.SEPTEMBER)) {
            out += SeasonFlag(
                "Squirrel litter window",
                SeasonFlag.Level.WATCH,
                "Spring and late-summer litters. Confirm occupancy before the final seal."
            )
        }

        if (month in setOf(Month.MAY, Month.JUNE)) {
            out += SeasonFlag(
                "Skunk kits",
                SeasonFlag.Level.WATCH,
                "Kits under decks/sheds. Do not seal a live den."
            )
        }

        if (month in setOf(Month.JUNE, Month.JULY, Month.AUGUST)) {
            out += SeasonFlag(
                "Heat trap checks",
                SeasonFlag.Level.HOLD,
                "12-hour trap checks in heat. No raccoon trap in full sun overnight."
            )
        }

        if (month in setOf(Month.APRIL, Month.MAY, Month.JUNE, Month.JULY)) {
            out += SeasonFlag(
                "MBTA nest timing",
                SeasonFlag.Level.HOLD,
                "Active migratory nests stay. Sell exclusion now, install after fledging."
            )
        }

        return out
    }

    fun canExcludeBats(date: LocalDate = LocalDate.now()): Boolean {
        val month = date.month
        val day = date.dayOfMonth
        return !(month == Month.MAY || month == Month.JUNE || month == Month.JULY ||
            (month == Month.AUGUST && day <= 15))
    }
}
