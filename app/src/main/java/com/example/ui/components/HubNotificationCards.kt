package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.HubNotificationItem
import com.example.model.HubSubTab
import com.example.model.ShizukuState
import com.example.ui.theme.CallIncomingGreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FocusBorderCyan
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.NavyDivider
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.SymbianBevelHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TileBrightAzure
import com.example.ui.theme.TileDeepCobalt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HubNotificationCard(
    item: HubNotificationItem,
    isFocused: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onInvokeAction: (Int) -> Unit,
    onOpenUrl: (String) -> Unit,
    onSendReply: (text: String, attachmentUri: Uri?, mimeType: String?) -> Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var replyText by remember(item.key) { mutableStateOf("") }
    var attachedUri by remember(item.key) { mutableStateOf<Uri?>(null) }
    var attachedName by remember(item.key) { mutableStateOf<String?>(null) }
    var attachedMime by remember(item.key) { mutableStateOf<String?>(null) }

    val attachFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            attachedUri = uri
            attachedName = resolveDisplayName(context, uri)
            attachedMime = context.contentResolver.getType(uri) ?: "*/*"
        }
    }

    fun sendCurrentReply() {
        val sent = onSendReply(replyText, attachedUri, attachedMime)
        if (sent) {
            replyText = ""
            attachedUri = null
            attachedName = null
            attachedMime = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(NavySurfaceElevated)
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) FocusBorderCyan else NavyDivider,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(11.dp)
    ) {
        // Header row: App icon, App name, Sender/Headline, Time, Dismiss
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item.appIcon?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = item.appName,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(9.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.appName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
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
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!item.isOngoing) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Скрыть уведомление",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Body / Chat messages stack
        if (item.messages.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(NavySurface.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                item.messages.forEach { msg ->
                    Text(
                        text = "${msg.sender}: ${msg.text}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = TextPrimary
                    )
                }
            }
        } else {
            val body = item.bigText.ifBlank { item.text }
            if (body.isNotBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = TextSecondary,
                    maxLines = if (item.subTab == HubSubTab.NEWS) 5 else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // URL chip for News or notifications containing article URLs
        if (item.extractedUrl != null) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(TileDeepCobalt)
                    .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .clickable { onOpenUrl(item.extractedUrl) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = "Открыть статью",
                    tint = ElectricCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "Открыть статью",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary
                )
            }
        }

        // Extra notification actions (Mark Read, Like, Archive, etc.)
        if (item.actions.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item.actions.forEach { act ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(NavySurface)
                            .border(1.dp, NavyDivider, RoundedCornerShape(3.dp))
                            .clickable { onInvokeAction(act.index) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = act.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                }
            }
        }

        // BlackBerry Hub Direct Reply + File Attachment bar for CHATS
        if (item.subTab == HubSubTab.CHATS) {
            Spacer(Modifier.height(8.dp))

            if (attachedUri != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(TileDeepCobalt.copy(alpha = 0.65f))
                        .border(1.dp, ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Файл: ${attachedName ?: "Вложение"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            attachedUri = null
                            attachedName = null
                            attachedMime = null
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Убрать файл",
                            tint = TextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Attach file button (0-permission SAF OpenDocument)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(NavySurface)
                        .border(1.dp, SymbianBevelHighlight.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .clickable { attachFileLauncher.launch(arrayOf("*/*")) }
                        .testTag("hub_attach_file_${item.key}"),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_symbian_attach),
                        contentDescription = "Прикрепить файл",
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(NavySurface)
                        .border(1.dp, NavyDivider, RoundedCornerShape(4.dp))
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                ) {
                    if (replyText.isEmpty()) {
                        Text(
                            text = if (item.canReplyInline) "Ответить в ${item.appName}…" else "Текст + файл в ${item.appName}…",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                        cursorBrush = SolidColor(ElectricCyan),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendCurrentReply() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hub_reply_input_${item.key}")
                    )
                }

                Spacer(Modifier.width(6.dp))

                val canSend = replyText.isNotBlank() || attachedUri != null
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (canSend) TileBrightAzure else NavySurface)
                        .border(1.dp, SymbianBevelHighlight.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                        .clickable(enabled = canSend) { sendCurrentReply() }
                        .testTag("hub_reply_send_${item.key}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Отправить ответ",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ShizukuControlPanelCard(
    shizukuState: ShizukuState,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onGrantNotificationAccessViaShizuku: () -> Unit,
    onToggleShadeInterception: () -> Unit,
    onToggleLockScreenInterception: () -> Unit,
    onPreviewLockScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusText = when {
        shizukuState.isBinderAlive && shizukuState.isPermissionGranted -> "Активен и разрешён"
        shizukuState.isBinderAlive -> "Сервис запущен (нужно разрешение)"
        shizukuState.isInstalled -> "Установлен, сервис не запущен"
        else -> "Приложение Shizuku не установлено"
    }
    val statusColor = when {
        shizukuState.isBinderAlive && shizukuState.isPermissionGranted -> CallIncomingGreen
        shizukuState.isBinderAlive -> MusicSourceAmber
        else -> TextMuted
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(NavySurfaceElevated)
            .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(12.dp)
            .testTag("shizuku_control_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_symbian_shizuku),
                    contentDescription = "Shizuku",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "SHIZUKU & ПЕРЕХВАТ",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = ElectricCyan
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(TileBrightAzure)
                        .clickable {
                            if (shizukuState.isBinderAlive) onRequestShizukuPermission()
                            else onOpenShizukuApp()
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("shizuku_connect_button")
                ) {
                    Text(
                        text = if (shizukuState.isBinderAlive && !shizukuState.isPermissionGranted) {
                            "Разрешить"
                        } else if (shizukuState.isPermissionGranted) {
                            "Проверить"
                        } else {
                            "Запустить"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                }
                if (shizukuState.isPermissionGranted && !shizukuState.isNotificationListenerGranted) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(TileDeepCobalt)
                            .border(1.dp, ElectricCyan, RoundedCornerShape(4.dp))
                            .clickable(onClick = onGrantNotificationAccessViaShizuku)
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("shizuku_grant_listener_button")
                    ) {
                        Text(
                            text = "Доступ к увед.",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Switch 1: Intercept notification shade
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(NavySurface)
                .clickable(onClick = onToggleShadeInterception)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Перехват шторки уведомлений",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary
                )
                Text(
                    text = "Свайп вниз и Ctrl+N сворачивают шторку и открывают Хаб",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Switch(
                checked = shizukuState.interceptShadeEnabled,
                onCheckedChange = { onToggleShadeInterception() },
                colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan, checkedTrackColor = TileBrightAzure)
            )
        }

        Spacer(Modifier.height(6.dp))

        // Switch 2: Intercept lock screen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(NavySurface)
                .clickable(onClick = onToggleLockScreenInterception)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Symbian-экран блокировки",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary
                )
                Text(
                    text = "Часы, плеер, счётчики Хаба и разблокировка через Shizuku",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(NavySurfaceElevated)
                    .border(1.dp, NavyDivider, RoundedCornerShape(4.dp))
                    .clickable(onClick = onPreviewLockScreen)
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            ) {
                Text("Тест", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
            }
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = shizukuState.interceptLockScreenEnabled,
                onCheckedChange = { onToggleLockScreenInterception() },
                colors = SwitchDefaults.colors(checkedThumbColor = ElectricCyan, checkedTrackColor = TileBrightAzure)
            )
        }
    }
}

private fun resolveDisplayName(context: Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "Файл"
}
