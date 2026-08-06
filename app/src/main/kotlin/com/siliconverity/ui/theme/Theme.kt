package com.siliconverity.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = SiliconPrimary,
    onPrimary = SiliconOnPrimary,
    primaryContainer = SiliconPrimaryContainer,
    onPrimaryContainer = SiliconOnPrimaryContainer,
    secondary = SiliconSecondary,
    onSecondary = SiliconOnSecondary,
    secondaryContainer = SiliconSecondaryContainer,
    onSecondaryContainer = SiliconOnSecondaryContainer,
    tertiary = SiliconTertiary,
    onTertiary = SiliconOnTertiary,
    tertiaryContainer = SiliconTertiaryContainer,
    onTertiaryContainer = SiliconOnTertiaryContainer,
    error = SiliconError,
    onError = SiliconOnError,
    errorContainer = SiliconErrorContainer,
    onErrorContainer = SiliconOnErrorContainer,
    background = SiliconBackground,
    onBackground = SiliconOnBackground,
    surface = SiliconSurface,
    onSurface = SiliconOnSurface,
    surfaceVariant = SiliconSurfaceVariant,
    onSurfaceVariant = SiliconOnSurfaceVariant,
)

private val DarkColors = darkColorScheme(
    primary = SiliconPrimaryDark,
    onPrimary = SiliconOnPrimaryDark,
    primaryContainer = SiliconPrimaryContainerDark,
    onPrimaryContainer = SiliconOnPrimaryContainerDark,
    background = SiliconBackgroundDark,
    onBackground = SiliconOnBackgroundDark,
    surface = SiliconSurfaceDark,
    onSurface = SiliconOnSurfaceDark,
    surfaceVariant = SiliconSurfaceVariantDark,
    onSurfaceVariant = SiliconOnSurfaceVariantDark,
)

@Composable
fun SiliconVerityTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content,
    )
}
