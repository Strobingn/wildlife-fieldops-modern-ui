package com.strobingn.wildlifefieldops.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.strobingn.wildlifefieldops.ai.camera.CustomEvidenceModel
import com.strobingn.wildlifefieldops.ai.camera.RegionBox
import com.strobingn.wildlifefieldops.ai.camera.WildlifeEvidenceDetector
import com.strobingn.wildlifefieldops.ai.fieldops.CropBounds
import com.strobingn.wildlifefieldops.ai.fieldops.CropTransform
import com.strobingn.wildlifefieldops.ai.fieldops.EvidenceScreening
import com.strobingn.wildlifefieldops.ai.fieldops.FrameAuthority
import com.strobingn.wildlifefieldops.ai.fieldops.PhotoAnalysisTask
import com.strobingn.wildlifefieldops.ai.fieldops.PhotoEvidence
import com.strobingn.wildlifefieldops.ai.fieldops.PhotoEvidenceSessions
import com.strobingn.wildlifefieldops.ai.fieldops.RegionProvenance
import com.strobingn.wildlifefieldops.ai.fieldops.ScoredLabel
import com.strobingn.wildlifefieldops.ai.fieldops.StructuredPhotoEvidence
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

data class AiAnalysisResult(
    val species: List<String> = emptyList(),
    /** Label → on-device score for species hits only (used by ObservationEvent). */
    val speciesScores: Map<String, Float> = emptyMap(),
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
    val entryTypes: List<String> = emptyList(),
    val equipmentTypes: List<String> = emptyList(),
    val repairScopeSuggestion: String = "",
    /** Blank unless this still went through the entry/damage evidence contract. */
    val evidenceStatus: String = "",
    val evidenceReasons: List<String> = emptyList(),
    /** Legacy callers stay eligible. The evidence contract sets this explicitly. */
    val vlmEligible: Boolean = true,
    val authoritativeFrameHash: String = "",
    val frameAuthorityLabel: String = "",
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
            val stillBitmap = try {
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (_: Throwable) {
                null
            }
            val frameWidth = stillBitmap?.takeIf { !it.isRecycled }?.width ?: 0
            val frameHeight = stillBitmap?.takeIf { !it.isRecycled }?.height ?: 0
            val frameHash = frameHash(stillBitmap, imageUri.toString())
            val sharpness = sharpness(stillBitmap)
            val authority = if (PhotoEvidence.isDerivedEvidencePath(imageUri.toString())) {
                FrameAuthority.DERIVED
            } else {
                FrameAuthority.ORIGINAL
            }
            val custom = try {
                CustomEvidenceModel.tryInfer(
                    context = context,
                    bitmap = stillBitmap,
                    labelHints = labels.map { it.text }
                )
            } catch (_: Throwable) {
                emptyList()
            } finally {
                try {
                    // Scaled analysis buffers are derived and discarded here.
                    // The hash above was taken from the original decode.
                    stillBitmap?.takeIf { !it.isRecycled }?.recycle()
                } catch (_: Throwable) {
                }
            }
            val evidence = WildlifeEvidenceDetector.detect(labels, objects, customHits = custom)
            val objectNames = objects.mapNotNull { it.labels.firstOrNull()?.text?.lowercase() }.distinct()
            val species = evidence.species.map { it.label }.distinct()
            val speciesScores = evidence.species
                .groupBy { it.label }
                .mapValues { (_, hits) -> hits.maxOf { it.score } }
            val damage = evidence.damage.map { it.label }.distinct()
            val entries = evidence.entries.map { it.label }
            val equipment = evidence.equipment.map { it.label }.distinct()

            val service = when {
                species.any { it.contains("bat", ignoreCase = true) } -> "Bat Exclusion & Removal"
                species.any { it.contains("raccoon", ignoreCase = true) } -> "Raccoon Removal & Exclusion"
                species.any { it.contains("squirrel", ignoreCase = true) } -> "Squirrel Removal & Exclusion"
                species.any { it.contains("skunk", ignoreCase = true) } -> "Skunk Removal & Exclusion"
                species.any { it.contains("groundhog", ignoreCase = true) || it.contains("woodchuck", ignoreCase = true) } -> "Groundhog / Woodchuck Control"
                equipment.any { it.contains("trap", ignoreCase = true) } -> "Trapping & Monitoring"
                entries.isNotEmpty() -> "Entry Point Sealing & Repair"
                else -> "Wildlife Inspection & Removal"
            }
            val priority = if (species.isNotEmpty() || damage.isNotEmpty() || entries.isNotEmpty()) "HIGH" else "MEDIUM"
            val confidence = listOf(
                evidence.species.maxOfOrNull { it.score } ?: 0f,
                evidence.entries.maxOfOrNull { it.score } ?: 0f,
                evidence.equipment.maxOfOrNull { it.score } ?: 0f,
                labels.maxOfOrNull { it.confidence } ?: 0f
            ).max()
            val notes = buildString {
                append("Evidence: ${evidence.topSummary}. ")
                if (species.isNotEmpty()) append("Species: ${species.joinToString()}. ")
                if (entries.isNotEmpty()) append("Entry: ${entries.joinToString()}. ")
                if (damage.isNotEmpty()) append("Damage: ${damage.joinToString()}. ")
                if (equipment.isNotEmpty()) append("Trap/equipment: ${equipment.joinToString()}. ")
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
            val contract = screenEntryOrDamage(
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                frameHash = frameHash,
                authority = authority,
                sharpness = sharpness,
                boxes = objects.map { detected ->
                    val bounds = detected.boundingBox
                    RegionBox(bounds.left, bounds.top, bounds.right, bounds.bottom)
                },
                species = evidence.species.map { ScoredLabel(it.label, it.score) },
                entries = evidence.entries.map { ScoredLabel(it.label, it.score) },
                damage = evidence.damage.map { ScoredLabel(it.label, it.score) },
                timestampMs = System.currentTimeMillis(),
            )

            AiAnalysisResult(
                species = species,
                speciesScores = speciesScores,
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
                entryTypes = entries,
                equipmentTypes = equipment,
                evidenceStatus = contract?.status?.name.orEmpty(),
                evidenceReasons = contract?.reasons?.map { it.name }.orEmpty(),
                vlmEligible = contract?.vlmEligible ?: true,
                authoritativeFrameHash = frameHash,
                frameAuthorityLabel = PhotoEvidence.frameLabel(authority),
            )
        } catch (e: Throwable) {
            val durationMs = SystemClock.elapsedRealtime() - startedAt
            Log.w(TAG, "still-photo analysis failed after ${durationMs}ms", e)
            AiAnalysisResult(
                suggestedNotes = "Photo analysis failed: ${e.message}. Manual entry required.",
                analysisDurationMs = durationMs
            )
        }
    }

    private fun screenEntryOrDamage(
        frameWidth: Int,
        frameHeight: Int,
        frameHash: String,
        authority: FrameAuthority,
        sharpness: Float,
        boxes: List<RegionBox>,
        species: List<ScoredLabel>,
        entries: List<ScoredLabel>,
        damage: List<ScoredLabel>,
        timestampMs: Long,
    ): EvidenceScreening? {
        val tasks = buildList {
            if (entries.isNotEmpty()) add(PhotoAnalysisTask.ENTRY_POINT)
            if (damage.isNotEmpty()) add(PhotoAnalysisTask.EXTERIOR_DAMAGE)
        }
        if (tasks.isEmpty() && authority == FrameAuthority.ORIGINAL) return null
        val crop = WildlifeEvidenceDetector.selectRegion(frameWidth, frameHeight, boxes)
        val provenance = crop?.let {
            RegionProvenance(
                cropBounds = CropBounds(it.left, it.top, it.right, it.bottom),
                sourceFrameHash = frameHash,
                transform = if (it.fullFrame) CropTransform.IDENTITY else CropTransform.SCALE_TO_MODEL,
                timestampMs = timestampMs,
                authority = authority,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
            )
        }
        val coverage = if (crop == null || frameWidth <= 0 || frameHeight <= 0) {
            0f
        } else if (crop.fullFrame) {
            if (entries.isNotEmpty() || damage.isNotEmpty()) 0.14f else 0.02f
        } else {
            val area = (crop.right - crop.left) * (crop.bottom - crop.top)
            area.toFloat() / (frameWidth * frameHeight).toFloat()
        }
        val evidence = StructuredPhotoEvidence(
            task = tasks.firstOrNull() ?: PhotoAnalysisTask.ENTRY_POINT,
            provenance = provenance,
            species = species,
            entries = entries,
            damage = damage,
            sharpness = sharpness,
            coverage = coverage,
            hasExtent = crop != null && !crop.fullFrame || coverage >= PhotoEvidence.ACCEPT_COVERAGE,
        )
        val screening = if (tasks.isEmpty()) {
            PhotoEvidence.screen(evidence.copy(task = PhotoAnalysisTask.ENTRY_POINT))
        } else {
            PhotoEvidence.screenAll(evidence, tasks)
        }
        PhotoEvidenceSessions.remember(screening)
        return screening
    }

    private fun frameHash(bitmap: Bitmap?, fallback: String): String {
        val source = bitmap?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
        if (source == null) {
            return PhotoEvidence.hashSamples(fallback.length, 1, fallback.map { it.code }.toIntArray())
        }
        val stepX = (source.width / 8).coerceAtLeast(1)
        val stepY = (source.height / 8).coerceAtLeast(1)
        val samples = ArrayList<Int>(64)
        var y = 0
        while (y < source.height) {
            var x = 0
            while (x < source.width) {
                samples += source.getPixel(x, y)
                x += stepX
            }
            y += stepY
        }
        return PhotoEvidence.hashSamples(source.width, source.height, samples.toIntArray())
    }

    /** In-memory luma used only to score blur. The scaled buffer is not saved or exported. */
    private fun sharpness(bitmap: Bitmap?): Float {
        val source = bitmap?.takeIf { !it.isRecycled && it.width > 1 && it.height > 1 } ?: return 0f
        val targetW = 64
        val targetH = (source.height * targetW / source.width).coerceIn(8, 64)
        val scaled = if (source.width == targetW && source.height == targetH) {
            source
        } else {
            Bitmap.createScaledBitmap(source, targetW, targetH, true)
        }
        return try {
            val pixels = IntArray(targetW * targetH)
            scaled.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
            val luma = IntArray(pixels.size) { index ->
                val pixel = pixels[index]
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                (red * 3 + green * 4 + blue) / 8
            }
            PhotoEvidence.sharpnessFromLuma(luma, targetW)
        } catch (_: Throwable) {
            0f
        } finally {
            if (scaled !== source) {
                try {
                    scaled.recycle()
                } catch (_: Throwable) {
                }
            }
        }
    }
}

private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { cont ->
    task.addOnSuccessListener { result -> cont.resume(result) {} }
    task.addOnFailureListener { exception -> cont.resumeWithException(exception) }
}
