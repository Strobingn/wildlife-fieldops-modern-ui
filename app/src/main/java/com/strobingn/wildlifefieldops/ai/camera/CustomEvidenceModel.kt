package com.strobingn.wildlifefieldops.ai.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * On-device wildlife evidence classifier (TFLite Interpreter).
 *
 * Loads `assets/models/wildlife_evidence.tflite` + `labels.txt` when present.
 * Runs real float32 NHWC inference on [Bitmap] frames and returns
 * [WildlifeEvidenceHit] with SPECIES/ENTRY/DAMAGE/EQUIPMENT/ACTIVITY/OTHER.
 *
 * Never crashes if the model/runtime is missing — returns empty so callers fall
 * back to ML Kit + [WildlifeEvidenceDetector] lexicon. Custom hits are merged
 * (and effectively boost) via [WildlifeEvidenceDetector.detect].
 */
object CustomEvidenceModel {
    private const val TAG = "CustomEvidenceModel"
    const val ASSET_PATH = "models/wildlife_evidence.tflite"
    const val LABELS_ASSET = "models/labels.txt"
    private const val META_ASSET = "models/model_meta.json"
    private const val SIDECAR_NAME = "wildlife_evidence.model_meta.json"
    private const val LEDGER_NAME = "model_pack_ledger.jsonl"
    private const val PACK_DIR = "model_packs"
    private const val INPUT_SIZE = 224
    private const val NUM_CHANNELS = 3
    private const val MIN_SCORE = 0.18f
    private const val TOP_K = 4
    /** Mild boost so custom model votes outweigh soft lexicon matches when merged. */
    private const val SCORE_BOOST = 0.06f

    data class LabelEntry(val index: Int, val label: String, val kind: WildlifeEvidenceHit.Kind)

    @Volatile private var probed: Boolean = false
    @Volatile private var assetPresent: Boolean = false
    @Volatile private var interpreter: Interpreter? = null
    @Volatile private var labels: List<LabelEntry> = emptyList()
    private val initFailed = AtomicBoolean(false)
    private val lock = Any()

    fun isAssetPresent(context: Context): Boolean {
        if (probed) return assetPresent
        synchronized(lock) {
            if (probed) return assetPresent
            assetPresent = try {
                context.assets.open(ASSET_PATH).use { true }
            } catch (_: Throwable) {
                false
            }
            probed = true
            if (!assetPresent) {
                Log.i(TAG, "No custom model at assets/$ASSET_PATH — using ML Kit + lexicon")
            }
            return assetPresent
        }
    }

    /**
     * Best-effort custom inference. Always safe: empty list on any failure.
     */
    fun tryInfer(
        context: Context,
        bitmap: Bitmap? = null,
        labelHints: List<String> = emptyList()
    ): List<WildlifeEvidenceHit> {
        return try {
            if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
                return emptyList()
            }
            if (!isAssetPresent(context)) return emptyList()
            if (initFailed.get()) return emptyList()
            val interp = ensureInterpreter(context) ?: return emptyList()
            val labelList = labels
            if (labelList.isEmpty()) return emptyList()

            val input = preprocess(bitmap)
            val output = Array(1) { FloatArray(labelList.size) }
            synchronized(lock) {
                interp.run(input, output)
            }
            val scores = output[0]
            val ranked = scores.indices
                .sortedByDescending { scores[it] }
                .take(TOP_K)
            val hits = ArrayList<WildlifeEvidenceHit>(ranked.size)
            for (idx in ranked) {
                val score = scores[idx]
                if (score < MIN_SCORE) continue
                val entry = labelList.getOrNull(idx) ?: continue
                if (entry.kind == WildlifeEvidenceHit.Kind.OTHER && score < 0.45f) continue
                val display = displayLabel(entry.label)
                val boosted = (score + SCORE_BOOST).coerceAtMost(1f)
                hits += WildlifeEvidenceHit(
                    kind = entry.kind,
                    label = display,
                    score = boosted,
                    source = "tflite"
                )
            }
            if (hits.isNotEmpty()) {
                Log.d(
                    TAG,
                    "tflite hits=${hits.joinToString { "${it.label}:${"%.2f".format(it.score)}" }} " +
                        "hints=${labelHints.size} ${bitmap.width}x${bitmap.height}"
                )
            }
            hits
        } catch (t: Throwable) {
            Log.w(TAG, "CustomEvidenceModel tryInfer failed (falling back): ${t.message}")
            emptyList()
        }
    }

    fun close() {
        synchronized(lock) {
            try {
                interpreter?.close()
            } catch (_: Throwable) {
            }
            interpreter = null
        }
    }

    private fun ensureInterpreter(context: Context): Interpreter? {
        interpreter?.let { return it }
        synchronized(lock) {
            interpreter?.let { return it }
            if (initFailed.get()) return null
            return try {
                val model = loadModelFile(context)
                val opts = Interpreter.Options().apply {
                    setNumThreads(2)
                }
                val interp = Interpreter(model, opts)
                labels = loadLabels(context, interp)
                if (labels.isEmpty()) {
                    Log.w(TAG, "labels empty — refusing Interpreter")
                    try { interp.close() } catch (_: Throwable) {}
                    initFailed.set(true)
                    return null
                }
                interpreter = interp
                Log.i(TAG, "TFLite Interpreter ready labels=${labels.size} input=${INPUT_SIZE}")
                interp
            } catch (t: Throwable) {
                Log.w(TAG, "Interpreter init failed (falling back): ${t.message}")
                initFailed.set(true)
                null
            }
        }
    }

    /**
     * Append-only rollback to the previous valid pack. A newer download timestamp
     * cannot undo this; the ledger row is the record.
     */
    fun rollbackToPriorValid(context: Context): String? {
        return try {
            val bundled = readBundledMeta(context) ?: return null
            val ledgerFile = ledgerFile(context)
            val ledger = ModelPackLineage.readLedger(
                if (ledgerFile.isFile) ledgerFile.readText() else ""
            )
            val available = mutableListOf(bundled)
            val packDir = File(context.filesDir, PACK_DIR)
            packDir.listFiles()?.forEach { file ->
                if (file.extension == "json") {
                    runCatching { ModelPackLineage.parse(file.readText()) }.getOrNull()?.let { available += it }
                }
            }
            val plan = ModelPackLineage.rollback(ledger, available, System.currentTimeMillis())
            if (!plan.changed) return plan.activeDigest.ifBlank { null }
            writeLedger(ledgerFile, plan.ledger)
            val dest = weightCache(context)
            val saved = File(packDir, ModelPackLineage.fileToken(plan.activeDigest) + ".tflite")
            if (saved.isFile && saved.length() > 0L) {
                saved.copyTo(dest, overwrite = true)
            } else if (plan.activeDigest == bundled.buildDigest) {
                copyAssetModel(context, dest)
            }
            close()
            plan.activeDigest
        } catch (t: Throwable) {
            Log.w(TAG, "rollback failed: ${t.message}")
            null
        }
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val dest = resolveWeightFile(context)
        FileInputStream(dest).use { fis ->
            val channel = fis.channel
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
        }
    }

    /**
     * Picks bundled vs downloaded weights using lineage, not file mtime.
     * Falls back to the existing cache-or-asset copy if the sidecar cannot be read.
     */
    private fun resolveWeightFile(context: Context): File {
        val dest = weightCache(context)
        try {
            val bundled = readBundledMeta(context) ?: return ensureCache(context, dest)
            val sidecar = File(context.filesDir, SIDECAR_NAME)
            val downloaded = if (sidecar.isFile && sidecar.length() > 0L) {
                runCatching { ModelPackLineage.parse(sidecar.readText()) }.getOrNull()
            } else {
                null
            }
            val ledgerFile = ledgerFile(context)
            val ledger = ModelPackLineage.readLedger(
                if (ledgerFile.isFile) ledgerFile.readText() else ""
            )
            val plan = ModelPackLineage.plan(
                bundled = bundled,
                downloaded = downloaded,
                downloadedFileTimestampMs = when {
                    sidecar.isFile -> sidecar.lastModified()
                    dest.isFile -> dest.lastModified()
                    else -> 0L
                },
                ledger = ledger,
                nowMs = System.currentTimeMillis(),
                cachedWeightBytes = if (dest.isFile) dest.length() else 0L,
            )
            if (plan.changed) writeLedger(ledgerFile, plan.ledger)
            when (plan.weightSource) {
                WeightSource.CACHED_DOWNLOAD -> {
                    if (!dest.exists() || dest.length() == 0L) copyAssetModel(context, dest)
                    archivePack(context, plan.activeDigest, dest, sidecar)
                }
                WeightSource.SAVED_PACK -> {
                    val saved = File(
                        context.filesDir,
                        "$PACK_DIR/${ModelPackLineage.fileToken(plan.activeDigest)}.tflite"
                    )
                    if (saved.isFile && saved.length() > 0L) {
                        saved.copyTo(dest, overwrite = true)
                    } else if (plan.activeDigest == bundled.buildDigest || !dest.exists() || dest.length() == 0L) {
                        copyAssetModel(context, dest)
                    }
                }
                WeightSource.BUNDLED -> {
                    val rejectedDownload = downloaded != null && !plan.useCandidate
                    if (!dest.exists() || dest.length() == 0L || rejectedDownload) {
                        copyAssetModel(context, dest)
                    }
                }
            }
            return dest
        } catch (t: Throwable) {
            Log.w(TAG, "model lineage gate skipped: ${t.message}")
            return ensureCache(context, dest)
        }
    }

    private fun ensureCache(context: Context, dest: File): File {
        if (!dest.exists() || dest.length() == 0L) copyAssetModel(context, dest)
        return dest
    }

    private fun copyAssetModel(context: Context, dest: File) {
        dest.parentFile?.mkdirs()
        context.assets.open(ASSET_PATH).use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
    }

    private fun archivePack(context: Context, digest: String, weights: File, sidecar: File) {
        if (digest.isBlank() || !weights.isFile || weights.length() == 0L) return
        val dir = File(context.filesDir, PACK_DIR)
        dir.mkdirs()
        val token = ModelPackLineage.fileToken(digest)
        weights.copyTo(File(dir, "$token.tflite"), overwrite = true)
        if (sidecar.isFile) sidecar.copyTo(File(dir, "$token.json"), overwrite = true)
    }

    private fun readBundledMeta(context: Context): ModelPackMeta? = try {
        val json = context.assets.open(META_ASSET).bufferedReader().use { it.readText() }
        val meta = ModelPackLineage.parse(json)
        meta.takeIf { it.complete }
    } catch (_: Throwable) {
        null
    }

    private fun writeLedger(file: File, entries: List<LedgerEntry>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(ModelPackLineage.writeLedger(entries))
        if (!tmp.renameTo(file)) {
            file.writeText(ModelPackLineage.writeLedger(entries))
            tmp.delete()
        }
    }

    private fun weightCache(context: Context) = File(context.filesDir, "wildlife_evidence.tflite")

    private fun ledgerFile(context: Context) = File(context.filesDir, LEDGER_NAME)

    private fun loadLabels(context: Context, interp: Interpreter): List<LabelEntry> {
        val fromAsset = try {
            context.assets.open(LABELS_ASSET).bufferedReader().use { it.readLines() }
                .mapNotNull { line ->
                    val t = line.trim()
                    if (t.isEmpty() || t.startsWith("#")) return@mapNotNull null
                    val parts = t.split('\t', ',', ' ').filter { it.isNotBlank() }
                    when {
                        parts.size >= 3 && parts[0].toIntOrNull() != null -> {
                            LabelEntry(
                                index = parts[0].toInt(),
                                label = parts[1],
                                kind = parseKind(parts[2])
                            )
                        }
                        parts.size == 2 -> {
                            LabelEntry(0, parts[0], parseKind(parts[1]))
                        }
                        parts.size == 1 -> {
                            LabelEntry(0, parts[0], guessKind(parts[0]))
                        }
                        else -> null
                    }
                }
                .sortedBy { it.index }
                .mapIndexed { i, e -> e.copy(index = i) }
        } catch (_: Throwable) {
            emptyList()
        }
        if (fromAsset.isNotEmpty()) return fromAsset

        // Fallback: infer class count from output tensor, use embedded defaults.
        val outShape = try {
            interp.getOutputTensor(0).shape()
        } catch (_: Throwable) {
            intArrayOf(1, DEFAULT_LABELS.size)
        }
        val n = outShape.last().coerceAtLeast(1)
        return DEFAULT_LABELS.take(min(n, DEFAULT_LABELS.size)).mapIndexed { i, (label, kind) ->
            LabelEntry(i, label, kind)
        }
    }

    private fun preprocess(src: Bitmap): ByteBuffer {
        val scaled = if (src.width == INPUT_SIZE && src.height == INPUT_SIZE) {
            src
        } else {
            Bitmap.createScaledBitmap(src, INPUT_SIZE, INPUT_SIZE, true)
        }
        val buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * NUM_CHANNELS)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        var i = 0
        while (i < pixels.size) {
            val p = pixels[i]
            buffer.putFloat(((p shr 16) and 0xFF) / 255f)
            buffer.putFloat(((p shr 8) and 0xFF) / 255f)
            buffer.putFloat((p and 0xFF) / 255f)
            i++
        }
        if (scaled !== src) {
            try { scaled.recycle() } catch (_: Throwable) {}
        }
        buffer.rewind()
        return buffer
    }


    /** Align TFLite class names with [WildlifeEvidenceDetector] lexicon display labels. */
    private fun displayLabel(raw: String): String = when (raw.lowercase()) {
        "woodchuck" -> "groundhog"
        "hole_entry" -> "hole / chew opening"
        "trap_cage" -> "live cage trap"
        "droppings" -> "droppings / guano"
        "nest" -> "nesting material"
        else -> raw.replace('_', ' ')
    }

    private fun parseKind(raw: String): WildlifeEvidenceHit.Kind =
        when (raw.trim().uppercase()) {
            "SPECIES" -> WildlifeEvidenceHit.Kind.SPECIES
            "ENTRY" -> WildlifeEvidenceHit.Kind.ENTRY
            "DAMAGE" -> WildlifeEvidenceHit.Kind.DAMAGE
            "ACTIVITY" -> WildlifeEvidenceHit.Kind.ACTIVITY
            "EQUIPMENT" -> WildlifeEvidenceHit.Kind.EQUIPMENT
            else -> WildlifeEvidenceHit.Kind.OTHER
        }

    private fun guessKind(label: String): WildlifeEvidenceHit.Kind {
        val t = label.lowercase()
        return when {
            t.contains("trap") || t.contains("cage") || t.contains("netting") || t.contains("ladder") ->
                WildlifeEvidenceHit.Kind.EQUIPMENT
            t.contains("hole") || t.contains("entry") || t.contains("vent") || t.contains("gap") ->
                WildlifeEvidenceHit.Kind.ENTRY
            t.contains("dropping") || t.contains("guano") || t.contains("chew") || t.contains("nest") ->
                WildlifeEvidenceHit.Kind.DAMAGE
            t.contains("track") || t.contains("trail") || t.contains("activity") ->
                WildlifeEvidenceHit.Kind.ACTIVITY
            t == "other" || t == "background" -> WildlifeEvidenceHit.Kind.OTHER
            else -> WildlifeEvidenceHit.Kind.SPECIES
        }
    }

    private val DEFAULT_LABELS = listOf(
        "raccoon" to WildlifeEvidenceHit.Kind.SPECIES,
        "bat" to WildlifeEvidenceHit.Kind.SPECIES,
        "squirrel" to WildlifeEvidenceHit.Kind.SPECIES,
        "woodchuck" to WildlifeEvidenceHit.Kind.SPECIES,
        "skunk" to WildlifeEvidenceHit.Kind.SPECIES,
        "opossum" to WildlifeEvidenceHit.Kind.SPECIES,
        "bird" to WildlifeEvidenceHit.Kind.SPECIES,
        "droppings" to WildlifeEvidenceHit.Kind.DAMAGE,
        "hole_entry" to WildlifeEvidenceHit.Kind.ENTRY,
        "trap_cage" to WildlifeEvidenceHit.Kind.EQUIPMENT,
        "nest" to WildlifeEvidenceHit.Kind.DAMAGE,
        "other" to WildlifeEvidenceHit.Kind.OTHER
    )
}
