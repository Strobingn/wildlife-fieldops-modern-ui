package com.strobingn.wildlifefieldops.ai.detection

/**
 * Small on-device memory of Sir's corrections.
 *
 * A new detection matches a stored pattern when the model label is the same
 * and, when both sides have an embedding, the images are similar.
 * With no embedding, the same model label is enough.
 */
data class CorrectionExample(
    val modelLabel: String,
    val preferredLabel: String,
    val embedding: List<Float>,
    val createdAt: Long,
    val verdict: String,
)

data class CorrectionPreference(
    val label: String,
    val learned: Boolean,
)

object DetectionCorrectionMemory {
    /** Cosine similarity for an 8×8 average hash. About 6 bits of difference. */
    const val SIMILARITY = 0.82f

    fun prefer(
        modelLabel: String,
        embedding: List<Float>?,
        examples: List<CorrectionExample>,
    ): CorrectionPreference? {
        val needle = DetectionLabels.normalize(modelLabel)
        if (needle.isEmpty()) return null
        val sameLabel = examples.filter {
            DetectionLabels.normalize(it.modelLabel) == needle && it.preferredLabel.isNotBlank()
        }
        if (sameLabel.isEmpty()) return null
        val query = embedding?.takeIf { it.isNotEmpty() }
        val chosen = if (query != null && sameLabel.any { it.embedding.isNotEmpty() }) {
            sameLabel
                .filter { it.embedding.isNotEmpty() }
                .map { it to FrameAverageHash.cosine(query, it.embedding) }
                .filter { it.second >= SIMILARITY }
                .maxWithOrNull(compareBy<Pair<CorrectionExample, Float>> { it.second }.thenBy { it.first.createdAt })
                ?.first
        } else {
            sameLabel.maxByOrNull { it.createdAt }
        } ?: return null
        val learned = !DetectionLabels.normalize(chosen.preferredLabel).equals(needle)
        return CorrectionPreference(label = chosen.preferredLabel.trim(), learned = learned)
    }

    fun fromRecord(record: StoredDetectionExample): CorrectionExample? {
        if (record.verdict !in DetectionLabels.REVIEWED) return null
        val preferred = when (record.verdict) {
            DetectionLabels.VERDICT_YES -> record.correctedLabel.ifBlank { record.modelLabel }
            else -> record.correctedLabel.ifBlank { record.whatImageShows }
        }.trim()
        if (record.modelLabel.isBlank() || preferred.isBlank()) return null
        return CorrectionExample(
            modelLabel = record.modelLabel.trim(),
            preferredLabel = preferred,
            embedding = FrameAverageHash.decode(record.embedding),
            createdAt = record.createdAt,
            verdict = record.verdict,
        )
    }
}
