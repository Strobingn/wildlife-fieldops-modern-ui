## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-18 - Single-Pass Relationship Indexing for List Item Card Rendering
**Learning:** Performing repeated array `.filter()` and `.some()` scans on sub-collections (visits, repairs, photos, signatures) for every rendered job card in component render loops creates $O(N \cdot (V + R + P + S))$ rendering bottlenecks. Building single-pass `Map`/`Set` index lookups reduces job list card rendering time by over 17x (~375ms to ~21ms for 100 jobs with 500 sub-items).
**Action:** When rendering lists where items reference external sub-collections, build single-pass `Map`/`Set` index structures before mapping items to components.
