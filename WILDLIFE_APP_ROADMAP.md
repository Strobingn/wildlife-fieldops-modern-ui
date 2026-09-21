# Wildlife FieldOps / Wildlife Whisperer — Product Roadmap

This document lists proposed product work to move Wildlife FieldOps (Wildlife Whisperer) from a capable field-operations app to a category-leading wildlife-removal platform. It is a planning roadmap, not a commitment to scope or sequence. The four items below extend existing capabilities: offline Room storage and Supabase sync, maps/GPS, on-device TFLite classification and ObservationEvent capture, county-oriented commercial workflows, and the on-device ASR / transcript pipeline (ADR 0003).

## 1. Offline-first mapping

Field technicians often lose cellular coverage on rural routes. The app should cache map tiles and service-area snapshots so job locations and observation pins remain usable without a network. Techs log observations with photos and GPS while offline; those records stay in the local Room store and sync through the existing Supabase path when the device is back in range. This builds on the current offline-first database and map/GPS screens rather than introducing a separate mapping stack.

## 2. On-device species recognition

A technician should be able to photograph a plant or animal and receive an identification on the device, without waiting on a cloud model. Inference should run against the existing TFLite / CameraX pipeline and write results as immutable ObservationEvents so later, higher-quality evidence is not overwritten. Confidence, safety notes, and a technician confirmation step remain required before an ID is treated as operational.

## 3. County-level reporting dashboards

Observation events and completed jobs should roll up into county-scoped dashboards that agencies and municipalities can actually use: volume by species, repeat sites, response times, and seasonal trends. Aggregates should be derived from the immutable event log rather than mutable job fields so reconnect-out-of-order uploads do not distort counts. The commercial target is reporting products counties will pay for, not a generic analytics page.

## 4. Voice-first logging mode

Hands-free capture matters when a technician is on a ladder or handling an animal. Voice-first mode should accept a dictated observation, transcribe it with the existing on-device ASR stack, and file the note against the current job or ObservationEvent. The fail-closed transcript state machine (ADR 0003) stays in force: empty or untrusted transcripts must not be committed. Original audio and an editable transcript should remain attached for later review.
