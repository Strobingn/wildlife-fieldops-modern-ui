## 2026-09-01 - Avoid Object.entries allocation inside array filter iterations
**Learning:** Re-allocating `Object.entries(filters)` and executing `.every()` inside `Array.prototype.filter()` creates unnecessary object allocations and string transformations per item.
**Action:** Extract active filter entries and lowercase target values before filtering arrays, and use an early-exit loop inside the predicate.
