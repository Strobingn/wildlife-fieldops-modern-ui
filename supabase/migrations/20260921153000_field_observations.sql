-- =============================================================================
-- Offline-first map observations
-- Metadata for tech-logged pins (photo + GPS + note) that Room queues offline
-- and SyncRepository upserts when the device is back online.
-- Safe to re-run.
-- =============================================================================

create extension if not exists pgcrypto;

create table if not exists public.field_observations (
  id uuid primary key default gen_random_uuid(),
  notes text not null default '',
  latitude double precision,
  longitude double precision,
  photo_path text,
  photo_id text,
  job_id uuid,
  species_hint text,
  accuracy_meters double precision,
  observed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_field_observations_observed_at
  on public.field_observations(observed_at desc);
create index if not exists idx_field_observations_job_id
  on public.field_observations(job_id);

do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'field_observations_job_id_fkey'
  ) then
    alter table public.field_observations
      add constraint field_observations_job_id_fkey
      foreign key (job_id) references public.jobs(id) on delete set null;
  end if;
exception when others then
  raise notice 'field_observations_job_id_fkey skipped: %', sqlerrm;
end $$;

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists set_field_observations_updated_at on public.field_observations;
create trigger set_field_observations_updated_at
  before update on public.field_observations
  for each row execute function public.set_updated_at();

alter table public.field_observations enable row level security;

drop policy if exists "field_observations_select" on public.field_observations;
drop policy if exists "field_observations_insert" on public.field_observations;
drop policy if exists "field_observations_update" on public.field_observations;
drop policy if exists "field_observations_delete" on public.field_observations;

create policy "field_observations_select" on public.field_observations for select using (true);
create policy "field_observations_insert" on public.field_observations for insert with check (true);
create policy "field_observations_update" on public.field_observations for update using (true) with check (true);
create policy "field_observations_delete" on public.field_observations for delete using (true);

grant select, insert, update, delete on public.field_observations to anon, authenticated;
