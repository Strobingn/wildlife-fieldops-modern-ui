package com.strobingn.wildlifefieldops.ui.theme

/**
 * Packed ARGB (`0xAARRGGBB`) for the Wildlife Whisperer FieldOps chrome.
 * Compose [androidx.compose.ui.graphics.Color] tokens in Color.kt read these so
 * unit tests can assert WCAG contrast without pulling in Android views.
 *
 * Dark: greyscale chrome + navy accents.
 * Light: paper surfaces, navy primary, AA-safe body/status text.
 */
object FieldSwatch {
    object Dark {
        const val Primary: Long = 0xFF1565C0
        const val PrimaryDark: Long = 0xFF0D47A1
        const val PrimaryLight: Long = 0xFF42A5F5
        const val PrimaryContainer: Long = 0xFF0A2744
        const val OnPrimaryContainer: Long = 0xFFD6E4F5
        const val Background: Long = 0xFF0D0D0D
        const val Card: Long = 0xFF171717
        const val Elevated: Long = 0xFF222222
        const val SurfaceVariant: Long = 0xFF292929
        const val SurfaceBright: Long = 0xFF383838
        const val OnBackground: Long = 0xFFF5F5F5
        const val OnSurface: Long = 0xFFF5F5F5
        const val OnSurfaceVariant: Long = 0xFFBDBDBD
        const val OnSurfaceMuted: Long = 0xFFB0B0B0
        const val Outline: Long = 0xFF3D3D3D
        const val OutlineVariant: Long = 0xFF252525
        const val Error: Long = 0xFFEF9A9A
        const val OnError: Long = 0xFF3B0002
        const val ErrorContainer: Long = 0xFF5C1A1A
        const val OnErrorContainer: Long = 0xFFFFDAD6
        const val StatusPending: Long = 0xFFD6D6D6
        const val StatusInProgress: Long = 0xFF90CAF9
        const val StatusCompleted: Long = 0xFFA5D6A7
        const val StatusCancelled: Long = 0xFFEF9A9A
        const val StatusUrgent: Long = 0xFFFFCC80
        const val AccentBlue: Long = 0xFF64B5F6
        const val AccentPurple: Long = 0xFF90CAF9
        const val AccentOrange: Long = 0xFF42A5F5
        const val AccentCyan: Long = 0xFF4FC3F7
        const val AccentPink: Long = 0xFF90A4AE
        const val AccentAmber: Long = 0xFF90CAF9
        const val Success: Long = 0xFF81C784
        const val GradientStart: Long = 0xFF0A1628
        const val GradientMid: Long = 0xFF0D47A1
        const val GradientEnd: Long = 0xFF1565C0
        const val Scrim: Long = 0xCC000000
    }

    object Light {
        const val Primary: Long = 0xFF0D47A1
        const val PrimaryDark: Long = 0xFF002171
        const val PrimaryLight: Long = 0xFF1565C0
        const val PrimaryContainer: Long = 0xFFD6E4F5
        const val OnPrimaryContainer: Long = 0xFF0A2744
        const val Background: Long = 0xFFF4F6F8
        const val Card: Long = 0xFFFFFFFF
        const val Elevated: Long = 0xFFEEF1F4
        const val SurfaceVariant: Long = 0xFFE3E7ED
        const val SurfaceBright: Long = 0xFFFFFFFF
        const val OnBackground: Long = 0xFF1A1C1E
        const val OnSurface: Long = 0xFF1A1C1E
        const val OnSurfaceVariant: Long = 0xFF44474F
        const val OnSurfaceMuted: Long = 0xFF575B63
        const val Outline: Long = 0xFF74777F
        const val OutlineVariant: Long = 0xFFC4C6D0
        const val Error: Long = 0xFFBA1A1A
        const val OnError: Long = 0xFFFFFFFF
        const val ErrorContainer: Long = 0xFFFFDAD6
        const val OnErrorContainer: Long = 0xFF410002
        const val StatusPending: Long = 0xFF5F6368
        const val StatusInProgress: Long = 0xFF0D47A1
        const val StatusCompleted: Long = 0xFF1B5E20
        const val StatusCancelled: Long = 0xFFBA1A1A
        const val StatusUrgent: Long = 0xFF9A3412
        const val AccentBlue: Long = 0xFF0D47A1
        const val AccentPurple: Long = 0xFF1565C0
        const val AccentOrange: Long = 0xFF0D47A1
        const val AccentCyan: Long = 0xFF01579B
        const val AccentPink: Long = 0xFF3949AB
        const val AccentAmber: Long = 0xFF0D47A1
        const val Success: Long = 0xFF1B5E20
        const val GradientStart: Long = 0xFF0D47A1
        const val GradientMid: Long = 0xFF1565C0
        const val GradientEnd: Long = 0xFF1976D2
        const val Scrim: Long = 0x66000000
    }

    const val OnPrimary: Long = 0xFFFFFFFF
    const val OverlayOnDark: Long = 0xFFF5F5F5
    const val OverlayOnDarkMuted: Long = 0xFFBDBDBD
    const val OverlayScrim: Long = 0xC8000000
    const val Paper: Long = 0xFFFFFFFF
    const val OnPaper: Long = 0xFF111111
}
