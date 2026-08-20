package com.strobingn.wildlifefieldops.ai.operations

/**
 * Operator tools only. Office-chat bots are gone.
 * The field desk computes these on-device.
 */
object IndividualAIToolCatalog {
    data class Tool(
        val id: String,
        val title: String,
        val purpose: String,
        val category: String,
        val insightName: String,
        val checklist: List<String>
    )

    val categories: List<String> = listOf("Field")

    val tools: List<Tool> = listOf(
        Tool(
            "sign_id",
            "Sign ID",
            "Rank species from droppings, hole size, and time of day.",
            "Field",
            "Sign ID",
            listOf("Mark dropping size", "Measure the hole", "Note night vs day")
        ),
        Tool(
            "season",
            "Season flags",
            "NY bat, raccoon, squirrel, and heat-check holds for today.",
            "Field",
            "Season flags",
            listOf("Read the hold", "Do not exclude occupied maternity", "12-hour trap checks in heat")
        ),
        Tool(
            "repair_replace",
            "Replace / Repair",
            "Cloth, screws, flashing, and hours from measured openings.",
            "Field",
            "Replace / Repair",
            listOf("Count openings", "Measure width and height", "16-ga cloth, no foam-only")
        ),
        Tool(
            "quote",
            "Field quote",
            "Line-item quote at Wildlife Whisperer rates.",
            "Field",
            "Field quote",
            listOf("Pick Remove or Replace/Repair", "Read season flags", "Copy to the customer")
        ),
        Tool(
            "work_order",
            "Work order",
            "Numbered steps for this job, not a chatbot essay.",
            "Field",
            "Work order",
            listOf("Photo with tape", "Remove or one-way", "Replace/repair the same opening")
        )
    )
}
