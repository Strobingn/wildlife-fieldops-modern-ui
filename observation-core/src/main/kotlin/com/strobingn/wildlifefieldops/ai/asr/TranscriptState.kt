package com.strobingn.wildlifefieldops.ai.asr

/**
 * Fail-closed state machine for a voice-capture transcript lifecycle.
 *
 * ## Allowed transitions
 *
 * ```
 * PENDING ──────────────────────────────► VALID
 *    │                                     (transcript passed all validation)
 *    │
 *    ├──► RETRYABLE_FAILURE ──────────────► VALID
 *    │        (first accelerator attempt     (CPU retry succeeded)
 *    │         failed; one CPU retry
 *    │         is triggered)
 *    │           │
 *    │           └──────────────────────────► FAILED
 *    │                                        (CPU retry also failed)
 *    │
 *    └──► FAILED
 *             (non-retryable; or first
 *              attempt was already CPU)
 * ```
 *
 * ## Invariants
 *  - Only [VALID] transcripts may be committed as observation notes.
 *  - Audio artifacts are ALWAYS retained regardless of terminal state.
 *  - At most ONE CPU fallback is ever performed; there is no infinite retry loop.
 *  - Silence is the only legitimate source of an empty transcript; the validator
 *    classifies silence independently of this state machine.
 */
enum class TranscriptState {
    /**
     * Initial state: no transcription attempt has completed yet, or an attempt
     * is currently in progress.
     */
    PENDING,

    /**
     * Terminal success: the transcript passed all structural and semantic validation
     * checks.  The validated text may be committed as an observation note.
     */
    VALID,

    /**
     * Intermediate failure: the first accelerator (GPU or NPU) attempt returned
     * an empty transcript on non-silent audio (including the LiteRT-LM #3684 scenario
     * where exitOk = true but rawText = ""), or exited with a transient error.
     * Exactly one CPU fallback attempt is permitted; after that, the state
     * transitions to either [VALID] or [FAILED].
     */
    RETRYABLE_FAILURE,

    /**
     * Terminal failure: either both attempts failed, or the first attempt was
     * already on CPU (no further fallback available), or the failure was classified
     * as non-retryable.  No further attempts will be made.
     *
     * Audio is always retained in this state to allow offline review.
     */
    FAILED,
}
