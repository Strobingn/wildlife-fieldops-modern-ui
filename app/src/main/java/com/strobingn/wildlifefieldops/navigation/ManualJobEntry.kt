package com.strobingn.wildlifefieldops.navigation

/**
 * Always-visible typed New Job entry. Home used to send Sir to [Screen.JobDictate]
 * (voice/AI), and the drawer had no create action after the Customers tab was
 * removed in #63. Every primary entry point must open the blank unified Job
 * form. Voice remains a helper, never the only path.
 */
object ManualJobEntry {
    const val ACTION_LABEL = "New Job"
    const val CREATE_JOB_ID = "new"

    enum class EntryPoint {
        HOME_FAB,
        HOME_QUICK_ACTION,
        JOBS_FAB,
        JOBS_TOP_BAR,
        DRAWER,
        SCHEDULE_FAB
    }

    /** Typed fields on the blank Job form. None of these require AI. */
    val typedFields = listOf(
        "customer name",
        "phone",
        "email",
        "service address",
        "billing address",
        "species",
        "problem",
        "notes",
        "date/time",
        "price lines",
        "totals"
    )

    fun createRoute(): String = Screen.JobForm.createRoute()

    fun destination(point: EntryPoint): String = createRoute()

    fun isManualCreateRoute(route: String): Boolean =
        route == createRoute() || route == "job_form/$CREATE_JOB_ID"

    fun isVoiceOnlyCreateRoute(route: String): Boolean =
        route == Screen.JobDictate.route
}
