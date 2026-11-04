package com.strobingn.wildlifefieldops.ai.detection

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** CSV of labeled and unlabeled detection examples for a later fine-tune. */
object DetectionDatasetCsv {
    val HEADER = listOf(
        "id",
        "created",
        "user",
        "job_id",
        "photo_path",
        "photo_storage_path",
        "model_label",
        "model_confidence",
        "verdict",
        "corrected_label",
        "what_image_shows",
        "notes",
    )

    fun formatDay(millis: Long): String =
        SimpleDateFormat("MM/dd/yyyy", Locale.US).format(Date(millis))

    fun render(rows: List<StoredDetectionExample>): String {
        val body = rows.sortedBy { it.createdAt }.joinToString("\n") { row ->
            listOf(
                row.id,
                formatDay(row.createdAt),
                row.userId,
                row.jobId,
                row.photoLocalPath,
                row.photoStoragePath,
                row.modelLabel,
                row.modelConfidence.toString(),
                row.verdict,
                row.correctedLabel,
                row.whatImageShows,
                row.notes,
            ).joinToString(",") { cell(it) }
        }
        val header = HEADER.joinToString(",")
        return if (body.isEmpty()) "$header\n" else "$header\n$body\n"
    }

    fun cell(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        val quote = escaped.contains(',') || escaped.contains('"') || escaped.contains('\n') || escaped.contains('\r')
        return if (quote) "\"$escaped\"" else escaped
    }
}
