-- =============================================================================
-- ObservationEvent persistence + observation-photos storage
-- Matches live wildlife_app (ref hgdzmwfcghtilyqagjak) schema.
-- Safe to re-run. Do not reshape columns; append-only events have no client UPDATE/DELETE.
-- =============================================================================

create table if not exists public.observation_events (
  event_id text primary key,
  entity_id text not null,
  observed_at bigint not null,
  uploaded_at bigint not null default ((extract(epoch from now()) * (1000)::numeric))::bigint,
  device_id text not null default '',
  operator_id text not null default '',
  model_id text not null default '',
  model_hash text not null default '',
  backend_tag text not null default '',
  quantizer_tag text not null default '',
  frame_hash text not null default '',
  crop_hash text not null default '',
  media_uri text,
  media_storage_path text,
  label_distribution jsonb not null default '{}'::jsonb,
  capture_quality double precision not null default 0,
  geometry_trust double precision not null default 0,
  human_verification text not null default 'UNREVIEWED',
  supersedes_event_id text,
  created_at timestamptz not null default now()
);

create index if not exists idx_observation_events_entity_observed
  on public.observation_events (entity_id, observed_at desc);
create index if not exists idx_observation_events_verification
  on public.observation_events (human_verification);
create index if not exists idx_observation_events_supersedes
  on public.observation_events (supersedes_event_id);

alter table public.observation_events enable row level security;

drop policy if exists "observation_events_select" on public.observation_events;
drop policy if exists "observation_events_insert" on public.observation_events;

create policy "observation_events_select"
  on public.observation_events
  for select
  using (true);

create policy "observation_events_insert"
  on public.observation_events
  for insert
  with check (true);

grant select, insert on public.observation_events to anon, authenticated;

alter table public.field_observations
  add column if not exists photo_storage_path text;
alter table public.field_observations
  add column if not exists photo_public_url text;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'observation-photos',
  'observation-photos',
  true,
  52428800,
  array['image/jpeg', 'image/png', 'image/webp', 'image/heic']::text[]
)
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "observation_photos_select" on storage.objects;
drop policy if exists "observation_photos_insert" on storage.objects;
drop policy if exists "observation_photos_update" on storage.objects;
drop policy if exists "observation_photos_delete" on storage.objects;

create policy "observation_photos_select"
  on storage.objects
  for select
  using (bucket_id = 'observation-photos');

create policy "observation_photos_insert"
  on storage.objects
  for insert
  with check (bucket_id = 'observation-photos');

create policy "observation_photos_update"
  on storage.objects
  for update
  using (bucket_id = 'observation-photos')
  with check (bucket_id = 'observation-photos');

create policy "observation_photos_delete"
  on storage.objects
  for delete
  using (bucket_id = 'observation-photos');
