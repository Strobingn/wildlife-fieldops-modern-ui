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
    val model: LocalLlmSpec = LocalLlmCatalog.DEFAULT,
    val backendLabel: String = "",
    val message: String = "Preparing baked Qwen3.5 0.8B abliterated…",
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

    fun selectedSpec(): LocalLlmSpec =
        LocalLlmCatalog.byId(prefs.getString(KEY_MODEL, LocalLlmCatalog.DEFAULT_ID))

    fun selectModel(id: String) {
        prefs.edit().putString(KEY_MODEL, id).apply()
        val spec = LocalLlmCatalog.byId(id)
        if (loadedModelId != spec.id) {
            closeEngine()
            _status.value = statusFor(spec)
        }
    }

    suspend fun downloadSelected(): Result<Unit> {
        val spec = selectedSpec()
        _status.value = _status.value.copy(
            phase = LocalLlmPhase.DOWNLOADING,
            model = spec,
            message = if (spec.bakedInDefault) "Extracting baked ${spec.displayName}…"
            else "Downloading ${spec.displayName} (${spec.sizeLabel})…"
        )
        val result = downloader.ensureAvailable(spec)
        return result.fold(
            onSuccess = {
                _status.value = _status.value.copy(
                    phase = LocalLlmPhase.LOADING,
                    message = "${spec.displayName} ready on disk. Loading…"
                )
                load().map { }
            },
            onFailure = { err ->
                _status.value = LocalLlmStatus(
                    phase = LocalLlmPhase.ERROR,
                    model = spec,
                    message = "Could not place model on disk",
                    lastError = err.message
                )
                Result.failure(err)
            }
        )
    }

    suspend fun load(): Result<Unit> = mutex.withLock {
        val spec = selectedSpec()
        if (!downloader.isOnDisk(spec)) {
            val placed = downloader.ensureAvailable(spec)
            if (placed.isFailure) {
                val msg = placed.exceptionOrNull()?.message ?: "${spec.displayName} is not on this phone yet."
                _status.value = LocalLlmStatus(LocalLlmPhase.MISSING, spec, message = msg)
                return Result.failure(placed.exceptionOrNull() ?: IllegalStateException(msg))
            }
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
                return@withLock "Local LLM is not loaded yet. The baked Qwen3.5 0.8B abliterated model is extracting or still packing."
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
        _status.value = statusFor(selectedSpec())
    }

    fun deleteSelected() {
        closeEngine()
        downloader.delete(selectedSpec())
        val spec = selectedSpec()
        _status.value = LocalLlmStatus(
            phase = LocalLlmPhase.MISSING,
            model = spec,
            message = if (spec.bakedInDefault)
                "${spec.displayName} removed from files. Re-extract from the APK to restore."
            else
                "${spec.displayName} removed from this phone."
        )
    }

    private fun statusFor(spec: LocalLlmSpec): LocalLlmStatus {
        return when {
            downloader.isOnDisk(spec) -> LocalLlmStatus(
                phase = LocalLlmPhase.MISSING,
                model = spec,
                message = "${spec.displayName} is on disk. Load it to chat offline."
            )
            downloader.hasBundledAsset(spec) -> LocalLlmStatus(
                phase = LocalLlmPhase.MISSING,
                model = spec,
                message = "${spec.displayName} is baked into this APK. Extract/load to use."
            )
            else -> LocalLlmStatus(
                phase = LocalLlmPhase.MISSING,
                model = spec,
                message = "Download ${spec.displayName} (${spec.sizeLabel}) to run on-device."
            )
        }
    }

    private fun readInitialStatus(): LocalLlmStatus = statusFor(selectedSpec())

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
