package com.example.ui.components

import android.content.ClipData
import android.net.Uri
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.LauncherPreferencesRepository
import com.example.data.UniversalSearchRepository
import com.example.model.AppEntry
import com.example.model.ConsensusWeatherReport
import com.example.model.DeviceSearchFile
import com.example.model.LinuxPackageEntry
import com.example.model.LockedChatThread
import com.example.model.MusicStreamMatch
import com.example.model.WeatherCityResult
import com.example.ui.L10n
import com.example.ui.LocalAppLang
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AppTileGrid(
    apps: List<AppEntry>,
    chatSearchResults: List<LockedChatThread> = emptyList(),
    searchImages: List<DeviceSearchFile>,
    searchFiles: List<DeviceSearchFile>,
    linuxSearchResults: List<LinuxPackageEntry> = emptyList(),
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
    onSearchFocusChange: (Boolean) -> Unit,
    onRequestSearchFocus: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onOpenLockedChat: (LockedChatThread) -> Unit = {},
    onOpenSearchFile: (DeviceSearchFile) -> Unit,
    onLongPressSearchFile: (DeviceSearchFile) -> Unit,
    onSendFileToTarget: (DeviceSearchFile) -> Unit = {},
    onCopySearchFile: (DeviceSearchFile) -> Unit = {},
    onResolveFileUri: (DeviceSearchFile) -> Uri = { Uri.parse(it.uriString) },
    onRunLinuxPackage: (LinuxPackageEntry) -> Unit = {},
    onLaunchMusicMatch: (MusicStreamMatch) -> Unit = {},
    onConnectSearchFolder: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onSearchWeb: (query: String, engine: String) -> Unit,
    onOpenShortcuts: () -> Unit,
    onOpenWriter: () -> Unit,
    onSwipeToWorkspaceSpace: () -> Unit = {},
    showWeatherTile: Boolean = false,
    weatherReport: ConsensusWeatherReport? = null,
    selectedWeatherCity: WeatherCityResult = LauncherPreferencesRepository.DEFAULT_WEATHER_CITY,
    isWeatherLoading: Boolean = false,
    onRefreshWeather: () -> Unit = {},
    onOpenWeatherForecast: () -> Unit = {},
    onOpenWeatherCityPicker: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    val lang = LocalAppLang.current
    val trimmedQuery = searchQuery.trim()
    val isUniversalMode = trimmedQuery.length >= 2

    // Collapsible Universal Search category folders (collapsed by default, auto-expand when apps empty)
    var chatsExpanded by remember(trimmedQuery) { mutableStateOf(false) }
    var photosExpanded by remember(trimmedQuery) { mutableStateOf(false) }
    var filesExpanded by remember(trimmedQuery) { mutableStateOf(false) }
    var linuxExpanded by remember(trimmedQuery) { mutableStateOf(false) }
    var musicExpanded by remember(trimmedQuery) { mutableStateOf(false) }

    LaunchedEffect(
        trimmedQuery,
        apps.size,
        chatSearchResults.size,
        searchImages.size,
        searchFiles.size,
        linuxSearchResults.size,
        musicSearchResults.size,
        isSearchingDevice
    ) {
        if (isUniversalMode && apps.isEmpty()) {
            if (chatSearchResults.isNotEmpty()) chatsExpanded = true
            if (searchImages.isNotEmpty()) photosExpanded = true
            if (searchFiles.isNotEmpty()) filesExpanded = true
            if (linuxSearchResults.isNotEmpty()) linuxExpanded = true
            if (musicSearchResults.isNotEmpty()) musicExpanded = true
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
                            text = if (highlightedLetter != null) {
                                "Буква: $highlightedLetter • ${L10n.searchPlaceholder(lang)}"
                            } else {
                                L10n.searchPlaceholder(lang)
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = if (highlightedLetter != null) theme.accent else TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    var appsSearchTfv by remember {
                        mutableStateOf(TextFieldValue(searchQuery, TextRange(searchQuery.length)))
                    }
                    LaunchedEffect(searchQuery) {
                        if (searchQuery != appsSearchTfv.text) {
                            appsSearchTfv = TextFieldValue(searchQuery, TextRange(searchQuery.length))
                        }
                    }
                    BasicTextField(
                        value = appsSearchTfv,
                        onValueChange = { next ->
                            appsSearchTfv = next
                            onSearchQueryChange(next.text)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontSize = 12.5.sp
                        ),
                        cursorBrush = SolidColor(theme.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                val firstApp = apps.firstOrNull()
                                val firstChat = chatSearchResults.firstOrNull()
                                val firstFile = searchFiles.firstOrNull()
                                val firstImg = searchImages.firstOrNull()
                                val firstLinux = linuxSearchResults.firstOrNull()
                                val firstMusic = musicSearchResults.firstOrNull()
                                when {
                                    firstApp != null -> onLaunchApp(firstApp)
                                    firstChat != null -> onOpenLockedChat(firstChat)
                                    firstFile != null -> onOpenSearchFile(firstFile)
                                    firstImg != null -> onOpenSearchFile(firstImg)
                                    firstLinux != null -> onRunLinuxPackage(firstLinux)
                                    firstMusic != null -> onLaunchMusicMatch(firstMusic)
                                    trimmedQuery.isNotEmpty() -> onSearchWeb(trimmedQuery, "google")
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

        // Normal Launcher App Grid + Universal Collapsible Folder Search
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
                            text = "Введите минимум 2 символа или маску (*apk, *deb) для поиска",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }
        } else if (isUniversalMode) {
            UniversalResultsGrid(
                apps = apps,
                chatSearchResults = chatSearchResults,
                searchImages = searchImages,
                searchFiles = searchFiles,
                linuxSearchResults = linuxSearchResults,
                musicSearchResults = musicSearchResults,
                isSearchingDevice = isSearchingDevice,
                indexedFolderCount = indexedFolderCount,
                hasAllFilesAccess = hasAllFilesAccess,
                columns = columns,
                trimmedQuery = trimmedQuery,
                focusedIndex = focusedIndex,
                gridState = gridState,
                chatsExpanded = chatsExpanded,
                photosExpanded = photosExpanded,
                filesExpanded = filesExpanded,
                linuxExpanded = linuxExpanded,
                musicExpanded = musicExpanded,
                onToggleChats = { chatsExpanded = !chatsExpanded },
                onTogglePhotos = { photosExpanded = !photosExpanded },
                onToggleFiles = { filesExpanded = !filesExpanded },
                onToggleLinux = { linuxExpanded = !linuxExpanded },
                onToggleMusic = { musicExpanded = !musicExpanded },
                onLaunchApp = onLaunchApp,
                onLongPressApp = onLongPressApp,
                onOpenLockedChat = onOpenLockedChat,
                onOpenSearchFile = onOpenSearchFile,
                onLongPressSearchFile = onLongPressSearchFile,
                onSendFileToTarget = onSendFileToTarget,
                onCopySearchFile = onCopySearchFile,
                onResolveFileUri = onResolveFileUri,
                onRunLinuxPackage = onRunLinuxPackage,
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
                    .pointerInput(searchQuery, highlightedLetter) {
                        if (searchQuery.isBlank() && highlightedLetter == null) {
                            var dragX = 0f
                            var triggered = false
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    dragX = 0f
                                    triggered = false
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    if (triggered) return@detectHorizontalDragGestures
                                    dragX += dragAmount
                                    if (kotlin.math.abs(dragX) > 110f) {
                                        triggered = true
                                        onSwipeToWorkspaceSpace()
                                    }
                                }
                            )
                        }
                    }
                    .testTag("apps_metro_grid")
            ) {
                if (showWeatherTile && searchQuery.isBlank() && highlightedLetter == null) {
                    item(key = "home_weather_tile", span = { GridItemSpan(maxLineSpan) }) {
                        WeatherWidgetCard(
                            report = weatherReport,
                            selectedCity = selectedWeatherCity,
                            isLoading = isWeatherLoading,
                            compact = true,
                            onRefresh = onRefreshWeather,
                            onOpenForecast = onOpenWeatherForecast,
                            onOpenCityPicker = onOpenWeatherCityPicker,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UniversalResultsGrid(
    apps: List<AppEntry>,
    chatSearchResults: List<LockedChatThread>,
    searchImages: List<DeviceSearchFile>,
    searchFiles: List<DeviceSearchFile>,
    linuxSearchResults: List<LinuxPackageEntry>,
    musicSearchResults: List<MusicStreamMatch>,
    isSearchingDevice: Boolean,
    indexedFolderCount: Int,
    hasAllFilesAccess: Boolean,
    columns: Int,
    trimmedQuery: String,
    focusedIndex: Int,
    gridState: LazyGridState,
    chatsExpanded: Boolean,
    photosExpanded: Boolean,
    filesExpanded: Boolean,
    linuxExpanded: Boolean,
    musicExpanded: Boolean,
    onToggleChats: () -> Unit,
    onTogglePhotos: () -> Unit,
    onToggleFiles: () -> Unit,
    onToggleLinux: () -> Unit,
    onToggleMusic: () -> Unit,
    onLaunchApp: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onOpenLockedChat: (LockedChatThread) -> Unit,
    onOpenSearchFile: (DeviceSearchFile) -> Unit,
    onLongPressSearchFile: (DeviceSearchFile) -> Unit,
    onSendFileToTarget: (DeviceSearchFile) -> Unit,
    onCopySearchFile: (DeviceSearchFile) -> Unit,
    onResolveFileUri: (DeviceSearchFile) -> Uri,
    onRunLinuxPackage: (LinuxPackageEntry) -> Unit,
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
                            text = "Нужен доступ ко всем файлам для поиска *apk, *deb и папок",
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

        // 2. Collapsible Aero Folder Pills strip for Chats / Photos / Files / Linux / Music
        if (chatSearchResults.isNotEmpty() || searchImages.isNotEmpty() || searchFiles.isNotEmpty() ||
            linuxSearchResults.isNotEmpty() || musicSearchResults.isNotEmpty()
        ) {
            item(key = "search_category_folders_strip", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (chatSearchResults.isNotEmpty()) {
                        SearchFolderPill(
                            label = L10n.chatsFolder(lang, chatSearchResults.size),
                            expanded = chatsExpanded,
                            onClick = onToggleChats,
                            tag = "folder_pill_chats"
                        )
                    }
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
                    if (linuxSearchResults.isNotEmpty()) {
                        SearchFolderPill(
                            label = L10n.linuxFolder(lang, linuxSearchResults.size),
                            expanded = linuxExpanded,
                            onClick = onToggleLinux,
                            tag = "folder_pill_linux"
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
                }
            }
        }

        // Expanded Chats & Mail (tap opens LockedSingleChatView in tile pane)
        if (chatsExpanded && chatSearchResults.isNotEmpty()) {
            item(key = "section_chats", span = { GridItemSpan(maxLineSpan) }) {
                UniversalSectionHeader(title = L10n.chatsFolder(lang, chatSearchResults.size).uppercase(), iconRes = R.drawable.ic_symbian_messaging)
            }
            items(items = chatSearchResults, key = { "chat_${it.key}" }, span = { GridItemSpan(maxLineSpan) }) { thread ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaWindowFrame(RoundedCornerShape(3.dp))
                        .clickable { onOpenLockedChat(thread) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("search_chat_row_${thread.key}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (thread.appIcon != null) {
                        Image(
                            bitmap = thread.appIcon,
                            contentDescription = thread.appName,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Forum,
                            contentDescription = thread.appName,
                            tint = theme.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = thread.contactName,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val preview = thread.messages.lastOrNull()?.text ?: thread.fallbackPhoneNumber.orEmpty()
                        Text(
                            text = if (preview.isNotBlank()) "${thread.appName} • $preview" else thread.appName,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = thread.timeFormatted.ifBlank { "Чат ▶" },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = theme.accent
                    )
                }
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
                    onCopy = { onCopySearchFile(img) },
                    onSendToTarget = { onSendFileToTarget(img) },
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
                    onCopy = { onCopySearchFile(file) },
                    onSendToTarget = { onSendFileToTarget(file) },
                    onClick = { onOpenSearchFile(file) },
                    onLongClick = { onLongPressSearchFile(file) }
                )
            }
        }

        // Expanded Linux (.deb / AppImage / CLI)
        if (linuxExpanded && linuxSearchResults.isNotEmpty()) {
            item(key = "section_linux", span = { GridItemSpan(maxLineSpan) }) {
                UniversalSectionHeader(title = L10n.linuxFolder(lang, linuxSearchResults.size).uppercase(), iconRes = R.drawable.ic_symbian_tools)
            }
            items(items = linuxSearchResults, key = { "linux_${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaWindowFrame(RoundedCornerShape(3.dp))
                        .clickable { onRunLinuxPackage(entry) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("search_linux_row_${entry.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = null,
                        tint = MusicSourceAmber,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${entry.kind.title} • v${entry.version} (${entry.architecture})",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = if (entry.isInstalled) "Запуск ▶" else "Установ.",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = theme.accent
                    )
                }
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

        // Always visible Web Search Card below category tabs & expanded results
        item(key = "section_web_search", span = { GridItemSpan(maxLineSpan) }) {
            val nothingLocal = apps.isEmpty() && chatSearchResults.isEmpty() &&
                searchImages.isEmpty() && searchFiles.isEmpty() &&
                linuxSearchResults.isEmpty() && musicSearchResults.isEmpty() && !isSearchingDevice
            WebSearchFallbackCard(
                query = trimmedQuery,
                isProminent = nothingLocal,
                onSearchWeb = { engine -> onSearchWeb(trimmedQuery, engine) }
            )
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
private fun AppGlossyTileItem(
    app: AppEntry,
    index: Int,
    isFocused: Boolean,
    isLetterHighlighted: Boolean,
    onLaunchApp: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit
) {
    val theme = LocalVistaTheme.current
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
    onCopy: () -> Unit,
    onSendToTarget: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val theme = LocalVistaTheme.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .vistaWindowFrame(RoundedCornerShape(3.dp))
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
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .vistaAeroButton()
                        .clickable(onClick = onCopy)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                        .testTag("copy_file_${file.id}")
                ) {
                    Text("Ctrl+C", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = TextPrimary)
                }
                Box(
                    modifier = Modifier
                        .vistaAeroButton(isPrimary = true)
                        .clickable(onClick = onSendToTarget)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                        .testTag("send_to_chat_${file.id}")
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
    onCopy: () -> Unit,
    onSendToTarget: () -> Unit,
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .vistaWindowFrame(RoundedCornerShape(3.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .testTag("search_file_row_${file.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = file.name,
                modifier = Modifier.size(26.dp)
            )
        }
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
        Box(
            modifier = Modifier
                .padding(start = 6.dp)
                .vistaAeroButton()
                .clickable(onClick = onCopy)
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .testTag("copy_file_${file.id}")
        ) {
            Text(
                text = "Ctrl+C",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        }
        Box(
            modifier = Modifier
                .padding(start = 5.dp)
                .vistaAeroButton(isPrimary = true)
                .clickable(onClick = onSendToTarget)
                .padding(horizontal = 7.dp, vertical = 4.dp)
                .testTag("send_to_chat_${file.id}")
        ) {
            Text(
                text = "В чат ⤵",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
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
            .testTag("web_search_fallback_card")
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
