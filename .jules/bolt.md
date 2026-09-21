## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-19 - Optimizing Array Filtering and Merging Overheads
**Learning:** Re-evaluating `Object.entries(filters)` and `toLowerCase()` inside `Array.prototype.filter` loops creates `O(N * F)` object/array allocations per render frame. Extracting non-empty filter criteria before filtering eliminates per-element allocations and allows `O(1)` early return when filters are inactive. Replacing `findIndex` inside loop with `Map` reduces `mergeArrays` complexity from `O(N * M)` to `O(N + M)`.
**Action:** Always hoist invariant filter criteria and search schemas outside collection iteration loops and use `Map` for key-based collection merges.
