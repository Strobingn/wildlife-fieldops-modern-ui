package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Photo
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Photo tags plus the region-first evidence contract (brief 2026-10-21).
 *
 * Entry-point and exterior-damage stills are screened deterministically:
 * region provenance, then completeness / consistency, then a VLM gate.
 * Only [EvidenceStatus.REVIEW] may call a VLM. Recovered fields are tagged
 * and run through [PhotoEvidence.screen] again. Offline reviews stay queued.
 * The original frame is authoritative; derived previews are labeled and
 * excluded from evidentiary exports.
 */
object PhotoEvidence {
    const val DERIVED_PREVIEW_LABEL = "Derived preview"
    const val ORIGINAL_FRAME_LABEL = "Original capture"
    const val ACCEPT_SCORE = 0.55f
    const val REVIEW_SCORE = 0.30f
    const val ACCEPT_SHARPNESS = 0.35f
    const val REVIEW_SHARPNESS = 0.18f
    const val ACCEPT_COVERAGE = 0.08f
    const val MIN_CROP_PX = 32
    const val SPECIES_CONFLICT_SCORE = 0.62f

    fun tags(photos: List<Photo>): List<String> = photos.flatMap { photo ->
        buildList {
            if (photo.category.name.isNotBlank()) add(photo.category.name.lowercase())
            photo.description.split(',', ';', '\n').map { it.trim() }.filter { it.isNotBlank() }.forEach { add(it) }
        }
    }.distinct()

    fun notes(photos: List<Photo>): List<String> =
        photos.map { it.description.trim() }.filter { it.isNotBlank() }

    fun frameLabel(authority: FrameAuthority): String =
        if (authority == FrameAuthority.DERIVED) DERIVED_PREVIEW_LABEL else ORIGINAL_FRAME_LABEL

    fun isDerivedEvidencePath(path: String): Boolean {
        val normalized = path.replace('\\', '/').lowercase()
        if (normalized.isBlank()) return false
        val name = normalized.substringAfterLast('/')
        return normalized.contains("/derived/") ||
            name.startsWith("derived_") ||
            name.contains(".derived.") ||
            name.contains("_derived.") ||
            name.contains("_enhanced_preview")
    }

    /** Original still only. Derived paths and thumbnail-only previews are dropped. */
    fun evidentiarySourcePath(photo: Photo): String? {
        val candidates = listOf(photo.localPath, photo.filePath)
        return candidates.map { it.trim() }.firstOrNull { it.isNotEmpty() && !isDerivedEvidencePath(it) }
    }

    fun evidentiaryPhotos(photos: List<Photo>): List<Photo> =
        photos.filter { evidentiarySourcePath(it) != null }

    fun evidentiaryFrames(frames: List<EvidenceFrameRef>): List<EvidenceFrameRef> =
        frames.filter { frame ->
            frame.authority == FrameAuthority.ORIGINAL && !isDerivedEvidencePath(frame.path)
        }

    fun hashSamples(width: Int, height: Int, samples: IntArray): String {
        var hash = 0x811c9dc5.toInt()
        fun mix(value: Int) {
            hash = hash xor value
            hash *= 0x01000193
        }
        mix(width)
        mix(height)
        for (sample in samples) mix(sample)
        return hash.toUInt().toString(16).padStart(8, '0')
    }

    /**
     * Maps Laplacian energy into 0..1. A variance near 180 lands at 0.5;
     * soft frames near 40 land at the review floor.
     */
    fun sharpnessFromLuma(luma: IntArray, width: Int): Float {
        if (width < 2 || luma.size < width * 2) return 0f
        val height = luma.size / width
        var energy = 0.0
        var count = 0
        for (y in 0 until height - 1) {
            val row = y * width
            val next = (y + 1) * width
            for (x in 0 until width - 1) {
                val center = luma[row + x]
                val dx = luma[row + x + 1] - center
                val dy = luma[next + x] - center
                energy += (dx * dx + dy * dy).toDouble()
                count++
            }
        }
        if (count == 0) return 0f
        val variance = energy / count
        return (variance / (variance + 180.0)).toFloat().coerceIn(0f, 1f)
    }

    fun screen(evidence: StructuredPhotoEvidence): EvidenceScreening {
        val provenanceIssue = provenanceProblems(evidence.provenance)
        if (provenanceIssue != null) {
            return terminal(evidence, EvidenceStatus.INSUFFICIENT, listOf(provenanceIssue))
        }
        if (evidence.sharpness < REVIEW_SHARPNESS) {
            return terminal(evidence, EvidenceStatus.INSUFFICIENT, listOf(AbstentionReason.LOW_SHARPNESS))
        }
        val cues = cuesFor(evidence)
        val cueScore = cues.maxOfOrNull { it.score } ?: 0f
        if (cueScore < REVIEW_SCORE) {
            val missing = if (evidence.task == PhotoAnalysisTask.ENTRY_POINT) {
                AbstentionReason.MISSING_ENTRY_GEOMETRY
            } else {
                AbstentionReason.MISSING_DAMAGE_EXTENT
            }
            return terminal(evidence, EvidenceStatus.INSUFFICIENT, listOf(missing))
        }

        val reasons = mutableListOf<AbstentionReason>()
        if (conflictingSpecies(evidence.species)) reasons += AbstentionReason.CONFLICTING_SPECIES
        if (negativeConflict(cues)) reasons += AbstentionReason.INCONSISTENT_LABELS
        if (cueScore < ACCEPT_SCORE) reasons += AbstentionReason.WEAK_SIGNAL
        if (evidence.sharpness < ACCEPT_SHARPNESS) reasons += AbstentionReason.LOW_SHARPNESS
        val extentOk = evidence.hasExtent || evidence.coverage >= ACCEPT_COVERAGE
        if (!extentOk) {
            reasons += if (evidence.task == PhotoAnalysisTask.ENTRY_POINT) {
                AbstentionReason.MISSING_ENTRY_GEOMETRY
            } else {
                AbstentionReason.MISSING_DAMAGE_EXTENT
            }
        }
        if (reasons.isEmpty()) {
            return EvidenceScreening(
                status = EvidenceStatus.ACCEPT,
                reasons = emptyList(),
                evidence = evidence,
                tasks = listOf(evidence.task),
                vlmEligible = false,
                settled = true,
                queuedOffline = false,
            )
        }
        return EvidenceScreening(
            status = EvidenceStatus.REVIEW,
            reasons = reasons.distinct(),
            evidence = evidence,
            tasks = listOf(evidence.task),
            vlmEligible = true,
            settled = false,
            queuedOffline = false,
        )
    }

    fun screenAll(
        evidence: StructuredPhotoEvidence,
        tasks: List<PhotoAnalysisTask>,
    ): EvidenceScreening {
        require(tasks.isNotEmpty()) { "at least one analysis task is required" }
        return combine(tasks.map { task -> screen(evidence.copy(task = task)) })
    }

    fun combine(parts: List<EvidenceScreening>): EvidenceScreening {
        require(parts.isNotEmpty()) { "screenings must not be empty" }
        val status = when {
            parts.any { it.status == EvidenceStatus.INSUFFICIENT } -> EvidenceStatus.INSUFFICIENT
            parts.any { it.status == EvidenceStatus.REVIEW } -> EvidenceStatus.REVIEW
            else -> EvidenceStatus.ACCEPT
        }
        val reasons = parts.flatMap { it.reasons }.distinct()
        return EvidenceScreening(
            status = status,
            reasons = reasons,
            evidence = parts.first().evidence,
            tasks = parts.flatMap { it.tasks }.distinct(),
            vlmEligible = status == EvidenceStatus.REVIEW,
            settled = status != EvidenceStatus.REVIEW,
            queuedOffline = false,
        )
    }

    /**
     * VLM output is eligible only for an unsettled review. Recovered values fill
     * empty fields, keep their provenance, and must pass [screen] again.
     * A failed recovery leaves the detector record unchanged.
     */
    fun applyVlm(
        screening: EvidenceScreening,
        recovered: List<VlmRecovery>,
        offline: Boolean,
        clearedKeys: Set<String> = emptySet(),
    ): EvidenceScreening {
        if (screening.status != EvidenceStatus.REVIEW) return screening
        if (offline) {
            return screening.copy(
                queuedOffline = true,
                settled = false,
                vlmEligible = true,
                reasons = (screening.reasons + AbstentionReason.OFFLINE_DEFERRED).distinct(),
            )
        }
        val fields = screening.evidence.fields.toMutableMap()
        var applied = false
        for (recovery in recovered) {
            if (recovery.key in clearedKeys) continue
            if (recovery.value.isBlank()) continue
            val existing = fields[recovery.key]
            if (existing != null && existing.value.isNotBlank()) continue
            if (familyAlreadyPresent(screening.evidence, recovery.key)) continue
            fields[recovery.key] = EvidenceField(
                value = recovery.value.trim(),
                source = FieldSource.VLM,
                confidence = recovery.confidence.coerceIn(0f, 1f),
            )
            applied = true
        }
        if (!applied) {
            return screening.copy(
                reasons = (screening.reasons + AbstentionReason.VLM_FAILED_REVALIDATION).distinct(),
                settled = false,
                queuedOffline = false,
                vlmEligible = true,
            )
        }
        val updated = projectFields(screening.evidence, fields)
        val tasks = screening.tasks.ifEmpty { listOf(updated.task) }
        val rescreened = screenAll(updated, tasks)
        if (rescreened.status == EvidenceStatus.ACCEPT) {
            return rescreened.copy(evidence = rescreened.evidence.copy(fields = fields))
        }
        return screening.copy(
            reasons = (screening.reasons + AbstentionReason.VLM_FAILED_REVALIDATION).distinct(),
            settled = false,
            queuedOffline = false,
            vlmEligible = true,
        )
    }

    /** Operator entry always sticks, including explicit blanks. */
    fun manualOverride(
        screening: EvidenceScreening,
        fields: Map<String, String>,
        clearedKeys: Set<String> = emptySet(),
    ): EvidenceScreening {
        val merged = screening.evidence.fields.toMutableMap()
        val cleared = clearedKeys.toMutableSet()
        for ((key, value) in fields) {
            if (value.isBlank()) cleared += key
        }
        for (key in cleared) {
            merged[key] = EvidenceField(value = "", source = FieldSource.MANUAL, confidence = 1f)
        }
        for ((key, value) in fields) {
            if (key in cleared) continue
            val trimmed = value.trim()
            if (trimmed.isEmpty()) continue
            merged[key] = EvidenceField(value = trimmed, source = FieldSource.MANUAL, confidence = 1f)
        }
        return screening.copy(
            status = EvidenceStatus.ACCEPT,
            reasons = listOf(AbstentionReason.MANUAL_OVERRIDE),
            evidence = screening.evidence.copy(fields = merged),
            vlmEligible = false,
            settled = true,
            queuedOffline = false,
        )
    }

    private fun projectFields(
        evidence: StructuredPhotoEvidence,
        fields: Map<String, EvidenceField>,
    ): StructuredPhotoEvidence {
        var hasExtent = evidence.hasExtent
        val entries = evidence.entries.toMutableList()
        val damage = evidence.damage.toMutableList()
        val species = evidence.species.toMutableList()
        for ((key, field) in fields) {
            if (field.value.isBlank()) continue
            when {
                key == "extent" -> hasExtent = true
                key == "entry" || key.startsWith("entry:") -> {
                    if (entries.none { it.label == field.value }) {
                        entries += ScoredLabel(field.value, field.confidence)
                    }
                }
                key == "damage" || key.startsWith("damage:") -> {
                    if (damage.none { it.label == field.value }) {
                        damage += ScoredLabel(field.value, field.confidence)
                    }
                }
                key == "species" || key.startsWith("species:") -> {
                    if (species.none { it.label == field.value }) {
                        species += ScoredLabel(field.value, field.confidence)
                    }
                }
            }
        }
        return evidence.copy(
            entries = entries,
            damage = damage,
            species = species,
            hasExtent = hasExtent,
            fields = fields,
        )
    }

    private fun familyAlreadyPresent(evidence: StructuredPhotoEvidence, key: String): Boolean =
        when {
            key == "entry" || key.startsWith("entry:") -> evidence.entries.isNotEmpty()
            key == "damage" || key.startsWith("damage:") -> evidence.damage.isNotEmpty()
            key == "species" || key.startsWith("species:") -> evidence.species.isNotEmpty()
            key == "extent" -> evidence.hasExtent
            else -> false
        }

    private fun cuesFor(evidence: StructuredPhotoEvidence): List<ScoredLabel> =
        if (evidence.task == PhotoAnalysisTask.ENTRY_POINT) evidence.entries else evidence.damage

    private fun provenanceProblems(provenance: RegionProvenance?): AbstentionReason? {
        if (provenance == null) return AbstentionReason.REGION_MISSING
        if (provenance.sourceFrameHash.isBlank() ||
            provenance.timestampMs <= 0L ||
            provenance.frameWidth <= 0 ||
            provenance.frameHeight <= 0
        ) {
            return AbstentionReason.PROVENANCE_INCOMPLETE
        }
        if (provenance.authority == FrameAuthority.DERIVED) {
            return AbstentionReason.DERIVED_FRAME_NOT_AUTHORITATIVE
        }
        if (!provenance.cropBounds.isInside(provenance.frameWidth, provenance.frameHeight)) {
            return AbstentionReason.CROP_OUT_OF_FRAME
        }
        if (provenance.cropBounds.width < MIN_CROP_PX || provenance.cropBounds.height < MIN_CROP_PX) {
            return AbstentionReason.CROP_TOO_SMALL
        }
        return null
    }

    private fun conflictingSpecies(species: List<ScoredLabel>): Boolean {
        val strong = species.filter { it.score >= SPECIES_CONFLICT_SCORE }.map { it.label }.distinct()
        return strong.size >= 2
    }

    private fun negativeConflict(cues: List<ScoredLabel>): Boolean {
        val negative = cues.any { isNegativeLabel(it.label) && it.score >= REVIEW_SCORE }
        val positive = cues.any { !isNegativeLabel(it.label) && it.score >= REVIEW_SCORE }
        return negative && positive
    }

    private fun isNegativeLabel(label: String): Boolean {
        val text = label.lowercase()
        return text.startsWith("no ") || text.contains("none") || text.contains("no entry") || text.contains("no damage")
    }

    private fun terminal(
        evidence: StructuredPhotoEvidence,
        status: EvidenceStatus,
        reasons: List<AbstentionReason>,
    ): EvidenceScreening = EvidenceScreening(
        status = status,
        reasons = reasons,
        evidence = evidence,
        tasks = listOf(evidence.task),
        vlmEligible = false,
        settled = true,
        queuedOffline = false,
    )
}

enum class PhotoAnalysisTask { ENTRY_POINT, EXTERIOR_DAMAGE }

enum class EvidenceStatus { ACCEPT, REVIEW, INSUFFICIENT }

enum class AbstentionReason {
    REGION_MISSING,
    PROVENANCE_INCOMPLETE,
    CROP_OUT_OF_FRAME,
    CROP_TOO_SMALL,
    DERIVED_FRAME_NOT_AUTHORITATIVE,
    LOW_SHARPNESS,
    MISSING_ENTRY_GEOMETRY,
    MISSING_DAMAGE_EXTENT,
    WEAK_SIGNAL,
    INCONSISTENT_LABELS,
    CONFLICTING_SPECIES,
    VLM_FAILED_REVALIDATION,
    OFFLINE_DEFERRED,
    MANUAL_OVERRIDE,
}

enum class FrameAuthority { ORIGINAL, DERIVED }

enum class CropTransform { IDENTITY, SCALE_TO_MODEL, ROTATE_UPRIGHT }

enum class FieldSource { DETERMINISTIC, VLM, MANUAL }

data class CropBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun isInside(frameWidth: Int, frameHeight: Int): Boolean =
        frameWidth > 0 && frameHeight > 0 &&
            left >= 0 && top >= 0 &&
            right <= frameWidth && bottom <= frameHeight &&
            width > 0 && height > 0
}

data class RegionProvenance(
    val cropBounds: CropBounds,
    val sourceFrameHash: String,
    val transform: CropTransform,
    val timestampMs: Long,
    val authority: FrameAuthority,
    val frameWidth: Int,
    val frameHeight: Int,
)

data class ScoredLabel(val label: String, val score: Float)

data class EvidenceField(
    val value: String,
    val source: FieldSource,
    val confidence: Float,
)

data class StructuredPhotoEvidence(
    val task: PhotoAnalysisTask,
    val provenance: RegionProvenance?,
    val species: List<ScoredLabel> = emptyList(),
    val entries: List<ScoredLabel> = emptyList(),
    val damage: List<ScoredLabel> = emptyList(),
    val sharpness: Float = 0f,
    val coverage: Float = 0f,
    val hasExtent: Boolean = false,
    val fields: Map<String, EvidenceField> = emptyMap(),
)

data class EvidenceScreening(
    val status: EvidenceStatus,
    val reasons: List<AbstentionReason>,
    val evidence: StructuredPhotoEvidence,
    val tasks: List<PhotoAnalysisTask> = listOf(evidence.task),
    val vlmEligible: Boolean,
    val settled: Boolean,
    val queuedOffline: Boolean,
)

data class VlmRecovery(
    val key: String,
    val value: String,
    val confidence: Float,
)

data class EvidenceFrameRef(
    val id: String,
    val path: String,
    val authority: FrameAuthority,
    val sourceFrameHash: String,
    val derivedFromHash: String? = null,
) {
    init {
        require(sourceFrameHash.isNotBlank()) { "frame hash required" }
        if (authority == FrameAuthority.DERIVED) {
            require(!derivedFromHash.isNullOrBlank()) { "derived frame must record its source hash" }
        }
    }
}

data class QueuedPhotoReview(
    val id: String,
    val screening: EvidenceScreening,
    val enqueuedAtMs: Long,
)

/**
 * Offline REVIEW deferral. Enqueue is idempotent. Completion is refused until
 * the screening is settled, so a review cannot flip to done by being queued.
 */
class PhotoReviewQueue {
    private val pending = LinkedHashMap<String, QueuedPhotoReview>()

    fun enqueue(id: String, screening: EvidenceScreening, nowMs: Long): QueuedPhotoReview? {
        if (id.isBlank() || screening.status != EvidenceStatus.REVIEW) return null
        pending[id]?.let { return it }
        val stored = screening.copy(
            queuedOffline = true,
            settled = false,
            vlmEligible = true,
            reasons = (screening.reasons + AbstentionReason.OFFLINE_DEFERRED).distinct(),
        )
        val item = QueuedPhotoReview(id = id, screening = stored, enqueuedAtMs = nowMs)
        pending[id] = item
        return item
    }

    fun get(id: String): QueuedPhotoReview? = pending[id]

    fun size(): Int = pending.size

    fun ids(): List<String> = pending.keys.toList()

    fun complete(id: String, updated: EvidenceScreening): Boolean {
        if (pending[id] == null) return false
        if (!updated.settled || updated.queuedOffline || updated.status == EvidenceStatus.REVIEW) {
            return false
        }
        pending.remove(id)
        return true
    }

    fun override(
        id: String,
        fields: Map<String, String>,
        clearedKeys: Set<String> = emptySet(),
    ): EvidenceScreening? {
        val existing = pending[id] ?: return null
        val updated = PhotoEvidence.manualOverride(existing.screening, fields, clearedKeys)
        pending.remove(id)
        return updated
    }
}

object PhotoReviewQueueHub {
    val shared: PhotoReviewQueue = PhotoReviewQueue()
}

object PhotoEvidenceSessions {
    private val byHash = ConcurrentHashMap<String, EvidenceScreening>()

    fun remember(screening: EvidenceScreening) {
        val hash = screening.evidence.provenance?.sourceFrameHash ?: return
        if (hash.isNotBlank()) byHash[hash] = screening
    }

    fun recall(hash: String): EvidenceScreening? = byHash[hash]

    fun replace(hash: String, screening: EvidenceScreening) {
        if (hash.isNotBlank()) byHash[hash] = screening
    }
}

data class DetectionBox(
    val frameIndex: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val label: String,
    val score: Float,
)

data class AnimalCount(
    val maxBoxCount: Int,
    val trackCount: Int,
    val low: Int,
    val high: Int,
    val point: Int?,
    val uncertain: Boolean,
    val withheld: Boolean = false,
)

/**
 * Numeric animal / colony / roost counts come from detection boxes.
 * A VLM integer string is never the published count (CountVideoBench bias).
 */
object AnimalCountGuard {
    const val DEFAULT_IOU = 0.30f
    const val DEFAULT_MIN_SCORE = 0.25f

    fun withheld(): AnimalCount = AnimalCount(
        maxBoxCount = 0,
        trackCount = 0,
        low = 0,
        high = 0,
        point = null,
        uncertain = true,
        withheld = true,
    )

    fun maxBoxCount(boxes: List<DetectionBox>, minScore: Float = DEFAULT_MIN_SCORE): Int =
        boxes.filter { it.score >= minScore }
            .groupBy { it.frameIndex }
            .values
            .maxOfOrNull { it.size } ?: 0

    fun trackCount(
        boxes: List<DetectionBox>,
        iouThreshold: Float = DEFAULT_IOU,
        minScore: Float = DEFAULT_MIN_SCORE,
    ): Int {
        val usable = boxes.filter { it.score >= minScore }
        if (usable.isEmpty()) return 0
        data class Track(var last: DetectionBox)
        val tracks = mutableListOf<Track>()
        val frames = usable.groupBy { it.frameIndex }.toSortedMap()
        for ((_, frameBoxes) in frames) {
            val pairs = mutableListOf<Triple<Float, DetectionBox, Track>>()
            for (box in frameBoxes) {
                for (track in tracks) {
                    val overlap = iou(box, track.last)
                    if (overlap >= iouThreshold) pairs += Triple(overlap, box, track)
                }
            }
            pairs.sortByDescending { it.first }
            val usedBoxes = mutableSetOf<DetectionBox>()
            val usedTracks = mutableSetOf<Track>()
            for ((_, box, track) in pairs) {
                if (box in usedBoxes || track in usedTracks) continue
                track.last = box
                usedBoxes += box
                usedTracks += track
            }
            for (box in frameBoxes) {
                if (box !in usedBoxes) tracks += Track(box)
            }
        }
        return tracks.size
    }

    fun estimate(
        boxes: List<DetectionBox>,
        iouThreshold: Float = DEFAULT_IOU,
        minScore: Float = DEFAULT_MIN_SCORE,
    ): AnimalCount {
        if (boxes.none { it.score >= minScore }) return withheld()
        val peak = maxBoxCount(boxes, minScore)
        val tracks = trackCount(boxes, iouThreshold, minScore)
        val low = min(peak, tracks)
        val high = max(peak, tracks)
        val uncertain = low != high
        return AnimalCount(
            maxBoxCount = peak,
            trackCount = tracks,
            low = low,
            high = high,
            point = if (uncertain) null else low,
            uncertain = uncertain,
            withheld = false,
        )
    }

    /** The published number is always the detector aggregate. */
    fun sourceOfTruth(truth: AnimalCount, vlmText: String): AnimalCount {
        if (vlmText.isEmpty()) return truth
        return truth
    }

    fun display(truth: AnimalCount): String = when {
        truth.withheld -> "count withheld (no detection boxes)"
        truth.point != null ->
            "deterministic count ${truth.point} (max-in-frame ${truth.maxBoxCount}, tracks ${truth.trackCount})"
        else ->
            "deterministic count ${truth.low}-${truth.high} " +
                "(max-in-frame ${truth.maxBoxCount}, tracks ${truth.trackCount}; uncertain)"
    }

    fun guardText(truth: AnimalCount, vlmText: String): String {
        if (vlmText.isBlank()) return vlmText
        val replacement = display(truth)
        var text = LEADING_COUNT.replace(vlmText) { match ->
            val number = match.groupValues[2].toIntOrNull()
            if (number != null && keepsInteger(truth, number)) match.value
            else "${match.groupValues[1]} $replacement"
        }
        text = TRAILING_COUNT.replace(text) { match ->
            val number = match.groupValues[1].toIntOrNull()
            if (number != null && keepsInteger(truth, number)) match.value
            else "$replacement ${match.groupValues[2]}"
        }
        return text
    }

    fun iou(a: DetectionBox, b: DetectionBox): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        val width = (right - left).coerceAtLeast(0f)
        val height = (bottom - top).coerceAtLeast(0f)
        val intersection = width * height
        val areaA = (a.right - a.left).coerceAtLeast(0f) * (a.bottom - a.top).coerceAtLeast(0f)
        val areaB = (b.right - b.left).coerceAtLeast(0f) * (b.bottom - b.top).coerceAtLeast(0f)
        val union = areaA + areaB - intersection
        if (union <= 0f) return 0f
        return intersection / union
    }

    private fun keepsInteger(truth: AnimalCount, number: Int): Boolean =
        !truth.withheld && number in truth.low..truth.high

    private val LEADING_COUNT = Regex(
        "(?i)\\b(count|colony|roost|animals|animal|bats|bat|individuals|birds|raccoons|squirrels)" +
            "\\s*(?:of|is|are|[:=])?\\s*(\\d{1,4})\\b",
    )
    private val TRAILING_COUNT = Regex(
        "(?i)\\b(\\d{1,4})\\s+(animals|animal|bats|bat|birds|individuals|raccoons|squirrels|roosting)\\b",
    )
}
