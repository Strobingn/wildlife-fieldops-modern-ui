# Wildlife FieldOps (Native Android)

**Native Android app only** — Kotlin + Jetpack Compose.  
Not a web app. Not Capacitor. Not a dual stack.

## What it is

Field operations app for wildlife removal:

- Jobs, customers, inspections, schedule
- GPS / maps (Google Maps API key at build time)
- Invoices / estimates / expenses / inventory
- Offline-first Room database
- Cloud sync via Supabase
- Weather (OpenWeather, optional)
- AI assistant (cloud LLM and/or **real on-device abliterated llama.cpp GGUF** — no canned tip lists)

## Build the APK (GitHub Actions)

1. Repo secrets (already set on `Strobingn/wildlife-fieldops`):

| Secret | Used for |
|--------|----------|
| `VITE_SUPABASE_URL` | Supabase project URL → `SUPABASE_URL` in Gradle |
| `VITE_SUPABASE_ANON_KEY` | Supabase anon key → `SUPABASE_ANON_KEY` |
| `GOOGLE_MAPS_API` | Maps + GPS screens |
| `VITE_OPENWEATHER_API_KEY` | Weather on inspections / field |
| `VITE_GOOGLE_CALENDAR_CLIENT_ID` | Optional calendar (future) |

> `GOOGLE_MAPS_API` is the native secret/build variable. The workflow also accepts older `GOOGLE_MAPS_API_KEY`, `VITE_GOOGLE_MAPS_API_KEY`, and `VITE_GOOGLE_MAPS_API` secret names.

2. Push to `main` or run **Build Native Android APK (Debug)** in Actions.
3. Download artifact **wildlife-field-ops-debug-apk**.

## Local build

```bash
# Windows / macOS / Linux with Android SDK
export SUPABASE_URL="https://YOUR_PROJECT.supabase.co"
export SUPABASE_ANON_KEY="your-anon-key"
export GOOGLE_MAPS_API="AIza..."
export OPENWEATHER_API_KEY="..."

./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/`

## Project layout (what matters)

```
app/                    # Native Android application module
build.gradle.kts        # Root Gradle
settings.gradle.kts
.github/workflows/      # APK build
supabase/               # Cloud schema + edge functions (optional backend)
```

Legacy web / Capacitor folders may still exist in the repo history for reference.  
**They are not the product.** Do not run `npm` / Capacitor for this app.

## Sync

- Local data lives in Room (`wildlife_fieldops.db`).
- **Settings → Sync Now** pushes unsynced jobs/customers/inspections/field observations/observation events and pulls cloud rows.
- Cloud project: `wildlife_app` (`hgdzmwfcghtilyqagjak`).
- Schema applied: `supabase/migrations/20260710153000_native_sync_fix.sql` (customers table + RLS/grants for `anon`).
- Verified: REST insert/select/delete for `customers` + `jobs` works with the app anon key.

### WorkManager 2.12 sync canary

Background upload is a **debug-only canary** (`BuildConfig.WM_SYNC_CANARY_ENABLED`).
Release builds keep the existing foreground `SyncRepository.syncAll()` path.

| Build | Flag | Sync Now |
| --- | --- | --- |
| `debug` | `true` | `enqueueUniqueWork("fieldops-sync", ExistingWorkPolicy.KEEP)` + network constraint |
| `release` | `false` | today’s `syncAll()` (unchanged) |

Enablement: ship a debug APK, or add a flavor that sets `WM_SYNC_CANARY_ENABLED=true`.
Do not flip the release default without a rollback plan. Diagnostics shows **WM sync canary**.

Two ledgers (see [docs/adr/0004-workmanager-sync-canary.md](docs/adr/0004-workmanager-sync-canary.md)):

1. **Domain** — Room `sync_operations` + `isSynced` queues. ACK only after `syncAll()` succeeds. Worker `Result.success` is not exactly-once proof.
2. **Scheduler** — enqueue/start/finish/retry tags (`fo-op:…`, `fo-idk:…` only; no GPS/photos/notes/tokens). WorkManager metrics retain **~7 days**; domain audit does not.

When reconciling a missed upload, compare **server rows** vs **Room `isSynced` / operation state** vs **WM metrics / adapter events**. Observation events stay insert-ignore (`23505` / `409`).

## AI (real generative only)

The chat / estimate / summary paths never answer from hardcoded keyword tip lists.

### Cloud LLM
- Bake `XAI_API_KEY` (or `LLM_API_KEY`) into the APK at build time for SpaceXAI / Grok.
- Optional: deploy Supabase Edge Function `ai-assistant` with a real provider key (`OPENROUTER_API_KEY`, `OPENAI_API_KEY`, etc.). Demo/canned responses are disabled when no key is set.

### On-device llama.cpp + **abliterated** GGUF (default: Qwen2.5 3B; optional 7B v3)
- Stack: [`dev.ffmpegkit-maintained:llama-android:0.1.1`](https://central.sonatype.com/artifact/dev.ffmpegkit-maintained/llama-android) (prebuilt llama.cpp JNI, no NDK in this app).
- Prompts use **ChatML** (Qwen2.5 Instruct). Selection is persisted; switching unloads the previous llama weights.
- **Default — Qwen2.5-3B-Instruct-abliterated Q4_K_M:**
  - Upstream: [`huihui-ai/Qwen2.5-3B-Instruct-abliterated`](https://huggingface.co/huihui-ai/Qwen2.5-3B-Instruct-abliterated)
  - Quant repo: [`mradermacher/Qwen2.5-3B-Instruct-abliterated-GGUF`](https://huggingface.co/mradermacher/Qwen2.5-3B-Instruct-abliterated-GGUF)
  - File: **`Qwen2.5-3B-Instruct-Abliterated.Q4_K_M.gguf`** (exact **2,104,933,600** bytes ≈ **2.1 GB**, LFS SHA256 `d0b449b22bc346ab75c63d91447e3a65e7735bbcbf102b5e008d8c75028cbb3f`)
- **Optional — Qwen2.5-7B-Instruct-abliterated-v3 Q4_K_M** (bigger / slower):
  - Upstream: [`huihui-ai/Qwen2.5-7B-Instruct-abliterated-v3`](https://huggingface.co/huihui-ai/Qwen2.5-7B-Instruct-abliterated-v3)
  - Quant repo: [`mradermacher/Qwen2.5-7B-Instruct-abliterated-v3-GGUF`](https://huggingface.co/mradermacher/Qwen2.5-7B-Instruct-abliterated-v3-GGUF)
  - File: **`Qwen2.5-7B-Instruct-abliterated-v3.Q4_K_M.gguf`** (exact **4,683,074,560** bytes ≈ **4.7 GB**, LFS SHA256 `fb4821c8707f89b03bd6738c07a382744184a3f15bd6668e6500cb313fbcaa75`)
- Delivery: download-once from Hugging Face (too large for APK `assets/`). Open **AI Assistant** → pick **3B** or **7B v3** → **Download**. Cached under `files/local_llm/<filename>.gguf`.
- On refresh, legacy **Qwen3.5-0.8B** / 1.5B / MediaPipe `.task` files are deleted so the app re-downloads the new default.
- Abliteration removes safety-refusal directions so the field assistant generates freely instead of stock chat refusals.
- Build notes: no NDK/CMake. Native libs ship for **`arm64-v8a`**. `android:largeHeap` is enabled; prefer ≥6 GB RAM for 3B and ≥8 GB for 7B.
- Catalog / URLs live in `LocalLlmModelManager` (`QWEN25_3B`, `QWEN25_7B_V3`).

### Priority order
1. On-device abliterated llama.cpp GGUF when the selected model file is installed (local-first for chat)  
2. Cloud chat completions when a key is present in the APK  
3. Clear setup error (never fake “field knowledge” bullets)




## Offline maps (what works)

The map screen still uses the existing Google Maps Compose stack. Full Play Services **OfflineRegion** tile packs are not bundled.

What **does** work offline in this app:

1. **Job markers from Room** — any job/customer with saved lat/lng still renders pins without network. If Room is empty, the last marker snapshot in `map_offline_cache` is shown.
2. **Last camera** — Map camera lat/lng/zoom is persisted to DataStore and restored on reopen.
3. **Service-area tile cache** — tap **Cache** on the map to download Carto/OSM light tiles for the current viewport (bounded budget). Tiles live under `files/map_tiles` and appear as a GoogleMap `TileOverlay` when the device is offline.
4. **Offline observation pins** — tap **Observe**, drop a pin, add a note and optional photo + GPS. Rows stay in Room (`field_observations` + `photos`) with `isSynced = 0` and push through **Settings → Sync Now** (same Supabase path as jobs).
5. **Offline banner** — Map shows cached-tile / outside-cached-area / Room-pins-only state from ConnectivityManager.
6. **Online maps unchanged** — with network, GoogleMap tiles + style load normally.

Apply `supabase/migrations/20260921153000_field_observations.sql` on the cloud project so observation upserts succeed. Until that migration is applied, Room still keeps the records and sync reports a warning.

Voice job intake and AI report paths prefer the **on-device LLM** when downloaded, so field forms can still fill without cloud.

## V2.5 route planner and visual system

- The Routes screen now reads active jobs from Room and uses only jobs with saved latitude/longitude.
- Route ordering runs locally with nearest-neighbor plus 2-opt improvement; no paid routing API is required.
- The planner reports stops, estimated travel/service time, optional return-to-start distance, and can hand the ordered stops to Google Maps.
- The app-wide Material palette is grayscale for a cleaner field-operations UI. Legacy color names remain only for source compatibility.
- See [docs/FIELD_OPPS_V2_5_FEATURE_ROADMAP.md](docs/FIELD_OPPS_V2_5_FEATURE_ROADMAP.md) for the next ten feature proposals.
- See [docs/FIELD_OPPS_V2_5_FEATURE_ROADMAP_20.md](docs/FIELD_OPPS_V2_5_FEATURE_ROADMAP_20.md) for the 20-feature expansion backlog.
