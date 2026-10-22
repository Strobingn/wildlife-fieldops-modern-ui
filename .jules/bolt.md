## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-18 - Algorithmic Optimization of Collection Merging in Data Sync
**Learning:** `mergeArrays` previously used `findIndex` inside a loop over incoming server items, causing O(N * M) runtime complexity (~107ms for 2000 items). Using a `Map` of key -> index turns this into O(N + M) runtime complexity (~3.4ms for 2000 items), achieving a 30x (~97%) performance improvement during offline sync.
**Action:** Always pre-build key-to-index `Map` lookups when merging or joining collections instead of performing repeated `findIndex` calls inside array loops.

## 2025-05-18 - Pre-indexing Relational Entities for List Rendering
**Learning:** Rendering job cards previously executed `.some()` and `.filter()` across full `visits`, `repairs`, `photos`, and `signatures` collections for every job card, resulting in O(N * M) nested array scans (~376ms per 100 renders of 100 jobs). Pre-building single-pass Map and Set lookups (`buildJobRelationCounts`) before the list render loop reduces relation lookups to O(1) and total runtime to O(N + M) (~27ms per 100 renders), achieving a ~14x speedup.
**Action:** When rendering lists that aggregate relational child collections, construct Map/Set count indexes once prior to mapping the list rather than performing linear array scans per item.
