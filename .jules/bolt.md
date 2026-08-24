## 2026-08-24 - Avoid `deepClone` inside reactive store primitives
**Learning:** `JSON.parse(JSON.stringify())` inside store getters/reducers (`getState()`, `setState()`, `subscribe()`, `select()`) adds non-trivial O(N) serialization latency whenever state components update or are accessed. Enforcing state immutability via `Object.freeze()` prevents illegal direct state mutations while avoiding deep cloning overhead entirely.
**Action:** Use shallow copy + `Object.freeze()` for state objects in simple pub/sub stores to eliminate deep-clone bottlenecks during state operations.
