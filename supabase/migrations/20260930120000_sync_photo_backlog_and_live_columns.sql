-- =============================================================================
-- Sync backlog fix — photos + live jobs columns the native app now writes
-- Safe to re-run. Does not drop tables, rows, or storage objects.
--
-- Owner: paste this file in the wildlife_app SQL Editor if the migration
-- runner has not applied it. Do not connect the app to a different project.
-- =============================================================================

create extension if not exists pgcrypto;

-- Jobs: app omits organization_id (no tenant UI). If the column is NOT NULL
-- without a default, upserts fail with 23502 and nothing new lands.
do $$
begin
  if exists (
    select 1
    from information_schema.columns
    where table_schema = 'public'
      and table_name = 'jobs'
      and column_name = 'organization_id'
  ) then
    begin
      alter table public.jobs alter column organization_id drop not null;
    exception when others then
      raise notice 'jobs.organization_id nullability skipped: %', sqlerrm;
    end;
  end if;
end $$;

-- Helpful defaults so omitted text fields still insert
do $$
begin
  if to_regclass('public.jobs') is not null then
    begin
      alter table public.jobs alter column address set default '';
    exception when others then
      raise notice 'jobs.address default skipped: %', sqlerrm;
    end;
    begin
      alter table public.jobs alter column customer set default 'Customer';
    exception when others then
      raise notice 'jobs.customer default skipped: %', sqlerrm;
    end;
  end if;
end $$;

-- Photos metadata (job + Live Capture stills)
create table if not exists public.photos (
  id uuid primary key default gen_random_uuid(),
  job_id uuid references public.jobs(id) on delete cascade,
  image_url text,
  public_url text,
  storage_path text,
  tag text,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.photos add column if not exists image_url text;
alter table public.photos add column if not exists public_url text;
alter table public.photos add column if not exists storage_path text;
alter table public.photos add column if not exists tag text;
alter table public.photos add column if not exists notes text;

-- Legacy dashboard table (cloudflare stub / older web). Best-effort dual-write.
create table if not exists public.job_photos (
  id uuid primary key default gen_random_uuid(),
  job_id uuid references public.jobs(id) on delete cascade,
  path text,
  storage_path text,
  public_url text,
  tag text,
  notes text,
  created_at timestamptz not null default now()
);

alter table public.job_photos add column if not exists path text;
alter table public.job_photos add column if not exists storage_path text;
alter table public.job_photos add column if not exists public_url text;
alter table public.job_photos add column if not exists tag text;
alter table public.job_photos add column if not exists notes text;

-- Field observation photo URL columns (PR #51). Idempotent if already applied.
do $$
begin
  if to_regclass('public.field_observations') is not null then
    alter table public.field_observations add column if not exists photo_storage_path text;
    alter table public.field_observations add column if not exists photo_public_url text;
  end if;
end $$;

-- Storage bucket for job / Live Capture photos (already exists live; upsert)
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'job-photos',
  'job-photos',
  true,
  52428800,
  array['image/jpeg', 'image/png', 'image/webp', 'image/heic']::text[]
)
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

-- Native client uses the anon key with no sign-in (PR #59 is not merged).
drop policy if exists "job_photos_select" on storage.objects;
drop policy if exists "job_photos_insert" on storage.objects;
drop policy if exists "job_photos_update" on storage.objects;

create policy "job_photos_select"
  on storage.objects
  for select
  using (bucket_id = 'job-photos');

create policy "job_photos_insert"
  on storage.objects
  for insert
  with check (bucket_id = 'job-photos');

create policy "job_photos_update"
  on storage.objects
  for update
  using (bucket_id = 'job-photos')
  with check (bucket_id = 'job-photos');

alter table public.photos enable row level security;
alter table public.job_photos enable row level security;

drop policy if exists "photos_select" on public.photos;
drop policy if exists "photos_insert" on public.photos;
drop policy if exists "photos_update" on public.photos;
create policy "photos_select" on public.photos for select using (true);
create policy "photos_insert" on public.photos for insert with check (true);
create policy "photos_update" on public.photos for update using (true) with check (true);

drop policy if exists "job_photos_table_select" on public.job_photos;
drop policy if exists "job_photos_table_insert" on public.job_photos;
drop policy if exists "job_photos_table_update" on public.job_photos;
create policy "job_photos_table_select" on public.job_photos for select using (true);
create policy "job_photos_table_insert" on public.job_photos for insert with check (true);
create policy "job_photos_table_update" on public.job_photos for update using (true) with check (true);

grant select, insert, update, delete on public.photos to anon, authenticated, service_role;
grant select, insert, update, delete on public.job_photos to anon, authenticated, service_role;

do $$
begin
  if to_regclass('public.field_observations') is not null then
    execute 'grant select, insert, update, delete on public.field_observations to anon, authenticated, service_role';
  end if;
  if to_regclass('public.observation_events') is not null then
    execute 'grant select, insert on public.observation_events to anon, authenticated, service_role';
  end if;
  if to_regclass('public.jobs') is not null then
    execute 'grant select, insert, update, delete on public.jobs to anon, authenticated, service_role';
  end if;
  if to_regclass('public.customers') is not null then
    execute 'grant select, insert, update, delete on public.customers to anon, authenticated, service_role';
  end if;
  if to_regclass('public.inspections') is not null then
    execute 'grant select, insert, update, delete on public.inspections to anon, authenticated, service_role';
  end if;
end $$;
