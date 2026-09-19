# ADR 0001 — Explanation faithfulness as a release gate

- **Status:** Accepted (FieldOps engineering brief 2026-09-18)
- **Date:** 2026-09-18

## Context

A sheep facial-pain study (arXiv:2609.20427 family) showed that attention maps, learned gates, and fluent clinical prose can look trustworthy while claimed descriptors barely move the decision (~1e-4 logit) and polarity agreement is ~32.6%. FieldOps ships species referral, welfare flags, and injury/assessment cues where "because" language can drive handling or escalation.

## Decision

1. Treat **evidence visualization**, **concept assertion**, and **causal explanation** as three distinct UI objects.
2. Only surfaces that pass [ExplanationAdmissionGate] may use causal "because" copy (`AdmissionLabel.EXPLANATION`).
3. Failures become `REVIEW_OVERLAY` (saliency/VLM text allowed if labeled as review) or `REJECT` for explanation-bearing builds.
4. Admission requires targeted interventions (mask, blur, donor-replace) with matched-area controls, claim withdrawal when evidence is removed, and agreement across ≥2 intervention kinds.

## Consequences

- New explanation-bearing model artifacts must run the admission harness before release.
- Annotation cost rises when we add real trait ontologies (expert session still required).
- Classification confidence alone is insufficient for explanation UI.

## Non-goals

- No Maps / CameraX / Room / ML Kit / ARCore / LiteRT dependency changes.
- This ADR does not import concept-bottleneck training; it gates whatever we deploy.
