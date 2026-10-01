package com.strobingn.wildlifefieldops.ai.fieldops

data class InspectionEvidence(
    val customerName: String = "",
    val jobTitle: String = "",
    val jobAddress: String = "",
    val jobType: String = "",
    val jobNotes: String = "",
    val existingFindings: String = "",
    val existingRecommendations: String = "",
    val existingSpecies: String = "",
    val existingEntryPoints: String = "",
    val existingDamage: String = "",
    val existingNotes: String = "",
    val photoTags: List<String> = emptyList(),
    val photoNotes: List<String> = emptyList()
)

data class InspectionNarrativeDraft(
    val findings: String = "",
    val recommendations: String = "",
    val speciesIdentified: String = "",
    val entryPoints: String = "",
    val damageAssessment: String = "",
    val notes: String = "",
    val source: AiRuntimeMode = AiRuntimeMode.HEURISTIC
)

/**
 * Offline-first inspection narrative. Used when cloud/on-device LLM is down,
 * and as a structured fallback if the model returns unusable JSON.
 */
object InspectionNarrativeEngine {

    fun draft(evidence: InspectionEvidence): InspectionNarrativeDraft {
        val species = firstNonBlank(
            evidence.existingSpecies,
            guessSpecies(evidence)
        )
        val entries = firstNonBlank(
            evidence.existingEntryPoints,
            guessEntries(evidence)
        )
        val damage = firstNonBlank(
            evidence.existingDamage,
            guessDamage(evidence)
        )
        val findings = buildFindings(evidence, species, entries, damage)
        val recommendations = buildRecommendations(species, entries, damage)
        val notes = buildString {
            if (evidence.photoNotes.any { it.isNotBlank() }) {
                append("Photo notes: ")
                append(evidence.photoNotes.filter { it.isNotBlank() }.take(4).joinToString("; "))
            }
            if (evidence.jobNotes.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append("Job notes: ")
                append(evidence.jobNotes.trim().take(400))
            }
        }
        return InspectionNarrativeDraft(
            findings = findings,
            recommendations = recommendations,
            speciesIdentified = species,
            entryPoints = entries,
            damageAssessment = damage,
            notes = notes,
            source = AiRuntimeMode.HEURISTIC
        )
    }

    fun evidenceTranscript(evidence: InspectionEvidence): String = buildString {
        appendLine("Job: ${evidence.jobTitle.ifBlank { "(untitled)" }}")
        appendLine("Address: ${evidence.jobAddress.ifBlank { "(none)" }}")
        appendLine("Service / species hint: ${evidence.jobType.ifBlank { "(none)" }}")
        if (evidence.jobNotes.isNotBlank()) appendLine("Job notes: ${evidence.jobNotes}")
        if (evidence.existingFindings.isNotBlank()) appendLine("Typed findings: ${evidence.existingFindings}")
        if (evidence.existingRecommendations.isNotBlank()) appendLine("Typed recommendations: ${evidence.existingRecommendations}")
        if (evidence.existingSpecies.isNotBlank()) appendLine("Typed species: ${evidence.existingSpecies}")
        if (evidence.existingEntryPoints.isNotBlank()) appendLine("Typed entry points: ${evidence.existingEntryPoints}")
        if (evidence.existingDamage.isNotBlank()) appendLine("Typed damage: ${evidence.existingDamage}")
        if (evidence.existingNotes.isNotBlank()) appendLine("Typed notes: ${evidence.existingNotes}")
        if (evidence.photoTags.isNotEmpty()) {
            appendLine("Photo tags: ${evidence.photoTags.distinct().joinToString()}")
        }
        evidence.photoNotes.filter { it.isNotBlank() }.forEach { appendLine("Photo note: $it") }
        appendLine("Write a professional NY wildlife-control inspection narrative from this evidence only.")
    }

    fun apply(
        current: InspectionNarrativeDraft,
        suggested: InspectionNarrativeDraft,
        replace: Boolean
    ): InspectionNarrativeDraft = InspectionNarrativeDraft(
        findings = OperatorWins.text(current.findings, suggested.findings, replace),
        recommendations = OperatorWins.text(current.recommendations, suggested.recommendations, replace),
        speciesIdentified = OperatorWins.text(current.speciesIdentified, suggested.speciesIdentified, replace),
        entryPoints = OperatorWins.text(current.entryPoints, suggested.entryPoints, replace),
        damageAssessment = OperatorWins.text(current.damageAssessment, suggested.damageAssessment, replace),
        notes = OperatorWins.text(current.notes, suggested.notes, replace),
        source = suggested.source
    )

    private fun guessSpecies(evidence: InspectionEvidence): String {
        val blob = blob(evidence)
        val hits = SPECIES.filter { (key, _) -> blob.contains(key) }.map { it.second }.distinct()
        return hits.joinToString(", ").ifBlank { evidence.jobType.takeIf { it.isNotBlank() && it.length <= 40 }.orEmpty() }
    }

    private fun guessEntries(evidence: InspectionEvidence): String {
        val blob = blob(evidence)
        val hits = ENTRIES.filter { blob.contains(it.first) }.map { it.second }.distinct()
        return hits.joinToString("; ")
    }

    private fun guessDamage(evidence: InspectionEvidence): String {
        val blob = blob(evidence)
        val hits = DAMAGE.filter { blob.contains(it.first) }.map { it.second }.distinct()
        return hits.joinToString("; ")
    }

    private fun buildFindings(
        evidence: InspectionEvidence,
        species: String,
        entries: String,
        damage: String
    ): String = buildString {
        append("Inspection at ")
        append(evidence.jobAddress.ifBlank { evidence.customerName.ifBlank { "the property" } })
        append('.')
        if (species.isNotBlank()) {
            append(" Evidence is consistent with ")
            append(species)
            append(" activity")
            append('.')
        }
        if (entries.isNotBlank()) {
            append(" Likely entry / access: ")
            append(entries)
            append('.')
        }
        if (damage.isNotBlank()) {
            append(" Observed damage / sign: ")
            append(damage)
            append('.')
        }
        if (evidence.photoTags.isNotEmpty()) {
            append(" Photo tags reviewed: ")
            append(evidence.photoTags.distinct().take(8).joinToString())
            append('.')
        }
        append(" Confirm occupancy, young, and access on site before exclusion or trapping.")
    }

    private fun buildRecommendations(species: String, entries: String, damage: String): String {
        val key = species.lowercase()
        val specific = when {
            key.contains("bat") ->
                "Do not seal roosts during maternity season. Install one-way exclusion after confirmed vacancy, then cap vents and chimney."
            key.contains("raccoon") ->
                "Inspect attic/chimney for young. Set a cage trap at the active run, then repair the entry with flashing and hardware cloth."
            key.contains("squirrel") ->
                "Use a one-way door on the active soffit/fascia hole after checking for nest young. Screen vents and patch chew."
            key.contains("skunk") ->
                "Trap at the den under the deck/shed. After capture, bury hardware cloth 12 in. out and 6 in. down."
            key.contains("rat") || key.contains("mouse") || key.contains("rodent") ->
                "Sanitation + snap program at harborage. Seal dime-sized gaps with steel wool and metal."
            else ->
                "Document the active opening, remove the animal humanely per NY DEC rules, then close every gap with wildlife-rated materials."
        }
        return buildString {
            append(specific)
            if (entries.isNotBlank()) {
                append(" Priority openings: ")
                append(entries)
                append('.')
            }
            if (damage.isNotBlank()) {
                append(" Repair scope should include: ")
                append(damage)
                append('.')
            }
        }
    }

    private fun blob(evidence: InspectionEvidence): String =
        listOf(
            evidence.jobTitle,
            evidence.jobType,
            evidence.jobNotes,
            evidence.existingFindings,
            evidence.existingSpecies,
            evidence.existingEntryPoints,
            evidence.existingDamage,
            evidence.existingNotes,
            evidence.photoTags.joinToString(" "),
            evidence.photoNotes.joinToString(" ")
        ).joinToString(" ").lowercase()

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }.orEmpty()

    private val SPECIES = listOf(
        "raccoon" to "raccoon",
        "squirrel" to "squirrel",
        "flying squirrel" to "flying squirrel",
        "bat" to "bat",
        "skunk" to "skunk",
        "opossum" to "opossum",
        "groundhog" to "groundhog",
        "woodchuck" to "groundhog",
        "rat" to "rat",
        "mouse" to "mouse",
        "snake" to "snake",
        "pigeon" to "pigeon"
    )

    private val ENTRIES = listOf(
        "soffit" to "soffit gap",
        "fascia" to "fascia chew",
        "ridge vent" to "ridge vent",
        "gable" to "gable vent",
        "chimney" to "chimney / damper",
        "dryer" to "dryer vent",
        "crawl" to "crawlspace opening",
        "deck" to "under-deck den",
        "roof" to "roof / flashing",
        "louver" to "attic louver"
    )

    private val DAMAGE = listOf(
        "guano" to "guano / droppings",
        "droppings" to "droppings",
        "urine" to "urine staining",
        "chew" to "chew damage",
        "gnaw" to "gnaw marks",
        "insulation" to "disturbed insulation",
        "nest" to "nesting material",
        "wiring" to "chewed wiring",
        "stain" to "staining"
    )
}
