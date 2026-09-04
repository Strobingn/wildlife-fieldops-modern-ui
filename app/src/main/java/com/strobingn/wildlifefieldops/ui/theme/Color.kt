package com.strobingn.wildlifefieldops.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Screens read these vals during composition; flipping isDark recomposes them. */
object ThemeMode {
    var isDark by mutableStateOf(true)
}

private fun pick(dark: Color, light: Color): Color = if (ThemeMode.isDark) dark else light

// Grayscale field-operations palette.
// Legacy names remain for source compatibility; every value is intentionally neutral.
val PrimaryGreen: Color get() = pick(Color(0xFFE5E5E5), Color(0xFF2A2A2A))
val PrimaryGreenDark: Color get() = pick(Color(0xFFBDBDBD), Color(0xFF1A1A1A))
val PrimaryGreenLight: Color get() = pick(Color(0xFFF5F5F5), Color(0xFF555555))
val PrimaryContainer: Color get() = pick(Color(0xFF333333), Color(0xFFE5E5E5))
val OnPrimaryContainer: Color get() = pick(Color(0xFFF5F5F5), Color(0xFF111111))

val BackgroundDark: Color get() = pick(Color(0xFF0D0D0D), Color(0xFFF6F6F6))
val BackgroundCard: Color get() = pick(Color(0xFF171717), Color(0xFFFFFFFF))
val BackgroundElevated: Color get() = pick(Color(0xFF222222), Color(0xFFF0F0F0))
val SurfaceDark: Color get() = pick(Color(0xFF171717), Color(0xFFFFFFFF))
val SurfaceVariant: Color get() = pick(Color(0xFF292929), Color(0xFFE5E5E5))
val SurfaceBright: Color get() = pick(Color(0xFF383838), Color(0xFFFFFFFF))

val TextPrimary: Color get() = pick(Color(0xFFF5F5F5), Color(0xFF111111))
val TextSecondary: Color get() = pick(Color(0xFFBDBDBD), Color(0xFF555555))
val TextTertiary: Color get() = pick(Color(0xFF858585), Color(0xFF727272))

// Status — distinguishable greyscale luminance steps (not rainbow)
val StatusPending: Color get() = Color(0xFFD6D6D6)
val StatusInProgress: Color get() = Color(0xFFB8B8B8)
val StatusCompleted: Color get() = Color(0xFFECECEC)
val StatusCancelled: Color get() = Color(0xFF8F8F8F)
val StatusUrgent: Color get() = Color(0xFFF8F8F8)

// Accents — greyscale contrast levels kept under existing names
val AccentBlue: Color get() = Color(0xFFC7C7C7)
val AccentPurple: Color get() = Color(0xFFAFAFAF)
val AccentOrange: Color get() = Color(0xFFD9D9D9)
val AccentCyan: Color get() = Color(0xFF9E9E9E)
val AccentPink: Color get() = Color(0xFFE7E7E7)
val AccentAmber: Color get() = Color(0xFFCECECE)

val BorderDark: Color get() = pick(Color(0xFF3D3D3D), Color(0xFFBDBDBD))
val DividerDark: Color get() = pick(Color(0xFF252525), Color(0xFFDADADA))
val ScrimDark: Color get() = pick(Color(0xCC000000), Color(0x66000000))

val ErrorRed: Color get() = Color(0xFFF0F0F0)
val ErrorRedDark: Color get() = Color(0xFF5C5C5C)
val SuccessGreen: Color get() = Color(0xFFF0F0F0)
val WarningYellow: Color get() = Color(0xFFD6D6D6)
val InfoBlue: Color get() = Color(0xFFB8B8B8)

val GradientStart: Color get() = pick(Color(0xFF121212), Color(0xFF2A2A2A))
val GradientMid: Color get() = pick(Color(0xFF2B2B2B), Color(0xFF6E6E6E))
val GradientEnd: Color get() = pick(Color(0xFF606060), Color(0xFFBDBDBD))
