#!/usr/bin/env bash
# Local Postgres check for authenticated RLS + the deferred anon revoke.
# Does not touch live wildlife_app. Requires Docker (preferred) or psql.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MIG="$ROOT/supabase/migrations"

need_cmd() {
  command -v "$1" >/dev/null 2>&1
}

run_psql() {
  local url="$1"
  shift
  psql "$url" -v ON_ERROR_STOP=1 "$@"
}

verify() {
  local url="$1"

  run_psql "$url" <<'SQL'
create extension if not exists pgcrypto;

do $$ begin create role anon nologin; exception when duplicate_object then null; end $$;
do $$ begin create role authenticated nologin; exception when duplicate_object then null; end $$;
do $$ begin create role service_role nologin bypassrls; exception when duplicate_object then null; end $$;

revoke all on schema public from public;
grant usage on schema public to anon, authenticated, service_role, postgres;

create schema if not exists auth;
create table if not exists auth.users (
  id uuid primary key default gen_random_uuid(),
  email text
);
create or replace function auth.jwt() returns jsonb language sql stable as $$
  select coalesce(nullif(current_setting('request.jwt.claims', true), ''), '{}')::jsonb
$$;
create or replace function auth.uid() returns uuid language sql stable as $$
  select nullif(auth.jwt()->>'sub', '')::uuid
$$;
create or replace function auth.role() returns text language sql stable as $$
  select coalesce(auth.jwt()->>'role', current_user::text)
$$;
grant usage on schema auth to anon, authenticated, service_role;
grant execute on all functions in schema auth to anon, authenticated, service_role;

create table if not exists public.customers (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  created_at timestamptz not null default now()
);
create table if not exists public.jobs (
  id uuid primary key default gen_random_uuid(),
  customer text not null default 'Customer',
  address text not null default '',
  status text not null default 'Active',
  ai_notes text,
  created_at timestamptz not null default now()
);
create table if not exists public.inspections (
  id uuid primary key default gen_random_uuid(),
  job_id uuid,
  created_at timestamptz not null default now()
);
create table if not exists public.field_observations (
  id uuid primary key default gen_random_uuid(),
  notes text not null default '',
  created_at timestamptz not null default now()
);
create table if not exists public.observation_events (
  event_id text primary key,
  entity_id text not null,
  observed_at bigint not null default 0,
  uploaded_at bigint not null default 0
);
create table if not exists public.audit_log (
  id uuid primary key default gen_random_uuid(),
  table_name text,
  record_id text,
  action text,
  old_data jsonb,
  new_data jsonb,
  changed_by text
);

-- Live extras used to prove step 1 closes non-sync tables immediately.
create table if not exists public.invoices (
  id text primary key,
  customer_name text not null default ''
);
create table if not exists public.payment_records (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null default gen_random_uuid(),
  amount numeric(12,2) not null default 0
);
create table if not exists public.organizations (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  slug text not null unique
);
create table if not exists public.organization_members (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id),
  user_id uuid,
  email text,
  role text not null default 'technician',
  active boolean not null default true
);
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text,
  name text,
  role text not null default 'technician'
);

create table if not exists public.photos (
  id uuid primary key default gen_random_uuid(),
  job_id uuid,
  image_url text,
  storage_path text,
  tag text,
  created_at timestamptz not null default now()
);
create table if not exists public.job_photos (
  id uuid primary key default gen_random_uuid(),
  job_id uuid,
  path text not null default '',
  public_url text,
  notes text,
  created_at timestamptz not null default now()
);

create or replace function public.is_org_member(org uuid)
returns boolean
language sql
stable
security definer
set search_path to public
as $$
  select exists (
    select 1 from public.organization_members m
    where m.organization_id = org and m.user_id = auth.uid() and m.active
  )
$$;

create or replace function public.has_org_role(org uuid, roles text[])
returns boolean
language sql
stable
security definer
set search_path to public
as $$
  select exists (
    select 1 from public.organization_members m
    where m.organization_id = org and m.user_id = auth.uid()
      and m.active and m.role = any (roles)
  )
$$;

create or replace function public.queue_campaign(p_campaign_id uuid)
returns integer language sql as $$ select 0 $$;

create or replace function public.generate_due_recurring_jobs(p_through date default current_date)
returns integer language sql as $$ select 0 $$;

create or replace function public.refresh_technician_metrics(p_from date, p_to date)
returns integer language sql as $$ select 0 $$;

create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create or replace function public.touch_integration_connection()
returns trigger language plpgsql as $$
begin
  return new;
end;
$$;

alter table public.customers enable row level security;
alter table public.jobs enable row level security;
alter table public.inspections enable row level security;
alter table public.field_observations enable row level security;
alter table public.observation_events enable row level security;
alter table public.audit_log enable row level security;
alter table public.invoices enable row level security;
alter table public.payment_records enable row level security;
alter table public.organizations enable row level security;
alter table public.organization_members enable row level security;
alter table public.profiles enable row level security;

alter table public.photos enable row level security;
alter table public.job_photos enable row level security;

-- Simulate live: ALL grants + testing_full_access, helpers not executable.
grant all on all tables in schema public to anon, authenticated, service_role;
grant usage, select on all sequences in schema public to anon, authenticated, service_role;
grant execute on function public.queue_campaign(uuid) to public, anon, authenticated;
grant execute on function public.generate_due_recurring_jobs(date) to public, anon, authenticated;
grant execute on function public.refresh_technician_metrics(date, date) to public, anon, authenticated;
revoke execute on function public.is_org_member(uuid) from public, anon, authenticated;
revoke execute on function public.has_org_role(uuid, text[]) from public, anon, authenticated;

create policy "open_customers" on public.customers for all using (true) with check (true);
create policy "open_jobs" on public.jobs for all using (true) with check (true);
create policy "open_inspections" on public.inspections for all using (true) with check (true);
create policy "open_field_observations" on public.field_observations for all using (true) with check (true);
create policy "open_observation_events_select" on public.observation_events for select using (true);
create policy "open_observation_events_insert" on public.observation_events for insert with check (true);
create policy "audit_log_insert" on public.audit_log for insert with check (true);
create policy "testing_full_access" on public.invoices for all to anon, authenticated using (true) with check (true);
create policy "testing_full_access" on public.payment_records for all to anon, authenticated using (true) with check (true);
create policy "testing_full_access" on public.organizations for all to anon, authenticated using (true) with check (true);
create policy "testing_full_access" on public.organization_members for all to anon, authenticated using (true) with check (true);
create policy "testing_full_access" on public.photos for all to anon, authenticated using (true) with check (true);
create policy "testing_full_access" on public.job_photos for all to anon, authenticated using (true) with check (true);
create policy "authenticated profiles access" on public.profiles for all to public
  using ((select auth.role()) = 'authenticated')
  with check ((select auth.role()) = 'authenticated');
create policy "members_manage" on public.organization_members for all to public
  using (has_org_role(organization_id, array['owner','admin']))
  with check (has_org_role(organization_id, array['owner','admin']));
create policy "members_select" on public.organization_members for select to public
  using (is_org_member(organization_id));

create schema if not exists storage;
create table if not exists storage.buckets (
  id text primary key,
  name text,
  public boolean default false
);
create table if not exists storage.objects (
  id uuid primary key default gen_random_uuid(),
  bucket_id text,
  name text
);
insert into storage.buckets(id, name, public) values
  ('observation-photos', 'observation-photos', true),
  ('job-photos', 'job-photos', true),
  ('job-pdfs', 'job-pdfs', true),
  ('fieldops-photos', 'fieldops-photos', false)
on conflict (id) do nothing;
alter table storage.objects enable row level security;
grant usage on schema storage to anon, authenticated, service_role;
grant select, insert, update, delete on storage.objects to anon, authenticated, service_role;
create or replace function storage.foldername(name text) returns text[] language sql immutable as $$
  select (string_to_array(name, '/'))[1:greatest(array_length(string_to_array(name, '/'), 1) - 1, 0)]
$$;
create or replace function storage.extension(name text) returns text language sql immutable as $$
  select lower(reverse(split_part(reverse(name), '.', 1)))
$$;
grant execute on function storage.foldername(text) to anon, authenticated, service_role;
grant execute on function storage.extension(text) to anon, authenticated, service_role;
create policy "observation_photos_select" on storage.objects for select using (bucket_id = 'observation-photos');
create policy "observation_photos_insert" on storage.objects for insert with check (bucket_id = 'observation-photos');
create policy "observation_photos_update" on storage.objects for update using (bucket_id = 'observation-photos') with check (bucket_id = 'observation-photos');
create policy "observation_photos_delete" on storage.objects for delete using (bucket_id = 'observation-photos');
create policy "fieldops_photos_access" on storage.objects for all to anon, authenticated
  using (bucket_id = any (array['fieldops-photos'::text, 'fieldops-documents'::text, 'fieldops-signatures'::text]))
  with check (bucket_id = any (array['fieldops-photos'::text, 'fieldops-documents'::text, 'fieldops-signatures'::text]));
SQL

  echo "== apply authenticated RLS migration (anon grants kept on hold tables)"
  run_psql "$url" -f "$MIG/20260929220000_authenticated_rls_for_signed_in_sync.sql"

  echo "== re-apply phase 1 (idempotence)"
  run_psql "$url" -f "$MIG/20260929220000_authenticated_rls_for_signed_in_sync.sql"

  echo "== stage 1: anon and authenticated can insert hold tables"
  run_psql "$url" <<'SQL'
do $$
declare
  uid uuid := gen_random_uuid();
begin
  insert into auth.users (id, email) values (uid, 'auth-tech@example.com');
  perform set_config(
    'request.jwt.claims',
    json_build_object('sub', uid::text, 'role', 'authenticated')::text,
    false
  );
end $$;
set role authenticated;
insert into public.customers (name) values ('auth-tech');
insert into public.jobs (customer, address) values ('Auth Job', '210 Willow Avenue');
insert into public.inspections default values;
insert into public.field_observations (notes) values ('raccoon');
insert into public.observation_events (event_id, entity_id) values ('evt-auth', 'ent-1');
insert into storage.objects (bucket_id, name) values ('observation-photos', 'auth.jpg');
insert into public.photos (storage_path) values ('jobs/1.jpg');
insert into public.job_photos (path) values ('jobs/1.jpg');
insert into storage.objects (bucket_id, name) values ('job-photos', 'jobs/1.jpg');
reset role;

-- PostgREST anon JWT. Do not leave the authenticated claims in this session:
-- auth.role() is coalesce(jwt->>'role', current_user), and live job-photos
-- JPG policies require auth.role() = 'anon'.
select set_config('request.jwt.claims', json_build_object('role', 'anon')::text, false);
set role anon;
insert into public.customers (name) values ('anon-old-apk');
update public.customers set name = 'anon-old-apk-upsert' where name = 'anon-old-apk';
insert into public.jobs (customer, ai_notes)
  values ('anon-job', 'AI: raccoon');
update public.jobs
   set status = 'Closed',
       ai_notes = 'AI: attic',
       pricing = '{"totalOverride": 200}'::jsonb
 where customer = 'anon-job';
insert into public.inspections default values;
insert into public.field_observations (notes) values ('anon-obs');
insert into public.observation_events (event_id, entity_id) values ('evt-anon', 'ent-anon');
insert into storage.objects (bucket_id, name) values ('observation-photos', 'anon.jpg');
insert into public.photos (id, image_url, storage_path, tag)
  values (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
    'https://example.test/p.jpg',
    'public/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa0/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1.jpg',
    'live_capture'
  )
  on conflict (id) do update set storage_path = excluded.storage_path, tag = excluded.tag;
insert into public.job_photos (id, path, public_url, notes)
  values (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
    'public/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa0/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1.jpg',
    'https://example.test/p.jpg',
    'Live Capture'
  )
  on conflict (id) do update set path = excluded.path, notes = excluded.notes;
insert into storage.objects (bucket_id, name)
  values (
    'job-photos',
    'public/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa0/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1.jpg'
  );
update storage.objects
   set name = name
 where bucket_id = 'job-photos'
   and name = 'public/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa0/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1.jpg';
select 1 from storage.objects
 where bucket_id = 'job-photos'
   and name = 'public/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa0/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1.jpg';
reset role;
select set_config('request.jwt.claims', '{}', false);
SQL

  echo "== stage 1b: anon denied on non-hold tables; no self-promotion"
  run_psql "$url" <<'SQL'
do $$
declare
  denied int := 0;
  rec record;
  uid uuid := gen_random_uuid();
  org uuid;
begin
  insert into auth.users (id, email) values (uid, 'tech@example.com');
  insert into public.organizations (name, slug) values ('Wildlife Whisperer', 'ww-llc') returning id into org;

  for rec in
    select * from (values
      ('invoices', 'insert into public.invoices (id) values (''inv-anon'')'),
      ('payment_records', 'insert into public.payment_records default values'),
      ('organizations', format('insert into public.organizations (name, slug) values (''x'', ''x-%s'')', uid)),
      ('organization_members', format(
        'insert into public.organization_members (organization_id, user_id, role) values (%L::uuid, %L::uuid, ''owner'')',
        org, uid
      )),
      ('fieldops storage', 'insert into storage.objects (bucket_id, name) values (''fieldops-photos'', ''secret.pdf'')'),
      ('job-photos outside public/', 'insert into storage.objects (bucket_id, name) values (''job-photos'', ''not-public/x.jpg'')'),
      ('queue_campaign', 'select public.queue_campaign(gen_random_uuid())'),
      ('generate_due_recurring_jobs', 'select public.generate_due_recurring_jobs(current_date)')
    ) as t(rel, stmt)
  loop
    perform set_config('request.jwt.claims', json_build_object('role', 'anon')::text, false);
    set role anon;
    begin
      execute rec.stmt;
      raise exception 'anon DML on % succeeded after step 1', rec.rel;
    exception
      when insufficient_privilege or invalid_grant_operation then
        denied := denied + 1;
        raise notice 'anon denied on % (privilege)', rec.rel;
      when others then
        if sqlerrm like '%succeeded after%' then
          raise;
        end if;
        denied := denied + 1;
        raise notice 'anon denied on % (%)', rec.rel, sqlerrm;
    end;
    reset role;
  end loop;

  perform set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, false);
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
    insert into public.profiles (id, email, role) values (uid, 'tech@example.com', 'owner');
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

  if denied < 8 then
    raise exception 'expected anon denies on non-hold relations, got %', denied;
  end if;

  -- Helpers must be executable so org policies filter instead of erroring.
  set role authenticated;
  perform public.is_org_member(org);
  perform public.has_org_role(org, array['owner']);
  reset role;
end $$;
SQL

  echo "== apply HOLD revoke migration"
  run_psql "$url" -f "$MIG/20260929221000_REVOKE_ANON_AFTER_SIGNED_IN_ROLLOUT.sql"

  echo "== re-run 28120000 (must not restore anon)"
  run_psql "$url" -f "$MIG/20260928120000_explicit_data_api_grants.sql"

  echo "== stage 2: authenticated allowed; anon denied"
  run_psql "$url" <<'SQL'
select set_config(
  'request.jwt.claims',
  json_build_object('role', 'authenticated')::text,
  false
);
set role authenticated;
insert into public.customers (name) values ('auth-after-revoke');
insert into public.jobs (customer, address, pricing)
  values ('Auth Job', '210 Willow Avenue', '{"totalOverride": 850}'::jsonb);
insert into public.inspections default values;
insert into public.field_observations (notes) values ('after-revoke');
insert into public.observation_events (event_id, entity_id) values ('evt-auth-2', 'ent-2');
insert into public.audit_log (table_name, action) values ('customers', 'INSERT');
insert into storage.objects (bucket_id, name) values ('observation-photos', 'auth-2.jpg');
insert into public.photos (storage_path) values ('public/after-revoke.jpg');
insert into public.job_photos (path) values ('public/after-revoke.jpg');
insert into storage.objects (bucket_id, name)
  values ('job-photos', 'public/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb0/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1.jpg');
reset role;
select set_config('request.jwt.claims', '{}', false);
SQL

  run_psql "$url" <<'SQL'
do $$
declare
  denied int := 0;
  rec record;
begin
  for rec in
    select * from (values
      ('customers', 'insert into public.customers (name) values (''x'')'),
      ('jobs', 'insert into public.jobs (customer) values (''x'')'),
      ('inspections', 'insert into public.inspections default values'),
      ('field_observations', 'insert into public.field_observations (notes) values (''x'')'),
      ('observation_events', 'insert into public.observation_events (event_id, entity_id) values (''x'', ''y'')'),
      ('audit_log', 'insert into public.audit_log (table_name, action) values (''x'', ''INSERT'')'),
      ('photos', 'insert into public.photos (storage_path) values (''x.jpg'')'),
      ('job_photos', 'insert into public.job_photos (path) values (''x.jpg'')'),
      ('storage.objects', 'insert into storage.objects (bucket_id, name) values (''observation-photos'', ''x.jpg'')'),
      ('job-photos', 'insert into storage.objects (bucket_id, name) values (''job-photos'', ''public/x.jpg'')')
    ) as t(rel, stmt)
  loop
    perform set_config('request.jwt.claims', json_build_object('role', 'anon')::text, false);
    set role anon;
    begin
      execute rec.stmt;
      raise exception 'anon DML on % succeeded after revoke', rec.rel;
    exception
      when insufficient_privilege then
        denied := denied + 1;
        raise notice 'anon denied on % (privilege)', rec.rel;
      when others then
        if sqlerrm like '%succeeded after%' then
          raise;
        end if;
        denied := denied + 1;
        raise notice 'anon denied on % (%)', rec.rel, sqlerrm;
    end;
    reset role;
  end loop;
  if denied < 10 then
    raise exception 'expected anon denies on all sync relations, got %', denied;
  end if;
end $$;
SQL

  echo "OK: step 1 keeps hold-table anon, closes the rest; step 2 blocks remaining anon."
}

start_local_postgres() {
  if sudo -u postgres pg_isready -q 2>/dev/null; then
    return 0
  fi
  if need_cmd pg_ctlcluster; then
    sudo pg_ctlcluster 16 main start || sudo pg_ctlcluster 16 main restart || true
  elif need_cmd pg_ctl; then
    sudo -u postgres pg_ctl -D /var/lib/postgresql/16/main -l /tmp/pg.log start || true
  fi
  sudo -u postgres pg_isready -q
}

if need_cmd docker; then
  NAME="fieldops-auth-rls-$$"
  echo "Starting ephemeral Postgres 16 ($NAME)"
  docker run --rm -d --name "$NAME" -e POSTGRES_PASSWORD=postgres -p 55432:5432 postgres:16-alpine >/dev/null
  cleanup() { docker stop "$NAME" >/dev/null 2>&1 || true; }
  trap cleanup EXIT
  for i in $(seq 1 40); do
    if docker exec "$NAME" pg_isready -U postgres >/dev/null 2>&1; then break; fi
    sleep 1
  done
  export PGPASSWORD=postgres
  verify "postgres://postgres:postgres@127.0.0.1:55432/postgres"
elif need_cmd psql; then
  start_local_postgres
  if [ -n "${DATABASE_URL:-}" ]; then
    verify "$DATABASE_URL"
  else
    DB="fieldops_auth_rls_$$"
    sudo -u postgres createdb "$DB"
    cleanup_db() { sudo -u postgres dropdb --if-exists "$DB" >/dev/null 2>&1 || true; }
    trap cleanup_db EXIT
    run_psql() {
      shift
      sudo -u postgres psql -d "$DB" -v ON_ERROR_STOP=1 "$@"
    }
    verify "peer:$DB"
  fi
else
  echo "SKIP: neither docker nor psql is available"
  exit 0
fi
