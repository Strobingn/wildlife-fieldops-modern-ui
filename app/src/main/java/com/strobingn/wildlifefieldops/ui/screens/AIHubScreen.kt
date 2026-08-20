package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.BuildConfig
import com.strobingn.wildlifefieldops.ai.FieldAIFeatures
import com.strobingn.wildlifefieldops.ai.OnDeviceLlm
import com.strobingn.wildlifefieldops.ui.components.FieldCard
import com.strobingn.wildlifefieldops.ui.theme.AccentPurple
import com.strobingn.wildlifefieldops.ui.theme.SurfaceBright
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIHubScreen(
    onOpenChat: () -> Unit,
    onOpenOperations: (String?) -> Unit,
    onOpenFeature: (String) -> Unit,
    onOpenWalkTalk: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showTools by rememberSaveable { mutableStateOf(false) }

    val phoneReadyHint = if (OnDeviceLlm.hasHfToken()) "Phone model can download" else "Phone model needs HF_TOKEN"
    val grokHint = if (BuildConfig.LLM_KEY_LENGTH >= 10) "Grok key baked in" else "No Grok key in this APK"

    val features = FieldAIFeatures.all.filter { feature ->
        query.isBlank() ||
            feature.title.contains(query, ignoreCase = true) ||
            feature.purpose.contains(query, ignoreCase = true) ||
            feature.category.contains(query, ignoreCase = true)
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Open menu")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "$grokHint · $phoneReadyHint",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Text(
                    "On site: talk, shoot, quote. That is the whole loop.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                HubHero(
                    title = "Walk + quote",
                    subtitle = "Talk while you inspect. Add photos. Estimate writes itself.",
                    icon = Icons.Default.Mic,
                    onClick = onOpenWalkTalk
                )
            }
            item {
                HubHero(
                    title = "Ask",
                    subtitle = "Species, timing, exclusion, safety. Live model, not a script.",
                    icon = Icons.Default.Chat,
                    onClick = onOpenChat
                )
            }
            item {
                HubHero(
                    title = "Field tools",
                    subtitle = "25 job-specific prompts. You type the facts, then run AI.",
                    icon = Icons.Outlined.Handyman,
                    onClick = { showTools = !showTools }
                )
            }

            if (showTools) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Search bats, traps, estimate…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                    )
                }
                itemsIndexed(features, key = { _, f -> f.id }) { index, feature ->
                    FieldCard(onClick = { onOpenFeature(feature.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = SurfaceBright, shape = CircleShape) {
                                Text(
                                    "${index + 1}",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(feature.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                                Text(feature.purpose, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                        }
                    }
                }
                if (features.isEmpty()) {
                    item { Text("Nothing matches.", color = TextTertiary) }
                }
            }

            item {
                FilterChip(
                    selected = false,
                    onClick = { onOpenOperations(null) },
                    label = { Text("Job numbers (not AI)") }
                )
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun HubHero(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    FieldCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(AccentPurple.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}
