package com.strobingn.wildlifefieldops.ai.fieldops

data class NextStepInput(
    val status: String = "",
    val species: String = "",
    val jobType: String = "",
    val notes: String = "",
    val description: String = "",
    val hasInspection: Boolean = false,
    val inspectionFollowUp: Boolean = false,
    val estimatedValue: Double = 0.0,
    val invoiced: Boolean = false,
    val paid: Boolean = false,
    val now: Long = System.currentTimeMillis()
)

object NextStepAttribution {
    /**
     * Keep an accepted suggestion's source. A later edit, or text that no longer
     * matches that suggestion, is manual. A blank step clears the source.
     */
    fun sourceForSave(
        typed: String,
        savedText: String,
        savedSource: String,
        acceptedSuggestion: String?,
        acceptedSource: String?
    ): String {
        val text = typed.trim()
        if (text.isEmpty()) return ""
        val accepted = acceptedSuggestion?.trim().orEmpty()
        if (accepted.isNotEmpty() && text == accepted && !acceptedSource.isNullOrBlank()) return acceptedSource
        if (text == savedText.trim() && savedSource.isNotBlank()) return savedSource
        return "manual"
    }
}

data class NextStepDraft(
    val text: String,
    val dueAt: Long?,
    val source: AiRuntimeMode = AiRuntimeMode.HEURISTIC
)

object JobNextStepEngine {

    private const val DAY = 86_400_000L

    fun suggest(input: NextStepInput): NextStepDraft {
        val species = input.species.ifBlank { input.jobType }.lowercase()
        val notes = (input.notes + " " + input.description).lowercase()
        val status = input.status.uppercase()

        return when {
            input.paid -> draft("Job paid — file photos and close the warranty card.", null)
            input.invoiced -> draft("Follow up on unpaid invoice (call / text, then mark paid).", input.now + 2 * DAY)
            status == "CANCELLED" -> draft("If the customer still wants help, reopen and reschedule.", null)
            status == "COMPLETED" && notes.contains("warranty") ->
                draft("Schedule the warranty walk-through and photograph the sealed entries.", input.now + 14 * DAY)
            status == "COMPLETED" ->
                draft("Send the invoice and note the warranty start date.", input.now + DAY)
            input.inspectionFollowUp ->
                draft("Complete the promised follow-up inspection and update findings.", input.now + 2 * DAY)
            notes.contains("trap") || species.contains("raccoon") || species.contains("skunk") ||
                species.contains("groundhog") ->
                draft(
                    "Check every set trap (NY: typically within 24 hours). Log catch/empty and reset bait.",
                    input.now + DAY
                )
            species.contains("bat") ->
                draft(
                    "Confirm vacancy / maternity timing, then install one-ways — do not seal an occupied roost.",
                    input.now + 2 * DAY
                )
            !input.hasInspection ->
                draft("Walk the property, shoot entry/damage photos, and write the inspection narrative.", input.now + DAY)
            input.estimatedValue <= 0.0 ->
                draft("Turn the inspection into a written estimate (line items + tax) and send it.", input.now + DAY)
            status == "PENDING" ->
                draft("Confirm access, load materials from the estimate, and start the job.", input.now + DAY)
            else ->
                draft("Finish exclusion, bag contaminated material, and photograph the closed openings.", input.now + DAY)
        }
    }

    private fun draft(text: String, dueAt: Long?) = NextStepDraft(text = text, dueAt = dueAt)
}
