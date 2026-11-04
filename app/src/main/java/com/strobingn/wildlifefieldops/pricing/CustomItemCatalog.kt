package com.strobingn.wildlifefieldops.pricing

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Phone-local memory of custom item names and the last price typed for each. */
object CustomItemCatalog {
    private const val PREFS = "fieldops_custom_estimate_items"
    private const val KEY = "remembered"
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(RememberedCustomItem.serializer())

    fun load(context: Context): List<RememberedCustomItem> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            .orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    /** Keeps the latest typed price. A blank price does not erase a price already remembered. */
    fun remember(context: Context, items: List<CustomEstimateItem>) {
        val current = load(context).associateBy { CustomEstimateItems.normalizeName(it.name) }.toMutableMap()
        var changed = false
        items.forEach { item ->
            val key = CustomEstimateItems.normalizeName(item.name)
            val price = item.price ?: return@forEach
            if (key.isBlank()) return@forEach
            val next = RememberedCustomItem(name = item.name.trim(), price = Money.round(price))
            if (current[key] != next) {
                current[key] = next
                changed = true
            }
        }
        if (!changed) return
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, json.encodeToString(serializer, current.values.toList()))
            .apply()
    }
}
