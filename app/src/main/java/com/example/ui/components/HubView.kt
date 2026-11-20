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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.KeyboardSearchMatcher
import com.example.data.LauncherPreferencesRepository
import com.example.model.ConsensusWeatherReport
import com.example.model.HubNotificationItem
import com.example.model.HubQuickLink
import com.example.model.HubSubTab
import com.example.model.ShizukuState
import com.example.model.WeatherCityResult
import com.example.ui.theme.LocalVistaTheme
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.symbianGlossyTile
import com.example.ui.theme.symbianMetallicBar
import com.example.ui.theme.vistaAeroButton
import com.example.ui.theme.vistaInsetBox
import com.example.ui.theme.vistaWindowFrame

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
    onToggleOemGestureBypass: () -> Unit = {},
    onPreviewLockScreen: () -> Unit,
    onOpenAppearanceSettings: () -> Unit,
    onOpenAutoKillSettings: () -> Unit,
    onOpenOptimizationSettings: () -> Unit = {},
    onOpenLinuxCatalog: () -> Unit = {},
    onGrantOverlayAndAccessibilityViaShizuku: () -> Unit = {},
    onOpenDefaultLauncherSettings: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenAllFilesAccess: () -> Unit,
    onOpenMusicSheet: () -> Unit,
    onOpenShortcuts: () -> Unit,
    onOpenSystemSettings: (String) -> Unit,
    showWeatherInHubOther: Boolean = true,
    weatherReport: ConsensusWeatherReport? = null,
    selectedWeatherCity: WeatherCityResult = LauncherPreferencesRepository.DEFAULT_WEATHER_CITY,
    isWeatherLoading: Boolean = false,
    onRefreshWeather: () -> Unit = {},
    onOpenWeatherForecast: () -> Unit = {},
    onOpenWeatherCityPicker: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    val lang = com.example.ui.LocalAppLang.current
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
                    .padding(horizontal = 5.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val chatsLbl = com.example.ui.L10n.hubSub(lang, HubSubTab.CHATS)
                val newsLbl = com.example.ui.L10n.hubSub(lang, HubSubTab.NEWS)
                val otherLbl = com.example.ui.L10n.hubSub(lang, HubSubTab.OTHER)
                val settingsLbl = com.example.ui.L10n.hubSub(lang, HubSubTab.SETTINGS)

                HubTabItem(
                    selected = subTab == HubSubTab.CHATS,
                    iconRes = R.drawable.ic_symbian_messaging,
                    label = if (chatCount > 0) "$chatsLbl ($chatCount)" else chatsLbl,
                    onClick = { onSelectSubTab(HubSubTab.CHATS) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hub_subtab_chats")
                )
                HubTabItem(
                    selected = subTab == HubSubTab.NEWS,
                    iconRes = R.drawable.ic_symbian_web_news,
                    label = if (newsCount > 0) "$newsLbl ($newsCount)" else newsLbl,
                    onClick = { onSelectSubTab(HubSubTab.NEWS) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hub_subtab_news")
                )
                HubTabItem(
                    selected = subTab == HubSubTab.OTHER,
                    iconRes = R.drawable.ic_symbian_tools,
                    label = if (otherCount > 0) "$otherLbl ($otherCount)" else otherLbl,
                    onClick = { onSelectSubTab(HubSubTab.OTHER) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hub_subtab_other")
                )
                HubTabItem(
                    selected = subTab == HubSubTab.SETTINGS,
                    iconRes = R.drawable.ic_symbian_settings,
                    label = settingsLbl,
                    onClick = { onSelectSubTab(HubSubTab.SETTINGS) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hub_subtab_settings")
                )
            }
            HorizontalDivider(color = theme.chromeBorderOuter, thickness = 1.dp)
        }

        // Hub status & refresh bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surface.copy(alpha = 0.92f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = when (subTab) {
                    HubSubTab.CHATS -> "BLACKBERRY ХАБ • TG/WA → ЧАТЫ → ПОЧТА/VK"
                    HubSubTab.NEWS -> "ЛЕНТА НОВОСТЕЙ И GOOGLE DISCOVER"
                    HubSubTab.OTHER -> "ПРОЧИЕ И СИСТЕМНЫЕ УВЕДОМЛЕНИЯ"
                    HubSubTab.SETTINGS -> "НАСТРОЙКИ KEYDECK, SHIZUKU, ROOT И ТЕМЫ"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = theme.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (subTab == HubSubTab.NEWS) {
                    Box(
                        modifier = Modifier
                            .vistaAeroButton(isPrimary = true)
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
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .vistaAeroButton()
                        .clickable(onClick = onRefreshNotifications),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Обновить уведомления",
                        tint = theme.accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        HorizontalDivider(color = theme.divider)

        // Banner if Android Notification Listener access hasn't been granted yet
        if (!shizukuState.isNotificationListenerGranted && subTab != HubSubTab.SETTINGS) {
            NotificationPermissionBanner(
                shizukuReady = shizukuState.isInstalled || shizukuState.isBinderAlive || shizukuState.isRootAvailable,
                shizukuGranted = shizukuState.isPermissionGranted || shizukuState.isRootAvailable,
                onOpenSettings = onOpenNotificationAccess,
                onGrantViaShizuku = {
                    if (shizukuState.isPermissionGranted || shizukuState.isRootAvailable) onGrantNotificationViaShizuku()
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
                        subtitle = "Уведомления из Telegram, WhatsApp и мессенджеров отображаются выше почты и VK — можно отвечать прямо из Хаба."
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
                        subtitle = "Уведомления из Google Discover, Google Новостей, Habr и браузеров автоматически собираются в эту вкладку."
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
                if (filteredNotifications.isEmpty() && !showWeatherInHubOther) {
                    HubEmptyNotificationsState(
                        iconRes = R.drawable.ic_symbian_tools,
                        title = "Прочих уведомлений пока нет",
                        subtitle = "Системные уведомления, напоминания, загрузки и сервисные события отображаются здесь. Настройки вынесены во вкладку «Настройки»."
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("hub_other_notifications_list")
                    ) {
                        if (showWeatherInHubOther) {
                            item(key = "hub_other_weather_card") {
                                WeatherWidgetCard(
                                    report = weatherReport,
                                    selectedCity = selectedWeatherCity,
                                    isLoading = isWeatherLoading,
                                    compact = false,
                                    onRefresh = onRefreshWeather,
                                    onOpenForecast = onOpenWeatherForecast,
                                    onOpenCityPicker = onOpenWeatherCityPicker,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        if (filteredNotifications.isEmpty()) {
                            item(key = "hub_other_empty_hint") {
                                Text(
                                    text = "Прочих уведомлений пока нет",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
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
                    }
                }
            }

            HubSubTab.SETTINGS -> {
                var settingsQueryTfv by remember { mutableStateOf(TextFieldValue("")) }
                val settingsQuery = settingsQueryTfv.text
                val actions = remember(
                    onOpenAppearanceSettings,
                    onOpenOptimizationSettings,
                    onOpenLinuxCatalog,
                    onOpenAutoKillSettings,
                    onOpenDefaultLauncherSettings,
                    onOpenNotificationAccess,
                    onOpenMusicSheet,
                    onOpenShortcuts,
                    onOpenSystemSettings,
                    onOpenAllFilesAccess
                ) {
                    listOf(
                        HubOtherAction("Оформление, палитры, язык и масштаб", R.drawable.ic_symbian_gallery, onOpenAppearanceSettings),
                        HubOtherAction("Оптимизация Android, жесты и Фон YouTube", R.drawable.ic_symbian_tools, onOpenOptimizationSettings),
                        HubOtherAction("Пакеты Linux (.deb / AppImage / Termux)", R.drawable.ic_symbian_tools, onOpenLinuxCatalog),
                        HubOtherAction("Одноразовые приложения (автозакрытие)", R.drawable.ic_symbian_tools, onOpenAutoKillSettings),
                        HubOtherAction("Лаунчер по умолчанию", R.drawable.ic_symbian_home, onOpenDefaultLauncherSettings),
                        HubOtherAction("Доступ к уведомлениям", R.drawable.ic_symbian_music, onOpenNotificationAccess),
                        HubOtherAction("Панель музыки (F5 / Ctrl+M)", R.drawable.ic_symbian_music, onOpenMusicSheet),
                        HubOtherAction("Горячие клавиши (F1–F6, Ctrl+R)", R.drawable.ic_symbian_keyboard, onOpenShortcuts),
                        HubOtherAction("Wi-Fi", R.drawable.ic_symbian_wifi) { onOpenSystemSettings(Settings.ACTION_WIFI_SETTINGS) },
                        HubOtherAction("Bluetooth", R.drawable.ic_symbian_bluetooth) { onOpenSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS) },
                        HubOtherAction("Настройки Android", R.drawable.ic_symbian_settings) { onOpenSystemSettings(Settings.ACTION_SETTINGS) },
                        HubOtherAction("Доступ ко всем файлам (поиск)", R.drawable.ic_symbian_files, onOpenAllFilesAccess)
                    )
                }
                val filteredActions = remember(actions, settingsQuery) {
                    if (settingsQuery.isBlank()) actions
                    else actions.filter { KeyboardSearchMatcher.matches(it.title, settingsQuery) }
                }
                val showShizukuCard = remember(settingsQuery) {
                    settingsQuery.isBlank() ||
                        listOf("shizuku", "шизуку", "root", "рут", "жест", "штор", "блок", "перехват", "доступ", "gesture", "lock")
                            .any { settingsQuery.contains(it, ignoreCase = true) || KeyboardSearchMatcher.matches(it, settingsQuery) }
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("hub_settings_list")
                ) {
                    item(key = "settings_search_bar") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .vistaInsetBox(isFocused = settingsQuery.isNotEmpty())
                                .padding(horizontal = 9.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Поиск в настройках",
                                tint = theme.accent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (settingsQuery.isEmpty()) {
                                    Text(
                                        text = "Поиск по настройкам (жесты, тема, Linux, Shizuku)…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = settingsQueryTfv,
                                    onValueChange = { settingsQueryTfv = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                                    cursorBrush = SolidColor(theme.accent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("hub_settings_search_input")
                                )
                            }
                            if (settingsQuery.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Очистить поиск настроек",
                                    tint = TextSecondary,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clickable { settingsQueryTfv = TextFieldValue("", TextRange.Zero) }
                                )
                            }
                        }
                    }

                    if (showWeatherInHubOther && (settingsQuery.isBlank() || KeyboardSearchMatcher.matches("погода weather", settingsQuery))) {
                        item(key = "hub_weather_card") {
                            WeatherWidgetCard(
                                report = weatherReport,
                                selectedCity = selectedWeatherCity,
                                isLoading = isWeatherLoading,
                                compact = false,
                                onRefresh = onRefreshWeather,
                                onOpenForecast = onOpenWeatherForecast,
                                onOpenCityPicker = onOpenWeatherCityPicker,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (showShizukuCard) {
                        item(key = "shizuku_panel") {
                            ShizukuControlPanelCard(
                                shizukuState = shizukuState,
                                onRequestShizukuPermission = onRequestShizukuPermission,
                                onOpenShizukuApp = onOpenShizukuApp,
                                onGrantNotificationAccessViaShizuku = onGrantNotificationViaShizuku,
                                onGrantOverlayAndAccessibilityViaShizuku = onGrantOverlayAndAccessibilityViaShizuku,
                                onOpenOptimizationSettings = onOpenOptimizationSettings,
                                onOpenLinuxCatalog = onOpenLinuxCatalog,
                                onToggleShadeInterception = onToggleShadeInterception,
                                onToggleLockScreenInterception = onToggleLockScreenInterception,
                                onToggleOemGestureBypass = onToggleOemGestureBypass,
                                onPreviewLockScreen = onPreviewLockScreen
                            )
                        }
                    }

                    if (filteredActions.isNotEmpty()) {
                        item(key = "system_tools_header") {
                            Text(
                                text = "ИНСТРУМЕНТЫ И НАСТРОЙКИ KEYDECK",
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.accent,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        }

                        itemsIndexed(filteredActions, key = { _, item -> "settings_tool_${item.title}" }) { idx, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .symbianGlossyTile(bucket = idx, isFocused = idx == focusedIndex)
                                    .clickable(onClick = item.onClick)
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                                    .testTag("hub_setting_item_$idx"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .vistaInsetBox(),
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
                    } else if (!showShizukuCard) {
                        item(key = "settings_empty") {
                            Text(
                                text = "По запросу «$settingsQuery» разделы настроек не найдены",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted,
                                modifier = Modifier.padding(16.dp)
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
    val theme = LocalVistaTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .vistaWindowFrame(RoundedCornerShape(4.dp))
            .padding(10.dp)
            .testTag("hub_notification_permission_banner")
    ) {
        Text(
            text = "Включите доступ к уведомлениям для BlackBerry Хаба",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = theme.focusBorder
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "После включения сюда будут приходить сообщения из мессенджеров (с быстрым ответом и прикреплением файлов), новости и прочие уведомления.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VistaButton(
                text = "Открыть настройки",
                isPrimary = true,
                onClick = onOpenSettings
            )
            if (shizukuReady) {
                VistaButton(
                    text = if (shizukuGranted) "Выдать через Shizuku" else "Подключить Shizuku",
                    onClick = onGrantViaShizuku
                )
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
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .vistaAeroButton(isPrimary = selected, isActive = selected)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 4.dp),
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
            color = if (selected) Color.White else TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AddHubLinkDialog(
    initialCategory: HubSubTab,
    onDismiss: () -> Unit,
    onConfirm: (title: String, url: String, category: HubSubTab) -> Unit
) {
    val theme = LocalVistaTheme.current
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val category = if (initialCategory == HubSubTab.OTHER) HubSubTab.NEWS else initialCategory

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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Добавить источник (${category.title})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = theme.focusBorder
                )
            }

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Название", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox()
                        .padding(horizontal = 9.dp, vertical = 8.dp)
                ) {
                    if (title.isEmpty()) {
                        Text("Например, Habr", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                        cursorBrush = SolidColor(theme.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("URL", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox()
                        .padding(horizontal = 9.dp, vertical = 8.dp)
                ) {
                    if (url.isEmpty()) {
                        Text("habr.com", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    BasicTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                        cursorBrush = SolidColor(theme.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    VistaButton(text = "Отмена", onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    VistaButton(
                        text = "Сохранить",
                        isPrimary = true,
                        enabled = title.isNotBlank() && url.isNotBlank(),
                        onClick = { onConfirm(title, url, category) }
                    )
                }
            }
        }
    }
}
