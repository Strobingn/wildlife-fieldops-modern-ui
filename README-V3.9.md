# V3.9 fixes branch: what changed and why

Branch: `Claude-V-3.9-fixes`
Base: `origin/main` at `ac25a59` (PR #117, "ai-assistant: default to fast grok-4.20-0309-non-reasoning model")

This branch replaces the work on `Claude-V-3.9` (draft PR #3). That branch was built on an old commit (`ed06fdd`), 126 `main` commits behind, and its build fails to compile. This branch starts from current `main` and carries over only the parts `main` does not already have, plus the fixes listed below.

---

## 1. Summary

| Area | Change |
| --- | --- |
| AI operations | 20 new offline-safe intelligence modules (insights 66-85). Total is now 85. |
| AI tools | 20 new launchable tools bound one-to-one to those modules. Total is now 40. |
| UI copy | Tool count and module count shown on the AI screens are updated to 40 and 85. |
| CI | New `Unit Tests` workflow runs the JVM tests that no workflow ran before. |
| CI | `actions/setup-java` moved from v4 to v5 in the APK build (v4 is deprecated). |
| Tests | Fixed a fixture bug in the new engine test (a job without a title counted as blank). |

Nothing in this branch changes Room schema, navigation, or the inspection flow.

---

## 2. AI operations: new modules and tools

All twenty modules are computed from real `Job` fields and are offline-safe. Each one has its own data slice and does not share logic with the others. The module names below are exactly as shown in the app.

| # | Launchable tool (title) | Intelligence module it reads |
| --- | --- | --- |
| 1 | New Lead Aging Monitor | New lead aging alert |
| 2 | Urgent Scheduling Guard | Urgent-without-schedule alert |
| 3 | Priority Mix Auditor | Priority mix auditor |
| 4 | Returning Customer Tracker | Returning-customer ratio |
| 5 | Duplicate Job Detector | Concurrent-job customer alert |
| 6 | Technician Bus-Factor Monitor | Technician concentration risk |
| 7 | Job Title Cleanup Assistant | Blank-title data hygiene |
| 8 | GPS Integrity Checker | Coordinate integrity checker |
| 9 | Pre-Work Photo Reminder | Pre-work photo gap |
| 10 | Undocumented Value Tracker | Undocumented value at risk |
| 11 | Multi-Species Complexity Planner | Multi-species complexity flag |
| 12 | Bat Maternity Season Guard | Bat-season compliance timing |
| 13 | Urgent Response SLA Tracker | Service-level response compliance |
| 14 | Long-Cycle Job Reviewer | Long-cycle job detector |
| 15 | Estimate Itemization Checker | Round-number estimate detector |
| 16 | Dispatch Readiness Index | Field readiness composite index |
| 17 | Technician Overload Detector | Technician day-overlap detector |
| 18 | Repeat-Property Cost Escalation Tracker | Repeat-property cost trend |
| 19 | Customer Name Formatting Auditor | Customer name formatting auditor |
| 20 | Commercial Site Planner | Commercial-site complexity flag |

Files:
- `app/src/main/java/com/strobingn/wildlifefieldops/ai/operations/AIOperationsEngine.kt`: the twenty new signals are added after the existing `Forecast maturity score`. The `advancedInsights` filters use `main`'s `JobStatus.isWorkOpen()` / `isWorkDone()` helpers, not the older hand-written status lists.
- `app/src/main/java/com/strobingn/wildlifefieldops/ai/operations/IndividualAIToolCatalog.kt`: twenty tool entries, each with a purpose line and a three-step field checklist.
- `app/src/main/java/com/strobingn/wildlifefieldops/ui/screens/AIOperationsScreen.kt`: the tool heading now reads its count from `IndividualAIToolCatalog.tools.size` instead of a hardcoded string.
- `app/src/main/java/com/strobingn/wildlifefieldops/ui/screens/SettingsScreen.kt`: the AI Command Center blurb now reads "40 individually launchable AI tools plus 85 live intelligence modules".

---

## 3. CI changes

### 3.1 Unit tests now run (new file)

`.github/workflows/unit-tests.yml` runs:

```
./gradlew :app:testDebugUnitTest :observation-core:test --no-daemon --stacktrace
```

Before this change, no workflow in the repo ran any unit test. `build-android.yml` only ran `:app:assembleDebug`, so the existing suites (for example `AIOperationsEngineTest` and the six `observation-core` test files) never executed automatically. The workflow needs no secrets: unit tests run against placeholder build config. On failure it uploads the test reports and log as the `unit-test-reports` artifact.

### 3.2 `setup-java` v4 to v5 (`build-android.yml`)

The build log annotated `actions/setup-java@v4` as deprecated. The inputs used (`distribution`, `java-version`, `cache`) are unchanged in v5.

---

## 4. Test fix

`AIOperationsEngineTest.newSchedulingAndHygieneModulesUseRecordedJobSignals` built a second job with no title. `Job.title` defaults to `""`, so the engine correctly counted two blank-titled jobs, and the assertion expecting `"1 job…"` failed. The fixture now has a real title (`"Raccoon attic cleanup"`). The engine was not changed.

---

## 5. Checked on `main` and deliberately not changed

These were on `Claude-V-3.9` or were proposed earlier. Each already exists on `main`, so carrying them over would only duplicate working code.

| Item | Where it lives on `main` |
| --- | --- |
| Inspection voice dictation (`RecognizerIntent` / `SpeechRecognizer`) | `ui/screens/InspectionFormScreen.kt` |
| Inspection photo capture (`TakePicture`, saved per inspection) | `ui/screens/InspectionFormScreen.kt` |
| AI draft of inspection fields from photos and notes | `InspectionFormScreen.kt` ("Draft from photos"), `data/remote/AiService.kt` (`InspectionReportDraft`), `ai/WalkthroughVideoAnalyzer.kt` |
| `Inspection.aiNarrativeDraft` / `aiDraftSource` fields | `data/model/Inspection.kt` |
| AI Operations page, drawer entry, and dashboard entry | `MainActivity.kt`, `navigation/FieldNav.kt` |

The inspection work from `Claude-V-3.9` was therefore dropped rather than ported. Its compile error (below) does not exist on this branch.

### What was dropped from `Claude-V-3.9`

- **Compile error.** `HybridAIService.kt` referenced `OnDeviceAIService.InspectionReportDraft` at lines 106 and 117, but the class was declared at top level in the `ai` package. CI failed on commits `26b698f` and `53dc080`. The fix commit did not touch that line.
- **Room version bump to 3.** `Claude-V-3.9` set `AppDatabase.version = 3`. `main` is at version 15, and its `AppModule.kt` forbids destructive migration. The bump was wrong and is not carried over.
- **Destructive-migration claim.** The earlier PR text said destructive migration matched the existing convention. It does not.

---

## 6. Facts about `main` that were verified

- **Room migrations.** Version 15 with a complete chain `MIGRATION_3_4` through `MIGRATION_14_15` in `data/local/Migrations.kt`. No `fallbackToDestructiveMigration`, by policy (`di/AppModule.kt`).
- **Secrets.** A pattern scan of tracked files (xAI, OpenAI-style, Google API, and JWT-shaped keys) found none.
- **Cloud AI key.** Not in the APK. `app/build.gradle.kts` does not put the xAI key in `BuildConfig`; cloud Grok calls go through the Supabase `ai-assistant` edge function (`AiEdgeGateway`). `build-android.yml` unsets `XAI_API_KEY` / `LLM_API_KEY` before building and greps the APK to prove neither is present.
- **Write-back workflows.** `apply-polish.yml` runs only on pushes that touch specific `tools/polish-chunks` paths. `restore-livecapture-screen.yml` runs only on `feat/capture-clock-soak`. Neither loops on `main`.
- **Latest APK build.** Run `38624977136` on `ac25a59` passed.

---

## 7. Verification

### 7.1 What was run

- **Engine and catalog, on the JVM.** `AIOperationsEngine.kt`, `IndividualAIToolCatalog.kt`, `AIOperationsEngineTest.kt`, and `Job.kt` / `ServiceTypes.kt` from `main` were compiled with `kotlinc` 1.9.24 and run with JUnit 4.13.2. Result: `OK (4 tests)`.
- **Harness stubs.** Only the three things that need Android or other `main` code were stubbed: the Room `@Entity` / `@PrimaryKey` annotations, `JobPricing`, and `JobStatusPipeline`.

### 7.2 What was not run here

- **The Android build and the whole app.** The Android SDK comes from `dl.google.com`, which this environment's proxy blocks (HTTP 403). The Kotlin 2.1.20 Gradle plugin's BOM is also missing from the available mirror. So the APK build and the full unit-test task could not run locally.
- **The tests on Kotlin 2.1.20.** The JVM run used Kotlin 1.9.24, not the project's 2.1.20.

**CI is the authoritative check.** Confirm both workflows are green on the PR before merging: `Build Native Android APK (Debug)` and `Unit Tests`.

---

## 8. Known issues (found, not fixed here)

An earlier version of this list said the xAI key ships inside the APK. That was wrong; see "Cloud AI key" in section 6.

1. **Silence detection is a stub.** `observation-core` `TranscriptValidator.isSilentAudio` always returns `false`. That is fail-closed by design (silent-looking audio is never accepted as empty), but the TODO for a real energy classifier is still open.
2. **No migration path from Room versions 1 and 2.** `Migrations.ALL` starts at 3→4. A device still holding a version 1 or 2 database would fail to open it. Probably no such installs remain, but this is unverified.
3. **Node 20 deprecation warnings.** `checkout`, `upload-artifact`, and `setup-android` are forced onto Node 24. This is a warning, not a failure.

---

## 9. Superseded

- **Draft PR #3 (`Claude-V-3.9`)** is superseded by this branch. It is still open and still shows a failing build. It has not been closed.
- **Designated development branch `claude/wildlife-ai-features-khsfn3`** is 52 commits behind `main` and has not been changed.
