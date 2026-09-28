-- =============================================================================
-- Explicit Data API grants (PostgREST / supabase-kt / supabase-js)
--
-- Starting 2026-10-30, tables newly created in public on existing projects no
-- longer receive automatic grants to anon / authenticated / service_role.
-- Existing live tables keep whatever grants they already have. This migration
-- is the follow-up to run on wildlife_app (and on preview / db reset) so every
-- public relation the repo defines is reachable after that cutoff.
--
-- Safe to re-run. Skips relations that are not present (migrations-only DBs
-- vs full schema.sql bootstrap).
--
-- Does not add or loosen RLS policies except audit_log INSERT (needed so
-- public.log_change() triggers still succeed once default grants are gone).
-- =============================================================================

grant usage on schema public to anon, authenticated, service_role;

do $$
begin
  if to_regprocedure('public.set_updated_at()') is not null then
    execute 'grant execute on function public.set_updated_at() to anon, authenticated, service_role';
  end if;
  if to_regprocedure('public.log_change()') is not null then
    execute 'grant execute on function public.log_change() to anon, authenticated, service_role';
  end if;
end $$;

grant usage, select on all sequences in schema public to anon, authenticated, service_role;

do $$
declare
  rec record;
begin
  -- include_anon: native Android uses the anon key with no sign-in
  -- (SupabaseService never calls signIn from UI). Only tables SyncRepository
  -- / ObservationPhotoUploader actually hit as that role get anon grants.
  for rec in
    select * from (values
      -- Native sync (anon key, no session)
      ('customers',            'select, insert, update, delete', true),
      ('jobs',                 'select, insert, update, delete', true),
      ('inspections',          'select, insert, update, delete', true),
      ('field_observations',   'select, insert, update, delete', true),
      ('observation_events',   'select, insert',                 true),
      -- Present in schema.sql / older migrations; not used by native sync
      ('techs',                'select, insert, update, delete', false),
      ('visits',               'select, insert, update, delete', false),
      ('repairs',              'select, insert, update, delete', false),
      ('signatures',           'select, insert, update, delete', false),
      ('materials',            'select, insert, update, delete', false),
      ('job_materials',        'select, insert, update, delete', false),
      ('services',             'select, insert, update, delete', false),
      ('photos',               'select, insert, update, delete', false),
      ('pdf_documents',        'select, insert, update, delete', false),
      ('appointments',         'select, insert, update, delete', false),
      ('sync_events',          'select, insert, update, delete', false),
      ('expenses',             'select, insert, update, delete', false),
      ('ai_runs',              'select, insert, update, delete', false),
      -- Dashboard views (leftover web client; not native)
      ('job_stats',            'select',                         false),
      ('tech_stats',           'select',                         false),
      ('species_stats',        'select',                         false),
      ('weekly_revenue',       'select',                         false),
      ('customer_summary',     'select',                         false),
      ('pending_repairs',      'select',                         false),
      ('material_inventory',   'select',                         false)
    ) as t(rel, privs, include_anon)
  loop
    if to_regclass('public.' || rec.rel) is null then
      raise notice 'data_api_grants: skip missing public.%', rec.rel;
      continue;
    end if;
    execute format(
      'grant %s on table public.%I to authenticated, service_role',
      rec.privs,
      rec.rel
    );
    if rec.include_anon then
      execute format(
        'grant %s on table public.%I to anon',
        rec.privs,
        rec.rel
      );
    end if;
  end loop;
end $$;

-- audit_log: trigger-written. Data API roles may INSERT (as the invoker of
-- job/customer/... DML) but must not SELECT customer/job history via REST.
do $$
begin
  if to_regclass('public.audit_log') is null then
    raise notice 'data_api_grants: skip missing public.audit_log';
    return;
  end if;

  execute 'alter table public.audit_log enable row level security';
  execute 'drop policy if exists "audit_log_insert" on public.audit_log';
  execute 'create policy "audit_log_insert" on public.audit_log for insert with check (true)';
  execute 'grant insert on public.audit_log to anon, authenticated, service_role';
  execute 'grant select, update, delete on public.audit_log to service_role';
end $$;
