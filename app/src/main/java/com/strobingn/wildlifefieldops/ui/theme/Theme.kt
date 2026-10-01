package com.strobingn.wildlifefieldops.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

internal val DarkColorScheme = darkColorScheme(
    primary = Color(FieldSwatch.Dark.Primary),
    onPrimary = Color(FieldSwatch.OnPrimary),
    primaryContainer = Color(FieldSwatch.Dark.PrimaryContainer),
    onPrimaryContainer = Color(FieldSwatch.Dark.OnPrimaryContainer),
    secondary = Color(FieldSwatch.Dark.PrimaryLight),
    onSecondary = Color(0xFF0A1628),
    secondaryContainer = Color(FieldSwatch.Dark.PrimaryDark),
    onSecondaryContainer = Color(FieldSwatch.Dark.OnPrimaryContainer),
    tertiary = Color(FieldSwatch.Dark.AccentCyan),
    onTertiary = Color(0xFF0A1628),
    tertiaryContainer = Color(FieldSwatch.Dark.Primary),
    onTertiaryContainer = Color(FieldSwatch.OnPrimary),
    background = Color(FieldSwatch.Dark.Background),
    onBackground = Color(FieldSwatch.Dark.OnBackground),
    surface = Color(FieldSwatch.Dark.Card),
    onSurface = Color(FieldSwatch.Dark.OnSurface),
    surfaceVariant = Color(FieldSwatch.Dark.SurfaceVariant),
    onSurfaceVariant = Color(FieldSwatch.Dark.OnSurfaceVariant),
    surfaceBright = Color(FieldSwatch.Dark.SurfaceBright),
    surfaceContainerLowest = Color(FieldSwatch.Dark.Background),
    surfaceContainerLow = Color(FieldSwatch.Dark.Card),
    surfaceContainer = Color(FieldSwatch.Dark.Elevated),
    surfaceContainerHigh = Color(FieldSwatch.Dark.SurfaceVariant),
    surfaceContainerHighest = Color(FieldSwatch.Dark.SurfaceBright),
    error = Color(FieldSwatch.Dark.Error),
    onError = Color(FieldSwatch.Dark.OnError),
    errorContainer = Color(FieldSwatch.Dark.ErrorContainer),
    onErrorContainer = Color(FieldSwatch.Dark.OnErrorContainer),
    outline = Color(FieldSwatch.Dark.Outline),
    outlineVariant = Color(FieldSwatch.Dark.OutlineVariant),
    scrim = Color(FieldSwatch.Dark.Scrim),
    inverseSurface = Color(0xFFE5E5E5),
    inverseOnSurface = Color(0xFF111111),
    inversePrimary = Color(FieldSwatch.Dark.PrimaryLight)
)

internal val LightColorScheme = lightColorScheme(
    primary = Color(FieldSwatch.Light.Primary),
    onPrimary = Color(FieldSwatch.OnPrimary),
    primaryContainer = Color(FieldSwatch.Light.PrimaryContainer),
    onPrimaryContainer = Color(FieldSwatch.Light.OnPrimaryContainer),
    secondary = Color(FieldSwatch.Light.PrimaryLight),
    onSecondary = Color(FieldSwatch.OnPrimary),
    secondaryContainer = Color(0xFFE3F2FD),
    onSecondaryContainer = Color(FieldSwatch.Light.OnPrimaryContainer),
    tertiary = Color(0xFF1976D2),
    onTertiary = Color(FieldSwatch.OnPrimary),
    tertiaryContainer = Color(0xFFBBDEFB),
    onTertiaryContainer = Color(FieldSwatch.Light.OnPrimaryContainer),
    background = Color(FieldSwatch.Light.Background),
    onBackground = Color(FieldSwatch.Light.OnBackground),
    surface = Color(FieldSwatch.Light.Card),
    onSurface = Color(FieldSwatch.Light.OnSurface),
    surfaceVariant = Color(FieldSwatch.Light.SurfaceVariant),
    onSurfaceVariant = Color(FieldSwatch.Light.OnSurfaceVariant),
    surfaceBright = Color(FieldSwatch.Light.SurfaceBright),
    surfaceContainerLowest = Color(FieldSwatch.Light.Card),
    surfaceContainerLow = Color(FieldSwatch.Light.Elevated),
    surfaceContainer = Color(0xFFE8ECF0),
    surfaceContainerHigh = Color(0xFFE3E7ED),
    surfaceContainerHighest = Color(0xFFDCE0E6),
    error = Color(FieldSwatch.Light.Error),
    onError = Color(FieldSwatch.Light.OnError),
    errorContainer = Color(FieldSwatch.Light.ErrorContainer),
    onErrorContainer = Color(FieldSwatch.Light.OnErrorContainer),
    outline = Color(FieldSwatch.Light.Outline),
    outlineVariant = Color(FieldSwatch.Light.OutlineVariant),
    scrim = Color(FieldSwatch.Light.Scrim),
    inverseSurface = Color(0xFF2F3133),
    inverseOnSurface = Color(0xFFF4F6F8),
    inversePrimary = Color(FieldSwatch.Dark.PrimaryLight)
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
    darkTheme: Boolean = isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    ThemeMode.isDark = darkTheme
    // Dynamic Material You off — greyscale chrome (dark) / paper chrome (light)
    // with Wildlife Whisperer navy primary.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val window = activity.window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = !darkTheme
            insets.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
