package com.example.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AutoCloseManager
import com.example.data.KeyboardSearchMatcher
import com.example.model.AppEntry
import com.example.model.AppLanguage
import com.example.model.CustomColorPalette
import com.example.model.PlayerCoverStyle
import com.example.model.UiThemeColor
import com.example.model.WallpaperPaletteData
import com.example.ui.L10n
import com.example.ui.LocalAppLang
import kotlin.math.roundToInt
import com.example.ui.theme.CallIncomingGreen
import com.example.ui.theme.CallMissedRed
import com.example.ui.theme.LocalVistaTheme
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.symbianMetallicBar
import com.example.ui.theme.vistaAeroButton
import com.example.ui.theme.vistaInsetBox
import com.example.ui.theme.vistaWindowFrame

@Composable
fun AppearanceSettingsDialog(
    uiThemeColor: UiThemeColor,
    hasWallpaper: Boolean,
    wallpaperBitmap: ImageBitmap?,
    wallpaperPalette: WallpaperPaletteData?,
    savedPalettes: List<CustomColorPalette> = emptyList(),
    activeCustomPaletteId: String? = null,
    appLanguage: AppLanguage = AppLanguage.RU,
    uiScale: Float = 1.0f,
    playerCoverStyle: PlayerCoverStyle = PlayerCoverStyle.COVER,
    showWeatherInHubOther: Boolean = true,
    showWeatherInHomeTile: Boolean = false,
    onSelectThemeColor: (UiThemeColor) -> Unit,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onSaveCurrentWallpaperPalette: (String) -> Unit = {},
    onCreateAndSaveCustomPalette: (name: String, darkArgb: Long, accentArgb: Long, highlightArgb: Long) -> Unit = { _, _, _, _ -> },
    onSelectCustomPalette: (String?) -> Unit = {},
    onDeleteCustomPalette: (String) -> Unit = {},
    onSelectLanguage: (AppLanguage) -> Unit = {},
    onChangeUiScale: (Float) -> Unit = {},
    onSelectCoverStyle: (PlayerCoverStyle) -> Unit = {},
    onToggleWeatherInHubOther: () -> Unit = {},
    onToggleWeatherInHomeTile: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val theme = LocalVistaTheme.current
    val lang = LocalAppLang.current

    val maxDialogH = (LocalConfiguration.current.screenHeightDp.dp * 0.90f).coerceAtLeast(240.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 560.dp)
                .heightIn(max = maxDialogH)
                .vistaWindowFrame(RoundedCornerShape(5.dp))
                .testTag("appearance_settings_dialog")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = theme.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = L10n.appearanceTitle(lang).uppercase(),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = theme.focusBorder,
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
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Wallpaper + Windows Vista Glass Tiles toggle
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Wallpaper,
                            contentDescription = null,
                            tint = theme.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "ОБОИ И СТЕКЛЯННЫЕ ПЛИТКИ VISTA AERO",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = theme.focusBorder
                        )
                    }
                    Text(
                        text = if (hasWallpaper) {
                            "Обои активны: плитки стали полупрозрачными со стеклянным блеском Aero, а непрозрачные панели адаптированы под палитру обоев."
                        } else {
                            "Без обоев интерфейс остаётся классическим непрозрачным. Установите фото, чтобы включить эффект стекла Vista."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    if (wallpaperBitmap != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                bitmap = wallpaperBitmap,
                                contentDescription = "Текущие обои",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 76.dp, height = 54.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .border(1.dp, theme.chromeInnerGlow, RoundedCornerShape(3.dp))
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = "Палитра панелей",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary
                                )
                                wallpaperPalette?.let { pal ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        ColorSwatchBox(Color(pal.dominantDarkArgb.toInt()))
                                        ColorSwatchBox(Color(pal.accentArgb.toInt()))
                                        ColorSwatchBox(Color(pal.highlightArgb.toInt()))
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VistaButton(
                            text = if (hasWallpaper) "Сменить обои" else "Выбрать обои",
                            isPrimary = true,
                            onClick = onPickWallpaper,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_wallpaper_button")
                        )
                        if (hasWallpaper) {
                            VistaButton(
                                text = "Убрать обои",
                                onClick = onClearWallpaper,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("clear_wallpaper_button")
                            )
                        }
                    }

                    if (hasWallpaper && wallpaperPalette != null) {
                        VistaButton(
                            text = "Сохранить текущую палитру обоев",
                            onClick = {
                                onSaveCurrentWallpaperPalette("Обои #${savedPalettes.size + 1}")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_wallpaper_palette_btn")
                        )
                    }
                }

                // 1b. Saved Palettes & Custom Palette Creator
                CustomPalettesSection(
                    savedPalettes = savedPalettes,
                    activeCustomPaletteId = activeCustomPaletteId,
                    onSelectCustomPalette = onSelectCustomPalette,
                    onDeleteCustomPalette = onDeleteCustomPalette,
                    onCreateAndSaveCustomPalette = onCreateAndSaveCustomPalette
                )

                // 2. Base Color (active without wallpaper, or as base preference)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "БАЗОВЫЙ ЦВЕТ ИНТЕРФЕЙСА (БЕЗ ОБОЕВ)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = theme.focusBorder
                    )
                    Text(
                        text = "Переключает все панели, ползунки и плитки между классическим синим Aero и строгим серым стилем.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeColorChoiceCard(
                            title = if (lang == AppLanguage.RU) "Синий" else "Blue",
                            subtitle = "Vista Aero",
                            swatchTop = Color(0xFF2777C7),
                            swatchBottom = Color(0xFF0A2444),
                            selected = uiThemeColor == UiThemeColor.BLUE,
                            onClick = { onSelectThemeColor(UiThemeColor.BLUE) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_color_blue")
                        )
                        ThemeColorChoiceCard(
                            title = if (lang == AppLanguage.RU) "Серый" else "Gray",
                            subtitle = "Graphite",
                            swatchTop = Color(0xFF5D6572),
                            swatchBottom = Color(0xFF1A1D22),
                            selected = uiThemeColor == UiThemeColor.GRAY,
                            onClick = { onSelectThemeColor(UiThemeColor.GRAY) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_color_gray")
                        )
                    }
                }

                // 3. Language (RU / EN)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            tint = theme.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = L10n.languageLabel(lang).uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = theme.focusBorder
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppLanguage.entries.forEach { option ->
                            val selected = appLanguage == option
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .vistaAeroButton(isPrimary = selected, isActive = selected)
                                    .clickable { onSelectLanguage(option) }
                                    .padding(vertical = 8.dp, horizontal = 8.dp)
                                    .testTag("lang_option_${option.code}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (selected) Color.White else TextPrimary
                                )
                            }
                        }
                    }
                }

                // 4. UI Scale Slider (80% - 130%) — uses local draftScale while dragging to prevent flicker
                val clampedScale = uiScale.coerceIn(0.80f, 1.30f)
                var draftScale by remember(clampedScale) { mutableFloatStateOf(clampedScale) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.ZoomIn,
                                contentDescription = null,
                                tint = theme.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${L10n.scaleLabel(lang).uppercase()} (${(draftScale * 100f).roundToInt()}%)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = theme.focusBorder
                            )
                        }
                        Box(
                            modifier = Modifier
                                .vistaAeroButton()
                                .clickable {
                                    draftScale = 1.0f
                                    onChangeUiScale(1.0f)
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .testTag("ui_scale_reset_button")
                        ) {
                            Text(
                                text = "100%",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 32.dp, height = 28.dp)
                                .vistaAeroButton()
                                .clickable {
                                    val next = (((draftScale - 0.05f) * 20f).roundToInt() / 20f).coerceIn(0.80f, 1.30f)
                                    draftScale = next
                                    onChangeUiScale(next)
                                }
                                .testTag("ui_scale_minus_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "−",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                        }
                        VistaSlider(
                            value = draftScale,
                            onValueChange = { raw ->
                                draftScale = ((raw * 20f).roundToInt() / 20f).coerceIn(0.80f, 1.30f)
                            },
                            onValueChangeFinished = {
                                onChangeUiScale(draftScale)
                            },
                            valueRange = 0.80f..1.30f,
                            tickCount = 11,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ui_scale_slider")
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 32.dp, height = 28.dp)
                                .vistaAeroButton()
                                .clickable {
                                    val next = (((draftScale + 0.05f) * 20f).roundToInt() / 20f).coerceIn(0.80f, 1.30f)
                                    draftScale = next
                                    onChangeUiScale(next)
                                }
                                .testTag("ui_scale_plus_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 38.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("80%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextMuted)
                        Text("100%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = theme.accent)
                        Text("130%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextMuted)
                    }
                }

                // 5. Player Cover Style (Cover vs Spinning Disc)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Album,
                            contentDescription = null,
                            tint = theme.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = L10n.playerStyleLabel(lang).uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = theme.focusBorder
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlayerCoverStyle.entries.forEach { style ->
                            val selected = playerCoverStyle == style
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .vistaAeroButton(isPrimary = selected, isActive = selected)
                                    .clickable { onSelectCoverStyle(style) }
                                    .padding(vertical = 8.dp, horizontal = 8.dp)
                                    .testTag("cover_style_${style.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = L10n.coverStyle(lang, style),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (selected) Color.White else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // 6. Weather Widget Placement
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        text = "ВИДЖЕТ ПОГОДЫ",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = theme.focusBorder
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .vistaAeroButton(isPrimary = showWeatherInHubOther, isActive = showWeatherInHubOther)
                                .clickable(onClick = onToggleWeatherInHubOther)
                                .padding(vertical = 8.dp, horizontal = 8.dp)
                                .testTag("weather_toggle_hub_other"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (showWeatherInHubOther) "✓ В Хаб → Прочее" else "В Хаб → Прочее",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (showWeatherInHubOther) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (showWeatherInHubOther) Color.White else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .vistaAeroButton(isPrimary = showWeatherInHomeTile, isActive = showWeatherInHomeTile)
                                .clickable(onClick = onToggleWeatherInHomeTile)
                                .padding(vertical = 8.dp, horizontal = 8.dp)
                                .testTag("weather_toggle_home_tile"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (showWeatherInHomeTile) "✓ Плитка Домой" else "Плитка Домой",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (showWeatherInHomeTile) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (showWeatherInHomeTile) Color.White else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatchBox(color: Color) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
    )
}

@Composable
private fun ThemeColorChoiceCard(
    title: String,
    subtitle: String,
    swatchTop: Color,
    swatchBottom: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    Row(
        modifier = modifier
            .vistaAeroButton(isPrimary = selected, isActive = selected)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(swatchTop, swatchBottom)
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (selected) Color.White else TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) theme.focusBorder else TextMuted
            )
        }
    }
}

@Composable
private fun CustomPalettesSection(
    savedPalettes: List<CustomColorPalette>,
    activeCustomPaletteId: String?,
    onSelectCustomPalette: (String?) -> Unit,
    onDeleteCustomPalette: (String) -> Unit,
    onCreateAndSaveCustomPalette: (name: String, darkArgb: Long, accentArgb: Long, highlightArgb: Long) -> Unit
) {
    val theme = LocalVistaTheme.current
    var showCreator by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var darkHue by remember { mutableFloatStateOf(215f) }
    var accentHue by remember { mutableFloatStateOf(205f) }
    var highlightHue by remember { mutableFloatStateOf(190f) }

    val previewDark = Color.hsv(darkHue, 0.55f, 0.24f)
    val previewAccent = Color.hsv(accentHue, 0.78f, 0.64f)
    val previewHighlight = Color.hsv(highlightHue, 0.62f, 0.92f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .vistaInsetBox()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ПОЛЬЗОВАТЕЛЬСКИЕ ПАЛИТРЫ",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = theme.focusBorder
            )
            Box(
                modifier = Modifier
                    .vistaAeroButton(isPrimary = showCreator)
                    .clickable { showCreator = !showCreator }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("toggle_palette_creator_btn")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = if (showCreator) "Скрыть" else "Создать",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }

        if (savedPalettes.isEmpty()) {
            Text(
                text = "Сохраняйте палитру из обоев или создавайте собственную схему для всей системы и экрана блокировки.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        } else {
            if (activeCustomPaletteId != null) {
                Box(
                    modifier = Modifier
                        .vistaAeroButton()
                        .clickable { onSelectCustomPalette(null) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("reset_custom_palette_btn")
                ) {
                    Text(
                        text = "Сбросить палитру (авто / базовая)",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.accent
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                savedPalettes.forEach { pal ->
                    val isSelected = pal.id == activeCustomPaletteId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .vistaAeroButton(isPrimary = isSelected, isActive = isSelected, shape = RoundedCornerShape(3.dp))
                            .clickable { onSelectCustomPalette(if (isSelected) null else pal.id) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("saved_palette_row_${pal.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorSwatchBox(Color(pal.dominantDarkArgb.toInt()))
                        Spacer(Modifier.width(4.dp))
                        ColorSwatchBox(Color(pal.accentArgb.toInt()))
                        Spacer(Modifier.width(4.dp))
                        ColorSwatchBox(Color(pal.highlightArgb.toInt()))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pal.name,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) Color.White else TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isSelected) "Активна" else if (pal.isFromWallpaper) "Из обоев" else "Пользовательская",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                color = if (isSelected) theme.focusBorder else TextMuted
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .vistaAeroButton()
                                .clickable { onDeleteCustomPalette(pal.id) }
                                .testTag("delete_palette_${pal.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Удалить палитру",
                                tint = CallMissedRed,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showCreator) {
            HorizontalDivider(color = theme.divider.copy(alpha = 0.6f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColorSwatchBox(previewDark)
                ColorSwatchBox(previewAccent)
                ColorSwatchBox(previewHighlight)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .vistaInsetBox(isFocused = true)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    if (customName.isEmpty()) {
                        Text("Название палитры…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    BasicTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                        cursorBrush = SolidColor(theme.accent),
                        modifier = Modifier.fillMaxWidth().testTag("custom_palette_name_input")
                    )
                }
            }
            Text("Фон (Dark): ${darkHue.roundToInt()}°", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            VistaSlider(
                value = darkHue,
                onValueChange = { darkHue = it },
                valueRange = 0f..360f,
                compact = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Акцент (Accent): ${accentHue.roundToInt()}°", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            VistaSlider(
                value = accentHue,
                onValueChange = { accentHue = it },
                valueRange = 0f..360f,
                compact = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Подсветка (Highlight): ${highlightHue.roundToInt()}°", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            VistaSlider(
                value = highlightHue,
                onValueChange = { highlightHue = it },
                valueRange = 0f..360f,
                compact = true,
                modifier = Modifier.fillMaxWidth()
            )
            VistaButton(
                text = "Сохранить и применить",
                isPrimary = true,
                onClick = {
                    val label = customName.trim().ifBlank { "Палитра ${savedPalettes.size + 1}" }
                    onCreateAndSaveCustomPalette(
                        label,
                        previewDark.toArgb().toLong() and 0xFFFFFFFFL,
                        previewAccent.toArgb().toLong() and 0xFFFFFFFFL,
                        previewHighlight.toArgb().toLong() and 0xFFFFFFFFL
                    )
                    customName = ""
                    showCreator = false
                },
                modifier = Modifier.fillMaxWidth().testTag("save_custom_palette_btn")
            )
        }
    }
}

@Composable
fun AutoKillSettingsDialog(
    enabled: Boolean,
    autoKillPackages: Set<String>,
    allApps: List<AppEntry>,
    shizukuReady: Boolean,
    isRootAvailable: Boolean = false,
    onToggleEnabled: () -> Unit,
    onTogglePackage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalVistaTheme.current
    var searchQuery by remember { mutableStateOf("") }
    val initialSelected = remember(allApps) { autoKillPackages }

    val sortedApps = remember(allApps, initialSelected, searchQuery) {
        val filtered = if (searchQuery.isBlank()) {
            allApps
        } else {
            allApps.filter {
                KeyboardSearchMatcher.matches(it.label, searchQuery) ||
                    KeyboardSearchMatcher.matches(it.packageName, searchQuery)
            }
        }
        filtered
            .distinctBy { it.packageName }
            .sortedWith(
                compareByDescending<AppEntry> { initialSelected.contains(it.packageName) }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
            )
    }

    val maxDialogH = (LocalConfiguration.current.screenHeightDp.dp * 0.90f).coerceAtLeast(240.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 540.dp)
                .heightIn(max = maxDialogH)
                .vistaWindowFrame(RoundedCornerShape(5.dp))
                .testTag("autokill_settings_dialog")
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
                    Icon(
                        imageVector = Icons.Rounded.AutoMode,
                        contentDescription = null,
                        tint = theme.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "ОДНОРАЗОВЫЕ ПРИЛОЖЕНИЯ",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = theme.focusBorder
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
                    .weight(1f, fill = false)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox()
                        .clickable(onClick = onToggleEnabled)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Закрывать сразу после выхода",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        val privilegedReady = shizukuReady || isRootAvailable
                        Text(
                            text = when {
                                shizukuReady && isRootAvailable -> "Shizuku + Root активны: выполняется полный force-stop без зависания в ОЗУ."
                                shizukuReady -> "Shizuku активен: выполняется полный force-stop без зависания в ОЗУ."
                                isRootAvailable -> "Root активен: выполняется полный su am force-stop без зависания в ОЗУ."
                                else -> "Очищает фоновые процессы при возврате в лаунчер (с Shizuku или Root — полный force-stop)."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (privilegedReady) CallIncomingGreen else TextMuted
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    VistaSwitch(
                        checked = enabled,
                        onCheckedChange = { onToggleEnabled() },
                        modifier = Modifier.testTag("autokill_master_switch")
                    )
                }

                Text(
                    text = "Выбрано: ${autoKillPackages.size}. Звонилка во время активного звонка и активные плееры не прерываются.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MusicSourceAmber
                )

                // App search box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Поиск приложения",
                        tint = theme.accent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Добавить или найти приложение…",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                            cursorBrush = SolidColor(theme.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("autokill_search_input")
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Очистить",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 100.dp, max = 320.dp)
                        .vistaInsetBox()
                        .padding(4.dp)
                        .testTag("autokill_apps_list"),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(sortedApps, key = { it.packageName }) { app ->
                        val isSelected = autoKillPackages.contains(app.packageName)
                        val isHeuristicDefault = AutoCloseManager.isHeuristicOneTimeApp(app.packageName, app.originalLabel)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .vistaAeroButton(isActive = isSelected, shape = RoundedCornerShape(3.dp))
                                .clickable { onTogglePackage(app.packageName) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("autokill_row_${app.packageName}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            app.icon?.let { bmp ->
                                Image(
                                    bitmap = bmp,
                                    contentDescription = app.label,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isSelected && isHeuristicDefault) {
                                        "Одноразовое по умолчанию"
                                    } else {
                                        app.packageName
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                    color = if (isSelected) theme.accent else TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = if (isSelected) "Включено" else "Выключено",
                                tint = if (isSelected) CallIncomingGreen else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
