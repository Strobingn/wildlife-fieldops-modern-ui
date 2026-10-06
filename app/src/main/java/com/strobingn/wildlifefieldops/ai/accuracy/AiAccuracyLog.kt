package com.strobingn.wildlifefieldops.ai.accuracy

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import java.util.UUID

/** One AI- or parser-filled field, and what Sir later saved there. */
@Serializable
data class AiFillRecord(
    val id: String = UUID.randomUUID().toString(),
    val source: String,
    val session: String,
    val field: String,
    val filled: String,
    val saved: String? = null,
    val filledAt: Long = 0L,
    val savedAt: Long? = null
) {
    val resolved: Boolean get() = saved != null
    val changed: Boolean get() = saved != null && AiAccuracyLedger.normalize(saved) != AiAccuracyLedger.normalize(filled)
}

data class AiFieldStat(
    val field: String,
    val saved: Int,
    val changed: Int,
    val sources: List<String>,
    val examples: List<AiFillRecord>
)

/**
 * Pure bookkeeping for the AI accuracy log. It only observes fills; it never
 * decides what gets filled.
 */
object AiAccuracyLedger {
    const val MAX_RECORDS = 500

    fun normalize(value: String): String =
        value.trim().replace(Regex("\\s+"), " ").lowercase(Locale.US)

    /** Fields that went from blank to filled between [before] and [after]. */
    fun filledFields(before: Map<String, String>, after: Map<String, String>): Map<String, String> =
        after.filter { (key, value) -> value.isNotBlank() && before[key].isNullOrBlank() }

    fun recordFill(
        records: List<AiFillRecord>,
        source: String,
        session: String,
        field: String,
        filled: String,
        now: Long
    ): List<AiFillRecord> {
        if (filled.isBlank()) return records
        // A later fill of the same field in the same session replaces the pending one.
        val kept = records.filterNot { it.session == session && it.field == field && !it.resolved }
        return (kept + AiFillRecord(source = source, session = session, field = field, filled = filled, filledAt = now))
            .takeLast(MAX_RECORDS)
    }

    /** Pairs pending fills for [session] with the values Sir saved. Blank saves count. */
    fun recordSaved(
        records: List<AiFillRecord>,
        session: String,
        saved: Map<String, String>,
        now: Long
    ): List<AiFillRecord> = records.map { rec ->
        if (rec.session != session || rec.resolved) return@map rec
        val value = saved[rec.field] ?: return@map rec
        rec.copy(saved = value.trim(), savedAt = now)
    }

    fun summary(records: List<AiFillRecord>, examplesPerField: Int = 3): List<AiFieldStat> =
        records.filter { it.resolved }
            .groupBy { it.field }
            .map { (field, list) ->
                val changed = list.filter { it.changed }
                AiFieldStat(
                    field = field,
                    saved = list.size,
                    changed = changed.size,
                    sources = list.map { it.source }.distinct(),
                    examples = changed.sortedByDescending { it.savedAt ?: 0L }.take(examplesPerField)
                )
            }
            .sortedWith(compareByDescending<AiFieldStat> { it.changed }.thenByDescending { it.saved }.thenBy { it.field })
}

/**
 * Local-only log file. Nothing here is synced, uploaded, or sent to an AI.
 * Writes happen off the main thread.
 */
class AiAccuracyLog private constructor(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val _records = MutableStateFlow(load())
    val records: StateFlow<List<AiFillRecord>> = _records.asStateFlow()

    private fun load(): List<AiFillRecord> = runCatching {
        if (!file.isFile) emptyList()
        else json.decodeFromString(ListSerializer(AiFillRecord.serializer()), file.readText())
    }.getOrDefault(emptyList())

    private fun update(block: (List<AiFillRecord>) -> List<AiFillRecord>) {
        val next = synchronized(lock) {
            val updated = block(_records.value)
            _records.value = updated
            updated
        }
        scope.launch {
            synchronized(lock) {
                runCatching {
                    file.parentFile?.mkdirs()
                    file.writeText(json.encodeToString(ListSerializer(AiFillRecord.serializer()), next))
                }
            }
        }
    }

    fun recordFill(source: String, session: String, field: String, filled: String) =
        update { AiAccuracyLedger.recordFill(it, source, session, field, filled, System.currentTimeMillis()) }

    /** Records every field that a fill changed from blank to filled. */
    fun recordFills(source: String, session: String, before: Map<String, String>, after: Map<String, String>) {
        val filled = AiAccuracyLedger.filledFields(before, after)
        if (filled.isEmpty()) return
        val now = System.currentTimeMillis()
        update { start ->
            filled.entries.fold(start) { acc, (field, value) ->
                AiAccuracyLedger.recordFill(acc, source, session, field, value, now)
            }
        }
    }

    fun recordSaved(session: String, saved: Map<String, String>) =
        update { AiAccuracyLedger.recordSaved(it, session, saved, System.currentTimeMillis()) }

    fun clear() = update { emptyList() }

    companion object {
        const val FILE_NAME = "ai_accuracy_log.json"
        @Volatile private var instance: AiAccuracyLog? = null

        fun get(context: Context): AiAccuracyLog = instance ?: synchronized(this) {
            instance ?: AiAccuracyLog(File(context.applicationContext.filesDir, FILE_NAME)).also { instance = it }
        }

        fun newSession(prefix: String): String = "$prefix:${UUID.randomUUID()}"
    }
}
