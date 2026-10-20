## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2025-05-18 - Algorithmic Optimization of Collection Merging in Data Sync
**Learning:** `mergeArrays` previously used `findIndex` inside a loop over incoming server items, causing O(N * M) runtime complexity (~107ms for 2000 items). Using a `Map` of key -> index turns this into O(N + M) runtime complexity (~3.4ms for 2000 items), achieving a 30x (~97%) performance improvement during offline sync.
**Action:** Always pre-build key-to-index `Map` lookups when merging or joining collections instead of performing repeated `findIndex` calls inside array loops.

## 2025-05-18 - Preventing Redundant Page Re-renders on Shell State Changes
**Learning:** In a store-subscribed SPA render loop, firing `appEl.innerHTML = page.render(state)` on every state mutation (including drawer toggles, toasts, loading overlays, and sync status updates) destroys the entire page DOM tree and forces expensive unmounting, re-parsing, and listener re-binding (~400 unnecessary DOM rebuilds per 400 UI shell operations). Comparing a shallow snapshot of page-relevant state slice before re-rendering `#app` eliminates 100% of redundant page DOM teardowns and re-renders while allowing UI shell components to update directly and instantly.
**Action:** Always guard top-level SPA page re-renders by comparing page-relevant state slices to prevent UI shell updates from triggering full page DOM teardowns.
