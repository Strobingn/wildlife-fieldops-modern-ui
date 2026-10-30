## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-18 - Algorithmic Optimization of Collection Merging in Data Sync
**Learning:** `mergeArrays` previously used `findIndex` inside a loop over incoming server items, causing O(N * M) runtime complexity (~107ms for 2000 items). Using a `Map` of key -> index turns this into O(N + M) runtime complexity (~3.4ms for 2000 items), achieving a 30x (~97%) performance improvement during offline sync.
**Action:** Always pre-build key-to-index `Map` lookups when merging or joining collections instead of performing repeated `findIndex` calls inside array loops.

## 2025-05-18 - Algorithmic Optimization of Customer Duplicate Detection
**Learning:** `detectDuplicateCustomers` used a brute-force O(N^2) double loop over all customer pairs to check normalized phone, email, and address similarity. For 1,000 customers, this performed 1,000,000 comparisons (~2,500ms). Indexing normalized phone and email into lookup Maps and bucketing addresses by length reduced candidate matching to O(N), bringing execution down to <30ms (a >80x speedup).
**Action:** Index lookup keys (Map) and pre-filter candidates by mathematical upper/lower bounds before running fuzzy or string similarity comparisons across collections.
