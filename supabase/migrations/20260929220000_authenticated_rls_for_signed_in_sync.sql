-- =============================================================================
-- Authenticated RLS + storage policies for signed-in FieldOps (apply with the
-- signed-in APK).
--
-- Safe to run on live wildlife_app NOW. Does NOT revoke anon. Currently
-- installed APKs that still talk as the Postgres `anon` role keep working.
--
-- What this does:
--   * Explicit `TO authenticated` policies on the tables SyncRepository and
--     ObservationPhotoUploader use, matching the privileges the app needs
--     (SIUD on customers/jobs/inspections/field_observations; SI on
--     observation_events; INSERT on audit_log for trigger invokers).
--   * Matching `TO authenticated` policies on storage.objects for bucket
--     `observation-photos` (alongside the existing open policies).
--
-- Apply the SEPARATE file
--   20260929221000_REVOKE_ANON_AFTER_SIGNED_IN_ROLLOUT.sql
-- only after every field device is on 2.3.7-supabase-auth (or later) and
-- owner-created Auth users exist. See supabase/migrations/README.md.
-- =============================================================================

-- customers (native_sync_fix already has "authenticated customers (consolidated)")
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
  if to_regclass('public.field_observations') is null then
    raise notice 'authenticated_rls: skip missing public.field_observations';
  else
    execute 'drop policy if exists "authenticated_field_observations_all" on public.field_observations';
    execute $p$
      create policy "authenticated_field_observations_all" on public.field_observations
        for all to authenticated using (true) with check (true)
    $p$;
  end if;
end $$;

do $$
begin
  if to_regclass('public.observation_events') is null then
    raise notice 'authenticated_rls: skip missing public.observation_events';
  else
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
end $$;

do $$
begin
  if to_regclass('public.audit_log') is null then
    raise notice 'authenticated_rls: skip missing public.audit_log';
  else
    execute 'alter table public.audit_log enable row level security';
    execute 'drop policy if exists "audit_log_insert_authenticated" on public.audit_log';
    execute $p$
      create policy "audit_log_insert_authenticated" on public.audit_log
        for insert to authenticated with check (true)
    $p$;
  end if;
end $$;

-- Storage: add authenticated policies. Leave the unrestricted
-- observation_photos_* policies in place until the revoke-anon rollout file.
do $$
begin
  if to_regclass('storage.objects') is null then
    raise notice 'authenticated_rls: skip missing storage.objects';
    return;
  end if;

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
