package com.siliconverity.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SvColorScheme = darkColorScheme(
    primary = SvColors.Accent,
    onPrimary = SvColors.Background,
    primaryContainer = SvColors.AccentDark,
    onPrimaryContainer = SvColors.TextPrimary,
    secondary = SvColors.AccentDark,
    onSecondary = SvColors.TextPrimary,
    tertiary = SvColors.Info,
    onTertiary = SvColors.Background,
    background = SvColors.Background,
    onBackground = SvColors.TextPrimary,
    surface = SvColors.Surface,
    onSurface = SvColors.TextPrimary,
    surfaceVariant = SvColors.SurfaceVariant,
    onSurfaceVariant = SvColors.TextSecondary,
    surfaceContainer = SvColors.SurfaceVariant,
    surfaceContainerHigh = SvColors.SurfaceVariant,
    surfaceContainerLow = SvColors.Surface,
    error = SvColors.Danger,
    onError = SvColors.TextPrimary,
    errorContainer = SvColors.Danger,
    onErrorContainer = SvColors.TextPrimary,
    outline = SvColors.StructureLine,
    outlineVariant = SvColors.StructureLine,
)

@Composable
fun SvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SvColorScheme,
        typography = SvTypography,
        shapes = SvShapes.MaterialShapes,
        content = content,
    )
}
