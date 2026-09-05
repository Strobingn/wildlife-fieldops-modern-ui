package com.strobingn.wildlifefieldops.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.data.model.*
import com.strobingn.wildlifefieldops.data.remote.InspectionReportContext
import com.strobingn.wildlifefieldops.ui.components.ScheduleDateTimeField
import com.strobingn.wildlifefieldops.ui.components.defaultAppointmentTime
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.InspectionsViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionFormScreen(
    inspectionId: String? = null,
    prefilledJobId: String = "",
    onBack: () -> Unit,
    onNavigateToEstimate: ((String) -> Unit)? = null,
    viewModel: InspectionsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var customerName by remember { mutableStateOf("") }
    var inspectorName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(InspectionType.ROUTINE) }
    var findings by remember { mutableStateOf("") }
    var recommendations by remember { mutableStateOf("") }
    var selectedSeverity by remember { mutableStateOf(FindingSeverity.NONE) }
    var speciesIdentified by remember { mutableStateOf("") }
    var entryPoints by remember { mutableStateOf("") }
    var damageAssessment by remember { mutableStateOf("") }
    var followUpRequired by remember { mutableStateOf(false) }
    var weatherConditions by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var dictationNotes by remember { mutableStateOf("") }
    var partialDictation by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var dictationError by remember { mutableStateOf<String?>(null) }
    var showTypeDropdown by remember { mutableStateOf(false) }
    var showSeverityDropdown by remember { mutableStateOf(false) }
    var scheduledAt by remember { mutableStateOf(defaultAppointmentTime()) }
    var linkedJobId by remember { mutableStateOf(prefilledJobId) }

    val existing by viewModel.getInspectionById(inspectionId.orEmpty())
        .collectAsState(initial = null)
    val reportLoading by viewModel.reportLoading.collectAsState()
    val reportError by viewModel.reportError.collectAsState()
    val reportSource by viewModel.reportSource.collectAsState()
    val estimatePrepLoading by viewModel.estimatePrepLoading.collectAsState()
    val estimatePrepMessage by viewModel.estimatePrepMessage.collectAsState()

    var linkedJobTitle by remember { mutableStateOf("") }
    var linkedJobAddress by remember { mutableStateOf("") }
    var linkedJobDescription by remember { mutableStateOf("") }

    LaunchedEffect(existing) {
        val insp = existing ?: return@LaunchedEffect
        customerName = insp.customerName
        inspectorName = insp.inspectorName
        selectedType = insp.inspectionType
        findings = insp.findings
        recommendations = insp.recommendations
        selectedSeverity = insp.severity
        speciesIdentified = insp.speciesIdentified
        entryPoints = insp.entryPoints
        damageAssessment = insp.damageAssessment
        followUpRequired = insp.followUpRequired
        weatherConditions = insp.weatherConditions
        notes = insp.notes
        scheduledAt = insp.inspectionDate
        if (insp.jobId.isNotBlank()) linkedJobId = insp.jobId
    }

    LaunchedEffect(linkedJobId) {
        if (linkedJobId.isBlank()) return@LaunchedEffect
        val job = viewModel.loadJobOnce(linkedJobId) ?: return@LaunchedEffect
        linkedJobTitle = job.title
        linkedJobAddress = job.address
        linkedJobDescription = job.description
        if (customerName.isBlank()) customerName = job.customerName
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    fun buildRecognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

    fun startListeningSession() {
        val sr = speechRecognizer
        if (sr == null) {
            dictationError = "Speech recognition is not available on this device."
            isListening = false
            return
        }
        dictationError = null
        partialDictation = ""
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                dictationError = null
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                // Keep listening flag until final/error; user can toggle off
            }
            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Speech client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error during recognition"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech matched — tap Dictate again"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy — try again"
                    SpeechRecognizer.ERROR_SERVER -> "Speech server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard — tap Dictate again"
                    else -> "Speech error ($error)"
                }
                // Soft errors: keep accumulated text, allow restart
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    dictationError = msg
                    if (isListening) {
                        try {
                            sr.startListening(buildRecognizerIntent())
                            return
                        } catch (_: Exception) {
                        }
                    }
                } else {
                    dictationError = msg
                    isListening = false
                }
            }
            override fun onResults(results: Bundle?) {
                val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val best = texts.firstOrNull()?.trim().orEmpty()
                if (best.isNotBlank()) {
                    dictationNotes = listOf(dictationNotes.trim(), best)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                    // Also mirror into notes if empty-ish
                    if (notes.isBlank()) notes = dictationNotes
                }
                partialDictation = ""
                if (isListening) {
                    try {
                        sr.startListening(buildRecognizerIntent())
                    } catch (e: Exception) {
                        isListening = false
                        dictationError = e.message ?: "Could not restart listening"
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val texts = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                partialDictation = texts.firstOrNull().orEmpty()
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        try {
            sr.startListening(buildRecognizerIntent())
            isListening = true
        } catch (e: Exception) {
            isListening = false
            dictationError = e.message ?: "Failed to start speech recognition"
        }
    }

    fun stopListeningSession() {
        isListening = false
        partialDictation = ""
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListeningSession()
        } else {
            dictationError = "Microphone permission denied. Enable RECORD_AUDIO in system settings."
            isListening = false
        }
    }

    fun toggleDictate() {
        if (isListening) {
            stopListeningSession()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startListeningSession()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun severityFromString(raw: String): FindingSeverity {
        return runCatching { FindingSeverity.valueOf(raw.trim().uppercase()) }
            .getOrDefault(FindingSeverity.MODERATE)
    }

    fun buildReportBlob(): String = buildString {
        if (speciesIdentified.isNotBlank()) appendLine("Species: $speciesIdentified")
        if (findings.isNotBlank()) appendLine("Findings: $findings")
        if (entryPoints.isNotBlank()) appendLine("Entry points: $entryPoints")
        if (damageAssessment.isNotBlank()) appendLine("Damage: $damageAssessment")
        if (recommendations.isNotBlank()) appendLine("Recommendations: $recommendations")
        appendLine("Severity: ${selectedSeverity.name}")
        if (notes.isNotBlank()) appendLine("Notes: $notes")
        if (dictationNotes.isNotBlank()) appendLine("Dictation: $dictationNotes")
    }.trim()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (inspectionId.isNullOrBlank()) "New Inspection" else "Inspection",
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        stopListeningSession()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Dictate card ---
            Card(
                colors = CardDefaults.cardColors(containerColor = BackgroundCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Dictate inspection",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Hold the mic toggle on while you walk the property. Speech accumulates across pauses.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { toggleDictate() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isListening) ErrorRed else PrimaryGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isListening) "Listening… tap to stop" else "Dictate",
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (isListening) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(ErrorRed, CircleShape)
                            )
                        }
                    }
                    if (!partialDictation.isNullOrBlank()) {
                        Text(
                            "Hearing: $partialDictation",
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryGreenLight
                        )
                    }
                    if (!dictationError.isNullOrBlank()) {
                        Text(dictationError!!, color = ErrorRed, style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedTextField(
                        value = dictationNotes,
                        onValueChange = { dictationNotes = it },
                        label = { Text("Dictation transcript (editable)") },
                        colors = fieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3,
                        maxLines = 8
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                viewModel.writeReportFromDictation(
                                    transcript = dictationNotes,
                                    context = InspectionReportContext(
                                        customerName = customerName,
                                        inspectorName = inspectorName,
                                        inspectionType = selectedType.name,
                                        jobTitle = linkedJobTitle,
                                        jobAddress = linkedJobAddress,
                                        jobDescription = linkedJobDescription,
                                        existingFindings = findings,
                                        existingRecommendations = recommendations,
                                        existingSpecies = speciesIdentified,
                                        existingEntryPoints = entryPoints,
                                        existingDamage = damageAssessment,
                                        existingNotes = notes
                                    )
                                ) { draft ->
                                    if (draft.findings.isNotBlank()) findings = draft.findings
                                    if (draft.recommendations.isNotBlank()) recommendations = draft.recommendations
                                    if (draft.speciesIdentified.isNotBlank()) speciesIdentified = draft.speciesIdentified
                                    if (draft.entryPoints.isNotBlank()) entryPoints = draft.entryPoints
                                    if (draft.damageAssessment.isNotBlank()) damageAssessment = draft.damageAssessment
                                    selectedSeverity = severityFromString(draft.severity)
                                    val summaryBits = listOf(draft.notes, draft.summary)
                                        .filter { it.isNotBlank() }
                                        .joinToString("\n")
                                    if (summaryBits.isNotBlank()) notes = summaryBits
                                }
                            },
                            enabled = !reportLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentBlue,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (reportLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("AI Write Report", fontWeight = FontWeight.Bold)
                        }
                        if (linkedJobId.isNotBlank() && onNavigateToEstimate != null) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.prepareJobForEstimate(
                                        jobId = linkedJobId,
                                        reportText = buildReportBlob()
                                    ) { jobId ->
                                        onNavigateToEstimate(jobId)
                                    }
                                },
                                enabled = !estimatePrepLoading && buildReportBlob().isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (estimatePrepLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = PrimaryGreen
                                    )
                                } else {
                                    Icon(Icons.Default.Calculate, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("AI Estimate")
                                }
                            }
                        }
                    }
                    if (!reportSource.isNullOrBlank()) {
                        Text(reportSource!!, color = PrimaryGreen, style = MaterialTheme.typography.labelMedium)
                    }
                    if (!reportError.isNullOrBlank()) {
                        Text(reportError!!, color = ErrorRed, style = MaterialTheme.typography.labelMedium)
                    }
                    if (!estimatePrepMessage.isNullOrBlank()) {
                        Text(estimatePrepMessage!!, color = PrimaryGreenLight, style = MaterialTheme.typography.labelMedium)
                    }
                    if (linkedJobId.isBlank()) {
                        Text(
                            "Tip: open Inspect from a Job to enable AI Estimate navigation.",
                            color = TextTertiary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            OutlinedTextField(
                value = customerName,
                onValueChange = { customerName = it },
                label = { Text("Customer Name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary) },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = inspectorName,
                onValueChange = { inspectorName = it },
                label = { Text("Inspector Name") },
                leadingIcon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = TextSecondary) },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Text(
                "Inspection date and time",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            ScheduleDateTimeField(
                value = scheduledAt,
                onValueChange = { scheduledAt = it }
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = showTypeDropdown,
                    onExpandedChange = { showTypeDropdown = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedType.name.lowercase().replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTypeDropdown) },
                        colors = fieldColors(),
                        modifier = Modifier.menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = showTypeDropdown,
                        onDismissRequest = { showTypeDropdown = false },
                        modifier = Modifier.exposedDropdownSize()
                    ) {
                        InspectionType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }, color = TextPrimary) },
                                onClick = {
                                    selectedType = type
                                    showTypeDropdown = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = showSeverityDropdown,
                    onExpandedChange = { showSeverityDropdown = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedSeverity.name.lowercase().replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Severity") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSeverityDropdown) },
                        colors = fieldColors(),
                        modifier = Modifier.menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = showSeverityDropdown,
                        onDismissRequest = { showSeverityDropdown = false },
                        modifier = Modifier.exposedDropdownSize()
                    ) {
                        FindingSeverity.entries.forEach { severity ->
                            DropdownMenuItem(
                                text = { Text(severity.name.lowercase().replaceFirstChar { it.uppercase() }, color = TextPrimary) },
                                onClick = {
                                    selectedSeverity = severity
                                    showSeverityDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = speciesIdentified,
                onValueChange = { speciesIdentified = it },
                label = { Text("Species Identified") },
                leadingIcon = { Icon(Icons.Default.Pets, contentDescription = null, tint = TextSecondary) },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = findings,
                onValueChange = { findings = it },
                label = { Text("Findings") },
                colors = fieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(12.dp),
                maxLines = 5
            )

            OutlinedTextField(
                value = recommendations,
                onValueChange = { recommendations = it },
                label = { Text("Recommendations") },
                colors = fieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(12.dp),
                maxLines = 4
            )

            OutlinedTextField(
                value = entryPoints,
                onValueChange = { entryPoints = it },
                label = { Text("Entry Points Found") },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = damageAssessment,
                onValueChange = { damageAssessment = it },
                label = { Text("Damage Assessment") },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = weatherConditions,
                onValueChange = { weatherConditions = it },
                label = { Text("Weather Conditions") },
                leadingIcon = { Icon(Icons.Default.WbCloudy, contentDescription = null, tint = TextSecondary) },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes / Summary") },
                colors = fieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(12.dp),
                maxLines = 4
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Follow-up Required", color = TextPrimary)
                Switch(
                    checked = followUpRequired,
                    onCheckedChange = { followUpRequired = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PrimaryGreen,
                        checkedTrackColor = PrimaryGreen.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    stopListeningSession()
                    val base = existing
                    if (base != null) {
                        viewModel.updateInspection(
                            base.copy(
                                jobId = linkedJobId.ifBlank { base.jobId },
                                customerName = customerName,
                                inspectorName = inspectorName,
                                inspectionType = selectedType,
                                inspectionDate = scheduledAt,
                                findings = findings,
                                recommendations = recommendations,
                                severity = selectedSeverity,
                                speciesIdentified = speciesIdentified,
                                entryPoints = entryPoints,
                                damageAssessment = damageAssessment,
                                followUpRequired = followUpRequired,
                                followUpDate = if (followUpRequired) {
                                    base.followUpDate ?: (System.currentTimeMillis() + 7 * 86400000L)
                                } else null,
                                weatherConditions = weatherConditions,
                                notes = notes,
                                isSynced = false
                            )
                        )
                    } else {
                        viewModel.createInspection(
                            jobId = linkedJobId,
                            customerId = "",
                            customerName = customerName,
                            inspectorName = inspectorName,
                            inspectionType = selectedType,
                            inspectionDate = scheduledAt,
                            findings = findings,
                            recommendations = recommendations,
                            severity = selectedSeverity,
                            speciesIdentified = speciesIdentified,
                            entryPoints = entryPoints,
                            damageAssessment = damageAssessment,
                            followUpRequired = followUpRequired,
                            followUpDate = if (followUpRequired) System.currentTimeMillis() + 7 * 86400000L else null,
                            weatherConditions = weatherConditions,
                            notes = notes
                        )
                    }
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                enabled = customerName.isNotBlank()
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Inspection", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryGreen,
    unfocusedBorderColor = BorderDark,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = BackgroundDark,
    unfocusedContainerColor = BackgroundDark,
    focusedLabelColor = PrimaryGreen,
    cursorColor = PrimaryGreen
)
