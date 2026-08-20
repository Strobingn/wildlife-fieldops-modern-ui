package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.ai.FieldAIFeatures
import com.strobingn.wildlifefieldops.ai.operations.IndividualAIToolCatalog
import com.strobingn.wildlifefieldops.ui.components.FieldCard
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.AIOperationsViewModel

private val HubCategories = listOf(
    "Operations",
    "Dispatch",
    "Money",
    "Records",
    "Field",
    "Insights",
    "Playbooks",
    "Chat"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIHubScreen(
    onOpenChat: () -> Unit,
    onOpenOperations: (String?) -> Unit,
    onOpenFeature: (String) -> Unit,
    onOpenDrawer: () -> Unit = {},
    viewModel: AIOperationsViewModel = hiltViewModel()
) {
    val dashboard by viewModel.dashboard.collectAsState()
    var category by rememberSaveable { mutableStateOf("Operations") }
    var query by rememberSaveable { mutableStateOf("") }

    val tools = remember(category, query) {
        IndividualAIToolCatalog.tools.filter { tool ->
            val inCategory = category == "Operations" || tool.category == category
            val matchesQuery = query.isBlank() ||
                tool.title.contains(query, ignoreCase = true) ||
                tool.purpose.contains(query, ignoreCase = true) ||
                tool.category.contains(query, ignoreCase = true)
            inCategory && matchesQuery
        }
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
                        Text(
                            "AI Operations",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Live Grok on a tool · SOP is labeled SOP",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(AccentPurple.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentPurple)
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = FieldShapes.hero,
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(listOf(GradientStart, GradientMid, PrimaryContainer)),
                                FieldShapes.hero
                            )
                            .padding(18.dp)
                    ) {
                        Column {
                            Text(
                                "Type the job. Run Grok. That is the AI.",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Checklists and job scores are not a model.",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search tools") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = FieldShapes.button
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HubCategories.forEach { label ->
                        FilterChip(
                            selected = category == label,
                            onClick = { category = label },
                            label = { Text(label) }
                        )
                    }
                }
            }

            when (category) {
                "Chat" -> item {
                    HubActionCard(
                        title = "AI Chat",
                        subtitle = "Ask Grok about species, safety, and equipment",
                        icon = Icons.Default.Chat,
                        onClick = onOpenChat
                    )
                }
                "Playbooks" -> {
                    itemsIndexed(FieldAIFeatures.all) { index, feature ->
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
                                    Text(feature.purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                "Insights" -> item {
                    FieldCard(onClick = { onOpenOperations(null) }) {
                        Column {
                            Text("Job numbers and rule scores", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${dashboard.business.totalJobs} jobs · close rate ${dashboard.business.closeRatePercent}% · not a model",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Open a tool and run Grok if you want AI", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                else -> {
                    if (category == "Operations") {
                        item {
                            HubActionCard(
                                title = "AI Chat",
                                subtitle = "Live Grok — species, safety, equipment",
                                icon = Icons.Default.Chat,
                                onClick = onOpenChat
                            )
                        }
                    }
                    IndividualAIToolCatalog.categories
                        .filter { category == "Operations" || it == category }
                        .forEach { group ->
                            val groupTools = tools.filter { it.category == group }
                            if (groupTools.isNotEmpty()) {
                                item {
                                    Text(
                                        group,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                itemsIndexed(groupTools, key = { _, tool -> tool.id }) { index, tool ->
                                    FieldCard(onClick = { onOpenOperations(tool.id) }) {
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
                                                Text(tool.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                                                Text(tool.purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    if (tools.isEmpty()) {
                        item {
                            Text(
                                "No tools match that search.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun HubActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    FieldCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AccentPurple.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = AccentPurple)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
