-- Northeast nuisance-wildlife reference library.
-- Photos are loaded by scripts/seed-wildlife-reference.mjs (service role), not by the app.
-- Owner corrections are copied in by scripts/promote-wildlife-corrections.mjs.
-- Anon and authenticated have no grants. The ai-assistant function reads nearest
-- neighbors with the service role. Apply this file, then redeploy functions/ai-assistant.
-- Safe to re-run.
-- verify_jwt on ai-assistant stays true.

create extension if not exists vector with schema extensions;

create table if not exists public.wildlife_reference (
  id uuid primary key default gen_random_uuid(),
  source text not null,
  source_photo_id text not null,
  species text not null,
  common_name text not null,
  field_label text not null,
  taxon_id integer,
  latitude double precision,
  longitude double precision,
  observed_on date,
  license text not null,
  attribution text not null,
  source_url text not null default '',
  storage_path text not null default '',
  image_url text not null default '',
  sign_type text not null default 'organism',
  description text not null default '',
  embedding extensions.vector(768),
  created_at timestamptz not null default now(),
  constraint wildlife_reference_source_check
    check (source in ('inaturalist', 'field_correction')),
  constraint wildlife_reference_sign_type_check
    check (sign_type in ('organism', 'track', 'scat')),
  constraint wildlife_reference_license_check
    check (char_length(license) > 0),
  constraint wildlife_reference_attribution_check
    check (char_length(attribution) > 0),
  constraint wildlife_reference_source_photo_unique
    unique (source, source_photo_id)
);

create index if not exists wildlife_reference_taxon_idx
  on public.wildlife_reference (taxon_id);
create index if not exists wildlife_reference_common_name_idx
  on public.wildlife_reference (common_name);
create index if not exists wildlife_reference_embedding_hnsw
  on public.wildlife_reference
  using hnsw (embedding extensions.vector_cosine_ops);

alter table public.wildlife_reference enable row level security;

revoke all on table public.wildlife_reference from public, anon, authenticated;
grant select, insert, update, delete on table public.wildlife_reference to service_role;

-- Nearest neighbors for a describe-then-text embedding (cosine distance).
-- security invoker: only service_role can execute, and service_role bypasses RLS.
create or replace function public.match_wildlife_reference(
  query_embedding extensions.vector(768),
  match_count integer default 5
)
returns table (
  id uuid,
  species text,
  common_name text,
  field_label text,
  taxon_id integer,
  license text,
  attribution text,
  source_url text,
  observed_on date,
  sign_type text,
  distance double precision
)
language sql
stable
security invoker
set search_path = public, extensions
as $$
  select
    r.id,
    r.species,
    r.common_name,
    r.field_label,
    r.taxon_id,
    r.license,
    r.attribution,
    r.source_url,
    r.observed_on,
    r.sign_type,
    (r.embedding <=> query_embedding) as distance
  from public.wildlife_reference r
  where r.embedding is not null
  order by r.embedding <=> query_embedding
  limit least(greatest(coalesce(match_count, 5), 1), 8);
$$;

revoke all on function public.match_wildlife_reference(extensions.vector, integer) from public, anon, authenticated;
grant execute on function public.match_wildlife_reference(extensions.vector, integer) to service_role;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'wildlife-reference',
  'wildlife-reference',
  false,
  5242880,
  array['image/jpeg', 'image/png', 'image/webp']::text[]
)
on conflict (id) do update set
  public = excluded.public,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

-- No anon/authenticated storage policies. The seed and promote scripts use the service role,
-- which bypasses storage RLS. The bucket stays private so licensed photos are not a public gallery.
