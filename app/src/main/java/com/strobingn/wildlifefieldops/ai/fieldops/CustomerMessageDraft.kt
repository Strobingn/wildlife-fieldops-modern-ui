package com.strobingn.wildlifefieldops.ai.fieldops

enum class CustomerMessageKind {
    ESTIMATE,
    REMINDER,
    WARRANTY,
    ON_THE_WAY
}

data class CustomerMessage(
    val kind: CustomerMessageKind,
    val subject: String,
    val body: String
)

object CustomerMessageDraft {
    fun draft(
        kind: CustomerMessageKind,
        customerName: String,
        jobTitle: String,
        address: String,
        amount: Double? = null,
        shop: String = "Wildlife Whisperer"
    ): CustomerMessage {
        val name = customerName.ifBlank { "there" }
        val job = jobTitle.ifBlank { "the wildlife job" }
        val where = address.ifBlank { "the property" }
        val money = amount?.takeIf { it > 0 }?.let { "$${"%.2f".format(it)}" }
        return when (kind) {
            CustomerMessageKind.ESTIMATE -> CustomerMessage(
                kind,
                "Estimate for $job",
                "Hi $name,\n\nHere is the written estimate for $job at $where${money?.let { " — $it" } ?: ""}. " +
                    "Every line is editable if you want a change. Reply yes and we will schedule.\n\n$shop"
            )
            CustomerMessageKind.REMINDER -> CustomerMessage(
                kind,
                "Reminder — $job",
                "Hi $name,\n\nFriendly reminder about $job at $where${money?.let { ". Balance $it" } ?: ""}. " +
                    "Call or text if you need a different day.\n\n$shop"
            )
            CustomerMessageKind.WARRANTY -> CustomerMessage(
                kind,
                "Warranty follow-up — $job",
                "Hi $name,\n\nChecking in on the warranty for $job at $where. " +
                    "If you hear scratching or see a new gap, send a photo and we will come look.\n\n$shop"
            )
            CustomerMessageKind.ON_THE_WAY -> CustomerMessage(
                kind,
                "On the way — $shop",
                "Hi $name,\n\nWe are on the way to $where for $job. See you shortly.\n\n$shop"
            )
        }
    }
}
