## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-18 - Algorithmic Optimization of Collection Merging in Data Sync
**Learning:** `mergeArrays` previously used `findIndex` inside a loop over incoming server items, causing O(N * M) runtime complexity (~107ms for 2000 items). Using a `Map` of key -> index turns this into O(N + M) runtime complexity (~3.4ms for 2000 items), achieving a 30x (~97%) performance improvement during offline sync.
**Action:** Always pre-build key-to-index `Map` lookups when merging or joining collections instead of performing repeated `findIndex` calls inside array loops.

## 2025-05-18 - Pre-grouping Collections for County Dashboard Aggregation
**Learning:** In `CountyReportAggregator.aggregate`, filtering `locatedObs` and `locatedJobs` inside a loop over all `countyKeys` resulted in $O(K \times (N + M))$ quadratic-like complexity (~32.8ms for 7000 items). Pre-grouping items by `county.key` using `groupBy` before creating county buckets reduces bucket creation complexity to $O(N + M)$ (~18.3ms for 7000 items), yielding a ~44% speedup.
**Action:** Always pre-group collections by key with `groupBy` before looping over keys to construct buckets.
