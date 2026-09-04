package com.strobingn.wildlifefieldops.ai.local

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.strobingn.wildlifefieldops.data.remote.AiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class LocalLlmPhase {
    MISSING,
    DOWNLOADING,
    LOADING,
    READY,
    ERROR
}

data class LocalLlmStatus(
    val phase: LocalLlmPhase = LocalLlmPhase.MISSING,
    val model: LocalLlmSpec = LocalLlmCatalog.QWEN3_06B,
    val backendLabel: String = "",
    val message: String = "Local LLM not downloaded",
    val lastError: String? = null
)

@Singleton
class LocalLlmEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: LocalLlmDownloader
) {
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var loadedModelId: String? = null
    private var loadedBackend: String = ""

    private val prefs = context.getSharedPreferences("local_llm", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(readInitialStatus())
    val status: StateFlow<LocalLlmStatus> = _status.asStateFlow()

    val downloadProgress = downloader.progress

    val isReady: Boolean
        get() = _status.value.phase == LocalLlmPhase.READY && conversation != null

    fun selectedSpec(): LocalLlmSpec = LocalLlmCatalog.byId(prefs.getString(KEY_MODEL, LocalLlmCatalog.DEFAULT_ID))

    fun selectModel(id: String) {
        prefs.edit().putString(KEY_MODEL, id).apply()
        val spec = LocalLlmCatalog.byId(id)
        if (loadedModelId != spec.id) {
            closeEngine()
            _status.value = LocalLlmStatus(
                phase = if (downloader.isDownloaded(spec)) LocalLlmPhase.MISSING else LocalLlmPhase.MISSING,
                model = spec,
                message = if (downloader.isDownloaded(spec))
                    "${spec.displayName} downloaded. Tap Load to run on-device."
                else
                    "${spec.displayName} — ${spec.sizeLabel}. Tap Download."
            )
        }
    }

    suspend fun downloadSelected(): Result<Unit> {
        val spec = selectedSpec()
        _status.value = _status.value.copy(
            phase = LocalLlmPhase.DOWNLOADING,
            model = spec,
            message = "Downloading ${spec.displayName} (${spec.sizeLabel})…"
        )
        val result = downloader.download(spec)
        return result.fold(
            onSuccess = {
                _status.value = _status.value.copy(
                    phase = LocalLlmPhase.MISSING,
                    message = "${spec.displayName} downloaded. Loading…"
                )
                load().map { }
            },
            onFailure = { err ->
                _status.value = LocalLlmStatus(
                    phase = LocalLlmPhase.ERROR,
                    model = spec,
                    message = "Download failed",
                    lastError = err.message
                )
                Result.failure(err)
            }
        )
    }

    suspend fun load(): Result<Unit> = mutex.withLock {
        val spec = selectedSpec()
        if (!downloader.isDownloaded(spec)) {
            val msg = "${spec.displayName} is not on this phone yet."
            _status.value = LocalLlmStatus(LocalLlmPhase.MISSING, spec, message = msg)
            return Result.failure(IllegalStateException(msg))
        }
        if (conversation != null && loadedModelId == spec.id) {
            _status.value = LocalLlmStatus(
                phase = LocalLlmPhase.READY,
                model = spec,
                backendLabel = loadedBackend,
                message = "On-device ${spec.shortLabel} ready ($loadedBackend)"
            )
            return Result.success(Unit)
        }
        closeEngineLocked()
        _status.value = LocalLlmStatus(
            phase = LocalLlmPhase.LOADING,
            model = spec,
            message = "Loading ${spec.displayName} into memory…"
        )
        return withContext(Dispatchers.IO) {
            try {
                Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
            } catch (t: Throwable) {
                Log.w(TAG, "setNativeMinLogSeverity failed", t)
            }
            val path = downloader.modelFile(spec).absolutePath
            val cache = FileCache(context)
            val backends = listOf("GPU" to gpuBackend(), "CPU" to cpuBackend())
            var lastError: Throwable? = null
            for ((label, backend) in backends) {
                try {
                    val cfg = EngineConfig(
                        modelPath = path,
                        backend = backend,
                        cacheDir = cache
                    )
                    val eng = Engine(cfg)
                    eng.initialize()
                    val convo = eng.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(AiService.WILDLIFE_SYSTEM_PROMPT),
                            samplerConfig = SamplerConfig(
                                topK = 40,
                                topP = 0.9f,
                                temperature = 0.6f
                            )
                        )
                    )
                    engine = eng
                    conversation = convo
                    loadedModelId = spec.id
                    loadedBackend = label
                    _status.value = LocalLlmStatus(
                        phase = LocalLlmPhase.READY,
                        model = spec,
                        backendLabel = label,
                        message = "On-device ${spec.shortLabel} ready ($label)"
                    )
                    return@withContext Result.success(Unit)
                } catch (t: Throwable) {
                    Log.w(TAG, "backend $label failed", t)
                    lastError = t
                }
            }
            val msg = lastError?.message ?: "Failed to initialize LiteRT-LM"
            _status.value = LocalLlmStatus(
                phase = LocalLlmPhase.ERROR,
                model = spec,
                message = "Could not start local model",
                lastError = msg
            )
            Result.failure(lastError ?: IllegalStateException(msg))
        }
    }

    suspend fun complete(userMessage: String, systemExtra: String = ""): String =
        mutex.withLock {
            val ready = conversation
            if (ready == null) {
                return@withLock "Local LLM is not loaded. Open Settings → Local LLM and tap Download / Load."
            }
            withContext(Dispatchers.IO) {
                val prompt = if (systemExtra.isBlank()) userMessage else "$systemExtra\n\n$userMessage"
                try {
                    val response: Message = ready.sendMessage(prompt)
                    extractText(response).ifBlank { "(empty on-device response)" }
                } catch (t: Throwable) {
                    Log.e(TAG, "generate failed", t)
                    "On-device model error: ${t.message ?: t.javaClass.simpleName}"
                }
            }
        }

    fun resetConversation() {
        try {
            conversation?.close()
        } catch (_: Throwable) {
        }
        conversation = null
        val eng = engine
        if (eng != null && loadedModelId != null) {
            try {
                conversation = eng.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(AiService.WILDLIFE_SYSTEM_PROMPT),
                        samplerConfig = SamplerConfig(topK = 40, topP = 0.9f, temperature = 0.6f)
                    )
                )
            } catch (t: Throwable) {
                Log.w(TAG, "reset conversation failed", t)
            }
        }
    }

    fun unload() {
        closeEngine()
        val spec = selectedSpec()
        _status.value = LocalLlmStatus(
            phase = if (downloader.isDownloaded(spec)) LocalLlmPhase.MISSING else LocalLlmPhase.MISSING,
            model = spec,
            message = if (downloader.isDownloaded(spec))
                "${spec.displayName} is on disk. Not loaded."
            else
                "${spec.displayName} not downloaded."
        )
    }

    fun deleteSelected() {
        closeEngine()
        downloader.delete(selectedSpec())
        val spec = selectedSpec()
        _status.value = LocalLlmStatus(
            phase = LocalLlmPhase.MISSING,
            model = spec,
            message = "${spec.displayName} removed from this phone."
        )
    }

    private fun readInitialStatus(): LocalLlmStatus {
        val spec = selectedSpec()
        return if (downloader.isDownloaded(spec)) {
            LocalLlmStatus(
                phase = LocalLlmPhase.MISSING,
                model = spec,
                message = "${spec.displayName} is on disk. Load it to chat offline."
            )
        } else {
            LocalLlmStatus(
                phase = LocalLlmPhase.MISSING,
                model = spec,
                message = "Download ${spec.displayName} (${spec.sizeLabel}) to run AI with no signal."
            )
        }
    }

    private fun closeEngine() {
        try {
            conversation?.close()
        } catch (_: Throwable) {
        }
        try {
            engine?.close()
        } catch (_: Throwable) {
        }
        conversation = null
        engine = null
        loadedModelId = null
        loadedBackend = ""
    }

    private fun closeEngineLocked() = closeEngine()

    private fun gpuBackend(): Backend = Backend.GPU()
    private fun cpuBackend(): Backend = Backend.CPU()

    private fun FileCache(context: Context): String =
        java.io.File(context.cacheDir, "litertlm").apply { mkdirs() }.absolutePath

    private fun extractText(message: Message): String {
        val direct = runCatching { message.text }.getOrNull()?.trim().orEmpty()
        if (direct.isNotBlank()) return direct
        return message.toString().trim()
    }

    companion object {
        private const val TAG = "LocalLlmEngine"
        private const val KEY_MODEL = "selected_model_id"
    }
}
