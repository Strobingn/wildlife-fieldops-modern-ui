## 2025-05-18 - Eliminating `deepClone` on Pub/Sub State Store Reads and Updates
**Learning:** In a central JS pub/sub store with large collections, performing `deepClone` (`JSON.parse(JSON.stringify(state))`) on every `getState()`, `setState()`, `subscribe()`, and `select()` call creates massive serialization overhead (~800ms per 1000 ops). `Object.freeze` provides immutability protection at top-level state without cloning overhead.
**Action:** Prefer `Object.freeze` or shallow structural updates over `deepClone` on frequent state store access paths.

## 2026-03-30 - Pre-normalizing Customer Fields and Length Ratio Pruning for Duplicate Detection
**Learning:** Performing string normalization (regex digit removal, lowercasing, non-alphanumeric replacement) inside nested O(N²) comparison loops creates massive regex/string instantiation overhead (~2.2s for 1,000 records). Pre-computing normalized fields in an O(N) map step along with early length-ratio pruning (`minLen / maxLen <= 0.8`) reduces duplicate detection runtime by ~85% (~330ms for 1,000 records).
**Action:** Pre-compute field transformations outside N² comparison loops and use fast mathematical lower-bound guards before running inner loop element checks.
