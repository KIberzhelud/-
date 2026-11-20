package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
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

@Composable
fun PowerampAlbumsTab(
    albums: List<SmartAlbum>,
    selectedAlbumId: String?,
    localTracks: List<LocalAudioTrack>,
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
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    var titleInput by remember { mutableStateOf("") }
    var artistInput by remember { mutableStateOf("") }
    var showAttachTracksDialogForAlbum by remember { mutableStateOf<SmartAlbum?>(null) }
    var showAltCoversForAlbum by remember { mutableStateOf<SmartAlbum?>(null) }

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
                    text = "Приложение найдёт официальный список треков и обложку (MusicBrainz / iTunes), сверит с вашими файлами и докачает недостающие (FLAC → Стриминг → MP3).",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(8.dp))

            if (albums.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .vistaInsetBox(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Album,
                            contentDescription = null,
                            tint = theme.accent,
                            modifier = Modifier.size(42.dp)
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(albums, key = { it.id }) { album ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .vistaWindowFrame(RoundedCornerShape(4.dp))
                                .clickable { onSelectAlbum(album.id) }
                                .padding(10.dp)
                                .testTag("smart_album_card_${album.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmartAlbumCoverBox(
                                customPath = album.customCoverPath,
                                coverUrl = album.coverUrl,
                                size = 56.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = album.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (album.artist.isNotBlank()) {
                                    Text(
                                        text = album.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (album.totalExpected > 0) {
                                            "Треков: ${album.matchedCount} из ${album.totalExpected}"
                                        } else {
                                            "Треков добавлено: ${album.attachedLocalTrackIds.size}"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (album.missingCount == 0 && album.matchedCount > 0) CallIncomingGreen else MusicSourceAmber
                                    )
                                    if (album.missingCount > 0) {
                                        Text(
                                            text = "Недостаёт: ${album.missingCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CallMissedRed
                                        )
                                    }
                                    if (album.isFetchingOnline) {
                                        CircularProgressIndicator(
                                            color = theme.accent,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                            VistaButton(
                                text = "Открыть",
                                isPrimary = true,
                                onClick = { onSelectAlbum(album.id) }
                            )
                        }
                    }
                }
            }
        } else {
            // Selected Album Detail View
            val localTracksById = remember(localTracks) { localTracks.associateBy { it.id } }
            val matchedIdsInSlots = remember(selectedAlbum) {
                selectedAlbum.expectedTracks.mapNotNull { it.matchedLocalTrackId }.toSet()
            }
            val extraLocalTracks = remember(selectedAlbum, localTracksById, matchedIdsInSlots) {
                selectedAlbum.attachedLocalTrackIds
                    .filterNot { matchedIdsInSlots.contains(it) }
                    .mapNotNull { localTracksById[it] }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Header bar
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
                        size = 72.dp
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
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Actions Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                            Text("Добавить треки с устройства", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
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
                            Text("Сменить обложку", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
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
                    Box(
                        modifier = Modifier
                            .vistaAeroButton()
                            .clickable { onRefreshAlbumOnline(selectedAlbum.id) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
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
                            Text("Удалить альбом", style = MaterialTheme.typography.labelSmall, color = CallMissedRed)
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    contentPadding = PaddingValues(bottom = 14.dp)
                ) {
                    if (selectedAlbum.expectedTracks.isNotEmpty()) {
                        itemsIndexed(
                            selectedAlbum.expectedTracks,
                            key = { idx, slot -> "exp_${slot.trackNumber}_${slot.title}_$idx" }
                        ) { _, slot ->
                            val matchedTrack = slot.matchedLocalTrackId?.let { localTracksById[it] }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (matchedTrack != null) theme.surface.copy(alpha = 0.88f)
                                        else theme.insetBg.copy(alpha = 0.92f),
                                        RoundedCornerShape(3.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (matchedTrack != null) CallIncomingGreen.copy(alpha = 0.55f)
                                        else MusicSourceAmber.copy(alpha = 0.55f),
                                        RoundedCornerShape(3.dp)
                                    )
                                    .clickable {
                                        if (matchedTrack != null) onPlayLocalTrack(matchedTrack)
                                        else onDownloadMissingTrack(selectedAlbum, slot)
                                    }
                                    .padding(horizontal = 9.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "%02d".format(slot.trackNumber),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (matchedTrack != null) CallIncomingGreen else TextMuted,
                                    modifier = Modifier.width(24.dp)
                                )
                                Icon(
                                    imageVector = if (matchedTrack != null) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = if (matchedTrack != null) CallIncomingGreen else MusicSourceAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(7.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = slot.title,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sub = if (matchedTrack != null) {
                                        "Есть (${slot.matchedFormat ?: "AUDIO"}) • ${matchedTrack.title}"
                                    } else {
                                        val dur = if (slot.durationMs > 0) MediaControlRepository.formatTimeMs(slot.durationMs) else ""
                                        "Недостаёт на устройстве ${if (dur.isNotBlank()) "• $dur" else ""}"
                                    }
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = if (matchedTrack != null) CallIncomingGreen else MusicSourceAmber,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (matchedTrack != null) {
                                    Box(
                                        modifier = Modifier
                                            .vistaAeroButton()
                                            .clickable { onOpenLyricsForTrack(matchedTrack) }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Rounded.Lyrics, contentDescription = "Текст", tint = theme.accent, modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .vistaAeroButton(isPrimary = true)
                                            .clickable { onPlayLocalTrack(matchedTrack) }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Играть", tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(Modifier.width(4.dp))
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
                        }
                    }

                    if (extraLocalTracks.isNotEmpty()) {
                        item(key = "extra_attached_header") {
                            Text(
                                text = "ДОПОЛНИТЕЛЬНО ДОБАВЛЕННЫЕ ТРЕКИ (${extraLocalTracks.size})",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = theme.accent,
                                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                            )
                        }
                        items(extraLocalTracks, key = { "extra_${it.id}" }) { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .vistaInsetBox()
                                    .clickable { onPlayLocalTrack(track) }
                                    .padding(horizontal = 9.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Играть", tint = theme.accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(7.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track.title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${track.artist.ifBlank { "Неизвестный" }} • ${track.formatLabel}", style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
                                }
                                Box(
                                    modifier = Modifier
                                        .vistaAeroButton()
                                        .clickable { onOpenLyricsForTrack(track) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Rounded.Lyrics, contentDescription = "Текст", tint = theme.accent, modifier = Modifier.size(14.dp))
                                }
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .vistaAeroButton()
                                        .clickable { onDetachLocalTrack(selectedAlbum.id, track.id) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Убрать", tint = CallMissedRed, modifier = Modifier.size(13.dp))
                                }
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
}

@Composable
fun SmartAlbumCoverBox(
    customPath: String?,
    coverUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    var loadedBitmap by remember(customPath, coverUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(customPath, coverUrl) {
        loadedBitmap = MusicCatalogAndLyricsRepository.loadArtworkBitmap(customPath)
            ?: MusicCatalogAndLyricsRepository.loadArtworkBitmap(coverUrl)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(theme.insetBg)
            .border(1.dp, theme.chromeBorderOuter, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (loadedBitmap != null) {
            Image(
                bitmap = loadedBitmap!!,
                contentDescription = "Обложка альбома",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp)
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

            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        .weight(1f, fill = false)
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(filtered, key = { it.id }) { track ->
                        val checked = selectedIds.contains(track.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (checked) theme.accent.copy(alpha = 0.2f) else theme.insetBg,
                                    RoundedCornerShape(3.dp)
                                )
                                .border(1.dp, if (checked) theme.accent else theme.divider, RoundedCornerShape(3.dp))
                                .clickable {
                                    if (checked) selectedIds.remove(track.id) else selectedIds.add(track.id)
                                }
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (checked) CallIncomingGreen else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${track.artist.ifBlank { "Артист" }} • ${track.album.ifBlank { track.displayPath }}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = track.formatLabel.ifBlank { "AUDIO" },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                                color = MusicSourceAmber
                            )
                        }
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
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ВЫБОР ОБЛОЖКИ", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = theme.focusBorder)
                VistaButton(text = "✕", onClick = onDismiss)
            }
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
