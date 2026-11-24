package com.example.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import com.example.data.AudioFileMetadataReadResult
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TrackContextMenuDialog(
    track: LocalAudioTrack,
    albumContext: SmartAlbum? = null,
    smartAlbums: List<SmartAlbum> = emptyList(),
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onOpenLyrics: () -> Unit,
    onEditMetadata: () -> Unit,
    onAttachToAlbum: (String) -> Unit = {},
    onDetachFromAlbum: (String) -> Unit = {},
    onDeleteTrack: () -> Unit
) {
    val theme = LocalVistaTheme.current
    val maxDialogH = (LocalConfiguration.current.screenHeightDp.dp * 0.90f).coerceAtLeast(240.dp)
    var confirmDelete by remember(track.id) { mutableStateOf(false) }
    var showAlbumPicker by remember(track.id) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 460.dp)
                .heightIn(max = maxDialogH)
                .vistaWindowFrame(RoundedCornerShape(5.dp))
                .testTag("track_context_menu_dialog")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .vistaInsetBox(shape = RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = if (track.isLossless) CallIncomingGreen else theme.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = listOf(
                        track.artist.ifBlank { "Неизвестный" },
                        track.album.takeIf { it.isNotBlank() },
                        track.formatLabel.takeIf { it.isNotBlank() }
                    ).filterNotNull().joinToString(" • ")
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (track.displayPath.isNotBlank()) {
                        Text(
                            text = track.displayPath,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontFamily = FontFamily.Monospace),
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .vistaAeroButton()
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = TextSecondary, modifier = Modifier.size(14.dp))
                }
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (confirmDelete) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .vistaInsetBox()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Удалить аудиофайл с устройства?",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = CallMissedRed
                        )
                        Text(
                            text = "Файл «${track.title}» будет удалён без возможности восстановления и отвязан от альбомов.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            VistaButton(text = "Отмена", onClick = { confirmDelete = false })
                            Spacer(Modifier.width(8.dp))
                            VistaButton(
                                text = "Удалить навсегда",
                                isPrimary = true,
                                onClick = {
                                    onDismiss()
                                    onDeleteTrack()
                                },
                                modifier = Modifier.testTag("track_ctx_confirm_delete_btn")
                            )
                        }
                    }
                } else {
                    TrackMenuActionRow(
                        icon = Icons.Rounded.PlayArrow,
                        label = "Воспроизвести",
                        tint = CallIncomingGreen,
                        onClick = {
                            onDismiss()
                            onPlay()
                        },
                        modifier = Modifier.testTag("track_ctx_play_btn")
                    )
                    TrackMenuActionRow(
                        icon = Icons.Rounded.Lyrics,
                        label = "Текст / Синхро-лирика",
                        tint = theme.accent,
                        onClick = {
                            onDismiss()
                            onOpenLyrics()
                        },
                        modifier = Modifier.testTag("track_ctx_lyrics_btn")
                    )
                    TrackMenuActionRow(
                        icon = Icons.Rounded.Edit,
                        label = "Свойства файла (теги и обложка)",
                        tint = MusicSourceAmber,
                        onClick = {
                            onDismiss()
                            onEditMetadata()
                        },
                        modifier = Modifier.testTag("track_ctx_edit_metadata_btn")
                    )

                    if (albumContext != null) {
                        TrackMenuActionRow(
                            icon = Icons.Rounded.RemoveCircleOutline,
                            label = "Убрать из альбома «${albumContext.title}»",
                            tint = MusicSourceAmber,
                            onClick = {
                                onDismiss()
                                onDetachFromAlbum(albumContext.id)
                            },
                            modifier = Modifier.testTag("track_ctx_detach_album_btn")
                        )
                    } else if (smartAlbums.isNotEmpty()) {
                        TrackMenuActionRow(
                            icon = Icons.Rounded.Album,
                            label = if (showAlbumPicker) "Скрыть список альбомов" else "Добавить в альбом",
                            tint = CallIncomingGreen,
                            onClick = { showAlbumPicker = !showAlbumPicker },
                            modifier = Modifier.testTag("track_ctx_attach_album_btn")
                        )
                        if (showAlbumPicker) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .vistaInsetBox()
                                    .padding(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                smartAlbums.forEach { alb ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .vistaAeroButton(shape = RoundedCornerShape(3.dp))
                                            .clickable {
                                                onDismiss()
                                                onAttachToAlbum(alb.id)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SmartAlbumCoverBox(
                                            customPath = alb.customCoverPath,
                                            coverUrl = alb.coverUrl,
                                            size = 24.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = listOf(alb.title, alb.artist).filter { it.isNotBlank() }.joinToString(" — "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TrackMenuActionRow(
                        icon = Icons.Rounded.DeleteOutline,
                        label = "Удалить трек с устройства",
                        tint = CallMissedRed,
                        onClick = { confirmDelete = true },
                        modifier = Modifier.testTag("track_ctx_delete_btn")
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackMenuActionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .vistaAeroButton(shape = RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EditAudioMetadataDialog(
    track: LocalAudioTrack,
    loadedMetadata: AudioFileMetadataReadResult? = null,
    existingCoverOverridePath: String? = null,
    pendingCoverUri: Uri? = null,
    onPickCoverImage: () -> Unit,
    onClearPendingCover: () -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (title: String, artist: String, album: String, clearEmbeddedCover: Boolean) -> Unit
) {
    val theme = LocalVistaTheme.current
    val context = LocalContext.current
    val maxDialogH = (LocalConfiguration.current.screenHeightDp.dp * 0.92f).coerceAtLeast(260.dp)

    val initialTitle = loadedMetadata?.title?.takeIf { it.isNotBlank() } ?: track.title
    val initialArtist = loadedMetadata?.artist?.takeIf { it.isNotBlank() } ?: track.artist
    val initialAlbum = loadedMetadata?.album?.takeIf { it.isNotBlank() } ?: track.album

    var titleTfv by remember(track.id) {
        mutableStateOf(TextFieldValue(initialTitle, TextRange(initialTitle.length)))
    }
    var artistTfv by remember(track.id) {
        mutableStateOf(TextFieldValue(initialArtist, TextRange(initialArtist.length)))
    }
    var albumTfv by remember(track.id) {
        mutableStateOf(TextFieldValue(initialAlbum, TextRange(initialAlbum.length)))
    }
    var touchedTitle by remember(track.id) { mutableStateOf(false) }
    var touchedArtist by remember(track.id) { mutableStateOf(false) }
    var touchedAlbum by remember(track.id) { mutableStateOf(false) }
    var clearEmbeddedCover by remember(track.id) { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(loadedMetadata, track.id) {
        if (loadedMetadata != null) {
            if (!touchedTitle && loadedMetadata.title.isNotBlank() && titleTfv.text == track.title) {
                titleTfv = TextFieldValue(loadedMetadata.title, TextRange(loadedMetadata.title.length))
            }
            if (!touchedArtist && loadedMetadata.artist.isNotBlank() && artistTfv.text == track.artist) {
                artistTfv = TextFieldValue(loadedMetadata.artist, TextRange(loadedMetadata.artist.length))
            }
            if (!touchedAlbum && loadedMetadata.album.isNotBlank() && albumTfv.text == track.album) {
                albumTfv = TextFieldValue(loadedMetadata.album, TextRange(loadedMetadata.album.length))
            }
        }
    }

    val pickedPreviewBitmap by produceState<ImageBitmap?>(initialValue = null, pendingCoverUri) {
        value = if (pendingCoverUri == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(pendingCoverUri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }

    val embeddedBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        loadedMetadata?.embeddedCoverBytes,
        clearEmbeddedCover
    ) {
        val bytes = loadedMetadata?.embeddedCoverBytes
        value = if (clearEmbeddedCover || bytes == null || bytes.isEmpty()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 520.dp)
                .heightIn(max = maxDialogH)
                .vistaWindowFrame(RoundedCornerShape(5.dp))
                .testTag("edit_audio_metadata_dialog")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "СВОЙСТВА И ТЕГИ АУДИОФАЙЛА",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = theme.focusBorder,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(
                            track.formatLabel.takeIf { it.isNotBlank() },
                            track.filePath?.substringAfterLast('/') ?: track.displayPath.takeIf { it.isNotBlank() }
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 9.5.sp),
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .vistaAeroButton()
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = TextSecondary, modifier = Modifier.size(14.dp))
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Embedded Cover Editor Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF10151D))
                            .border(1.dp, theme.chromeBorderOuter, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            pickedPreviewBitmap != null -> {
                                Image(
                                    bitmap = pickedPreviewBitmap!!,
                                    contentDescription = "Выбранная обложка",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            embeddedBitmap != null -> {
                                Image(
                                    bitmap = embeddedBitmap!!,
                                    contentDescription = "Обложка в файле",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            !clearEmbeddedCover && !existingCoverOverridePath.isNullOrBlank() -> {
                                SmartAlbumCoverBox(
                                    customPath = existingCoverOverridePath,
                                    coverUrl = null,
                                    size = 64.dp
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = "Нет обложки",
                                    tint = TextMuted,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        val coverStatus = when {
                            pendingCoverUri != null -> "Новая обложка будет вшита в файл"
                            clearEmbeddedCover -> "Обложка будет удалена из файла"
                            loadedMetadata?.hasEmbeddedCover == true -> "В файле найдена встроенная обложка"
                            !existingCoverOverridePath.isNullOrBlank() -> "Привязана локальная обложка"
                            else -> "Обложка в файле отсутствует"
                        }
                        Text(
                            text = coverStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (pendingCoverUri != null) CallIncomingGreen else TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .vistaAeroButton(isPrimary = true)
                                    .clickable {
                                        clearEmbeddedCover = false
                                        onPickCoverImage()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                    .testTag("track_meta_pick_cover_btn")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Выбрать обложку в файл",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                        color = Color.White
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .vistaAeroButton()
                                    .clickable {
                                        onClearPendingCover()
                                        clearEmbeddedCover = true
                                    }
                                    .padding(horizontal = 7.dp, vertical = 5.dp)
                                    .testTag("track_meta_clear_cover_btn")
                            ) {
                                Text(
                                    text = "Удалить",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                    color = CallMissedRed
                                )
                            }
                        }
                    }
                }

                MetadataLabeledInput(
                    label = "Название трека",
                    value = titleTfv,
                    onValueChange = {
                        touchedTitle = true
                        titleTfv = it
                    },
                    placeholder = "Например: Осколки",
                    fieldTag = "track_meta_title_input"
                )
                MetadataLabeledInput(
                    label = "Исполнитель",
                    value = artistTfv,
                    onValueChange = {
                        touchedArtist = true
                        artistTfv = it
                    },
                    placeholder = "Например: АукцЫон",
                    fieldTag = "track_meta_artist_input"
                )
                MetadataLabeledInput(
                    label = "Альбом",
                    value = albumTfv,
                    onValueChange = {
                        touchedAlbum = true
                        albumTfv = it
                    },
                    placeholder = "Например: Это мама",
                    fieldTag = "track_meta_album_input"
                )

                Text(
                    text = "Теги (Название, Исполнитель, Альбом) и выбранная обложка записываются непосредственно в аудиофайл (.mp3, .flac, .m4a, .ogg, .wav) и синхронизируются с MediaStore и альбомами.",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextMuted
                )
            }

            HorizontalDivider(color = theme.chromeBorderOuter.copy(alpha = 0.6f), thickness = 1.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VistaButton(text = "Отмена", onClick = onDismiss)
                Spacer(Modifier.width(8.dp))
                VistaButton(
                    text = "Сохранить в файл",
                    isPrimary = true,
                    onClick = {
                        val cleanTitle = titleTfv.text.trim().ifBlank { track.title }
                        onSave(
                            cleanTitle,
                            artistTfv.text.trim(),
                            albumTfv.text.trim(),
                            clearEmbeddedCover
                        )
                    },
                    modifier = Modifier.testTag("track_meta_save_btn")
                )
            }
        }
    }
}

@Composable
private fun MetadataLabeledInput(
    label: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    fieldTag: String
) {
    val theme = LocalVistaTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = TextSecondary
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .vistaInsetBox(isFocused = value.text.isNotEmpty())
                .padding(horizontal = 9.dp, vertical = 7.dp)
        ) {
            if (value.text.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                cursorBrush = SolidColor(theme.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(fieldTag)
            )
        }
    }
}
