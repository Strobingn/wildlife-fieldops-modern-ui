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
- AI assistant (Supabase Edge Function + on-device fallback)

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
- **Settings → Sync Now** pushes unsynced jobs/customers/inspections and pulls cloud rows.
- Cloud project: `wildlife_app` (`hgdzmwfcghtilyqagjak`).
- Schema applied: `supabase/migrations/20260710153000_native_sync_fix.sql` (customers table + RLS/grants for `anon`).
- Verified: REST insert/select/delete for `customers` + `jobs` works with the app anon key.

## AI

- Prefer Supabase function `ai-assistant` (set LLM keys in Supabase Edge Function secrets).
- If the function is offline, the app uses built-in field knowledge so the chat still works.


## V2.5 route planner and visual system

- The Routes screen now reads active jobs from Room and uses only jobs with saved latitude/longitude.
- Route ordering runs locally with nearest-neighbor plus 2-opt improvement; no paid routing API is required.
- The planner reports stops, estimated travel/service time, optional return-to-start distance, and can hand the ordered stops to Google Maps.
- The app-wide Material palette is grayscale for a cleaner field-operations UI. Legacy color names remain only for source compatibility.
- See [docs/FIELD_OPPS_V2_5_FEATURE_ROADMAP.md](docs/FIELD_OPPS_V2_5_FEATURE_ROADMAP.md) for the next ten feature proposals.
- See [docs/FIELD_OPPS_V2_5_FEATURE_ROADMAP_20.md](docs/FIELD_OPPS_V2_5_FEATURE_ROADMAP_20.md) for the 20-feature expansion backlog.
