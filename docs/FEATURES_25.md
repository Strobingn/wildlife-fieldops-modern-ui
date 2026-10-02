# Wildlife FieldOps — 25 new field features

Audit of `main` (Kotlin/Compose, Room v13 after batch 2, Supabase AutoSync, HybridAI / TFLite / voice, estimates, invoices, PDFs, unified Job+customer page). These 25 items are **not** already wired end-to-end. Existing building blocks (TrapLog/Reminder/Visit/Inventory tables, species safety catalog, invoice signatures, Job Dictate, weather banner, generic Live Capture checklist) are reused, not re-listed as new products.

Rule for every feature: real Room data, operator-editable fields (AI suggests, Sir’s typing wins), `isSynced = false` + AutoSync on every add/edit/delete, Supabase push for new user data, no stubs or “coming soon”.

## Batch 1 — Job-site AI (this PR, `2.5.0-ai-batch1`)

1. **AI inspection narrative from photos + notes** — Draft findings, recommendations, species, entry points, and damage from the linked job’s photos, notes, and typed fields; apply only empty fields unless Sir chooses replace.
2. **AI estimate line items from photos** — Suggest exclusion/repair lines (entry, materials, linear feet, unit price) from species/damage/notes/photo tags; every line is editable and persists on the job worksheet.
3. **Job-level species safety & legal notes** — Persist confirmed species and NY DEC / rabies-vector / protected-species notes on the job; Sir can override the catalog text.
4. **AI next-step on each job** — Suggest the next field action and due time from status, species, notes, and inspections; persist and edit on the job; surface due items on Home.
5. **Offline AI fallback indicator** — Show Cloud / On-device / Heuristic on Job, Inspection, and Estimate so Sir knows which brain filled the draft.

## Batch 2 — Traps, DEC, follow-ups (`2.5.1-ai-batch2`)

6. **Daily trap-check list** — Schedule trap checks from `trap_logs`, show today’s due/overdue list, mark set/empty/catch, set next check.
7. **Trap pins on the job map** — Place and edit trap GPS/location per job and see them on the property map.
8. **NY DEC nuisance wildlife log export** — Required-record fields (species, date, location, disposition, method) and CSV/share export.
9. **Weather-aware trap-check advice** — Use the job-site forecast to advise bait, check timing, and skip/unsafe conditions; persist the advice Sir accepts.
10. **Follow-up visit planner** — From a completed job, create a dated follow-up visit + reminder (warranty / exclusion / trap pull).

## Batch 3 — Money, time, tax (`2.5.2-ai-batch3`)

11. **On-site job timer** — Start/stop a visit timer on the job; persist elapsed minutes and sync with the visit record.
12. **Profit per job** — Materials + labor time vs quoted/paid price, shown on the job and editable cost inputs.
13. **Mileage tax log** — IRS-style log from routes/estimates (date, miles, purpose, job); year export.
14. **Daily / weekly earnings dashboard** — Paid vs invoiced vs estimated, today and this week, from real invoices/jobs.
15. **Overdue invoice reminders** — Payment status list, mark sent/paid, and open SMS/email reminder drafts.

## Batch 4 — Inventory, warranty, customers

16. **Job materials deducted from inventory** — Pick parts used on a job; decrement on-hand; low-stock alert.
17. **Warranty tracking** — Start date, term, covered work on the job; expiry list and reminders.
18. **Seasonal / recurring customer reminders** — Spring bats/squirrels, fall rodents, etc., tied to the customer on the job.
19. **AI customer message drafts** — Estimate / reminder / warranty / on-the-way text and email; opens the phone SMS/email app with Sir’s edited copy.
20. **Duplicate customer detect + merge** — Warn on same phone/address/name and merge into one customer used by jobs.

## Batch 5 — Search, photos, checklists, share

21. **Smart search** — One search across jobs, customers, notes, species, photo tags, and inspection findings.
22. **Photo auto-tags (searchable)** — Persist species / damage / entry-point tags from vision+LLM; filter the gallery.
23. **Before / after photo pairing** — Pair job photos for reports and PDFs.
24. **Species job checklist templates** — Raccoon / bat / squirrel / skunk / rodent checklists on the job; persist completion.
25. **Shareable inspection report + QR** — Share the branded PDF and a QR that opens/saves the same report from the phone.

## Already on `main` (do not rebuild as “new”)

Voice-to-job, invoice signature pad, generic Live Capture checklist, inventory list + low-stock UI (no job deduction), species safety catalog during recognition only, weather banner, county reports, NY sales tax, route optimizer, estimate/invoice PDFs, unified Job+customer page, AI job summary, dictation/walkthrough inspection report (not photo-tag narrative persist).

## Sync notes

New user data rides existing AutoSync watchers (`jobs`, `inspections`, …) or adds tables + DTO push. Batch 1 and batch 2 store extras in `jobs.pricing` jsonb (and batch 1 also `inspections.findings` jsonb) so live PostgREST does not 400. Dedicated SQL columns stay unapplied unless a later batch genuinely needs them.
