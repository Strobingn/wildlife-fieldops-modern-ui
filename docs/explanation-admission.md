# Explanation admission harness

Brief 2026-09-18 rank-1 test: causal explanation admission suite.

## What it gates

| Label | UI meaning |
|-------|------------|
| `EXPLANATION` | May say "because …" |
| `REVIEW_OVERLAY` | Heat map / VLM sentence OK if labeled as review, not causal |
| `REJECT` | Do not ship explanation-bearing UI for this artifact |

## Code

- `com.strobingn.wildlifefieldops.ai.admission.ExplanationAdmissionGate`
- Stub confusion sets: `TraitOntologyStub` (replace after wildlife expert trait session)
- Synthetic dry-run factory: `SyntheticInterventionFactory`
- Unit tests: `ExplanationAdmissionTest`

## How to admit a real build

1. Pick 3–5 confusion sets; finalize 8–12 traits with a wildlife expert.
2. For each test crop, log full frame, model crop, trait mask, model/preprocess versions, site/device/season.
3. For each claimed trait, run mask, blur, and donor-replace on the trait region; repeat on a matched control region.
4. Map each run to `InterventionTrial` (logits, top label, abstention, claimed trait set).
5. Call `ExplanationAdmissionGate.evaluate(trials)`.
6. Ship explanation UI only when label is `EXPLANATION`.

## Pass / fail (product)

**Pass:** named trait moves the decision more than matched controls in the expected direction; explanation claim withdraws when evidence is removed; behavior replicates on ≥2 intervention methods.

**Fail:** negligible/wrong-direction effect, unsupported concepts in text, local explanation depends mainly on another region, or held-out site/device fidelity collapse → `REVIEW_OVERLAY` or remove from field workflow.
