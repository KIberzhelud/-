package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalVistaTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.VistaGreenBottom
import com.example.ui.theme.VistaGreenMid
import com.example.ui.theme.VistaGreenTop
import com.example.ui.theme.vistaAeroButton
import kotlin.math.roundToInt

/**
 * Windows Vista Aero classic trackbar slider with sunken 3D bevel channel,
 * glossy cyan glass fill, and iconic pointed metallic Vista thumb.
 * Consumes horizontal drag and tap gestures cleanly so parent containers never steal them.
 */
@Composable
fun VistaSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    tickCount: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
    accentCyan: Boolean = true
) {
    val theme = LocalVistaTheme.current
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val normalized = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestOnFinish by rememberUpdatedState(onValueChangeFinished)
    val latestRange by rememberUpdatedState(valueRange)
    var widthPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }

    fun positionToValue(x: Float): Float {
        val pad = if (compact) 8f else 14f
        val usable = (widthPx - pad * 2f).coerceAtLeast(1f)
        val frac = ((x - pad) / usable).coerceIn(0f, 1f)
        return latestRange.start + frac * ((latestRange.endInclusive - latestRange.start).coerceAtLeast(0f))
    }

    val hasTicks = tickCount > 1
    val heightDp = when {
        compact -> 24.dp
        hasTicks -> 34.dp
        else -> 30.dp
    }

    Canvas(
        modifier = modifier
            .height(heightDp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isDragging = true
                    latestOnChange(positionToValue(down.position.x))

                    val finished = drag(down.id) { change ->
                        change.consume()
                        latestOnChange(positionToValue(change.position.x))
                    }
                    isDragging = false
                    if (finished) {
                        latestOnFinish?.invoke()
                    } else {
                        latestOnFinish?.invoke()
                    }
                }
            }
    ) {
        val hPad = if (compact) 6.dp.toPx() else 10.dp.toPx()
        val trackHeight = if (compact) 5.dp.toPx() else 6.dp.toPx()
        val trackWidth = (size.width - hPad * 2f).coerceAtLeast(4f)
        val trackTop = if (hasTicks) {
            ((size.height - trackHeight) / 2f - 3.dp.toPx()).coerceAtLeast(4.dp.toPx())
        } else {
            (size.height - trackHeight) / 2f
        }

        // 1. Sunken Vista groove background
        drawRoundRect(
            color = theme.insetBg,
            topLeft = Offset(hPad, trackTop),
            size = Size(trackWidth, trackHeight),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
        // Top inner shadow
        drawLine(
            color = theme.insetShadow,
            start = Offset(hPad + 1f, trackTop + 0.8f),
            end = Offset(hPad + trackWidth - 1f, trackTop + 0.8f),
            strokeWidth = 1.4f
        )
        // Bottom metallic rim
        drawLine(
            color = theme.insetBottomRim.copy(alpha = 0.85f),
            start = Offset(hPad + 1f, trackTop + trackHeight),
            end = Offset(hPad + trackWidth - 1f, trackTop + trackHeight),
            strokeWidth = 1.1f
        )

        // 2. Vista Aero Glossy Fill
        val activeWidth = (trackWidth * normalized).coerceAtLeast(0f)
        if (activeWidth > 1f) {
            val fillColors = if (accentCyan) {
                listOf(theme.trackTop, theme.trackMid, theme.trackBottom)
            } else {
                listOf(VistaGreenTop, VistaGreenMid, VistaGreenBottom)
            }
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = fillColors,
                    startY = trackTop,
                    endY = trackTop + trackHeight
                ),
                topLeft = Offset(hPad, trackTop),
                size = Size(activeWidth, trackHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
            // Upper glass gleam
            drawLine(
                color = Color.White.copy(alpha = 0.65f),
                start = Offset(hPad + 1f, trackTop + 1f),
                end = Offset(hPad + activeWidth - 1f, trackTop + 1f),
                strokeWidth = 1f
            )
        }

        // Outer track border
        drawRoundRect(
            color = theme.chromeBorderOuter,
            topLeft = Offset(hPad, trackTop),
            size = Size(trackWidth, trackHeight),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = 1f)
        )

        // Classic Vista Trackbar Tick Marks underneath groove
        if (hasTicks) {
            val tickTop = trackTop + trackHeight + 4.dp.toPx()
            for (i in 0 until tickCount) {
                val frac = i.toFloat() / (tickCount - 1).toFloat()
                val tx = hPad + trackWidth * frac
                val isMajor = i == 0 || i == tickCount - 1 || (tickCount % 2 == 1 && i == tickCount / 2)
                val tickLen = if (isMajor) 5.dp.toPx() else 3.dp.toPx()
                drawLine(
                    color = if (isMajor) theme.accent.copy(alpha = 0.85f) else theme.insetBottomRim.copy(alpha = 0.6f),
                    start = Offset(tx, tickTop),
                    end = Offset(tx, tickTop + tickLen),
                    strokeWidth = if (isMajor) 1.5f else 1.1f
                )
            }
        }

        // 3. Classic Windows Vista Pointed Trackbar Thumb
        val thumbCx = hPad + activeWidth
        val thumbCy = trackTop + trackHeight / 2f
        val halfW = if (compact) 5.5.dp.toPx() else 6.5.dp.toPx()
        val topY = thumbCy - (if (compact) 7.dp.toPx() else 9.dp.toPx())
        val midY = thumbCy + (if (compact) 2.5.dp.toPx() else 3.5.dp.toPx())
        val tipY = thumbCy + (if (compact) 7.5.dp.toPx() else 9.5.dp.toPx())

        val thumbPath = Path().apply {
            moveTo(thumbCx - halfW, topY + 2f)
            lineTo(thumbCx - halfW + 2f, topY)
            lineTo(thumbCx + halfW - 2f, topY)
            lineTo(thumbCx + halfW, topY + 2f)
            lineTo(thumbCx + halfW, midY)
            lineTo(thumbCx, tipY)
            lineTo(thumbCx - halfW, midY)
            close()
        }

        val thumbBrush = Brush.verticalGradient(
            colors = if (isDragging) {
                listOf(Color.White, theme.trackTop, theme.trackMid)
            } else {
                listOf(theme.thumbLight, theme.thumbMid, theme.thumbDark)
            },
            startY = topY,
            endY = tipY
        )

        drawPath(path = thumbPath, brush = thumbBrush)
        drawPath(
            path = thumbPath,
            color = if (isDragging) theme.focusBorder else theme.chromeBorderOuter,
            style = Stroke(width = if (isDragging) 1.6f else 1.2f)
        )
        // Inner vertical metallic rib on the Vista thumb
        drawLine(
            color = if (isDragging) Color.White else theme.accent.copy(alpha = 0.7f),
            start = Offset(thumbCx, topY + 3f),
            end = Offset(thumbCx, midY - 1f),
            strokeWidth = 1.4f
        )
    }
}

/**
 * Windows Vista Aero recessed progress bar (for read-only bars or compact status indicators).
 */
@Composable
fun VistaProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 7.dp,
    greenTint: Boolean = false
) {
    val theme = LocalVistaTheme.current
    val clamped = progress.coerceIn(0f, 1f)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        drawRoundRect(
            color = theme.insetBg,
            size = size,
            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
        )
        if (clamped > 0f) {
            val fillW = size.width * clamped
            val colors = if (greenTint) {
                listOf(VistaGreenTop, VistaGreenMid, VistaGreenBottom)
            } else {
                listOf(theme.trackTop, theme.trackMid, theme.trackBottom)
            }
            drawRoundRect(
                brush = Brush.verticalGradient(colors),
                size = Size(fillW, size.height),
                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(1f, 1f),
                end = Offset(fillW - 1f, 1f),
                strokeWidth = 1.1f
            )
        }
        drawRoundRect(
            color = theme.insetBottomRim.copy(alpha = 0.7f),
            size = size,
            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
            style = Stroke(width = 1f)
        )
    }
}

/**
 * Retro Windows Vista / Symbian beveled metallic toggle switch replacing modern flat Material 3 Switch.
 */
@Composable
fun VistaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    val trackBrush = if (checked) {
        Brush.verticalGradient(listOf(VistaGreenTop, VistaGreenMid, VistaGreenBottom))
    } else {
        Brush.verticalGradient(listOf(theme.insetShadow, theme.insetBg, theme.background))
    }
    Box(
        modifier = modifier
            .size(width = 50.dp, height = 24.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(trackBrush)
            .border(
                width = 1.dp,
                color = if (checked) theme.focusBorder.copy(alpha = 0.8f) else theme.insetBottomRim.copy(alpha = 0.65f),
                shape = RoundedCornerShape(3.dp)
            )
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = if (checked) "ON" else "OFF",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold
            ),
            color = if (checked) Color.White else TextMuted,
            modifier = Modifier
                .align(if (checked) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = 4.dp)
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(x = if (checked) 24.dp.roundToPx() else 0, y = 0) }
                .size(width = 20.dp, height = 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.verticalGradient(listOf(theme.thumbLight, theme.thumbMid, theme.thumbDark))
                )
                .border(1.dp, theme.chromeBorderOuter, RoundedCornerShape(2.dp))
        )
    }
}

/**
 * Classic Windows Vista Aero metallic push button.
 */
@Composable
fun VistaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    enabled: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .vistaAeroButton(isPrimary = isPrimary && enabled, isActive = false)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingContent != null) {
            leadingContent()
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp
            ),
            color = if (enabled) TextPrimary else TextMuted,
            maxLines = 1
        )
    }
}
