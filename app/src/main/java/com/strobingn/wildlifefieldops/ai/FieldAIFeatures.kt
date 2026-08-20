package com.strobingn.wildlifefieldops.ai

/** Kept for old routes. Real work is [com.strobingn.wildlifefieldops.ai.field.QuoteBuilder]. */
object FieldAIFeatures {
    data class Feature(
        val id: String,
        val title: String,
        val category: String,
        val purpose: String,
        val steps: List<String>,
        val output: String
    )

    val all: List<Feature> = listOf(
        Feature(
            "sign_id",
            "Sign ID",
            "Field",
            "Rank species from signs.",
            listOf("Dropping size", "Hole size", "Night vs day"),
            "Use the Field desk. It scores this live."
        ),
        Feature(
            "season",
            "Season flags",
            "Field",
            "NY holds for bats, kits, and heat traps.",
            listOf("Read today", "Do not exclude occupied maternity"),
            "Use the Field desk."
        ),
        Feature(
            "repair_replace",
            "Replace / Repair",
            "Field",
            "Materials and hours from openings.",
            listOf("Measure", "16-ga cloth", "Mechanical fasteners"),
            "Use the Field desk."
        ),
        Feature(
            "quote",
            "Field quote",
            "Field",
            "Remove or replace/repair priced on-device.",
            listOf("Pick the job", "Copy the total"),
            "Use the Field desk."
        )
    )
}
