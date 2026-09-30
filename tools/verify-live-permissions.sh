#!/usr/bin/env bash
# Restore the live wildlife_app catalog dump into local Postgres and apply the
# PR #59 permission migrations. Does not touch the live project.
#
# Usage:
#   DUMP_DIR=/path/to/uploads ./tools/verify-live-permissions.sh
#
# Looks for 00_restore_live_schema.sql (hashed suffix ok) plus the numbered
# include files that restore script \i s.
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
sudo -u postgres bash -c "cd '$STAGE' && psql -d '$DB' -v ON_ERROR_STOP=1 -f 00_restore_live_schema.sql"

psql_db() {
  sudo -u postgres psql -d "$DB" -v ON_ERROR_STOP=1 "$@"
}

echo "== apply step 1 (authenticated RLS + immediate lock-down)"
psql_db -f "$MIG/20260929220000_authenticated_rls_for_signed_in_sync.sql"

echo "== assert step 1"
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
    raise exception 'step 1 revoked anon INSERT on customers (old APKs need it)';
  end if;

  select count(*) into anon_invoices
  from information_schema.role_table_grants
  where table_schema = 'public' and table_name = 'invoices'
    and grantee = 'anon';
  if anon_invoices > 0 then
    raise exception 'step 1 left anon grants on invoices';
  end if;

  select has_function_privilege('authenticated', 'public.is_org_member(uuid)', 'EXECUTE')
    into helper_ok;
  if not helper_ok then
    raise exception 'authenticated lacks EXECUTE on is_org_member';
  end if;

  if has_function_privilege('anon', 'public.queue_campaign(uuid)', 'EXECUTE') then
    raise exception 'anon can still EXECUTE queue_campaign';
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

  -- Hold tables still have open policies / grants so old APKs work.
  if not exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'jobs'
      and policyname in ('allow anon all jobs', 'anon_select_jobs', 'open_jobs')
  ) then
    raise exception 'step 1 dropped hold-table anon policies on jobs';
  end if;
end $$;
SQL

echo "== DML smoke (step 1 hold tables vs locked tables)"
psql_db <<'SQL'
set role authenticated;
insert into public.customers (name) values ('auth-live-1');
reset role;

set role anon;
insert into public.customers (name) values ('anon-live-hold');
reset role;

do $$
begin
  set role anon;
  begin
    insert into public.invoices (id) values ('should-fail');
    raise exception 'anon insert invoices succeeded after step 1';
  exception
    when insufficient_privilege then
      raise notice 'anon invoices blocked';
    when others then
      if sqlerrm like '%succeeded%' then raise; end if;
      raise notice 'anon invoices blocked (%)', sqlerrm;
  end;
  reset role;

  set role authenticated;
  perform public.is_org_member('00000000-0000-0000-0000-000000000001'::uuid);
  perform public.has_org_role('00000000-0000-0000-0000-000000000001'::uuid, array['owner']);
  begin
    perform public.queue_campaign('00000000-0000-0000-0000-000000000001'::uuid);
  exception when others then
    -- campaign not found is fine; permission denied is not
    if sqlerrm ilike '%permission denied%' then
      raise;
    end if;
  end;
  reset role;

  set role anon;
  begin
    perform public.queue_campaign('00000000-0000-0000-0000-000000000001'::uuid);
    raise exception 'anon queue_campaign succeeded';
  exception
    when insufficient_privilege then
      raise notice 'anon queue_campaign blocked';
    when others then
      if sqlerrm like '%succeeded%' then raise; end if;
      raise notice 'anon queue_campaign blocked (%)', sqlerrm;
  end;
  reset role;
end $$;
SQL

echo "== apply HOLD revoke"
psql_db -f "$MIG/20260929221000_REVOKE_ANON_AFTER_SIGNED_IN_ROLLOUT.sql"

echo "== assert HOLD"
psql_db <<'SQL'
do $$
begin
  if exists (
    select 1 from information_schema.role_table_grants
    where table_schema = 'public'
      and table_name in ('customers','jobs','inspections','field_observations','observation_events','audit_log')
      and grantee = 'anon'
  ) then
    raise exception 'HOLD left anon table grants on a sync relation';
  end if;

  if exists (
    select 1 from pg_policies
    where schemaname = 'public' and tablename = 'jobs'
      and policyname in ('allow anon all jobs', 'anon_select_jobs', 'testing_full_access')
  ) then
    raise exception 'HOLD left anon policies on jobs';
  end if;

  if exists (
    select 1 from pg_policies
    where schemaname = 'storage' and tablename = 'objects'
      and policyname in ('observation_photos_select', 'observation_photos_insert')
  ) then
    raise exception 'HOLD left PUBLIC observation-photos policies';
  end if;

  if not has_table_privilege('authenticated', 'public.customers', 'INSERT') then
    raise exception 'authenticated lost INSERT on customers';
  end if;
end $$;
SQL

echo "== DML smoke after HOLD"
psql_db <<'SQL'
set role authenticated;
insert into public.customers (name) values ('auth-live-after-hold');
insert into public.jobs (customer, title) values ('Hold Job', 'Hold Job');
reset role;

do $$
begin
  set role anon;
  begin
    insert into public.customers (name) values ('anon-after-hold');
    raise exception 'anon insert customers succeeded after HOLD';
  exception
    when insufficient_privilege then
      raise notice 'anon customers blocked after HOLD';
    when others then
      if sqlerrm like '%succeeded%' then raise; end if;
      raise notice 'anon customers blocked after HOLD (%)', sqlerrm;
  end;
  reset role;
end $$;
SQL

echo "OK: live-restore permissions match the two-step rollout."

