-- =============================================================================
-- Native FieldOps sync fix
-- Creates missing customers table, customer_id on jobs, grants + RLS for anon
-- Safe to re-run.
-- =============================================================================

create extension if not exists pgcrypto;

-- Customers (was missing on remote)
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

create index if not exists idx_customers_name on public.customers(name);
create index if not exists idx_customers_phone on public.customers(phone);

-- Link jobs → customers when possible
alter table public.jobs add column if not exists customer_id uuid;
do $$
begin
  if not exists (
    select 1 from pg_constraint where conname = 'jobs_customer_id_fkey'
  ) then
    alter table public.jobs
      add constraint jobs_customer_id_fkey
      foreign key (customer_id) references public.customers(id) on delete set null;
  end if;
exception when others then
  -- ignore if types/partial data prevent FK; column still exists for soft links
  raise notice 'jobs_customer_id_fkey skipped: %', sqlerrm;
end $$;

create index if not exists idx_jobs_customer_id on public.jobs(customer_id);

-- Helpful columns the native app may send
alter table public.jobs add column if not exists warranty text;
alter table public.jobs add column if not exists accuracy numeric;
alter table public.jobs add column if not exists is_recurring boolean not null default false;
alter table public.jobs add column if not exists recurrence_pattern text;

-- Ensure required job text fields can accept empty-ish defaults when clients omit them
alter table public.jobs alter column species set default 'Wildlife';
alter table public.jobs alter column title set default 'Field Job';
alter table public.jobs alter column customer_name set default 'Customer';
alter table public.jobs alter column status set default 'Active';
alter table public.jobs alter column priority set default 'Normal';

-- Updated-at helper
create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists set_customers_updated_at on public.customers;
create trigger set_customers_updated_at
  before update on public.customers
  for each row execute function public.set_updated_at();

-- RLS
alter table public.customers enable row level security;

drop policy if exists "customers_select" on public.customers;
drop policy if exists "customers_insert" on public.customers;
drop policy if exists "customers_update" on public.customers;
drop policy if exists "customers_delete" on public.customers;
drop policy if exists "anon_select_customers" on public.customers;
drop policy if exists "anon_insert_customers" on public.customers;
drop policy if exists "anon_update_customers" on public.customers;
drop policy if exists "anon_delete_customers" on public.customers;
drop policy if exists "allow anon all customers" on public.customers;
drop policy if exists "authenticated customers (consolidated)" on public.customers;

create policy "anon_select_customers" on public.customers for select to anon using (true);
create policy "anon_insert_customers" on public.customers for insert to anon with check (true);
create policy "anon_update_customers" on public.customers for update to anon using (true) with check (true);
create policy "anon_delete_customers" on public.customers for delete to anon using (true);

create policy "authenticated customers (consolidated)" on public.customers
  for all to authenticated using (true) with check (true);

-- Table privileges (required for Data API beyond RLS).
-- Native FieldOps uses the anon key with no sign-in, so anon is required
-- to keep current sync working. service_role is for Edge Functions / SQL.
grant select, insert, update, delete on public.customers to anon, authenticated, service_role;
grant usage, select on all sequences in schema public to anon, authenticated, service_role;

-- Realtime (optional)
do $$
begin
  alter publication supabase_realtime add table public.customers;
exception when duplicate_object then null;
when undefined_object then null;
end $$;
