package com.example.viewmodel

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.json.JSONObject
import com.example.data.AndroidOptimizerManager
import com.example.data.AppPreviewCache
import com.example.data.AppsRepository
import com.example.data.AudioFileMetadataReadResult
import com.example.data.AudioFileMetadataRepository
import com.example.data.AudioFileMetadataUpdate
import com.example.data.AutoCloseManager
import com.example.data.BackgroundMediaGuardService
import com.example.data.CallsContactsRepository
import com.example.data.KeyboardSearchMatcher
import com.example.data.KeyDeckAccessibilityService
import com.example.data.LauncherPreferencesRepository
import com.example.data.LinuxPackagesRepository
import com.example.data.MediaControlBus
import com.example.data.MediaControlRepository
import com.example.data.MediaGuardRuntime
import com.example.data.MusicCatalogAndLyricsRepository
import com.example.data.NotificationHubRepository
import com.example.data.DownloadOutcome
import com.example.data.RecentForegroundTracker
import com.example.data.ShizukuBridge
import com.example.data.StreamAudioCaptureRuntime
import com.example.data.StreamAudioCaptureService
import com.example.data.UniversalSearchRepository
import com.example.data.WallpaperThemeHelper
import com.example.data.GitHubCloudBuildRepository
import com.example.data.WeatherRepository
import com.example.data.ZipUpdateRepository
import com.example.model.AlbumExpectedTrack
import com.example.model.AlphabetMode
import com.example.model.AndroidOptimizationStats
import com.example.model.AppEntry
import com.example.model.AppIconMode
import com.example.model.AppIconOverride
import com.example.model.AppLanguage
import com.example.model.AudioCaptureState
import com.example.model.CallLogEntry
import com.example.model.CallsSubTab
import com.example.model.ConsensusWeatherReport
import com.example.model.ContactEntry
import com.example.model.CustomColorPalette
import com.example.model.DeviceSearchFile
import com.example.model.EqualizerPreset
import com.example.model.EqualizerState
import com.example.model.GitHubBuildConfig
import com.example.model.HubCategory
import com.example.model.HubChatMessage
import com.example.model.HubNotificationItem
import com.example.model.HubQuickLink
import com.example.model.HubSubTab
import com.example.model.LinuxPackageEntry
import com.example.model.LocalAudioTrack
import com.example.model.LockedChatThread
import com.example.model.MainTab
import com.example.model.MediaBrowserTrack
import com.example.model.MediaPlayerState
import com.example.model.MusicDrawerTab
import com.example.model.MusicSourceKind
import com.example.model.MusicStreamMatch
import com.example.model.PlayerCoverStyle
import com.example.model.PowerampTab
import com.example.model.PreviewPlaybackState
import com.example.model.RecentAppFrame
import com.example.model.SearchResultKind
import com.example.model.ShizukuState
import com.example.model.SmartAlbum
import com.example.model.SourceSelectionMode
import com.example.model.TrackLyricsData
import com.example.model.TrackSourceCandidate
import com.example.model.UiThemeColor
import com.example.model.UnifiedMusicSearchResult
import com.example.model.WallpaperPaletteData
import com.example.model.WeatherCityResult
import com.example.model.WorkspaceCell
import com.example.model.WorkspaceGridState
import com.example.model.WriterSubTab
import com.example.model.ZipUpdateInspection
import com.example.ui.LockScreenActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class PendingStreamCaptureRequest(
    val title: String,
    val artist: String,
    val packageName: String,
    val appLabel: String,
    val targetAlbumId: String? = null,
    val requestId: Long = System.currentTimeMillis()
)

data class RootThemeConfig(
    val uiThemeColor: UiThemeColor = UiThemeColor.BLUE,
    val activeCustomPalette: CustomColorPalette? = null,
    val wallpaperPalette: WallpaperPaletteData? = null,
    val hasWallpaper: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.RU,
    val uiScale: Float = 1.0f
)

data class LauncherUiState(
    val activeTab: MainTab = MainTab.HOME,
    val callsSubTab: CallsSubTab = CallsSubTab.LOG,
    val hubSubTab: HubSubTab = HubSubTab.CHATS,
    val alphabetMode: AlphabetMode = AlphabetMode.LATIN,
    val allApps: List<AppEntry> = emptyList(),
    val filteredApps: List<AppEntry> = emptyList(),
    val favoriteApps: List<AppEntry> = emptyList(),
    val musicApps: List<AppEntry> = emptyList(),
    val chatApps: List<AppEntry> = emptyList(),
    val newsApps: List<AppEntry> = emptyList(),
    val appSearchQuery: String = "",
    val searchImages: List<DeviceSearchFile> = emptyList(),
    val searchFiles: List<DeviceSearchFile> = emptyList(),
    val chatSearchResults: List<LockedChatThread> = emptyList(),
    val linuxSearchResults: List<LinuxPackageEntry> = emptyList(),
    val isSearchingDevice: Boolean = false,
    val indexedFolderCount: Int = 0,
    val focusedAppIndex: Int = 0,
    val focusedCallLogIndex: Int = 0,
    val focusedContactIndex: Int = 0,
    val focusedHubIndex: Int = 0,
    val activeScrubLetter: Char? = null,
    val highlightedLetter: Char? = null,
    val isLoadingApps: Boolean = true,
    val callsSearchQuery: String = "",
    val callLogs: List<CallLogEntry> = emptyList(),
    val contacts: List<ContactEntry> = emptyList(),
    val starredContacts: List<ContactEntry> = emptyList(),
    val hasCallLogPermission: Boolean = false,
    val hasContactsPermission: Boolean = false,
    val hasCallPhonePermission: Boolean = false,
    val hasAllFilesAccess: Boolean = false,
    val hasAudioReadAccess: Boolean = false,
    val hubQuickLinks: List<HubQuickLink> = emptyList(),
    val hubNotifications: List<HubNotificationItem> = emptyList(),
    val shizukuState: ShizukuState = ShizukuState(),
    val isWriterModeOpen: Boolean = false,
    val writerInitialSubTab: WriterSubTab = WriterSubTab.BOOKS,
    val mediaState: MediaPlayerState = MediaPlayerState(),
    val livePositionMs: Long = 0L,
    val localAudioTracks: List<LocalAudioTrack> = emptyList(),
    val localTrackSearchQuery: String = "",
    val musicDrawerTab: MusicDrawerTab = MusicDrawerTab.STREAMING,
    val isPlayerCollapsed: Boolean = false,
    val showMusicSheet: Boolean = false,
    val showShortcutsDialog: Boolean = false,
    val showAddLinkDialog: Boolean = false,
    val showAppearanceDialog: Boolean = false,
    val showAutoKillDialog: Boolean = false,
    val showOptimizationDialog: Boolean = false,
    val showBackgroundWebAudioDialog: Boolean = false,
    val showLinuxCatalogDialog: Boolean = false,
    val showZipUpdateDialog: Boolean = false,
    val zipUpdateInspection: ZipUpdateInspection? = null,
    val zipUpdateBusy: Boolean = false,
    val zipUpdateStatus: String? = null,
    val githubBuildConfig: GitHubBuildConfig = GitHubBuildConfig(),
    val appVersionName: String = "1.0",
    val appVersionCode: Long = 1L,
    val uiThemeColor: UiThemeColor = UiThemeColor.BLUE,
    val wallpaperBitmap: ImageBitmap? = null,
    val wallpaperPalette: WallpaperPaletteData? = null,
    val hasWallpaper: Boolean = false,
    val savedPalettes: List<CustomColorPalette> = emptyList(),
    val activeCustomPaletteId: String? = null,
    val autoKillEnabled: Boolean = true,
    val autoKillPackages: Set<String> = emptySet(),
    val optimizationStats: AndroidOptimizationStats = AndroidOptimizationStats(),
    val backgroundSpoofEnabled: Boolean = false,
    val backgroundSpoofPackages: Set<String> = emptySet(),
    val appLanguage: AppLanguage = AppLanguage.RU,
    val uiScale: Float = 1.0f,
    val playerCoverStyle: PlayerCoverStyle = PlayerCoverStyle.COVER,
    val showRecentsOverlay: Boolean = false,
    val recentFrames: List<RecentAppFrame> = emptyList(),
    val focusedRecentIndex: Int = 0,
    val pinnedRecentPackages: Set<String> = emptySet(),
    val workspaceGrid: WorkspaceGridState = WorkspaceGridState(),
    val showWorkspaceSpace: Boolean = false,
    val workspaceActionFeedback: String? = null,
    val lockedChatThread: LockedChatThread? = null,
    val linuxPackages: List<LinuxPackageEntry> = emptyList(),
    val isTermuxInstalled: Boolean = false,
    val weatherReport: ConsensusWeatherReport? = null,
    val isWeatherLoading: Boolean = false,
    val weatherCityResults: List<WeatherCityResult> = emptyList(),
    val selectedWeatherCity: WeatherCityResult = LauncherPreferencesRepository.DEFAULT_WEATHER_CITY,
    val musicSearchResults: List<MusicStreamMatch> = emptyList(),
    val streamSearchHistory: List<String> = emptyList(),
    val showFullPowerampPlayer: Boolean = false,
    val powerampTab: PowerampTab = PowerampTab.PLAYER,
    val sourceSelectionMode: SourceSelectionMode = SourceSelectionMode.AUTO,
    val unifiedMusicQuery: String = "",
    val unifiedMusicResults: List<UnifiedMusicSearchResult> = emptyList(),
    val isSearchingMusicOnline: Boolean = false,
    val sourcePickerTarget: UnifiedMusicSearchResult? = null,
    val sourcePickerActionDownload: Boolean = false,
    val currentLyrics: TrackLyricsData? = null,
    val isFetchingLyrics: Boolean = false,
    val equalizerState: EqualizerState = EqualizerState(),
    val audioCaptureState: AudioCaptureState = AudioCaptureState(),
    val pendingStreamCaptureRequest: PendingStreamCaptureRequest? = null,
    val smartAlbums: List<SmartAlbum> = emptyList(),
    val albumSearchResults: List<SmartAlbum> = emptyList(),
    val selectedAlbumId: String? = null,
    val trackCoverOverrides: Map<String, String> = emptyMap(),
    val defaultTrackFallbackCoverPath: String? = null,
    val previewPlaybackState: PreviewPlaybackState = PreviewPlaybackState(),
    val showWeatherInHubOther: Boolean = true,
    val showWeatherInHomeTile: Boolean = false,
    val showWeatherForecastDialog: Boolean = false,
    val showWeatherCityPickerDialog: Boolean = false,
    val contextMenuApp: AppEntry? = null,
    val contextMenuFile: DeviceSearchFile? = null,
    val contextMenuTrack: LocalAudioTrack? = null,
    val contextMenuTrackAlbum: SmartAlbum? = null,
    val editingMetadataTrack: LocalAudioTrack? = null,
    val editingMetadataPreview: AudioFileMetadataReadResult? = null,
    val renameTargetApp: AppEntry? = null,
    val iconPickerTargetApp: AppEntry? = null,
    val statusToast: String? = null
) {
    val activeCustomPalette: CustomColorPalette?
        get() = activeCustomPaletteId?.let { id -> savedPalettes.firstOrNull { it.id == id } }
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = LauncherPreferencesRepository(application)
    private val appsRepo = AppsRepository(application)
    private val callsRepo = CallsContactsRepository(application)
    private val mediaRepo = MediaControlRepository(application)
    private val shizukuBridge = ShizukuBridge(application)
    private val autoCloseManager = AutoCloseManager(application, shizukuBridge)
    private val optimizerManager = AndroidOptimizerManager(application, shizukuBridge)
    private val universalSearchRepo = UniversalSearchRepository(application, shizukuBridge)
    private val linuxRepo = LinuxPackagesRepository(application, shizukuBridge)
    private val zipUpdateRepo = ZipUpdateRepository(application, shizukuBridge)
    private val githubCloudBuildRepo = GitHubCloudBuildRepository(application)
    private val installedVersionInfo = zipUpdateRepo.getCurrentVersionInfo()

    private val _allApps = MutableStateFlow<List<AppEntry>>(emptyList())
    private val _isLoadingApps = MutableStateFlow(true)
    private val _activeTab = MutableStateFlow(MainTab.HOME)
    private val _callsSubTab = MutableStateFlow(CallsSubTab.LOG)
    private val _hubSubTab = MutableStateFlow(HubSubTab.CHATS)
    private val _appSearchQuery = MutableStateFlow("")
    private val _callsSearchQuery = MutableStateFlow("")
    private val _focusedAppIndex = MutableStateFlow(0)
    private val _focusedCallLogIndex = MutableStateFlow(0)
    private val _focusedContactIndex = MutableStateFlow(0)
    private val _focusedHubIndex = MutableStateFlow(0)
    private val _activeScrubLetter = MutableStateFlow<Char?>(null)
    private val _highlightedLetter = MutableStateFlow<Char?>(null)
    private val _callLogs = MutableStateFlow<List<CallLogEntry>>(emptyList())
    private val _contacts = MutableStateFlow<List<ContactEntry>>(emptyList())
    private val _permissionsState = MutableStateFlow(Triple(false, false, false))
    private val _livePositionMs = MutableStateFlow(0L)
    val livePositionMs: StateFlow<Long> = _livePositionMs.asStateFlow()
    private val _showMusicSheet = MutableStateFlow(false)
    private val _musicDrawerTab = MutableStateFlow(MusicDrawerTab.STREAMING)
    private val _showShortcutsDialog = MutableStateFlow(false)
    private val _showAddLinkDialog = MutableStateFlow(false)
    private val _showAppearanceDialog = MutableStateFlow(false)
    private val _showAutoKillDialog = MutableStateFlow(false)
    private val _showOptimizationDialog = MutableStateFlow(false)
    private val _showBackgroundWebAudioDialog = MutableStateFlow(false)
    private val _showLinuxCatalogDialog = MutableStateFlow(false)
    private val _optimizationStats = MutableStateFlow(AndroidOptimizationStats())
    private val _wallpaperState = MutableStateFlow<Pair<ImageBitmap?, WallpaperPaletteData?>>(null to null)
    private val _contextMenuApp = MutableStateFlow<AppEntry?>(null)
    private val _contextMenuFile = MutableStateFlow<DeviceSearchFile?>(null)
    private val _renameTargetApp = MutableStateFlow<AppEntry?>(null)
    private val _iconPickerTargetApp = MutableStateFlow<AppEntry?>(null)
    private val _statusToast = MutableStateFlow<String?>(null)
    private val _isWriterModeOpen = MutableStateFlow(false)
    private val _writerInitialSubTab = MutableStateFlow(WriterSubTab.BOOKS)
    private val _deviceSearchResults = MutableStateFlow<List<DeviceSearchFile>>(emptyList())
    private val _chatSearchResults = MutableStateFlow<List<LockedChatThread>>(emptyList())
    private val _isSearchingDevice = MutableStateFlow(false)
    private val _storageAccessState = MutableStateFlow(false to false)
    private val _localTrackSearchQuery = MutableStateFlow("")
    private val _showRecentsOverlay = MutableStateFlow(false)
    private val _recentFrames = MutableStateFlow<List<RecentAppFrame>>(emptyList())
    private val _focusedRecentIndex = MutableStateFlow(0)
    private val _musicSearchResults = MutableStateFlow<List<MusicStreamMatch>>(emptyList())
    private val _lockedChatThread = MutableStateFlow<LockedChatThread?>(null)
    private val _showWorkspaceSpace = MutableStateFlow(false)
    private val _workspaceActionFeedback = MutableStateFlow<String?>(null)
    private val _linuxPackages = MutableStateFlow<List<LinuxPackageEntry>>(emptyList())
    private val _linuxSearchResults = MutableStateFlow<List<LinuxPackageEntry>>(emptyList())
    private val _isTermuxInstalled = MutableStateFlow(linuxRepo.isTermuxInstalled())
    private val _weatherReport = MutableStateFlow<ConsensusWeatherReport?>(null)
    private val _isWeatherLoading = MutableStateFlow(false)
    private val _weatherCityResults = MutableStateFlow<List<WeatherCityResult>>(emptyList())
    private val _showWeatherForecastDialog = MutableStateFlow(false)
    private val _showWeatherCityPickerDialog = MutableStateFlow(false)
    private val _showFullPowerampPlayer = MutableStateFlow(false)
    private val _powerampTab = MutableStateFlow(PowerampTab.PLAYER)
    private val _unifiedMusicQuery = MutableStateFlow("")
    private val _unifiedMusicResults = MutableStateFlow<List<UnifiedMusicSearchResult>>(emptyList())
    private val _isSearchingMusicOnline = MutableStateFlow(false)
    private val _sourcePickerTarget = MutableStateFlow<Pair<UnifiedMusicSearchResult?, Boolean>>(null to false)
    private val _currentLyrics = MutableStateFlow<TrackLyricsData?>(null)
    private val _isFetchingLyrics = MutableStateFlow(false)
    private val _pendingStreamCaptureRequest = MutableStateFlow<PendingStreamCaptureRequest?>(null)
    private val _selectedAlbumId = MutableStateFlow<String?>(null)
    private val _contextMenuTrack = MutableStateFlow<Pair<LocalAudioTrack?, SmartAlbum?>>(null to null)
    private val _editingMetadataTrack = MutableStateFlow<Pair<LocalAudioTrack?, AudioFileMetadataReadResult?>>(null to null)
    private val _captureAutoSplitEnabled = MutableStateFlow(true)
    private val _captureSilentMuteMode = MutableStateFlow(false)
    private val _activeLinuxRunnerTile = MutableStateFlow<LauncherPreferencesRepository.PersistedLinuxTile?>(null)
    val activeLinuxRunnerTile: StateFlow<LauncherPreferencesRepository.PersistedLinuxTile?> = _activeLinuxRunnerTile.asStateFlow()
    private val _linuxConsoleOutput = MutableStateFlow("")
    val linuxConsoleOutput: StateFlow<String> = _linuxConsoleOutput.asStateFlow()
    private val _showZipUpdateDialog = MutableStateFlow(false)
    private val _zipUpdateInspection = MutableStateFlow<ZipUpdateInspection?>(null)
    private val _zipUpdateBusy = MutableStateFlow(false)
    private val _zipUpdateStatus = MutableStateFlow<String?>(null)

    @Volatile
    private var wasPausedSinceResume: Boolean = false
    private var searchDebounceJob: Job? = null
    private var chatSearchJob: Job? = null
    private var deviceSearchJob: Job? = null
    private var linuxSearchJob: Job? = null
    private var weatherSearchJob: Job? = null
    private var onlineMusicSearchJob: Job? = null
    private var lyricsFetchJob: Job? = null
    private var metadataLoadJob: Job? = null
    private val albumRefreshJobs = mutableMapOf<String, Job>()
    private var lastLoadedLyricsKey: String = ""
    private var lyricsRetryAttempt: Int = 0
    private var lastRejectedLyricsRaw: String? = null
    private var musicAppRotationIndex: Int = -1
    private val workspaceMutex = Mutex()

    @Volatile
    var isLandscapeLayout: Boolean = false

    @Volatile
    var isAppSearchInputFocused: Boolean = false

    @Volatile
    var isCallsSearchInputFocused: Boolean = false

    private val _scrollAppIndexEvents = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val scrollAppIndexEvents = _scrollAppIndexEvents.asSharedFlow()

    private val _scrollCallLogIndexEvents = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val scrollCallLogIndexEvents = _scrollCallLogIndexEvents.asSharedFlow()

    private val _scrollContactIndexEvents = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val scrollContactIndexEvents = _scrollContactIndexEvents.asSharedFlow()

    private val _scrollHubIndexEvents = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val scrollHubIndexEvents = _scrollHubIndexEvents.asSharedFlow()

    private data class CustomizedAppsBundle(
        val allApps: List<AppEntry>,
        val pinnedApps: List<AppEntry>,
        val musicApps: List<AppEntry>,
        val chatApps: List<AppEntry>,
        val newsApps: List<AppEntry>
    )

    private data class AppsFilterBundle(
        val customized: CustomizedAppsBundle,
        val filteredApps: List<AppEntry>
    )

    private val customizedAppsFlow = combine(
        _allApps,
        prefsRepo.pinnedAppIds,
        prefsRepo.customAppLabels,
        prefsRepo.appIconOverrides,
        prefsRepo.installedLinuxTiles
    ) { apps, pinnedIds, customLabels, iconOverrides, linuxTiles ->
        val linuxTileApps = linuxTiles.map { t ->
            val firstCh = t.name.trim().firstOrNull()?.uppercaseChar() ?: '#'
            val normCh = when {
                firstCh == 'Ё' -> 'Е'
                firstCh in 'A'..'Z' || firstCh in 'А'..'Я' -> firstCh
                else -> '#'
            }
            AppEntry(
                id = "linux_tile:${t.id}",
                label = t.name,
                packageName = "linux.${t.packageName}",
                activityName = t.packageName,
                icon = null,
                initialChar = normCh,
                originalLabel = t.name,
                isLinuxTile = true,
                linuxPackageId = t.id
            )
        }
        val updatedApps = (apps + linuxTileApps)
            .map { app ->
                appsRepo.applyCustomization(
                    app = app,
                    customLabel = customLabels[app.id],
                    override = iconOverrides[app.id],
                    isPinned = pinnedIds.contains(app.id)
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        CustomizedAppsBundle(
            allApps = updatedApps,
            pinnedApps = updatedApps.filter { it.isPinned },
            musicApps = updatedApps.filter { it.hubCategory == HubCategory.MUSIC },
            chatApps = updatedApps.filter { it.hubCategory == HubCategory.CHAT },
            newsApps = updatedApps.filter { it.hubCategory == HubCategory.NEWS }
        )
    }.flowOn(Dispatchers.Default)

    private val baseAppsFlow = combine(
        customizedAppsFlow,
        _appSearchQuery,
        _highlightedLetter
    ) { customized, query, letterFilter ->
        val updatedApps = customized.allApps
        val filtered = when {
            query.isNotBlank() -> updatedApps
                .filter {
                    KeyboardSearchMatcher.matches(it.label, query) ||
                        (it.originalLabel != it.label && KeyboardSearchMatcher.matches(it.originalLabel, query)) ||
                        KeyboardSearchMatcher.matches(it.packageName, query)
                }
                .sortedByDescending {
                    val primaryScore = KeyboardSearchMatcher.matchScore(it.label, query)
                    if (primaryScore >= 100 || it.originalLabel == it.label) primaryScore
                    else maxOf(primaryScore, KeyboardSearchMatcher.matchScore(it.originalLabel, query))
                }
            letterFilter != null -> updatedApps.filter {
                KeyboardSearchMatcher.matchesInitialWithTranslit(it.label, it.initialChar, letterFilter)
            }
            else -> updatedApps
        }
        AppsFilterBundle(customized, filtered)
    }.flowOn(Dispatchers.Default)

    private val callsFlow = combine(
        _callLogs,
        _contacts,
        _callsSearchQuery,
        _permissionsState,
        _highlightedLetter
    ) { logs, contacts, query, perms, letterFilter ->
        val trimmed = query.trim()
        val filteredLogs = if (trimmed.isEmpty()) {
            logs
        } else {
            logs.filter {
                KeyboardSearchMatcher.matches(it.displayName, trimmed) ||
                    it.number.contains(trimmed, ignoreCase = true)
            }
        }
        val filteredContacts = when {
            trimmed.isNotEmpty() -> contacts.filter {
                KeyboardSearchMatcher.matches(it.name, trimmed) ||
                    it.number.contains(trimmed, ignoreCase = true)
            }
            letterFilter != null -> contacts.filter {
                KeyboardSearchMatcher.matchesInitialWithTranslit(it.name, it.initialChar, letterFilter)
            }
            else -> contacts
        }
        val starred = contacts.filter { it.isStarred }.distinctBy { it.id }
        CallsBundle(filteredLogs, filteredContacts, starred, perms)
    }.flowOn(Dispatchers.Default)

    private data class CallsBundle(
        val logs: List<CallLogEntry>,
        val contacts: List<ContactEntry>,
        val starred: List<ContactEntry>,
        val perms: Triple<Boolean, Boolean, Boolean>
    )

    private val navFlow = combine(
        _activeTab,
        _callsSubTab,
        _hubSubTab,
        prefsRepo.alphabetMode,
        _appSearchQuery
    ) { tab, callsSub, hubSub, alphaMode, appQuery ->
        NavBundle(tab, callsSub, hubSub, alphaMode, appQuery)
    }

    private data class NavBundle(
        val activeTab: MainTab,
        val callsSubTab: CallsSubTab,
        val hubSubTab: HubSubTab,
        val alphabetMode: AlphabetMode,
        val appSearchQuery: String
    )

    private val uiOverlaysFlow = combine(
        combine(_focusedAppIndex, _focusedCallLogIndex, _focusedContactIndex, _focusedHubIndex) { a, c, co, h ->
            intArrayOf(a, c, co, h)
        },
        _activeScrubLetter,
        _highlightedLetter,
        _showMusicSheet,
        _showShortcutsDialog
    ) { focusArr, scrub, highlight, musicSheet, shortcuts ->
        OverlayBundle(focusArr[0], focusArr[1], focusArr[2], focusArr[3], scrub, highlight, musicSheet, shortcuts)
    }

    private data class OverlayBundle(
        val appIdx: Int,
        val callIdx: Int,
        val contactIdx: Int,
        val hubIdx: Int,
        val scrub: Char?,
        val highlight: Char?,
        val musicSheet: Boolean,
        val shortcuts: Boolean
    )

    private val hubWriterSearchFlow = combine(
        NotificationHubRepository.notifications,
        shizukuBridge.statusState,
        combine(_isWriterModeOpen, _writerInitialSubTab) { open, sub -> open to sub },
        combine(
            _deviceSearchResults,
            _chatSearchResults,
            _isSearchingDevice,
            prefsRepo.searchTreeUris,
            combine(_contextMenuFile, _renameTargetApp, _iconPickerTargetApp) { f, r, i -> Triple(f, r, i) }
        ) { res, chats, loading, trees, modalTriple ->
            SearchBundle(
                results = res,
                chatResults = chats,
                isSearching = loading,
                indexedTreeCount = trees.size,
                contextMenuFile = modalTriple.first,
                renameTargetApp = modalTriple.second,
                iconPickerTargetApp = modalTriple.third
            )
        }
    ) { notifications, shizuku, writerPair, searchBundle ->
        HubWriterSearchBundle(notifications, shizuku, writerPair.first, writerPair.second, searchBundle)
    }

    private data class SearchBundle(
        val results: List<DeviceSearchFile>,
        val chatResults: List<LockedChatThread>,
        val isSearching: Boolean,
        val indexedTreeCount: Int,
        val contextMenuFile: DeviceSearchFile?,
        val renameTargetApp: AppEntry?,
        val iconPickerTargetApp: AppEntry?
    )

    private data class HubWriterSearchBundle(
        val notifications: List<HubNotificationItem>,
        val shizuku: ShizukuState,
        val writerOpen: Boolean,
        val writerSubTab: WriterSubTab,
        val searchBundle: SearchBundle
    )

    private val mediaAndExtrasFlow = combine(
        mediaRepo.mediaState,
        flowOf(0L),
        prefsRepo.hubQuickLinks,
        _callsSearchQuery,
        combine(
            combine(_showAddLinkDialog, _contextMenuApp, _isLoadingApps, _statusToast) { a, b, c, d ->
                arrayOf<Any?>(a, b, c, d)
            },
            _storageAccessState,
            mediaRepo.localTracks,
            _localTrackSearchQuery,
            prefsRepo.isPlayerCollapsed
        ) { extras, storageAccess, tracks, trackQuery, collapsed ->
            arrayOf<Any?>(extras[0], extras[1], extras[2], extras[3], storageAccess, tracks, trackQuery, collapsed)
        }
    ) { media, livePos, links, callsQuery, extras ->
        @Suppress("UNCHECKED_CAST")
        val storagePair = extras[4] as Pair<Boolean, Boolean>
        @Suppress("UNCHECKED_CAST")
        val allTracks = extras[5] as List<LocalAudioTrack>
        val trackQuery = extras[6] as String
        val filteredTracks = if (trackQuery.isBlank()) {
            allTracks
        } else {
            allTracks.filter {
                KeyboardSearchMatcher.matches(it.title, trackQuery) ||
                    KeyboardSearchMatcher.matches(it.artist, trackQuery) ||
                    KeyboardSearchMatcher.matches(it.album, trackQuery)
            }
        }
        MediaAndExtrasBundle(
            media = media,
            livePos = livePos,
            links = links,
            callsQuery = callsQuery,
            addLink = extras[0] as Boolean,
            ctxApp = extras[1] as AppEntry?,
            loading = extras[2] as Boolean,
            toast = extras[3] as String?,
            hasAllFiles = storagePair.first,
            hasAudioAccess = storagePair.second,
            localTracks = filteredTracks,
            localTrackSearchQuery = trackQuery,
            isPlayerCollapsed = extras[7] as Boolean
        )
    }.flowOn(Dispatchers.Default)

    private data class MediaAndExtrasBundle(
        val media: MediaPlayerState,
        val livePos: Long,
        val links: List<HubQuickLink>,
        val callsQuery: String,
        val addLink: Boolean,
        val ctxApp: AppEntry?,
        val loading: Boolean,
        val toast: String?,
        val hasAllFiles: Boolean,
        val hasAudioAccess: Boolean,
        val localTracks: List<LocalAudioTrack>,
        val localTrackSearchQuery: String,
        val isPlayerCollapsed: Boolean
    )

    private val customizationPrefsFlow = combine(
        prefsRepo.uiThemeColor,
        _wallpaperState,
        prefsRepo.autoKillEnabled,
        prefsRepo.autoKillPackages,
        combine(
            _showAppearanceDialog,
            _showAutoKillDialog,
            _musicDrawerTab,
            prefsRepo.savedPalettes,
            prefsRepo.activeCustomPaletteId
        ) { appDlg, killDlg, mTab, palettes, activePalId ->
            arrayOf<Any?>(appDlg, killDlg, mTab, palettes, activePalId)
        }
    ) { themeColor, wallpaperPair, autoKillOn, autoKillPkgs, uiArr ->
        @Suppress("UNCHECKED_CAST")
        CustomizationBundle(
            themeColor = themeColor,
            wallpaperBitmap = wallpaperPair.first,
            wallpaperPalette = wallpaperPair.second,
            autoKillEnabled = autoKillOn,
            autoKillPackages = autoKillPkgs,
            showAppearanceDialog = uiArr[0] as Boolean,
            showAutoKillDialog = uiArr[1] as Boolean,
            musicDrawerTab = uiArr[2] as MusicDrawerTab,
            savedPalettes = uiArr[3] as List<CustomColorPalette>,
            activeCustomPaletteId = uiArr[4] as String?
        )
    }

    private data class CustomizationBundle(
        val themeColor: UiThemeColor,
        val wallpaperBitmap: ImageBitmap?,
        val wallpaperPalette: WallpaperPaletteData?,
        val autoKillEnabled: Boolean,
        val autoKillPackages: Set<String>,
        val showAppearanceDialog: Boolean,
        val showAutoKillDialog: Boolean,
        val musicDrawerTab: MusicDrawerTab,
        val savedPalettes: List<CustomColorPalette>,
        val activeCustomPaletteId: String?
    )

    private data class SystemFeatureBundle(
        val optimizationStats: AndroidOptimizationStats,
        val showOptimizationDialog: Boolean,
        val showBackgroundWebAudioDialog: Boolean,
        val showLinuxCatalogDialog: Boolean,
        val backgroundSpoofEnabled: Boolean,
        val backgroundSpoofPackages: Set<String>,
        val linuxPackages: List<LinuxPackageEntry>,
        val linuxSearchResults: List<LinuxPackageEntry>,
        val isTermuxInstalled: Boolean
    )

    private val systemFeatureFlow = combine(
        prefsRepo.backgroundSpoofEnabled,
        prefsRepo.backgroundSpoofPackages,
        combine(_optimizationStats, _showOptimizationDialog, _showBackgroundWebAudioDialog, _showLinuxCatalogDialog) { s, o, w, l ->
            arrayOf<Any?>(s, o, w, l)
        },
        combine(_linuxPackages, _linuxSearchResults, _isTermuxInstalled) { allL, searchL, termux ->
            Triple(allL, searchL, termux)
        }
    ) { bgEnabled, bgPkgs, uiArr, linuxTriple ->
        SystemFeatureBundle(
            optimizationStats = uiArr[0] as AndroidOptimizationStats,
            showOptimizationDialog = uiArr[1] as Boolean,
            showBackgroundWebAudioDialog = uiArr[2] as Boolean,
            showLinuxCatalogDialog = uiArr[3] as Boolean,
            backgroundSpoofEnabled = bgEnabled,
            backgroundSpoofPackages = bgPkgs,
            linuxPackages = linuxTriple.first,
            linuxSearchResults = linuxTriple.second,
            isTermuxInstalled = linuxTriple.third
        )
    }

    private data class WorkspaceAndWeatherBundle(
        val workspaceGrid: WorkspaceGridState,
        val showWorkspaceSpace: Boolean,
        val workspaceFeedback: String?,
        val lockedChat: LockedChatThread?,
        val weatherReport: ConsensusWeatherReport?,
        val isWeatherLoading: Boolean,
        val weatherCityResults: List<WeatherCityResult>,
        val selectedWeatherCity: WeatherCityResult
    )

    private val workspaceAndWeatherFlow = combine(
        prefsRepo.workspaceGridState,
        _showWorkspaceSpace,
        _workspaceActionFeedback,
        _lockedChatThread,
        combine(_weatherReport, _isWeatherLoading, _weatherCityResults, prefsRepo.selectedWeatherCity) { r, load, cities, selected ->
            arrayOf<Any?>(r, load, cities, selected)
        }
    ) { wsGrid, showWs, wsFb, locked, wArr ->
        @Suppress("UNCHECKED_CAST")
        WorkspaceAndWeatherBundle(
            workspaceGrid = wsGrid,
            showWorkspaceSpace = showWs,
            workspaceFeedback = wsFb,
            lockedChat = locked,
            weatherReport = wArr[0] as ConsensusWeatherReport?,
            isWeatherLoading = wArr[1] as Boolean,
            weatherCityResults = wArr[2] as List<WeatherCityResult>,
            selectedWeatherCity = wArr[3] as WeatherCityResult
        )
    }

    private data class RecentsAndUiPrefsBundle(
        val appLanguage: AppLanguage,
        val uiScale: Float,
        val playerCoverStyle: PlayerCoverStyle,
        val showRecentsOverlay: Boolean,
        val recentFrames: List<RecentAppFrame>,
        val focusedRecentIndex: Int,
        val pinnedRecentPackages: Set<String>,
        val musicSearchResults: List<MusicStreamMatch>,
        val streamSearchHistory: List<String>
    )

    private val recentsAndUiPrefsFlow = combine(
        combine(
            prefsRepo.appLanguage,
            prefsRepo.uiScale,
            prefsRepo.playerCoverStyle,
            prefsRepo.pinnedRecentPackages,
            prefsRepo.streamSearchHistory
        ) { lang, scale, coverStyle, pinnedRecents, streamHist ->
            arrayOf<Any?>(lang, scale, coverStyle, pinnedRecents, streamHist)
        },
        combine(
            _showRecentsOverlay,
            _recentFrames,
            _focusedRecentIndex,
            _musicSearchResults
        ) { showRec, frames, recIdx, musicRes ->
            arrayOf<Any?>(showRec, frames, recIdx, musicRes)
        }
    ) { prefsArr, recArr ->
        @Suppress("UNCHECKED_CAST")
        RecentsAndUiPrefsBundle(
            appLanguage = prefsArr[0] as AppLanguage,
            uiScale = prefsArr[1] as Float,
            playerCoverStyle = prefsArr[2] as PlayerCoverStyle,
            pinnedRecentPackages = prefsArr[3] as Set<String>,
            streamSearchHistory = prefsArr[4] as List<String>,
            showRecentsOverlay = recArr[0] as Boolean,
            recentFrames = recArr[1] as List<RecentAppFrame>,
            focusedRecentIndex = recArr[2] as Int,
            musicSearchResults = recArr[3] as List<MusicStreamMatch>
        )
    }

    private data class PowerampBundle(
        val showFull: Boolean,
        val tab: PowerampTab,
        val sourceMode: SourceSelectionMode,
        val unifiedQuery: String,
        val unifiedResults: List<UnifiedMusicSearchResult>,
        val isSearchingOnline: Boolean,
        val sourcePicker: Pair<UnifiedMusicSearchResult?, Boolean>,
        val lyrics: TrackLyricsData?,
        val isFetchingLyrics: Boolean,
        val eqState: EqualizerState,
        val captureState: AudioCaptureState,
        val pendingCapture: PendingStreamCaptureRequest?,
        val smartAlbums: List<SmartAlbum>,
        val selectedAlbumId: String?,
        val weatherInHubOther: Boolean,
        val weatherInHomeTile: Boolean,
        val showWeatherForecast: Boolean,
        val showWeatherCityPicker: Boolean,
        val trackCoverOverrides: Map<String, String>,
        val defaultTrackFallbackCoverPath: String?,
        val previewState: PreviewPlaybackState
    )

    private val powerampMusicFlow = combine(
        combine(_showFullPowerampPlayer, _powerampTab, prefsRepo.musicSourceMode, _unifiedMusicQuery) { s, t, m, q ->
            arrayOf<Any?>(s, t, m, q)
        },
        combine(_unifiedMusicResults, _isSearchingMusicOnline, _sourcePickerTarget, _currentLyrics, _isFetchingLyrics) { r, load, pick, lyr, fl ->
            arrayOf<Any?>(r, load, pick, lyr, fl)
        },
        combine(
            mediaRepo.dspController.state,
            StreamAudioCaptureRuntime.captureState,
            _pendingStreamCaptureRequest,
            prefsRepo.smartAlbums,
            _selectedAlbumId
        ) { eq, cap, pend, albums, selAlb ->
            arrayOf<Any?>(eq, cap, pend, albums, selAlb)
        },
        combine(
            prefsRepo.showWeatherInHubOther,
            prefsRepo.showWeatherInHomeTile,
            _showWeatherForecastDialog,
            _showWeatherCityPickerDialog,
            combine(
                prefsRepo.trackCoverOverrides,
                prefsRepo.defaultTrackFallbackCoverPath,
                mediaRepo.previewState
            ) { cov, fb, prev -> Triple(cov, fb, prev) }
        ) { wHub, wHome, wFc, wCity, mediaCov ->
            arrayOf<Any?>(wHub, wHome, wFc, wCity, mediaCov)
        }
    ) { a, b, c, d ->
        @Suppress("UNCHECKED_CAST")
        val mediaCov = d[4] as Triple<Map<String, String>, String?, PreviewPlaybackState>
        PowerampBundle(
            showFull = a[0] as Boolean,
            tab = a[1] as PowerampTab,
            sourceMode = a[2] as SourceSelectionMode,
            unifiedQuery = a[3] as String,
            unifiedResults = b[0] as List<UnifiedMusicSearchResult>,
            isSearchingOnline = b[1] as Boolean,
            sourcePicker = b[2] as Pair<UnifiedMusicSearchResult?, Boolean>,
            lyrics = b[3] as TrackLyricsData?,
            isFetchingLyrics = b[4] as Boolean,
            eqState = c[0] as EqualizerState,
            captureState = c[1] as AudioCaptureState,
            pendingCapture = c[2] as PendingStreamCaptureRequest?,
            smartAlbums = c[3] as List<SmartAlbum>,
            selectedAlbumId = c[4] as String?,
            weatherInHubOther = d[0] as Boolean,
            weatherInHomeTile = d[1] as Boolean,
            showWeatherForecast = d[2] as Boolean,
            showWeatherCityPicker = d[3] as Boolean,
            trackCoverOverrides = mediaCov.first,
            defaultTrackFallbackCoverPath = mediaCov.second,
            previewState = mediaCov.third
        )
    }

    private data class ZipUpdateBundle(
        val showDialog: Boolean,
        val inspection: ZipUpdateInspection?,
        val busy: Boolean,
        val status: String?,
        val githubConfig: GitHubBuildConfig,
        val contextTrack: Pair<LocalAudioTrack?, SmartAlbum?>,
        val editingTrack: Pair<LocalAudioTrack?, AudioFileMetadataReadResult?>
    )

    private val zipUpdateFlow = combine(
        combine(_showZipUpdateDialog, _zipUpdateInspection, _zipUpdateBusy, _zipUpdateStatus, prefsRepo.githubBuildConfig) { show, insp, busy, status, ghCfg ->
            arrayOf<Any?>(show, insp, busy, status, ghCfg)
        },
        _contextMenuTrack,
        _editingMetadataTrack
    ) { arr, ctxTrack, editTrack ->
        ZipUpdateBundle(
            showDialog = arr[0] as Boolean,
            inspection = arr[1] as ZipUpdateInspection?,
            busy = arr[2] as Boolean,
            status = arr[3] as String?,
            githubConfig = arr[4] as GitHubBuildConfig,
            contextTrack = ctxTrack,
            editingTrack = editTrack
        )
    }

    val uiState: StateFlow<LauncherUiState> = combine(
        baseAppsFlow,
        callsFlow,
        navFlow,
        uiOverlaysFlow,
        combine(
            combine(mediaAndExtrasFlow, hubWriterSearchFlow) { m, hw -> m to hw },
            combine(
                customizationPrefsFlow,
                systemFeatureFlow,
                recentsAndUiPrefsFlow,
                workspaceAndWeatherFlow,
                combine(powerampMusicFlow, zipUpdateFlow) { pw, zip -> pw to zip }
            ) { cust, sys, rec, wsw, pwZip ->
                arrayOf<Any?>(cust, sys, rec, wsw, pwZip.first, pwZip.second)
            }
        ) { mh, right -> mh to right }
    ) { appsBundle, calls, nav, overlays, rightPair ->
        val mediaBundle = rightPair.first.first
        val hw = rightPair.first.second
        val cust = rightPair.second[0] as CustomizationBundle
        val sys = rightPair.second[1] as SystemFeatureBundle
        val rec = rightPair.second[2] as RecentsAndUiPrefsBundle
        val wsw = rightPair.second[3] as WorkspaceAndWeatherBundle
        val pw = rightPair.second[4] as PowerampBundle
        val zip = rightPair.second[5] as ZipUpdateBundle

        val customized = appsBundle.customized
        val filtered = appsBundle.filteredApps
        val coercedAppFocus = if (filtered.isEmpty()) 0 else overlays.appIdx.coerceIn(0, filtered.lastIndex)
        val coercedLogFocus = if (calls.logs.isEmpty()) 0 else overlays.callIdx.coerceIn(0, calls.logs.lastIndex)
        val coercedContactFocus = if (calls.contacts.isEmpty()) 0 else overlays.contactIdx.coerceIn(0, calls.contacts.lastIndex)
        val coercedRecentFocus = if (rec.recentFrames.isEmpty()) 0 else rec.focusedRecentIndex.coerceIn(0, rec.recentFrames.lastIndex)

        val images = hw.searchBundle.results.filter { it.kind == SearchResultKind.IMAGE }
        val files = hw.searchBundle.results.filter { it.kind == SearchResultKind.FILE }
        val trimmedQuery = nav.appSearchQuery.trim()
        val matchedAlbums = if (trimmedQuery.length >= 2) {
            pw.smartAlbums.filter { alb ->
                KeyboardSearchMatcher.matches(alb.title, trimmedQuery) ||
                    KeyboardSearchMatcher.matches(alb.artist, trimmedQuery) ||
                    alb.expectedTracks.any { KeyboardSearchMatcher.matches(it.title, trimmedQuery) }
            }
        } else {
            emptyList()
        }

        LauncherUiState(
            activeTab = nav.activeTab,
            callsSubTab = nav.callsSubTab,
            hubSubTab = nav.hubSubTab,
            alphabetMode = nav.alphabetMode,
            allApps = customized.allApps,
            filteredApps = filtered,
            favoriteApps = customized.pinnedApps,
            musicApps = customized.musicApps,
            chatApps = customized.chatApps,
            newsApps = customized.newsApps,
            appSearchQuery = nav.appSearchQuery,
            searchImages = images,
            searchFiles = files,
            chatSearchResults = hw.searchBundle.chatResults,
            linuxSearchResults = sys.linuxSearchResults,
            isSearchingDevice = hw.searchBundle.isSearching,
            indexedFolderCount = hw.searchBundle.indexedTreeCount,
            focusedAppIndex = coercedAppFocus,
            focusedCallLogIndex = coercedLogFocus,
            focusedContactIndex = coercedContactFocus,
            focusedHubIndex = overlays.hubIdx,
            activeScrubLetter = overlays.scrub,
            highlightedLetter = overlays.highlight,
            isLoadingApps = mediaBundle.loading,
            callsSearchQuery = mediaBundle.callsQuery,
            callLogs = calls.logs,
            contacts = calls.contacts,
            starredContacts = calls.starred,
            hasCallLogPermission = calls.perms.first,
            hasContactsPermission = calls.perms.second,
            hasCallPhonePermission = calls.perms.third,
            hasAllFilesAccess = mediaBundle.hasAllFiles,
            hasAudioReadAccess = mediaBundle.hasAudioAccess,
            hubQuickLinks = mediaBundle.links,
            hubNotifications = hw.notifications,
            shizukuState = hw.shizuku,
            isWriterModeOpen = hw.writerOpen,
            writerInitialSubTab = hw.writerSubTab,
            mediaState = mediaBundle.media,
            livePositionMs = mediaBundle.livePos,
            localAudioTracks = mediaBundle.localTracks,
            localTrackSearchQuery = mediaBundle.localTrackSearchQuery,
            musicDrawerTab = cust.musicDrawerTab,
            isPlayerCollapsed = mediaBundle.isPlayerCollapsed,
            showMusicSheet = overlays.musicSheet,
            showShortcutsDialog = overlays.shortcuts,
            showAddLinkDialog = mediaBundle.addLink,
            showAppearanceDialog = cust.showAppearanceDialog,
            showAutoKillDialog = cust.showAutoKillDialog,
            showOptimizationDialog = sys.showOptimizationDialog,
            showBackgroundWebAudioDialog = sys.showBackgroundWebAudioDialog,
            showLinuxCatalogDialog = sys.showLinuxCatalogDialog,
            showZipUpdateDialog = zip.showDialog,
            zipUpdateInspection = zip.inspection,
            zipUpdateBusy = zip.busy,
            zipUpdateStatus = zip.status,
            githubBuildConfig = zip.githubConfig,
            appVersionName = installedVersionInfo.first,
            appVersionCode = installedVersionInfo.second,
            uiThemeColor = cust.themeColor,
            wallpaperBitmap = cust.wallpaperBitmap,
            wallpaperPalette = cust.wallpaperPalette,
            hasWallpaper = cust.wallpaperBitmap != null,
            savedPalettes = cust.savedPalettes,
            activeCustomPaletteId = cust.activeCustomPaletteId,
            autoKillEnabled = cust.autoKillEnabled,
            autoKillPackages = cust.autoKillPackages,
            optimizationStats = sys.optimizationStats,
            backgroundSpoofEnabled = sys.backgroundSpoofEnabled,
            backgroundSpoofPackages = sys.backgroundSpoofPackages,
            appLanguage = rec.appLanguage,
            uiScale = rec.uiScale,
            playerCoverStyle = rec.playerCoverStyle,
            showRecentsOverlay = rec.showRecentsOverlay,
            recentFrames = rec.recentFrames,
            focusedRecentIndex = coercedRecentFocus,
            pinnedRecentPackages = rec.pinnedRecentPackages,
            workspaceGrid = wsw.workspaceGrid,
            showWorkspaceSpace = wsw.showWorkspaceSpace,
            workspaceActionFeedback = wsw.workspaceFeedback,
            lockedChatThread = wsw.lockedChat,
            linuxPackages = sys.linuxPackages,
            isTermuxInstalled = sys.isTermuxInstalled,
            weatherReport = wsw.weatherReport,
            isWeatherLoading = wsw.isWeatherLoading,
            weatherCityResults = wsw.weatherCityResults,
            selectedWeatherCity = wsw.selectedWeatherCity,
            musicSearchResults = rec.musicSearchResults,
            streamSearchHistory = rec.streamSearchHistory,
            showFullPowerampPlayer = pw.showFull,
            powerampTab = pw.tab,
            sourceSelectionMode = pw.sourceMode,
            unifiedMusicQuery = pw.unifiedQuery,
            unifiedMusicResults = pw.unifiedResults,
            isSearchingMusicOnline = pw.isSearchingOnline,
            sourcePickerTarget = pw.sourcePicker.first,
            sourcePickerActionDownload = pw.sourcePicker.second,
            currentLyrics = pw.lyrics,
            isFetchingLyrics = pw.isFetchingLyrics,
            equalizerState = pw.eqState,
            audioCaptureState = pw.captureState,
            pendingStreamCaptureRequest = pw.pendingCapture,
            smartAlbums = pw.smartAlbums,
            albumSearchResults = matchedAlbums,
            selectedAlbumId = pw.selectedAlbumId,
            trackCoverOverrides = pw.trackCoverOverrides,
            defaultTrackFallbackCoverPath = pw.defaultTrackFallbackCoverPath,
            previewPlaybackState = pw.previewState,
            showWeatherInHubOther = pw.weatherInHubOther,
            showWeatherInHomeTile = pw.weatherInHomeTile,
            showWeatherForecastDialog = pw.showWeatherForecast,
            showWeatherCityPickerDialog = pw.showWeatherCityPicker,
            contextMenuApp = mediaBundle.ctxApp,
            contextMenuFile = hw.searchBundle.contextMenuFile,
            contextMenuTrack = zip.contextTrack.first,
            contextMenuTrackAlbum = zip.contextTrack.second,
            editingMetadataTrack = zip.editingTrack.first,
            editingMetadataPreview = zip.editingTrack.second,
            renameTargetApp = hw.searchBundle.renameTargetApp,
            iconPickerTargetApp = hw.searchBundle.iconPickerTargetApp,
            statusToast = mediaBundle.toast
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LauncherUiState()
    )

    val rootThemeConfig: StateFlow<RootThemeConfig> = combine(
        customizationPrefsFlow,
        recentsAndUiPrefsFlow
    ) { cust, rec ->
        val customPal = cust.activeCustomPaletteId?.let { id ->
            cust.savedPalettes.firstOrNull { it.id == id }
        }
        RootThemeConfig(
            uiThemeColor = cust.themeColor,
            activeCustomPalette = customPal,
            wallpaperPalette = cust.wallpaperPalette,
            hasWallpaper = cust.wallpaperBitmap != null,
            appLanguage = rec.appLanguage,
            uiScale = rec.uiScale
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = RootThemeConfig()
    )

    init {
        StreamAudioCaptureRuntime.setCaptureModes(
            autoSplitEnabled = _captureAutoSplitEnabled.value,
            silentMuteMode = _captureSilentMuteMode.value
        )
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                prefsRepo.getAppIconOverridesNow().values
                    .mapNotNull { it.customFilePath }
                    .forEach { appsRepo.resolveCustomBitmap(it) }
            }
            val savedHistory = runCatching { prefsRepo.getStreamingHistoryNow() }.getOrDefault(emptyList())
            mediaRepo.loadPersistedHistory(savedHistory)
            runCatching { prefsRepo.getEqualizerConfigNow() }.getOrNull()?.let { eqCfg ->
                mediaRepo.dspController.applySaved(
                    enabled = eqCfg.enabled,
                    preset = eqCfg.preset,
                    bandsMb = eqCfg.bandsMb,
                    bass = eqCfg.bassBoost,
                    virt = eqCfg.virtualizer
                )
            }
        }
        mediaRepo.onTrackHistoryUpdated = { tracks ->
            viewModelScope.launch {
                prefsRepo.saveStreamingHistory(tracks)
            }
        }

        viewModelScope.launch {
            zipUpdateRepo.installEvents.collect { msg ->
                _zipUpdateStatus.value = msg
                showToast(msg)
            }
        }

        viewModelScope.launch {
            StreamAudioCaptureRuntime.completedCaptures.collect { captured ->
                val refreshed = mediaRepo.refreshLocalTracks()
                val newTrack = refreshed.firstOrNull { it.filePath == captured.filePath }
                if (newTrack != null && captured.targetAlbumId != null) {
                    prefsRepo.updateSmartAlbums { albums ->
                        albums.map { alb ->
                            if (alb.id == captured.targetAlbumId) {
                                MusicCatalogAndLyricsRepository.reconcileAlbumTracks(
                                    alb.copy(attachedLocalTrackIds = (alb.attachedLocalTrackIds + newTrack.id).distinct()),
                                    refreshed
                                )
                            } else alb
                        }
                    }
                }
                showToast("Захват сохранён: ${captured.title} (WAV)")
            }
        }

        viewModelScope.launch {
            var lastSeenDiscardReason: String? = null
            StreamAudioCaptureRuntime.captureState.collect { cap ->
                val reason = cap.discardReason
                if (!reason.isNullOrBlank() && reason != lastSeenDiscardReason) {
                    lastSeenDiscardReason = reason
                    mediaRepo.refreshLocalTracks()
                    showToast(reason)
                } else if (reason.isNullOrBlank()) {
                    lastSeenDiscardReason = null
                }
            }
        }

        viewModelScope.launch {
            mediaRepo.mediaState.collect { mState ->
                val active = mState.activeSession
                if (active != null && active.title.isNotBlank()) {
                    val key = MusicCatalogAndLyricsRepository.buildTrackKey(active.title, active.artist)
                    if (key != lastLoadedLyricsKey) {
                        lyricsRetryAttempt = 0
                        lastRejectedLyricsRaw = null
                        lastLoadedLyricsKey = key
                        loadLyricsForCurrentTrack(forceRefresh = false)
                    }
                    if (StreamAudioCaptureRuntime.captureState.value.isCapturing) {
                        StreamAudioCaptureRuntime.updateActiveTrackMetadata(
                            title = active.title,
                            artist = active.artist,
                            durationMs = active.durationMs
                        )
                    }
                }
            }
        }

        refreshInstalledApps()
        refreshCallsAndContacts()
        refreshStorageAndAudioAccess()
        refreshOptimizationStats()
        refreshLinuxCatalog()
        mediaRepo.startObserving()
        shizukuBridge.startObserving()

        viewModelScope.launch {
            prefsRepo.wallpaperPath.collect { path ->
                _wallpaperState.value = WallpaperThemeHelper.loadWallpaperAndPalette(path)
            }
        }

        viewModelScope.launch {
            combine(
                prefsRepo.backgroundSpoofEnabled,
                prefsRepo.backgroundSpoofPackages,
                prefsRepo.interceptLockScreenEnabled
            ) { bgEnabled, pkgs, lockEnabled ->
                Triple(bgEnabled, pkgs, lockEnabled)
            }.collect { (bgEnabled, pkgs, lockEnabled) ->
                MediaGuardRuntime.isGuardEnabled = bgEnabled
                MediaGuardRuntime.guardedPackages = pkgs
                BackgroundMediaGuardService.syncServiceState(getApplication(), bgEnabled || lockEnabled)
            }
        }

        viewModelScope.launch {
            combine(
                prefsRepo.interceptLockScreenEnabled,
                prefsRepo.oemGestureBypassEnabled
            ) { lock, oem -> lock to oem }.collect { (lock, oem) ->
                shizukuBridge.updateInterceptFlags(lock, oem)
            }
        }

        viewModelScope.launch {
            prefsRepo.selectedWeatherCity.collect { city ->
                refreshWeatherForCity(city)
            }
        }

        viewModelScope.launch {
            appsRepo.observePackageChanges().collect {
                refreshInstalledApps()
            }
        }

        viewModelScope.launch {
            MediaControlBus.listenerEvents.collect {
                mediaRepo.refreshListenerStatusAndSessions()
                NotificationHubRepository.refreshFromAttachedService()
                shizukuBridge.refreshStatus()
            }
        }

        viewModelScope.launch {
            NotificationHubRepository.notifications.collect { list ->
                val cur = _lockedChatThread.value ?: return@collect
                val updated = list.firstOrNull {
                    it.key == cur.key || (it.packageName == cur.packageName && it.title == cur.contactName)
                }
                if (updated != null) {
                    _lockedChatThread.value = notificationToLockedThread(updated)
                }
            }
        }

        viewModelScope.launch {
            while (isActive) {
                val active = mediaRepo.mediaState.value.activeSession
                _livePositionMs.value = if (active != null) {
                    MediaControlRepository.currentEstimatedPositionMs(active)
                } else {
                    0L
                }
                delay(if (active?.isPlaying == true) 200L else 500L)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaRepo.onTrackHistoryUpdated = null
        mediaRepo.stopObserving()
        shizukuBridge.stopObserving()
    }

    fun onLauncherPause() {
        wasPausedSinceResume = true
    }

    fun onResumeRefresh() {
        KeyDeckAccessibilityService.hideDockedWorkspaceBar()
        mediaRepo.refreshListenerStatusAndSessions()
        NotificationHubRepository.refreshFromAttachedService()
        shizukuBridge.refreshStatus()
        refreshCallsAndContacts()
        refreshStorageAndAudioAccess()
        refreshOptimizationStats()

        if (wasPausedSinceResume) {
            wasPausedSinceResume = false
            val state = uiState.value
            if (state.autoKillEnabled && state.autoKillPackages.isNotEmpty()) {
                val activeAudioPkgs = state.mediaState.sessions
                    .filter { it.isPlaying }
                    .map { it.packageName }
                    .toSet()
                viewModelScope.launch {
                    autoCloseManager.terminateExitedOneTimeApps(
                        configuredPackages = state.autoKillPackages,
                        activeMediaPackages = activeAudioPkgs
                    )
                }
            }
        }
    }

    fun refreshStorageAndAudioAccess() {
        val ctx = getApplication<Application>()
        val allFiles = UniversalSearchRepository.hasAllFilesAccess(ctx)
        val audioRead = UniversalSearchRepository.hasAudioReadAccess(ctx)
        _storageAccessState.value = allFiles to audioRead
        if (audioRead || allFiles) {
            viewModelScope.launch {
                mediaRepo.refreshLocalTracks()
            }
        }
    }

    fun openAllFilesAccessSettings() {
        if (!UniversalSearchRepository.openAllFilesAccessSettings(getApplication())) {
            showToast("Откройте Настройки → Приложения → KeyDeck → Разрешения")
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            val loaded = appsRepo.loadInstalledApps(emptySet())
            _allApps.value = loaded
            _isLoadingApps.value = false
            _isTermuxInstalled.value = linuxRepo.isTermuxInstalled()
            val defaults = autoCloseManager.discoverDefaultOneTimePackages(loaded)
            prefsRepo.initDefaultAutoKillPackagesIfNeeded(defaults)

            val defaultSpoofPkgs = loaded
                .map { it.packageName }
                .filter { MediaGuardRuntime.isDefaultSpoilerCandidate(it) }
                .toSet()
            prefsRepo.initDefaultBackgroundSpoofPackagesIfNeeded(defaultSpoofPkgs)
        }
    }

    fun refreshCallsAndContacts() {
        val hasLog = callsRepo.hasCallLogPermission()
        val hasContacts = callsRepo.hasContactsPermission()
        val hasCall = callsRepo.hasCallPhonePermission()
        _permissionsState.value = Triple(hasLog, hasContacts, hasCall)

        viewModelScope.launch {
            if (hasLog) _callLogs.value = callsRepo.loadCallLogs()
            if (hasContacts) _contacts.value = callsRepo.loadContacts()
        }
    }

    fun selectTab(tab: MainTab) {
        _isWriterModeOpen.value = false
        _showFullPowerampPlayer.value = false
        if (tab != MainTab.HOME) {
            _showWorkspaceSpace.value = false
        }
        if (_activeTab.value != tab) {
            _highlightedLetter.value = null
            _activeScrubLetter.value = null
        }
        _activeTab.value = tab
    }

    fun selectCallsSubTab(subTab: CallsSubTab) {
        _callsSubTab.value = subTab
    }

    fun selectHubSubTab(subTab: HubSubTab) {
        _hubSubTab.value = subTab
        _focusedHubIndex.value = 0
    }

    // Writer / Book Studio Mode
    fun openWriterMode(subTab: WriterSubTab? = null) {
        if (subTab != null) _writerInitialSubTab.value = subTab
        _showFullPowerampPlayer.value = false
        _isWriterModeOpen.value = true
    }

    fun closeWriterMode() {
        _isWriterModeOpen.value = false
    }

    fun toggleWriterMode() {
        _isWriterModeOpen.value = !_isWriterModeOpen.value
    }

    fun toggleAlphabetMode() {
        viewModelScope.launch {
            val next = if (uiState.value.alphabetMode == AlphabetMode.LATIN) {
                AlphabetMode.CYRILLIC
            } else {
                AlphabetMode.LATIN
            }
            prefsRepo.setAlphabetMode(next)
        }
    }

    fun updateAppSearchQuery(query: String) {
        _appSearchQuery.value = query
        _focusedAppIndex.value = 0
        _highlightedLetter.value = null
        searchDebounceJob?.cancel()
        chatSearchJob?.cancel()
        if (query.trim().length < 2) {
            _chatSearchResults.value = emptyList()
            _musicSearchResults.value = emptyList()
            _deviceSearchResults.value = emptyList()
            _linuxSearchResults.value = emptyList()
            _isSearchingDevice.value = false
            return
        }
        chatSearchJob = viewModelScope.launch {
            delay(50L)
            val snapshotNotifs = NotificationHubRepository.notifications.value
            val snapshotContacts = _contacts.value
            val snapshotChatApps = uiState.value.chatApps
            val matchedChats = withContext(Dispatchers.Default) {
                buildChatSearchResults(
                    query = query,
                    notifications = snapshotNotifs,
                    contacts = snapshotContacts,
                    chatApps = snapshotChatApps
                )
            }
            if (_appSearchQuery.value == query) {
                _chatSearchResults.value = matchedChats
            }
        }
        searchDebounceJob = viewModelScope.launch {
            delay(180L)
            val musicAppsSnapshot = uiState.value.musicApps
            _musicSearchResults.value = withContext(Dispatchers.Default) {
                mediaRepo.searchStreamingAndLocalCatalog(query, musicAppsSnapshot)
            }
            triggerDeviceSearch(query)
            triggerLinuxSearch(query)
        }
    }

    private fun triggerDeviceSearch(query: String) {
        deviceSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _deviceSearchResults.value = emptyList()
            _isSearchingDevice.value = false
            return
        }
        deviceSearchJob = viewModelScope.launch {
            _isSearchingDevice.value = true
            delay(260L)
            val trees = prefsRepo.getSearchTreeUrisNow()
            val found = universalSearchRepo.searchDevice(trimmed, trees)
            _deviceSearchResults.value = found
            _isSearchingDevice.value = false
        }
    }

    private fun triggerLinuxSearch(query: String) {
        linuxSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _linuxSearchResults.value = emptyList()
            return
        }
        linuxSearchJob = viewModelScope.launch {
            delay(150L)
            _linuxSearchResults.value = linuxRepo.scanLinuxPackages(trimmed)
        }
    }

    fun addSearchFolderTree(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }.onFailure {
                runCatching {
                    getApplication<Application>().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            prefsRepo.addSearchTreeUri(uri.toString())
            showToast("Папка подключена к универсальному поиску")
            val q = _appSearchQuery.value
            if (q.trim().length >= 2) triggerDeviceSearch(q)
        }
    }

    fun openFileContextMenu(file: DeviceSearchFile?) {
        _contextMenuFile.value = file
    }

    fun openSearchFile(file: DeviceSearchFile) {
        val lower = file.name.lowercase()
        if (lower.endsWith(".zip") || lower.endsWith(".apks") || lower.endsWith(".xapk")) {
            _contextMenuFile.value = null
            _showZipUpdateDialog.value = true
            inspectZipUpdateUri(
                uri = Uri.parse(file.uriString),
                localFilePath = file.localFilePath,
                displayName = file.name
            )
            return
        }
        if (!universalSearchRepo.openFile(file)) {
            showToast("Нет приложения для открытия ${file.name}")
        }
    }

    fun shareSearchFile(file: DeviceSearchFile) {
        _contextMenuFile.value = null
        if (!universalSearchRepo.shareFile(file)) {
            showToast("Не удалось поделиться файлом")
        }
    }

    fun openSearchFileFolder(file: DeviceSearchFile) {
        _contextMenuFile.value = null
        val opened = universalSearchRepo.openContainingFolder(file)
        showToast(
            if (opened) "Путь скопирован: ${file.parentPath}"
            else "Путь скопирован в буфер: ${file.displayPath}"
        )
    }

    fun copySearchFilePath(file: DeviceSearchFile) {
        universalSearchRepo.copyPath(file)
        _contextMenuFile.value = null
        showToast("Путь скопирован: ${file.displayPath}")
    }

    fun copySearchFileToClipboard(file: DeviceSearchFile) {
        _contextMenuFile.value = null
        if (universalSearchRepo.copyFileToClipboard(file)) {
            showToast("Скопировано в буфер (Ctrl+C): ${file.name}")
        } else {
            universalSearchRepo.copyPath(file)
            showToast("Путь скопирован: ${file.name}")
        }
    }

    fun pasteClipboardAction() {
        if (KeyDeckAccessibilityService.performGlobalPaste()) {
            showToast("Вставка (Ctrl+V)")
        } else {
            val shizuku = uiState.value.shizukuState
            if ((shizuku.isBinderAlive && shizuku.isPermissionGranted) || shizuku.isRootAvailable) {
                viewModelScope.launch {
                    shizukuBridge.pasteClipboardViaInputKeyEvent()
                }
            }
        }
    }

    fun deleteSearchFile(file: DeviceSearchFile) {
        _contextMenuFile.value = null
        viewModelScope.launch {
            val ok = universalSearchRepo.deleteFile(file)
            if (ok) {
                _deviceSearchResults.value = _deviceSearchResults.value.filterNot { it.id == file.id }
                showToast("Удалено: ${file.name}")
            } else {
                showToast("Не удалось удалить ${file.name} (ограничение Android)")
            }
        }
    }

    fun resolveShareableUri(file: DeviceSearchFile): Uri =
        universalSearchRepo.resolveShareableUri(file)

    fun sendFileToWorkspaceAppOrChat(file: DeviceSearchFile) {
        val state = uiState.value
        val activeChat = state.lockedChatThread
        if (activeChat != null) {
            val uri = universalSearchRepo.resolveShareableUri(file)
            if (activeChat.canReplyInline && activeChat.key.startsWith("contact:").not()) {
                replyLockedChat("", uri, file.mimeType)
            } else {
                universalSearchRepo.sendFileToPackage(file, activeChat.packageName)
            }
            return
        }
        val activeCell = state.workspaceGrid.activeCell
        if (!activeCell.isHome && !activeCell.packageName.isNullOrBlank()) {
            universalSearchRepo.sendFileToPackage(file, activeCell.packageName)
            return
        }
        shareSearchFile(file)
    }

    fun searchOnWeb(query: String, engine: String = "google") {
        val clean = query.trim()
        if (clean.isEmpty()) return
        val encoded = Uri.encode(clean)
        val url = when (engine) {
            "yandex" -> "https://yandex.ru/search/?text=$encoded"
            "youtube" -> "https://www.youtube.com/results?search_query=$encoded"
            "wiki" -> "https://ru.wikipedia.org/w/index.php?search=$encoded"
            else -> "https://www.google.com/search?q=$encoded"
        }
        openUrl(url)
    }

    fun appendAppSearchChar(char: Char) = updateAppSearchQuery(_appSearchQuery.value + char)

    fun backspaceAppSearch() {
        val current = _appSearchQuery.value
        if (current.isNotEmpty()) updateAppSearchQuery(current.dropLast(1))
    }

    fun updateCallsSearchQuery(query: String) {
        _callsSearchQuery.value = query
        _focusedCallLogIndex.value = 0
        _focusedContactIndex.value = 0
    }

    fun appendCallsSearchChar(char: Char) = updateCallsSearchQuery(_callsSearchQuery.value + char)

    fun backspaceCallsSearch() {
        val current = _callsSearchQuery.value
        if (current.isNotEmpty()) updateCallsSearchQuery(current.dropLast(1))
    }

    fun clearAllSearchOrHighlights() {
        when (_activeTab.value) {
            MainTab.HOME -> {
                searchDebounceJob?.cancel()
                chatSearchJob?.cancel()
                _appSearchQuery.value = ""
                _chatSearchResults.value = emptyList()
                _deviceSearchResults.value = emptyList()
                _musicSearchResults.value = emptyList()
                _linuxSearchResults.value = emptyList()
                _isSearchingDevice.value = false
                _highlightedLetter.value = null
                _activeScrubLetter.value = null
                _focusedAppIndex.value = 0
            }
            MainTab.CALLS -> {
                _callsSearchQuery.value = ""
                _highlightedLetter.value = null
                _activeScrubLetter.value = null
                _focusedCallLogIndex.value = 0
                _focusedContactIndex.value = 0
            }
            MainTab.HUB -> {
                _highlightedLetter.value = null
            }
        }
    }

    fun onAlphabetScrub(letter: Char?) {
        _activeScrubLetter.value = letter
        if (letter == null) return

        if (_activeTab.value == MainTab.CALLS && _callsSubTab.value == CallsSubTab.CONTACTS) {
            if (_callsSearchQuery.value.isNotEmpty()) _callsSearchQuery.value = ""
            _highlightedLetter.value = letter
            _focusedContactIndex.value = 0
            _scrollContactIndexEvents.tryEmit(0)
            return
        }

        if (_activeTab.value != MainTab.HOME) _activeTab.value = MainTab.HOME
        if (_appSearchQuery.value.isNotEmpty()) updateAppSearchQuery("")
        _highlightedLetter.value = letter
        _focusedAppIndex.value = 0
        _scrollAppIndexEvents.tryEmit(0)
    }

    fun jumpToLetter(letter: Char) {
        if (_highlightedLetter.value == letter) {
            _highlightedLetter.value = null
            _activeScrubLetter.value = null
            _focusedAppIndex.value = 0
            _focusedContactIndex.value = 0
            return
        }
        onAlphabetScrub(letter)
        _activeScrubLetter.value = null
    }

    fun moveGridFocus(dx: Int, dy: Int, columns: Int) {
        val state = uiState.value
        val cols = columns.coerceAtLeast(1)

        when (state.activeTab) {
            MainTab.HOME -> {
                val list = state.filteredApps
                if (list.isEmpty()) return
                val current = _focusedAppIndex.value.coerceIn(0, list.lastIndex)
                val target = computeNextGridIndex(current, dx, dy, cols, list.size)
                _focusedAppIndex.value = target
                _scrollAppIndexEvents.tryEmit(target)
            }
            MainTab.CALLS -> {
                if (state.callsSubTab == CallsSubTab.CONTACTS) {
                    val list = state.contacts
                    if (list.isEmpty()) return
                    val current = _focusedContactIndex.value.coerceIn(0, list.lastIndex)
                    val target = computeNextGridIndex(current, dx, dy, cols, list.size)
                    _focusedContactIndex.value = target
                    _scrollContactIndexEvents.tryEmit(target)
                } else {
                    val list = state.callLogs
                    if (list.isEmpty()) return
                    val step = if (dy != 0) dy else dx
                    val current = _focusedCallLogIndex.value.coerceIn(0, list.lastIndex)
                    val target = (current + step).coerceIn(0, list.lastIndex)
                    _focusedCallLogIndex.value = target
                    _scrollCallLogIndexEvents.tryEmit(target)
                }
            }
            MainTab.HUB -> {
                val listCount = state.hubNotifications.count { it.subTab == state.hubSubTab }
                val totalCount = when (state.hubSubTab) {
                    HubSubTab.CHATS -> listCount
                    HubSubTab.NEWS -> listCount + state.hubQuickLinks.count { it.category == HubSubTab.NEWS }
                    HubSubTab.OTHER -> listCount
                    HubSubTab.SETTINGS -> 12
                }
                if (totalCount <= 0) return
                val step = if (dy != 0) dy else dx
                val current = _focusedHubIndex.value.coerceIn(0, totalCount - 1)
                val target = (current + step).coerceIn(0, totalCount - 1)
                _focusedHubIndex.value = target
                _scrollHubIndexEvents.tryEmit(target)
            }
        }
    }

    private fun computeNextGridIndex(current: Int, dx: Int, dy: Int, columns: Int, size: Int): Int {
        if (size <= 0) return 0
        return (current + dx + dy * columns).coerceIn(0, size - 1)
    }

    fun setFocusedAppIndex(index: Int) {
        val list = uiState.value.filteredApps
        if (list.isNotEmpty()) _focusedAppIndex.value = index.coerceIn(0, list.lastIndex)
    }

    fun activateFocusedItem() {
        val state = uiState.value
        when (state.activeTab) {
            MainTab.HOME -> {
                if (state.filteredApps.isNotEmpty()) {
                    launchFocusedApp()
                } else if (state.chatSearchResults.isNotEmpty()) {
                    openLockedChat(state.chatSearchResults.first())
                } else if (state.searchFiles.isNotEmpty()) {
                    openSearchFile(state.searchFiles.first())
                } else if (state.searchImages.isNotEmpty()) {
                    openSearchFile(state.searchImages.first())
                } else if (state.linuxSearchResults.isNotEmpty()) {
                    installOrRunLinuxPackage(state.linuxSearchResults.first())
                } else if (state.appSearchQuery.isNotBlank()) {
                    searchOnWeb(state.appSearchQuery, "google")
                }
            }
            MainTab.CALLS -> {
                if (state.callsSearchQuery.isNotBlank()) {
                    placeCall(state.callsSearchQuery)
                } else if (state.callsSubTab == CallsSubTab.CONTACTS) {
                    state.contacts.getOrNull(state.focusedContactIndex)?.let { placeCall(it.number) }
                } else {
                    state.callLogs.getOrNull(state.focusedCallLogIndex)?.let { placeCall(it.number) }
                }
            }
            MainTab.HUB -> {
                val items = state.hubNotifications.filter { it.subTab == state.hubSubTab }
                val idx = state.focusedHubIndex
                if (state.hubSubTab != HubSubTab.SETTINGS && idx in items.indices) {
                    openHubNotification(items[idx].key)
                    return
                }
                val remIdx = if (state.hubSubTab == HubSubTab.SETTINGS) idx else idx - items.size
                when (state.hubSubTab) {
                    HubSubTab.NEWS -> {
                        val links = state.hubQuickLinks.filter { it.category == HubSubTab.NEWS }
                        links.getOrNull(remIdx)?.let { openUrl(it.url) }
                    }
                    HubSubTab.SETTINGS -> {
                        when (remIdx) {
                            0 -> setShowAppearanceDialog(true)
                            1 -> setShowOptimizationDialog(true)
                            2 -> setShowLinuxCatalogDialog(true)
                            3 -> setShowAutoKillDialog(true)
                            4 -> openDefaultLauncherSettings()
                            5 -> openNotificationListenerSettings()
                            6 -> setShowMusicSheet(true)
                            7 -> setShowShortcutsDialog(true)
                            8 -> openSystemSettings(Settings.ACTION_WIFI_SETTINGS)
                            9 -> openSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                            10 -> openSystemSettings(Settings.ACTION_SETTINGS)
                            11 -> openAllFilesAccessSettings()
                        }
                    }
                    HubSubTab.OTHER, HubSubTab.CHATS -> Unit
                }
            }
        }
    }

    // Isolated Locked Single Chat & Search
    private fun buildChatSearchResults(
        query: String,
        notifications: List<HubNotificationItem>,
        contacts: List<ContactEntry>,
        chatApps: List<AppEntry>
    ): List<LockedChatThread> {
        val clean = query.trim()
        if (clean.length < 2) return emptyList()

        val result = mutableListOf<LockedChatThread>()
        val seenKeys = HashSet<String>()

        notifications
            .filter {
                it.subTab == HubSubTab.CHATS && (
                    KeyboardSearchMatcher.matches(it.title, clean) ||
                        KeyboardSearchMatcher.matches(it.appName, clean) ||
                        KeyboardSearchMatcher.matches(it.text, clean) ||
                        KeyboardSearchMatcher.matches(it.bigText, clean)
                    )
            }
            .take(10)
            .forEach { n ->
                val dedup = "${n.packageName}:${n.title.lowercase()}"
                if (seenKeys.add(dedup)) {
                    result.add(notificationToLockedThread(n))
                }
            }

        NotificationHubRepository.searchArchivedThreads(getApplication(), clean)
            .forEach { archived ->
                val dedup = "${archived.packageName}:${archived.contactName.lowercase()}"
                if (seenKeys.add(dedup)) {
                    result.add(archived)
                }
            }

        val smsApp = chatApps.firstOrNull { NotificationHubRepository.isSmsPackage(it.packageName) }
        contacts
            .filter {
                KeyboardSearchMatcher.matches(it.name, clean) ||
                    it.number.contains(clean, ignoreCase = true)
            }
            .take(8)
            .forEach { contact ->
                val dedup = "contact:${contact.name.lowercase()}"
                if (seenKeys.add(dedup) && result.none { it.contactName.equals(contact.name, ignoreCase = true) }) {
                    result.add(
                        LockedChatThread(
                            key = "contact:${contact.id}",
                            packageName = smsApp?.packageName ?: "com.android.mms",
                            appName = smsApp?.label ?: "SMS / Контакт",
                            contactName = contact.name,
                            messages = listOf(
                                HubChatMessage(
                                    sender = contact.name,
                                    text = "Тел: ${contact.number}",
                                    timestamp = System.currentTimeMillis()
                                )
                            ),
                            canReplyInline = false,
                            supportsMediaReply = false,
                            lastTimestamp = 0L,
                            timeFormatted = contact.number,
                            fallbackPhoneNumber = contact.number,
                            appIcon = smsApp?.icon
                        )
                    )
                }
            }
        return result
    }

    private fun notificationToLockedThread(n: HubNotificationItem): LockedChatThread {
        val msgs = if (n.messages.isNotEmpty()) {
            n.messages
        } else {
            val body = n.bigText.ifBlank { n.text }
            if (body.isNotBlank()) listOf(HubChatMessage(n.title, body, n.postTime)) else emptyList()
        }
        return LockedChatThread(
            key = n.key,
            packageName = n.packageName,
            appName = n.appName,
            contactName = n.title.ifBlank { n.appName },
            messages = msgs,
            canReplyInline = n.canReplyInline,
            supportsMediaReply = n.supportsMediaReply,
            lastTimestamp = n.postTime,
            timeFormatted = n.timeFormatted,
            appIcon = n.appIcon
        )
    }

    fun openLockedChat(thread: LockedChatThread) {
        _lockedChatThread.value = thread
        _isWriterModeOpen.value = false
        _activeTab.value = MainTab.HOME
    }

    fun openLockedChatInMessenger(thread: LockedChatThread) {
        if (!NotificationHubRepository.openChatThreadInMessenger(getApplication(), thread)) {
            if (!NotificationHubRepository.isSmsPackage(thread.packageName)) {
                launchPackage(thread.packageName)
            } else {
                showToast("Не удалось открыть чат в ${thread.appName}")
            }
        }
    }

    fun closeLockedChat() {
        _lockedChatThread.value = null
    }

    fun replyLockedChat(
        text: String,
        attachmentUri: Uri? = null,
        attachmentMimeType: String? = null
    ): Boolean {
        val active = _lockedChatThread.value ?: return false
        val isContactSms = active.key.startsWith("contact:") ||
            NotificationHubRepository.isSmsPackage(active.packageName)
        if (isContactSms && !active.fallbackPhoneNumber.isNullOrBlank()) {
            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${active.fallbackPhoneNumber}")).apply {
                if (text.isNotBlank()) putExtra("sms_body", text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return runCatching {
                getApplication<Application>().startActivity(smsIntent)
                showToast("Открыто SMS для ${active.contactName}")
                true
            }.getOrDefault(false)
        }
        if (active.canReplyInline && !active.key.startsWith("archive:")) {
            val ok = replyToHubNotification(active.key, text, attachmentUri, attachmentMimeType)
            if (ok && text.isNotBlank()) {
                val newMsg = HubChatMessage(
                    sender = "Вы",
                    text = text,
                    timestamp = System.currentTimeMillis()
                )
                _lockedChatThread.value = active.copy(messages = (active.messages + newMsg).takeLast(12))
            }
            if (ok) return true
        }
        val openedMessenger = NotificationHubRepository.openChatThreadInMessenger(
            context = getApplication(),
            thread = active,
            messageText = text
        )
        if (openedMessenger) {
            showToast("Открыто в ${active.appName}")
        } else {
            showToast("Откройте чат в ${active.appName} для ответа")
        }
        return openedMessenger
    }

    // 2D Multi-Workspace Separate Space
    fun openWorkspaceSpace() {
        _isWriterModeOpen.value = false
        _activeTab.value = MainTab.HOME
        _showWorkspaceSpace.value = true
    }

    fun closeWorkspaceSpace() {
        _showWorkspaceSpace.value = false
        KeyDeckAccessibilityService.hideDockedWorkspaceBar()
    }

    fun swipeToWorkspaceSpace(dx: Int = 1) {
        val dirX = if (dx >= 0) 1 else -1
        _isWriterModeOpen.value = false
        _activeTab.value = MainTab.HOME
        _showWorkspaceSpace.value = true
        viewModelScope.launch {
            workspaceMutex.withLock {
                val updated = prefsRepo.updateWorkspaceGridState { current ->
                    if (!current.isOnHome) {
                        current
                    } else {
                        val existingInDir = current.cells
                            .filter { !it.isHome && if (dirX > 0) it.x > 0 else it.x < 0 }
                            .minByOrNull { kotlin.math.abs(it.x) + kotlin.math.abs(it.y) * 10 }
                        if (existingInDir != null) {
                            current.copy(activeX = existingInDir.x, activeY = existingInDir.y)
                        } else {
                            val tx = dirX
                            val ty = 0
                            val nextCells = if (current.cells.any { it.x == tx && it.y == ty }) {
                                current.cells
                            } else {
                                current.cells + WorkspaceCell(tx, ty)
                            }
                            current.copy(cells = nextCells, activeX = tx, activeY = ty)
                        }
                    }
                }
                if (!updated.isOnHome) {
                    _workspaceActionFeedback.value = "Рабочий стол (${updated.activeX}, ${updated.activeY})"
                }
            }
        }
    }

    fun navigateWorkspace(dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        viewModelScope.launch {
            workspaceMutex.withLock {
                val updated = prefsRepo.updateWorkspaceGridState { current ->
                    val tx = current.activeX + dx
                    val ty = current.activeY + dy
                    val prevCell = current.activeCell
                    val prunedCells = if (!prevCell.isHome && !prevCell.hasAssignedApp) {
                        current.cells.filterNot { it.x == prevCell.x && it.y == prevCell.y }
                    } else {
                        current.cells
                    }
                    val exists = prunedCells.any { it.x == tx && it.y == ty }
                    val nextCells = if (exists || (tx == 0 && ty == 0)) {
                        prunedCells
                    } else {
                        prunedCells + WorkspaceCell(tx, ty)
                    }
                    current.copy(cells = nextCells, activeX = tx, activeY = ty)
                }
                if (updated.isOnHome) {
                    _showWorkspaceSpace.value = false
                    KeyDeckAccessibilityService.hideDockedWorkspaceBar()
                    _workspaceActionFeedback.value = "Главный рабочий стол (0, 0)"
                } else {
                    _showWorkspaceSpace.value = true
                    _workspaceActionFeedback.value = "Рабочий стол (${updated.activeX}, ${updated.activeY})"
                }
            }
        }
    }

    fun switchWorkspaceByGesture(dx: Int) {
        val dir = if (dx >= 0) 1 else -1
        viewModelScope.launch {
            val targetCell = workspaceMutex.withLock {
                val current = prefsRepo.workspaceGridState.first()
                val ordered = current.cells
                    .filter { it.isHome || it.hasAssignedApp }
                    .sortedWith(compareBy<WorkspaceCell> { it.y }.thenBy { it.x })
                if (ordered.size <= 1) {
                    current.activeCell
                } else {
                    val curIdx = ordered.indexOfFirst { it.x == current.activeX && it.y == current.activeY }
                        .let { if (it >= 0) it else 0 }
                    val nextIdx = (curIdx + dir + ordered.size) % ordered.size
                    val chosen = ordered[nextIdx]
                    prefsRepo.updateWorkspaceGridState { st ->
                        st.copy(activeX = chosen.x, activeY = chosen.y)
                    }
                    chosen
                }
            }
            if (targetCell.isHome) {
                openHomeWorkspace()
            } else {
                _showWorkspaceSpace.value = true
                launchWorkspaceCellFullscreen(targetCell)
            }
        }
    }

    fun createWorkspaceWithApp(app: AppEntry, dx: Int = 1, dy: Int = 0) {
        viewModelScope.launch {
            workspaceMutex.withLock {
                val updated = prefsRepo.updateWorkspaceGridState { current ->
                    var step = 1
                    var targetX = current.activeX + dx
                    var targetY = current.activeY + dy
                    while (current.cells.any { it.x == targetX && it.y == targetY } || (targetX == 0 && targetY == 0)) {
                        step++
                        targetX = current.activeX + dx * step
                        targetY = current.activeY + dy * step
                    }
                    val newCell = WorkspaceCell(
                        x = targetX,
                        y = targetY,
                        packageName = app.packageName,
                        activityName = app.activityName,
                        label = app.label
                    )
                    current.copy(
                        cells = current.cells + newCell,
                        activeX = targetX,
                        activeY = targetY
                    )
                }
                _contextMenuApp.value = null
                _isWriterModeOpen.value = false
                _activeTab.value = MainTab.HOME
                _appSearchQuery.value = ""
                _showWorkspaceSpace.value = true
                showToast("Стол (${updated.activeX}, ${updated.activeY}): ${app.label}")
            }
        }
    }

    fun assignAppToCurrentWorkspace(app: AppEntry) {
        viewModelScope.launch {
            workspaceMutex.withLock {
                val updated = prefsRepo.updateWorkspaceGridState { current ->
                    if (current.isOnHome) {
                        current
                    } else {
                        val updatedCells = current.cells.filterNot { it.x == current.activeX && it.y == current.activeY } +
                            WorkspaceCell(
                                x = current.activeX,
                                y = current.activeY,
                                packageName = app.packageName,
                                activityName = app.activityName,
                                label = app.label
                            )
                        current.copy(cells = updatedCells)
                    }
                }
                if (!updated.isOnHome) {
                    showToast("Назначено на стол (${updated.activeX}, ${updated.activeY}): ${app.label}")
                }
            }
        }
    }

    fun deleteWorkspace(x: Int = uiState.value.workspaceGrid.activeX, y: Int = uiState.value.workspaceGrid.activeY) {
        if (x == 0 && y == 0) {
            showToast("Главный стол (0, 0) удалить нельзя")
            return
        }
        viewModelScope.launch {
            workspaceMutex.withLock {
                prefsRepo.updateWorkspaceGridState { current ->
                    val remaining = current.cells.filterNot { it.x == x && it.y == y }
                    current.copy(cells = remaining, activeX = 0, activeY = 0)
                }
                _showWorkspaceSpace.value = false
                KeyDeckAccessibilityService.hideDockedWorkspaceBar()
                showToast("Рабочий стол ($x, $y) удалён")
            }
        }
    }

    fun openHomeWorkspace() {
        _showWorkspaceSpace.value = false
        KeyDeckAccessibilityService.hideDockedWorkspaceBar()
        viewModelScope.launch {
            workspaceMutex.withLock {
                prefsRepo.updateWorkspaceGridState { current ->
                    if (current.isOnHome) current else current.copy(activeX = 0, activeY = 0)
                }
            }
        }
    }

    fun launchCurrentWorkspaceAppInBounds(@Suppress("UNUSED_PARAMETER") bounds: Rect = Rect()) {
        val cell = uiState.value.workspaceGrid.activeCell
        launchWorkspaceCellFullscreen(cell)
    }

    private fun launchWorkspaceCellFullscreen(cell: WorkspaceCell) {
        val state = uiState.value
        val pkg = cell.packageName ?: return
        val app = state.allApps.firstOrNull {
            it.packageName == pkg && (cell.activityName == null || it.activityName == cell.activityName)
        } ?: state.allApps.firstOrNull { it.packageName == pkg }

        autoCloseManager.recordLaunchedPackage(pkg)
        val wsTitle = "${cell.label ?: app?.label ?: "Стол"} (${cell.x},${cell.y})"
        KeyDeckAccessibilityService.showDockedWorkspaceBar(wsTitle)

        val shizuku = state.shizukuState
        val hasPrivileged = (shizuku.isBinderAlive && shizuku.isPermissionGranted) || shizuku.isRootAvailable
        if (hasPrivileged) {
            viewModelScope.launch {
                val res = shizukuBridge.launchAppFullscreenViaShizuku(
                    packageName = pkg,
                    activityName = app?.activityName ?: cell.activityName
                )
                if (res.isFailure) {
                    if (app != null) appsRepo.launchApp(app) else appsRepo.launchPackage(pkg)
                }
            }
        } else {
            if (app != null) {
                appsRepo.launchApp(app)
            } else {
                appsRepo.launchPackage(pkg)
            }
        }
    }

    // BlackBerry Hub Notification Actions
    fun replyToHubNotification(
        key: String,
        text: String,
        attachmentUri: Uri? = null,
        attachmentMimeType: String? = null
    ): Boolean {
        val result = NotificationHubRepository.replyToNotification(
            context = getApplication(),
            key = key,
            replyText = text,
            attachmentUri = attachmentUri,
            attachmentMimeType = attachmentMimeType
        )
        result.onSuccess { msg ->
            showToast(msg)
            NotificationHubRepository.refreshFromAttachedService()
        }.onFailure { err ->
            showToast(err.message ?: "Не удалось отправить ответ")
        }
        return result.isSuccess
    }

    fun openHubNotification(key: String) {
        if (!NotificationHubRepository.openNotification(getApplication(), key)) {
            showToast("Уведомление не содержит ссылки для открытия")
        }
    }

    fun invokeHubNotificationAction(key: String, actionIndex: Int) {
        if (!NotificationHubRepository.invokeAction(key, actionIndex)) {
            showToast("Действие недоступно")
        } else {
            NotificationHubRepository.refreshFromAttachedService()
        }
    }

    fun dismissHubNotification(key: String) {
        NotificationHubRepository.dismissNotification(key)
    }

    fun refreshHubNotifications() {
        NotificationHubRepository.refreshFromAttachedService()
        shizukuBridge.refreshStatus()
    }

    // Shizuku & Interception
    fun requestShizukuPermission() {
        shizukuBridge.requestShizukuPermission()
    }

    fun openShizukuApp() {
        shizukuBridge.openShizukuManagerApp()
    }

    fun grantNotificationAccessViaShizuku() {
        viewModelScope.launch {
            val res = shizukuBridge.grantNotificationAccessViaShizuku()
            res.onSuccess {
                mediaRepo.refreshListenerStatusAndSessions()
                NotificationHubRepository.refreshFromAttachedService()
                showToast("Доступ к уведомлениям выдан через Shizuku")
            }.onFailure { err ->
                showToast(err.message ?: "Запустите Shizuku и выдайте разрешение")
            }
        }
    }

    fun toggleLockScreenInterception() {
        val target = !uiState.value.shizukuState.interceptLockScreenEnabled
        viewModelScope.launch {
            prefsRepo.setInterceptLockScreenEnabled(target)
            showToast(
                if (target) "Экран блокировки KeyDeck включён"
                else "Экран блокировки KeyDeck выключен"
            )
        }
    }

    fun openLockScreenPreview() {
        val intent = Intent(getApplication(), LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    fun launchFocusedApp() {
        val list = uiState.value.filteredApps
        val target = list.getOrNull(_focusedAppIndex.value) ?: list.firstOrNull() ?: return
        launchApp(target)
    }

    fun launchApp(app: AppEntry) {
        if (app.isLinuxTile) {
            openLinuxRunner(app.linuxPackageId ?: app.id.removePrefix("linux_tile:"))
            return
        }
        autoCloseManager.recordLaunchedPackage(app.packageName)
        if (!appsRepo.launchApp(app)) showToast("Не удалось открыть ${app.label}")
    }

    fun launchPackage(packageName: String) {
        autoCloseManager.recordLaunchedPackage(packageName)
        appsRepo.launchPackage(packageName)
    }

    fun togglePinApp(app: AppEntry) {
        viewModelScope.launch {
            prefsRepo.togglePinApp(app.id)
            showToast(if (app.isPinned) "${app.label} убран из избранного" else "${app.label} добавлен в избранное")
        }
    }

    fun togglePinFocusedApp() {
        val app = uiState.value.filteredApps.getOrNull(_focusedAppIndex.value) ?: return
        togglePinApp(app)
    }

    fun openContextMenu(app: AppEntry?) {
        _contextMenuApp.value = app
    }

    fun openRenameAppDialog(app: AppEntry?) {
        _contextMenuApp.value = null
        _renameTargetApp.value = app
    }

    fun saveAppRename(app: AppEntry, newLabel: String) {
        val cleaned = newLabel.trim()
        _renameTargetApp.value = null
        viewModelScope.launch {
            if (cleaned.isEmpty() || cleaned == app.originalLabel) {
                prefsRepo.setCustomAppLabel(app.id, null)
                showToast("Название сброшено: ${app.originalLabel}")
            } else {
                prefsRepo.setCustomAppLabel(app.id, cleaned)
                showToast("Переименовано в «$cleaned»")
            }
        }
    }

    fun resetAppRename(app: AppEntry) {
        _renameTargetApp.value = null
        viewModelScope.launch {
            prefsRepo.setCustomAppLabel(app.id, null)
            showToast("Название сброшено: ${app.originalLabel}")
        }
    }

    fun openIconPickerDialog(app: AppEntry?) {
        _contextMenuApp.value = null
        _iconPickerTargetApp.value = app
    }

    fun setAppIconAuto(app: AppEntry) {
        _iconPickerTargetApp.value = null
        viewModelScope.launch {
            prefsRepo.setAppIconOverride(app.id, null)
            showToast("Иконка «${app.label}»: Авто (Symbian)")
        }
    }

    fun setAppIconOriginal(app: AppEntry) {
        _iconPickerTargetApp.value = null
        viewModelScope.launch {
            prefsRepo.setAppIconOverride(app.id, AppIconOverride(mode = AppIconMode.ORIGINAL))
            showToast("Иконка «${app.label}»: Оригинал Android")
        }
    }

    fun setAppIconSymbian(app: AppEntry, symbianKey: String) {
        _iconPickerTargetApp.value = null
        viewModelScope.launch {
            prefsRepo.setAppIconOverride(app.id, AppIconOverride(mode = AppIconMode.SYMBIAN, symbianKey = symbianKey))
            showToast("Установлена иконка Symbian для «${app.label}»")
        }
    }

    fun setAppIconCustomFromUri(app: AppEntry, sourceUri: Uri) {
        _iconPickerTargetApp.value = null
        viewModelScope.launch {
            appsRepo.importCustomAppIcon(app.id, sourceUri).onSuccess { path ->
                prefsRepo.setAppIconOverride(app.id, AppIconOverride(mode = AppIconMode.CUSTOM, customFilePath = path))
                showToast("Своя иконка установлена для «${app.label}»")
            }.onFailure { err ->
                showToast(err.message ?: "Не удалось загрузить изображение")
            }
        }
    }

    fun getSymbianIconPreview(resId: Int) = appsRepo.getSymbianBitmap(resId)

    fun shareAppAsApk(app: AppEntry) {
        _contextMenuApp.value = null
        showToast("Подготовка APK: ${app.label}…")
        viewModelScope.launch {
            appsRepo.shareAppAsApk(app).onFailure { err ->
                showToast(err.message ?: "Не удалось поделиться APK")
            }
        }
    }

    fun getSuggestedApkName(app: AppEntry): String = appsRepo.buildSuggestedApkName(app)

    fun saveAppApkToUri(app: AppEntry, destUri: Uri) {
        _contextMenuApp.value = null
        showToast("Сохранение APK: ${app.label}…")
        viewModelScope.launch {
            appsRepo.saveAppApkToUri(app, destUri).onSuccess {
                showToast("APK сохранён: ${app.originalLabel}.apk")
            }.onFailure { err ->
                showToast(err.message ?: "Не удалось сохранить APK")
            }
        }
    }

    fun openAppInfo(app: AppEntry) {
        _contextMenuApp.value = null
        appsRepo.openAppInfo(app.packageName)
    }

    fun uninstallApp(app: AppEntry) {
        _contextMenuApp.value = null
        if (appsRepo.canBeUninstalled(app.packageName)) {
            if (!appsRepo.uninstallApp(app.packageName)) showToast("Не удалось открыть системное окно удаления")
            return
        }
        val state = uiState.value.shizukuState
        val hasPrivileged = (state.isBinderAlive && state.isPermissionGranted) || state.isRootAvailable
        if (hasPrivileged) {
            viewModelScope.launch {
                appsRepo.uninstallPackagePrivileged(app.packageName, shizukuBridge).onSuccess {
                    showToast("Удалено через Shizuku/Root: ${app.label}")
                    refreshInstalledApps()
                }.onFailure {
                    appsRepo.openAppInfo(app.packageName)
                    showToast("Системное приложение — открыта информация")
                }
            }
        } else {
            appsRepo.openAppInfo(app.packageName)
            showToast("Системное приложение — открыта карточка приложения")
        }
    }

    fun launchCamera() {
        uiState.value.allApps
            .filter { AutoCloseManager.isHeuristicOneTimeApp(it.packageName, it.originalLabel) }
            .forEach { autoCloseManager.recordLaunchedPackage(it.packageName) }
        if (!appsRepo.launchCamera()) showToast("Камера не найдена на устройстве")
    }

    fun placeCall(numberOrQuery: String) {
        uiState.value.allApps
            .filter { AutoCloseManager.isDialerPackage(it.packageName) }
            .forEach { autoCloseManager.recordLaunchedPackage(it.packageName) }
        val trimmed = numberOrQuery.trim()
        if (trimmed.isEmpty()) {
            val fallback = uiState.value.callLogs.firstOrNull()?.number
                ?: uiState.value.contacts.firstOrNull()?.number.orEmpty()
            if (fallback.isNotEmpty()) callsRepo.placeCall(fallback)
            else showToast("Введите номер или выберите контакт")
            return
        }
        val firstContactMatch = uiState.value.contacts.firstOrNull {
            KeyboardSearchMatcher.matches(it.name, trimmed)
        }
        val dialTarget = if (trimmed.any { it.isDigit() } && trimmed.none { it.isLetter() }) {
            trimmed
        } else {
            firstContactMatch?.number ?: trimmed
        }
        if (!callsRepo.placeCall(dialTarget)) showToast("Не удалось совершить вызов")
    }

    fun openAddContact() = callsRepo.openAddContact(_callsSearchQuery.value)

    fun openUrl(url: String) {
        if (!appsRepo.openUrl(url)) showToast("Не найден браузер для открытия ссылки")
    }

    fun openDefaultLauncherSettings() = appsRepo.openSystemSettings(Settings.ACTION_HOME_SETTINGS)
    fun openSystemSettings(action: String) = appsRepo.openSystemSettings(action)

    fun setShowAddLinkDialog(show: Boolean) {
        _showAddLinkDialog.value = show
    }

    fun addHubQuickLink(title: String, url: String, category: HubSubTab) {
        if (title.isBlank() || url.isBlank()) return
        viewModelScope.launch {
            prefsRepo.addHubQuickLink(title.trim(), url.trim(), category)
            _showAddLinkDialog.value = false
            showToast("Ссылка «${title.trim()}» добавлена")
        }
    }

    fun removeHubQuickLink(id: String) {
        viewModelScope.launch { prefsRepo.removeHubQuickLink(id) }
    }

    // Music Controls & Collapsible Mini-Player
    fun setPlayerCollapsed(collapsed: Boolean) {
        viewModelScope.launch {
            if (collapsed && _showMusicSheet.value) _showMusicSheet.value = false
            prefsRepo.setPlayerCollapsed(collapsed)
        }
    }

    fun togglePlayerCollapsed() = setPlayerCollapsed(!uiState.value.isPlayerCollapsed)

    fun togglePlayPause() = mediaRepo.togglePlayPause()
    fun skipNext() = mediaRepo.skipNext()
    fun skipPrevious() = mediaRepo.skipPrevious()
    fun seekTo(positionMs: Long) = mediaRepo.seekTo(positionMs)
    fun seekBy(deltaMs: Long) = mediaRepo.seekBy(deltaMs)
    fun selectMediaSession(sessionId: String) = mediaRepo.selectSession(sessionId)

    fun swipeMusicSource(direction: Int) {
        val state = uiState.value
        val sessions = state.mediaState.sessions
        if (sessions.size > 1) {
            mediaRepo.cycleSession(direction)
            mediaRepo.mediaState.value.activeSession?.let { showToast("Плеер: ${it.appLabel}") }
            return
        }
        val musicApps = state.musicApps
        val candidates = if (sessions.size == 1) {
            val activePkg = sessions.first().packageName
            musicApps.filter { it.packageName != activePkg }.ifEmpty { musicApps }
        } else {
            musicApps
        }
        if (candidates.isEmpty()) {
            showToast("Нет других плееров для переключения")
            return
        }
        val step = if (direction >= 0) 1 else -1
        musicAppRotationIndex = ((musicAppRotationIndex + step) % candidates.size + candidates.size) % candidates.size
        val targetApp = candidates[musicAppRotationIndex]
        showToast("Источник музыки: ${targetApp.label}")
        launchApp(targetApp)
    }

    fun cycleMediaSession() = swipeMusicSource(+1)

    fun openActivePlayer() {
        if (!mediaRepo.openActivePlayer()) {
            val firstMusicApp = uiState.value.musicApps.firstOrNull()
            if (firstMusicApp != null) launchApp(firstMusicApp)
            else showToast("Музыкальный плеер не найден")
        }
    }

    fun adjustVolume(delta: Int) = mediaRepo.adjustVolume(delta)
    fun setVolume(volume: Int) = mediaRepo.setVolume(volume)
    fun toggleMute() = mediaRepo.toggleMute()
    fun openNotificationListenerSettings() = mediaRepo.openNotificationListenerSettings()

    fun updateLocalTrackSearchQuery(query: String) {
        _localTrackSearchQuery.value = query
        mediaRepo.searchConnectedMediaBrowser(query)
    }

    fun selectMusicDrawerTab(tab: MusicDrawerTab) {
        _musicDrawerTab.value = tab
    }

    fun skipToQueueItem(queueId: Long) = mediaRepo.skipToQueueItem(queueId)
    fun invokeMediaCustomAction(action: String) = mediaRepo.invokeCustomAction(action)

    fun playBrowserTrack(track: MediaBrowserTrack) {
        if (!mediaRepo.playBrowserTrack(track)) showToast("Не удалось запустить «${track.title}»")
    }

    fun searchAndPlayInActiveStream(query: String, packageName: String? = null) {
        val targetQuery = query.trim()
        if (targetQuery.isEmpty()) return
        viewModelScope.launch { prefsRepo.addStreamSearchHistory(targetQuery) }
        if (mediaRepo.playFromSearchInStream(targetQuery, packageName)) {
            showToast("Поиск в стриминге: «$targetQuery»")
        } else {
            mediaRepo.searchConnectedMediaBrowser(targetQuery)
            showToast("Откройте медиасессию плеера или выберите сервис ниже")
        }
    }

    fun connectBrowserForPackage(packageName: String?) = mediaRepo.connectMediaBrowserForActiveOrPackage(packageName)

    fun playLocalAudioTrack(track: LocalAudioTrack) {
        mediaRepo.playLocalTrack(track)
        showToast("Воспроизведение: ${track.title}")
    }

    fun deleteLocalAudioTrack(track: LocalAudioTrack) {
        viewModelScope.launch {
            val deleted = mediaRepo.deleteLocalTrack(track)
            if (deleted) {
                val refreshed = mediaRepo.localTracks.value
                prefsRepo.updateSmartAlbums { albums ->
                    albums.map { alb ->
                        val cleanedAttached = alb.attachedLocalTrackIds.filterNot { it == track.id }
                        MusicCatalogAndLyricsRepository.reconcileAlbumTracks(
                            alb.copy(attachedLocalTrackIds = cleanedAttached),
                            refreshed
                        )
                    }
                }
            }
            showToast(
                if (deleted) "Файл удалён: ${track.title}"
                else "Не удалось удалить «${track.title}» (ограничение доступа)"
            )
        }
    }

    fun deleteActiveTrackIfLocal() {
        val currentId = uiState.value.mediaState.currentLocalTrackId
        val localTrack = currentId?.let { id -> uiState.value.localAudioTracks.firstOrNull { it.id == id } }
        if (localTrack != null) {
            deleteLocalAudioTrack(localTrack)
        } else {
            showToast("Удаление файла доступно для локальных и скачанных треков")
        }
    }

    fun renameLocalAudioTrack(track: LocalAudioTrack, newTitle: String) {
        viewModelScope.launch {
            val renamed = mediaRepo.renameLocalTrack(track, newTitle)
            if (renamed != null) {
                if (renamed.id != track.id) {
                    prefsRepo.updateSmartAlbums { albums ->
                        albums.map { alb ->
                            if (alb.attachedLocalTrackIds.contains(track.id)) {
                                alb.copy(
                                    attachedLocalTrackIds = alb.attachedLocalTrackIds.map {
                                        if (it == track.id) renamed.id else it
                                    }.distinct()
                                )
                            } else alb
                        }
                    }
                }
                reconcileAllSmartAlbumsWithTracks(mediaRepo.localTracks.value)
                showToast("Переименовано в «${renamed.title}»")
            } else {
                showToast("Не удалось переименовать файл")
            }
        }
    }

    fun openTrackContextMenu(track: LocalAudioTrack?, albumContext: SmartAlbum? = null) {
        _contextMenuTrack.value = track to albumContext
    }

    fun openTrackMetadataEditor(track: LocalAudioTrack?) {
        metadataLoadJob?.cancel()
        if (track == null) {
            _editingMetadataTrack.value = null to null
            return
        }
        _editingMetadataTrack.value = track to null
        metadataLoadJob = viewModelScope.launch {
            val loaded = AudioFileMetadataRepository.readTrackMetadata(getApplication(), track)
            if (_editingMetadataTrack.value.first?.id == track.id) {
                _editingMetadataTrack.value = track to loaded
            }
        }
    }

    fun openTrackMetadataForDeviceFile(file: DeviceSearchFile) {
        val existing = uiState.value.localAudioTracks.firstOrNull { tr ->
            (!tr.filePath.isNullOrBlank() && tr.filePath == file.localFilePath) ||
                tr.uriString == file.uriString
        }
        if (existing != null) {
            openTrackMetadataEditor(existing)
            return
        }
        val ext = file.name.substringAfterLast('.', "").uppercase().ifBlank { "AUDIO" }
        val synthetic = LocalAudioTrack(
            id = if (!file.localFilePath.isNullOrBlank()) "fs:${file.localFilePath}" else "file:${file.uriString}",
            title = file.name.substringBeforeLast('.').ifBlank { file.name },
            artist = "",
            album = "",
            durationMs = 0L,
            uriString = file.uriString,
            filePath = file.localFilePath,
            displayPath = file.displayPath,
            formatLabel = ext
        )
        openTrackMetadataEditor(synthetic)
    }

    fun saveTrackFileMetadata(
        track: LocalAudioTrack,
        title: String,
        artist: String,
        album: String,
        newCoverUri: Uri?,
        clearEmbeddedCover: Boolean
    ) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val coverBytes = if (newCoverUri != null) {
                AudioFileMetadataRepository.decodeAndScaleCoverBytes(app, newCoverUri)
            } else {
                null
            }
            val result = AudioFileMetadataRepository.writeTrackMetadata(
                context = app,
                track = track,
                update = AudioFileMetadataUpdate(
                    title = title,
                    artist = artist,
                    album = album,
                    newCoverBytes = coverBytes,
                    clearEmbeddedCover = clearEmbeddedCover
                )
            )
            result.onSuccess { updatedTrack ->
                mediaRepo.updateTrackMetadataInMemory(track, updatedTrack)
                val oldKeys = MusicCatalogAndLyricsRepository.buildTrackCoverLookupKeys(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist
                )
                val newKeys = MusicCatalogAndLyricsRepository.buildTrackCoverLookupKeys(
                    trackId = updatedTrack.id,
                    title = updatedTrack.title,
                    artist = updatedTrack.artist
                )
                val mergedKeys = (oldKeys + newKeys).distinct()
                if (coverBytes != null) {
                    val cachedPath = AudioFileMetadataRepository.cacheCoverBytesToFile(
                        context = app,
                        keyHint = MusicCatalogAndLyricsRepository.buildTrackKey(updatedTrack.title, updatedTrack.artist),
                        coverBytes = coverBytes
                    )
                    if (!cachedPath.isNullOrBlank()) {
                        prefsRepo.saveTrackCoverOverride(mergedKeys, cachedPath)
                    }
                } else if (clearEmbeddedCover) {
                    prefsRepo.clearTrackCoverOverride(mergedKeys)
                } else if (track.title != updatedTrack.title || track.artist != updatedTrack.artist) {
                    val existingPath = MusicCatalogAndLyricsRepository.resolveTrackCoverPath(
                        overrides = uiState.value.trackCoverOverrides,
                        trackId = track.id,
                        title = track.title,
                        artist = track.artist
                    )
                    if (!existingPath.isNullOrBlank()) {
                        prefsRepo.saveTrackCoverOverride(newKeys, existingPath)
                    }
                }
                _editingMetadataTrack.value = null to null
                reconcileAllSmartAlbumsWithTracks(mediaRepo.localTracks.value)
                showToast("Теги и обложка файла сохранены: ${updatedTrack.title}")
            }.onFailure { err ->
                showToast(err.message ?: "Не удалось записать теги в аудиофайл")
            }
        }
    }

    fun refreshLocalAudioTracks() {
        viewModelScope.launch {
            val tracks = mediaRepo.refreshLocalTracks()
            reconcileAllSmartAlbumsWithTracks(tracks)
            mediaRepo.connectMediaBrowserForActiveOrPackage(
                mediaRepo.mediaState.value.activeSession?.packageName
            )
        }
    }

    fun toggleMusicDrawer() = setShowMusicSheet(!_showMusicSheet.value)

    fun setShowMusicSheet(show: Boolean) {
        _showMusicSheet.value = show
        if (show) {
            mediaRepo.refreshListenerStatusAndSessions()
            refreshStorageAndAudioAccess()
        }
    }

    fun expandMusicStep() {
        val state = uiState.value
        when {
            state.isPlayerCollapsed -> setPlayerCollapsed(false)
            !state.showMusicSheet -> setShowMusicSheet(true)
            else -> openFullPowerampPlayer(PowerampTab.PLAYER)
        }
    }

    fun openFullPowerampPlayer(tab: PowerampTab? = null) {
        if (tab != null) _powerampTab.value = tab
        _showMusicSheet.value = false
        _showFullPowerampPlayer.value = true
        mediaRepo.refreshListenerStatusAndSessions()
        refreshStorageAndAudioAccess()
        loadLyricsForCurrentTrack(forceRefresh = false)
    }

    fun closeFullPowerampPlayer() {
        mediaRepo.stopCandidatePreview()
        _showFullPowerampPlayer.value = false
        _sourcePickerTarget.value = null to false
    }

    fun selectPowerampTab(tab: PowerampTab) {
        _powerampTab.value = tab
        if (tab == PowerampTab.LYRICS && _currentLyrics.value == null) {
            loadLyricsForCurrentTrack(forceRefresh = false)
        }
    }

    fun setMusicSourceMode(mode: SourceSelectionMode) {
        viewModelScope.launch {
            prefsRepo.setMusicSourceMode(mode)
            showToast("Выбор источника: ${mode.title}")
        }
    }

    fun toggleMusicSourceMode() {
        val next = if (uiState.value.sourceSelectionMode == SourceSelectionMode.AUTO) {
            SourceSelectionMode.PICK
        } else {
            SourceSelectionMode.AUTO
        }
        setMusicSourceMode(next)
    }

    fun openSourcePicker(result: UnifiedMusicSearchResult?, forDownload: Boolean = false) {
        if (result == null) {
            mediaRepo.stopCandidatePreview()
        }
        _sourcePickerTarget.value = result to forDownload
    }

    fun toggleCandidatePreview(candidate: TrackSourceCandidate) {
        mediaRepo.toggleCandidatePreview(candidate)
    }

    fun stopCandidatePreview() {
        mediaRepo.stopCandidatePreview()
    }

    fun searchMusicEverywhere(query: String) {
        _unifiedMusicQuery.value = query
        onlineMusicSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _unifiedMusicResults.value = emptyList()
            _isSearchingMusicOnline.value = false
            return
        }
        onlineMusicSearchJob = viewModelScope.launch {
            _isSearchingMusicOnline.value = true
            delay(220L)
            val state = uiState.value
            val found = MusicCatalogAndLyricsRepository.searchAllSources(
                query = trimmed,
                localTracks = state.localAudioTracks,
                mediaState = state.mediaState,
                installedMusicApps = state.musicApps
            )
            _unifiedMusicResults.value = found
            _isSearchingMusicOnline.value = false
        }
    }

    fun handleUnifiedTrackAction(result: UnifiedMusicSearchResult, downloadAction: Boolean) {
        val mode = uiState.value.sourceSelectionMode
        if (mode == SourceSelectionMode.PICK && result.hasMultipleSources) {
            openSourcePicker(result, forDownload = downloadAction)
            return
        }
        val best = result.primaryCandidate
        if (downloadAction) {
            viewModelScope.launch {
                val outcome = MusicCatalogAndLyricsRepository.downloadTrackByPriority(
                    context = getApplication(),
                    title = result.title,
                    artist = result.artist,
                    candidates = result.candidates,
                    installedMusicApps = uiState.value.musicApps
                )
                handleDownloadOutcome(outcome, albumId = null)
            }
        } else if (best != null) {
            playTrackSourceCandidate(best)
        }
    }

    fun playTrackSourceCandidate(candidate: TrackSourceCandidate) {
        _sourcePickerTarget.value = null to false
        when {
            candidate.localTrack != null -> playLocalAudioTrack(candidate.localTrack)
            candidate.queueItemId != null && !candidate.packageName.isNullOrBlank() -> {
                val targetSession = uiState.value.mediaState.sessions.firstOrNull { it.packageName == candidate.packageName }
                if (targetSession != null) {
                    mediaRepo.selectSession(targetSession.id)
                    mediaRepo.skipToQueueItem(candidate.queueItemId)
                } else {
                    searchAndPlayInActiveStream(candidate.title, candidate.packageName)
                }
            }
            candidate.mediaBrowserTrack != null -> playBrowserTrack(candidate.mediaBrowserTrack)
            candidate.sourceKind == MusicSourceKind.STREAMING_PAID && !candidate.packageName.isNullOrBlank() -> {
                val query = listOf(candidate.artist, candidate.title).filter { it.isNotBlank() }.joinToString(" ")
                if (!mediaRepo.playFromSearchInStream(query, candidate.packageName)) {
                    launchPackage(candidate.packageName)
                    showToast("Открыт ${candidate.sourceLabel}: «$query»")
                } else {
                    showToast("Запуск в ${candidate.sourceLabel}: «${candidate.title}»")
                }
            }
            !candidate.downloadUrl.isNullOrBlank() -> {
                downloadTrackSourceCandidate(candidate, autoPlay = true)
            }
            else -> searchAndPlayInActiveStream(candidate.title, candidate.packageName)
        }
    }

    fun downloadTrackSourceCandidate(
        candidate: TrackSourceCandidate,
        autoPlay: Boolean = false,
        attachToAlbumId: String? = null
    ) {
        mediaRepo.stopCandidatePreview()
        _sourcePickerTarget.value = null to false
        viewModelScope.launch {
            if (candidate.sourceKind == MusicSourceKind.STREAMING_PAID && !candidate.packageName.isNullOrBlank()) {
                requestStreamCaptureForTrack(
                    title = candidate.title,
                    artist = candidate.artist,
                    packageName = candidate.packageName,
                    appLabel = candidate.sourceLabel,
                    albumId = attachToAlbumId
                )
                return@launch
            }
            if (candidate.localTrack != null) {
                if (autoPlay) playLocalAudioTrack(candidate.localTrack)
                else showToast("Трек уже на устройстве: ${candidate.localTrack.title}")
                return@launch
            }
            showToast("Скачивание ${candidate.formatBadge}: ${candidate.title}…")
            val outcome = MusicCatalogAndLyricsRepository.downloadDirectCandidate(getApplication(), candidate)
            if (outcome != null) {
                val saved = outcome.track
                mediaRepo.registerDownloadedTrack(saved)
                val tracks = mediaRepo.refreshLocalTracks()
                val syncedSaved = tracks.firstOrNull { it.filePath == saved.filePath } ?: saved
                if (!outcome.coverPath.isNullOrBlank()) {
                    val keys = MusicCatalogAndLyricsRepository.buildTrackCoverLookupKeys(
                        trackId = syncedSaved.id,
                        title = syncedSaved.title,
                        artist = syncedSaved.artist
                    )
                    prefsRepo.saveTrackCoverOverride(keys, outcome.coverPath)
                }
                if (attachToAlbumId != null) {
                    attachLocalTracksToAlbum(attachToAlbumId, listOf(syncedSaved.id))
                }
                if (autoPlay) playLocalAudioTrack(syncedSaved)
                else showToast(outcome.message)
            } else {
                showToast("Источник не отдал файл, пробуем резервный поиск по сайтам…")
                val outcomeFallback = MusicCatalogAndLyricsRepository.downloadTrackByPriority(
                    context = getApplication(),
                    title = candidate.title,
                    artist = candidate.artist,
                    candidates = listOf(candidate),
                    installedMusicApps = uiState.value.musicApps,
                    expectedDurationMs = candidate.durationMs,
                    albumCoverUrl = candidate.artworkUrl,
                    expectedAlbum = candidate.album,
                    preferDirectOnlineOverStreaming = true
                )
                handleDownloadOutcome(outcomeFallback, albumId = attachToAlbumId)
            }
        }
    }

    private suspend fun handleDownloadOutcome(outcome: DownloadOutcome, albumId: String?) {
        when (outcome) {
            is DownloadOutcome.DownloadedFile -> {
                mediaRepo.registerDownloadedTrack(outcome.track)
                val tracks = mediaRepo.refreshLocalTracks()
                val matched = tracks.firstOrNull { it.filePath == outcome.track.filePath } ?: outcome.track
                if (!outcome.coverPath.isNullOrBlank()) {
                    val keys = MusicCatalogAndLyricsRepository.buildTrackCoverLookupKeys(
                        trackId = matched.id,
                        title = matched.title,
                        artist = matched.artist
                    )
                    prefsRepo.saveTrackCoverOverride(keys, outcome.coverPath)
                }
                if (albumId != null) {
                    attachLocalTracksToAlbum(albumId, listOf(matched.id))
                }
                showToast(outcome.message)
            }
            is DownloadOutcome.NeedsStreamingCapture -> {
                requestStreamCaptureForTrack(
                    title = outcome.title,
                    artist = outcome.artist,
                    packageName = outcome.packageName,
                    appLabel = outcome.appLabel,
                    albumId = albumId
                )
            }
            is DownloadOutcome.Failed -> showToast(outcome.reason)
        }
    }

    fun requestStreamCaptureForTrack(
        title: String,
        artist: String,
        packageName: String,
        appLabel: String,
        albumId: String? = null
    ) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            showToast("Захват звука стриминга доступен с Android 10+")
            return
        }
        _pendingStreamCaptureRequest.value = PendingStreamCaptureRequest(
            title = title,
            artist = artist,
            packageName = packageName,
            appLabel = appLabel,
            targetAlbumId = albumId
        )
    }

    fun clearPendingStreamCaptureRequest() {
        _pendingStreamCaptureRequest.value = null
    }

    fun toggleCaptureAutoSplit() {
        val next = !_captureAutoSplitEnabled.value
        _captureAutoSplitEnabled.value = next
        StreamAudioCaptureRuntime.setCaptureModes(next, _captureSilentMuteMode.value)
        showToast(if (next) "Авто-нарезка коллекции включена" else "Авто-нарезка коллекции выключена")
    }

    fun toggleCaptureSilentMute() {
        val next = !_captureSilentMuteMode.value
        _captureSilentMuteMode.value = next
        StreamAudioCaptureRuntime.setCaptureModes(_captureAutoSplitEnabled.value, next)
        showToast(if (next) "Тихий захват (без звука) включён" else "Тихий захват выключен")
    }

    fun onMediaProjectionResult(resultCode: Int, data: Intent?) {
        val req = _pendingStreamCaptureRequest.value
        _pendingStreamCaptureRequest.value = null
        if (req == null) return
        if (resultCode != android.app.Activity.RESULT_OK || data == null) {
            showToast("Захват потока отменён")
            return
        }
        val matchingSession = mediaRepo.mediaState.value.sessions.firstOrNull { sess ->
            sess.packageName == req.packageName &&
                MusicCatalogAndLyricsRepository.normalizeKeyText(sess.title) ==
                MusicCatalogAndLyricsRepository.normalizeKeyText(req.title)
        } ?: mediaRepo.mediaState.value.activeSession?.takeIf { it.packageName == req.packageName }
        val expectedDurationMs = matchingSession?.durationMs ?: 0L

        StreamAudioCaptureService.startCapture(
            context = getApplication(),
            resultCode = resultCode,
            data = data,
            title = req.title,
            artist = req.artist,
            packageName = req.packageName,
            targetAlbumId = req.targetAlbumId,
            expectedDurationMs = expectedDurationMs,
            autoSplitEnabled = _captureAutoSplitEnabled.value,
            silentMuteMode = _captureSilentMuteMode.value
        )
        if (matchingSession != null) {
            mediaRepo.selectSession(matchingSession.id)
            mediaRepo.seekTo(0L)
            if (!matchingSession.isPlaying) {
                mediaRepo.togglePlayPause()
            }
        } else {
            val searchQ = listOf(req.artist, req.title).filter { it.isNotBlank() }.joinToString(" ")
            if (!mediaRepo.playFromSearchInStream(searchQ, req.packageName)) {
                if (req.packageName.isNotBlank()) launchPackage(req.packageName)
            }
            viewModelScope.launch {
                delay(450L)
                mediaRepo.seekTo(0L)
            }
        }
        showToast("Захват ${req.appLabel} с 00:00 → WAV: «${req.title}»")
    }

    fun stopStreamCapture() {
        StreamAudioCaptureService.stopCapture(getApplication())
        showToast("Захват потока остановлен, проверяем запись WAV…")
    }

    // Synced LRC Lyrics + In-App Editor + Persistent Cache
    fun loadLyricsForCurrentTrack(forceRefresh: Boolean = false) {
        val session = uiState.value.mediaState.activeSession ?: return
        if (session.title.isBlank() || session.title == session.appLabel) return
        val key = MusicCatalogAndLyricsRepository.buildTrackKey(session.title, session.artist)
        val localTrack = uiState.value.mediaState.currentLocalTrackId?.let { id ->
            uiState.value.localAudioTracks.firstOrNull { it.id == id }
        }

        lyricsFetchJob?.cancel()
        lyricsFetchJob = viewModelScope.launch {
            _isFetchingLyrics.value = true
            val overrides = runCatching { prefsRepo.getLyricsOverridesNow() }.getOrDefault(emptyMap())
            val saved = overrides[key]
            if (!forceRefresh && saved != null) {
                _currentLyrics.value = if (saved.isDeleted) null else saved
                _isFetchingLyrics.value = false
                return@launch
            }
            val fetched = MusicCatalogAndLyricsRepository.fetchSyncedLyrics(
                title = session.title,
                artist = session.artist,
                durationMs = session.durationMs,
                localFilePath = if (forceRefresh) null else localTrack?.filePath,
                attemptIndex = if (forceRefresh) lyricsRetryAttempt else 0,
                excludeRawLrc = if (forceRefresh) lastRejectedLyricsRaw else null
            )
            _currentLyrics.value = fetched
            _isFetchingLyrics.value = false
            if (fetched != null && fetched.hasAnyLyrics) {
                prefsRepo.saveLyricsOverride(fetched)
                if (localTrack?.filePath != null && fetched.rawLrc.isNotBlank()) {
                    MusicCatalogAndLyricsRepository.saveSidecarLrc(localTrack.filePath, fetched.rawLrc)
                }
            }
        }
    }

    fun saveEditedLyrics(rawLrcOrText: String) {
        val session = uiState.value.mediaState.activeSession ?: return
        val key = MusicCatalogAndLyricsRepository.buildTrackKey(session.title, session.artist)
        val trimmed = rawLrcOrText.trim()
        val parsed = MusicCatalogAndLyricsRepository.parseLrc(trimmed)
        val plain = if (parsed.isNotEmpty()) parsed.joinToString("\n") { it.text } else trimmed
        val entry = TrackLyricsData(
            trackKey = key,
            title = session.title,
            artist = session.artist,
            syncedLines = parsed,
            plainText = plain,
            rawLrc = trimmed,
            sourceLabel = "Редактор KeyDeck",
            isDeleted = false,
            isCustomEdited = true,
            isConfirmedPrimary = true
        )
        _currentLyrics.value = entry
        val localTrack = uiState.value.mediaState.currentLocalTrackId?.let { id ->
            uiState.value.localAudioTracks.firstOrNull { it.id == id }
        }
        viewModelScope.launch {
            prefsRepo.saveLyricsOverride(entry)
            if (localTrack?.filePath != null && trimmed.isNotEmpty()) {
                MusicCatalogAndLyricsRepository.saveSidecarLrc(localTrack.filePath, trimmed)
            }
            showToast(if (parsed.isNotEmpty()) "Синхронный текст (.lrc) сохранён" else "Текст трека сохранён")
        }
    }

    fun deleteCurrentLyrics() {
        val session = uiState.value.mediaState.activeSession ?: return
        val key = MusicCatalogAndLyricsRepository.buildTrackKey(session.title, session.artist)
        val localTrack = uiState.value.mediaState.currentLocalTrackId?.let { id ->
            uiState.value.localAudioTracks.firstOrNull { it.id == id }
        }
        _currentLyrics.value = null
        viewModelScope.launch {
            MusicCatalogAndLyricsRepository.deleteSidecarLrc(localTrack?.filePath)
            prefsRepo.saveLyricsOverride(
                TrackLyricsData(
                    trackKey = key,
                    title = session.title,
                    artist = session.artist,
                    isDeleted = true
                )
            )
            showToast("Текст трека удалён")
        }
    }

    fun autoPickLyricsAgain() {
        lastRejectedLyricsRaw = _currentLyrics.value?.rawLrc ?: _currentLyrics.value?.plainText
        lyricsRetryAttempt++
        loadLyricsForCurrentTrack(forceRefresh = true)
        showToast("Поиск альтернативного источника текста…")
    }

    fun toggleConfirmPrimaryLyrics() {
        val cur = _currentLyrics.value ?: return
        if (!cur.hasAnyLyrics) return
        val session = uiState.value.mediaState.activeSession
        val localTrack = uiState.value.mediaState.currentLocalTrackId?.let { id ->
            uiState.value.localAudioTracks.firstOrNull { it.id == id }
        }
        if (!cur.isConfirmedPrimary) {
            val confirmed = cur.copy(isConfirmedPrimary = true)
            _currentLyrics.value = confirmed
            viewModelScope.launch {
                prefsRepo.saveLyricsOverride(confirmed)
                if (localTrack?.filePath != null && confirmed.rawLrc.isNotBlank()) {
                    MusicCatalogAndLyricsRepository.saveSidecarLrc(localTrack.filePath, confirmed.rawLrc)
                }
            }
            showToast("Текст подтверждён как основной ✓")
        } else {
            val unconfirmed = cur.copy(isConfirmedPrimary = false, isCustomEdited = false)
            _currentLyrics.value = unconfirmed
            viewModelScope.launch {
                prefsRepo.saveLyricsOverride(unconfirmed)
            }
            if (session != null && session.title.isNotBlank()) {
                lastRejectedLyricsRaw = cur.rawLrc.ifBlank { cur.plainText }
                lyricsRetryAttempt++
                loadLyricsForCurrentTrack(forceRefresh = true)
                showToast("Подтверждение снято — запущен автопоиск другого текста")
            } else {
                showToast("Подтверждение текста снято")
            }
        }
    }

    // Poweramp DSP Equalizer & Playback Controls
    private fun persistEqualizerState() {
        val eq = mediaRepo.dspController.state.value
        viewModelScope.launch {
            prefsRepo.saveEqualizerConfig(
                LauncherPreferencesRepository.SavedEqualizerConfig(
                    enabled = eq.enabled,
                    preset = eq.preset,
                    bandsMb = eq.bands.map { it.levelMb },
                    bassBoost = eq.bassBoostStrength,
                    virtualizer = eq.virtualizerStrength
                )
            )
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        mediaRepo.dspController.setEnabled(enabled)
        persistEqualizerState()
    }

    fun applyEqualizerPreset(preset: EqualizerPreset) {
        mediaRepo.dspController.applyPreset(preset)
        persistEqualizerState()
        showToast("Эквалайзер: ${preset.title}")
    }

    fun setEqualizerBandLevel(bandIndex: Int, levelMb: Int) {
        mediaRepo.dspController.setBandLevel(bandIndex, levelMb)
        persistEqualizerState()
    }

    fun setBassBoostStrength(strength: Int) {
        mediaRepo.dspController.setBassBoost(strength)
        persistEqualizerState()
    }

    fun setVirtualizerStrength(strength: Int) {
        mediaRepo.dspController.setVirtualizer(strength)
        persistEqualizerState()
    }

    fun setLocalPlaybackSpeed(speed: Float) {
        mediaRepo.setLocalPlaybackSpeed(speed)
        showToast("Скорость: ${"%.2f".format(speed)}x")
    }

    fun cycleLocalRepeatMode() {
        val next = mediaRepo.cycleLocalRepeatMode()
        showToast("Повтор: ${next.title}")
    }

    fun toggleLocalShuffle() {
        val enabled = mediaRepo.toggleLocalShuffle()
        showToast(if (enabled) "Перемешивание включено" else "Перемешивание выключено")
    }

    // Smart Album Builder (Auto Tracklist Comparison, Cover Change & Missing Track Downloads)
    fun selectSmartAlbum(albumId: String?) {
        _selectedAlbumId.value = albumId
    }

    fun openSmartAlbumFromSearch(albumId: String) {
        _selectedAlbumId.value = albumId
        openFullPowerampPlayer(PowerampTab.ALBUMS)
    }

    fun createSmartAlbum(title: String, artistHint: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        val newId = "alb_${System.currentTimeMillis()}"
        val placeholder = SmartAlbum(
            id = newId,
            title = cleanTitle,
            artist = artistHint.trim(),
            isFetchingOnline = true,
            statusMessage = "Поиск официального треклиста и обложки…"
        )
        _selectedAlbumId.value = newId
        viewModelScope.launch {
            prefsRepo.updateSmartAlbums { current -> listOf(placeholder) + current }
            refreshAlbumOnlineMetadata(newId)
        }
    }

    fun refreshAlbumOnlineMetadata(albumId: String) {
        albumRefreshJobs[albumId]?.cancel()
        albumRefreshJobs[albumId] = viewModelScope.launch {
            val albums = prefsRepo.getSmartAlbumsNow()
            val target = albums.firstOrNull { it.id == albumId } ?: return@launch
            prefsRepo.updateSmartAlbums { latest ->
                latest.map { alb ->
                    if (alb.id == albumId) alb.copy(isFetchingOnline = true) else alb
                }
            }
            val blueprint = MusicCatalogAndLyricsRepository.fetchAlbumBlueprint(target.title, target.artist)
            val cachedCoverPath = blueprint?.coverUrl?.let { url ->
                MusicCatalogAndLyricsRepository.cacheRemoteCoverFile(getApplication(), url, "alb_$albumId")
            }
            val localTracks = mediaRepo.localTracks.value.ifEmpty { uiState.value.localAudioTracks }
            prefsRepo.updateSmartAlbums { latest ->
                latest.map { currentAlb ->
                    if (currentAlb.id != albumId) {
                        currentAlb
                    } else if (blueprint != null) {
                        val sourceName = when {
                            blueprint.releaseId.startsWith("discogs:") -> "Discogs.com"
                            blueprint.releaseId.startsWith("deezer:") -> "Deezer"
                            blueprint.releaseId.startsWith("itunes:") -> "iTunes"
                            blueprint.releaseId.startsWith("arch:") || blueprint.releaseId.startsWith("archive:") -> "Archive.org"
                            else -> "MusicBrainz"
                        }
                        val dedupExpected = MusicCatalogAndLyricsRepository.deduplicateExpectedTracks(blueprint.expectedTracks)
                        val updated = currentAlb.copy(
                            title = blueprint.title.ifBlank { currentAlb.title },
                            artist = blueprint.artist.ifBlank { currentAlb.artist },
                            releaseId = blueprint.releaseId,
                            expectedTrackCount = maxOf(blueprint.expectedTrackCount, dedupExpected.size),
                            expectedTracks = dedupExpected,
                            coverUrl = currentAlb.coverUrl ?: blueprint.coverUrl,
                            customCoverPath = currentAlb.customCoverPath ?: cachedCoverPath,
                            alternateCoverUrls = blueprint.alternateCoverUrls,
                            isFetchingOnline = false,
                            statusMessage = "Треклист получен ($sourceName): ${dedupExpected.size} трек(ов)"
                        )
                        MusicCatalogAndLyricsRepository.reconcileAlbumTracks(updated, localTracks)
                    } else {
                        MusicCatalogAndLyricsRepository.reconcileAlbumTracks(
                            currentAlb.copy(
                                isFetchingOnline = false,
                                statusMessage = "Не найден точный релиз онлайн — добавляйте треки с устройства"
                            ),
                            localTracks
                        )
                    }
                }
            }
        }
    }

    fun attachLocalTracksToAlbum(albumId: String, trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            val localTracks = mediaRepo.localTracks.value.ifEmpty { uiState.value.localAudioTracks }
            prefsRepo.updateSmartAlbums { albums ->
                albums.map { alb ->
                    if (alb.id == albumId) {
                        val mergedIds = (alb.attachedLocalTrackIds + trackIds).distinct()
                        MusicCatalogAndLyricsRepository.reconcileAlbumTracks(
                            alb.copy(attachedLocalTrackIds = mergedIds),
                            localTracks
                        )
                    } else alb
                }
            }
            showToast("Треки добавлены в альбом и сверены")
        }
    }

    fun detachTrackFromAlbum(albumId: String, trackId: String) {
        viewModelScope.launch {
            val localTracks = mediaRepo.localTracks.value.ifEmpty { uiState.value.localAudioTracks }
            prefsRepo.updateSmartAlbums { albums ->
                albums.map { alb ->
                    if (alb.id == albumId) {
                        val remaining = alb.attachedLocalTrackIds.filterNot { it == trackId }
                        MusicCatalogAndLyricsRepository.reconcileAlbumTracks(
                            alb.copy(attachedLocalTrackIds = remaining),
                            localTracks
                        )
                    } else alb
                }
            }
        }
    }

    fun setAlbumCoverFromUri(albumId: String, uri: Uri) {
        viewModelScope.launch {
            MusicCatalogAndLyricsRepository.importCustomCoverFromUri(getApplication(), albumId, uri)
                .onSuccess { savedPath ->
                    prefsRepo.updateSmartAlbums { albums ->
                        albums.map { if (it.id == albumId) it.copy(customCoverPath = savedPath) else it }
                    }
                    showToast("Обложка альбома обновлена")
                }
                .onFailure { err ->
                    showToast(err.message ?: "Не удалось загрузить обложку")
                }
        }
    }

    fun setAlbumCoverUrl(albumId: String, coverUrl: String) {
        viewModelScope.launch {
            val cachedLocal = MusicCatalogAndLyricsRepository.cacheRemoteCoverFile(
                getApplication(),
                coverUrl,
                "alb_${albumId}_${coverUrl.hashCode()}"
            )
            prefsRepo.updateSmartAlbums { albums ->
                albums.map {
                    if (it.id == albumId) it.copy(coverUrl = coverUrl, customCoverPath = cachedLocal)
                    else it
                }
            }
            showToast("Выбрана новая обложка из каталога")
        }
    }

    fun deleteSmartAlbum(albumId: String) {
        viewModelScope.launch {
            prefsRepo.updateSmartAlbums { albums -> albums.filterNot { it.id == albumId } }
            if (_selectedAlbumId.value == albumId) _selectedAlbumId.value = null
            showToast("Альбом удалён")
        }
    }

    fun setTrackCustomCoverFromUri(trackId: String?, title: String, artist: String, uri: Uri) {
        viewModelScope.launch {
            val keyHint = "trk_${MusicCatalogAndLyricsRepository.buildTrackKey(title, artist)}"
            MusicCatalogAndLyricsRepository.importRichMediaCoverFromUri(
                context = getApplication(),
                subDir = "track_covers",
                keyPrefix = keyHint,
                sourceUri = uri
            ).onSuccess { savedPath ->
                val keys = MusicCatalogAndLyricsRepository.buildTrackCoverLookupKeys(trackId, title, artist)
                prefsRepo.saveTrackCoverOverride(keys, savedPath)
                showToast("Обложка трека обновлена")
            }.onFailure { err ->
                showToast(err.message ?: "Не удалось сохранить обложку трека")
            }
        }
    }

    fun clearTrackCustomCover(trackId: String?, title: String, artist: String) {
        viewModelScope.launch {
            val keys = MusicCatalogAndLyricsRepository.buildTrackCoverLookupKeys(trackId, title, artist)
            prefsRepo.clearTrackCoverOverride(keys)
            showToast("Своя обложка трека сброшена")
        }
    }

    fun setDefaultTrackFallbackCoverFromUri(uri: Uri) {
        viewModelScope.launch {
            MusicCatalogAndLyricsRepository.importRichMediaCoverFromUri(
                context = getApplication(),
                subDir = "fallback_covers",
                keyPrefix = "default_disc",
                sourceUri = uri
            ).onSuccess { savedPath ->
                prefsRepo.setDefaultTrackFallbackCoverPath(savedPath)
                showToast("Фон заглушки / пластинки сохранён")
            }.onFailure { err ->
                showToast(err.message ?: "Не удалось загрузить медиа для заглушки")
            }
        }
    }

    fun clearDefaultTrackFallbackCover() {
        viewModelScope.launch {
            prefsRepo.setDefaultTrackFallbackCoverPath(null)
            showToast("Стандартная заглушка / пластинка восстановлена")
        }
    }

    fun downloadExpectedTrackForAlbum(album: SmartAlbum, expectedTrack: AlbumExpectedTrack) {
        viewModelScope.launch {
            val artist = expectedTrack.artist.ifBlank { album.artist }
            showToast("Поиск «${expectedTrack.title}» (FLAC / Интернет)…")
            val grouped = MusicCatalogAndLyricsRepository.searchAllSources(
                query = "$artist ${expectedTrack.title}".trim(),
                localTracks = uiState.value.localAudioTracks,
                mediaState = uiState.value.mediaState,
                installedMusicApps = uiState.value.musicApps
            )
            val candidates = grouped.firstOrNull()?.candidates ?: emptyList()
            val outcome = MusicCatalogAndLyricsRepository.downloadTrackByPriority(
                context = getApplication(),
                title = expectedTrack.title,
                artist = artist,
                candidates = candidates,
                installedMusicApps = uiState.value.musicApps,
                expectedDurationMs = expectedTrack.durationMs,
                albumCoverUrl = album.coverUrl ?: album.customCoverPath,
                expectedAlbum = album.title,
                preferDirectOnlineOverStreaming = true
            )
            handleDownloadOutcome(outcome, albumId = album.id)
        }
    }

    fun downloadMissingAlbumTracks(albumId: String) {
        viewModelScope.launch {
            val album = prefsRepo.getSmartAlbumsNow().firstOrNull { it.id == albumId } ?: return@launch
            val missing = album.missingTracks
            if (missing.isEmpty()) {
                showToast("Все треки альбома уже на устройстве!")
                return@launch
            }
            showToast("Автоскачивание ${missing.size} трек(ов) из «${album.title}»…")
            val albumArchiveFiles = withContext(Dispatchers.IO) {
                MusicCatalogAndLyricsRepository.searchArchiveOrgAlbumFiles(album.title, album.artist)
            }
            var downloadedCount = 0
            var firstCaptureNeeded: DownloadOutcome.NeedsStreamingCapture? = null
            var captureNeededCount = 0

            for (slot in missing) {
                val artist = slot.artist.ifBlank { album.artist }
                val albumMatches = albumArchiveFiles.filter { cand ->
                    MusicCatalogAndLyricsRepository.titlesMatchStrict(slot.title, cand.title)
                }
                val grouped = MusicCatalogAndLyricsRepository.searchAllSources(
                    query = "$artist ${slot.title}".trim(),
                    localTracks = uiState.value.localAudioTracks,
                    mediaState = uiState.value.mediaState,
                    installedMusicApps = uiState.value.musicApps
                )
                val queryCandidates = grouped.firstOrNull()?.candidates ?: emptyList()
                val outcome = MusicCatalogAndLyricsRepository.downloadTrackByPriority(
                    context = getApplication(),
                    title = slot.title,
                    artist = artist,
                    candidates = queryCandidates,
                    installedMusicApps = uiState.value.musicApps,
                    allowStreamingCapture = false,
                    extraCandidates = albumMatches,
                    expectedDurationMs = slot.durationMs,
                    albumCoverUrl = album.coverUrl ?: album.customCoverPath,
                    expectedAlbum = album.title,
                    preferDirectOnlineOverStreaming = true
                )
                when (outcome) {
                    is DownloadOutcome.DownloadedFile -> {
                        downloadedCount++
                        mediaRepo.registerDownloadedTrack(outcome.track)
                        val refreshed = mediaRepo.refreshLocalTracks()
                        val matched = refreshed.firstOrNull { it.filePath == outcome.track.filePath } ?: outcome.track
                        if (!outcome.coverPath.isNullOrBlank()) {
                            val keys = MusicCatalogAndLyricsRepository.buildTrackCoverLookupKeys(
                                trackId = matched.id,
                                title = matched.title,
                                artist = matched.artist
                            )
                            prefsRepo.saveTrackCoverOverride(keys, outcome.coverPath)
                        }
                        prefsRepo.updateSmartAlbums { latestAlbums ->
                            latestAlbums.map { alb ->
                                if (alb.id == album.id) {
                                    MusicCatalogAndLyricsRepository.reconcileAlbumTracks(
                                        alb.copy(attachedLocalTrackIds = (alb.attachedLocalTrackIds + matched.id).distinct()),
                                        refreshed
                                    )
                                } else alb
                            }
                        }
                    }
                    is DownloadOutcome.NeedsStreamingCapture -> {
                        captureNeededCount++
                        if (firstCaptureNeeded == null) {
                            firstCaptureNeeded = outcome
                        }
                    }
                    is DownloadOutcome.Failed -> Unit
                }
            }

            if (downloadedCount == 0 && firstCaptureNeeded != null) {
                requestStreamCaptureForTrack(
                    title = firstCaptureNeeded.title,
                    artist = firstCaptureNeeded.artist,
                    packageName = firstCaptureNeeded.packageName,
                    appLabel = firstCaptureNeeded.appLabel,
                    albumId = album.id
                )
                showToast("Прямые FLAC/MP3 не найдены — захватите «${firstCaptureNeeded.title}» из ${firstCaptureNeeded.appLabel}")
            } else if (downloadedCount > 0 && captureNeededCount > 0) {
                showToast("Скачано $downloadedCount трек(ов); ещё $captureNeededCount доступны через захват стриминга")
            } else {
                showToast(
                    if (downloadedCount > 0) "Докачано треков в альбом: $downloadedCount"
                    else "Не удалось автоматически скачать недостающие треки"
                )
            }
        }
    }

    private suspend fun reconcileAllSmartAlbumsWithTracks(tracks: List<LocalAudioTrack>) {
        prefsRepo.updateSmartAlbums { current ->
            if (current.isEmpty()) current
            else current.map { MusicCatalogAndLyricsRepository.reconcileAlbumTracks(it, tracks) }
        }
    }

    fun launchMusicSearchMatch(match: MusicStreamMatch) {
        if (match.isLocalTrack && match.localTrack != null) {
            playLocalAudioTrack(match.localTrack)
            return
        }
        if (match.queueItemId != null) {
            val targetSession = uiState.value.mediaState.sessions.firstOrNull { it.packageName == match.packageName }
            if (targetSession != null) {
                mediaRepo.selectSession(targetSession.id)
                mediaRepo.skipToQueueItem(match.queueItemId)
                return
            }
        }
        if (match.mediaId != null) {
            playBrowserTrack(
                MediaBrowserTrack(
                    mediaId = match.mediaId,
                    packageName = match.packageName,
                    appLabel = match.appLabel,
                    title = match.title,
                    subtitle = match.subtitle
                )
            )
            return
        }
        searchAndPlayInActiveStream(_appSearchQuery.value, match.packageName)
    }

    // Appearance, Wallpaper & Custom Palettes
    fun setShowAppearanceDialog(show: Boolean) {
        _showAppearanceDialog.value = show
    }

    fun setUiThemeColor(color: UiThemeColor) {
        viewModelScope.launch {
            prefsRepo.setActiveCustomPaletteId(null)
            prefsRepo.setUiThemeColor(color)
            showToast("Цвет интерфейса: ${color.title}")
        }
    }

    fun setWallpaperFromUri(uri: Uri) {
        viewModelScope.launch {
            WallpaperThemeHelper.importWallpaperFromUri(getApplication(), uri).onSuccess { path ->
                prefsRepo.setWallpaperPath(path)
                val loaded = WallpaperThemeHelper.loadWallpaperAndPalette(path)
                _wallpaperState.value = loaded
                loaded.second?.let { pal ->
                    prefsRepo.saveCustomPalette(
                        CustomColorPalette(
                            id = "wall_${System.currentTimeMillis()}",
                            name = "Обои ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
                            dominantDarkArgb = pal.dominantDarkArgb,
                            accentArgb = pal.accentArgb,
                            highlightArgb = pal.highlightArgb,
                            isFromWallpaper = true
                        ),
                        activate = true
                    )
                }
                showToast("Обои применены • Стеклянный режим Aero")
            }.onFailure { err ->
                showToast(err.message ?: "Не удалось установить обои")
            }
        }
    }

    fun clearWallpaper() {
        viewModelScope.launch {
            val oldPath = prefsRepo.getWallpaperPathNow()
            prefsRepo.setWallpaperPath(null)
            WallpaperThemeHelper.deleteWallpaperFile(oldPath)
            _wallpaperState.value = null to null
            showToast("Обои убраны • Палитра сохранена")
        }
    }

    fun saveCurrentWallpaperPalette(name: String) {
        val pal = uiState.value.wallpaperPalette ?: return
        val cleanName = name.trim().ifBlank { "Палитра обоев" }
        viewModelScope.launch {
            prefsRepo.saveCustomPalette(
                CustomColorPalette(
                    id = "pal_${System.currentTimeMillis()}",
                    name = cleanName,
                    dominantDarkArgb = pal.dominantDarkArgb,
                    accentArgb = pal.accentArgb,
                    highlightArgb = pal.highlightArgb,
                    isFromWallpaper = true
                ),
                activate = true
            )
            showToast("Палитра «$cleanName» сохранена")
        }
    }

    fun createAndSaveCustomPalette(name: String, darkArgb: Long, accentArgb: Long, highlightArgb: Long) {
        val cleanName = name.trim().ifBlank { "Своя тема" }
        viewModelScope.launch {
            prefsRepo.saveCustomPalette(
                CustomColorPalette(
                    id = "custom_${System.currentTimeMillis()}",
                    name = cleanName,
                    dominantDarkArgb = darkArgb,
                    accentArgb = accentArgb,
                    highlightArgb = highlightArgb,
                    isFromWallpaper = false
                ),
                activate = true
            )
            showToast("Своя палитра «$cleanName» применена")
        }
    }

    fun selectCustomPalette(paletteId: String?) {
        viewModelScope.launch {
            prefsRepo.setActiveCustomPaletteId(paletteId)
            showToast(if (paletteId == null) "Сброшено на стандартную схему" else "Палитра применена")
        }
    }

    fun deleteCustomPalette(paletteId: String) {
        viewModelScope.launch {
            prefsRepo.deleteCustomPalette(paletteId)
            showToast("Палитра удалена")
        }
    }

    fun deduplicateCustomPalettes() {
        viewModelScope.launch {
            val removed = prefsRepo.deduplicateCustomPalettes()
            if (removed > 0) {
                showToast("Удалено $removed дубликат(ов) палитр")
            } else {
                showToast("Дубликатов среди палитр не обнаружено")
            }
        }
    }

    fun shareCustomPalette(palette: CustomColorPalette) {
        val darkHex = String.format("#%08X", palette.dominantDarkArgb)
        val accentHex = String.format("#%08X", palette.accentArgb)
        val hlHex = String.format("#%08X", palette.highlightArgb)
        val json = JSONObject().apply {
            put("id", palette.id)
            put("name", palette.name)
            put("dark", palette.dominantDarkArgb)
            put("accent", palette.accentArgb)
            put("hl", palette.highlightArgb)
            put("wall", palette.isFromWallpaper)
        }.toString(2)

        val shareText = buildString {
            append("🎨 Цветовая палитра KeyDeck: «").append(palette.name).append("»\n")
            append("• Фон: ").append(darkHex).append("\n")
            append("• Акцент: ").append(accentHex).append("\n")
            append("• Подсветка: ").append(hlHex).append("\n\n")
            append("Конфигурация JSON:\n")
            append(json)
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Палитра KeyDeck: ${palette.name}")
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(sendIntent, "Поделиться палитрой «${palette.name}»").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            getApplication<Application>().startActivity(chooser)
        }.onFailure {
            showToast("Не удалось открыть меню отправки")
        }
    }

    // Auto-Close, System Optimization, Linux Packages, ZIP Updater & Weather
    fun setShowZipUpdateDialog(show: Boolean) {
        _showZipUpdateDialog.value = show
        if (!show) {
            _zipUpdateStatus.value = null
        }
    }

    fun inspectZipUpdateUri(
        uri: Uri,
        localFilePath: String? = null,
        displayName: String? = null
    ) {
        viewModelScope.launch {
            _zipUpdateBusy.value = true
            _zipUpdateStatus.value = "Проверка содержимого архива…"
            zipUpdateRepo.inspectZipArchive(uri, localFilePath, displayName)
                .onSuccess { insp ->
                    _zipUpdateInspection.value = insp
                    _zipUpdateStatus.value = null
                }
                .onFailure { err ->
                    _zipUpdateStatus.value = "Ошибка чтения ZIP: ${err.message ?: "повреждённый архив"}"
                    showToast("Не удалось разобрать ZIP-архив")
                }
            _zipUpdateBusy.value = false
        }
    }

    fun applyZipUpdate(preferShizukuOrRoot: Boolean) {
        val inspection = _zipUpdateInspection.value ?: return
        viewModelScope.launch {
            _zipUpdateBusy.value = true
            val messages = mutableListOf<String>()
            var anyApplied = false
            if (inspection.canApplyConfigPack) {
                zipUpdateRepo.applyConfigPack(inspection, prefsRepo)
                    .onSuccess { msg ->
                        anyApplied = true
                        messages.add(msg)
                        if (!inspection.extractedWallpaperPath.isNullOrBlank()) {
                            _wallpaperState.value = WallpaperThemeHelper.loadWallpaperAndPalette(
                                inspection.extractedWallpaperPath
                            )
                        }
                        if (inspection.pendingLyrics.isNotEmpty()) {
                            lastLoadedLyricsKey = ""
                            loadLyricsForCurrentTrack(forceRefresh = false)
                        }
                    }
                    .onFailure { err ->
                        messages.add("Настройки/ресурсы: ${err.message ?: "ошибка"}")
                    }
            }
            if (inspection.canInstallApks) {
                zipUpdateRepo.installExtractedApks(inspection, preferShizukuOrRoot)
                    .onSuccess { msg ->
                        anyApplied = true
                        messages.add(msg)
                    }
                    .onFailure { err ->
                        messages.add("Установка APK: ${err.message ?: "ошибка"}")
                    }
            }
            val finalMsg = messages.joinToString("\n").ifBlank {
                "В архиве нет готового APK или пакета настроек для применения"
            }
            _zipUpdateStatus.value = finalMsg
            if (anyApplied) {
                showToast(finalMsg.lineSequence().firstOrNull() ?: "Обновление применено")
            }
            _zipUpdateBusy.value = false
        }
    }

    fun saveGitHubBuildConfig(config: GitHubBuildConfig) {
        viewModelScope.launch {
            prefsRepo.saveGitHubBuildConfig(config)
        }
    }

    fun cloudBuildApkFromSourceZip(config: GitHubBuildConfig, preferShizukuOrRoot: Boolean) {
        val inspection = _zipUpdateInspection.value ?: return
        val sourceUri = runCatching { Uri.parse(inspection.sourceUriString) }.getOrNull() ?: return
        viewModelScope.launch {
            _zipUpdateBusy.value = true
            prefsRepo.saveGitHubBuildConfig(config)
            githubCloudBuildRepo.buildApkFromSourceZip(
                sourceUri = sourceUri,
                localFilePath = inspection.localFilePath,
                config = config,
                onProgress = { msg -> _zipUpdateStatus.value = msg }
            ).onSuccess { apkFile ->
                val apkInspection = zipUpdateRepo.inspectLocalApkFile(apkFile)
                _zipUpdateInspection.value = apkInspection
                _zipUpdateStatus.value = "APK собран (${apkFile.name}), запуск установки…"
                zipUpdateRepo.installExtractedApks(apkInspection, preferShizukuOrRoot)
                    .onSuccess { msg ->
                        _zipUpdateStatus.value = "Сборка и установка: $msg"
                        showToast(msg)
                    }
                    .onFailure { err ->
                        _zipUpdateStatus.value = "APK собран и сохранён, ошибка установки: ${err.message}"
                    }
            }.onFailure { err ->
                _zipUpdateStatus.value = "Ошибка облачной сборки: ${err.message ?: "проверьте репозиторий и токен"}"
                showToast("Ошибка сборки в GitHub Actions")
            }
            _zipUpdateBusy.value = false
        }
    }

    fun fetchLatestCloudBuiltApk(config: GitHubBuildConfig, preferShizukuOrRoot: Boolean) {
        viewModelScope.launch {
            _zipUpdateBusy.value = true
            prefsRepo.saveGitHubBuildConfig(config)
            githubCloudBuildRepo.fetchLatestApkFromRepo(
                config = config,
                onProgress = { msg -> _zipUpdateStatus.value = msg }
            ).onSuccess { apkFile ->
                val apkInspection = zipUpdateRepo.inspectLocalApkFile(apkFile)
                _zipUpdateInspection.value = apkInspection
                zipUpdateRepo.installExtractedApks(apkInspection, preferShizukuOrRoot)
                    .onSuccess { msg ->
                        _zipUpdateStatus.value = "Загружен готовый APK: $msg"
                        showToast(msg)
                    }
                    .onFailure { err ->
                        _zipUpdateStatus.value = "APK скачан, ошибка установки: ${err.message}"
                    }
            }.onFailure { err ->
                _zipUpdateStatus.value = "Не удалось скачать APK: ${err.message ?: "ошибка"}"
            }
            _zipUpdateBusy.value = false
        }
    }

    fun openUnknownAppSourcesSettings() {
        if (!zipUpdateRepo.openUnknownAppSourcesSettings()) {
            showToast("Откройте Настройки → Приложения → KeyDeck → Установка неизвестных приложений")
        }
    }

    fun exportConfigBackupZip(destUri: Uri) {
        viewModelScope.launch {
            _zipUpdateBusy.value = true
            _zipUpdateStatus.value = "Сохранение ZIP-бэкапа настроек и текстов…"
            val palettes = runCatching { prefsRepo.getSavedPalettesNow() }.getOrDefault(emptyList())
            val links = runCatching { prefsRepo.getHubQuickLinksNow() }.getOrDefault(emptyList())
            val lyrics = runCatching { prefsRepo.getLyricsOverridesNow() }.getOrDefault(emptyMap())
            zipUpdateRepo.exportConfigBackupZip(destUri, palettes, links, lyrics)
                .onSuccess { msg ->
                    _zipUpdateStatus.value = msg
                    showToast(msg)
                }
                .onFailure { err ->
                    val msg = "Ошибка экспорта ZIP: ${err.message ?: "не удалось записать файл"}"
                    _zipUpdateStatus.value = msg
                    showToast(msg)
                }
            _zipUpdateBusy.value = false
        }
    }

    fun setShowAutoKillDialog(show: Boolean) {
        _showAutoKillDialog.value = show
    }

    fun toggleAutoKillEnabled() {
        val next = !uiState.value.autoKillEnabled
        viewModelScope.launch {
            prefsRepo.setAutoKillEnabled(next)
            showToast(
                if (next) "Автозавершение одноразовых приложений включено"
                else "Автозавершение одноразовых приложений выключено"
            )
        }
    }

    fun toggleAutoKillPackage(packageName: String) {
        viewModelScope.launch {
            val existed = uiState.value.autoKillPackages.contains(packageName)
            prefsRepo.toggleAutoKillPackage(packageName)
            showToast(if (existed) "Убрано из автозавершения" else "Добавлено в одноразовые")
        }
    }

    fun setShowOptimizationDialog(show: Boolean) {
        _showOptimizationDialog.value = show
        if (show) {
            refreshOptimizationStats()
            shizukuBridge.refreshStatus()
        }
    }

    fun setShowBackgroundWebAudioDialog(show: Boolean) {
        _showBackgroundWebAudioDialog.value = show
    }

    fun setShowLinuxCatalogDialog(show: Boolean) {
        _showLinuxCatalogDialog.value = show
        if (show) refreshLinuxCatalog()
    }

    fun refreshLinuxCatalog() {
        viewModelScope.launch {
            _isTermuxInstalled.value = linuxRepo.isTermuxInstalled()
            _linuxPackages.value = linuxRepo.scanLinuxPackages("")
        }
    }

    fun installOrRunLinuxPackage(entry: LinuxPackageEntry) {
        viewModelScope.launch {
            linuxRepo.installLocally(entry).onSuccess { outcome ->
                val tile = LauncherPreferencesRepository.PersistedLinuxTile(
                    id = outcome.tileId,
                    name = outcome.name,
                    packageName = outcome.packageName,
                    kind = outcome.kind.name,
                    installedDirPath = outcome.installedDirPath,
                    entryCommand = outcome.entryCommand,
                    version = entry.version
                )
                prefsRepo.upsertInstalledLinuxTile(tile)
                _showLinuxCatalogDialog.value = false
                _activeLinuxRunnerTile.value = tile
                _linuxConsoleOutput.value = outcome.log
                refreshLinuxCatalog()
                showToast("Создана плитка: ${tile.name}")
            }.onFailure { err ->
                showToast(err.message ?: "Ошибка установки пакета Linux")
            }
        }
    }

    fun openLinuxRunner(tileId: String) {
        viewModelScope.launch {
            val tiles = prefsRepo.getInstalledLinuxTilesNow()
            val tile = tiles.firstOrNull { it.id == tileId }
            if (tile == null) {
                showToast("Пакет Linux не найден")
                return@launch
            }
            _activeLinuxRunnerTile.value = tile
            _linuxConsoleOutput.value = "Запуск ${tile.name} (${tile.version})…\n$ ${tile.entryCommand}"
            val workDir = tile.installedDirPath.takeIf { it.isNotBlank() }?.let { File(it) }
            val result = linuxRepo.executeLocalShellCommand(tile.entryCommand, workDir)
            result.onSuccess { out ->
                _linuxConsoleOutput.value = "$ ${tile.entryCommand}\n$out"
            }.onFailure { err ->
                _linuxConsoleOutput.value = "$ ${tile.entryCommand}\n[Ошибка]: ${err.message}"
            }
        }
    }

    fun runLinuxConsoleCommand(command: String) {
        val tile = _activeLinuxRunnerTile.value ?: return
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val workDir = tile.installedDirPath.takeIf { it.isNotBlank() }?.let { File(it) }
            val res = linuxRepo.executeLocalShellCommand(trimmed, workDir)
            val text = res.getOrElse { "[Ошибка]: ${it.message}" }
            _linuxConsoleOutput.value = buildString {
                if (_linuxConsoleOutput.value.isNotBlank()) {
                    appendLine(_linuxConsoleOutput.value.trimEnd())
                    appendLine()
                }
                appendLine("$ $trimmed")
                append(text)
            }
        }
    }

    fun uninstallLinuxTile(tileId: String) {
        viewModelScope.launch {
            val tiles = prefsRepo.getInstalledLinuxTilesNow()
            val target = tiles.firstOrNull { it.id == tileId }
            if (target != null) {
                linuxRepo.deleteLocalPackageFiles(target.installedDirPath)
            }
            prefsRepo.removeInstalledLinuxTile(tileId)
            if (_activeLinuxRunnerTile.value?.id == tileId) {
                _activeLinuxRunnerTile.value = null
            }
            refreshLinuxCatalog()
            showToast("Linux-плитка удалена")
        }
    }

    fun closeLinuxRunner() {
        _activeLinuxRunnerTile.value = null
    }

    fun openTermuxDownload() {
        showToast("Termux больше не требуется — пакеты ставятся локально в KeyDeck")
    }

    fun refreshWeather() {
        refreshWeatherForCity(uiState.value.selectedWeatherCity)
    }

    private fun refreshWeatherForCity(city: WeatherCityResult) {
        viewModelScope.launch {
            _isWeatherLoading.value = true
            WeatherRepository.fetchConsensusWeather(city).onSuccess { report ->
                _weatherReport.value = report
            }
            _isWeatherLoading.value = false
        }
    }

    fun searchWeatherCities(query: String) {
        weatherSearchJob?.cancel()
        if (query.trim().length < 2) {
            _weatherCityResults.value = emptyList()
            return
        }
        weatherSearchJob = viewModelScope.launch {
            delay(250L)
            _weatherCityResults.value = WeatherRepository.searchCities(query)
        }
    }

    fun selectWeatherCity(city: WeatherCityResult) {
        _weatherCityResults.value = emptyList()
        _showWeatherCityPickerDialog.value = false
        viewModelScope.launch {
            prefsRepo.setSelectedWeatherCity(city)
            showToast("Город погоды: ${city.name}")
        }
    }

    fun toggleWeatherInHubOther() {
        val next = !uiState.value.showWeatherInHubOther
        viewModelScope.launch {
            prefsRepo.setShowWeatherInHubOther(next)
            showToast(if (next) "Виджет погоды показан в Хабе → Прочее" else "Виджет погоды скрыт из Хаба → Прочее")
        }
    }

    fun toggleWeatherInHomeTile() {
        val next = !uiState.value.showWeatherInHomeTile
        viewModelScope.launch {
            prefsRepo.setShowWeatherInHomeTile(next)
            showToast(if (next) "Виджет погоды добавлен плиткой на рабочий стол" else "Плитка погоды убрана с рабочего стола")
        }
    }

    fun setShowWeatherForecastDialog(show: Boolean) {
        _showWeatherForecastDialog.value = show
    }

    fun setShowWeatherCityPickerDialog(show: Boolean) {
        _showWeatherCityPickerDialog.value = show
    }

    fun refreshOptimizationStats() {
        val prev = _optimizationStats.value
        _optimizationStats.value = optimizerManager.readRamStats(
            phantomPatched = prev.phantomKillerPatched,
            lastSummary = prev.lastCleanupSummary
        )
    }

    fun optimizeAndroidSystem() {
        viewModelScope.launch {
            val state = uiState.value
            val protected = buildSet {
                addAll(state.backgroundSpoofPackages)
                addAll(state.mediaState.sessions.filter { it.isPlaying }.map { it.packageName })
            }
            val updated = optimizerManager.optimizeMemoryAndProcesses(state.allApps, protected)
            _optimizationStats.value = updated
            showToast(updated.lastCleanupSummary ?: "Система оптимизирована")
        }
    }

    fun disablePhantomKillerViaShizuku() {
        viewModelScope.launch {
            optimizerManager.disablePhantomProcessKillerViaShizuku().onSuccess {
                _optimizationStats.value = _optimizationStats.value.copy(
                    phantomKillerPatched = true,
                    lastCleanupSummary = "Phantom Process Killer отключён через Shizuku"
                )
                showToast("Phantom Process Killer отключён")
            }.onFailure { err ->
                showToast(err.message ?: "Нужно разрешение Shizuku")
            }
        }
    }

    fun boostAppForBackgroundViaShizuku(packageName: String) {
        viewModelScope.launch {
            optimizerManager.boostAppAndProtectFromDoze(packageName).onSuccess {
                showToast("Фоновый звук и Doze-защита выданы для $packageName")
            }.onFailure { err ->
                showToast(err.message ?: "Не удалось применить через Shizuku")
            }
        }
    }

    fun toggleBackgroundSpoofEnabled() {
        val next = !uiState.value.backgroundSpoofEnabled
        viewModelScope.launch {
            prefsRepo.setBackgroundSpoofEnabled(next)
            showToast(if (next) "Фоновый обход паузы включён" else "Фоновый обход паузы отключён")
        }
    }

    fun toggleBackgroundSpoofPackage(packageName: String) {
        viewModelScope.launch {
            val existed = uiState.value.backgroundSpoofPackages.contains(packageName)
            prefsRepo.toggleBackgroundSpoofPackage(packageName)
            if (!existed && !uiState.value.backgroundSpoofEnabled) {
                prefsRepo.setBackgroundSpoofEnabled(true)
            }
            showToast(if (existed) "Убрано из фонового обхода" else "Добавлено в фоновый обход паузы")
        }
    }

    fun toggleCurrentSessionBackgroundSpoof() {
        val pkg = uiState.value.mediaState.activeSession?.packageName
        if (pkg.isNullOrBlank() || pkg == getApplication<Application>().packageName) {
            toggleBackgroundSpoofEnabled()
            return
        }
        toggleBackgroundSpoofPackage(pkg)
    }

    fun grantOverlayAndAccessibilityViaShizuku() {
        viewModelScope.launch {
            shizukuBridge.grantOverlayAndAccessibilityViaShizuku().onSuccess {
                showToast("Спец. возможности и Поверх окон включены через Shizuku")
            }.onFailure { err ->
                showToast(err.message ?: "Запустите Shizuku и повторите")
            }
        }
    }

    fun openAccessibilitySettings() {
        runCatching {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            getApplication<Application>().startActivity(intent)
        }
    }

    fun openOverlaySettings() {
        val ctx = getApplication<Application>()
        runCatching {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            ctx.startActivity(intent)
        }
    }

    // Streaming quick controls
    fun likeStreamTrack() {
        val ok = mediaRepo.triggerStreamingLike(uiState.value.mediaState.activeSession?.packageName)
        showToast(if (ok) "Отметка «Нравится» отправлена" else "Действие «Нравится» не поддерживается сессией")
    }

    fun dislikeStreamTrack() {
        val ok = mediaRepo.triggerStreamingDislike(uiState.value.mediaState.activeSession?.packageName)
        showToast(if (ok) "Отметка «Не нравится» отправлена" else "Действие «Не нравится» не поддерживается сессией")
    }

    fun startMyWave() {
        val ok = mediaRepo.triggerMyWave(uiState.value.mediaState.activeSession?.packageName)
        showToast(if (ok) "Запуск «Моя Волна»…" else "Откройте Яндекс Музыку для «Моей Волны»")
    }

    // Language, UI Scale, Player Cover Style & OEM Gesture Bypass
    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch { prefsRepo.setAppLanguage(language) }
    }

    fun setUiScale(scale: Float) {
        viewModelScope.launch { prefsRepo.setUiScale(scale) }
    }

    fun setPlayerCoverStyle(style: PlayerCoverStyle) {
        viewModelScope.launch { prefsRepo.setPlayerCoverStyle(style) }
    }

    fun toggleOemGestureBypass() {
        val target = !uiState.value.shizukuState.oemGestureBypassEnabled
        viewModelScope.launch {
            prefsRepo.setOemGestureBypassEnabled(target)
            shizukuBridge.applyOemGestureBypass(target)
            showToast(if (target) "Обход блокировки жестов OEM включён" else "Обход блокировки жестов OEM выключен")
        }
    }

    // BlackBerry OS 10 / KEY2 Active Frames Recents System
    fun openRecentsOverlay() {
        _focusedRecentIndex.value = 0
        _showRecentsOverlay.value = true
        refreshRecentFrames()
    }

    fun closeRecentsOverlay() {
        _showRecentsOverlay.value = false
    }

    fun toggleRecentsOverlay() {
        if (_showRecentsOverlay.value) closeRecentsOverlay() else openRecentsOverlay()
    }

    fun moveRecentsFocus(dx: Int, dy: Int, columns: Int) {
        val list = _recentFrames.value
        if (list.isEmpty()) return
        val cols = columns.coerceAtLeast(1)
        val cur = _focusedRecentIndex.value.coerceIn(0, list.lastIndex)
        _focusedRecentIndex.value = (cur + dx + dy * cols).coerceIn(0, list.lastIndex)
    }

    fun activateFocusedRecentFrame() {
        val frame = _recentFrames.value.getOrNull(_focusedRecentIndex.value) ?: return
        launchRecentFrame(frame)
    }

    fun refreshRecentFrames() {
        viewModelScope.launch {
            val state = uiState.value
            val pinned = state.pinnedRecentPackages
            val history = RecentForegroundTracker.snapshotRecentHistory()
            val historyMap = history.toMap()
            val shizuku = state.shizukuState
            val privilegedActive = (shizuku.isBinderAlive && shizuku.isPermissionGranted) || shizuku.isRootAvailable
            val privilegedPkgs = if (privilegedActive) {
                shizukuBridge.queryRecentPackagesViaPrivilegedOrRoot()
            } else {
                emptyList()
            }

            val orderedPkgs = linkedSetOf<String>()
            pinned.forEach { orderedPkgs.add(it) }
            history.forEach { (pkg, _) -> orderedPkgs.add(pkg) }
            privilegedPkgs.forEach { orderedPkgs.add(it) }
            state.mediaState.sessions
                .map { it.packageName }
                .filter { it.isNotBlank() && it != getApplication<Application>().packageName }
                .forEach { orderedPkgs.add(it) }

            val aliveProcesses = if (privilegedActive && orderedPkgs.isNotEmpty()) {
                shizukuBridge.findRunningPackagesViaShizuku(orderedPkgs)
            } else {
                emptySet()
            }

            val appsByPkg = state.allApps.associateBy { it.packageName }
            val frames = orderedPkgs.mapNotNull { pkg ->
                if (pkg == getApplication<Application>().packageName) return@mapNotNull null
                if (!pinned.contains(pkg) && RecentForegroundTracker.isRecentlyDismissed(pkg)) {
                    return@mapNotNull null
                }
                val session = state.mediaState.sessions.firstOrNull { it.packageName == pkg }
                if (privilegedActive &&
                    !pinned.contains(pkg) &&
                    session == null &&
                    !privilegedPkgs.contains(pkg) &&
                    !aliveProcesses.contains(pkg)
                ) {
                    return@mapNotNull null
                }
                val app = appsByPkg[pkg]
                val notif = state.hubNotifications.firstOrNull { it.packageName == pkg }
                val cachedPreview = AppPreviewCache.get(pkg)
                val label = app?.label ?: session?.appLabel ?: notif?.appName ?: return@mapNotNull null
                val previewTitle = when {
                    session != null -> session.title
                    notif != null -> notif.title.ifBlank { label }
                    else -> label
                }
                val previewSub = when {
                    session != null -> session.artist.ifBlank { session.appLabel }
                    notif != null -> notif.text
                    else -> app?.packageName ?: pkg
                }
                RecentAppFrame(
                    packageName = pkg,
                    activityName = app?.activityName,
                    appLabel = label,
                    icon = app?.icon,
                    previewBitmap = cachedPreview ?: session?.artwork,
                    lastActiveMs = historyMap[pkg] ?: notif?.postTime ?: 0L,
                    previewTitle = previewTitle,
                    previewSubtitle = previewSub,
                    badgeText = if (session?.isPlaying == true) "▶" else "",
                    isPinned = pinned.contains(pkg),
                    isPlayingMedia = session?.isPlaying == true
                )
            }.sortedWith(
                compareByDescending<RecentAppFrame> { it.isPinned }
                    .thenByDescending { it.isPlayingMedia }
                    .thenByDescending { it.lastActiveMs }
            )
            _recentFrames.value = frames
            _focusedRecentIndex.value = if (frames.isEmpty()) 0 else _focusedRecentIndex.value.coerceIn(0, frames.lastIndex)
        }
    }

    fun togglePinRecentFrame(packageName: String) {
        viewModelScope.launch {
            prefsRepo.togglePinRecentPackage(packageName)
            delay(60L)
            refreshRecentFrames()
        }
    }

    fun launchRecentFrame(frame: RecentAppFrame) {
        _showRecentsOverlay.value = false
        launchPackage(frame.packageName)
    }

    fun openRecentFrameInfo(frame: RecentAppFrame) {
        _showRecentsOverlay.value = false
        appsRepo.openAppInfo(frame.packageName)
    }

    fun closeRecentFrame(frame: RecentAppFrame) {
        viewModelScope.launch {
            RecentForegroundTracker.removeFromHistory(frame.packageName)
            AppPreviewCache.remove(frame.packageName)
            val am = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            runCatching { am?.killBackgroundProcesses(frame.packageName) }
            val shizuku = uiState.value.shizukuState
            val privilegedActive = (shizuku.isBinderAlive && shizuku.isPermissionGranted) || shizuku.isRootAvailable
            if (privilegedActive) {
                shizukuBridge.forceStopAndRemoveTasksViaShizuku(listOf(frame.packageName))
            }
            _recentFrames.value = _recentFrames.value.filterNot { it.packageName == frame.packageName }
            showToast(
                if (privilegedActive) "Полностью закрыто: ${frame.appLabel}"
                else "Убрано и остановлено в фоне: ${frame.appLabel}"
            )
        }
    }

    fun closeAllRecentsExceptPinned() {
        viewModelScope.launch {
            val pinned = uiState.value.pinnedRecentPackages
            val targets = _recentFrames.value.map { it.packageName }.filterNot { pinned.contains(it) }
            RecentForegroundTracker.clearHistoryExcept(pinned)
            targets.forEach { AppPreviewCache.remove(it) }
            val am = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            targets.forEach { pkg -> runCatching { am?.killBackgroundProcesses(pkg) } }
            val shizuku = uiState.value.shizukuState
            if (targets.isNotEmpty() && ((shizuku.isBinderAlive && shizuku.isPermissionGranted) || shizuku.isRootAvailable)) {
                shizukuBridge.forceStopAndRemoveTasksViaShizuku(targets)
            }
            _recentFrames.value = _recentFrames.value.filter { pinned.contains(it.packageName) }
            showToast(
                if (pinned.isNotEmpty()) "Очищено (закреплённые сохранены: ${pinned.size})"
                else "Недавние приложения очищены"
            )
        }
    }

    fun setShowShortcutsDialog(show: Boolean) {
        _showShortcutsDialog.value = show
    }

    fun dismissToast() {
        _statusToast.value = null
    }

    private fun showToast(message: String) {
        _statusToast.value = message
        viewModelScope.launch {
            delay(2500L)
            if (_statusToast.value == message) {
                _statusToast.value = null
            }
        }
    }
}
