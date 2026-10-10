package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.data.model.DefaultServiceTypes
import com.strobingn.wildlifefieldops.data.model.JobPriority
import com.strobingn.wildlifefieldops.data.model.JobCustomerDraft
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.data.remote.DictationJobParser
import com.strobingn.wildlifefieldops.data.remote.IntakeField
import com.strobingn.wildlifefieldops.data.remote.JobIntakeDraft
import com.strobingn.wildlifefieldops.data.remote.JobIntakeParser
import com.strobingn.wildlifefieldops.navigation.VoiceJobEntry
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.JobsViewModel

/** Dictate → instant heuristic fill → optional AI refine → create job. Save never waits on AI. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDictateScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit = onBack,
    onTypeManually: () -> Unit = onBack,
    viewModel: JobsViewModel = hiltViewModel()
) {
    val aiFillLoading by viewModel.aiFillLoading.collectAsState()
    val aiFillRefining by viewModel.aiFillRefining.collectAsState()
    val aiFillError by viewModel.aiFillError.collectAsState()
    val aiFillSource by viewModel.aiFillSource.collectAsState()
    var draft by remember { mutableStateOf<JobIntakeDraft?>(null) }
    var editedFields by remember { mutableStateOf(setOf<String>()) }
    var isSaving by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.warmupDictationEngine() }
    // AI accuracy log: observe only. Fill rules stay in JobIntakeParser.merge.
    val aiLog = com.strobingn.wildlifefieldops.ai.accuracy.AiAccuracyLog.get(androidx.compose.ui.platform.LocalContext.current)
    val aiSession = remember { com.strobingn.wildlifefieldops.ai.accuracy.AiAccuracyLog.newSession("dictate") }

    fun edit(key: String, value: String) {
        val current = draft ?: JobIntakeDraft()
        editedFields = editedFields + key
        draft = when (key) {
            IntakeField.TITLE -> current.copy(title = value)
            IntakeField.CUSTOMER -> current.copy(customerName = value)
            IntakeField.ADDRESS -> current.copy(address = value)
            IntakeField.TYPE -> current.copy(type = value)
            IntakeField.PRIORITY -> current.copy(priority = value)
            IntakeField.DESCRIPTION -> current.copy(description = value)
            IntakeField.NOTES -> current.copy(notes = value)
            IntakeField.PHONE -> current.copy(phone = value)
            IntakeField.EMAIL -> current.copy(email = value)
            IntakeField.CITY -> current.copy(city = value)
            IntakeField.STATE -> current.copy(state = value)
            IntakeField.ZIP -> current.copy(zip = value)
            IntakeField.SPECIES -> current.copy(species = value)
            IntakeField.STATUS -> current.copy(status = value)
            else -> current
        }
    }

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
            OutlinedButton(
                onClick = onTypeManually,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen)
            ) {
                Text(VoiceJobEntry.TYPE_MANUALLY_LABEL, fontWeight = FontWeight.SemiBold)
            }
            JobVoiceIntakePanel(
                aiFillLoading = aiFillLoading,
                aiFillError = aiFillError,
                aiFillSource = aiFillSource,
                onClearAiFeedback = { viewModel.clearAiFillError() },
                onFillFromDictation = { transcript, onFilled ->
                    viewModel.fillJobFromDictation(
                        transcript = transcript,
                        current = { draft },
                        editedFields = { editedFields },
                        onFilled = { d ->
                            aiLog.recordFills("Dictation", aiSession, dictationFillMap(draft), dictationFillMap(d))
                            draft = d
                            onFilled(d)
                        }
                    )
                },
                onApplyDraft = { draft = it }
            )
            if (aiFillRefining) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        color = TextSecondary,
                        strokeWidth = 2.dp
                    )
                    Text(
                        "AI refining…",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.skipAiRefine() }) {
                        Text("Skip", color = TextPrimary)
                    }
                }
            }
            draft?.let { d ->
                DictateReviewFields(d) { key, value -> edit(key, value) }
                Button(
                    onClick = {
                        if (isSaving) return@Button
                        isSaving = true
                        aiLog.recordSaved(aiSession, dictationSavedMap(d))
                        val priority = runCatching {
                            JobPriority.valueOf(d.priority.trim().uppercase())
                        }.getOrDefault(JobPriority.MEDIUM)
                        viewModel.saveJobWithSchedule(
                            existingJob = null,
                            customer = dictatedCustomer(d),
                            status = dictatedStatus(d.status),
                            scheduleAt = DictationJobParser.scheduleMillis(d.scheduleDay),
                            title = d.title.ifBlank { "Voice job" }.trim(),
                            description = d.description.trim(),
                            customerId = "",
                            customerName = d.customerName.trim(),
                            address = dictatedCustomer(d).composedServiceAddress(),
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
                    enabled = !isSaving && JobIntakeParser.canSave(d),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSaving) "Saving…" else "Create job", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun dictateFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryGreen,
    unfocusedBorderColor = BorderDark,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)

/** Fields the dictation fill can set. Priority counts only when it moves off the default. */
private fun dictationFillMap(d: JobIntakeDraft?): Map<String, String> {
    if (d == null) return emptyMap()
    return buildMap {
        put("Title", d.title)
        put("Customer", d.customerName)
        put("Address", d.address)
        put("Type", d.type)
        if (!d.priority.equals("MEDIUM", ignoreCase = true)) put("Priority", d.priority)
        put("Description", d.description)
        put("Notes", d.notes)
        put("Phone", d.phone)
        put("Email", d.email)
        put("Town", d.city)
        put("State", d.state)
        put("ZIP", d.zip)
        put("Species", d.species)
    }
}

private fun dictationSavedMap(d: JobIntakeDraft): Map<String, String> = mapOf(
    "Title" to d.title.ifBlank { "Voice job" }.trim(),
    "Customer" to d.customerName.trim(),
    "Address" to d.address.trim(),
    "Type" to d.type.trim(),
    "Priority" to d.priority.trim(),
    "Description" to d.description.trim(),
    "Notes" to d.notes.trim()
)

@Composable
private fun DictateField(
    value: String,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        colors = dictateFieldColors()
    )
}

/** Only the three job statuses Sir uses. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DictateStatusPicker(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Status", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            JobIntakeParser.allowedStatuses.forEach { s ->
                FilterChip(
                    selected = selected.equals(s, ignoreCase = true),
                    onClick = { onSelect(s) },
                    label = { Text(s, color = TextPrimary) }
                )
            }
        }
    }
}

internal fun dictatedCustomer(d: JobIntakeDraft) = JobCustomerDraft(
    name = d.customerName.trim(),
    phone = d.phone.trim(),
    email = d.email.trim(),
    address = d.address.trim(),
    city = d.city.trim(),
    state = d.state.trim(),
    zipCode = d.zip.trim()
)

internal fun dictatedStatus(raw: String): JobStatus? = when (raw.trim().lowercase()) {
    "in progress" -> JobStatus.IN_PROGRESS
    "completed" -> JobStatus.COMPLETED
    "scheduled" -> JobStatus.SCHEDULED
    else -> null
}

/** Review fields filled by dictation. Each spoken fact has its own field. */
@Composable
internal fun DictateReviewFields(d: JobIntakeDraft, edit: (String, String) -> Unit) {
                Text("Review before create", color = TextPrimary, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = d.title,
                    onValueChange = { edit(IntakeField.TITLE, it) },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = dictateFieldColors()
                )
                OutlinedTextField(
                    value = d.customerName,
                    onValueChange = { edit(IntakeField.CUSTOMER, it) },
                    label = { Text("Customer") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = dictateFieldColors()
                )
                DictateField(d.phone, "Phone") { edit(IntakeField.PHONE, it) }
                DictateField(d.email, "Email") { edit(IntakeField.EMAIL, it) }
                OutlinedTextField(
                    value = d.address,
                    onValueChange = { edit(IntakeField.ADDRESS, it) },
                    label = { Text("Street address") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = dictateFieldColors()
                )
                DictateField(d.city, "Town") { edit(IntakeField.CITY, it) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DictateField(d.state, "State", Modifier.weight(1f)) { edit(IntakeField.STATE, it) }
                    DictateField(d.zip, "ZIP", Modifier.weight(1f)) { edit(IntakeField.ZIP, it) }
                }
                DictateField(d.species, "Species") { edit(IntakeField.SPECIES, it) }
                OutlinedTextField(
                    value = d.type,
                    onValueChange = { edit(IntakeField.TYPE, it) },
                    label = { Text("Type") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = dictateFieldColors()
                )
                OutlinedTextField(
                    value = d.priority,
                    onValueChange = { edit(IntakeField.PRIORITY, it) },
                    label = { Text("Priority") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = dictateFieldColors()
                )
                DictateStatusPicker(d.status) { edit(IntakeField.STATUS, it) }
                OutlinedTextField(
                    value = d.description,
                    onValueChange = { edit(IntakeField.DESCRIPTION, it) },
                    label = { Text("Scope of work") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = dictateFieldColors()
                )
                OutlinedTextField(
                    value = d.notes,
                    onValueChange = { edit(IntakeField.NOTES, it) },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = dictateFieldColors()
                )
}
