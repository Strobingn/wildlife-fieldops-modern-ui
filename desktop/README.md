# Wildlife Field Ops — Windows (Compose Desktop)

Windows client for Wildlife Field Ops. Syncs with the Android app through the
same Supabase project: the phone pushes jobs to `public.jobs`, this client
pulls them and pushes notes edits back to the same rows.

## Sync design (Android to Windows)

1. Android already syncs every local write to Supabase via WorkManager
   (`fieldops-sync` unique work, network-constrained, 15-min safety net).
2. This client pulls `public.jobs` on launch and on demand (Pull now).
3. Desktop writes are single-column updates (`notes`) filtered by `id`, so a
   stale desktop pull can never clobber phone data. Conflict policy: last
   writer wins per column, matching the Android merge behavior.
4. Both sides use the publishable anon key; row-level security on Supabase is
   the real authorization gate. No service-role key in any client.

## Build and run

```
export FIELDOPS_SUPABASE_URL="https://<project>.supabase.co"
export FIELDOPS_SUPABASE_ANON_KEY="<publishable key>"
cd desktop
../gradlew run          # if run from repo root: gradle -p desktop run
```

Standalone Gradle project; the root Android build does not include it, so the
CI APK pipeline is unaffected. Package the MSI installer with
`gradle packageDistributionMsi` (TargetFormat.Msi).

## Version notes

- Compose Desktop 1.6.11 / Kotlin 2.0.21: verified pair when written; re-verify
  before release.
- supabase-kt 2.6.1 and ktor 2.3.12 deliberately match the Android app so both
  clients speak the same wire protocol.

## Roadmap

- v0.2: local SQLite cache + offline first, real-time Supabase subscriptions
- v0.3: inspections and customers screens, PDF generation parity
- v0.4: dictation via local Whisper endpoint (shared with the phone's Ollama)
