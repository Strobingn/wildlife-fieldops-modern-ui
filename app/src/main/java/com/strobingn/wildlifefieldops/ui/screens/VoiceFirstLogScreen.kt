package com.strobingn.wildlifefieldops.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.VoiceObservation
import com.strobingn.wildlifefieldops.data.voice.VoiceAudioCapture
import com.strobingn.wildlifefieldops.ui.theme.AccentBlue
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.BorderDark
import com.strobingn.wildlifefieldops.ui.theme.ErrorRed
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.SuccessGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary
import com.strobingn.wildlifefieldops.ui.viewmodel.VoiceFirstLogViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.VoiceLogPhase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hands-free observation logging: record audio, run the fail-closed ASR stack,
 * then file an editable transcript against the current job or ObservationEvent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceFirstLogScreen(
    jobId: String? = null,
    observationEventId: String? = null,
    onBack: () -> Unit,
    viewModel: VoiceFirstLogViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val filedNotes by viewModel.filedNotes.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(jobId, observationEventId) {
        viewModel.bind(jobId, observationEventId)
    }

    var isListening by remember { mutableStateOf(false) }
    var livePartial by remember { mutableStateOf("") }
    var liveFinal by remember { mutableStateOf("") }
    var captureError by remember { mutableStateOf<String?>(null) }
    var jobMenuOpen by remember { mutableStateOf(false) }

    val audioCapture = remember { VoiceAudioCapture(context.filesDir) }
    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                audioCapture.cancel()
            } catch (_: Exception) {
            }
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    fun buildSpeechIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }

    fun stopSession() {
        isListening = false
        livePartial = ""
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
        val captured = try {
            audioCapture.stop()
        } catch (_: Exception) {
            null
        }
        if (captured == null) {
            captureError = "Audio was not retained — observation not filed"
            return
        }
        viewModel.transcribeCapture(
            uri = captured.uri,
            sha256 = captured.sha256,
            durationMs = captured.durationMs,
            capturedText = liveFinal,
        )
    }

    fun startSession() {
        captureError = null
        liveFinal = ""
        livePartial = ""
        viewModel.resetSession()
        if (!audioCapture.start()) {
            captureError = "Could not start audio recorder"
            return
        }
        isListening = true
        val sr = speechRecognizer
        if (sr == null) {
            captureError = "On-device speech recognition unavailable — audio will still be retained"
            return
        }
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                captureError = null
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ) {
                    if (isListening) {
                        try {
                            sr.startListening(buildSpeechIntent())
                            return
                        } catch (_: Exception) {
                        }
                    }
                }
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    captureError = "Microphone permission required"
                }
            }

            override fun onResults(results: Bundle?) {
                val best = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (best.isNotBlank()) {
                    liveFinal = listOf(liveFinal.trim(), best.trim())
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                }
                livePartial = ""
                if (isListening) {
                    try {
                        sr.startListening(buildSpeechIntent())
                    } catch (_: Exception) {
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                livePartial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        try {
            sr.startListening(buildSpeechIntent())
        } catch (e: Exception) {
            captureError = e.message ?: "Speech recognizer failed to start"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startSession()
        else {
            captureError = "Microphone permission denied"
            isListening = false
        }
    }

    fun toggleListen() {
        if (isListening) {
            stopSession()
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startSession()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice log", color = TextPrimary) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    "Hands-free observation",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Dictate while you work. Empty or untrusted transcripts are never filed. Audio stays on device for review.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            item {
                TargetCard(
                    jobTitle = ui.jobTitle,
                    jobId = ui.jobId,
                    observationEventId = ui.observationEventId,
                    jobs = jobs,
                    menuOpen = jobMenuOpen,
                    onMenuChange = { jobMenuOpen = it },
                    onSelectJob = {
                        viewModel.selectJob(it)
                        jobMenuOpen = false
                    },
                )
            }

            item {
                MicCard(
                    isListening = isListening,
                    phase = ui.phase,
                    livePartial = livePartial,
                    liveFinal = liveFinal,
                    onToggle = { toggleListen() },
                )
            }

            if (captureError != null || ui.error != null) {
                item {
                    Text(
                        (captureError ?: ui.error).orEmpty(),
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (ui.audioRetained && ui.audioUri != null) {
                        Text(
                            "Original audio retained: ${ui.audioUri}",
                            color = TextTertiary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            if (ui.phase == VoiceLogPhase.Transcribing) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), color = AccentBlue, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Transcribing through fail-closed ASR…", color = TextSecondary)
                    }
                }
            }

            if (ui.phase == VoiceLogPhase.Review || ui.phase == VoiceLogPhase.Filed) {
                item {
                    ReviewCard(
                        uiTranscript = ui.editableTranscript,
                        validatedTranscript = ui.validatedTranscript,
                        audioUri = ui.audioUri,
                        durationMs = ui.durationMs,
                        canEdit = ui.phase == VoiceLogPhase.Review,
                        onTranscriptChange = viewModel::updateEditedTranscript,
                        onFile = viewModel::fileNote,
                        filedMessage = ui.filedMessage,
                        onReset = viewModel::resetSession,
                    )
                }
            }

            if (ui.phase == VoiceLogPhase.Failed) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Not filed", color = ErrorRed, fontWeight = FontWeight.Bold)
                            Text(
                                "The fail-closed gate blocked this observation. Record again when you can speak clearly.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = {
                                captureError = null
                                viewModel.resetSession()
                            }) {
                                Text("Try again", color = AccentBlue)
                            }
                        }
                    }
                }
            }

            if (filedNotes.isNotEmpty()) {
                item {
                    Text("Filed voice notes", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }
                items(filedNotes, key = { it.id }) { note ->
                    FiledNoteRow(note)
                }
            }
        }
    }
}

@Composable
private fun TargetCard(
    jobTitle: String?,
    jobId: String?,
    observationEventId: String?,
    jobs: List<Job>,
    menuOpen: Boolean,
    onMenuChange: (Boolean) -> Unit,
    onSelectJob: (Job) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("File against", color = TextPrimary, fontWeight = FontWeight.Medium)
            if (!jobId.isNullOrBlank()) {
                FilterChip(
                    selected = true,
                    onClick = { if (jobs.isNotEmpty()) onMenuChange(true) },
                    label = { Text(jobTitle?.ifBlank { jobId } ?: jobId) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue.copy(alpha = 0.18f),
                        selectedLabelColor = TextPrimary
                    )
                )
            } else {
                Text("No current job — pick one to attach this observation.", color = TextSecondary)
                Box {
                    OutlinedButton(onClick = { onMenuChange(true) }) {
                        Text(if (jobs.isEmpty()) "No jobs yet" else "Choose job")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuChange(false) }) {
                        jobs.take(20).forEach { job ->
                            DropdownMenuItem(
                                text = { Text(job.title.ifBlank { job.customerName.ifBlank { job.id } }) },
                                onClick = { onSelectJob(job) }
                            )
                        }
                    }
                }
            }
            if (!observationEventId.isNullOrBlank()) {
                Text(
                    "ObservationEvent $observationEventId",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun MicCard(
    isListening: Boolean,
    phase: VoiceLogPhase,
    livePartial: String,
    liveFinal: String,
    onToggle: () -> Unit,
) {
    val busy = phase == VoiceLogPhase.Transcribing
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(if (isListening) ErrorRed.copy(alpha = 0.18f) else AccentBlue.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onToggle,
                    enabled = !busy,
                    modifier = Modifier.size(96.dp)
                ) {
                    Icon(
                        if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isListening) "Stop dictation" else "Start dictation",
                        tint = if (isListening) ErrorRed else AccentBlue,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Text(
                when {
                    isListening -> "Listening — tap to stop"
                    busy -> "Validating transcript"
                    else -> "Tap to dictate"
                },
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            if (livePartial.isNotBlank()) {
                Text("Hearing: $livePartial", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
            }
            if (liveFinal.isNotBlank()) {
                Text(liveFinal, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ReviewCard(
    uiTranscript: String,
    validatedTranscript: String,
    audioUri: String?,
    durationMs: Long,
    canEdit: Boolean,
    onTranscriptChange: (String) -> Unit,
    onFile: () -> Unit,
    filedMessage: String?,
    onReset: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Review transcript", color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(
                "Audio attached · ${durationMs / 1000}s",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall
            )
            if (!audioUri.isNullOrBlank()) {
                OutlinedButton(onClick = { playAudio(audioUri) }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Play original audio")
                }
            }
            OutlinedTextField(
                value = uiTranscript,
                onValueChange = onTranscriptChange,
                enabled = canEdit,
                label = { Text("Editable transcript") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            if (validatedTranscript.isNotBlank() && validatedTranscript != uiTranscript) {
                Text(
                    "ASR original: $validatedTranscript",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (canEdit) {
                Button(
                    onClick = onFile,
                    enabled = uiTranscript.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("File observation", fontWeight = FontWeight.Bold)
                }
            }
            filedMessage?.let {
                Text(it, color = SuccessGreen, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onReset) {
                    Text("Log another", color = AccentBlue)
                }
            }
        }
    }
}

@Composable
private fun FiledNoteRow(note: VoiceObservation) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(note.editedTranscript, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            Text(
                SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(note.observedAt)) +
                    " · audio ${note.audioSha256.take(8)}…",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun playAudio(path: String) {
    try {
        MediaPlayer().apply {
            setDataSource(path)
            setOnCompletionListener { release() }
            setOnErrorListener { _, _, _ ->
                release()
                true
            }
            prepare()
            start()
        }
    } catch (_: Exception) {
    }
}
