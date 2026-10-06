package com.strobingn.wildlifefieldops.weather

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

object NwsAlertParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(body: String): List<NwsActiveAlert> {
        if (body.isBlank()) return emptyList()
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val features = root["features"]?.jsonArray ?: return emptyList()
        return features.mapNotNull { element ->
            val feature = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val properties = feature["properties"]?.jsonObject ?: return@mapNotNull null
            val event = properties.string("event")
            val headline = properties.string("headline")
            if (event.isBlank() && headline.isBlank()) return@mapNotNull null
            val id = feature.string("id").ifBlank { properties.string("id") }
            NwsActiveAlert(
                id = id.ifBlank { "$event|$headline" },
                event = event,
                headline = headline,
                onsetMillis = properties.string("onset").toEpochMillis(),
                endsMillis = properties.string("ends").toEpochMillis()
                    ?: properties.string("expires").toEpochMillis()
            )
        }
    }

    private fun kotlinx.serialization.json.JsonObject.string(name: String): String {
        val element = this[name] ?: return ""
        if (element is JsonNull) return ""
        return runCatching { element.jsonPrimitive.content }.getOrDefault("")
    }

    private fun String.toEpochMillis(): Long? {
        if (isBlank()) return null
        return runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
    }
}
