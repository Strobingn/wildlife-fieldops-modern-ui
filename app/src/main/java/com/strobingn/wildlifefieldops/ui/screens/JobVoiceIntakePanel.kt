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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.strobingn.wildlifefieldops.data.remote.JobIntakeDraft
import com.strobingn.wildlifefieldops.ui.theme.*
import java.util.Locale
@Composable
fun JobVoiceIntakePanel(
    aiFillLoading: Boolean,
    aiFillError: String?,
    aiFillSource: String?,
    onClearAiFeedback: () -> Unit,
    onFillFromDictation: (String, (JobIntakeDraft) -> Unit) -> Unit,
    onApplyDraft: (JobIntakeDraft) -> Unit
) {
    val context = LocalContext.current
    var dictationNotes by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var dictationError by remember { mutableStateOf<String?>(null) }
    var speechPartial by remember { mutableStateOf("") }
    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }
    fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }
    fun startListening() {
        val sr = speechRecognizer ?: run {
            dictationError = "Speech recognition not available on this device"
            return
        }
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { dictationError = null }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech — tap Dictate again"
                    else -> "Speech error ($error)"
                }
                dictationError = msg
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    if (isListening) {
                        try { sr.startListening(buildIntent()); return } catch (_: Exception) {}
                    }
                }
                isListening = false
                speechPartial = ""
            }
            override fun onResults(results: Bundle?) {
                val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val best = texts.firstOrNull().orEmpty()
                if (best.isNotBlank()) {
                    dictationNotes = listOf(dictationNotes.trim(), best)
                        .filter { it.isNotBlank() }.joinToString(" ")
                }
                speechPartial = ""
                if (isListening) {
                    try { sr.startListening(buildIntent()) } catch (_: Exception) {
                        isListening = false
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val texts = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                speechPartial = texts.firstOrNull().orEmpty()
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        try {
            isListening = true
            sr.startListening(buildIntent())
        } catch (e: Exception) {
            isListening = false
            dictationError = e.message ?: "Failed to start listening"
        }
    }
    fun stopListening() {
        isListening = false
        speechPartial = ""
        try { speechRecognizer?.stopListening() } catch (_: Exception) {}
    }
    DisposableEffect(Unit) {
        onDispose {
            try { speechRecognizer?.destroy() } catch (_: Exception) {}
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListening()
        else {
            dictationError = "Microphone permission denied. Enable RECORD_AUDIO in system settings."
            isListening = false
        }
    }
    fun toggleDictate() {
        if (isListening) stopListening()
        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) startListening()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundElevated),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Dictate new job", color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(
                "Say customer, address, species/service, priority, and notes. Tap AI Fill Job to parse into fields — edit before save.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { toggleDictate() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListening) ErrorRed else AccentBlue
                    )
                ) {
                    Icon(
                        if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isListening) "Listening… tap to stop" else "Dictate")
                }
                if (isListening) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = AccentBlue, strokeWidth = 2.dp)
                }
            }
            if (speechPartial.isNotBlank()) {
                Text("Hearing: $speechPartial", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
            }
            OutlinedTextField(
                value = dictationNotes,
                onValueChange = { dictationNotes = it },
                label = { Text("Transcript") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Button(
                onClick = {
                    onClearAiFeedback()
                    onFillFromDictation(dictationNotes) { draft -> onApplyDraft(draft) }
                },
                enabled = !aiFillLoading && dictationNotes.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (aiFillLoading) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                }
                Text("AI Fill Job", fontWeight = FontWeight.Bold)
            }
            aiFillSource?.let {
                Text(it, color = PrimaryGreen, style = MaterialTheme.typography.labelSmall)
            }
            (dictationError ?: aiFillError)?.let {
                Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
