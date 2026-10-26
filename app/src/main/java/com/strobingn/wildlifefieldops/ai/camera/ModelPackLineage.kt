package com.strobingn.wildlifefieldops.ai.camera

/**
 * Model-pack lineage and append-only activation ledger (brief 2026-10-21).
 *
 * A reconnected device must not promote a pack just because its download or
 * file timestamp is newer. Promotion follows source-data cutoff, inherited lag,
 * and review version. Rollback appends a new ledger row that points at the
 * prior valid pack.
 */
data class ModelPackMeta(
    val parentModelDigest: String,
    val sourceDataCutoff: String,
    val reviewVersion: String,
    val buildDigest: String,
    val inheritedLagHours: Long,
    val sizeBytes: Long = 0L,
) {
    val complete: Boolean
        get() = parentModelDigest.isNotBlank() &&
            sourceDataCutoff.isNotBlank() &&
            reviewVersion.isNotBlank() &&
            buildDigest.isNotBlank() &&
            inheritedLagHours >= 0L &&
            ModelPackLineage.canonicalCutoff(sourceDataCutoff) != null
}

enum class LedgerAction { ACTIVATE, ROLLBACK }

data class LedgerEntry(
    val sequence: Long,
    val action: LedgerAction,
    val buildDigest: String,
    val parentModelDigest: String,
    val sourceDataCutoff: String,
    val reviewVersion: String,
    val inheritedLagHours: Long,
    val targetDigest: String,
    val recordedAtMs: Long,
)

enum class WeightSource { BUNDLED, CACHED_DOWNLOAD, SAVED_PACK }

data class PackPlan(
    val useCandidate: Boolean,
    val activeDigest: String,
    val weightSource: WeightSource,
    val ledger: List<LedgerEntry>,
    val changed: Boolean,
    val reasons: List<String>,
    /** Recorded for audit. Never consulted when choosing a pack. */
    val ignoredFileTimestampMs: Long,
)

object ModelPackLineage {
    fun canonicalCutoff(raw: String): String? {
        val text = raw.trim()
        if (Regex("""\d{4}-\d{2}-\d{2}""").matches(text)) return text
        val us = Regex("""(\d{2})/(\d{2})/(\d{4})""").matchEntire(text) ?: return null
        val (month, day, year) = us.destructured
        return "$year-$month-$day"
    }

    /** Operator-facing cutoff. Storage stays ISO so comparisons stay ordered. */
    fun displayCutoff(raw: String): String {
        val iso = canonicalCutoff(raw) ?: return raw
        val parts = iso.split('-')
        if (parts.size != 3) return raw
        return "${parts[1]}/${parts[2]}/${parts[0]}"
    }

    fun fileToken(digest: String): String =
        digest.replace(Regex("[^A-Za-z0-9._-]"), "_")

    fun parse(json: String): ModelPackMeta {
        val cutoffRaw = stringField(json, "sourceDataCutoff")
        val lag = longField(json, "inheritedLagHours")
        return ModelPackMeta(
            parentModelDigest = stringField(json, "parentModelDigest"),
            sourceDataCutoff = canonicalCutoff(cutoffRaw).orEmpty(),
            reviewVersion = stringField(json, "reviewVersion"),
            buildDigest = stringField(json, "buildDigest"),
            inheritedLagHours = lag ?: -1L,
            sizeBytes = longField(json, "size_bytes") ?: 0L,
        )
    }

    fun plan(
        bundled: ModelPackMeta,
        downloaded: ModelPackMeta?,
        downloadedFileTimestampMs: Long,
        ledger: List<LedgerEntry>,
        nowMs: Long,
        cachedWeightBytes: Long = -1L,
    ): PackPlan {
        val prefix = validPrefix(ledger)
        val active = activeMeta(prefix, bundled, downloaded) ?: bundled
        if (downloaded == null) {
            if (prefix.isEmpty()) {
                val entry = activateEntry(bundled, 1L, nowMs)
                return PackPlan(
                    useCandidate = false,
                    activeDigest = bundled.buildDigest,
                    weightSource = WeightSource.BUNDLED,
                    ledger = listOf(entry),
                    changed = true,
                    reasons = listOf("activated bundled pack"),
                    ignoredFileTimestampMs = downloadedFileTimestampMs,
                )
            }
            val head = effectiveDigest(prefix.last())
            return PackPlan(
                useCandidate = false,
                activeDigest = head,
                weightSource = if (head == bundled.buildDigest) WeightSource.BUNDLED else WeightSource.SAVED_PACK,
                ledger = prefix,
                changed = prefix.size != ledger.size,
                reasons = listOf("no download candidate; keeping ledger head"),
                ignoredFileTimestampMs = downloadedFileTimestampMs,
            )
        }

        val rejection = rejectionReason(active, downloaded, cachedWeightBytes)
        if (rejection != null) {
            val head = if (prefix.isEmpty()) bundled.buildDigest else effectiveDigest(prefix.last())
            val source = when {
                head == bundled.buildDigest -> WeightSource.BUNDLED
                head == downloaded.buildDigest -> WeightSource.CACHED_DOWNLOAD
                else -> WeightSource.SAVED_PACK
            }
            val baseline = if (prefix.isEmpty()) {
                listOf(activateEntry(bundled, 1L, nowMs))
            } else {
                prefix
            }
            return PackPlan(
                useCandidate = false,
                activeDigest = if (prefix.isEmpty()) bundled.buildDigest else head,
                weightSource = if (prefix.isEmpty()) WeightSource.BUNDLED else source,
                ledger = baseline,
                changed = baseline != ledger,
                reasons = listOf(rejection),
                ignoredFileTimestampMs = downloadedFileTimestampMs,
            )
        }

        if (downloaded.buildDigest == active.buildDigest) {
            return PackPlan(
                useCandidate = false,
                activeDigest = active.buildDigest,
                weightSource = if (active.buildDigest == bundled.buildDigest) {
                    WeightSource.BUNDLED
                } else {
                    WeightSource.CACHED_DOWNLOAD
                },
                ledger = prefix.ifEmpty { listOf(activateEntry(bundled, 1L, nowMs)) },
                changed = prefix.isEmpty() || prefix.size != ledger.size,
                reasons = listOf("candidate matches the active digest"),
                ignoredFileTimestampMs = downloadedFileTimestampMs,
            )
        }

        val baseline = if (prefix.isEmpty()) listOf(activateEntry(bundled, 1L, nowMs)) else prefix
        val nextSequence = baseline.last().sequence + 1L
        val entry = activateEntry(downloaded, nextSequence, nowMs)
        val next = append(baseline, entry)
        return PackPlan(
            useCandidate = true,
            activeDigest = downloaded.buildDigest,
            weightSource = WeightSource.CACHED_DOWNLOAD,
            ledger = next,
            changed = true,
            reasons = listOf("promoted ${downloaded.buildDigest}"),
            ignoredFileTimestampMs = downloadedFileTimestampMs,
        )
    }

    fun rollback(
        ledger: List<LedgerEntry>,
        available: List<ModelPackMeta>,
        nowMs: Long,
    ): PackPlan {
        val prefix = validPrefix(ledger)
        val current = prefix.lastOrNull()?.let { effectiveDigest(it) }
            ?: return PackPlan(
                useCandidate = false,
                activeDigest = "",
                weightSource = WeightSource.BUNDLED,
                ledger = prefix,
                changed = false,
                reasons = listOf("no active pack to roll back"),
                ignoredFileTimestampMs = 0L,
            )
        val prior = prefix.asReversed().drop(1).firstOrNull { entry ->
            val digest = effectiveDigest(entry)
            digest != current && available.any { it.buildDigest == digest }
        }
        if (prior == null) {
            return PackPlan(
                useCandidate = false,
                activeDigest = current,
                weightSource = WeightSource.BUNDLED,
                ledger = prefix,
                changed = prefix.size != ledger.size,
                reasons = listOf("no prior valid pack"),
                ignoredFileTimestampMs = 0L,
            )
        }
        val priorDigest = effectiveDigest(prior)
        val priorMeta = available.first { it.buildDigest == priorDigest }
        val entry = LedgerEntry(
            sequence = prefix.last().sequence + 1L,
            action = LedgerAction.ROLLBACK,
            buildDigest = priorMeta.buildDigest,
            parentModelDigest = priorMeta.parentModelDigest,
            sourceDataCutoff = priorMeta.sourceDataCutoff,
            reviewVersion = priorMeta.reviewVersion,
            inheritedLagHours = priorMeta.inheritedLagHours,
            targetDigest = priorDigest,
            recordedAtMs = nowMs,
        )
        return PackPlan(
            useCandidate = false,
            activeDigest = priorDigest,
            weightSource = WeightSource.SAVED_PACK,
            ledger = append(prefix, entry),
            changed = true,
            reasons = listOf("rollback to $priorDigest"),
            ignoredFileTimestampMs = 0L,
        )
    }

    fun append(existing: List<LedgerEntry>, entry: LedgerEntry): List<LedgerEntry> {
        val prefix = validPrefix(existing)
        require(prefix.size == existing.size) { "ledger is not a contiguous append-only prefix" }
        val expected = (prefix.lastOrNull()?.sequence ?: 0L) + 1L
        require(entry.sequence == expected) { "ledger sequence must be $expected" }
        val next = prefix + entry
        require(verifyAppendOnly(prefix, next)) { "append broke the ledger" }
        return next
    }

    fun verifyAppendOnly(before: List<LedgerEntry>, after: List<LedgerEntry>): Boolean {
        if (after.size < before.size) return false
        if (after.take(before.size) != before) return false
        val sequences = after.map { it.sequence }
        return sequences == (1L..after.size.toLong()).toList()
    }

    fun validPrefix(ledger: List<LedgerEntry>): List<LedgerEntry> {
        val out = mutableListOf<LedgerEntry>()
        var expected = 1L
        for (entry in ledger) {
            if (entry.sequence != expected) break
            out += entry
            expected++
        }
        return out
    }

    fun effectiveDigest(entry: LedgerEntry): String = when (entry.action) {
        LedgerAction.ACTIVATE -> entry.buildDigest
        LedgerAction.ROLLBACK -> entry.targetDigest
    }

    fun readLedger(text: String): List<LedgerEntry> {
        if (text.isBlank()) return emptyList()
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { decode(it) }
            .toList()
    }

    fun writeLedger(entries: List<LedgerEntry>): String {
        if (entries.isEmpty()) return ""
        return entries.joinToString("\n") { encode(it) } + "\n"
    }

    fun compareReview(left: String, right: String): Int {
        val a = leadingNumbers(left)
        val b = leadingNumbers(right)
        val count = maxOf(a.size, b.size)
        for (index in 0 until count) {
            val delta = a.getOrElse(index) { 0 } - b.getOrElse(index) { 0 }
            if (delta != 0) return delta
        }
        return left.compareTo(right)
    }

    private fun rejectionReason(
        active: ModelPackMeta,
        candidate: ModelPackMeta,
        cachedWeightBytes: Long,
    ): String? {
        if (!candidate.complete) return "incomplete lineage"
        if (candidate.sizeBytes > 0L && cachedWeightBytes >= 0L && cachedWeightBytes != candidate.sizeBytes) {
            return "cached weights do not match candidate sizeBytes"
        }
        if (candidate.buildDigest == active.buildDigest) return null
        val candidateCutoff = canonicalCutoff(candidate.sourceDataCutoff) ?: return "incomplete lineage"
        val activeCutoff = canonicalCutoff(active.sourceDataCutoff) ?: return "active pack cutoff is unreadable"
        if (candidateCutoff < activeCutoff) {
            return "stale source cutoff $candidateCutoff older than $activeCutoff"
        }
        if (candidateCutoff == activeCutoff && candidate.inheritedLagHours > active.inheritedLagHours) {
            return "inherited lag ${candidate.inheritedLagHours}h exceeds active ${active.inheritedLagHours}h"
        }
        if (candidateCutoff == activeCutoff && candidate.inheritedLagHours == active.inheritedLagHours) {
            val review = compareReview(candidate.reviewVersion, active.reviewVersion)
            if (review < 0) return "older review version ${candidate.reviewVersion}"
            if (review == 0) return "same lineage as the active pack"
        }
        return null
    }

    private fun activeMeta(
        prefix: List<LedgerEntry>,
        bundled: ModelPackMeta,
        downloaded: ModelPackMeta?,
    ): ModelPackMeta? {
        val digest = prefix.lastOrNull()?.let { effectiveDigest(it) } ?: return null
        if (bundled.buildDigest == digest) return bundled
        if (downloaded != null && downloaded.buildDigest == digest) return downloaded
        val head = prefix.last()
        return ModelPackMeta(
            parentModelDigest = head.parentModelDigest,
            sourceDataCutoff = head.sourceDataCutoff,
            reviewVersion = head.reviewVersion,
            buildDigest = digest,
            inheritedLagHours = head.inheritedLagHours,
        )
    }

    private fun activateEntry(meta: ModelPackMeta, sequence: Long, nowMs: Long): LedgerEntry =
        LedgerEntry(
            sequence = sequence,
            action = LedgerAction.ACTIVATE,
            buildDigest = meta.buildDigest,
            parentModelDigest = meta.parentModelDigest,
            sourceDataCutoff = meta.sourceDataCutoff,
            reviewVersion = meta.reviewVersion,
            inheritedLagHours = meta.inheritedLagHours,
            targetDigest = meta.buildDigest,
            recordedAtMs = nowMs,
        )

    private fun leadingNumbers(version: String): List<Int> {
        val head = version.takeWhile { it.isDigit() || it == '.' }
        if (head.isBlank()) return emptyList()
        return head.split('.').filter { it.isNotBlank() }.map { it.toIntOrNull() ?: 0 }
    }

    private fun stringField(json: String, key: String): String =
        Regex(""""$key"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1).orEmpty()

    private fun longField(json: String, key: String): Long? =
        Regex(""""$key"\s*:\s*(-?\d+)""").find(json)?.groupValues?.get(1)?.toLongOrNull()

    private fun encode(entry: LedgerEntry): String {
        fun plain(value: String): String {
            require(value.none { it == '"' || it == '\n' || it == '\\' }) {
                "ledger fields must be plain text"
            }
            return value
        }
        return buildString {
            append("{\"sequence\":").append(entry.sequence)
            append(",\"action\":\"").append(entry.action.name).append('"')
            append(",\"buildDigest\":\"").append(plain(entry.buildDigest)).append('"')
            append(",\"parentModelDigest\":\"").append(plain(entry.parentModelDigest)).append('"')
            append(",\"sourceDataCutoff\":\"").append(plain(entry.sourceDataCutoff)).append('"')
            append(",\"reviewVersion\":\"").append(plain(entry.reviewVersion)).append('"')
            append(",\"inheritedLagHours\":").append(entry.inheritedLagHours)
            append(",\"targetDigest\":\"").append(plain(entry.targetDigest)).append('"')
            append(",\"recordedAtMs\":").append(entry.recordedAtMs)
            append('}')
        }
    }

    private fun decode(line: String): LedgerEntry = LedgerEntry(
        sequence = longField(line, "sequence") ?: error("ledger sequence missing"),
        action = LedgerAction.valueOf(stringField(line, "action")),
        buildDigest = stringField(line, "buildDigest"),
        parentModelDigest = stringField(line, "parentModelDigest"),
        sourceDataCutoff = stringField(line, "sourceDataCutoff"),
        reviewVersion = stringField(line, "reviewVersion"),
        inheritedLagHours = longField(line, "inheritedLagHours") ?: -1L,
        targetDigest = stringField(line, "targetDigest"),
        recordedAtMs = longField(line, "recordedAtMs") ?: 0L,
    )
}
