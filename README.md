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
- AI assistant (Grok + optional Microsoft Agent Framework sidecar + on-device fallback)

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
# optional MAF sidecar for GrokAIV5:
export AGENT_FRAMEWORK_URL="http://10.0.2.2:8088"

./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/`

## Project layout (what matters)

```
app/                    # Native Android application module
agents/fieldops-maf/    # Microsoft Agent Framework sidecar (GrokAIV5)
build.gradle.kts        # Root Gradle
settings.gradle.kts
.github/workflows/      # APK build
supabase/               # Cloud schema + edge functions (optional backend)
```

Legacy web / Capacitor folders may still exist in the repo history for reference.  
**They are not the product.** Do not run `npm` / Capacitor for this app.

## Sync

- Local data lives in Room (`wildlife_fieldops.db`).
- **Settings → Sync Now** pushes unsynced jobs/customers/inspections and pulls cloud rows.
- Cloud project: `wildlife_app` (`hgdzmwfcghtilyqagjak`).
- Schema applied: `supabase/migrations/20260710153000_native_sync_fix.sql` (customers table + RLS/grants for `anon`).
- Verified: REST insert/select/delete for `customers` + `jobs` works with the app anon key.

## AI

- Live chat uses xAI Grok when `XAI_API_KEY` is baked into the APK.
- GrokAIV5 optionally routes chat through a Microsoft Agent Framework sidecar (`agents/fieldops-maf`).
- Set `AGENT_FRAMEWORK_URL` at build time (example: `http://10.0.2.2:8088`). If it is blank or the sidecar is down, the app falls back to Grok, then offline field knowledge.
- See [docs/GROK_AI_V5_AGENT_FRAMEWORK.md](docs/GROK_AI_V5_AGENT_FRAMEWORK.md).

## V2.5 route planner and visual system

- The Routes screen now reads active jobs from Room and uses only jobs with saved latitude/longitude.
- Route ordering runs locally with nearest-neighbor plus 2-opt improvement; no paid routing API is required.
- The planner reports stops, estimated travel/service time, optional return-to-start distance, and can hand the ordered stops to Google Maps.
- The app-wide Material palette is grayscale for a cleaner field-operations UI. Legacy color names remain only for source compatibility.
- See [docs/FIELD_OPPS_V2_5_FEATURE_ROADMAP.md](docs/FIELD_OPPS_V2_5_FEATURE_ROADMAP.md) for the next ten feature proposals.
- See [docs/FIELD_OPPS_V2_5_FEATURE_ROADMAP_20.md](docs/FIELD_OPPS_V2_5_FEATURE_ROADMAP_20.md) for the 20-feature expansion backlog.
