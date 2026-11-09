## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-18 - Algorithmic Optimization of Collection Merging in Data Sync
**Learning:** `mergeArrays` previously used `findIndex` inside a loop over incoming server items, causing O(N * M) runtime complexity (~107ms for 2000 items). Using a `Map` of key -> index turns this into O(N + M) runtime complexity (~3.4ms for 2000 items), achieving a 30x (~97%) performance improvement during offline sync.
**Action:** Always pre-build key-to-index `Map` lookups when merging or joining collections instead of performing repeated `findIndex` calls inside array loops.

## 2025-05-19 - Pre-indexing Candidate Fields in Pairwise Similarity Checks
**Learning:** Pairwise similarity scanning over records (`detectDuplicateCustomers`) scales quadratically $O(N^2)$. Pre-building exact lookup `Map`s for discrete fields (phone, email) and grouping candidate items into length buckets for string similarity checks avoids redundant comparisons for items that cannot match or have already matched higher-priority rules.
**Action:** In $O(N^2)$ pairwise comparison algorithms, pre-build index maps and length/range buckets to skip unmatchable or already-matched candidate pairs before running string similarity computations.
