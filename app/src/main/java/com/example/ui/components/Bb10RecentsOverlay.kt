package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RecentAppFrame
import com.example.ui.L10n
import com.example.ui.LocalAppLang
import com.example.ui.theme.LocalVistaTheme
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.symbianMetallicBar
import com.example.ui.theme.vistaAeroButton
import com.example.ui.theme.vistaWindowFrame

/**
 * BlackBerry OS 10 / KEY2 Active Frames Recents System:
 * Summoned by 2-finger pinch-out on the launcher (or Ctrl+R).
 * Shows a 2x2 (portrait) or 3x2 (landscape) grid of live app frames with preview snippet,
 * Pin, App Info, Close frame ('X'), and Close All (preserving pinned frames).
 * Pinch-in (scale > 1.15f) or Escape/Back returns smoothly to the launcher.
 */
@Composable
fun Bb10RecentsOverlay(
    frames: List<RecentAppFrame>,
    focusedIndex: Int,
    isLandscape: Boolean,
    onOpenFrame: (RecentAppFrame) -> Unit,
    onCloseFrame: (RecentAppFrame) -> Unit,
    onTogglePinFrame: (RecentAppFrame) -> Unit,
    onOpenFrameInfo: (RecentAppFrame) -> Unit,
    onCloseAllExceptPinned: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    val lang = LocalAppLang.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom > 1.16f) {
                        onDismiss()
                    }
                }
            }
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag("bb10_recents_overlay")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .vistaWindowFrame(RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            // Top Active Frames Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .border(1.dp, theme.chromeBorderOuter, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${L10n.recentsTitle(lang)} (${frames.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = theme.focusBorder,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = L10n.pinchBackHint(lang),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (frames.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .vistaAeroButton()
                                .clickable(onClick = onCloseAllExceptPinned)
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                                .testTag("bb10_close_all_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteSweep,
                                    contentDescription = L10n.closeAll(lang),
                                    tint = MusicSourceAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = L10n.closeAll(lang),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .vistaAeroButton(isPrimary = true)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                            .testTag("bb10_recents_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Закрыть",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (frames.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = L10n.noRecentApps(lang),
                            style = MaterialTheme.typography.titleSmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        VistaButton(
                            text = if (lang == com.example.model.AppLanguage.RU) "Вернуться" else "Return",
                            isPrimary = true,
                            onClick = onDismiss
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isLandscape) 3 else 2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("bb10_recents_grid"),
                    contentPadding = PaddingValues(2.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(frames, key = { _, it -> it.packageName }) { idx, frame ->
                        ActiveFrameCard(
                            frame = frame,
                            isFocused = idx == focusedIndex,
                            onOpen = { onOpenFrame(frame) },
                            onClose = { onCloseFrame(frame) },
                            onTogglePin = { onTogglePinFrame(frame) },
                            onOpenInfo = { onOpenFrameInfo(frame) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveFrameCard(
    frame: RecentAppFrame,
    isFocused: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onTogglePin: () -> Unit,
    onOpenInfo: () -> Unit
) {
    val theme = LocalVistaTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(182.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        theme.glassTop.copy(alpha = 0.92f),
                        theme.surface.copy(alpha = 0.95f)
                    )
                )
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) theme.focusBorder else theme.chromeBorderOuter,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onOpen)
            .testTag("bb10_frame_${frame.packageName}")
    ) {
        // Top preview card body (Real screenshot/cover thumbnail when available + Active Frame overlay)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            frame.previewBitmap?.let { previewBmp ->
                Image(
                    bitmap = previewBmp,
                    contentDescription = frame.appLabel,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("bb10_preview_image_${frame.packageName}")
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.72f),
                                    Color.Black.copy(alpha = 0.36f),
                                    Color.Black.copy(alpha = 0.76f)
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Live App / Hub / Media preview title & snippet
                    Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                        Text(
                            text = frame.previewTitle,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (frame.isPlayingMedia) MusicSourceAmber else theme.focusBorder,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (frame.previewSubtitle.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = frame.previewSubtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.5.sp,
                                    lineHeight = 13.sp
                                ),
                                color = if (frame.previewBitmap != null) Color.White.copy(alpha = 0.92f) else TextSecondary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // BB10 style circular corner app icon
                    frame.icon?.let { bmp ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(theme.surfaceElevated)
                                .border(1.dp, theme.chromeInnerGlow.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bmp,
                                contentDescription = frame.appLabel,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(22.dp).clip(CircleShape)
                            )
                        }
                    }
                }

                // Bottom row inside preview: status badge + Pin/Info small Aero controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (frame.badgeText.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(theme.accent.copy(alpha = 0.28f))
                                .border(0.75.dp, theme.accent.copy(alpha = 0.65f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = frame.badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .vistaAeroButton(isPrimary = frame.isPinned, isActive = frame.isPinned)
                                .clickable(onClick = onTogglePin)
                                .testTag("bb10_pin_${frame.packageName}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = "Закрепить",
                                tint = if (frame.isPinned) Color.White else TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .vistaAeroButton()
                                .clickable(onClick = onOpenInfo)
                                .testTag("bb10_info_${frame.packageName}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = "О приложении",
                                tint = TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        // BB10 Bottom Dark Strip: App Name + Prominent 'X' Close Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .symbianMetallicBar()
                .border(0.5.dp, theme.chromeBorderOuter)
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = frame.appLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onClose)
                    .testTag("bb10_close_${frame.packageName}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Закрыть ${frame.appLabel}",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
