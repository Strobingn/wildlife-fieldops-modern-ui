package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.ai.camera.WildlifeEvidenceDetector
import kotlinx.serialization.Serializable

@Serializable
data class SyncedPhotoTag(
    val photoId: String = "",
    val species: String = "",
    val damage: String = "",
    val entry: String = "",
    val extra: String = ""
) {
    fun allTags(): List<String> =
        listOf(species, damage, entry, extra).map { it.trim() }.filter { it.isNotBlank() }

    fun asDescription(): String = allTags().joinToString(" · ")
}

object PhotoAutoTags {
    fun suggest(text: String): SyncedPhotoTag {
        val result = WildlifeEvidenceDetector.detectFromText(text)
        return SyncedPhotoTag(
            species = result.primarySpecies.orEmpty(),
            damage = result.damage.firstOrNull()?.label.orEmpty(),
            entry = result.primaryEntry.orEmpty(),
            extra = result.equipment.firstOrNull()?.label.orEmpty()
        )
    }

    fun mergeOperatorWins(ai: SyncedPhotoTag, typed: SyncedPhotoTag): SyncedPhotoTag = SyncedPhotoTag(
        photoId = typed.photoId.ifBlank { ai.photoId },
        species = typed.species.ifBlank { ai.species },
        damage = typed.damage.ifBlank { ai.damage },
        entry = typed.entry.ifBlank { ai.entry },
        extra = typed.extra.ifBlank { ai.extra }
    )

    fun matchesFilter(tag: SyncedPhotoTag, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return tag.allTags().any { it.lowercase().contains(q) }
    }
}
