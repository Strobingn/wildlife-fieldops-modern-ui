## 2025-05-18 - Fast recursive cloning for pub/sub state stores
**Learning:** `JSON.parse(JSON.stringify(obj))` causes massive performance bottlenecks in reactive pub/sub state stores (like `createStore`) when subscribers or selectors clone state on every state change, especially when state contains large base64 data URLs or deeply nested arrays/collections.
**Action:** Use a fast recursive cloning function for plain objects, arrays, primitives, and `Date` instances to avoid JSON serialization and stringification overhead.
