package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.ai.local.LocalLlmEngine
import com.strobingn.wildlifefieldops.ai.local.LocalLlmModelManager
import com.strobingn.wildlifefieldops.data.remote.AiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val aiService: AiService,
    private val localLlm: LocalLlmEngine,
    private val modelManager: LocalLlmModelManager
) : ViewModel() {

    private fun buildWelcome(): String = buildString {
        append("Hello — I'm your Wildlife FieldOps AI.\n\n")
        append("Answers come from a real LLM only:\n")
        append("• Cloud (SpaceXAI / Grok) when a key is baked into the APK\n")
        append("• On-device llama.cpp (abliterated GGUF) (${LocalLlmModelManager.MODEL_DISPLAY_NAME}) otherwise\n\n")
        append("Canned keyword / \"field knowledge\" stub lists have been removed.\n\n")
        append(aiService.configDiagnostics())
        if (!aiService.isConfigured && !localLlm.isReady) {
            append("\n\n⬇ Tap \"Download local model\" below (~940 MB, one-time) to enable offline generative AI.")
        }
    }

    private val _messages = MutableStateFlow(listOf(ChatMessage(buildWelcome(), false)))
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    val modelState = modelManager.state

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    init {
        modelManager.refreshState()
    }

    fun downloadLocalModel(force: Boolean = false) {
        if (_isDownloading.value) return
        _isDownloading.value = true
        viewModelScope.launch {
            val result = localLlm.ensureReady(forceRedownload = force)
            _isDownloading.value = false
            val note = if (result.isSuccess) {
                "✅ Local LLM ready (${LocalLlmModelManager.MODEL_DISPLAY_NAME}). Ask anything — answers are generated on-device."
            } else {
                "❌ Local LLM download/load failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"
            }
            _messages.value = _messages.value + ChatMessage(note, false)
        }
    }

    fun send(userMessage: String) {
        val trimmed = userMessage.trim()
        if (trimmed.isEmpty() || _isTyping.value) return
        _messages.value = _messages.value + ChatMessage(trimmed, true)
        _isTyping.value = true
        viewModelScope.launch {
            val reply = runCatching {
                if (!aiService.isConfigured && !localLlm.isReady) {
                    val edge = runCatching { aiService.askViaSupabase(trimmed) }.getOrNull()
                    if (!edge.isNullOrBlank() &&
                        !edge.startsWith("⚠️") &&
                        !edge.startsWith("Supabase") &&
                        !edge.startsWith("Network") &&
                        !edge.contains("Demo mode", ignoreCase = true)
                    ) {
                        return@runCatching "☁️ Supabase ai-assistant:\n\n$edge"
                    }
                }
                aiService.ask(trimmed)
            }.getOrElse { "AI error: ${it.message}" }
            _messages.value = _messages.value + ChatMessage(reply, false)
            _isTyping.value = false
        }
    }
}
