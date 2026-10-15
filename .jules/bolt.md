## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-18 - Algorithmic Optimization of Collection Merging in Data Sync
**Learning:** `mergeArrays` previously used `findIndex` inside a loop over incoming server items, causing O(N * M) runtime complexity (~107ms for 2000 items). Using a `Map` of key -> index turns this into O(N + M) runtime complexity (~3.4ms for 2000 items), achieving a 30x (~97%) performance improvement during offline sync.
**Action:** Always pre-build key-to-index `Map` lookups when merging or joining collections instead of performing repeated `findIndex` calls inside array loops.

## 2025-05-18 - Pre-Indexing Relational Collections for Component Rendering
**Learning:** Performing repeated array filtering (`.filter()`, `.some()`) over relational collections (`visits`, `repairs`, `photos`, `signatures`) inside item rendering loops (`_jobCard`) results in O(N * (V + R + P + S)) runtime complexity (~350,000 iterations for 100 jobs). Pre-indexing associated records by foreign key (`job_id`) into `Map` count lookups and `Set` presence lookups once per `render()` pass drops runtime complexity to O(V + R + P + S + N) (~2,100 iterations), achieving a 160x (>99%) reduction in loop execution.
**Action:** When rendering items that query associated collections, always pre-build foreign-key `Map` / `Set` index lookups once per render pass instead of performing linear array scans inside per-item render loops.
