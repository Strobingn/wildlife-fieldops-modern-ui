# Supabase migrations

Apply these in timestamp order (or paste `schema.sql` first for a full bootstrap,
then any later migrations).

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
-- policies that match the real access model; do not default to using(true)
-- unless that is intentional.

-- Signed-in app + Edge Functions / service role:
grant select, insert, update, delete on public.your_table
  to authenticated, service_role;

-- Only if the client genuinely reads this table with no user session
-- (this app currently uses the anon key with no sign-in — see README):
-- grant select, insert, update, delete on public.your_table to anon;

-- Identity / serial PK used by authenticated inserts:
-- grant usage, select on sequence public.your_table_id_seq to authenticated, service_role;
```

Do **not** grant `anon` on customer, invoice, or other sensitive tables unless
the shipping client has no auth session and that table is required for sync.
Prefer adding GoTrue sign-in and dropping anon table grants.

Catch-up for tables that already exist: `20260928120000_explicit_data_api_grants.sql`.
Run that file in the SQL Editor on live `wildlife_app` if it has not been applied
via the migration runner. Do not re-run older migrations just to pick up grant
edits; the catch-up file is idempotent.

Photo / Live Capture backlog (native sync): `20260930120000_sync_photo_backlog_and_live_columns.sql`.
Additive columns only (photos link fields, nullable `jobs.organization_id`).
**Does not change GRANT / RLS / storage policies** — a separate permissions agent owns those.
