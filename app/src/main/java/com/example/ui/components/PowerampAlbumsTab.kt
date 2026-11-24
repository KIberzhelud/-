package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.KeyboardSearchMatcher
import com.example.data.MediaControlRepository
import com.example.data.MusicCatalogAndLyricsRepository
import com.example.model.AlbumExpectedTrack
import com.example.model.LocalAudioTrack
import com.example.model.SmartAlbum
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PowerampAlbumsTab(
    albums: List<SmartAlbum>,
    selectedAlbumId: String?,
    localTracks: List<LocalAudioTrack>,
    isLandscape: Boolean = false,
    onSelectAlbum: (String?) -> Unit,
    onCreateAlbum: (title: String, artist: String) -> Unit,
    onRefreshAlbumOnline: (String) -> Unit,
    onAttachLocalTracks: (albumId: String, trackIds: List<String>) -> Unit,
    onDetachLocalTrack: (albumId: String, trackId: String) -> Unit,
    onPickCustomAlbumCover: (albumId: String) -> Unit,
    onSelectAlternateCoverUrl: (albumId: String, coverUrl: String) -> Unit,
    onDeleteAlbum: (String) -> Unit,
    onPlayLocalTrack: (LocalAudioTrack) -> Unit,
    onOpenLyricsForTrack: (LocalAudioTrack) -> Unit,
    onDownloadMissingTrack: (SmartAlbum, AlbumExpectedTrack) -> Unit,
    onDownloadAllMissingTracks: (String) -> Unit,
    defaultFallbackCoverPath: String? = null,
    onDeleteLocalTrack: (LocalAudioTrack) -> Unit = {},
    onEditTrackMetadata: (LocalAudioTrack) -> Unit = {},
    onLongClickTrack: ((LocalAudioTrack, SmartAlbum?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    var titleInput by remember { mutableStateOf("") }
    var artistInput by remember { mutableStateOf("") }
    var showAttachTracksDialogForAlbum by remember { mutableStateOf<SmartAlbum?>(null) }
    var showAltCoversForAlbum by remember { mutableStateOf<SmartAlbum?>(null) }
    var fallbackTrackMenuPair by remember { mutableStateOf<Pair<LocalAudioTrack, SmartAlbum?>?>(null) }
    val handleTrackLongClick: (LocalAudioTrack, SmartAlbum?) -> Unit = { tr, alb ->
        if (onLongClickTrack != null) {
            onLongClickTrack(tr, alb)
        } else {
            fallbackTrackMenuPair = tr to alb
        }
    }

    val selectedAlbum = remember(albums, selectedAlbumId) {
        selectedAlbumId?.let { id -> albums.firstOrNull { it.id == id } }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("poweramp_albums_tab")
    ) {
        if (selectedAlbum == null) {
            // Album Creator Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .vistaInsetBox()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = "СОЗДАТЬ УМНЫЙ АЛЬБОМ (СВЕРКА ТРЕКЛИСТА И ОБЛОЖЕК)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = theme.focusBorder
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .vistaInsetBox(isFocused = titleInput.isNotEmpty())
                            .padding(horizontal = 8.dp, vertical = 7.dp)
                    ) {
                        if (titleInput.isEmpty()) {
                            Text("Название альбома…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                        BasicTextField(
                            value = titleInput,
                            onValueChange = { titleInput = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                            cursorBrush = SolidColor(theme.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("album_create_title_input")
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .vistaInsetBox(isFocused = artistInput.isNotEmpty())
                            .padding(horizontal = 8.dp, vertical = 7.dp)
                    ) {
                        if (artistInput.isEmpty()) {
                            Text("Исполнитель…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                        BasicTextField(
                            value = artistInput,
                            onValueChange = { artistInput = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                            cursorBrush = SolidColor(theme.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("album_create_artist_input")
                        )
                    }
                    Box(
                        modifier = Modifier
                            .vistaAeroButton(isPrimary = titleInput.isNotBlank())
                            .clickable(enabled = titleInput.isNotBlank()) {
                                onCreateAlbum(titleInput.trim(), artistInput.trim())
                                titleInput = ""
                                artistInput = ""
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("album_create_submit_btn")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Создать", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                        }
                    }
                }
                Text(
                    text = "Поиск по Discogs.com, MusicBrainz, Deezer, iTunes и Archive.org — сверка с вашими файлами и докачка с 17 интернет-источников (приоритет FLAC и discogs.com).",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(if (isLandscape) 5.dp else 8.dp))

            if (albums.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .vistaInsetBox(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Album,
                            contentDescription = null,
                            tint = theme.accent,
                            modifier = Modifier.size(if (isLandscape) 34.dp else 42.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Альбомов пока нет",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Введите название альбома выше — можно добавить свои треки с устройства и автоматически добрать недостающие.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = if (isLandscape) 142.dp else 148.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("smart_albums_grid"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 14.dp)
                ) {
                    gridItems(albums, key = { it.id }) { album ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .vistaWindowFrame(RoundedCornerShape(5.dp))
                                .clickable { onSelectAlbum(album.id) }
                                .padding(7.dp)
                                .testTag("smart_album_card_${album.id}"),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .vistaInsetBox(shape = RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val resolvedCover = album.customCoverPath?.takeIf { it.isNotBlank() }
                                    ?: album.coverUrl?.takeIf { it.isNotBlank() }
                                    ?: defaultFallbackCoverPath
                                RichCoverMedia(
                                    mediaPathOrUrl = resolvedCover,
                                    contentDescription = album.title,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                                    placeholder = {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Album,
                                                contentDescription = album.title,
                                                tint = theme.accent,
                                                modifier = Modifier.size(44.dp)
                                            )
                                        }
                                    }
                                )

                                // Bottom-right status pill overlay
                                val statusBadge = when {
                                    album.isFetchingOnline -> "Поиск…"
                                    album.totalExpected > 0 -> "${album.matchedCount}/${album.totalExpected}"
                                    else -> "${album.attachedLocalTrackIds.size} тр."
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(5.dp)
                                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(3.dp))
                                        .border(
                                            1.dp,
                                            if (album.missingCount == 0 && album.matchedCount > 0) CallIncomingGreen else MusicSourceAmber,
                                            RoundedCornerShape(3.dp)
                                        )
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = statusBadge,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = if (album.missingCount == 0 && album.matchedCount > 0) CallIncomingGreen else MusicSourceAmber
                                    )
                                }

                                if (album.isFetchingOnline) {
                                    CircularProgressIndicator(
                                        color = theme.accent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(5.dp)
                                            .size(16.dp)
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = album.title,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = album.artist.ifBlank { "Исполнитель не указан" },
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = if (album.artist.isNotBlank()) TextSecondary else TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when {
                                        album.missingCount > 0 -> "Недостаёт: ${album.missingCount}"
                                        album.totalExpected > 0 && album.matchedCount >= album.totalExpected -> "Альбом собран"
                                        else -> "Локально: ${album.attachedLocalTrackIds.size}"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = when {
                                        album.missingCount > 0 -> CallMissedRed
                                        album.matchedCount > 0 -> CallIncomingGreen
                                        else -> MusicSourceAmber
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Selected Album Detail View
            var hideMissingTracks by rememberSaveable(selectedAlbum.id) { mutableStateOf(false) }
            val localTracksById = remember(localTracks) { localTracks.associateBy { it.id } }
            val matchedIdsInSlots = remember(selectedAlbum) {
                selectedAlbum.expectedTracks.mapNotNull { it.matchedLocalTrackId }.toSet()
            }
            val matchedTitleKeys = remember(selectedAlbum) {
                selectedAlbum.expectedTracks
                    .filter { it.isMatched }
                    .map { MusicCatalogAndLyricsRepository.normalizeKeyText(it.title) }
                    .filter { it.isNotEmpty() }
                    .toSet()
            }
            val displayedExpectedTracks = remember(selectedAlbum.expectedTracks, hideMissingTracks) {
                if (hideMissingTracks) selectedAlbum.expectedTracks.filter { it.isMatched }
                else selectedAlbum.expectedTracks
            }
            val extraLocalTracks = remember(selectedAlbum, localTracksById, matchedIdsInSlots, matchedTitleKeys) {
                val seenKeys = HashSet<String>()
                selectedAlbum.attachedLocalTrackIds
                    .filterNot { matchedIdsInSlots.contains(it) }
                    .mapNotNull { localTracksById[it] }
                    .filter { tr ->
                        val normTitle = MusicCatalogAndLyricsRepository.normalizeKeyText(tr.title)
                        if (matchedTitleKeys.contains(normTitle)) return@filter false
                        val dedupKey = tr.filePath?.takeIf { it.isNotBlank() }?.lowercase()
                            ?: "${normTitle}|${tr.durationMs / 2000L}"
                        seenKeys.add(dedupKey)
                    }
            }

            val albumHeaderCard: @Composable () -> Unit = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaWindowFrame(RoundedCornerShape(4.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmartAlbumCoverBox(
                        customPath = selectedAlbum.customCoverPath,
                        coverUrl = selectedAlbum.coverUrl,
                        fallbackCoverPath = defaultFallbackCoverPath,
                        size = if (isLandscape) 60.dp else 72.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedAlbum.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (selectedAlbum.artist.isNotBlank()) {
                            Text(
                                text = selectedAlbum.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        val comparisonText = if (selectedAlbum.totalExpected > 0) {
                            "На устройстве: ${selectedAlbum.matchedCount} из ${selectedAlbum.totalExpected} • Недостаёт: ${selectedAlbum.missingCount}"
                        } else {
                            "Локальных треков: ${selectedAlbum.attachedLocalTrackIds.size}"
                        }
                        Text(
                            text = comparisonText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (selectedAlbum.missingCount == 0 && selectedAlbum.matchedCount > 0) CallIncomingGreen else MusicSourceAmber,
                            modifier = Modifier.testTag("album_comparison_summary")
                        )
                        if (selectedAlbum.statusMessage.isNotBlank()) {
                            Text(
                                text = selectedAlbum.statusMessage,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = TextMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            val albumActionItems: @Composable () -> Unit = {
                Box(
                    modifier = Modifier
                        .vistaAeroButton()
                        .clickable { onSelectAlbum(null) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("album_back_to_list_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад", tint = TextPrimary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Список", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                    }
                }
                Box(
                    modifier = Modifier
                        .vistaAeroButton(isPrimary = true)
                        .clickable { showAttachTracksDialogForAlbum = selectedAlbum }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                        .testTag("album_add_local_tracks_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Добавить треки", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                    }
                }
                Box(
                    modifier = Modifier
                        .vistaAeroButton()
                        .clickable { onPickCustomAlbumCover(selectedAlbum.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("album_change_cover_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Image, contentDescription = null, tint = theme.accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Обложка", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                    }
                }
                if (selectedAlbum.alternateCoverUrls.size > 1) {
                    Box(
                        modifier = Modifier
                            .vistaAeroButton()
                            .clickable { showAltCoversForAlbum = selectedAlbum }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text("Варианты (${selectedAlbum.alternateCoverUrls.size})", style = MaterialTheme.typography.labelSmall, color = MusicSourceAmber)
                    }
                }
                if (selectedAlbum.missingCount > 0) {
                    Box(
                        modifier = Modifier
                            .vistaAeroButton(isPrimary = true)
                            .clickable { onDownloadAllMissingTracks(selectedAlbum.id) }
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                            .testTag("album_download_missing_btn")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Докачать (${selectedAlbum.missingCount})", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                        }
                    }
                }
                if (selectedAlbum.missingCount > 0) {
                    Box(
                        modifier = Modifier
                            .vistaAeroButton(isPrimary = hideMissingTracks, isActive = hideMissingTracks)
                            .clickable { hideMissingTracks = !hideMissingTracks }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("album_toggle_missing_btn")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (hideMissingTracks) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = null,
                                tint = if (hideMissingTracks) Color.White else MusicSourceAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (hideMissingTracks) {
                                    "Показать недостающие (${selectedAlbum.missingCount})"
                                } else {
                                    "Скрыть недостающие (${selectedAlbum.missingCount})"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (hideMissingTracks) Color.White else TextPrimary
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .vistaAeroButton()
                        .clickable { onRefreshAlbumOnline(selectedAlbum.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("album_refresh_reconcile_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = theme.accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Сверить", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                    }
                }
                Box(
                    modifier = Modifier
                        .vistaAeroButton()
                        .clickable { onDeleteAlbum(selectedAlbum.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = CallMissedRed, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Удалить", style = MaterialTheme.typography.labelSmall, color = CallMissedRed)
                    }
                }
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.36f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        albumHeaderCard()
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            albumActionItems()
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(0.64f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 14.dp)
                    ) {
                        if (selectedAlbum.expectedTracks.isNotEmpty()) {
                            item(key = "exp_header") {
                                RetroListSectionStrip(
                                    title = "ТРЕКЛИСТ И СВЕРКА",
                                    countBadge = "${selectedAlbum.matchedCount}/${selectedAlbum.totalExpected}"
                                )
                            }
                            if (displayedExpectedTracks.isEmpty() && hideMissingTracks) {
                                item(key = "all_hidden_hint") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .vistaInsetBox()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Все недостающие треки скрыты", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                        VistaButton(text = "Показать (${selectedAlbum.missingCount})", onClick = { hideMissingTracks = false })
                                    }
                                }
                            }
                            itemsIndexed(displayedExpectedTracks, key = { idx, tr -> "exp_${tr.trackNumber}_${tr.title}_$idx" }) { idx, exp ->
                                val matchedLocal = exp.matchedLocalTrackId?.let { localTracksById[it] }
                                RetroGlossyTrackRow(
                                    index = idx,
                                    title = "${exp.trackNumber}. ${exp.title}",
                                    subtitle = if (matchedLocal != null) {
                                        "Есть: ${matchedLocal.displayPath}"
                                    } else {
                                        exp.artist.ifBlank { selectedAlbum.artist }
                                    },
                                    badgeText = matchedLocal?.formatLabel?.ifBlank { "OK" } ?: "НЕТ",
                                    badgeLossless = matchedLocal?.isLossless == true,
                                    durationText = MediaControlRepository.formatTimeMs(exp.durationMs).takeIf { exp.durationMs > 0L },
                                    isActive = matchedLocal != null,
                                    statusBarColor = if (matchedLocal != null) CallIncomingGreen else CallMissedRed,
                                    onClick = {
                                        if (matchedLocal != null) {
                                            onPlayLocalTrack(matchedLocal)
                                        } else {
                                            onDownloadMissingTrack(selectedAlbum, exp)
                                        }
                                    },
                                    onLongClick = {
                                        if (matchedLocal != null) {
                                            handleTrackLongClick(matchedLocal, selectedAlbum)
                                        } else {
                                            onDownloadMissingTrack(selectedAlbum, exp)
                                        }
                                    },
                                    modifier = Modifier.testTag("album_track_row_${exp.trackNumber}"),
                                    trailingSlot = {
                                        if (matchedLocal != null) {
                                            Box(
                                                modifier = Modifier
                                                    .vistaAeroButton()
                                                    .clickable { onOpenLyricsForTrack(matchedLocal) }
                                                    .padding(horizontal = 6.dp, vertical = 5.dp)
                                            ) {
                                                Icon(Icons.Rounded.Lyrics, contentDescription = "Текст", tint = theme.accent, modifier = Modifier.size(14.dp))
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .vistaAeroButton(isPrimary = true)
                                                    .clickable { onDownloadMissingTrack(selectedAlbum, exp) }
                                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                                    .testTag("album_download_track_${exp.trackNumber}")
                                            ) {
                                                Text("Скачать", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        if (extraLocalTracks.isNotEmpty()) {
                            item(key = "extra_header") {
                                Spacer(Modifier.height(6.dp))
                                RetroListSectionStrip(
                                    title = "ДОБАВЛЕННЫЕ С УСТРОЙСТВА",
                                    countBadge = "${extraLocalTracks.size}"
                                )
                            }
                            itemsIndexed(extraLocalTracks, key = { idx, tr -> "extra_${idx}_${tr.id}" }) { idx, tr ->
                                RetroGlossyTrackRow(
                                    index = idx,
                                    title = tr.title,
                                    subtitle = tr.artist.ifBlank { tr.displayPath },
                                    badgeText = tr.formatLabel.ifBlank { "AUDIO" },
                                    badgeLossless = tr.isLossless,
                                    durationText = if (tr.durationMs > 0L) MediaControlRepository.formatTimeMs(tr.durationMs) else null,
                                    isActive = true,
                                    statusBarColor = theme.accent,
                                    onClick = { onPlayLocalTrack(tr) },
                                    onLongClick = { handleTrackLongClick(tr, selectedAlbum) },
                                    modifier = Modifier.testTag("album_extra_track_$idx"),
                                    trailingSlot = {
                                        Box(
                                            modifier = Modifier
                                                .vistaAeroButton()
                                                .clickable { onDetachLocalTrack(selectedAlbum.id, tr.id) }
                                                .padding(horizontal = 6.dp, vertical = 5.dp)
                                        ) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Убрать из альбома", tint = CallMissedRed, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                )
                            }
                        }

                        if (selectedAlbum.expectedTracks.isEmpty() && extraLocalTracks.isEmpty()) {
                            item(key = "empty_album_detail") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .vistaInsetBox()
                                        .padding(18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (selectedAlbum.isFetchingOnline) {
                                            "Поиск официального треклиста и обложки…"
                                        } else {
                                            "Нажмите «Добавить треки» для сверки или «Сверить» для загрузки списка треков."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    albumHeaderCard()

                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        albumActionItems()
                    }

                    Spacer(Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 14.dp)
                    ) {
                        if (selectedAlbum.expectedTracks.isNotEmpty()) {
                            item(key = "expected_tracks_header") {
                                RetroListSectionStrip(
                                    title = "ТРЕКЛИСТ АЛЬБОМА",
                                    countBadge = "${selectedAlbum.matchedCount}/${selectedAlbum.expectedTracks.size}"
                                )
                            }
                            if (displayedExpectedTracks.isEmpty() && hideMissingTracks) {
                                item(key = "all_hidden_hint_port") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .vistaInsetBox()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Все недостающие треки скрыты", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                        VistaButton(text = "Показать (${selectedAlbum.missingCount})", onClick = { hideMissingTracks = false })
                                    }
                                }
                            }
                            itemsIndexed(
                                displayedExpectedTracks,
                                key = { idx, slot -> "exp_${slot.trackNumber}_${slot.title}_$idx" }
                            ) { idx, slot ->
                            val matchedTrack = slot.matchedLocalTrackId?.let { localTracksById[it] }
                            val isMatchedLossless = matchedTrack != null && (
                                slot.matchedFormat?.contains("FLAC", ignoreCase = true) == true ||
                                    slot.matchedFormat?.contains("WAV", ignoreCase = true) == true ||
                                    matchedTrack.isLossless
                                )
                            RetroGlossyTrackRow(
                                index = idx,
                                title = slot.title,
                                subtitle = if (matchedTrack != null) {
                                    "Есть • ${matchedTrack.title}"
                                } else {
                                    "Недостаёт на устройстве"
                                },
                                trackNumberOverride = slot.trackNumber,
                                badgeText = if (matchedTrack != null) (slot.matchedFormat ?: "AUDIO") else "НЕТ",
                                badgeLossless = isMatchedLossless,
                                durationText = if (slot.durationMs > 0) MediaControlRepository.formatTimeMs(slot.durationMs) else null,
                                isActive = matchedTrack != null,
                                statusBarColor = if (matchedTrack != null) CallIncomingGreen else MusicSourceAmber,
                                onClick = {
                                    if (matchedTrack != null) onPlayLocalTrack(matchedTrack)
                                    else onDownloadMissingTrack(selectedAlbum, slot)
                                },
                                onLongClick = {
                                    if (matchedTrack != null) handleTrackLongClick(matchedTrack, selectedAlbum)
                                    else onDownloadMissingTrack(selectedAlbum, slot)
                                },
                                modifier = Modifier.testTag("album_track_row_${slot.trackNumber}"),
                                leadingSlot = {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .vistaInsetBox(isFocused = matchedTrack != null, shape = RoundedCornerShape(2.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (matchedTrack != null) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                                            contentDescription = null,
                                            tint = if (matchedTrack != null) CallIncomingGreen else MusicSourceAmber,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                },
                                trailingSlot = {
                                    if (matchedTrack != null) {
                                        Box(
                                            modifier = Modifier
                                                .vistaAeroButton()
                                                .clickable { onOpenLyricsForTrack(matchedTrack) }
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Rounded.Lyrics, contentDescription = "Текст", tint = theme.accent, modifier = Modifier.size(14.dp))
                                        }
                                        Box(
                                            modifier = Modifier
                                                .vistaAeroButton(isPrimary = true)
                                                .clickable { onPlayLocalTrack(matchedTrack) }
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Играть", tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                        Box(
                                            modifier = Modifier
                                                .vistaAeroButton()
                                                .clickable { onDetachLocalTrack(selectedAlbum.id, matchedTrack.id) }
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Убрать из альбома", tint = TextSecondary, modifier = Modifier.size(13.dp))
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .vistaAeroButton(isPrimary = true)
                                                .clickable { onDownloadMissingTrack(selectedAlbum, slot) }
                                                .padding(horizontal = 7.dp, vertical = 5.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Rounded.Download, contentDescription = "Скачать", tint = Color.White, modifier = Modifier.size(13.dp))
                                                Spacer(Modifier.width(3.dp))
                                                Text("FLAC/Поток", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = Color.White)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }

                    if (extraLocalTracks.isNotEmpty()) {
                        item(key = "extra_attached_header") {
                            RetroListSectionStrip(
                                title = "ДОПОЛНИТЕЛЬНО ДОБАВЛЕННЫЕ ТРЕКИ",
                                countBadge = "${extraLocalTracks.size}",
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        itemsIndexed(extraLocalTracks, key = { idx, it -> "extra_${idx}_${it.id}" }) { idx, track ->
                            RetroGlossyTrackRow(
                                index = idx,
                                title = track.title,
                                subtitle = track.artist.ifBlank { "Неизвестный" },
                                badgeText = track.formatLabel.ifBlank { "AUDIO" },
                                badgeLossless = track.isLossless,
                                durationText = if (track.durationMs > 0L) MediaControlRepository.formatTimeMs(track.durationMs) else null,
                                onClick = { onPlayLocalTrack(track) },
                                onLongClick = { handleTrackLongClick(track, selectedAlbum) },
                                modifier = Modifier.testTag("album_extra_track_$idx"),
                                trailingSlot = {
                                    Box(
                                        modifier = Modifier
                                            .vistaAeroButton()
                                            .clickable { onOpenLyricsForTrack(track) }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Rounded.Lyrics, contentDescription = "Текст", tint = theme.accent, modifier = Modifier.size(14.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .vistaAeroButton()
                                            .clickable { onDetachLocalTrack(selectedAlbum.id, track.id) }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Убрать", tint = CallMissedRed, modifier = Modifier.size(13.dp))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    }

    showAttachTracksDialogForAlbum?.let { albumTarget ->
        AttachLocalTracksDialog(
            album = albumTarget,
            localTracks = localTracks,
            onDismiss = { showAttachTracksDialogForAlbum = null },
            onConfirm = { chosenIds ->
                onAttachLocalTracks(albumTarget.id, chosenIds)
                showAttachTracksDialogForAlbum = null
            }
        )
    }

    showAltCoversForAlbum?.let { albumTarget ->
        AlternateAlbumCoversDialog(
            album = albumTarget,
            onPickFromGallery = {
                showAltCoversForAlbum = null
                onPickCustomAlbumCover(albumTarget.id)
            },
            onSelectUrl = { url ->
                onSelectAlternateCoverUrl(albumTarget.id, url)
                showAltCoversForAlbum = null
            },
            onDismiss = { showAltCoversForAlbum = null }
        )
    }

    fallbackTrackMenuPair?.let { (track, alb) ->
        TrackContextMenuDialog(
            track = track,
            albumContext = alb,
            smartAlbums = albums,
            onDismiss = { fallbackTrackMenuPair = null },
            onPlay = { onPlayLocalTrack(track) },
            onOpenLyrics = { onOpenLyricsForTrack(track) },
            onEditMetadata = { onEditTrackMetadata(track) },
            onAttachToAlbum = { targetAlbId -> onAttachLocalTracks(targetAlbId, listOf(track.id)) },
            onDetachFromAlbum = { targetAlbId -> onDetachLocalTrack(targetAlbId, track.id) },
            onDeleteTrack = { onDeleteLocalTrack(track) }
        )
    }
}

@Composable
fun SmartAlbumCoverBox(
    customPath: String?,
    coverUrl: String?,
    size: Dp,
    fallbackCoverPath: String? = null,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    val resolvedMedia = customPath?.takeIf { it.isNotBlank() }
        ?: coverUrl?.takeIf { it.isNotBlank() }
        ?: fallbackCoverPath?.takeIf { it.isNotBlank() }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(theme.insetBg)
            .border(1.dp, theme.chromeBorderOuter, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (resolvedMedia != null) {
            RichCoverMedia(
                mediaPathOrUrl = resolvedMedia,
                contentDescription = "Обложка альбома",
                modifier = Modifier.fillMaxSize(),
                placeholder = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Album,
                            contentDescription = "Обложка",
                            tint = theme.accent.copy(alpha = 0.85f),
                            modifier = Modifier.size(size * 0.52f)
                        )
                    }
                }
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Album,
                contentDescription = "Обложка",
                tint = theme.accent.copy(alpha = 0.85f),
                modifier = Modifier.size(size * 0.52f)
            )
        }
    }
}

@Composable
private fun AttachLocalTracksDialog(
    album: SmartAlbum,
    localTracks: List<LocalAudioTrack>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val theme = LocalVistaTheme.current
    var query by remember { mutableStateOf("") }
    val selectedIds = remember(album.id) {
        mutableStateListOf<String>().apply { addAll(album.attachedLocalTrackIds) }
    }
    val filtered = remember(localTracks, query) {
        if (query.isBlank()) localTracks
        else localTracks.filter {
            KeyboardSearchMatcher.matches(it.title, query) ||
                KeyboardSearchMatcher.matches(it.artist, query) ||
                KeyboardSearchMatcher.matches(it.album, query)
        }
    }

    val maxDialogH = (LocalConfiguration.current.screenHeightDp.dp * 0.90f).coerceAtLeast(240.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 560.dp)
                .heightIn(max = maxDialogH)
                .vistaWindowFrame(RoundedCornerShape(5.dp))
                .testTag("attach_album_tracks_dialog")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ТРЕКИ С УСТРОЙСТВА → ${album.title}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = theme.focusBorder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                VistaButton(text = "✕", onClick = onDismiss)
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox(isFocused = query.isNotEmpty())
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                ) {
                    if (query.isEmpty()) {
                        Text("Фильтр по названию, артисту, папке…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                        cursorBrush = SolidColor(theme.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(filtered, key = { idx, it -> "attach_${idx}_${it.id}" }) { idx, track ->
                        val checked = selectedIds.contains(track.id)
                        RetroGlossyTrackRow(
                            index = idx,
                            title = track.title,
                            subtitle = "${track.artist.ifBlank { "Артист" }} • ${track.album.ifBlank { track.displayPath }}",
                            badgeText = track.formatLabel.ifBlank { "AUDIO" },
                            badgeLossless = track.isLossless,
                            isActive = checked,
                            statusBarColor = if (checked) CallIncomingGreen else null,
                            onClick = {
                                if (checked) selectedIds.remove(track.id) else selectedIds.add(track.id)
                            },
                            leadingSlot = {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .vistaInsetBox(isFocused = checked, shape = RoundedCornerShape(2.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (checked) CallIncomingGreen else TextMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    VistaButton(text = "Отмена", onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    VistaButton(
                        text = "Сверить (${selectedIds.size})",
                        isPrimary = true,
                        onClick = { onConfirm(selectedIds.toList()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlternateAlbumCoversDialog(
    album: SmartAlbum,
    onPickFromGallery: () -> Unit,
    onSelectUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalVistaTheme.current
    val maxDialogH = (LocalConfiguration.current.screenHeightDp.dp * 0.90f).coerceAtLeast(240.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .heightIn(max = maxDialogH)
                .vistaWindowFrame(RoundedCornerShape(5.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ВЫБОР ОБЛОЖКИ", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = theme.focusBorder)
                VistaButton(text = "✕", onClick = onDismiss)
            }
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VistaButton(
                    text = "Выбрать своё фото из галереи",
                    isPrimary = true,
                    onClick = onPickFromGallery,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    album.alternateCoverUrls.forEach { url ->
                        Box(
                            modifier = Modifier
                                .border(
                                    width = if (url == album.coverUrl) 2.dp else 1.dp,
                                    color = if (url == album.coverUrl) theme.accent else theme.chromeBorderOuter,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable { onSelectUrl(url) }
                        ) {
                            SmartAlbumCoverBox(customPath = null, coverUrl = url, size = 84.dp)
                        }
                    }
                }
            }
        }
    }
}
