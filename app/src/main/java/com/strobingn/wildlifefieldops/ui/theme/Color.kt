package com.strobingn.wildlifefieldops.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// Grayscale Wildlife FieldOps palette — pure monochrome for field readability
// and the grayscale requirement. No color accents.
// ============================================================================

// Primary — mid gray
val PrimaryBlue = Color(0xFF6B7280)
val PrimaryBlueDark = Color(0xFF374151)
val PrimaryBlueLight = Color(0xFF9CA3AF)
val OnPrimary = Color(0xFFFFFFFF)

// Secondary — lighter gray
val SecondaryCyan = Color(0xFF9CA3AF)
val SecondaryCyanDark = Color(0xFF6B7280)
val OnSecondary = Color(0xFF000000)

// Tertiary — brighter gray for highlights
val TertiaryAmber = Color(0xFFD1D5DB)
val TertiaryAmberDark = Color(0xFF9CA3AF)
val OnTertiary = Color(0xFF000000)

// Background / surface — near-black with pure gray scale
val BackgroundDark = Color(0xFF0A0A0A)
val SurfaceDark = Color(0xFF121212)
val SurfaceElevated = Color(0xFF1A1A1A)
val SurfaceVariant = Color(0xFF262626)

// Text
val TextPrimary = Color(0xFFFAFAFA)
val TextSecondary = Color(0xFFA3A3A3)
val TextTertiary = Color(0xFF737373)

// Status colors — desaturated grayscale equivalents
val StatusPending = Color(0xFFA3A3A3)
val StatusInProgress = Color(0xFFD4D4D4)
val StatusCompleted = Color(0xFFE5E5E5)
val StatusCancelled = Color(0xFF525252)
val StatusUrgent = Color(0xFF737373)
val StatusInvoiced = Color(0xFFA3A3A3)
val StatusPaid = Color(0xFFE5E5E5)

// Functional accent colors — all gray
val AccentBlue = Color(0xFF9CA3AF)
val AccentPurple = Color(0xFFA3A3A3)
val AccentOrange = Color(0xFFD4D4D4)
val AccentCyan = Color(0xFF9CA3AF)
val AccentPink = Color(0xFFA3A3A3)

// Border / divider
val BorderDark = Color(0xFF262626)
val DividerDark = Color(0xFF404040)

// Error / success / warning — grayscale
val ErrorRed = Color(0xFF737373)
val ErrorRedDark = Color(0xFF525252)
val SuccessGreen = Color(0xFFD4D4D4)
val WarningYellow = Color(0xFFA3A3A3)

// Gradient stops
val GradientStart = PrimaryBlue
val GradientEnd = Color(0xFF9CA3AF)
val GradientAmberStart = TertiaryAmber
val GradientAmberEnd = Color(0xFFD4D4D4)
val GradientDarkStart = Color(0xFF1A1A1A)
val GradientDarkEnd = Color(0xFF0A0A0A)

// Glassmorphism scrim
val GlassBackground = Color(0xFF121212).copy(alpha = 0.72f)
val GlassBorder = Color(0xFF404040).copy(alpha = 0.35f)

// ============================================================================
// Backwards-compatible aliases
// ============================================================================
val PrimaryGreen = PrimaryBlue
val PrimaryGreenDark = PrimaryBlueDark
val PrimaryGreenLight = PrimaryBlueLight
val BackgroundCard = SurfaceDark
val BackgroundElevated = SurfaceElevated
