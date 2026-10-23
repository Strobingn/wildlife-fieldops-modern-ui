## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-18 - Algorithmic Optimization of Collection Merging in Data Sync
**Learning:** `mergeArrays` previously used `findIndex` inside a loop over incoming server items, causing O(N * M) runtime complexity (~107ms for 2000 items). Using a `Map` of key -> index turns this into O(N + M) runtime complexity (~3.4ms for 2000 items), achieving a 30x (~97%) performance improvement during offline sync.
**Action:** Always pre-build key-to-index `Map` lookups when merging or joining collections instead of performing repeated `findIndex` calls inside array loops.

## 2025-05-18 - Pre-Aggregating Collection Relationships to Avoid N+1 Scans in List Rendering
**Learning:** Rendering job cards and expense lists previously executed nested `.filter()`, `.some()`, and `.find()` array scans per item (e.g. 7 array scans per job card for visits, repairs, photos, signatures), creating O(K * (N + M + P + S)) complexity (~1.214s for 1000 items). Pre-aggregating counts and relationships into `Map` lookups before rendering reduces complexity to O(N + M + P + S + K) (~291ms for 1000 items), achieving a 76% (>4x) speedup in render passes.
**Action:** Pre-build `Map` lookups or count maps once before mapping arrays in component render methods to prevent N+1 linear collection scans.
