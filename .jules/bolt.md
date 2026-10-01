## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-18 - Pre-computing Job Collection Counts for List Rendering
**Learning:** Calling `.some()` and `.filter()` repeatedly on related arrays (`visits`, `photos`, `repairs`, `signatures`) for each job card in a list creates an $O(N \times M)$ bottleneck (~480ms per 100 renders). Pre-building single-pass lookup maps (`buildJobCounts`) reduces list rendering time to $O(N + M)$ (~24ms per 100 renders, ~95% speedup).
**Action:** Pass pre-computed index/count maps when rendering collections of cards that require counts or presence checks from related arrays.
