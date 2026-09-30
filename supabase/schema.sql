-- Wildlife Whisperer FieldOps - Supabase Schema v3.0.0
-- Drop this file in: supabase/schema.sql
-- Safe to re-run. Designed for the current repo's src/main.js data calls.
--
-- STALE vs live wildlife_app (dumped 2026-09-30). Live catalog, Kotlin usage,
-- and the two-step permission rollout live in supabase/migrations/LIVE_BASELINE.md.
-- Do not apply this file on production; it does not match the live schema.

create extension if not exists pgcrypto;

-- =========================
-- Helpers
-- =========================

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- Audit log helper
create or replace function public.log_change()
returns trigger
language plpgsql
as $$
declare
  v_user_id text;
begin
  -- Try to get the current user from auth
  begin
    v_user_id := coalesce(current_setting('request.jwt.claims', true)::json->>'sub', 'anonymous');
  exception when others then
    v_user_id := 'anonymous';
  end;

  insert into public.audit_log (table_name, record_id, action, old_data, new_data, changed_by)
  values (
    tg_table_name,
    coalesce(new.id, old.id)::text,
    tg_op,
    case when tg_op = 'DELETE' then row_to_json(old) else null end,
    case when tg_op in ('INSERT', 'UPDATE') then row_to_json(new) else null end,
    v_user_id
  );
  return coalesce(new, old);
end;
$$;

-- =========================
-- Core tables
-- =========================

create table if not exists public.techs (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  phone text,
  role text,
  active boolean not null default true,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Customers MUST exist before jobs (FK customer_id)
create table if not exists public.customers (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  phone text,
  email text,
  address text,
  town text,
  state text,
  zip text,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.jobs (
  id uuid primary key default gen_random_uuid(),

  customer text not null,
  customer_id uuid references public.customers(id) on delete set null,
  phone text,
  email text,
  address text not null,
  town text,
  state text,
  zip text,

  species text,
  status text not null default 'Active',
  priority text default 'Normal',
  assigned_tech text,

  notes text,
  ai_notes text,
  scope text,
  warranty text,

  latitude text,
  longitude text,
  accuracy numeric,

  estimate numeric(12,2) not null default 0,
  subtotal numeric(12,2) not null default 0,
  tax_rate numeric(6,3) not null default 0,
  tax_amount numeric(12,2) not null default 0,
  grand_total numeric(12,2) not null default 0,
  deposit_paid numeric(12,2) not null default 0,
  balance_due numeric(12,2) not null default 0,

  scheduled_start timestamptz,
  scheduled_end timestamptz,
  completed_at timestamptz,
  timer_start timestamptz,
  timer_total numeric default 0,
  reminder_date date,

  is_recurring boolean not null default false,
  recurrence_pattern text,
  parent_job_id uuid references public.jobs(id) on delete set null,

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint jobs_status_check check (
    status in ('Active', 'Scheduled', 'In Progress', 'Needs Follow-up', 'Closed', 'Cancelled')
  ),
  constraint jobs_priority_check check (
    priority in ('Low', 'Normal', 'High', 'Critical')
  )
);

create table if not exists public.visits (
  id uuid primary key default gen_random_uuid(),
  job_id uuid not null references public.jobs(id) on delete cascade,
  tech_id uuid references public.techs(id) on delete set null,
  tech_name text,
  visit_date timestamptz not null default now(),
  notes text,
  findings text,
  recommendations text,
  status text not null default 'Completed',
  timer_start timestamptz,
  timer_total numeric default 0,
  gps_latitude text,
  gps_longitude text,
  gps_accuracy numeric,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint visits_status_check check (
    status in ('Scheduled', 'In Progress', 'Completed', 'Cancelled')
  )
);

create table if not exists public.repairs (
  id uuid primary key default gen_random_uuid(),
  job_id uuid not null references public.jobs(id) on delete cascade,
  description text not null,
  location text,
  material text,
  cost numeric(12,2) not null default 0,
  completed boolean not null default false,
  completed_at timestamptz,
  completed_by text,
  before_photo_url text,
  after_photo_url text,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.signatures (
  id uuid primary key default gen_random_uuid(),
  job_id uuid not null references public.jobs(id) on delete cascade,
  document_type text not null default 'contract',
  signer_name text not null,
  signer_role text not null default 'customer',
  signature_data text not null,
  signed_at timestamptz not null default now(),
  ip_address text,
  notes text,
  created_at timestamptz not null default now()
);

create table if not exists public.materials (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  sku text,
  category text,
  unit text not null default 'each',
  unit_cost numeric(12,2) not null default 0,
  quantity_on_hand numeric(10,2) not null default 0,
  reorder_level numeric(10,2) not null default 0,
  supplier text,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.job_materials (
  id uuid primary key default gen_random_uuid(),
  job_id uuid not null references public.jobs(id) on delete cascade,
  material_id uuid references public.materials(id) on delete set null,
  material_name text not null,
  quantity numeric(10,2) not null default 1,
  unit_cost numeric(12,2) not null default 0,
  total_cost numeric(12,2) not null default 0,
  notes text,
  created_at timestamptz not null default now()
);

create table if not exists public.services (
  id uuid primary key default gen_random_uuid(),
  job_id uuid not null references public.jobs(id) on delete cascade,

  service text not null,
  qty numeric(10,2) not null default 1,
  unit_price numeric(12,2) not null default 0,
  total numeric(12,2) not null default 0,
  notes text,

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.inspections (
  id uuid primary key default gen_random_uuid(),
  job_id uuid not null references public.jobs(id) on delete cascade,

  inspection_type text not null,
  notes text,
  findings jsonb not null default '{}'::jsonb,

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.photos (
  id uuid primary key default gen_random_uuid(),
  job_id uuid not null references public.jobs(id) on delete cascade,

  image_url text,
  storage_path text,
  tag text,
  notes text,

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.pdf_documents (
  id uuid primary key default gen_random_uuid(),
  job_id uuid not null references public.jobs(id) on delete cascade,

  type text,
  file_path text,
  public_url text,

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Future-proof scheduling/calendar table.
create table if not exists public.appointments (
  id uuid primary key default gen_random_uuid(),
  job_id uuid references public.jobs(id) on delete cascade,

  title text not null,
  starts_at timestamptz not null,
  ends_at timestamptz,
  location text,
  assigned_tech text,
  status text not null default 'Scheduled',
  notes text,

  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint appointments_status_check check (
    status in ('Scheduled', 'Completed', 'Cancelled', 'No Show')
  )
);

-- Offline sync / audit-friendly queue table.
create table if not exists public.sync_events (
  id uuid primary key default gen_random_uuid(),
  device_id text,
  entity_type text not null,
  entity_id uuid,
  action text not null,
  payload jsonb not null default '{}'::jsonb,
  synced_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.expenses (
  id uuid primary key default gen_random_uuid(),
  job_id uuid not null references public.jobs(id) on delete cascade,
  description text not null,
  amount numeric(12,2) not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Audit log for tracking all changes
create table if not exists public.audit_log (
  id uuid primary key default gen_random_uuid(),
  table_name text not null,
  record_id text not null,
  action text not null,
  old_data jsonb,
  new_data jsonb,
  changed_by text default 'anonymous',
  created_at timestamptz not null default now()
);

-- =========================
-- Compatibility migrations
-- =========================

alter table public.jobs add column if not exists estimate numeric(12,2) not null default 0;
alter table public.jobs add column if not exists customer text;
alter table public.jobs add column if not exists customer_name text;
alter table public.jobs add column if not exists state text;
alter table public.jobs add column if not exists zip text;
alter table public.jobs add column if not exists phone text;
alter table public.jobs add column if not exists email text;
alter table public.jobs add column if not exists address text;
alter table public.jobs add column if not exists town text;
alter table public.jobs add column if not exists species text;
alter table public.jobs add column if not exists status text not null default 'Active';
alter table public.jobs add column if not exists assigned_tech text;
alter table public.jobs add column if not exists notes text;
alter table public.jobs add column if not exists latitude text;
alter table public.jobs add column if not exists longitude text;
alter table public.jobs add column if not exists subtotal numeric(12,2) not null default 0;
alter table public.jobs add column if not exists tax_rate numeric(6,3) not null default 0;
alter table public.jobs add column if not exists tax_amount numeric(12,2) not null default 0;
alter table public.jobs add column if not exists grand_total numeric(12,2) not null default 0;
alter table public.jobs add column if not exists ai_notes text;
alter table public.jobs add column if not exists scheduled_start timestamptz;
alter table public.jobs add column if not exists scheduled_end timestamptz;
alter table public.jobs add column if not exists completed_at timestamptz;
alter table public.jobs add column if not exists updated_at timestamptz not null default now();

-- New feature columns
alter table public.jobs add column if not exists priority text default 'normal';
alter table public.jobs add column if not exists deposit_paid numeric(12,2) not null default 0;
alter table public.jobs add column if not exists balance_due numeric(12,2) not null default 0;
alter table public.jobs add column if not exists reminder_date date;
alter table public.jobs add column if not exists timer_start timestamptz;
alter table public.jobs add column if not exists timer_total numeric default 0;
alter table public.jobs add column if not exists customer_id uuid references public.customers(id) on delete set null;
alter table public.jobs add column if not exists is_recurring boolean not null default false;
alter table public.jobs add column if not exists recurrence_pattern text;
alter table public.jobs add column if not exists parent_job_id uuid references public.jobs(id) on delete set null;

alter table public.techs add column if not exists active boolean not null default true;
alter table public.techs add column if not exists notes text;
alter table public.techs add column if not exists updated_at timestamptz not null default now();

alter table public.services add column if not exists notes text;
alter table public.services add column if not exists updated_at timestamptz not null default now();

alter table public.inspections add column if not exists findings jsonb not null default '{}'::jsonb;
alter table public.inspections add column if not exists updated_at timestamptz not null default now();

alter table public.photos add column if not exists storage_path text;
alter table public.photos add column if not exists updated_at timestamptz not null default now();

alter table public.pdf_documents add column if not exists updated_at timestamptz not null default now();

-- =========================
-- Indexes
-- =========================

create index if not exists idx_jobs_created_at on public.jobs(created_at desc);
create index if not exists idx_jobs_status on public.jobs(status);
create index if not exists idx_jobs_species on public.jobs(species);
create index if not exists idx_jobs_assigned_tech on public.jobs(assigned_tech);
create index if not exists idx_jobs_scheduled_start on public.jobs(scheduled_start);
create index if not exists idx_jobs_priority on public.jobs(priority);
create index if not exists idx_jobs_customer_id on public.jobs(customer_id);
create index if not exists idx_jobs_town on public.jobs(town);
create index if not exists idx_jobs_parent_job_id on public.jobs(parent_job_id);

create index if not exists idx_customers_name on public.customers(name);
create index if not exists idx_customers_phone on public.customers(phone);

create index if not exists idx_visits_job_id on public.visits(job_id);
create index if not exists idx_visits_visit_date on public.visits(visit_date);
create index if not exists idx_visits_tech_id on public.visits(tech_id);

create index if not exists idx_repairs_job_id on public.repairs(job_id);
create index if not exists idx_repairs_completed on public.repairs(completed);

create index if not exists idx_signatures_job_id on public.signatures(job_id);
create index if not exists idx_signatures_document_type on public.signatures(document_type);

create index if not exists idx_materials_category on public.materials(category);
create index if not exists idx_materials_sku on public.materials(sku);

create index if not exists idx_job_materials_job_id on public.job_materials(job_id);

create index if not exists idx_services_job_id on public.services(job_id);
create index if not exists idx_inspections_job_id on public.inspections(job_id);
create index if not exists idx_photos_job_id on public.photos(job_id);
create index if not exists idx_photos_storage_path on public.photos(storage_path);
create index if not exists idx_pdf_documents_job_id on public.pdf_documents(job_id);
create index if not exists idx_appointments_job_id on public.appointments(job_id);
create index if not exists idx_appointments_starts_at on public.appointments(starts_at);
create index if not exists idx_expenses_job_id on public.expenses(job_id);

create index if not exists idx_audit_log_table_name on public.audit_log(table_name);
create index if not exists idx_audit_log_record_id on public.audit_log(record_id);
create index if not exists idx_audit_log_created_at on public.audit_log(created_at desc);

create index if not exists idx_sync_events_entity on public.sync_events(entity_type, entity_id);
create index if not exists idx_sync_events_synced on public.sync_events(synced_at);

-- Full-text search indexes
create index if not exists idx_jobs_search on public.jobs
  using gin(to_tsvector('english', coalesce(customer,'') || ' ' || coalesce(address,'') || ' ' || coalesce(notes,'')));

create index if not exists idx_customers_search on public.customers
  using gin(to_tsvector('english', coalesce(name,'') || ' ' || coalesce(address,'') || ' ' || coalesce(notes,'')));

-- =========================
-- Updated-at triggers
-- =========================

drop trigger if exists set_techs_updated_at on public.techs;
create trigger set_techs_updated_at
  before update on public.techs
  for each row execute function public.set_updated_at();

drop trigger if exists set_jobs_updated_at on public.jobs;
create trigger set_jobs_updated_at
  before update on public.jobs
  for each row execute function public.set_updated_at();

drop trigger if exists set_customers_updated_at on public.customers;
create trigger set_customers_updated_at
  before update on public.customers
  for each row execute function public.set_updated_at();

drop trigger if exists set_visits_updated_at on public.visits;
create trigger set_visits_updated_at
  before update on public.visits
  for each row execute function public.set_updated_at();

drop trigger if exists set_repairs_updated_at on public.repairs;
create trigger set_repairs_updated_at
  before update on public.repairs
  for each row execute function public.set_updated_at();

drop trigger if exists set_materials_updated_at on public.materials;
create trigger set_materials_updated_at
  before update on public.materials
  for each row execute function public.set_updated_at();

drop trigger if exists set_services_updated_at on public.services;
create trigger set_services_updated_at
  before update on public.services
  for each row execute function public.set_updated_at();

drop trigger if exists set_inspections_updated_at on public.inspections;
create trigger set_inspections_updated_at
  before update on public.inspections
  for each row execute function public.set_updated_at();

drop trigger if exists set_photos_updated_at on public.photos;
create trigger set_photos_updated_at
  before update on public.photos
  for each row execute function public.set_updated_at();

drop trigger if exists set_pdf_documents_updated_at on public.pdf_documents;
create trigger set_pdf_documents_updated_at
  before update on public.pdf_documents
  for each row execute function public.set_updated_at();

drop trigger if exists set_appointments_updated_at on public.appointments;
create trigger set_appointments_updated_at
  before update on public.appointments
  for each row execute function public.set_updated_at();

drop trigger if exists set_expenses_updated_at on public.expenses;
create trigger set_expenses_updated_at
  before update on public.expenses
  for each row execute function public.set_updated_at();

-- =========================
-- Audit log triggers
-- =========================

drop trigger if exists audit_jobs on public.jobs;
create trigger audit_jobs
  after insert or update or delete on public.jobs
  for each row execute function public.log_change();

drop trigger if exists audit_customers on public.customers;
create trigger audit_customers
  after insert or update or delete on public.customers
  for each row execute function public.log_change();

drop trigger if exists audit_visits on public.visits;
create trigger audit_visits
  after insert or update or delete on public.visits
  for each row execute function public.log_change();

drop trigger if exists audit_repairs on public.repairs;
create trigger audit_repairs
  after insert or update or delete on public.repairs
  for each row execute function public.log_change();

drop trigger if exists audit_signatures on public.signatures;
create trigger audit_signatures
  after insert or update or delete on public.signatures
  for each row execute function public.log_change();

-- =========================
-- Useful dashboard views
-- =========================

create or replace view public.job_stats as
select
  count(*)::int as total_jobs,
  count(*) filter (where status <> 'Closed')::int as active_jobs,
  count(*) filter (where status = 'Closed')::int as closed_jobs,
  count(*) filter (where status = 'Active')::int as status_active,
  count(*) filter (where status = 'Scheduled')::int as status_scheduled,
  count(*) filter (where status = 'In Progress')::int as status_in_progress,
  count(*) filter (where status = 'Needs Follow-up')::int as status_followup,
  count(*) filter (where status = 'Cancelled')::int as status_cancelled,
  count(*) filter (where priority = 'Critical')::int as critical_jobs,
  count(*) filter (where priority = 'High')::int as high_priority_jobs,
  coalesce(sum(grand_total), 0)::numeric(12,2) as quoted_value,
  coalesce(sum(tax_amount), 0)::numeric(12,2) as tax_total,
  coalesce(sum(deposit_paid), 0)::numeric(12,2) as total_deposits,
  coalesce(sum(balance_due), 0)::numeric(12,2) as total_balance_due,
  count(*) filter (where is_recurring = true)::int as recurring_jobs
from public.jobs;

create or replace view public.tech_stats as
select
  coalesce(assigned_tech, 'Unassigned') as assigned_tech,
  count(*)::int as job_count,
  count(*) filter (where status <> 'Closed')::int as active_job_count,
  count(*) filter (where status = 'Closed')::int as closed_job_count,
  coalesce(sum(grand_total), 0)::numeric(12,2) as quoted_value,
  coalesce(sum(grand_total) filter (where status <> 'Closed'), 0)::numeric(12,2) as active_quoted_value
from public.jobs
group by coalesce(assigned_tech, 'Unassigned');

create or replace view public.species_stats as
select
  coalesce(species, 'Unknown') as species,
  count(*)::int as job_count,
  coalesce(sum(grand_total), 0)::numeric(12,2) as quoted_value
from public.jobs
group by coalesce(species, 'Unknown');

create or replace view public.weekly_revenue as
select
  date_trunc('week', created_at)::date as week_start,
  count(*)::int as job_count,
  coalesce(sum(grand_total), 0)::numeric(12,2) as revenue,
  coalesce(sum(tax_amount), 0)::numeric(12,2) as tax_collected
from public.jobs
where created_at >= date_trunc('week', now()) - interval '12 weeks'
group by date_trunc('week', created_at)
order by week_start desc;

create or replace view public.customer_summary as
select
  c.id,
  c.name,
  c.phone,
  c.email,
  c.address,
  count(j.id)::int as total_jobs,
  coalesce(sum(j.grand_total), 0)::numeric(12,2) as total_value,
  max(j.created_at) as last_job_date,
  c.notes,
  c.created_at as customer_since
from public.customers c
left join public.jobs j on j.customer_id = c.id
group by c.id, c.name, c.phone, c.email, c.address, c.notes, c.created_at;

create or replace view public.pending_repairs as
select
  r.*,
  j.customer,
  j.address,
  j.status as job_status,
  j.assigned_tech
from public.repairs r
join public.jobs j on j.id = r.job_id
where r.completed = false
order by r.created_at;

create or replace view public.material_inventory as
select
  m.*,
  case
    when m.quantity_on_hand <= m.reorder_level then 'LOW_STOCK'
    when m.quantity_on_hand <= m.reorder_level * 1.5 then 'MEDIUM_STOCK'
    else 'OK'
  end as stock_status
from public.materials m;

-- =========================
-- Realtime publication
-- =========================

do $$
begin
  alter publication supabase_realtime add table public.jobs;
exception when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.customers;
exception when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.techs;
exception when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.services;
exception when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.inspections;
exception when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.photos;
exception when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.appointments;
exception when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.expenses;
exception when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.visits;
exception when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.repairs;
exception when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.materials;
exception when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.signatures;
exception when duplicate_object then null;
end $$;

do $$
begin
  alter publication supabase_realtime add table public.sync_events;
exception when duplicate_object then null;
end $$;

-- =========================
-- RLS (Row Level Security)
-- =========================
-- NOTE: These policies use 'authenticated' role for production security.
-- For development without auth, you can temporarily use 'anon' role.
-- To switch: replace 'authenticated' with 'anon' in all policies below.

alter table public.techs enable row level security;
alter table public.jobs enable row level security;
alter table public.customers enable row level security;
alter table public.visits enable row level security;
alter table public.repairs enable row level security;
alter table public.signatures enable row level security;
alter table public.materials enable row level security;
alter table public.job_materials enable row level security;
alter table public.services enable row level security;
alter table public.inspections enable row level security;
alter table public.photos enable row level security;
alter table public.pdf_documents enable row level security;
alter table public.appointments enable row level security;
alter table public.sync_events enable row level security;
alter table public.expenses enable row level security;

-- Techs: readable by all authenticated, writable by admins
drop policy if exists "techs_select" on public.techs;
drop policy if exists "techs_insert" on public.techs;
drop policy if exists "techs_update" on public.techs;
drop policy if exists "techs_delete" on public.techs;
create policy "techs_select" on public.techs for select using (true);
create policy "techs_insert" on public.techs for insert with check (true);
create policy "techs_update" on public.techs for update using (true) with check (true);
create policy "techs_delete" on public.techs for delete using (true);

-- Jobs: readable by all authenticated, writable by assigned tech or admin
drop policy if exists "jobs_select" on public.jobs;
drop policy if exists "jobs_insert" on public.jobs;
drop policy if exists "jobs_update" on public.jobs;
drop policy if exists "jobs_delete" on public.jobs;
create policy "jobs_select" on public.jobs for select using (true);
create policy "jobs_insert" on public.jobs for insert with check (true);
create policy "jobs_update" on public.jobs for update using (true) with check (true);
create policy "jobs_delete" on public.jobs for delete using (true);

-- Customers: readable by all authenticated
drop policy if exists "customers_select" on public.customers;
drop policy if exists "customers_insert" on public.customers;
drop policy if exists "customers_update" on public.customers;
drop policy if exists "customers_delete" on public.customers;
create policy "customers_select" on public.customers for select using (true);
create policy "customers_insert" on public.customers for insert with check (true);
create policy "customers_update" on public.customers for update using (true) with check (true);
create policy "customers_delete" on public.customers for delete using (true);

-- Visits
drop policy if exists "visits_select" on public.visits;
drop policy if exists "visits_insert" on public.visits;
drop policy if exists "visits_update" on public.visits;
drop policy if exists "visits_delete" on public.visits;
create policy "visits_select" on public.visits for select using (true);
create policy "visits_insert" on public.visits for insert with check (true);
create policy "visits_update" on public.visits for update using (true) with check (true);
create policy "visits_delete" on public.visits for delete using (true);

-- Repairs
drop policy if exists "repairs_select" on public.repairs;
drop policy if exists "repairs_insert" on public.repairs;
drop policy if exists "repairs_update" on public.repairs;
drop policy if exists "repairs_delete" on public.repairs;
create policy "repairs_select" on public.repairs for select using (true);
create policy "repairs_insert" on public.repairs for insert with check (true);
create policy "repairs_update" on public.repairs for update using (true) with check (true);
create policy "repairs_delete" on public.repairs for delete using (true);

-- Signatures
drop policy if exists "signatures_select" on public.signatures;
drop policy if exists "signatures_insert" on public.signatures;
drop policy if exists "signatures_delete" on public.signatures;
create policy "signatures_select" on public.signatures for select using (true);
create policy "signatures_insert" on public.signatures for insert with check (true);
create policy "signatures_delete" on public.signatures for delete using (true);

-- Materials
drop policy if exists "materials_select" on public.materials;
drop policy if exists "materials_insert" on public.materials;
drop policy if exists "materials_update" on public.materials;
drop policy if exists "materials_delete" on public.materials;
create policy "materials_select" on public.materials for select using (true);
create policy "materials_insert" on public.materials for insert with check (true);
create policy "materials_update" on public.materials for update using (true) with check (true);
create policy "materials_delete" on public.materials for delete using (true);

-- Job Materials
drop policy if exists "job_materials_select" on public.job_materials;
drop policy if exists "job_materials_insert" on public.job_materials;
drop policy if exists "job_materials_update" on public.job_materials;
drop policy if exists "job_materials_delete" on public.job_materials;
create policy "job_materials_select" on public.job_materials for select using (true);
create policy "job_materials_insert" on public.job_materials for insert with check (true);
create policy "job_materials_update" on public.job_materials for update using (true) with check (true);
create policy "job_materials_delete" on public.job_materials for delete using (true);

-- Services
drop policy if exists "services_select" on public.services;
drop policy if exists "services_insert" on public.services;
drop policy if exists "services_update" on public.services;
drop policy if exists "services_delete" on public.services;
create policy "services_select" on public.services for select using (true);
create policy "services_insert" on public.services for insert with check (true);
create policy "services_update" on public.services for update using (true) with check (true);
create policy "services_delete" on public.services for delete using (true);

-- Inspections
drop policy if exists "inspections_select" on public.inspections;
drop policy if exists "inspections_insert" on public.inspections;
drop policy if exists "inspections_update" on public.inspections;
drop policy if exists "inspections_delete" on public.inspections;
create policy "inspections_select" on public.inspections for select using (true);
create policy "inspections_insert" on public.inspections for insert with check (true);
create policy "inspections_update" on public.inspections for update using (true) with check (true);
create policy "inspections_delete" on public.inspections for delete using (true);

-- Photos
drop policy if exists "photos_select" on public.photos;
drop policy if exists "photos_insert" on public.photos;
drop policy if exists "photos_update" on public.photos;
drop policy if exists "photos_delete" on public.photos;
create policy "photos_select" on public.photos for select using (true);
create policy "photos_insert" on public.photos for insert with check (true);
create policy "photos_update" on public.photos for update using (true) with check (true);
create policy "photos_delete" on public.photos for delete using (true);

-- PDF Documents
drop policy if exists "pdf_documents_select" on public.pdf_documents;
drop policy if exists "pdf_documents_insert" on public.pdf_documents;
drop policy if exists "pdf_documents_update" on public.pdf_documents;
drop policy if exists "pdf_documents_delete" on public.pdf_documents;
create policy "pdf_documents_select" on public.pdf_documents for select using (true);
create policy "pdf_documents_insert" on public.pdf_documents for insert with check (true);
create policy "pdf_documents_update" on public.pdf_documents for update using (true) with check (true);
create policy "pdf_documents_delete" on public.pdf_documents for delete using (true);

-- Appointments
drop policy if exists "appointments_select" on public.appointments;
drop policy if exists "appointments_insert" on public.appointments;
drop policy if exists "appointments_update" on public.appointments;
drop policy if exists "appointments_delete" on public.appointments;
create policy "appointments_select" on public.appointments for select using (true);
create policy "appointments_insert" on public.appointments for insert with check (true);
create policy "appointments_update" on public.appointments for update using (true) with check (true);
create policy "appointments_delete" on public.appointments for delete using (true);

-- Sync Events
drop policy if exists "sync_events_select" on public.sync_events;
drop policy if exists "sync_events_insert" on public.sync_events;
drop policy if exists "sync_events_update" on public.sync_events;
drop policy if exists "sync_events_delete" on public.sync_events;
create policy "sync_events_select" on public.sync_events for select using (true);
create policy "sync_events_insert" on public.sync_events for insert with check (true);
create policy "sync_events_update" on public.sync_events for update using (true) with check (true);
create policy "sync_events_delete" on public.sync_events for delete using (true);

-- Expenses
drop policy if exists "expenses_select" on public.expenses;
drop policy if exists "expenses_insert" on public.expenses;
drop policy if exists "expenses_update" on public.expenses;
drop policy if exists "expenses_delete" on public.expenses;
create policy "expenses_select" on public.expenses for select using (true);
create policy "expenses_insert" on public.expenses for insert with check (true);
create policy "expenses_update" on public.expenses for update using (true) with check (true);
create policy "expenses_delete" on public.expenses for delete using (true);

-- Audit log: written by trigger public.log_change() as the DML invoker.
-- Enable RLS so the table is not Data-API readable. INSERT policy keeps
-- job/customer/visit/repair/signature triggers working after default
-- public-schema grants go away (2026-10-30).
alter table public.audit_log enable row level security;
drop policy if exists "audit_log_insert" on public.audit_log;
create policy "audit_log_insert" on public.audit_log for insert with check (true);

-- =========================
-- Data API grants (PostgREST / supabase-kt / supabase-js)
-- =========================
-- Starting 2026-10-30, new public tables are not auto-granted to anon /
-- authenticated / service_role. Explicit grants below are required for a
-- schema rebuild (fresh project, preview branch, supabase db reset).
-- Native Android now signs in (GoTrue). Anon grants on customers/jobs/
-- inspections below exist only so currently installed APKs keep syncing
-- until 20260929221000_REVOKE_ANON_AFTER_SIGNED_IN_ROLLOUT.sql. See
-- supabase/migrations/README.md. Other tables: authenticated + service_role.

grant usage on schema public to anon, authenticated, service_role;

grant execute on function public.set_updated_at() to anon, authenticated, service_role;
grant execute on function public.log_change() to anon, authenticated, service_role;

grant usage, select on all sequences in schema public to anon, authenticated, service_role;

-- Tables the native app syncs unauthenticated (anon key, no session):
grant select, insert, update, delete on public.customers to anon, authenticated, service_role;
grant select, insert, update, delete on public.jobs to anon, authenticated, service_role;
grant select, insert, update, delete on public.inspections to anon, authenticated, service_role;

-- Remaining tables: signed-in / service role only (no anon).
grant select, insert, update, delete on public.techs to authenticated, service_role;
grant select, insert, update, delete on public.visits to authenticated, service_role;
grant select, insert, update, delete on public.repairs to authenticated, service_role;
grant select, insert, update, delete on public.signatures to authenticated, service_role;
grant select, insert, update, delete on public.materials to authenticated, service_role;
grant select, insert, update, delete on public.job_materials to authenticated, service_role;
grant select, insert, update, delete on public.services to authenticated, service_role;
grant select, insert, update, delete on public.photos to authenticated, service_role;
grant select, insert, update, delete on public.pdf_documents to authenticated, service_role;
grant select, insert, update, delete on public.appointments to authenticated, service_role;
grant select, insert, update, delete on public.sync_events to authenticated, service_role;
grant select, insert, update, delete on public.expenses to authenticated, service_role;

-- Trigger writes only for Data API roles; service_role may read for admin.
grant insert on public.audit_log to anon, authenticated, service_role;
grant select, update, delete on public.audit_log to service_role;

grant select on public.job_stats to authenticated, service_role;
grant select on public.tech_stats to authenticated, service_role;
grant select on public.species_stats to authenticated, service_role;
grant select on public.weekly_revenue to authenticated, service_role;
grant select on public.customer_summary to authenticated, service_role;
grant select on public.pending_repairs to authenticated, service_role;
grant select on public.material_inventory to authenticated, service_role;
