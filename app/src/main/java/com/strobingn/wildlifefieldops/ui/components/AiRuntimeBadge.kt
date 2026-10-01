package com.strobingn.wildlifefieldops.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.ai.fieldops.AiRuntimeStatus
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.SurfaceVariant
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary

@Composable
fun AiRuntimeBadge(
    status: AiRuntimeStatus,
    lastUsed: String = "",
    modifier: Modifier = Modifier
) {
    val used = if (lastUsed.isNotBlank()) {
        "Last draft: $lastUsed"
    } else {
        status.detail
    }
    Surface(
        modifier = modifier,
        color = SurfaceVariant,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "AI: ${status.label}",
                style = MaterialTheme.typography.labelMedium,
                color = PrimaryGreen
            )
            Text(
                used,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
fun AiRuntimeCard(status: AiRuntimeStatus, lastUsed: String = "", modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = BackgroundCard,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Offline AI status", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text("Ready: ${status.label}", style = MaterialTheme.typography.bodySmall, color = PrimaryGreen)
            Text(status.detail, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            if (lastUsed.isNotBlank()) {
                Text("Last used on this job: $lastUsed", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            }
        }
    }
}
