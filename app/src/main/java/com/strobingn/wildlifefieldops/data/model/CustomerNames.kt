package com.strobingn.wildlifefieldops.data.model

import java.util.Locale

/**
 * Person + company display names. A company tag is shown once: sync used to
 * push "Pam Johnston (TSO)" as the remote name, split it back into
 * first/last on pull while keeping companyName, and append "(TSO)" again on
 * every round trip.
 */
object CustomerNames {
    private val GROUP = Regex("""\s*\(([^()]*)\)""")
    private val SPACES = Regex("""\s+""")

    /** "Pam Johnston (TSO) (TSO)" -> "Pam Johnston (TSO)". Keeps the first copy of each tag. */
    fun dedupeSuffixes(name: String): String {
        if (!name.contains('(')) return name.trim()
        val seen = mutableSetOf<String>()
        val out = GROUP.replace(name) { match ->
            val key = match.groupValues[1].trim().lowercase(Locale.US)
            if (key.isEmpty() || seen.add(key)) match.value else ""
        }
        return out.replace(SPACES, " ").trim()
    }

    /** "Pam Johnston" + "TSO" -> "Pam Johnston (TSO)", never "(TSO) (TSO)". */
    fun withCompany(person: String, company: String): String {
        val cleanPerson = dedupeSuffixes(person)
        val cleanCompany = company.trim()
        return when {
            cleanPerson.isBlank() -> cleanCompany
            cleanCompany.isBlank() -> cleanPerson
            hasTag(cleanPerson, cleanCompany) -> cleanPerson
            else -> "$cleanPerson ($cleanCompany)"
        }
    }

    /** Drops trailing "(company)" tags so a pulled display name splits back into first/last. */
    fun stripCompany(name: String, company: String): String {
        var out = dedupeSuffixes(name)
        val tag = "(" + company.trim() + ")"
        if (company.isBlank()) return out
        while (out.lowercase(Locale.US).endsWith(tag.lowercase(Locale.US))) {
            out = out.dropLast(tag.length).trim()
        }
        return out
    }

    private fun hasTag(person: String, company: String): Boolean =
        person.lowercase(Locale.US).contains("(" + company.lowercase(Locale.US) + ")")
}
