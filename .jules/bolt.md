## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-18 - Algorithmic Optimization of Collection Merging in Data Sync
**Learning:** `mergeArrays` previously used `findIndex` inside a loop over incoming server items, causing O(N * M) runtime complexity (~107ms for 2000 items). Using a `Map` of key -> index turns this into O(N + M) runtime complexity (~3.4ms for 2000 items), achieving a 30x (~97%) performance improvement during offline sync.
**Action:** Always pre-build key-to-index `Map` lookups when merging or joining collections instead of performing repeated `findIndex` calls inside array loops.

## 2025-05-19 - Pre-indexing Relation Collections for List Component Rendering
**Learning:** Rendering list items (e.g. job cards) where each card scans multiple relation collections (`visits`, `repairs`, `photos`, `signatures`) using `.filter()`/`.some()` creates an $O(J \times (V + R + P + S))$ bottleneck (~774ms per 10 renders of 500 items). Pre-building `Map` count lookups and `Set` membership lookups once in $O(V + R + P + S)$ before iterating over list items reduces render time to ~15.6ms (~50x / 98% faster).
**Action:** Always pre-index related collections into `Map`/`Set` structures once before iterating over list items in render methods.
