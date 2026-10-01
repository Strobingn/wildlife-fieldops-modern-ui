package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Photo

object PhotoEvidence {
    fun tags(photos: List<Photo>): List<String> = photos.flatMap { photo ->
        buildList {
            if (photo.category.name.isNotBlank()) add(photo.category.name.lowercase())
            photo.description.split(',', ';', '\n').map { it.trim() }.filter { it.isNotBlank() }.forEach { add(it) }
        }
    }.distinct()

    fun notes(photos: List<Photo>): List<String> =
        photos.map { it.description.trim() }.filter { it.isNotBlank() }
}