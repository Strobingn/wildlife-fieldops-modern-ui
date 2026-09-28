-- supabase/migrations/202605240001_ai_integration.sql

create table if not exists public.ai_runs (
  id uuid primary key default gen_random_uuid(),
  job_id uuid references public.jobs(id) on delete set null,
  mode text not null,
  input jsonb not null default '{}'::jsonb,
  output jsonb not null default '{}'::jsonb,
  provider text not null default 'kimi_moonshot',
  created_at timestamptz not null default now()
);

create index if not exists idx_ai_runs_job_id on public.ai_runs(job_id);
create index if not exists idx_ai_runs_created_at on public.ai_runs(created_at desc);
create index if not exists idx_ai_runs_mode on public.ai_runs(mode);

alter table public.ai_runs enable row level security;

drop policy if exists "Allow all ops on ai_runs" on public.ai_runs;
create policy "Allow all ops on ai_runs"
on public.ai_runs
for all
using (true)
with check (true);

-- Native Android does not read/write ai_runs. No anon grant.
grant select, insert, update, delete on public.ai_runs to authenticated, service_role;

alter table public.jobs add column if not exists ai_notes text;
alter table public.jobs add column if not exists ai_customer_message text;
alter table public.jobs add column if not exists ai_invoice_notes text;
alter table public.jobs add column if not exists ai_last_run_at timestamptz;
