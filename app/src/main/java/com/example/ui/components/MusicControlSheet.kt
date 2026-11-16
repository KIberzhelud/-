package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.MediaControlRepository
import com.example.model.AppEntry
import com.example.model.MediaPlayerState
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.NavyDivider
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TileBrightAzure
import com.example.ui.theme.TileDeepCobalt

@Composable
fun MusicControlDialog(
    mediaState: MediaPlayerState,
    livePositionMs: Long,
    musicApps: List<AppEntry>,
    onDismiss: () -> Unit,
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
    onLaunchMusicApp: (AppEntry) -> Unit
) {
    val active = mediaState.activeSession

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(1.dp, NavyDivider, RoundedCornerShape(10.dp)),
            shape = RoundedCornerShape(10.dp),
            color = NavySurface
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_symbian_music),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "МУЗЫКА И ПЛЕЕРЫ",
                            style = MaterialTheme.typography.titleMedium,
                            color = ElectricCyan
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Закрыть панель музыки",
                            tint = TextSecondary
                        )
                    }
                }

                if (!mediaState.isListenerGranted) {
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(TileDeepCobalt.copy(alpha = 0.45f))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = MusicSourceAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Включите доступ к медиа-сессиям",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Разрешение на чтение уведомлений позволит постоянно видеть треки, обложки и переключаться между активными плеерами.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onOpenNotificationAccess,
                            colors = ButtonDefaults.buttonColors(containerColor = TileBrightAzure),
                            modifier = Modifier.testTag("grant_media_listener_button")
                        ) {
                            Text("Открыть настройки доступа")
                        }
                    }
                }

                if (mediaState.sessions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Активные плееры (${mediaState.sessions.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        mediaState.sessions.forEach { session ->
                            val selected = session.id == active?.id
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectSession(session.id) },
                                label = {
                                    Text(
                                        text = if (session.isPlaying) "▶ ${session.appLabel}" else session.appLabel,
                                        maxLines = 1
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TileBrightAzure,
                                    selectedLabelColor = TextPrimary,
                                    containerColor = NavySurfaceElevated,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArtThumb(artwork = active?.artwork, size = 76.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = active?.title ?: "Нет активного воспроизведения",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (active != null && active.artist.isNotEmpty()) {
                            Text(
                                text = active.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (active != null) {
                            Text(
                                text = buildString {
                                    append(active.appLabel)
                                    if (active.album.isNotEmpty()) append(" • ${active.album}")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MusicSourceAmber,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (active != null && active.durationMs > 0L) {
                    Spacer(Modifier.height(12.dp))
                    var isDragging by remember { mutableStateOf(false) }
                    var sliderPos by remember(active.id) { mutableFloatStateOf(0f) }
                    val displayPos = if (isDragging) {
                        sliderPos.toLong()
                    } else {
                        livePositionMs
                    }
                    Slider(
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
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricCyan,
                            activeTrackColor = ElectricCyan,
                            inactiveTrackColor = NavyDivider
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = MediaControlRepository.formatTimeMs(displayPos),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = MediaControlRepository.formatTimeMs(active.durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onSeekBy(-10_000L) }, enabled = active?.canSeek == true) {
                        Icon(
                            imageVector = Icons.Rounded.Replay10,
                            contentDescription = "Назад 10 секунд",
                            tint = if (active?.canSeek == true) TextPrimary else TextSecondary.copy(alpha = 0.4f)
                        )
                    }
                    IconButton(onClick = onSkipPrevious) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Предыдущий трек",
                            tint = TextPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Surface(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onTogglePlayPause),
                        color = TileBrightAzure
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (active?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (active?.isPlaying == true) "Пауза" else "Воспроизвести",
                                tint = TextPrimary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    IconButton(onClick = onSkipNext) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Следующий трек",
                            tint = TextPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    IconButton(onClick = { onSeekBy(10_000L) }, enabled = active?.canSeek == true) {
                        Icon(
                            imageVector = Icons.Rounded.Forward10,
                            contentDescription = "Вперёд 10 секунд",
                            tint = if (active?.canSeek == true) TextPrimary else TextSecondary.copy(alpha = 0.4f)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onToggleMute) {
                        Icon(
                            imageVector = if (mediaState.isMuted) {
                                Icons.AutoMirrored.Rounded.VolumeOff
                            } else if (mediaState.systemVolume < mediaState.maxVolume / 2) {
                                Icons.AutoMirrored.Rounded.VolumeDown
                            } else {
                                Icons.AutoMirrored.Rounded.VolumeUp
                            },
                            contentDescription = "Звук",
                            tint = ElectricCyan
                        )
                    }
                    Slider(
                        value = mediaState.systemVolume.toFloat(),
                        onValueChange = { onSetVolume(it.toInt()) },
                        valueRange = 0f..mediaState.maxVolume.toFloat(),
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricCyan,
                            activeTrackColor = ElectricCyan,
                            inactiveTrackColor = NavyDivider
                        )
                    )
                    Text(
                        text = "${mediaState.systemVolume}/${mediaState.maxVolume}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                if (active != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onOpenActivePlayer()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Открыть ${active.appLabel}")
                    }
                }

                if (musicApps.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = NavyDivider)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Установленные плееры",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        musicApps.forEach { app ->
                            Column(
                                modifier = Modifier
                                    .width(68.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NavySurfaceElevated)
                                    .clickable {
                                        onLaunchMusicApp(app)
                                        onDismiss()
                                    }
                                    .padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                app.icon?.let { bmp ->
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = app.label,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
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
    }
}
