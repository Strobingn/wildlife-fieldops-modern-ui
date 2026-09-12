package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.strobingn.wildlifefieldops.data.remote.AiService
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary

/** Safe AI diagnostics block for Settings (no Hilt AiService constructor). */
@Composable
fun SettingsAiDiagnosticsBlock() {
    val aiDiag = remember {
        try {
            AiService.cloudDiagnosticsOnly()
        } catch (_: Exception) {
            "AI diagnostics unavailable"
        }
    }
    Text("AI (cloud + on-device LLM)", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
    Text(aiDiag, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
    Text(
        "Open AI Assistant to pick/download abliterated GGUFs: Qwen2.5-1.5B, Qwen2.5-3B (default), Llama-3.2-3B, or Qwen2.5-7B v3.",
        color = TextTertiary,
        style = MaterialTheme.typography.bodySmall
    )
}
