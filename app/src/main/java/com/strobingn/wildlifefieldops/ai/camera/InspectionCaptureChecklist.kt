package com.strobingn.wildlifefieldops.ai.camera

/**
 * Guided inspection evidence checklist. Items only complete when CaptureGuidancePolicy
 * ACCEPT gates a smart capture (or an explicit skip). AI never owns advancement.
 */
data class ChecklistItemDef(
    val id: String,
    val title: String,
    val hint: String
)

data class ChecklistItemProgress(
    val def: ChecklistItemDef,
    val completed: Boolean = false,
    val skipped: Boolean = false,
    val photoId: String? = null,
    val reasonCode: String? = null,
    val frameId: Long? = null
)

data class ChecklistSession(
    val items: List<ChecklistItemProgress>,
    val activeIndex: Int = 0
) {
    val active: ChecklistItemProgress? get() = items.getOrNull(activeIndex)
    val completedCount: Int get() = items.count { it.completed || it.skipped }
    val total: Int get() = items.size
    val allDone: Boolean get() = items.all { it.completed || it.skipped }
    val progressLabel: String get() = "$completedCount / $total"
}

object InspectionCaptureChecklist {
    val DEFAULT_ITEMS: List<ChecklistItemDef> = listOf(
        ChecklistItemDef("entry_point", "Entry point", "Hole, vent, soffit, or gap where wildlife enters"),
        ChecklistItemDef("droppings", "Droppings / urine", "Guano, scat, or staining evidence"),
        ChecklistItemDef("chew_damage", "Chew / claw damage", "Gnaw marks, torn insulation, scratch trails"),
        ChecklistItemDef("nesting", "Nesting / bedding", "Nest material, den, or staging area"),
        ChecklistItemDef("animal", "Animal / activity", "Live animal, sound cue photo, or clear activity sign"),
        ChecklistItemDef("overview", "Site overview", "Wide shot of structure / work area for the file")
    )

    fun newSession(defs: List<ChecklistItemDef> = DEFAULT_ITEMS): ChecklistSession =
        ChecklistSession(items = defs.map { ChecklistItemProgress(it) }, activeIndex = 0)
}
