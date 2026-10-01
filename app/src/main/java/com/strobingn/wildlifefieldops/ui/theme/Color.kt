package com.strobingn.wildlifefieldops.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Screens read these vals during composition; flipping isDark recomposes them. */
object ThemeMode {
    var isDark by mutableStateOf(true)
}

private fun pick(dark: Long, light: Long): Color =
    Color(if (ThemeMode.isDark) dark else light)

private fun swatch(argb: Long): Color = Color(argb)

// Field-ops chrome is greyscale. Legacy name PrimaryGreen kept for source
// compatibility — values are neutral gray (dark gray in light, light gray in dark).
val PrimaryGreen: Color get() = pick(FieldSwatch.Dark.Primary, FieldSwatch.Light.Primary)
val PrimaryGreenDark: Color get() = pick(FieldSwatch.Dark.PrimaryDark, FieldSwatch.Light.PrimaryDark)
val PrimaryGreenLight: Color get() = pick(FieldSwatch.Dark.PrimaryLight, FieldSwatch.Light.PrimaryLight)
val PrimaryContainer: Color get() = pick(FieldSwatch.Dark.PrimaryContainer, FieldSwatch.Light.PrimaryContainer)
val OnPrimaryContainer: Color get() = pick(FieldSwatch.Dark.OnPrimaryContainer, FieldSwatch.Light.OnPrimaryContainer)
/** Text/icons on primary fills — white on dark gray (light theme), near-black on light gray (dark theme). */
val OnPrimary: Color get() = pick(FieldSwatch.Dark.OnPrimary, FieldSwatch.Light.OnPrimary)

val BackgroundDark: Color get() = pick(FieldSwatch.Dark.Background, FieldSwatch.Light.Background)
val BackgroundCard: Color get() = pick(FieldSwatch.Dark.Card, FieldSwatch.Light.Card)
val BackgroundElevated: Color get() = pick(FieldSwatch.Dark.Elevated, FieldSwatch.Light.Elevated)
val SurfaceDark: Color get() = pick(FieldSwatch.Dark.Card, FieldSwatch.Light.Card)
val SurfaceVariant: Color get() = pick(FieldSwatch.Dark.SurfaceVariant, FieldSwatch.Light.SurfaceVariant)
val SurfaceBright: Color get() = pick(FieldSwatch.Dark.SurfaceBright, FieldSwatch.Light.SurfaceBright)

val TextPrimary: Color get() = pick(FieldSwatch.Dark.OnSurface, FieldSwatch.Light.OnSurface)
val TextSecondary: Color get() = pick(FieldSwatch.Dark.OnSurfaceVariant, FieldSwatch.Light.OnSurfaceVariant)
val TextTertiary: Color get() = pick(FieldSwatch.Dark.OnSurfaceMuted, FieldSwatch.Light.OnSurfaceMuted)

val StatusPending: Color get() = pick(FieldSwatch.Dark.StatusPending, FieldSwatch.Light.StatusPending)
val StatusInProgress: Color get() = pick(FieldSwatch.Dark.StatusInProgress, FieldSwatch.Light.StatusInProgress)
val StatusCompleted: Color get() = pick(FieldSwatch.Dark.StatusCompleted, FieldSwatch.Light.StatusCompleted)
val StatusCancelled: Color get() = pick(FieldSwatch.Dark.StatusCancelled, FieldSwatch.Light.StatusCancelled)
val StatusUrgent: Color get() = pick(FieldSwatch.Dark.StatusUrgent, FieldSwatch.Light.StatusUrgent)

val AccentBlue: Color get() = pick(FieldSwatch.Dark.AccentBlue, FieldSwatch.Light.AccentBlue)
val AccentPurple: Color get() = pick(FieldSwatch.Dark.AccentPurple, FieldSwatch.Light.AccentPurple)
val AccentOrange: Color get() = pick(FieldSwatch.Dark.AccentOrange, FieldSwatch.Light.AccentOrange)
val AccentCyan: Color get() = pick(FieldSwatch.Dark.AccentCyan, FieldSwatch.Light.AccentCyan)
val AccentPink: Color get() = pick(FieldSwatch.Dark.AccentPink, FieldSwatch.Light.AccentPink)
val AccentAmber: Color get() = pick(FieldSwatch.Dark.AccentAmber, FieldSwatch.Light.AccentAmber)

val BorderDark: Color get() = pick(FieldSwatch.Dark.Outline, FieldSwatch.Light.Outline)
val DividerDark: Color get() = pick(FieldSwatch.Dark.OutlineVariant, FieldSwatch.Light.OutlineVariant)
val ScrimDark: Color get() = pick(FieldSwatch.Dark.Scrim, FieldSwatch.Light.Scrim)

val ErrorRed: Color get() = pick(FieldSwatch.Dark.Error, FieldSwatch.Light.Error)
val ErrorRedDark: Color get() = pick(FieldSwatch.Dark.ErrorContainer, FieldSwatch.Light.Error)
val SuccessGreen: Color get() = pick(FieldSwatch.Dark.Success, FieldSwatch.Light.Success)
val WarningYellow: Color get() = pick(FieldSwatch.Dark.StatusUrgent, FieldSwatch.Light.StatusUrgent)
val InfoBlue: Color get() = pick(FieldSwatch.Dark.AccentBlue, FieldSwatch.Light.AccentBlue)

val GradientStart: Color get() = pick(FieldSwatch.Dark.GradientStart, FieldSwatch.Light.GradientStart)
val GradientMid: Color get() = pick(FieldSwatch.Dark.GradientMid, FieldSwatch.Light.GradientMid)
val GradientEnd: Color get() = pick(FieldSwatch.Dark.GradientEnd, FieldSwatch.Light.GradientEnd)

/** Camera / AR / map HUDs sit on video or tiles — always light-on-dark, independent of app theme. */
val OverlayOnDark: Color get() = swatch(FieldSwatch.OverlayOnDark)
val OverlayOnDarkMuted: Color get() = swatch(FieldSwatch.OverlayOnDarkMuted)
val OverlayScrim: Color get() = swatch(FieldSwatch.OverlayScrim)

/** Invoice/inspection PDF paper and on-screen signature pads stay white regardless of theme. */
val PaperWhite: Color get() = swatch(FieldSwatch.Paper)
val OnPaper: Color get() = swatch(FieldSwatch.OnPaper)
