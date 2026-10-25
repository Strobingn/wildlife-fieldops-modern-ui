## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-18 - Algorithmic Optimization of Collection Merging in Data Sync
**Learning:** `mergeArrays` previously used `findIndex` inside a loop over incoming server items, causing O(N * M) runtime complexity (~107ms for 2000 items). Using a `Map` of key -> index turns this into O(N + M) runtime complexity (~3.4ms for 2000 items), achieving a 30x (~97%) performance improvement during offline sync.
**Action:** Always pre-build key-to-index `Map` lookups when merging or joining collections instead of performing repeated `findIndex` calls inside array loops.

## 2025-05-18 - Pre-indexing Job Relationships for Fast List Rendering
**Learning:** Computing job completeness scores and related entity counts (visits, repairs, photos, signatures) inside list item render loops caused repeated O(N * (V + R + P + S)) array filter and search operations. Pre-indexing relational collections into `Map`s and `Set`s once before list mapping transforms lookups into O(1) constant time operations, reducing loop operations by ~770x (~99.8%) on large job lists.
**Action:** When rendering lists that display child collection counts or status flags, pre-build index `Map`s or `Set`s once per render instead of scanning child arrays inside the list item loop.
