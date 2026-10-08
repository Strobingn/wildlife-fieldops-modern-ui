package com.strobingn.wildlifefieldops.ui.screens

import android.Manifest
import android.net.Uri
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.ai.fieldops.AiRuntimeStatus
import com.strobingn.wildlifefieldops.ai.fieldops.InspectionNarrativeDraft
import com.strobingn.wildlifefieldops.ai.fieldops.InspectionNarrativeEngine
import com.strobingn.wildlifefieldops.ai.fieldops.ManualField
import com.strobingn.wildlifefieldops.ai.fieldops.NarrativeCleared
import com.strobingn.wildlifefieldops.ai.fieldops.OperatorWins
import com.strobingn.wildlifefieldops.data.inspection.InspectionContact
import com.strobingn.wildlifefieldops.data.inspection.JobInspectionLink
import com.strobingn.wildlifefieldops.data.model.*
import com.strobingn.wildlifefieldops.data.remote.InspectionReportContext
import com.strobingn.wildlifefieldops.data.remote.InspectionReportDraft
import com.strobingn.wildlifefieldops.ui.components.AiRuntimeBadge
import com.strobingn.wildlifefieldops.ui.components.ScheduleDateTimeField
import com.strobingn.wildlifefieldops.ui.components.defaultAppointmentTime
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.InspectionsViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.LiveWeatherViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.WeatherUiState
import com.strobingn.wildlifefieldops.util.WildlifeWhispererInspectionReportPdf
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionFormScreen(
    inspectionId: String? = null,
    prefilledJobId: String = "",
    onBack: () -> Unit,
    onNavigateToEstimate: ((String) -> Unit)? = null,
    onNavigateToJob: ((String) -> Unit)? = null,
    viewModel: InspectionsViewModel = hiltViewModel(),
    searchVm: com.strobingn.wildlifefieldops.ui.viewmodel.SearchFieldOpsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val weatherVm: LiveWeatherViewModel = hiltViewModel()
    val weatherState by weatherVm.state.collectAsState()
    var customerId by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var serviceAddress by remember { mutableStateOf("") }
    var serviceType by remember { mutableStateOf("") }
    var inspectorName by remember { mutableStateOf("") }
    var inspectionTypeTouched by remember { mutableStateOf(false) }
    var jobPrefillToken by remember { mutableStateOf("") }
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
    var fillReportOnStop by remember { mutableStateOf(false) }
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
    val walkthroughLoading by viewModel.walkthroughLoading.collectAsState()
    val walkthroughHint by viewModel.walkthroughHint.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val allJobs by viewModel.allJobs.collectAsState()

    var linkedJobTitle by remember { mutableStateOf("") }
    var linkedJobAddress by remember { mutableStateOf("") }
    var linkedJobDescription by remember { mutableStateOf("") }
    var replaceAiFields by remember { mutableStateOf(false) }
    var aiNarrativeDraft by remember { mutableStateOf("") }
    var aiDraftSource by remember { mutableStateOf("") }
    var narrativeCleared by remember { mutableStateOf(emptySet<String>()) }
    var findingsPreview by remember { mutableStateOf<String?>(null) }
    var recommendationsPreview by remember { mutableStateOf<String?>(null) }

    var hydratedInspectionId by remember { mutableStateOf<String?>(null) }
    fun keepTyped(typed: String, loaded: String) = if (typed.isBlank()) loaded else typed
    fun packedDraftSource(): String =
        NarrativeCleared.pack(
            InspectionContact.embed(
                aiDraftSource,
                InspectionContact.Values(customerPhone, serviceAddress, serviceType)
            ),
            narrativeCleared
        )
    suspend fun applyJobPrefill(jobId: String, manual: Set<String> = narrativeCleared) {
        val id = jobId.trim()
        if (id.isBlank()) return
        val token = id + ":" + (inspectionId ?: "new")
        if (jobPrefillToken == token) return
        jobPrefillToken = token
        val job = viewModel.loadJobOnce(id)
        if (job == null) {
            jobPrefillToken = ""
            return
        }
        val seeded = JobInspectionLink.inspectionForRoute(
            job = job,
            phone = viewModel.customerPhone(job.customerId),
            current = Inspection(
                jobId = id,
                customerId = customerId,
                customerName = customerName,
                speciesIdentified = speciesIdentified,
                inspectionType = selectedType,
                aiDraftSource = InspectionContact.embed(
                    aiDraftSource,
                    InspectionContact.Values(customerPhone, serviceAddress, serviceType)
                )
            ),
            manual = manual,
            typeUntouched = inspectionId.isNullOrBlank() && !inspectionTypeTouched
        )
        val contact = InspectionContact.read(seeded.aiDraftSource)
        if (customerId.isBlank()) customerId = seeded.customerId
        customerName = seeded.customerName
        customerPhone = contact.phone
        serviceAddress = contact.address
        serviceType = contact.serviceType
        speciesIdentified = seeded.speciesIdentified
        selectedType = seeded.inspectionType
        linkedJobTitle = job.title
        linkedJobAddress = job.address
        linkedJobDescription = job.description
    }
    fun applyLiveNarrative(
        findingsIn: String,
        recommendationsIn: String,
        speciesIn: String,
        entryIn: String,
        damageIn: String,
        notesIn: String
    ) {
        findingsPreview = OperatorWins.preview(
            findings,
            findingsIn,
            ManualField.NARRATIVE_FINDINGS in narrativeCleared || findings.isNotBlank()
        )
        recommendationsPreview = OperatorWins.preview(
            recommendations,
            recommendationsIn,
            ManualField.NARRATIVE_RECS in narrativeCleared || recommendations.isNotBlank()
        )
        val merged = InspectionNarrativeEngine.apply(
            InspectionNarrativeDraft(
                findings = findings,
                recommendations = recommendations,
                speciesIdentified = speciesIdentified,
                entryPoints = entryPoints,
                damageAssessment = damageAssessment,
                notes = notes
            ),
            InspectionNarrativeDraft(
                findings = findingsIn,
                recommendations = recommendationsIn,
                speciesIdentified = speciesIn,
                entryPoints = entryIn,
                damageAssessment = damageIn,
                notes = notesIn
            ),
            replace = replaceAiFields,
            cleared = narrativeCleared
        )
        val draftToStore = InspectionNarrativeEngine.aiDraftToStore(
            previousDraft = aiNarrativeDraft,
            typedBefore = findings,
            merged = merged.findings,
            suggested = findingsIn
        )
        findings = merged.findings
        recommendations = merged.recommendations
        speciesIdentified = merged.speciesIdentified
        entryPoints = merged.entryPoints
        damageAssessment = merged.damageAssessment
        notes = merged.notes
        aiNarrativeDraft = draftToStore
    }
    LaunchedEffect(existing?.id) {
        val insp = existing ?: return@LaunchedEffect
        if (hydratedInspectionId == insp.id) return@LaunchedEffect
        hydratedInspectionId = insp.id
        val typedAlready = listOf(
            customerName, inspectorName, findings, recommendations, speciesIdentified,
            entryPoints, damageAssessment, notes, weatherConditions, aiNarrativeDraft
        ).any { it.isNotBlank() }
        customerName = keepTyped(customerName, insp.customerName)
        inspectorName = keepTyped(inspectorName, insp.inspectorName)
        findings = keepTyped(findings, insp.findings)
        recommendations = keepTyped(recommendations, insp.recommendations)
        speciesIdentified = keepTyped(speciesIdentified, insp.speciesIdentified)
        entryPoints = keepTyped(entryPoints, insp.entryPoints)
        damageAssessment = keepTyped(damageAssessment, insp.damageAssessment)
        weatherConditions = keepTyped(weatherConditions, insp.weatherConditions)
        notes = keepTyped(notes, insp.notes)
        aiNarrativeDraft = keepTyped(aiNarrativeDraft, insp.aiNarrativeDraft)
        val loadedContact = InspectionContact.read(insp.aiDraftSource)
        customerPhone = keepTyped(customerPhone, loadedContact.phone)
        serviceAddress = keepTyped(serviceAddress, loadedContact.address)
        serviceType = keepTyped(serviceType, loadedContact.serviceType)
        if (customerId.isBlank()) customerId = insp.customerId
        if (aiDraftSource.isBlank()) {
            aiDraftSource = NarrativeCleared.source(insp.aiDraftSource)
            narrativeCleared = NarrativeCleared.cleared(insp.aiDraftSource)
        }
        if (!typedAlready) {
            selectedType = insp.inspectionType
            selectedSeverity = insp.severity
            followUpRequired = insp.followUpRequired
            scheduledAt = insp.inspectionDate
        }
        if (insp.jobId.isNotBlank() && linkedJobId.isBlank()) linkedJobId = insp.jobId
        applyJobPrefill(insp.jobId.ifBlank { linkedJobId }, narrativeCleared)
    }

    LaunchedEffect(linkedJobId) {
        if (linkedJobId.isBlank()) return@LaunchedEffect
        val job = viewModel.loadJobOnce(linkedJobId) ?: return@LaunchedEffect
        linkedJobTitle = job.title
        linkedJobAddress = job.address
        linkedJobDescription = job.description
        applyJobPrefill(linkedJobId)
    }


    LaunchedEffect(reportSource) {
        val src = reportSource ?: return@LaunchedEffect
        snackbarHostState.showSnackbar("Report filled · $src")
    }
    LaunchedEffect(walkthroughHint) {
        val hint = walkthroughHint ?: return@LaunchedEffect
        if (hint.isNotBlank()) snackbarHostState.showSnackbar(hint.take(180))
    }
    LaunchedEffect(reportError) {
        val err = reportError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(err)
        viewModel.clearReportError()
    }
    LaunchedEffect(estimatePrepMessage) {
        val msg = estimatePrepMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearEstimatePrepMessage()
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    fun transcriptForReport(): String {
        val committed = dictationNotes.trim()
        val partial = partialDictation.trim()
        return when {
            partial.isBlank() || committed.endsWith(partial) -> committed
            committed.isBlank() -> partial
            else -> "$committed $partial"
        }
    }

    fun applyReportDraft(draft: InspectionReportDraft) {
        if (replaceAiFields || customerName.isBlank()) {
            draft.customerName.takeIf { it.isNotBlank() }?.let { customerName = it }
        }
        if (replaceAiFields || customerPhone.isBlank()) {
            draft.customerPhone.takeIf { it.isNotBlank() }?.let { customerPhone = it }
        }
        if (replaceAiFields || serviceAddress.isBlank()) {
            draft.serviceAddress.takeIf { it.isNotBlank() }?.let { serviceAddress = it }
        }
        // Notes only if the model wrote a summary that is not the raw transcript.
        val spoken = transcriptForReport()
        val summaryBits = draft.notes.trim().takeUnless { it.isBlank() || it == spoken }.orEmpty()
        applyLiveNarrative(
            draft.findings,
            draft.recommendations,
            draft.speciesIdentified,
            draft.entryPoints,
            draft.damageAssessment,
            summaryBits
        )
        if (replaceAiFields || selectedSeverity == FindingSeverity.NONE) {
            selectedSeverity = runCatching { FindingSeverity.valueOf(draft.severity.trim().uppercase()) }
                .getOrDefault(selectedSeverity)
        }
    }

    fun fillReportFromDictation() {
        val transcript = transcriptForReport()
        if (transcript.isBlank()) return
        viewModel.writeReportFromDictation(
            transcript = transcript,
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
        ) { draft -> applyReportDraft(draft) }
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
                if (fillReportOnStop && !isListening) {
                    fillReportOnStop = false
                    fillReportFromDictation()
                }
            }
            override fun onResults(results: Bundle?) {
                val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val best = texts.firstOrNull()?.trim().orEmpty()
                if (best.isNotBlank()) {
                    dictationNotes = listOf(dictationNotes.trim(), best)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                }
                partialDictation = ""
                if (fillReportOnStop && !isListening) {
                    fillReportOnStop = false
                    fillReportFromDictation()
                } else if (isListening) {
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
            fillReportOnStop = transcriptForReport().isNotBlank()
            stopListeningSession()
            scope.launch {
                delay(700)
                if (fillReportOnStop) {
                    fillReportOnStop = false
                    fillReportFromDictation()
                }
            }
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


    // Inspection photos: saved against the inspection id as soon as they are taken, so they
    // survive leaving the screen. A new inspection gets its id up front and keeps it on Save.
    val photoOwnerId = rememberSaveable(inspectionId) {
        inspectionId?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString()
    }
    val inspectionPhotos by remember(photoOwnerId) { viewModel.photosForInspection(photoOwnerId) }
        .collectAsState(initial = emptyList())
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }

    fun newPhotoFile(): File {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        return File(dir, "INS_${System.currentTimeMillis()}_${(0..999).random()}.jpg")
    }
    fun recordPhoto(file: File) {
        viewModel.addInspectionPhoto(
            Photo(
                filePath = FileProvider.getUriForFile(context, "${context.packageName}.provider", file).toString(),
                localPath = file.absolutePath,
                jobId = linkedJobId.ifBlank { null },
                inspectionId = photoOwnerId,
                customerId = customerId.ifBlank { null },
                category = PhotoCategory.INSPECTION,
                takenAt = System.currentTimeMillis(),
                takenBy = inspectorName,
                fileSize = file.length()
            )
        )
    }
    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val path = pendingPhotoPath
        pendingPhotoPath = null
        if (path != null) {
            val file = File(path)
            if (saved && file.isFile && file.length() > 0) recordPhoto(file)
            else file.delete()
        }
    }
    fun startCamera() {
        val file = newPhotoFile()
        pendingPhotoPath = file.absolutePath
        takePictureLauncher.launch(
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else scope.launch { snackbarHostState.showSnackbar("Camera permission is needed to take photos.") }
    }
    fun takeInspectionPhoto() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) startCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val copied = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        val file = newPhotoFile()
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { input.copyTo(it) }
                        } ?: return@runCatching null
                        file.takeIf { it.length() > 0 }
                    }.getOrNull()
                }
            }
            copied.forEach { recordPhoto(it) }
            if (copied.size < uris.size) {
                snackbarHostState.showSnackbar("Could not read ${uris.size - copied.size} photo(s).")
            }
        }
    }

    val walkthroughPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.analyzeWalkthroughVideo(
            context = context,
            videoUri = uri,
            reportContext = InspectionReportContext(
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
            val summaryBits = listOf(draft.notes, draft.summary).filter { it.isNotBlank() }.joinToString("\n")
            applyLiveNarrative(
                draft.findings,
                draft.recommendations,
                draft.speciesIdentified,
                draft.entryPoints,
                draft.damageAssessment,
                summaryBits
            )
            if (replaceAiFields) {
                selectedSeverity = runCatching { FindingSeverity.valueOf(draft.severity.trim().uppercase()) }
                    .getOrDefault(selectedSeverity)
            }
        }
    }



    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
        bottomBar = {
            Surface(color = BackgroundCard, shadowElevation = 10.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { toggleDictate() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isListening) ErrorRed else AccentBlue,
                                contentColor = if (isListening) Color.White else OnPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isListening) "Stop" else "Dictate", fontWeight = FontWeight.Bold)
                        }
                        Button(
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
                                ) { draft -> applyReportDraft(draft) }
                            },
                            enabled = !reportLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentBlue,
                                contentColor = OnPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.15f)
                        ) {
                            if (reportLoading) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = OnPrimary)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("AI Report", fontWeight = FontWeight.Bold)
                        }
                    }
                    AiRuntimeBadge(
                        status = viewModel.aiRuntime(),
                        lastUsed = aiDraftSource,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.draftNarrativeFromEvidence(
                                    appContext = context.applicationContext,
                                    jobId = linkedJobId,
                                    inspectionId = photoOwnerId,
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
                                    ),
                                    replace = replaceAiFields
                                ) { draft ->
                                    val beforeDraft = aiNarrativeDraft
                                    applyLiveNarrative(
                                        draft.findings,
                                        draft.recommendations,
                                        draft.speciesIdentified,
                                        draft.entryPoints,
                                        draft.damageAssessment,
                                        draft.notes
                                    )
                                    if (aiNarrativeDraft != beforeDraft) {
                                        aiDraftSource = AiRuntimeStatus.wireName(draft.source)
                                    }
                                }
                            },
                            enabled = !reportLoading,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (replaceAiFields) "Replace from photos" else "Draft from photos")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = replaceAiFields,
                                onCheckedChange = { replaceAiFields = it },
                                colors = CheckboxDefaults.colors(checkedColor = PrimaryGreen)
                            )
                            Text("Replace mine", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (linkedJobId.isNotBlank() && onNavigateToEstimate != null) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.prepareJobForEstimate(
                                        jobId = linkedJobId,
                                        reportText = buildReportBlob()
                                    ) { jobId -> onNavigateToEstimate(jobId) }
                                },
                                enabled = !estimatePrepLoading && buildReportBlob().isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (estimatePrepLoading) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = AccentBlue)
                                } else {
                                    Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("AI Estimate")
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                try {
                                    val qrJobId = linkedJobId.ifBlank { existing?.jobId.orEmpty() }
                                    val qr = qrJobId.takeIf { it.isNotBlank() }?.let {
                                        com.strobingn.wildlifefieldops.util.QrBitmap.encode(
                                            com.strobingn.wildlifefieldops.ai.fieldops.ShareableReport.payload(it)
                                        )
                                    }
                                    val path = WildlifeWhispererInspectionReportPdf.generate(
                                        context = context,
                                        fields = com.strobingn.wildlifefieldops.util.InspectionReportFields(
                                            customerName = customerName,
                                            customerId = customerId,
                                            customerPhone = customerPhone,
                                            inspectorName = inspectorName,
                                            inspectionType = selectedType.name,
                                            inspectionDate = scheduledAt,
                                            jobTitle = linkedJobTitle,
                                            jobAddress = linkedJobAddress,
                                            species = speciesIdentified,
                                            findings = findings,
                                            entryPoints = entryPoints,
                                            damage = damageAssessment,
                                            recommendations = recommendations,
                                            severity = selectedSeverity.name,
                                            notes = notes,
                                            weather = weatherConditions,
                                            followUpRequired = followUpRequired
                                        ),
                                        qr = qr
                                    )
                                    if (qrJobId.isNotBlank()) searchVm.saveShareReport(qrJobId, path)
                                    WildlifeWhispererInspectionReportPdf.share(context, path)
                                } catch (e: Exception) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            e.message?.take(80) ?: "Could not create report PDF"
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share PDF")
                        }
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
                                            isSynced = false,
                                            aiNarrativeDraft = aiNarrativeDraft,
                                            aiDraftSource = packedDraftSource()
                                        )
                                    )
                                } else {
                                    viewModel.createInspection(
                                        id = photoOwnerId,
                                        jobId = linkedJobId,
                                        customerId = customerId,
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
                                        notes = notes,
                                        aiNarrativeDraft = aiNarrativeDraft,
                                        aiDraftSource = packedDraftSource()
                                    )
                                }
                                onBack()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
                            shape = RoundedCornerShape(12.dp),
                            enabled = customerName.isNotBlank()
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
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
            InspectionJobLinkCard(
                linkedJobId = linkedJobId,
                linkedJobTitle = linkedJobTitle,
                linkedJobAddress = linkedJobAddress,
                jobs = allJobs,
                suggestedJob = com.strobingn.wildlifefieldops.data.inspection.JobInspectionLink.suggestedJob(
                    Inspection(
                        jobId = linkedJobId,
                        customerId = existing?.customerId.orEmpty(),
                        customerName = customerName,
                        findings = findings,
                        notes = notes,
                        entryPoints = entryPoints
                    ),
                    allJobs
                ),
                onLink = { jobId ->
                    linkedJobId = jobId
                    existing?.id?.let { viewModel.linkInspectionToJob(it, jobId) }
                },
                onUnlink = {
                    linkedJobId = ""
                    linkedJobTitle = ""
                    linkedJobAddress = ""
                    existing?.id?.let { viewModel.unlinkInspectionFromJob(it) }
                },
                onOpenJob = { jobId -> onNavigateToJob?.invoke(jobId) }
            )

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
                        "Voice notes",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Stop fills the report from what you said. AI Report reruns it. Speech accumulates across pauses.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    if (isListening) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(ErrorRed, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text("Listening…", color = ErrorRed, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (partialDictation.isNotBlank()) {
                        Text("Hearing: $partialDictation", style = MaterialTheme.typography.bodySmall, color = PrimaryGreenLight)
                    }
                    if (!dictationError.isNullOrBlank()) {
                        Text(dictationError!!, color = ErrorRed, style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedTextField(
                        value = dictationNotes,
                        onValueChange = { dictationNotes = it },
                        label = { Text("Dictation transcript (editable)") },
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3,
                        maxLines = 8
                    )
                    if (reportLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = AccentBlue,
                            trackColor = BorderDark
                        )
                    }
                    if (!reportSource.isNullOrBlank()) {
                        Text(reportSource!!, color = PrimaryGreen, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }


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
                        "Photos (${inspectionPhotos.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Saved to this inspection as you take them. Draft from photos uses them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { takeInspectionPhoto() },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Take photo")
                        }
                        OutlinedButton(
                            onClick = { photoPicker.launch("image/*") },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("From gallery")
                        }
                    }
                    if (inspectionPhotos.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(inspectionPhotos, key = { it.id }) { photo ->
                                Box(Modifier.size(96.dp)) {
                                    AsyncImage(
                                        model = File(photo.localPath),
                                        contentDescription = "Inspection photo",
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(BorderDark, RoundedCornerShape(10.dp))
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    IconButton(
                                        onClick = { viewModel.removeInspectionPhoto(photo) },
                                        modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove photo",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                                .padding(3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
                        "Walkthrough video",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Record a ~60–90s property walkthrough (overview → entries → attic/crawl). " +
                            "FieldOps samples frames, runs on-device vision, and fills an editable report draft. Works offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Button(
                        onClick = { walkthroughPicker.launch("video/*") },
                        enabled = !walkthroughLoading && !reportLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentBlue,
                            contentColor = OnPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (walkthroughLoading) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = OnPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Analyzing walkthrough…")
                        } else {
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Import walkthrough video", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (walkthroughLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = AccentBlue,
                            trackColor = BorderDark
                        )
                    }
                    if (!walkthroughHint.isNullOrBlank()) {
                        Text(walkthroughHint!!, color = PrimaryGreen, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            OutlinedTextField(
                value = customerName,
                onValueChange = {
                    customerName = it
                    narrativeCleared = OperatorWins.markCleared(narrativeCleared, ManualField.CUSTOMER_NAME, it)
                },
                label = { Text("Customer Name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary) },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = customerPhone,
                onValueChange = {
                    customerPhone = it
                    narrativeCleared = OperatorWins.markCleared(narrativeCleared, ManualField.PHONE, it)
                },
                label = { Text("Phone") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = TextSecondary) },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = serviceAddress,
                onValueChange = {
                    serviceAddress = it
                    narrativeCleared = OperatorWins.markCleared(narrativeCleared, ManualField.ADDRESS, it)
                },
                label = { Text("Address") },
                leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = TextSecondary) },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
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
                                    inspectionTypeTouched = true
                                    narrativeCleared = narrativeCleared - ManualField.INSPECTION_TYPE
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
                value = serviceType,
                onValueChange = {
                    serviceType = it
                    narrativeCleared = OperatorWins.markCleared(narrativeCleared, ManualField.SERVICE_TYPE, it)
                },
                label = { Text("Service type") },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = speciesIdentified,
                onValueChange = {
                    speciesIdentified = it
                    narrativeCleared = OperatorWins.markCleared(narrativeCleared, ManualField.SPECIES, it)
                },
                label = { Text("Species Identified") },
                leadingIcon = { Icon(Icons.Default.Pets, contentDescription = null, tint = TextSecondary) },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = findings,
                onValueChange = {
                    findings = it
                    narrativeCleared = OperatorWins.markCleared(narrativeCleared, ManualField.NARRATIVE_FINDINGS, it)
                },
                label = { Text("Findings") },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth().height(120.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(12.dp),
                maxLines = 5
            )
            com.strobingn.wildlifefieldops.ui.components.ApplySuggestionChip(findingsPreview) {
                findings = it
                narrativeCleared = narrativeCleared - ManualField.NARRATIVE_FINDINGS
                findingsPreview = null
            }

            OutlinedTextField(
                value = recommendations,
                onValueChange = {
                    recommendations = it
                    narrativeCleared = OperatorWins.markCleared(narrativeCleared, ManualField.NARRATIVE_RECS, it)
                },
                label = { Text("Recommendations") },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth().height(100.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(12.dp),
                maxLines = 4
            )
            com.strobingn.wildlifefieldops.ui.components.ApplySuggestionChip(recommendationsPreview) {
                recommendations = it
                narrativeCleared = narrativeCleared - ManualField.NARRATIVE_RECS
                recommendationsPreview = null
            }

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
                onValueChange = {
                    notes = it
                    narrativeCleared = OperatorWins.markCleared(narrativeCleared, ManualField.NARRATIVE_NOTES, it)
                },
                label = { Text("Notes / Summary") },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth().height(100.dp),
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
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { weatherVm.loadShopWeather() },
                        enabled = weatherState !is WeatherUiState.Loading
                    ) { Text("Use live weather") }
                    when (val w = weatherState) {
                        is WeatherUiState.Ready -> {
                            TextButton(onClick = { weatherConditions = w.snap.summaryLine }) {
                                Text("Apply ${w.snap.tempF}°F ${w.snap.condition}")
                            }
                        }
                        is WeatherUiState.Unavailable -> Text(w.reason, color = TextTertiary, style = MaterialTheme.typography.labelSmall)
                        else -> Unit
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = BorderDark,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = BackgroundDark,
    unfocusedContainerColor = BackgroundDark,
    focusedLabelColor = AccentBlue,
    cursorColor = AccentBlue
)
