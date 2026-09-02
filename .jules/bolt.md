## 2025-05-18 - Hoisting Filter Processing and Avoiding Inner Array Allocations
**Learning:** Calling `Object.entries(filters)` and `.toLowerCase()` inside per-item callbacks in high-frequency list utilities (`filterJobs` and `searchJobs`) creates significant CPU overhead and array allocation churn during search inputs or state updates.
**Action:** Always pre-process active filter entries outside collection iterations, and prefer direct short-circuiting property checks over `.some()` loops when matching fixed sets of object properties.
