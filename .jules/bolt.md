## 2025-05-24 - Avoid Deep Cloning Store State on Read

**Learning:** In pub/sub or reactive store factories, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()` or listener notification causes severe CPU bottlenecks (O(N) operations) whenever items are rendered in loops (e.g., list items computing scores or derived metrics). When `state` is kept immutable via `Object.freeze()`, returning or passing state references directly reduces state access cost to O(1).

**Action:** Ensure `store.getState()` and component helper methods return or reuse frozen state references rather than performing deep clones during state reads.
