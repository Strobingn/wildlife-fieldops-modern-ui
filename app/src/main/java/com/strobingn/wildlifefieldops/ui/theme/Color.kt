package com.strobingn.wildlifefieldops.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// Modern Wildlife FieldOps palette — deep navy primary, crisp neutrals,
// and high-visibility functional accents for outdoor field work.
// ============================================================================

// Primary — Deep navy blue
val PrimaryBlue = Color(0xFF1d4ed8)
val PrimaryBlueDark = Color(0xFF1e3a8a)
val PrimaryBlueLight = Color(0xFF60a5fa)
val OnPrimary = Color(0xFFffffff)

// Secondary — Cyan/sky for maps & water (kept as a cool complement)
val SecondaryCyan = Color(0xFF0891b2)
val SecondaryCyanDark = Color(0xFF0e7490)
val OnSecondary = Color(0xFFffffff)

// Tertiary — Amber/orange for warnings and highlights
val TertiaryAmber = Color(0xFFf59e0b)
val TertiaryAmberDark = Color(0xFFd97706)
val OnTertiary = Color(0xFF000000)

// Background / surface — near-black with subtle cool tint
val BackgroundDark = Color(0xFF09090b)
val SurfaceDark = Color(0xFF121214)
val SurfaceElevated = Color(0xFF18181b)
val SurfaceVariant = Color(0xFF27272a)

// Text
val TextPrimary = Color(0xFFfafafa)
val TextSecondary = Color(0xFFa1a1aa)
val TextTertiary = Color(0xFF71717a)

// Status colors
val StatusPending = Color(0xFFf59e0b)
val StatusInProgress = Color(0xFF0ea5e9)
val StatusCompleted = Color(0xFF10b981)
val StatusCancelled = Color(0xFFef4444)
val StatusUrgent = Color(0xFFdc2626)
val StatusInvoiced = Color(0xFF8b5cf6)
val StatusPaid = Color(0xFF10b981)

// Functional accent colors
val AccentBlue = Color(0xFF3b82f6)
val AccentPurple = Color(0xFF8b5cf6)
val AccentOrange = Color(0xFFf97316)
val AccentCyan = Color(0xFF06b6d4)
val AccentPink = Color(0xFFec4899)

// Border / divider
val BorderDark = Color(0xFF27272a)
val DividerDark = Color(0xFF3f3f46)

// Error / success / warning
val ErrorRed = Color(0xFFef4444)
val ErrorRedDark = Color(0xFFdc2626)
val SuccessGreen = Color(0xFF10b981)
val WarningYellow = Color(0xFFf59e0b)

// Gradient stops (used for hero cards / premium surfaces)
val GradientStart = PrimaryBlue
val GradientEnd = Color(0xFF0ea5e9)
val GradientAmberStart = TertiaryAmber
val GradientAmberEnd = Color(0xFFf97316)
val GradientDarkStart = Color(0xFF18181b)
val GradientDarkEnd = Color(0xFF09090b)

// Glassmorphism scrim
val GlassBackground = Color(0xFF121214).copy(alpha = 0.72f)
val GlassBorder = Color(0xFF3f3f46).copy(alpha = 0.35f)

// ============================================================================
// Backwards-compatible aliases for legacy screens not yet migrated.
// Prefer the modern tokens above for new work.
// ============================================================================
val PrimaryGreen = PrimaryBlue
val PrimaryGreenDark = PrimaryBlueDark
val PrimaryGreenLight = PrimaryBlueLight
val BackgroundCard = SurfaceDark
val BackgroundElevated = SurfaceElevated
