package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
 * Classic Nokia E61i / Symbian S60v3 + Windows Vista Aero glossy metallic tile.
 * When wallpaper is enabled (theme.isGlassMode == true), tiles transform into translucent
 * Windows Vista Aero Glass window-border panes with double dark/bright rims and diagonal glass streaks.
 * When wallpaper is absent, tiles stay opaque glossy metallic in either Blue or Gray theme.
 */
@Composable
fun Modifier.symbianGlossyTile(
    bucket: Int,
    isFocused: Boolean = false,
    isLetterHighlighted: Boolean = false,
    shape: Shape = RoundedCornerShape(3.dp)
): Modifier {
    val theme = LocalVistaTheme.current
    val (topColor, midColor, bottomColor) = theme.tileBuckets[bucket % theme.tileBuckets.size]

    val outerBorderColor = when {
        isFocused -> theme.focusBorder
        isLetterHighlighted -> theme.accent
        theme.isGlassMode -> Color.Black.copy(alpha = 0.78f)
        else -> theme.chromeInnerGlow.copy(alpha = 0.42f)
    }
    val borderWidth: Dp = if (isFocused) 2.dp else 1.dp

    if (theme.isGlassMode) {
        return this
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    0.0f to topColor.copy(alpha = 0.42f),
                    0.46f to midColor.copy(alpha = 0.30f),
                    0.50f to bottomColor.copy(alpha = 0.46f),
                    1.0f to bottomColor.copy(alpha = 0.56f)
                )
            )
            .drawWithContent {
                // Diagonal Vista Aero glass light reflection wash
                drawRect(
                    brush = Brush.linearGradient(
                        0.0f to Color.White.copy(alpha = 0.22f),
                        0.38f to Color.White.copy(alpha = 0.06f),
                        0.48f to Color.Transparent,
                        0.72f to theme.chromeInnerGlow.copy(alpha = 0.09f),
                        1.0f to Color.Transparent,
                        start = Offset(0f, 0f),
                        end = Offset(size.width * 0.9f, size.height)
                    )
                )
                drawContent()
                // Inner glowing white/ice Aero window-border bevel
                val innerRimAlpha = if (isFocused) 0.72f else 0.44f
                drawLine(
                    color = Color.White.copy(alpha = innerRimAlpha),
                    start = Offset(2f, 2f),
                    end = Offset(size.width - 2f, 2f),
                    strokeWidth = 1.4f
                )
                drawLine(
                    color = Color.White.copy(alpha = innerRimAlpha * 0.6f),
                    start = Offset(2f, 2f),
                    end = Offset(2f, size.height - 2f),
                    strokeWidth = 1.1f
                )
                drawLine(
                    color = Color.White.copy(alpha = innerRimAlpha * 0.6f),
                    start = Offset(size.width - 2f, 2f),
                    end = Offset(size.width - 2f, size.height - 2f),
                    strokeWidth = 1.1f
                )
                drawLine(
                    color = theme.chromeInnerGlow.copy(alpha = if (isFocused) 0.55f else 0.26f),
                    start = Offset(2f, size.height - 2f),
                    end = Offset(size.width - 2f, size.height - 2f),
                    strokeWidth = 1.1f
                )
            }
            .border(borderWidth, outerBorderColor, shape)
    }

    return this
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                0.0f to topColor,
                0.47f to midColor,
                0.50f to bottomColor.copy(alpha = 0.96f),
                1.0f to bottomColor
            )
        )
        .drawWithContent {
            drawContent()
            drawLine(
                color = Color.White.copy(alpha = if (isFocused) 0.46f else 0.24f),
                start = Offset(1f, 1.5f),
                end = Offset(size.width - 1f, 1.5f),
                strokeWidth = 1.5f
            )
            drawLine(
                color = theme.chromeInnerGlow.copy(alpha = if (isFocused) 0.35f else 0.14f),
                start = Offset(1f, size.height - 1.5f),
                end = Offset(size.width - 1f, size.height - 1.5f),
                strokeWidth = 1f
            )
        }
        .border(borderWidth, outerBorderColor, shape)
}

@Composable
fun Modifier.symbianMetallicBar(): Modifier {
    val theme = LocalVistaTheme.current
    return this
        .background(
            brush = Brush.verticalGradient(
                0.0f to theme.glassTop,
                0.46f to theme.glassUpperMid,
                0.50f to theme.glassLowerMid,
                1.0f to theme.glassBottom
            )
        )
        .drawWithContent {
            drawContent()
            drawLine(
                color = Color.White.copy(alpha = 0.24f),
                start = Offset(0f, 1f),
                end = Offset(size.width, 1f),
                strokeWidth = 1.2f
            )
            drawLine(
                color = theme.chromeBorderOuter,
                start = Offset(0f, size.height - 1f),
                end = Offset(size.width, size.height - 1f),
                strokeWidth = 1.5f
            )
        }
}

/**
 * Windows Vista Aero opaque raised beveled button/panel chrome (adapts to Blue, Gray, or Wallpaper palette).
 */
@Composable
fun Modifier.vistaAeroButton(
    isPrimary: Boolean = false,
    isActive: Boolean = false,
    shape: Shape = RoundedCornerShape(3.dp)
): Modifier {
    val theme = LocalVistaTheme.current
    val brush = when {
        isPrimary -> Brush.verticalGradient(
            0.0f to theme.primaryButtonTop,
            0.47f to theme.primaryButtonUpperMid,
            0.50f to theme.primaryButtonLowerMid,
            1.0f to theme.primaryButtonBottom
        )
        isActive -> Brush.verticalGradient(
            0.0f to theme.activeButtonTop,
            0.47f to theme.activeButtonUpperMid,
            0.50f to theme.activeButtonLowerMid,
            1.0f to theme.activeButtonBottom
        )
        else -> Brush.verticalGradient(
            0.0f to theme.buttonTop,
            0.47f to theme.buttonUpperMid,
            0.50f to theme.buttonLowerMid,
            1.0f to theme.buttonBottom
        )
    }
    val outerBorder = if (isPrimary || isActive) {
        theme.accent.copy(alpha = 0.85f)
    } else {
        theme.chromeInnerGlow.copy(alpha = 0.42f)
    }
    return this
        .clip(shape)
        .background(brush)
        .drawWithContent {
            drawContent()
            drawLine(
                color = Color.White.copy(alpha = if (isPrimary) 0.48f else 0.26f),
                start = Offset(1f, 1.5f),
                end = Offset(size.width - 1f, 1.5f),
                strokeWidth = 1.2f
            )
            drawLine(
                color = Color.Black.copy(alpha = 0.45f),
                start = Offset(1f, size.height - 1f),
                end = Offset(size.width - 1f, size.height - 1f),
                strokeWidth = 1f
            )
        }
        .border(1.dp, outerBorder, shape)
}

/**
 * Sunken classic Windows Vista / Symbian 3D text input groove.
 */
@Composable
fun Modifier.vistaInsetBox(
    isFocused: Boolean = false,
    shape: Shape = RoundedCornerShape(2.dp)
): Modifier {
    val theme = LocalVistaTheme.current
    return this
        .clip(shape)
        .background(theme.insetBg)
        .drawWithContent {
            drawContent()
            drawLine(
                color = theme.insetShadow,
                start = Offset(0f, 1f),
                end = Offset(size.width, 1f),
                strokeWidth = 2f
            )
            drawLine(
                color = theme.insetShadow,
                start = Offset(1f, 0f),
                end = Offset(1f, size.height),
                strokeWidth = 1.5f
            )
            drawLine(
                color = if (isFocused) theme.accent.copy(alpha = 0.7f) else theme.insetBottomRim.copy(alpha = 0.65f),
                start = Offset(0f, size.height - 1f),
                end = Offset(size.width, size.height - 1f),
                strokeWidth = 1.2f
            )
        }
        .border(
            width = 1.dp,
            color = if (isFocused) theme.accent else theme.insetBottomRim.copy(alpha = 0.55f),
            shape = shape
        )
}

/**
 * Windows Vista Aero opaque window/panel container adapting cleanly to current theme or wallpaper colors.
 */
@Composable
fun Modifier.vistaWindowFrame(
    shape: Shape = RoundedCornerShape(5.dp)
): Modifier {
    val theme = LocalVistaTheme.current
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                0.0f to theme.glassUpperMid,
                0.14f to theme.surface,
                1.0f to theme.background
            )
        )
        .border(1.dp, theme.chromeBorderOuter, shape)
        .drawWithContent {
            drawContent()
            drawLine(
                color = theme.chromeInnerGlow.copy(alpha = 0.5f),
                start = Offset(2f, 1.5f),
                end = Offset(size.width - 2f, 1.5f),
                strokeWidth = 1.5f
            )
        }
        .border(1.dp, theme.chromeInnerGlow.copy(alpha = 0.45f), shape)
}
