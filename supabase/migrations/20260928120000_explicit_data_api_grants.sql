-- =============================================================================
-- Explicit Data API grants (PostgREST / supabase-kt / supabase-js)
--
-- Starting 2026-10-30, tables newly created in public on existing projects no
-- longer receive automatic grants to anon / authenticated / service_role.
--
-- STALE vs live wildlife_app (dumped 2026-09-30). Live does not have
-- materials, job_materials, weekly_revenue, customer_summary, pending_repairs,
-- or material_inventory. Do not use this file as the live permission source.
-- Phase 1 (`20260929220000_authenticated_rls_for_signed_in_sync.sql`) is the
-- grant + RLS lock-down that matches live. This file only top-ups
-- authenticated / service_role SIUD on relations that exist.
--
-- Does NOT grant anon. Safe to re-run. Skips missing relations.
-- Do not run this after phase 2 expecting it to restore anon — it will not.
-- =============================================================================

grant usage on schema public to anon, authenticated, service_role;

do $$
begin
  if to_regprocedure('public.set_updated_at()') is not null then
    execute 'grant execute on function public.set_updated_at() to authenticated, service_role';
  end if;
  if to_regprocedure('public.log_change()') is not null then
    execute 'grant execute on function public.log_change() to authenticated, service_role';
  end if;
end $$;

grant usage, select on all sequences in schema public to authenticated, service_role;

do $$
declare
  rec record;
begin
  for rec in
    select * from (values
      -- Native sync (authenticated after sign-in; phase 1 separately holds
      -- anon SIUD until the HOLD file)
      ('customers',            'select, insert, update, delete'),
      ('jobs',                 'select, insert, update, delete'),
      ('inspections',          'select, insert, update, delete'),
      ('field_observations',   'select, insert, update, delete'),
      ('observation_events',   'select, insert'),
      -- Present on live; unused by native sync (leftover web / future photo sync)
      ('photos',               'select, insert, update, delete'),
      ('job_photos',           'select, insert, update, delete'),
      ('techs',                'select, insert, update, delete'),
      ('visits',               'select, insert, update, delete'),
      ('repairs',              'select, insert, update, delete'),
      ('signatures',           'select, insert, update, delete'),
      ('services',             'select, insert, update, delete'),
      ('pdf_documents',        'select, insert, update, delete'),
      ('appointments',         'select, insert, update, delete'),
      ('sync_events',          'select, insert, update, delete'),
      ('expenses',             'select, insert, update, delete'),
      ('ai_runs',              'select, insert, update, delete'),
      -- Live views (security_invoker)
      ('job_stats',            'select'),
      ('tech_stats',           'select'),
      ('species_stats',        'select'),
      ('business_snapshot_v2', 'select'),
      ('inventory_alerts',     'select'),
      ('warranty_alerts',      'select')
    ) as t(rel, privs)
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
  end loop;
end $$;

-- audit_log: trigger-written on live. Authenticated INSERT only if an invoker
-- trigger exists; service_role may read.
do $$
begin
  if to_regclass('public.audit_log') is null then
    raise notice 'data_api_grants: skip missing public.audit_log';
    return;
  end if;

  execute 'alter table public.audit_log enable row level security';
  execute 'drop policy if exists "audit_log_insert" on public.audit_log';
  execute 'drop policy if exists "audit_log_insert_authenticated" on public.audit_log';
  execute 'create policy "audit_log_insert_authenticated" on public.audit_log for insert to authenticated with check (true)';
  execute 'grant insert on public.audit_log to authenticated, service_role';
  execute 'grant select on public.audit_log to authenticated';
  execute 'grant select, update, delete on public.audit_log to service_role';
end $$;
