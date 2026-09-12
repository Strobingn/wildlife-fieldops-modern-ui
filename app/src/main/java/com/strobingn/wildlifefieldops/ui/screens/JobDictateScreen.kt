package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.data.model.DefaultServiceTypes
import com.strobingn.wildlifefieldops.data.model.JobPriority
import com.strobingn.wildlifefieldops.data.remote.JobIntakeDraft
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.JobsViewModel

/** Dictate → AI Fill → review → create job (New Job voice entry). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDictateScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit = onBack,
    viewModel: JobsViewModel = hiltViewModel()
) {
    val aiFillLoading by viewModel.aiFillLoading.collectAsState()
    val aiFillError by viewModel.aiFillError.collectAsState()
    val aiFillSource by viewModel.aiFillSource.collectAsState()
    var draft by remember { mutableStateOf<JobIntakeDraft?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Voice job intake", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            JobVoiceIntakePanel(
                aiFillLoading = aiFillLoading,
                aiFillError = aiFillError,
                aiFillSource = aiFillSource,
                onClearAiFeedback = { viewModel.clearAiFillError() },
                onFillFromDictation = { transcript, onFilled ->
                    viewModel.fillJobFromDictation(transcript) { d ->
                        draft = d
                        onFilled(d)
                    }
                },
                onApplyDraft = { draft = it }
            )
            draft?.let { d ->
                Text("Review before create", color = TextPrimary, fontWeight = FontWeight.Bold)
                Text("Title: ${d.title.ifBlank { "(none)" }}", color = TextSecondary)
                Text("Customer: ${d.customerName.ifBlank { "(none)" }}", color = TextSecondary)
                Text("Address: ${d.address.ifBlank { "(none)" }}", color = TextSecondary)
                Text("Type: ${d.type.ifBlank { "(none)" }} · Priority: ${d.priority}", color = TextSecondary)
                Text("Notes: ${d.description.ifBlank { d.notes }.ifBlank { "(none)" }}", color = TextSecondary)
                Button(
                    onClick = {
                        if (isSaving) return@Button
                        isSaving = true
                        val priority = runCatching {
                            JobPriority.valueOf(d.priority.trim().uppercase())
                        }.getOrDefault(JobPriority.MEDIUM)
                        viewModel.saveJobWithSchedule(
                            existingJob = null,
                            title = d.title.ifBlank { "Voice job" }.trim(),
                            description = d.description.trim(),
                            customerId = "",
                            customerName = d.customerName.trim(),
                            address = d.address.trim(),
                            type = DefaultServiceTypes.display(d.type.ifBlank { DefaultServiceTypes.all.first() }),
                            priority = priority,
                            estimatedValue = 0.0,
                            notes = d.notes.trim(),
                            appointmentTimes = emptyList()
                        ) {
                            isSaving = false
                            onCreated()
                        }
                    },
                    enabled = !isSaving && (d.title.isNotBlank() || d.customerName.isNotBlank() || d.address.isNotBlank()),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSaving) "Saving…" else "Create job", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
