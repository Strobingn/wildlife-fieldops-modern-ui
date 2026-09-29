-- =============================================================================
-- HOLD — DO NOT APPLY ON LIVE wildlife_app UNTIL THE SIGNED-IN BUILD IS ON
-- EVERY FIELD DEVICE.
--
-- File: 20260929221000_REVOKE_ANON_AFTER_SIGNED_IN_ROLLOUT.sql
--
-- Pre-conditions (owner checklist):
--   1. Every phone runs Wildlife FieldOps 2.3.7-supabase-auth (versionCode 48)
--      or later and techs can Sign in (Settings → Account).
--   2. Auth users exist for each tech/owner (Dashboard → Authentication →
--      Users → Add user / Invite). Passwords shared out-of-band.
--   3. Public self-signup is disabled (Authentication → Providers → Email →
--      uncheck "Allow new users to sign up").
--   4. Confirm a signed-in device can Sync Now (jobs/customers/photos).
--
-- Applying this while any remaining APK still uses the anon key with no
-- session will break that device's sync (PostgREST 401/42501). Local Room
-- data on those devices is not deleted, but cloud push/pull will fail until
-- they upgrade and sign in.
--
-- This file:
--   * REVOKEs table privileges from `anon` on customers, jobs, inspections,
--     field_observations, observation_events, audit_log
--   * Drops anon-specific RLS policies on those tables
--   * Tightens observation-photos storage.objects policies to `authenticated`
--     (drops the open policies from 20260922132741)
--
-- Safe to re-run. Does not touch service_role or authenticated grants.
-- =============================================================================

revoke all on table public.customers from anon;
revoke all on table public.jobs from anon;
revoke all on table public.inspections from anon;

do $$
begin
  if to_regclass('public.field_observations') is not null then
    execute 'revoke all on table public.field_observations from anon';
  end if;
  if to_regclass('public.observation_events') is not null then
    execute 'revoke all on table public.observation_events from anon';
  end if;
  if to_regclass('public.audit_log') is not null then
    execute 'revoke all on table public.audit_log from anon';
  end if;
end $$;

-- Anon-specific policies (native_sync_fix / inspections fix pack). Unrestricted
-- using(true) policies without TO remain; REVOKE is what blocks the Data API.
drop policy if exists "anon_select_customers" on public.customers;
drop policy if exists "anon_insert_customers" on public.customers;
drop policy if exists "anon_update_customers" on public.customers;
drop policy if exists "anon_delete_customers" on public.customers;

drop policy if exists "Allow anon access to inspections" on public.inspections;

-- Storage: only authenticated may read/write observation-photos objects.
do $$
begin
  if to_regclass('storage.objects') is null then
    raise notice 'revoke_anon: skip missing storage.objects';
    return;
  end if;

  execute 'drop policy if exists "observation_photos_select" on storage.objects';
  execute 'drop policy if exists "observation_photos_insert" on storage.objects';
  execute 'drop policy if exists "observation_photos_update" on storage.objects';
  execute 'drop policy if exists "observation_photos_delete" on storage.objects';

  -- Recreate authenticated policies in case the previous migration was skipped.
  execute 'drop policy if exists "observation_photos_select_authenticated" on storage.objects';
  execute 'drop policy if exists "observation_photos_insert_authenticated" on storage.objects';
  execute 'drop policy if exists "observation_photos_update_authenticated" on storage.objects';
  execute 'drop policy if exists "observation_photos_delete_authenticated" on storage.objects';

  execute $p$
    create policy "observation_photos_select_authenticated"
      on storage.objects for select to authenticated
      using (bucket_id = 'observation-photos')
  $p$;
  execute $p$
    create policy "observation_photos_insert_authenticated"
      on storage.objects for insert to authenticated
      with check (bucket_id = 'observation-photos')
  $p$;
  execute $p$
    create policy "observation_photos_update_authenticated"
      on storage.objects for update to authenticated
      using (bucket_id = 'observation-photos')
      with check (bucket_id = 'observation-photos')
  $p$;
  execute $p$
    create policy "observation_photos_delete_authenticated"
      on storage.objects for delete to authenticated
      using (bucket_id = 'observation-photos')
  $p$;
end $$;
