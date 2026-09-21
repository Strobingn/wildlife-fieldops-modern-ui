package com.strobingn.wildlifefieldops.ui.viewmodel

import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.ai.asr.VoiceCaptureOrchestrator
import com.strobingn.wildlifefieldops.ai.asr.VoiceObservationCommitDecision
import com.strobingn.wildlifefieldops.ai.asr.VoiceObservationCommitGate
import com.strobingn.wildlifefieldops.ai.asr.VoiceObservationNoteStamp
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.VoiceObservationDao
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.VoiceObservation
import com.strobingn.wildlifefieldops.data.voice.CapturedUtteranceEngine
import com.strobingn.wildlifefieldops.data.voice.FileBackedAudioStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

enum class VoiceLogPhase {
    Idle,
    Transcribing,
    Review,
    Failed,
    Filed,
}

data class VoiceFirstUiState(
    val phase: VoiceLogPhase = VoiceLogPhase.Idle,
    val jobId: String? = null,
    val observationEventId: String? = null,
    val jobTitle: String? = null,
    val editableTranscript: String = "",
    val validatedTranscript: String = "",
    val audioUri: String? = null,
    val audioSha256: String? = null,
    val durationMs: Long = 0L,
    val error: String? = null,
    val filedMessage: String? = null,
    val audioRetained: Boolean = false,
)

@HiltViewModel
class VoiceFirstLogViewModel @Inject constructor(
    private val audioStore: FileBackedAudioStore,
    private val voiceObservationDao: VoiceObservationDao,
    private val jobDao: JobDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        VoiceFirstUiState(
            jobId = savedStateHandle.get<String>("jobId").asOptionalNavArg(),
            observationEventId = savedStateHandle.get<String>("observationEventId").asOptionalNavArg(),
        )
    )
    val state: StateFlow<VoiceFirstUiState> = _state.asStateFlow()

    val jobs: StateFlow<List<Job>> = jobDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val filedNotes: StateFlow<List<VoiceObservation>> = _state
        .flatMapLatest { ui ->
            val jobId = ui.jobId
            val eventId = ui.observationEventId
            when {
                !jobId.isNullOrBlank() -> voiceObservationDao.getByJob(jobId)
                !eventId.isNullOrBlank() -> voiceObservationDao.getByObservationEvent(eventId)
                else -> flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            refreshJobTitle(_state.value.jobId)
        }
    }

    fun bind(jobId: String?, observationEventId: String?) {
        val nextJob = jobId.asOptionalNavArg()
        val nextEvent = observationEventId.asOptionalNavArg()
        _state.update {
            it.copy(
                jobId = nextJob ?: it.jobId,
                observationEventId = nextEvent ?: it.observationEventId,
            )
        }
        viewModelScope.launch { refreshJobTitle(nextJob ?: _state.value.jobId) }
    }

    fun selectJob(job: Job) {
        _state.update {
            it.copy(jobId = job.id, jobTitle = job.title.ifBlank { job.customerName })
        }
    }

    fun updateEditedTranscript(text: String) {
        _state.update { it.copy(editableTranscript = text) }
    }

    fun transcribeCapture(
        uri: String,
        sha256: String,
        durationMs: Long,
        capturedText: String?,
    ) {
        if (_state.value.phase == VoiceLogPhase.Transcribing) return
        _state.update {
            it.copy(
                phase = VoiceLogPhase.Transcribing,
                error = null,
                filedMessage = null,
                audioUri = uri,
                audioSha256 = sha256,
                durationMs = durationMs,
                audioRetained = true,
            )
        }
        viewModelScope.launch {
            val decision = withContext(Dispatchers.Default) {
                val orchestrator = VoiceCaptureOrchestrator(
                    audioStore = audioStore,
                    transcriptionEngine = CapturedUtteranceEngine(capturedText),
                )
                val artifact = orchestrator.saveAudioAtomic(
                    uri = uri,
                    sha256 = sha256,
                    durationMs = durationMs,
                )
                val outcome = orchestrator.runTranscription(
                    audio = artifact,
                    modelSha256 = CapturedUtteranceEngine.MODEL_SHA256,
                    runtimeVersion = CapturedUtteranceEngine.RUNTIME_VERSION,
                    deviceKey = deviceKey(),
                    windowConfig = CapturedUtteranceEngine.WINDOW_CONFIG,
                )
                VoiceObservationCommitGate.evaluate(
                    outcome = outcome,
                    audio = artifact,
                    jobId = _state.value.jobId,
                    observationEventId = _state.value.observationEventId,
                    editedTranscript = null,
                    noteId = UUID.randomUUID().toString(),
                    committedAt = System.currentTimeMillis(),
                )
            }
            when (decision) {
                is VoiceObservationCommitDecision.Ready -> {
                    _state.update {
                        it.copy(
                            phase = VoiceLogPhase.Review,
                            validatedTranscript = decision.note.validatedTranscript,
                            editableTranscript = decision.note.editedTranscript,
                            audioUri = decision.note.audioUri,
                            audioSha256 = decision.note.audioSha256,
                            durationMs = decision.note.durationMs,
                            error = null,
                            audioRetained = true,
                        )
                    }
                }
                is VoiceObservationCommitDecision.Rejected -> {
                    _state.update {
                        it.copy(
                            phase = VoiceLogPhase.Failed,
                            error = decision.reason,
                            audioRetained = decision.audioRetained,
                            validatedTranscript = "",
                            editableTranscript = "",
                        )
                    }
                }
            }
        }
    }

    fun fileNote() {
        val ui = _state.value
        if (ui.phase != VoiceLogPhase.Review) return
        val sha = ui.audioSha256 ?: return
        if (ui.audioUri.isNullOrBlank()) return
        val artifact = audioStore.artifact(sha) ?: return
        viewModelScope.launch {
            val transcript = audioStore.transcript(sha)
            if (transcript == null) {
                _state.update {
                    it.copy(
                        phase = VoiceLogPhase.Failed,
                        error = "No VALID transcript is attached — not filed",
                        audioRetained = true,
                    )
                }
                return@launch
            }
            val derivedOutcome = VoiceCaptureOrchestrator.TranscriptionOutcome.Success(
                state = com.strobingn.wildlifefieldops.ai.asr.TranscriptState.VALID,
                derived = transcript,
                attempts = transcript.allAttempts,
            )
            val decision = VoiceObservationCommitGate.evaluate(
                outcome = derivedOutcome,
                audio = artifact,
                jobId = ui.jobId,
                observationEventId = ui.observationEventId,
                editedTranscript = ui.editableTranscript,
                noteId = UUID.randomUUID().toString(),
                committedAt = System.currentTimeMillis(),
            )
            when (decision) {
                is VoiceObservationCommitDecision.Rejected -> {
                    _state.update {
                        it.copy(error = decision.reason, audioRetained = true)
                    }
                }
                is VoiceObservationCommitDecision.Ready -> {
                    val note = decision.note
                    voiceObservationDao.insert(
                        VoiceObservation(
                            id = note.noteId,
                            jobId = note.jobId,
                            observationEventId = note.observationEventId,
                            audioLocalPath = note.audioUri,
                            audioSha256 = note.audioSha256,
                            durationMs = note.durationMs,
                            validatedTranscript = note.validatedTranscript,
                            editedTranscript = note.editedTranscript,
                            winningAttemptId = note.winningAttemptId,
                            observedAt = note.committedAt,
                            createdAt = note.committedAt,
                            isSynced = false,
                        )
                    )
                    note.jobId?.let { jobId ->
                        val job = jobDao.getById(jobId)
                        if (job != null) {
                            jobDao.insert(
                                job.copy(
                                    notes = VoiceObservationNoteStamp.appendToJobNotes(
                                        existingNotes = job.notes,
                                        transcript = note.editedTranscript,
                                        audioSha256 = note.audioSha256,
                                        audioUri = note.audioUri,
                                        committedAt = note.committedAt,
                                    ),
                                    updatedAt = System.currentTimeMillis(),
                                    isSynced = false,
                                )
                            )
                        }
                    }
                    _state.update {
                        it.copy(
                            phase = VoiceLogPhase.Filed,
                            filedMessage = "Filed against ${targetLabel(note.jobId, note.observationEventId)}",
                            error = null,
                        )
                    }
                }
            }
        }
    }

    fun resetSession() {
        _state.update {
            it.copy(
                phase = VoiceLogPhase.Idle,
                editableTranscript = "",
                validatedTranscript = "",
                audioUri = null,
                audioSha256 = null,
                durationMs = 0L,
                error = null,
                filedMessage = null,
                audioRetained = false,
            )
        }
    }

    private suspend fun refreshJobTitle(jobId: String?) {
        if (jobId.isNullOrBlank()) {
            _state.update { it.copy(jobTitle = null) }
            return
        }
        val job = withContext(Dispatchers.IO) { jobDao.getById(jobId) }
        _state.update {
            it.copy(jobTitle = job?.title?.ifBlank { job.customerName } ?: jobId)
        }
    }

    private fun targetLabel(jobId: String?, eventId: String?): String = buildString {
        if (!jobId.isNullOrBlank()) append(_state.value.jobTitle ?: "job $jobId")
        if (!eventId.isNullOrBlank()) {
            if (isNotEmpty()) append(" · ")
            append("event $eventId")
        }
        if (isEmpty()) append("record")
    }

    companion object {
        fun deviceKey(): String {
            val manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "unknown" }
            val model = Build.MODEL.orEmpty().ifBlank { "device" }
            val hardware = Build.HARDWARE.orEmpty().ifBlank { "soc" }
            return "$manufacturer/$model/$hardware".replace(' ', '_')
        }

        fun String?.asOptionalNavArg(): String? =
            this?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }
}
