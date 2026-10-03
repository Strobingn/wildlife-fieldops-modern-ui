package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.PestControl
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.ai.fieldops.DecLogExporter
import com.strobingn.wildlifefieldops.ai.fieldops.OpsLedger
import com.strobingn.wildlifefieldops.ai.fieldops.FieldDate
import com.strobingn.wildlifefieldops.ai.fieldops.TrapCheckItem
import com.strobingn.wildlifefieldops.ai.fieldops.TrapCheckPlanner
import com.strobingn.wildlifefieldops.ai.fieldops.TrapDueState
import com.strobingn.wildlifefieldops.data.model.CatchType
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.TrapLog
import com.strobingn.wildlifefieldops.data.model.TrapStatus
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.BorderDark
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.StatusUrgent
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary
import com.strobingn.wildlifefieldops.ui.viewmodel.LiveWeatherViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.TrapCheckViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.WeatherUiState
import com.strobingn.wildlifefieldops.util.DecLogShare
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrapCheckScreen(
    onBack: () -> Unit,
    onNavigateToJobDetail: (String) -> Unit = {},
    onOpenNwcoLog: () -> Unit = {},
    viewModel: TrapCheckViewModel = hiltViewModel()
) {
    val dueToday by viewModel.dueToday.collectAsState()
    val scheduled by viewModel.scheduled.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val customerJobs = jobs.filterNot { OpsLedger.isLedger(it) }
    val message by viewModel.message.collectAsState()
    val lastAdvice by viewModel.lastAdvice.collectAsState()
    val weatherVm: LiveWeatherViewModel = hiltViewModel()
    val weatherState by weatherVm.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var adviceJobId by remember { mutableStateOf("") }
    LaunchedEffect(customerJobs.map { it.id }) {
        if (adviceJobId.isBlank() || customerJobs.none { it.id == adviceJobId }) {
            adviceJobId = customerJobs.firstOrNull()?.id.orEmpty()
        }
    }
    var editing by remember { mutableStateOf<TrapLog?>(null) }
    var showTodayOnly by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { weatherVm.loadShopWeather() }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.clearMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trap checks", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenNwcoLog) {
                        Icon(Icons.Default.Assignment, contentDescription = "Official NWCO log", tint = TextSecondary)
                    }
                    IconButton(onClick = { DecLogShare.shareCsv(context, viewModel.decCsv()) }) {
                        Icon(Icons.Default.Share, contentDescription = "Export DEC log", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = PrimaryGreen,
                contentColor = OnPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add trap")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = BackgroundDark
    ) { padding ->
        val list = if (showTodayOnly) dueToday else scheduled
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                val adviceJob = customerJobs.firstOrNull { it.id == adviceJobId }
                key(adviceJobId) {
                    WeatherAdviceCard(
                        weatherState = weatherState,
                        savedAdvice = adviceJob?.weatherTrapAdvice.orEmpty(),
                        onSuggest = {
                            val snap = (weatherState as? WeatherUiState.Ready)?.snap
                            viewModel.draftWeatherAdvice(adviceJob, snap).text
                        },
                        draft = lastAdvice,
                        onAccept = { text ->
                            viewModel.acceptWeatherAdvice(adviceJobId, text, "manual")
                        },
                        jobPicker = {
                            SearchableJobPicker(
                                jobs = customerJobs,
                                selectedId = adviceJobId,
                                onSelect = { adviceJobId = it },
                                label = "Save advice on"
                            )
                        }
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = showTodayOnly,
                        onClick = { showTodayOnly = true },
                        label = { Text("Due today (${dueToday.size})") }
                    )
                    FilterChip(
                        selected = !showTodayOnly,
                        onClick = { showTodayOnly = false },
                        label = { Text("All traps (${scheduled.size})") }
                    )
                }
            }
            if (list.isEmpty()) {
                item {
                    Text(
                        if (showTodayOnly) "No traps due today. Add a set trap or open All traps."
                        else "No trap logs yet. Add a cage from here or drop a pin on the Property Map.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            items(list, key = { it.trap.id }) { item ->
                TrapCheckCard(
                    item = item,
                    onOpenJob = { if (item.trap.jobId.isNotBlank()) onNavigateToJobDetail(item.trap.jobId) },
                    onLog = { editing = item.trap }
                )
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showAdd) {
        TrapEditorDialog(
            jobs = customerJobs,
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { trap ->
                viewModel.saveTrap(trap)
                showAdd = false
            }
        )
    }
    editing?.let { trap ->
        TrapEditorDialog(
            jobs = customerJobs,
            initial = trap,
            onDismiss = { editing = null },
            onSave = { next ->
                viewModel.saveTrap(next)
                editing = null
            },
            onDelete = {
                viewModel.deleteTrap(trap)
                editing = null
            }
        )
    }
}

@Composable
fun WeatherAdviceCard(
    weatherState: WeatherUiState,
    savedAdvice: String,
    draft: com.strobingn.wildlifefieldops.ai.fieldops.WeatherAdviceDraft?,
    onSuggest: () -> String,
    onAccept: (String) -> Unit,
    adviceManual: Boolean = false,
    jobPicker: @Composable () -> Unit = {}
) {
    var edited by remember(savedAdvice) { mutableStateOf(savedAdvice) }
    var manual by remember(savedAdvice) { mutableStateOf(adviceManual || savedAdvice.isNotBlank()) }
    var preview by remember { mutableStateOf<String?>(null) }
    var pendingReplace by remember { mutableStateOf<String?>(null) }
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Weather-aware trap advice", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            val summary = when (weatherState) {
                is WeatherUiState.Ready -> weatherState.snap.summaryLine
                is WeatherUiState.Unavailable -> weatherState.reason
                WeatherUiState.Loading -> "Loading forecast…"
                WeatherUiState.Idle -> "Forecast not loaded"
            }
            Text(summary, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            if (draft?.skipUnsafe == true) {
                Text("UNSAFE / skip until conditions clear", color = StatusUrgent, fontWeight = FontWeight.Bold)
            }
            jobPicker()
            OutlinedTextField(
                value = edited,
                onValueChange = {
                    edited = it
                    manual = true
                },
                label = { Text("Advice (yours win)") },
                supportingText = { Text("Blank saves a blank. Suggest fills this only when it is empty.", color = TextTertiary) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val suggestion = onSuggest().trim()
                    if (suggestion.isBlank()) return@OutlinedButton
                    if (edited.isBlank()) {
                        edited = suggestion
                        preview = null
                    } else {
                        preview = com.strobingn.wildlifefieldops.ai.fieldops.OperatorWins.preview(
                            edited,
                            suggestion,
                            manual = manual
                        )
                        pendingReplace = suggestion
                    }
                }) { Text("Suggest") }
                Button(
                    onClick = { onAccept(edited.trim()) },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                ) { Text("Save advice") }
            }
            com.strobingn.wildlifefieldops.ui.components.ApplySuggestionChip(preview) {
                edited = it
                manual = true
                preview = null
                pendingReplace = null
            }
        }
    }
    pendingReplace?.let { suggestion ->
        AlertDialog(
            onDismissRequest = { pendingReplace = null },
            containerColor = BackgroundCard,
            title = { Text("Replace advice?", color = TextPrimary) },
            text = { Text("This replaces the advice you typed.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    edited = suggestion
                    manual = true
                    preview = null
                    pendingReplace = null
                }) { Text("Replace", color = PrimaryGreen) }
            },
            dismissButton = {
                TextButton(onClick = { pendingReplace = null }) { Text("Keep mine", color = TextSecondary) }
            }
        )
    }
}

@Composable
private fun TrapCheckCard(
    item: TrapCheckItem,
    onOpenJob: () -> Unit,
    onLog: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val dueColor = when (item.dueState) {
        TrapDueState.OVERDUE -> StatusUrgent
        TrapDueState.DUE_TODAY -> PrimaryGreen
        else -> TextTertiary
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PestControl, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    item.trap.trapId.ifBlank { "Trap" } + " · " + item.trap.status.name.replace('_', ' '),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    com.strobingn.wildlifefieldops.ai.fieldops.TrapCheckPlanner.dueLabel(item.dueState),
                    color = dueColor,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (item.jobTitle.isNotBlank()) Text(item.jobTitle, color = TextSecondary)
            if (item.trap.trapLocation.isNotBlank()) Text(item.trap.trapLocation, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
            item.trap.nextCheckDate?.let {
                Text("Next check ${fmt.format(Date(it))}", color = dueColor, style = MaterialTheme.typography.labelSmall)
            }
            if (item.trap.catchType != CatchType.NONE) {
                Text("Catch: ${item.trap.catchType.name} ×${item.trap.catchCount}", color = TextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onLog,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                ) { Text("Log check") }
                TextButton(onClick = onOpenJob, enabled = item.trap.jobId.isNotBlank()) {
                    Text("Open job", color = PrimaryGreen)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrapEditorDialog(
    jobs: List<Job>,
    initial: TrapLog?,
    onDismiss: () -> Unit,
    onSave: (TrapLog) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var trapId by remember { mutableStateOf(initial?.trapId.orEmpty()) }
    var location by remember { mutableStateOf(initial?.trapLocation.orEmpty()) }
    var jobId by remember { mutableStateOf(initial?.jobId.orEmpty()) }
    var status by remember { mutableStateOf(initial?.status ?: TrapStatus.SET) }
    var catchType by remember { mutableStateOf(initial?.catchType ?: CatchType.NONE) }
    var catchCount by remember { mutableStateOf((initial?.catchCount ?: 0).toString()) }
    var bait by remember { mutableStateOf(initial?.baitType.orEmpty()) }
    var disposition by remember { mutableStateOf(initial?.disposition.orEmpty()) }
    var method by remember { mutableStateOf(initial?.method.orEmpty().ifBlank { "Live cage trap" }) }
    var notes by remember { mutableStateOf(initial?.conditionNotes.orEmpty()) }
    var lat by remember { mutableStateOf(initial?.latitude?.toString().orEmpty()) }
    var lng by remember { mutableStateOf(initial?.longitude?.toString().orEmpty()) }
    val prefill = remember {
        TrapCheckPlanner.editorDates(initial?.status ?: TrapStatus.SET)
    }
    var checkDateText by remember { mutableStateOf(prefill.checkDay) }
    var nextCheckText by remember { mutableStateOf(prefill.nextCheckDay) }
    var dateError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundCard,
        title = { Text(if (initial == null) "Add trap" else "Log trap check", color = TextPrimary) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchableJobPicker(jobs = jobs, selectedId = jobId, onSelect = { jobId = it }, allowNone = true)
                OutlinedTextField(value = trapId, onValueChange = { trapId = it }, label = { Text("Trap ID / tag") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location on property") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                EnumPicker("Status", TrapStatus.entries, status) { status = it }
                EnumPicker("Catch", CatchType.entries, catchType) { catchType = it }
                OutlinedTextField(value = catchCount, onValueChange = { catchCount = it.filter { ch -> ch.isDigit() } }, label = { Text("Catch count") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(value = bait, onValueChange = { bait = it }, label = { Text("Bait") }, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                StringPicker("Disposition", DecLogExporter.DISPOSITIONS, disposition) { disposition = it }
                StringPicker("Method", DecLogExporter.METHODS, method) { method = it }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth(), colors = fieldColors())
                OutlinedTextField(
                    value = checkDateText,
                    onValueChange = { checkDateText = it; dateError = null },
                    label = { Text("Check date (yyyy-MM-dd)") },
                    supportingText = { Text("Starts at today. Edit it if the check was another day. This is the DEC log date.", color = TextTertiary) },
                    isError = dateError != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = nextCheckText,
                    onValueChange = { nextCheckText = it; dateError = null },
                    label = { Text("Next check (yyyy-MM-dd)") },
                    supportingText = { Text("Starts at the next check for this status. Clear it for no next check.", color = TextTertiary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
                TextButton(onClick = {
                    if (nextCheckText.isNotBlank()) return@TextButton
                    val base = FieldDate.parseDay(checkDateText).millis ?: System.currentTimeMillis()
                    val suggested = TrapCheckPlanner.nextCheckAfter(status, base)
                    nextCheckText = suggested?.let { FieldDate.formatDay(it) }.orEmpty()
                }) { Text("Suggest next check") }
                if (dateError != null) Text(dateError!!, color = StatusUrgent)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("Lat") }, modifier = Modifier.weight(1f), colors = fieldColors())
                    OutlinedTextField(value = lng, onValueChange = { lng = it }, label = { Text("Lng") }, modifier = Modifier.weight(1f), colors = fieldColors())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val check = FieldDate.parseDay(checkDateText)
                    val nextCheck = FieldDate.parseDay(nextCheckText)
                    if (!check.ok || !nextCheck.ok) {
                        dateError = check.error ?: nextCheck.error
                        return@Button
                    }
                    val now = System.currentTimeMillis()
                    val next = (initial ?: TrapLog()).copy(
                        trapId = trapId.trim(),
                        trapLocation = location.trim(),
                        jobId = jobId,
                        status = status,
                        catchType = catchType,
                        catchCount = catchCount.toIntOrNull() ?: 0,
                        baitType = bait.trim(),
                        disposition = disposition.trim(),
                        method = method.trim(),
                        conditionNotes = notes.trim(),
                        latitude = lat.toDoubleOrNull(),
                        longitude = lng.toDoubleOrNull(),
                        checkDate = check.millis ?: 0L,
                        nextCheckDate = nextCheck.millis,
                        updatedAt = now,
                        isSynced = false
                    )
                    onSave(next)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = StatusUrgent) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Enum<T>> EnumPicker(label: String, values: List<T>, selected: T, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected.name.replace('_', ' '),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            colors = fieldColors()
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value.name.replace('_', ' ')) },
                    onClick = {
                        onSelect(value)
                        open = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StringPicker(label: String, values: List<String>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = onSelect,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            colors = fieldColors()
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = {
                        onSelect(value)
                        open = false
                    }
                )
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryGreen,
    unfocusedBorderColor = BorderDark,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
