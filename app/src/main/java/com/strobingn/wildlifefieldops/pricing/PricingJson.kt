package com.strobingn.wildlifefieldops.pricing

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Shared JSON codec for Room + Supabase jsonb (`jobs.pricing`). */
object PricingJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun encode(value: JobPricing): String = json.encodeToString(value)

    fun decode(raw: String?): JobPricing {
        if (raw.isNullOrBlank() || raw == "{}") return JobPricing()
        return runCatching { json.decodeFromString<JobPricing>(raw) }.getOrDefault(JobPricing())
    }
}
