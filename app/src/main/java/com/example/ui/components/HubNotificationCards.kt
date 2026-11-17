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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val theme = LocalVistaTheme.current
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
            .vistaWindowFrame(RoundedCornerShape(4.dp))
            .then(
                if (isFocused) Modifier.border(1.5.dp, theme.focusBorder, RoundedCornerShape(4.dp))
                else Modifier
            )
    ) {
        // Windows Vista / Symbian metallic title bar header for each card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .symbianMetallicBar()
                .clickable(onClick = onOpen)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item.appIcon?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = item.appName,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.width(8.dp))
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
                        color = theme.accent,
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
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .vistaAeroButton()
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Скрыть уведомление",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            // Body / Chat messages stack
            if (item.messages.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox()
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
                        .vistaAeroButton(isActive = true)
                        .clickable { onOpenUrl(item.extractedUrl) }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = "Открыть статью",
                        tint = theme.accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Открыть статью",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
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
                                .vistaAeroButton()
                                .clickable { onInvokeAction(act.index) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = act.title,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = theme.accent
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
                            .clip(RoundedCornerShape(3.dp))
                            .background(theme.activeButtonLowerMid.copy(alpha = 0.65f))
                            .border(1.dp, theme.accent.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Файл: ${attachedName ?: "Вложение"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.accent,
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
                            .vistaAeroButton()
                            .clickable { attachFileLauncher.launch(arrayOf("*/*")) }
                            .testTag("hub_attach_file_${item.key}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_symbian_attach),
                            contentDescription = "Прикрепить файл",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .vistaInsetBox()
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
                            cursorBrush = SolidColor(theme.accent),
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
                            .vistaAeroButton(isPrimary = canSend)
                            .clickable(enabled = canSend) { sendCurrentReply() }
                            .testTag("hub_reply_send_${item.key}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Отправить ответ",
                            tint = if (canSend) Color.White else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
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
    val theme = LocalVistaTheme.current
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
            .vistaWindowFrame(RoundedCornerShape(4.dp))
            .testTag("shizuku_control_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .symbianMetallicBar()
                .padding(horizontal = 10.dp, vertical = 8.dp),
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
                        color = theme.focusBorder
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VistaButton(
                    text = if (shizukuState.isBinderAlive && !shizukuState.isPermissionGranted) {
                        "Разрешить"
                    } else if (shizukuState.isPermissionGranted) {
                        "Проверить"
                    } else {
                        "Запустить"
                    },
                    isPrimary = true,
                    onClick = {
                        if (shizukuState.isBinderAlive) onRequestShizukuPermission()
                        else onOpenShizukuApp()
                    },
                    modifier = Modifier.testTag("shizuku_connect_button")
                )
                if (shizukuState.isPermissionGranted && !shizukuState.isNotificationListenerGranted) {
                    VistaButton(
                        text = "Доступ",
                        onClick = onGrantNotificationAccessViaShizuku,
                        modifier = Modifier.testTag("shizuku_grant_listener_button")
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Vista Switch 1: Intercept notification shade
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .vistaInsetBox()
                    .clickable(onClick = onToggleShadeInterception)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
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
                Spacer(Modifier.width(8.dp))
                VistaSwitch(
                    checked = shizukuState.interceptShadeEnabled,
                    onCheckedChange = { onToggleShadeInterception() },
                    modifier = Modifier.testTag("vista_switch_shade_intercept")
                )
            }

            // Vista Switch 2: Intercept lock screen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .vistaInsetBox()
                    .clickable(onClick = onToggleLockScreenInterception)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
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
                        .vistaAeroButton()
                        .clickable(onClick = onPreviewLockScreen)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Тест", style = MaterialTheme.typography.labelSmall, color = theme.accent)
                }
                Spacer(Modifier.width(6.dp))
                VistaSwitch(
                    checked = shizukuState.interceptLockScreenEnabled,
                    onCheckedChange = { onToggleLockScreenInterception() },
                    modifier = Modifier.testTag("vista_switch_lock_intercept")
                )
            }
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
