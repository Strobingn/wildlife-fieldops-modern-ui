package com.strobingn.wildlifefieldops.ai.admission

/**
 * Causal explanation admission for FieldOps model releases (brief 2026-09-18).
 *
 * Distinguishes three UI objects that must not be conflated:
 * 1. Evidence visualization — where pixels/regions were salient
 * 2. Concept assertion — which visible trait the model claims is present
 * 3. Causal explanation — which trait materially affected this decision
 *
 * Only [AdmissionLabel.EXPLANATION] may use "because" language in product UI.
 * Failed faithfulness → [AdmissionLabel.REVIEW_OVERLAY] (or reject the build).
 */

enum class UiClaimKind {
    EVIDENCE_VISUALIZATION,
    CONCEPT_ASSERTION,
    CAUSAL_EXPLANATION,
}

enum class AdmissionLabel {
    /** Named traits causally affect the decision; safe for "because" copy. */
    EXPLANATION,
    /** Useful for review but not causally validated — never say "because". */
    REVIEW_OVERLAY,
    /** Do not ship explanation-bearing surfaces for this artifact. */
    REJECT,
}

enum class InterventionKind {
    MASK,
    BLUR,
    DONOR_REPLACE,
}

data class TraitDef(
    val id: String,
    val name: String,
    /** +1 = removing the trait should decrease the target-class logit. */
    val expectedDirection: Int,
)

data class ConfusionSet(
    val id: String,
    val label: String,
    val traits: List<TraitDef>,
)

data class ModelOutput(
    val targetLogit: Double,
    val topLabel: String,
    val abstained: Boolean,
    /** Traits the model (or VLM prose) currently claims as evidence. */
    val claimedTraits: Set<String>,
    val conceptScores: Map<String, Double> = emptyMap(),
)

data class InterventionTrial(
    val traitId: String,
    val kind: InterventionKind,
    val isControl: Boolean,
    val baseline: ModelOutput,
    val after: ModelOutput,
) {
    val targetLogitDelta: Double get() = after.targetLogit - baseline.targetLogit
    val claimWithdrawn: Boolean
        get() = traitId in baseline.claimedTraits && traitId !in after.claimedTraits
}

data class GateConfig(
    /** Minimum |Δlogit| on the claimed trait to count as load-bearing. */
    val minTraitAbsDelta: Double = 0.25,
    /** Claimed trait must beat matched control by at least this margin. */
    val minTraitOverControlMargin: Double = 0.15,
    /** Require this many distinct intervention kinds to agree. */
    val minAgreeingInterventionKinds: Int = 2,
    /** Fraction of claimed-trait trials that must withdraw the claim when evidence is removed. */
    val minClaimWithdrawalRate: Double = 0.8,
)

data class TraitGateResult(
    val traitId: String,
    val passed: Boolean,
    val agreeingKinds: Set<InterventionKind>,
    val meanTraitDelta: Double,
    val meanControlDelta: Double,
    val claimWithdrawalRate: Double,
    val notes: List<String>,
)

data class AdmissionDecision(
    val label: AdmissionLabel,
    val traitResults: List<TraitGateResult>,
    val reasons: List<String>,
) {
    val allowedUiClaim: UiClaimKind
        get() = when (label) {
            AdmissionLabel.EXPLANATION -> UiClaimKind.CAUSAL_EXPLANATION
            AdmissionLabel.REVIEW_OVERLAY -> UiClaimKind.EVIDENCE_VISUALIZATION
            AdmissionLabel.REJECT -> UiClaimKind.EVIDENCE_VISUALIZATION
        }
}

/**
 * Stub confusion sets for harness wiring. Replace trait names with wildlife-expert
 * ontology after the scheduled trait-definition session (brief backlog rank 1).
 */
object TraitOntologyStub {
    val confusionSets: List<ConfusionSet> = listOf(
        ConfusionSet(
            id = "bat_vs_bird_entry",
            label = "Bat vs bird entry stain",
            traits = listOf(
                TraitDef("guano_texture", "Guano texture / peppering", expectedDirection = +1),
                TraitDef("stain_shape", "Stain shape under soffit", expectedDirection = +1),
                TraitDef("feather_debris", "Feather debris present", expectedDirection = -1),
            ),
        ),
        ConfusionSet(
            id = "raccoon_vs_squirrel_damage",
            label = "Raccoon vs squirrel fascia damage",
            traits = listOf(
                TraitDef("tear_width", "Tear / hole width", expectedDirection = +1),
                TraitDef("claw_marks", "Claw rake marks", expectedDirection = +1),
                TraitDef("gnaw_edges", "Gnawed wood edges", expectedDirection = -1),
            ),
        ),
        ConfusionSet(
            id = "active_vs_historic_nest",
            label = "Active vs historic nest",
            traits = listOf(
                TraitDef("fresh_droppings", "Fresh droppings", expectedDirection = +1),
                TraitDef("heat_sheen", "Heat / moisture sheen", expectedDirection = +1),
                TraitDef("weathered_material", "Weathered nesting material", expectedDirection = -1),
            ),
        ),
    )
}

object ExplanationAdmissionGate {
    fun evaluate(
        trials: List<InterventionTrial>,
        config: GateConfig = GateConfig(),
    ): AdmissionDecision {
        require(trials.isNotEmpty()) { "trials must not be empty" }

        val byTrait = trials.groupBy { it.traitId }
        val traitResults = byTrait.map { (traitId, traitTrials) ->
            evaluateTrait(traitId, traitTrials, config)
        }

        val failing = traitResults.filterNot { it.passed }
        val reasons = mutableListOf<String>()
        val label = when {
            traitResults.isEmpty() -> {
                reasons += "No trait trials supplied"
                AdmissionLabel.REJECT
            }
            failing.isEmpty() -> {
                reasons += "All claimed traits beat matched controls across ≥${config.minAgreeingInterventionKinds} intervention kinds; claims withdraw when evidence is removed"
                AdmissionLabel.EXPLANATION
            }
            failing.size < traitResults.size -> {
                reasons += "Partial faithfulness: failed traits=${failing.map { it.traitId }}; label as review overlay, not causal explanation"
                AdmissionLabel.REVIEW_OVERLAY
            }
            else -> {
                reasons += "No claimed trait passed causal checks; do not ship explanation-bearing UI for this artifact"
                AdmissionLabel.REJECT
            }
        }

        return AdmissionDecision(label = label, traitResults = traitResults, reasons = reasons)
    }

    private fun evaluateTrait(
        traitId: String,
        trials: List<InterventionTrial>,
        config: GateConfig,
    ): TraitGateResult {
        val notes = mutableListOf<String>()
        val claimed = trials.filter { !it.isControl }
        val controls = trials.filter { it.isControl }

        if (claimed.isEmpty()) {
            return TraitGateResult(
                traitId = traitId,
                passed = false,
                agreeingKinds = emptySet(),
                meanTraitDelta = 0.0,
                meanControlDelta = 0.0,
                claimWithdrawalRate = 0.0,
                notes = listOf("No non-control trials for trait"),
            )
        }

        // Direction: removing a +1 trait should decrease target logit ⇒ delta < 0.
        // We score "effect magnitude in the load-bearing direction" as -delta for +1 traits.
        fun directedEffect(delta: Double, expectedDirection: Int): Double =
            -expectedDirection * delta

        // Infer expected direction from majority of claimed trials' baseline concept if present;
        // default +1 (removal should hurt the predicted class).
        val expectedDirection = +1

        val agreeing = mutableSetOf<InterventionKind>()
        for (kind in InterventionKind.entries) {
            val c = claimed.filter { it.kind == kind }
            val k = controls.filter { it.kind == kind }
            if (c.isEmpty() || k.isEmpty()) continue
            val traitMean = c.map { directedEffect(it.targetLogitDelta, expectedDirection) }.average()
            val controlMean = k.map { directedEffect(it.targetLogitDelta, expectedDirection) }.average()
            val strongEnough = traitMean >= config.minTraitAbsDelta
            val beatsControl = traitMean - controlMean >= config.minTraitOverControlMargin
            if (strongEnough && beatsControl) {
                agreeing += kind
            } else {
                notes += "$kind: traitMean=${"%.3f".format(traitMean)} controlMean=${"%.3f".format(controlMean)}"
            }
        }

        val withdrawalRate = claimed.count { it.claimWithdrawn }.toDouble() / claimed.size
        val meanTraitDelta = claimed.map { it.targetLogitDelta }.average()
        val meanControlDelta = if (controls.isEmpty()) 0.0 else controls.map { it.targetLogitDelta }.average()

        val kindsOk = agreeing.size >= config.minAgreeingInterventionKinds
        val withdrawOk = withdrawalRate >= config.minClaimWithdrawalRate
        if (!kindsOk) notes += "Agreeing intervention kinds ${agreeing.size} < ${config.minAgreeingInterventionKinds}"
        if (!withdrawOk) notes += "Claim withdrawal rate ${"%.2f".format(withdrawalRate)} < ${config.minClaimWithdrawalRate}"

        return TraitGateResult(
            traitId = traitId,
            passed = kindsOk && withdrawOk,
            agreeingKinds = agreeing,
            meanTraitDelta = meanTraitDelta,
            meanControlDelta = meanControlDelta,
            claimWithdrawalRate = withdrawalRate,
            notes = notes,
        )
    }
}

/**
 * Builds synthetic intervention trials for unit tests / offline harness dry-runs.
 * Production runners should call the real model on mask/blur/donor crops and map
 * results into [InterventionTrial] instead.
 */
object SyntheticInterventionFactory {
    fun faithfulTrait(
        traitId: String,
        baselineLogit: Double = 2.0,
        effect: Double = 0.8,
    ): List<InterventionTrial> {
        val baseline = ModelOutput(
            targetLogit = baselineLogit,
            topLabel = "target",
            abstained = false,
            claimedTraits = setOf(traitId),
        )
        return InterventionKind.entries.flatMap { kind ->
            listOf(
                InterventionTrial(
                    traitId = traitId,
                    kind = kind,
                    isControl = false,
                    baseline = baseline,
                    after = baseline.copy(
                        targetLogit = baselineLogit - effect,
                        claimedTraits = emptySet(),
                    ),
                ),
                InterventionTrial(
                    traitId = traitId,
                    kind = kind,
                    isControl = true,
                    baseline = baseline,
                    after = baseline.copy(
                        targetLogit = baselineLogit - 0.05,
                        claimedTraits = setOf(traitId),
                    ),
                ),
            )
        }
    }

    /** Looks persuasive (claim present) but interventions barely move the logit — sheep-pain failure mode. */
    fun inertTrait(
        traitId: String,
        baselineLogit: Double = 2.0,
    ): List<InterventionTrial> {
        val baseline = ModelOutput(
            targetLogit = baselineLogit,
            topLabel = "target",
            abstained = false,
            claimedTraits = setOf(traitId),
        )
        return InterventionKind.entries.flatMap { kind ->
            listOf(
                InterventionTrial(
                    traitId = traitId,
                    kind = kind,
                    isControl = false,
                    baseline = baseline,
                    after = baseline.copy(targetLogit = baselineLogit - 1e-4),
                ),
                InterventionTrial(
                    traitId = traitId,
                    kind = kind,
                    isControl = true,
                    baseline = baseline,
                    after = baseline.copy(targetLogit = baselineLogit - 1e-4),
                ),
            )
        }
    }
}
