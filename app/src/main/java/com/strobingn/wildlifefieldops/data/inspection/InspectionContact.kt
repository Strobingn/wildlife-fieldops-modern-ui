package com.strobingn.wildlifefieldops.data.inspection

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Phone, address, and service type ride on [com.strobingn.wildlifefieldops.data.model.Inspection.aiDraftSource]
 * so linking a job does not need a Room migration. Cleared cells stay in the
 * existing `|cleared=` list; this block is always stored before that marker.
 */
object InspectionContact {
    const val MARKER = "|contact="

    data class Values(
        val phone: String = "",
        val address: String = "",
        val serviceType: String = ""
    )

    fun read(raw: String): Values {
        val start = raw.indexOf(MARKER)
        if (start < 0) return Values()
        val payload = raw.substring(start + MARKER.length).substringBefore("|cleared=")
        if (payload.isBlank()) return Values()
        val map = payload.split('&').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else part.substring(0, eq) to decode(part.substring(eq + 1))
        }.toMap()
        return Values(
            phone = map["p"].orEmpty(),
            address = map["a"].orEmpty(),
            serviceType = map["t"].orEmpty()
        )
    }

    fun embed(raw: String, values: Values): String {
        val without = remove(raw)
        if (values.phone.isBlank() && values.address.isBlank() && values.serviceType.isBlank()) {
            return without
        }
        val payload = listOf(
            "p=${encode(values.phone)}",
            "a=${encode(values.address)}",
            "t=${encode(values.serviceType)}"
        ).joinToString("&")
        val clearedAt = without.indexOf("|cleared=")
        return if (clearedAt >= 0) {
            without.substring(0, clearedAt) + MARKER + payload + without.substring(clearedAt)
        } else {
            without + MARKER + payload
        }
    }

    private fun remove(raw: String): String {
        val start = raw.indexOf(MARKER)
        if (start < 0) return raw
        val after = start + MARKER.length
        val endRel = raw.substring(after).indexOf("|cleared=")
        val end = if (endRel < 0) raw.length else after + endRel
        return raw.removeRange(start, end)
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
}
