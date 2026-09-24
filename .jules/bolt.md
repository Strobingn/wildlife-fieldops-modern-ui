## 2025-05-19 - Replacing $O(N \times M)$ `findIndex` with $O(N + M)$ Map Lookups in Array Synchronization
**Learning:** Performing `findIndex` inside an array iteration during collection merging creates quadratic time complexity ($O(N \times M)$). For datasets with 2000 elements, array synchronization took over 815ms. Pre-building a `Map` of array item keys reduces synchronization time to 36ms (~22x speedup).
**Action:** Always use index or item `Map` lookup tables when merging or cross-referencing collections by unique key identifiers.

## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.
