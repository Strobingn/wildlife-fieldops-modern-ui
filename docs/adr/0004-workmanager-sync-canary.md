# ADR 0004 — WorkManager 2.12 sync canary (two-ledger model)

- **Status:** Accepted (FieldOps engineering brief 2026-09-24)
- **Date:** 2026-09-24
- **Related:** ADR 0002 (immutable ObservationEvent insert-ignore)

## Context

Offline upload is foreground-only today: Settings → Sync Now calls
`SyncRepository.syncAll()` (coroutines + Room + Supabase). Rows use `isSynced`
and the observation / event queues. Observation events treat Postgres `23505` /
HTTP `409` as already-synced (insert-ignore). There is no WorkManager unique-work
policy.

WorkManager **2.12.0** (stable 2026-09-23) adds experimental `work-analytics`,
`ScheduleEventListener`, and `ExecutionEventListener`. `minSdk = 29` is above the
library’s API-24 floor. This ADR introduces a **guarded canary**, not a cutover.

## Decision

### 1. Two ledgers stay separate

| Ledger | Source of truth for | Retention |
| --- | --- | --- |
| **Domain sync ledger** | Business state of one sync *operation* and per-row `isSynced` / queues | Durable Room (`sync_operations` + existing tables) and Supabase |
| **Scheduler telemetry** | enqueue / unblock / start / finish / stop / retry / failed-prerequisite / generation / duration | WorkManager metrics default **~7 days**; in-process adapter snapshot is process-local |

`ListenableWorker.Result.success` is **not** exactly-once proof and is **not**
an ACK. Domain `ACKNOWLEDGED` is written only after `SyncRepository.syncAll()`
returns `success = true`. Partial row failures still leave `isSynced = 0`.

Domain operation states:

`PENDING → READY → UPLOADING → ACKNOWLEDGED | CONFLICT | RETRYABLE_FAILURE | TERMINAL_FAILURE`

### 2. Feature flag

`BuildConfig.WM_SYNC_CANARY_ENABLED`

- **release:** `false` — Sync Now stays on today’s `syncAll()` path.
- **debug / internal canary:** `true` — Sync Now enqueues unique work.

Override for tests by injecting `WorkManagerSyncCanaryFlag`.

### 3. Unique work policy

```
enqueueUniqueWork("fieldops-sync", ExistingWorkPolicy.KEEP, request)
```

**KEEP** is intentional: a second Sync Now while work is unfinished is ignored.
`REPLACE` would cancel an in-flight upload; it still would not clear Room
pending, but it can interrupt photo / event pushes. Cancel / update calls
`revertInFlightToReady()` only — observation queues are untouched.

Constraints: `NetworkType.CONNECTED`.

### 4. Experimental APIs stay behind an adapter

`WorkAnalyticsAdapter` is the production type. `WorkManagerConfigurationFactory`
is the only class that `@OptIn`s `ExperimentalEventsApi` /
`ExperimentalWorkMetricsApi` and constructs `WorkMetricsInfoRepository`.
When the flag is off, listeners are not registered and adapter writes are no-ops.

### 5. Correlation / privacy

WorkRequest tags and input `Data` may contain only:

- `fieldops-sync`, `fo-sync`
- `fo-op:<operationId>`
- `fo-idk:<idempotencyKey>`

Tokens are bounded (`[A-Za-z0-9._-]`, max 64). **Never** tag or put in `Data`:
photo bytes / paths, GPS, species notes, auth tokens, operator names, or other PII.

When practical, `workRequestId` and `generation` are stored on the domain
`sync_operations` row.

### 6. Reuse SyncRepository

`FieldOpsSyncWorker` → `FieldOpsSyncWorkRunner` → `FieldOpsSyncGateway`
(`SyncRepository`). No second Supabase upsert / photo path.

## How to enable the canary

1. Debug APK: already on (`debug { WM_SYNC_CANARY_ENABLED = true }`).
2. Release: remains off. To ship a store canary, add a product flavor that sets
   the BuildConfig field `true` — do not flip release default without a
   rollback plan.
3. Settings → Sync Now (flag on) enqueues `fieldops-sync` and prints the
   operation id. Diagnostics shows **WM sync canary**.
4. Flag off: same Sync Now / `SyncRepository` path as before this ADR.

## Reconciling server / domain / WM metrics

When investigating a missed or duplicate upload:

1. **Server (Supabase):** row exists? Event insert-ignore (`23505` / `409`)
   means a retry is success, not a new fact.
2. **Domain Room:** `isSynced` / queue remaining + `sync_operations.state`.
   `ACKNOWLEDGED` means the batch call returned success, not that every row
   pushed. `RETRYABLE_FAILURE` means do not treat WM `Result.retry` as terminal.
3. **WM metrics / adapter snapshot:** enqueue → unblock → start → finish /
   retry / stop. Metrics older than ~7 days are gone; domain rows are not.

Mismatch patterns:

- WM `FINISH` + domain `RETRYABLE_FAILURE` — should not happen; runner ACKs
  only on repository success.
- WM `FINISH` + domain `ACKNOWLEDGED` + server missing a row — check warnings
  inside `SyncResult.message` (per-row skip).
- Duplicate enqueue + one domain operation — KEEP + `beginOrReuseActive`
  (insert-ignore). Expected.

## Device chaos matrix (outline only)

Out of scope for this PR. Later on-device script:

1. Seed N unsynced jobs / observations / events (stable ids).
2. Toggle airplane mode mid-upload; confirm domain stays READY / RETRYABLE
   and `isSynced` is unchanged for failed rows.
3. Force-stop the app; confirm unique work resumes with KEEP and no second
   ACK for the same `operationId`.
4. Re-enqueue while running; assert one `WorkRequest` generation in flight.
5. Replay the same `eventId`; server insert-ignore, queue `afterSuccessfulPush`
   flips once.
6. Compare counts: server rows vs Room `isSynced=1` vs WM finish events
   (do not expect 1:1 with `Result.success`).

Do **not** include RAMP / Visual Tripwires / DerivedAssessment upload.

## Consequences

- Debug builds schedule background sync on Sync Now; release behavior is unchanged.
- New Room table `sync_operations` (v8 → v9).
- Experimental WM analytics can change; only the configuration factory imports them.
