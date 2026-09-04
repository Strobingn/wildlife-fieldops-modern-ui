package com.strobingn.wildlifefieldops.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE5E5E5),
    onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = Color(0xFFF5F5F5),
    secondary = Color(0xFFC7C7C7),
    onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0xFF292929),
    onSecondaryContainer = Color(0xFFF5F5F5),
    tertiary = Color(0xFFCECECE),
    onTertiary = Color(0xFF111111),
    tertiaryContainer = Color(0xFF292929),
    onTertiaryContainer = Color(0xFFF5F5F5),
    background = Color(0xFF0D0D0D),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF171717),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF292929),
    onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceBright = Color(0xFF383838),
    surfaceContainerLowest = Color(0xFF0D0D0D),
    surfaceContainerLow = Color(0xFF171717),
    surfaceContainer = Color(0xFF222222),
    surfaceContainerHigh = Color(0xFF292929),
    surfaceContainerHighest = Color(0xFF383838),
    error = Color(0xFFF0F0F0),
    onError = Color(0xFF111111),
    errorContainer = Color(0xFF292929),
    onErrorContainer = Color(0xFFF5F5F5),
    outline = Color(0xFF3D3D3D),
    outlineVariant = Color(0xFF252525),
    scrim = Color(0xCC000000),
    inverseSurface = Color(0xFFE5E5E5),
    inverseOnSurface = Color(0xFF111111),
    inversePrimary = Color(0xFF1A1A1A)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2A2A2A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E5E5),
    onPrimaryContainer = Color(0xFF111111),
    secondary = Color(0xFF555555),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E5E5),
    onSecondaryContainer = Color(0xFF111111),
    tertiary = Color(0xFF727272),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE5E5E5),
    onTertiaryContainer = Color(0xFF111111),
    background = Color(0xFFF6F6F6),
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFE5E5E5),
    onSurfaceVariant = Color(0xFF555555),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F0F0),
    surfaceContainer = Color(0xFFEAEAEA),
    surfaceContainerHigh = Color(0xFFE3E3E3),
    surfaceContainerHighest = Color(0xFFDADADA),
    error = Color(0xFF5C5C5C),
    onError = Color.White,
    errorContainer = Color(0xFFE5E5E5),
    onErrorContainer = Color(0xFF111111),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFDADADA),
    scrim = Color.Black,
    inverseSurface = Color(0xFF292929),
    inverseOnSurface = Color(0xFFF7F7F7),
    inversePrimary = Color(0xFFE5E5E5)
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
    darkTheme: Boolean = true,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    ThemeMode.isDark = darkTheme
    // Dynamic Material You / system color adjustment intentionally off — app greyscale only.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

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
