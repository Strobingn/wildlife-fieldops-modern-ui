## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-18 - Algorithmic Optimization of Collection Merging in Data Sync
**Learning:** `mergeArrays` previously used `findIndex` inside a loop over incoming server items, causing O(N * M) runtime complexity (~107ms for 2000 items). Using a `Map` of key -> index turns this into O(N + M) runtime complexity (~3.4ms for 2000 items), achieving a 30x (~97%) performance improvement during offline sync.
**Action:** Always pre-build key-to-index `Map` lookups when merging or joining collections instead of performing repeated `findIndex` calls inside array loops.

## 2025-05-18 - Pre-Indexing Child Collections for Card List Rendering
**Learning:** Rendering job list cards previously executed up to 7 separate array scans (`.filter()` / `.some()`) per job across `visits`, `repairs`, `photos`, and `signatures`. For 100 jobs and 500 items per collection, this resulted in ~350,000 element checks per render pass (~425ms per 100 renders). Pre-building `Map` and `Set` lookup structures in a single O(N_items) pass (`buildJobCountsMap`) reduces card rendering complexity to O(N_items + N_jobs) (~25ms per 100 renders), achieving a ~16.7x (94%) speedup.
**Action:** Batch-index relational child collections into `Map`/`Set` lookup tables prior to list rendering loops rather than filtering child arrays individually per parent card.
