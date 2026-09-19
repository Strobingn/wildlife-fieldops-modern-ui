# NY County Sales Tax — Auto-fill on Invoices

## Summary

Wildlife Whisperer LLC (Cornwall, NY / Orange County) operates primarily in the Hudson
Valley. Each job invoice now automatically applies the correct combined New York State
sales tax rate for the job's county, eliminating manual rate entry.

## How county is resolved

Resolution happens in `CountyLookupService` when the InvoiceScreen opens:

1. **Reverse-geocode from coordinates** — if `job.latitude` and `job.longitude` are
   present, Android `Geocoder.getFromLocation()` is called first (free, works offline
   after first device lock fix). Falls back to the Google Maps Geocoding API if the
   system geocoder is unavailable and `GOOGLE_MAPS_API_KEY` is configured.
2. **Forward-geocode from address text** — if no coordinates are available, the same
   two-tier approach is applied to `job.address`.
3. **Cached result** — the resolved `county` and `state` strings are persisted to the
   `jobs` table (Room migration 3 → 4) so subsequent offline invoice opens skip the
   network call entirely.
4. **Graceful failure** — if every strategy returns nothing, the Tax % field stays
   editable and a small hint reads "County unknown — set tax % manually".

A **Refresh county** icon button re-runs the lookup (useful after the operator updates
the job address or coordinates).

## Rate table

Rates are stored in `NyCountyTaxRates` (combined state + local, %).

| County       | Combined rate |
|--------------|---------------|
| Orange       | 8.125 %       |
| Rockland     | 8.375 %       |
| Ulster       | 8.0 %         |
| Dutchess     | 8.125 %       |
| Putnam       | 8.375 %       |
| Westchester  | 8.375 %       |
| Sullivan     | 8.0 %         |
| Columbia     | 8.0 %         |
| Greene       | 8.0 %         |
| Warren       | 8.0 %         |
| Albany       | 8.0 %         |
| Nassau       | 8.625 %       |
| Suffolk      | 8.625 %       |
| New York City| 8.875 %       |
| *(default)*  | 8.0 %         |

Source: NYS Department of Taxation and Finance TB-ST-825, retrieved June 2025.
A `TODO` in `NyCountyTaxRates.kt` marks where a Settings-level override can be wired in.

## Files changed

| File | Change |
|------|--------|
| `tax/NyCountyTaxRates.kt` | New — rate table + `taxRatePercentForCounty()` |
| `tax/CountyLookupService.kt` | New — geocode → county logic |
| `data/model/Job.kt` | Added `county` and `state` nullable fields |
| `data/local/Migrations.kt` | New — Room migration 3 → 4 (ALTER TABLE) |
| `data/local/AppDatabase.kt` | Version bump 3 → 4 |
| `di/AppModule.kt` | Register `MIGRATION_3_4` |
| `data/local/JobDao.kt` | Added `updateCounty` query |
| `data/repository/JobRepository.kt` | Added `updateJobCounty` helper |
| `ui/viewmodel/InvoiceViewModel.kt` | `CountyTaxState` + `resolveCountyTax` / `refreshCountyTax` |
| `ui/screens/InvoiceScreen.kt` | Auto-fill taxRate, `CountyTaxLabel`, refresh button |
| `test/…/NyCountyTaxRatesTest.kt` | New — 20 unit tests |
