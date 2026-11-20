package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.KeyboardSearchMatcher
import com.example.data.UniversalSearchRepository
import com.example.model.LinuxPackageEntry
import com.example.ui.theme.CallIncomingGreen
import com.example.ui.theme.LocalVistaTheme
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.symbianGlossyTile
import com.example.ui.theme.vistaInsetBox
import com.example.ui.theme.vistaWindowFrame

@Composable
fun LinuxCatalogDialog(
    packages: List<LinuxPackageEntry>,
    isTermuxInstalled: Boolean,
    onInstallOrRun: (LinuxPackageEntry) -> Unit,
    onRefresh: () -> Unit,
    onOpenTermuxDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalVistaTheme.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(packages, query) {
        if (query.isBlank()) packages
        else packages.filter {
            KeyboardSearchMatcher.matches(it.name, query) ||
                KeyboardSearchMatcher.matches(it.packageName, query) ||
                KeyboardSearchMatcher.matches(it.description, query)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 540.dp)
                .heightIn(max = 600.dp)
                .vistaWindowFrame(RoundedCornerShape(6.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Пакеты Linux (.deb / AppImage / CLI)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = if (isTermuxInstalled) "Termux обнаружен • готов к запуску" else "Termux не найден • fallback на Shizuku/proot",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isTermuxInstalled) CallIncomingGreen else MusicSourceAmber
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!isTermuxInstalled) {
                        VistaButton(text = "Termux", isPrimary = true, onClick = onOpenTermuxDownload)
                    }
                    VistaButton(text = "↻", onClick = onRefresh)
                    VistaButton(text = "✕", onClick = onDismiss)
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .vistaInsetBox(isFocused = query.isNotEmpty())
                    .padding(horizontal = 8.dp, vertical = 7.dp)
            ) {
                if (query.isEmpty()) {
                    Text("Поиск .deb, AppImage, утилит…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                    cursorBrush = SolidColor(theme.accent),
                    modifier = Modifier.fillMaxWidth().testTag("linux_catalog_search")
                )
            }

            Spacer(Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Файлы .deb, .AppImage и .sh не найдены в Download / Linux / Documents",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.id }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .symbianGlossyTile(bucket = 2, shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                                    .border(1.dp, theme.accent.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (entry.isInstalled) "CLI" else entry.kind.title.substringBefore(' '),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                                    color = theme.accent
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "v${entry.version} • ${entry.architecture}" +
                                        if (entry.sizeBytes > 0L) " • ${UniversalSearchRepository.formatSize(entry.sizeBytes)}" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                                Text(
                                    text = entry.description,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            VistaButton(
                                text = if (entry.isInstalled) "Запуск" else "Установ.",
                                isPrimary = true,
                                onClick = { onInstallOrRun(entry) }
                            )
                        }
                    }
                }
            }
        }
    }
}
