package com.strobingn.wildlifefieldops.ai.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val message: String = "On-device LiteRT is disabled on this build. Cloud Grok is used instead.",
    val lastError: String? = null
)

/**
 * LiteRT-LM 0.16.1 cannot compile on the project Kotlin 1.9 / AGP 8.2 stack.
 * Keep this type so Settings and HybridAI still inject, but do not load a local engine.
 * The green on-device path is feat/real-local-llm-ai (llama.cpp).
 */
@Singleton
class LocalLlmEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: LocalLlmDownloader
) {
    private val prefs = context.getSharedPreferences("local_llm", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(
        LocalLlmStatus(
            phase = LocalLlmPhase.MISSING,
            model = selectedSpec(),
            message = "Local LiteRT runtime removed so CI can produce an APK. Use cloud Grok, or install the feat/real-local-llm-ai build for on-device GGUF."
        )
    )
    val status: StateFlow<LocalLlmStatus> = _status.asStateFlow()
    val downloadProgress = downloader.progress
    val isReady: Boolean = false

    fun selectedSpec(): LocalLlmSpec =
        LocalLlmCatalog.byId(prefs.getString(KEY_MODEL, LocalLlmCatalog.DEFAULT_ID))

    fun selectModel(id: String) {
        prefs.edit().putString(KEY_MODEL, id).apply()
        _status.value = _status.value.copy(model = LocalLlmCatalog.byId(id))
    }

    suspend fun downloadSelected(): Result<Unit> =
        Result.failure(IllegalStateException("On-device LiteRT is not in this APK."))

    suspend fun load(): Result<Unit> =
        Result.failure(IllegalStateException("On-device LiteRT is not in this APK."))

    suspend fun complete(userMessage: String, systemExtra: String = ""): String =
        "Local LiteRT is disabled on this build. Use cloud Grok."

    fun resetConversation() {}
    fun unload() {}
    fun deleteSelected() {}

    companion object {
        private const val KEY_MODEL = "selected_model_id"
    }
}
