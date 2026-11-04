package com.strobingn.wildlifefieldops.pricing

import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import kotlinx.serialization.Serializable
import java.util.Locale
import java.util.UUID

/**
 * One technician-named service on an estimate. The line total is [price].
 * A null price is waiting on the technician — it is not $0.00 and it is not invented.
 */
@Serializable
data class CustomEstimateItem(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val price: Double? = null
) {
    fun lineTotal(): Double = price?.let { Money.round(it) } ?: 0.0

    fun needsPrice(): Boolean = price == null

    fun toInvoiceLine(): InvoiceLineItem {
        val label = buildString {
            append(name.trim())
            if (description.isNotBlank()) {
                append(" — ")
                append(description.trim())
            }
        }
        val amount = price?.let { Money.round(it) }
        return InvoiceLineItem(
            id = id.ifBlank { UUID.randomUUID().toString() },
            description = label,
            quantity = 0.0,
            unit = "",
            unitPrice = amount ?: 0.0,
            total = amount ?: 0.0,
            priceOnly = true,
            priceMissing = amount == null
        )
    }
}

/** Last name + price typed on this phone, for the next estimate's quick pick. */
@Serializable
data class RememberedCustomItem(
    val name: String = "",
    val price: Double = 0.0
)

/**
 * Adds custom lines without duplicating a name already on the estimate.
 * A later candidate can fill a price only on a line added in this same pass.
 * A price the technician already saved is left alone.
 */
object CustomEstimateItems {

    private val WORKSHEET_NAMES = setOf(
        "labor", "materials", "material", "mileage", "miles", "tax", "discount",
        "hours", "hour", "equipment", "permit", "permits", "disposal", "subtotal", "total"
    )

    fun normalizeName(name: String): String =
        name.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), " ").trim()

    fun textMentions(text: String, name: String): Boolean {
        val hay = normalizeName(text)
        val needle = normalizeName(name)
        if (needle.isBlank() || hay.isBlank()) return false
        return Regex("(?:^| )${Regex.escape(needle)}(?: |$)").containsMatchIn(hay)
    }

    /**
     * [aiItems] come from the model. [noteText] is parsed locally so a missing
     * model still creates the lines. [remembered] supplies a last price only
     * when the note names that service and nobody wrote a price.
     */
    fun resolve(
        existing: List<CustomEstimateItem>,
        aiItems: List<CustomEstimateItem>,
        noteText: String,
        remembered: List<RememberedCustomItem> = emptyList()
    ): List<CustomEstimateItem> {
        val parsed = CustomItemNoteParser.parse(noteText)
        val mentioned = remembered.mapNotNull { item ->
            if (!textMentions(noteText, item.name)) null
            else CustomEstimateItem(name = item.name.trim(), price = Money.round(item.price))
        }
        return admit(existing, parsed + aiItems + mentioned, remembered)
    }

    fun admit(
        existing: List<CustomEstimateItem>,
        incoming: List<CustomEstimateItem>,
        remembered: List<RememberedCustomItem> = emptyList()
    ): List<CustomEstimateItem> {
        val rememberedByName = remembered.associateBy { normalizeName(it.name) }
        val existingKeys = existing.map { normalizeName(it.name) }.filter { it.isNotBlank() }.toSet()
        val result = existing.toMutableList()
        incoming.forEach { raw ->
            val name = raw.name.trim().replace(Regex("\\s+"), " ")
            val key = normalizeName(name)
            if (key.isBlank() || key in WORKSHEET_NAMES) return@forEach
            val rememberedPrice = rememberedByName[key]?.price?.let { Money.round(it) }
            val price = raw.price?.let { Money.round(it) } ?: rememberedPrice
            val index = result.indexOfFirst { normalizeName(it.name) == key }
            if (index >= 0) {
                val current = result[index]
                if (key !in existingKeys) {
                    val nextPrice = current.price ?: price
                    val nextDescription = current.description.ifBlank { raw.description.trim() }
                    if (nextPrice != current.price || nextDescription != current.description) {
                        result[index] = current.copy(price = nextPrice, description = nextDescription)
                    }
                }
            } else {
                result += CustomEstimateItem(
                    id = raw.id.ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    description = raw.description.trim(),
                    price = price
                )
            }
        }
        return result
    }
}

/**
 * Pulls "gutter cleaning $250, gutter guards 400" out of notes without a model.
 * A service named with no amount stays unpriced.
 */
object CustomItemNoteParser {

    private val STOP = setOf(
        "the", "a", "an", "is", "are", "was", "were", "for", "with", "and", "or", "to", "of",
        "in", "on", "at", "total", "please", "customer", "job", "note", "notes", "say", "says",
        "said", "has", "have", "had", "this", "that", "from", "will", "would", "should",
        "wants", "want", "need", "needs", "include", "including", "also", "about", "asked",
        "last", "year", "our", "we", "they", "he", "she", "it", "be", "been", "do", "did",
        "done", "add", "into", "onto", "per", "by", "as", "if", "but", "not"
    )

    private val dollarToken = Regex("""\$\s*((?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?)""")
    private val leadIn = Regex(
        """^(?:please\s+)?(?:also\s+)?(?:add|need|needs|include|including|want|wants)\s+""",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String): List<CustomEstimateItem> {
        if (text.isBlank()) return emptyList()
        val found = linkedMapOf<String, CustomEstimateItem>()
        fun put(name: String, price: Double?) {
            val clean = cleanName(name) ?: return
            val key = CustomEstimateItems.normalizeName(clean)
            if (key.isBlank() || key in worksheetNames()) return
            if (key in found) return
            found[key] = CustomEstimateItem(name = clean, price = price?.let { Money.round(it) })
        }

        dollarToken.findAll(text).forEach { match ->
            val name = nameBefore(text, match.range.first) ?: return@forEach
            val amount = parseAmount(match.groupValues[1]) ?: return@forEach
            put(name, amount)
        }

        clauses(text).forEach { clause ->
            if (dollarToken.containsMatchIn(clause)) return@forEach
            val trailing = Regex("""^(.*?)(?:\s*[:\-–]\s*|\s+)(\d{1,6}(?:\.\d{1,2})?)$""")
                .matchEntire(clause.trim())
            if (trailing != null) {
                val amount = parseAmount(trailing.groupValues[2]) ?: return@forEach
                if (isBareNoise(amount)) return@forEach
                val name = nameBefore(clause.trim(), clause.trim().length - trailing.groupValues[2].length)
                    ?: cleanName(trailing.groupValues[1])
                if (name != null) put(name, amount)
                return@forEach
            }
            val stripped = clause.trim().replace(leadIn, "").trim().trim('.', ';', ':')
            if (looksLikeServiceLabel(stripped)) put(stripped, null)
        }
        return found.values.toList()
    }

    private fun worksheetNames(): Set<String> = setOf(
        "labor", "materials", "material", "mileage", "miles", "tax", "discount",
        "hours", "hour", "equipment", "permit", "permits", "disposal", "subtotal", "total"
    )

    private fun clauses(text: String): List<String> {
        val rough = text.split(Regex("[,;\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<String>()
        rough.forEach { part ->
            val pieces = part.split(Regex("(?i)\\s+and\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
            if (pieces.size >= 2 && pieces.any { dollarToken.containsMatchIn(it) || hasBarePrice(it) }) {
                out += pieces
            } else {
                out += part
            }
        }
        return out
    }

    private fun hasBarePrice(clause: String): Boolean =
        Regex("""(?i)\s(\d{1,6}(?:\.\d{1,2})?)$""").containsMatchIn(clause.trim())

    private fun isBareNoise(amount: Double): Boolean {
        if (amount < 10.0) return true
        val whole = amount.toLong().toDouble()
        return amount == whole && whole in 1900.0..2099.0
    }

    private fun parseAmount(raw: String): Double? {
        val n = raw.replace(",", "").toDoubleOrNull() ?: return null
        if (n.isNaN() || n.isInfinite() || n < 0.0) return null
        return Money.round(n)
    }

    private fun nameBefore(text: String, end: Int): String? {
        if (end <= 0) return null
        val prefix = text.substring(0, end.coerceAtMost(text.length))
        val words = Regex("""[A-Za-z][A-Za-z0-9'&/-]*""").findAll(prefix).toList()
        if (words.isEmpty()) return null
        val taken = mutableListOf<String>()
        var cursor = prefix.length
        for (word in words.asReversed()) {
            val between = prefix.substring(word.range.last + 1, cursor)
            if (!between.matches(Regex("""[\s:\-–]*"""))) break
            if (word.value.lowercase(Locale.US) in STOP) break
            taken += word.value
            cursor = word.range.first
            if (taken.size == 6) break
        }
        if (taken.isEmpty()) return null
        return cleanName(taken.asReversed().joinToString(" "))
    }

    private fun cleanName(raw: String): String? {
        var text = raw.trim().trim(':', '-', '–', ',', '.', ';').trim()
        text = text.replace(Regex("\\s+"), " ")
        text = text.replace(leadIn, "").trim()
        if (text.length < 2 || !text.any { it.isLetter() }) return null
        if (CustomEstimateItems.normalizeName(text) in worksheetNames()) return null
        return text
    }

    private fun looksLikeServiceLabel(raw: String): Boolean {
        val text = raw.trim()
        if (text.length !in 2..48) return false
        if (!text.all { it.isLetter() || it.isWhitespace() || it in "/&'-" }) return false
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size !in 1..6) return false
        if (words.size == 1 && words[0].length < 4) return false
        val lower = words.map { it.lowercase(Locale.US) }
        if (lower.any { it in STOP }) return false
        return cleanName(text) != null
    }
}
