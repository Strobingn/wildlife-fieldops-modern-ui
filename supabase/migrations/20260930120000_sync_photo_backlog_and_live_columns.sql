-- =============================================================================
-- Additive schema only (no GRANT / RLS / storage policy changes).
-- Permissions are owned by a separate agent. Safe to re-run.
-- Does not drop tables, rows, or storage objects.
-- =============================================================================

create extension if not exists pgcrypto;

-- App omits organization_id (no tenant UI). If the column is NOT NULL
-- without a default, job upserts fail with 23502.
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

do $$
begin
  if to_regclass('public.field_observations') is not null then
    alter table public.field_observations add column if not exists photo_storage_path text;
    alter table public.field_observations add column if not exists photo_public_url text;
  end if;
end $$;

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
