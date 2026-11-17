package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppsRepository
import com.example.data.AutoCloseManager
import com.example.data.CallsContactsRepository
import com.example.data.KeyboardSearchMatcher
import com.example.data.LauncherPreferencesRepository
import com.example.data.MediaControlBus
import com.example.data.MediaControlRepository
import com.example.data.NotificationHubRepository
import com.example.data.ShizukuBridge
import com.example.data.UniversalSearchRepository
import com.example.data.WallpaperThemeHelper
import com.example.model.AlphabetMode
import com.example.model.AppEntry
import com.example.model.AppIconMode
import com.example.model.AppIconOverride
import com.example.model.CallLogEntry
import com.example.model.CallsSubTab
import com.example.model.ContactEntry
import com.example.model.DeviceSearchFile
import com.example.model.HubCategory
import com.example.model.HubNotificationItem
import com.example.model.HubQuickLink
import com.example.model.HubSubTab
import com.example.model.LocalAudioTrack
import com.example.model.MainTab
import com.example.model.MediaBrowserTrack
import com.example.model.MediaPlayerState
import com.example.model.MusicDrawerTab
import com.example.model.SearchResultKind
import com.example.model.ShizukuState
import com.example.model.UiThemeColor
import com.example.model.WallpaperPaletteData
import com.example.model.WriterSubTab
import com.example.ui.LockScreenActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    val musicDrawerTab: MusicDrawerTab = MusicDrawerTab.QUEUE,
    val showMusicSheet: Boolean = false,
    val showShortcutsDialog: Boolean = false,
    val showAddLinkDialog: Boolean = false,
    val showAppearanceDialog: Boolean = false,
    val showAutoKillDialog: Boolean = false,
    val uiThemeColor: UiThemeColor = UiThemeColor.BLUE,
    val wallpaperBitmap: ImageBitmap? = null,
    val wallpaperPalette: WallpaperPaletteData? = null,
    val hasWallpaper: Boolean = false,
    val autoKillEnabled: Boolean = true,
    val autoKillPackages: Set<String> = emptySet(),
    val contextMenuApp: AppEntry? = null,
    val contextMenuFile: DeviceSearchFile? = null,
    val renameTargetApp: AppEntry? = null,
    val iconPickerTargetApp: AppEntry? = null,
    val statusToast: String? = null
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = LauncherPreferencesRepository(application)
    private val appsRepo = AppsRepository(application)
    private val callsRepo = CallsContactsRepository(application)
    private val mediaRepo = MediaControlRepository(application)
    private val shizukuBridge = ShizukuBridge(application)
    private val autoCloseManager = AutoCloseManager(application, shizukuBridge)
    private val universalSearchRepo = UniversalSearchRepository(application, shizukuBridge)

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
    private val _showMusicSheet = MutableStateFlow(false)
    private val _musicDrawerTab = MutableStateFlow(MusicDrawerTab.QUEUE)
    private val _showShortcutsDialog = MutableStateFlow(false)
    private val _showAddLinkDialog = MutableStateFlow(false)
    private val _showAppearanceDialog = MutableStateFlow(false)
    private val _showAutoKillDialog = MutableStateFlow(false)
    private val _wallpaperState = MutableStateFlow<Pair<ImageBitmap?, WallpaperPaletteData?>>(null to null)
    private val _contextMenuApp = MutableStateFlow<AppEntry?>(null)
    private val _contextMenuFile = MutableStateFlow<DeviceSearchFile?>(null)
    private val _renameTargetApp = MutableStateFlow<AppEntry?>(null)
    private val _iconPickerTargetApp = MutableStateFlow<AppEntry?>(null)
    private val _statusToast = MutableStateFlow<String?>(null)
    private val _isWriterModeOpen = MutableStateFlow(false)
    private val _writerInitialSubTab = MutableStateFlow(WriterSubTab.BOOKS)
    private val _deviceSearchResults = MutableStateFlow<List<DeviceSearchFile>>(emptyList())
    private val _isSearchingDevice = MutableStateFlow(false)
    private val _storageAccessState = MutableStateFlow(false to false)
    private val _localTrackSearchQuery = MutableStateFlow("")

    @Volatile
    private var wasPausedSinceResume: Boolean = false
    private var deviceSearchJob: Job? = null
    private var musicAppRotationIndex: Int = -1

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

    private val baseAppsFlow = combine(
        _allApps,
        prefsRepo.pinnedAppIds,
        prefsRepo.customAppLabels,
        prefsRepo.appIconOverrides,
        _appSearchQuery
    ) { apps, pinnedIds, customLabels, iconOverrides, query ->
        val updatedApps = apps
            .map { app ->
                appsRepo.applyCustomization(
                    app = app,
                    customLabel = customLabels[app.id],
                    override = iconOverrides[app.id],
                    isPinned = pinnedIds.contains(app.id)
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        val filtered = if (query.isBlank()) {
            updatedApps
        } else {
            updatedApps
                .filter {
                    KeyboardSearchMatcher.matches(it.label, query) ||
                        KeyboardSearchMatcher.matches(it.originalLabel, query) ||
                        KeyboardSearchMatcher.matches(it.packageName, query)
                }
                .sortedByDescending {
                    maxOf(
                        KeyboardSearchMatcher.matchScore(it.label, query),
                        KeyboardSearchMatcher.matchScore(it.originalLabel, query)
                    )
                }
        }
        Triple(updatedApps, filtered, updatedApps.filter { it.isPinned })
    }

    private val callsFlow = combine(
        _callLogs,
        _contacts,
        _callsSearchQuery,
        _permissionsState
    ) { logs, contacts, query, perms ->
        val filteredLogs = if (query.isBlank()) {
            logs
        } else {
            logs.filter {
                KeyboardSearchMatcher.matches(it.displayName, query) ||
                    it.number.contains(query.trim(), ignoreCase = true)
            }
        }
        val filteredContacts = if (query.isBlank()) {
            contacts
        } else {
            contacts.filter {
                KeyboardSearchMatcher.matches(it.name, query) ||
                    it.number.contains(query.trim(), ignoreCase = true)
            }
        }
        val starred = contacts.filter { it.isStarred }.distinctBy { it.id }
        CallsBundle(filteredLogs, filteredContacts, starred, perms)
    }

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

    private val focusIndicesFlow = combine(
        _focusedAppIndex,
        _focusedCallLogIndex,
        _focusedContactIndex,
        _focusedHubIndex
    ) { appIdx, callIdx, contactIdx, hubIdx ->
        FocusBundle(appIdx, callIdx, contactIdx, hubIdx)
    }

    private data class FocusBundle(
        val appIdx: Int,
        val callIdx: Int,
        val contactIdx: Int,
        val hubIdx: Int
    )

    private val uiOverlaysFlow = combine(
        focusIndicesFlow,
        _activeScrubLetter,
        _highlightedLetter,
        _showMusicSheet,
        _showShortcutsDialog
    ) { focus, scrub, highlight, musicSheet, shortcuts ->
        OverlayBundle(focus, scrub, highlight, musicSheet, shortcuts)
    }

    private data class OverlayBundle(
        val focus: FocusBundle,
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
            _isSearchingDevice,
            prefsRepo.searchTreeUris,
            combine(_contextMenuFile, _renameTargetApp, _iconPickerTargetApp) { f, r, i -> Triple(f, r, i) }
        ) { res, loading, trees, modalTriple ->
            SearchBundle(
                results = res,
                isSearching = loading,
                indexedTreeCount = trees.size,
                contextMenuFile = modalTriple.first,
                renameTargetApp = modalTriple.second,
                iconPickerTargetApp = modalTriple.third
            )
        }
    ) { notifications, shizuku, writerPair, searchBundle ->
        HubWriterSearchBundle(
            notifications = notifications,
            shizuku = shizuku,
            writerOpen = writerPair.first,
            writerSubTab = writerPair.second,
            searchBundle = searchBundle
        )
    }

    private data class SearchBundle(
        val results: List<DeviceSearchFile>,
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
        _livePositionMs,
        prefsRepo.hubQuickLinks,
        _callsSearchQuery,
        combine(
            combine(_showAddLinkDialog, _contextMenuApp, _isLoadingApps, _statusToast) { a, b, c, d ->
                arrayOf<Any?>(a, b, c, d)
            },
            _storageAccessState,
            mediaRepo.localTracks,
            _localTrackSearchQuery
        ) { extras, storageAccess, tracks, trackQuery ->
            arrayOf<Any?>(extras[0], extras[1], extras[2], extras[3], storageAccess, tracks, trackQuery)
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
            localTrackSearchQuery = trackQuery
        )
    }

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
        val localTrackSearchQuery: String
    )

    private val customizationPrefsFlow = combine(
        prefsRepo.uiThemeColor,
        _wallpaperState,
        prefsRepo.autoKillEnabled,
        prefsRepo.autoKillPackages,
        combine(_showAppearanceDialog, _showAutoKillDialog, _musicDrawerTab) { appDlg, killDlg, mTab ->
            Triple(appDlg, killDlg, mTab)
        }
    ) { themeColor, wallpaperPair, autoKillOn, autoKillPkgs, uiTriple ->
        CustomizationBundle(
            themeColor = themeColor,
            wallpaperBitmap = wallpaperPair.first,
            wallpaperPalette = wallpaperPair.second,
            autoKillEnabled = autoKillOn,
            autoKillPackages = autoKillPkgs,
            showAppearanceDialog = uiTriple.first,
            showAutoKillDialog = uiTriple.second,
            musicDrawerTab = uiTriple.third
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
        val musicDrawerTab: MusicDrawerTab
    )

    val uiState: StateFlow<LauncherUiState> = combine(
        baseAppsFlow,
        callsFlow,
        navFlow,
        uiOverlaysFlow,
        combine(
            combine(mediaAndExtrasFlow, hubWriterSearchFlow) { m, hw -> Pair(m, hw) },
            customizationPrefsFlow
        ) { mh, cust -> mh to cust }
    ) { appsTriple, calls, nav, overlays, rightTriple ->
        val mediaBundle = rightTriple.first.first
        val hw = rightTriple.first.second
        val cust = rightTriple.second
        val (updatedAll, filtered, pinned) = appsTriple
        val coercedAppFocus = if (filtered.isEmpty()) 0 else overlays.focus.appIdx.coerceIn(0, filtered.lastIndex)
        val coercedLogFocus = if (calls.logs.isEmpty()) 0 else overlays.focus.callIdx.coerceIn(0, calls.logs.lastIndex)
        val coercedContactFocus = if (calls.contacts.isEmpty()) 0 else overlays.focus.contactIdx.coerceIn(0, calls.contacts.lastIndex)

        val images = hw.searchBundle.results.filter { it.kind == SearchResultKind.IMAGE }
        val files = hw.searchBundle.results.filter { it.kind == SearchResultKind.FILE }

        LauncherUiState(
            activeTab = nav.activeTab,
            callsSubTab = nav.callsSubTab,
            hubSubTab = nav.hubSubTab,
            alphabetMode = nav.alphabetMode,
            allApps = updatedAll,
            filteredApps = filtered,
            favoriteApps = pinned,
            musicApps = updatedAll.filter { it.hubCategory == HubCategory.MUSIC },
            chatApps = updatedAll.filter { it.hubCategory == HubCategory.CHAT },
            newsApps = updatedAll.filter { it.hubCategory == HubCategory.NEWS },
            appSearchQuery = nav.appSearchQuery,
            searchImages = images,
            searchFiles = files,
            isSearchingDevice = hw.searchBundle.isSearching,
            indexedFolderCount = hw.searchBundle.indexedTreeCount,
            focusedAppIndex = coercedAppFocus,
            focusedCallLogIndex = coercedLogFocus,
            focusedContactIndex = coercedContactFocus,
            focusedHubIndex = overlays.focus.hubIdx,
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
            showMusicSheet = overlays.musicSheet,
            showShortcutsDialog = overlays.shortcuts,
            showAddLinkDialog = mediaBundle.addLink,
            showAppearanceDialog = cust.showAppearanceDialog,
            showAutoKillDialog = cust.showAutoKillDialog,
            uiThemeColor = cust.themeColor,
            wallpaperBitmap = cust.wallpaperBitmap,
            wallpaperPalette = cust.wallpaperPalette,
            hasWallpaper = cust.wallpaperBitmap != null,
            autoKillEnabled = cust.autoKillEnabled,
            autoKillPackages = cust.autoKillPackages,
            contextMenuApp = mediaBundle.ctxApp,
            contextMenuFile = hw.searchBundle.contextMenuFile,
            renameTargetApp = hw.searchBundle.renameTargetApp,
            iconPickerTargetApp = hw.searchBundle.iconPickerTargetApp,
            statusToast = mediaBundle.toast
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LauncherUiState()
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                prefsRepo.appIconOverrides.first().values
                    .mapNotNull { it.customFilePath }
                    .forEach { appsRepo.resolveCustomBitmap(it) }
            }
        }
        refreshInstalledApps()
        refreshCallsAndContacts()
        refreshStorageAndAudioAccess()
        mediaRepo.startObserving()
        shizukuBridge.startObserving()

        viewModelScope.launch {
            prefsRepo.wallpaperPath.collect { path ->
                _wallpaperState.value = WallpaperThemeHelper.loadWallpaperAndPalette(path)
            }
        }

        viewModelScope.launch {
            combine(
                prefsRepo.interceptShadeEnabled,
                prefsRepo.interceptLockScreenEnabled
            ) { shade, lock -> shade to lock }.collect { (shade, lock) ->
                shizukuBridge.updateInterceptFlags(shade, lock)
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
            while (isActive) {
                val active = mediaRepo.mediaState.value.activeSession
                _livePositionMs.value = if (active != null) {
                    MediaControlRepository.currentEstimatedPositionMs(active)
                } else {
                    0L
                }
                delay(500L)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaRepo.stopObserving()
        shizukuBridge.stopObserving()
    }

    fun onLauncherPause() {
        wasPausedSinceResume = true
    }

    fun onResumeRefresh() {
        mediaRepo.refreshListenerStatusAndSessions()
        NotificationHubRepository.refreshFromAttachedService()
        shizukuBridge.refreshStatus()
        refreshCallsAndContacts()
        refreshStorageAndAudioAccess()

        if (wasPausedSinceResume) {
            wasPausedSinceResume = false
            val state = uiState.value
            if (state.autoKillEnabled && state.autoKillPackages.isNotEmpty()) {
                val activeAudioPkgs = state.mediaState.sessions
                    .filter { it.isPlaying }
                    .map { it.packageName }
                    .toSet()
                viewModelScope.launch {
                    val closedCount = autoCloseManager.terminateExitedOneTimeApps(
                        configuredPackages = state.autoKillPackages,
                        activeMediaPackages = activeAudioPkgs
                    )
                    if (closedCount > 0) {
                        val shizukuActive = state.shizukuState.isBinderAlive && state.shizukuState.isPermissionGranted
                        if (shizukuActive) {
                            showToast("Одноразовые приложения завершены ($closedCount)")
                        }
                    }
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
            val defaults = autoCloseManager.discoverDefaultOneTimePackages(loaded)
            prefsRepo.initDefaultAutoKillPackagesIfNeeded(defaults)
        }
    }

    fun refreshCallsAndContacts() {
        val hasLog = callsRepo.hasCallLogPermission()
        val hasContacts = callsRepo.hasContactsPermission()
        val hasCall = callsRepo.hasCallPhonePermission()
        _permissionsState.value = Triple(hasLog, hasContacts, hasCall)

        viewModelScope.launch {
            if (hasLog) {
                _callLogs.value = callsRepo.loadCallLogs()
            }
            if (hasContacts) {
                _contacts.value = callsRepo.loadContacts()
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _isWriterModeOpen.value = false
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
        if (subTab != null) {
            _writerInitialSubTab.value = subTab
        }
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
        triggerDeviceSearch(query)
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
            delay(180L)
            val trees = prefsRepo.searchTreeUris.first()
            val found = universalSearchRepo.searchDevice(trimmed, trees)
            _deviceSearchResults.value = found
            _isSearchingDevice.value = false
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
            if (q.trim().length >= 2) {
                triggerDeviceSearch(q)
            }
        }
    }

    fun openFileContextMenu(file: DeviceSearchFile?) {
        _contextMenuFile.value = file
    }

    fun openSearchFile(file: DeviceSearchFile) {
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

    fun appendAppSearchChar(char: Char) {
        updateAppSearchQuery(_appSearchQuery.value + char)
    }

    fun backspaceAppSearch() {
        val current = _appSearchQuery.value
        if (current.isNotEmpty()) {
            updateAppSearchQuery(current.dropLast(1))
        }
    }

    fun updateCallsSearchQuery(query: String) {
        _callsSearchQuery.value = query
        _focusedCallLogIndex.value = 0
        _focusedContactIndex.value = 0
    }

    fun appendCallsSearchChar(char: Char) {
        updateCallsSearchQuery(_callsSearchQuery.value + char)
    }

    fun backspaceCallsSearch() {
        val current = _callsSearchQuery.value
        if (current.isNotEmpty()) {
            updateCallsSearchQuery(current.dropLast(1))
        }
    }

    fun clearAllSearchOrHighlights() {
        when (_activeTab.value) {
            MainTab.HOME -> {
                _appSearchQuery.value = ""
                _deviceSearchResults.value = emptyList()
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
        _highlightedLetter.value = letter

        if (_activeTab.value == MainTab.CALLS && _callsSubTab.value == CallsSubTab.CONTACTS) {
            val contacts = uiState.value.contacts
            if (contacts.isNotEmpty()) {
                val targetIdx = findBestLetterIndex(contacts.map { it.initialChar }, letter)
                if (targetIdx >= 0) {
                    _focusedContactIndex.value = targetIdx
                    _scrollContactIndexEvents.tryEmit(targetIdx)
                }
            }
            return
        }

        if (_activeTab.value != MainTab.HOME) {
            _activeTab.value = MainTab.HOME
        }
        if (_appSearchQuery.value.isNotEmpty()) {
            updateAppSearchQuery("")
        }
        val apps = uiState.value.allApps
        if (apps.isNotEmpty()) {
            val targetIdx = findBestLetterIndex(apps.map { it.initialChar }, letter)
            if (targetIdx >= 0) {
                _focusedAppIndex.value = targetIdx
                _scrollAppIndexEvents.tryEmit(targetIdx)
            }
        }
    }

    fun jumpToLetter(letter: Char) {
        onAlphabetScrub(letter)
        _activeScrubLetter.value = null
    }

    private fun findBestLetterIndex(initials: List<Char>, letter: Char): Int {
        val exact = initials.indexOfFirst { it == letter }
        if (exact >= 0) return exact
        if (letter == '#') return 0
        val next = initials.indexOfFirst { it != '#' && it >= letter }
        return if (next >= 0) next else initials.lastIndex.coerceAtLeast(0)
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
                    HubSubTab.OTHER -> listCount + 10
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

    private fun computeNextGridIndex(
        current: Int,
        dx: Int,
        dy: Int,
        columns: Int,
        size: Int
    ): Int {
        if (size <= 0) return 0
        val next = current + dx + dy * columns
        return next.coerceIn(0, size - 1)
    }

    fun setFocusedAppIndex(index: Int) {
        val list = uiState.value.filteredApps
        if (list.isNotEmpty()) {
            _focusedAppIndex.value = index.coerceIn(0, list.lastIndex)
        }
    }

    fun activateFocusedItem() {
        val state = uiState.value
        when (state.activeTab) {
            MainTab.HOME -> {
                if (state.filteredApps.isNotEmpty()) {
                    launchFocusedApp()
                } else if (state.searchFiles.isNotEmpty()) {
                    openSearchFile(state.searchFiles.first())
                } else if (state.searchImages.isNotEmpty()) {
                    openSearchFile(state.searchImages.first())
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
                if (idx in items.indices) {
                    openHubNotification(items[idx].key)
                    return
                }
                val remIdx = idx - items.size
                when (state.hubSubTab) {
                    HubSubTab.NEWS -> {
                        val links = state.hubQuickLinks.filter { it.category == HubSubTab.NEWS }
                        links.getOrNull(remIdx)?.let { openUrl(it.url) }
                    }
                    HubSubTab.OTHER -> {
                        when (remIdx) {
                            0 -> setShowAppearanceDialog(true)
                            1 -> setShowAutoKillDialog(true)
                            2 -> openDefaultLauncherSettings()
                            3 -> openNotificationListenerSettings()
                            4 -> setShowMusicSheet(true)
                            5 -> setShowShortcutsDialog(true)
                            6 -> openSystemSettings(Settings.ACTION_WIFI_SETTINGS)
                            7 -> openSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                            8 -> openSystemSettings(Settings.ACTION_SETTINGS)
                            9 -> openAllFilesAccessSettings()
                        }
                    }
                    HubSubTab.CHATS -> Unit
                }
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

    fun toggleShadeInterception() {
        val target = !uiState.value.shizukuState.interceptShadeEnabled
        viewModelScope.launch {
            prefsRepo.setInterceptShadeEnabled(target)
            showToast(
                if (target) "Перехват шторки включён (свайп вниз / Ctrl+N -> Хаб)"
                else "Перехват шторки отключён"
            )
        }
    }

    fun toggleLockScreenInterception() {
        val target = !uiState.value.shizukuState.interceptLockScreenEnabled
        viewModelScope.launch {
            prefsRepo.setInterceptLockScreenEnabled(target)
            showToast(
                if (target) "Symbian-экран блокировки включён"
                else "Symbian-экран блокировки выключен"
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
        val idx = _focusedAppIndex.value
        val target = list.getOrNull(idx) ?: list.firstOrNull() ?: return
        launchApp(target)
    }

    fun launchApp(app: AppEntry) {
        autoCloseManager.recordLaunchedPackage(app.packageName)
        val ok = appsRepo.launchApp(app)
        if (!ok) {
            showToast("Не удалось открыть ${app.label}")
        }
    }

    fun launchPackage(packageName: String) {
        autoCloseManager.recordLaunchedPackage(packageName)
        appsRepo.launchPackage(packageName)
    }

    fun togglePinApp(app: AppEntry) {
        viewModelScope.launch {
            prefsRepo.togglePinApp(app.id)
            val msg = if (app.isPinned) "${app.label} убран из избранного" else "${app.label} добавлен в избранное"
            showToast(msg)
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
            prefsRepo.setAppIconOverride(
                app.id,
                AppIconOverride(mode = AppIconMode.SYMBIAN, symbianKey = symbianKey)
            )
            showToast("Установлена иконка Symbian для «${app.label}»")
        }
    }

    fun setAppIconCustomFromUri(app: AppEntry, sourceUri: Uri) {
        _iconPickerTargetApp.value = null
        viewModelScope.launch {
            val res = appsRepo.importCustomAppIcon(app.id, sourceUri)
            res.onSuccess { path ->
                prefsRepo.setAppIconOverride(
                    app.id,
                    AppIconOverride(mode = AppIconMode.CUSTOM, customFilePath = path)
                )
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
            val result = appsRepo.shareAppAsApk(app)
            result.onFailure { err ->
                showToast(err.message ?: "Не удалось поделиться APK")
            }
        }
    }

    fun getSuggestedApkName(app: AppEntry): String = appsRepo.buildSuggestedApkName(app)

    fun saveAppApkToUri(app: AppEntry, destUri: Uri) {
        _contextMenuApp.value = null
        showToast("Сохранение APK: ${app.label}…")
        viewModelScope.launch {
            val result = appsRepo.saveAppApkToUri(app, destUri)
            result.onSuccess {
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
        appsRepo.uninstallApp(app.packageName)
    }

    fun launchCamera() {
        uiState.value.allApps
            .filter { AutoCloseManager.isHeuristicOneTimeApp(it.packageName, it.originalLabel) }
            .forEach { autoCloseManager.recordLaunchedPackage(it.packageName) }
        val ok = appsRepo.launchCamera()
        if (!ok) {
            showToast("Камера не найдена на устройстве")
        }
    }

    fun expandNotificationShade() {
        if (uiState.value.shizukuState.interceptShadeEnabled) {
            _isWriterModeOpen.value = false
            _activeTab.value = MainTab.HUB
            _hubSubTab.value = HubSubTab.CHATS
            viewModelScope.launch {
                if (uiState.value.shizukuState.isBinderAlive && uiState.value.shizukuState.isPermissionGranted) {
                    shizukuBridge.collapseSystemShadeViaShizuku()
                }
            }
            NotificationHubRepository.refreshFromAttachedService()
            showToast("Шторка перехвачена → BlackBerry Хаб")
        } else {
            appsRepo.expandNotificationShade()
        }
    }

    fun placeCall(numberOrQuery: String) {
        uiState.value.allApps
            .filter { AutoCloseManager.isDialerPackage(it.packageName) }
            .forEach { autoCloseManager.recordLaunchedPackage(it.packageName) }
        val trimmed = numberOrQuery.trim()
        if (trimmed.isEmpty()) {
            val topLog = uiState.value.callLogs.firstOrNull()
            val topContact = uiState.value.contacts.firstOrNull()
            val fallback = topLog?.number ?: topContact?.number.orEmpty()
            if (fallback.isNotEmpty()) {
                callsRepo.placeCall(fallback)
            } else {
                showToast("Введите номер или выберите контакт")
            }
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
        if (!callsRepo.placeCall(dialTarget)) {
            showToast("Не удалось совершить вызов")
        }
    }

    fun openAddContact() {
        callsRepo.openAddContact(_callsSearchQuery.value)
    }

    fun openUrl(url: String) {
        if (!appsRepo.openUrl(url)) {
            showToast("Не найден браузер для открытия ссылки")
        }
    }

    fun openDefaultLauncherSettings() {
        appsRepo.openSystemSettings(Settings.ACTION_HOME_SETTINGS)
    }

    fun openSystemSettings(action: String) {
        appsRepo.openSystemSettings(action)
    }

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
        viewModelScope.launch {
            prefsRepo.removeHubQuickLink(id)
        }
    }

    // Music Controls & Swipe Source Switching across all active sessions + music-capable apps
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
            val updated = mediaRepo.mediaState.value.activeSession
            if (updated != null) {
                showToast("Плеер: ${updated.appLabel}")
            }
            return
        }

        // Fallback when <= 1 active session: cycle through installed music-capable apps
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

    fun cycleMediaSession() {
        swipeMusicSource(+1)
    }

    fun openActivePlayer() {
        if (!mediaRepo.openActivePlayer()) {
            val firstMusicApp = uiState.value.musicApps.firstOrNull()
            if (firstMusicApp != null) {
                launchApp(firstMusicApp)
            } else {
                showToast("Музыкальный плеер не найден")
            }
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

    fun skipToQueueItem(queueId: Long) {
        mediaRepo.skipToQueueItem(queueId)
    }

    fun invokeMediaCustomAction(action: String) {
        mediaRepo.invokeCustomAction(action)
    }

    fun playBrowserTrack(track: MediaBrowserTrack) {
        if (!mediaRepo.playBrowserTrack(track)) {
            showToast("Не удалось запустить «${track.title}»")
        }
    }

    fun searchAndPlayInActiveStream(query: String, packageName: String? = null) {
        val targetQuery = query.trim()
        if (targetQuery.isEmpty()) return
        if (mediaRepo.playFromSearchInStream(targetQuery, packageName)) {
            showToast("Поиск в стриминге: «$targetQuery»")
        } else {
            mediaRepo.searchConnectedMediaBrowser(targetQuery)
            showToast("Откройте медиасессию плеера или выберите сервис ниже")
        }
    }

    fun connectBrowserForPackage(packageName: String?) {
        mediaRepo.connectMediaBrowserForActiveOrPackage(packageName)
    }

    fun playLocalAudioTrack(track: LocalAudioTrack) {
        mediaRepo.playLocalTrack(track)
        showToast("Воспроизведение: ${track.title}")
    }

    fun refreshLocalAudioTracks() {
        viewModelScope.launch {
            mediaRepo.refreshLocalTracks()
            mediaRepo.connectMediaBrowserForActiveOrPackage(
                mediaRepo.mediaState.value.activeSession?.packageName
            )
        }
    }

    fun toggleMusicDrawer() {
        setShowMusicSheet(!_showMusicSheet.value)
    }

    fun setShowMusicSheet(show: Boolean) {
        _showMusicSheet.value = show
        if (show) {
            mediaRepo.refreshListenerStatusAndSessions()
            refreshStorageAndAudioAccess()
        }
    }

    // Appearance, Wallpaper & Vista Theme
    fun setShowAppearanceDialog(show: Boolean) {
        _showAppearanceDialog.value = show
    }

    fun setUiThemeColor(color: UiThemeColor) {
        viewModelScope.launch {
            prefsRepo.setUiThemeColor(color)
            showToast("Цвет интерфейса: ${color.title}")
        }
    }

    fun setWallpaperFromUri(uri: Uri) {
        viewModelScope.launch {
            val res = WallpaperThemeHelper.importWallpaperFromUri(getApplication(), uri)
            res.onSuccess { path ->
                prefsRepo.setWallpaperPath(path)
                _wallpaperState.value = WallpaperThemeHelper.loadWallpaperAndPalette(path)
                showToast("Обои применены • Включён стеклянный режим Aero")
            }.onFailure { err ->
                showToast(err.message ?: "Не удалось установить обои")
            }
        }
    }

    fun clearWallpaper() {
        viewModelScope.launch {
            val oldPath = prefsRepo.wallpaperPath.first()
            prefsRepo.setWallpaperPath(null)
            WallpaperThemeHelper.deleteWallpaperFile(oldPath)
            _wallpaperState.value = null to null
            showToast("Обои убраны • Классический непрозрачный вид")
        }
    }

    // One-Time Auto-Close Apps Settings
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
            showToast(
                if (existed) "Убрано из автозавершения"
                else "Добавлено в одноразовые (будет закрываться при выходе)"
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
