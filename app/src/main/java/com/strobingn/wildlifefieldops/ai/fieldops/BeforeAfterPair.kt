package com.strobingn.wildlifefieldops.ai.fieldops

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class PhotoPairRecord(
    val id: String = UUID.randomUUID().toString(),
    val beforeId: String = "",
    val afterId: String = "",
    val notes: String = ""
)

object BeforeAfterPair {
    fun pair(beforeId: String, afterId: String, notes: String = ""): PhotoPairRecord =
        PhotoPairRecord(beforeId = beforeId, afterId = afterId, notes = notes)

    fun forPhoto(pairs: List<PhotoPairRecord>, photoId: String): List<PhotoPairRecord> =
        pairs.filter { it.beforeId == photoId || it.afterId == photoId }

    fun upsert(pairs: List<PhotoPairRecord>, pair: PhotoPairRecord): List<PhotoPairRecord> =
        pairs.filterNot { it.id == pair.id } + pair

    fun remove(pairs: List<PhotoPairRecord>, id: String): List<PhotoPairRecord> =
        pairs.filterNot { it.id == id }
}
