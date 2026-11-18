package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.model.AppAdaptationMode
import com.example.model.AppEntry
import com.example.model.CallsSubTab
import com.example.model.MainTab
import com.example.ui.components.AddHubLinkDialog
import com.example.ui.components.AndroidOptimizationAndSpoofDialog
import com.example.ui.components.AppAdaptationSettingsDialog
import com.example.ui.components.AppContextDialog
import com.example.ui.components.AppIconPickerDialog
import com.example.ui.components.AppTileGrid
import com.example.ui.components.AppearanceSettingsDialog
import com.example.ui.components.AutoKillSettingsDialog
import com.example.ui.components.BackgroundWebAudioDialog
import com.example.ui.components.Bb10RecentsOverlay
import com.example.ui.components.CallsContactsView
import com.example.ui.components.FavoritesAndAlphabetRail
import com.example.ui.components.FileContextDialog
import com.example.ui.components.HubView
import com.example.ui.components.InlineMusicControlDrawer
import com.example.ui.components.KeyboardShortcutsDialog
import com.example.ui.components.LandscapeSymbianSidebar
import com.example.ui.components.PortraitMusicHeaderBar
import com.example.ui.components.RenameAppDialog
import com.example.ui.components.VistaButton
import com.example.ui.components.WriterStudioScreen
import com.example.ui.theme.LocalVistaTheme
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.symbianMetallicBar
import com.example.ui.theme.vistaAeroButton
import com.example.ui.theme.vistaWindowFrame
import com.example.viewmodel.LauncherUiState
import com.example.viewmodel.LauncherViewModel
import com.example.viewmodel.WriterViewModel

@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel,
    appSearchFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    writerViewModel: WriterViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val appGridState = rememberLazyGridState()
    val callLogListState = rememberLazyListState()
    val contactsGridState = rememberLazyGridState()
    val hubListState = rememberLazyListState()

    val callsSearchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshCallsAndContacts()
    }

    val storageAudioPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshStorageAndAudioAccess()
    }

    fun requestMediaAndAudioPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        storageAudioPermissionsLauncher.launch(perms)
    }

    val folderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let(viewModel::addSearchFolderTree)
    }

    var pendingCustomIconApp by remember { mutableStateOf<AppEntry?>(null) }
    val customIconPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val target = pendingCustomIconApp
        pendingCustomIconApp = null
        if (uri != null && target != null) {
            viewModel.setAppIconCustomFromUri(target, uri)
        }
    }

    val wallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let(viewModel::setWallpaperFromUri)
    }

    var pendingSaveApkApp by remember { mutableStateOf<AppEntry?>(null) }
    val saveApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
        val target = pendingSaveApkApp
        pendingSaveApkApp = null
        if (uri != null && target != null) {
            viewModel.saveAppApkToUri(target, uri)
        }
    }

    var didPromptPermissionsOnLaunch by rememberSaveable { mutableStateOf(false) }
    var showAllFilesPromptDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshStorageAndAudioAccess()
        if (!didPromptPermissionsOnLaunch) {
            didPromptPermissionsOnLaunch = true
            if (!viewModel.uiState.value.hasAudioReadAccess) {
                requestMediaAndAudioPermissions()
            }
            if (!viewModel.uiState.value.hasAllFilesAccess) {
                showAllFilesPromptDialog = true
            }
        }
    }

    LaunchedEffect(state.hasAllFilesAccess) {
        if (state.hasAllFilesAccess) {
            showAllFilesPromptDialog = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.scrollAppIndexEvents.collect { index ->
            if (index >= 0) {
                val offset = if (viewModel.uiState.value.appSearchQuery.trim().length >= 2) 2 else 0
                appGridState.animateScrollToItem(index + offset)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.scrollCallLogIndexEvents.collect { index ->
            if (index >= 0) callLogListState.animateScrollToItem(index)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.scrollContactIndexEvents.collect { index ->
            if (index >= 0) contactsGridState.animateScrollToItem(index)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.scrollHubIndexEvents.collect { index ->
            if (index >= 0) hubListState.animateScrollToItem(index)
        }
    }

    val hasAnyModalOpen = state.showMusicSheet ||
        showAllFilesPromptDialog ||
        state.showAppearanceDialog ||
        state.showAutoKillDialog ||
        state.showOptimizationDialog ||
        state.showAdaptationDialog ||
        state.showBackgroundWebAudioDialog ||
        state.showShortcutsDialog ||
        state.showAddLinkDialog ||
        state.contextMenuApp != null ||
        state.contextMenuFile != null ||
        state.renameTargetApp != null ||
        state.iconPickerTargetApp != null

    val shouldHandleBack = hasAnyModalOpen ||
        state.showRecentsOverlay ||
        state.isWriterModeOpen ||
        state.appSearchQuery.isNotEmpty() ||
        state.callsSearchQuery.isNotEmpty() ||
        state.highlightedLetter != null ||
        state.activeTab != MainTab.HOME

    BackHandler(enabled = shouldHandleBack) {
        when {
            showAllFilesPromptDialog -> showAllFilesPromptDialog = false
            state.showRecentsOverlay -> viewModel.closeRecentsOverlay()
            state.showBackgroundWebAudioDialog -> viewModel.setShowBackgroundWebAudioDialog(false)
            state.showAdaptationDialog -> viewModel.setShowAdaptationDialog(false)
            state.showOptimizationDialog -> viewModel.setShowOptimizationDialog(false)
            state.showAppearanceDialog -> viewModel.setShowAppearanceDialog(false)
            state.showAutoKillDialog -> viewModel.setShowAutoKillDialog(false)
            state.showMusicSheet -> viewModel.setShowMusicSheet(false)
            state.showShortcutsDialog -> viewModel.setShowShortcutsDialog(false)
            state.showAddLinkDialog -> viewModel.setShowAddLinkDialog(false)
            state.contextMenuApp != null -> viewModel.openContextMenu(null)
            state.contextMenuFile != null -> viewModel.openFileContextMenu(null)
            state.renameTargetApp != null -> viewModel.openRenameAppDialog(null)
            state.iconPickerTargetApp != null -> viewModel.openIconPickerDialog(null)
            state.isWriterModeOpen -> viewModel.closeWriterMode()
            state.appSearchQuery.isNotEmpty() || state.callsSearchQuery.isNotEmpty() || state.highlightedLetter != null -> {
                viewModel.clearAllSearchOrHighlights()
                focusManager.clearFocus()
            }
            state.activeTab != MainTab.HOME -> viewModel.selectTab(MainTab.HOME)
        }
    }

    val theme = LocalVistaTheme.current
    val lang = LocalAppLang.current
    val desktopScale by animateFloatAsState(
        targetValue = if (state.showRecentsOverlay) 0.86f else 1.0f,
        label = "recents_desktop_scale"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .pointerInput(state.showRecentsOverlay, state.isWriterModeOpen, hasAnyModalOpen) {
                if (!state.showRecentsOverlay && !state.isWriterModeOpen && !hasAnyModalOpen) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom < 0.85f) {
                            viewModel.openRecentsOverlay()
                        }
                    }
                }
            }
    ) {
        state.wallpaperBitmap?.let { wallBmp ->
            Image(
                bitmap = wallBmp,
                contentDescription = "Обои рабочего стола",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.24f))
            )
        }

        val isLandscape = maxWidth > maxHeight || maxWidth >= 640.dp
        val gridColumns = if (isLandscape) 3 else 2

        LaunchedEffect(isLandscape) {
            viewModel.isLandscapeLayout = isLandscape
        }

        val presentLetters = remember(state.activeTab, state.callsSubTab, state.allApps, state.contacts) {
            if (state.activeTab == MainTab.CALLS && state.callsSubTab == CallsSubTab.CONTACTS) {
                state.contacts.map { it.initialChar }.toSet()
            } else {
                state.allApps.map { it.initialChar }.toSet()
            }
        }

        if (isLandscape) {
            // LANDSCAPE MODE: Player & vertical tabs on left; track/streaming drawer slides out SIDEWAYS to the right!
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = desktopScale
                        scaleY = desktopScale
                    }
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                LandscapeSymbianSidebar(
                    mediaState = state.mediaState,
                    livePositionMs = state.livePositionMs,
                    isDrawerOpen = state.showMusicSheet,
                    coverStyle = state.playerCoverStyle,
                    activeTab = state.activeTab,
                    callsSubTab = state.callsSubTab,
                    hubSubTab = state.hubSubTab,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onSkipPrevious = viewModel::skipPrevious,
                    onSkipNext = viewModel::skipNext,
                    onSeekTo = viewModel::seekTo,
                    onCyclePlayer = viewModel::cycleMediaSession,
                    onSwipeMusicSource = viewModel::swipeMusicSource,
                    onToggleMute = viewModel::toggleMute,
                    onTogglePlayerDrawer = viewModel::toggleMusicDrawer,
                    onSelectMainTab = viewModel::selectTab,
                    onSelectCallsSubTab = viewModel::selectCallsSubTab,
                    onSelectHubSubTab = viewModel::selectHubSubTab,
                    onLaunchCamera = viewModel::launchCamera,
                    onOpenWriter = { viewModel.openWriterMode() }
                )

                VerticalDivider(color = theme.chromeBorderOuter, thickness = 1.dp)

                AnimatedVisibility(
                    visible = state.showMusicSheet,
                    enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()
                ) {
                    InlineMusicControlDrawer(
                        mediaState = state.mediaState,
                        livePositionMs = state.livePositionMs,
                        selectedTab = state.musicDrawerTab,
                        localTracks = state.localAudioTracks,
                        telegramNotifications = state.hubNotifications,
                        trackSearchQuery = state.localTrackSearchQuery,
                        hasAudioReadAccess = state.hasAudioReadAccess,
                        hasAllFilesAccess = state.hasAllFilesAccess,
                        musicApps = state.musicApps,
                        isLandscape = true,
                        backgroundSpoofEnabled = state.backgroundSpoofEnabled,
                        isCurrentSessionSpoofed = state.mediaState.activeSession?.packageName?.let {
                            state.backgroundSpoofPackages.contains(it)
                        } ?: false,
                        onToggleCurrentSessionSpoof = viewModel::toggleCurrentSessionBackgroundSpoof,
                        onOpenBackgroundWebAudio = { viewModel.setShowBackgroundWebAudioDialog(true) },
                        onSelectTab = viewModel::selectMusicDrawerTab,
                        onTrackSearchChange = viewModel::updateLocalTrackSearchQuery,
                        onSkipToQueueItem = viewModel::skipToQueueItem,
                        onInvokeCustomAction = viewModel::invokeMediaCustomAction,
                        onLikeStreamTrack = viewModel::likeStreamTrack,
                        onDislikeStreamTrack = viewModel::dislikeStreamTrack,
                        onStartMyWave = viewModel::startMyWave,
                        onPlayBrowserTrack = viewModel::playBrowserTrack,
                        onSearchAndPlayInStream = viewModel::searchAndPlayInActiveStream,
                        onConnectBrowser = viewModel::connectBrowserForPackage,
                        onPlayLocalTrack = viewModel::playLocalAudioTrack,
                        onOpenHubNotification = viewModel::openHubNotification,
                        onRefreshTracks = viewModel::refreshLocalAudioTracks,
                        onRequestAudioPermission = { requestMediaAndAudioPermissions() },
                        onRequestAllFilesAccess = viewModel::openAllFilesAccessSettings,
                        onClose = { viewModel.setShowMusicSheet(false) },
                        onSelectSession = viewModel::selectMediaSession,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onSkipPrevious = viewModel::skipPrevious,
                        onSkipNext = viewModel::skipNext,
                        onSeekTo = viewModel::seekTo,
                        onSeekBy = viewModel::seekBy,
                        onOpenActivePlayer = viewModel::openActivePlayer,
                        onSetVolume = viewModel::setVolume,
                        onToggleMute = viewModel::toggleMute,
                        onOpenNotificationAccess = viewModel::openNotificationListenerSettings,
                        onLaunchMusicApp = viewModel::launchApp
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    MainTabContent(
                        activeTab = state.activeTab,
                        isLandscape = true,
                        gridColumns = gridColumns,
                        state = state,
                        appGridState = appGridState,
                        callLogListState = callLogListState,
                        contactsGridState = contactsGridState,
                        hubListState = hubListState,
                        appSearchFocusRequester = appSearchFocusRequester,
                        callsSearchFocusRequester = callsSearchFocusRequester,
                        viewModel = viewModel,
                        onConnectSearchFolder = { folderTreeLauncher.launch(null) },
                        onRequestCallsPermissions = {
                            permissionsLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CALL_LOG,
                                    Manifest.permission.READ_CONTACTS,
                                    Manifest.permission.CALL_PHONE
                                )
                            )
                        }
                    )
                }

                if (state.activeTab != MainTab.HUB) {
                    FavoritesAndAlphabetRail(
                        activeTab = state.activeTab,
                        alphabetMode = state.alphabetMode,
                        favoriteApps = state.favoriteApps,
                        starredContacts = state.starredContacts,
                        presentLetters = presentLetters,
                        activeScrubLetter = state.activeScrubLetter,
                        highlightedLetter = state.highlightedLetter,
                        onToggleAlphabetMode = viewModel::toggleAlphabetMode,
                        onScrubLetter = viewModel::onAlphabetScrub,
                        onTapLetter = viewModel::jumpToLetter,
                        onLaunchApp = viewModel::launchApp,
                        onLongPressApp = viewModel::openContextMenu,
                        onCallContact = { viewModel.placeCall(it.number) }
                    )
                }
            }
        } else {
            // PORTRAIT MODE: Player at top; swiping down slides inline drawer out from under player and pushes tile grid + rail down!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = desktopScale
                        scaleY = desktopScale
                    }
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                PortraitMusicHeaderBar(
                    mediaState = state.mediaState,
                    livePositionMs = state.livePositionMs,
                    isDrawerOpen = state.showMusicSheet,
                    coverStyle = state.playerCoverStyle,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onSkipPrevious = viewModel::skipPrevious,
                    onSkipNext = viewModel::skipNext,
                    onSeekTo = viewModel::seekTo,
                    onCyclePlayer = viewModel::cycleMediaSession,
                    onSwipeMusicSource = viewModel::swipeMusicSource,
                    onTogglePlayerDrawer = viewModel::toggleMusicDrawer,
                    onOpenPlayerDrawer = { viewModel.setShowMusicSheet(true) },
                    onClosePlayerDrawer = { viewModel.setShowMusicSheet(false) }
                )

                AnimatedVisibility(
                    visible = state.showMusicSheet,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                ) {
                    InlineMusicControlDrawer(
                        mediaState = state.mediaState,
                        livePositionMs = state.livePositionMs,
                        selectedTab = state.musicDrawerTab,
                        localTracks = state.localAudioTracks,
                        telegramNotifications = state.hubNotifications,
                        trackSearchQuery = state.localTrackSearchQuery,
                        hasAudioReadAccess = state.hasAudioReadAccess,
                        hasAllFilesAccess = state.hasAllFilesAccess,
                        musicApps = state.musicApps,
                        isLandscape = false,
                        backgroundSpoofEnabled = state.backgroundSpoofEnabled,
                        isCurrentSessionSpoofed = state.mediaState.activeSession?.packageName?.let {
                            state.backgroundSpoofPackages.contains(it)
                        } ?: false,
                        onToggleCurrentSessionSpoof = viewModel::toggleCurrentSessionBackgroundSpoof,
                        onOpenBackgroundWebAudio = { viewModel.setShowBackgroundWebAudioDialog(true) },
                        onSelectTab = viewModel::selectMusicDrawerTab,
                        onTrackSearchChange = viewModel::updateLocalTrackSearchQuery,
                        onSkipToQueueItem = viewModel::skipToQueueItem,
                        onInvokeCustomAction = viewModel::invokeMediaCustomAction,
                        onLikeStreamTrack = viewModel::likeStreamTrack,
                        onDislikeStreamTrack = viewModel::dislikeStreamTrack,
                        onStartMyWave = viewModel::startMyWave,
                        onPlayBrowserTrack = viewModel::playBrowserTrack,
                        onSearchAndPlayInStream = viewModel::searchAndPlayInActiveStream,
                        onConnectBrowser = viewModel::connectBrowserForPackage,
                        onPlayLocalTrack = viewModel::playLocalAudioTrack,
                        onOpenHubNotification = viewModel::openHubNotification,
                        onRefreshTracks = viewModel::refreshLocalAudioTracks,
                        onRequestAudioPermission = { requestMediaAndAudioPermissions() },
                        onRequestAllFilesAccess = viewModel::openAllFilesAccessSettings,
                        onClose = { viewModel.setShowMusicSheet(false) },
                        onSelectSession = viewModel::selectMediaSession,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onSkipPrevious = viewModel::skipPrevious,
                        onSkipNext = viewModel::skipNext,
                        onSeekTo = viewModel::seekTo,
                        onSeekBy = viewModel::seekBy,
                        onOpenActivePlayer = viewModel::openActivePlayer,
                        onSetVolume = viewModel::setVolume,
                        onToggleMute = viewModel::toggleMute,
                        onOpenNotificationAccess = viewModel::openNotificationListenerSettings,
                        onLaunchMusicApp = viewModel::launchApp
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        MainTabContent(
                            activeTab = state.activeTab,
                            isLandscape = false,
                            gridColumns = gridColumns,
                            state = state,
                            appGridState = appGridState,
                            callLogListState = callLogListState,
                            contactsGridState = contactsGridState,
                            hubListState = hubListState,
                            appSearchFocusRequester = appSearchFocusRequester,
                            callsSearchFocusRequester = callsSearchFocusRequester,
                            viewModel = viewModel,
                            onConnectSearchFolder = { folderTreeLauncher.launch(null) },
                            onRequestCallsPermissions = {
                                permissionsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_CALL_LOG,
                                        Manifest.permission.READ_CONTACTS,
                                        Manifest.permission.CALL_PHONE
                                    )
                                )
                            }
                        )
                    }

                    if (state.activeTab != MainTab.HUB) {
                        FavoritesAndAlphabetRail(
                            activeTab = state.activeTab,
                            alphabetMode = state.alphabetMode,
                            favoriteApps = state.favoriteApps,
                            starredContacts = state.starredContacts,
                            presentLetters = presentLetters,
                            activeScrubLetter = state.activeScrubLetter,
                            highlightedLetter = state.highlightedLetter,
                            onToggleAlphabetMode = viewModel::toggleAlphabetMode,
                            onScrubLetter = viewModel::onAlphabetScrub,
                            onTapLetter = viewModel::jumpToLetter,
                            onLaunchApp = viewModel::launchApp,
                            onLongPressApp = viewModel::openContextMenu,
                            onCallContact = { viewModel.placeCall(it.number) }
                        )
                    }
                }

                // Bottom Windows Vista / Symbian S60 Softkey Taskbar
                HorizontalDivider(color = theme.chromeBorderOuter, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .symbianMetallicBar()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .height(52.dp)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PortraitNavButton(
                        label = L10n.camera(lang),
                        iconRes = R.drawable.ic_symbian_camera,
                        selected = false,
                        onClick = viewModel::launchCamera,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_camera_portrait")
                    )
                    PortraitNavButton(
                        label = L10n.tab(lang, MainTab.HOME),
                        iconRes = R.drawable.ic_symbian_menu,
                        selected = state.activeTab == MainTab.HOME && !state.isWriterModeOpen,
                        onClick = { viewModel.selectTab(MainTab.HOME) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_home_portrait")
                    )
                    PortraitNavButton(
                        label = L10n.tab(lang, MainTab.CALLS),
                        iconRes = R.drawable.ic_symbian_phone,
                        selected = state.activeTab == MainTab.CALLS && !state.isWriterModeOpen,
                        onClick = { viewModel.selectTab(MainTab.CALLS) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_calls_portrait")
                    )
                    PortraitNavButton(
                        label = L10n.tab(lang, MainTab.HUB),
                        iconRes = R.drawable.ic_symbian_messaging,
                        selected = state.activeTab == MainTab.HUB && !state.isWriterModeOpen,
                        onClick = { viewModel.selectTab(MainTab.HUB) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_hub_portrait")
                    )
                    PortraitNavButton(
                        label = L10n.books(lang),
                        iconRes = R.drawable.ic_symbian_editor,
                        selected = state.isWriterModeOpen,
                        onClick = { viewModel.openWriterMode() },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_writer_portrait")
                    )
                }
            }
        }

        // BlackBerry OS 10 / KEY2 Active Frames Recents System Overlay
        AnimatedVisibility(
            visible = state.showRecentsOverlay,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Bb10RecentsOverlay(
                frames = state.recentFrames,
                focusedIndex = state.focusedRecentIndex,
                isLandscape = isLandscape,
                onOpenFrame = viewModel::launchRecentFrame,
                onCloseFrame = viewModel::closeRecentFrame,
                onTogglePinFrame = { viewModel.togglePinRecentFrame(it.packageName) },
                onOpenFrameInfo = viewModel::openRecentFrameInfo,
                onCloseAllExceptPinned = viewModel::closeAllRecentsExceptPinned,
                onDismiss = viewModel::closeRecentsOverlay,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
            )
        }

        // Full-Screen Book & Notes Writer Studio Overlay
        AnimatedVisibility(
            visible = state.isWriterModeOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            WriterStudioScreen(
                writerViewModel = writerViewModel,
                isLandscape = isLandscape,
                onClose = viewModel::closeWriterMode
            )
        }

        // Floating Retro Windows Vista Status Toast
        AnimatedVisibility(
            visible = state.statusToast != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 64.dp, start = 20.dp, end = 20.dp)
        ) {
            state.statusToast?.let { msg ->
                Box(
                    modifier = Modifier
                        .vistaWindowFrame(RoundedCornerShape(4.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = TextPrimary
                    )
                }
            }
        }
    }

    if (showAllFilesPromptDialog && !state.hasAllFilesAccess) {
        AllFilesAccessPromptDialog(
            onGrantClick = {
                showAllFilesPromptDialog = false
                viewModel.openAllFilesAccessSettings()
            },
            onDismiss = { showAllFilesPromptDialog = false }
        )
    }

    state.contextMenuApp?.let { app ->
        AppContextDialog(
            app = app,
            isAutoKill = state.autoKillPackages.contains(app.packageName),
            adaptationMode = state.appAdaptationModes[app.packageName] ?: AppAdaptationMode.DISABLED,
            isBackgroundSpoofed = state.backgroundSpoofPackages.contains(app.packageName),
            onDismiss = { viewModel.openContextMenu(null) },
            onLaunch = { viewModel.launchApp(app) },
            onTogglePin = { viewModel.togglePinApp(app) },
            onToggleAutoKill = { viewModel.toggleAutoKillPackage(app.packageName) },
            onCycleAdaptationMode = { viewModel.cycleAppAdaptationMode(app.packageName) },
            onToggleBackgroundSpoof = { viewModel.toggleBackgroundSpoofPackage(app.packageName) },
            onRename = { viewModel.openRenameAppDialog(app) },
            onChooseIcon = { viewModel.openIconPickerDialog(app) },
            onShareApk = { viewModel.shareAppAsApk(app) },
            onSaveApk = {
                pendingSaveApkApp = app
                viewModel.openContextMenu(null)
                saveApkLauncher.launch(viewModel.getSuggestedApkName(app))
            },
            onOpenInfo = { viewModel.openAppInfo(app) },
            onUninstall = { viewModel.uninstallApp(app) }
        )
    }

    if (state.showAppearanceDialog) {
        AppearanceSettingsDialog(
            uiThemeColor = state.uiThemeColor,
            hasWallpaper = state.hasWallpaper,
            wallpaperBitmap = state.wallpaperBitmap,
            wallpaperPalette = state.wallpaperPalette,
            appLanguage = state.appLanguage,
            uiScale = state.uiScale,
            playerCoverStyle = state.playerCoverStyle,
            onSelectThemeColor = viewModel::setUiThemeColor,
            onPickWallpaper = {
                wallpaperPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onClearWallpaper = viewModel::clearWallpaper,
            onSelectLanguage = viewModel::setAppLanguage,
            onChangeUiScale = viewModel::setUiScale,
            onSelectCoverStyle = viewModel::setPlayerCoverStyle,
            onDismiss = { viewModel.setShowAppearanceDialog(false) }
        )
    }

    if (state.showAutoKillDialog) {
        AutoKillSettingsDialog(
            enabled = state.autoKillEnabled,
            autoKillPackages = state.autoKillPackages,
            allApps = state.allApps,
            shizukuReady = state.shizukuState.isBinderAlive && state.shizukuState.isPermissionGranted,
            isRootAvailable = state.shizukuState.isRootAvailable,
            onToggleEnabled = viewModel::toggleAutoKillEnabled,
            onTogglePackage = viewModel::toggleAutoKillPackage,
            onDismiss = { viewModel.setShowAutoKillDialog(false) }
        )
    }

    if (state.showOptimizationDialog) {
        AndroidOptimizationAndSpoofDialog(
            stats = state.optimizationStats,
            shizukuState = state.shizukuState,
            backgroundSpoofEnabled = state.backgroundSpoofEnabled,
            backgroundSpoofPackages = state.backgroundSpoofPackages,
            allApps = state.allApps,
            onOptimizeNow = viewModel::optimizeAndroidSystem,
            onDisablePhantomKiller = viewModel::disablePhantomKillerViaShizuku,
            onToggleBackgroundSpoofEnabled = viewModel::toggleBackgroundSpoofEnabled,
            onToggleBackgroundSpoofPackage = viewModel::toggleBackgroundSpoofPackage,
            onBoostPackageViaShizuku = viewModel::boostAppForBackgroundViaShizuku,
            onOpenBackgroundWebAudio = { viewModel.setShowBackgroundWebAudioDialog(true) },
            onDismiss = { viewModel.setShowOptimizationDialog(false) }
        )
    }

    if (state.showAdaptationDialog) {
        AppAdaptationSettingsDialog(
            enabled = state.appAdaptationEnabled,
            adaptationModes = state.appAdaptationModes,
            allApps = state.allApps,
            shizukuState = state.shizukuState,
            onToggleEnabled = viewModel::toggleAppAdaptationEnabled,
            onSetAppMode = viewModel::setAppAdaptationMode,
            onGrantViaShizuku = viewModel::grantOverlayAndAccessibilityViaShizuku,
            onToggleOemGestureBypass = viewModel::toggleOemGestureBypass,
            onOpenAccessibilitySettings = viewModel::openAccessibilitySettings,
            onOpenOverlaySettings = viewModel::openOverlaySettings,
            onDismiss = { viewModel.setShowAdaptationDialog(false) }
        )
    }

    if (state.showBackgroundWebAudioDialog) {
        BackgroundWebAudioDialog(
            onDismiss = { viewModel.setShowBackgroundWebAudioDialog(false) }
        )
    }

    state.renameTargetApp?.let { app ->
        RenameAppDialog(
            app = app,
            onDismiss = { viewModel.openRenameAppDialog(null) },
            onSave = { newLabel -> viewModel.saveAppRename(app, newLabel) },
            onReset = { viewModel.resetAppRename(app) }
        )
    }

    state.iconPickerTargetApp?.let { app ->
        AppIconPickerDialog(
            app = app,
            onDismiss = { viewModel.openIconPickerDialog(null) },
            onSelectAuto = { viewModel.setAppIconAuto(app) },
            onSelectOriginal = { viewModel.setAppIconOriginal(app) },
            onSelectSymbianKey = { key -> viewModel.setAppIconSymbian(app, key) },
            onPickCustomImage = {
                pendingCustomIconApp = app
                customIconPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )
    }

    state.contextMenuFile?.let { file ->
        FileContextDialog(
            file = file,
            onDismiss = { viewModel.openFileContextMenu(null) },
            onOpen = { viewModel.openSearchFile(file) },
            onShare = { viewModel.shareSearchFile(file) },
            onOpenFolder = { viewModel.openSearchFileFolder(file) },
            onCopyPath = { viewModel.copySearchFilePath(file) },
            onDelete = { viewModel.deleteSearchFile(file) }
        )
    }

    if (state.showShortcutsDialog) {
        KeyboardShortcutsDialog(onDismiss = { viewModel.setShowShortcutsDialog(false) })
    }

    if (state.showAddLinkDialog) {
        AddHubLinkDialog(
            initialCategory = state.hubSubTab,
            onDismiss = { viewModel.setShowAddLinkDialog(false) },
            onConfirm = viewModel::addHubQuickLink
        )
    }
}

@Composable
private fun AllFilesAccessPromptDialog(
    onGrantClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalVistaTheme.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .vistaWindowFrame(RoundedCornerShape(5.dp))
                .testTag("all_files_prompt_dialog")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_symbian_files),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "ДОСТУП КО ВСЕМ ФАЙЛАМ",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = theme.focusBorder
                )
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Разрешите KeyDeck доступ ко всем файлам на устройстве, чтобы универсальный поиск находил документы, APK, архивы и музыку в любых папках памяти.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Откроется системный экран разрешений для KeyDeck.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MusicSourceAmber
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    VistaButton(text = "Позже", onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    VistaButton(
                        text = "Выдать доступ",
                        isPrimary = true,
                        onClick = onGrantClick,
                        modifier = Modifier.testTag("all_files_prompt_confirm")
                    )
                }
            }
        }
    }
}

@Composable
private fun MainTabContent(
    activeTab: MainTab,
    isLandscape: Boolean,
    gridColumns: Int,
    state: LauncherUiState,
    appGridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    callLogListState: LazyListState,
    contactsGridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    hubListState: LazyListState,
    appSearchFocusRequester: FocusRequester,
    callsSearchFocusRequester: FocusRequester,
    viewModel: LauncherViewModel,
    onConnectSearchFolder: () -> Unit,
    onRequestCallsPermissions: () -> Unit
) {
    when (activeTab) {
        MainTab.HOME -> {
            AppTileGrid(
                apps = state.filteredApps,
                searchImages = state.searchImages,
                searchFiles = state.searchFiles,
                musicSearchResults = state.musicSearchResults,
                isSearchingDevice = state.isSearchingDevice,
                indexedFolderCount = state.indexedFolderCount,
                hasAllFilesAccess = state.hasAllFilesAccess,
                columns = gridColumns,
                searchQuery = state.appSearchQuery,
                focusedIndex = state.focusedAppIndex,
                highlightedLetter = state.highlightedLetter,
                isLoading = state.isLoadingApps,
                gridState = appGridState,
                searchFocusRequester = appSearchFocusRequester,
                isSearchFocused = viewModel.isAppSearchInputFocused,
                embeddedSnapshot = state.embeddedSnapshot,
                messengerSearchMode = state.messengerSearchMode,
                onToggleMessengerSearchMode = viewModel::toggleMessengerSearchMode,
                onSearchFocusChange = { viewModel.isAppSearchInputFocused = it },
                onRequestSearchFocus = { runCatching { appSearchFocusRequester.requestFocus() } },
                onSearchQueryChange = viewModel::updateAppSearchQuery,
                onClearSearch = viewModel::clearAllSearchOrHighlights,
                onLaunchApp = viewModel::launchApp,
                onLongPressApp = viewModel::openContextMenu,
                onOpenSearchFile = viewModel::openSearchFile,
                onLongPressSearchFile = viewModel::openFileContextMenu,
                onLaunchMusicMatch = viewModel::launchMusicSearchMatch,
                onSendFileToMessenger = viewModel::sendFileToMessenger,
                onSendAdaptationCommand = viewModel::sendAdaptationCommand,
                onDisableEmbeddedAdaptation = viewModel::disableEmbeddedAdaptation,
                onRestoreEmbeddedApp = viewModel::restoreAdaptedApp,
                onDismissEmbedded = viewModel::dismissEmbeddedSnapshot,
                onConnectSearchFolder = onConnectSearchFolder,
                onRequestAllFilesAccess = viewModel::openAllFilesAccessSettings,
                onSearchWeb = viewModel::searchOnWeb,
                onOpenShortcuts = { viewModel.setShowShortcutsDialog(true) },
                onOpenWriter = { viewModel.openWriterMode() }
            )
        }

        MainTab.CALLS -> {
            CallsContactsView(
                subTab = state.callsSubTab,
                isLandscape = isLandscape,
                searchQuery = state.callsSearchQuery,
                callLogs = state.callLogs,
                contacts = state.contacts,
                focusedCallLogIndex = state.focusedCallLogIndex,
                focusedContactIndex = state.focusedContactIndex,
                hasCallLogPermission = state.hasCallLogPermission,
                hasContactsPermission = state.hasContactsPermission,
                highlightedLetter = state.highlightedLetter,
                callLogListState = callLogListState,
                contactsGridState = contactsGridState,
                callsSearchFocusRequester = callsSearchFocusRequester,
                onSearchFocusChange = { viewModel.isCallsSearchInputFocused = it },
                onSelectSubTab = viewModel::selectCallsSubTab,
                onSearchQueryChange = viewModel::updateCallsSearchQuery,
                onCallClick = viewModel::placeCall,
                onAddContactClick = viewModel::openAddContact,
                onResetClick = viewModel::clearAllSearchOrHighlights,
                onRequestPermissions = onRequestCallsPermissions
            )
        }

        MainTab.HUB -> {
            HubView(
                subTab = state.hubSubTab,
                isLandscape = isLandscape,
                notifications = state.hubNotifications,
                shizukuState = state.shizukuState,
                quickLinks = state.hubQuickLinks,
                focusedIndex = state.focusedHubIndex,
                listState = hubListState,
                onSelectSubTab = viewModel::selectHubSubTab,
                onReplyNotification = viewModel::replyToHubNotification,
                onOpenNotification = viewModel::openHubNotification,
                onInvokeNotificationAction = viewModel::invokeHubNotificationAction,
                onDismissNotification = viewModel::dismissHubNotification,
                onRefreshNotifications = viewModel::refreshHubNotifications,
                onOpenUrl = viewModel::openUrl,
                onShowAddLinkDialog = { viewModel.setShowAddLinkDialog(true) },
                onRemoveQuickLink = viewModel::removeHubQuickLink,
                onRequestShizukuPermission = viewModel::requestShizukuPermission,
                onOpenShizukuApp = viewModel::openShizukuApp,
                onGrantNotificationViaShizuku = viewModel::grantNotificationAccessViaShizuku,
                onToggleShadeInterception = viewModel::toggleShadeInterception,
                onToggleLockScreenInterception = viewModel::toggleLockScreenInterception,
                onToggleOemGestureBypass = viewModel::toggleOemGestureBypass,
                onPreviewLockScreen = viewModel::openLockScreenPreview,
                onOpenAppearanceSettings = { viewModel.setShowAppearanceDialog(true) },
                onOpenAutoKillSettings = { viewModel.setShowAutoKillDialog(true) },
                onOpenOptimizationSettings = { viewModel.setShowOptimizationDialog(true) },
                onOpenAdaptationSettings = { viewModel.setShowAdaptationDialog(true) },
                onGrantOverlayAndAccessibilityViaShizuku = viewModel::grantOverlayAndAccessibilityViaShizuku,
                onOpenDefaultLauncherSettings = viewModel::openDefaultLauncherSettings,
                onOpenNotificationAccess = viewModel::openNotificationListenerSettings,
                onOpenAllFilesAccess = viewModel::openAllFilesAccessSettings,
                onOpenMusicSheet = { viewModel.setShowMusicSheet(true) },
                onOpenShortcuts = { viewModel.setShowShortcutsDialog(true) },
                onOpenSystemSettings = viewModel::openSystemSettings
            )
        }
    }
}

@Composable
private fun PortraitNavButton(
    label: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .minimumInteractiveComponentSize()
            .vistaAeroButton(isPrimary = selected, isActive = selected, shape = RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) Color.White else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
