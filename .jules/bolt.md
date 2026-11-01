## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-18 - Algorithmic Optimization of Collection Merging in Data Sync
**Learning:** `mergeArrays` previously used `findIndex` inside a loop over incoming server items, causing O(N * M) runtime complexity (~107ms for 2000 items). Using a `Map` of key -> index turns this into O(N + M) runtime complexity (~3.4ms for 2000 items), achieving a 30x (~97%) performance improvement during offline sync.
**Action:** Always pre-build key-to-index `Map` lookups when merging or joining collections instead of performing repeated `findIndex` calls inside array loops.

## 2025-05-18 - Pre-partitioning Grouped Fact Aggregations in Pure Projectors
**Learning:** In pure projection pipelines like `CountyReportAggregator`, extracting keys and repeatedly running `.filter { it.key == key }` across all observations and completions causes $O(K \times (N + M))$ repeated linear scans. Using a single-pass `groupBy { it.key }` partitions in $O(N + M)$ time and naturally preserves initial sort order, dropping execution time from 46.9ms to 20.6ms (~2.3x speedup) on 10k items.
**Action:** Always pre-group collections with `groupBy` before mapping over distinct keys rather than performing repeated collection filtering inside key maps.
