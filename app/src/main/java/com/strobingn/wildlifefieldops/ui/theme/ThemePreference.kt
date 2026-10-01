package com.strobingn.wildlifefieldops.ui.theme

/**
 * User-facing appearance choice. Persisted in DataStore as [storageValue].
 * Default is [SYSTEM] so a first launch follows the device DayNight setting.
 */
enum class ThemePreference(
    val storageValue: String,
    val label: String,
    val subtitle: String
) {
    SYSTEM(
        storageValue = "system",
        label = "System default",
        subtitle = "Follow the phone light/dark setting"
    ),
    LIGHT(
        storageValue = "light",
        label = "Light",
        subtitle = "Always use the light color scheme"
    ),
    DARK(
        storageValue = "dark",
        label = "Dark",
        subtitle = "Always use the dark color scheme"
    );

    fun resolveIsDark(isSystemDark: Boolean): Boolean = when (this) {
        SYSTEM -> isSystemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStorage(raw: String?): ThemePreference =
            entries.firstOrNull { it.storageValue.equals(raw?.trim(), ignoreCase = true) }
                ?: SYSTEM

        /**
         * Resolve preference from the new `theme_mode` key, falling back to the
         * legacy `dark_theme` boolean so existing installs keep their choice.
         * Unset keys default to [SYSTEM].
         */
        fun fromPersisted(themeMode: String?, legacyDarkTheme: Boolean?): ThemePreference {
            if (!themeMode.isNullOrBlank()) return fromStorage(themeMode)
            return when (legacyDarkTheme) {
                true -> DARK
                false -> LIGHT
                null -> SYSTEM
            }
        }
    }
}
