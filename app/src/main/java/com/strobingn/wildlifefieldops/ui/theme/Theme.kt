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

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryBlueDark,
    onPrimaryContainer = Color(0xFFdbeafe),
    secondary = SecondaryCyan,
    onSecondary = OnSecondary,
    secondaryContainer = Color(0xFF164e63),
    onSecondaryContainer = Color(0xFFcffafe),
    tertiary = TertiaryAmber,
    onTertiary = OnTertiary,
    tertiaryContainer = Color(0xFF78350f),
    onTertiaryContainer = Color(0xFFfef3c7),
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceTint = PrimaryBlue.copy(alpha = 0.05f),
    error = ErrorRed,
    onError = Color(0xFFffffff),
    errorContainer = Color(0xFF450a0a),
    onErrorContainer = Color(0xFFfecaca),
    outline = BorderDark,
    outlineVariant = DividerDark,
    scrim = Color(0xFF000000).copy(alpha = 0.72f),
    inverseSurface = Color(0xFFf4f4f5),
    inverseOnSurface = Color(0xFF18181b),
    inversePrimary = PrimaryBlueLight
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlueDark,
    onPrimary = Color(0xFFffffff),
    primaryContainer = PrimaryBlueLight,
    onPrimaryContainer = Color(0xFF172554),
    secondary = SecondaryCyanDark,
    onSecondary = Color(0xFFffffff),
    secondaryContainer = Color(0xFFcffafe),
    onSecondaryContainer = Color(0xFF164e63),
    tertiary = TertiaryAmberDark,
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFFfef3c7),
    onTertiaryContainer = Color(0xFF78350f),
    background = Color(0xFFfafafa),
    onBackground = Color(0xFF18181b),
    surface = Color(0xFFffffff),
    onSurface = Color(0xFF18181b),
    surfaceVariant = Color(0xFFf4f4f5),
    onSurfaceVariant = Color(0xFF52525b),
    error = ErrorRed,
    onError = Color(0xFFffffff),
    errorContainer = Color(0xFFfee2e2),
    onErrorContainer = Color(0xFF450a0a),
    outline = Color(0xFFd4d4d8),
    outlineVariant = Color(0xFFe4e4e7),
    scrim = Color(0xFF000000).copy(alpha = 0.5f),
    inverseSurface = Color(0xFF27272a),
    inverseOnSurface = Color(0xFFf4f4f5),
    inversePrimary = PrimaryBlue
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
    darkTheme: Boolean = true, // Field ops stays dark by default to avoid light flash outdoors
    dynamicColor: Boolean = true, // Enable Material You on Android 12+
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
            window.navigationBarColor = colorScheme.background.toArgb()
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
