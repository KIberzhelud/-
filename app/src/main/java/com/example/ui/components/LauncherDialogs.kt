package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoMode
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.rounded.AddToQueue
import androidx.compose.material.icons.rounded.Headset
import com.example.R
import com.example.data.SymbianIconMapper
import com.example.data.UniversalSearchRepository
import com.example.model.AppEntry
import com.example.model.AppIconMode
import com.example.model.DeviceSearchFile
import com.example.model.SearchResultKind
import com.example.ui.theme.CallIncomingGreen
import com.example.ui.theme.CallMissedRed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FocusBorderCyan
import com.example.ui.theme.LocalVistaTheme
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.NavyDivider
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.symbianMetallicBar
import com.example.ui.theme.vistaAeroButton
import com.example.ui.theme.vistaInsetBox
import com.example.ui.theme.vistaWindowFrame

@Composable
fun AppContextDialog(
    app: AppEntry,
    isAutoKill: Boolean,
    isBackgroundSpoofed: Boolean = false,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onCreateWorkspace: () -> Unit = {},
    onTogglePin: () -> Unit,
    onToggleAutoKill: () -> Unit,
    onToggleBackgroundSpoof: () -> Unit = {},
    onRename: () -> Unit,
    onChooseIcon: () -> Unit,
    onShareApk: () -> Unit,
    onSaveApk: () -> Unit,
    onOpenInfo: () -> Unit,
    onUninstall: () -> Unit
) {
    val theme = LocalVistaTheme.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .vistaWindowFrame(RoundedCornerShape(5.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                app.icon?.let { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = app.label,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (app.isLinuxTile) "Linux CLI • ${app.packageName}" else app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                ContextActionRow(
                    icon = Icons.AutoMirrored.Rounded.Launch,
                    label = if (app.isLinuxTile) "Открыть в терминале Linux" else "Открыть приложение",
                    tint = ElectricCyan,
                    onClick = {
                        onDismiss()
                        onLaunch()
                    }
                )
                if (!app.isLinuxTile) {
                    ContextActionRow(
                        icon = Icons.Rounded.AddToQueue,
                        label = "Создать рабочий стол",
                        tint = theme.focusBorder,
                        onClick = {
                            onDismiss()
                            onCreateWorkspace()
                        },
                        modifier = Modifier.testTag("context_create_workspace")
                    )
                }
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
                if (!app.isLinuxTile) {
                    ContextActionRow(
                        icon = Icons.Rounded.AutoMode,
                        label = if (isAutoKill) {
                            "Одноразовое: закрывать при выходе ✓"
                        } else {
                            "Сделать одноразовым (закрывать при выходе)"
                        },
                        tint = if (isAutoKill) CallIncomingGreen else theme.accent,
                        onClick = {
                            onToggleAutoKill()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("context_toggle_autokill")
                    )
                    ContextActionRow(
                        icon = Icons.Rounded.Headset,
                        label = if (isBackgroundSpoofed) {
                            "Фоновый звук/обход паузы: ВКЛ ✓"
                        } else {
                            "Разрешить звук в фоне (обход паузы)"
                        },
                        tint = if (isBackgroundSpoofed) CallIncomingGreen else TextSecondary,
                        onClick = {
                            onToggleBackgroundSpoof()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("context_toggle_bg_spoof")
                    )
                }
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
                    tint = theme.accent,
                    onClick = onChooseIcon,
                    modifier = Modifier.testTag("context_choose_icon")
                )
                if (!app.isLinuxTile) {
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
                }
                ContextActionRow(
                    icon = Icons.Rounded.DeleteOutline,
                    label = if (app.isLinuxTile) "Удалить пакет и плитку" else "Удалить",
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .vistaWindowFrame(RoundedCornerShape(5.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ПЕРЕИМЕНОВАТЬ ПРИЛОЖЕНИЕ",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = FocusBorderCyan
                )
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Системное имя: ${app.originalLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox(isFocused = true)
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = "Новое название",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                        cursorBrush = SolidColor(ElectricCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rename_app_input")
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (app.isRenamed || inputText.trim() != app.originalLabel) {
                        VistaButton(
                            text = "Сбросить",
                            onClick = onReset,
                            modifier = Modifier.testTag("rename_app_reset")
                        )
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VistaButton(text = "Отмена", onClick = onDismiss)
                        VistaButton(
                            text = "Сохранить",
                            isPrimary = true,
                            onClick = { onSave(inputText) },
                            modifier = Modifier.testTag("rename_app_save")
                        )
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .vistaWindowFrame(RoundedCornerShape(5.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ИКОНКА ПРИЛОЖЕНИЯ",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = FocusBorderCyan
                    )
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .vistaAeroButton()
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Закрыть",
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
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
                                    .clip(RoundedCornerShape(3.dp))
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
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
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
                                        .vistaAeroButton(isPrimary = isSelected, isActive = isSelected)
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
                                        color = if (isSelected) Color.White else TextPrimary,
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
            .vistaAeroButton(isPrimary = selected, isActive = selected)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        iconContent()
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (selected) Color.White else TextPrimary,
            maxLines = 1
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = if (selected) FocusBorderCyan else TextMuted,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .vistaWindowFrame(RoundedCornerShape(5.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (file.thumbnail != null) {
                    Image(
                        bitmap = file.thumbnail,
                        contentDescription = file.name,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(3.dp))
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
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
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

            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
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
            .vistaAeroButton(shape = RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = TextPrimary
        )
    }
}

@Composable
fun KeyboardShortcutsDialog(
    onDismiss: () -> Unit
) {
    val shortcuts = listOf(
        "F1 / F2 / F3" to "Вкладки лаунчера: Главная / Звонки (+подвкладки) / Хаб (+подвкладки)",
        "F4" to "Редактор книг (план, главы, .docx) и заметок (.txt)",
        "F5" to "Выдвижная панель плеера и треков",
        "F6" to "Быстрый запуск Камеры",
        "Свайп от края / Зажатие" to "Переход на соседний 2D рабочий стол или создание нового",
        "Свайп вверх по плееру" to "Свернуть плеер в компактную полосу; вниз — развернуть",
        "Стрелки / D-Pad" to "Навигация по плиткам, звонкам и элементам Хаба",
        "Enter / Пробел" to "Запуск выбранного приложения / чата / файла",
        "Ctrl + R / Щипок внутрь/наружу" to "Недавние приложения в стиле BlackBerry OS 10 / KEY2",
        "Буквы / *apk / *deb" to "Универсальный поиск: приложения, чаты, файлы, пакеты Linux, музыка, сеть",
        "Esc" to "Сброс поиска, выход из книг/недавних, закрытие окон",
        "Alt + Буква" to "Переход к букве в алфавите Niagara",
        "Alt + L" to "Переключить алфавит (A-Z / А-Я)",
        "Ctrl + 1 / 2 / 3 / 4" to "Главная / Звонки / Хаб / Камера",
        "Ctrl + Пробел / Media" to "Пауза / Воспроизведение музыки",
        "Ctrl + ← / →" to "Предыдущий / Следующий трек",
        "Ctrl + ↑ / ↓" to "Громкость музыки + / -",
        "Ctrl + M / Свайп плеера" to "Открыть выдвижную панель плеера и треков",
        "Ctrl + Tab / Свайп обложки" to "Переключить источник музыки (все плееры)",
        "Ctrl + D" to "Закрепить / открепить выбранное приложение",
        "Ctrl + N" to "Открыть шторку уведомлений или BlackBerry Хаб",
        "Зажатие Tab / Ctrl + E" to "Редактор книг (план, главы, .docx) и заметок (.txt)",
        "Tab (короткое)" to "Переключить подвкладку в Звонках / Хабе",
        "Shift + F1 / F12 / Ctrl + /" to "Показать эту справку"
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .vistaWindowFrame(RoundedCornerShape(5.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_symbian_keyboard),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "ГОРЯЧИЕ КЛАВИШИ",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = FocusBorderCyan
                    )
                }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .vistaAeroButton()
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Закрыть",
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                shortcuts.forEach { (keys, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = keys,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MusicSourceAmber,
                            modifier = Modifier
                                .vistaInsetBox()
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider(color = NavyDivider.copy(alpha = 0.45f))
                }
            }
        }
    }
}
