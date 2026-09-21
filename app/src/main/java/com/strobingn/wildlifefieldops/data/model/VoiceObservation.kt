package com.strobingn.wildlifefieldops.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Filed voice-first observation note. Only rows that passed the fail-closed
 * [com.strobingn.wildlifefieldops.ai.asr.VoiceObservationCommitGate] are inserted.
 *
 * [validatedTranscript] is the ASR text; [editedTranscript] is the technician
 * review copy. Original audio stays on disk at [audioLocalPath].
 */
@Entity(tableName = "voice_observations")
data class VoiceObservation(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val jobId: String? = null,
    val observationEventId: String? = null,
    val audioLocalPath: String,
    val audioSha256: String,
    val durationMs: Long,
    val validatedTranscript: String,
    val editedTranscript: String,
    val winningAttemptId: String,
    val observedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
)
