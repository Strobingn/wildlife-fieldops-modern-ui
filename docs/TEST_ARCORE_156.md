# ARCore 1.56 isolated test branch

**Branch:** `test/arcore-1.56`  
**Change:** `com.google.ar:core:1.45.0` → `1.56.0`  
**Unchanged:** `compileSdk`/`targetSdk` 35, Maps Compose 4.3.0, AGP/Kotlin

## Why isolated
Do not combine with Maps Compose 8.x or SDK bumps. One variable per branch.

## Build
- Actions APK from this branch
- Install over previous build or clean install; note versionName `2.2.8-test-arcore-1.56`

## Regression checklist (metric depth / AR)
- [ ] App launches; no ARCore session crash on cold start
- [ ] AR / measurement flow opens on device with ARCore support (S24 Ultra)
- [ ] Plane detection still works outdoors + indoors
- [ ] Metric depth / distance estimate vs tape: within expected tolerance vs 1.45 baseline
- [ ] No new ANRs / native crashes in logcat (`arcore`, `libarcore`)
- [ ] Battery / thermal during 5–10 min AR session vs baseline
- [ ] App still works on a non-ARCore fallback path if any

## Pass / fail
- **Pass:** depth accuracy ≥ baseline, no new crashes
- **Fail:** crash, session fail, or depth regression → keep `1.45.0` on main feature branch

## Merge rule
Only merge into `feat/real-local-llm-ai` after checklist pass. Never merge with maps-compose-8.6 in the same PR.
