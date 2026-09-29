# Supabase migrations

Apply these in timestamp order (or paste `schema.sql` first for a full bootstrap,
then any later migrations).

## Two-step auth rollout (read this before applying new SQL on live)

The native app now signs in with email + password (GoTrue). PostgREST and Storage
then send the user JWT, so traffic runs as Postgres `authenticated`.

**Do not apply the revoke file on live until every field device is on the
signed-in APK.** Old APKs still use the anon key with no session.

| Step | When | File / dashboard action |
| --- | --- | --- |
| 1 | With this app release (`2.3.7-supabase-auth`) | `20260929220000_authenticated_rls_for_signed_in_sync.sql` — extra `TO authenticated` RLS + storage policies. **Keeps anon grants** so currently installed APKs keep syncing. |
| 2 | Ship the signed-in APK to every phone | Settings → Account → Sign in. Capture still works offline without a session; sync shows **Sign in to sync**. |
| 3 | Owner | Create users (no public signup). See dashboard steps below. |
| 4 | After **all** devices are signed in and Sync Now works | `20260929221000_REVOKE_ANON_AFTER_SIGNED_IN_ROLLOUT.sql` — `REVOKE` anon on customers, jobs, inspections, field_observations, observation_events, audit_log; tighten `observation-photos` to `authenticated`. |

Greenfield `supabase db reset` applies both files in timestamp order, which is
correct for a new project that will only run the signed-in app. **Live
`wildlife_app` must apply step 1 now and hold step 4** until rollout is done.
Paste each file in the SQL Editor; do not let an agent connect to production.

### Owner: create accounts and disable public signup

In the Supabase dashboard for project `wildlife_app` (`hgdzmwfcghtilyqagjak`):

1. **Authentication → Users → Add user** (or Invite). Email + password for each
   field tech and the owner. Share the password out-of-band. There is no
   self-signup screen in the app.
2. **Authentication → Providers → Email**
   - Disable **Allow new users to sign up** (public signup).
   - Confirm email can stay on or off for a handful of owner-created accounts;
     if confirm-email is on, confirm each user before they sign in on a phone.
3. Optional: **Authentication → URL configuration** — not required for email +
   password inside the app (no OAuth redirect).
4. After rollout, run the revoke SQL (step 4). Confirm a signed-in device can
   still Sync Now, then confirm an anon-key REST call to `customers` is denied.

### Apply on live (SQL Editor)

Apply only if not already applied via the migration runner:

```text
supabase/migrations/20260928120000_explicit_data_api_grants.sql
supabase/migrations/20260929220000_authenticated_rls_for_signed_in_sync.sql
```

**Hold until every device is upgraded:**

```text
supabase/migrations/20260929221000_REVOKE_ANON_AFTER_SIGNED_IN_ROLLOUT.sql
```

## Data API grants (required after 2026-10-30)

New tables in `public` no longer get automatic PostgREST grants to `anon`,
`authenticated`, or `service_role`. **Put grants in the same migration that
creates the table.** RLS is not a substitute for `GRANT`.

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
-- Anon table grants are revoked after the signed-in APK is on all devices
-- (see 20260929221000_REVOKE_ANON_AFTER_SIGNED_IN_ROLLOUT.sql).

-- Identity / serial PK used by authenticated inserts:
-- grant usage, select on sequence public.your_table_id_seq to authenticated, service_role;
```

Catch-up for tables that already exist: `20260928120000_explicit_data_api_grants.sql`.
Run that file in the SQL Editor on live `wildlife_app` if it has not been applied
via the migration runner. Do not re-run older migrations just to pick up grant
edits; the catch-up file is idempotent.
