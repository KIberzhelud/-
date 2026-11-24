package com.example.ui.components

import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.KeyboardSearchMatcher
import com.example.model.AppEntry
import com.example.model.WorkspaceCell
import com.example.model.WorkspaceGridState
import com.example.ui.L10n
import com.example.ui.LocalAppLang
import com.example.ui.theme.LocalVistaTheme
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.symbianGlossyTile
import com.example.ui.theme.vistaAeroButton
import com.example.ui.theme.vistaInsetBox
import com.example.ui.theme.vistaWindowFrame
import kotlin.math.abs

/**
 * 2D Multi-Workspace viewport reached via horizontal swipe from Home.
 * Includes a compact 2D coordinate mini-map, 4-direction swipes/D-pad creation,
 * empty-cell app picker with cursor-safe TextFieldValue filter, and bounded launch.
 */
@Composable
fun WorkspaceCanvasHost(
    workspaceGrid: WorkspaceGridState,
    allApps: List<AppEntry>,
    onNavigateWorkspace: (dx: Int, dy: Int) -> Unit,
    onSelectWorkspaceCoord: (x: Int, y: Int) -> Unit = { tx, ty ->
        onNavigateWorkspace(tx - workspaceGrid.activeX, ty - workspaceGrid.activeY)
    },
    onAssignApp: (AppEntry) -> Unit,
    onLaunchCellAppInBounds: (Rect) -> Unit,
    onOpenHome: () -> Unit,
    onDeleteWorkspace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    val lang = LocalAppLang.current
    val density = LocalDensity.current
    val cell = workspaceGrid.activeCell
    var showPickerOverride by remember(cell.x, cell.y) { mutableStateOf(false) }
    var appFilterTfv by remember(cell.x, cell.y) { mutableStateOf(TextFieldValue("")) }
    var viewportRect by remember { mutableStateOf<Rect?>(null) }

    val assignedApp = remember(cell.packageName, cell.activityName, allApps) {
        val pkg = cell.packageName ?: return@remember null
        allApps.firstOrNull { it.packageName == pkg && (cell.activityName == null || it.activityName == cell.activityName) }
            ?: allApps.firstOrNull { it.packageName == pkg }
    }

    val swipeThresholdPx = with(density) { 56.dp.toPx() }
    val verticalSwipeThresholdPx = with(density) { 68.dp.toPx() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.insetBg.copy(alpha = if (theme.isGlassMode) 0.42f else 0.85f))
            .pointerInput(cell.x, cell.y, showPickerOverride, cell.hasAssignedApp) {
                var accX = 0f
                var accY = 0f
                var triggered = false
                if (!cell.hasAssignedApp || showPickerOverride) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            accX = 0f
                            triggered = false
                        },
                        onHorizontalDrag = { _, drag ->
                            if (triggered) return@detectHorizontalDragGestures
                            accX += drag
                            if (abs(accX) >= swipeThresholdPx) {
                                triggered = true
                                onNavigateWorkspace(if (accX < 0f) +1 else -1, 0)
                            }
                        }
                    )
                } else {
                    detectDragGestures(
                        onDragStart = {
                            accX = 0f
                            accY = 0f
                            triggered = false
                        },
                        onDrag = { _, drag ->
                            if (triggered) return@detectDragGestures
                            accX += drag.x
                            accY += drag.y
                            if (abs(accX) >= swipeThresholdPx && abs(accX) > abs(accY) * 1.15f) {
                                triggered = true
                                onNavigateWorkspace(if (accX < 0f) +1 else -1, 0)
                            } else if (abs(accY) >= verticalSwipeThresholdPx && abs(accY) > abs(accX) * 1.15f) {
                                triggered = true
                                onNavigateWorkspace(0, if (accY < 0f) +1 else -1)
                            }
                        }
                    )
                }
            }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("workspace_canvas_host")
    ) {
        // Workspace header + 4-way navigation + Home & Delete controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = L10n.workspaceLabel(lang, cell.x, cell.y),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = theme.accent,
                maxLines = 1
            )
            SmallNavChip("◀", "ws_nav_left") { onNavigateWorkspace(-1, 0) }
            SmallNavChip("▲", "ws_nav_up") { onNavigateWorkspace(0, -1) }
            SmallNavChip("▼", "ws_nav_down") { onNavigateWorkspace(0, +1) }
            SmallNavChip("▶", "ws_nav_right") { onNavigateWorkspace(+1, 0) }

            Spacer(Modifier.weight(1f))

            SmallNavChip("🏠", "ws_home_btn") { onOpenHome() }
            if (!cell.isHome) {
                Box(
                    modifier = Modifier
                        .testTag("workspace_delete_btn")
                        .vistaAeroButton(isPrimary = false, isActive = false)
                        .clickable(onClick = onDeleteWorkspace)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = L10n.deleteWorkspace(lang),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFFF8E8E),
                        maxLines = 1
                    )
                }
            }
        }

        // Visual 2D Mini-Map Grid showing all created (x, y) cells
        WorkspaceMiniMapBar(
            cells = workspaceGrid.cells,
            activeX = workspaceGrid.activeX,
            activeY = workspaceGrid.activeY,
            onSelectCoord = onSelectWorkspaceCoord,
            onOpenHome = onOpenHome
        )

        Spacer(Modifier.height(6.dp))

        // Clean borderless desktop workspace surface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .onGloballyPositioned { coords ->
                    val b = coords.boundsInWindow()
                    val next = Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                    if (next.width() > 50 && next.height() > 50) {
                        viewportRect = next
                    }
                }
        ) {
            if (!cell.hasAssignedApp || showPickerOverride) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = L10n.pickWorkspaceApp(lang),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        if (cell.hasAssignedApp) {
                            VistaButton(text = "Отмена", onClick = { showPickerOverride = false })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .vistaInsetBox(isFocused = true)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        BasicTextField(
                            value = appFilterTfv,
                            onValueChange = { appFilterTfv = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                            cursorBrush = SolidColor(theme.accent),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 34.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (appFilterTfv.text.isEmpty()) {
                                        Text("Фильтр приложений…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                                    }
                                    innerTextField()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("workspace_app_filter_input")
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val filterStr = appFilterTfv.text
                    val visibleApps = remember(allApps, filterStr) {
                        if (filterStr.isBlank()) allApps
                        else allApps.filter { KeyboardSearchMatcher.matches(it.label, filterStr) }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 86.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(visibleApps, key = { it.id }) { candidate ->
                            Column(
                                modifier = Modifier
                                    .symbianGlossyTile(bucket = candidate.colorBucket)
                                    .clickable {
                                        onAssignApp(candidate)
                                        showPickerOverride = false
                                    }
                                    .padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                candidate.icon?.let { bmp ->
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = candidate.label,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = candidate.label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onLaunchCellAppInBounds(viewportRect ?: Rect()) }
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        assignedApp?.icon?.let { bmp ->
                            Image(
                                bitmap = bmp,
                                contentDescription = assignedApp.label,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = cell.label ?: assignedApp?.label ?: cell.packageName.orEmpty(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Стол (${cell.x}, ${cell.y}) • Полный экран без рамок\nСвайп ← ↑ ↓ → для смены стола",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VistaButton(
                            text = "Открыть на весь экран",
                            isPrimary = true,
                            onClick = { onLaunchCellAppInBounds(viewportRect ?: Rect()) },
                            modifier = Modifier.testTag("workspace_launch_btn")
                        )
                        VistaButton(
                            text = "Сменить",
                            onClick = { showPickerOverride = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceMiniMapBar(
    cells: List<WorkspaceCell>,
    activeX: Int,
    activeY: Int,
    onSelectCoord: (Int, Int) -> Unit,
    onOpenHome: () -> Unit
) {
    val theme = LocalVistaTheme.current
    val xs = cells.map { it.x }.distinct().sorted()
    val ys = cells.map { it.y }.distinct().sorted()
    val cellByCoord = remember(cells) { cells.associateBy { it.x to it.y } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .vistaInsetBox()
            .padding(horizontal = 6.dp, vertical = 5.dp)
            .testTag("workspace_minimap"),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "2D КАРТА ПРОСТРАНСТВ (X/Y)",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
            color = MusicSourceAmber
        )
        ys.forEach { y ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                xs.forEach { x ->
                    val cell = cellByCoord[x to y]
                    val isCurrent = x == activeX && y == activeY
                    if (cell != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .vistaAeroButton(isPrimary = isCurrent, isActive = isCurrent)
                                .clickable {
                                    if (cell.isHome) onOpenHome() else onSelectCoord(x, y)
                                }
                                .padding(horizontal = 4.dp, vertical = 3.dp)
                                .testTag("minimap_cell_${x}_${y}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (cell.isHome) "🏠 (0,0)" else "${cell.label?.take(8) ?: "Стол"} ($x,$y)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isCurrent) Color.White else theme.accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(3.dp))
                                .border(1.dp, theme.divider.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                                .clickable { onSelectCoord(x, y) }
                                .padding(vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+ ($x,$y)",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = TextMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallNavChip(
    label: String,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .testTag(tag)
            .vistaAeroButton(isPrimary = false, isActive = false)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
    }
}
