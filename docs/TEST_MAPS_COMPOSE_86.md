# Maps Compose 8.6 regression test branch

Isolated upgrade: `maps-compose:4.3.0` → `8.6.0`, `play-services-maps:18.2.0` → `19.0.0`. Do **not** merge with ARCore 1.56 work.

## Regression suite
- [ ] Open/close map **20** times (dashboard ↔ job ↔ map)
- [ ] Rotate + background/restore with an info window open (lifecycle crash fix)
- [ ] Completed-job markers, clustering, route lines, camera restoration
- [ ] Light/dark remain grayscale + dark-blue accents (watch `FOLLOW_SYSTEM` color scheme default)
- [ ] Zoom limits usable on job-density / route maps
- [ ] Maps API key still restricted to correct package + signing cert

Wait for stable Maps Compose **9.0** before considering 9.0.0-rc.
