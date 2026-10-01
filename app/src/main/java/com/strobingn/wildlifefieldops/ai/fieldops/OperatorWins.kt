package com.strobingn.wildlifefieldops.ai.fieldops

/**
 * AI may fill blanks. Anything the operator already typed is kept unless they
 * explicitly choose replace.
 */
object OperatorWins {
    fun text(existing: String, suggested: String, replace: Boolean): String {
        val suggestion = suggested.trim()
        if (suggestion.isBlank()) return existing
        if (replace) return suggestion
        return if (existing.isBlank()) suggestion else existing
    }
}
