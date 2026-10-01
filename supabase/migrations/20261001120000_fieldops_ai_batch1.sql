-- FieldOps AI batch 1 extras ride on existing jsonb so live PostgREST
-- does not 400 if these comments/columns are not applied yet.
--
-- App write path:
--   jobs.pricing jsonb  → confirmedSpecies, legalNotes, nextStep,
--                         nextStepDueAt, nextStepSource, aiRuntime, photoLineItems
--   inspections.findings jsonb → ai_narrative, ai_narrative_source
--
-- Optional dedicated columns (safe IF NOT EXISTS). The native client does
-- NOT add these keys to LiveJobUpsert. Apply when you want SQL-side query.

comment on column public.jobs.pricing is
  'Estimate worksheet plus FieldOps AI extras (confirmedSpecies, legalNotes, nextStep, nextStepDueAt, nextStepSource, aiRuntime, photoLineItems). Empty {} means use calculated money fields.';

alter table public.jobs
  add column if not exists confirmed_species text not null default '';

alter table public.jobs
  add column if not exists legal_notes text not null default '';

alter table public.jobs
  add column if not exists next_step text not null default '';

alter table public.jobs
  add column if not exists next_step_due_at timestamptz;

alter table public.jobs
  add column if not exists next_step_source text not null default '';

alter table public.jobs
  add column if not exists ai_runtime text not null default '';

-- inspections.findings already jsonb; no new table.
-- No GRANT here (PR #59 / 20260928120000 owns Data API grants).
-- No RLS change: jobs + inspections already have anon SIUD + authenticated
-- policies in the phase-1 pattern.
