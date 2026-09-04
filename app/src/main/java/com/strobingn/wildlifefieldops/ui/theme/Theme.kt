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
    primary = Color(0xFF7CB342),
    onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFF1E3A1A),
    onPrimaryContainer = Color(0xFFDCECC8),
    secondary = Color(0xFF42A5F5),
    onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0xFF2A322C),
    onSecondaryContainer = Color(0xFFF3F5F1),
    tertiary = Color(0xFFFFC107),
    onTertiary = Color(0xFF111111),
    tertiaryContainer = Color(0xFF2A322C),
    onTertiaryContainer = Color(0xFFF3F5F1),
    background = Color(0xFF0E1110),
    onBackground = Color(0xFFF3F5F1),
    surface = Color(0xFF171C19),
    onSurface = Color(0xFFF3F5F1),
    surfaceVariant = Color(0xFF2A322C),
    onSurfaceVariant = Color(0xFFB5BDB6),
    surfaceBright = Color(0xFF3A433C),
    surfaceContainerLowest = Color(0xFF0E1110),
    surfaceContainerLow = Color(0xFF171C19),
    surfaceContainer = Color(0xFF1F2622),
    surfaceContainerHigh = Color(0xFF2A322C),
    surfaceContainerHighest = Color(0xFF3A433C),
    error = Color(0xFFE53935),
    onError = Color.White,
    errorContainer = Color(0xFF3B1616),
    onErrorContainer = Color(0xFFFFCDD2),
    outline = Color(0xFF3D4A40),
    outlineVariant = Color(0xFF2A332C),
    scrim = Color(0xCC000000),
    inverseSurface = Color(0xFFF4F1EA),
    inverseOnSurface = Color(0xFF1B1D1A),
    inversePrimary = Color(0xFF2E7D32)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF1B5E20),
    secondary = Color(0xFF1565C0),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFBBDEFB),
    onSecondaryContainer = Color(0xFF0D47A1),
    tertiary = Color(0xFFEF6C00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0B2),
    onTertiaryContainer = Color(0xFFE65100),
    background = Color(0xFFF4F1EA),
    onBackground = Color(0xFF1B1D1A),
    surface = Color.White,
    onSurface = Color(0xFF1B1D1A),
    surfaceVariant = Color(0xFFE8E4DA),
    onSurfaceVariant = Color(0xFF5C615A),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F5F0),
    surfaceContainer = Color(0xFFEFEBE3),
    surfaceContainerHigh = Color(0xFFE8E4DA),
    surfaceContainerHighest = Color(0xFFDED9CE),
    error = Color(0xFFC62828),
    onError = Color.White,
    errorContainer = Color(0xFFFFCDD2),
    onErrorContainer = Color(0xFFB71C1C),
    outline = Color(0xFFD4CFC4),
    outlineVariant = Color(0xFFE6E1D6),
    scrim = Color.Black,
    inverseSurface = Color(0xFF292929),
    inverseOnSurface = Color(0xFFF7F7F7),
    inversePrimary = Color(0xFF7CB342)
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
