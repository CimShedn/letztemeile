package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = BrandMintLight,
    onPrimary = OnBrandMintLight,
    primaryContainer = BrandMintContainerLight,
    onPrimaryContainer = OnBrandMintContainerLight,
    secondary = BrandMintLight,
    onSecondary = Color.White,
    secondaryContainer = SecondarySurfaceLight,
    onSecondaryContainer = OnSecondarySurfaceLight,
    tertiary = BatteryTightLight,
    onTertiary = Color.White,
    tertiaryContainer = BatteryTightContainerLight,
    onTertiaryContainer = BatteryTightLight,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = BatteryCriticalLight,
    onError = Color.White,
    errorContainer = BatteryCriticalContainerLight,
    onErrorContainer = BatteryCriticalLight
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandMintDark,
    onPrimary = OnBrandMintDark,
    primaryContainer = BrandMintContainerDark,
    onPrimaryContainer = OnBrandMintContainerDark,
    secondary = BrandMintDark,
    onSecondary = OnBrandMintDark,
    secondaryContainer = SecondarySurfaceDark,
    onSecondaryContainer = OnSecondarySurfaceDark,
    tertiary = BatteryTightDark,
    onTertiary = Color(0xFF2D1E02),
    tertiaryContainer = BatteryTightContainerDark,
    onTertiaryContainer = BatteryTightDark,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = BatteryCriticalDark,
    onError = Color(0xFF3E0A0A),
    errorContainer = BatteryCriticalContainerDark,
    onErrorContainer = BatteryCriticalDark
)

val LetzteMeileShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun LetzteMeileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LetzteMeileTypography,
        shapes = LetzteMeileShapes,
        content = content
    )
}
