-- =============================================================================
-- Authenticated RLS + live permission lock-down (apply with the signed-in APK).
--
-- Safe to run on live wildlife_app NOW. Does NOT revoke anon on the
-- relations old APKs still hit (customers, jobs, inspections,
-- field_observations, observation_events) or on storage.objects for
-- observation-photos. Live audit_log is written by SECURITY DEFINER
-- trigger audit_row_change(); clients do not need INSERT.
--
-- Grounded in the 2026-09-30 live catalog dump of project wildlife_app
-- (hgdzmwfcghtilyqagjak): 52 tables, 6 views, testing_full_access / anon_*
-- using(true) policies, default ALL grants to anon, helper EXECUTE revoked,
-- storage buckets open to anon.
--
-- What this does:
--   * Explicit `TO authenticated` policies on the tables SyncRepository and
--     ObservationPhotoUploader use.
--   * Matching `TO authenticated` policies on storage.objects for bucket
--     `observation-photos` (alongside the existing open policies).
--   * Immediately closes the live holes that old APKs do not need:
--       - REVOKE anon on every public table/view except the six hold relations
--       - drop testing_full_access and anon_* policies on non-hold tables
--       - stop default-privilege auto-grants of ALL to anon
--       - GRANT EXECUTE on is_org_member / has_org_role so org policies filter
--         instead of erroring
--       - block self-promotion on profiles / organization_members
--       - revoke anon EXECUTE on generate_due_recurring_jobs, queue_campaign,
--         refresh_technician_metrics
--       - close fieldops-* and job-photos anon storage policies
--   * Fixes mutable search_path on set_updated_at and
--     touch_integration_connection.
--
-- Apply the SEPARATE file
--   20260929221000_REVOKE_ANON_AFTER_SIGNED_IN_ROLLOUT.sql
-- only after every field device is on 2.3.7-supabase-auth (or later) and
-- owner-created Auth users exist. See supabase/migrations/README.md.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Native sync tables: authenticated policies (idempotent, skip missing).
-- customers already has "authenticated customers (consolidated)" on live.
-- ---------------------------------------------------------------------------
do $$
begin
  if to_regclass('public.customers') is null then
    raise notice 'authenticated_rls: skip missing public.customers';
  else
    execute 'drop policy if exists "authenticated_customers_all" on public.customers';
    execute $p$
      create policy "authenticated_customers_all" on public.customers
        for all to authenticated using (true) with check (true)
    $p$;
  end if;
  if to_regclass('public.jobs') is null then
    raise notice 'authenticated_rls: skip missing public.jobs';
  else
    execute 'drop policy if exists "authenticated_jobs_all" on public.jobs';
    execute $p$
      create policy "authenticated_jobs_all" on public.jobs
        for all to authenticated using (true) with check (true)
    $p$;
  end if;
  if to_regclass('public.inspections') is null then
    raise notice 'authenticated_rls: skip missing public.inspections';
  else
    execute 'drop policy if exists "authenticated_inspections_all" on public.inspections';
    execute $p$
      create policy "authenticated_inspections_all" on public.inspections
        for all to authenticated using (true) with check (true)
    $p$;
  end if;
end $$;

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
    -- Live audit_row_change() is SECURITY DEFINER (owner writes the row).
    -- Do not grant anon INSERT. Drop leftover open insert policies.
    execute 'drop policy if exists "audit_log_insert_anon" on public.audit_log';
    execute 'drop policy if exists "audit_log_insert" on public.audit_log';
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

-- ---------------------------------------------------------------------------
-- Live lock-down for everything old APKs do not touch.
-- Catalog-driven so this is a no-op on tables a preview DB does not have.
-- ---------------------------------------------------------------------------
do $$
declare
  hold constant text[] := array[
    'customers',
    'jobs',
    'inspections',
    'field_observations',
    'observation_events'
  ];
  privileged constant text[] := array[
    'organization_members',
    'organizations',
    'profiles',
    'audit_log'
  ];
  rec record;
  has_auth_policy boolean;
  defacl_role text;
begin
  -- 1. Table / view grants: anon loses ALL except SIUD (or narrower) on hold
  --    relations. Authenticated keeps SIUD, not TRUNCATE/TRIGGER/REFERENCES.
  for rec in
    select c.relname, c.relkind
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public'
      and c.relkind in ('r', 'v', 'p')
  loop
    execute format('revoke all on table public.%I from anon', rec.relname);
    execute format('revoke all on table public.%I from authenticated', rec.relname);
    execute format('grant all on table public.%I to service_role', rec.relname);

    if rec.relkind = 'v' then
      execute format('grant select on table public.%I to authenticated', rec.relname);
      continue;
    end if;

    if rec.relname = 'audit_log' then
      -- Trigger-written on live (SECURITY DEFINER). Authenticated INSERT
      -- covers invoker-style log_change() if that function exists.
      execute 'grant insert on table public.audit_log to authenticated';
      execute 'grant select on table public.audit_log to authenticated';
      continue;
    end if;

    if rec.relname = 'observation_events' then
      execute 'grant select, insert on table public.observation_events to anon, authenticated';
      continue;
    end if;

    execute format(
      'grant select, insert, update, delete on table public.%I to authenticated',
      rec.relname
    );

    if rec.relname = any (hold) then
      execute format(
        'grant select, insert, update, delete on table public.%I to anon',
        rec.relname
      );
    end if;

    -- Privilege-escalation tables: keep DML grants narrow; RLS does the rest.
    -- Membership rows are created in the SQL Editor as postgres/service_role.
    if rec.relname = 'organizations' then
      execute 'revoke insert, delete on table public.organizations from authenticated';
    elsif rec.relname = 'organization_members' then
      execute 'revoke insert, delete on table public.organization_members from authenticated';
    elsif rec.relname = 'profiles' then
      execute 'revoke delete on table public.profiles from authenticated';
    end if;
  end loop;

  -- Never leave TRUNCATE / TRIGGER / REFERENCES on Data API client roles.
  begin
    execute 'revoke truncate, trigger, references on all tables in schema public from anon, authenticated';
  exception when undefined_object or invalid_grant_operation then
    raise notice 'authenticated_rls: skip truncate/trigger/references revoke (%)', sqlerrm;
  end;

  -- 2. Sequences: anon does not need setval. Identity inserts run as owner.
  begin
    execute 'revoke all on all sequences in schema public from anon';
    execute 'grant usage, select on all sequences in schema public to authenticated, service_role';
  exception when undefined_object then
    raise notice 'authenticated_rls: skip sequence grants (%)', sqlerrm;
  end;

  -- 3. Default privileges: stop auto-granting ALL to anon on new objects.
  foreach defacl_role in array array['postgres', 'supabase_admin']
  loop
    if not exists (select 1 from pg_roles where rolname = defacl_role) then
      continue;
    end if;
    begin
      execute format(
        'alter default privileges for role %I in schema public revoke all on tables from anon',
        defacl_role
      );
      execute format(
        'alter default privileges for role %I in schema public revoke all on sequences from anon',
        defacl_role
      );
      execute format(
        'alter default privileges for role %I in schema public revoke all on functions from anon',
        defacl_role
      );
      execute format(
        'alter default privileges for role %I in schema public revoke all on tables from authenticated',
        defacl_role
      );
      execute format(
        'alter default privileges for role %I in schema public grant select, insert, update, delete on tables to authenticated',
        defacl_role
      );
      execute format(
        'alter default privileges for role %I in schema public grant all on tables to service_role',
        defacl_role
      );
    exception
      when insufficient_privilege or invalid_grant_operation then
        raise notice 'authenticated_rls: skip default privileges for % (%)', defacl_role, sqlerrm;
    end;
    if exists (select 1 from pg_namespace where nspname = 'storage') then
      begin
        execute format(
          'alter default privileges for role %I in schema storage revoke all on tables from anon',
          defacl_role
        );
      exception
        when insufficient_privilege or invalid_grant_operation or invalid_schema_name then
          raise notice 'authenticated_rls: skip storage default privileges for % (%)', defacl_role, sqlerrm;
      end;
    end if;
  end loop;

  -- 4. Drop testing_full_access everywhere. Drop anon_* / PUBLIC `true`
  --    policies on non-hold tables. Hold-table anon policies stay until HOLD.
  for rec in
    select schemaname, tablename, policyname, roles, qual, with_check
    from pg_policies
    where schemaname = 'public'
  loop
    if rec.policyname = 'testing_full_access' then
      execute format(
        'drop policy if exists %I on %I.%I',
        rec.policyname, rec.schemaname, rec.tablename
      );
      continue;
    end if;

    if rec.tablename = any (hold) then
      continue;
    end if;

    if rec.policyname like 'anon_%'
       or rec.policyname ilike '%anon%'
       or rec.policyname = 'Allow all ops on ai_runs'
       or rec.policyname = 'authenticated profiles access'
       or rec.policyname = 'profiles own access'
       or (
         ('public' = any (rec.roles::text[]) or 'anon' = any (rec.roles::text[]))
         and (
           coalesce(rec.qual, '') in ('true', '(true)')
           or coalesce(rec.with_check, '') in ('true', '(true)')
         )
       )
    then
      execute format(
        'drop policy if exists %I on %I.%I',
        rec.policyname, rec.schemaname, rec.tablename
      );
    end if;
  end loop;

  -- colors: SELECT is a palette lookup; drop anon from the live policy.
  if to_regclass('public.colors') is not null then
    execute 'drop policy if exists "colors_read" on public.colors';
    execute $p$
      create policy "colors_read" on public.colors
        for select to authenticated using (true)
    $p$;
  end if;

  -- 5. Fallback authenticated SIUD on tables that would otherwise have zero
  --    client policies after dropping testing_full_access. Skip privilege-
  --    escalation tables so org/profile policies stay restrictive.
  for rec in
    select c.relname
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public'
      and c.relkind = 'r'
      and c.relname <> all (privileged)
  loop
    select exists (
      select 1
      from pg_policies p
      where p.schemaname = 'public'
        and p.tablename = rec.relname
        and (
          'public' = any (p.roles::text[])
          or 'authenticated' = any (p.roles::text[])
        )
    ) into has_auth_policy;

    if not has_auth_policy then
      execute format(
        'drop policy if exists %I on public.%I',
        'authenticated_staff_all', rec.relname
      );
      execute format(
        $p$create policy %I on public.%I
             for all to authenticated using (true) with check (true)$p$,
        'authenticated_staff_all', rec.relname
      );
    end if;
  end loop;
end $$;

-- Org helpers: live REVOKE of EXECUTE makes every is_org_member / has_org_role
-- policy fail with "permission denied for function". Grant to client roles so
-- those policies filter (false for anon / non-members) instead of erroring.
do $$
begin
  if to_regprocedure('public.is_org_member(uuid)') is not null then
    execute 'grant execute on function public.is_org_member(uuid) to anon, authenticated, service_role';
  end if;
  if to_regprocedure('public.has_org_role(uuid, text[])') is not null then
    execute 'grant execute on function public.has_org_role(uuid, text[]) to anon, authenticated, service_role';
  end if;
end $$;

-- Restrictive policies for the tables a signed-in user currently uses to
-- self-promote to owner (FINDINGS #3). Requires the helpers above.
-- Recreate TO authenticated (live copies were TO public).
do $$
begin
  if to_regclass('public.organization_members') is null then
    return;
  end if;

  execute 'drop policy if exists "testing_full_access" on public.organization_members';
  execute 'drop policy if exists "members_self_select" on public.organization_members';
  execute 'drop policy if exists "members_select" on public.organization_members';
  execute 'drop policy if exists "members_manage" on public.organization_members';

  execute $p$
    create policy "members_self_select" on public.organization_members
      for select to authenticated
      using (user_id = auth.uid())
  $p$;
  execute $p$
    create policy "members_select" on public.organization_members
      for select to authenticated
      using (is_org_member(organization_id))
  $p$;
  execute $p$
    create policy "members_manage" on public.organization_members
      for update to authenticated
      using (has_org_role(organization_id, array['owner'::text, 'admin'::text]))
      with check (has_org_role(organization_id, array['owner'::text, 'admin'::text]))
  $p$;
end $$;

do $$
begin
  if to_regclass('public.organizations') is null then
    return;
  end if;

  execute 'drop policy if exists "testing_full_access" on public.organizations';
  execute 'drop policy if exists "organizations_select" on public.organizations';
  execute 'drop policy if exists "organizations_update" on public.organizations';
  execute 'drop policy if exists "authenticated_staff_all" on public.organizations';

  execute $p$
    create policy "organizations_select" on public.organizations
      for select to authenticated
      using (is_org_member(id))
  $p$;
  execute $p$
    create policy "organizations_update" on public.organizations
      for update to authenticated
      using (has_org_role(id, array['owner'::text, 'admin'::text]))
      with check (has_org_role(id, array['owner'::text, 'admin'::text]))
  $p$;
end $$;

do $$
begin
  if to_regclass('public.profiles') is null then
    return;
  end if;

  execute 'drop policy if exists "authenticated profiles access" on public.profiles';
  execute 'drop policy if exists "profiles own access" on public.profiles';
  execute 'drop policy if exists "profiles_select_own" on public.profiles';
  execute 'drop policy if exists "profiles_update_own_no_escalation" on public.profiles';
  execute 'drop policy if exists "profiles_insert_own_staff" on public.profiles';

  execute $p$
    create policy "profiles_select_own" on public.profiles
      for select to authenticated
      using (id = auth.uid())
  $p$;
  execute $p$
    create policy "profiles_update_own_no_escalation" on public.profiles
      for update to authenticated
      using (id = auth.uid())
      with check (id = auth.uid())
  $p$;
  execute $p$
    create policy "profiles_insert_own_staff" on public.profiles
      for insert to authenticated
      with check (
        id = auth.uid()
        and role is distinct from 'owner'
        and role is distinct from 'admin'
      )
  $p$;
end $$;

do $$
begin
  if to_regclass('public.profiles') is null then
    return;
  end if;

  execute $f$
    create or replace function public.prevent_profile_role_escalation()
    returns trigger
    language plpgsql
    set search_path to public, pg_temp
    as $fn$
    begin
      if tg_op = 'update' and new.role is distinct from old.role then
        if old.role is distinct from 'owner' and old.role is distinct from 'admin' then
          raise exception 'profile role changes require the dashboard or service_role';
        end if;
      end if;
      if tg_op = 'insert'
         and new.role in ('owner', 'admin')
         and current_user not in ('service_role', 'postgres', 'supabase_admin') then
        raise exception 'cannot self-insert as owner/admin';
      end if;
      return new;
    end;
    $fn$
  $f$;

  execute 'drop trigger if exists prevent_profile_role_escalation on public.profiles';
  execute $t$
    create trigger prevent_profile_role_escalation
      before insert or update on public.profiles
      for each row execute function public.prevent_profile_role_escalation()
  $t$;
end $$;

-- audit_read is TO PUBLIC and calls has_org_role. Recreate for authenticated
-- so anon no longer errors (or reads) the audit log.
do $$
begin
  if to_regclass('public.audit_log') is null then
    return;
  end if;
  if to_regprocedure('public.has_org_role(uuid, text[])') is null then
    return;
  end if;
  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'audit_log' and column_name = 'organization_id'
  ) then
    raise notice 'authenticated_rls: skip audit_read (no organization_id)';
    return;
  end if;

  execute 'drop policy if exists "audit_read" on public.audit_log';
  execute $p$
    create policy "audit_read" on public.audit_log
      for select to authenticated
      using (
        organization_id is null
        or has_org_role(organization_id, array['owner'::text, 'admin'::text])
      )
  $p$;
end $$;

-- Dangerous RPCs: live PUBLIC EXECUTE lets anon queue SMS/email and mint jobs.
do $$
declare
  fn text;
begin
  foreach fn in array array[
    'public.generate_due_recurring_jobs(date)',
    'public.queue_campaign(uuid)',
    'public.refresh_technician_metrics(date, date)'
  ]
  loop
    if to_regprocedure(fn) is null then
      raise notice 'authenticated_rls: skip missing %', fn;
      continue;
    end if;
    execute format('revoke all on function %s from public, anon', fn);
    execute format('grant execute on function %s to authenticated, service_role', fn);
  end loop;
end $$;

-- Advisor: mutable search_path on two trigger functions.
do $$
begin
  if to_regprocedure('public.set_updated_at()') is not null then
    execute 'alter function public.set_updated_at() set search_path to public, pg_temp';
  end if;
  if to_regprocedure('public.touch_integration_connection()') is not null then
    execute 'alter function public.touch_integration_connection() set search_path to public, pg_temp';
  end if;
end $$;

-- Storage: close anon write on fieldops-* and job-photos now. Keep
-- observation-photos PUBLIC policies until HOLD so old APKs can upload.
do $$
declare
  pol record;
begin
  if to_regclass('storage.objects') is null then
    return;
  end if;

  execute 'revoke all on table storage.objects from authenticated';
  execute 'revoke all on table storage.objects from anon';
  execute 'grant all on table storage.objects to service_role';
  execute 'grant select, insert, update, delete on table storage.objects to authenticated';
  -- anon keeps SIUD until HOLD (observation-photos upserts from old APKs).
  execute 'grant select, insert, update, delete on table storage.objects to anon';
  begin
    execute 'revoke truncate, trigger, references on table storage.objects from anon, authenticated';
  exception when undefined_object or invalid_grant_operation then
    raise notice 'authenticated_rls: skip storage truncate/trigger/references revoke (%)', sqlerrm;
  end;

  for pol in
    select policyname
    from pg_policies
    where schemaname = 'storage'
      and tablename = 'objects'
      and (
        policyname = 'fieldops_photos_access'
        or policyname like 'Give anon users access to JPG images in folder%'
      )
  loop
    execute format('drop policy if exists %I on storage.objects', pol.policyname);
  end loop;

  execute 'drop policy if exists "fieldops_photos_access" on storage.objects';
  execute $p$
    create policy "fieldops_photos_access" on storage.objects
      for all to authenticated
      using (bucket_id = any (array['fieldops-photos'::text, 'fieldops-documents'::text, 'fieldops-signatures'::text]))
      with check (bucket_id = any (array['fieldops-photos'::text, 'fieldops-documents'::text, 'fieldops-signatures'::text]))
  $p$;

  -- Working job-photo upload (assumed authenticated). Keep live names + add
  -- an explicit UPDATE policy matching supabase-kt upsert.
  execute 'drop policy if exists "Allow authenticated reads" on storage.objects';
  execute 'drop policy if exists "Allow authenticated uploads" on storage.objects';
  execute 'drop policy if exists "Allow authenticated deletes" on storage.objects';
  execute 'drop policy if exists "Allow authenticated updates" on storage.objects';
  execute $p$
    create policy "Allow authenticated reads" on storage.objects
      for select to authenticated using (bucket_id = 'job-photos')
  $p$;
  execute $p$
    create policy "Allow authenticated uploads" on storage.objects
      for insert to authenticated with check (bucket_id = 'job-photos')
  $p$;
  execute $p$
    create policy "Allow authenticated updates" on storage.objects
      for update to authenticated
      using (bucket_id = 'job-photos')
      with check (bucket_id = 'job-photos')
  $p$;
  execute $p$
    create policy "Allow authenticated deletes" on storage.objects
      for delete to authenticated using (bucket_id = 'job-photos')
  $p$;

  if to_regclass('storage.buckets') is not null then
    execute $u$
      update storage.buckets
         set public = false
       where id in ('job-photos', 'job-pdfs')
    $u$;
    -- observation-photos stays public: Android ObservationPhotoUploader stores publicUrl.
    execute $u$
      update storage.buckets
         set public = true
       where id = 'observation-photos'
    $u$;
  end if;
end $$;
