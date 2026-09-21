# Wildlife FieldOps — agent notes

Native Android app (Kotlin + Jetpack Compose). The product path is Gradle `:app`.
Leftover Vite / Capacitor files may exist in the tree; do not treat `npm` or
Capacitor as the default workflow.

## Cursor Cloud specific instructions

Source the durable SDK/JDK exports written by `.cursor/install-android-sdk.sh`.
Environment Builds preserve disk, not shell exports:

```bash
source .cursor/env.sh
```

### Assemble a debug APK

```bash
source .cursor/env.sh
./gradlew :app:assembleDebug --no-daemon
```

Output: `app/build/outputs/apk/debug/`.

### Maps / API keys

Bake keys at Gradle configure time from **Cursor Secrets** (environment
variables) or from a local gitignored `local.properties` / exported env. Never
commit secrets, keystores, or real API keys.

`app/build.gradle.kts` reads (among others):

| Purpose | Environment variables |
| --- | --- |
| Google Maps | `GOOGLE_MAPS_API` (also `GOOGLE_MAPS_API_KEY`, `VITE_GOOGLE_MAPS_API_KEY`, `VITE_GOOGLE_MAPS_API`) |
| Supabase | `SUPABASE_URL`, `SUPABASE_ANON_KEY` |
| Weather | `OPENWEATHER_API_KEY` |
| Cloud LLM | `XAI_API_KEY` or `LLM_API_KEY` |

Compile and debug assemble succeed without secrets (placeholders / empty keys).
Maps, sync, and cloud AI need secrets to function in a built APK.

### Preferred verify command

```bash
source .cursor/env.sh
./gradlew :app:compileDebugKotlin :observation-core:test --no-daemon
```

Use `:app:assembleDebug` when you need a debug APK (same as CI). Re-run
`bash .cursor/install-android-sdk.sh` after JDK/SDK/Gradle pin changes; the
script is idempotent.
