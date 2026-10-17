## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-18 - Algorithmic Optimization of Collection Merging in Data Sync
**Learning:** `mergeArrays` previously used `findIndex` inside a loop over incoming server items, causing O(N * M) runtime complexity (~107ms for 2000 items). Using a `Map` of key -> index turns this into O(N + M) runtime complexity (~3.4ms for 2000 items), achieving a 30x (~97%) performance improvement during offline sync.
**Action:** Always pre-build key-to-index `Map` lookups when merging or joining collections instead of performing repeated `findIndex` calls inside array loops.

## 2025-05-18 - Eliminating Repeated Array Scans in Component Rendering Loops
**Learning:** Calling `.filter()` or `.some()` on associated collections (`visits`, `repairs`, `photos`, `signatures`) inside a job list mapping loop creates O(K * (V + R + P + S)) runtime complexity (~62,500 loop iterations for 50 jobs). Pre-building O(1) `Map`/`Set` lookups once before rendering reduces runtime complexity to O(V + R + P + S + K) (~700 iterations), achieving a ~90x reduction in work on state updates and search filtering.
**Action:** Always pre-build metric/count `Map`s or `Set`s before mapping collections in render methods or template loops.
