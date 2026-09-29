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
do $$ begin create role service_role nologin; exception when duplicate_object then null; end $$;

revoke all on schema public from public;
grant usage on schema public to anon, authenticated, service_role, postgres;

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

alter table public.customers enable row level security;
alter table public.jobs enable row level security;
alter table public.inspections enable row level security;
alter table public.field_observations enable row level security;
alter table public.observation_events enable row level security;
alter table public.audit_log enable row level security;

-- Simulate PR #58 grants (anon still present until the HOLD revoke file).
grant select, insert, update, delete on public.customers to anon, authenticated, service_role;
grant select, insert, update, delete on public.jobs to anon, authenticated, service_role;
grant select, insert, update, delete on public.inspections to anon, authenticated, service_role;
grant select, insert, update, delete on public.field_observations to anon, authenticated, service_role;
grant select, insert on public.observation_events to anon, authenticated, service_role;
grant insert on public.audit_log to anon, authenticated, service_role;
grant select, update, delete on public.audit_log to service_role;

create policy "open_customers" on public.customers for all using (true) with check (true);
create policy "open_jobs" on public.jobs for all using (true) with check (true);
create policy "open_inspections" on public.inspections for all using (true) with check (true);
create policy "open_field_observations" on public.field_observations for all using (true) with check (true);
create policy "open_observation_events_select" on public.observation_events for select using (true);
create policy "open_observation_events_insert" on public.observation_events for insert with check (true);
create policy "audit_log_insert" on public.audit_log for insert with check (true);

create schema if not exists storage;
create table if not exists storage.objects (
  id uuid primary key default gen_random_uuid(),
  bucket_id text,
  name text
);
alter table storage.objects enable row level security;
grant usage on schema storage to anon, authenticated, service_role;
grant select, insert, update, delete on storage.objects to anon, authenticated, service_role;
create policy "observation_photos_select" on storage.objects for select using (bucket_id = 'observation-photos');
create policy "observation_photos_insert" on storage.objects for insert with check (bucket_id = 'observation-photos');
create policy "observation_photos_update" on storage.objects for update using (bucket_id = 'observation-photos') with check (bucket_id = 'observation-photos');
create policy "observation_photos_delete" on storage.objects for delete using (bucket_id = 'observation-photos');
SQL

  echo "== apply authenticated RLS migration (anon grants kept)"
  run_psql "$url" -f "$MIG/20260929220000_authenticated_rls_for_signed_in_sync.sql"

  echo "== stage 1: anon and authenticated can insert"
  run_psql "$url" <<'SQL'
set role authenticated;
insert into public.customers (name) values ('auth-tech');
insert into public.field_observations (notes) values ('raccoon');
insert into public.observation_events (event_id, entity_id) values ('evt-auth', 'ent-1');
insert into storage.objects (bucket_id, name) values ('observation-photos', 'auth.jpg');
reset role;

set role anon;
insert into public.customers (name) values ('anon-old-apk');
insert into storage.objects (bucket_id, name) values ('observation-photos', 'anon.jpg');
reset role;
SQL

  echo "== apply HOLD revoke migration"
  run_psql "$url" -f "$MIG/20260929221000_REVOKE_ANON_AFTER_SIGNED_IN_ROLLOUT.sql"

  echo "== stage 2: authenticated allowed; anon denied"
  run_psql "$url" <<'SQL'
set role authenticated;
insert into public.customers (name) values ('auth-after-revoke');
insert into public.jobs (customer, address) values ('Auth Job', '210 Willow Avenue');
insert into public.inspections default values;
insert into public.field_observations (notes) values ('after-revoke');
insert into public.observation_events (event_id, entity_id) values ('evt-auth-2', 'ent-2');
insert into public.audit_log (table_name, action) values ('customers', 'INSERT');
insert into storage.objects (bucket_id, name) values ('observation-photos', 'auth-2.jpg');
reset role;
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
      ('storage.objects', 'insert into storage.objects (bucket_id, name) values (''observation-photos'', ''x.jpg'')')
    ) as t(rel, stmt)
  loop
    set role anon;
    begin
      execute rec.stmt;
      raise exception 'anon DML on % succeeded after revoke', rec.rel;
    exception
      when insufficient_privilege then
        denied := denied + 1;
        raise notice 'anon denied on % (privilege)', rec.rel;
      when others then
        denied := denied + 1;
        raise notice 'anon denied on % (%)', rec.rel, sqlerrm;
    end;
    reset role;
  end loop;
  if denied < 6 then
    raise exception 'expected anon denies on all sync relations, got %', denied;
  end if;
end $$;
SQL

  echo "OK: authenticated allowed after revoke; anon blocked on tables + observation-photos."
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
  url="${DATABASE_URL:-postgres://postgres:postgres@127.0.0.1:5432/postgres}"
  verify "$url"
else
  echo "SKIP: neither docker nor psql is available"
  exit 0
fi
