package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.model.PlayerCoverStyle
import com.example.ui.L10n
import com.example.ui.LocalAppLang
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
    coverStyle: PlayerCoverStyle = PlayerCoverStyle.COVER,
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
    val lang = LocalAppLang.current
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
                    coverStyle = coverStyle,
                    isPlaying = active?.isPlaying == true,
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
                        text = active?.title ?: L10n.noActiveTrack(lang),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                    val subtitle = when {
                        active != null && active.artist.isNotEmpty() -> active.artist
                        active != null -> MediaControlRepository.formatTimeMs(livePositionMs)
                        !mediaState.isListenerGranted -> L10n.chooseServiceOrTg(lang)
                        else -> L10n.chooseServiceOrTg(lang)
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = TextSecondary,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
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
    coverStyle: PlayerCoverStyle = PlayerCoverStyle.COVER,
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
    val lang = LocalAppLang.current
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
                AlbumArtThumb(
                    artwork = active?.artwork,
                    size = 64.dp,
                    coverStyle = coverStyle,
                    isPlaying = active?.isPlaying == true
                )
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
                    text = active?.title ?: L10n.noActiveTrack(lang),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.5.sp),
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
                if (active != null && active.artist.isNotEmpty()) {
                    Text(
                        text = active.artist,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                }
                Text(
                    text = if (active != null) "⇆ ${active.appLabel}" else "▶",
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

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 32.dp)
                        .vistaAeroButton()
                        .clickable(onClick = onSkipPrevious)
                        .testTag("landscape_music_prev"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Предыдущий трек",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 34.dp)
                        .vistaAeroButton(isPrimary = true)
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("landscape_music_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (active?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (active?.isPlaying == true) "Пауза" else "Играть",
                        tint = theme.focusBorder,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 32.dp)
                        .vistaAeroButton()
                        .clickable(onClick = onSkipNext)
                        .testTag("landscape_music_next"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Следующий трек",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 27.dp)
                        .vistaAeroButton(isActive = mediaState.isMuted)
                        .clickable(onClick = onToggleMute)
                        .testTag("landscape_music_mute"),
                    contentAlignment = Alignment.Center
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
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 27.dp)
                            .vistaAeroButton()
                            .clickable(onClick = onCyclePlayer)
                            .testTag("landscape_music_switch"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = "Сменить плеер",
                            tint = MusicSourceAmber,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 27.dp)
                        .vistaAeroButton(isActive = isDrawerOpen)
                        .clickable(onClick = onTogglePlayerDrawer)
                        .testTag("landscape_music_tune"),
                    contentAlignment = Alignment.Center
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
                        label = L10n.callsSub(lang, CallsSubTab.LOG),
                        iconRes = R.drawable.ic_symbian_log,
                        selected = callsSubTab == CallsSubTab.LOG,
                        onClick = { onSelectCallsSubTab(CallsSubTab.LOG) },
                        modifier = Modifier.testTag("landscape_side_log")
                    )
                    HorizontalDivider(color = theme.divider, thickness = 1.dp)
                    SideRailTabButton(
                        label = L10n.callsSub(lang, CallsSubTab.CONTACTS),
                        iconRes = R.drawable.ic_symbian_contacts,
                        selected = callsSubTab == CallsSubTab.CONTACTS,
                        onClick = { onSelectCallsSubTab(CallsSubTab.CONTACTS) },
                        modifier = Modifier.testTag("landscape_side_contacts")
                    )
                    HorizontalDivider(color = theme.accent.copy(alpha = 0.35f), thickness = 1.5.dp)
                }

                MainTab.HUB -> {
                    SideRailTabButton(
                        label = L10n.hubSub(lang, HubSubTab.CHATS),
                        iconRes = R.drawable.ic_symbian_messaging,
                        selected = hubSubTab == HubSubTab.CHATS,
                        onClick = { onSelectHubSubTab(HubSubTab.CHATS) },
                        modifier = Modifier.testTag("landscape_side_hub_chats")
                    )
                    HorizontalDivider(color = theme.divider, thickness = 1.dp)
                    SideRailTabButton(
                        label = L10n.hubSub(lang, HubSubTab.NEWS),
                        iconRes = R.drawable.ic_symbian_web_news,
                        selected = hubSubTab == HubSubTab.NEWS,
                        onClick = { onSelectHubSubTab(HubSubTab.NEWS) },
                        modifier = Modifier.testTag("landscape_side_hub_news")
                    )
                    HorizontalDivider(color = theme.divider, thickness = 1.dp)
                    SideRailTabButton(
                        label = L10n.hubSub(lang, HubSubTab.OTHER),
                        iconRes = R.drawable.ic_symbian_tools,
                        selected = hubSubTab == HubSubTab.OTHER,
                        onClick = { onSelectHubSubTab(HubSubTab.OTHER) },
                        modifier = Modifier.testTag("landscape_side_hub_other")
                    )
                    HorizontalDivider(color = theme.divider, thickness = 1.dp)
                    SideRailTabButton(
                        label = L10n.hubSub(lang, HubSubTab.SETTINGS),
                        iconRes = R.drawable.ic_symbian_settings,
                        selected = hubSubTab == HubSubTab.SETTINGS,
                        onClick = { onSelectHubSubTab(HubSubTab.SETTINGS) },
                        modifier = Modifier.testTag("landscape_side_hub_settings")
                    )
                    HorizontalDivider(color = theme.accent.copy(alpha = 0.35f), thickness = 1.5.dp)
                }

                MainTab.HOME -> Unit
            }

            SideRailTabButton(
                label = L10n.camera(lang),
                iconRes = R.drawable.ic_symbian_camera,
                selected = false,
                onClick = onLaunchCamera,
                modifier = Modifier.testTag("nav_camera_landscape")
            )
            HorizontalDivider(color = theme.divider, thickness = 1.dp)
            SideRailTabButton(
                label = L10n.tab(lang, MainTab.HOME),
                iconRes = R.drawable.ic_symbian_menu,
                selected = activeTab == MainTab.HOME,
                onClick = { onSelectMainTab(MainTab.HOME) },
                modifier = Modifier.testTag("nav_home_landscape")
            )
            HorizontalDivider(color = theme.divider, thickness = 1.dp)
            SideRailTabButton(
                label = L10n.tab(lang, MainTab.CALLS),
                iconRes = R.drawable.ic_symbian_phone,
                selected = activeTab == MainTab.CALLS,
                onClick = { onSelectMainTab(MainTab.CALLS) },
                modifier = Modifier.testTag("nav_calls_landscape")
            )
            HorizontalDivider(color = theme.divider, thickness = 1.dp)
            SideRailTabButton(
                label = L10n.tab(lang, MainTab.HUB),
                iconRes = R.drawable.ic_symbian_messaging,
                selected = activeTab == MainTab.HUB,
                onClick = { onSelectMainTab(MainTab.HUB) },
                modifier = Modifier.testTag("nav_hub_landscape")
            )
            HorizontalDivider(color = theme.divider, thickness = 1.dp)
            SideRailTabButton(
                label = L10n.books(lang),
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
    coverStyle: PlayerCoverStyle = PlayerCoverStyle.COVER,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    if (coverStyle == PlayerCoverStyle.DISC) {
        val infiniteTransition = rememberInfiniteTransition(label = "disc_spin")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "disc_angle"
        )
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .rotate(if (isPlaying) angle else 0f)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF1A222E), Color(0xFF0A0F16))
                    )
                )
                .border(1.5.dp, theme.chromeInnerGlow.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = "Диск трека",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(size * 0.84f)
                        .clip(CircleShape)
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_symbian_music),
                    contentDescription = null,
                    modifier = Modifier
                        .size(size * 0.56f)
                        .clip(CircleShape)
                )
            }
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.22f),
                    radius = this.size.minDimension * 0.38f,
                    style = Stroke(width = 1.dp.toPx())
                )
                drawCircle(
                    color = Color(0xFF0E141D),
                    radius = this.size.minDimension * 0.14f
                )
                drawCircle(
                    color = theme.chromeInnerGlow.copy(alpha = 0.85f),
                    radius = this.size.minDimension * 0.14f,
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
        }
    } else {
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
}
