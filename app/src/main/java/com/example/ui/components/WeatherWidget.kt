package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.ConsensusWeatherReport
import com.example.model.WeatherCityResult
import com.example.ui.theme.LocalVistaTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.symbianGlossyTile
import com.example.ui.theme.vistaAeroButton
import com.example.ui.theme.vistaInsetBox
import com.example.ui.theme.vistaWindowFrame
import kotlin.math.roundToInt

fun weatherSymbol(iconKey: String): String = when (iconKey) {
    "sun" -> "☀"
    "partly" -> "⛅"
    "rain" -> "🌧"
    "snow" -> "❄"
    "storm" -> "⛈"
    "fog" -> "🌫"
    else -> "☁"
}

@Composable
fun WeatherWidgetCard(
    report: ConsensusWeatherReport?,
    selectedCity: WeatherCityResult,
    isLoading: Boolean,
    compact: Boolean,
    onRefresh: () -> Unit,
    onOpenForecast: () -> Unit,
    onOpenCityPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    Column(
        modifier = modifier
            .testTag("weather_widget_card")
            .symbianGlossyTile(bucket = 1, shape = RoundedCornerShape(4.dp))
            .clickable(onClick = { if (report != null) onOpenForecast() else onRefresh() })
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 6.dp else 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = weatherSymbol(report?.iconKey ?: "partly"),
                    fontSize = if (compact) 18.sp else 22.sp
                )
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (report != null) "${report.tempC}°C" else if (isLoading) "…" else "--°",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = if (compact) 14.sp else 16.sp
                        ),
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = report?.cityName ?: selectedCity.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = theme.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .testTag("weather_city_picker_btn")
                        .vistaAeroButton(isPrimary = false, isActive = false)
                        .clickable(onClick = onOpenCityPicker),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⌖", color = TextPrimary, fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .testTag("weather_refresh_btn")
                        .vistaAeroButton(isPrimary = false, isActive = isLoading)
                        .clickable(onClick = onRefresh),
                    contentAlignment = Alignment.Center
                ) {
                    Text("↻", color = TextPrimary, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = report?.condition ?: if (isLoading) "Обновление…" else "Нажмите для прогноза",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (report != null) {
            Text(
                text = "💨 ${report.windKmh} км/ч  •  💧 ${report.humidityPercent}%  •  ${report.sources.size} ист.",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun WeatherForecastDialog(
    report: ConsensusWeatherReport,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onPickCity: () -> Unit,
    showInHubOther: Boolean = true,
    showInHomeTile: Boolean = false,
    onToggleHubOther: () -> Unit = {},
    onToggleHomeTile: () -> Unit = {}
) {
    val theme = LocalVistaTheme.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 520.dp)
                .heightIn(max = 620.dp)
                .vistaWindowFrame(RoundedCornerShape(6.dp))
                .padding(12.dp)
                .testTag("weather_forecast_dialog")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${weatherSymbol(report.iconKey)} ${report.cityName} — ${report.tempC}°C",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "${report.condition} • Ощущается ${report.feelsLikeC}°C • Консенсус ${report.sources.size} источников",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.accent
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VistaButton(text = "Город", onClick = onPickCity)
                    VistaButton(text = "↻", onClick = onRefresh)
                    VistaButton(text = "✕", onClick = onDismiss)
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .testTag("weather_forecast_scroll"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "sources_breakdown") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .vistaInsetBox()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Сравнение метео-источников (усреднение)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = theme.accent
                        )
                        report.sources.forEach { src ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = src.sourceName,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary,
                                    modifier = Modifier.weight(0.32f)
                                )
                                Text(
                                    text = src.tempC?.let { "${it.roundToInt()}°C" } ?: "--",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(0.16f)
                                )
                                Text(
                                    text = src.windKmh?.let { "${it.roundToInt()} км/ч" } ?: "--",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    modifier = Modifier.weight(0.22f)
                                )
                                Text(
                                    text = src.condition,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(0.30f)
                                )
                            }
                        }
                    }
                }

                item(key = "placement_toggles") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .vistaInsetBox()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .vistaAeroButton(isPrimary = showInHubOther, isActive = showInHubOther)
                                .clickable(onClick = onToggleHubOther)
                                .padding(vertical = 6.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (showInHubOther) "✓ В Хаб → Прочее" else "В Хаб → Прочее",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (showInHubOther) Color.White else TextPrimary,
                                maxLines = 1
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .vistaAeroButton(isPrimary = showInHomeTile, isActive = showInHomeTile)
                                .clickable(onClick = onToggleHomeTile)
                                .padding(vertical = 6.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (showInHomeTile) "✓ На Главном экране" else "На Главном экране",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (showInHomeTile) Color.White else TextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (report.hourly.isNotEmpty()) {
                    item(key = "hourly_section") {
                        Column {
                            Text(
                                text = "Почасовой прогноз",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(4.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp)
                            ) {
                                items(report.hourly) { pt ->
                                    Column(
                                        modifier = Modifier
                                            .symbianGlossyTile(bucket = 2, shape = RoundedCornerShape(3.dp))
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(pt.timeLabel, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                        Text("${pt.tempC}°C", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                                        Text("💧${pt.precipitationProb}%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = theme.accent)
                                    }
                                }
                            }
                        }
                    }
                }

                if (report.daily.isNotEmpty()) {
                    item(key = "daily_header") {
                        Text(
                            text = "Прогноз на 7 дней",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextSecondary
                        )
                    }
                    items(report.daily, key = { "day_${it.dayLabel}_${it.minTempC}_${it.maxTempC}" }) { day ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(theme.insetBg.copy(alpha = 0.65f), RoundedCornerShape(3.dp))
                                .border(1.dp, theme.divider.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(day.dayLabel, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary, modifier = Modifier.width(68.dp))
                            Text(day.condition, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("💨${day.windMaxKmh}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Spacer(Modifier.width(8.dp))
                            Text("${day.minTempC}° / ${day.maxTempC}°", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = theme.accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherCityPickerDialog(
    currentCity: WeatherCityResult,
    searchResults: List<WeatherCityResult>,
    onSearchChange: (String) -> Unit,
    onSelectCity: (WeatherCityResult) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalVistaTheme.current
    var queryField by remember { mutableStateOf(TextFieldValue("")) }
    val presets = remember {
        listOf(
            WeatherCityResult("Москва", "Россия", "Москва", 55.7558, 37.6173),
            WeatherCityResult("Санкт-Петербург", "Россия", "СПб", 59.9343, 30.3351),
            WeatherCityResult("Казань", "Россия", "Татарстан", 55.7961, 49.1064),
            WeatherCityResult("Екатеринбург", "Россия", "Свердловская", 56.8389, 60.6057),
            WeatherCityResult("Новосибирск", "Россия", "Новосибирская", 55.0084, 82.9357)
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 440.dp)
                .heightIn(max = 520.dp)
                .vistaWindowFrame(RoundedCornerShape(6.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Выбор города (${currentCity.name})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                VistaButton(text = "✕", onClick = onDismiss)
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .vistaInsetBox(isFocused = true)
                    .padding(horizontal = 8.dp, vertical = 7.dp)
            ) {
                if (queryField.text.isEmpty()) {
                    Text(
                        text = "Введите название города…",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                BasicTextField(
                    value = queryField,
                    onValueChange = {
                        queryField = it
                        onSearchChange(it.text)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                    cursorBrush = SolidColor(theme.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(8.dp))
            val listToShow = if (searchResults.isNotEmpty()) searchResults else presets
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(listToShow, key = { "${it.name}:${it.latitude}:${it.longitude}" }) { city ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .symbianGlossyTile(bucket = 0, isFocused = city.name == currentCity.name)
                            .clickable {
                                onSelectCity(city)
                                onDismiss()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(city.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
                            Text(
                                listOf(city.admin, city.country).filter { it.isNotBlank() }.joinToString(", "),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Text("Выбрать ▶", style = MaterialTheme.typography.labelSmall, color = theme.accent)
                    }
                }
            }
        }
    }
}
