# ADR 0002 – Immutable ObservationEvent / Derived Projection Architecture

**Status:** Accepted  
**Date:** 2026-09-18  
**Context:** FieldOps Brief Item #2

---

## Context

The Wildlife FieldOps app captures species identifications from live camera frames
(CameraX + TFLite / ML Kit pipeline) and syncs them to Supabase. The previous
approach wrote each classification result directly into a mutable record keyed on
`entityId`, meaning a lower-confidence later frame silently overwrote a
high-confidence earlier one. Under offline/reconnect scenarios (PerSeM lesson:
field operators lose connectivity mid-session and reconnect out of order), the
last upload won regardless of evidence quality. UAV voxel geometry is out of
scope for this sprint; this ADR addresses the non-UAV observation pipeline only.

---

## Decision

### 1. Immutable Evidence Layer – `ObservationEvent`

Every classifier inference or human correction is appended as an immutable event.
Events are **never updated or deleted** after creation. An `eventId` is a
deterministic content hash so that the same evidence uploaded multiple times
produces an identical record (idempotent).

```
ObservationEvent {
    eventId              : String    // SHA-256 of (entityId + observedAt + frameHash + modelId)
    entityId             : String    // stable site / individual ID
    observedAt           : Long      // capture clock epoch-ms (device time of frame, NOT upload time)
    uploadedAt           : Long      // wall-clock arrival; used only for diagnostics
    deviceId             : String    // sensor hardware identifier
    operatorId           : String    // field operator UUID
    modelId              : String    // e.g. "wildlife_evidence_v3"
    modelHash            : String    // SHA-256 of the TFLite flatbuffer
    backendTag           : String    // "tflite" | "mlkit" | "litert" | "human"
    quantizerTag         : String    // "int8" | "float16" | "none"
    frameHash            : String    // perceptual hash of the captured frame
    cropHash             : String    // hash of the detection-crop region (may differ from frameHash)
    mediaUri             : String?   // optional content-URI / object-store key; null for live frames
    labelDistribution    : Map<String,Float>  // label → confidence; sums ≤ 1.0
    captureQuality       : Float     // frame sharpness score [0,1]
    geometryTrust        : Float     // GPS/IMU fix confidence [0,1]; 0 when unavailable
    humanVerificationState : HumanVerificationState
    supersedesEventId    : String?   // links a human correction to the event it corrects; null otherwise
}

enum HumanVerificationState {
    UNREVIEWED,        // raw model output, no operator review
    CONFIRMED,         // operator confirmed model label is correct
    CORRECTED,         // operator provided a different label (see supersedesEventId)
    DISPUTED           // operator marked evidence as ambiguous / contested
}
```

**Invariants:**
- `eventId` is recomputed on ingest; duplicate eventIds are silently discarded.
- `observedAt` is the device-local capture timestamp, not the server receive time.
  Clock-skew events (|observedAt − uploadedAt| > 24 h) are accepted but flagged.
- `labelDistribution` is read-only after creation; reshaping a distribution for a
  new model version requires a new event with the new `modelId`.

---

### 2. Derived Assessment Layer – `DerivedAssessment`

A `DerivedAssessment` is a **pure function of a set of `ObservationEvent`s**. It
is recomputed whenever new evidence arrives; it is never a source of truth itself.

```
DerivedAssessment {
    entityId             : String
    projectionVersion    : Int       // bumped whenever the projector logic changes
    primaryLabel         : String
    alternatives         : List<LabelAlternative>  // ranked by aggregated weight
    uncertainty          : Float     // 1 - max(aggregated confidence); [0,1]
    evidenceEventIds     : Set<String>  // IDs of all events that contributed
    status               : AssessmentStatus
    humanVerifiedLabel   : String?   // non-null only when ≥1 CONFIRMED/CORRECTED event exists
    lastProjectedAt      : Long      // wall-clock epoch-ms of this projection run
}

data class LabelAlternative(val label: String, val weight: Float)

enum AssessmentStatus {
    STABLE,           // all non-disputed events agree
    DISPUTED,         // ≥1 DISPUTED event with a different label than primary
    NEEDS_REVIEW,     // model versions differ across evidence, or clock-skew flag present
    HUMAN_OVERRIDE    // humanVerifiedLabel is set and supersedes model consensus
}
```

---

### 3. Evidence vs. Projection; Confidence vs. Trust

| Layer             | Persisted? | Mutable? | Who writes it      |
|-------------------|------------|----------|--------------------|
| ObservationEvent  | Yes        | **Never**| Classifier / operator |
| DerivedAssessment | Derived    | Recomputed | Projector (pure fn) |

| Field                 | Meaning                                           |
|-----------------------|---------------------------------------------------|
| `labelDistribution`   | Raw model or operator confidence; local to event |
| `geometryTrust`       | GPS/IMU fix quality at capture time              |
| `captureQuality`      | Frame sharpness; affects projector weight         |
| `humanVerificationState` | Operator intent; overrides model confidence   |
| `uncertainty`         | Projector-computed spread across aggregated labels|

Confidence and trust are kept in separate fields so that a high-confidence model
result on a poor-quality frame does not masquerade as high-trust evidence.

---

### 4. Room as Offline Source-of-Truth; Maps consumes DerivedAssessment only

- **Room** stores `ObservationEvent` rows append-only (no UPDATE, no DELETE).
  A `conflict = IGNORE` strategy on the primary key enforces idempotency.
- **DerivedAssessment** is either recomputed in-memory from the full event set or
  cached in a separate Room table (never the authoritative record).
- **MapScreen / MapViewModel** reads only `DerivedAssessment` records, never
  raw `ObservationEvent` rows. This insulates the map display from evidence
  ordering artifacts.
- On sync reconnect, the device replays unsynced events to Supabase; the
  projector re-runs server-side after all events land.

---

### 5. Human-Verified Evidence Precedence

The projector applies a **trust hierarchy**:

```
CORRECTED > CONFIRMED > UNREVIEWED (model output) > DISPUTED (excluded)
```

If any `CORRECTED` or `CONFIRMED` event exists for an entity:
- `humanVerifiedLabel` is set.
- `status` becomes `HUMAN_OVERRIDE`.
- Model-only events with lower aggregate confidence cannot silently flip the label.
- A subsequent model event with higher raw confidence does **not** override a
  human correction; it is recorded as evidence and surfaces `NEEDS_REVIEW`.

---

### 6. Commutativity Guarantee

The projector is a pure function:

```
project(events: Set<ObservationEvent>) → DerivedAssessment
```

Because `Set` has no order, identical evidence inputs always produce identical
outputs. Implementations must not rely on insertion order, arrival time, or
upload batch identity.

Practically: sort events by `(eventId)` before iterating when ordering is
needed for deterministic tie-breaking. Use `eventId` (content hash) rather than
`uploadedAt` (wall clock) for all tie-break decisions.

---

### 7. Explicitly Rejected Strategy: Last-Write-Wins (LWW)

LWW is rejected because:
- A high-confidence early capture is silently erased by a low-confidence late
  upload in any reconnect scenario.
- LWW result depends on network order, not evidence quality.
- LWW cannot represent "we have conflicting evidence" — a scientifically relevant
  state for rare-species records.

The unit test suite includes a dedicated test that demonstrates LWW diverging
under reorder where the conservative projector converges.

---

### 8. Edge Cases and Handling Strategy

| Scenario                    | Strategy                                                         |
|-----------------------------|------------------------------------------------------------------|
| Duplicate upload            | `eventId` collision → IGNORE (idempotent)                        |
| Out-of-order reconnect      | All events accepted; projector re-runs after each new event     |
| Clock skew (>24 h delta)    | Event accepted, `NEEDS_REVIEW` status set on assessment          |
| Contradictory model labels  | Both retained; `uncertainty` rises; `DISPUTED` if explicit       |
| Human correction            | `CORRECTED` event with `supersedesEventId`; `HUMAN_OVERRIDE`     |
| Model version change        | New event with new `modelId`/`modelHash`; `NEEDS_REVIEW`         |
| Rare-class preservation     | Conservative projector preserves human-labelled rare class even  |
|                             | if later model events vote for common class                       |
| captureQuality = 0          | Event excluded from confidence-weighted aggregation; still stored|

---

### 9. Room Schema Spike Notes (out of scope for this PR)

When the production schema migration is scheduled, the following additions are
anticipated:

```sql
CREATE TABLE observation_events (
    event_id              TEXT PRIMARY KEY,
    entity_id             TEXT NOT NULL,
    observed_at           INTEGER NOT NULL,
    uploaded_at           INTEGER NOT NULL,
    device_id             TEXT NOT NULL,
    operator_id           TEXT NOT NULL,
    model_id              TEXT NOT NULL,
    model_hash            TEXT NOT NULL,
    backend_tag           TEXT NOT NULL,
    quantizer_tag         TEXT NOT NULL,
    frame_hash            TEXT NOT NULL,
    crop_hash             TEXT NOT NULL,
    media_uri             TEXT,
    label_distribution    TEXT NOT NULL,  -- JSON blob
    capture_quality       REAL NOT NULL,
    geometry_trust        REAL NOT NULL,
    human_verification    TEXT NOT NULL,
    supersedes_event_id   TEXT
);

CREATE TABLE derived_assessments (
    entity_id             TEXT PRIMARY KEY,
    projection_version    INTEGER NOT NULL,
    primary_label         TEXT NOT NULL,
    alternatives          TEXT NOT NULL,  -- JSON blob
    uncertainty           REAL NOT NULL,
    evidence_event_ids    TEXT NOT NULL,  -- JSON array
    status                TEXT NOT NULL,
    human_verified_label  TEXT,
    last_projected_at     INTEGER NOT NULL
);
```

`DerivedAssessment` rows should be regenerated by replaying all events after
any schema migration; they are **not** migrated row-by-row.

---

## Consequences

**Positive:**
- Full audit trail; every classification is preserved.
- Commutativity: safe offline/reconnect; safe multi-device sync.
- Human corrections are permanent and cannot be silently overwritten.
- Map display is insulated from evidence internals.

**Negative:**
- Storage grows with every observation. Pruning policy (archive events older than
  N months to cold storage) must be defined before production deployment.
- DerivedAssessment must be re-projected on startup if the event log grew offline.
  For large event sets this is a background task; a cached assessment should be
  displayed until the re-projection completes.

---

## Alternatives Considered

| Alternative                   | Reason rejected                                          |
|-------------------------------|----------------------------------------------------------|
| Last-write-wins (mutable row) | Non-commutative; loses best evidence on reconnect        |
| Event sourcing with snapshots | Considered future optimization; premature for this scope |
| Server-side-only projection   | Requires connectivity; breaks offline-first guarantee    |
