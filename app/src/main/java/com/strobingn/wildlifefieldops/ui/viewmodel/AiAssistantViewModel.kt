package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val aiService: AiService
) : ViewModel() {

    private val welcomeMessage = buildString {
        append("Hello — I'm your Wildlife FieldOps AI (GrokAIV5).\n\n")
        append("Ask about inspections, jobs, trapping, exclusion, safety, estimates, customers, or daily workflow.\n")
        append("Optional Microsoft Agent Framework sidecar is used when AGENT_FRAMEWORK_URL is configured.\n")
        if (aiService.isConfigured) {
            append("\n✅ Live AI connected via ${aiService.providerLabel}.")
            append("\n${aiService.configDiagnostics()}")
        } else {
            append("\n⚠️ Live Grok key not baked into this APK.\n")
            append("\n${aiService.configDiagnostics()}")
            append("\n\nMAF sidecar still works if AGENT_FRAMEWORK_URL is set.")
            append("\nTo enable SpaceXAI in the APK:")
            append("\n1. Create a key at https://console.x.ai")
            append("\n2. GitHub secret name: XAI_API_KEY (exact)")
            append("\n3. Re-run the Android build workflow")
            append("\n4. Install the new APK (old installs keep empty key)")
        }
    }

    private val _messages = MutableStateFlow(listOf(ChatMessage(welcomeMessage, false)))
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    fun send(userMessage: String) {
        val trimmed = userMessage.trim()
        if (trimmed.isEmpty() || _isTyping.value) return
        _messages.value = _messages.value + ChatMessage(trimmed, true)
        _isTyping.value = true
        viewModelScope.launch {
            val reply = aiService.ask(trimmed)
            _messages.value = _messages.value + ChatMessage(reply, false)
            _isTyping.value = false
        }
    }
}
