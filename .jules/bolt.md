## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-18 - Algorithmic Optimization of Collection Merging in Data Sync
**Learning:** `mergeArrays` previously used `findIndex` inside a loop over incoming server items, causing O(N * M) runtime complexity (~107ms for 2000 items). Using a `Map` of key -> index turns this into O(N + M) runtime complexity (~3.4ms for 2000 items), achieving a 30x (~97%) performance improvement during offline sync.
**Action:** Always pre-build key-to-index `Map` lookups when merging or joining collections instead of performing repeated `findIndex` calls inside array loops.

## 2026-11-04 - Algorithmic Optimization of Nearest-Neighbor Route Optimization
**Learning:** In `calculateOptimalRoute`, performing string-to-float conversions, degree-to-radian transformations, and `Math.cos` calls inside nested distance loops recomputes identical values $O(N^2)$ times, while `unvisited.splice()` causes repeated $O(N)$ memory copies. Pre-parsing coordinates into radians, caching $\cos(\text{lat})$, and using a visited flag cuts execution time by ~3.5x (~2600ms down to ~750ms for 20 iterations of 1000 stops).
**Action:** In spatial algorithms comparing pairwise geographic points, precompute radian angles and trigonometric factors upfront, and track visited items via boolean flags rather than splicing arrays.
