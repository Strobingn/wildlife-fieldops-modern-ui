## 2025-05-18 - Avoid Deep Cloning Central Store State in Offline-First PWAs

**Learning:** Deep cloning the entire reactive store state on every `getState()`, `setState()`, `subscribe()`, and `select()` call via `JSON.parse(JSON.stringify(state))` creates massive O(N) main-thread blocking, especially in applications containing photo base64 strings and large data collections.
**Action:** Use top-level `Object.freeze` and reference passing for state snapshots instead of deep cloning during store access and updates.
