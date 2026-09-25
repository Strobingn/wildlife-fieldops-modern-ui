## 2025-05-19 - Pre-indexing Sub-collections for $O(1)$ Job Card Rendering
**Learning:** Calling `.some()` and `.filter()` repeatedly on sub-collections (`visits`, `photos`, `repairs`, `signatures`) inside individual card render functions creates an $O(N \cdot M)$ complexity ($7 \times N \times M$ array iterations). Pre-building `Set` and `Map` lookups once per list render pass reduces complexity to $O(N + M)$ and reduces loop iterations by over 99%.
**Action:** When rendering lists that rely on related items in flat state collections, construct lookup `Set`s / `Map`s once in the parent `render()` method and pass them into item renderers.

## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.
