package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.BuildConfig
import com.strobingn.wildlifefieldops.ai.HybridAIService
import com.strobingn.wildlifefieldops.ai.OnDeviceLlm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@HiltViewModel
class AiAssistantViewModel @Inject constructor() : ViewModel() {

    private val welcomeMessage = buildString {
        append("Talk like you are on the job.\n\n")
        append("Ask about species, exclusion, timing, safety, or a quote.\n")
        if (BuildConfig.LLM_KEY_LENGTH >= 10) append("Grok key is in this APK.\n")
        else append("No Grok key in this APK.\n")
        if (OnDeviceLlm.hasHfToken()) append("Phone model can download on first live run.")
        else append("Phone model needs HF_TOKEN to download.")
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
            val reply = withContext(Dispatchers.IO) {
                val answer = HybridAIService.answerFieldTool(
                    toolTitle = "Field chat",
                    purpose = "Answer the technician in the field from the question they just asked.",
                    steps = listOf(
                        "Hudson Valley wildlife control",
                        "Do not invent openings, animals, or prices",
                        "Ask for the missing measurement instead of guessing"
                    ),
                    species = "",
                    siteNotes = trimmed
                )
                if (answer.source == "Offline") answer.text else "${answer.text}\n\n— ${answer.source}"
            }
            _messages.value = _messages.value + ChatMessage(reply, false)
            _isTyping.value = false
        }
    }
}
