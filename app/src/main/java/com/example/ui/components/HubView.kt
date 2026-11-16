package com.example.ui.components

import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.HubNotificationItem
import com.example.model.HubQuickLink
import com.example.model.HubSubTab
import com.example.model.ShizukuState
import com.example.ui.theme.ElectricCyan
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
import com.example.ui.theme.symbianGlossyTile
import com.example.ui.theme.symbianMetallicBar

@Composable
fun HubView(
    subTab: HubSubTab,
    isLandscape: Boolean,
    notifications: List<HubNotificationItem>,
    shizukuState: ShizukuState,
    quickLinks: List<HubQuickLink>,
    focusedIndex: Int,
    listState: LazyListState,
    onSelectSubTab: (HubSubTab) -> Unit,
    onReplyNotification: (key: String, text: String, attachmentUri: Uri?, mimeType: String?) -> Boolean,
    onOpenNotification: (String) -> Unit,
    onInvokeNotificationAction: (key: String, actionIndex: Int) -> Unit,
    onDismissNotification: (String) -> Unit,
    onRefreshNotifications: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onShowAddLinkDialog: () -> Unit,
    onRemoveQuickLink: (String) -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onGrantNotificationViaShizuku: () -> Unit,
    onToggleShadeInterception: () -> Unit,
    onToggleLockScreenInterception: () -> Unit,
    onPreviewLockScreen: () -> Unit,
    onOpenDefaultLauncherSettings: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenMusicSheet: () -> Unit,
    onOpenShortcuts: () -> Unit,
    onOpenSystemSettings: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val chatCount = notifications.count { it.subTab == HubSubTab.CHATS }
    val newsCount = notifications.count { it.subTab == HubSubTab.NEWS }
    val otherCount = notifications.count { it.subTab == HubSubTab.OTHER }

    val filteredNotifications = remember(notifications, subTab) {
        notifications.filter { it.subTab == subTab }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Portrait-only top sub-tabs: in Landscape they move beside/below the music player!
        if (!isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HubTabItem(
                    selected = subTab == HubSubTab.CHATS,
                    iconRes = R.drawable.ic_symbian_messaging,
                    label = if (chatCount > 0) "${HubSubTab.CHATS.title} ($chatCount)" else HubSubTab.CHATS.title,
                    onClick = { onSelectSubTab(HubSubTab.CHATS) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hub_subtab_chats")
                )
                HubTabItem(
                    selected = subTab == HubSubTab.NEWS,
                    iconRes = R.drawable.ic_symbian_web_news,
                    label = if (newsCount > 0) "${HubSubTab.NEWS.title} ($newsCount)" else HubSubTab.NEWS.title,
                    onClick = { onSelectSubTab(HubSubTab.NEWS) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hub_subtab_news")
                )
                HubTabItem(
                    selected = subTab == HubSubTab.OTHER,
                    iconRes = R.drawable.ic_symbian_tools,
                    label = if (otherCount > 0) "${HubSubTab.OTHER.title} ($otherCount)" else HubSubTab.OTHER.title,
                    onClick = { onSelectSubTab(HubSubTab.OTHER) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hub_subtab_other")
                )
            }
            HorizontalDivider(color = NavyDivider, thickness = 1.dp)
        }

        // Hub status & refresh bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NavySurface.copy(alpha = 0.85f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = when (subTab) {
                    HubSubTab.CHATS -> "BLACKBERRY ХАБ • ЧАТЫ И ПОЧТА"
                    HubSubTab.NEWS -> "ЛЕНТА НОВОСТЕЙ И СТАТЕЙ"
                    HubSubTab.OTHER -> "ПРОЧИЕ УВЕДОМЛЕНИЯ И SHIZUKU"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = ElectricCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (subTab == HubSubTab.NEWS) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(TileBrightAzure)
                            .clickable(onClick = onShowAddLinkDialog)
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                            .testTag("hub_add_link_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Источник", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                }
                IconButton(onClick = onRefreshNotifications, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Обновить уведомления",
                        tint = ElectricCyan,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
        HorizontalDivider(color = NavyDivider)

        // Banner if Android Notification Listener access hasn't been granted yet
        if (!shizukuState.isNotificationListenerGranted) {
            NotificationPermissionBanner(
                shizukuReady = shizukuState.isInstalled || shizukuState.isBinderAlive,
                shizukuGranted = shizukuState.isPermissionGranted,
                onOpenSettings = onOpenNotificationAccess,
                onGrantViaShizuku = {
                    if (shizukuState.isPermissionGranted) onGrantNotificationViaShizuku()
                    else onRequestShizukuPermission()
                }
            )
        }

        when (subTab) {
            HubSubTab.CHATS -> {
                if (filteredNotifications.isEmpty()) {
                    HubEmptyNotificationsState(
                        iconRes = R.drawable.ic_symbian_messaging,
                        title = "Входящих сообщений нет",
                        subtitle = "Уведомления из Telegram, WhatsApp, SMS и почты появятся здесь — можно отвечать и прикреплять файлы прямо из Хаба."
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("hub_chats_notifications_list")
                    ) {
                        itemsIndexed(filteredNotifications, key = { _, it -> it.key }) { idx, item ->
                            HubNotificationCard(
                                item = item,
                                isFocused = idx == focusedIndex,
                                onOpen = { onOpenNotification(item.key) },
                                onDismiss = { onDismissNotification(item.key) },
                                onInvokeAction = { actionIdx -> onInvokeNotificationAction(item.key, actionIdx) },
                                onOpenUrl = onOpenUrl,
                                onSendReply = { text, uri, mime ->
                                    onReplyNotification(item.key, text, uri, mime)
                                }
                            )
                        }
                    }
                }
            }

            HubSubTab.NEWS -> {
                val newsLinks = quickLinks.filter { it.category == HubSubTab.NEWS }
                if (filteredNotifications.isEmpty() && newsLinks.isEmpty()) {
                    HubEmptyNotificationsState(
                        iconRes = R.drawable.ic_symbian_web_news,
                        title = "Новостных уведомлений пока нет",
                        subtitle = "Уведомления из новостных лент, RSS, Reddit, Habr и браузеров автоматически собираются в эту вкладку."
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("hub_news_notifications_list")
                    ) {
                        itemsIndexed(filteredNotifications, key = { _, it -> it.key }) { idx, item ->
                            HubNotificationCard(
                                item = item,
                                isFocused = idx == focusedIndex,
                                onOpen = { onOpenNotification(item.key) },
                                onDismiss = { onDismissNotification(item.key) },
                                onInvokeAction = { actionIdx -> onInvokeNotificationAction(item.key, actionIdx) },
                                onOpenUrl = onOpenUrl,
                                onSendReply = { _, _, _ -> false }
                            )
                        }

                        if (newsLinks.isNotEmpty()) {
                            item {
                                Text(
                                    text = "СОХРАНЁННЫЕ ИСТОЧНИКИ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MusicSourceAmber,
                                    modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                                )
                            }
                            itemsIndexed(newsLinks, key = { _, link -> "news_link_${link.id}" }) { idx, link ->
                                val globalIdx = filteredNotifications.size + idx
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .symbianGlossyTile(bucket = 3, isFocused = globalIdx == focusedIndex)
                                        .clickable { onOpenUrl(link.url) }
                                        .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.ic_symbian_link),
                                        contentDescription = link.title,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(link.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1)
                                        Text(link.url, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(onClick = { onRemoveQuickLink(link.id) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Удалить", tint = TextMuted, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HubSubTab.OTHER -> {
                val actions = listOf(
                    HubOtherAction("Лаунчер по умолчанию", R.drawable.ic_symbian_home, onOpenDefaultLauncherSettings),
                    HubOtherAction("Доступ к уведомлениям", R.drawable.ic_symbian_music, onOpenNotificationAccess),
                    HubOtherAction("Панель музыки (Ctrl+M)", R.drawable.ic_symbian_music, onOpenMusicSheet),
                    HubOtherAction("Горячие клавиши (F1)", R.drawable.ic_symbian_keyboard, onOpenShortcuts),
                    HubOtherAction("Wi-Fi", R.drawable.ic_symbian_wifi) { onOpenSystemSettings(Settings.ACTION_WIFI_SETTINGS) },
                    HubOtherAction("Bluetooth", R.drawable.ic_symbian_bluetooth) { onOpenSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS) },
                    HubOtherAction("Настройки Android", R.drawable.ic_symbian_settings) { onOpenSystemSettings(Settings.ACTION_SETTINGS) }
                )

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("hub_other_notifications_list")
                ) {
                    item(key = "shizuku_panel") {
                        ShizukuControlPanelCard(
                            shizukuState = shizukuState,
                            onRequestShizukuPermission = onRequestShizukuPermission,
                            onOpenShizukuApp = onOpenShizukuApp,
                            onGrantNotificationAccessViaShizuku = onGrantNotificationViaShizuku,
                            onToggleShadeInterception = onToggleShadeInterception,
                            onToggleLockScreenInterception = onToggleLockScreenInterception,
                            onPreviewLockScreen = onPreviewLockScreen
                        )
                    }

                    if (filteredNotifications.isNotEmpty()) {
                        item(key = "other_notifications_header") {
                            Text(
                                text = "СИСТЕМНЫЕ И ПРОЧИЕ УВЕДОМЛЕНИЯ (${filteredNotifications.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MusicSourceAmber,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        }
                        itemsIndexed(filteredNotifications, key = { _, it -> it.key }) { idx, item ->
                            HubNotificationCard(
                                item = item,
                                isFocused = idx == focusedIndex,
                                onOpen = { onOpenNotification(item.key) },
                                onDismiss = { onDismissNotification(item.key) },
                                onInvokeAction = { actionIdx -> onInvokeNotificationAction(item.key, actionIdx) },
                                onOpenUrl = onOpenUrl,
                                onSendReply = { _, _, _ -> false }
                            )
                        }
                    }

                    item(key = "system_tools_header") {
                        Text(
                            text = "ИНСТРУМЕНТЫ СИСТЕМЫ",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }

                    itemsIndexed(actions, key = { idx, _ -> "other_tool_$idx" }) { idx, item ->
                        val globalIdx = filteredNotifications.size + idx
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .symbianGlossyTile(bucket = idx, isFocused = globalIdx == focusedIndex)
                                .clickable(onClick = item.onClick)
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NavySurface.copy(alpha = 0.72f))
                                    .border(1.dp, SymbianBevelHighlight.copy(alpha = 0.35f), RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(item.iconRes),
                                    contentDescription = item.title,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class HubOtherAction(
    val title: String,
    val iconRes: Int,
    val onClick: () -> Unit
)

@Composable
private fun NotificationPermissionBanner(
    shizukuReady: Boolean,
    shizukuGranted: Boolean,
    onOpenSettings: () -> Unit,
    onGrantViaShizuku: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(TileDeepCobalt.copy(alpha = 0.6f))
            .border(1.dp, ElectricCyan.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
            .padding(10.dp)
            .testTag("hub_notification_permission_banner")
    ) {
        Text(
            text = "Включите доступ к уведомлениям для BlackBerry Хаба",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "После включения сюда будут приходить сообщения из мессенджеров (с быстрым ответом и прикреплением файлов), новости и прочие уведомления.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(TileBrightAzure)
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("Открыть настройки", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
            }
            if (shizukuReady) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(NavySurfaceElevated)
                        .border(1.dp, ElectricCyan, RoundedCornerShape(4.dp))
                        .clickable(onClick = onGrantViaShizuku)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (shizukuGranted) "Выдать через Shizuku" else "Подключить Shizuku",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun HubEmptyNotificationsState(
    iconRes: Int,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(38.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HubTabItem(
    selected: Boolean,
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.padding(vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = label,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) ElectricCyan else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(2.dp)
                .background(if (selected) ElectricCyan else Color.Transparent)
        )
    }
}

@Composable
fun AddHubLinkDialog(
    initialCategory: HubSubTab,
    onDismiss: () -> Unit,
    onConfirm: (title: String, url: String, category: HubSubTab) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val category = if (initialCategory == HubSubTab.OTHER) HubSubTab.NEWS else initialCategory

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavySurfaceElevated,
        title = { Text("Добавить источник (${category.title})", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL (например, habr.com)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, url, category) },
                enabled = title.isNotBlank() && url.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = TileBrightAzure)
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = TextSecondary)
            }
        }
    )
}
