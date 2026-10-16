# Wiring audit: what is connected, what was not, and what this branch fixes

Branch: `claude/wildlife-ai-features-khsfn3`
Base: `origin/main` at `ac25a59` (fast-forwarded; `main` itself was not changed)

This branch answers one question: which features in the app are fully connected from the button the operator taps through to the database, sync, and background work? Section 1 lists what is fully wired. Section 2 lists the switches and records that were not connected and how this branch connects them. Section 3 lists what was found and deliberately left alone.

---

## 1. Fully wired on `main` (checked, no change needed)

| Area | What was checked | Result |
| --- | --- | --- |
| Navigation | Every `Screen` object has a `composable(...)` entry in `MainActivity.AppNavHost` | 38 of 38 routes registered |
| Navigation | Every screen callback that defaults to a no-op (`= {}`) is passed a real handler by the NavHost | All passed |
| Buttons | Empty click handlers (`onClick = {}`) in real screens | None. Only in `ThemePreviews.kt` (previews) |
| Database | All 16 Room DAOs are exposed by `AppDatabase` and provided by `di/AppModule.kt` | All provided |
| Database | Each DAO is used by at least one view model or store | 15 of 16 (see `RepairDao` in section 3) |
| Background work | Workers and where they are scheduled | 4 workers (`FieldOpsSyncWorker`, `TrapReminderWorker`, `WeatherAlertWorker`, `AppUpdateWorker`), all enqueued from `WildlifeFieldOpsApp.onCreate` |
| Manifest | Activity, update-install receiver, `FileProvider`, custom WorkManager init | All present and backed by real classes |
| Sync | Tables pushed to Supabase | Jobs, customers, inspections, photos, field observations, observation events, deletions (tombstones) |
| Sync | Tables pulled from Supabase | Jobs and customers |
| Backup | Backup zip | Copies the whole Room database file plus photos and settings, so every table is included, even the ones that do not sync |
| Cloud AI | Route for Grok calls | Supabase edge function `ai-assistant`. No xAI key in `BuildConfig`. The APK workflow unsets the key and greps the APK to prove it is absent |
| AI Operations | Launchable tools and modules on `main` | 20 tools, 65 modules (PR #120 raises this to 40 and 85) |

---

## 2. Not wired on `main`, fixed on this branch

### 2.1 Reminders could never be cleared

The Jobs tab lists pending reminders (trap-check and follow-up reminders created by the trap and customer stores). The card had no click and no action, and nothing in the UI called `completeReminder`. A reminder stayed on the Jobs tab forever.

- `ui/screens/DashboardScreen.kt`: `ReminderCard` takes optional `onOpen` and `onDone`. With `onDone` it shows a **Done** button.
- `ui/screens/JobListScreen.kt`: tapping a reminder opens its job (when it has one). **Done** marks it complete.
- `ui/viewmodel/JobsViewModel.kt`: new `completeReminder(id)`. It sets status `COMPLETED` and `completedDate` and keeps the row, so the trap store's "is there already a reminder with this id" check still works.

### 2.2 Offline Mode did not stop background uploads

Offline Mode stopped the on-change auto-sync and **Sync Now**, but the 15-minute periodic sync worker ignored it and kept uploading.

- `sync/work/FieldOpsSyncWorker.kt`: returns early when Offline Mode is on. Nothing is dropped: rows stay queued in Room, and `AutoSync` already enqueues a sync when Offline Mode is turned off.
- `ui/viewmodel/SettingsPrefs.kt`: new `Context.offlineModeOn()` reads the same `offline_mode` key the Settings switch writes.

### 2.3 "Enable Notifications" did nothing

The Settings switch saved `notifications_enabled`, but no code read it. Trap-check reminders and weather alerts have their own switches, and those work. The master switch now applies on top of them.

- `trapreminders/TrapReminderRunner.kt` and `weather/WeatherAlertRepository.kt`: skip posting when the master switch is off, the same way they already skip when their own switch is off.
- `ui/viewmodel/SettingsPrefs.kt`: new `Context.notificationsAllowed()`.

### 2.4 "Auto Sync" switch did nothing

The switch saved `auto_sync`, but no code read it. Auto-sync is always on by design: PR #60 hard-wired `AutoSyncGate { true }` after sync had been failing silently. This branch keeps that guarantee and makes the screen say so.

- `ui/screens/SettingsScreen.kt`: the switch is replaced by a read-only row: "Always on. Every save uploads when online. Use Offline Mode to pause." The Offline Mode subtitle now says what it actually does: it pauses cloud sync.
- If you would rather have a real auto-sync off switch, that is a small follow-up: make `provideAutoSyncGate` read the preference. It was not done here because it would undo PR #60's always-on behavior.

### 2.5 "High Accuracy GPS" did nothing

The switch saved `high_accuracy_gps`, but the GPS tracker always used the GPS provider at 1 second / 1 metre.

- `ui/screens/GPSScreen.kt`: when the switch is off, tracking uses network location (5 seconds / 10 metres) to save battery. If the phone has no network location, it falls back to GPS so tracking still works. When the switch is on, nothing changes.
- The Settings subtitle now says this.

### 2.6 Customers, inspections, and inventory items could not be deleted

Each view model already had a finished delete function. The customer and inspection ones write a deletion record so the delete also syncs to Supabase. No screen called any of them. Only jobs and expenses had a delete button.

| Screen | Change | View model call |
| --- | --- | --- |
| Inspection (open an existing one) | Trash icon in the top bar, confirm dialog | `InspectionsViewModel.deleteInspection` |
| Edit Customer | Trash icon in the top bar, confirm dialog | `CustomersViewModel.deleteCustomer` |
| Inventory | Trash icon on each item, confirm dialog | `InventoryViewModel.deleteItem` |

Customer delete is safe for jobs. On Supabase, `jobs.customer_id` is `on delete set null`, so remote jobs are not deleted. Locally, jobs and inspections keep the customer's name. The confirm dialog says this.

---

## 3. Found and left alone

### 3.1 Code nothing uses

| Item | Notes |
| --- | --- |
| `ai/OnDeviceAIService.kt` (136 lines) | `@Singleton @Inject` class that nothing injects. Its job is done by `AiService` and `LocalLlmEngine`. |
| `data/remote/RemoteDtos.kt` `AiEdgeRequest` | Unused. `AiEdgeGateway` sends `AiEdgeProtocol.requestJson(...)` instead. |
| `Repair` entity and `RepairDao` | The `repairs` table exists in Room (and migrations), but no screen or store reads or writes it. Repair scope from Live Capture is written into job notes instead. Removing the table would need a Room migration. |
| `ai/admission/ExplanationAdmission.kt` (gate, ontology stub, synthetic factory) | Only tests use it. |
| `data/repository/JobRepository.kt`: `getAllJobs`, `getJobsByStatus`, `getUnsyncedJobs`, `searchJobs` | Unused queries. |
| `SettingsViewModel`: `setDarkTheme`, `setSyncInterval` / `syncInterval`, `setDefaultTaxRate`, `importData`, and now `setAutoSync` | Replaced by newer functions or left over from older Settings screens. No UI calls them. |
| UI helpers `CountBadge`, `EmptyStateMessage`, `RoundedCornerShapeCompat`, `OutlinedSurface`, `SpeciesIcon`, `InvoiceTotalRow` | Not called. |

None of these break anything. Deleting them is a cleanup for a separate change.

### 3.2 Backend ready, no button yet

These are smaller than the items in section 2 and were left for you to decide:

- Edit an expense (`ExpensesViewModel.updateExpense`). Today an expense can be added or deleted, not edited.
- Edit an inventory item's details (`InventoryViewModel.updateItem`). Today only stock can be adjusted.
- Delete an invoice (`InvoiceViewModel.deleteInvoice`).
- The Schedule calendar loads reminders (`ScheduleViewModel.allReminders`) but does not show them.
- The Home dashboard loads pending reminders but does not show them. That looks deliberate after the Home redesign, which moved lists to the Jobs tab.

### 3.3 Sync scope

These tables are stored only on the phone and are not pushed to Supabase: visits, expenses, trap logs, inventory, reminders, invoices, voice observations. They are in the backup zip. Whether they should sync is a product decision, not a wiring bug.

### 3.4 Open TODO

`tax/NyCountyTaxRates.kt`: per-install override of county tax rates from Settings. It was the only real `TODO` in the app code.

---

## 4. How this was checked

- A script listed every top-level class and function in `app/src/main/java` and counted references from other files (main code, tests, manifest, and XML resources).
- A second script listed public view-model and store functions with no caller outside their own file. That found the delete functions, `completeReminder`, and the Settings setters above.
- Each finding was then read by hand to separate dead code from features with no button.
- Routes, DAOs, workers, the manifest, and the AI transport were checked directly (section 1).

### Build and test status

- The Android SDK cannot be downloaded in this environment (`dl.google.com` is blocked by the proxy), so the app was not compiled here. CI is the build check: `Build Native Android APK (Debug)` and `Unit Tests`.
- Source-text tests that guard `DashboardScreen.kt` (no "overdue", no "Recent jobs", no money terms) were checked against the new `ReminderCard` code by hand.
- No test constructs `TrapReminderRunner` or `WeatherAlertRepository` directly, so adding the Hilt-injected context to them does not change any test.
