package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val KeyDeckShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(14.dp)
)

@Composable
fun KeyDeckTheme(
    vistaTokens: VistaThemeTokens = BlueVistaTokens,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = vistaTokens.primaryButtonUpperMid,
        onPrimary = TextPrimary,
        primaryContainer = vistaTokens.activeButtonLowerMid,
        onPrimaryContainer = TextPrimary,
        secondary = vistaTokens.accent,
        onSecondary = vistaTokens.background,
        secondaryContainer = vistaTokens.surfaceElevated,
        onSecondaryContainer = TextPrimary,
        tertiary = MusicSourceAmber,
        onTertiary = vistaTokens.background,
        background = vistaTokens.background,
        onBackground = TextPrimary,
        surface = vistaTokens.surface,
        onSurface = TextPrimary,
        surfaceVariant = vistaTokens.surfaceElevated,
        onSurfaceVariant = TextSecondary,
        outline = vistaTokens.divider,
        outlineVariant = vistaTokens.divider,
        error = CallMissedRed,
        onError = vistaTokens.background
    )

    CompositionLocalProvider(LocalVistaTheme provides vistaTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KeyDeckTypography,
            shapes = KeyDeckShapes,
            content = content
        )
    }
}
