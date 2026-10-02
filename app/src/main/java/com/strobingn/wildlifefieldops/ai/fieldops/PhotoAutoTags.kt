package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.ai.camera.WildlifeEvidenceDetector
import kotlinx.serialization.Serializable

@Serializable
data class SyncedPhotoTag(
    val photoId: String = "",
    val species: String = "",
    val damage: String = "",
    val entry: String = "",
    val extra: String = "",
    val clearedKeys: Set<String> = emptySet()
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

    fun mergeOperatorWins(ai: SyncedPhotoTag, typed: SyncedPhotoTag): SyncedPhotoTag {
        fun cell(key: String, typedValue: String, aiValue: String): String {
            if (key in typed.clearedKeys) return typedValue
            if (typedValue.isNotBlank()) return typedValue
            return aiValue
        }
        return SyncedPhotoTag(
            photoId = typed.photoId.ifBlank { ai.photoId },
            species = cell(ManualField.PHOTO_SPECIES, typed.species, ai.species),
            damage = cell(ManualField.PHOTO_DAMAGE, typed.damage, ai.damage),
            entry = cell(ManualField.PHOTO_ENTRY, typed.entry, ai.entry),
            extra = cell(ManualField.PHOTO_EXTRA, typed.extra, ai.extra),
            clearedKeys = typed.clearedKeys
        )
    }

    fun persistTyped(photoId: String, species: String, damage: String, entry: String, extra: String, previous: SyncedPhotoTag?): SyncedPhotoTag {
        val cleared = (previous?.clearedKeys ?: emptySet()) + buildSet {
            if (species.isBlank()) add(ManualField.PHOTO_SPECIES)
            if (damage.isBlank()) add(ManualField.PHOTO_DAMAGE)
            if (entry.isBlank()) add(ManualField.PHOTO_ENTRY)
            if (extra.isBlank()) add(ManualField.PHOTO_EXTRA)
        }
        return SyncedPhotoTag(
            photoId = photoId,
            species = species,
            damage = damage,
            entry = entry,
            extra = extra,
            clearedKeys = cleared
        )
    }

    fun matchesFilter(tag: SyncedPhotoTag, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return tag.allTags().any { it.lowercase().contains(q) }
    }
}
