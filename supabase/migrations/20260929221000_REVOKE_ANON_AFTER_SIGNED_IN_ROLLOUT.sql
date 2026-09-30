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
--   5. 20260929220000_authenticated_rls_for_signed_in_sync.sql has been
--      applied (closes invoices/payments/orgs/storage for anon already).
--
-- Applying this while any remaining APK still uses the anon key with no
-- session will break that device's sync (PostgREST 401/42501). Local Room
-- data on those devices is not deleted, but cloud push/pull will fail until
-- they upgrade and sign in.
--
-- This file:
--   * REVOKEs remaining table privileges from `anon` on the six hold
--     relations (customers, jobs, inspections, field_observations,
--     observation_events, audit_log) and on storage.objects
--   * Drops leftover anon / PUBLIC using(true) policies on those tables
--   * Tightens observation-photos storage.objects policies to `authenticated`
--   * REVOKEs remaining function EXECUTE from anon
--
-- Safe to re-run. Does not touch service_role grants. Authenticated SIUD on
-- native sync tables is unchanged.
-- =============================================================================

do $$
declare
  hold constant text[] := array[
    'customers',
    'jobs',
    'inspections',
    'field_observations',
    'observation_events',
    'audit_log'
  ];
  rel text;
begin
  foreach rel in array hold
  loop
    if to_regclass('public.' || rel) is null then
      raise notice 'revoke_anon: skip missing public.%', rel;
      continue;
    end if;
    execute format('revoke all on table public.%I from anon', rel);
  end loop;

  begin
    execute 'revoke all on all sequences in schema public from anon';
  exception when undefined_object then
    null;
  end;
end $$;

-- Drop leftover open policies on the hold tables (live names + fixture names).
drop policy if exists "anon_select_customers" on public.customers;
drop policy if exists "anon_insert_customers" on public.customers;
drop policy if exists "anon_update_customers" on public.customers;
drop policy if exists "anon_delete_customers" on public.customers;
drop policy if exists "testing_full_access" on public.customers;
drop policy if exists "open_customers" on public.customers;

drop policy if exists "allow anon all jobs" on public.jobs;
drop policy if exists "anon_delete_jobs" on public.jobs;
drop policy if exists "anon_insert_jobs" on public.jobs;
drop policy if exists "anon_select_jobs" on public.jobs;
drop policy if exists "anon_update_jobs" on public.jobs;
drop policy if exists "testing_full_access" on public.jobs;
drop policy if exists "open_jobs" on public.jobs;

drop policy if exists "Allow anon access to inspections" on public.inspections;
drop policy if exists "anon_delete_inspections" on public.inspections;
drop policy if exists "anon_insert_inspections" on public.inspections;
drop policy if exists "anon_select_inspections" on public.inspections;
drop policy if exists "anon_update_inspections" on public.inspections;
drop policy if exists "testing_full_access" on public.inspections;
drop policy if exists "open_inspections" on public.inspections;

do $$
begin
  if to_regclass('public.field_observations') is null then
    return;
  end if;
  execute 'drop policy if exists "field_observations_delete" on public.field_observations';
  execute 'drop policy if exists "field_observations_insert" on public.field_observations';
  execute 'drop policy if exists "field_observations_select" on public.field_observations';
  execute 'drop policy if exists "field_observations_update" on public.field_observations';
  execute 'drop policy if exists "open_field_observations" on public.field_observations';
  execute 'drop policy if exists "testing_full_access" on public.field_observations';
end $$;

do $$
begin
  if to_regclass('public.observation_events') is null then
    return;
  end if;
  execute 'drop policy if exists "observation_events_select" on public.observation_events';
  execute 'drop policy if exists "observation_events_insert" on public.observation_events';
  execute 'drop policy if exists "open_observation_events_select" on public.observation_events';
  execute 'drop policy if exists "open_observation_events_insert" on public.observation_events';
  execute 'drop policy if exists "testing_full_access" on public.observation_events';
end $$;

do $$
begin
  if to_regclass('public.audit_log') is null then
    return;
  end if;
  execute 'drop policy if exists "audit_log_insert_anon" on public.audit_log';
  execute 'drop policy if exists "audit_log_insert" on public.audit_log';
  execute 'drop policy if exists "testing_full_access" on public.audit_log';
end $$;

-- Recreate authenticated policies in case the previous migration was skipped.
drop policy if exists "authenticated_customers_all" on public.customers;
create policy "authenticated_customers_all" on public.customers
  for all to authenticated using (true) with check (true);

drop policy if exists "authenticated_jobs_all" on public.jobs;
create policy "authenticated_jobs_all" on public.jobs
  for all to authenticated using (true) with check (true);

drop policy if exists "authenticated_inspections_all" on public.inspections;
create policy "authenticated_inspections_all" on public.inspections
  for all to authenticated using (true) with check (true);

do $$
begin
  if to_regclass('public.field_observations') is not null then
    execute 'drop policy if exists "authenticated_field_observations_all" on public.field_observations';
    execute $p$
      create policy "authenticated_field_observations_all" on public.field_observations
        for all to authenticated using (true) with check (true)
    $p$;
  end if;
  if to_regclass('public.observation_events') is not null then
    execute 'drop policy if exists "authenticated_observation_events_select" on public.observation_events';
    execute 'drop policy if exists "authenticated_observation_events_insert" on public.observation_events';
    execute $p$
      create policy "authenticated_observation_events_select" on public.observation_events
        for select to authenticated using (true)
    $p$;
    execute $p$
      create policy "authenticated_observation_events_insert" on public.observation_events
        for insert to authenticated with check (true)
    $p$;
  end if;
  if to_regclass('public.audit_log') is not null then
    execute 'drop policy if exists "audit_log_insert_authenticated" on public.audit_log';
    execute $p$
      create policy "audit_log_insert_authenticated" on public.audit_log
        for insert to authenticated with check (true)
    $p$;
  end if;
end $$;

-- Remaining function EXECUTE for anon (trigger functions old APKs invoked).
do $$
declare
  rec record;
begin
  for rec in
    select p.oid::regprocedure as fn
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
  loop
    execute format('revoke all on function %s from anon', rec.fn);
  end loop;
end $$;

-- Storage: only authenticated may read/write observation-photos objects.
do $$
begin
  if to_regclass('storage.objects') is null then
    raise notice 'revoke_anon: skip missing storage.objects';
    return;
  end if;

  execute 'revoke all on table storage.objects from anon';

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
