package com.example.ui

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.R
import com.example.data.LauncherPreferencesRepository
import com.example.data.MediaControlRepository
import com.example.data.MusicCatalogAndLyricsRepository
import com.example.data.NotificationHubRepository
import com.example.data.ShizukuBridge
import com.example.data.WallpaperThemeHelper
import com.example.data.WeatherRepository
import com.example.model.AppLanguage
import com.example.model.ConsensusWeatherReport
import com.example.model.HubNotificationItem
import com.example.model.HubSubTab
import com.example.model.MediaPlayerState
import com.example.model.PlayerCoverStyle
import com.example.model.ShizukuState
import com.example.model.TrackLyricsData
import com.example.model.UiThemeColor
import com.example.model.WallpaperPaletteData
import com.example.model.WeatherCityResult
import com.example.ui.components.AlbumArtThumb
import com.example.ui.components.VistaSlider
import com.example.ui.components.WeatherCityPickerDialog
import com.example.ui.components.WeatherForecastDialog
import com.example.ui.components.WeatherWidgetCard
import com.example.ui.theme.CallIncomingGreen
import com.example.ui.theme.KeyDeckTheme
import com.example.ui.theme.LocalVistaTheme
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.buildVistaThemeTokens
import com.example.ui.theme.symbianGlossyTile
import com.example.ui.theme.vistaAeroButton
import com.example.ui.theme.vistaInsetBox
import com.example.ui.theme.vistaWindowFrame
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

class LockScreenActivity : ComponentActivity() {

    private lateinit var mediaRepo: MediaControlRepository
    private lateinit var shizukuBridge: ShizukuBridge
    private lateinit var prefsRepo: LauncherPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        mediaRepo = MediaControlRepository(application)
        shizukuBridge = ShizukuBridge(application)
        prefsRepo = LauncherPreferencesRepository(application)
        mediaRepo.startObserving()
        shizukuBridge.startObserving()

        setContent {
            val scope = rememberCoroutineScope()
            val mediaState by mediaRepo.mediaState.collectAsStateWithLifecycle()
            val eqState by mediaRepo.dspController.state.collectAsStateWithLifecycle()
            val notifications by NotificationHubRepository.notifications.collectAsStateWithLifecycle()
            val shizukuState by shizukuBridge.statusState.collectAsStateWithLifecycle()

            val uiThemeColor by prefsRepo.uiThemeColor.collectAsStateWithLifecycle(initialValue = UiThemeColor.BLUE)
            val wallpaperPath by prefsRepo.wallpaperPath.collectAsStateWithLifecycle(initialValue = null)
            val savedPalettes by prefsRepo.savedPalettes.collectAsStateWithLifecycle(initialValue = emptyList())
            val activeCustomPaletteId by prefsRepo.activeCustomPaletteId.collectAsStateWithLifecycle(initialValue = null)
            val appLanguage by prefsRepo.appLanguage.collectAsStateWithLifecycle(initialValue = AppLanguage.RU)
            val uiScale by prefsRepo.uiScale.collectAsStateWithLifecycle(initialValue = 1.0f)
            val playerCoverStyle by prefsRepo.playerCoverStyle.collectAsStateWithLifecycle(initialValue = PlayerCoverStyle.COVER)
            val showWeatherInHubOther by prefsRepo.showWeatherInHubOther.collectAsStateWithLifecycle(initialValue = true)
            val showWeatherInHomeTile by prefsRepo.showWeatherInHomeTile.collectAsStateWithLifecycle(initialValue = false)
            val selectedCity by prefsRepo.selectedWeatherCity.collectAsStateWithLifecycle(
                initialValue = LauncherPreferencesRepository.DEFAULT_WEATHER_CITY
            )

            var wallpaperState by remember {
                mutableStateOf<Pair<ImageBitmap?, WallpaperPaletteData?>>(null to null)
            }
            LaunchedEffect(wallpaperPath) {
                wallpaperState = WallpaperThemeHelper.loadWallpaperAndPalette(wallpaperPath)
            }

            var currentLyrics by remember { mutableStateOf<TrackLyricsData?>(null) }
            val activeTrackTitle = mediaState.activeSession?.title.orEmpty()
            val activeTrackArtist = mediaState.activeSession?.artist.orEmpty()
            val activeDuration = mediaState.activeSession?.durationMs ?: 0L
            LaunchedEffect(activeTrackTitle, activeTrackArtist) {
                if (activeTrackTitle.isBlank() || activeTrackTitle == mediaState.activeSession?.appLabel) {
                    currentLyrics = null
                } else {
                    val key = MusicCatalogAndLyricsRepository.buildTrackKey(activeTrackTitle, activeTrackArtist)
                    val overrides = runCatching { prefsRepo.lyricsOverrides.first() }.getOrDefault(emptyMap())
                    val saved = overrides[key]
                    currentLyrics = if (saved != null) {
                        if (saved.isDeleted) null else saved
                    } else {
                        MusicCatalogAndLyricsRepository.fetchSyncedLyrics(
                            title = activeTrackTitle,
                            artist = activeTrackArtist,
                            durationMs = activeDuration,
                            localFilePath = null
                        )
                    }
                }
            }

            var livePositionMs by remember { mutableLongStateOf(0L) }
            LaunchedEffect(mediaState.activeSession) {
                while (isActive) {
                    val active = mediaState.activeSession
                    livePositionMs = if (active != null) {
                        MediaControlRepository.currentEstimatedPositionMs(active)
                    } else {
                        0L
                    }
                    delay(500L)
                }
            }

            var weatherReport by remember { mutableStateOf<ConsensusWeatherReport?>(null) }
            var isWeatherLoading by remember { mutableStateOf(false) }
            var showForecastDialog by remember { mutableStateOf(false) }
            var showCityPickerDialog by remember { mutableStateOf(false) }
            var weatherCityResults by remember { mutableStateOf<List<WeatherCityResult>>(emptyList()) }
            var citySearchJob by remember { mutableStateOf<Job?>(null) }

            fun refreshWeather(city: WeatherCityResult = selectedCity) {
                scope.launch {
                    isWeatherLoading = true
                    WeatherRepository.fetchConsensusWeather(city).onSuccess {
                        weatherReport = it
                    }
                    isWeatherLoading = false
                }
            }

            LaunchedEffect(selectedCity) {
                refreshWeather(selectedCity)
            }

            val customPalette = activeCustomPaletteId?.let { id ->
                savedPalettes.firstOrNull { it.id == id }
            }
            val effectivePalette = customPalette?.let {
                WallpaperPaletteData(
                    dominantDarkArgb = it.dominantDarkArgb,
                    accentArgb = it.accentArgb,
                    highlightArgb = it.highlightArgb
                )
            } ?: wallpaperState.second

            val vistaTokens = buildVistaThemeTokens(
                themeColor = uiThemeColor,
                wallpaperPalette = effectivePalette,
                hasWallpaper = wallpaperState.first != null
            )
            val baseDensity = LocalDensity.current
            val scaledDensity = remember(baseDensity.density, baseDensity.fontScale, uiScale) {
                Density(
                    density = (baseDensity.density * uiScale).coerceIn(0.6f, 6.0f),
                    fontScale = (baseDensity.fontScale * uiScale).coerceIn(0.6f, 4.0f)
                )
            }

            CompositionLocalProvider(
                LocalAppLang provides appLanguage,
                LocalDensity provides scaledDensity
            ) {
                KeyDeckTheme(vistaTokens = vistaTokens) {
                    SymbianLockScreen(
                        mediaState = mediaState,
                        livePositionMs = livePositionMs,
                        currentLyrics = currentLyrics,
                        isShuffleEnabled = eqState.shuffleEnabled,
                        repeatModeLabel = eqState.repeatMode.title,
                        playbackSpeed = eqState.playbackSpeed,
                        notifications = notifications,
                        shizukuState = shizukuState,
                        wallpaperBitmap = wallpaperState.first,
                        coverStyle = playerCoverStyle,
                        weatherReport = weatherReport,
                        selectedWeatherCity = selectedCity,
                        isWeatherLoading = isWeatherLoading,
                        onTogglePlayPause = { mediaRepo.togglePlayPause() },
                        onSkipPrevious = { mediaRepo.skipPrevious() },
                        onSkipNext = { mediaRepo.skipNext() },
                        onSeekTo = { pos -> mediaRepo.seekTo(pos) },
                        onCyclePlayer = { mediaRepo.cycleSession(+1) },
                        onSelectSession = { sid -> mediaRepo.selectSession(sid) },
                        onLikeStream = { mediaRepo.triggerStreamingLike(mediaState.activeSession?.packageName) },
                        onDislikeStream = { mediaRepo.triggerStreamingDislike(mediaState.activeSession?.packageName) },
                        onStartMyWave = { mediaRepo.triggerMyWave(mediaState.activeSession?.packageName) },
                        onToggleShuffle = { mediaRepo.toggleLocalShuffle() },
                        onCycleRepeat = { mediaRepo.cycleLocalRepeatMode() },
                        onSetSpeed = { speed -> mediaRepo.setLocalPlaybackSpeed(speed) },
                        onRefreshWeather = { refreshWeather(selectedCity) },
                        onOpenWeatherForecast = { showForecastDialog = true },
                        onOpenWeatherPicker = { showCityPickerDialog = true },
                        onDismissNotification = { key -> NotificationHubRepository.dismissNotification(key) },
                        onUnlock = { unlockScreen() }
                    )

                    if (showForecastDialog && weatherReport != null) {
                        WeatherForecastDialog(
                            report = weatherReport!!,
                            onDismiss = { showForecastDialog = false },
                            onRefresh = { refreshWeather(selectedCity) },
                            onPickCity = {
                                showForecastDialog = false
                                showCityPickerDialog = true
                            },
                            showInHubOther = showWeatherInHubOther,
                            showInHomeTile = showWeatherInHomeTile,
                            onToggleHubOther = {
                                scope.launch { prefsRepo.setShowWeatherInHubOther(!showWeatherInHubOther) }
                            },
                            onToggleHomeTile = {
                                scope.launch { prefsRepo.setShowWeatherInHomeTile(!showWeatherInHomeTile) }
                            }
                        )
                    }

                    if (showCityPickerDialog) {
                        WeatherCityPickerDialog(
                            currentCity = selectedCity,
                            searchResults = weatherCityResults,
                            onSearchChange = { query ->
                                citySearchJob?.cancel()
                                if (query.trim().length < 2) {
                                    weatherCityResults = emptyList()
                                } else {
                                    citySearchJob = scope.launch {
                                        delay(250L)
                                        weatherCityResults = WeatherRepository.searchCities(query)
                                    }
                                }
                            },
                            onSelectCity = { city ->
                                weatherCityResults = emptyList()
                                scope.launch { prefsRepo.setSelectedWeatherCity(city) }
                            },
                            onDismiss = { showCityPickerDialog = false }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val state = shizukuBridge.statusState.value
        if (km != null && km.isKeyguardLocked && !km.isDeviceSecure) {
            if (state.isBinderAlive && state.isPermissionGranted) {
                lifecycleScope.launch {
                    shizukuBridge.dismissSystemKeyguardViaShizuku()
                }
            }
        }
    }

    override fun onDestroy() {
        mediaRepo.stopObserving()
        shizukuBridge.stopObserving()
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_SPACE,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_DPAD_CENTER -> {
                    unlockScreen()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    mediaRepo.togglePlayPause()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    mediaRepo.skipNext()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    mediaRepo.skipPrevious()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun unlockScreen() {
        val state = shizukuBridge.statusState.value
        if (state.isBinderAlive && state.isPermissionGranted) {
            lifecycleScope.launch {
                shizukuBridge.dismissSystemKeyguardViaShizuku()
                finish()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (km != null && km.isKeyguardLocked) {
                km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        finish()
                    }

                    override fun onDismissCancelled() {
                        finish()
                    }

                    override fun onDismissError() {
                        finish()
                    }
                })
            } else {
                finish()
            }
        } else {
            finish()
        }
    }
}

@Composable
internal fun SymbianLockScreen(
    mediaState: MediaPlayerState,
    livePositionMs: Long = mediaState.activeSession?.positionMs ?: 0L,
    currentLyrics: TrackLyricsData? = null,
    isShuffleEnabled: Boolean = false,
    repeatModeLabel: String = "Выкл",
    playbackSpeed: Float = 1.0f,
    notifications: List<HubNotificationItem>,
    @Suppress("UNUSED_PARAMETER") shizukuState: ShizukuState = ShizukuState(),
    wallpaperBitmap: ImageBitmap? = null,
    coverStyle: PlayerCoverStyle = PlayerCoverStyle.COVER,
    weatherReport: ConsensusWeatherReport? = null,
    selectedWeatherCity: WeatherCityResult = LauncherPreferencesRepository.DEFAULT_WEATHER_CITY,
    isWeatherLoading: Boolean = false,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekTo: (Long) -> Unit = {},
    onCyclePlayer: () -> Unit = {},
    onSelectSession: (String) -> Unit = {},
    onLikeStream: () -> Unit = {},
    onDislikeStream: () -> Unit = {},
    onStartMyWave: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onCycleRepeat: () -> Unit = {},
    onSetSpeed: (Float) -> Unit = {},
    onRefreshWeather: () -> Unit = {},
    onOpenWeatherForecast: () -> Unit = {},
    onOpenWeatherPicker: () -> Unit = {},
    onDismissNotification: (String) -> Unit = {},
    onUnlock: () -> Unit
) {
    val theme = LocalVistaTheme.current
    val lang = LocalAppLang.current

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val timeStr = remember(nowMs) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nowMs))
    }
    val secStr = remember(nowMs) {
        SimpleDateFormat(":ss", Locale.getDefault()).format(Date(nowMs))
    }
    val dateStr = remember(nowMs, lang) {
        SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag(lang.code)).format(Date(nowMs))
            .replaceFirstChar { it.uppercaseChar() }
    }

    val chatCount = notifications.count { it.subTab == HubSubTab.CHATS }
    val newsCount = notifications.count { it.subTab == HubSubTab.NEWS }
    val otherCount = notifications.count { it.subTab == HubSubTab.OTHER }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .testTag("lock_screen_root"),
        contentAlignment = Alignment.Center
    ) {
        wallpaperBitmap?.let { wallBmp ->
            Image(
                bitmap = wallBmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("lock_wallpaper_image")
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
            )
        }

        val isLandscape = maxWidth > maxHeight || maxWidth >= 640.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .then(if (isLandscape) Modifier else Modifier.widthIn(max = 620.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Left column in Landscape: Clock + Weather beside clock, Advanced Player Tile, Unlock CTA
                    Column(
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight()
                            .testTag("lock_clock_panel"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    LockClockDisplay(
                                        timeStr = timeStr,
                                        secStr = secStr,
                                        dateStr = dateStr,
                                        compact = true
                                    )
                                }
                                WeatherWidgetCard(
                                    report = weatherReport,
                                    selectedCity = selectedWeatherCity,
                                    isLoading = isWeatherLoading,
                                    compact = true,
                                    onRefresh = onRefreshWeather,
                                    onOpenForecast = onOpenWeatherForecast,
                                    onOpenCityPicker = onOpenWeatherPicker,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            LockMusicTile(
                                mediaState = mediaState,
                                livePositionMs = livePositionMs,
                                currentLyrics = currentLyrics,
                                isShuffleEnabled = isShuffleEnabled,
                                repeatModeLabel = repeatModeLabel,
                                playbackSpeed = playbackSpeed,
                                coverStyle = coverStyle,
                                lang = lang,
                                onTogglePlayPause = onTogglePlayPause,
                                onSkipPrevious = onSkipPrevious,
                                onSkipNext = onSkipNext,
                                onSeekTo = onSeekTo,
                                onCyclePlayer = onCyclePlayer,
                                onSelectSession = onSelectSession,
                                onLikeStream = onLikeStream,
                                onDislikeStream = onDislikeStream,
                                onStartMyWave = onStartMyWave,
                                onToggleShuffle = onToggleShuffle,
                                onCycleRepeat = onCycleRepeat,
                                onSetSpeed = onSetSpeed
                            )
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LockUnlockFooter(lang = lang, onUnlock = onUnlock)
                        }
                    }

                    // Right column in Landscape: Hub Counters + Swipeable Notifications List
                    Column(
                        modifier = Modifier
                            .weight(1.05f)
                            .fillMaxHeight()
                            .testTag("lock_landscape_notifications_column")
                    ) {
                        LockCountersRow(
                            chatCount = chatCount,
                            newsCount = newsCount,
                            otherCount = otherCount,
                            lang = lang
                        )
                        Spacer(Modifier.height(8.dp))
                        LockNotificationsList(
                            notifications = notifications,
                            lang = lang,
                            onDismissNotification = onDismissNotification,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }
            } else {
                // Portrait stack: Clock + Weather beside clock, Advanced Player Tile, Counters, Swipeable Notifications
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lock_clock_panel"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            LockClockDisplay(
                                timeStr = timeStr,
                                secStr = secStr,
                                dateStr = dateStr,
                                compact = false
                            )
                        }
                        WeatherWidgetCard(
                            report = weatherReport,
                            selectedCity = selectedWeatherCity,
                            isLoading = isWeatherLoading,
                            compact = true,
                            onRefresh = onRefreshWeather,
                            onOpenForecast = onOpenWeatherForecast,
                            onOpenCityPicker = onOpenWeatherPicker,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LockMusicTile(
                        mediaState = mediaState,
                        livePositionMs = livePositionMs,
                        currentLyrics = currentLyrics,
                        isShuffleEnabled = isShuffleEnabled,
                        repeatModeLabel = repeatModeLabel,
                        playbackSpeed = playbackSpeed,
                        coverStyle = coverStyle,
                        lang = lang,
                        onTogglePlayPause = onTogglePlayPause,
                        onSkipPrevious = onSkipPrevious,
                        onSkipNext = onSkipNext,
                        onSeekTo = onSeekTo,
                        onCyclePlayer = onCyclePlayer,
                        onSelectSession = onSelectSession,
                        onLikeStream = onLikeStream,
                        onDislikeStream = onDislikeStream,
                        onStartMyWave = onStartMyWave,
                        onToggleShuffle = onToggleShuffle,
                        onCycleRepeat = onCycleRepeat,
                        onSetSpeed = onSetSpeed
                    )
                }

                Spacer(Modifier.height(10.dp))

                LockCountersRow(
                    chatCount = chatCount,
                    newsCount = newsCount,
                    otherCount = otherCount,
                    lang = lang
                )

                Spacer(Modifier.height(10.dp))

                LockNotificationsList(
                    notifications = notifications,
                    lang = lang,
                    onDismissNotification = onDismissNotification,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = theme.divider)
                Spacer(Modifier.height(10.dp))

                LockUnlockFooter(lang = lang, onUnlock = onUnlock)
            }
        }
    }
}

@Composable
private fun LockClockDisplay(
    timeStr: String,
    secStr: String,
    dateStr: String,
    compact: Boolean
) {
    val theme = LocalVistaTheme.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = timeStr,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = if (compact) 40.sp else 46.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TextPrimary,
                maxLines = 1
            )
            Text(
                text = secStr,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = if (compact) 16.sp else 18.sp),
                color = theme.accent,
                modifier = Modifier.padding(bottom = if (compact) 5.dp else 7.dp),
                maxLines = 1
            )
        }
        Text(
            text = dateStr,
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LockMusicTile(
    mediaState: MediaPlayerState,
    livePositionMs: Long,
    currentLyrics: TrackLyricsData?,
    isShuffleEnabled: Boolean,
    repeatModeLabel: String,
    playbackSpeed: Float,
    coverStyle: PlayerCoverStyle,
    lang: AppLanguage,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCyclePlayer: () -> Unit,
    onSelectSession: (String) -> Unit,
    onLikeStream: () -> Unit,
    onDislikeStream: () -> Unit,
    onStartMyWave: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSetSpeed: (Float) -> Unit
) {
    val theme = LocalVistaTheme.current
    val active = mediaState.activeSession

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableLongStateOf(0L) }
    var isDeckExpanded by remember { mutableStateOf(false) }
    val duration = active?.durationMs ?: 0L
    val shownPos = if (isScrubbing) scrubMs else livePositionMs.coerceIn(0L, duration.coerceAtLeast(1L))
    val isLocalSession = active?.id == MediaControlRepository.LOCAL_SESSION_ID
    val isStreamingSession = active != null && !isLocalSession && MediaControlRepository.isStreamingPackage(active.packageName)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .symbianGlossyTile(bucket = 0, isFocused = isDeckExpanded)
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .testTag("lock_music_tile")
    ) {
        // Row 1: Artwork + Marquee Track/Artist/Player + Format badge + Expand + Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.clickable { isDeckExpanded = !isDeckExpanded }
            ) {
                AlbumArtThumb(
                    artwork = active?.artwork,
                    size = 44.dp,
                    coverStyle = coverStyle,
                    isPlaying = active?.isPlaying == true
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { isDeckExpanded = !isDeckExpanded }
            ) {
                Text(
                    text = active?.title ?: if (lang == AppLanguage.RU) "Музыка на паузе" else "Music Paused",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
                Text(
                    text = active?.artist?.ifBlank { active.appLabel }
                        ?: if (lang == AppLanguage.RU) "Плеер не активен" else "No active player",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
                if (active != null) {
                    val multi = if (mediaState.sessions.size > 1) {
                        val idx = mediaState.sessions.indexOfFirst { it.id == active.id } + 1
                        " ($idx/${mediaState.sessions.size})"
                    } else ""
                    val fmt = buildString {
                        if (active.formatLabel.isNotBlank()) append(" • ${active.formatLabel}")
                        if (active.bitrateKbps > 0) append(" ${active.bitrateKbps}k")
                    }
                    Text(
                        text = "⇆ ${active.appLabel}$multi$fmt",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (active.formatLabel.equals("FLAC", true)) CallIncomingGreen else MusicSourceAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (mediaState.sessions.size > 1) {
                    Box(
                        modifier = Modifier
                            .size(width = 28.dp, height = 34.dp)
                            .vistaAeroButton()
                            .clickable(onClick = onCyclePlayer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.SwapHoriz,
                            contentDescription = "Сменить плеер",
                            tint = MusicSourceAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(width = 30.dp, height = 34.dp)
                        .vistaAeroButton()
                        .clickable(onClick = onSkipPrevious),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.SkipPrevious,
                        contentDescription = "Назад",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(width = 34.dp, height = 34.dp)
                        .vistaAeroButton(isPrimary = true)
                        .clickable(onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (active?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (active?.isPlaying == true) "Пауза" else "Играть",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(width = 30.dp, height = 34.dp)
                        .vistaAeroButton()
                        .clickable(onClick = onSkipNext),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Вперёд",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(width = 28.dp, height = 34.dp)
                        .vistaAeroButton(isActive = isDeckExpanded)
                        .clickable { isDeckExpanded = !isDeckExpanded }
                        .testTag("lock_music_expand_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDeckExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Расширенный плеер",
                        tint = theme.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Row 2: Seekbar + timestamps
        if (active != null && duration > 0L) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = MediaControlRepository.formatTimeMs(shownPos),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = theme.accent
                )
                VistaSlider(
                    value = shownPos.toFloat(),
                    valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                    compact = true,
                    onValueChange = { v ->
                        isScrubbing = true
                        scrubMs = v.roundToLong().coerceIn(0L, duration)
                    },
                    onValueChangeFinished = {
                        val target = scrubMs
                        isScrubbing = false
                        onSeekTo(target)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lock_music_seek_slider")
                )
                Text(
                    text = MediaControlRepository.formatTimeMs(duration),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextMuted
                )
            }
        }

        // Expandable Poweramp Deck: Session switcher, Streaming/Local actions & Live Synced Lyrics preview
        AnimatedVisibility(visible = isDeckExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag("lock_music_expanded_deck"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (mediaState.sessions.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        mediaState.sessions.forEach { sess ->
                            val isSel = sess.id == active?.id
                            Box(
                                modifier = Modifier
                                    .vistaAeroButton(isPrimary = isSel, isActive = isSel)
                                    .clickable { onSelectSession(sess.id) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${if (sess.isPlaying) "▶ " else ""}${sess.appLabel}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = if (isSel) Color.White else TextPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Quick actions row: Streaming (Like / Dislike / My Wave) or Local Poweramp (Shuffle / Repeat / Speed)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isStreamingSession) {
                        Box(
                            modifier = Modifier
                                .vistaAeroButton()
                                .clickable(onClick = onLikeStream)
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text("♥ Нравится", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = CallIncomingGreen)
                        }
                        Box(
                            modifier = Modifier
                                .vistaAeroButton()
                                .clickable(onClick = onDislikeStream)
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text("✕ Дизлайк", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextSecondary)
                        }
                        Box(
                            modifier = Modifier
                                .vistaAeroButton(isPrimary = true)
                                .clickable(onClick = onStartMyWave)
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text("🌊 Волна", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White)
                        }
                    }
                    if (isLocalSession || !isStreamingSession) {
                        Box(
                            modifier = Modifier
                                .vistaAeroButton(isActive = isShuffleEnabled)
                                .clickable(onClick = onToggleShuffle)
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (isShuffleEnabled) "🔀 Вкл" else "🔀 Выкл",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (isShuffleEnabled) Color.White else TextPrimary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .vistaAeroButton()
                                .clickable(onClick = onCycleRepeat)
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "🔁 $repeatModeLabel",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = TextPrimary
                            )
                        }
                        val nextSpeed = when {
                            playbackSpeed < 0.9f -> 1.0f
                            playbackSpeed < 1.15f -> 1.25f
                            playbackSpeed < 1.4f -> 1.5f
                            else -> 0.85f
                        }
                        Box(
                            modifier = Modifier
                                .vistaAeroButton()
                                .clickable { onSetSpeed(nextSpeed) }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "${"%.2f".format(playbackSpeed)}x",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = theme.accent
                            )
                        }
                    }
                }

                // Live Synced Lyrics preview on LockScreen
                if (currentLyrics != null && currentLyrics.hasAnyLyrics) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .vistaInsetBox()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (currentLyrics.syncedLines.isNotEmpty()) {
                            val activeIdx = currentLyrics.syncedLines.indexOfLast { it.timeMs <= shownPos }
                            val center = activeIdx.coerceAtLeast(0)
                            val from = (center - 1).coerceAtLeast(0)
                            val to = (center + 1).coerceAtMost(currentLyrics.syncedLines.lastIndex)
                            for (idx in from..to) {
                                val line = currentLyrics.syncedLines[idx]
                                val isCur = idx == activeIdx
                                Text(
                                    text = line.text,
                                    style = if (isCur) MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    else MaterialTheme.typography.labelSmall,
                                    color = if (isCur) theme.accent else TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Text(
                                text = currentLyrics.plainText.lines().filter { it.isNotBlank() }.take(3).joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 2,
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
private fun LockCountersRow(
    chatCount: Int,
    newsCount: Int,
    otherCount: Int,
    lang: AppLanguage
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LockCounterChip(
            label = L10n.hubSub(lang, HubSubTab.CHATS),
            count = chatCount,
            iconRes = R.drawable.ic_symbian_messaging,
            modifier = Modifier.weight(1f)
        )
        LockCounterChip(
            label = L10n.hubSub(lang, HubSubTab.NEWS),
            count = newsCount,
            iconRes = R.drawable.ic_symbian_web_news,
            modifier = Modifier.weight(1f)
        )
        LockCounterChip(
            label = L10n.hubSub(lang, HubSubTab.OTHER),
            count = otherCount,
            iconRes = R.drawable.ic_symbian_tools,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LockNotificationsList(
    notifications: List<HubNotificationItem>,
    lang: AppLanguage,
    onDismissNotification: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    val dismissThresholdPx = with(LocalDensity.current) { 100.dp.toPx() }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (notifications.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vistaWindowFrame()
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (lang == AppLanguage.RU) {
                            "Нет непрочитанных уведомлений в Хабе"
                        } else {
                            "No unread Hub notifications"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }
        } else {
            items(notifications.take(10), key = { it.key }) { item ->
                var dragOffsetPx by remember(item.key) { mutableFloatStateOf(0f) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationX = dragOffsetPx
                            alpha = (1f - abs(dragOffsetPx) / (dismissThresholdPx * 1.4f)).coerceIn(0.25f, 1f)
                        }
                        .pointerInput(item.key) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (abs(dragOffsetPx) >= dismissThresholdPx) {
                                        onDismissNotification(item.key)
                                    } else {
                                        dragOffsetPx = 0f
                                    }
                                },
                                onDragCancel = { dragOffsetPx = 0f },
                                onHorizontalDrag = { _, dragAmount ->
                                    dragOffsetPx += dragAmount
                                }
                            )
                        }
                        .vistaWindowFrame()
                        .padding(10.dp)
                        .testTag("lock_notif_card_${item.key}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item.appIcon?.let { iconBmp ->
                        Image(
                            bitmap = iconBmp,
                            contentDescription = item.appName,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${item.appName} • ${item.title}",
                                style = MaterialTheme.typography.labelLarge,
                                color = theme.accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = item.timeFormatted,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                        Text(
                            text = item.bigText.ifBlank { item.text },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LockUnlockFooter(
    lang: AppLanguage,
    onUnlock: () -> Unit
) {
    val theme = LocalVistaTheme.current
    Text(
        text = if (lang == AppLanguage.RU) {
            "Нажмите ПРОБЕЛ или ENTER для разблокировки"
        } else {
            "Press SPACE or ENTER to unlock"
        },
        style = MaterialTheme.typography.labelSmall,
        color = MusicSourceAmber
    )
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .vistaAeroButton(isPrimary = true)
            .clickable(onClick = onUnlock)
            .testTag("lock_unlock_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.LockOpen,
            contentDescription = null,
            tint = theme.focusBorder
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (lang == AppLanguage.RU) "РАЗБЛОКИРОВАТЬ" else "UNLOCK",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
    }
}

@Composable
private fun LockCounterChip(
    label: String,
    count: Int,
    iconRes: Int,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    Row(
        modifier = modifier
            .vistaWindowFrame()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$label: $count",
            style = MaterialTheme.typography.labelMedium,
            color = if (count > 0) theme.accent else TextSecondary,
            maxLines = 1
        )
    }
}
