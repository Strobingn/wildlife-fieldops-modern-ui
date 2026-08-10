# Wildlife FieldOps V2.5 Feature Roadmap

This branch adds the first production route-planning slice and establishes the grayscale visual system. These are the next ten high-value features for a wildlife-removal field workflow.

1. **Offline route plans and cached map areas** — save a day's route and keep the stop list available without signal.
2. **Service-time and skill-aware scheduling** — account for bat exclusions, trapping, inspections, equipment, and technician capability.
3. **Live ETA and delay alerts** — recalculate arrival windows from traffic and notify customers when the route slips.
4. **Recurring follow-up automation** — create warranty, exclusion-check, and trap-check visits from completed jobs.
5. **Customer self-service portal** — let customers approve estimates, upload photos, sign contracts, and choose appointment windows.
6. **Photo-to-report automation** — turn field photos and notes into a branded inspection report with before/after evidence.
7. **Vehicle and technician inventory** — reserve traps, exclusion materials, PPE, and consumables against a route.
8. **Payments and invoice capture** — collect signatures and payments in the field, including partial deposits.
9. **Permit, species, and compliance tracker** — surface New York rules, protected-species warnings, and required documentation by service type.
10. **Safety and incident checklists** — require site-risk, ladder, rabies/PPE, and wildlife-release checks before closing a job.

## Recommended implementation order

- **Next:** offline route plans, service-time scheduling, and recurring follow-ups.
- **Then:** live ETA, customer portal, and photo-to-report.
- **Later:** inventory, payments, compliance intelligence, and safety analytics.

Each feature should remain offline-safe first, sync through the existing Room/Supabase boundary, and preserve the grayscale field UI.
