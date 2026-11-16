package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.MediaControlRepository
import com.example.model.CallsSubTab
import com.example.model.HubSubTab
import com.example.model.MainTab
import com.example.model.MediaPlayerState
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FocusBorderCyan
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.NavyDivider
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.SymbianBevelHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TileBrightAzure
import com.example.ui.theme.TileBrightAzureBottom
import com.example.ui.theme.TileBrightAzureTop
import com.example.ui.theme.symbianMetallicBar

import kotlin.math.abs

@Composable
fun PortraitMusicHeaderBar(
    mediaState: MediaPlayerState,
    livePositionMs: Long,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onCyclePlayer: () -> Unit,
    onSwipeMusicSource: (Int) -> Unit,
    onOpenMusicSheet: () -> Unit,
    onSwipeDownShade: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = mediaState.activeSession
    val progress = if (active != null && active.durationMs > 0L) {
        (livePositionMs.toFloat() / active.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .symbianMetallicBar()
            .pointerInput(Unit) {
                var dragX = 0f
                var dragY = 0f
                var triggered = false
                detectDragGestures(
                    onDragStart = {
                        dragX = 0f
                        dragY = 0f
                        triggered = false
                    },
                    onDrag = { _, dragAmount ->
                        if (triggered) return@detectDragGestures
                        dragX += dragAmount.x
                        dragY += dragAmount.y
                        if (abs(dragX) > 55f && abs(dragX) > abs(dragY) * 1.25f) {
                            triggered = true
                            onSwipeMusicSource(if (dragX < 0f) +1 else -1)
                        } else if (dragY > 70f && dragY > abs(dragX) * 1.25f) {
                            triggered = true
                            onSwipeDownShade()
                        }
                    }
                )
            }
            .testTag("portrait_music_panel")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onOpenMusicSheet)
                    .padding(end = 6.dp)
                    .testTag("music_header_info"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArtThumb(
                    artwork = active?.artwork,
                    size = 44.dp
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = active?.title ?: if (mediaState.isListenerGranted) {
                            "Музыка не играет"
                        } else {
                            "Плеер (подключить)"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = when {
                        active != null && active.artist.isNotEmpty() -> active.artist
                        active != null -> MediaControlRepository.formatTimeMs(livePositionMs)
                        !mediaState.isListenerGranted -> "Нажмите для доступа к трекам"
                        else -> "Нажмите для выбора плеера"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (active != null) {
                        val multiSuffix = if (mediaState.sessions.size > 1) {
                            val idx = mediaState.sessions.indexOfFirst { it.id == active.id } + 1
                            " ($idx/${mediaState.sessions.size})"
                        } else ""
                        Text(
                            text = "⇆ ${active.appLabel}$multiSuffix",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MusicSourceAmber,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (mediaState.isListenerGranted) {
                        Text(
                            text = "⇆ Свайп для смены плеера",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = MusicSourceAmber.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Symbian metallic transport cluster (clean portrait player bar without Чаты/Новости)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                SymbianTransportButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier.testTag("music_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Предыдущий трек",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                SymbianTransportButton(
                    onClick = onTogglePlayPause,
                    isPrimary = true,
                    modifier = Modifier.testTag("music_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (active?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (active?.isPlaying == true) "Пауза" else "Воспроизвести",
                        tint = FocusBorderCyan,
                        modifier = Modifier.size(23.dp)
                    )
                }

                SymbianTransportButton(
                    onClick = onSkipNext,
                    modifier = Modifier.testTag("music_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Следующий трек",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (mediaState.sessions.size > 1) {
                    SymbianTransportButton(
                        onClick = onCyclePlayer,
                        modifier = Modifier.testTag("music_switch_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = "Переключить плеер",
                            tint = MusicSourceAmber,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                SymbianTransportButton(
                    onClick = onOpenMusicSheet,
                    modifier = Modifier.testTag("music_tune_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Панель музыки",
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (active != null && active.durationMs > 0L) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = ElectricCyan,
                trackColor = NavyDivider
            )
        } else {
            HorizontalDivider(color = NavyDivider, thickness = 1.dp)
        }
    }
}

@Composable
private fun SymbianTransportButton(
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(width = if (isPrimary) 42.dp else 38.dp, height = 38.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(
                if (isPrimary) {
                    Brush.verticalGradient(listOf(TileBrightAzureTop, TileBrightAzure, TileBrightAzureBottom))
                } else {
                    Brush.verticalGradient(listOf(NavySurfaceElevated, NavySurface))
                }
            )
            .border(
                width = 1.dp,
                color = if (isPrimary) ElectricCyan.copy(alpha = 0.75f) else SymbianBevelHighlight.copy(alpha = 0.45f),
                shape = RoundedCornerShape(3.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Landscape-only Left Sidebar:
 * Single compact vertical column:
 * Top = Symbian Player (Album Art, Track, Progress, Transport, Volume/Tune)
 * Below Player = Stacked vertical buttons one under another ("под него друг под другом"):
 *   Context Sub-Tabs (Calls: Журнал / Контакты, Hub: Чаты / Новости / Прочее) + Main Nav (Камера, Главная, Звонки, Хаб)
 */
@Composable
fun LandscapeSymbianSidebar(
    mediaState: MediaPlayerState,
    livePositionMs: Long,
    activeTab: MainTab,
    callsSubTab: CallsSubTab,
    hubSubTab: HubSubTab,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onCyclePlayer: () -> Unit,
    onSwipeMusicSource: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onOpenMusicSheet: () -> Unit,
    onSelectMainTab: (MainTab) -> Unit,
    onSelectCallsSubTab: (CallsSubTab) -> Unit,
    onSelectHubSubTab: (HubSubTab) -> Unit,
    onLaunchCamera: () -> Unit,
    onOpenWriter: () -> Unit,
    onSwipeDownShade: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = mediaState.activeSession
    val progress = if (active != null && active.durationMs > 0L) {
        (livePositionMs.toFloat() / active.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .width(152.dp)
            .fillMaxHeight()
            .symbianMetallicBar()
    ) {
        // Top Section: Symbian Player (supports horizontal swipe to switch music source + swipe down for shade)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    var dragX = 0f
                    var dragY = 0f
                    var triggered = false
                    detectDragGestures(
                        onDragStart = {
                            dragX = 0f
                            dragY = 0f
                            triggered = false
                        },
                        onDrag = { _, dragAmount ->
                            if (triggered) return@detectDragGestures
                            dragX += dragAmount.x
                            dragY += dragAmount.y
                            if (abs(dragX) > 50f && abs(dragX) > abs(dragY) * 1.25f) {
                                triggered = true
                                onSwipeMusicSource(if (dragX < 0f) +1 else -1)
                            } else if (dragY > 70f && dragY > abs(dragX) * 1.25f) {
                                triggered = true
                                onSwipeDownShade()
                            }
                        }
                    )
                }
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .testTag("landscape_music_panel"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, SymbianBevelHighlight.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                    .clickable(onClick = onOpenMusicSheet)
                    .testTag("landscape_music_art"),
                contentAlignment = Alignment.Center
            ) {
                AlbumArtThumb(artwork = active?.artwork, size = 66.dp)
            }

            Spacer(Modifier.height(5.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .clickable(onClick = onOpenMusicSheet)
                    .padding(horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = active?.title ?: if (mediaState.isListenerGranted) "Нет трека" else "Подключить",
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.5.sp),
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (active != null && active.artist.isNotEmpty()) {
                    Text(
                        text = active.artist,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (active != null) "⇆ ${active.appLabel}" else if (!mediaState.isListenerGranted) "Доступ к медиа" else "⇆ Плеер",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = if (active != null) MusicSourceAmber else ElectricCyan,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (active != null && active.durationMs > 0L) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = ElectricCyan,
                    trackColor = NavyDivider
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = MediaControlRepository.formatTimeMs(livePositionMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                        color = TextMuted
                    )
                    Text(
                        text = MediaControlRepository.formatTimeMs(active.durationMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                        color = TextMuted
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Transport row directly beneath track info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("landscape_music_prev")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Предыдущий трек",
                        tint = TextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("landscape_music_play_pause")
                ) {
                    Icon(
                        imageVector = if (active?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (active?.isPlaying == true) "Пауза" else "Играть",
                        tint = ElectricCyan,
                        modifier = Modifier.size(23.dp)
                    )
                }
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("landscape_music_next")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Следующий трек",
                        tint = TextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // Compact sound & player sheet utility bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (mediaState.isMuted) {
                            Icons.AutoMirrored.Rounded.VolumeOff
                        } else {
                            Icons.AutoMirrored.Rounded.VolumeUp
                        },
                        contentDescription = if (mediaState.isMuted) "Включить звук" else "Без звука",
                        tint = if (mediaState.isMuted) TextMuted else TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                if (mediaState.sessions.size > 1) {
                    IconButton(
                        onClick = onCyclePlayer,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = "Сменить плеер",
                            tint = MusicSourceAmber,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onOpenMusicSheet,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Управление музыкой",
                        tint = ElectricCyan,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = ElectricCyan.copy(alpha = 0.35f), thickness = 1.dp)

        // Stacked vertical buttons directly UNDER the player ("под него друг под другом")
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(NavySurface.copy(alpha = 0.65f))
                .verticalScroll(rememberScrollState())
        ) {
            // Context sub-tabs moved under the player for Calls & Hub
            when (activeTab) {
                MainTab.CALLS -> {
                    SideRailTabButton(
                        label = "Журнал",
                        iconRes = R.drawable.ic_symbian_log,
                        selected = callsSubTab == CallsSubTab.LOG,
                        onClick = { onSelectCallsSubTab(CallsSubTab.LOG) },
                        modifier = Modifier.testTag("landscape_side_log")
                    )
                    HorizontalDivider(color = NavyDivider, thickness = 1.dp)
                    SideRailTabButton(
                        label = "Контакты",
                        iconRes = R.drawable.ic_symbian_contacts,
                        selected = callsSubTab == CallsSubTab.CONTACTS,
                        onClick = { onSelectCallsSubTab(CallsSubTab.CONTACTS) },
                        modifier = Modifier.testTag("landscape_side_contacts")
                    )
                    HorizontalDivider(color = ElectricCyan.copy(alpha = 0.35f), thickness = 1.5.dp)
                }

                MainTab.HUB -> {
                    SideRailTabButton(
                        label = "Чаты",
                        iconRes = R.drawable.ic_symbian_messaging,
                        selected = hubSubTab == HubSubTab.CHATS,
                        onClick = { onSelectHubSubTab(HubSubTab.CHATS) },
                        modifier = Modifier.testTag("landscape_side_hub_chats")
                    )
                    HorizontalDivider(color = NavyDivider, thickness = 1.dp)
                    SideRailTabButton(
                        label = "Новости",
                        iconRes = R.drawable.ic_symbian_web_news,
                        selected = hubSubTab == HubSubTab.NEWS,
                        onClick = { onSelectHubSubTab(HubSubTab.NEWS) },
                        modifier = Modifier.testTag("landscape_side_hub_news")
                    )
                    HorizontalDivider(color = NavyDivider, thickness = 1.dp)
                    SideRailTabButton(
                        label = "Прочее",
                        iconRes = R.drawable.ic_symbian_tools,
                        selected = hubSubTab == HubSubTab.OTHER,
                        onClick = { onSelectHubSubTab(HubSubTab.OTHER) },
                        modifier = Modifier.testTag("landscape_side_hub_other")
                    )
                    HorizontalDivider(color = ElectricCyan.copy(alpha = 0.35f), thickness = 1.5.dp)
                }

                MainTab.HOME -> Unit
            }

            // Primary Navigation Stack: Камера, Главная, Звонки, Хаб stacked one under another
            SideRailTabButton(
                label = "Камера",
                iconRes = R.drawable.ic_symbian_camera,
                selected = false,
                onClick = onLaunchCamera,
                modifier = Modifier.testTag("nav_camera_landscape")
            )
            HorizontalDivider(color = NavyDivider, thickness = 1.dp)
            SideRailTabButton(
                label = "Главная",
                iconRes = R.drawable.ic_symbian_menu,
                selected = activeTab == MainTab.HOME,
                onClick = { onSelectMainTab(MainTab.HOME) },
                modifier = Modifier.testTag("nav_home_landscape")
            )
            HorizontalDivider(color = NavyDivider, thickness = 1.dp)
            SideRailTabButton(
                label = "Звонки",
                iconRes = R.drawable.ic_symbian_phone,
                selected = activeTab == MainTab.CALLS,
                onClick = { onSelectMainTab(MainTab.CALLS) },
                modifier = Modifier.testTag("nav_calls_landscape")
            )
            HorizontalDivider(color = NavyDivider, thickness = 1.dp)
            SideRailTabButton(
                label = "Хаб",
                iconRes = R.drawable.ic_symbian_messaging,
                selected = activeTab == MainTab.HUB,
                onClick = { onSelectMainTab(MainTab.HUB) },
                modifier = Modifier.testTag("nav_hub_landscape")
            )
            HorizontalDivider(color = NavyDivider, thickness = 1.dp)
            SideRailTabButton(
                label = "Книги",
                iconRes = R.drawable.ic_symbian_editor,
                selected = false,
                onClick = onOpenWriter,
                modifier = Modifier.testTag("nav_writer_landscape")
            )
            HorizontalDivider(color = NavyDivider, thickness = 1.dp)
        }
    }
}

@Composable
private fun SideRailTabButton(
    label: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .background(
                if (selected) {
                    Brush.horizontalGradient(
                        listOf(TileBrightAzure.copy(alpha = 0.42f), TileBrightAzure.copy(alpha = 0.16f))
                    )
                } else {
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(22.dp)
                    .background(FocusBorderCyan)
            )
            Spacer(Modifier.width(9.dp))
        } else {
            Spacer(Modifier.width(12.dp))
        }

        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) FocusBorderCyan else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AlbumArtThumb(
    artwork: androidx.compose.ui.graphics.ImageBitmap?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(3.dp))
            .background(NavySurfaceElevated)
            .border(1.dp, SymbianBevelHighlight.copy(alpha = 0.55f), RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (artwork != null) {
            Image(
                bitmap = artwork,
                contentDescription = "Обложка трека",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_symbian_music),
                contentDescription = null,
                modifier = Modifier.size(size * 0.58f)
            )
        }
    }
}
