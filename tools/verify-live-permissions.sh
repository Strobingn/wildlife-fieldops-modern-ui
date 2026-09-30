#!/usr/bin/env bash
# Restore the live wildlife_app catalog dump into local Postgres and apply the
# PR #59 permission migrations. Does not touch the live project.
#
# Usage:
#   DUMP_DIR=/path/to/uploads ./tools/verify-live-permissions.sh
#
# From DUMP_DIR (after staging hashed names):
#   psql -v ON_ERROR_STOP=1 -f 00_restore_live_schema.sql
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MIG="$ROOT/supabase/migrations"

need_cmd() { command -v "$1" >/dev/null 2>&1; }

find_dump_dir() {
  local candidates=(
    "${DUMP_DIR:-}"
    /home/ubuntu/.cursor/projects/workspace/uploads
    "$ROOT/../uploads"
  )
  local c
  for c in "${candidates[@]}"; do
    [ -n "$c" ] || continue
    [ -d "$c" ] || continue
    if ls "$c"/00_restore_live_schema*.sql >/dev/null 2>&1; then
      echo "$c"
      return 0
    fi
  done
  return 1
}

stage_dump() {
  local src="$1"
  local dest="$2"
  mkdir -p "$dest"
  cp "$src"/00_restore_live_schema*.sql "$dest/00_restore_live_schema.sql"
  # Restore script \i s un-hashed names. Map hashed uploads onto those names.
  local mapping=(
    "01_types.sql:01_types"
    "02_tables_a.sql:02_tables_a"
    "03_tables_b.sql:03_tables_b"
    "04_tables_c.sql:04_tables_c"
    "05_constraints_indexes.sql:05_constraints_indexes"
    "06_views_ordered.sql:06_views_ordered"
    "07_policies.sql:07_policies"
    "15_functions.sql:15_functions"
    "16_triggers.sql:16_triggers"
  )
  local entry name prefix match
  for entry in "${mapping[@]}"; do
    name="${entry%%:*}"
    prefix="${entry##*:}"
    match=$(ls "$src/${prefix}"*.sql 2>/dev/null | head -n1 || true)
    if [ -z "$match" ]; then
      echo "missing dump file matching ${prefix}*.sql in $src" >&2
      exit 1
    fi
    cp "$match" "$dest/$name"
  done
}

if ! DUMP=$(find_dump_dir); then
  echo "SKIP: live schema dump not found (set DUMP_DIR)"
  exit 0
fi

if ! need_cmd psql; then
  echo "SKIP: psql not available"
  exit 0
fi

if ! sudo -u postgres pg_isready -q 2>/dev/null; then
  sudo pg_ctlcluster 16 main start || true
fi

STAGE=$(mktemp -d)
chmod 755 "$STAGE"
DB="fieldops_live_perms_$$"
trap 'sudo -u postgres dropdb --if-exists "$DB" >/dev/null 2>&1 || true; rm -rf "$STAGE"' EXIT

stage_dump "$DUMP" "$STAGE"
chmod -R a+rX "$STAGE"
sudo -u postgres createdb "$DB"

echo "== restore live catalog into $DB"
echo "    (cd \$STAGE && psql -v ON_ERROR_STOP=1 -f 00_restore_live_schema.sql)"
sudo -u postgres bash -c "cd '$STAGE' && psql -d '$DB' -v ON_ERROR_STOP=1 -f 00_restore_live_schema.sql"

psql_db() {
  sudo -u postgres psql -d "$DB" -v ON_ERROR_STOP=1 "$@"
}

echo "== apply phase 1 (authenticated RLS + immediate lock-down)"
psql_db -f "$MIG/20260929220000_authenticated_rls_for_signed_in_sync.sql"

echo "== re-apply phase 1 (idempotence)"
psql_db -f "$MIG/20260929220000_authenticated_rls_for_signed_in_sync.sql"

echo "== assert phase 1 grants / policies"
psql_db <<'SQL'
do $$
declare
  anon_hold int;
  anon_invoices int;
  helper_ok boolean;
begin
  select count(*) into anon_hold
  from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'customers'
    and grantee = 'anon' and privilege_type = 'INSERT';
  if anon_hold < 1 then
    raise exception 'phase 1 revoked anon INSERT on customers (old APKs need it)';
  end if;

  if has_table_privilege('anon', 'public.customers', 'TRUNCATE')
     or has_table_privilege('authenticated', 'public.customers', 'TRUNCATE') then
    raise exception 'TRUNCATE still granted on customers to a client role';
  end if;

  select count(*) into anon_invoices
  from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'invoices'
    and grantee = 'anon';
  if anon_invoices > 0 then
    raise exception 'phase 1 left anon grants on invoices';
  end if;

  if has_table_privilege('anon', 'public.payments', 'SELECT')
     or has_table_privilege('anon', 'public.payment_records', 'SELECT')
     or has_table_privilege('anon', 'public.organizations', 'SELECT')
     or has_table_privilege('anon', 'public.organization_members', 'SELECT')
     or has_table_privilege('anon', 'public.audit_log', 'INSERT') then
    raise exception 'phase 1 left anon DML on a non-hold table';
  end if;

  if not has_table_privilege('anon', 'public.photos', 'INSERT')
     or not has_table_privilege('anon', 'public.job_photos', 'INSERT') then
    raise exception 'phase 1 revoked anon INSERT on photos/job_photos (PR #60 needs it)';
  end if;

  if has_table_privilege('authenticated', 'public.organization_members', 'INSERT') then
    raise exception 'authenticated still has INSERT on organization_members';
  end if;

  select has_function_privilege('authenticated', 'public.is_org_member(uuid)', 'EXECUTE')
    into helper_ok;
  if not helper_ok then
    raise exception 'authenticated lacks EXECUTE on is_org_member';
  end if;

  if has_function_privilege('anon', 'public.queue_campaign(uuid)', 'EXECUTE')
     or has_function_privilege('anon', 'public.generate_due_recurring_jobs(date)', 'EXECUTE')
     or has_function_privilege('anon', 'public.refresh_technician_metrics(date, date)', 'EXECUTE') then
    raise exception 'anon can still EXECUTE a dangerous RPC';
  end if;

  if exists (
    select 1 from pg_policies
    where schemaname = 'public' and policyname = 'testing_full_access'
      and tablename = 'invoices'
  ) then
    raise exception 'testing_full_access still on invoices';
  end if;

  if exists (
    select 1 from pg_policies
    where schemaname = 'storage' and tablename = 'objects'
      and policyname = 'fieldops_photos_access'
      and 'anon' = any (roles::text[])
  ) then
    raise exception 'fieldops_photos_access still includes anon';
  end if;

  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'jobs'
      and policyname in ('allow anon all jobs', 'anon_select_jobs', 'open_jobs')
  ) then
    raise exception 'phase 1 dropped hold-table anon policies on jobs';
  end if;

  if not exists (
    select 1 from storage.buckets where id in ('observation-photos', 'job-photos') and public
  ) then
    raise exception 'observation-photos / job-photos must stay public (Android publicUrl)';
  end if;
  if exists (
    select 1 from storage.buckets where id = 'job-pdfs' and public
  ) then
    raise exception 'job-pdfs still public';
  end if;

  if not exists (
    select 1 from pg_policies
    where schemaname = 'storage' and tablename = 'objects'
      and policyname like 'Give anon users access to JPG images in folder%'
  ) then
    raise exception 'phase 1 dropped live anon job-photos JPG policies';
  end if;

  if not exists (
    select 1 from information_schema.columns
    where table_schema = 'public' and table_name = 'jobs' and column_name = 'pricing'
  ) then
    raise exception 'phase 1 did not add jobs.pricing';
  end if;
end $$;
SQL

echo "== Kotlin-shaped queries (phase 1: anon current APK + authenticated signed-in)"
psql_db <<'SQL'
do $$
declare
  uid uuid := gen_random_uuid();
  org uuid;
  cust uuid;
  job uuid;
  insp uuid;
  obs uuid;
begin
  -- ---- authenticated signed-in path (PR #59) ----
  insert into auth.users (id, email) values (uid, 'tech@example.com');
  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', uid::text, 'role', 'authenticated')::text,
    false
  );
  set role authenticated;

  insert into public.customers (name, phone, town)
    values ('Auth Customer', '555-0100', 'Walden')
    returning id into cust;
  update public.customers set notes = 'upsert-merge' where id = cust;
  perform * from public.customers;

  insert into public.jobs (customer, title, status, address, ai_notes, pricing)
    values (
      'Auth Customer',
      'Squirrel job',
      'Active',
      '210 Willow Avenue',
      'AI: attic',
      '{"totalOverride": 850}'::jsonb
    )
    returning id into job;
  update public.jobs set notes = 'follow-up' where id = job;
  perform * from public.jobs;

  insert into public.inspections (job_id, notes, status)
    values (job, 'attic', 'completed')
    returning id into insp;
  update public.inspections set notes = 'updated' where id = insp;

  insert into public.field_observations (notes, species_hint, photo_storage_path)
    values ('raccoon', 'raccoon', 'field/obs/obs.jpg')
    returning id into obs;
  update public.field_observations set photo_public_url = 'https://example.test/obs.jpg' where id = obs;

  insert into public.observation_events (event_id, entity_id, observed_at, uploaded_at)
    values ('evt-auth-1', obs::text, 0, 0);

  insert into storage.objects (bucket_id, name)
    values ('observation-photos', 'field/obs/obs.jpg')
    on conflict do nothing;
  update storage.objects
     set name = name
   where bucket_id = 'observation-photos' and name = 'field/obs/obs.jpg';
  perform * from storage.objects where bucket_id = 'observation-photos';

  insert into public.photos (job_id, storage_path) values (job, 'jobs/' || job::text || '/1.jpg');
  insert into public.job_photos (job_id, path) values (job, 'jobs/' || job::text || '/1.jpg');
  insert into storage.objects (bucket_id, name)
    values ('job-photos', 'jobs/' || job::text || '/1.jpg');

  perform public.is_org_member('00000000-0000-0000-0000-000000000001'::uuid);
  perform public.has_org_role('00000000-0000-0000-0000-000000000001'::uuid, array['owner']);

  reset role;

  -- ---- anon current APK (hold tables + observation-photos only) ----
  perform set_config('request.jwt.claims', json_build_object('role', 'anon')::text, false);
  set role anon;

  insert into public.customers (name) values ('Anon Old APK') returning id into cust;
  update public.customers set notes = 'anon-upsert' where id = cust;
  perform * from public.customers;
  delete from public.customers where id = cust;

  insert into public.jobs (customer, title) values ('Anon Job', 'Anon Job') returning id into job;
  update public.jobs set status = 'Closed', ai_notes = 'AI: raccoon', pricing = '{"totalOverride": 200}'::jsonb where id = job;
  perform * from public.jobs;

  insert into public.inspections (notes) values ('anon-insp') returning id into insp;
  update public.inspections set notes = 'anon-insp-2' where id = insp;
  delete from public.inspections where id = insp;

  insert into public.field_observations (notes) values ('anon-obs') returning id into obs;
  update public.field_observations set notes = 'anon-obs-2' where id = obs;

  insert into public.observation_events (event_id, entity_id, observed_at)
    values ('evt-anon-1', 'ent-anon', 1);

  -- PR #60: PostgREST-style upsert by id on photos / job_photos + job-photos
  -- object at public/{jobId}/{photoId}.jpg
  insert into public.photos (id, job_id, image_url, storage_path, tag)
    values (
      'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1'::uuid,
      job,
      'https://example.test/public/job/photo.jpg',
      'public/' || job::text || '/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1.jpg',
      'live_capture'
    )
    on conflict (id) do update
      set image_url = excluded.image_url,
          storage_path = excluded.storage_path,
          tag = excluded.tag;
  update public.photos
     set tag = 'entry'
   where id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1'::uuid;

  insert into public.job_photos (id, job_id, path, public_url, notes)
    values (
      'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1'::uuid,
      job,
      'public/' || job::text || '/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1.jpg',
      'https://example.test/public/job/photo.jpg',
      'Live Capture raccoon'
    )
    on conflict (id) do update
      set path = excluded.path,
          public_url = excluded.public_url,
          notes = excluded.notes;

  insert into storage.objects (bucket_id, name)
    values (
      'job-photos',
      'public/' || job::text || '/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1.jpg'
    );
  update storage.objects
     set name = name
   where bucket_id = 'job-photos'
     and name = 'public/' || job::text || '/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1.jpg';
  perform 1 from storage.objects where bucket_id = 'job-photos' limit 1;

  insert into storage.objects (bucket_id, name)
    values ('observation-photos', 'field/anon/anon.jpg');
  update storage.objects
     set name = name
   where bucket_id = 'observation-photos' and name = 'field/anon/anon.jpg';
  perform 1 from storage.objects where bucket_id = 'observation-photos' limit 1;

  reset role;
end $$;
SQL

echo "== phase 1: anon denied on unused tables / RPCs / buckets; no self-promotion"
psql_db <<'SQL'
do $$
declare
  denied int := 0;
  rec record;
  uid uuid := gen_random_uuid();
  org uuid;
  stmt_err text;
begin
  insert into public.organizations (name, slug) values ('Wildlife Whisperer', 'ww-llc')
    returning id into org;
  insert into auth.users (id, email) values (uid, 'tech2@example.com');

  for rec in
    select * from (values
      ('invoices', $s$insert into public.invoices (id, issue_date, due_date, created_at, updated_at) values ('inv-anon', 1, 1, 1, 1)$s$),
      ('payments', format($s$insert into public.payments (organization_id, provider, amount, status) values (%L::uuid, 'cash', 1, 'paid')$s$, org)),
      ('payment_records', format($s$insert into public.payment_records (organization_id, amount) values (%L::uuid, 1)$s$, org)),
      ('organizations', format($s$insert into public.organizations (name, slug) values ('x', 'x-%s')$s$, uid)),
      ('organization_members', format($s$insert into public.organization_members (organization_id, user_id, role) values (%L::uuid, %L::uuid, 'owner')$s$, org, uid)),
      ('audit_log', $s$insert into public.audit_log (entity_type, action) values ('customers', 'INSERT')$s$),
      ('fieldops storage', $s$insert into storage.objects (bucket_id, name) values ('fieldops-photos', 'secret.pdf')$s$),
      ('job-photos outside public/', $s$insert into storage.objects (bucket_id, name) values ('job-photos', 'not-public/x.jpg')$s$),
      ('queue_campaign', $s$select public.queue_campaign(gen_random_uuid())$s$),
      ('generate_due_recurring_jobs', $s$select public.generate_due_recurring_jobs(current_date)$s$)
    ) as t(rel, stmt)
  loop
    perform set_config('request.jwt.claims', json_build_object('role', 'anon')::text, false);
    set role anon;
    begin
      execute rec.stmt;
      raise exception 'anon DML on % succeeded after phase 1', rec.rel;
    exception
      when insufficient_privilege or invalid_grant_operation then
        denied := denied + 1;
        raise notice 'anon denied on % (privilege)', rec.rel;
      when others then
        if sqlerrm like '%succeeded after%' then
          raise;
        end if;
        denied := denied + 1;
        stmt_err := sqlerrm;
        raise notice 'anon denied on % (%)', rec.rel, stmt_err;
    end;
    reset role;
  end loop;

  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', uid::text, 'role', 'authenticated')::text,
    false
  );
  set role authenticated;
  begin
    insert into public.organization_members (organization_id, user_id, role)
    values (org, uid, 'owner');
    raise exception 'authenticated self-insert as owner succeeded';
  exception
    when insufficient_privilege then
      raise notice 'authenticated cannot insert organization_members (privilege)';
    when others then
      if sqlerrm like '%succeeded%' then
        raise;
      end if;
      raise notice 'authenticated cannot insert organization_members (%)', sqlerrm;
  end;
  begin
    insert into public.profiles (id, email, role) values (uid, 'tech2@example.com', 'owner');
    raise exception 'authenticated self-insert profile owner succeeded';
  exception
    when insufficient_privilege then
      raise notice 'authenticated cannot insert owner profile (privilege)';
    when others then
      if sqlerrm like '%succeeded%' then
        raise;
      end if;
      raise notice 'authenticated cannot insert owner profile (%)', sqlerrm;
  end;
  reset role;
  perform set_config('request.jwt.claims', '{}', false);

  if denied < 9 then
    raise exception 'expected anon denies on non-hold relations, got %', denied;
  end if;
end $$;
SQL

echo "== default privileges: new public table is not auto-granted to anon"
psql_db <<'SQL'
create table public._perm_probe (id int primary key);
do $$
begin
  if has_table_privilege('anon', 'public._perm_probe', 'SELECT')
     or has_table_privilege('anon', 'public._perm_probe', 'INSERT') then
    raise exception 'new table still auto-granted to anon';
  end if;
end $$;
drop table public._perm_probe;
SQL

echo "== apply HOLD / phase 2"
psql_db -f "$MIG/20260929221000_REVOKE_ANON_AFTER_SIGNED_IN_ROLLOUT.sql"

echo "== re-run 28120000 (must not restore anon)"
psql_db -f "$MIG/20260928120000_explicit_data_api_grants.sql"

echo "== assert phase 2"
psql_db <<'SQL'
do $$
begin
  if exists (
    select 1 from information_schema.role_table_grants
    where table_schema = 'public'
      and table_name in ('customers','jobs','inspections','field_observations','observation_events','photos','job_photos','audit_log')
      and grantee = 'anon'
  ) then
    raise exception 'phase 2 left anon table grants on a sync relation';
  end if;

  if has_table_privilege('anon', 'storage.objects', 'INSERT')
     or has_table_privilege('anon', 'storage.objects', 'SELECT') then
    raise exception 'phase 2 left anon grants on storage.objects';
  end if;

  if exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'jobs'
      and policyname in ('allow anon all jobs', 'anon_select_jobs', 'testing_full_access')
  ) then
    raise exception 'phase 2 left anon policies on jobs';
  end if;

  if exists (
    select 1 from pg_policies
    where schemaname = 'storage' and tablename = 'objects'
      and policyname in ('observation_photos_select', 'observation_photos_insert')
  ) then
    raise exception 'phase 2 left PUBLIC observation-photos policies';
  end if;

  if not has_table_privilege('authenticated', 'public.customers', 'INSERT') then
    raise exception 'authenticated lost INSERT on customers';
  end if;
end $$;
SQL

echo "== Kotlin-shaped queries after phase 2 (authenticated ok, anon blocked)"
psql_db <<'SQL'
do $$
declare
  uid uuid := gen_random_uuid();
  denied int := 0;
  rec record;
begin
  insert into auth.users (id, email) values (uid, 'tech3@example.com');
  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', uid::text, 'role', 'authenticated')::text,
    false
  );
  set role authenticated;
  insert into public.customers (name) values ('auth-after-hold');
  insert into public.jobs (customer, title, pricing)
    values ('Hold Job', 'Hold Job', '{"totalOverride": 850}'::jsonb);
  insert into public.inspections (notes) values ('after-hold');
  insert into public.field_observations (notes) values ('after-hold');
  insert into public.observation_events (event_id, entity_id, observed_at)
    values ('evt-auth-hold', 'ent-hold', 2);
  insert into public.photos (storage_path) values ('public/hold.jpg');
  insert into public.job_photos (path) values ('public/hold.jpg');
  insert into storage.objects (bucket_id, name)
    values ('observation-photos', 'field/hold/hold.jpg');
  insert into storage.objects (bucket_id, name)
    values ('job-photos', 'public/cccccccc-cccc-cccc-cccc-ccccccccccc0/cccccccc-cccc-cccc-cccc-ccccccccccc1.jpg');
  reset role;

  for rec in
    select * from (values
      ('customers', $s$insert into public.customers (name) values ('anon-after-hold')$s$),
      ('photos', $s$insert into public.photos (storage_path) values ('anon.jpg')$s$),
      ('job_photos', $s$insert into public.job_photos (path) values ('anon.jpg')$s$),
      ('job-photos storage', $s$insert into storage.objects (bucket_id, name) values ('job-photos', 'public/x.jpg')$s$),
      ('jobs', $s$insert into public.jobs (customer, title) values ('x', 'x')$s$),
      ('inspections', $s$insert into public.inspections (notes) values ('x')$s$),
      ('field_observations', $s$insert into public.field_observations (notes) values ('x')$s$),
      ('observation_events', $s$insert into public.observation_events (event_id, entity_id, observed_at) values ('x', 'y', 3)$s$),
      ('storage.objects', $s$insert into storage.objects (bucket_id, name) values ('observation-photos', 'x.jpg')$s$)
    ) as t(rel, stmt)
  loop
    perform set_config('request.jwt.claims', json_build_object('role', 'anon')::text, false);
    set role anon;
    begin
      execute rec.stmt;
      raise exception 'anon DML on % succeeded after phase 2', rec.rel;
    exception
      when insufficient_privilege then
        denied := denied + 1;
        raise notice 'anon denied on % after phase 2 (privilege)', rec.rel;
      when others then
        if sqlerrm like '%succeeded after%' then
          raise;
        end if;
        denied := denied + 1;
        raise notice 'anon denied on % after phase 2 (%)', rec.rel, sqlerrm;
    end;
    reset role;
  end loop;

  if denied < 8 then
    raise exception 'expected anon denies on remaining sync relations, got %', denied;
  end if;
end $$;
SQL

echo "OK: live-restore permissions match the two-step rollout (phase 1 idempotent)."
