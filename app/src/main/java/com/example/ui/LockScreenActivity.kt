package com.example.ui

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.R
import com.example.data.MediaControlRepository
import com.example.data.NotificationHubRepository
import com.example.data.ShizukuBridge
import com.example.model.HubNotificationItem
import com.example.model.HubSubTab
import com.example.model.MediaPlayerState
import com.example.model.ShizukuState
import com.example.ui.components.AlbumArtThumb
import com.example.ui.theme.CallIncomingGreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FocusBorderCyan
import com.example.ui.theme.KeyDeckTheme
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavyDivider
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.SymbianBevelHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.symbianGlossyTile
import com.example.ui.theme.symbianMetallicBar
import com.example.ui.theme.vistaAeroButton
import com.example.ui.theme.vistaWindowFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LockScreenActivity : ComponentActivity() {

    private lateinit var mediaRepo: MediaControlRepository
    private lateinit var shizukuBridge: ShizukuBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        mediaRepo = MediaControlRepository(application)
        shizukuBridge = ShizukuBridge(application)
        mediaRepo.startObserving()
        shizukuBridge.startObserving()

        setContent {
            val mediaState by mediaRepo.mediaState.collectAsStateWithLifecycle()
            val notifications by NotificationHubRepository.notifications.collectAsStateWithLifecycle()
            val shizukuState by shizukuBridge.statusState.collectAsStateWithLifecycle()

            KeyDeckTheme {
                SymbianLockScreen(
                    mediaState = mediaState,
                    notifications = notifications,
                    shizukuState = shizukuState,
                    onTogglePlayPause = { mediaRepo.togglePlayPause() },
                    onSkipPrevious = { mediaRepo.skipPrevious() },
                    onSkipNext = { mediaRepo.skipNext() },
                    onUnlock = { unlockScreen() }
                )
            }
        }
    }

    override fun onDestroy() {
        mediaRepo.stopObserving()
        shizukuBridge.stopObserving()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_DPAD_CENTER -> {
                    unlockScreen()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    mediaRepo.togglePlayPause()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    mediaRepo.skipNext()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    mediaRepo.skipPrevious()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun unlockScreen() {
        val state = shizukuBridge.statusState.value
        if (state.isBinderAlive && state.isPermissionGranted) {
            lifecycleScope.launch {
                shizukuBridge.dismissSystemKeyguardViaShizuku()
                finish()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (km != null && km.isKeyguardLocked) {
                km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        finish()
                    }

                    override fun onDismissCancelled() {
                        finish()
                    }

                    override fun onDismissError() {
                        finish()
                    }
                })
            } else {
                finish()
            }
        } else {
            finish()
        }
    }
}

@Composable
private fun SymbianLockScreen(
    mediaState: MediaPlayerState,
    notifications: List<HubNotificationItem>,
    shizukuState: ShizukuState,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onUnlock: () -> Unit
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val timeStr = remember(nowMs) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nowMs))
    }
    val secStr = remember(nowMs) {
        SimpleDateFormat(":ss", Locale.getDefault()).format(Date(nowMs))
    }
    val dateStr = remember(nowMs) {
        SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date(nowMs))
            .replaceFirstChar { it.uppercaseChar() }
    }

    val chatCount = notifications.count { it.subTab == HubSubTab.CHATS }
    val newsCount = notifications.count { it.subTab == HubSubTab.NEWS }
    val otherCount = notifications.count { it.subTab == HubSubTab.OTHER }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Symbian Lock Status Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .symbianMetallicBar()
                    .border(1.dp, NavyDivider, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_symbian_lock),
                        contentDescription = "Блокировка",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "KEYDECK SYMBIAN LOCK",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_symbian_shizuku),
                        contentDescription = "Shizuku",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (shizukuState.isBinderAlive && shizukuState.isPermissionGranted) {
                            "Shizuku Активен"
                        } else {
                            "Автономный режим"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (shizukuState.isBinderAlive && shizukuState.isPermissionGranted) {
                            CallIncomingGreen
                        } else {
                            TextSecondary
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Clock & Date
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = TextPrimary
                )
                Text(
                    text = secStr,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    color = ElectricCyan,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(
                text = dateStr,
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )

            Spacer(Modifier.height(14.dp))

            // Music Player Tile
            val active = mediaState.activeSession
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianGlossyTile(bucket = 0, isFocused = false)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArtThumb(artwork = active?.artwork, size = 44.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = active?.title ?: "Музыка на паузе",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = active?.artist?.ifBlank { active.appLabel } ?: "Плеер не активен",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(38.dp)
                        .vistaAeroButton()
                ) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Назад", tint = TextPrimary)
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(40.dp)
                        .vistaAeroButton(isPrimary = true)
                ) {
                    Icon(
                        imageVector = if (active?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (active?.isPlaying == true) "Пауза" else "Играть",
                        tint = TextPrimary
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(38.dp)
                        .vistaAeroButton()
                ) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Вперёд", tint = TextPrimary)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Hub Counters Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LockCounterChip(
                    label = "Чаты",
                    count = chatCount,
                    iconRes = R.drawable.ic_symbian_messaging,
                    modifier = Modifier.weight(1f)
                )
                LockCounterChip(
                    label = "Новости",
                    count = newsCount,
                    iconRes = R.drawable.ic_symbian_web_news,
                    modifier = Modifier.weight(1f)
                )
                LockCounterChip(
                    label = "Прочее",
                    count = otherCount,
                    iconRes = R.drawable.ic_symbian_tools,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            // Recent Notification Previews
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (notifications.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .vistaWindowFrame()
                                .padding(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Нет непрочитанных уведомлений в Хабе",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        }
                    }
                } else {
                    items(notifications.take(6), key = { it.key }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .vistaWindowFrame()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item.appIcon?.let { iconBmp ->
                                Image(
                                    bitmap = iconBmp,
                                    contentDescription = item.appName,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${item.appName} • ${item.title}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = ElectricCyan,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = item.timeFormatted,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }
                                Text(
                                    text = item.bigText.ifBlank { item.text },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = NavyDivider)
            Spacer(Modifier.height(10.dp))

            // Unlock Footer
            Text(
                text = "Нажмите ПРОБЕЛ или ENTER для разблокировки",
                style = MaterialTheme.typography.labelSmall,
                color = MusicSourceAmber
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .vistaAeroButton(isPrimary = true)
                    .clickable(onClick = onUnlock)
                    .testTag("lock_unlock_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LockOpen,
                    contentDescription = null,
                    tint = FocusBorderCyan
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "РАЗБЛОКИРОВАТЬ",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun LockCounterChip(
    label: String,
    count: Int,
    iconRes: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .vistaWindowFrame()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$label: $count",
            style = MaterialTheme.typography.labelMedium,
            color = if (count > 0) ElectricCyan else TextSecondary,
            maxLines = 1
        )
    }
}
