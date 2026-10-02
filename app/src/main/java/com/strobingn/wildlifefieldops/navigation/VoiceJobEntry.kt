package com.strobingn.wildlifefieldops.navigation

/**
 * Visible voice New Job entry. #71 moved every primary create action to the
 * blank typed form so Sir could work without AI, but that hid [Screen.JobDictate].
 * Voice stays a helper: record → transcript → AI fill → save. Typed New Job
 * remains the default FAB.
 */
object VoiceJobEntry {
    const val ACTION_LABEL = "Dictate job"
    const val TYPE_MANUALLY_LABEL = "Type manually"

    fun createRoute(): String = Screen.JobDictate.route

    fun isVoiceCreateRoute(route: String): Boolean =
        route == Screen.JobDictate.route || ManualJobEntry.isVoiceOnlyCreateRoute(route)

    fun manualFallbackRoute(): String = ManualJobEntry.createRoute()
}
