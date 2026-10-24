## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-18 - Algorithmic Optimization of Collection Merging in Data Sync
**Learning:** `mergeArrays` previously used `findIndex` inside a loop over incoming server items, causing O(N * M) runtime complexity (~107ms for 2000 items). Using a `Map` of key -> index turns this into O(N + M) runtime complexity (~3.4ms for 2000 items), achieving a 30x (~97%) performance improvement during offline sync.
**Action:** Always pre-build key-to-index `Map` lookups when merging or joining collections instead of performing repeated `findIndex` calls inside array loops.

## 2025-05-18 - Algorithmic Optimization of Job Card List Rendering
**Learning:** Rendering job card lists previously performed multiple `.filter()` and `.some()` scans on sub-collections (`visits`, `repairs`, `photos`, `signatures`) for every single job rendered. This created O(N * (V + R + P + S)) algorithmic complexity (~63.8ms per render for 500 jobs with 6,000 sub-items). Pre-building `Map` and `Set` lookups once per render pass converted lookups to O(1) and reduced overall complexity to O(N + V + R + P + S) (~0.8ms per render), delivering an ~80x (~98.7%) performance improvement in UI list rendering.
**Action:** Pre-aggregate sub-collection counts and presence into hash maps or sets once per render pass before mapping over list items in UI renderers.
