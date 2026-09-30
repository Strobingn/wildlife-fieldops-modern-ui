# Live wildlife_app baseline vs this repo

Dumped **2026-09-30 16:40 EDT** from Supabase project `wildlife_app`
(`hgdzmwfcghtilyqagjak`). Catalog only; no row data. The live database was
**not** changed by the dump.

This file is the drift record. **Do not treat `supabase/schema.sql` or older
migrations as live.** Apply
`20260929220000_authenticated_rls_for_signed_in_sync.sql` (phase 1) and, after
every device is signed in,
`20260929221000_REVOKE_ANON_AFTER_SIGNED_IN_ROLLOUT.sql` (phase 2) in the SQL
Editor. SIUD = SELECT, INSERT, UPDATE, DELETE. None of the Data API client
grants include TRUNCATE, TRIGGER, or REFERENCES.

## Native Android usage (this branch)

Proven from Kotlin on `cursor/supabase-auth-signin-2b39`. There is **no**
`client.rpc()` / PostgREST function call. GoTrue is email+password only
(`AuthSessionRepository`).

| Surface | Live object | Operations in code |
| --- | --- | --- |
| PostgREST | `customers` | `select()`, `upsert` (INSERT+UPDATE), `delete` (tombstones + tryRemoteDelete) |
| PostgREST | `jobs` | `select()`, `upsert`, `delete` |
| PostgREST | `inspections` | `upsert`, `delete` — **no SELECT pull** today |
| PostgREST | `field_observations` | `upsert` of queued rows (no pull) |
| PostgREST | `observation_events` | `insert` only; HTTP 409 / PG 23505 treated as already synced |
| Storage | bucket `observation-photos` | `upload(..., upsert = true)` → INSERT+UPDATE+SELECT on `storage.objects`; `publicUrl` (bucket is public) |
| Auth | GoTrue `/token` | sign-in, refresh, sign-out. Not a table grant. |
| Views | — | none |
| RPCs | — | none (`generate_due_recurring_jobs`, `queue_campaign`, `refresh_technician_metrics` are unused by the app) |

Room-only (never sent to PostgREST on this branch): `invoices`, `photos`,
`expenses`, `estimates`, `inventory`, `signatures`, `visits`, `repairs`.

### Assumptions (concurrent agents)

A separate agent is fixing sync + auto-sync and photo upload on `main`. Nothing
has reached live since ~12 Sep; buckets currently hold 0 objects.

- Photo upload that **already exists** on this branch uses **only**
  `observation-photos`. Job-site stills in Room `photos` are **not** uploaded.
- A working job-photo cloud path will almost certainly use live tables
  `photos` and/or `job_photos` plus bucket `job-photos`, **as `authenticated`**
  (PR #59 gates every PostgREST/Storage call on a session). Phase 1 therefore
  keeps **authenticated SIUD** on `photos` / `job_photos` and authenticated
  storage policies on `job-photos`, and **does not** keep anon on them.
- `fieldops-photos` / `fieldops-documents` / `fieldops-signatures` are unused
  by the native app; anon is revoked in phase 1; authenticated keeps access
  for a leftover web client.
- Native DTOs do **not** send `organization_id`. Sync tables stay
  `TO authenticated using (true)`. Org-helper policies remain for other
  tables once EXECUTE is restored. `organization_members` INSERT/DELETE is
  revoked from `authenticated`; add owner/tech rows in the SQL Editor.

## What is on live that is not in `schema.sql`

Live has **52 tables + 6 views**. `schema.sql` is an older FieldOps subset
plus leftover web views.

**On live, not in `schema.sql`:** `organizations`, `organization_members`,
`profiles`, `invoices`, `payments`, `payment_records`, `customer_portal_sessions`,
`customer_portal_tokens`, `customer_campaigns`, `marketing_campaigns`,
`campaign_deliveries`, `outbound_messages`, `integration_connections`,
`inventory_items`, `inventory_transactions`, `equipment`, `compliance_rules`,
`field_measurements`, `measurements`, `photo_annotations`, `route_plans`,
`route_stops`, `recurring_services`, `recurring_service_plans`,
`technician_metrics`, `technician_daily_metrics`, `trap_checks`,
`warranty_claims`, `callbacks`, `colors`, `ai_plans`, `job_photos`,
views `business_snapshot_v2`, `inventory_alerts`, `warranty_alerts`.

**In repo grants (`20260928120000` / `schema.sql`), not on live:**
`materials`, `job_materials`, views `weekly_revenue`, `customer_summary`,
`pending_repairs`, `material_inventory`. Phase 1 skips missing relations.

**Shape drift:** live `audit_log` is `(organization_id, actor_user_id,
entity_type, entity_id, …)` written by `audit_row_change()` SECURITY DEFINER.
Repo `schema.sql` uses `log_change()` (invoker) and columns
`table_name, record_id, changed_by`. Phase 1 does **not** grant anon INSERT
on `audit_log`; the live trigger writes as the table owner.

**Policies:** live has 142 policies including `testing_full_access` and
`anon_*` `using(true)` on 38 tables. Repo migrations before this PR did not
drop those live names.

**Helpers:** `is_org_member(uuid)` and `has_org_role(uuid, text[])` are
SECURITY DEFINER with a fixed `search_path`. Live revokes EXECUTE from anon
and authenticated, so those policies error. Phase 1 grants EXECUTE to both
client roles (anon still has no table GRANT on those relations).

**Storage buckets (0 objects):** `observation-photos` (public), `job-photos`
(public → private in phase 1), `job-pdfs` (public → private in phase 1),
`fieldops-photos`, `fieldops-documents`, `fieldops-signatures` (already private).

**Functions:** PUBLIC EXECUTE on `generate_due_recurring_jobs`,
`queue_campaign`, `refresh_technician_metrics`. Mutable search_path on
`set_updated_at` and `touch_integration_connection` (advisor WARN). Auth
leaked-password protection is a dashboard toggle, not SQL.

## Privilege matrix (live → phase 1 → phase 2)

Phase 1 is safe for currently installed APKs: they only talk as `anon` to the
five hold tables + `observation-photos`. Phase 2 removes that remainder.

| Relation | Kind | Live anon | Live authenticated | Live service_role | Phase 1 anon | Phase 1 authenticated | Phase 1 service_role | Phase 2 anon |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `ai_plans` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `ai_runs` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `appointments` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `audit_log` | table | ALL | ALL | ALL | — | INSERT,SELECT | ALL | — |
| `callbacks` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `campaign_deliveries` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `colors` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `compliance_rules` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `customer_campaigns` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `customer_portal_sessions` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `customer_portal_tokens` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `customers` | table | ALL | ALL | ALL | SIUD | SIUD | ALL | — |
| `equipment` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `estimates` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `expenses` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `field_measurements` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `field_observations` | table | ALL | ALL | ALL | SIUD | SIUD | ALL | — |
| `inspections` | table | ALL | ALL | ALL | SIUD | SIUD | ALL | — |
| `integration_connections` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `inventory_items` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `inventory_transactions` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `invoices` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `job_photos` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `jobs` | table | ALL | ALL | ALL | SIUD | SIUD | ALL | — |
| `marketing_campaigns` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `measurements` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `observation_events` | table | ALL | ALL | ALL | SI | SI | ALL | — |
| `organization_members` | table | ALL | ALL | ALL | — | SELECT,UPDATE | ALL | — |
| `organizations` | table | ALL | ALL | ALL | — | SELECT,UPDATE | ALL | — |
| `outbound_messages` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `payment_records` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `payments` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `pdf_documents` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `photo_annotations` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `photos` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `profiles` | table | ALL | ALL | ALL | — | SELECT,INSERT,UPDATE | ALL | — |
| `properties` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `recurring_service_plans` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `recurring_services` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `repairs` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `route_plans` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `route_stops` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `services` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `signatures` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `sync_events` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `technician_daily_metrics` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `technician_metrics` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `techs` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `trap_checks` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `visits` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `warranties` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `warranty_claims` | table | ALL | ALL | ALL | — | SIUD | ALL | — |
| `business_snapshot_v2` | view | ALL | ALL | ALL | — | SELECT | ALL | — |
| `inventory_alerts` | view | ALL | ALL | ALL | — | SELECT | ALL | — |
| `job_stats` | view | ALL | ALL | ALL | — | SELECT | ALL | — |
| `species_stats` | view | ALL | ALL | ALL | — | SELECT | ALL | — |
| `tech_stats` | view | ALL | ALL | ALL | — | SELECT | ALL | — |
| `warranty_alerts` | view | ALL | ALL | ALL | — | SELECT | ALL | — |

`TRUNCATE` / `TRIGGER` / `REFERENCES` are revoked from `anon` and
`authenticated` on every public table in phase 1. `service_role` keeps ALL.

Default privileges: phase 1 `ALTER DEFAULT PRIVILEGES` for `postgres` and
`supabase_admin` (if the role exists) so **new** public tables/sequences/functions
are not auto-granted to `anon`. Authenticated default becomes SIUD, not ALL.

## Run order on live (SQL Editor)

1. (Optional, already on `main`) `20260928120000_explicit_data_api_grants.sql` — stale vs live; skip if phase 1 will run in the same session.
2. **Now:** `20260929220000_authenticated_rls_for_signed_in_sync.sql`
3. Dashboard: disable public signup; enable leaked-password protection; create Auth users; optionally insert `organization_members` owner row as postgres.
4. **After every device is signed in:** `20260929221000_REVOKE_ANON_AFTER_SIGNED_IN_ROLLOUT.sql`

Do not re-run `20260928120000` after phase 2.

## Local restore (not live)

From a directory that contains the un-hashed dump files (see
`tools/verify-live-permissions.sh`):

```bash
psql -v ON_ERROR_STOP=1 -f 00_restore_live_schema.sql
psql -v ON_ERROR_STOP=1 -f supabase/migrations/20260929220000_authenticated_rls_for_signed_in_sync.sql
# re-apply to prove idempotence
psql -v ON_ERROR_STOP=1 -f supabase/migrations/20260929220000_authenticated_rls_for_signed_in_sync.sql
psql -v ON_ERROR_STOP=1 -f supabase/migrations/20260929221000_REVOKE_ANON_AFTER_SIGNED_IN_ROLLOUT.sql
```
