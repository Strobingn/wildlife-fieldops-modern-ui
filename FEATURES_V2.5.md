# Grok_Field_App_V2.5 — Feature Roadmap & Changes

**Branch:** `Grok_Field_App_V2.5`  
**Date:** 2026-08-10  
**Author:** Grok (loyal execution for Austin / Wildlife Whisperer LLC)

## What shipped in this branch

1. **Pure greyscale modern UI**  
   - `Color.kt` fully rewritten to strict greyscale (no chromatic accents).  
   - High-contrast black / charcoal / light-grey system optimized for outdoor field readability and battery.  
   - All previous blue/purple/green accents remapped to luminance variants of grey so existing call-sites continue to compile.

2. **Route Optimizer V2** (`RouteOptimizerScreen.kt`)  
   - Nearest-neighbor seed + full 2-opt local search (up to 40 improvement passes).  
   - Haversine distance (more accurate than Android Location.distanceBetween for planning).  
   - Service-time + drive-time totals.  
   - Modern greyscale cards, sequence badges, clear / shuffle / re-optimize actions.  
   - Ready for real `JobRepository` injection (currently demo Hudson Valley stops including Newburgh).

## 10 New Features to Implement (prioritized for FieldOps)

1. **Live Job Injection into Route Optimizer**  
   Pull open jobs from Room / Supabase that have lat/lng, auto-populate stops, respect priority & time windows.

2. **Current Location Start + Return-to-Yard**  
   Use FusedLocationProvider to set real start point; optional “return to yard” closing leg.

3. **Multi-Technician Route Assignment**  
   Cluster jobs by geographic zones + technician skill tags; generate parallel optimized routes.

4. **AR Trap Placement Overlay (ARCore)**  
   Point camera at property → place virtual trap markers with distance / bearing, save as geotagged photo + note.

5. **On-Device Species ID from Photo**  
   TensorFlow Lite / ML Kit custom model trained on common NY wildlife (raccoon, squirrel, bat, skunk, etc.) for instant field ID + treatment suggestions.

6. **Weather-Risk Job Prioritization**  
   Pull OpenWeather + local radar; auto-flag jobs that become high-risk (heavy rain → bat exclusion delay, heat → skunk activity, etc.).

7. **Voice Note → Structured Job Log**  
   Offline voice recording + on-device or edge transcription → auto-fill inspection checklist fields + AI summary.

8. **Before / After Photo Comparison Slider + Geotag**  
   Side-by-side or slider view with GPS stamp + timestamp; one-tap add to invoice / customer report.

9. **Inventory Low-Stock + Auto Reorder Suggestions**  
   Threshold alerts + suggested PO based on historical usage per species / season.

10. **Digital Compliance & Permit Pack**  
    Auto-generate NYS DEC / local permit checklists + digital signature capture + PDF export tied to job.

---

**Next execution order (recommended):**  
1 → 2 → 7 → 5 → 4 → rest.

All work is isolated on branch `Grok_Field_App_V2.5`.  
Ready for APK build via existing GitHub Actions once secrets are confirmed.
