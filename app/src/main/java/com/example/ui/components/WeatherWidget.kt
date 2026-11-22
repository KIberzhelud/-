package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.ConsensusWeatherReport
import com.example.model.WeatherCityResult
import com.example.ui.theme.LocalVistaTheme
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.symbianGlossyTile
import com.example.ui.theme.vistaAeroButton
import com.example.ui.theme.vistaInsetBox
import com.example.ui.theme.vistaWindowFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

fun weatherSymbol(iconKey: String): String = when (iconKey) {
    "sun" -> "☀"
    "partly" -> "⛅"
    "rain" -> "🌧"
    "snow" -> "❄"
    "storm" -> "⛈"
    "fog" -> "🌫"
    else -> "☁"
}

private fun weatherBackdropRes(iconKey: String): Int = when (iconKey) {
    "sun", "partly" -> R.drawable.img_weather_sunny
    "rain" -> R.drawable.img_weather_rain
    "storm" -> R.drawable.img_weather_storm
    else -> R.drawable.img_weather_cloudy
}

@Composable
private fun TouchWizFlipClockTempBadge(
    tempText: String,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val badgeShape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .clip(badgeShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF434850),
                        Color(0xFF23272D),
                        Color(0xFF0E1014),
                        Color(0xFF1A1E24)
                    )
                )
            )
            .border(1.dp, Color(0xFF8C97A6), badgeShape)
            .padding(horizontal = if (compact) 8.dp else 11.dp, vertical = if (compact) 4.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tempText,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (compact) 15.sp else 20.sp,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.88f),
                    offset = Offset(0f, 1.5f),
                    blurRadius = 3f
                )
            ),
            color = Color(0xFFF6F9FC),
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun WeatherAtmosphereOverlay(
    iconKey: String,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        fun drawSoftCloud(cx: Float, cy: Float, scale: Float, tintTop: Color, tintBottom: Color) {
            val rMain = 18.dp.toPx() * scale
            val baseBrush = Brush.verticalGradient(listOf(tintTop, tintBottom))
            drawCircle(brush = baseBrush, radius = rMain * 0.72f, center = Offset(cx - rMain * 0.68f, cy + rMain * 0.14f))
            drawCircle(brush = baseBrush, radius = rMain, center = Offset(cx - rMain * 0.08f, cy - rMain * 0.14f))
            drawCircle(brush = baseBrush, radius = rMain * 0.78f, center = Offset(cx + rMain * 0.62f, cy + rMain * 0.08f))
            drawRoundRect(
                brush = baseBrush,
                topLeft = Offset(cx - rMain * 1.1f, cy),
                size = androidx.compose.ui.geometry.Size(rMain * 2.25f, rMain * 0.74f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(rMain * 0.38f, rMain * 0.38f)
            )
            // Soft upper rim specular gloss
            drawCircle(
                color = Color.White.copy(alpha = 0.22f),
                radius = rMain * 0.45f,
                center = Offset(cx - rMain * 0.18f, cy - rMain * 0.45f)
            )
        }

        when (iconKey) {
            "sun" -> {
                val sunCenter = Offset(w * 0.84f, h * 0.30f)
                val sunR = 17.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color(0x99FFE79B), Color(0x33FFA726), Color.Transparent),
                        center = sunCenter,
                        radius = sunR * 2.5f
                    ),
                    radius = sunR * 2.5f,
                    center = sunCenter
                )
                for (i in 0 until 10) {
                    val rad = (i * 36.0) * (PI / 180.0)
                    val inner = Offset(sunCenter.x + (sunR * 1.1f) * cos(rad).toFloat(), sunCenter.y + (sunR * 1.1f) * sin(rad).toFloat())
                    val outer = Offset(sunCenter.x + (sunR * 1.65f) * cos(rad).toFloat(), sunCenter.y + (sunR * 1.65f) * sin(rad).toFloat())
                    drawLine(
                        color = Color(0xCCFFE082),
                        start = inner,
                        end = outer,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0xFFFFF8D6), Color(0xFFFFC107)), center = sunCenter, radius = sunR),
                    radius = sunR,
                    center = sunCenter
                )
            }
            "partly" -> {
                val sunCenter = Offset(w * 0.80f, h * 0.28f)
                val sunR = 14.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0xBBFFE082), Color(0x22FFA000), Color.Transparent), center = sunCenter, radius = sunR * 2.1f),
                    radius = sunR * 2.1f,
                    center = sunCenter
                )
                drawCircle(color = Color(0xFFFFD54F), radius = sunR, center = sunCenter)
                drawSoftCloud(w * 0.72f, h * 0.56f, 0.92f, Color(0xFFF4F8FC), Color(0xFFB5C4D4))
                drawSoftCloud(w * 0.26f, h * 0.22f, 0.55f, Color(0x88FFFFFF), Color(0x55B8C8D8))
            }
            "rain" -> {
                drawSoftCloud(w * 0.78f, h * 0.30f, 0.96f, Color(0xFFD2DCE6), Color(0xFF7B8C9E))
                drawSoftCloud(w * 0.55f, h * 0.26f, 0.70f, Color(0xBBE2EAF2), Color(0xFF6B7B8C))
                for (i in 0 until 9) {
                    val rx = w * (0.48f + i * 0.055f)
                    val ry = h * (0.46f + (i % 3) * 0.14f)
                    drawLine(
                        color = Color(0xCC9AD6FF),
                        start = Offset(rx, ry),
                        end = Offset(rx - 6.dp.toPx(), ry + 13.dp.toPx()),
                        strokeWidth = 1.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            "snow" -> {
                drawSoftCloud(w * 0.78f, h * 0.28f, 0.90f, Color(0xFFEAF2FA), Color(0xFF9FB2C6))
                for (i in 0 until 10) {
                    val sx = w * (0.42f + i * 0.055f)
                    val sy = h * (0.45f + ((i * 37) % 45) / 100f)
                    drawCircle(color = Color(0xEEFFFFFF), radius = 2.4.dp.toPx(), center = Offset(sx, sy))
                }
            }
            "storm" -> {
                drawSoftCloud(w * 0.76f, h * 0.28f, 1.0f, Color(0xFF9BA8B8), Color(0xFF475361))
                val bolt = Path().apply {
                    moveTo(w * 0.76f, h * 0.42f)
                    lineTo(w * 0.70f, h * 0.62f)
                    lineTo(w * 0.77f, h * 0.62f)
                    lineTo(w * 0.69f, h * 0.88f)
                    lineTo(w * 0.83f, h * 0.56f)
                    lineTo(w * 0.75f, h * 0.56f)
                    close()
                }
                drawPath(path = bolt, color = Color(0xFFFFD54F))
                drawPath(path = bolt, color = Color(0xAAFFFFFF), style = Stroke(width = 1.dp.toPx()))
            }
            "fog" -> {
                drawSoftCloud(w * 0.76f, h * 0.30f, 0.88f, Color(0xFFDDE5ED), Color(0xFF9AA8B5))
                for (band in 0..2) {
                    val y = h * (0.58f + band * 0.13f)
                    drawLine(
                        color = Color(0x88E4EEF8),
                        start = Offset(w * 0.36f, y),
                        end = Offset(w * 0.94f, y),
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            else -> {
                drawSoftCloud(w * 0.80f, h * 0.34f, 0.98f, Color(0xFFF2F6FA), Color(0xFFA0B1C2))
                drawSoftCloud(w * 0.56f, h * 0.26f, 0.68f, Color(0xCCE8F0F8), Color(0xFF8FA0B2))
            }
        }
    }
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
    val iconKey = report?.iconKey ?: "partly"
    val cardShape = RoundedCornerShape(5.dp)

    Box(
        modifier = modifier
            .testTag("weather_widget_card")
            .clip(cardShape)
            .symbianGlossyTile(bucket = 1, shape = cardShape)
            .border(1.2.dp, Color(0xFF7B9AB8), cardShape)
            .clickable(onClick = { if (report != null) onOpenForecast() else onRefresh() })
    ) {
        // Real atmospheric backdrop photo (old TouchWiz / HTC Sense style)
        // Real atmospheric backdrop photo (old TouchWiz / HTC Sense style adapting to weather)
        Image(
            painter = painterResource(weatherBackdropRes(iconKey)),
            contentDescription = report?.condition ?: "Погода",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xC908111D),
                            Color(0x880B1726),
                            Color(0x550C1B2E)
                        )
                    )
                )
        )
        WeatherAtmosphereOverlay(
            iconKey = iconKey,
            modifier = Modifier.matchParentSize()
        )
        // Top Vista glass specular gloss
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 22.dp else 28.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.24f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                    TouchWizFlipClockTempBadge(
                        tempText = if (report != null) "${report.tempC}°" else if (isLoading) "…" else "--°",
                        compact = compact
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = report?.cityName ?: selectedCity.name,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = if (compact) 12.sp else 13.5.sp,
                                shadow = Shadow(Color.Black, Offset(0f, 1.2f), 3f)
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${weatherSymbol(iconKey)} ${report?.condition ?: if (isLoading) "Обновление…" else "Нажмите для прогноза"}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.sp,
                                shadow = Shadow(Color.Black, Offset(0f, 1f), 2f)
                            ),
                            color = Color(0xFFE3F2FD),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("weather_city_picker_btn")
                            .vistaAeroButton(isPrimary = false, isActive = false)
                            .clickable(onClick = onOpenCityPicker),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⌖", color = Color.White, fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("weather_refresh_btn")
                            .vistaAeroButton(isPrimary = false, isActive = isLoading)
                            .clickable(onClick = onRefresh),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("↻", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
            if (report != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Ощущ. ${report.feelsLikeC}° • 💨 ${report.windKmh} км/ч • 💧 ${report.humidityPercent}% • ${report.sources.size} ист.",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        shadow = Shadow(Color.Black, Offset(0f, 1f), 2f)
                    ),
                    color = Color(0xFFD6E8FA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
    val maxDialogH = (LocalConfiguration.current.screenHeightDp.dp * 0.90f).coerceAtLeast(240.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 520.dp)
                .heightIn(max = maxDialogH)
                .vistaWindowFrame(RoundedCornerShape(6.dp))
                .padding(10.dp)
                .testTag("weather_forecast_dialog")
        ) {
            // HTC Sense / Samsung TouchWiz Skeuomorphic Hero Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp))
                    .border(1.dp, Color(0xFF7FA6CC), RoundedCornerShape(5.dp))
            ) {
                Image(
                    painter = painterResource(weatherBackdropRes(report.iconKey)),
                    contentDescription = report.condition,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xB809131F), Color(0xD608101A))
                            )
                        )
                )
                WeatherAtmosphereOverlay(
                    iconKey = report.iconKey,
                    modifier = Modifier.matchParentSize()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(11.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            TouchWizFlipClockTempBadge(tempText = "${report.tempC}°C", compact = false)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${weatherSymbol(report.iconKey)} ${report.cityName}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        shadow = Shadow(Color.Black, Offset(0f, 1.5f), 3f)
                                    ),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${report.condition} • Ощущ. ${report.feelsLikeC}°C",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        shadow = Shadow(Color.Black, Offset(0f, 1f), 2f)
                                    ),
                                    color = Color(0xFFE0F1FF),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            VistaButton(text = "Город", onClick = onPickCity)
                            VistaButton(text = "↻", onClick = onRefresh)
                            VistaButton(text = "✕", onClick = onDismiss)
                        }
                    }
                    Text(
                        text = "💨 Ветер ${report.windKmh} км/ч  •  💧 Влажность ${report.humidityPercent}%  •  Консенсус ${report.sources.size} источников",
                        style = MaterialTheme.typography.labelSmall,
                        color = MusicSourceAmber
                    )
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
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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

    val maxDialogH = (LocalConfiguration.current.screenHeightDp.dp * 0.90f).coerceAtLeast(240.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 480.dp)
                .heightIn(max = maxDialogH)
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
