package com.example.model

import androidx.compose.ui.graphics.ImageBitmap

enum class MainTab(val title: String, val shortcutHint: String) {
    HOME("Главная", "F1"),
    CALLS("Звонки", "F2"),
    HUB("Хаб", "F3")
}

enum class CallsSubTab(val title: String) {
    LOG("Журнал"),
    CONTACTS("Контакты")
}

enum class HubSubTab(val title: String) {
    CHATS("Чаты"),
    NEWS("Новости"),
    OTHER("Прочее"),
    SETTINGS("Настройки")
}

enum class AlphabetMode(val label: String) {
    LATIN("A-Z"),
    CYRILLIC("А-Я")
}

enum class HubCategory {
    CHAT,
    NEWS,
    MUSIC,
    UTILITY
}

enum class AppIconMode {
    AUTO,
    ORIGINAL,
    SYMBIAN,
    CUSTOM
}

enum class UiThemeColor(val title: String) {
    BLUE("Синий (Vista Blue)"),
    GRAY("Серый (Graphite)")
}

data class WallpaperPaletteData(
    val dominantDarkArgb: Long,
    val accentArgb: Long,
    val highlightArgb: Long
)

enum class AppLanguage(val code: String, val title: String) {
    RU("ru", "Русский"),
    EN("en", "English")
}

enum class PlayerCoverStyle(val title: String) {
    COVER("Обложка"),
    DISC("Крутящийся диск")
}

enum class GlobalSearchMode {
    APPS_AND_FILES,
    MESSENGER_SEARCH
}

enum class SearchCategoryGroup {
    APPS,
    IMAGES,
    FILES,
    MUSIC
}

enum class MusicDrawerTab(val title: String) {
    STREAMING("Стриминг и ТГ"),
    LOCAL("Файлы")
}

data class RecentAppFrame(
    val packageName: String,
    val activityName: String?,
    val appLabel: String,
    val icon: ImageBitmap? = null,
    val lastActiveMs: Long,
    val previewTitle: String,
    val previewSubtitle: String,
    val badgeText: String = "",
    val isPinned: Boolean = false,
    val isPlayingMedia: Boolean = false
)

data class MusicStreamMatch(
    val id: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val subtitle: String,
    val isLocalTrack: Boolean = false,
    val localTrack: LocalAudioTrack? = null,
    val queueItemId: Long? = null,
    val mediaId: String? = null,
    val isServiceSearchTrigger: Boolean = false
)

data class AppIconOverride(
    val mode: AppIconMode,
    val symbianKey: String? = null,
    val customFilePath: String? = null
)

data class AppEntry(
    val id: String,
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: ImageBitmap? = null,
    val initialChar: Char,
    val isPinned: Boolean = false,
    val hubCategory: HubCategory = HubCategory.UTILITY,
    val colorBucket: Int = 0,
    val isSymbianIcon: Boolean = false,
    val originalLabel: String = label,
    val isRenamed: Boolean = false,
    val iconMode: AppIconMode = AppIconMode.AUTO,
    val androidIcon: ImageBitmap? = icon,
    val iconOverride: AppIconOverride? = null
)

data class MediaQueueTrack(
    val queueId: Long,
    val title: String,
    val subtitle: String,
    val mediaId: String? = null,
    val packageName: String = "",
    val appLabel: String = "",
    val isActive: Boolean = false
)

data class MediaCustomActionItem(
    val action: String,
    val name: String
)

data class MediaBrowserTrack(
    val mediaId: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val subtitle: String,
    val isPlayable: Boolean = true,
    val isBrowsable: Boolean = false
)

data class MediaSessionUi(
    val id: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String,
    val album: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val playbackSpeed: Float,
    val lastUpdateElapsedMs: Long,
    val artwork: ImageBitmap? = null,
    val canSkipNext: Boolean = true,
    val canSkipPrev: Boolean = true,
    val canSeek: Boolean = true,
    val canPlayFromSearch: Boolean = false,
    val activeQueueItemId: Long = -1L,
    val queue: List<MediaQueueTrack> = emptyList(),
    val customActions: List<MediaCustomActionItem> = emptyList()
)

data class LocalAudioTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uriString: String,
    val filePath: String? = null,
    val displayPath: String = "",
    val isTelegramAudio: Boolean = false
)

data class MediaPlayerState(
    val sessions: List<MediaSessionUi> = emptyList(),
    val selectedSessionId: String? = null,
    val isListenerGranted: Boolean = false,
    val systemVolume: Int = 0,
    val maxVolume: Int = 15,
    val isMuted: Boolean = false,
    val currentLocalTrackId: String? = null,
    val recentTracks: List<MediaQueueTrack> = emptyList(),
    val browsableTracks: List<MediaBrowserTrack> = emptyList(),
    val browserSearchResults: List<MediaBrowserTrack> = emptyList(),
    val isSearchingStream: Boolean = false,
    val connectedBrowserPackage: String? = null
) {
    val activeSession: MediaSessionUi?
        get() = sessions.firstOrNull { it.id == selectedSessionId } ?: sessions.firstOrNull()
}

enum class CallDirection(val label: String) {
    INCOMING("Входящий"),
    OUTGOING("Исходящий"),
    MISSED("Пропущенный"),
    REJECTED("Отклонён"),
    OTHER("Вызов")
}

data class CallLogEntry(
    val id: Long,
    val displayName: String,
    val number: String,
    val direction: CallDirection,
    val timeFormatted: String,
    val timestamp: Long
)

data class ContactEntry(
    val id: Long,
    val name: String,
    val number: String,
    val isStarred: Boolean,
    val initialChar: Char
)

data class HubQuickLink(
    val id: String,
    val title: String,
    val url: String,
    val category: HubSubTab
)

data class HubChatMessage(
    val sender: String,
    val text: String,
    val timestamp: Long
)

data class HubActionSummary(
    val index: Int,
    val title: String
)

data class HubNotificationItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val bigText: String = "",
    val conversationTitle: String = "",
    val postTime: Long,
    val timeFormatted: String,
    val subTab: HubSubTab,
    val canReplyInline: Boolean = false,
    val supportsMediaReply: Boolean = false,
    val messages: List<HubChatMessage> = emptyList(),
    val actions: List<HubActionSummary> = emptyList(),
    val extractedUrl: String? = null,
    val isOngoing: Boolean = false,
    val appIcon: ImageBitmap? = null
)

enum class AppAdaptationMode(val title: String, val shortTitle: String) {
    DISABLED("Без адаптации", "Выкл"),
    STANDARD("Стандартная перерисовка (F1–F6 + стрелки)", "Стандарт"),
    MESSENGER("Мессенджер (чаты, быстрый ввод, F1–F6)", "Мессенджер")
}

data class AdaptedTabNode(
    val index: Int,
    val label: String,
    val viewId: String? = null,
    val isSelected: Boolean = false
)

data class AdaptedItemNode(
    val index: Int,
    val title: String,
    val subtitle: String = "",
    val badge: String = "",
    val isClickable: Boolean = true,
    val isEditable: Boolean = false
)

data class AdaptedAppSnapshot(
    val packageName: String,
    val appLabel: String,
    val mode: AppAdaptationMode,
    val tabs: List<AdaptedTabNode> = emptyList(),
    val items: List<AdaptedItemNode> = emptyList(),
    val focusedItemIndex: Int = 0,
    val hasInputField: Boolean = false,
    val inputHint: String = "",
    val hasSendButton: Boolean = false,
    val overlayCollapsed: Boolean = false
)

data class AndroidOptimizationStats(
    val totalRamMb: Int = 0,
    val availableRamMb: Int = 0,
    val usedRamPercent: Int = 0,
    val phantomKillerPatched: Boolean = false,
    val lastCleanupSummary: String? = null
)

data class ShizukuState(
    val isInstalled: Boolean = false,
    val isBinderAlive: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val isRootAvailable: Boolean = false,
    val isNotificationListenerGranted: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val shizukuVersion: Int = -1,
    val interceptShadeEnabled: Boolean = false,
    val interceptLockScreenEnabled: Boolean = false,
    val oemGestureBypassEnabled: Boolean = false,
    val lastCommandStatus: String? = null
)

