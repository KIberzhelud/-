package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Classic Nokia E61i / Symbian S60v3 glossy metallic surface with subtle top sheen and beveled border.
 * Preserves existing layout structure while elevating cosmetic finish.
 */
fun Modifier.symbianGlossyTile(
    bucket: Int,
    isFocused: Boolean = false,
    isLetterHighlighted: Boolean = false,
    shape: Shape = RoundedCornerShape(3.dp)
): Modifier {
    val (topColor, midColor, bottomColor) = when (bucket % 4) {
        0 -> Triple(TileVibrantBlueTop, TileVibrantBlue, TileVibrantBlueBottom)
        1 -> Triple(TileBrightAzureTop, TileBrightAzure, TileBrightAzureBottom)
        2 -> Triple(TileDeepCobaltTop, TileDeepCobalt, TileDeepCobaltBottom)
        else -> Triple(TileMidnightSlateTop, TileMidnightSlate, TileMidnightSlateBottom)
    }

    val borderColor = when {
        isFocused -> FocusBorderCyan
        isLetterHighlighted -> ElectricCyan
        else -> SymbianBevelHighlight.copy(alpha = 0.45f)
    }
    val borderWidth: Dp = if (isFocused) 2.dp else 1.dp

    return this
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                0.0f to topColor,
                0.48f to midColor,
                0.52f to bottomColor.copy(alpha = 0.96f),
                1.0f to bottomColor
            )
        )
        .drawWithContent {
            drawContent()
            // Subtle Symbian top inner specular edge
            drawLine(
                color = Color.White.copy(alpha = if (isFocused) 0.38f else 0.16f),
                start = Offset(0f, 1f),
                end = Offset(size.width, 1f),
                strokeWidth = 1.5f
            )
        }
        .border(borderWidth, borderColor, shape)
}

fun Modifier.symbianMetallicBar(): Modifier {
    return this.background(
        brush = Brush.verticalGradient(
            0.0f to NavySurfaceTop,
            0.5f to NavySurface,
            1.0f to NavySurfaceBottom
        )
    )
}
