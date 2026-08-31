## 2025-05-24 - Avoid `deepClone` on Every Store State Read/Write
**Learning:** Calling `JSON.parse(JSON.stringify(state))` inside state store getters (`getState`), reducers (`setState`), and selectors (`select`) incurs an $O(N)$ deep-cloning penalty on every single operation. For 100+ objects, `getState` calls can take several milliseconds each.
**Action:** Return frozen state references (`Object.freeze(next)`) instead of deep copies to achieve $O(1)$ state retrieval and state reads without sacrificing immutability guarantees.
