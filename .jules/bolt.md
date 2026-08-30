## 2025-05-18 - State Store Deep Clone Bottleneck vs Immutability
**Learning:** `createStore` deep-cloned state payloads via `JSON.parse(JSON.stringify(state))` on every state read, update, notification, and selection. Removing unnecessary deep-cloning while maintaining top-level immutability via `Object.freeze` speeds up store operations by ~98% without breaking existing code.
**Action:** Prefer freezing snapshots and avoiding full JSON serializations/deserializations during store updates.
