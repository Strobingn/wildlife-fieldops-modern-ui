package com.strobingn.wildlifefieldops.ai

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.strobingn.wildlifefieldops.ai.camera.CustomEvidenceModel
import com.strobingn.wildlifefieldops.ai.camera.WildlifeEvidenceDetector
import com.strobingn.wildlifefieldops.ai.camera.WildlifeEvidenceHit
import com.strobingn.wildlifefieldops.data.remote.InspectionReportDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/**
 * Walkthrough video → sparse frame sample → on-device vision → editable
 * [InspectionReportDraft] (Wildlife Whisperer FieldOps).
 *
 * Guidance: capture a ~60–90s property walkthrough. Longer videos are truncated
 * for sampling. Graceful offline — lexicon/ML Kit always produce a draft even
 * when cloud/local LLM is unavailable.
 */
object WalkthroughVideoAnalyzer {
    private const val TAG = "WalkthroughVideo"
    const val GUIDANCE_SECONDS_MIN = 60
    const val GUIDANCE_SECONDS_MAX = 90
    private const val MAX_SAMPLE_MS = 90_000L
    private const val TARGET_FRAMES = 10

    data class WalkthroughResult(
        val draft: InspectionReportDraft,
        val framesSampled: Int,
        val durationMs: Long,
        val evidenceSummary: String,
        val sourceLabel: String,
        val guidanceHint: String,
        val offline: Boolean
    )

    suspend fun analyze(
        context: Context,
        videoUri: Uri,
        enrichWithAi: (suspend (transcript: String) -> InspectionReportDraft?)? = null
    ): WalkthroughResult = withContext(Dispatchers.IO) {
        val started = SystemClock.elapsedRealtime()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(1L)
                ?: 1L
            val sampleWindowMs = min(durationMs, MAX_SAMPLE_MS)
            val frameCount = when {
                sampleWindowMs < 8_000L -> 4
                sampleWindowMs < 30_000L -> 6
                else -> TARGET_FRAMES
            }
            val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            val allHits = mutableListOf<WildlifeEvidenceHit>()
            val rawLabels = mutableListOf<String>()
            var framesOk = 0
            try {
                for (i in 0 until frameCount) {
                    val tMs = if (frameCount <= 1) 0L else (sampleWindowMs * i) / (frameCount - 1)
                    val frame = try {
                        retriever.getFrameAtTime(tMs * 1_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } catch (_: Throwable) {
                        null
                    } ?: continue
                    try {
                        val scaled = scaleDown(frame, 640)
                        if (scaled !== frame) frame.recycle()
                        val custom = CustomEvidenceModel.tryInfer(context, scaled)
                        val image = InputImage.fromBitmap(scaled, 0)
                        val labels = try {
                            awaitTask(labeler.process(image))
                        } catch (t: Throwable) {
                            Log.w(TAG, "label frame $i failed: ${t.message}")
                            emptyList()
                        }
                        val evidence = WildlifeEvidenceDetector.detect(
                            labels = labels,
                            objects = emptyList(),
                            checklistHint = "walkthrough",
                            customHits = custom
                        )
                        allHits += evidence.species + evidence.entries + evidence.damage +
                            evidence.activity + evidence.equipment
                        rawLabels += evidence.rawLabels
                        framesOk++
                        if (scaled !== frame) scaled.recycle() else frame.recycle()
                    } catch (t: Throwable) {
                        Log.w(TAG, "frame $i analyze failed: ${t.message}")
                        try { frame.recycle() } catch (_: Throwable) {}
                    }
                }
            } finally {
                try { labeler.close() } catch (_: Throwable) {}
            }

            val aggregated = WildlifeEvidenceDetector.aggregateHits(allHits)
            val transcript = buildTranscript(
                durationMs = durationMs,
                sampleWindowMs = sampleWindowMs,
                framesOk = framesOk,
                aggregated = aggregated,
                rawLabels = rawLabels.distinct().take(20)
            )
            val offlineDraft = lexiconDraft(aggregated, transcript)
            var usedAi = false
            val draft = try {
                val enriched = enrichWithAi?.invoke(transcript)
                if (enriched != null) {
                    usedAi = true
                    mergeDrafts(offlineDraft, enriched)
                } else offlineDraft
            } catch (t: Throwable) {
                Log.w(TAG, "AI enrich failed, using offline draft: ${t.message}")
                offlineDraft
            }

            val guidance = when {
                durationMs < GUIDANCE_SECONDS_MIN * 1000L ->
                    "Tip: aim for a ${GUIDANCE_SECONDS_MIN}–${GUIDANCE_SECONDS_MAX}s walkthrough (overview → entries → attic/crawl)."
                durationMs > GUIDANCE_SECONDS_MAX * 1000L ->
                    "Sampled first ~${GUIDANCE_SECONDS_MAX}s of a longer video."
                else ->
                    "Walkthrough length looks good (~${GUIDANCE_SECONDS_MIN}–${GUIDANCE_SECONDS_MAX}s guidance)."
            }

            val elapsed = SystemClock.elapsedRealtime() - started
            Log.i(TAG, "walkthrough frames=$framesOk/${frameCount} duration=${durationMs}ms analyze=${elapsed}ms ai=$usedAi")

            WalkthroughResult(
                draft = draft,
                framesSampled = framesOk,
                durationMs = durationMs,
                evidenceSummary = aggregated.topSummary,
                sourceLabel = if (usedAi) "walkthrough+ai" else "walkthrough_offline_ml",
                guidanceHint = guidance,
                offline = !usedAi
            )
        } catch (t: Throwable) {
            Log.w(TAG, "walkthrough failed: ${t.message}", t)
            WalkthroughResult(
                draft = InspectionReportDraft(
                    findings = "Walkthrough video could not be analyzed: ${t.message}. Dictate findings or retry offline.",
                    notes = "Manual entry required.",
                    severity = "MODERATE"
                ),
                framesSampled = 0,
                durationMs = 0L,
                evidenceSummary = "unavailable",
                sourceLabel = "walkthrough_error",
                guidanceHint = "Record a ${GUIDANCE_SECONDS_MIN}–${GUIDANCE_SECONDS_MAX}s walkthrough and retry. Works offline with ML Kit.",
                offline = true
            )
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }
    }

    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val w = src.width
        val h = src.height
        val longest = max(w, h)
        if (longest <= maxSide) return src
        val scale = maxSide.toFloat() / longest.toFloat()
        return Bitmap.createScaledBitmap(src, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true)
    }

    private fun buildTranscript(
        durationMs: Long,
        sampleWindowMs: Long,
        framesOk: Int,
        aggregated: com.strobingn.wildlifefieldops.ai.camera.WildlifeEvidenceResult,
        rawLabels: List<String>
    ): String = buildString {
        append("Property walkthrough video analysis (Wildlife Whisperer FieldOps).\n")
        append("Duration: ${durationMs / 1000}s; sampled window: ${sampleWindowMs / 1000}s; frames: $framesOk.\n")
        append("Evidence: ${aggregated.topSummary}\n")
        if (aggregated.species.isNotEmpty()) {
            append("Species cues: ${aggregated.species.joinToString { it.label }}\n")
        }
        if (aggregated.entries.isNotEmpty()) {
            append("Entry cues: ${aggregated.entries.joinToString { it.label }}\n")
        }
        if (aggregated.damage.isNotEmpty()) {
            append("Damage cues: ${aggregated.damage.joinToString { it.label }}\n")
        }
        if (aggregated.equipment.isNotEmpty()) {
            append("Trap/equipment cues: ${aggregated.equipment.joinToString { it.label }}\n")
        }
        if (rawLabels.isNotEmpty()) {
            append("Raw vision labels: ${rawLabels.joinToString()}\n")
        }
        append("Draft an inspection report from these field cues. Prefer NY wildlife vocabulary.")
    }

    private fun lexiconDraft(
        aggregated: com.strobingn.wildlifefieldops.ai.camera.WildlifeEvidenceResult,
        transcript: String
    ): InspectionReportDraft {
        val species = aggregated.species.joinToString { it.label }
        val entries = aggregated.entries.joinToString { it.label }
        val damage = aggregated.damage.joinToString { it.label }
        val equipment = aggregated.equipment.joinToString { it.label }
        val severity = when {
            aggregated.species.any { it.label.contains("bat", true) } ||
                aggregated.damage.any { it.label.contains("guano", true) || it.label.contains("chew", true) } -> "HIGH"
            aggregated.entries.isNotEmpty() || aggregated.species.isNotEmpty() -> "MODERATE"
            else -> "LOW"
        }
        val findings = buildString {
            append("Walkthrough vision review: ")
            append(aggregated.topSummary)
            if (equipment.isNotBlank()) append(". Equipment/traps noted: ").append(equipment)
            append(".")
        }
        val recommendations = buildString {
            when {
                aggregated.entries.isNotEmpty() ->
                    append("Measure and seal primary entry (${aggregated.entries.first().label}); confirm vacancy before permanent exclusion.")
                aggregated.species.isNotEmpty() ->
                    append("Confirm species activity, check for young, then plan trapping/exclusion per Wildlife Whisperer protocol.")
                else ->
                    append("Re-walk key elevations and attic/crawl; capture stills of any openings.")
            }
            if (equipment.contains("trap", true) || equipment.contains("one-way", true)) {
                append(" Log trap checks and condition in Trap Log.")
            }
        }
        return InspectionReportDraft(
            findings = findings,
            recommendations = recommendations,
            speciesIdentified = species,
            entryPoints = entries,
            damageAssessment = damage.ifBlank { "See walkthrough frames / stills." },
            severity = severity,
            notes = "Offline walkthrough draft. $transcript".take(1200),
            summary = "Walkthrough evidence: ${aggregated.topSummary}"
        )
    }

    private fun mergeDrafts(base: InspectionReportDraft, ai: InspectionReportDraft): InspectionReportDraft =
        InspectionReportDraft(
            findings = ai.findings.ifBlank { base.findings },
            recommendations = ai.recommendations.ifBlank { base.recommendations },
            speciesIdentified = ai.speciesIdentified.ifBlank { base.speciesIdentified },
            entryPoints = ai.entryPoints.ifBlank { base.entryPoints },
            damageAssessment = ai.damageAssessment.ifBlank { base.damageAssessment },
            severity = ai.severity.ifBlank { base.severity },
            notes = listOf(ai.notes, base.notes).filter { it.isNotBlank() }.distinct().joinToString("\n"),
            summary = ai.summary.ifBlank { base.summary }
        )
}

private suspend fun <T> awaitTask(task: Task<T>): T = suspendCancellableCoroutine { cont ->
    task.addOnSuccessListener { result -> cont.resume(result) }
    task.addOnFailureListener { exception -> cont.resumeWithException(exception) }
}
