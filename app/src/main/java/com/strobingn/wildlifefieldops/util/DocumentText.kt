package com.strobingn.wildlifefieldops.util

import com.strobingn.wildlifefieldops.data.model.Customer
import java.util.Locale

/** Display-only cleanup for standard documents. Nothing here rewrites stored data. */
object DocumentText {
    const val EM_DASH = "\u2014"

    private val US_STATES = setOf(
        "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "DC", "FL", "GA", "HI", "ID", "IL", "IN", "IA",
        "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ", "NM",
        "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA",
        "WV", "WI", "WY", "PR"
    )
    private val STATE_TAIL = Regex("""(^|[,\s])([A-Za-z]{2})((?:\s+\d{5}(?:-\d{4})?)?)\s*$""")
    private val NON_WORD = Regex("""[^a-z0-9]+""")

    /** "Bronx, ny 10464" -> "Bronx, NY 10464". Only a trailing two-letter US state code changes. */
    fun upperState(cityLine: String): String {
        val match = STATE_TAIL.find(cityLine) ?: return cityLine
        val code = match.groupValues[2]
        val upper = code.uppercase(Locale.US)
        if (code == upper || upper !in US_STATES) return cityLine
        return cityLine.substring(0, match.range.first) + match.groupValues[1] + upper + match.groupValues[3]
    }

    /** Blank customer contact fields print an em dash instead of an empty fill-in line. */
    fun dash(value: String): String = value.trim().ifBlank { EM_DASH }

    /**
     * Joins note sources (typed notes, job description, job notes) without
     * printing the same paragraph twice. A paragraph that is equal to, or
     * contained in, one already kept is skipped; when the new one is the fuller
     * version it replaces the shorter one in place. Different text is kept.
     */
    fun mergeNotes(parts: List<String>): String {
        val kept = mutableListOf<String>()
        parts.forEach { part ->
            part.replace("\r", "").trim().split('\n').forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty()) {
                    if (kept.isNotEmpty() && kept.last().isNotEmpty()) kept += ""
                    return@forEach
                }
                val key = normalize(line)
                val index = if (key.isEmpty()) -1 else kept.indexOfFirst { it.isNotEmpty() && sameOrInside(normalize(it), key) }
                when {
                    index < 0 -> kept += line
                    normalize(kept[index]).length < key.length -> kept[index] = line
                    else -> Unit
                }
            }
        }
        while (kept.isNotEmpty() && kept.last().isEmpty()) kept.removeAt(kept.lastIndex)
        return kept.joinToString("\n")
    }

    private fun normalize(text: String): String =
        text.lowercase(Locale.US).replace(NON_WORD, " ").trim()

    private fun sameOrInside(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b || " $a ".contains(" $b ") || " $b ".contains(" $a ")) return true
        val wordsA = a.split(' ').toSet()
        val wordsB = b.split(' ').toSet()
        if (wordsA.size < 8 || wordsB.size < 8) return false
        val overlap = wordsA.intersect(wordsB).size.toDouble()
        return overlap / minOf(wordsA.size, wordsB.size) >= 0.9 && overlap / maxOf(wordsA.size, wordsB.size) >= 0.8
    }
}

/**
 * Customer phone/email for a document packet. Typed packet values win; blanks
 * fall back to the job's linked customer record, then to the email saved on
 * the job's latest invoice record. The local Job row has no phone/email
 * columns (and the Room schema stays as is), so the customer record is the
 * job's contact source.
 */
object JobContactFallback {
    fun apply(packet: StandardJobPacket, customer: Customer?): StandardJobPacket {
        val phone = packet.customerPhone.trim()
            .ifBlank { customer?.phone?.trim().orEmpty() }
            .ifBlank { customer?.alternatePhone?.trim().orEmpty() }
        val email = packet.customerEmail.trim()
            .ifBlank { customer?.email?.trim().orEmpty() }
            .ifBlank {
                packet.job.pricing.invoiceRecords
                    .sortedByDescending { it.issueDate }
                    .firstNotNullOfOrNull { record -> record.customerEmail.trim().takeIf { it.isNotBlank() } }
                    .orEmpty()
            }
        if (phone == packet.customerPhone && email == packet.customerEmail) return packet
        return packet.copy(customerPhone = phone, customerEmail = email)
    }
}
