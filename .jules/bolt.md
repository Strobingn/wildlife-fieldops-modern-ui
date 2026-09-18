## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-19 - Pre-extracting Active Filter Criteria in Collection Utility Functions
**Learning:** In collection search and filter utilities (`filterJobs` / `searchJobs`) called on search input strokes or render state updates, calling `Object.entries(filters)` per item and `.toLowerCase()` inside per-item callbacks creates high garbage collection and CPU overhead (~1550ms per 1000 ops for 2000 items). Extracting active filter keys and pre-lowercasing target criteria values once before looping through items provides a ~6x speedup (~250ms per 1000 ops).
**Action:** Always extract active filter criteria and pre-process target criteria values once outside array filtering loops.
