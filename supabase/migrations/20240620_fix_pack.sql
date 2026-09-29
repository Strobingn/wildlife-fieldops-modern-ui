-- =============================================================================
-- Wildlife FieldOps Fix Pack
-- Generated: June 20, 2026
-- Run this in Supabase SQL Editor to enable inspections + photos
-- =============================================================================

-- --- PHOTOS TABLE ------------------------------------------------------------

create table if not exists photos (
    id          text primary key,
    job_id      text not null,
    storage_path text,
    public_url  text,
    tag         text default '',
    notes       text default '',
    file_size   integer,
    content_type text,
    created_at  timestamptz default now()
);

-- Enable RLS on photos
alter table if exists photos enable row level security;

-- Photos policies (drop first to avoid conflicts)
drop policy if exists "Allow authenticated access to photos" on photos;

create policy "Allow authenticated access to photos"
    on photos for all
    to authenticated
    using (true)
    with check (true);

-- Data API (PostgREST) table privileges. Native Android sync does not use
-- public.photos; leftover web/Capacitor code did. No anon grant.
grant select, insert, update, delete on public.photos to authenticated, service_role;

-- Index for fast job-based lookups
create index if not exists idx_photos_job_id on photos(job_id);

-- --- INSPECTIONS TABLE -------------------------------------------------------

create table if not exists inspections (
    id              text primary key,
    customer_name   text not null,
    phone           text,
    address         text,
    town            text,
    species         text,
    priority        text default 'Normal',
    status          text default 'scheduled',
    scheduled_date  timestamptz,
    notes           text,
    job_id          text,
    created_at      timestamptz default now(),
    updated_at      timestamptz default now()
);

-- Enable RLS on inspections
alter table if exists inspections enable row level security;

-- Inspections policies (drop first to avoid conflicts)
drop policy if exists "Allow authenticated access to inspections" on inspections;
drop policy if exists "Allow anon access to inspections" on inspections;

create policy "Allow authenticated access to inspections"
    on inspections for all
    to authenticated
    using (true)
    with check (true);

-- Native FieldOps syncs inspections with the anon key (no user session).
create policy "Allow anon access to inspections"
    on inspections for all
    to anon
    using (true)
    with check (true);

grant select, insert, update, delete on public.inspections to anon, authenticated, service_role;

-- Indexes for common queries
create index if not exists idx_inspections_status on inspections(status);
create index if not exists idx_inspections_scheduled_date on inspections(scheduled_date);
create index if not exists idx_inspections_job_id on inspections(job_id);

-- --- STORAGE BUCKET SETUP ----------------------------------------------------
-- NOTE: Create the "job-photos" bucket manually in Supabase Storage UI:
-- 1. Go to Storage → Buckets
-- 2. Click "New Bucket"
-- 3. Name: job-photos
-- 4. Check "Public bucket" (for testing)
-- 5. Click "Save"
--
-- Then the policies below will work:

drop policy if exists "Allow authenticated uploads" on storage.objects;
drop policy if exists "Allow authenticated reads" on storage.objects;
drop policy if exists "Allow authenticated deletes" on storage.objects;

create policy "Allow authenticated uploads"
    on storage.objects for insert
    to authenticated
    with check (bucket_id = 'job-photos');

create policy "Allow authenticated reads"
    on storage.objects for select
    to authenticated
    using (bucket_id = 'job-photos');

create policy "Allow authenticated deletes"
    on storage.objects for delete
    to authenticated
    using (bucket_id = 'job-photos');

-- =============================================================================
-- VERIFICATION QUERIES (run these to confirm everything worked)
-- =============================================================================

-- Check inspections table
select 'inspections table' as check, count(*) as column_count
from information_schema.columns
where table_name = 'inspections';

-- Check photos table
select 'photos table' as check, count(*) as column_count
from information_schema.columns
where table_name = 'photos';
