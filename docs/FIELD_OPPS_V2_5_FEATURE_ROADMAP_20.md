# Wildlife FieldOps V2.5 — 20-Feature Expansion

This is the implementation backlog for the Android-only `Field_Opps_V2.5` branch. Every feature must use real Room/Supabase data, remain safe when offline, and keep the grayscale field UI.

## Map and field operations

1. **Complete job map coverage** — show pending, active, completed, invoiced, paid, and cancelled jobs.
2. **Customer-location fallback** — use a customer’s saved coordinates when an older job has no job-level coordinates.
3. **Automatic map fit** — fit the camera to the current job set after loading or sync.
4. **Unlocated-job queue** — show jobs without coordinates and provide a direct action to fix their address/location.
5. **Status map filters** — filter markers by any job status without losing the search query.
6. **Offline map snapshots** — cache selected service areas for field work without signal.
7. **One-tap navigation** — launch turn-by-turn navigation from a job marker or job detail screen.
8. **Arrival check-in** — record technician arrival time and GPS position against the job.
9. **Geofence arrival detection** — optionally detect arrival within a configurable radius and create an audit event.
10. **Traffic-aware route ETA** — recalculate route order and arrival windows when traffic data is available.

## AI-assisted operations

11. **AI route planner** — optimize stops using service duration, priority, equipment, and appointment windows.
12. **AI species identification** — identify likely nuisance species from a field photo and show confidence plus safety notes.
13. **AI damage classifier** — classify entry points, chewing, droppings, nesting, and structural damage from photos.
14. **AI inspection report writer** — turn verified field notes and selected photos into a professional report draft.
15. **AI estimate assistant** — suggest an estimate range from service type, access conditions, materials, and prior approved jobs.
16. **AI follow-up planner** — create recommended warranty, trap-check, and exclusion-check visits from completed work.
17. **NY compliance assistant** — surface species, permit, release, and documentation warnings from the job record.

## Business and technician workflow

18. **Inventory-aware scheduling** — warn when a route requires traps, PPE, exclusion materials, or consumables that are not available.
19. **Voice field notes** — transcribe hands-free notes and attach the original audio plus editable text to the job.
20. **Customer update automation** — prepare appointment, arrival, completion, and follow-up messages from verified job state.

## Delivery rules

- Room remains the source of truth while offline.
- Supabase sync must preserve coordinates, status, completed date, and audit fields.
- AI output is always a draft until the technician accepts it.
- No feature may silently replace an existing job location or status.
- Build and unit checks must pass on every branch update.
