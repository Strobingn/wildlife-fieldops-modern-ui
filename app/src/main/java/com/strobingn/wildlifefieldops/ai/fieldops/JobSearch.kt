package com.strobingn.wildlifefieldops.ai.fieldops

data class JobSearchRow(
    val id: String,
    val label: String,
    val haystack: String
)

object JobSearch {
    /** The box holds only what was typed. The selected job is not part of the query. */
    fun fieldValue(typed: String): String = typed

    fun filter(rows: List<JobSearchRow>, typed: String): List<JobSearchRow> {
        val query = fieldValue(typed).trim()
        if (query.isEmpty()) return rows
        return rows.filter { it.haystack.contains(query, ignoreCase = true) }
    }

    fun selectionNote(selectedLabel: String, typed: String, matchCount: Int): String {
        val selected = if (selectedLabel.isBlank()) "No job selected" else "Selected: $selectedLabel"
        if (typed.isBlank()) return selected
        val matches = "$matchCount match${if (matchCount == 1) "" else "es"}"
        return "$selected · $matches"
    }
}
