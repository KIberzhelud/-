package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.UniversalSearchRepository
import com.example.model.AppEntry
import com.example.model.DeviceSearchFile
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FocusBorderCyan
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.NavyDivider
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.SymbianBevelHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TileBrightAzure
import com.example.ui.theme.TileBrightAzureBottom
import com.example.ui.theme.TileBrightAzureTop
import com.example.ui.theme.symbianGlossyTile
import com.example.ui.theme.symbianMetallicBar

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AppTileGrid(
    apps: List<AppEntry>,
    searchImages: List<DeviceSearchFile>,
    searchFiles: List<DeviceSearchFile>,
    isSearchingDevice: Boolean,
    indexedFolderCount: Int,
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
    onOpenSearchFile: (DeviceSearchFile) -> Unit,
    onLongPressSearchFile: (DeviceSearchFile) -> Unit,
    onConnectSearchFolder: () -> Unit,
    onSearchWeb: (query: String, engine: String) -> Unit,
    onOpenShortcuts: () -> Unit,
    onOpenWriter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trimmedQuery = searchQuery.trim()
    val isUniversalMode = trimmedQuery.length >= 2

    Column(modifier = modifier.fillMaxSize()) {
        // Symbian metallic search & shortcuts strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .symbianMetallicBar()
                .border(width = 1.dp, color = NavyDivider)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_symbian_search),
                contentDescription = "Поиск",
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onRequestSearchFocus),
                contentAlignment = Alignment.CenterStart
            ) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = if (highlightedLetter != null) {
                            "Буква: $highlightedLetter • Поиск по телефону и сети…"
                        } else {
                            "Поиск: приложения, файлы, фото, сеть…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (highlightedLetter != null) ElectricCyan else TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                    cursorBrush = SolidColor(ElectricCyan),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val firstApp = apps.firstOrNull()
                            val firstFile = searchFiles.firstOrNull()
                            val firstImg = searchImages.firstOrNull()
                            when {
                                firstApp != null -> onLaunchApp(firstApp)
                                firstFile != null -> onOpenSearchFile(firstFile)
                                firstImg != null -> onOpenSearchFile(firstImg)
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

            if (searchQuery.isNotEmpty() || highlightedLetter != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(NavySurfaceElevated)
                        .border(1.dp, SymbianBevelHighlight.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                        .clickable(onClick = onClearSearch)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("apps_search_clear")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_symbian_clear),
                            contentDescription = "Сбросить поиск",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "ESC",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(NavySurfaceElevated)
                        .border(1.dp, SymbianBevelHighlight.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                        .clickable(onClick = onOpenShortcuts)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("shortcuts_help_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_symbian_keyboard),
                            contentDescription = "Горячие клавиши",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "F1",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                }
            }

            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(NavySurfaceElevated)
                    .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                    .clickable(onClick = onOpenWriter)
                    .padding(horizontal = 7.dp, vertical = 4.dp)
                    .testTag("apps_toolbar_writer_button")
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_symbian_editor),
                    contentDescription = "Редактор книг и заметок",
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ElectricCyan)
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
                        text = "Нет приложений по запросу «$searchQuery»",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Введите минимум 2 символа для поиска по файлам, фото и интернету",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
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
                if (isUniversalMode) {
                    // Universal Palm Pre "Just Type" top status + SAF folder index button
                    item(
                        key = "universal_header_bar",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(NavySurfaceElevated.copy(alpha = 0.85f))
                                .border(1.dp, NavyDivider, RoundedCornerShape(4.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (isSearchingDevice) "Поиск по устройству…" else "Универсальный поиск",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ElectricCyan
                                )
                                if (isSearchingDevice) {
                                    Spacer(Modifier.width(8.dp))
                                    CircularProgressIndicator(
                                        color = ElectricCyan,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(NavySurface)
                                    .border(1.dp, SymbianBevelHighlight.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                                    .clickable(onClick = onConnectSearchFolder)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("universal_connect_folder_button"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CreateNewFolder,
                                    contentDescription = "Подключить папку",
                                    tint = MusicSourceAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (indexedFolderCount == 0) "+ Папка" else "Папок: $indexedFolderCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    // 1. Matching Apps
                    if (apps.isNotEmpty()) {
                        item(
                            key = "section_apps",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            UniversalSectionHeader(
                                title = "ПРИЛОЖЕНИЯ (${apps.size})",
                                iconRes = R.drawable.ic_symbian_menu
                            )
                        }
                        itemsIndexed(apps, key = { _, app -> app.id }) { index, app ->
                            AppGlossyTileItem(
                                app = app,
                                index = index,
                                isFocused = index == focusedIndex,
                                isLetterHighlighted = highlightedLetter != null && app.initialChar == highlightedLetter,
                                onLaunchApp = onLaunchApp,
                                onLongPressApp = onLongPressApp
                            )
                        }
                    }

                    // 2. Matching Images
                    if (searchImages.isNotEmpty()) {
                        item(
                            key = "section_images",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            UniversalSectionHeader(
                                title = "КАРТИНКИ (${searchImages.size})",
                                iconRes = R.drawable.ic_symbian_gallery
                            )
                        }
                        items(searchImages, key = { it.id }) { img ->
                            SearchImageCard(
                                file = img,
                                onClick = { onOpenSearchFile(img) },
                                onLongClick = { onLongPressSearchFile(img) }
                            )
                        }
                    }

                    // 3. Matching Files
                    if (searchFiles.isNotEmpty()) {
                        item(
                            key = "section_files",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            UniversalSectionHeader(
                                title = "ФАЙЛЫ (${searchFiles.size})",
                                iconRes = R.drawable.ic_symbian_files
                            )
                        }
                        items(
                            items = searchFiles,
                            key = { it.id },
                            span = { GridItemSpan(maxLineSpan) }
                        ) { file ->
                            SearchFileRow(
                                file = file,
                                onClick = { onOpenSearchFile(file) },
                                onLongClick = { onLongPressSearchFile(file) }
                            )
                        }
                    }

                    // 4. Web Search fallback / companion (Palm Pre style)
                    item(
                        key = "section_web_search",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        val nothingLocal = apps.isEmpty() && searchImages.isEmpty() && searchFiles.isEmpty() && !isSearchingDevice
                        WebSearchFallbackCard(
                            query = trimmedQuery,
                            isProminent = nothingLocal,
                            onSearchWeb = { engine -> onSearchWeb(trimmedQuery, engine) }
                        )
                    }
                } else {
                    // Normal clean Home grid
                    itemsIndexed(apps, key = { _, app -> app.id }) { index, app ->
                        AppGlossyTileItem(
                            app = app,
                            index = index,
                            isFocused = index == focusedIndex,
                            isLetterHighlighted = highlightedLetter != null && app.initialChar == highlightedLetter,
                            onLaunchApp = onLaunchApp,
                            onLongPressApp = onLongPressApp
                        )
                    }
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
                        .clip(RoundedCornerShape(4.dp))
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
                    fontWeight = FontWeight.Medium
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
            color = FocusBorderCyan
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchImageCard(
    file: DeviceSearchFile,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(NavySurfaceElevated)
            .border(1.dp, SymbianBevelHighlight.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
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
                .clip(RoundedCornerShape(3.dp))
                .background(NavySurface),
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
            .clip(RoundedCornerShape(4.dp))
            .background(NavySurfaceElevated)
            .border(1.dp, SymbianBevelHighlight.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
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
        Text(
            text = "Зажать",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = TextMuted,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebSearchFallbackCard(
    query: String,
    isProminent: Boolean,
    onSearchWeb: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (isProminent) {
                    Brush.verticalGradient(listOf(TileBrightAzureTop.copy(alpha = 0.35f), NavySurfaceElevated))
                } else {
                    Brush.verticalGradient(listOf(NavySurfaceElevated, NavySurface))
                }
            )
            .border(
                width = 1.dp,
                color = if (isProminent) ElectricCyan.copy(alpha = 0.65f) else NavyDivider,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Public,
                contentDescription = null,
                tint = ElectricCyan,
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
            .clip(RoundedCornerShape(3.dp))
            .background(
                Brush.verticalGradient(listOf(TileBrightAzureTop, TileBrightAzure, TileBrightAzureBottom))
            )
            .border(1.dp, ElectricCyan.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag(tag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = FocusBorderCyan
        )
    }
}
