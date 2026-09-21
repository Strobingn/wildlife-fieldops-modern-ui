package com.strobingn.wildlifefieldops.data.voice

import com.strobingn.wildlifefieldops.ai.asr.AudioArtifact
import com.strobingn.wildlifefieldops.ai.asr.AudioStore
import com.strobingn.wildlifefieldops.ai.asr.DerivedTranscript
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-local [AudioStore]. Audio bytes live on disk (the capture recorder
 * writes the WAV); this store records the durable artifact handle and any
 * [DerivedTranscript] the orchestrator was allowed to commit.
 */
@Singleton
class FileBackedAudioStore @Inject constructor() : AudioStore {
    private val artifacts = ConcurrentHashMap<String, AudioArtifact>()
    private val transcripts = ConcurrentHashMap<String, DerivedTranscript>()

    override fun save(artifact: AudioArtifact) {
        artifacts[artifact.sha256] = artifact
    }

    override fun exists(sha256: String): Boolean = artifacts.containsKey(sha256)

    override fun commitTranscript(transcript: DerivedTranscript) {
        transcripts[transcript.audioSha256] = transcript
    }

    fun artifact(sha256: String): AudioArtifact? = artifacts[sha256]

    fun transcript(sha256: String): DerivedTranscript? = transcripts[sha256]
}
