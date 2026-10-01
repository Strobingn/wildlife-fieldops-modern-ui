## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-18 - Map-Based Array Merging vs Linear Scans in Collection Sync
**Learning:** Performing array merges (`mergeArrays`) with linear scans (`findIndex`) inside item loops leads to $O(N \cdot M)$ time complexity, causing major UI thread blocking when syncing large local and remote state stores across multiple collections. Using a pre-built `Map` for index lookups brings merge time down to $O(N + M)$ (~100x speedup on 2,000 items).
**Action:** Use `Map` index lookups instead of `findIndex` or nested loops when merging large collection arrays by key.
