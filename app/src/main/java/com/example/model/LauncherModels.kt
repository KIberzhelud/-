package com.example.model

import androidx.compose.ui.graphics.ImageBitmap

enum class MainTab(val title: String, val shortcutHint: String) {
    HOME("Главная", "Ctrl+1"),
    CALLS("Звонки", "Ctrl+2"),
    HUB("Хаб", "Ctrl+3")
}

enum class CallsSubTab(val title: String) {
    LOG("Журнал"),
    CONTACTS("Контакты")
}

enum class HubSubTab(val title: String) {
    CHATS("Чаты"),
    NEWS("Новости"),
    OTHER("Прочее")
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
    val canSeek: Boolean = true
)

data class MediaPlayerState(
    val sessions: List<MediaSessionUi> = emptyList(),
    val selectedSessionId: String? = null,
    val isListenerGranted: Boolean = false,
    val systemVolume: Int = 0,
    val maxVolume: Int = 15,
    val isMuted: Boolean = false
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

data class ShizukuState(
    val isInstalled: Boolean = false,
    val isBinderAlive: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val isNotificationListenerGranted: Boolean = false,
    val interceptShadeEnabled: Boolean = false,
    val interceptLockScreenEnabled: Boolean = false,
    val lastCommandStatus: String? = null
)

