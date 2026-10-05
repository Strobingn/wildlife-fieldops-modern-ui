-- Mirror of live wildlife_app migration add_missing_fk_indexes_20261005
-- (supabase_migrations.schema_migrations version 20261005133021).
-- Already applied on live. This file is the repo copy only.
-- 26 foreign-key covering indexes. CREATE INDEX IF NOT EXISTS makes re-apply a no-op.
-- Names match the indexes present on public (table_column_fkey_idx).

create index if not exists campaign_deliveries_campaign_id_fkey_idx on public.campaign_deliveries (campaign_id);
create index if not exists campaign_deliveries_customer_id_fkey_idx on public.campaign_deliveries (customer_id);
create index if not exists customer_portal_tokens_customer_id_fkey_idx on public.customer_portal_tokens (customer_id);
create index if not exists customer_portal_tokens_organization_id_fkey_idx on public.customer_portal_tokens (organization_id);
create index if not exists field_measurements_inspection_id_fkey_idx on public.field_measurements (inspection_id);
create index if not exists field_measurements_organization_id_fkey_idx on public.field_measurements (organization_id);
create index if not exists inventory_transactions_created_by_fkey_idx on public.inventory_transactions (created_by);
create index if not exists job_photos_uploaded_by_fkey_idx on public.job_photos (uploaded_by);
create index if not exists jobs_created_by_fkey_idx on public.jobs (created_by);
create index if not exists measurements_job_id_fkey_idx on public.measurements (job_id);
create index if not exists payments_invoice_id_fkey_idx on public.payments (invoice_id);
create index if not exists payments_organization_id_fkey_idx on public.payments (organization_id);
create index if not exists photo_annotations_linked_service_id_fkey_idx on public.photo_annotations (linked_service_id);
create index if not exists properties_created_by_fkey_idx on public.properties (created_by);
create index if not exists recurring_service_plans_assigned_tech_id_fkey_idx on public.recurring_service_plans (assigned_tech_id);
create index if not exists recurring_service_plans_customer_id_fkey_idx on public.recurring_service_plans (customer_id);
create index if not exists recurring_service_plans_organization_id_fkey_idx on public.recurring_service_plans (organization_id);
create index if not exists recurring_service_plans_property_id_fkey_idx on public.recurring_service_plans (property_id);
create index if not exists route_stops_appointment_id_fkey_idx on public.route_stops (appointment_id);
create index if not exists signatures_job_id_fkey_idx on public.signatures (job_id);
create index if not exists technician_daily_metrics_technician_id_fkey_idx on public.technician_daily_metrics (technician_id);
create index if not exists trap_checks_job_id_fkey_idx on public.trap_checks (job_id);
create index if not exists trap_checks_technician_id_fkey_idx on public.trap_checks (technician_id);
create index if not exists warranty_claims_assigned_tech_id_fkey_idx on public.warranty_claims (assigned_tech_id);
create index if not exists warranty_claims_job_id_fkey_idx on public.warranty_claims (job_id);
create index if not exists warranty_claims_warranty_id_fkey_idx on public.warranty_claims (warranty_id);
