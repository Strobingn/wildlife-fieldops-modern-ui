# Native Android APK — GitHub Actions

This repo builds a **native** debug APK only (Gradle `:app:assembleDebug`).

## Run

1. Open [Actions](https://github.com/Strobingn/wildlife-fieldops/actions)
2. **Build Native Android APK (Debug)**
3. Wait ~5 minutes
4. Download artifact **wildlife-field-ops-debug-apk**
5. Install the `.apk` on your phone

## Secrets (required for a real cloud-connected app)

| GitHub Secret | Maps to Android |
|---------------|-----------------|
| `VITE_SUPABASE_URL` | `SUPABASE_URL` / `BuildConfig.SUPABASE_URL` |
| `VITE_SUPABASE_ANON_KEY` | `SUPABASE_ANON_KEY` |
| `GOOGLE_MAPS_API` | `GOOGLE_MAPS_API_KEY` + Maps meta-data |
| `VITE_OPENWEATHER_API_KEY` | `OPENWEATHER_API_KEY` |

No Node / Vite / Capacitor steps. Pure Android.

The workflow also accepts the legacy `GOOGLE_MAPS_API_KEY`, `VITE_GOOGLE_MAPS_API_KEY`, and `VITE_GOOGLE_MAPS_API` names and normalizes them to `GOOGLE_MAPS_API` before Gradle runs.

## Install on phone

Allow install from unknown sources if asked. Open the APK after unzipping the artifact.
