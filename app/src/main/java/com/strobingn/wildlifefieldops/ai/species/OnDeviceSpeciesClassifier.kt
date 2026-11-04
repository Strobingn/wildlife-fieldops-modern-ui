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
        val labels = buildList {
            analysis.rawVisionLabels.forEach { (name, score) -> add(LabelScore(name, score)) }
            // Decision labels (including a TFLite species). A lexicon rewrite such as
            // "gray squirrel" is dropped when the raw text is only "squirrel".
            analysis.speciesScores.forEach { (name, score) -> add(LabelScore(name, score)) }
            if (isEmpty()) {
                analysis.species.forEach { name ->
                    add(LabelScore(name, analysis.confidence.coerceIn(0.2f, 1f)))
                }
            }
        }
        val backend = when {
            analysis.source.contains("tflite", ignoreCase = true) -> "tflite"
            analysis.source.contains("grok", ignoreCase = true) -> "grok"
            else -> "mlkit"
        }
        val decision = GenericLabelSpeciesMapper.map(labels) ?: return null
        return decision.toSuggestion(backend)
    }

    fun suggestFromEvidence(
        evidence: WildlifeEvidenceResult,
        customHits: List<WildlifeEvidenceHit> = emptyList(),
    ): SpeciesSuggestion? {
        val labels = buildList {
            evidence.rawLabels.forEach { add(LabelScore(it, 0.5f)) }
            evidence.species.forEach { add(LabelScore(it.label, it.score)) }
            customHits.filter { it.source == "tflite" }.forEach { add(LabelScore(it.label, it.score)) }
        }
        val backend = when {
            customHits.any { it.source == "tflite" } -> "tflite"
            evidence.species.any { it.source == "tflite" } -> "tflite"
            else -> "mlkit"
        }
        val decision = GenericLabelSpeciesMapper.map(labels) ?: return null
        return decision.toSuggestion(backend)
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
