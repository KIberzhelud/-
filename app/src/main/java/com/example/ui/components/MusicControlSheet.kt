package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.KeyboardSearchMatcher
import com.example.data.MediaControlRepository
import com.example.model.AppEntry
import com.example.model.HubNotificationItem
import com.example.model.LocalAudioTrack
import com.example.model.MediaBrowserTrack
import com.example.model.MediaPlayerState
import com.example.model.MediaQueueTrack
import com.example.model.MusicDrawerTab
import com.example.ui.theme.LocalVistaTheme
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.symbianMetallicBar
import com.example.ui.theme.vistaAeroButton
import com.example.ui.theme.vistaInsetBox
import com.example.ui.theme.vistaWindowFrame

private val TELEGRAM_CLIENT_TOKENS = listOf(
    "telegram",
    "exteragram",
    "nekogram",
    "ayugram",
    "challegram"
)

private fun isTelegramPackage(packageName: String): Boolean {
    val lower = packageName.lowercase()
    return TELEGRAM_CLIENT_TOKENS.any { lower.contains(it) }
}

/**
 * Inline expandable Windows Vista / Symbian music control & deep streaming drawer.
 * - Portrait: slides down beneath the top player bar, pushing tile grid + rail down.
 * - Landscape: slides out horizontally to the side from the left sidebar.
 */
@Composable
fun InlineMusicControlDrawer(
    mediaState: MediaPlayerState,
    livePositionMs: Long,
    selectedTab: MusicDrawerTab,
    localTracks: List<LocalAudioTrack>,
    telegramNotifications: List<HubNotificationItem>,
    trackSearchQuery: String,
    hasAudioReadAccess: Boolean,
    hasAllFilesAccess: Boolean,
    musicApps: List<AppEntry>,
    isLandscape: Boolean,
    onSelectTab: (MusicDrawerTab) -> Unit,
    onTrackSearchChange: (String) -> Unit,
    onSkipToQueueItem: (Long) -> Unit,
    onInvokeCustomAction: (String) -> Unit,
    onPlayBrowserTrack: (MediaBrowserTrack) -> Unit,
    onSearchAndPlayInStream: (String, String?) -> Unit,
    onConnectBrowser: (String?) -> Unit,
    onPlayLocalTrack: (LocalAudioTrack) -> Unit,
    onOpenHubNotification: (String) -> Unit,
    onRefreshTracks: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onClose: () -> Unit,
    onSelectSession: (String) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onOpenActivePlayer: () -> Unit,
    onSetVolume: (Int) -> Unit,
    onToggleMute: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onLaunchMusicApp: (AppEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    val active = mediaState.activeSession

    val drawerBody: @Composable (Modifier) -> Unit = { contentModifier ->
        Column(
            modifier = contentModifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Drawer Title / Active Stream Open / Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .border(1.dp, theme.chromeBorderOuter, RoundedCornerShape(3.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_symbian_music),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isLandscape) "ТРЕКИ И СТРИМИНГ" else "СТРИМИНГ, ОЧЕРЕДЬ И ТГ",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = theme.focusBorder,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (active != null && active.id != MediaControlRepository.LOCAL_SESSION_ID) {
                        Box(
                            modifier = Modifier
                                .vistaAeroButton()
                                .clickable {
                                    onOpenActivePlayer()
                                    onClose()
                                }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                .testTag("drawer_open_player_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = "Открыть ${active.appLabel}",
                                    tint = theme.accent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = active.appLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .vistaAeroButton()
                            .clickable(onClick = onClose)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("drawer_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Закрыть панель плеера",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            if (!mediaState.isListenerGranted) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = MusicSourceAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Включите доступ к уведомлениям для очереди стримингов и ТГ",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 2
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    VistaButton(
                        text = "Включить",
                        isPrimary = true,
                        onClick = onOpenNotificationAccess,
                        modifier = Modifier.testTag("grant_media_listener_button")
                    )
                }
            }

            // Active Streaming & Telegram Sessions selector
            if (mediaState.sessions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Источник:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    mediaState.sessions.forEach { session ->
                        val selected = session.id == active?.id
                        Box(
                            modifier = Modifier
                                .vistaAeroButton(isPrimary = selected, isActive = selected)
                                .clickable { onSelectSession(session.id) }
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (session.isPlaying) "▶ ${session.appLabel}" else session.appLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selected) Color.White else TextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Seek Trackbar (Windows Vista slider)
            if (active != null && active.durationMs > 0L) {
                Spacer(Modifier.height(6.dp))
                var isDragging by remember { mutableStateOf(false) }
                var sliderPos by remember(active.id) { mutableFloatStateOf(0f) }
                val displayPos = if (isDragging) sliderPos.toLong() else livePositionMs

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = MediaControlRepository.formatTimeMs(displayPos),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.width(38.dp)
                    )
                    VistaSlider(
                        value = displayPos.toFloat().coerceIn(0f, active.durationMs.toFloat()),
                        onValueChange = {
                            isDragging = true
                            sliderPos = it
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            onSeekTo(sliderPos.toLong())
                        },
                        valueRange = 0f..active.durationMs.toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("drawer_vista_seek_slider")
                    )
                    Text(
                        text = MediaControlRepository.formatTimeMs(active.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Transport controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 31.dp)
                        .vistaAeroButton()
                        .clickable(enabled = active?.canSeek == true) { onSeekBy(-10_000L) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Replay10,
                        contentDescription = "Назад 10 секунд",
                        tint = if (active?.canSeek == true) TextPrimary else TextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 33.dp)
                        .vistaAeroButton()
                        .clickable(onClick = onSkipPrevious),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Предыдущий трек",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(width = 54.dp, height = 35.dp)
                        .vistaAeroButton(isPrimary = true)
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("drawer_play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (active?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (active?.isPlaying == true) "Пауза" else "Воспроизвести",
                        tint = Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 33.dp)
                        .vistaAeroButton()
                        .clickable(onClick = onSkipNext),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Следующий трек",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 31.dp)
                        .vistaAeroButton()
                        .clickable(enabled = active?.canSeek == true) { onSeekBy(10_000L) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Forward10,
                        contentDescription = "Вперёд 10 секунд",
                        tint = if (active?.canSeek == true) TextPrimary else TextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // Custom Actions (Like, Dislike, Shuffle, Repeat exposed by Yandex Music / Spotify / YT Music)
            if (active != null && active.customActions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    active.customActions.forEach { act ->
                        Box(
                            modifier = Modifier
                                .vistaAeroButton()
                                .clickable { onInvokeCustomAction(act.action) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = act.name,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = theme.accent,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Vista Volume Trackbar Row
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .vistaAeroButton()
                        .clickable(onClick = onToggleMute),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (mediaState.isMuted) {
                            Icons.AutoMirrored.Rounded.VolumeOff
                        } else if (mediaState.systemVolume < mediaState.maxVolume / 2) {
                            Icons.AutoMirrored.Rounded.VolumeDown
                        } else {
                            Icons.AutoMirrored.Rounded.VolumeUp
                        },
                        contentDescription = "Громкость / Без звука",
                        tint = theme.accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
                VistaSlider(
                    value = mediaState.systemVolume.toFloat(),
                    onValueChange = { onSetVolume(it.toInt()) },
                    valueRange = 0f..mediaState.maxVolume.toFloat().coerceAtLeast(1f),
                    accentCyan = false,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                        .testTag("drawer_vista_volume_slider")
                )
                Text(
                    text = "${mediaState.systemVolume}/${mediaState.maxVolume}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(7.dp))
            HorizontalDivider(color = theme.divider, thickness = 1.dp)
            Spacer(Modifier.height(7.dp))

            // Mode Tabs: Плейлист (Streaming Queue + Search) | ТГ Музыка | Файлы
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                MusicDrawerTabPill(
                    title = MusicDrawerTab.QUEUE.title,
                    selected = selectedTab == MusicDrawerTab.QUEUE,
                    onClick = { onSelectTab(MusicDrawerTab.QUEUE) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("music_tab_queue")
                )
                MusicDrawerTabPill(
                    title = MusicDrawerTab.TELEGRAM.title,
                    selected = selectedTab == MusicDrawerTab.TELEGRAM,
                    onClick = { onSelectTab(MusicDrawerTab.TELEGRAM) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("music_tab_telegram")
                )
                MusicDrawerTabPill(
                    title = MusicDrawerTab.LOCAL.title,
                    selected = selectedTab == MusicDrawerTab.LOCAL,
                    onClick = { onSelectTab(MusicDrawerTab.LOCAL) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("music_tab_local")
                )
            }

            Spacer(Modifier.height(7.dp))

            // Track / Stream Search Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .vistaInsetBox()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Поиск трека в стриминге и ТГ",
                        tint = theme.accent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (trackSearchQuery.isEmpty()) {
                            Text(
                                text = "Поиск треков в стриминге и ТГ…",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = trackSearchQuery,
                            onValueChange = onTrackSearchChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = TextPrimary,
                                fontSize = 12.sp
                            ),
                            cursorBrush = SolidColor(theme.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (trackSearchQuery.isNotBlank()) {
                                        onSearchAndPlayInStream(trackSearchQuery, active?.packageName)
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("drawer_track_search_input")
                        )
                    }
                    if (mediaState.isSearchingStream) {
                        CircularProgressIndicator(
                            color = theme.accent,
                            strokeWidth = 1.5.dp,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    if (trackSearchQuery.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Очистить поиск треков",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onTrackSearchChange("") }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .vistaAeroButton()
                        .clickable(onClick = onRefreshTracks)
                        .testTag("drawer_refresh_tracks_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Обновить очередь и треки",
                        tint = theme.accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Direct Streaming Search-and-Play Button when query is entered
            if (trackSearchQuery.isNotBlank() && selectedTab != MusicDrawerTab.LOCAL) {
                Spacer(Modifier.height(5.dp))
                val targetServiceName = active?.takeIf { it.id != MediaControlRepository.LOCAL_SESSION_ID }?.appLabel
                    ?: musicApps.firstOrNull()?.label
                    ?: "стриминге"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaAeroButton(isPrimary = true)
                        .clickable {
                            onSearchAndPlayInStream(
                                trackSearchQuery,
                                active?.takeIf { it.id != MediaControlRepository.LOCAL_SESSION_ID }?.packageName
                                    ?: musicApps.firstOrNull()?.packageName
                            )
                        }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                        .testTag("drawer_play_from_search_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Найти и играть «${trackSearchQuery.trim()}» в $targetServiceName",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // TAB BODY
            when (selectedTab) {
                MusicDrawerTab.QUEUE -> {
                    val queueItems = remember(active?.queue, trackSearchQuery) {
                        val raw = active?.queue.orEmpty()
                        if (trackSearchQuery.isBlank()) raw
                        else raw.filter {
                            KeyboardSearchMatcher.matches(it.title, trackSearchQuery) ||
                                KeyboardSearchMatcher.matches(it.subtitle, trackSearchQuery)
                        }
                    }
                    val browserResults = remember(mediaState.browserSearchResults, mediaState.browsableTracks, trackSearchQuery) {
                        if (mediaState.browserSearchResults.isNotEmpty()) {
                            mediaState.browserSearchResults
                        } else if (trackSearchQuery.isBlank()) {
                            mediaState.browsableTracks
                        } else {
                            mediaState.browsableTracks.filter {
                                KeyboardSearchMatcher.matches(it.title, trackSearchQuery) ||
                                    KeyboardSearchMatcher.matches(it.subtitle, trackSearchQuery)
                            }
                        }
                    }
                    val filteredRecent = remember(mediaState.recentTracks, trackSearchQuery) {
                        val raw = mediaState.recentTracks
                        if (trackSearchQuery.isBlank()) raw
                        else raw.filter {
                            KeyboardSearchMatcher.matches(it.title, trackSearchQuery) ||
                                KeyboardSearchMatcher.matches(it.subtitle, trackSearchQuery) ||
                                KeyboardSearchMatcher.matches(it.appLabel, trackSearchQuery)
                        }
                    }

                    if (queueItems.isEmpty() && browserResults.isEmpty() && filteredRecent.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .vistaInsetBox()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Очередь стриминга пока пуста. Запустите трек в Яндекс Музыке / Spotify / ТГ или подключите каталог.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                            VistaButton(
                                text = "Подключить каталог (${active?.appLabel ?: "плеер"})",
                                isPrimary = true,
                                onClick = { onConnectBrowser(active?.packageName) }
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = if (isLandscape) 240.dp else 210.dp)
                                .vistaInsetBox()
                                .testTag("drawer_streaming_queue_list"),
                            contentPadding = PaddingValues(4.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (queueItems.isNotEmpty()) {
                                item(key = "hdr_queue") {
                                    DrawerSectionLabel("ОЧЕРЕДЬ ВОСПРОИЗВЕДЕНИЯ (${active?.appLabel ?: ""})")
                                }
                                items(queueItems, key = { "q_${it.queueId}_${it.title}" }) { qItem ->
                                    QueueTrackRow(
                                        item = qItem,
                                        isActive = qItem.isActive,
                                        onClick = { onSkipToQueueItem(qItem.queueId) }
                                    )
                                }
                            }

                            if (browserResults.isNotEmpty()) {
                                item(key = "hdr_browser") {
                                    DrawerSectionLabel(
                                        if (mediaState.browserSearchResults.isNotEmpty()) "РЕЗУЛЬТАТЫ В КАТАЛОГЕ"
                                        else "КАТАЛОГ И ПЛЕЙЛИСТЫ (${browserResults.size})"
                                    )
                                }
                                items(browserResults, key = { "br_${it.mediaId}" }) { bItem ->
                                    BrowserTrackRow(
                                        item = bItem,
                                        onClick = { onPlayBrowserTrack(bItem) }
                                    )
                                }
                            }

                            if (filteredRecent.isNotEmpty()) {
                                item(key = "hdr_recent") {
                                    DrawerSectionLabel("НЕДАВНИЕ ТРЕКИ (${filteredRecent.size})")
                                }
                                items(filteredRecent, key = { "rec_${it.packageName}_${it.title}_${it.subtitle}" }) { rItem ->
                                    QueueTrackRow(
                                        item = rItem,
                                        isActive = active?.title == rItem.title && active.packageName == rItem.packageName,
                                        onClick = {
                                            val matchingQueue = active?.queue?.firstOrNull {
                                                it.title.equals(rItem.title, ignoreCase = true)
                                            }
                                            if (matchingQueue != null) {
                                                onSkipToQueueItem(matchingQueue.queueId)
                                            } else {
                                                onSearchAndPlayInStream(
                                                    "${rItem.title} ${rItem.subtitle}".trim(),
                                                    rItem.packageName
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                MusicDrawerTab.TELEGRAM -> {
                    val tgSessions = remember(mediaState.sessions) {
                        mediaState.sessions.filter { isTelegramPackage(it.packageName) }
                    }
                    val tgRecent = remember(mediaState.recentTracks, trackSearchQuery) {
                        mediaState.recentTracks.filter { item ->
                            isTelegramPackage(item.packageName) && (
                                trackSearchQuery.isBlank() ||
                                    KeyboardSearchMatcher.matches(item.title, trackSearchQuery) ||
                                    KeyboardSearchMatcher.matches(item.subtitle, trackSearchQuery)
                                )
                        }
                    }
                    val tgAudioFiles = remember(localTracks, trackSearchQuery) {
                        localTracks.filter { track ->
                            track.isTelegramAudio && (
                                trackSearchQuery.isBlank() ||
                                    KeyboardSearchMatcher.matches(track.title, trackSearchQuery) ||
                                    KeyboardSearchMatcher.matches(track.artist, trackSearchQuery)
                                )
                        }
                    }
                    val tgMsgs = remember(telegramNotifications, trackSearchQuery) {
                        telegramNotifications.filter { notif ->
                            isTelegramPackage(notif.packageName) && (
                                trackSearchQuery.isBlank() ||
                                    KeyboardSearchMatcher.matches(notif.title, trackSearchQuery) ||
                                    KeyboardSearchMatcher.matches(notif.text, trackSearchQuery) ||
                                    KeyboardSearchMatcher.matches(notif.bigText, trackSearchQuery)
                                )
                        }.take(12)
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = if (isLandscape) 240.dp else 210.dp)
                            .vistaInsetBox()
                            .testTag("drawer_telegram_music_list"),
                        contentPadding = PaddingValues(4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (tgSessions.isNotEmpty()) {
                            item(key = "tg_sessions_hdr") {
                                DrawerSectionLabel("АКТИВНЫЙ ТГ ПЛЕЕР")
                            }
                            tgSessions.forEach { sess ->
                                item(key = "tg_sess_${sess.id}") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .vistaAeroButton(isPrimary = true, isActive = sess.id == active?.id)
                                            .clickable { onSelectSession(sess.id) }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Send,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(Modifier.width(7.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = sess.title,
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${sess.appLabel} • ${sess.artist.ifBlank { "Telegram Аудио" }}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.8f),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                if (sess.queue.isNotEmpty()) {
                                    items(sess.queue, key = { "tg_q_${sess.id}_${it.queueId}" }) { qItem ->
                                        QueueTrackRow(
                                            item = qItem,
                                            isActive = qItem.isActive,
                                            onClick = {
                                                onSelectSession(sess.id)
                                                onSkipToQueueItem(qItem.queueId)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (tgAudioFiles.isNotEmpty()) {
                            item(key = "tg_files_hdr") {
                                DrawerSectionLabel("АУДИО ИЗ TELEGRAM (${tgAudioFiles.size})")
                            }
                            items(tgAudioFiles, key = { "tg_audio_${it.id}" }) { track ->
                                val isCurrent = track.id == mediaState.currentLocalTrackId
                                LocalTrackRow(
                                    track = track,
                                    isSelected = isCurrent,
                                    isPlaying = isCurrent && active?.isPlaying == true,
                                    onClick = { onPlayLocalTrack(track) }
                                )
                            }
                        }

                        if (tgRecent.isNotEmpty()) {
                            item(key = "tg_rec_hdr") {
                                DrawerSectionLabel("ИСТОРИЯ ТРЕКОВ ТГ")
                            }
                            items(tgRecent, key = { "tg_r_${it.title}_${it.subtitle}" }) { rItem ->
                                QueueTrackRow(
                                    item = rItem,
                                    isActive = false,
                                    onClick = {
                                        onSearchAndPlayInStream(rItem.title, rItem.packageName)
                                    }
                                )
                            }
                        }

                        if (tgMsgs.isNotEmpty()) {
                            item(key = "tg_notif_hdr") {
                                DrawerSectionLabel("ЧАТЫ И КАНАЛЫ TELEGRAM")
                            }
                            items(tgMsgs, key = { "tg_msg_${it.key}" }) { notif ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .vistaAeroButton(shape = RoundedCornerShape(2.dp))
                                        .clickable { onOpenHubNotification(notif.key) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LibraryMusic,
                                        contentDescription = null,
                                        tint = theme.accent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(7.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = notif.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = notif.bigText.ifBlank { notif.text },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        if (tgSessions.isEmpty() && tgAudioFiles.isEmpty() && tgRecent.isEmpty() && tgMsgs.isEmpty()) {
                            item(key = "tg_empty") {
                                Text(
                                    text = "Запустите аудио в Telegram / exteraGram / AyuGram — очередь и аудиофайлы появятся прямо здесь.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

                MusicDrawerTab.LOCAL -> {
                    if (!hasAudioReadAccess && !hasAllFilesAccess) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .vistaInsetBox()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Разрешите доступ к аудио и файлам для поиска локальных треков",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            VistaButton(
                                text = "Разрешить",
                                isPrimary = true,
                                onClick = onRequestAudioPermission,
                                modifier = Modifier.testTag("drawer_grant_audio_button")
                            )
                        }
                    } else {
                        if (!hasAllFilesAccess) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 5.dp)
                                    .vistaInsetBox()
                                    .clickable(onClick = onRequestAllFilesAccess)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Выдайте «Доступ ко всем файлам» для папок Telegram Audio и Music",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MusicSourceAmber,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Открыть",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = theme.accent
                                )
                            }
                        }

                        if (localTracks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .vistaInsetBox()
                                    .padding(vertical = 14.dp, horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (trackSearchQuery.isBlank()) {
                                        "Локальные аудиофайлы не найдены"
                                    } else {
                                        "По запросу «$trackSearchQuery» файлы не найдены"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 110.dp, max = if (isLandscape) 220.dp else 178.dp)
                                    .vistaInsetBox()
                                    .testTag("drawer_tracks_list"),
                                contentPadding = PaddingValues(4.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                items(localTracks, key = { it.id }) { track ->
                                    val isCurrent = track.id == mediaState.currentLocalTrackId
                                    LocalTrackRow(
                                        track = track,
                                        isSelected = isCurrent,
                                        isPlaying = isCurrent && active?.isPlaying == true,
                                        onClick = { onPlayLocalTrack(track) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Streaming & Player Apps Quick Launcher Strip
            if (musicApps.isNotEmpty()) {
                Spacer(Modifier.height(7.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Сервисы:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    musicApps.forEach { app ->
                        Row(
                            modifier = Modifier
                                .vistaAeroButton()
                                .clickable {
                                    if (trackSearchQuery.isNotBlank()) {
                                        onSearchAndPlayInStream(trackSearchQuery, app.packageName)
                                    } else {
                                        onLaunchMusicApp(app)
                                        onClose()
                                    }
                                }
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            app.icon?.let { bmp ->
                                Image(
                                    bitmap = bmp,
                                    contentDescription = app.label,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    if (isLandscape) {
        // LANDSCAPE: Sideways drawer directly to the right of LandscapeSymbianSidebar, with a right-edge collapse bar!
        Row(
            modifier = modifier
                .widthIn(min = 270.dp, max = 335.dp)
                .fillMaxHeight()
                .vistaWindowFrame(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                .testTag("inline_music_drawer")
        ) {
            drawerBody(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            VerticalDivider(color = theme.chromeBorderOuter, thickness = 1.dp)
            Column(
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight()
                    .symbianMetallicBar()
                    .pointerInput(Unit) {
                        var dragX = 0f
                        var triggered = false
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragX = 0f
                                triggered = false
                            },
                            onHorizontalDrag = { change, amount ->
                                if (triggered) return@detectHorizontalDragGestures
                                dragX += amount
                                if (dragX < -34f) {
                                    change.consume()
                                    triggered = true
                                    onClose()
                                }
                            }
                        )
                    }
                    .clickable(onClick = onClose)
                    .testTag("drawer_collapse_handle"),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowLeft,
                    contentDescription = "Свернуть панель плеера влево",
                    tint = theme.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    } else {
        // PORTRAIT: Drawer expands downwards beneath top player bar, pushing grid/rail down
        Column(
            modifier = modifier
                .fillMaxWidth()
                .vistaWindowFrame(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                .testTag("inline_music_drawer")
        ) {
            drawerBody(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .pointerInput(Unit) {
                        var dragY = 0f
                        var triggered = false
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragY = 0f
                                triggered = false
                            },
                            onVerticalDrag = { change, amount ->
                                if (triggered) return@detectVerticalDragGestures
                                dragY += amount
                                if (dragY < -36f) {
                                    change.consume()
                                    triggered = true
                                    onClose()
                                }
                            }
                        )
                    }
                    .clickable(onClick = onClose)
                    .padding(vertical = 5.dp)
                    .testTag("drawer_collapse_handle"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Свернуть панель плеера",
                    tint = theme.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Свернуть панель плеера",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = theme.chromeInnerGlow
                )
            }
        }
    }
}

@Composable
private fun MusicDrawerTabPill(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .vistaAeroButton(isPrimary = selected, isActive = selected)
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 10.5.sp
            ),
            color = if (selected) Color.White else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    val theme = LocalVistaTheme.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 9.5.sp
        ),
        color = theme.accent,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
    )
}

@Composable
private fun QueueTrackRow(
    item: MediaQueueTrack,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalVistaTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isActive) {
                    Modifier.vistaAeroButton(isPrimary = false, isActive = true, shape = RoundedCornerShape(2.dp))
                } else {
                    Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(theme.surface.copy(alpha = 0.72f))
                        .border(0.5.dp, theme.divider.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isActive) Icons.Rounded.GraphicEq else Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = null,
            tint = if (isActive) MusicSourceAmber else theme.accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isActive) theme.focusBorder else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.subtitle.isNotBlank() || item.appLabel.isNotBlank()) {
                Text(
                    text = listOfNotNull(
                        item.subtitle.takeIf { it.isNotBlank() },
                        item.appLabel.takeIf { it.isNotBlank() }
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = if (isActive) TextPrimary.copy(alpha = 0.85f) else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BrowserTrackRow(
    item: MediaBrowserTrack,
    onClick: () -> Unit
) {
    val theme = LocalVistaTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(theme.surface.copy(alpha = 0.72f))
            .border(0.5.dp, theme.divider.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.isPlayable) Icons.Rounded.PlayArrow else Icons.Rounded.LibraryMusic,
            contentDescription = null,
            tint = theme.accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LocalTrackRow(
    track: LocalAudioTrack,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalVistaTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.vistaAeroButton(isPrimary = false, isActive = true, shape = RoundedCornerShape(2.dp))
                } else {
                    Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(theme.surface.copy(alpha = 0.72f))
                        .border(0.5.dp, theme.divider.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("local_track_row_${track.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = if (isSelected) MusicSourceAmber else theme.accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isSelected) theme.focusBorder else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (track.artist.isNotBlank() || track.album.isNotBlank()) {
                Text(
                    text = buildString {
                        append(track.artist)
                        if (track.artist.isNotBlank() && track.album.isNotBlank()) append(" • ")
                        append(track.album)
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = if (isSelected) TextPrimary.copy(alpha = 0.85f) else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (track.durationMs > 0L) MediaControlRepository.formatTimeMs(track.durationMs) else "",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = if (isSelected) MusicSourceAmber else TextMuted
        )
    }
}
