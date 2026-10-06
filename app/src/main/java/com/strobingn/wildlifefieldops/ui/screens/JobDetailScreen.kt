package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.ai.fieldops.AiRuntimeStatus
import com.strobingn.wildlifefieldops.ai.fieldops.FieldDate
import com.strobingn.wildlifefieldops.ai.fieldops.ManualField
import com.strobingn.wildlifefieldops.ai.fieldops.NextStepAttribution
import com.strobingn.wildlifefieldops.ai.fieldops.OperatorWins
import com.strobingn.wildlifefieldops.ai.fieldops.SpeciesJobLegal
import com.strobingn.wildlifefieldops.data.inspection.JobInspectionLink
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobPriority
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.pricing.isManual
import com.strobingn.wildlifefieldops.ui.components.*
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.components.WeatherBanner
import com.strobingn.wildlifefieldops.ui.viewmodel.JobAiViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.JobWorkspaceViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.LiveWeatherViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.JobsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    jobId: String,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToInvoice: (String) -> Unit,
    onNavigateToEstimate: (String) -> Unit,
    onNavigateToInspectionForm: (String) -> Unit,
    onNavigateToLiveCapture: (String) -> Unit,
    onNavigateToVoiceLog: (String) -> Unit,
    onNavigateToTrapChecks: () -> Unit = {},
    onNavigateToInspection: (String) -> Unit = {},
    onNavigateToJob: (String) -> Unit = {},
    onNavigateToTodayRoute: () -> Unit = {},
    onNavigateToPhotos: () -> Unit = {},
    onBack: () -> Unit,
    viewModel: JobsViewModel = hiltViewModel(),
    workspaceViewModel: JobWorkspaceViewModel = hiltViewModel(),
    jobAiViewModel: JobAiViewModel = hiltViewModel(),
    trapCheckViewModel: com.strobingn.wildlifefieldops.ui.viewmodel.TrapCheckViewModel = hiltViewModel(),
    moneyViewModel: com.strobingn.wildlifefieldops.ui.viewmodel.MoneyFieldOpsViewModel = hiltViewModel(),
    customerFieldOpsViewModel: com.strobingn.wildlifefieldops.ui.viewmodel.CustomerFieldOpsViewModel = hiltViewModel(),
    searchFieldOpsViewModel: com.strobingn.wildlifefieldops.ui.viewmodel.SearchFieldOpsViewModel = hiltViewModel(),
    inspectionsViewModel: com.strobingn.wildlifefieldops.ui.viewmodel.InspectionsViewModel = hiltViewModel(),
    weatherAlertsViewModel: com.strobingn.wildlifefieldops.ui.viewmodel.WeatherAlertsViewModel = hiltViewModel()
) {
    val job by viewModel.getJobById(jobId).collectAsState(initial = null)
    val customerDraft by workspaceViewModel.draft.collectAsState()
    val customerQuery by workspaceViewModel.searchQuery.collectAsState()
    val customerMatches by workspaceViewModel.matches.collectAsState()
    val customerSaving by workspaceViewModel.isSaving.collectAsState()
    val summary by jobAiViewModel.summary.collectAsState()
    val summaryLoading by jobAiViewModel.summaryLoading.collectAsState()
    val aiMessage by jobAiViewModel.message.collectAsState()
    val nextStepLoading by jobAiViewModel.nextStepLoading.collectAsState()
    val nextStepDraft by jobAiViewModel.nextStepDraft.collectAsState()
    val weatherVm: LiveWeatherViewModel = hiltViewModel()
    val weatherState by weatherVm.state.collectAsState()
    val allTraps by trapCheckViewModel.traps.collectAsState()
    val allInspections by inspectionsViewModel.inspections.collectAsState()
    val allJobs by viewModel.jobs.collectAsState()

    var showStatusDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var speciesText by remember { mutableStateOf("") }
    var legalNotesText by remember { mutableStateOf("") }
    var nextStepText by remember { mutableStateOf("") }
    var nextStepDueText by remember { mutableStateOf("") }
    var fieldError by remember { mutableStateOf<String?>(null) }
    var confirmCatalog by remember { mutableStateOf(false) }

    job?.let { currentJob ->
        LaunchedEffect(currentJob.id, currentJob.customerId) {
            workspaceViewModel.loadForJob(currentJob)
        }
        LaunchedEffect(currentJob.id) {
            speciesText = currentJob.confirmedSpecies
            legalNotesText = currentJob.legalNotes
            nextStepText = currentJob.nextStep
            nextStepDueText = currentJob.nextStepDueAt?.let { FieldDate.formatDay(it) }.orEmpty()
            fieldError = null
        }
        var legalManual by remember(currentJob.id) {
            mutableStateOf(currentJob.pricing.isManual(ManualField.LEGAL_NOTES) || currentJob.legalNotes.isNotBlank())
        }
        var nextStepPreview by remember(currentJob.id) { mutableStateOf<String?>(null) }
        var acceptedSuggestion by remember(currentJob.id) { mutableStateOf<String?>(null) }
        var acceptedSource by remember(currentJob.id) { mutableStateOf<String?>(null) }
        LaunchedEffect(nextStepDraft) {
            val draft = nextStepDraft ?: return@LaunchedEffect
            val source = AiRuntimeStatus.wireName(draft.source)
            if (nextStepText.isBlank()) {
                nextStepText = draft.text
                acceptedSuggestion = draft.text
                acceptedSource = source
                nextStepPreview = null
            } else {
                nextStepPreview = OperatorWins.preview(nextStepText, draft.text, manual = true)
            }
            if (nextStepDueText.isBlank() && draft.dueAt != null) {
                nextStepDueText = FieldDate.formatDay(draft.dueAt)
            }
        }
        LaunchedEffect(currentJob.id, currentJob.latitude, currentJob.longitude, currentJob.address) {
            weatherVm.loadJobWeather(currentJob.latitude, currentJob.longitude, currentJob.address)
        }
        LaunchedEffect(
            currentJob.id,
            currentJob.latitude,
            currentJob.longitude,
            currentJob.address,
            currentJob.status,
            currentJob.scheduledDate
        ) {
            weatherAlertsViewModel.trackJobs(listOf(currentJob))
        }
        val jobWeather by weatherAlertsViewModel.jobAlerts.collectAsState()
        val weatherLines = jobWeather[currentJob.id].orEmpty()
        val linkedInspections = JobInspectionLink.linkedTo(currentJob.id, allInspections)
        fun openLinkedJobInspection() {
            when (val dest = JobInspectionLink.destination(currentJob.id, allInspections)) {
                is JobInspectionLink.Destination.Existing -> onNavigateToInspection(dest.inspectionId)
                is JobInspectionLink.Destination.NewForm -> onNavigateToInspectionForm(dest.jobId)
            }
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Job Details", color = TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showStatusDialog = true }) {
                            Icon(Icons.Default.Flag, contentDescription = "Change Status", tint = TextSecondary)
                        }
                        IconButton(onClick = { onNavigateToEdit(currentJob.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary)
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
                )
            },
            containerColor = BackgroundDark
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title and Status
                Card(
                    colors = CardDefaults.cardColors(containerColor = BackgroundCard),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            currentJob.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(status = currentJob.status)
                            Spacer(modifier = Modifier.width(8.dp))
                            PriorityBadge(priority = currentJob.priority)
                            Spacer(modifier = Modifier.width(8.dp))
                            TypeBadge(type = currentJob.type)
                        }
                        if (weatherLines.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            weatherLines.forEach { alert ->
                                JobWeatherWarningChip(alert.summary)
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                        if (!currentJob.syncError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Sync failed: ${currentJob.syncError}",
                                color = ErrorRed,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else if (!currentJob.isSynced) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Pending cloud sync — still on this phone. Settings → Sync Now.",
                                color = TextTertiary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                JobReachCustomerCard(
                    name = customerDraft.name.ifBlank { currentJob.customerName },
                    phone = customerDraft.phone
                )
                JobCustomerSection(
                    draft = customerDraft,
                    onDraftChange = workspaceViewModel::updateDraft,
                    searchQuery = customerQuery,
                    onSearchQueryChange = workspaceViewModel::searchCustomers,
                    matches = customerMatches,
                    onPickCustomer = workspaceViewModel::applyCustomer,
                    onNewCustomer = workspaceViewModel::startNewCustomer
                )
                JobDirectionsButton(
                    target = JobDirections.fromJob(
                        currentJob,
                        fullAddress = customerDraft.composedServiceAddress()
                    )
                )
                Button(
                    onClick = { workspaceViewModel.saveCustomerOnJob(currentJob) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentBlue,
                        contentColor = OnPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !customerSaving
                ) {
                    if (customerSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = OnPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        if (customerSaving) "Saving customer…" else "Save customer",
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { openLinkedJobInspection() },
                            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryGreen,
                                contentColor = OnPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Link job to inspection",
                                fontWeight = FontWeight.Bold,
                                maxLines = 2
                            )
                        }
                        if (linkedInspections.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { onNavigateToInspection(linkedInspections.first().id) },
                                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    "Open linked inspection",
                                    color = PrimaryGreen,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }

                Text("Actions", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(
                        label = "Estimate",
                        icon = Icons.Default.Calculate,
                        color = AccentBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToEstimate(currentJob.id) }
                    )
                    ActionButton(
                        label = "Invoice",
                        icon = Icons.Default.Receipt,
                        color = AccentPurple,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToInvoice(currentJob.id) }
                    )
                    ActionButton(
                        label = "Inspect",
                        icon = Icons.Default.Search,
                        color = AccentCyan,
                        modifier = Modifier.weight(1f),
                        onClick = { openLinkedJobInspection() }
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(
                        label = "Photos",
                        icon = Icons.Default.PhotoCamera,
                        color = AccentBlue,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToPhotos
                    )
                    ActionButton(
                        label = "Live AI",
                        icon = Icons.Default.Videocam,
                        color = PrimaryGreen,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToLiveCapture(currentJob.id) }
                    )
                    ActionButton(
                        label = "Voice log",
                        icon = Icons.Default.Mic,
                        color = AccentBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToVoiceLog(currentJob.id) }
                    )
                }

                JobStatusPipelineCard(
                    job = currentJob,
                    onSetStatus = { viewModel.updateJobStatus(currentJob.id, it) }
                )
                TextButton(onClick = onNavigateToTodayRoute) {
                    Text("Today's route", color = PrimaryGreen)
                }
                JobPaymentsCard(job = currentJob, onSave = { viewModel.saveJobExtras(currentJob.id, it) })
                JobSignatureCard(
                    job = currentJob,
                    customerName = customerDraft.name.ifBlank { currentJob.customerName },
                    onSave = { viewModel.saveJobExtras(currentJob.id, it) }
                )
                RepeatCustomerHistoryCard(
                    current = currentJob,
                    jobs = allJobs,
                    onOpen = onNavigateToJob
                )
                JobLinkedInspectionsCard(
                    job = currentJob,
                    inspections = allInspections,
                    onOpen = onNavigateToInspection,
                    onUnlink = { inspectionsViewModel.unlinkInspectionFromJob(it) },
                    onLink = { inspectionsViewModel.linkInspectionToJob(it, currentJob.id) },
                    onNew = { onNavigateToInspectionForm(currentJob.id) }
                )
                JobExclusionCard(job = currentJob, onSave = { viewModel.saveJobExtras(currentJob.id, it) })

                // Job Details
                InfoCard(title = "Job Details") {
                    InfoRow(Icons.Default.Description, currentJob.description.ifBlank { "No description" })
                    if (currentJob.scheduledDate != null) {
                        InfoRow(
                            Icons.Default.CalendarToday,
                            SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                                .format(Date(currentJob.scheduledDate))
                        )
                    }
                    InfoRow(Icons.Default.PersonOutline, currentJob.assignedTo.ifBlank { "Unassigned" })
                    if (currentJob.estimatedValue > 0) {
                        val quote = com.strobingn.wildlifefieldops.pricing.PricingCalculator.compute(currentJob.pricing)
                        val label = if (quote.total.isOverridden) {
                            "Estimated: $" + String.format("%.2f", currentJob.estimatedValue) + " (manual)"
                        } else {
                            "Estimated: $" + String.format("%.2f", currentJob.estimatedValue)
                        }
                        InfoRow(Icons.Default.AttachMoney, label)
                    }
                    if (currentJob.actualCost > 0) {
                        InfoRow(Icons.Default.Money, "Actual: $${String.format("%.2f", currentJob.actualCost)}")
                    }
                }

                // Notes
                if (currentJob.notes.isNotBlank()) {
                    InfoCard(title = "Notes") {
                        Text(currentJob.notes, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }

                val runtime = jobAiViewModel.runtimeStatus
                AiRuntimeCard(
                    status = runtime,
                    lastUsed = currentJob.aiRuntime,
                    modifier = Modifier.fillMaxWidth()
                )

                val hintSpecies = speciesText.ifBlank { currentJob.type }
                val legal = SpeciesJobLegal.card(hintSpecies, legalNotesText)
                InfoCard(title = "Species safety & NY legal") {
                    OutlinedTextField(
                        value = speciesText,
                        onValueChange = { speciesText = it },
                        label = { Text("Confirmed species") },
                        supportingText = {
                            Text(
                                "Service type “${currentJob.type.ifBlank { "unset" }}” is a hint. This box saves only what you type.",
                                color = TextTertiary
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGreen,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Risk: ${legal.risk}", style = MaterialTheme.typography.labelMedium, color = StatusUrgent)
                    Text(
                        "Catalog hint (not saved until you insert it)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                    legal.catalogNotes.take(3).forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = legalNotesText,
                        onValueChange = {
                            legalNotesText = it
                            legalManual = true
                        },
                        label = { Text("Legal / safety notes (yours win)") },
                        supportingText = {
                            Text("Catalog is a suggestion. Clear this box and save to keep it blank.", color = TextTertiary)
                        },
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGreen,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = {
                        if (legalNotesText.isBlank()) {
                            legalNotesText = legal.catalogNotes.joinToString("\n")
                            legalManual = true
                        } else {
                            confirmCatalog = true
                        }
                    }) {
                        Text("Insert catalog", color = PrimaryGreen)
                    }
                    val catalogPreview = OperatorWins.preview(
                        legalNotesText,
                        legal.catalogNotes.joinToString("\n"),
                        legalManual
                    )
                    ApplySuggestionChip(catalogPreview) {
                        legalNotesText = it
                        legalManual = true
                    }
                }

                InfoCard(title = "Next step") {
                    Text(
                        "AI suggests; you edit and save. Due items also show on Home.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nextStepText,
                        onValueChange = { nextStepText = it },
                        label = { Text("Next field action") },
                        minLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGreen,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nextStepDueText,
                        onValueChange = {
                            nextStepDueText = it
                            fieldError = null
                        },
                        label = { Text("Due date (yyyy-MM-dd)") },
                        supportingText = { Text("Blank clears the due date. A bad date is not saved.", color = TextTertiary) },
                        isError = fieldError != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGreen,
                            unfocusedBorderColor = BorderDark,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            errorBorderColor = StatusUrgent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (fieldError != null) {
                        Text(fieldError!!, color = StatusUrgent, style = MaterialTheme.typography.labelSmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { jobAiViewModel.suggestNextStep(currentJob) },
                            enabled = !nextStepLoading,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen)
                        ) {
                            if (nextStepLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = PrimaryGreen)
                            else Text("Suggest")
                        }
                        Button(
                            onClick = {
                                val due = FieldDate.parseDay(nextStepDueText)
                                if (!due.ok) {
                                    fieldError = due.error
                                    return@Button
                                }
                                fieldError = null
                                val step = nextStepText.trim()
                                val source = NextStepAttribution.sourceForSave(
                                    typed = step,
                                    savedText = currentJob.nextStep,
                                    savedSource = currentJob.nextStepSource,
                                    acceptedSuggestion = acceptedSuggestion,
                                    acceptedSource = acceptedSource
                                )
                                viewModel.saveFieldOps(
                                    job = currentJob,
                                    confirmedSpecies = speciesText.trim(),
                                    legalNotes = legalNotesText.trim(),
                                    nextStep = step,
                                    nextStepDueAt = due.millis,
                                    nextStepSource = source,
                                    aiRuntime = AiRuntimeStatus.wireName(runtime.mode)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                        ) {
                            Text("Save field notes")
                        }
                    }
                    val sourceNow = NextStepAttribution.sourceForSave(
                        typed = nextStepText,
                        savedText = currentJob.nextStep,
                        savedSource = currentJob.nextStepSource,
                        acceptedSuggestion = acceptedSuggestion,
                        acceptedSource = acceptedSource
                    )
                    Text(
                        "Source: ${sourceNow.ifBlank { "none" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                    ApplySuggestionChip(nextStepPreview) {
                        nextStepText = it
                        acceptedSuggestion = it
                        acceptedSource = nextStepDraft?.source?.let { mode -> AiRuntimeStatus.wireName(mode) }
                            ?: acceptedSource
                        nextStepPreview = null
                    }
                    currentJob.nextStepDueAt?.let { due ->
                        Text(
                            "Due: ${SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault()).format(Date(due))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    }
                }

                // AI summary + estimate
                InfoCard(title = "AI tools (${jobAiViewModel.providerLabel})") {
                    Text(
                        if (jobAiViewModel.isConfigured) {
                            "Generate a handoff summary or open Estimate and draft from notes."
                        } else {
                            "Offline mode: heuristics still work. Cloud Grok needs Supabase, or download the on-device model."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { jobAiViewModel.generateSummary(currentJob) },
                            enabled = !summaryLoading,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentPurple)
                        ) {
                            if (summaryLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = AccentPurple
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(if (summaryLoading) "Writing…" else "Summary")
                        }
                        Button(
                            onClick = { onNavigateToEstimate(currentJob.id) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentBlue,
                                contentColor = OnPrimary
                            )
                        ) {
                            Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Estimate")
                        }
                    }
                    if (!aiMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(aiMessage!!, style = MaterialTheme.typography.labelSmall, color = PrimaryGreen)
                    }
                    if (!summary.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            summary!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { jobAiViewModel.appendSummaryToNotes(currentJob) }) {
                            Text("Save summary to notes", color = PrimaryGreen)
                        }
                    }
                }

                // Primary edit — always available after a job is entered
                Button(
                    onClick = { onNavigateToEdit(currentJob.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryGreen,
                        contentColor = OnPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit job", fontWeight = FontWeight.Bold)
                }

                Text("Site weather", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                WeatherBanner(
                    state = weatherState,
                    title = "Job site",
                    onRefresh = {
                        weatherVm.loadJobWeather(currentJob.latitude, currentJob.longitude, currentJob.address)
                    }
                )

                JobMoneySection(job = currentJob, moneyVm = moneyViewModel)

                JobBatch4Section(job = currentJob, customerVm = customerFieldOpsViewModel)

                JobBatch5Section(job = currentJob, searchVm = searchFieldOpsViewModel)

                JobBatch2Section(
                    job = currentJob,
                    traps = allTraps.filter { it.jobId == currentJob.id },
                    weatherState = weatherState,
                    trapVm = trapCheckViewModel,
                    onOpenTrapChecks = onNavigateToTrapChecks
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Created: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(currentJob.createdAt))}" +
                        " · Updated: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(currentJob.updatedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }

        // Status Change Dialog
        if (confirmCatalog) {
            AlertDialog(
                onDismissRequest = { confirmCatalog = false },
                containerColor = BackgroundCard,
                title = { Text("Replace your notes?", color = TextPrimary) },
                text = { Text("Insert catalog replaces the legal notes you typed.", color = TextSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        legalNotesText = SpeciesJobLegal.catalogText(speciesText.ifBlank { currentJob.type })
                        legalManual = true
                        confirmCatalog = false
                    }) { Text("Replace", color = PrimaryGreen) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmCatalog = false }) { Text("Keep mine", color = TextSecondary) }
                }
            )
        }

        if (showStatusDialog) {
            AlertDialog(
                onDismissRequest = { showStatusDialog = false },
                title = { Text("Change Status", color = TextPrimary) },
                text = {
                    Column {
                        com.strobingn.wildlifefieldops.ai.fieldops.JobStatusPipeline.stages.forEach { status ->
                            TextButton(
                                onClick = {
                                    viewModel.updateJobStatus(currentJob.id, status)
                                    showStatusDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    com.strobingn.wildlifefieldops.ai.fieldops.JobStatusPipeline.label(status),
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showStatusDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = BackgroundCard
            )
        }

        // Delete Confirmation
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Job?", color = TextPrimary) },
                text = { Text("This action cannot be undone.", color = TextSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteJob(currentJob)
                        showDeleteDialog = false
                        onBack()
                    }) {
                        Text("Delete", color = ErrorRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = BackgroundCard
            )
        }
    } ?: run {
        // Loading or not found
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryGreen)
        }
    }
}

@Composable
private fun StatusBadge(status: JobStatus) {
    val color = jobStatusColor(status)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            com.strobingn.wildlifefieldops.ai.fieldops.JobStatusPipeline.label(status),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun PriorityBadge(priority: JobPriority) {
    val color = when (priority) {
        JobPriority.LOW -> TextSecondary
        JobPriority.MEDIUM -> StatusPending
        JobPriority.HIGH -> ErrorRed
        JobPriority.URGENT -> StatusUrgent
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(priority.name, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun TypeBadge(type: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(type, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}
