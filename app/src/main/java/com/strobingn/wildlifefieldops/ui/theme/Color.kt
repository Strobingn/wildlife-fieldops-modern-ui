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

val PrimaryGreen: Color get() = pick(Color(0xFF7CB342), Color(0xFF2E7D32))
val PrimaryGreenDark: Color get() = pick(Color(0xFF558B2F), Color(0xFF1B5E20))
val PrimaryGreenLight: Color get() = pick(Color(0xFFAED581), Color(0xFF81C784))
val PrimaryContainer: Color get() = pick(Color(0xFF1E3A1A), Color(0xFFC8E6C9))
val OnPrimaryContainer: Color get() = pick(Color(0xFFDCECC8), Color(0xFF1B5E20))

val BackgroundDark: Color get() = pick(Color(0xFF0E1110), Color(0xFFF4F1EA))
val BackgroundCard: Color get() = pick(Color(0xFF171C19), Color(0xFFFFFFFF))
val BackgroundElevated: Color get() = pick(Color(0xFF1F2622), Color(0xFFF7F5F0))
val SurfaceDark: Color get() = pick(Color(0xFF171C19), Color(0xFFFFFFFF))
val SurfaceVariant: Color get() = pick(Color(0xFF2A322C), Color(0xFFE8E4DA))
val SurfaceBright: Color get() = pick(Color(0xFF3A433C), Color(0xFFFFFFFF))

val TextPrimary: Color get() = pick(Color(0xFFF3F5F1), Color(0xFF1B1D1A))
val TextSecondary: Color get() = pick(Color(0xFFB5BDB6), Color(0xFF5C615A))
val TextTertiary: Color get() = pick(Color(0xFF80887F), Color(0xFF7A7F76))

val StatusPending: Color get() = Color(0xFFFFC107)
val StatusInProgress: Color get() = Color(0xFF2196F3)
val StatusCompleted: Color get() = Color(0xFF43A047)
val StatusCancelled: Color get() = Color(0xFFE53935)
val StatusUrgent: Color get() = Color(0xFFFF6D00)

val AccentBlue: Color get() = Color(0xFF42A5F5)
val AccentPurple: Color get() = Color(0xFFAB47BC)
val AccentOrange: Color get() = Color(0xFFFF9800)
val AccentCyan: Color get() = Color(0xFF26C6DA)
val AccentPink: Color get() = Color(0xFFEC407A)
val AccentAmber: Color get() = Color(0xFFFFC107)

val BorderDark: Color get() = pick(Color(0xFF3D4A40), Color(0xFFD4CFC4))
val DividerDark: Color get() = pick(Color(0xFF2A332C), Color(0xFFE6E1D6))
val ScrimDark: Color get() = pick(Color(0xCC000000), Color(0x66000000))

val ErrorRed: Color get() = Color(0xFFE53935)
val ErrorRedDark: Color get() = Color(0xFFB71C1C)
val SuccessGreen: Color get() = Color(0xFF43A047)
val WarningYellow: Color get() = Color(0xFFFFC107)
val InfoBlue: Color get() = Color(0xFF1E88E5)

val GradientStart: Color get() = pick(Color(0xFF0E1110), Color(0xFF2E7D32))
val GradientMid: Color get() = pick(Color(0xFF1B3A22), Color(0xFF66BB6A))
val GradientEnd: Color get() = pick(Color(0xFF2E7D32), Color(0xFFA5D6A7))
