-- AI detection feedback and unlabeled photo examples.
-- Additive. Older app builds do not read or write this table.
-- Native FieldOps syncs with the anon key and no user session, same as
-- observation_events. Apply this file, then redeploy functions/ai-assistant.
-- Safe to re-run.

create table if not exists public.ai_detection_feedback (
  id uuid primary key,
  user_id text not null default '',
  job_id text not null default '',
  photo_id text not null default '',
  photo_local_path text not null default '',
  photo_storage_path text not null default '',
  model_label text not null default '',
  model_confidence double precision not null default 0,
  verdict text not null default 'UNLABELED',
  corrected_label text not null default '',
  what_image_shows text not null default '',
  notes text not null default '',
  embedding text not null default '',
  created_at timestamptz not null default now(),
  constraint ai_detection_feedback_verdict_check
    check (verdict in ('YES', 'NO', 'PARTLY', 'UNLABELED'))
);

create index if not exists idx_ai_detection_feedback_created
  on public.ai_detection_feedback (created_at desc);
create index if not exists idx_ai_detection_feedback_job
  on public.ai_detection_feedback (job_id);
create index if not exists idx_ai_detection_feedback_verdict
  on public.ai_detection_feedback (verdict);

alter table public.ai_detection_feedback enable row level security;

drop policy if exists "ai_detection_feedback_select" on public.ai_detection_feedback;
drop policy if exists "ai_detection_feedback_insert" on public.ai_detection_feedback;

create policy "ai_detection_feedback_select"
  on public.ai_detection_feedback
  for select
  using (true);

create policy "ai_detection_feedback_insert"
  on public.ai_detection_feedback
  for insert
  with check (true);

grant select, insert on public.ai_detection_feedback
  to anon, authenticated, service_role;
