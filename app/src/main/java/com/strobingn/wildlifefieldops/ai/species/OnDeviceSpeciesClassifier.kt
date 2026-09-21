package com.strobingn.wildlifefieldops.ai.species

import android.content.Context
import android.net.Uri
import com.strobingn.wildlifefieldops.ai.AiAnalysisResult
import com.strobingn.wildlifefieldops.ai.PhotoAIHelper
import com.strobingn.wildlifefieldops.ai.camera.CustomEvidenceModel
import com.strobingn.wildlifefieldops.ai.camera.WildlifeEvidenceHit
import com.strobingn.wildlifefieldops.ai.camera.WildlifeEvidenceResult
import com.strobingn.wildlifefieldops.data.observation.ObservationEventFactory
import java.io.File

/**
 * Thin adapter from the existing TFLite / ML Kit still-photo pipeline
 * into [SpeciesRecognition] suggestions.
 */
object OnDeviceSpeciesClassifier {

    fun suggestFromAnalysis(analysis: AiAnalysisResult): SpeciesSuggestion? {
        val hits = analysis.speciesScores.ifEmpty {
            analysis.species.associateWith { analysis.confidence.coerceIn(0.2f, 1f) }
        }
        val backend = when {
            analysis.source.contains("tflite", ignoreCase = true) -> "tflite"
            analysis.source.contains("offline", ignoreCase = true) -> "mlkit"
            else -> "mlkit"
        }
        return SpeciesRecognition.suggest(hits, backendTag = backend)
    }

    fun suggestFromEvidence(
        evidence: WildlifeEvidenceResult,
        customHits: List<WildlifeEvidenceHit> = emptyList(),
    ): SpeciesSuggestion? {
        val hits = evidence.species.associate { it.label to it.score }
        val backend = when {
            customHits.any { it.source == "tflite" } -> "tflite"
            evidence.species.any { it.source == "tflite" } -> "tflite"
            else -> "mlkit"
        }
        return SpeciesRecognition.suggest(hits, backendTag = backend)
    }

    suspend fun classifyStill(context: Context, imageUri: Uri): Pair<AiAnalysisResult, SpeciesSuggestion?> {
        val analysis = PhotoAIHelper.analyzePhotoForFormFilling(context, imageUri)
        return analysis to suggestFromAnalysis(analysis)
    }

    fun modelHash(context: Context): String {
        return try {
            val dest = File(context.filesDir, "wildlife_evidence.tflite")
            if (dest.exists() && dest.length() > 0L) {
                ObservationEventFactory.hashUtf8("${dest.length()}:${dest.lastModified()}")
            } else if (CustomEvidenceModel.isAssetPresent(context)) {
                ObservationEventFactory.hashUtf8(CustomEvidenceModel.ASSET_PATH)
            } else {
                ObservationEventFactory.hashUtf8("mlkit-lexicon")
            }
        } catch (_: Throwable) {
            ObservationEventFactory.hashUtf8("unknown-model")
        }
    }

    fun frameHash(pathOrUri: String, observedAt: Long, extra: String = ""): String =
        ObservationEventFactory.hashUtf8("$pathOrUri|$observedAt|$extra")
}
