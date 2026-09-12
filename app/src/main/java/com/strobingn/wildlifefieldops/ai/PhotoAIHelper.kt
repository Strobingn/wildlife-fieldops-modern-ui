package com.strobingn.wildlifefieldops.ai

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.strobingn.wildlifefieldops.ai.camera.WildlifeEvidenceDetector
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

data class AiAnalysisResult(
    val species: List<String> = emptyList(),
    val damageTypes: List<String> = emptyList(),
    val confidence: Float = 0f,
    val suggestedServiceType: String = "",
    val suggestedPriority: String = "MEDIUM",
    val suggestedNotes: String = "",
    val estimatedPriceRange: String = "",
    val estimatedPriceLow: Double = 0.0,
    val estimatedPriceHigh: Double = 0.0,
    val objectDetections: List<String> = emptyList(),
    val source: String = "offline_ml",
    val analysisDurationMs: Long = 0L,
    val evidenceSummary: String = "",
    val entryTypes: List<String> = emptyList()
) {
    val serviceType: String get() = suggestedServiceType
    val priority: String get() = suggestedPriority
    val notes: String get() = suggestedNotes
    val fromGrok: Boolean get() = source == "grok"
}

object PhotoAIHelper {
    private const val TAG = "PhotoAIHelper"

    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    suspend fun analyzePhotoForFormFilling(context: Context, imageUri: Uri): AiAnalysisResult {
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            val (labels, objects) = coroutineScope {
                val labelsDeferred = async { awaitTask(labeler.process(image)) }
                val objectsDeferred = async { awaitTask(objectDetector.process(image)) }
                labelsDeferred.await() to objectsDeferred.await()
            }
            val evidence = WildlifeEvidenceDetector.detect(labels, objects)
            val objectNames = objects.mapNotNull { it.labels.firstOrNull()?.text?.lowercase() }.distinct()
            val species = evidence.species.map { it.label }.distinct()
            val damage = evidence.damage.map { it.label }.distinct()
            val entries = evidence.entries.map { it.label }

            val service = when {
                species.any { it.contains("bat", ignoreCase = true) } -> "Bat Exclusion & Removal"
                species.any { it.contains("raccoon", ignoreCase = true) } -> "Raccoon Removal & Exclusion"
                species.any { it.contains("squirrel", ignoreCase = true) } -> "Squirrel Removal & Exclusion"
                entries.isNotEmpty() -> "Entry Point Sealing & Repair"
                else -> "Wildlife Inspection & Removal"
            }
            val priority = if (species.isNotEmpty() || damage.isNotEmpty() || entries.isNotEmpty()) "HIGH" else "MEDIUM"
            val confidence = listOf(
                evidence.species.maxOfOrNull { it.score } ?: 0f,
                evidence.entries.maxOfOrNull { it.score } ?: 0f,
                labels.maxOfOrNull { it.confidence } ?: 0f
            ).max()
            val notes = buildString {
                append("Evidence: ${evidence.topSummary}. ")
                if (species.isNotEmpty()) append("Species: ${species.joinToString()}. ")
                if (entries.isNotEmpty()) append("Entry: ${entries.joinToString()}. ")
                if (damage.isNotEmpty()) append("Damage: ${damage.joinToString()}. ")
                append("On-device confidence: ${String.format("%.0f", confidence * 100)}%. ")
                append("Verify on site.")
            }
            val prices = when {
                service.contains("Bat") -> Triple(450.0, 1200.0, "$450 - $1,200")
                service.contains("Raccoon") -> Triple(350.0, 950.0, "$350 - $950")
                service.contains("Squirrel") -> Triple(275.0, 750.0, "$275 - $750")
                else -> Triple(200.0, 600.0, "$200 - $600")
            }
            val durationMs = SystemClock.elapsedRealtime() - startedAt
            Log.i(TAG, "still-photo analysis ${durationMs}ms evidence=${evidence.topSummary}")

            AiAnalysisResult(
                species = species,
                damageTypes = damage,
                confidence = confidence,
                suggestedServiceType = service,
                suggestedPriority = priority,
                suggestedNotes = notes,
                estimatedPriceRange = prices.third,
                estimatedPriceLow = prices.first,
                estimatedPriceHigh = prices.second,
                objectDetections = objectNames,
                source = "offline_ml",
                analysisDurationMs = durationMs,
                evidenceSummary = evidence.topSummary,
                entryTypes = entries
            )
        } catch (e: Exception) {
            val durationMs = SystemClock.elapsedRealtime() - startedAt
            Log.w(TAG, "still-photo analysis failed after ${durationMs}ms", e)
            AiAnalysisResult(
                suggestedNotes = "Photo analysis failed: ${e.message}. Manual entry required.",
                analysisDurationMs = durationMs
            )
        }
    }
}

private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { cont ->
    task.addOnSuccessListener { result -> cont.resume(result) {} }
    task.addOnFailureListener { exception -> cont.resumeWithException(exception) }
}
