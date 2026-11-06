## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-18 - Algorithmic Optimization of Collection Merging in Data Sync
**Learning:** `mergeArrays` previously used `findIndex` inside a loop over incoming server items, causing O(N * M) runtime complexity (~107ms for 2000 items). Using a `Map` of key -> index turns this into O(N + M) runtime complexity (~3.4ms for 2000 items), achieving a 30x (~97%) performance improvement during offline sync.
**Action:** Always pre-build key-to-index `Map` lookups when merging or joining collections instead of performing repeated `findIndex` calls inside array loops.

## 2026-03-31 - Route Optimization and Distance Calculation Acceleration
**Learning:** In GPS route optimization, computing full Haversine distances (`Math.atan2`, `Math.sqrt`, trigonometry, and string parsing) inside the inner loop and splicing an array on every step caused high CPU and GC load (~760ms for 100 runs over 200 stops). Precomputing radians and latitude cosines once, tracking visited indices via a compact `Uint8Array`, and comparing the monotonic central angle term `a` in the inner loop reduces execution time by over 92% (~60ms for 100 runs over 200 stops).
**Action:** In spatial nearest-neighbor search loops, precompute trigonometric conversions and compare monotonic squared chord or central angle parameters rather than calculating full `atan2` on every iteration.
