package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.ui.theme.LocalVistaTheme
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.symbianMetallicBar
import com.example.ui.theme.vistaAeroButton
import kotlin.math.abs

@Composable
fun PortraitMusicHeaderBar(
    mediaState: MediaPlayerState,
    livePositionMs: Long,
    isDrawerOpen: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCyclePlayer: () -> Unit,
    onSwipeMusicSource: (Int) -> Unit,
    onTogglePlayerDrawer: () -> Unit,
    onOpenPlayerDrawer: () -> Unit,
    onClosePlayerDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    val active = mediaState.activeSession

    Column(
        modifier = modifier
            .fillMaxWidth()
            .symbianMetallicBar()
            .testTag("portrait_music_panel")
    ) {
        // Upper header row: vertical drag DOWN expands the inline player drawer; drag UP closes it.
        // Source horizontal swipe is isolated strictly to the AlbumArtThumb so the seekbar below never conflicts!
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(isDrawerOpen) {
                    var dragY = 0f
                    var triggered = false
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragY = 0f
                            triggered = false
                        },
                        onVerticalDrag = { _, amount ->
                            if (triggered) return@detectVerticalDragGestures
                            dragY += amount
                            if (dragY > 42f) {
                                triggered = true
                                onOpenPlayerDrawer()
                            } else if (dragY < -42f && isDrawerOpen) {
                                triggered = true
                                onClosePlayerDrawer()
                            }
                        }
                    )
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(3.dp))
                    .clickable(onClick = onTogglePlayerDrawer)
                    .padding(end = 6.dp)
                    .testTag("music_header_info"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArtThumb(
                    artwork = active?.artwork,
                    size = 44.dp,
                    modifier = Modifier.pointerInput(Unit) {
                        var dragX = 0f
                        var dragY = 0f
                        var triggered = false
                        detectDragGestures(
                            onDragStart = {
                                dragX = 0f
                                dragY = 0f
                                triggered = false
                            },
                            onDrag = { change, dragAmount ->
                                if (triggered) return@detectDragGestures
                                dragX += dragAmount.x
                                dragY += dragAmount.y
                                if (abs(dragX) > 40f && abs(dragX) > abs(dragY) * 1.4f) {
                                    change.consume()
                                    triggered = true
                                    onSwipeMusicSource(if (dragX < 0f) +1 else -1)
                                } else if (dragY > 42f && dragY > abs(dragX) * 1.4f) {
                                    change.consume()
                                    triggered = true
                                    onOpenPlayerDrawer()
                                }
                            }
                        )
                    }
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = active?.title ?: if (mediaState.isListenerGranted) {
                            "Музыка не играет"
                        } else {
                            "Плеер (подключить)"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = when {
                        active != null && active.artist.isNotEmpty() -> active.artist
                        active != null -> MediaControlRepository.formatTimeMs(livePositionMs)
                        !mediaState.isListenerGranted -> "Свайп вниз — треки и настройки"
                        else -> "Свайп вниз — список треков"
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
                    } else {
                        Text(
                            text = if (isDrawerOpen) "▲ Скрыть панель" else "▼ Потяните вниз — плеер",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = theme.accent.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Windows Vista / Symbian metallic transport cluster
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
                        modifier = Modifier.size(19.dp)
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
                        tint = theme.focusBorder,
                        modifier = Modifier.size(22.dp)
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
                        modifier = Modifier.size(19.dp)
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
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                SymbianTransportButton(
                    onClick = onTogglePlayerDrawer,
                    isActive = isDrawerOpen,
                    modifier = Modifier.testTag("music_tune_button")
                ) {
                    Icon(
                        imageVector = if (isDrawerOpen) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Панель музыки и треков",
                        tint = theme.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Interactive Windows Vista seekbar strip (strictly isolated from source switching!)
        if (active != null && active.durationMs > 0L) {
            var isScrubbing by remember(active.id) { mutableStateOf(false) }
            var scrubPos by remember(active.id) { mutableFloatStateOf(0f) }
            val currentPos = if (isScrubbing) scrubPos.toLong() else livePositionMs

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.insetBg)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = MediaControlRepository.formatTimeMs(currentPos),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = TextSecondary
                )
                VistaSlider(
                    value = currentPos.toFloat().coerceIn(0f, active.durationMs.toFloat()),
                    onValueChange = {
                        isScrubbing = true
                        scrubPos = it
                    },
                    onValueChangeFinished = {
                        isScrubbing = false
                        onSeekTo(scrubPos.toLong())
                    },
                    valueRange = 0f..active.durationMs.toFloat(),
                    compact = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .testTag("header_vista_seek_slider")
                )
                Text(
                    text = MediaControlRepository.formatTimeMs(active.durationMs),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = TextMuted
                )
            }
        } else {
            HorizontalDivider(color = theme.chromeBorderOuter, thickness = 1.dp)
        }
    }
}

@Composable
private fun SymbianTransportButton(
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    isActive: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(width = if (isPrimary) 40.dp else 36.dp, height = 36.dp)
            .vistaAeroButton(isPrimary = isPrimary, isActive = isActive)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Landscape-only Left Sidebar:
 * Top = Symbian/Vista Player (Album Art, Track, Interactive Seekbar, Transport, Volume/Tune)
 * Below Player = Stacked vertical Vista/Symbian buttons one under another
 */
@Composable
fun LandscapeSymbianSidebar(
    mediaState: MediaPlayerState,
    livePositionMs: Long,
    isDrawerOpen: Boolean,
    activeTab: MainTab,
    callsSubTab: CallsSubTab,
    hubSubTab: HubSubTab,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCyclePlayer: () -> Unit,
    onSwipeMusicSource: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onTogglePlayerDrawer: () -> Unit,
    onSelectMainTab: (MainTab) -> Unit,
    onSelectCallsSubTab: (CallsSubTab) -> Unit,
    onSelectHubSubTab: (HubSubTab) -> Unit,
    onLaunchCamera: () -> Unit,
    onOpenWriter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    val active = mediaState.activeSession

    Column(
        modifier = modifier
            .width(156.dp)
            .fillMaxHeight()
            .symbianMetallicBar()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .testTag("landscape_music_panel"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .border(1.dp, theme.chromeInnerGlow.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
                    .pointerInput(Unit) {
                        var dragX = 0f
                        var triggered = false
                        detectDragGestures(
                            onDragStart = {
                                dragX = 0f
                                triggered = false
                            },
                            onDrag = { change, dragAmount ->
                                if (triggered) return@detectDragGestures
                                dragX += dragAmount.x
                                if (abs(dragX) > 40f && abs(dragX) > abs(dragAmount.y) * 1.4f) {
                                    change.consume()
                                    triggered = true
                                    onSwipeMusicSource(if (dragX < 0f) +1 else -1)
                                }
                            }
                        )
                    }
                    .clickable(onClick = onTogglePlayerDrawer)
                    .testTag("landscape_music_art"),
                contentAlignment = Alignment.Center
            ) {
                AlbumArtThumb(artwork = active?.artwork, size = 64.dp)
            }

            Spacer(Modifier.height(5.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .pointerInput(isDrawerOpen) {
                        var dragX = 0f
                        var triggered = false
                        detectDragGestures(
                            onDragStart = {
                                dragX = 0f
                                triggered = false
                            },
                            onDrag = { change, dragAmount ->
                                if (triggered) return@detectDragGestures
                                dragX += dragAmount.x
                                if (dragX > 36f && !isDrawerOpen) {
                                    change.consume()
                                    triggered = true
                                    onTogglePlayerDrawer()
                                } else if (dragX < -36f && isDrawerOpen) {
                                    change.consume()
                                    triggered = true
                                    onTogglePlayerDrawer()
                                }
                            }
                        )
                    }
                    .clickable(onClick = onTogglePlayerDrawer)
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
                    text = if (active != null) "⇆ ${active.appLabel}" else "▶ Треки вбок",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = if (active != null) MusicSourceAmber else theme.accent,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (active != null && active.durationMs > 0L) {
                var isScrubbing by remember(active.id) { mutableStateOf(false) }
                var scrubPos by remember(active.id) { mutableFloatStateOf(0f) }
                val currentPos = if (isScrubbing) scrubPos.toLong() else livePositionMs

                Spacer(Modifier.height(4.dp))
                VistaSlider(
                    value = currentPos.toFloat().coerceIn(0f, active.durationMs.toFloat()),
                    onValueChange = {
                        isScrubbing = true
                        scrubPos = it
                    },
                    onValueChangeFinished = {
                        isScrubbing = false
                        onSeekTo(scrubPos.toLong())
                    },
                    valueRange = 0f..active.durationMs.toFloat(),
                    compact = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = MediaControlRepository.formatTimeMs(currentPos),
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

            Spacer(Modifier.height(5.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(32.dp)
                        .vistaAeroButton()
                        .testTag("landscape_music_prev")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Предыдущий трек",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(34.dp)
                        .vistaAeroButton(isPrimary = true)
                        .testTag("landscape_music_play_pause")
                ) {
                    Icon(
                        imageVector = if (active?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (active?.isPlaying == true) "Пауза" else "Играть",
                        tint = theme.focusBorder,
                        modifier = Modifier.size(21.dp)
                    )
                }
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(32.dp)
                        .vistaAeroButton()
                        .testTag("landscape_music_next")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Следующий трек",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier.size(28.dp)
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
                        modifier = Modifier.size(28.dp)
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
                    onClick = onTogglePlayerDrawer,
                    modifier = Modifier
                        .size(28.dp)
                        .then(if (isDrawerOpen) Modifier.vistaAeroButton(isActive = true) else Modifier)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Панель музыки и треков",
                        tint = theme.accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = theme.accent.copy(alpha = 0.35f), thickness = 1.dp)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(theme.surface.copy(alpha = 0.78f))
                .verticalScroll(rememberScrollState())
        ) {
            when (activeTab) {
                MainTab.CALLS -> {
                    SideRailTabButton(
                        label = "Журнал",
                        iconRes = R.drawable.ic_symbian_log,
                        selected = callsSubTab == CallsSubTab.LOG,
                        onClick = { onSelectCallsSubTab(CallsSubTab.LOG) },
                        modifier = Modifier.testTag("landscape_side_log")
                    )
                    HorizontalDivider(color = theme.divider, thickness = 1.dp)
                    SideRailTabButton(
                        label = "Контакты",
                        iconRes = R.drawable.ic_symbian_contacts,
                        selected = callsSubTab == CallsSubTab.CONTACTS,
                        onClick = { onSelectCallsSubTab(CallsSubTab.CONTACTS) },
                        modifier = Modifier.testTag("landscape_side_contacts")
                    )
                    HorizontalDivider(color = theme.accent.copy(alpha = 0.35f), thickness = 1.5.dp)
                }

                MainTab.HUB -> {
                    SideRailTabButton(
                        label = "Чаты",
                        iconRes = R.drawable.ic_symbian_messaging,
                        selected = hubSubTab == HubSubTab.CHATS,
                        onClick = { onSelectHubSubTab(HubSubTab.CHATS) },
                        modifier = Modifier.testTag("landscape_side_hub_chats")
                    )
                    HorizontalDivider(color = theme.divider, thickness = 1.dp)
                    SideRailTabButton(
                        label = "Новости",
                        iconRes = R.drawable.ic_symbian_web_news,
                        selected = hubSubTab == HubSubTab.NEWS,
                        onClick = { onSelectHubSubTab(HubSubTab.NEWS) },
                        modifier = Modifier.testTag("landscape_side_hub_news")
                    )
                    HorizontalDivider(color = theme.divider, thickness = 1.dp)
                    SideRailTabButton(
                        label = "Прочее",
                        iconRes = R.drawable.ic_symbian_tools,
                        selected = hubSubTab == HubSubTab.OTHER,
                        onClick = { onSelectHubSubTab(HubSubTab.OTHER) },
                        modifier = Modifier.testTag("landscape_side_hub_other")
                    )
                    HorizontalDivider(color = theme.accent.copy(alpha = 0.35f), thickness = 1.5.dp)
                }

                MainTab.HOME -> Unit
            }

            SideRailTabButton(
                label = "Камера",
                iconRes = R.drawable.ic_symbian_camera,
                selected = false,
                onClick = onLaunchCamera,
                modifier = Modifier.testTag("nav_camera_landscape")
            )
            HorizontalDivider(color = theme.divider, thickness = 1.dp)
            SideRailTabButton(
                label = "Главная",
                iconRes = R.drawable.ic_symbian_menu,
                selected = activeTab == MainTab.HOME,
                onClick = { onSelectMainTab(MainTab.HOME) },
                modifier = Modifier.testTag("nav_home_landscape")
            )
            HorizontalDivider(color = theme.divider, thickness = 1.dp)
            SideRailTabButton(
                label = "Звонки",
                iconRes = R.drawable.ic_symbian_phone,
                selected = activeTab == MainTab.CALLS,
                onClick = { onSelectMainTab(MainTab.CALLS) },
                modifier = Modifier.testTag("nav_calls_landscape")
            )
            HorizontalDivider(color = theme.divider, thickness = 1.dp)
            SideRailTabButton(
                label = "Хаб",
                iconRes = R.drawable.ic_symbian_messaging,
                selected = activeTab == MainTab.HUB,
                onClick = { onSelectMainTab(MainTab.HUB) },
                modifier = Modifier.testTag("nav_hub_landscape")
            )
            HorizontalDivider(color = theme.divider, thickness = 1.dp)
            SideRailTabButton(
                label = "Книги",
                iconRes = R.drawable.ic_symbian_editor,
                selected = false,
                onClick = onOpenWriter,
                modifier = Modifier.testTag("nav_writer_landscape")
            )
            HorizontalDivider(color = theme.divider, thickness = 1.dp)
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
    val theme = LocalVistaTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .background(
                if (selected) {
                    Brush.verticalGradient(
                        listOf(
                            theme.primaryButtonUpperMid.copy(alpha = 0.55f),
                            theme.primaryButtonLowerMid.copy(alpha = 0.25f)
                        )
                    )
                } else {
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
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
                    .background(theme.focusBorder)
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
            color = if (selected) theme.focusBorder else TextPrimary,
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
    val theme = LocalVistaTheme.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(3.dp))
            .background(theme.surfaceElevated)
            .border(1.dp, theme.chromeInnerGlow.copy(alpha = 0.65f), RoundedCornerShape(3.dp)),
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
