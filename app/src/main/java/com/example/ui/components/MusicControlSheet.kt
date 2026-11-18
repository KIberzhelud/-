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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
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
import com.example.ui.L10n
import com.example.ui.LocalAppLang
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
 * Compact-header, list-first expandable Windows Vista / Symbian music & streaming drawer.
 * - Volume slider removed completely so the track list owns ~75-80% of the height.
 * - Only 2 tabs: "Стриминг и ТГ" (streaming queue, catalog, cross-service search & Telegram audio) + "Файлы".
 * - Streaming-only buttons (Like, Dislike, My Wave) appear only when an external streaming player is active.
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
    backgroundSpoofEnabled: Boolean = false,
    isCurrentSessionSpoofed: Boolean = false,
    onToggleCurrentSessionSpoof: () -> Unit = {},
    onOpenBackgroundWebAudio: () -> Unit = {},
    onSelectTab: (MusicDrawerTab) -> Unit,
    onTrackSearchChange: (String) -> Unit,
    onSkipToQueueItem: (Long) -> Unit,
    onInvokeCustomAction: (String) -> Unit,
    onLikeStreamTrack: () -> Unit = {},
    onDislikeStreamTrack: () -> Unit = {},
    onStartMyWave: () -> Unit = {},
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
    val lang = LocalAppLang.current
    val active = mediaState.activeSession
    val isActiveStreaming = active != null &&
        active.id != MediaControlRepository.LOCAL_SESSION_ID &&
        MediaControlRepository.isStreamingPackage(active.packageName)

    val drawerBody: @Composable (Modifier) -> Unit = { contentModifier ->
        Column(
            modifier = contentModifier
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // 1. Pinned Compact Top Header: Source + Open + YT Audio + Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .border(1.dp, theme.chromeBorderOuter, RoundedCornerShape(3.dp))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (mediaState.sessions.isEmpty()) {
                        Image(
                            painter = painterResource(R.drawable.ic_symbian_music),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = L10n.musicTab(lang, selectedTab),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = theme.focusBorder,
                            maxLines = 1
                        )
                    } else {
                        mediaState.sessions.forEach { session ->
                            val selected = session.id == active?.id
                            Box(
                                modifier = Modifier
                                    .vistaAeroButton(isPrimary = selected, isActive = selected)
                                    .clickable { onSelectSession(session.id) }
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (session.isPlaying) "▶ ${session.appLabel}" else session.appLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (selected) Color.White else TextPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (active != null && active.id != MediaControlRepository.LOCAL_SESSION_ID) {
                        Box(
                            modifier = Modifier
                                .vistaAeroButton()
                                .clickable {
                                    onOpenActivePlayer()
                                    onClose()
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .testTag("drawer_open_player_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = "Открыть ${active.appLabel}",
                                tint = theme.accent,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    val spoofActive = backgroundSpoofEnabled && (active == null || isCurrentSessionSpoofed)
                    Box(
                        modifier = Modifier
                            .vistaAeroButton(isPrimary = spoofActive, isActive = spoofActive)
                            .clickable(onClick = onOpenBackgroundWebAudio)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .testTag("drawer_yt_web_audio_button")
                    ) {
                        Text(
                            text = "BG YT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (spoofActive) Color.White else MusicSourceAmber
                        )
                    }

                    Box(
                        modifier = Modifier
                            .vistaAeroButton()
                            .clickable(onClick = onClose)
                            .padding(horizontal = 5.dp, vertical = 3.dp)
                            .testTag("drawer_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Закрыть панель плеера",
                            tint = TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            if (!mediaState.isListenerGranted) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = MusicSourceAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (lang == com.example.model.AppLanguage.RU) {
                            "Включите доступ к уведомлениям для очереди"
                        } else {
                            "Enable Notification Access for streaming queue"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    VistaButton(
                        text = if (lang == com.example.model.AppLanguage.RU) "Вкл." else "Grant",
                        isPrimary = true,
                        onClick = onOpenNotificationAccess,
                        modifier = Modifier.testTag("grant_media_listener_button")
                    )
                }
            }

            // 2. Slim Transport + Seekbar + Streaming-Only Actions (Like / Dislike / My Wave)
            if (active != null && active.durationMs > 0L) {
                Spacer(Modifier.height(4.dp))
                var isDragging by remember { mutableStateOf(false) }
                var sliderPos by remember(active.id) { mutableFloatStateOf(0f) }
                val displayPos = if (isDragging) sliderPos.toLong() else livePositionMs

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = MediaControlRepository.formatTimeMs(displayPos),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = TextSecondary,
                        modifier = Modifier.width(34.dp)
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
                        compact = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("drawer_vista_seek_slider")
                    )
                    Text(
                        text = MediaControlRepository.formatTimeMs(active.durationMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = TextMuted,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Compact Transport + Streaming-Only Buttons (No Volume Slider!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Core transport cluster
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 32.dp, height = 28.dp)
                            .vistaAeroButton()
                            .clickable(onClick = onSkipPrevious),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Предыдущий трек",
                            tint = TextPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 28.dp)
                            .vistaAeroButton(isPrimary = true)
                            .clickable(onClick = onTogglePlayPause)
                            .testTag("drawer_play_pause_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (active?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (active?.isPlaying == true) "Пауза" else "Воспроизвести",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 32.dp, height = 28.dp)
                            .vistaAeroButton()
                            .clickable(onClick = onSkipNext),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Следующий трек",
                            tint = TextPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                // Streaming-only buttons: Dislike | Like | My Wave (never shown for offline file player)
                if (isActiveStreaming) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .vistaAeroButton()
                                .clickable(onClick = onLikeStreamTrack)
                                .padding(horizontal = 7.dp)
                                .testTag("drawer_stream_like_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.ThumbUp,
                                    contentDescription = L10n.like(lang),
                                    tint = theme.accent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = L10n.like(lang),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .vistaAeroButton()
                                .clickable(onClick = onDislikeStreamTrack)
                                .padding(horizontal = 7.dp)
                                .testTag("drawer_stream_dislike_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.ThumbDown,
                                    contentDescription = L10n.dislike(lang),
                                    tint = TextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = L10n.dislike(lang),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .vistaAeroButton(isActive = true)
                                .clickable(onClick = onStartMyWave)
                                .padding(horizontal = 7.dp)
                                .testTag("drawer_stream_wave_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = L10n.myWave(lang),
                                    tint = MusicSourceAmber,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = L10n.myWave(lang),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = theme.focusBorder,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                    val spoofActive = backgroundSpoofEnabled
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .vistaAeroButton(isPrimary = spoofActive, isActive = spoofActive)
                            .clickable(onClick = onToggleCurrentSessionSpoof)
                            .padding(horizontal = 8.dp)
                            .testTag("drawer_spoof_toggle_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (spoofActive) "Фон: ВКЛ" else "Фон: ВЫКЛ",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (spoofActive) Color.White else theme.accent
                        )
                    }
                }
            }

            Spacer(Modifier.height(5.dp))

            // 3. Strictly 2 Tabs: "Стриминг и ТГ" | "Файлы"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                MusicDrawerTabPill(
                    title = L10n.musicTab(lang, MusicDrawerTab.STREAMING),
                    selected = selectedTab == MusicDrawerTab.STREAMING,
                    onClick = { onSelectTab(MusicDrawerTab.STREAMING) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("music_tab_streaming")
                )
                MusicDrawerTabPill(
                    title = L10n.musicTab(lang, MusicDrawerTab.LOCAL),
                    selected = selectedTab == MusicDrawerTab.LOCAL,
                    onClick = { onSelectTab(MusicDrawerTab.LOCAL) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("music_tab_local")
                )
            }

            Spacer(Modifier.height(5.dp))

            // 4. Lower Scrollable Zone (~75-80% of Drawer Height): Track Search + Streaming/TG + Files List
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Search Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .vistaInsetBox()
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Поиск трека в стриминге и ТГ",
                            tint = theme.accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (trackSearchQuery.isEmpty()) {
                                Text(
                                    text = if (lang == com.example.model.AppLanguage.RU) {
                                        "Поиск треков по всем стримингам и ТГ…"
                                    } else {
                                        "Search tracks across streaming & TG…"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
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
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        if (trackSearchQuery.isNotEmpty()) {
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
                            .size(28.dp)
                            .vistaAeroButton()
                            .clickable(onClick = onRefreshTracks)
                            .testTag("drawer_refresh_tracks_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Обновить очередь и треки",
                            tint = theme.accent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Instant Search-Across-Streaming Services Bar when typing
                if (trackSearchQuery.isNotBlank() && selectedTab == MusicDrawerTab.STREAMING) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val targetServices = musicApps
                            .filter { MediaControlRepository.isStreamingPackage(it.packageName) }
                            .ifEmpty { musicApps }
                            .take(5)
                        targetServices.forEach { app ->
                            Box(
                                modifier = Modifier
                                    .vistaAeroButton(isPrimary = app.packageName == active?.packageName)
                                    .clickable {
                                        onSearchAndPlayInStream(trackSearchQuery, app.packageName)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "▶ «${trackSearchQuery.trim()}» • ${app.label}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (app.packageName == active?.packageName) Color.White else theme.focusBorder,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(5.dp))

                // Expanded Dominant Scrollable List (fills all remaining vertical space)
                when (selectedTab) {
                    MusicDrawerTab.STREAMING -> {
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
                        val tgSessions = remember(mediaState.sessions) {
                            mediaState.sessions.filter { isTelegramPackage(it.packageName) }
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
                                        KeyboardSearchMatcher.matches(notif.text, trackSearchQuery)
                                    )
                            }.take(8)
                        }

                        if (queueItems.isEmpty() && browserResults.isEmpty() && filteredRecent.isEmpty() &&
                            tgSessions.isEmpty() && tgAudioFiles.isEmpty() && tgMsgs.isEmpty()
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .vistaInsetBox()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (lang == com.example.model.AppLanguage.RU) {
                                        "Запустите трек в стриминге или Telegram — очередь, аудио и каталог появятся здесь."
                                    } else {
                                        "Play audio in any streaming service or Telegram — queue and tracks appear here."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                                Spacer(Modifier.height(8.dp))
                                VistaButton(
                                    text = if (lang == com.example.model.AppLanguage.RU) {
                                        "Подключить каталог (${active?.appLabel ?: "стриминг"})"
                                    } else {
                                        "Connect catalog (${active?.appLabel ?: "streaming"})"
                                    },
                                    isPrimary = true,
                                    onClick = { onConnectBrowser(active?.packageName) }
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .vistaInsetBox()
                                    .testTag("drawer_streaming_queue_list"),
                                contentPadding = PaddingValues(4.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                if (queueItems.isNotEmpty()) {
                                    item(key = "hdr_queue") {
                                        DrawerSectionLabel("ОЧЕРЕДЬ (${active?.appLabel ?: ""})")
                                    }
                                    items(queueItems, key = { "q_${it.queueId}_${it.title}" }) { qItem ->
                                        QueueTrackRow(
                                            item = qItem,
                                            isActive = qItem.isActive,
                                            onClick = { onSkipToQueueItem(qItem.queueId) }
                                        )
                                    }
                                }

                                if (tgSessions.isNotEmpty()) {
                                    item(key = "tg_sessions_hdr") {
                                        DrawerSectionLabel("TELEGRAM ПЛЕЕР")
                                    }
                                    tgSessions.forEach { sess ->
                                        item(key = "tg_sess_${sess.id}") {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .vistaAeroButton(isPrimary = true, isActive = sess.id == active?.id)
                                                    .clickable { onSelectSession(sess.id) }
                                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Send,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
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
                                    }
                                }

                                if (browserResults.isNotEmpty()) {
                                    item(key = "hdr_browser") {
                                        DrawerSectionLabel(
                                            if (mediaState.browserSearchResults.isNotEmpty()) "РЕЗУЛЬТАТЫ В КАТАЛОГЕ"
                                            else "КАТАЛОГ И ПЛЕЙЛИСТЫ (${browserResults.size})"
                                        )
                                    }
                                    items(browserResults, key = { "br_${it.packageName}_${it.mediaId}" }) { bItem ->
                                        BrowserTrackRow(
                                            item = bItem,
                                            onClick = { onPlayBrowserTrack(bItem) }
                                        )
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

                                if (tgMsgs.isNotEmpty()) {
                                    item(key = "tg_notif_hdr") {
                                        DrawerSectionLabel("КАНАЛЫ И ЧАТЫ TELEGRAM")
                                    }
                                    items(tgMsgs, key = { "tg_msg_${it.key}" }) { notif ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .vistaAeroButton(shape = RoundedCornerShape(2.dp))
                                                .clickable { onOpenHubNotification(notif.key) }
                                                .padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.LibraryMusic,
                                                contentDescription = null,
                                                tint = theme.accent,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
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
                                    text = "Разрешите доступ к аудио для поиска файлов",
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
                                        .padding(bottom = 4.dp)
                                        .vistaInsetBox()
                                        .clickable(onClick = onRequestAllFilesAccess)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Выдайте «Доступ ко всем файлам» для папок Music и Telegram Audio",
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
                                        .weight(1f)
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
                                        .weight(1f)
                                        .fillMaxWidth()
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
            }

            // Compact Streaming Service Pills at Bottom
            if (musicApps.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            app.icon?.let { bmp ->
                                Image(
                                    bitmap = bmp,
                                    contentDescription = app.label,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
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
        Row(
            modifier = modifier
                .widthIn(min = 285.dp, max = 345.dp)
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
                    .width(22.dp)
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
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .vistaWindowFrame(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                .testTag("inline_music_drawer")
        ) {
            drawerBody(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 340.dp, max = 470.dp)
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
                    .padding(vertical = 4.dp)
                    .testTag("drawer_collapse_handle"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Свернуть панель плеера",
                    tint = theme.accent,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (lang == com.example.model.AppLanguage.RU) "Свернуть" else "Collapse",
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
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
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
