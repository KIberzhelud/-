package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AdaptationCommand
import com.example.data.UniversalSearchRepository
import com.example.model.AdaptedAppSnapshot
import com.example.model.AppAdaptationMode
import com.example.model.AppEntry
import com.example.model.DeviceSearchFile
import com.example.model.GlobalSearchMode
import com.example.model.MusicStreamMatch
import com.example.ui.L10n
import com.example.ui.LocalAppLang
import com.example.ui.theme.CallIncomingGreen
import com.example.ui.theme.CallMissedRed
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
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AppTileGrid(
    apps: List<AppEntry>,
    searchImages: List<DeviceSearchFile>,
    searchFiles: List<DeviceSearchFile>,
    musicSearchResults: List<MusicStreamMatch> = emptyList(),
    isSearchingDevice: Boolean,
    indexedFolderCount: Int,
    hasAllFilesAccess: Boolean,
    columns: Int,
    searchQuery: String,
    focusedIndex: Int,
    highlightedLetter: Char?,
    isLoading: Boolean,
    gridState: LazyGridState,
    searchFocusRequester: FocusRequester,
    isSearchFocused: Boolean,
    embeddedSnapshot: AdaptedAppSnapshot? = null,
    messengerSearchMode: GlobalSearchMode = GlobalSearchMode.APPS_AND_FILES,
    onToggleMessengerSearchMode: () -> Unit = {},
    onSearchFocusChange: (Boolean) -> Unit,
    onRequestSearchFocus: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onOpenSearchFile: (DeviceSearchFile) -> Unit,
    onLongPressSearchFile: (DeviceSearchFile) -> Unit,
    onLaunchMusicMatch: (MusicStreamMatch) -> Unit = {},
    onSendFileToMessenger: (DeviceSearchFile, String) -> Unit = { _, _ -> },
    onSendAdaptationCommand: (AdaptationCommand) -> Unit = {},
    onDisableEmbeddedAdaptation: (String) -> Unit = {},
    onRestoreEmbeddedApp: (String) -> Unit = {},
    onDismissEmbedded: () -> Unit = {},
    onConnectSearchFolder: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onSearchWeb: (query: String, engine: String) -> Unit,
    onOpenShortcuts: () -> Unit,
    onOpenWriter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    val lang = LocalAppLang.current
    val trimmedQuery = searchQuery.trim()
    val isUniversalMode = trimmedQuery.length >= 2
    val hasMessengerEmbedded = embeddedSnapshot?.mode == AppAdaptationMode.MESSENGER

    // Collapsible Universal Search category folders (collapsed by default, auto-expand when apps empty)
    var photosExpanded by remember(trimmedQuery) { mutableStateOf(false) }
    var filesExpanded by remember(trimmedQuery) { mutableStateOf(false) }
    var musicExpanded by remember(trimmedQuery) { mutableStateOf(false) }
    var internetExpanded by remember(trimmedQuery) { mutableStateOf(false) }

    LaunchedEffect(
        trimmedQuery,
        apps.size,
        searchImages.size,
        searchFiles.size,
        musicSearchResults.size,
        isSearchingDevice
    ) {
        if (isUniversalMode && apps.isEmpty()) {
            if (searchImages.isNotEmpty()) photosExpanded = true
            if (searchFiles.isNotEmpty()) filesExpanded = true
            if (musicSearchResults.isNotEmpty()) musicExpanded = true
            if (searchImages.isEmpty() && searchFiles.isEmpty() && musicSearchResults.isEmpty() && !isSearchingDevice) {
                internetExpanded = true
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Windows Vista / Symbian metallic search & shortcuts strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .symbianMetallicBar()
                .border(width = 1.dp, color = theme.chromeBorderOuter)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .vistaInsetBox(isFocused = isSearchFocused)
                    .then(
                        if (hasMessengerEmbedded) {
                            Modifier.pointerInput(messengerSearchMode) {
                                var dragX = 0f
                                var triggered = false
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        dragX = 0f
                                        triggered = false
                                    },
                                    onHorizontalDrag = { _, delta ->
                                        if (triggered) return@detectHorizontalDragGestures
                                        dragX += delta
                                        if (abs(dragX) > 40f) {
                                            triggered = true
                                            onToggleMessengerSearchMode()
                                        }
                                    }
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
                    .clickable(onClick = onRequestSearchFocus)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_symbian_search),
                    contentDescription = "Search",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(7.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = when {
                                highlightedLetter != null -> "Буква: $highlightedLetter • ${L10n.searchPlaceholder(lang)}"
                                hasMessengerEmbedded && messengerSearchMode == GlobalSearchMode.MESSENGER_SEARCH ->
                                    L10n.messengerSearchPlaceholder(lang, embeddedSnapshot!!.appLabel)
                                else -> L10n.searchPlaceholder(lang)
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = if (highlightedLetter != null) theme.accent else TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontSize = 12.5.sp
                        ),
                        cursorBrush = SolidColor(theme.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                if (hasMessengerEmbedded &&
                                    messengerSearchMode == GlobalSearchMode.MESSENGER_SEARCH &&
                                    trimmedQuery.isNotEmpty()
                                ) {
                                    onSendAdaptationCommand(AdaptationCommand.SendText(trimmedQuery))
                                } else {
                                    val firstApp = apps.firstOrNull()
                                    val firstFile = searchFiles.firstOrNull()
                                    val firstImg = searchImages.firstOrNull()
                                    val firstMusic = musicSearchResults.firstOrNull()
                                    when {
                                        firstApp != null -> onLaunchApp(firstApp)
                                        firstFile != null -> onOpenSearchFile(firstFile)
                                        firstImg != null -> onOpenSearchFile(firstImg)
                                        firstMusic != null -> onLaunchMusicMatch(firstMusic)
                                        trimmedQuery.isNotEmpty() -> onSearchWeb(trimmedQuery, "google")
                                    }
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFocusRequester)
                            .onFocusChanged { onSearchFocusChange(it.isFocused) }
                            .testTag("apps_search_input")
                    )
                }
            }

            if (hasMessengerEmbedded) {
                Spacer(Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .vistaAeroButton(isPrimary = messengerSearchMode == GlobalSearchMode.APPS_AND_FILES)
                        .clickable(onClick = onToggleMessengerSearchMode)
                        .padding(horizontal = 7.dp, vertical = 5.dp)
                        .testTag("messenger_search_mode_toggle")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = "Режим поиска",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = if (messengerSearchMode == GlobalSearchMode.APPS_AND_FILES) "Поиск" else "Чат",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            if (searchQuery.isNotEmpty() || highlightedLetter != null) {
                Box(
                    modifier = Modifier
                        .vistaAeroButton()
                        .clickable(onClick = onClearSearch)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("apps_search_clear")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_symbian_clear),
                            contentDescription = "Clear",
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "ESC",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextSecondary
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .vistaAeroButton()
                        .clickable(onClick = onOpenShortcuts)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("shortcuts_help_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_symbian_keyboard),
                            contentDescription = "Hotkeys",
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "F1",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = theme.accent
                        )
                    }
                }
            }

            Spacer(Modifier.width(5.dp))
            Box(
                modifier = Modifier
                    .vistaAeroButton()
                    .clickable(onClick = onOpenWriter)
                    .padding(horizontal = 7.dp, vertical = 5.dp)
                    .testTag("apps_toolbar_writer_button")
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_symbian_editor),
                    contentDescription = L10n.books(lang),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Case A: Embedded Adapted App docked cleanly in tile area (no active search)
        if (embeddedSnapshot != null && !isUniversalMode && highlightedLetter == null) {
            EmbeddedAdaptedAppPanel(
                snapshot = embeddedSnapshot,
                searchFilter = "",
                isDropTargetHighlight = false,
                onSendCommand = onSendAdaptationCommand,
                onDisableAdaptation = { onDisableEmbeddedAdaptation(embeddedSnapshot.packageName) },
                onRestoreApp = { onRestoreEmbeddedApp(embeddedSnapshot.packageName) },
                onDismiss = onDismissEmbedded,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(6.dp)
            )
            return@Column
        }

        // Case B: Messenger Dual Split Mode (Top = Expanded Search with File Send/Drag, Bottom = Adapted Messenger)
        if (hasMessengerEmbedded && isUniversalMode && messengerSearchMode == GlobalSearchMode.APPS_AND_FILES) {
            val messengerSnap = embeddedSnapshot!!
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxWidth()
                ) {
                    UniversalResultsGrid(
                        apps = apps,
                        searchImages = searchImages,
                        searchFiles = searchFiles,
                        musicSearchResults = musicSearchResults,
                        isSearchingDevice = isSearchingDevice,
                        indexedFolderCount = indexedFolderCount,
                        hasAllFilesAccess = hasAllFilesAccess,
                        columns = columns,
                        trimmedQuery = trimmedQuery,
                        focusedIndex = focusedIndex,
                        highlightedLetter = highlightedLetter,
                        gridState = gridState,
                        photosExpanded = photosExpanded,
                        filesExpanded = filesExpanded,
                        musicExpanded = musicExpanded,
                        internetExpanded = internetExpanded,
                        onTogglePhotos = { photosExpanded = !photosExpanded },
                        onToggleFiles = { filesExpanded = !filesExpanded },
                        onToggleMusic = { musicExpanded = !musicExpanded },
                        onToggleInternet = { internetExpanded = !internetExpanded },
                        messengerTargetPackage = messengerSnap.packageName,
                        onSendFileToMessenger = { file ->
                            onSendFileToMessenger(file, messengerSnap.packageName)
                        },
                        onLaunchApp = onLaunchApp,
                        onLongPressApp = onLongPressApp,
                        onOpenSearchFile = onOpenSearchFile,
                        onLongPressSearchFile = onLongPressSearchFile,
                        onLaunchMusicMatch = onLaunchMusicMatch,
                        onConnectSearchFolder = onConnectSearchFolder,
                        onRequestAllFilesAccess = onRequestAllFilesAccess,
                        onSearchWeb = onSearchWeb
                    )
                }

                HorizontalDivider(color = theme.focusBorder, thickness = 1.5.dp)

                EmbeddedAdaptedAppPanel(
                    snapshot = messengerSnap,
                    searchFilter = "",
                    isDropTargetHighlight = searchFiles.isNotEmpty() || searchImages.isNotEmpty(),
                    onSendCommand = onSendAdaptationCommand,
                    onDisableAdaptation = { onDisableEmbeddedAdaptation(messengerSnap.packageName) },
                    onRestoreApp = { onRestoreEmbeddedApp(messengerSnap.packageName) },
                    onDismiss = onDismissEmbedded,
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
            return@Column
        }

        // Case C: Messenger Search Only Mode (filters chats in embedded messenger)
        if (hasMessengerEmbedded && isUniversalMode && messengerSearchMode == GlobalSearchMode.MESSENGER_SEARCH) {
            EmbeddedAdaptedAppPanel(
                snapshot = embeddedSnapshot!!,
                searchFilter = trimmedQuery,
                isDropTargetHighlight = false,
                onSendCommand = onSendAdaptationCommand,
                onDisableAdaptation = { onDisableEmbeddedAdaptation(embeddedSnapshot.packageName) },
                onRestoreApp = { onRestoreEmbeddedApp(embeddedSnapshot.packageName) },
                onDismiss = onDismissEmbedded,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(6.dp)
            )
            return@Column
        }

        // Case D: Normal Launcher App Grid + Universal Collapsible Folder Search
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = theme.accent)
            }
        } else if (!isUniversalMode && apps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(R.drawable.ic_symbian_search),
                        contentDescription = null,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (highlightedLetter != null) {
                            "Нет приложений на букву «$highlightedLetter»"
                        } else {
                            "Нет приложений по запросу «$searchQuery»"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(6.dp))
                    if (highlightedLetter != null) {
                        VistaButton(
                            text = "Показать все",
                            isPrimary = true,
                            onClick = onClearSearch,
                            modifier = Modifier.testTag("clear_letter_filter_button")
                        )
                    } else {
                        Text(
                            text = "Введите минимум 2 символа или маску (напр. *apk) для поиска",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }
        } else if (isUniversalMode) {
            UniversalResultsGrid(
                apps = apps,
                searchImages = searchImages,
                searchFiles = searchFiles,
                musicSearchResults = musicSearchResults,
                isSearchingDevice = isSearchingDevice,
                indexedFolderCount = indexedFolderCount,
                hasAllFilesAccess = hasAllFilesAccess,
                columns = columns,
                trimmedQuery = trimmedQuery,
                focusedIndex = focusedIndex,
                highlightedLetter = highlightedLetter,
                gridState = gridState,
                photosExpanded = photosExpanded,
                filesExpanded = filesExpanded,
                musicExpanded = musicExpanded,
                internetExpanded = internetExpanded,
                onTogglePhotos = { photosExpanded = !photosExpanded },
                onToggleFiles = { filesExpanded = !filesExpanded },
                onToggleMusic = { musicExpanded = !musicExpanded },
                onToggleInternet = { internetExpanded = !internetExpanded },
                messengerTargetPackage = null,
                onSendFileToMessenger = {},
                onLaunchApp = onLaunchApp,
                onLongPressApp = onLongPressApp,
                onOpenSearchFile = onOpenSearchFile,
                onLongPressSearchFile = onLongPressSearchFile,
                onLaunchMusicMatch = onLaunchMusicMatch,
                onConnectSearchFolder = onConnectSearchFolder,
                onRequestAllFilesAccess = onRequestAllFilesAccess,
                onSearchWeb = onSearchWeb
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("apps_metro_grid")
            ) {
                itemsIndexed(apps, key = { _, app -> app.id }) { index, app ->
                    AppGlossyTileItem(
                        app = app,
                        index = index,
                        isFocused = index == focusedIndex,
                        isLetterHighlighted = false,
                        onLaunchApp = onLaunchApp,
                        onLongPressApp = onLongPressApp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UniversalResultsGrid(
    apps: List<AppEntry>,
    searchImages: List<DeviceSearchFile>,
    searchFiles: List<DeviceSearchFile>,
    musicSearchResults: List<MusicStreamMatch>,
    isSearchingDevice: Boolean,
    indexedFolderCount: Int,
    hasAllFilesAccess: Boolean,
    columns: Int,
    trimmedQuery: String,
    focusedIndex: Int,
    highlightedLetter: Char?,
    gridState: LazyGridState,
    photosExpanded: Boolean,
    filesExpanded: Boolean,
    musicExpanded: Boolean,
    internetExpanded: Boolean,
    onTogglePhotos: () -> Unit,
    onToggleFiles: () -> Unit,
    onToggleMusic: () -> Unit,
    onToggleInternet: () -> Unit,
    messengerTargetPackage: String?,
    onSendFileToMessenger: (DeviceSearchFile) -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onOpenSearchFile: (DeviceSearchFile) -> Unit,
    onLongPressSearchFile: (DeviceSearchFile) -> Unit,
    onLaunchMusicMatch: (MusicStreamMatch) -> Unit,
    onConnectSearchFolder: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onSearchWeb: (String, String) -> Unit
) {
    val theme = LocalVistaTheme.current
    val lang = LocalAppLang.current

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        contentPadding = PaddingValues(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("apps_metro_grid")
    ) {
        if (!hasAllFilesAccess) {
            item(key = "all_files_permission_banner", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaWindowFrame(RoundedCornerShape(3.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("all_files_access_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = MusicSourceAmber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "Нужен доступ ко всем файлам для поиска *apk и папок",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    VistaButton(
                        text = "Разрешить",
                        isPrimary = true,
                        onClick = onRequestAllFilesAccess,
                        modifier = Modifier.testTag("grant_all_files_access_button")
                    )
                }
            }
        }

        // Top search status + SAF folder index button
        item(key = "universal_header_bar", span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .vistaWindowFrame(RoundedCornerShape(3.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isSearchingDevice) "Поиск по устройству…" else "Универсальный поиск",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = theme.accent
                    )
                    if (isSearchingDevice) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(color = theme.accent, strokeWidth = 2.dp, modifier = Modifier.size(13.dp))
                    }
                }
                Row(
                    modifier = Modifier
                        .vistaAeroButton()
                        .clickable(onClick = onConnectSearchFolder)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("universal_connect_folder_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = "Подключить папку", tint = MusicSourceAmber, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (indexedFolderCount == 0) "+ Папка" else "Папок: $indexedFolderCount",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }
            }
        }

        // 1. Matching Apps (always expanded first)
        if (apps.isNotEmpty()) {
            item(key = "section_apps", span = { GridItemSpan(maxLineSpan) }) {
                UniversalSectionHeader(title = L10n.appsFolder(lang, apps.size).uppercase(), iconRes = R.drawable.ic_symbian_menu)
            }
            itemsIndexed(apps, key = { _, app -> app.id }) { index, app ->
                AppGlossyTileItem(
                    app = app,
                    index = index,
                    isFocused = index == focusedIndex,
                    isLetterHighlighted = false,
                    onLaunchApp = onLaunchApp,
                    onLongPressApp = onLongPressApp
                )
            }
        }

        // 2. Collapsible Aero Folder Pills strip for Photos / Files / Music / Internet
        item(key = "search_category_folders_strip", span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (searchImages.isNotEmpty()) {
                    SearchFolderPill(
                        label = L10n.photosFolder(lang, searchImages.size),
                        expanded = photosExpanded,
                        onClick = onTogglePhotos,
                        tag = "folder_pill_photos"
                    )
                }
                if (searchFiles.isNotEmpty()) {
                    SearchFolderPill(
                        label = L10n.filesFolder(lang, searchFiles.size),
                        expanded = filesExpanded,
                        onClick = onToggleFiles,
                        tag = "folder_pill_files"
                    )
                }
                if (musicSearchResults.isNotEmpty()) {
                    SearchFolderPill(
                        label = L10n.musicFolder(lang, musicSearchResults.size),
                        expanded = musicExpanded,
                        onClick = onToggleMusic,
                        tag = "folder_pill_music"
                    )
                }
                SearchFolderPill(
                    label = L10n.internetFolder(lang),
                    expanded = internetExpanded,
                    onClick = onToggleInternet,
                    tag = "folder_pill_internet"
                )
            }
        }

        // Expanded Photos
        if (photosExpanded && searchImages.isNotEmpty()) {
            item(key = "section_images", span = { GridItemSpan(maxLineSpan) }) {
                UniversalSectionHeader(title = L10n.photosFolder(lang, searchImages.size).uppercase(), iconRes = R.drawable.ic_symbian_gallery)
            }
            items(searchImages, key = { it.id }) { img ->
                SearchImageCard(
                    file = img,
                    canSendToMessenger = messengerTargetPackage != null,
                    onSendToMessenger = { onSendFileToMessenger(img) },
                    onClick = { onOpenSearchFile(img) },
                    onLongClick = { onLongPressSearchFile(img) }
                )
            }
        }

        // Expanded Files
        if (filesExpanded && searchFiles.isNotEmpty()) {
            item(key = "section_files", span = { GridItemSpan(maxLineSpan) }) {
                UniversalSectionHeader(title = L10n.filesFolder(lang, searchFiles.size).uppercase(), iconRes = R.drawable.ic_symbian_files)
            }
            items(items = searchFiles, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { file ->
                SearchFileRow(
                    file = file,
                    canSendToMessenger = messengerTargetPackage != null,
                    onSendToMessenger = { onSendFileToMessenger(file) },
                    onClick = { onOpenSearchFile(file) },
                    onLongClick = { onLongPressSearchFile(file) }
                )
            }
        }

        // Expanded Music & Streaming
        if (musicExpanded && musicSearchResults.isNotEmpty()) {
            item(key = "section_music", span = { GridItemSpan(maxLineSpan) }) {
                UniversalSectionHeader(title = L10n.musicFolder(lang, musicSearchResults.size).uppercase(), iconRes = R.drawable.ic_symbian_music)
            }
            items(items = musicSearchResults, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { match ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaWindowFrame(RoundedCornerShape(3.dp))
                        .clickable { onLaunchMusicMatch(match) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("search_music_row_${match.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (match.isServiceSearchTrigger) Icons.Rounded.Search else Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = if (match.isLocalTrack) MusicSourceAmber else theme.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(match.title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.5.sp), color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${match.appLabel} • ${match.subtitle}", style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        text = if (match.isServiceSearchTrigger) "Искать ▶" else "▶",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = theme.accent
                    )
                }
            }
        }

        // Expanded Web Search
        if (internetExpanded) {
            item(key = "section_web_search", span = { GridItemSpan(maxLineSpan) }) {
                val nothingLocal = apps.isEmpty() && searchImages.isEmpty() && searchFiles.isEmpty() && musicSearchResults.isEmpty() && !isSearchingDevice
                WebSearchFallbackCard(
                    query = trimmedQuery,
                    isProminent = nothingLocal,
                    onSearchWeb = { engine -> onSearchWeb(trimmedQuery, engine) }
                )
            }
        }
    }
}

@Composable
private fun SearchFolderPill(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .vistaAeroButton(isPrimary = expanded, isActive = expanded)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.FolderOpen,
            contentDescription = null,
            tint = if (expanded) Color.White else MusicSourceAmber,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (expanded) Color.White else TextPrimary
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = if (expanded) Color.White else TextMuted,
            modifier = Modifier.size(14.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmbeddedAdaptedAppPanel(
    snapshot: AdaptedAppSnapshot,
    searchFilter: String,
    isDropTargetHighlight: Boolean,
    onSendCommand: (AdaptationCommand) -> Unit,
    onDisableAdaptation: () -> Unit,
    onRestoreApp: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    val lang = LocalAppLang.current
    var draftText by remember(snapshot.packageName) { mutableStateOf("") }
    val visibleItems = remember(snapshot.items, searchFilter) {
        if (searchFilter.isBlank()) snapshot.items
        else snapshot.items.filter {
            it.title.contains(searchFilter, ignoreCase = true) ||
                it.subtitle.contains(searchFilter, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .vistaWindowFrame(RoundedCornerShape(6.dp))
            .then(
                if (isDropTargetHighlight) {
                    Modifier.border(2.dp, theme.focusBorder, RoundedCornerShape(6.dp))
                } else Modifier
            )
            .testTag("embedded_adapted_app_panel")
    ) {
        // Vista Aero Header Bar (Long-press header also disables adaptation for this app)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .symbianMetallicBar()
                .combinedClickable(
                    onClick = {},
                    onLongClick = onDisableAdaptation
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${snapshot.appLabel} • ${snapshot.mode.shortTitle}",
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                    color = theme.focusBorder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isDropTargetHighlight) {
                    Text(
                        text = L10n.dropToMessengerHint(lang, snapshot.appLabel),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = CallIncomingGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .vistaAeroButton()
                        .clickable(onClick = onDisableAdaptation)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("embedded_disable_adaptation_btn")
                ) {
                    Text(
                        text = L10n.embeddedDisableAdapt(lang),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = CallMissedRed
                    )
                }
                Box(
                    modifier = Modifier
                        .vistaAeroButton(isPrimary = true)
                        .clickable(onClick = onRestoreApp)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Открыть приложение", tint = Color.White, modifier = Modifier.size(13.dp))
                }
                Box(
                    modifier = Modifier
                        .vistaAeroButton()
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("embedded_dismiss_btn")
                ) {
                    Text(L10n.embeddedRestore(lang), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp), color = TextSecondary)
                }
            }
        }

        // F1-F6 tab switcher strip
        if (snapshot.tabs.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.surface.copy(alpha = 0.85f))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                snapshot.tabs.take(6).forEachIndexed { idx, tab ->
                    Box(
                        modifier = Modifier
                            .vistaAeroButton(isPrimary = tab.isSelected, isActive = tab.isSelected)
                            .clickable { onSendCommand(AdaptationCommand.ClickTab(idx)) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "F${idx + 1} ${tab.label}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (tab.isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (tab.isSelected) Color.White else TextPrimary,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Adapted rows / chat messages / settings entries
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (visibleItems.isEmpty()) {
                item {
                    Text(
                        text = if (snapshot.mode == AppAdaptationMode.MESSENGER) {
                            "Ожидание чатов / сообщений из ${snapshot.appLabel}. Нажмите «Открыть», чтобы обновить снимок."
                        } else {
                            "Элементы экрана ${snapshot.appLabel} появятся после открытия."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                lazyItemsIndexed(visibleItems, key = { idx, it -> "${it.index}_$idx" }) { idx, node ->
                    val focused = idx == snapshot.focusedItemIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .symbianGlossyTile(bucket = idx, isFocused = focused)
                            .clickable {
                                val delta = node.index - snapshot.focusedItemIndex
                                if (delta != 0) onSendCommand(AdaptationCommand.MoveFocus(delta))
                                onSendCommand(AdaptationCommand.ActivateFocused)
                            }
                            .padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = node.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.5.sp),
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (node.subtitle.isNotBlank()) {
                                Text(
                                    text = node.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (node.badge.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = node.badge,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MusicSourceAmber
                            )
                        }
                    }
                }
            }
        }

        // Messenger inline quick-send bar
        if (snapshot.hasInputField) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.surface.copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .vistaInsetBox()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    if (draftText.isEmpty()) {
                        Text(snapshot.inputHint.ifBlank { "Сообщение…" }, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    BasicTextField(
                        value = draftText,
                        onValueChange = { draftText = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                        cursorBrush = SolidColor(theme.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(
                    modifier = Modifier
                        .vistaAeroButton(isPrimary = draftText.isNotBlank())
                        .clickable(enabled = draftText.isNotBlank()) {
                            onSendCommand(AdaptationCommand.SendText(draftText))
                            draftText = ""
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Отправить", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }

        // Bottom control bar (Back / Up / Down / OK / Refresh)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .symbianMetallicBar()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier.vistaAeroButton().clickable { onSendCommand(AdaptationCommand.NavigateBack) }.padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад", tint = TextPrimary, modifier = Modifier.size(14.dp))
                }
                Box(
                    modifier = Modifier.vistaAeroButton().clickable { onSendCommand(AdaptationCommand.MoveFocus(-1)) }.padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Вверх", tint = TextPrimary, modifier = Modifier.size(14.dp))
                }
                Box(
                    modifier = Modifier.vistaAeroButton().clickable { onSendCommand(AdaptationCommand.MoveFocus(+1)) }.padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Вниз", tint = TextPrimary, modifier = Modifier.size(14.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier.vistaAeroButton(isPrimary = true).clickable { onSendCommand(AdaptationCommand.ActivateFocused) }.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("OK", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                }
                Box(
                    modifier = Modifier.vistaAeroButton().clickable { onSendCommand(AdaptationCommand.RefreshTree) }.padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Обновить", tint = theme.accent, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGlossyTileItem(
    app: AppEntry,
    index: Int,
    isFocused: Boolean,
    isLetterHighlighted: Boolean,
    onLaunchApp: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .symbianGlossyTile(
                bucket = index + app.colorBucket,
                isFocused = isFocused,
                isLetterHighlighted = isLetterHighlighted
            )
            .combinedClickable(
                onClick = { onLaunchApp(app) },
                onLongClick = { onLongPressApp(app) }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("app_tile_${app.packageName}"),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (app.icon != null) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_symbian_tools),
                    contentDescription = app.label,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            val theme = LocalVistaTheme.current
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    shadow = if (theme.isGlassMode) {
                        Shadow(
                            color = Color.Black.copy(alpha = 0.85f),
                            offset = Offset(0f, 1.5f),
                            blurRadius = 4f
                        )
                    } else null
                ),
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        if (app.isPinned) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = "В избранном",
                tint = MusicSourceAmber,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(12.dp)
            )
        }
    }
}

@Composable
private fun UniversalSectionHeader(
    title: String,
    iconRes: Int
) {
    val theme = LocalVistaTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp, start = 2.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = theme.focusBorder
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchImageCard(
    file: DeviceSearchFile,
    canSendToMessenger: Boolean,
    onSendToMessenger: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val theme = LocalVistaTheme.current
    var verticalDragAccum by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .vistaWindowFrame(RoundedCornerShape(3.dp))
            .then(
                if (canSendToMessenger) {
                    Modifier.pointerInput(file.id) {
                        detectVerticalDragGestures(
                            onDragStart = { verticalDragAccum = 0f },
                            onDragEnd = {
                                if (verticalDragAccum > 60f) {
                                    onSendToMessenger()
                                }
                                verticalDragAccum = 0f
                            },
                            onVerticalDrag = { _, dragAmount -> verticalDragAccum += dragAmount }
                        )
                    }
                } else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(6.dp)
            .testTag("search_image_card_${file.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .vistaInsetBox()
                .background(theme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (file.thumbnail != null) {
                Image(
                    bitmap = file.thumbnail,
                    contentDescription = file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_symbian_gallery),
                    contentDescription = file.name,
                    modifier = Modifier.size(34.dp)
                )
            }
            if (canSendToMessenger) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .vistaAeroButton(isPrimary = true)
                        .clickable(onClick = onSendToMessenger)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("В чат ⤵", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = file.parentPath.ifEmpty { file.displayPath },
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchFileRow(
    file: DeviceSearchFile,
    canSendToMessenger: Boolean,
    onSendToMessenger: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val iconRes = when {
        file.name.endsWith(".apk", ignoreCase = true) -> R.drawable.ic_symbian_apk
        file.name.endsWith(".docx", ignoreCase = true) ||
            file.name.endsWith(".txt", ignoreCase = true) ||
            file.name.endsWith(".pdf", ignoreCase = true) -> R.drawable.ic_symbian_docx
        file.name.endsWith(".mp3", ignoreCase = true) ||
            file.name.endsWith(".flac", ignoreCase = true) -> R.drawable.ic_symbian_music
        file.name.endsWith(".mp4", ignoreCase = true) ||
            file.name.endsWith(".mkv", ignoreCase = true) -> R.drawable.ic_symbian_video
        else -> R.drawable.ic_symbian_files
    }
    val sizeText = UniversalSearchRepository.formatSize(file.sizeBytes)
    var verticalDragAccum by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .vistaWindowFrame(RoundedCornerShape(3.dp))
            .then(
                if (canSendToMessenger) {
                    Modifier.pointerInput(file.id) {
                        detectVerticalDragGestures(
                            onDragStart = { verticalDragAccum = 0f },
                            onDragEnd = {
                                if (verticalDragAccum > 55f) {
                                    onSendToMessenger()
                                }
                                verticalDragAccum = 0f
                            },
                            onVerticalDrag = { _, dragAmount -> verticalDragAccum += dragAmount }
                        )
                    }
                } else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .testTag("search_file_row_${file.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = file.name,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    if (sizeText.isNotEmpty()) append("$sizeText • ")
                    append(file.displayPath)
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (canSendToMessenger) {
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .vistaAeroButton(isPrimary = true)
                    .clickable(onClick = onSendToMessenger)
                    .padding(horizontal = 7.dp, vertical = 4.dp)
                    .testTag("send_to_chat_${file.id}")
            ) {
                Text(
                    text = "В чат ⤵",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        } else {
            Text(
                text = "Зажать",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color = TextMuted,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebSearchFallbackCard(
    query: String,
    isProminent: Boolean,
    onSearchWeb: (String) -> Unit
) {
    val theme = LocalVistaTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .vistaWindowFrame(RoundedCornerShape(4.dp))
            .then(
                if (isProminent) Modifier.border(1.dp, theme.accent.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                else Modifier
            )
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Public,
                contentDescription = null,
                tint = theme.accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isProminent) {
                    "На телефоне ничего не найдено — поискать в интернете «$query»:"
                } else {
                    "Поискать «$query» в интернете:"
                },
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.5.sp),
                color = TextPrimary
            )
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WebEngineButton("Google", "web_search_google") { onSearchWeb("google") }
            WebEngineButton("Яндекс", "web_search_yandex") { onSearchWeb("yandex") }
            WebEngineButton("YouTube", "web_search_youtube") { onSearchWeb("youtube") }
            WebEngineButton("Wikipedia", "web_search_wiki") { onSearchWeb("wiki") }
        }
    }
}

@Composable
private fun WebEngineButton(
    label: String,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .vistaAeroButton(isPrimary = true)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag(tag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
    }
}
