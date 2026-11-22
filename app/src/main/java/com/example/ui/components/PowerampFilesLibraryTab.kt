package com.example.ui.components

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.KeyboardSearchMatcher
import com.example.data.MediaControlRepository
import com.example.model.AudioCaptureState
import com.example.model.LocalAudioTrack
import com.example.model.MediaPlayerState
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

private enum class LibrarySourceFilter(val title: String) {
    ALL("Все"),
    KEYDECK("KeyDeck"),
    TELEGRAM("Telegram")
}

@Composable
fun PowerampFilesLibraryTab(
    localTracks: List<LocalAudioTrack>,
    mediaState: MediaPlayerState,
    audioCaptureState: AudioCaptureState,
    smartAlbums: List<SmartAlbum>,
    isLandscape: Boolean = false,
    onPlayTrack: (LocalAudioTrack) -> Unit,
    onRenameTrack: (LocalAudioTrack, String) -> Unit,
    onDeleteTrack: (LocalAudioTrack) -> Unit,
    onOpenLyricsForTrack: (LocalAudioTrack) -> Unit,
    onAttachTrackToAlbum: (String, List<String>) -> Unit,
    onToggleCaptureAutoSplit: () -> Unit,
    onToggleCaptureSilentMute: () -> Unit
) {
    val theme = LocalVistaTheme.current
    var filter by remember { mutableStateOf(LibrarySourceFilter.ALL) }
    var searchTfv by remember { mutableStateOf(TextFieldValue("")) }
    var renameTarget by remember { mutableStateOf<LocalAudioTrack?>(null) }
    var deleteTarget by remember { mutableStateOf<LocalAudioTrack?>(null) }
    var attachTarget by remember { mutableStateOf<LocalAudioTrack?>(null) }

    val active = mediaState.activeSession

    val filteredTracks = remember(localTracks, filter, searchTfv.text) {
        val bySource = when (filter) {
            LibrarySourceFilter.ALL -> localTracks
            LibrarySourceFilter.KEYDECK -> localTracks.filter {
                val p = (it.filePath ?: it.displayPath).lowercase()
                p.contains("keydeck") || p.contains("downloads") || p.contains("music/keydeck")
            }
            LibrarySourceFilter.TELEGRAM -> localTracks.filter { it.isTelegramAudio }
        }
        val q = searchTfv.text.trim()
        if (q.isEmpty()) bySource
        else bySource.filter {
            KeyboardSearchMatcher.matches(it.title, q) ||
                KeyboardSearchMatcher.matches(it.artist, q) ||
                KeyboardSearchMatcher.matches(it.album, q)
        }
    }

    val controlsPanel: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .vistaWindowFrame(RoundedCornerShape(4.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = theme.accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "ФАЙЛЫ И ЗАГРУЗКИ (${filteredTracks.size})",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = theme.focusBorder,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .vistaAeroButton(
                            isPrimary = audioCaptureState.autoSplitEnabled,
                            isActive = audioCaptureState.autoSplitEnabled
                        )
                        .clickable(onClick = onToggleCaptureAutoSplit)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("library_autosplit_toggle")
                ) {
                    Text(
                        text = if (audioCaptureState.autoSplitEnabled) "✓ Авто-нарезка коллекции" else "Авто-нарезка коллекции",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = if (audioCaptureState.autoSplitEnabled) Color.White else TextSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .vistaAeroButton(
                            isPrimary = audioCaptureState.silentMuteMode,
                            isActive = audioCaptureState.silentMuteMode
                        )
                        .clickable(onClick = onToggleCaptureSilentMute)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("library_silent_mute_toggle")
                ) {
                    Text(
                        text = if (audioCaptureState.silentMuteMode) "✓ Тихий режим (без звука)" else "Тихий режим (без звука)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = if (audioCaptureState.silentMuteMode) Color.White else TextSecondary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .vistaInsetBox(isFocused = searchTfv.text.isNotEmpty())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = theme.accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                BasicTextField(
                    value = searchTfv,
                    onValueChange = { searchTfv = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                    cursorBrush = SolidColor(theme.accent),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 28.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchTfv.text.isEmpty()) {
                                Text("Поиск по скачанным и локальным файлам…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("library_search_input")
                )
                if (searchTfv.text.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Очистить",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(15.dp)
                            .clickable { searchTfv = TextFieldValue("") }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                LibrarySourceFilter.entries.forEach { f ->
                    val sel = filter == f
                    Box(
                        modifier = Modifier
                            .vistaAeroButton(isPrimary = sel, isActive = sel)
                            .clickable { filter = f }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                            .testTag("library_filter_${f.name.lowercase()}")
                    ) {
                        Text(
                            text = f.title,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (sel) Color.White else TextSecondary
                        )
                    }
                }
            }
        }
    }

    val tracksPane: @Composable (Modifier) -> Unit = { paneMod ->
        if (filteredTracks.isEmpty()) {
            Box(
                modifier = paneMod
                    .fillMaxWidth()
                    .vistaInsetBox(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchTfv.text.isBlank()) "Скачанные и локальные аудиофайлы пока не найдены" else "Файлы по запросу не найдены",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        } else {
            LazyColumn(
                modifier = paneMod
                    .fillMaxWidth()
                    .testTag("poweramp_library_list"),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                itemsIndexed(filteredTracks, key = { _, t -> "lib_${t.id}" }) { idx, track ->
                    val isPlayingThis = track.id == mediaState.currentLocalTrackId
                    val folderHint = track.displayPath.ifBlank {
                        track.filePath?.substringBeforeLast('/')?.substringAfterLast('/').orEmpty()
                    }
                    val sub = buildString {
                        append(track.artist.ifBlank { "Локально" })
                        if (track.album.isNotBlank()) append(" • ${track.album}")
                        if (folderHint.isNotBlank()) append(" • $folderHint")
                    }
                    RetroGlossyTrackRow(
                        index = idx,
                        title = track.title,
                        subtitle = sub,
                        badgeText = track.formatLabel.ifBlank { "AUDIO" },
                        badgeLossless = track.isLossless,
                        durationText = if (track.durationMs > 0L) MediaControlRepository.formatTimeMs(track.durationMs) else null,
                        isActive = isPlayingThis,
                        isPlaying = isPlayingThis && active?.isPlaying == true,
                        onClick = { onPlayTrack(track) },
                        trailingSlot = {
                            Box(
                                modifier = Modifier
                                    .vistaAeroButton()
                                    .clickable { renameTarget = track }
                                    .padding(horizontal = 6.dp, vertical = 5.dp)
                                    .testTag("track_rename_btn_${track.id}")
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Переименовать", tint = MusicSourceAmber, modifier = Modifier.size(14.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .vistaAeroButton()
                                    .clickable { onOpenLyricsForTrack(track) }
                                    .padding(horizontal = 6.dp, vertical = 5.dp)
                                    .testTag("track_lyrics_btn_${track.id}")
                            ) {
                                Icon(Icons.Rounded.Lyrics, contentDescription = "Текст", tint = theme.accent, modifier = Modifier.size(14.dp))
                            }
                            if (smartAlbums.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .vistaAeroButton()
                                        .clickable { attachTarget = track }
                                        .padding(horizontal = 6.dp, vertical = 5.dp)
                                        .testTag("track_album_btn_${track.id}")
                                ) {
                                    Icon(Icons.Rounded.Album, contentDescription = "В альбом", tint = CallIncomingGreen, modifier = Modifier.size(14.dp))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .vistaAeroButton()
                                    .clickable { deleteTarget = track }
                                    .padding(horizontal = 6.dp, vertical = 5.dp)
                                    .testTag("library_delete_btn_${track.id}")
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Удалить", tint = CallMissedRed, modifier = Modifier.size(14.dp))
                            }
                        }
                    )
                }
            }
        }
    }

    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .testTag("poweramp_library_tab"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.36f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                controlsPanel()
            }
            tracksPane(
                Modifier
                    .weight(0.64f)
                    .fillMaxHeight()
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .testTag("poweramp_library_tab"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            controlsPanel()
            tracksPane(Modifier.weight(1f))
        }
    }

    renameTarget?.let { target ->
        RenameLocalTrackDialog(
            track = target,
            onDismiss = { renameTarget = null },
            onConfirm = { newTitle ->
                onRenameTrack(target, newTitle)
                renameTarget = null
            }
        )
    }

    deleteTarget?.let { target ->
        val themeRef = LocalVistaTheme.current
        val maxDialogH = (LocalConfiguration.current.screenHeightDp.dp * 0.90f).coerceAtLeast(240.dp)
        Dialog(
            onDismissRequest = { deleteTarget = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 460.dp)
                    .heightIn(max = maxDialogH)
                    .vistaWindowFrame(RoundedCornerShape(5.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .symbianMetallicBar()
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text("УДАЛИТЬ ТРЕК С УСТРОЙСТВА?", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = themeRef.focusBorder)
                }
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp)
                ) {
                    Text("Файл «${target.title}» будет удалён с устройства.", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        VistaButton(text = "Отмена", onClick = { deleteTarget = null })
                        Spacer(Modifier.width(8.dp))
                        VistaButton(text = "Удалить", isPrimary = true, onClick = {
                            onDeleteTrack(target)
                            deleteTarget = null
                        })
                    }
                }
            }
        }
    }

    attachTarget?.let { target ->
        val themeRef = LocalVistaTheme.current
        val maxDialogH = (LocalConfiguration.current.screenHeightDp.dp * 0.90f).coerceAtLeast(240.dp)
        Dialog(
            onDismissRequest = { attachTarget = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 500.dp)
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
                    Text("ДОБАВИТЬ В АЛЬБОМ", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = themeRef.focusBorder)
                    VistaButton(text = "✕", onClick = { attachTarget = null })
                }
                LazyColumn(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    itemsIndexed(smartAlbums, key = { _, alb -> alb.id }) { idx, alb ->
                        RetroGlossyTrackRow(
                            index = idx,
                            title = alb.title,
                            subtitle = alb.artist.ifBlank { "${alb.matchedCount} треков" },
                            badgeText = "${alb.matchedCount}/${alb.totalExpected.coerceAtLeast(1)}",
                            onClick = {
                                onAttachTrackToAlbum(alb.id, listOf(target.id))
                                attachTarget = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RenameLocalTrackDialog(
    track: LocalAudioTrack,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val theme = LocalVistaTheme.current
    val maxDialogH = (LocalConfiguration.current.screenHeightDp.dp * 0.90f).coerceAtLeast(240.dp)
    var tfv by remember(track.id) {
        mutableStateOf(TextFieldValue(track.title, TextRange(track.title.length)))
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 480.dp)
                .heightIn(max = maxDialogH)
                .vistaWindowFrame(RoundedCornerShape(5.dp))
                .testTag("track_rename_dialog")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    text = "ПЕРЕИМЕНОВАТЬ ТРЕК",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = theme.focusBorder
                )
            }
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = track.filePath?.substringAfterLast('/') ?: track.title,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox(isFocused = true)
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    BasicTextField(
                        value = tfv,
                        onValueChange = { tfv = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                        cursorBrush = SolidColor(theme.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("track_rename_input")
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    VistaButton(text = "Отмена", onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    VistaButton(
                        text = "Сохранить",
                        isPrimary = true,
                        onClick = {
                            val clean = tfv.text.trim()
                            if (clean.isNotEmpty()) onConfirm(clean)
                        },
                        modifier = Modifier.testTag("track_rename_save_btn")
                    )
                }
            }
        }
    }
}
