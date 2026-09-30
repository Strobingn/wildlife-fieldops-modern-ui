# Supabase migrations

Apply these in timestamp order (or paste `schema.sql` first for a full bootstrap,
then any later migrations).

**Live `wildlife_app` does not match this folder.** Catalog dump 2026-09-30:
52 tables + 6 views, 142 policies. Drift, per-table privilege matrix, and
Kotlin usage: [`LIVE_BASELINE.md`](LIVE_BASELINE.md).

## Two-step auth rollout (read this before applying new SQL on live)

The native app now signs in with email + password (GoTrue). PostgREST and Storage
then send the user JWT, so traffic runs as Postgres `authenticated`.

**Do not apply the revoke file on live until every field device is on the
signed-in APK.** Old APKs still use the anon key with no session.

Live `wildlife_app` (dumped 2026-09-30) currently grants ALL on all 58 public
relations to `anon`, ships `testing_full_access` / `anon_*` `using(true)`
policies, auto-grants ALL to anon on new tables, and revokes `EXECUTE` on
`is_org_member` / `has_org_role` (so the real org policies error). The two
files below close that, in two steps so currently installed APKs keep syncing.

| Step | When | File / dashboard action |
| --- | --- | --- |
| 1 | With this app release (`2.3.9-supabase-auth`, versionCode 50) | `20260929220000_authenticated_rls_for_signed_in_sync.sql` — authenticated RLS for native sync **and** immediate lock-down of everything PR #60 does not hit (invoices, payments, orgs, profiles, fieldops storage, dangerous RPCs, default privileges). **Keeps anon SIUD on customers/jobs/inspections/field_observations/observation_events/photos/job_photos** plus `observation-photos` and live `job-photos` JPG `public/` policies so the #60 phone build keeps syncing. Adds `jobs.pricing` if missing (PR #61). |
| 2 | Ship the signed-in APK to every phone | Settings → Account → Sign in. Capture still works offline without a session; sync shows **Sign in to sync**. |
| 3 | Owner | Create users (no public signup). Insert the owner row in `organization_members` via the SQL Editor if that table is empty (see below). |
| 4 | After **all** devices are signed in and Sync Now works | `20260929221000_REVOKE_ANON_AFTER_SIGNED_IN_ROLLOUT.sql` — `REVOKE` anon on customers, jobs, inspections, field_observations, observation_events, photos, job_photos, audit_log and `storage.objects`; drop leftover PUBLIC `using(true)` policies; tighten `observation-photos` and `job-photos` JPG `public/` writes to `authenticated`. |

Greenfield `supabase db reset` applies both files in timestamp order, which is
correct for a new project that will only run the signed-in app. **Live
`wildlife_app` must apply step 1 now and hold step 4** until rollout is done.
Paste each file in the SQL Editor; do not let an agent connect to production.

### What step 1 already closes (does not need the HOLD file)

Anyone holding the public anon key **immediately** loses Data API access to
invoices, payment records, customer portal sessions, organizations,
`organization_members`, campaigns, and the other non-sync tables. Anon can no
longer call `queue_campaign` / `generate_due_recurring_jobs` /
`refresh_technician_metrics`. Signed-in users can no longer
`INSERT` themselves as `owner` on `organization_members` or set
`profiles.role` to `owner`. `is_org_member` / `has_org_role` become executable
so the existing org policies filter instead of raising "permission denied for
function".

`observation-photos` and `job-photos` stay **public** buckets because the
Android uploaders store `publicUrl`. Anon writes to `job-photos` stay limited
to `.jpg` under `public/` (live policy). Writes are JWT-gated after step 4;
object URLs remain readable by anyone who has the path. Follow-up: signed URLs
and `public = false`.

### Owner: create accounts and disable public signup

In the Supabase dashboard for project `wildlife_app` (`hgdzmwfcghtilyqagjak`):

1. **Authentication → Users → Add user** (or Invite). Email + password for each
   field tech and the owner. Share the password out-of-band. There is no
   self-signup screen in the app.
2. **Authentication → Providers → Email**
   - Disable **Allow new users to sign up** (public signup).
   - Confirm email can stay on or off for a handful of owner-created accounts;
     if confirm-email is on, confirm each user before they sign in on a phone.
3. **Authentication → Password protection** — enable leaked-password protection
   (HaveIBeenPwned). The security advisor flags this as WARN; it is a dashboard
   toggle, not SQL.
4. If org-scoped tables (payments, inventory, campaigns, trap checks, …) should
   work for signed-in staff, insert membership **as postgres / service_role**
   (the Data API can no longer self-serve owner rows):

   ```sql
   insert into public.organization_members (organization_id, user_id, email, role, active)
   values (
     '<org-uuid>',
     '<auth-user-uuid>',
     'owner@example.com',
     'owner',
     true
   );
   ```

   Native FieldOps sync of jobs/customers does **not** require this row; those
   tables stay `TO authenticated using (true)` because the Android DTOs do not
   send `organization_id`.
5. Optional: **Authentication → URL configuration** — not required for email +
   password inside the app (no OAuth redirect).
6. After rollout, run the revoke SQL (step 4). Confirm a signed-in device can
   still Sync Now, then confirm an anon-key REST call to `customers` is denied.

### Apply on live (SQL Editor)

Apply only if not already applied via the migration runner:

```text
supabase/migrations/20260928120000_explicit_data_api_grants.sql
supabase/migrations/20260929220000_authenticated_rls_for_signed_in_sync.sql
```

`20260928120000_explicit_data_api_grants.sql` does **not** grant `anon` (it is
stale vs live). Prefer phase 1 as the live source of truth. Re-running it after
the HOLD file will not restore anon privileges. Phase 1 already adds
`jobs.pricing` (PR #61). Do **not** re-run #61's `GRANT … TO anon` on `jobs`
after phase 2 — that would reopen anon DML.

**Hold until every device is upgraded:**

```text
supabase/migrations/20260929221000_REVOKE_ANON_AFTER_SIGNED_IN_ROLLOUT.sql
```

## Data API grants (required after 2026-10-30)

New tables in `public` no longer get automatic PostgREST grants to `anon`,
`authenticated`, or `service_role`. **Put grants in the same migration that
creates the table.** RLS is not a substitute for `GRANT`. Step 1 also revokes
the live default-privilege auto-grant of ALL to `anon`.

Template:

```sql
create table if not exists public.your_table (
  id uuid primary key default gen_random_uuid()
  -- ...
);

alter table public.your_table enable row level security;

-- Signed-in app + Edge Functions / service role:
grant select, insert, update, delete on public.your_table
  to authenticated, service_role;

-- Do not grant anon on customer / observation / other sensitive tables.
-- Remaining anon table grants are revoked after the signed-in APK is on all
-- devices (see 20260929221000_REVOKE_ANON_AFTER_SIGNED_IN_ROLLOUT.sql).

-- Identity / serial PK used by authenticated inserts:
-- grant usage, select on sequence public.your_table_id_seq to authenticated, service_role;
```

Catch-up for tables that already exist: `20260928120000_explicit_data_api_grants.sql`
is **stale vs live** (it used to mention `materials` / `job_materials`, which
are not on live). Prefer phase 1. Re-running `20260928120000` after phase 2 does
not restore `anon` (it only top-ups authenticated / service_role).
