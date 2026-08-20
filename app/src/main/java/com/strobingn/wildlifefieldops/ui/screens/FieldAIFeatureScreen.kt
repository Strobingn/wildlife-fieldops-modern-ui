package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
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
import com.strobingn.wildlifefieldops.ai.HybridAIService
import com.strobingn.wildlifefieldops.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldAIFeatureScreen(
    featureId: String,
    onBack: () -> Unit
) {
    val feature = FieldAIFeatures.all.firstOrNull { it.id == featureId }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by rememberSaveable { mutableStateOf(false) }
    var species by rememberSaveable { mutableStateOf("") }
    var siteNotes by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }

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

    val hint = when (feature.id) {
        "exclusion_calc" -> "Example: 3 soffit gaps 18x3 in, 40 ft fascia, bats + squirrels, wood soffit to asphalt roof"
        else -> "Species signs, sizes, photos already taken, weather, access"
    }

    fun runLive() {
        if (loading) return
        loading = true
        error = ""
        copied = false
        scope.launch {
            val result = HybridAIService.answerFieldTool(
                toolTitle = feature.title,
                purpose = feature.purpose,
                steps = feature.steps,
                species = species,
                siteNotes = siteNotes
            )
            answer = result.text
            source = result.source
            if (result.source == "Offline") {
                error = "That box is only a live answer when Grok or MAF responds."
            }
            loading = false
        }
    }

    val report = buildString {
        appendLine(feature.title)
        appendLine("Species: ${species.ifBlank { "n/a" }}")
        appendLine("Site: ${siteNotes.ifBlank { "n/a" }}")
        appendLine()
        if (answer.isNotBlank()) {
            appendLine("Live answer ($source):")
            appendLine(answer)
        }
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

            Text("Field SOP (not AI)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            feature.steps.forEachIndexed { index, step ->
                Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
            }

            OutlinedTextField(
                value = species,
                onValueChange = { species = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Species") },
                placeholder = { Text("bat, squirrel, raccoon…") },
                singleLine = true
            )
            OutlinedTextField(
                value = siteNotes,
                onValueChange = { siteNotes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Openings / measurements") },
                placeholder = { Text(hint) },
                minLines = 3
            )

            Button(
                onClick = { runLive() },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Asking Grok…")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Run live AI on these measurements")
                }
            }

            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (answer.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (source == "Offline") "Offline SOP fallback" else "Live $source answer",
                            fontWeight = FontWeight.Bold
                        )
                        Text(answer, color = TextSecondary)
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
                    Text(if (copied) "Copied" else "Copy live answer")
                }
            }
        }
    }
}
