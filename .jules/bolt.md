## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-18 - Algorithmic Optimization of Collection Merging in Data Sync
**Learning:** `mergeArrays` previously used `findIndex` inside a loop over incoming server items, causing O(N * M) runtime complexity (~107ms for 2000 items). Using a `Map` of key -> index turns this into O(N + M) runtime complexity (~3.4ms for 2000 items), achieving a 30x (~97%) performance improvement during offline sync.
**Action:** Always pre-build key-to-index `Map` lookups when merging or joining collections instead of performing repeated `findIndex` calls inside array loops.

## 2025-05-18 - Map/Set Pre-indexing in Sub-collection List Rendering
**Learning:** Rendering job lists repeatedly evaluated `.filter()` and `.some()` on sub-collections (`visits`, `repairs`, `photos`, `signatures`) for each job card, creating O(N * M) algorithmic complexity (~67ms per render on 500 jobs). Pre-indexing sub-collections into `Map` and `Set` lookup tables once before the list loop turns sub-collection lookups into O(1), achieving ~86x faster rendering (~0.78ms per render).
**Action:** Always pre-build `Map` and `Set` lookups by foreign key (`job_id`) when rendering component lists with sub-collections.
