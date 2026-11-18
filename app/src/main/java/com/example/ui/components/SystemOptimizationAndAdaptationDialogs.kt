package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.ViewQuilt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.KeyboardSearchMatcher
import com.example.data.MediaGuardRuntime
import com.example.model.AndroidOptimizationStats
import com.example.model.AppAdaptationMode
import com.example.model.AppEntry
import com.example.model.ShizukuState
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

/**
 * Dialog for Deep Android Optimization (RAM/Cache cleanup, Phantom Process Killer patch, Doze whitelist)
 * and 3rd-party Background Audio Playback Spoofing (YouTube, VK Video, Rutube, etc.).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AndroidOptimizationAndSpoofDialog(
    stats: AndroidOptimizationStats,
    shizukuState: ShizukuState,
    backgroundSpoofEnabled: Boolean,
    backgroundSpoofPackages: Set<String>,
    allApps: List<AppEntry>,
    onOptimizeNow: () -> Unit,
    onDisablePhantomKiller: () -> Unit,
    onToggleBackgroundSpoofEnabled: () -> Unit,
    onToggleBackgroundSpoofPackage: (String) -> Unit,
    onBoostPackageViaShizuku: (String) -> Unit,
    onOpenBackgroundWebAudio: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalVistaTheme.current
    val shizukuReady = shizukuState.isBinderAlive && shizukuState.isPermissionGranted
    val hasPrivileged = shizukuReady || shizukuState.isRootAvailable
    var searchQuery by remember { mutableStateOf("") }

    val sortedApps = remember(allApps, backgroundSpoofPackages, searchQuery) {
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
                compareByDescending<AppEntry> { backgroundSpoofPackages.contains(it.packageName) }
                    .thenByDescending { MediaGuardRuntime.isDefaultSpoilerCandidate(it.packageName) }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
            )
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 610.dp)
                .vistaWindowFrame(RoundedCornerShape(5.dp))
                .testTag("optimization_and_spoof_dialog")
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
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        tint = theme.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "ОПТИМИЗАЦИЯ И ФОНОВЫЙ ЗВУК",
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Live RAM & Android System Optimization card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ПАМЯТЬ ОЗУ ANDROID",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = theme.focusBorder
                        )
                        Text(
                            text = "${stats.availableRamMb} / ${stats.totalRamMb} МБ свободно (${stats.usedRamPercent}% занято)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (stats.usedRamPercent > 82) CallMissedRed else CallIncomingGreen
                        )
                    }

                    VistaProgressBar(
                        progress = (stats.usedRamPercent / 100f).coerceIn(0f, 1f),
                        height = 9.dp,
                        greenTint = stats.usedRamPercent < 75
                    )

                    stats.lastCleanupSummary?.let { summary ->
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MusicSourceAmber
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VistaButton(
                            text = "Очистить ОЗУ и кэш",
                            isPrimary = true,
                            onClick = onOptimizeNow,
                            modifier = Modifier.testTag("optimize_ram_button"),
                            leadingContent = {
                                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        )
                        if (hasPrivileged) {
                            VistaButton(
                                text = if (stats.phantomKillerPatched) "Phantom Killer: Откл ✓" else "Убрать Phantom Killer",
                                onClick = onDisablePhantomKiller,
                                modifier = Modifier.testTag("disable_phantom_killer_button")
                            )
                        }
                    }
                    if (!hasPrivileged) {
                        Text(
                            text = "С активным Shizuku или Root доступны глубокий trim-caches, am kill-all и снятие Phantom Process Killer.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }

                // 2. Anti-Pause Background Playback Spoof for YouTube, VK Video, etc.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onToggleBackgroundSpoofEnabled),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Headset,
                                    contentDescription = null,
                                    tint = theme.accent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "ОБМАН ФОНОВОЙ ПАУЗЫ (YouTube и др.)",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = theme.focusBorder
                                )
                            }
                            Text(
                                text = "Спуфинг фонового аудиофокуса + WakeLock + автовозврат воспроизведения при сворачивании и выключении экрана.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        VistaSwitch(
                            checked = backgroundSpoofEnabled,
                            onCheckedChange = { onToggleBackgroundSpoofEnabled() },
                            modifier = Modifier.testTag("bg_spoof_master_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VistaButton(
                            text = "Открыть Фон YouTube (Web)",
                            isPrimary = true,
                            onClick = {
                                onDismiss()
                                onOpenBackgroundWebAudio()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_bg_web_youtube_btn"),
                            leadingContent = {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            }
                        )
                    }

                    // Search box for spoof packages
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .vistaInsetBox()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = theme.accent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text("Найти плеер (YouTube, VK, Rutube…)", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                                cursorBrush = SolidColor(theme.accent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("bg_spoof_search_input")
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
                            .heightIn(min = 140.dp, max = 230.dp)
                            .vistaInsetBox()
                            .padding(4.dp)
                            .testTag("bg_spoof_apps_list"),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        items(sortedApps, key = { "spoof_${it.packageName}" }) { app ->
                            val selected = backgroundSpoofPackages.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .vistaAeroButton(isActive = selected, shape = RoundedCornerShape(3.dp))
                                    .clickable { onToggleBackgroundSpoofPackage(app.packageName) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("spoof_row_${app.packageName}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                app.icon?.let { bmp ->
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = app.label,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                        color = if (selected) theme.accent else TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (selected && hasPrivileged) {
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .vistaAeroButton()
                                            .clickable { onBoostPackageViaShizuku(app.packageName) }
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text("Doze+", style = MaterialTheme.typography.labelSmall, color = theme.accent)
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = if (selected) "Под защитой" else "Выключено",
                                    tint = if (selected) CallIncomingGreen else TextMuted.copy(alpha = 0.4f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom WebView that spoofs window visibility so HTML5 audio/video keeps playing
 * even when minimized, overlayed, or when the device screen is turned off.
 */
private class BackgroundAudioWebView(context: Context) : WebView(context) {
    override fun onWindowVisibilityChanged(visibility: Int) {
        // Always report VISIBLE to WebView renderer so JS doesn't pause media on screen off/minimize
        super.onWindowVisibilityChanged(View.VISIBLE)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, View.VISIBLE)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BackgroundWebAudioDialog(
    onDismiss: () -> Unit
) {
    val theme = LocalVistaTheme.current
    var urlInput by remember { mutableStateOf("https://m.youtube.com") }
    var desktopUa by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<BackgroundAudioWebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webViewRef?.stopLoading()
                webViewRef?.destroy()
            }
        }
    }

    fun applyUaAndLoad(view: BackgroundAudioWebView, targetUrl: String, useDesktop: Boolean) {
        val clean = targetUrl.trim().let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
        }
        view.settings.userAgentString = if (useDesktop) {
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        } else {
            WebSettings.getDefaultUserAgent(view.context)
        }
        urlInput = clean
        view.loadUrl(clean)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 14.dp)
                .vistaWindowFrame(RoundedCornerShape(5.dp))
                .testTag("background_web_audio_dialog")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .symbianMetallicBar()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Headset, contentDescription = null, tint = theme.accent, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "YOUTUBE / СТРИМИНГ В ФОНЕ (WEB-СПУФИНГ)",
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
                    Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = TextSecondary, modifier = Modifier.size(15.dp))
                }
            }

            // Quick service presets & Desktop UA spoof toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.surface.copy(alpha = 0.88f))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val presets = listOf(
                    "YouTube" to "https://m.youtube.com",
                    "YT Music" to "https://music.youtube.com",
                    "VK Видео" to "https://vkvideo.ru",
                    "Rutube" to "https://rutube.ru"
                )
                presets.forEach { (title, link) ->
                    Box(
                        modifier = Modifier
                            .vistaAeroButton(isActive = urlInput.contains(link.substringAfter("://")))
                            .clickable {
                                webViewRef?.let { applyUaAndLoad(it, link, desktopUa) }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(title, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                    }
                }
                Box(
                    modifier = Modifier
                        .vistaAeroButton(isPrimary = desktopUa, isActive = desktopUa)
                        .clickable {
                            desktopUa = !desktopUa
                            webViewRef?.let { applyUaAndLoad(it, urlInput, desktopUa) }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (desktopUa) "ПК-режим: ВКЛ" else "Моб. режим",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (desktopUa) Color.White else TextSecondary
                    )
                }
            }

            // Navigation / Address bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .vistaAeroButton()
                        .clickable { webViewRef?.goBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад", tint = TextPrimary, modifier = Modifier.size(15.dp))
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .vistaAeroButton()
                        .clickable { webViewRef?.reload() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Обновить", tint = theme.accent, modifier = Modifier.size(15.dp))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .vistaInsetBox()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    BasicTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                        cursorBrush = SolidColor(theme.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = { webViewRef?.let { applyUaAndLoad(it, urlInput, desktopUa) } }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                VistaButton(
                    text = "Перейти",
                    isPrimary = true,
                    onClick = { webViewRef?.let { applyUaAndLoad(it, urlInput, desktopUa) } }
                )
            }

            AndroidView(
                factory = { ctx ->
                    BackgroundAudioWebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (!url.isNullOrBlank()) {
                                    urlInput = url
                                }
                                // Inject Page Visibility API & wake/background play spoof for YouTube/HTML5 video
                                view?.evaluateJavascript(
                                    """
                                    (function(){
                                      try {
                                        Object.defineProperty(document, 'hidden', { get: function(){ return false; }, configurable: true });
                                        Object.defineProperty(document, 'visibilityState', { get: function(){ return 'visible'; }, configurable: true });
                                        document.addEventListener('visibilitychange', function(e){ e.stopImmediatePropagation(); }, true);
                                        document.addEventListener('webkitvisibilitychange', function(e){ e.stopImmediatePropagation(); }, true);
                                      } catch(err){}
                                    })();
                                    """.trimIndent(),
                                    null
                                )
                            }
                        }
                        applyUaAndLoad(this, urlInput, desktopUa)
                        webViewRef = this
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .border(1.dp, theme.chromeBorderOuter, RoundedCornerShape(3.dp))
            )

            Text(
                text = "Подсказка: включите трек и можете заблокировать экран — сервис KeyDeck и подмена Visibility API не дадут видео остановиться.",
                style = MaterialTheme.typography.labelSmall,
                color = MusicSourceAmber,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Dialog to configure App UI Adaptation (Accessibility + Overlay redraw into Standard / Messenger modes)
 * with F1-F6 tab switching and D-Pad/arrow-key focus navigation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppAdaptationSettingsDialog(
    enabled: Boolean,
    adaptationModes: Map<String, AppAdaptationMode>,
    allApps: List<AppEntry>,
    shizukuState: ShizukuState,
    onToggleEnabled: () -> Unit,
    onSetAppMode: (String, AppAdaptationMode) -> Unit,
    onGrantViaShizuku: () -> Unit,
    onToggleOemGestureBypass: () -> Unit = {},
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalVistaTheme.current
    val shizukuReady = shizukuState.isBinderAlive && shizukuState.isPermissionGranted
    val hasPrivileged = shizukuReady || shizukuState.isRootAvailable
    var searchQuery by remember { mutableStateOf("") }

    val sortedApps = remember(allApps, adaptationModes, searchQuery) {
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
                compareByDescending<AppEntry> {
                    (adaptationModes[it.packageName] ?: AppAdaptationMode.DISABLED) != AppAdaptationMode.DISABLED
                }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
            )
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .vistaWindowFrame(RoundedCornerShape(5.dp))
                .testTag("app_adaptation_dialog")
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
                        imageVector = Icons.Rounded.ViewQuilt,
                        contentDescription = null,
                        tint = theme.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "АДАПТАЦИЯ ПРИЛОЖЕНИЙ",
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
                    Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = TextSecondary, modifier = Modifier.size(15.dp))
                }
            }

            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Master toggle
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
                            text = "Перерисовка сторонних приложений",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "Обычные приложения и Мессенджеры перерисовываются в стиль Vista/Symbian с управлением стрелками, Enter, Esc и вкладками F1–F6.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    VistaSwitch(
                        checked = enabled,
                        onCheckedChange = { onToggleEnabled() },
                        modifier = Modifier.testTag("adaptation_master_switch")
                    )
                }

                // Permissions status row (Accessibility + Overlay)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusBadgeBox(
                            icon = Icons.Rounded.Security,
                            label = if (shizukuState.isAccessibilityEnabled) "Спец. возм.: ВКЛ" else "Спец. возм.: ВЫКЛ",
                            ok = shizukuState.isAccessibilityEnabled,
                            modifier = Modifier.weight(1f)
                        )
                        StatusBadgeBox(
                            icon = Icons.Rounded.Layers,
                            label = if (shizukuState.hasOverlayPermission) "Поверх окон: ВКЛ" else "Поверх окон: ВЫКЛ",
                            ok = shizukuState.hasOverlayPermission,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (hasPrivileged && (!shizukuState.isAccessibilityEnabled || !shizukuState.hasOverlayPermission)) {
                            VistaButton(
                                text = if (shizukuState.isRootAvailable && !shizukuReady) {
                                    "Включить через Root"
                                } else {
                                    "Включить всё через Shizuku"
                                },
                                isPrimary = true,
                                onClick = onGrantViaShizuku,
                                modifier = Modifier.testTag("grant_adaptation_shizuku_btn")
                            )
                        }
                        if (!shizukuState.isAccessibilityEnabled) {
                            VistaButton(
                                text = "Спец. возможности",
                                onClick = onOpenAccessibilitySettings
                            )
                        }
                        if (!shizukuState.hasOverlayPermission) {
                            VistaButton(
                                text = "Поверх окон",
                                onClick = onOpenOverlaySettings
                            )
                        }
                        val settingsAdapted = (adaptationModes["com.android.settings"] ?: AppAdaptationMode.DISABLED) != AppAdaptationMode.DISABLED
                        VistaButton(
                            text = if (settingsAdapted) "Настройки Android: Адапт. ✓" else "Перерисовать Настройки Android",
                            isPrimary = settingsAdapted,
                            onClick = {
                                val next = if (settingsAdapted) AppAdaptationMode.DISABLED else AppAdaptationMode.STANDARD
                                onSetAppMode("com.android.settings", next)
                            },
                            modifier = Modifier.testTag("toggle_android_settings_adaptation_btn")
                        )
                    }

                    // OEM Gesture Bypass (Xiaomi MIUI / HyperOS / ColorOS)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(theme.surface.copy(alpha = 0.55f))
                            .clickable(onClick = onToggleOemGestureBypass)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("oem_gesture_bypass_row"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Обход блокировки жестов (Xiaomi / HyperOS)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Text(
                                text = "Нижняя полоса и боковые триггеры Назад/Домой/Недавние для стороннего лаунчера",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = TextMuted
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        VistaSwitch(
                            checked = shizukuState.oemGestureBypassEnabled,
                            onCheckedChange = { onToggleOemGestureBypass() },
                            modifier = Modifier.testTag("oem_gesture_bypass_switch")
                        )
                    }
                }

                // Search bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaInsetBox()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = theme.accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text("Найти приложение или мессенджер…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                            cursorBrush = SolidColor(theme.accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("adaptation_search_input")
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

                // Per-app mode list: Выкл | Стандарт | Мессенджер
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 170.dp, max = 300.dp)
                        .vistaInsetBox()
                        .padding(4.dp)
                        .testTag("adaptation_apps_list"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(sortedApps, key = { "adapt_${it.packageName}" }) { app ->
                        val currentMode = adaptationModes[app.packageName] ?: AppAdaptationMode.DISABLED
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .vistaAeroButton(isActive = currentMode != AppAdaptationMode.DISABLED, shape = RoundedCornerShape(3.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("adapt_row_${app.packageName}"),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                app.icon?.let { bmp ->
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = app.label,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    )
                                    Spacer(Modifier.width(7.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AppAdaptationMode.entries.forEach { mode ->
                                    val selected = currentMode == mode
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .vistaAeroButton(isPrimary = selected, isActive = selected, shape = RoundedCornerShape(3.dp))
                                            .clickable { onSetAppMode(app.packageName, mode) }
                                            .padding(vertical = 4.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = mode.shortTitle,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (selected) Color.White else TextSecondary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadgeBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    ok: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (ok) CallIncomingGreen.copy(alpha = 0.14f) else CallMissedRed.copy(alpha = 0.14f))
            .border(
                1.dp,
                if (ok) CallIncomingGreen.copy(alpha = 0.5f) else CallMissedRed.copy(alpha = 0.5f),
                RoundedCornerShape(3.dp)
            )
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (ok) CallIncomingGreen else CallMissedRed,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (ok) CallIncomingGreen else CallMissedRed,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
