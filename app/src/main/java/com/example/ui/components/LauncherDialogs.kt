package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.SymbianIconMapper
import com.example.data.UniversalSearchRepository
import com.example.model.AppEntry
import com.example.model.AppIconMode
import com.example.model.DeviceSearchFile
import com.example.model.SearchResultKind
import com.example.ui.theme.CallMissedRed
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

@Composable
fun AppContextDialog(
    app: AppEntry,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onChooseIcon: () -> Unit,
    onShareApk: () -> Unit,
    onSaveApk: () -> Unit,
    onOpenInfo: () -> Unit,
    onUninstall: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NavyDivider, RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            color = NavySurface
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    app.icon?.let { bmp ->
                        Image(
                            bitmap = bmp,
                            contentDescription = app.label,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = NavyDivider)
                Spacer(Modifier.height(6.dp))

                ContextActionRow(
                    icon = Icons.AutoMirrored.Rounded.Launch,
                    label = "Открыть приложение",
                    tint = ElectricCyan,
                    onClick = {
                        onDismiss()
                        onLaunch()
                    }
                )
                ContextActionRow(
                    icon = if (app.isPinned) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    label = if (app.isPinned) "Убрать из избранного (Ctrl+D)" else "Закрепить в избранном (Ctrl+D)",
                    tint = MusicSourceAmber,
                    onClick = {
                        onTogglePin()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("context_toggle_pin")
                )
                ContextActionRow(
                    icon = Icons.Rounded.Edit,
                    label = "Переименовать",
                    tint = TextPrimary,
                    onClick = onRename,
                    modifier = Modifier.testTag("context_rename_app")
                )
                ContextActionRow(
                    icon = Icons.Rounded.Palette,
                    label = "Иконка (Symbian / Ориг. / Своя)",
                    tint = ElectricCyan,
                    onClick = onChooseIcon,
                    modifier = Modifier.testTag("context_choose_icon")
                )
                ContextActionRow(
                    icon = Icons.Rounded.Share,
                    label = "Поделиться APK",
                    tint = ElectricCyan,
                    onClick = onShareApk,
                    modifier = Modifier.testTag("context_share_apk")
                )
                ContextActionRow(
                    icon = Icons.Rounded.SaveAlt,
                    label = "Сохранить APK в папку…",
                    tint = TextPrimary,
                    onClick = onSaveApk,
                    modifier = Modifier.testTag("context_save_apk")
                )
                ContextActionRow(
                    icon = Icons.Rounded.Info,
                    label = "О приложении",
                    tint = TextPrimary,
                    onClick = onOpenInfo
                )
                ContextActionRow(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "Удалить",
                    tint = CallMissedRed,
                    onClick = onUninstall
                )
            }
        }
    }
}

@Composable
fun RenameAppDialog(
    app: AppEntry,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    var inputText by remember(app.id) { mutableStateOf(app.label) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            color = NavySurface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ПЕРЕИМЕНОВАТЬ ПРИЛОЖЕНИЕ",
                    style = MaterialTheme.typography.titleSmall,
                    color = ElectricCyan
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Системное имя: ${app.originalLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    singleLine = true,
                    label = { Text("Новое название") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = NavyDivider,
                        cursorColor = ElectricCyan,
                        focusedLabelColor = ElectricCyan,
                        unfocusedLabelColor = TextSecondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_app_input")
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (app.isRenamed || inputText.trim() != app.originalLabel) {
                        TextButton(
                            onClick = onReset,
                            modifier = Modifier.testTag("rename_app_reset")
                        ) {
                            Text("Сбросить", color = MusicSourceAmber)
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("Отмена", color = TextSecondary)
                        }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = { onSave(inputText) },
                            colors = ButtonDefaults.buttonColors(containerColor = TileBrightAzure),
                            modifier = Modifier.testTag("rename_app_save")
                        ) {
                            Text("Сохранить", color = FocusBorderCyan)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppIconPickerDialog(
    app: AppEntry,
    onDismiss: () -> Unit,
    onSelectAuto: () -> Unit,
    onSelectOriginal: () -> Unit,
    onSelectSymbianKey: (String) -> Unit,
    onPickCustomImage: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .border(1.dp, ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            color = NavySurface
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ИКОНКА ПРИЛОЖЕНИЯ",
                            style = MaterialTheme.typography.titleSmall,
                            color = ElectricCyan
                        )
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Закрыть",
                            tint = TextSecondary
                        )
                    }
                }

                HorizontalDivider(color = NavyDivider)
                Spacer(Modifier.height(10.dp))

                // Mode buttons: Auto / Original / Custom gallery image
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconSourceOptionCard(
                        title = "Авто",
                        subtitle = "По умолчанию",
                        selected = app.iconMode == AppIconMode.AUTO,
                        onClick = onSelectAuto,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("icon_mode_auto")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconSourceOptionCard(
                        title = "Оригинал",
                        subtitle = "Android",
                        selected = app.iconMode == AppIconMode.ORIGINAL,
                        onClick = onSelectOriginal,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("icon_mode_original")
                    ) {
                        if (app.androidIcon != null) {
                            Image(
                                bitmap = app.androidIcon,
                                contentDescription = "Оригинальная иконка",
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        } else {
                            Image(
                                painter = painterResource(R.drawable.ic_symbian_apk),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    IconSourceOptionCard(
                        title = "Своя",
                        subtitle = "Из фото",
                        selected = app.iconMode == AppIconMode.CUSTOM,
                        onClick = onPickCustomImage,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("icon_mode_custom")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = null,
                            tint = MusicSourceAmber,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "ВЫБРАТЬ ИЗ СПИСКА SYMBIAN E61i",
                    style = MaterialTheme.typography.labelMedium,
                    color = FocusBorderCyan
                )
                Spacer(Modifier.height(8.dp))

                val rows = remember { SymbianIconMapper.symbianIconCatalog.chunked(3) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rows.forEach { rowOptions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowOptions.forEach { option ->
                                val isSelected = app.iconMode == AppIconMode.SYMBIAN &&
                                    app.iconOverride?.symbianKey == option.key
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (isSelected) TileBrightAzure.copy(alpha = 0.45f) else NavySurfaceElevated
                                        )
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) ElectricCyan else SymbianBevelHighlight.copy(alpha = 0.45f),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .clickable { onSelectSymbianKey(option.key) }
                                        .padding(horizontal = 4.dp, vertical = 8.dp)
                                        .testTag("symbian_icon_option_${option.key}"),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Image(
                                        painter = painterResource(option.resId),
                                        contentDescription = option.label,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isSelected) FocusBorderCyan else TextPrimary,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            repeat(3 - rowOptions.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconSourceOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconContent: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) TileBrightAzure.copy(alpha = 0.45f) else NavySurfaceElevated)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) ElectricCyan else SymbianBevelHighlight.copy(alpha = 0.45f),
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        iconContent()
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (selected) FocusBorderCyan else TextPrimary,
            maxLines = 1
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = TextMuted,
            maxLines = 1
        )
    }
}

@Composable
fun FileContextDialog(
    file: DeviceSearchFile,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onOpenFolder: () -> Unit,
    onCopyPath: () -> Unit,
    onDelete: () -> Unit
) {
    val sizeText = UniversalSearchRepository.formatSize(file.sizeBytes)
    val metaLine = buildString {
        if (sizeText.isNotEmpty()) append("$sizeText • ")
        append(file.mimeType)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            color = NavySurface
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (file.thumbnail != null) {
                        Image(
                            bitmap = file.thumbnail,
                            contentDescription = file.name,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    } else {
                        val iconRes = when {
                            file.kind == SearchResultKind.IMAGE -> R.drawable.ic_symbian_gallery
                            file.name.endsWith(".apk", ignoreCase = true) -> R.drawable.ic_symbian_apk
                            file.name.endsWith(".docx", ignoreCase = true) || file.name.endsWith(".txt", ignoreCase = true) -> R.drawable.ic_symbian_docx
                            else -> R.drawable.ic_symbian_files
                        }
                        Image(
                            painter = painterResource(iconRes),
                            contentDescription = file.name,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = file.displayPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = metaLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = NavyDivider)
                Spacer(Modifier.height(6.dp))

                ContextActionRow(
                    icon = Icons.AutoMirrored.Rounded.Launch,
                    label = "Открыть файл",
                    tint = ElectricCyan,
                    onClick = {
                        onDismiss()
                        onOpen()
                    },
                    modifier = Modifier.testTag("context_file_open")
                )
                ContextActionRow(
                    icon = Icons.Rounded.Share,
                    label = "Поделиться",
                    tint = MusicSourceAmber,
                    onClick = onShare,
                    modifier = Modifier.testTag("context_file_share")
                )
                ContextActionRow(
                    icon = Icons.Rounded.FolderOpen,
                    label = "Перейти по пути (папка)",
                    tint = TextPrimary,
                    onClick = onOpenFolder,
                    modifier = Modifier.testTag("context_file_open_folder")
                )
                ContextActionRow(
                    icon = Icons.Rounded.ContentCopy,
                    label = "Копировать путь",
                    tint = TextSecondary,
                    onClick = onCopyPath,
                    modifier = Modifier.testTag("context_file_copy_path")
                )
                ContextActionRow(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "Удалить",
                    tint = CallMissedRed,
                    onClick = onDelete,
                    modifier = Modifier.testTag("context_file_delete")
                )
            }
        }
    }
}

@Composable
private fun ContextActionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
    }
}

@Composable
fun KeyboardShortcutsDialog(
    onDismiss: () -> Unit
) {
    val shortcuts = listOf(
        "Стрелки / D-Pad" to "Навигация по плиткам приложений",
        "Enter" to "Запуск выбранного приложения / вызов",
        "Любая буква / цифра" to "Универсальный поиск: приложения, фото, файлы и интернет",
        "Esc" to "Сброс поиска и возврат к списку",
        "Alt + Буква" to "Переход к букве в алфавите Niagara",
        "Alt + L" to "Переключить алфавит (A-Z / А-Я)",
        "Ctrl + 1 / 2 / 3" to "Главная / Звонки / Хаб",
        "Ctrl + 4" to "Быстрый запуск Камеры",
        "Ctrl + Пробел / Media" to "Пауза / Воспроизведение музыки",
        "Ctrl + ← / →" to "Предыдущий / Следующий трек",
        "Ctrl + ↑ / ↓" to "Громкость музыки + / -",
        "Ctrl + M" to "Открыть расширенную панель музыки",
        "Ctrl + Tab / Свайп плеера" to "Переключить источник музыки (все плееры)",
        "Ctrl + D" to "Закрепить / открепить выбранное приложение",
        "Ctrl + N / Свайп вниз" to "Открыть шторку уведомлений или BlackBerry Хаб",
        "Зажатие Tab / Ctrl + E" to "Редактор книг (план, главы, .docx) и заметок (.txt)",
        "Tab (короткое)" to "Переключить вкладку в Звонках / Хабе",
        "F1 или Ctrl + /" to "Показать эту справку"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NavyDivider, RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            color = NavySurface
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_symbian_keyboard),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "ГОРЯЧИЕ КЛАВИШИ",
                            style = MaterialTheme.typography.titleMedium,
                            color = ElectricCyan
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Закрыть",
                            tint = TextSecondary
                        )
                    }
                }
                HorizontalDivider(color = NavyDivider)
                Spacer(Modifier.height(8.dp))

                shortcuts.forEach { (keys, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = keys,
                            style = MaterialTheme.typography.labelMedium,
                            color = MusicSourceAmber,
                            modifier = Modifier
                                .background(NavySurfaceElevated, RoundedCornerShape(3.dp))
                                .border(1.dp, NavyDivider, RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
