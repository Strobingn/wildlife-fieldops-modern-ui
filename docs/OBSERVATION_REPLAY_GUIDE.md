# Observation Replay Tests — Operator Guide

**Applies to:** FieldOps Android app · Immutable Observation Architecture (ADR 0002)

---

## Overview

The observation replay test suite verifies that the `ObservationProjector` — the
pure Kotlin engine that turns immutable `ObservationEvent` records into displayable
`DerivedAssessment` labels — behaves correctly regardless of how evidence arrives.
It runs entirely on the JVM (no device or emulator needed).

---

## Running the Tests

### Prerequisites

- JDK 17 or higher (already required by the app module)
- Android Gradle Plugin installed (Gradle wrapper is included in the repo)

No Supabase credentials, Google Maps keys, or device connections are required.

### Command

From the repository root:

```bash
./gradlew :observation-core:test
```

This runs the pure-JVM observation module tests without requiring an Android SDK
or device/emulator.  The `:observation-core` subproject is a vanilla Kotlin/JVM
module; the same source files are mirrored into `:app` for production use once
the Room schema migration is complete (ADR 0002 §9).

To run all app unit tests (requires Android SDK):

```bash
./gradlew :app:testDebugUnitTest
```

### Expected output

```
com.strobingn.wildlifefieldops.data.observation.ObservationProjectorTest > sameEvidenceDifferentOrderProducesSameAssessment PASSED
com.strobingn.wildlifefieldops.data.observation.ObservationProjectorTest > twoEventPermutationsConverge PASSED
com.strobingn.wildlifefieldops.data.observation.ObservationProjectorTest > humanCorrectionPreservedAgainstHighConfidenceModelEvent PASSED
...
BUILD SUCCESSFUL
```

All tests in `ObservationProjectorTest` must be green before merging any change
to the projector or data classes.

---

## What the Tests Verify

| Test name | What it checks |
|-----------|---------------|
| `sameEvidenceDifferentOrderProducesSameAssessment` | 6 permutations of 3 events → identical `primaryLabel` and `uncertainty` |
| `twoEventPermutationsConverge` | 2-event forward/reverse order converges |
| `humanCorrectionPreservedAgainstHighConfidenceModelEvent` | CORRECTED event wins over 0.99-confidence model output |
| `humanConfirmationPersistsEvenAfterLateReorderingModelEvents` | CONFIRMED label survives out-of-order reconnect |
| `humanCorrectionToRareClassPreservedAgainstManyCommonClassVotes` | Rare-class human label not overturned by 5 × common-class model votes |
| `duplicateEventIsIgnoredIdempotently` | Uploading same event 1×, 2×, 3× yields same assessment |
| `reuploadOfHumanCorrectionIsIdempotent` | Duplicate CORRECTED events deduplicated cleanly |
| `lwwDivergesWhereConservativeProjectorConverges` | Documents that last-write-wins would produce different results under network reorder |
| `clockSkewFlagsNeedsReview` | Event with 25-hour capture/upload delta triggers NEEDS_REVIEW |
| `modelVersionMixFlagsNeedsReview` | Mixed v1/v2 model events trigger NEEDS_REVIEW |
| `disputedEventWithConflictingLabelSurfacesDisputedStatus` | DISPUTED event with different label surfaces DISPUTED status |
| `zeroQualityEventExcludedFromWeightedAggregation` | Zero-quality blurry frame doesn't flip the label |
| `singleUnreviewedEventProducesStableAssessment` | Baseline: single clean inference → STABLE |
| `outOfOrderReconnectConverges` | Two offline sessions arriving in swapped order → same result |
| `eventsForOtherEntityAreIgnored` | Events with a different `entityId` are silently excluded |

---

## What Maps Should Consume

The `MapScreen` and `MapViewModel` must read **only** `DerivedAssessment` records.

```
MapViewModel
  └── reads DerivedAssessment (entityId, primaryLabel, status, uncertainty, humanVerifiedLabel)
        └── never reads ObservationEvent rows directly
```

Key fields for map marker display:

| Field | Use |
|-------|-----|
| `primaryLabel` | Marker icon / species label |
| `humanVerifiedLabel` | If non-null, display with a verification badge |
| `status` | Colour-code: STABLE=green, NEEDS_REVIEW=amber, DISPUTED=red, HUMAN_OVERRIDE=blue |
| `uncertainty` | Optional confidence bar beneath the label |
| `alternatives` | Tap-to-expand detail sheet showing ranked alternatives |

The `DerivedAssessment` is recomputed by `ObservationProjector.project()` whenever
new `ObservationEvent`s are ingested.  Until Room schema migration is complete
(see ADR 0002 §9), this projection runs in-memory over the full event set for
each entity on each sync cycle.

---

## Adding New Evidence Types

1. Append a new `ObservationEvent` to the event store.  Never modify existing rows.
2. Call `ObservationProjector.project(entityId, allEventsForEntity)`.
3. Persist the returned `DerivedAssessment` to the cache table (or update in-memory state).
4. The map will reflect the new assessment on next render.

---

## Architecture Reference

See [docs/adr/0002-immutable-observation-events.md](adr/0002-immutable-observation-events.md)
for the full design rationale, data model, rejected alternatives, and Room migration notes.
