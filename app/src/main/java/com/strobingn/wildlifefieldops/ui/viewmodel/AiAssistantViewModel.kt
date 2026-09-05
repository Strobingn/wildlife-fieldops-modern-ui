package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.ai.local.LocalLlmEngine
import com.strobingn.wildlifefieldops.ai.local.LocalLlmModelManager
import com.strobingn.wildlifefieldops.ai.local.LocalLlmOption
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
        val sel = modelManager.selected
        append("Hello — I'm your on-device AI assistant.\n\n")
        append("Ask anything: general questions, writing help, tech, or field ops.\n\n")
        append("Backend preference (chat):\n")
        append("1) On-device LLM when downloaded (${sel.displayName}) — preferred / local-first\n")
        append("2) Cloud (SpaceXAI / Grok) only if local is not ready\n\n")
        append("Pick 3B (default, ~2.1 GB) or 7B v3 (~4.7 GB) below, then download/switch.\n\n")
        append("Responses are labeled 📱 On-device or ☁️ Cloud so you can tell which answered.\n\n")
        append(aiService.configDiagnostics())
        if (!localLlm.isReady) {
            append("\n\n⬇ Tap \"Download local model\" below (${sel.approxSizeLabel}) for preferred on-device answers.")
        } else {
            append("\n\n✅ Local model ready — chat will use on-device first.")
        }
    }

    private val _messages = MutableStateFlow(listOf(ChatMessage(buildWelcome(), false)))
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    val modelState = modelManager.state
    val selectedModelId = modelManager.selectedId

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    val catalog: List<LocalLlmOption> = modelManager.options()

    fun selectedOption(): LocalLlmOption = modelManager.selected

    fun isOptionDownloaded(option: LocalLlmOption): Boolean = modelManager.isOptionDownloaded(option)

    init {
        modelManager.refreshState()
    }

    fun selectModel(optionId: String) {
        if (_isDownloading.value) return
        viewModelScope.launch {
            val option = modelManager.optionById(optionId) ?: return@launch
            val result = localLlm.switchModel(option.id, downloadIfNeeded = modelManager.isOptionDownloaded(option))
            val note = when {
                result.isSuccess && modelManager.isModelReady() ->
                    "✅ Switched to ${option.displayName}. Ready on-device."
                result.isSuccess ->
                    "Selected ${option.displayName} (${option.approxSizeLabel}). Tap Download to install."
                else ->
                    "❌ Switch failed: ${result.exceptionOrNull()?.message ?: "unknown error"}"
            }
            _messages.value = _messages.value + ChatMessage(note, false)
        }
    }

    fun downloadLocalModel(force: Boolean = false) {
        if (_isDownloading.value) return
        _isDownloading.value = true
        viewModelScope.launch {
            val sel = modelManager.selected
            val result = localLlm.ensureReady(forceRedownload = force)
            _isDownloading.value = false
            val note = if (result.isSuccess) {
                "✅ Local LLM ready (${sel.displayName}). Ask anything — answers are generated on-device."
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
