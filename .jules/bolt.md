## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-18 - Pre-aggregating Job Collection Item Counts for List Views
**Learning:** In list views (`Dashboard`, `JobList`) rendering N job cards, calling `.some()` and `.filter()` repeatedly on state collections (`visits`, `repairs`, `photos`, `signatures`) causes O(N * M) quadratic array iterations. Pre-aggregating item counts by `jobId` into a single Map in O(M) time before mapping job cards reduces render lookups to O(1).
**Action:** Always pre-aggregate sub-collection counts/flags into a Map or Set before mapping items in UI list renderers.
