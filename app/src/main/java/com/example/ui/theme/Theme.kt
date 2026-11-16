package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val KeyDeckDarkColorScheme = darkColorScheme(
    primary = TileBrightAzure,
    onPrimary = TextPrimary,
    primaryContainer = TileDeepCobalt,
    onPrimaryContainer = TextPrimary,
    secondary = ElectricCyan,
    onSecondary = NavyBackground,
    secondaryContainer = NavySurfaceElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = MusicSourceAmber,
    onTertiary = NavyBackground,
    background = NavyBackground,
    onBackground = TextPrimary,
    surface = NavySurface,
    onSurface = TextPrimary,
    surfaceVariant = NavySurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = NavyDivider,
    outlineVariant = NavyDivider,
    error = CallMissedRed,
    onError = NavyBackground
)

private val KeyDeckShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(14.dp)
)

@Composable
fun KeyDeckTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = KeyDeckDarkColorScheme,
        typography = KeyDeckTypography,
        shapes = KeyDeckShapes,
        content = content
    )
}
