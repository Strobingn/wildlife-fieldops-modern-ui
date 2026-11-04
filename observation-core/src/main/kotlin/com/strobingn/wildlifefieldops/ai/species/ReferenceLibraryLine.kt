package com.strobingn.wildlifefieldops.ai.species

/**
 * One line under a detection result when the server library found similar photos.
 * Empty when the library was missing, too far, or not deployed yet.
 */
object ReferenceLibraryLine {
    fun format(species: String, similarPhotos: Int): String? {
        val name = species.trim()
        if (name.isEmpty() || similarPhotos <= 0) return null
        return "Matched to reference library: $name ($similarPhotos similar photos)"
    }
}
