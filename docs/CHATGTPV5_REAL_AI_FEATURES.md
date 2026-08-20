# chatGTPV5.0 — 25 Real AI Features

This branch separates **live LLM features** from the app's existing deterministic/offline analytics. A feature is only presented as a successful live-AI result when the configured remote provider actually returns a successful model response.

## Runtime path

`AIOperationsScreen` → `AIOperationsViewModel` → `RealAIFeatureService` → existing `AiService` → configured OpenAI-compatible `/chat/completions` endpoint.

The default build configuration uses xAI/Grok through `https://api.x.ai/v1`. `XAI_API_KEY` is preferred and `LLM_API_KEY` remains supported by the existing build setup.

If the provider/key is missing or a recognized transport/auth/rate-limit failure occurs, the feature reports failure explicitly. It does **not** replace the failed model call with deterministic text and label that output as AI.

## 25 live tools

### Selected job + property history

1. Inspection Narrative Generator
2. Scope-of-Work Builder
3. Exclusion Plan Architect
4. Next-Visit Planner
5. Technician Handoff Brief
6. Customer Update Writer
7. Estimate Assumption Reviewer
8. Invoice Narrative Builder
9. Callback Root-Cause Analyst
10. Property History Synthesizer
11. Species Evidence Differential
12. Entry-Point Reasoning Assistant
13. Photo Documentation Planner
14. Jobsite Safety Brief
15. Dependent-Young Decision Support
16. Sanitation Scope Planner
17. Trap/Monitoring Strategy Planner
18. Warranty Exposure Reviewer
19. Customer Objection Coach
20. Quote Completeness Auditor

### Current portfolio

21. Daily Dispatch Intelligence Brief
22. Inventory Purchase Planner
23. Seasonal Operations Advisor
24. Technician Documentation Coach
25. Weekly Business Intelligence Brief

## Grounding

Job-specific tools use the selected Room job, same-address history where available, and recent portfolio context. Portfolio tools use the most recently updated job records. Prompts include job status, priority, customer/site information, service type, estimates/costs, schedule/completion timestamps, description, notes, photo count, assignment, and sync state.

Every prompt instructs the model to separate observed facts from inference, expose uncertainty, avoid inventing prices/permits/regulations/customer promises, and identify facts that require field verification.

## UI behavior

The AI Operations screen includes:

- provider/configuration status;
- a selected-job picker for job-scoped tools;
- all 25 tools as individual runnable controls;
- per-tool loading state;
- provider-labelled result state;
- explicit failed/not-completed state;
- rerun and clear-result controls;
- a separate section for the existing 45 offline analytics signals so deterministic calculations are not misrepresented as live LLM output.

## Tests

`RealAIFeatureCatalogTest` enforces:

- exactly 25 tools;
- unique IDs;
- unique titles;
- non-empty descriptions and executable prompt instructions;
- exactly 20 selected-job tools and 5 portfolio tools.
