package com.strobingn.wildlifefieldops.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.data.remote.WeatherSnapshot
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary
import com.strobingn.wildlifefieldops.ui.viewmodel.WeatherUiState

@Composable
fun WeatherBanner(
    state: WeatherUiState,
    title: String = "Weather",
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        when (state) {
            is WeatherUiState.Idle, is WeatherUiState.Loading -> {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = PrimaryGreen)
                    Text("Loading $title…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            is WeatherUiState.Unavailable -> {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(state.reason, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry weather", tint = PrimaryGreen)
                    }
                }
            }
            is WeatherUiState.Ready -> {
                WeatherReadyContent(title = title, snap = state.snap, place = state.placeLabel, onRefresh = onRefresh)
            }
        }
    }
}

@Composable
private fun WeatherReadyContent(
    title: String,
    snap: WeatherSnapshot,
    place: String,
    onRefresh: () -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(weatherIcon(snap.condition), contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(28.dp))
                Column {
                    Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(place, color = TextTertiary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh weather", tint = TextSecondary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${snap.tempF}°F",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            snap.description.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            snap.humidity?.let {
                MetaChip(Icons.Default.WaterDrop, "$it% humidity")
            }
            snap.windMph?.let {
                MetaChip(Icons.Default.Air, "${"%.0f".format(it)} mph wind")
            }
        }
    }
}

@Composable
private fun MetaChip(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
        Text(text, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
    }
}

private fun weatherIcon(condition: String): ImageVector = when (condition.lowercase()) {
    "clear" -> Icons.Default.WbSunny
    "clouds", "mist", "fog", "haze", "smoke", "dust", "sand", "ash" -> Icons.Default.Cloud
    else -> Icons.Default.Cloud
}
