package com.strobingn.wildlifefieldops.ui.theme

/**
 * Packed ARGB (`0xAARRGGBB`) for the Wildlife Whisperer FieldOps chrome.
 * Compose [androidx.compose.ui.graphics.Color] tokens in Color.kt read these so
 * unit tests can assert WCAG contrast without pulling in Android views.
 *
 * Dark: true-black page, dark cards, white text, bright multi-color accents
 * (green primary, cyan scheduled, purple in progress, blue inspections, pink follow-ups).
 * Light: paper surfaces with the same hues at AA-safe depth.
 * Urgent is a strong red. No text or icon color is yellow, amber, gold, orange, or lime;
 * the legacy AccentOrange / AccentAmber names now hold pink and teal.
 */
object FieldSwatch {
    object Dark {
        const val Primary: Long = 0xFF4ADE80
        const val PrimaryDark: Long = 0xFF22C55E
        const val PrimaryLight: Long = 0xFF86EFAC
        const val PrimaryContainer: Long = 0xFF0F2A1A
        const val OnPrimary: Long = 0xFF03140A
        const val OnPrimaryContainer: Long = 0xFFDCFCE7
        /** Page background. True black so lifted surfaces read as cards. */
        const val Background: Long = 0xFF000000
        /** Material surface and surfaceContainer. */
        const val Surface: Long = 0xFF121212
        /** Bottom navigation bar. A step under cards, still clear of the page. */
        const val NavBar: Long = 0xFF0C0C0C
        /** Selected bottom-nav pill. Bright green and white text on this fill stay above 4.5:1. */
        const val NavIndicator: Long = 0xFF14532D
        /** Cards, search fields, and surfaceContainerHigh. */
        const val Card: Long = 0xFF1A1A1A
        const val Elevated: Long = 0xFF121212
        const val SurfaceVariant: Long = 0xFF1A1A1A
        const val SurfaceBright: Long = 0xFF262626
        const val OnBackground: Long = 0xFFFFFFFF
        const val OnSurface: Long = 0xFFFFFFFF
        const val OnSurfaceVariant: Long = 0xFFD4D4D4
        const val OnSurfaceMuted: Long = 0xFFA3A3A3
        /** #616161 clears 3:1 on the black page. */
        const val Outline: Long = 0xFF616161
        const val OutlineVariant: Long = 0xFF616161
        /** Bright red on the dark cards; a 15% badge wash still clears 4.5:1. Not yellow. */
        const val Error: Long = 0xFFF87171
        const val OnError: Long = 0xFF3B0002
        const val ErrorContainer: Long = 0xFF5C1A1A
        const val OnErrorContainer: Long = 0xFFFFDAD6
        const val StatusPending: Long = 0xFF22D3EE
        const val StatusInProgress: Long = 0xFFC084FC
        const val StatusCompleted: Long = 0xFF4ADE80
        const val StatusCancelled: Long = 0xFFF87171
        /** Light red for urgent text on dark cards. Not yellow. */
        const val StatusUrgent: Long = 0xFFFF6B6B
        /** Dark red for urgent text on the light Today hero. Not for dark cards. */
        const val OnHeroWarning: Long = 0xFF4A0C0C
        const val AccentBlue: Long = 0xFF60A5FA
        const val AccentPurple: Long = 0xFFC084FC
        const val AccentOrange: Long = 0xFFF472B6
        const val AccentCyan: Long = 0xFF22D3EE
        const val AccentPink: Long = 0xFFE879F9
        const val AccentAmber: Long = 0xFF2DD4BF
        const val Success: Long = 0xFF4ADE80
        const val GradientStart: Long = 0xFF4ADE80
        const val GradientMid: Long = 0xFF22D3EE
        const val GradientEnd: Long = 0xFF60A5FA
        const val Scrim: Long = 0xCC000000
    }

    object Light {
        const val Primary: Long = 0xFF166534
        const val PrimaryDark: Long = 0xFF14532D
        const val PrimaryLight: Long = 0xFF15803D
        const val PrimaryContainer: Long = 0xFFDCFCE7
        const val OnPrimary: Long = 0xFFFFFFFF
        const val OnPrimaryContainer: Long = 0xFF14532D
        const val Background: Long = 0xFFF5F5F5
        const val Card: Long = 0xFFFFFFFF
        const val Elevated: Long = 0xFFEEEEEE
        const val SurfaceVariant: Long = 0xFFE6E6E6
        const val SurfaceBright: Long = 0xFFFFFFFF
        const val OnBackground: Long = 0xFF1A1C1E
        const val OnSurface: Long = 0xFF1A1C1E
        const val OnSurfaceVariant: Long = 0xFF44474F
        const val OnSurfaceMuted: Long = 0xFF575B63
        const val Outline: Long = 0xFF74777F
        const val OutlineVariant: Long = 0xFFC4C4C4
        const val Error: Long = 0xFF991B1B
        const val OnError: Long = 0xFFFFFFFF
        const val ErrorContainer: Long = 0xFFFFDAD6
        const val OnErrorContainer: Long = 0xFF410002
        /** Dark enough that pending chips clear 4.5:1 on the light card wash. */
        const val StatusPending: Long = 0xFF155E75
        const val StatusInProgress: Long = 0xFF7E22CE
        const val StatusCompleted: Long = 0xFF166534
        const val StatusCancelled: Long = 0xFF991B1B
        /** Dark red for urgent text on light cards. Not yellow or orange. */
        const val StatusUrgent: Long = 0xFF93000A
        /** Light red for urgent text on the dark Today hero. Not for light cards. */
        const val OnHeroWarning: Long = 0xFFFFCDD2
        /** Dictate FAB fill. Distinct from PrimaryContainer so the label stays AA. */
        const val SecondaryContainer: Long = 0xFFBBF7D0
        const val AccentBlue: Long = 0xFF1D4ED8
        const val AccentPurple: Long = 0xFF7E22CE
        const val AccentOrange: Long = 0xFFBE185D
        const val AccentCyan: Long = 0xFF0E7490
        const val AccentPink: Long = 0xFFA21CAF
        const val AccentAmber: Long = 0xFF0F766E
        const val Success: Long = 0xFF166534
        const val GradientStart: Long = 0xFF166534
        const val GradientMid: Long = 0xFF155E75
        const val GradientEnd: Long = 0xFF1E3A8A
        const val Scrim: Long = 0x66000000
    }

    const val OverlayOnDark: Long = 0xFFF5F5F5
    const val OverlayOnDarkMuted: Long = 0xFFBDBDBD
    const val OverlayScrim: Long = 0xC8000000
    const val Paper: Long = 0xFFFFFFFF
    const val OnPaper: Long = 0xFF111111
}
