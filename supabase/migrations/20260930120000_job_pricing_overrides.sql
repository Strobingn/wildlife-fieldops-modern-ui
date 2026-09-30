-- Job pricing worksheet + computed-field overrides (estimate / invoice totals).
--
-- Live wildlife_app already has public.jobs.pricing jsonb NOT NULL DEFAULT '{}'::jsonb
-- (phase 1 applied). This file is safe to re-run: ADD COLUMN IF NOT EXISTS is a no-op
-- when the column is present.
--
-- Permissions are owned by the PR #59 phase grant files. Do not GRANT here
-- (especially not to anon).

alter table public.jobs
  add column if not exists pricing jsonb not null default '{}'::jsonb;

comment on column public.jobs.pricing is
  'Estimate worksheet and computed-field overrides (labor/materials/mileage/tax/total locks). Empty {} means use calculated values.';
