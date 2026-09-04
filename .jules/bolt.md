## 2025-05-18 - Hash Map Lookups for List Component Entity Counts
**Learning:** In Vanilla JS list component renders (`JobList.js`), scanning relational collections (`visits`, `repairs`, `photos`, `signatures`) using `Array.prototype.filter` or `some` inside each item template creates an $O(N \cdot M)$ performance bottleneck.
**Action:** Always pre-compute entity counts and presence using `Map` or `Set` indexers prior to `.map()` rendering passes to achieve $O(1)$ lookups and $O(N + M)$ overall render time.
