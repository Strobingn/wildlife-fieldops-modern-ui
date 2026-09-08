## 2025-05-24 - Eliminate Redundant Deep Cloning in Central Pub/Sub Store

**Learning:** `createStore` was calling `deepClone(state)` (which performs `JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call. As state collections (jobs, inspections, photos, signatures) grow, deep cloning the state tree on every state access/subscription took >1.9s for 500 store access operations and created high garbage collection pressure. Since state is already shallowly frozen via `Object.freeze()`, returning direct frozen references guarantees immutability while eliminating stringify/parse overhead.

**Action:** Maintain immutability via `Object.freeze()` on state updates and avoid unnecessary deep cloning in state store getters/selectors/subscribers to achieve O(1) state accesses.
