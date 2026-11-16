package com.example.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.model.AppEntry
import com.example.model.CallsSubTab
import com.example.model.MainTab
import com.example.ui.components.AddHubLinkDialog
import com.example.ui.components.AppContextDialog
import com.example.ui.components.AppIconPickerDialog
import com.example.ui.components.AppTileGrid
import com.example.ui.components.CallsContactsView
import com.example.ui.components.FavoritesAndAlphabetRail
import com.example.ui.components.FileContextDialog
import com.example.ui.components.HubView
import com.example.ui.components.KeyboardShortcutsDialog
import com.example.ui.components.LandscapeSymbianSidebar
import com.example.ui.components.MusicControlDialog
import com.example.ui.components.PortraitMusicHeaderBar
import com.example.ui.components.RenameAppDialog
import com.example.ui.components.WriterStudioScreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FocusBorderCyan
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavyDivider
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TileBrightAzure
import com.example.ui.theme.symbianMetallicBar
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
        state.showShortcutsDialog ||
        state.showAddLinkDialog ||
        state.contextMenuApp != null ||
        state.contextMenuFile != null ||
        state.renameTargetApp != null ||
        state.iconPickerTargetApp != null

    val shouldHandleBack = hasAnyModalOpen ||
        state.isWriterModeOpen ||
        state.appSearchQuery.isNotEmpty() ||
        state.callsSearchQuery.isNotEmpty() ||
        state.highlightedLetter != null ||
        state.activeTab != MainTab.HOME

    BackHandler(enabled = shouldHandleBack) {
        when {
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBackground)
    ) {
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
            // LANDSCAPE MODE: Top bar disappears; player & stacked vertical tabs sit on the left!
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                LandscapeSymbianSidebar(
                    mediaState = state.mediaState,
                    livePositionMs = state.livePositionMs,
                    activeTab = state.activeTab,
                    callsSubTab = state.callsSubTab,
                    hubSubTab = state.hubSubTab,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onSkipPrevious = viewModel::skipPrevious,
                    onSkipNext = viewModel::skipNext,
                    onCyclePlayer = viewModel::cycleMediaSession,
                    onSwipeMusicSource = viewModel::swipeMusicSource,
                    onToggleMute = viewModel::toggleMute,
                    onOpenMusicSheet = { viewModel.setShowMusicSheet(true) },
                    onSelectMainTab = viewModel::selectTab,
                    onSelectCallsSubTab = viewModel::selectCallsSubTab,
                    onSelectHubSubTab = viewModel::selectHubSubTab,
                    onLaunchCamera = viewModel::launchCamera,
                    onOpenWriter = { viewModel.openWriterMode() },
                    onSwipeDownShade = viewModel::expandNotificationShade
                )

                VerticalDivider(color = NavyDivider, thickness = 1.dp)

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
            // PORTRAIT MODE: Music header bar at top, softkeys + Editor at bottom!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                PortraitMusicHeaderBar(
                    mediaState = state.mediaState,
                    livePositionMs = state.livePositionMs,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onSkipPrevious = viewModel::skipPrevious,
                    onSkipNext = viewModel::skipNext,
                    onCyclePlayer = viewModel::cycleMediaSession,
                    onSwipeMusicSource = viewModel::swipeMusicSource,
                    onOpenMusicSheet = { viewModel.setShowMusicSheet(true) },
                    onSwipeDownShade = viewModel::expandNotificationShade
                )

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

                // Bottom Symbian S60 Softkey Navigation Bar
                HorizontalDivider(color = NavyDivider, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .symbianMetallicBar()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .height(52.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PortraitNavButton(
                        label = "Камера",
                        iconRes = R.drawable.ic_symbian_camera,
                        selected = false,
                        onClick = viewModel::launchCamera,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_camera_portrait")
                    )
                    VerticalDivider(color = NavyDivider, modifier = Modifier.fillMaxHeight(0.6f))
                    PortraitNavButton(
                        label = "Главная",
                        iconRes = R.drawable.ic_symbian_menu,
                        selected = state.activeTab == MainTab.HOME && !state.isWriterModeOpen,
                        onClick = { viewModel.selectTab(MainTab.HOME) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_home_portrait")
                    )
                    VerticalDivider(color = NavyDivider, modifier = Modifier.fillMaxHeight(0.6f))
                    PortraitNavButton(
                        label = "Звонки",
                        iconRes = R.drawable.ic_symbian_phone,
                        selected = state.activeTab == MainTab.CALLS && !state.isWriterModeOpen,
                        onClick = { viewModel.selectTab(MainTab.CALLS) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_calls_portrait")
                    )
                    VerticalDivider(color = NavyDivider, modifier = Modifier.fillMaxHeight(0.6f))
                    PortraitNavButton(
                        label = "Хаб",
                        iconRes = R.drawable.ic_symbian_messaging,
                        selected = state.activeTab == MainTab.HUB && !state.isWriterModeOpen,
                        onClick = { viewModel.selectTab(MainTab.HUB) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_hub_portrait")
                    )
                    VerticalDivider(color = NavyDivider, modifier = Modifier.fillMaxHeight(0.6f))
                    PortraitNavButton(
                        label = "Книги",
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

        // Floating Status Toast
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
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = NavySurfaceElevated,
                    modifier = Modifier.border(1.dp, ElectricCyan, RoundedCornerShape(6.dp))
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }

    if (state.showMusicSheet) {
        MusicControlDialog(
            mediaState = state.mediaState,
            livePositionMs = state.livePositionMs,
            musicApps = state.musicApps,
            onDismiss = { viewModel.setShowMusicSheet(false) },
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

    state.contextMenuApp?.let { app ->
        AppContextDialog(
            app = app,
            onDismiss = { viewModel.openContextMenu(null) },
            onLaunch = { viewModel.launchApp(app) },
            onTogglePin = { viewModel.togglePinApp(app) },
            onRename = { viewModel.openRenameAppDialog(app) },
            onChooseIcon = { viewModel.openIconPickerDialog(app) },
            onShareApk = { viewModel.shareAppAsApk(app) },
            onOpenInfo = { viewModel.openAppInfo(app) },
            onUninstall = { viewModel.uninstallApp(app) }
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
                isSearchingDevice = state.isSearchingDevice,
                indexedFolderCount = state.indexedFolderCount,
                columns = gridColumns,
                searchQuery = state.appSearchQuery,
                focusedIndex = state.focusedAppIndex,
                highlightedLetter = state.highlightedLetter,
                isLoading = state.isLoadingApps,
                gridState = appGridState,
                searchFocusRequester = appSearchFocusRequester,
                isSearchFocused = viewModel.isAppSearchInputFocused,
                onSearchFocusChange = { viewModel.isAppSearchInputFocused = it },
                onRequestSearchFocus = { runCatching { appSearchFocusRequester.requestFocus() } },
                onSearchQueryChange = viewModel::updateAppSearchQuery,
                onClearSearch = viewModel::clearAllSearchOrHighlights,
                onLaunchApp = viewModel::launchApp,
                onLongPressApp = viewModel::openContextMenu,
                onOpenSearchFile = viewModel::openSearchFile,
                onLongPressSearchFile = viewModel::openFileContextMenu,
                onConnectSearchFolder = onConnectSearchFolder,
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
                onPreviewLockScreen = viewModel::openLockScreenPreview,
                onOpenDefaultLauncherSettings = viewModel::openDefaultLauncherSettings,
                onOpenNotificationAccess = viewModel::openNotificationListenerSettings,
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
            .background(if (selected) TileBrightAzure.copy(alpha = 0.28f) else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) FocusBorderCyan else Color.Transparent)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = label,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) FocusBorderCyan else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
