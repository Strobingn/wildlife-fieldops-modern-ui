## 2025-05-18 - Avoid Redundant State Cloning in Central Store

**Learning:** In pub/sub store implementations like `createStore`, calling `deepClone` (`JSON.parse(JSON.stringify(state))`) inside `getState()`, `setState()`, `subscribe()`, and `select()` causes an O(N) object graph serialization overhead on every state mutation or read. Using `Object.freeze` on state assignment guarantees immutability while providing O(1) state reads and notifications.

**Action:** Prefer `Object.freeze` for state store immutability over defensive `deepClone` calls on read/subscribe operations.
