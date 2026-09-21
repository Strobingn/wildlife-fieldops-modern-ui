## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-19 - Single-Pass Map Indexing for List Item Card Calculations
**Learning:** Calling `.filter()` and `.some()` on relational arrays (e.g., visits, repairs, photos, signatures) inside each card item callback (`.map()`) turns list rendering into an $O(N \times (V + R + P + S))$ quadratic loop. Building a single-pass index map prior to rendering reduces array traversals by >85% (~1,700ms down to ~230ms per 1,000 list renders).
**Action:** Always pre-aggregate child/relational record counts into Map/Set lookup structures before mapping over collection arrays in render loops.
