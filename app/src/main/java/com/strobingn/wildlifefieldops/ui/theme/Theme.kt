package com.strobingn.wildlifefieldops.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Grok_Field_App_V2.5 — forced greyscale dark-first theme
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color(0xFF0A0A0A),
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = AccentCyan,
    onSecondary = Color(0xFF0A0A0A),
    secondaryContainer = Color(0xFF1A1A1A),
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = AccentAmber,
    onTertiary = Color(0xFF0A0A0A),
    tertiaryContainer = Color(0xFF1E1E1E),
    onTertiaryContainer = Color(0xFFCFCFCF),
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceBright = SurfaceBright,
    surfaceContainerLowest = BackgroundDark,
    surfaceContainerLow = BackgroundCard,
    surfaceContainer = BackgroundElevated,
    surfaceContainerHigh = SurfaceVariant,
    surfaceContainerHighest = SurfaceBright,
    error = ErrorRed,
    onError = Color(0xFF0A0A0A),
    errorContainer = Color(0xFF1A1A1A),
    onErrorContainer = Color(0xFFE0E0E0),
    outline = BorderDark,
    outlineVariant = DividerDark,
    scrim = ScrimDark,
    inverseSurface = Color(0xFFE8E8E8),
    inverseOnSurface = Color(0xFF121212),
    inversePrimary = PrimaryGreenDark
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A1A1A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = Color(0xFF0A0A0A),
    secondary = Color(0xFF424242),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5F5F5),
    onSecondaryContainer = Color(0xFF1A1A1A),
    tertiary = Color(0xFF616161),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEEEEEE),
    onTertiaryContainer = Color(0xFF212121),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF0A0A0A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFF424242),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFEEEEEE),
    surfaceContainerHigh = Color(0xFFE0E0E0),
    surfaceContainerHighest = Color(0xFFBDBDBD),
    error = Color(0xFF616161),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFEEEEEE),
    onErrorContainer = Color(0xFF212121),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFE0E0E0),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF212121),
    inverseOnSurface = Color(0xFFF5F5F5),
    inversePrimary = Color(0xFFBDBDBD)
)

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun WildlifeFieldOpsTheme(
    darkTheme: Boolean = true, // Field ops prefers dark; greyscale V2.5 forces high-contrast dark
    dynamicColor: Boolean = false, // disabled — pure greyscale only
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val window = activity.window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.surfaceContainerLow.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
