## 2025-05-18 - Avoid JSON Deep Cloning in Pub/Sub State Stores
**Learning:** `createStore` was calling `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call. As state grows with large collections and base64 strings, serializing the state tree on every update or getter introduces ~10-50ms main-thread blocking per operation.
**Action:** Use shallow `Object.freeze` and return state references directly instead of deep-cloning the state tree on every state access or state notification.
