package com.strobingn.wildlifefieldops.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
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

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = Color(0xFF111318),
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = AccentBlue,
    onSecondary = Color(0xFF111318),
    secondaryContainer = SurfaceVariant,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentAmber,
    onTertiary = Color(0xFF111318),
    tertiaryContainer = SurfaceVariant,
    onTertiaryContainer = TextPrimary,
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
    onError = Color(0xFF111318),
    errorContainer = SurfaceVariant,
    onErrorContainer = TextPrimary,
    outline = BorderDark,
    outlineVariant = DividerDark,
    scrim = ScrimDark,
    inverseSurface = Color(0xFFE5E7EB),
    inverseOnSurface = Color(0xFF111318),
    inversePrimary = PrimaryGreenDark
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E7EB),
    onPrimaryContainer = Color(0xFF111318),
    secondary = Color(0xFF4B5563),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E7EB),
    onSecondaryContainer = Color(0xFF111318),
    tertiary = Color(0xFF6B7280),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE5E7EB),
    onTertiaryContainer = Color(0xFF111318),
    background = Color(0xFFF5F6F7),
    onBackground = Color(0xFF111318),
    surface = Color.White,
    onSurface = Color(0xFF111318),
    surfaceVariant = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFF4B5563),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F1F2),
    surfaceContainer = Color(0xFFE8EAED),
    surfaceContainerHigh = Color(0xFFE1E4E8),
    surfaceContainerHighest = Color(0xFFD7DBE0),
    error = ErrorRedDark,
    onError = Color.White,
    errorContainer = Color(0xFFE5E7EB),
    onErrorContainer = Color(0xFF111318),
    outline = Color(0xFFB8BEC7),
    outlineVariant = Color(0xFFD7DBE0),
    scrim = Color.Black,
    inverseSurface = Color(0xFF272B31),
    inverseOnSurface = Color(0xFFF7F7F5),
    inversePrimary = PrimaryGreen
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
    dynamicColor: Boolean = false,
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
