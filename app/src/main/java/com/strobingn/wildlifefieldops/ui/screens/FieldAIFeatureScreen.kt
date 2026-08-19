package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.ai.FieldAIFeatures
import com.strobingn.wildlifefieldops.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldAIFeatureScreen(
    featureId: String,
    onBack: () -> Unit
) {
    val feature = FieldAIFeatures.all.firstOrNull { it.id == featureId }
    val clipboard = LocalClipboardManager.current
    var copied by rememberSaveable { mutableStateOf(false) }

    if (feature == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("AI tool") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Text("Tool not found", modifier = Modifier.padding(padding).padding(16.dp))
        }
        return
    }

    val report = buildString {
        appendLine(feature.title)
        appendLine(feature.category)
        appendLine()
        appendLine(feature.purpose)
        appendLine()
        appendLine("Field steps:")
        feature.steps.forEachIndexed { i, step -> appendLine("${i + 1}. $step") }
        appendLine()
        appendLine("AI recommendation:")
        appendLine(feature.output)
    }.trim()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(feature.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AssistChip(onClick = {}, label = { Text(feature.category) })
            Text(feature.purpose, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("Field steps", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            feature.steps.forEachIndexed { index, step ->
                Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
            }

            Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI recommendation", fontWeight = FontWeight.Bold)
                    Text(feature.output, color = TextSecondary)
                }
            }

            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(report))
                    copied = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (copied) "Copied" else "Copy field report")
            }
        }
    }
}
