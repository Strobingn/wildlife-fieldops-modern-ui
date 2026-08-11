package com.strobingn.wildlifefieldops.ai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
// Note: kotlinx-coroutines-play-services dependency not included.
// Using a suspend helper that bridges Google Play Services Tasks to coroutines.

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
    val source: String = "offline_ml"
) {
    val serviceType: String get() = suggestedServiceType
    val priority: String get() = suggestedPriority
    val notes: String get() = suggestedNotes
    val fromGrok: Boolean get() = source == "grok"
}

object PhotoAIHelper {
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    suspend fun analyzePhotoForFormFilling(context: Context, imageUri: Uri): AiAnalysisResult {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            val labels = awaitTask(labeler.process(image))
            val knownSpecies = setOf("raccoon", "bat", "squirrel", "opossum", "snake", "bird", "rodent")
            val knownDamage = setOf("damage", "hole", "entry point", "chew marks", "nesting", "droppings", "scratching")
            val accepted = labels.filter { it.confidence > 0.55f }.map { it.text.lowercase() }
            val species = accepted.filter { it in knownSpecies }.distinct()
            val damage = accepted.filter { it in knownDamage }.distinct()
            val objects = awaitTask(objectDetector.process(image))
            val objectNames = objects.mapNotNull { it.labels.firstOrNull()?.text?.lowercase() }.distinct()

            val service = when {
                species.any { it.contains("bat") } -> "Bat Exclusion & Removal"
                species.any { it.contains("raccoon") } -> "Raccoon Removal & Exclusion"
                species.any { it.contains("squirrel") } -> "Squirrel Removal & Exclusion"
                damage.any { it.contains("entry") || it.contains("hole") } -> "Entry Point Sealing & Repair"
                else -> "Wildlife Inspection & Removal"
            }
            val priority = if (species.isNotEmpty() || damage.isNotEmpty()) "HIGH" else "MEDIUM"
            val confidence = labels.maxOfOrNull { it.confidence } ?: 0f
            val notes = buildString {
                if (species.isNotEmpty()) append("Species observed: ${species.joinToString()}. ")
                if (damage.isNotEmpty()) append("Damage noted: ${damage.joinToString()}. ")
                append("On-device confidence: ${String.format("%.0f", confidence * 100)}%. ")
                append("Verify on site and photograph all entry points.")
            }
            val prices = when {
                service.contains("Bat") -> Triple(450.0, 1200.0, "$450 - $1,200")
                service.contains("Raccoon") -> Triple(350.0, 950.0, "$350 - $950")
                service.contains("Squirrel") -> Triple(275.0, 750.0, "$275 - $750")
                else -> Triple(200.0, 600.0, "$200 - $600")
            }

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
                source = "offline_ml"
            )
        } catch (e: Exception) {
            AiAnalysisResult(suggestedNotes = "Photo analysis failed: ${e.message}. Manual entry required.")
        }
    }
}

/** Bridge Google Play Services [Task] to Kotlin coroutines without extra dependencies. */
private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { cont ->
    task.addOnSuccessListener { result -> cont.resume(result) {} }
    task.addOnFailureListener { exception -> cont.resumeWithException(exception) }
}
