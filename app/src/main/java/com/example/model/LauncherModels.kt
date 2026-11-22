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

enum class SearchCategoryGroup {
    APPS,
    CHATS,
    IMAGES,
    FILES,
    LINUX,
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
    val isPlayingMedia: Boolean = false,
    val previewBitmap: ImageBitmap? = null
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
    val iconOverride: AppIconOverride? = null,
    val isLinuxTile: Boolean = false,
    val linuxPackageId: String? = null
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
    val customActions: List<MediaCustomActionItem> = emptyList(),
    val formatLabel: String = "",
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0,
    val artworkUrl: String? = null
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
    val isTelegramAudio: Boolean = false,
    val formatLabel: String = "",
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0
) {
    val isLossless: Boolean
        get() = formatLabel.equals("FLAC", ignoreCase = true) ||
            formatLabel.equals("WAV", ignoreCase = true) ||
            formatLabel.equals("ALAC", ignoreCase = true) ||
            formatLabel.equals("APE", ignoreCase = true) ||
            formatLabel.equals("DSD", ignoreCase = true)
}

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

data class WorkspaceCell(
    val x: Int,
    val y: Int,
    val packageName: String? = null,
    val activityName: String? = null,
    val label: String? = null
) {
    val isHome: Boolean get() = x == 0 && y == 0
    val hasAssignedApp: Boolean get() = !packageName.isNullOrBlank()
}

data class WorkspaceGridState(
    val cells: List<WorkspaceCell> = listOf(WorkspaceCell(0, 0)),
    val activeX: Int = 0,
    val activeY: Int = 0
) {
    val activeCell: WorkspaceCell
        get() = cells.firstOrNull { it.x == activeX && it.y == activeY } ?: WorkspaceCell(activeX, activeY)
    val isOnHome: Boolean get() = activeX == 0 && activeY == 0
}

data class LockedChatThread(
    val key: String,
    val packageName: String,
    val appName: String,
    val contactName: String,
    val messages: List<HubChatMessage> = emptyList(),
    val canReplyInline: Boolean = false,
    val supportsMediaReply: Boolean = false,
    val lastTimestamp: Long = 0L,
    val timeFormatted: String = "",
    val fallbackPhoneNumber: String? = null,
    val appIcon: ImageBitmap? = null
)

data class CustomColorPalette(
    val id: String,
    val name: String,
    val dominantDarkArgb: Long,
    val accentArgb: Long,
    val highlightArgb: Long,
    val isFromWallpaper: Boolean = false
)

data class WeatherSourceReading(
    val sourceName: String,
    val tempC: Double?,
    val condition: String,
    val windKmh: Double?,
    val humidityPercent: Int?
)

data class HourlyWeatherPoint(
    val timeLabel: String,
    val tempC: Int,
    val condition: String,
    val precipitationProb: Int
)

data class DailyWeatherForecast(
    val dayLabel: String,
    val minTempC: Int,
    val maxTempC: Int,
    val condition: String,
    val windMaxKmh: Int
)

data class WeatherCityResult(
    val name: String,
    val country: String = "",
    val admin: String = "",
    val latitude: Double,
    val longitude: Double
)

data class ConsensusWeatherReport(
    val cityName: String,
    val tempC: Int,
    val feelsLikeC: Int,
    val condition: String,
    val iconKey: String,
    val windKmh: Int,
    val humidityPercent: Int,
    val updatedAtMs: Long,
    val sources: List<WeatherSourceReading> = emptyList(),
    val hourly: List<HourlyWeatherPoint> = emptyList(),
    val daily: List<DailyWeatherForecast> = emptyList()
)

enum class LinuxPackageKind(val title: String) {
    DEB(".deb Пакет"),
    APPIMAGE("AppImage"),
    SHELL("Скрипт / CLI")
}

data class LinuxPackageEntry(
    val id: String,
    val filePath: String,
    val name: String,
    val packageName: String,
    val version: String,
    val architecture: String,
    val description: String,
    val sizeBytes: Long,
    val kind: LinuxPackageKind,
    val isInstalled: Boolean = false,
    val entryCommand: String = "",
    val installedDirPath: String = ""
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
    val interceptLockScreenEnabled: Boolean = false,
    val oemGestureBypassEnabled: Boolean = false,
    val lastCommandStatus: String? = null
)

