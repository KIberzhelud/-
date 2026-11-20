package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.KeyboardSearchMatcher
import com.example.data.MediaControlRepository
import com.example.data.MusicCatalogAndLyricsRepository
import com.example.model.AlbumExpectedTrack
import com.example.model.AudioCaptureState
import com.example.model.EqualizerPreset
import com.example.model.EqualizerState
import com.example.model.LocalAudioTrack
import com.example.model.MediaPlayerState
import com.example.model.MusicSourceKind
import com.example.model.PlayerCoverStyle
import com.example.model.PowerampTab
import com.example.model.RepeatMode as TrackRepeatMode
import com.example.model.SmartAlbum
import com.example.model.SourceSelectionMode
import com.example.model.TrackLyricsData
import com.example.model.TrackSourceCandidate
import com.example.model.UnifiedMusicSearchResult
import com.example.ui.theme.CallIncomingGreen
import com.example.ui.theme.CallMissedRed
import com.example.ui.theme.LocalVistaTheme
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.symbianMetallicBar
import com.example.ui.theme.vistaAeroButton
import com.example.ui.theme.vistaInsetBox
import com.example.ui.theme.vistaWindowFrame
import kotlin.math.abs
import kotlin.math.sin

private enum class SearchSourceFilter(val title: String) {
    ALL("Все"),
    FLAC("FLAC"),
    STREAM("Стриминг"),
    LOCAL("Локально"),
    MP3("MP3/M4A")
}

@Composable
fun PowerampPlayerScreen(
    mediaState: MediaPlayerState,
    livePositionMs: Long,
    coverStyle: PlayerCoverStyle,
    activeTab: PowerampTab,
    isLandscape: Boolean,
    sourceSelectionMode: SourceSelectionMode,
    localTracks: List<LocalAudioTrack>,
    unifiedSearchQuery: String,
    unifiedSearchResults: List<UnifiedMusicSearchResult>,
    isSearchingOnline: Boolean,
    currentLyrics: TrackLyricsData?,
    isFetchingLyrics: Boolean,
    equalizerState: EqualizerState,
    audioCaptureState: AudioCaptureState,
    smartAlbums: List<SmartAlbum>,
    selectedAlbumId: String?,
    onClose: () -> Unit,
    onSelectTab: (PowerampTab) -> Unit,
    onToggleSourceMode: () -> Unit,
    onSelectSession: (String) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSkipToQueueItem: (Long) -> Unit,
    onOpenActivePlayer: () -> Unit,
    onPlayLocalTrack: (LocalAudioTrack) -> Unit,
    onDeleteLocalTrack: (LocalAudioTrack) -> Unit,
    onDeleteActiveTrack: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSearchMusicEverywhere: (String) -> Unit,
    onUnifiedTrackAction: (UnifiedMusicSearchResult, downloadAction: Boolean) -> Unit,
    onOpenSourcePicker: (UnifiedMusicSearchResult, forDownload: Boolean) -> Unit,
    onStopAudioCapture: () -> Unit,
    onAutoFetchLyrics: () -> Unit,
    onSaveLyrics: (String) -> Unit,
    onDeleteLyrics: () -> Unit,
    onSetEqualizerEnabled: (Boolean) -> Unit,
    onApplyEqualizerPreset: (EqualizerPreset) -> Unit,
    onSetEqualizerBand: (Int, Int) -> Unit,
    onSetBassBoost: (Int) -> Unit,
    onSetVirtualizer: (Int) -> Unit,
    onSelectAlbum: (String?) -> Unit,
    onCreateAlbum: (String, String) -> Unit,
    onRefreshAlbumOnline: (String) -> Unit,
    onAttachLocalTracksToAlbum: (String, List<String>) -> Unit,
    onDetachLocalTrackFromAlbum: (String, String) -> Unit,
    onPickCustomAlbumCover: (String) -> Unit,
    onSelectAlternateCoverUrl: (String, String) -> Unit,
    onDeleteAlbum: (String) -> Unit,
    onDownloadMissingAlbumTrack: (SmartAlbum, AlbumExpectedTrack) -> Unit,
    onDownloadAllMissingAlbumTracks: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    val active = mediaState.activeSession

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF090E16),
                        theme.background,
                        Color(0xFF070B12)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .testTag("poweramp_full_player_screen")
    ) {
        // 1. Poweramp Top Bar: Back, Technical Audio Spec Pill, Source Mode Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .symbianMetallicBar()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .vistaAeroButton()
                    .clickable(onClick = onClose)
                    .padding(horizontal = 9.dp, vertical = 6.dp)
                    .testTag("poweramp_close_button")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Назад",
                        tint = TextPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Назад", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "KEYDECK POWERAMP DSP",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                    color = theme.focusBorder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val techLine = buildString {
                    val fmt = active?.formatLabel?.ifBlank { null } ?: "AUDIO"
                    append(fmt)
                    if ((active?.sampleRateHz ?: 0) > 0) {
                        append(" • ${"%.1f".format((active!!.sampleRateHz / 1000f))} kHz")
                    }
                    if ((active?.bitrateKbps ?: 0) > 0) {
                        append(" • ${active!!.bitrateKbps} kbps")
                    }
                    if (equalizerState.enabled) {
                        append(if (equalizerState.dspHardwareAttached) " • DSP EQ" else " • EQ")
                    }
                }
                Text(
                    text = techLine,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = MusicSourceAmber,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .vistaAeroButton(isPrimary = sourceSelectionMode == SourceSelectionMode.PICK, isActive = sourceSelectionMode == SourceSelectionMode.PICK)
                    .clickable(onClick = onToggleSourceMode)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("poweramp_source_mode_toggle")
            ) {
                Text(
                    text = if (sourceSelectionMode == SourceSelectionMode.AUTO) "Ист: Авто" else "Ист: Выбор",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                    color = Color.White,
                    maxLines = 1
                )
            }
        }

        // Active Stream Audio Capture Banner
        if (audioCaptureState.isCapturing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF3E1218))
                    .border(1.dp, CallMissedRed)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(CallMissedRed)
                    )
                    Spacer(Modifier.width(7.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Захват потока: ${audioCaptureState.targetTitle}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val kb = audioCaptureState.bytesWritten / 1024L
                        Text(
                            text = "${audioCaptureState.elapsedSec} сек • $kb КБ WAV",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = MusicSourceAmber
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .vistaAeroButton(isPrimary = true)
                        .clickable(onClick = onStopAudioCapture)
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                        .testTag("poweramp_stop_capture_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Stop, contentDescription = "Стоп", tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Стоп WAV", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                }
            }
        }

        // 2. Poweramp 5-Tab Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surface.copy(alpha = 0.94f))
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PowerampTab.entries.forEach { tab ->
                val selected = activeTab == tab
                val icon = when (tab) {
                    PowerampTab.PLAYER -> Icons.Rounded.GraphicEq
                    PowerampTab.EQUALIZER -> Icons.Rounded.Equalizer
                    PowerampTab.LYRICS -> Icons.Rounded.Lyrics
                    PowerampTab.SEARCH -> Icons.Rounded.Search
                    PowerampTab.ALBUMS -> Icons.Rounded.Album
                }
                val tag = "poweramp_tab_${tab.name.lowercase()}"
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .vistaAeroButton(isPrimary = selected, isActive = selected)
                        .clickable { onSelectTab(tab) }
                        .padding(vertical = 7.dp, horizontal = 4.dp)
                        .testTag(tag),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.title,
                        tint = if (selected) Color.White else theme.accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) Color.White else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        HorizontalDivider(color = theme.chromeBorderOuter, thickness = 1.dp)

        // 3. Tab Body
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (activeTab) {
                PowerampTab.PLAYER -> PowerampPlayerMainTab(
                    mediaState = mediaState,
                    livePositionMs = livePositionMs,
                    coverStyle = coverStyle,
                    equalizerState = equalizerState,
                    localTracks = localTracks,
                    currentLyrics = currentLyrics,
                    isFetchingLyrics = isFetchingLyrics,
                    isLandscape = isLandscape,
                    onSelectSession = onSelectSession,
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipPrevious = onSkipPrevious,
                    onSkipNext = onSkipNext,
                    onSeekTo = onSeekTo,
                    onSeekBy = onSeekBy,
                    onSkipToQueueItem = onSkipToQueueItem,
                    onOpenActivePlayer = onOpenActivePlayer,
                    onPlayLocalTrack = onPlayLocalTrack,
                    onDeleteLocalTrack = onDeleteLocalTrack,
                    onDeleteActiveTrack = onDeleteActiveTrack,
                    onSetPlaybackSpeed = onSetPlaybackSpeed,
                    onCycleRepeatMode = onCycleRepeatMode,
                    onToggleShuffle = onToggleShuffle,
                    onAutoFetchLyrics = onAutoFetchLyrics,
                    onDeleteLyrics = onDeleteLyrics,
                    onOpenFullLyricsEditor = { onSelectTab(PowerampTab.LYRICS) },
                    onFindTrackInAllSources = { q ->
                        onSearchMusicEverywhere(q)
                        onSelectTab(PowerampTab.SEARCH)
                    }
                )

                PowerampTab.EQUALIZER -> PowerampEqualizerTab(
                    equalizerState = equalizerState,
                    onSetEnabled = onSetEqualizerEnabled,
                    onApplyPreset = onApplyEqualizerPreset,
                    onSetBand = onSetEqualizerBand,
                    onSetBassBoost = onSetBassBoost,
                    onSetVirtualizer = onSetVirtualizer
                )

                PowerampTab.LYRICS -> PowerampLyricsTab(
                    trackTitle = active?.title.orEmpty(),
                    trackArtist = active?.artist.orEmpty(),
                    livePositionMs = livePositionMs,
                    lyrics = currentLyrics,
                    isFetching = isFetchingLyrics,
                    onSeekTo = onSeekTo,
                    onAutoFetch = onAutoFetchLyrics,
                    onSaveLyrics = onSaveLyrics,
                    onDeleteLyrics = onDeleteLyrics
                )

                PowerampTab.SEARCH -> PowerampMultiSourceSearchTab(
                    query = unifiedSearchQuery,
                    results = unifiedSearchResults,
                    isSearching = isSearchingOnline,
                    sourceSelectionMode = sourceSelectionMode,
                    onQueryChange = onSearchMusicEverywhere,
                    onToggleSourceMode = onToggleSourceMode,
                    onPlayOrPick = { res -> onUnifiedTrackAction(res, false) },
                    onDownloadOrPick = { res -> onUnifiedTrackAction(res, true) },
                    onOpenSourcesModal = { res -> onOpenSourcePicker(res, false) }
                )

                PowerampTab.ALBUMS -> PowerampAlbumsTab(
                    albums = smartAlbums,
                    selectedAlbumId = selectedAlbumId,
                    localTracks = localTracks,
                    onSelectAlbum = onSelectAlbum,
                    onCreateAlbum = onCreateAlbum,
                    onRefreshAlbumOnline = onRefreshAlbumOnline,
                    onAttachLocalTracks = onAttachLocalTracksToAlbum,
                    onDetachLocalTrack = onDetachLocalTrackFromAlbum,
                    onPickCustomAlbumCover = onPickCustomAlbumCover,
                    onSelectAlternateCoverUrl = onSelectAlternateCoverUrl,
                    onDeleteAlbum = onDeleteAlbum,
                    onPlayLocalTrack = onPlayLocalTrack,
                    onOpenLyricsForTrack = { lt ->
                        onPlayLocalTrack(lt)
                        onSelectTab(PowerampTab.LYRICS)
                    },
                    onDownloadMissingTrack = onDownloadMissingAlbumTrack,
                    onDownloadAllMissingTracks = onDownloadAllMissingAlbumTracks
                )
            }
        }
    }
}

@Composable
private fun PowerampPlayerMainTab(
    mediaState: MediaPlayerState,
    livePositionMs: Long,
    coverStyle: PlayerCoverStyle,
    equalizerState: EqualizerState,
    localTracks: List<LocalAudioTrack>,
    currentLyrics: TrackLyricsData?,
    isFetchingLyrics: Boolean,
    isLandscape: Boolean,
    onSelectSession: (String) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSkipToQueueItem: (Long) -> Unit,
    onOpenActivePlayer: () -> Unit,
    onPlayLocalTrack: (LocalAudioTrack) -> Unit,
    onDeleteLocalTrack: (LocalAudioTrack) -> Unit,
    onDeleteActiveTrack: () -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onAutoFetchLyrics: () -> Unit,
    onDeleteLyrics: () -> Unit,
    onOpenFullLyricsEditor: () -> Unit,
    onFindTrackInAllSources: (String) -> Unit
) {
    val theme = LocalVistaTheme.current
    val active = mediaState.activeSession
    var quickListTfv by remember { mutableStateOf(TextFieldValue("")) }
    val quickListQuery = quickListTfv.text
    var pendingDeleteLocal by remember { mutableStateOf<LocalAudioTrack?>(null) }
    var confirmDeleteActive by remember { mutableStateOf(false) }

    val filteredLocal = remember(localTracks, quickListQuery) {
        if (quickListQuery.isBlank()) localTracks.take(40)
        else localTracks.filter {
            KeyboardSearchMatcher.matches(it.title, quickListQuery) ||
                KeyboardSearchMatcher.matches(it.artist, quickListQuery) ||
                KeyboardSearchMatcher.matches(it.album, quickListQuery)
        }
    }

    val playerDeck: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Session switcher pills if multiple sessions
            if (mediaState.sessions.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    mediaState.sessions.forEach { sess ->
                        val selected = sess.id == active?.id
                        Box(
                            modifier = Modifier
                                .vistaAeroButton(isPrimary = selected, isActive = selected)
                                .clickable { onSelectSession(sess.id) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = sess.appLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                                color = if (selected) Color.White else TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Cover Art + Track Info + Live Visualizer Bars
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .vistaWindowFrame(RoundedCornerShape(6.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AlbumArtThumb(
                        artwork = active?.artwork,
                        size = if (isLandscape) 104.dp else 112.dp,
                        coverStyle = coverStyle,
                        isPlaying = active?.isPlaying == true
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = active?.title ?: "Трек не выбран",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.5.sp),
                            color = TextPrimary,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        )
                        Text(
                            text = active?.artist?.ifBlank { active.appLabel } ?: "Запустите плеер или выберите трек ниже",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.accent,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        )
                        if (!active?.album.isNullOrBlank()) {
                            Text(
                                text = active?.album.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val badge = active?.formatLabel?.ifBlank { "AUDIO" } ?: "IDLE"
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.5.sp),
                                color = MusicSourceAmber,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                                    .border(1.dp, MusicSourceAmber.copy(alpha = 0.65f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                            if (active != null && active.title.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .vistaAeroButton()
                                        .clickable {
                                            onFindTrackInAllSources("${active.artist} ${active.title}".trim())
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Источники / Скачать", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp), color = Color.White)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                PowerampSpectrumVisualizer(isPlaying = active?.isPlaying == true)

                // Precision Vista Seekbar
                if (active != null && active.durationMs > 0L) {
                    var scrubbing by remember(active.id) { mutableStateOf(false) }
                    var scrubPos by remember(active.id) { mutableFloatStateOf(0f) }
                    val displayPos = if (scrubbing) scrubPos.toLong() else livePositionMs

                    Spacer(Modifier.height(6.dp))
                    VistaSlider(
                        value = displayPos.toFloat().coerceIn(0f, active.durationMs.toFloat()),
                        onValueChange = {
                            scrubbing = true
                            scrubPos = it
                        },
                        onValueChangeFinished = {
                            scrubbing = false
                            onSeekTo(scrubPos.toLong())
                        },
                        valueRange = 0f..active.durationMs.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(MediaControlRepository.formatTimeMs(displayPos), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(MediaControlRepository.formatTimeMs(active.durationMs), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Main Transport Deck
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DeckIconBtn(
                        active = equalizerState.shuffleEnabled,
                        onClick = onToggleShuffle,
                        tag = "poweramp_shuffle_btn"
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = "Перемешать", tint = if (equalizerState.shuffleEnabled) Color.White else TextMuted, modifier = Modifier.size(16.dp))
                    }
                    DeckIconBtn(onClick = onSkipPrevious, tag = "poweramp_prev_btn") {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Назад", tint = TextPrimary, modifier = Modifier.size(19.dp))
                    }
                    DeckIconBtn(onClick = { onSeekBy(-10_000L) }) {
                        Icon(Icons.Rounded.Replay10, contentDescription = "-10с", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 58.dp, height = 42.dp)
                            .vistaAeroButton(isPrimary = true)
                            .clickable(onClick = onTogglePlayPause)
                            .testTag("poweramp_play_pause_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (active?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Играть / Пауза",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    DeckIconBtn(onClick = { onSeekBy(10_000L) }) {
                        Icon(Icons.Rounded.Forward10, contentDescription = "+10с", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    DeckIconBtn(onClick = onSkipNext, tag = "poweramp_next_btn") {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Вперёд", tint = TextPrimary, modifier = Modifier.size(19.dp))
                    }
                    DeckIconBtn(
                        active = equalizerState.repeatMode != TrackRepeatMode.OFF,
                        onClick = onCycleRepeatMode,
                        tag = "poweramp_repeat_btn"
                    ) {
                        Icon(
                            imageVector = if (equalizerState.repeatMode == TrackRepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = "Повтор",
                            tint = if (equalizerState.repeatMode != TrackRepeatMode.OFF) Color.White else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Speed & Utility Row (Delete Track, Open Source App)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(0.85f, 1.0f, 1.25f, 1.5f).forEach { spd ->
                        val isCur = abs(equalizerState.playbackSpeed - spd) < 0.03f
                        Box(
                            modifier = Modifier
                                .vistaAeroButton(isPrimary = isCur, isActive = isCur)
                                .clickable { onSetPlaybackSpeed(spd) }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Text("${spd}x", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = if (isCur) Color.White else TextSecondary)
                        }
                    }
                    if (mediaState.currentLocalTrackId != null) {
                        Box(
                            modifier = Modifier
                                .vistaAeroButton()
                                .clickable { confirmDeleteActive = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("poweramp_delete_track_btn")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Удалить трек", tint = CallMissedRed, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Удалить трек", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = CallMissedRed)
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .vistaAeroButton()
                            .clickable(onClick = onOpenActivePlayer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = theme.accent, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Источник", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextPrimary)
                        }
                    }
                }
            }

            // Live Synced Lyrics Quick Card (directly in Player tab)
            InlineSyncedLyricsPreviewCard(
                livePositionMs = livePositionMs,
                lyrics = currentLyrics,
                isFetching = isFetchingLyrics,
                onSeekTo = onSeekTo,
                onAutoFetch = onAutoFetchLyrics,
                onOpenEditor = onOpenFullLyricsEditor,
                onDeleteLyrics = onDeleteLyrics
            )
        }
    }

    val queueAndLocalDeck: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .vistaWindowFrame(RoundedCornerShape(5.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ПЛЕЙЛИСТ И ЛОКАЛЬНЫЕ (${localTracks.size})",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = theme.focusBorder
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .vistaInsetBox(isFocused = quickListQuery.isNotEmpty())
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                if (quickListQuery.isEmpty()) {
                    Text("Поиск по локальным трекам и очереди…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                BasicTextField(
                    value = quickListTfv,
                    onValueChange = { quickListTfv = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                    cursorBrush = SolidColor(theme.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val queueItems = active?.queue.orEmpty().filter {
                quickListQuery.isBlank() || KeyboardSearchMatcher.matches(it.title, quickListQuery) || KeyboardSearchMatcher.matches(it.subtitle, quickListQuery)
            }
            queueItems.take(15).forEach { qi ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (qi.isActive) theme.accent.copy(alpha = 0.22f) else theme.insetBg, RoundedCornerShape(3.dp))
                        .clickable { onSkipToQueueItem(qi.queueId) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = if (qi.isActive) theme.focusBorder else TextMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(qi.title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (qi.subtitle.isNotBlank()) {
                            Text(qi.subtitle, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextSecondary, maxLines = 1)
                        }
                    }
                    Text("Очередь", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MusicSourceAmber)
                }
            }

            filteredLocal.forEach { track ->
                val isPlayingThis = track.id == mediaState.currentLocalTrackId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isPlayingThis) theme.accent.copy(alpha = 0.24f) else theme.insetBg, RoundedCornerShape(3.dp))
                        .border(1.dp, if (isPlayingThis) theme.accent else Color.Transparent, RoundedCornerShape(3.dp))
                        .clickable { onPlayLocalTrack(track) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Играть", tint = if (isPlayingThis) CallIncomingGreen else theme.accent, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${track.artist.ifBlank { "Локально" }} • ${track.formatLabel}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .vistaAeroButton()
                            .clickable { pendingDeleteLocal = track }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("poweramp_list_delete_${track.id}")
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Удалить", tint = CallMissedRed, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                playerDeck()
            }
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                queueAndLocalDeck()
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "player_deck") { playerDeck() }
            item(key = "queue_local_deck") { queueAndLocalDeck() }
        }
    }

    pendingDeleteLocal?.let { target ->
        ConfirmDeleteTrackDialog(
            title = target.title,
            onDismiss = { pendingDeleteLocal = null },
            onConfirm = {
                onDeleteLocalTrack(target)
                pendingDeleteLocal = null
            }
        )
    }

    if (confirmDeleteActive) {
        ConfirmDeleteTrackDialog(
            title = active?.title ?: "Трек",
            onDismiss = { confirmDeleteActive = false },
            onConfirm = {
                onDeleteActiveTrack()
                confirmDeleteActive = false
            }
        )
    }
}

@Composable
private fun DeckIconBtn(
    onClick: () -> Unit,
    active: Boolean = false,
    tag: String? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 36.dp)
            .vistaAeroButton(isPrimary = active, isActive = active)
            .clickable(onClick = onClick)
            .let { if (tag != null) it.testTag(tag) else it },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun PowerampSpectrumVisualizer(isPlaying: Boolean) {
    val theme = LocalVistaTheme.current
    val transition = rememberInfiniteTransition(label = "spectrum")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spectrum_phase"
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = 0.35f))
    ) {
        val barCount = 24
        val gap = 3.dp.toPx()
        val barW = ((size.width - gap * (barCount + 1)) / barCount).coerceAtLeast(2f)
        for (i in 0 until barCount) {
            val amp = if (isPlaying) {
                0.28f + 0.68f * ((sin(phase + i * 0.55f) + 1f) * 0.5f)
            } else {
                0.15f
            }
            val barH = size.height * amp
            val x = gap + i * (barW + gap)
            drawRoundRect(
                color = if (amp > 0.72f) MusicSourceAmber else theme.accent,
                topLeft = Offset(x, size.height - barH),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }
}

@Composable
private fun InlineSyncedLyricsPreviewCard(
    livePositionMs: Long,
    lyrics: TrackLyricsData?,
    isFetching: Boolean,
    onSeekTo: (Long) -> Unit,
    onAutoFetch: () -> Unit,
    onOpenEditor: () -> Unit,
    onDeleteLyrics: () -> Unit
) {
    val theme = LocalVistaTheme.current
    val activeIdx by remember(lyrics, livePositionMs) {
        derivedStateOf {
            val lines = lyrics?.syncedLines.orEmpty()
            if (lines.isEmpty()) -1
            else lines.indexOfLast { it.timeMs <= livePositionMs }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .vistaWindowFrame(RoundedCornerShape(5.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Rounded.Lyrics, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
                Text(
                    text = if (lyrics?.hasSynced == true) "СИНХРОННЫЙ ТЕКСТ (${lyrics.sourceLabel})" else "ТЕКСТ ПЕСНИ",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = theme.focusBorder
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .vistaAeroButton()
                        .clickable(onClick = onAutoFetch)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text("Автоподбор", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp), color = MusicSourceAmber)
                }
                Box(
                    modifier = Modifier
                        .vistaAeroButton()
                        .clickable(onClick = onOpenEditor)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text("Редактор", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp), color = Color.White)
                }
                if (lyrics?.hasAnyLyrics == true) {
                    Box(
                        modifier = Modifier
                            .vistaAeroButton()
                            .clickable(onClick = onDeleteLyrics)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                            .testTag("poweramp_player_delete_lyrics_btn")
                    ) {
                        Text("✕", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold), color = CallMissedRed)
                    }
                }
            }
        }

        when {
            isFetching -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(color = theme.accent, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                Text("Поиск синхронизированного текста (LRCLIB)…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            lyrics == null || !lyrics.hasAnyLyrics -> {
                Text(
                    text = "Текст не загружен. Нажмите «Автоподбор» или «Редактор» для ручной разметки .lrc.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            lyrics.syncedLines.isNotEmpty() -> {
                val startIdx = (activeIdx - 1).coerceAtLeast(0)
                val endIdx = (startIdx + 4).coerceAtMost(lyrics.syncedLines.lastIndex)
                for (i in startIdx..endIdx) {
                    val line = lyrics.syncedLines[i]
                    val isActive = i == activeIdx
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            fontSize = if (isActive) 13.5.sp else 12.sp
                        ),
                        color = if (isActive) MusicSourceAmber else TextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isActive) theme.accent.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { onSeekTo(line.timeMs) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
            else -> {
                Text(
                    text = lyrics.plainText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PowerampEqualizerTab(
    equalizerState: EqualizerState,
    onSetEnabled: (Boolean) -> Unit,
    onApplyPreset: (EqualizerPreset) -> Unit,
    onSetBand: (Int, Int) -> Unit,
    onSetBassBoost: (Int) -> Unit,
    onSetVirtualizer: (Int) -> Unit
) {
    val theme = LocalVistaTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp)
            .testTag("poweramp_equalizer_tab"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Master Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .vistaWindowFrame(RoundedCornerShape(4.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "POWERAMP 5-ПОЛОСНЫЙ ЭКВАЛАЙЗЕР И DSP",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = theme.focusBorder
                )
                Text(
                    text = if (equalizerState.dspHardwareAttached) "Аппаратный DSP подключён к аудиопотоку" else "Профиль готов (включится с локальным плеером)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (equalizerState.dspHardwareAttached) CallIncomingGreen else TextSecondary
                )
            }
            Box(
                modifier = Modifier
                    .vistaAeroButton(isPrimary = equalizerState.enabled, isActive = equalizerState.enabled)
                    .clickable { onSetEnabled(!equalizerState.enabled) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("eq_master_toggle")
            ) {
                Text(
                    text = if (equalizerState.enabled) "ВКЛ" else "ВЫКЛ",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }

        // Presets
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .vistaInsetBox()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("ПРЕСЕТЫ ЗВУЧАНИЯ", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MusicSourceAmber)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EqualizerPreset.entries.forEach { preset ->
                    val selected = equalizerState.preset == preset
                    Box(
                        modifier = Modifier
                            .vistaAeroButton(isPrimary = selected, isActive = selected)
                            .clickable { onApplyPreset(preset) }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("eq_preset_${preset.name.lowercase()}")
                    ) {
                        Text(
                            text = preset.title,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                            color = if (selected) Color.White else TextPrimary
                        )
                    }
                }
            }
        }

        // Band Sliders
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .vistaInsetBox()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("ЧАСТОТНЫЕ ПОЛОСЫ (−15 dB … +15 dB)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = theme.accent)
            equalizerState.bands.forEach { band ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = band.freqLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        modifier = Modifier.width(52.dp)
                    )
                    VistaSlider(
                        value = band.levelMb.toFloat(),
                        onValueChange = { onSetBand(band.index, it.toInt()) },
                        valueRange = -1500f..1500f,
                        compact = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("eq_band_slider_${band.index}")
                    )
                    Text(
                        text = "%+.1f dB".format(band.levelMb / 100f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MusicSourceAmber,
                        modifier = Modifier.width(58.dp)
                    )
                }
            }
        }

        // Bass Boost & Virtualizer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .vistaInsetBox()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("УСИЛЕНИЕ БАСА И СЦЕНА", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = theme.accent)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Bass Boost", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary, modifier = Modifier.width(74.dp))
                VistaSlider(
                    value = equalizerState.bassBoostStrength.toFloat(),
                    onValueChange = { onSetBassBoost(it.toInt()) },
                    valueRange = 0f..1000f,
                    compact = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("eq_bass_slider")
                )
                Text("${equalizerState.bassBoostStrength / 10}%", style = MaterialTheme.typography.labelSmall, color = theme.accent, modifier = Modifier.width(38.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("3D Сцена", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary, modifier = Modifier.width(74.dp))
                VistaSlider(
                    value = equalizerState.virtualizerStrength.toFloat(),
                    onValueChange = { onSetVirtualizer(it.toInt()) },
                    valueRange = 0f..1000f,
                    compact = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("eq_virtualizer_slider")
                )
                Text("${equalizerState.virtualizerStrength / 10}%", style = MaterialTheme.typography.labelSmall, color = theme.accent, modifier = Modifier.width(38.dp))
            }
        }
    }
}

@Composable
private fun PowerampLyricsTab(
    trackTitle: String,
    trackArtist: String,
    livePositionMs: Long,
    lyrics: TrackLyricsData?,
    isFetching: Boolean,
    onSeekTo: (Long) -> Unit,
    onAutoFetch: () -> Unit,
    onSaveLyrics: (String) -> Unit,
    onDeleteLyrics: () -> Unit
) {
    val theme = LocalVistaTheme.current
    var isEditing by remember { mutableStateOf(false) }
    var editorText by remember(lyrics?.trackKey, lyrics?.rawLrc) {
        mutableStateOf(lyrics?.rawLrc ?: lyrics?.plainText ?: "")
    }
    var nextStampLineIdx by remember(trackTitle, isEditing) { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    val activeIdx by remember(lyrics, livePositionMs) {
        derivedStateOf {
            val lines = lyrics?.syncedLines.orEmpty()
            if (lines.isEmpty()) -1 else lines.indexOfLast { it.timeMs <= livePositionMs }
        }
    }

    LaunchedEffect(activeIdx, isEditing) {
        if (!isEditing && activeIdx >= 0) {
            runCatching { listState.animateScrollToItem((activeIdx - 2).coerceAtLeast(0)) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
            .testTag("poweramp_lyrics_tab"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Lyrics Header + Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .vistaWindowFrame(RoundedCornerShape(4.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trackTitle.ifBlank { "Нет активного трека" },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val info = buildString {
                    if (trackArtist.isNotBlank()) append("$trackArtist • ")
                    if (lyrics?.hasAnyLyrics == true) append(lyrics.sourceLabel) else append("Текст отсутствует")
                }
                Text(info, style = MaterialTheme.typography.labelSmall, color = theme.accent, maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .vistaAeroButton()
                        .clickable(onClick = onAutoFetch)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("lyrics_auto_fetch_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MusicSourceAmber, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Авто", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                    }
                }
                Box(
                    modifier = Modifier
                        .vistaAeroButton(isPrimary = isEditing, isActive = isEditing)
                        .clickable { isEditing = !isEditing }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("lyrics_edit_mode_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(if (isEditing) "Просмотр" else "Редактир.", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                }
                if (lyrics?.hasAnyLyrics == true) {
                    Box(
                        modifier = Modifier
                            .vistaAeroButton()
                            .clickable {
                                editorText = ""
                                onDeleteLyrics()
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("lyrics_delete_btn")
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Удалить текст", tint = CallMissedRed, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }

        if (isEditing) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .vistaInsetBox()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .vistaAeroButton(isPrimary = true)
                            .clickable {
                                val stamp = MusicCatalogAndLyricsRepository.formatLrcTimestamp(livePositionMs)
                                val lines = editorText.lines().toMutableList()
                                if (lines.isEmpty() || (lines.size == 1 && lines[0].isBlank())) {
                                    editorText = "$stamp "
                                    nextStampLineIdx = 0
                                } else {
                                    val idx = nextStampLineIdx.coerceIn(0, lines.lastIndex)
                                    val clean = lines[idx].replace(Regex("""^\s*(\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]\s*)+"""), "")
                                    lines[idx] = "$stamp $clean"
                                    editorText = lines.joinToString("\n")
                                    nextStampLineIdx = (idx + 1).coerceAtMost(lines.lastIndex)
                                }
                            }
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                            .testTag("lyrics_stamp_time_btn")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "+ Таймкод (${MediaControlRepository.formatTimeMs(livePositionMs)})",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .vistaAeroButton()
                            .clickable {
                                nextStampLineIdx = 0
                                editorText = editorText.lines().joinToString("\n") {
                                    it.replace(Regex("""\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]\s*"""), "")
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("Сброс меток", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }

                    Spacer(Modifier.weight(1f))

                    Box(
                        modifier = Modifier
                            .vistaAeroButton(isPrimary = true)
                            .clickable {
                                onSaveLyrics(editorText)
                                isEditing = false
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("lyrics_save_btn")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Save, contentDescription = "Сохранить", tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Сохранить", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                        }
                    }
                }

                Text(
                    text = "Совет: вставьте текст песни построчно, включите трек и нажимайте «+ Таймкод», чтобы привязать каждую строчку (строка №${nextStampLineIdx + 1}).",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MusicSourceAmber
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                        .border(1.dp, theme.chromeBorderOuter, RoundedCornerShape(4.dp))
                        .padding(10.dp)
                ) {
                    if (editorText.isEmpty()) {
                        Text(
                            text = "[00:12.50] Введите или вставьте текст песни в формате .lrc…",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    BasicTextField(
                        value = editorText,
                        onValueChange = { editorText = it },
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, lineHeight = 20.sp),
                        cursorBrush = SolidColor(theme.accent),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .testTag("lyrics_editor_field")
                    )
                }
            }
        } else {
            when {
                isFetching -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = theme.accent)
                        Text("Поиск синхронизированного текста…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
                lyrics == null || !lyrics.hasAnyLyrics -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .vistaInsetBox(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Текст для этого трека не найден или был удалён", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VistaButton(text = "Автопоиск (LRCLIB)", isPrimary = true, onClick = onAutoFetch)
                            VistaButton(text = "Написать вручную", onClick = { isEditing = true })
                        }
                    }
                }
                lyrics.syncedLines.isNotEmpty() -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .vistaInsetBox(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(lyrics.syncedLines) { idx, line ->
                            val isActive = idx == activeIdx
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isActive) theme.accent.copy(alpha = 0.28f) else Color.Transparent)
                                    .clickable { onSeekTo(line.timeMs) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = MediaControlRepository.formatTimeMs(line.timeMs),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = if (isActive) MusicSourceAmber else TextMuted,
                                    modifier = Modifier.width(42.dp)
                                )
                                Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = if (isActive) 15.sp else 13.5.sp
                                    ),
                                    color = if (isActive) Color.White else TextSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .vistaInsetBox()
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp)
                    ) {
                        Text(
                            text = lyrics.plainText,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerampMultiSourceSearchTab(
    query: String,
    results: List<UnifiedMusicSearchResult>,
    isSearching: Boolean,
    sourceSelectionMode: SourceSelectionMode,
    onQueryChange: (String) -> Unit,
    onToggleSourceMode: () -> Unit,
    onPlayOrPick: (UnifiedMusicSearchResult) -> Unit,
    onDownloadOrPick: (UnifiedMusicSearchResult) -> Unit,
    onOpenSourcesModal: (UnifiedMusicSearchResult) -> Unit
) {
    val theme = LocalVistaTheme.current
    var selectedFilter by remember { mutableStateOf(SearchSourceFilter.ALL) }

    val filteredResults = remember(results, selectedFilter) {
        when (selectedFilter) {
            SearchSourceFilter.ALL -> results
            SearchSourceFilter.FLAC -> results.filter { r ->
                r.candidates.any { it.sourceKind == MusicSourceKind.LOCAL_FLAC || it.sourceKind == MusicSourceKind.ARCHIVE_FLAC }
            }
            SearchSourceFilter.STREAM -> results.filter { r ->
                r.candidates.any { it.sourceKind == MusicSourceKind.STREAMING_PAID }
            }
            SearchSourceFilter.LOCAL -> results.filter { r ->
                r.candidates.any { it.localTrack != null }
            }
            SearchSourceFilter.MP3 -> results.filter { r ->
                r.candidates.any { it.sourceKind == MusicSourceKind.ARCHIVE_LOSSY || it.sourceKind == MusicSourceKind.ITUNES_CATALOG }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
            .testTag("poweramp_multi_search_tab"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Search Input + Mode Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .vistaInsetBox(isFocused = query.isNotEmpty())
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Трек или артист (везде сразу)…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    var unifiedTfv by remember {
                        mutableStateOf(TextFieldValue(query, TextRange(query.length)))
                    }
                    LaunchedEffect(query) {
                        if (query != unifiedTfv.text) {
                            unifiedTfv = TextFieldValue(query, TextRange(query.length))
                        }
                    }
                    BasicTextField(
                        value = unifiedTfv,
                        onValueChange = { next ->
                            unifiedTfv = next
                            onQueryChange(next.text)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                        cursorBrush = SolidColor(theme.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("poweramp_unified_search_input")
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Очистить",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(15.dp)
                            .clickable { onQueryChange("") }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .vistaAeroButton(isPrimary = sourceSelectionMode == SourceSelectionMode.PICK, isActive = sourceSelectionMode == SourceSelectionMode.PICK)
                    .clickable(onClick = onToggleSourceMode)
                    .padding(horizontal = 9.dp, vertical = 7.dp)
            ) {
                Text(
                    text = if (sourceSelectionMode == SourceSelectionMode.AUTO) "Режим: Авто" else "Режим: Выбор",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }

        // Priority Legend & Filters
        Text(
            text = "Приоритет загрузки: а) FLAC → б) Захват оплаченных стримингов (WAV) → в) MP3/M4A",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MusicSourceAmber
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            SearchSourceFilter.entries.forEach { f ->
                val sel = selectedFilter == f
                Box(
                    modifier = Modifier
                        .vistaAeroButton(isPrimary = sel, isActive = sel)
                        .clickable { selectedFilter = f }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(f.title, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = if (sel) Color.White else TextSecondary)
                }
            }
        }

        when {
            isSearching -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = theme.accent)
                    Text("Одновременный опрос: Файлы, TG, Стриминги, Archive FLAC, iTunes…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            query.trim().length < 2 -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .vistaInsetBox(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Введите минимум 2 символа — поиск пройдёт одновременно по локальным FLAC/MP3, Telegram Audio, активным стримингам и онлайн-каталогам.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    modifier = Modifier.padding(24.dp)
                )
            }
            filteredResults.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .vistaInsetBox(),
                contentAlignment = Alignment.Center
            ) {
                Text("Совпадений по фильтру «${selectedFilter.title}» не найдено", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    itemsIndexed(filteredResults, key = { _, item -> item.key }) { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .vistaWindowFrame(RoundedCornerShape(4.dp))
                                .clickable { onPlayOrPick(item) }
                                .padding(9.dp)
                                .testTag("unified_music_row_$idx"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmartAlbumCoverBox(customPath = null, coverUrl = item.artworkUrl, size = 46.dp)
                            Spacer(Modifier.width(9.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(
                                        text = item.bestFormatBadge,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        color = if (item.primaryCandidate?.isLossless == true) CallIncomingGreen else MusicSourceAmber,
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                                            .border(1.dp, theme.chromeBorderOuter, RoundedCornerShape(3.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Text(
                                    text = listOf(item.artist, item.primaryCandidate?.sourceLabel.orEmpty())
                                        .filter { it.isNotBlank() }
                                        .joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.hasMultipleSources) {
                                    Text(
                                        text = "Найдено в источниках (${item.candidates.size}) — нажмите для выбора",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                        color = theme.accent,
                                        modifier = Modifier
                                            .clickable { onOpenSourcesModal(item) }
                                            .testTag("unified_sources_badge_$idx")
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (item.hasMultipleSources) {
                                    Box(
                                        modifier = Modifier
                                            .vistaAeroButton()
                                            .clickable { onOpenSourcesModal(item) }
                                            .padding(horizontal = 7.dp, vertical = 6.dp)
                                    ) {
                                        Text("${item.candidates.size}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = theme.accent)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .vistaAeroButton(isPrimary = true)
                                        .clickable { onPlayOrPick(item) }
                                        .padding(horizontal = 7.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Играть", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .vistaAeroButton()
                                        .clickable { onDownloadOrPick(item) }
                                        .padding(horizontal = 7.dp, vertical = 6.dp)
                                        .testTag("unified_download_btn_$idx")
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = "Скачать (FLAC/Поток)", tint = MusicSourceAmber, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SourceChooserDialog(
    result: UnifiedMusicSearchResult,
    forDownload: Boolean,
    onPickPlay: (TrackSourceCandidate) -> Unit,
    onPickDownload: (TrackSourceCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalVistaTheme.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 520.dp)
                .vistaWindowFrame(RoundedCornerShape(5.dp))
                .testTag("source_chooser_dialog")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (forDownload) "ВЫБОР ИСТОЧНИКА ЗАГРУЗКИ" else "ВЫБОР ИСТОЧНИКА ВОСПРОИЗВЕДЕНИЯ",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = theme.focusBorder
                    )
                    Text(
                        text = listOf(result.artist, result.title).filter { it.isNotBlank() }.joinToString(" — "),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                VistaButton(text = "✕", onClick = onDismiss)
            }

            LazyColumn(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(result.candidates, key = { it.id }) { candidate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .vistaInsetBox()
                            .clickable {
                                if (forDownload) onPickDownload(candidate) else onPickPlay(candidate)
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = candidate.formatBadge,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                            color = if (candidate.isLossless) CallIncomingGreen else MusicSourceAmber,
                            modifier = Modifier.width(50.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(candidate.sourceLabel, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                            val meta = buildString {
                                append(candidate.sourceKind.title)
                                if (candidate.bitrateKbps > 0) append(" • ${candidate.bitrateKbps} kbps")
                                if (candidate.sampleRateHz > 0) append(" • ${candidate.sampleRateHz / 1000}kHz")
                            }
                            Text(meta, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            VistaButton(text = "▶", isPrimary = !forDownload, onClick = { onPickPlay(candidate) })
                            VistaButton(
                                text = if (candidate.sourceKind == MusicSourceKind.STREAMING_PAID) "Захват" else "⬇",
                                isPrimary = forDownload,
                                onClick = { onPickDownload(candidate) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDeleteTrackDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val theme = LocalVistaTheme.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .vistaWindowFrame(RoundedCornerShape(5.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text("УДАЛИТЬ ТРЕК С УСТРОЙСТВА?", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = theme.focusBorder)
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Text("Файл «$title» будет безвозвратно удалён с устройства и убран из альбомов.", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    VistaButton(text = "Отмена", onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    VistaButton(text = "Удалить", isPrimary = true, onClick = onConfirm)
                }
            }
        }
    }
}
