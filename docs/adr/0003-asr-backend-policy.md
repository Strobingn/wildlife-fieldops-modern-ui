# ADR 0003 — ASR Backend Policy: Fail-Closed Transcript State Machine

**Status:** Accepted  
**Date:** 2026-09-19  
**Author:** FieldOps Platform (brief item #2)  
**Supersedes:** —  
**Related:** LiteRT-LM issue #3684, ADR 0001 (faithfulness gate), ADR 0002 (immutable observation events)

---

## Context

The Wildlife FieldOps app performs offline voice capture using an on-device ASR model
(Qwen3-ASR via LiteRT-LM). A known defect in LiteRT-LM (issue **#3684**) allows the GPU
inference path to:

1. Initialise successfully on the GPU,
2. Exit with code 0 (`exitOk = true`), and
3. Return an **empty transcript**.

An empty transcript committed as a valid observation note is a data-quality failure.
Field officers may later discover the blank note and attempt to re-capture audio that
no longer exists (the original capture window has passed), or, worse, submit the blank
note into the observation record silently.

This ADR describes the domain types, state machine, validator, and backend policy that
prevent this from happening.

---

## Decision

### 1. Domain types

All ASR provenance types live in the `observation-core` JVM module under the package
`com.strobingn.wildlifefieldops.ai.asr`. They carry no Android, Room, or coroutine
dependencies.

| Type | Role |
|------|------|
| `AudioArtifact` | Immutable on-device audio record (URI, SHA-256, duration, capturedAt). |
| `InferenceBackend` | Enum: `NPU`, `GPU`, `CPU`. |
| `TranscriptAttempt` | Single model run provenance: backend, model SHA-256, runtime version, device key, window config, start/end times, exitOk, rawText, error. |
| `TranscriptState` | State machine enum (see §2). |
| `DerivedTranscript` | Committed result: audio SHA-256, validated text, winning attempt ID, all attempts. Created only when state is `VALID`. |

### 2. Transcript state machine (fail-closed)

```
                  ┌─────────────────────────────────────────────┐
                  │                 PENDING                     │
                  └──────────────────────┬──────────────────────┘
                                         │
              ┌──────────────────────────┼──────────────────────────┐
              │                          │                          │
              ▼                          ▼                          ▼
         RETRYABLE_FAILURE           VALID ✓                    FAILED ✗
         (first accel. empty          (committed)               (non-retryable;
          or crashed)                                            or CPU-first)
              │
     [one CPU retry only]
              │
     ┌────────┴────────┐
     ▼                 ▼
  VALID ✓           FAILED ✗
  (committed)       (audio retained)
```

Rules:
- Only `VALID` transcripts may be committed as observation notes.
- Audio is **always retained** regardless of terminal state.
- **At most one CPU fallback** is ever performed — no infinite retry loop.
- The silence classifier is the only legitimate path to an empty `VALID` transcript.

### 3. Validator / gate (`TranscriptValidator`)

The validator applies checks in order (fail-closed — first failure wins):

1. **Silence exemption** — if `isSilentAudio(audio)` returns true, empty transcript → `VALID`.
   The stub always returns `false` (safe default).  
   `TODO(energy-threshold)`: replace with real RMS classifier (threshold: RMS ≤ −40 dBFS).

2. **Null guard** — `rawText == null` means the process crashed → `Failure`.

3. **Non-empty / non-whitespace** — empty or blank trimmed text on non-silent audio → `Failure`.
   When `exitOk = true` the failure message explicitly names LiteRT-LM issue #3684.

4. **Token-spam detection** — if a single token comprises > 60% of all tokens (minimum 4
   tokens), the output is flagged as a model repetition loop → `Failure`.

5. **Duration heuristic** — for audio ≥ 5 s, transcripts yielding < 0.5 chars/second are
   flagged as suspiciously sparse → `Failure`.

All thresholds are constructor-injectable for testing and future tuning.

### 4. Orchestrator (`VoiceCaptureOrchestrator`)

```
saveAudioAtomic(uri, sha256, durationMs) → AudioArtifact
runTranscription(audio, modelSha256, runtimeVersion, deviceKey, windowConfig) → TranscriptionOutcome
```

Retry budget:
```
attempt(preferredBackend)
  → VALID?  commit → Success
  → RETRYABLE_FAILURE AND preferredBackend ≠ CPU?
      attempt(CPU)
        → VALID?  commit → Success
        → else?   Failure(FAILED)
  → else?  Failure(FAILED)
```

### 5. Backend policy key

The allowlist key is the **5-tuple**:

```
model_sha256 × runtime_version × backend × device_key × window_config
```

Where:
- `model_sha256` — SHA-256 of the ASR model flatbuffer binary.
- `runtime_version` — LiteRT-LM / runtime version string (e.g. `"litert-lm-1.0.1"`).
- `backend` — `NPU | GPU | CPU`.
- `device_key` — stable device/SoC identifier (e.g. `"google/Pixel_8_Pro/tensor_g3"`).
- `window_config` — serialised window configuration (e.g. `"chunkMs=2000,overlapMs=200"`).

**Untested combination → CPU only (safe default).**  
An accelerator backend is **never** selected unless it appears in a signed allowlist entry
for the exact (model × runtime × device × window) tuple.

Resolution preference order within an approved entry: `NPU > GPU > CPU`.
`CPU` is always the final element in the resolved list, even when not explicitly allowlisted.

The production allowlist ships empty; it should be populated from a signed remote config
fetched at startup, with a local signed bundle as fallback.

### 6. GPU disable scope

GPU is **not globally disabled**. The allowlist stub ships empty, so no GPU runs occur
until a validated entry is added. This is an allowlist (opt-in), not a blocklist (opt-out),
which means new device/model combinations are always safe by default.

---

## Consequences

**Positive:**
- Empty GPU transcripts (LiteRT-LM #3684) are detected and retried on CPU automatically.
- No observation note can be committed without a validated, non-blank transcript.
- All attempt provenance (backend, model, runtime, device, timestamps) is preserved for
  post-incident analysis.
- The allowlist design prevents untested accelerator configurations from running in production.

**Negative / trade-offs:**
- A CPU fallback adds latency (typically 3–10× slower than GPU for Qwen3-ASR on Pixel 8 Pro).
  This is acceptable for field capture: the user is informed of the retry, and the audio
  artifact is always retained.
- The silence classifier stub always returns `false`, meaning legitimate silence recordings
  are currently classified as `FAILED` rather than `VALID`-empty. This is intentional and
  fail-closed; it must be resolved before shipping voice capture to production.
- The allowlist starts empty; the ops team must populate it before GPU inference is used.

---

## Implementation notes

- Package: `com.strobingn.wildlifefieldops.ai.asr` in `:observation-core`.
- No Room migration required (pure Kotlin types; persistence injected via `AudioStore` interface).
- Tests: `VoiceCaptureOrchestratorTest` (24 test cases) in `:observation-core`.
- Maps/ARCore/CameraX/ML Kit/LiteRT-LM dependency pins are unchanged.
