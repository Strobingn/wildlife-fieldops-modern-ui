# Confirm the on-device sync backlog (before and after this APK)

Jobs, Live Capture stills, and ML notes live in Room (`wildlife_fieldops.db`) and
app files. **An app update does not upload them by itself**, and this release
does not delete them. Confirm they are still on the phone, then tap **Sync Now**.

## Before installing 2.3.7-sync-backlog (current APK)

Do this on the field phone. Do **not** tap **Settings → Clear All Data**.

1. Open **Jobs**. Count the cards. The Middletown raccoon job should be in the
   list even if the Supabase dashboard only shows four older jobs.
2. Open that job → **Live Capture** / photo gallery. Confirm the stills and AI
   notes are visible.
3. Open **Settings**. Connection line should include `Supabase OK`. If it says
   `Supabase missing`, the APK was built without secrets (sync cannot run until
   a secret-baked APK is installed — local jobs/photos still stay on the phone).
4. Optional: **Settings → AI and App Diagnostics**. Leave **Offline mode** off.

Write down: job count, whether the raccoon job is present, and about how many
photos it has. After the update those numbers must still match.

## After installing this APK (before Sync Now)

1. Open **Settings → Sync & Data**. The card lists pending jobs, customers,
   inspections, map pins, ML events, and photos **on this phone**.
2. Confirm the pending job/photo counts cover the Middletown raccoon job and its
   stills. If the counts are zero and the job is gone, stop and do not sync
   (data did not survive — that would be unexpected; this migration only adds
   nullable `syncError` columns).
3. Tap **Sync Now**. Watch the message. Green/OK means the cloud accepted the
   backlog. Red lists the failed item and a readable reason; the row stays
   unsynced for retry. Nothing is deleted on failure.

## What the first successful sync does

- Upserts each local job/customer/inspection/observation **by UUID** (retry-safe,
  no duplicates).
- Uploads Live Capture / job gallery files to the `job-photos` bucket and inserts
  `photos` rows (`job_id` + public URL). ML `ObservationEvent` stills go to
  `observation-photos`.
- Sets `isSynced` / `isUploaded` only after the server returns success.

Owner SQL (if the dashboard still rejects writes): paste
`supabase/migrations/20260930120000_sync_photo_backlog_and_live_columns.sql`
in the `wildlife_app` SQL Editor. The agent does not run SQL on live.
