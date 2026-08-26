## 2025-05-18 - Eliminating Redundant Deep-Cloning in Reactive State Stores
**Learning:** Calling `deepClone` (`JSON.parse(JSON.stringify(state))`) on every state read (`getState`, `select`) and subscription event converts $O(1)$ state accesses into expensive $O(N)$ operations that scale poorly as offline state grows.
**Action:** Use top-level `Object.freeze` for state snapshots in custom JS store implementations to guarantee immutability without incurring recursive serialization overhead.
