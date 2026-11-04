package com.strobingn.wildlifefieldops.ai.detection

/**
 * Few-shot context for the vision call.
 * Empty input adds nothing, so a request without examples keeps the old shape.
 */
data class FewShotExample(
    val label: String,
    val description: String,
    val verdict: String = "",
    val modelLabel: String = "",
    val thumbnailDataUrl: String = "",
)

object DetectionFewShot {
    const val MARKER = "Confirmed field corrections:"
    const val MAX_EXAMPLES = 6
    const val THUMB_EACH = 40_000
    const val THUMB_BUDGET = 120_000

    private val dataUrl = Regex("""^data:image/(jpeg|jpg|png|webp);base64,[a-z0-9+/=\r\n]+$""", RegexOption.IGNORE_CASE)

    fun oneLine(raw: String): String =
        raw.replace(Regex("[\\r\\n]+"), " ").replace(Regex("\\s+"), " ").trim()

    fun fromRecords(records: List<StoredDetectionExample>): List<FewShotExample> =
        records.mapNotNull { record ->
            if (record.verdict !in DetectionLabels.REVIEWED) return@mapNotNull null
            val label = when (record.verdict) {
                DetectionLabels.VERDICT_YES -> record.correctedLabel.ifBlank { record.modelLabel }
                else -> record.correctedLabel.ifBlank { record.whatImageShows }
            }.trim()
            val description = record.whatImageShows.ifBlank { record.notes }.trim()
            if (label.isEmpty() && description.isEmpty()) return@mapNotNull null
            FewShotExample(
                label = label.take(80),
                description = description.take(240),
                verdict = record.verdict,
                modelLabel = record.modelLabel.take(80),
            )
        }.take(MAX_EXAMPLES)

    fun promptBlock(examples: List<FewShotExample>): String {
        if (examples.isEmpty()) return ""
        val lines = examples.take(MAX_EXAMPLES).map { example ->
            val shown = oneLine(example.label).ifBlank { "unlabeled" }
            val modelName = oneLine(example.modelLabel)
            val model = modelName.takeIf { it.isNotBlank() }?.let { "Model said \"$it\". " }.orEmpty()
            val verdict = oneLine(example.verdict).takeIf { it.isNotBlank() }?.let { "Verdict $it. " }.orEmpty()
            val description = oneLine(example.description)
            val desc = description.takeIf { it.isNotBlank() }?.let { " Shows: $it." }.orEmpty()
            "- $model$verdict" + "Correct label: \"$shown\".$desc"
        }
        return buildString {
            append(MARKER)
            append('\n')
            append("A wildlife technician confirmed or corrected these photo findings. ")
            append("When the new photo is similar, prefer the correct label. ")
            append("Entry points include gap, vent, soffit, roof return, chimney, gable vent, and foundation crack. ")
            append("If the correction is an entry point, keep the species on the allowed list and name the entry in evidence_notes.")
            lines.forEach {
                append('\n')
                append(it)
            }
        }
    }

    /**
     * Examples safe to put on the edge-function payload.
     * Thumbnails that are not a small image data URL are dropped.
     * The label and description still go.
     */
    fun wire(examples: List<FewShotExample>): List<FewShotExample> {
        var budget = THUMB_BUDGET
        var thumbs = 0
        return examples.take(MAX_EXAMPLES).map { example ->
            val thumb = example.thumbnailDataUrl.trim()
            val keep = thumbs < 2 &&
                thumb.length in 1..THUMB_EACH &&
                thumb.length <= budget &&
                dataUrl.matches(thumb)
            if (keep) {
                thumbs += 1
                budget -= thumb.length
                example.copy(
                    label = example.label.take(80),
                    description = example.description.take(240),
                    thumbnailDataUrl = thumb,
                )
            } else {
                example.copy(
                    label = example.label.take(80),
                    description = example.description.take(240),
                    thumbnailDataUrl = "",
                )
            }
        }.filter { it.label.isNotBlank() || it.description.isNotBlank() }
    }
}
