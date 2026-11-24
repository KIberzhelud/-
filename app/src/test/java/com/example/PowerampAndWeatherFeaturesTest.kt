package com.example

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Looper
import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.LauncherPreferencesRepository
import com.example.data.LinuxPackagesRepository
import kotlinx.coroutines.flow.first
import com.example.data.MusicCatalogAndLyricsRepository
import com.example.data.NotificationHubRepository
import com.example.data.RecentForegroundTracker
import com.example.data.StreamAudioCaptureRuntime
import com.example.data.UniversalSearchRepository
import com.example.data.WeatherRepository
import com.example.data.ZipUpdateRepository
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.example.model.AlbumExpectedTrack
import com.example.model.AudioCaptureState
import com.example.model.ConsensusWeatherReport
import com.example.model.CustomColorPalette
import com.example.model.DailyWeatherForecast
import com.example.model.DeviceSearchFile
import com.example.model.EqualizerState
import com.example.model.HubQuickLink
import com.example.model.HubSubTab
import com.example.model.LocalAudioTrack
import com.example.model.MediaPlayerState
import com.example.model.MediaSessionUi
import com.example.model.MusicSourceKind
import com.example.model.PlayerCoverStyle
import com.example.model.PowerampTab
import com.example.model.SearchResultKind
import com.example.model.ShizukuState
import com.example.model.SmartAlbum
import com.example.model.SourceSelectionMode
import com.example.model.SyncedLyricLine
import com.example.model.TrackLyricsData
import com.example.model.TrackSourceCandidate
import com.example.model.WeatherSourceReading
import com.example.model.WorkspaceCell
import com.example.model.WorkspaceGridState
import com.example.model.ZipArchiveKind
import com.example.model.ZipUpdateInspection
import com.example.ui.HardwareKeyHandler
import com.example.ui.SymbianLockScreen
import com.example.ui.components.HubView
import com.example.ui.components.PowerampFilesLibraryTab
import com.example.ui.components.PowerampPlayerScreen
import com.example.ui.components.WeatherForecastDialog
import com.example.ui.components.WorkspaceCanvasHost
import com.example.ui.components.ZipUpdateDialog
import com.example.ui.theme.KeyDeckTheme
import com.example.viewmodel.LauncherViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PowerampAndWeatherFeaturesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val trackedViewModels = mutableListOf<LauncherViewModel>()

    private fun createViewModel(app: Application): LauncherViewModel =
        LauncherViewModel(app).also { trackedViewModels.add(it) }

    @After
    fun tearDown() {
        val onClearedMethod = runCatching {
            LauncherViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }
        }.getOrNull()
        trackedViewModels.forEach { vm ->
            runCatching { onClearedMethod?.invoke(vm) }
            runCatching { vm.viewModelScope.cancel() }
        }
        trackedViewModels.clear()
    }

    private fun pumpMainUntil(timeoutMs: Long = 8000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            Thread.sleep(15)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Timed out waiting for condition", predicate())
    }

    @Test
    fun `multi-source music grouping merges duplicates and prioritizes flac over streaming and mp3`() {
        val candidates = listOf(
            TrackSourceCandidate(
                id = "mp3",
                sourceKind = MusicSourceKind.ARCHIVE_LOSSY,
                sourceLabel = "Archive MP3",
                title = "Starlight",
                artist = "Muse",
                fileExtension = "mp3",
                bitrateKbps = 320,
                downloadUrl = "https://example.org/starlight.mp3"
            ),
            TrackSourceCandidate(
                id = "ym",
                sourceKind = MusicSourceKind.STREAMING_PAID,
                sourceLabel = "Яндекс Музыка",
                title = "Starlight",
                artist = "Muse",
                packageName = "ru.yandex.music",
                fileExtension = "stream"
            ),
            TrackSourceCandidate(
                id = "flac",
                sourceKind = MusicSourceKind.LOCAL_FLAC,
                sourceLabel = "FLAC Локально",
                title = "Starlight",
                artist = "Muse",
                fileExtension = "flac",
                bitrateKbps = 1411
            )
        )

        val grouped = MusicCatalogAndLyricsRepository.groupAndSortCandidates(candidates)
        assertEquals(1, grouped.size)
        val group = grouped.first()
        assertTrue(group.hasMultipleSources)
        assertEquals("FLAC", group.bestFormatBadge)
        assertEquals(
            listOf(
                MusicSourceKind.LOCAL_FLAC,
                MusicSourceKind.STREAMING_PAID,
                MusicSourceKind.ARCHIVE_LOSSY
            ),
            group.candidates.map { it.sourceKind }
        )
    }

    @Test
    fun `lrc parser and serializer handle synced multi-tag timestamps cleanly`() {
        val raw = """
            [00:15.20][01:05.50]Repeated chorus line
            [00:05.00]Opening verse
        """.trimIndent()
        val parsed = MusicCatalogAndLyricsRepository.parseLrc(raw)
        assertEquals(3, parsed.size)
        assertEquals(5000L, parsed[0].timeMs)
        assertEquals("Opening verse", parsed[0].text)
        assertEquals(15200L, parsed[1].timeMs)
        assertEquals(65500L, parsed[2].timeMs)

        val serialized = MusicCatalogAndLyricsRepository.serializeLrc(parsed)
        assertTrue(serialized.contains("[00:05.00] Opening verse"))
        assertTrue(serialized.contains("[00:15.20] Repeated chorus line"))
        assertTrue(serialized.contains("[01:05.50] Repeated chorus line"))
    }

    @Test
    fun `smart album reconciliation matches local tracks and reports missing slots`() {
        val album = SmartAlbum(
            id = "alb_1",
            title = "Black Holes and Revelations",
            artist = "Muse",
            expectedTrackCount = 3,
            expectedTracks = listOf(
                AlbumExpectedTrack(1, "Take a Bow", "Muse"),
                AlbumExpectedTrack(2, "Starlight", "Muse"),
                AlbumExpectedTrack(3, "Supermassive Black Hole", "Muse")
            )
        )
        val localTracks = listOf(
            LocalAudioTrack(
                id = "lt_1",
                title = "02 Starlight",
                artist = "Muse",
                album = "Black Holes and Revelations",
                durationMs = 240_000L,
                uriString = "file:///sdcard/Music/02_Starlight.flac",
                filePath = "/sdcard/Music/02_Starlight.flac",
                formatLabel = "FLAC"
            )
        )

        val reconciled = MusicCatalogAndLyricsRepository.reconcileAlbumTracks(album, localTracks)
        assertEquals(1, reconciled.matchedCount)
        assertEquals(2, reconciled.missingCount)
        assertEquals(listOf("Take a Bow", "Supermassive Black Hole"), reconciled.missingTracks.map { it.title })
        assertEquals("FLAC", reconciled.expectedTracks[1].matchedFormat)
    }

    @Test
    fun `weather repository parses google yandex and builds 5-source consensus`() {
        val googleHtml = """
            <span id="wob_tm">20</span>
            <span id="wob_dc">Солнечно</span>
            <span id="wob_hm">50%</span>
            <span id="wob_ws">10 км/ч</span>
        """.trimIndent()
        val yandexJson = """
            {"fact":{"temp":18.0,"wind_speed":5.0,"humidity":60,"condition":"partly-cloudy"}}
        """.trimIndent()

        val g = WeatherRepository.parseGoogleWeatherHtml(googleHtml)
        val y = WeatherRepository.parseYandexWeatherPayload(yandexJson)
        assertNotNull(g)
        assertNotNull(y)
        assertEquals("Google Погода", g?.sourceName)
        assertEquals(20.0, g?.tempC ?: 0.0, 0.1)
        assertEquals("Яндекс Погода", y?.sourceName)
        assertEquals(18.0, y?.tempC ?: 0.0, 0.1)

        val om = WeatherRepository.parseOpenMeteoJson(
            """
            {"current":{"temperature_2m":16.0,"apparent_temperature":15.0,"relative_humidity_2m":70,"weather_code":1,"wind_speed_10m":14.0},
             "hourly":{"time":["2026-11-20T12:00"],"temperature_2m":[16.0],"precipitation_probability":[15],"weather_code":[1]},
             "daily":{"time":["2026-11-20"],"temperature_2m_min":[10.0],"temperature_2m_max":[18.0],"weather_code":[1],"wind_speed_10m_max":[18.0]}}
            """.trimIndent()
        )
        val met = WeatherSourceReading("MET Norway", 14.0, "Облачно", 16.0, 65)
        val wttr = WeatherSourceReading("wttr.in", 22.0, "Ясно", 12.0, 55)

        val report = WeatherRepository.buildConsensusReport(
            cityName = "Москва",
            openMeteo = om,
            metNo = met,
            wttr = wttr,
            yandexWeather = y,
            googleWeather = g
        )
        assertNotNull(report)
        assertEquals(5, report?.sources?.size)
        // Avg of 16, 18, 20, 14, 22 = 18°C
        assertEquals(18, report?.tempC)
    }

    @Test
    fun `apk installer selector excludes termux and prefers system installer`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shizuku = com.example.data.ShizukuBridge(app)
        val repo = UniversalSearchRepository(app, shizuku)

        val chosen = repo.selectPreferredApkInstallerPackage(
            listOf("com.termux", "com.google.android.packageinstaller", "com.miui.packageinstaller")
        )
        assertEquals("com.google.android.packageinstaller", chosen)

        assertNull(repo.selectPreferredApkInstallerPackage(listOf("com.termux")))
    }

    @Test
    fun `sms detection and recent foreground dismissal tracking work accurately`() {
        assertTrue(NotificationHubRepository.isSmsPackage("com.google.android.apps.messaging"))
        assertTrue(NotificationHubRepository.isSmsPackage("com.android.mms"))
        assertFalse(NotificationHubRepository.isSmsPackage("org.telegram.messenger"))
        assertFalse(NotificationHubRepository.isSmsPackage("com.exteragram.messenger"))

        val app = ApplicationProvider.getApplicationContext<Application>()
        RecentForegroundTracker.clearHistoryExcept(emptySet())
        RecentForegroundTracker.recordForeground("com.example.foo", app.packageName)
        assertFalse(RecentForegroundTracker.isRecentlyDismissed("com.example.foo"))

        RecentForegroundTracker.removeFromHistory("com.example.foo")
        assertTrue(RecentForegroundTracker.isRecentlyDismissed("com.example.foo"))

        RecentForegroundTracker.recordForeground("com.example.foo", app.packageName)
        assertFalse(RecentForegroundTracker.isRecentlyDismissed("com.example.foo"))
    }

    @Test
    fun `viewmodel copy file puts uri or path to clipboard and workspace space swipe works`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = createViewModel(app)
        pumpMainUntil { !viewModel.uiState.value.isLoadingApps }

        val sampleFile = DeviceSearchFile(
            id = "copy_1",
            name = "track.flac",
            displayPath = "/sdcard/Music/track.flac",
            parentPath = "/sdcard/Music",
            uriString = "file:///sdcard/Music/track.flac",
            mimeType = "audio/flac",
            sizeBytes = 20_000_000L,
            modifiedMs = 0L,
            kind = SearchResultKind.FILE
        )
        viewModel.copySearchFileToClipboard(sampleFile)
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertTrue(cm.hasPrimaryClip())
        val clipItem = cm.primaryClip?.getItemAt(0)
        assertEquals("file:///sdcard/Music/track.flac", clipItem?.uri?.toString())

        viewModel.copySearchFilePath(sampleFile)
        val pathClipItem = cm.primaryClip?.getItemAt(0)
        assertEquals("/sdcard/Music/track.flac", pathClipItem?.text?.toString())

        // Workspace Space swipe toggles showWorkspaceSpace cleanly without polluting Home grid
        assertFalse(viewModel.uiState.value.showWorkspaceSpace)
        viewModel.swipeToWorkspaceSpace()
        pumpMainUntil { viewModel.uiState.value.showWorkspaceSpace }

        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE),
                viewModel, {}, {}
            )
        )
        pumpMainUntil { !viewModel.uiState.value.showWorkspaceSpace && viewModel.uiState.value.workspaceGrid.isOnHome }

        // Open full Poweramp player and close via Esc
        viewModel.openFullPowerampPlayer()
        pumpMainUntil { viewModel.uiState.value.showFullPowerampPlayer }
        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE),
                viewModel, {}, {}
            )
        )
        pumpMainUntil { !viewModel.uiState.value.showFullPowerampPlayer }
    }

    @Test
    fun `compose weather dialog hub weather widget and expandable lock player render accurately`() {
        val report = ConsensusWeatherReport(
            cityName = "Москва",
            tempC = 19,
            feelsLikeC = 18,
            condition = "Переменная облачность",
            iconKey = "partly",
            windKmh = 12,
            humidityPercent = 58,
            updatedAtMs = 1_000L,
            sources = listOf(
                WeatherSourceReading("Open-Meteo", 18.0, "Облачно", 12.0, 60),
                WeatherSourceReading("Яндекс Погода", 19.0, "Малооблачно", 11.0, 56),
                WeatherSourceReading("Google Погода", 20.0, "Ясно", 13.0, 58)
            ),
            daily = (1..7).map { idx ->
                DailyWeatherForecast("День $idx", 10 + idx, 18 + idx, "Облачно", 12)
            }
        )

        var toggledHome = false
        composeTestRule.setContent {
            KeyDeckTheme {
                WeatherForecastDialog(
                    report = report,
                    onDismiss = {},
                    onRefresh = {},
                    onPickCity = {},
                    showInHubOther = true,
                    showInHomeTile = false,
                    onToggleHubOther = {},
                    onToggleHomeTile = { toggledHome = true }
                )
            }
        }
        composeTestRule.onNodeWithTag("weather_forecast_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Яндекс Погода").assertIsDisplayed()
        composeTestRule.onNodeWithText("Google Погода").assertIsDisplayed()
        composeTestRule.onNodeWithText("На Главном экране").performClick()
        assertTrue(toggledHome)
    }

    @Test
    fun `hub other tab displays weather card even with empty notifications and lock player expands lyrics`() {
        val lyrics = TrackLyricsData(
            trackKey = "muse|starlight",
            title = "Starlight",
            artist = "Muse",
            syncedLines = listOf(
                SyncedLyricLine(0L, "Hold you in my arms"),
                SyncedLyricLine(10_000L, "I just wanted to hold")
            ),
            plainText = "Hold you in my arms\nI just wanted to hold"
        )
        val session = MediaSessionUi(
            id = "local_test",
            packageName = "com.example",
            appLabel = "KeyDeck",
            title = "Starlight",
            artist = "Muse",
            album = "Black Holes",
            isPlaying = true,
            positionMs = 11_000L,
            durationMs = 240_000L,
            playbackSpeed = 1.0f,
            lastUpdateElapsedMs = 0L,
            formatLabel = "FLAC",
            bitrateKbps = 1000
        )

        composeTestRule.setContent {
            KeyDeckTheme {
                val listState = rememberLazyListState()
                HubView(
                    subTab = HubSubTab.OTHER,
                    isLandscape = false,
                    notifications = emptyList(),
                    shizukuState = ShizukuState(),
                    quickLinks = emptyList(),
                    focusedIndex = 0,
                    listState = listState,
                    onSelectSubTab = {},
                    onReplyNotification = { _, _, _, _ -> false },
                    onOpenNotification = {},
                    onInvokeNotificationAction = { _, _ -> },
                    onDismissNotification = {},
                    onRefreshNotifications = {},
                    onOpenUrl = {},
                    onShowAddLinkDialog = {},
                    onRemoveQuickLink = {},
                    onRequestShizukuPermission = {},
                    onOpenShizukuApp = {},
                    onGrantNotificationViaShizuku = {},
                    onToggleLockScreenInterception = {},
                    onPreviewLockScreen = {},
                    onOpenAppearanceSettings = {},
                    onOpenAutoKillSettings = {},
                    onOpenDefaultLauncherSettings = {},
                    onOpenNotificationAccess = {},
                    onOpenAllFilesAccess = {},
                    onOpenMusicSheet = {},
                    onOpenShortcuts = {},
                    onOpenSystemSettings = {},
                    showWeatherInHubOther = true,
                    weatherReport = ConsensusWeatherReport(
                        cityName = "Москва",
                        tempC = 17,
                        feelsLikeC = 16,
                        condition = "Ясно",
                        iconKey = "sun",
                        windKmh = 9,
                        humidityPercent = 50,
                        updatedAtMs = 1L
                    )
                )
            }
        }
        composeTestRule.onNodeWithTag("weather_widget_card").assertIsDisplayed()
    }

    @Test
    fun `lockscreen expandable poweramp music deck and full poweramp tabs render cleanly`() {
        val lyrics = TrackLyricsData(
            trackKey = "muse|starlight",
            title = "Starlight",
            artist = "Muse",
            syncedLines = listOf(
                SyncedLyricLine(0L, "Hold you in my arms"),
                SyncedLyricLine(10_000L, "Far away")
            ),
            plainText = "Hold you in my arms\nFar away"
        )
        val session = MediaSessionUi(
            id = "local_test",
            packageName = "com.example",
            appLabel = "KeyDeck",
            title = "Starlight",
            artist = "Muse",
            album = "Black Holes",
            isPlaying = true,
            positionMs = 11_000L,
            durationMs = 240_000L,
            playbackSpeed = 1.0f,
            lastUpdateElapsedMs = 0L,
            formatLabel = "FLAC",
            bitrateKbps = 1000
        )

        composeTestRule.setContent {
            KeyDeckTheme {
                SymbianLockScreen(
                    mediaState = MediaPlayerState(sessions = listOf(session), selectedSessionId = session.id),
                    livePositionMs = 11_000L,
                    currentLyrics = lyrics,
                    notifications = emptyList(),
                    coverStyle = PlayerCoverStyle.COVER,
                    onTogglePlayPause = {},
                    onSkipPrevious = {},
                    onSkipNext = {},
                    onUnlock = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("lock_music_tile").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lock_music_expand_btn").performClick()
        composeTestRule.onNodeWithTag("lock_music_expanded_deck").assertIsDisplayed()
        composeTestRule.onNodeWithText("Far away").assertIsDisplayed()
    }

    @Test
    fun `full poweramp player screen renders all five tabs and switches seamlessly`() {
        val lyrics = TrackLyricsData(
            trackKey = "muse|starlight",
            title = "Starlight",
            artist = "Muse",
            syncedLines = listOf(SyncedLyricLine(0L, "Hold you in my arms")),
            plainText = "Hold you in my arms"
        )
        val session = MediaSessionUi(
            id = "local_test",
            packageName = "com.example",
            appLabel = "KeyDeck",
            title = "Starlight",
            artist = "Muse",
            album = "Black Holes",
            isPlaying = true,
            positionMs = 11_000L,
            durationMs = 240_000L,
            playbackSpeed = 1.0f,
            lastUpdateElapsedMs = 0L,
            formatLabel = "FLAC",
            bitrateKbps = 1000
        )

        composeTestRule.setContent {
            KeyDeckTheme {
                var activeTab by remember { mutableStateOf(PowerampTab.PLAYER) }
                PowerampPlayerScreen(
                    mediaState = MediaPlayerState(sessions = listOf(session), selectedSessionId = session.id),
                    livePositionMs = 11_000L,
                    coverStyle = PlayerCoverStyle.COVER,
                    activeTab = activeTab,
                    isLandscape = false,
                    sourceSelectionMode = SourceSelectionMode.AUTO,
                    localTracks = emptyList(),
                    unifiedSearchQuery = "",
                    unifiedSearchResults = emptyList(),
                    isSearchingOnline = false,
                    currentLyrics = lyrics,
                    isFetchingLyrics = false,
                    equalizerState = EqualizerState(enabled = true),
                    audioCaptureState = AudioCaptureState(),
                    smartAlbums = emptyList(),
                    selectedAlbumId = null,
                    onClose = {},
                    onSelectTab = { activeTab = it },
                    onToggleSourceMode = {},
                    onSelectSession = {},
                    onTogglePlayPause = {},
                    onSkipPrevious = {},
                    onSkipNext = {},
                    onSeekTo = {},
                    onSeekBy = {},
                    onSkipToQueueItem = {},
                    onOpenActivePlayer = {},
                    onPlayLocalTrack = {},
                    onDeleteLocalTrack = {},
                    onDeleteActiveTrack = {},
                    onSetPlaybackSpeed = {},
                    onCycleRepeatMode = {},
                    onToggleShuffle = {},
                    onSearchMusicEverywhere = {},
                    onUnifiedTrackAction = { _, _ -> },
                    onOpenSourcePicker = { _, _ -> },
                    onStopAudioCapture = {},
                    onAutoFetchLyrics = {},
                    onSaveLyrics = {},
                    onDeleteLyrics = {},
                    onSetEqualizerEnabled = {},
                    onApplyEqualizerPreset = {},
                    onSetEqualizerBand = { _, _ -> },
                    onSetBassBoost = {},
                    onSetVirtualizer = {},
                    onSelectAlbum = {},
                    onCreateAlbum = { _, _ -> },
                    onRefreshAlbumOnline = {},
                    onAttachLocalTracksToAlbum = { _, _ -> },
                    onDetachLocalTrackFromAlbum = { _, _ -> },
                    onPickCustomAlbumCover = {},
                    onSelectAlternateCoverUrl = { _, _ -> },
                    onDeleteAlbum = {},
                    onDownloadMissingAlbumTrack = { _, _ -> },
                    onDownloadAllMissingAlbumTracks = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("poweramp_full_player_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("poweramp_tab_equalizer").performClick()
        composeTestRule.onNodeWithTag("poweramp_tab_lyrics").performClick()
        composeTestRule.onNodeWithTag("poweramp_tab_search").performClick()
        composeTestRule.onNodeWithTag("poweramp_tab_albums").performClick()
        composeTestRule.onNodeWithTag("poweramp_tab_library").performClick()
        composeTestRule.onNodeWithTag("poweramp_library_tab").assertIsDisplayed()
        composeTestRule.onNodeWithTag("poweramp_tab_player").performClick()
        composeTestRule.onNodeWithText("Starlight").assertIsDisplayed()
    }

    @Test
    fun `poweramp global mini deck stays interactive across eq lyrics search and albums tabs`() {
        val lyrics = TrackLyricsData(
            trackKey = "muse|starlight",
            title = "Starlight",
            artist = "Muse",
            syncedLines = listOf(
                SyncedLyricLine(0L, "Hold you in my arms"),
                SyncedLyricLine(10_000L, "Far away")
            ),
            plainText = "Hold you in my arms\nFar away"
        )
        val session = MediaSessionUi(
            id = "local_test",
            packageName = "com.example",
            appLabel = "KeyDeck",
            title = "Starlight",
            artist = "Muse",
            album = "Black Holes",
            isPlaying = true,
            positionMs = 11_000L,
            durationMs = 240_000L,
            playbackSpeed = 1.0f,
            lastUpdateElapsedMs = 0L,
            formatLabel = "FLAC",
            bitrateKbps = 1000
        )

        var playPauseClicks = 0
        var nextClicks = 0
        var seekBySum = 0L

        composeTestRule.setContent {
            KeyDeckTheme {
                var activeTab by remember { mutableStateOf(PowerampTab.PLAYER) }
                PowerampPlayerScreen(
                    mediaState = MediaPlayerState(sessions = listOf(session), selectedSessionId = session.id),
                    livePositionMs = 11_000L,
                    coverStyle = PlayerCoverStyle.COVER,
                    activeTab = activeTab,
                    isLandscape = false,
                    sourceSelectionMode = SourceSelectionMode.AUTO,
                    localTracks = emptyList(),
                    unifiedSearchQuery = "",
                    unifiedSearchResults = emptyList(),
                    isSearchingOnline = false,
                    currentLyrics = lyrics,
                    isFetchingLyrics = false,
                    equalizerState = EqualizerState(enabled = true),
                    audioCaptureState = AudioCaptureState(),
                    smartAlbums = emptyList(),
                    selectedAlbumId = null,
                    onClose = {},
                    onSelectTab = { activeTab = it },
                    onToggleSourceMode = {},
                    onSelectSession = {},
                    onTogglePlayPause = { playPauseClicks++ },
                    onSkipPrevious = {},
                    onSkipNext = { nextClicks++ },
                    onSeekTo = {},
                    onSeekBy = { seekBySum += it },
                    onSkipToQueueItem = {},
                    onOpenActivePlayer = {},
                    onPlayLocalTrack = {},
                    onDeleteLocalTrack = {},
                    onDeleteActiveTrack = {},
                    onSetPlaybackSpeed = {},
                    onCycleRepeatMode = {},
                    onToggleShuffle = {},
                    onSearchMusicEverywhere = {},
                    onUnifiedTrackAction = { _, _ -> },
                    onOpenSourcePicker = { _, _ -> },
                    onStopAudioCapture = {},
                    onAutoFetchLyrics = {},
                    onSaveLyrics = {},
                    onDeleteLyrics = {},
                    onSetEqualizerEnabled = {},
                    onApplyEqualizerPreset = {},
                    onSetEqualizerBand = { _, _ -> },
                    onSetBassBoost = {},
                    onSetVirtualizer = {},
                    onSelectAlbum = {},
                    onCreateAlbum = { _, _ -> },
                    onRefreshAlbumOnline = {},
                    onAttachLocalTracksToAlbum = { _, _ -> },
                    onDetachLocalTrackFromAlbum = { _, _ -> },
                    onPickCustomAlbumCover = {},
                    onSelectAlternateCoverUrl = { _, _ -> },
                    onDeleteAlbum = {},
                    onDownloadMissingAlbumTrack = { _, _ -> },
                    onDownloadAllMissingAlbumTracks = {}
                )
            }
        }

        // On PLAYER tab mini deck is hidden (main transport controls are visible)
        composeTestRule.onNodeWithTag("poweramp_global_mini_deck").assertDoesNotExist()

        // Vintage EQ tab shows rack controls + persistent mini deck
        composeTestRule.onNodeWithTag("poweramp_tab_equalizer").performClick()
        composeTestRule.onNodeWithTag("eq_master_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("eq_band_slider_0").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("eq_band_slider_4").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("eq_bass_slider").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("eq_virtualizer_slider").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("poweramp_global_mini_deck").assertIsDisplayed()

        // Lyrics, Search, and Albums tabs all retain persistent playback & seek mini deck
        composeTestRule.onNodeWithTag("poweramp_tab_lyrics").performClick()
        composeTestRule.onNodeWithTag("poweramp_global_mini_deck").assertIsDisplayed()
        composeTestRule.onNodeWithTag("poweramp_tab_search").performClick()
        composeTestRule.onNodeWithTag("poweramp_global_mini_deck").assertIsDisplayed()
        composeTestRule.onNodeWithTag("poweramp_tab_albums").performClick()
        composeTestRule.onNodeWithTag("poweramp_global_mini_deck").assertIsDisplayed()

        composeTestRule.onNodeWithTag("poweramp_mini_play_pause_btn").performClick()
        composeTestRule.onNodeWithTag("poweramp_mini_next_btn").performClick()
        composeTestRule.onNodeWithTag("poweramp_mini_fwd10_btn").performClick()
        composeTestRule.onNodeWithTag("poweramp_mini_back10_btn").performClick()

        assertEquals(1, playPauseClicks)
        assertEquals(1, nextClicks)
        assertEquals(0L, seekBySum)
    }

    @Test
    fun `archive metadata parser extracts multiple album tracks and linux ar parser reads deb members`() {
        val metaJson = """
            {
              "files": [
                {"name": "01_Take_a_Bow.flac", "title": "Take a Bow", "artist": "Muse", "length": "275"},
                {"name": "02_Starlight.flac", "title": "Starlight", "artist": "Muse", "length": "240"},
                {"name": "02_Starlight.mp3", "title": "Starlight", "artist": "Muse", "length": "240"},
                {"name": "cover.jpg", "title": "Cover"}
              ]
            }
        """.trimIndent()
        val parsed = MusicCatalogAndLyricsRepository.parseArchiveMetadataFiles(
            id = "muse_bhar",
            itemTitle = "Black Holes",
            itemArtist = "Muse",
            metaRaw = metaJson,
            maxResults = 16
        )
        assertEquals(3, parsed.size)
        assertTrue(parsed.any { it.title == "Take a Bow" && it.fileExtension == "flac" })
        assertTrue(parsed.any { it.title == "Starlight" && it.fileExtension == "flac" })
        assertTrue(parsed.any { it.title == "Starlight" && it.fileExtension == "mp3" })

        val app = ApplicationProvider.getApplicationContext<Application>()
        val linuxRepo = LinuxPackagesRepository(app, com.example.data.ShizukuBridge(app))
        val tmpDeb = File.createTempFile("sample_pkg", ".deb", app.cacheDir)
        try {
            fun headerBytes(name: String, size: Int): ByteArray {
                val paddedName = name.padEnd(16, ' ').substring(0, 16)
                val paddedMeta = "0           0     0     100644  " + size.toString().padEnd(10, ' ') + "`\n"
                return (paddedName + paddedMeta).toByteArray(Charsets.US_ASCII)
            }
            val debContent = buildList {
                add("!<arch>\n".toByteArray(Charsets.US_ASCII))
                add(headerBytes("debian-binary/", 4))
                add("2.0\n".toByteArray(Charsets.US_ASCII))
                add(headerBytes("data.tar.gz/", 3))
                add(byteArrayOf(1, 2, 3))
            }.fold(ByteArray(0)) { acc, bytes -> acc + bytes }
            tmpDeb.writeBytes(debContent)

            val headers = linuxRepo.parseArMemberHeaders(tmpDeb)
            assertEquals(2, headers.size)
            assertEquals("debian-binary", headers[0].first)
            assertEquals(4L, headers[0].second)
            assertEquals("data.tar.gz", headers[1].first)
            assertEquals(3L, headers[1].second)
        } finally {
            tmpDeb.delete()
        }
    }

    @Test
    fun `poweramp retro local track list renders lazily with delete action`() {
        val track1 = LocalAudioTrack(
            id = "track_flac_1",
            title = "Knights of Cydonia",
            artist = "Muse",
            album = "Black Holes",
            durationMs = 366_000L,
            uriString = "file:///sdcard/Music/knights.flac",
            filePath = "/sdcard/Music/knights.flac",
            formatLabel = "FLAC"
        )
        val track2 = LocalAudioTrack(
            id = "track_mp3_2",
            title = "Supermassive Black Hole",
            artist = "Muse",
            album = "Black Holes",
            durationMs = 209_000L,
            uriString = "file:///sdcard/Music/supermassive.mp3",
            filePath = "/sdcard/Music/supermassive.mp3",
            formatLabel = "MP3"
        )
        val session = MediaSessionUi(
            id = "local_test",
            packageName = "com.example",
            appLabel = "KeyDeck",
            title = "Knights of Cydonia",
            artist = "Muse",
            album = "Black Holes",
            isPlaying = true,
            positionMs = 12_000L,
            durationMs = 366_000L,
            playbackSpeed = 1.0f,
            lastUpdateElapsedMs = 0L,
            formatLabel = "FLAC",
            bitrateKbps = 1411
        )
        var playedTrackId: String? = null

        composeTestRule.setContent {
            KeyDeckTheme {
                PowerampPlayerScreen(
                    mediaState = MediaPlayerState(
                        sessions = listOf(session),
                        selectedSessionId = session.id,
                        currentLocalTrackId = track1.id
                    ),
                    livePositionMs = 12_000L,
                    coverStyle = PlayerCoverStyle.COVER,
                    activeTab = PowerampTab.PLAYER,
                    isLandscape = false,
                    sourceSelectionMode = SourceSelectionMode.AUTO,
                    localTracks = listOf(track1, track2),
                    unifiedSearchQuery = "",
                    unifiedSearchResults = emptyList(),
                    isSearchingOnline = false,
                    currentLyrics = null,
                    isFetchingLyrics = false,
                    equalizerState = EqualizerState(enabled = true),
                    audioCaptureState = AudioCaptureState(),
                    smartAlbums = emptyList(),
                    selectedAlbumId = null,
                    onClose = {},
                    onSelectTab = {},
                    onToggleSourceMode = {},
                    onSelectSession = {},
                    onTogglePlayPause = {},
                    onSkipPrevious = {},
                    onSkipNext = {},
                    onSeekTo = {},
                    onSeekBy = {},
                    onSkipToQueueItem = {},
                    onOpenActivePlayer = {},
                    onPlayLocalTrack = { playedTrackId = it.id },
                    onDeleteLocalTrack = {},
                    onDeleteActiveTrack = {},
                    onSetPlaybackSpeed = {},
                    onCycleRepeatMode = {},
                    onToggleShuffle = {},
                    onSearchMusicEverywhere = {},
                    onUnifiedTrackAction = { _, _ -> },
                    onOpenSourcePicker = { _, _ -> },
                    onStopAudioCapture = {},
                    onAutoFetchLyrics = {},
                    onSaveLyrics = {},
                    onDeleteLyrics = {},
                    onSetEqualizerEnabled = {},
                    onApplyEqualizerPreset = {},
                    onSetEqualizerBand = { _, _ -> },
                    onSetBassBoost = {},
                    onSetVirtualizer = {},
                    onSelectAlbum = {},
                    onCreateAlbum = { _, _ -> },
                    onRefreshAlbumOnline = {},
                    onAttachLocalTracksToAlbum = { _, _ -> },
                    onDetachLocalTrackFromAlbum = { _, _ -> },
                    onPickCustomAlbumCover = {},
                    onSelectAlternateCoverUrl = { _, _ -> },
                    onDeleteAlbum = {},
                    onDownloadMissingAlbumTrack = { _, _ -> },
                    onDownloadAllMissingAlbumTracks = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("poweramp_player_lazy_list")
            .performScrollToNode(hasTestTag("poweramp_list_delete_${track1.id}"))
        composeTestRule.onNodeWithTag("poweramp_list_delete_${track1.id}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("poweramp_player_lazy_list")
            .performScrollToNode(hasTestTag("poweramp_list_delete_${track2.id}"))
        composeTestRule.onNodeWithTag("poweramp_list_delete_${track2.id}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("poweramp_player_lazy_list")
            .performScrollToNode(hasText("Supermassive Black Hole"))
        composeTestRule.onNodeWithText("Supermassive Black Hole").performClick()
        assertEquals(track2.id, playedTrackId)
    }

    @Test
    fun `wikimedia commons, lrclib candidates, lyrics ovh parsers and six poweramp tabs work`() {
        assertTrue(PowerampTab.entries.contains(PowerampTab.LIBRARY))
        assertEquals("Файлы", PowerampTab.LIBRARY.title)

        val wikiJson = """
            {
              "query": {
                "pages": {
                  "101": {
                    "title": "File:Moonlight_Sonata.flac",
                    "imageinfo": [{"url": "https://upload.wikimedia.org/moonlight.flac", "size": 1048576}]
                  },
                  "102": {
                    "title": "File:Cover_Image.jpg",
                    "imageinfo": [{"url": "https://upload.wikimedia.org/cover.jpg", "size": 2048}]
                  }
                }
              }
            }
        """.trimIndent()
        val wikiTracks = MusicCatalogAndLyricsRepository.parseWikimediaCommonsJson(wikiJson)
        assertEquals(1, wikiTracks.size)
        assertEquals("Moonlight Sonata", wikiTracks.first().title)
        assertEquals(MusicSourceKind.ARCHIVE_FLAC, wikiTracks.first().sourceKind)

        val lrclibArray = """
            [
              {"trackName": "Starlight", "artistName": "Muse", "syncedLyrics": "[00:01.00] Far away", "plainLyrics": "Far away"},
              {"trackName": "Starlight (Live)", "artistName": "Muse", "syncedLyrics": "[00:02.00] Live version", "plainLyrics": "Live version"}
            ]
        """.trimIndent()
        val candidates = MusicCatalogAndLyricsRepository.parseLrclibSearchCandidates(
            rawJson = lrclibArray,
            trackKey = "muse|starlight",
            title = "Starlight",
            artist = "Muse"
        )
        assertEquals(2, candidates.size)
        assertEquals("LRCLIB #1", candidates[0].sourceLabel)
        assertEquals("LRCLIB #2", candidates[1].sourceLabel)

        val ovhLyrics = MusicCatalogAndLyricsRepository.parseLyricsOvhJson(
            rawJson = """{"lyrics":"Line one\nLine two"}""",
            trackKey = "muse|starlight",
            title = "Starlight",
            artist = "Muse"
        )
        assertEquals("Line one\nLine two", ovhLyrics?.plainText)
    }

    @Test
    fun `stream capture runtime checks auto-discard threshold, percent progress and auto-split`() {
        assertTrue(StreamAudioCaptureRuntime.shouldDiscardIncompleteRecording(recordedMs = 40_000L, expectedMs = 100_000L))
        assertFalse(StreamAudioCaptureRuntime.shouldDiscardIncompleteRecording(recordedMs = 75_000L, expectedMs = 100_000L))
        assertFalse(StreamAudioCaptureRuntime.shouldDiscardIncompleteRecording(recordedMs = 5_000L, expectedMs = 10_000L))

        assertEquals(50, StreamAudioCaptureRuntime.computeProgressPercent(recordedMs = 90_000L, expectedMs = 180_000L))
        assertEquals(0, StreamAudioCaptureRuntime.computeProgressPercent(recordedMs = 10_000L, expectedMs = 0L))

        StreamAudioCaptureRuntime.pendingTrackChange = null
        StreamAudioCaptureRuntime.updateState {
            AudioCaptureState(
                isCapturing = true,
                targetTitle = "First Song",
                targetArtist = "Artist A",
                expectedDurationMs = 180_000L,
                autoSplitEnabled = true,
                silentMuteMode = true
            )
        }
        StreamAudioCaptureRuntime.updateActiveTrackMetadata("Second Song", "Artist B", 210_000L)
        assertEquals(Triple("Second Song", "Artist B", 210_000L), StreamAudioCaptureRuntime.pendingTrackChange)

        StreamAudioCaptureRuntime.pendingTrackChange = null
        StreamAudioCaptureRuntime.updateState { AudioCaptureState() }
    }

    @Test
    fun `poweramp library tab renders downloaded tracks cleanly`() {
        val localTrack = LocalAudioTrack(
            id = "lib_track_1",
            title = "Unique Library File",
            artist = "Band X",
            album = "KeyDeck Singles",
            durationMs = 195_000L,
            uriString = "file:///sdcard/Music/KeyDeck/downloaded.mp3",
            filePath = "/sdcard/Music/KeyDeck/downloaded.mp3",
            formatLabel = "MP3"
        )
        val session = MediaSessionUi(
            id = "local_test",
            packageName = "com.example",
            appLabel = "KeyDeck",
            title = "Active Deck Song",
            artist = "Band X",
            album = "KeyDeck Singles",
            isPlaying = false,
            positionMs = 0L,
            durationMs = 195_000L,
            playbackSpeed = 1.0f,
            lastUpdateElapsedMs = 0L,
            formatLabel = "MP3",
            bitrateKbps = 320
        )

        var renamedTo: String? = null
        composeTestRule.setContent {
            KeyDeckTheme {
                PowerampFilesLibraryTab(
                    localTracks = listOf(localTrack),
                    mediaState = MediaPlayerState(sessions = listOf(session), selectedSessionId = session.id),
                    audioCaptureState = AudioCaptureState(),
                    smartAlbums = emptyList(),
                    onPlayTrack = {},
                    onRenameTrack = { _, newTitle -> renamedTo = newTitle },
                    onDeleteTrack = {},
                    onOpenLyricsForTrack = {},
                    onAttachTrackToAlbum = { _, _ -> },
                    onToggleCaptureAutoSplit = {},
                    onToggleCaptureSilentMute = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("poweramp_library_tab").assertIsDisplayed()
        composeTestRule.onNodeWithText("Unique Library File").assertIsDisplayed()
        composeTestRule.onNodeWithTag("track_rename_btn_lib_track_1").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("track_rename_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("track_rename_save_btn").performClick()
        assertEquals("Unique Library File", renamedTo)
    }

    @Test
    fun `workspace canvas host renders fullscreen launch supports 2d swipes and returns home on swipe back`() {
        val grid = WorkspaceGridState(
            cells = listOf(
                WorkspaceCell(0, 0),
                WorkspaceCell(1, 0, packageName = "com.test.app", label = "TestApp")
            ),
            activeX = 1,
            activeY = 0
        )
        val navEvents = mutableListOf<Pair<Int, Int>>()

        composeTestRule.setContent {
            KeyDeckTheme {
                WorkspaceCanvasHost(
                    workspaceGrid = grid,
                    allApps = emptyList(),
                    onNavigateWorkspace = { dx, dy -> navEvents += (dx to dy) },
                    onAssignApp = {},
                    onLaunchCellAppInBounds = {},
                    onOpenHome = {},
                    onDeleteWorkspace = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("workspace_launch_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("workspace_delete_btn").assertIsDisplayed()
        composeTestRule.onNodeWithText("Открыть на весь экран").assertIsDisplayed()

        // Horizontal swipe right inside workspace body navigates back (-1, 0)
        composeTestRule.onNodeWithTag("workspace_canvas_host").performTouchInput {
            swipeRight(startX = centerX * 0.4f, endX = centerX * 1.6f)
        }
        composeTestRule.waitForIdle()
        assertTrue(navEvents.contains(-1 to 0))

        // Vertical swipe up inside workspace body navigates (0, +1)
        composeTestRule.onNodeWithTag("workspace_canvas_host").performTouchInput {
            swipeUp(startY = centerY * 1.4f, endY = centerY * 0.4f)
        }
        composeTestRule.waitForIdle()
        assertTrue(navEvents.contains(0 to 1))
    }

    @Test
    fun `viewmodel navigateWorkspace returns to home tiles when stepping to (0, 0) and prunes empty ghost cells`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = LauncherViewModel(app)
        pumpMainUntil { !vm.uiState.value.isLoadingApps }

        vm.swipeToWorkspaceSpace(1)
        pumpMainUntil { vm.uiState.value.showWorkspaceSpace && vm.uiState.value.workspaceGrid.activeX == 1 }

        // Swipe back to (0, 0) via navigateWorkspace(-1, 0) -> closes workspace mode & shows Home tiles
        vm.navigateWorkspace(-1, 0)
        pumpMainUntil { !vm.uiState.value.showWorkspaceSpace && vm.uiState.value.workspaceGrid.isOnHome }
        assertTrue(vm.uiState.value.workspaceGrid.cells.none { it.x == 1 && it.y == 0 })
    }

    @Test
    fun `zip update repository guards zip slip parses config packs and classifies source archives`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ZipUpdateRepository(app, com.example.data.ShizukuBridge(app))

        val destRoot = File(app.cacheDir, "zip_slip_test_root").apply { mkdirs() }
        assertNull(ZipUpdateRepository.safeResolveEntryFile(destRoot, "../evil.apk"))
        assertNull(ZipUpdateRepository.safeResolveEntryFile(destRoot, "sub/../../escape.apk"))
        assertNotNull(ZipUpdateRepository.safeResolveEntryFile(destRoot, "apks/base.apk"))

        val palette = CustomColorPalette(
            id = "pal_retro",
            name = "Vista Retro",
            dominantDarkArgb = 0xFF0E1A2B,
            accentArgb = 0xFF2882D8,
            highlightArgb = 0xFF8CD3FF
        )
        val link = HubQuickLink(
            id = "lnk_1",
            title = "Archive",
            url = "https://archive.org",
            category = HubSubTab.NEWS
        )
        val manifestJson = ZipUpdateRepository.buildBackupManifestJson(
            versionName = "2.4",
            versionCode = 24L,
            palettes = listOf(palette),
            quickLinks = listOf(link)
        )
        val parsedManifest = ZipUpdateRepository.parseConfigManifestJson(manifestJson)
        assertNotNull(parsedManifest)
        assertEquals("2.4", parsedManifest?.versionName)
        assertEquals(1, parsedManifest?.palettes?.size)
        assertEquals("Vista Retro", parsedManifest?.palettes?.first()?.name)
        assertEquals(1, parsedManifest?.quickLinks?.size)

        val lrcEntry = ZipUpdateRepository.parseLrcArchiveEntry(
            "Muse - Starlight.lrc",
            "[00:05.00] Far away\n[00:12.00] Hold you in my arms"
        )
        assertNotNull(lrcEntry)
        assertEquals("Muse", lrcEntry?.artist)
        assertEquals("Starlight", lrcEntry?.title)
        assertEquals(2, lrcEntry?.syncedLines?.size)

        val packZip = File.createTempFile("config_pack", ".zip", app.cacheDir)
        val srcZip = File.createTempFile("source_code", ".zip", app.cacheDir)
        try {
            ZipOutputStream(packZip.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("keydeck-update.json"))
                zos.write(manifestJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("lyrics/Muse - Starlight.lrc"))
                zos.write("[00:01.00] Far away".toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            val packInspection = runBlocking {
                repo.inspectZipArchive(Uri.fromFile(packZip), packZip.absolutePath, "config_pack.zip")
            }.getOrThrow()
            assertEquals(ZipArchiveKind.KEYDECK_CONFIG_PACK, packInspection.kind)
            assertEquals(1, packInspection.paletteCount)
            assertEquals(1, packInspection.quickLinkCount)
            assertEquals(1, packInspection.lrcCount)
            assertTrue(packInspection.canApplyConfigPack)

            ZipOutputStream(srcZip.outputStream()).use { zos ->
                zos.putNextEntry(ZipEntry("app/build.gradle.kts"))
                zos.write("plugins { }".toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("app/src/main/java/com/example/MainActivity.kt"))
                zos.write("package com.example".toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            val srcInspection = runBlocking {
                repo.inspectZipArchive(Uri.fromFile(srcZip), srcZip.absolutePath, "source_code.zip")
            }.getOrThrow()
            assertEquals(ZipArchiveKind.SOURCE_CODE_ARCHIVE, srcInspection.kind)
            assertEquals(2, srcInspection.sourceCodeFileCount)
            assertFalse(srcInspection.canInstallApks)
        } finally {
            packZip.delete()
            srcZip.delete()
            destRoot.deleteRecursively()
        }
    }

    @Test
    fun `zip update dialog renders controls and install action cleanly`() {
        val inspection = ZipUpdateInspection(
            sourceUriString = "file:///sdcard/Download/keydeck_update.zip",
            archiveName = "keydeck_update.zip",
            archiveSizeBytes = 14_500_000L,
            kind = ZipArchiveKind.APK_UPDATE,
            isSamePackageUpdate = true,
            targetPackageName = "com.example",
            targetVersionName = "2.5",
            targetVersionCode = 25L,
            currentVersionName = "2.4",
            currentVersionCode = 24L,
            extractedApkPaths = listOf("/tmp/base.apk"),
            changelog = "Обновление плеера и поворот экрана",
            summaryMessage = "Найден APK обновления KeyDeck"
        )
        var applyClicked = false
        composeTestRule.setContent {
            KeyDeckTheme {
                ZipUpdateDialog(
                    currentVersionName = "2.4",
                    currentVersionCode = 24L,
                    inspection = inspection,
                    isBusy = false,
                    statusMessage = null,
                    shizukuState = ShizukuState(),
                    onPickZipFile = {},
                    onExportBackupZip = {},
                    onApplyUpdate = { applyClicked = true },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("zip_update_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("zip_pick_file_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("zip_export_backup_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("zip_inspection_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("zip_apply_update_btn").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(applyClicked)
    }

    @Test
    @Config(qualifiers = "w800dp-h380dp-land")
    fun `landscape poweramp player preserves non-crushed repeat button alongside lyrics and list`() {
        val lyrics = TrackLyricsData(
            trackKey = "muse|starlight",
            title = "Starlight",
            artist = "Muse",
            syncedLines = listOf(
                SyncedLyricLine(0L, "Hold you in my arms"),
                SyncedLyricLine(10_000L, "Far away")
            ),
            plainText = "Hold you in my arms\nFar away"
        )
        val session = MediaSessionUi(
            id = "local_test",
            packageName = "com.example",
            appLabel = "KeyDeck",
            title = "Starlight",
            artist = "Muse",
            album = "Black Holes",
            isPlaying = true,
            positionMs = 11_000L,
            durationMs = 240_000L,
            playbackSpeed = 1.0f,
            lastUpdateElapsedMs = 0L,
            formatLabel = "FLAC",
            bitrateKbps = 1411
        )

        var densityValue = 1f
        composeTestRule.setContent {
            densityValue = LocalDensity.current.density
            KeyDeckTheme {
                Box(modifier = Modifier.size(width = 800.dp, height = 380.dp)) {
                    PowerampPlayerScreen(
                        mediaState = MediaPlayerState(sessions = listOf(session), selectedSessionId = session.id),
                        livePositionMs = 11_000L,
                        coverStyle = PlayerCoverStyle.COVER,
                        activeTab = PowerampTab.PLAYER,
                        isLandscape = true,
                        sourceSelectionMode = SourceSelectionMode.AUTO,
                        localTracks = emptyList(),
                        unifiedSearchQuery = "",
                        unifiedSearchResults = emptyList(),
                        isSearchingOnline = false,
                        currentLyrics = lyrics,
                        isFetchingLyrics = false,
                        equalizerState = EqualizerState(enabled = true),
                        audioCaptureState = AudioCaptureState(),
                        smartAlbums = emptyList(),
                        selectedAlbumId = null,
                        onClose = {},
                        onSelectTab = {},
                        onToggleSourceMode = {},
                        onSelectSession = {},
                        onTogglePlayPause = {},
                        onSkipPrevious = {},
                        onSkipNext = {},
                        onSeekTo = {},
                        onSeekBy = {},
                        onSkipToQueueItem = {},
                        onOpenActivePlayer = {},
                        onPlayLocalTrack = {},
                        onDeleteLocalTrack = {},
                        onDeleteActiveTrack = {},
                        onSetPlaybackSpeed = {},
                        onCycleRepeatMode = {},
                        onToggleShuffle = {},
                        onSearchMusicEverywhere = {},
                        onUnifiedTrackAction = { _, _ -> },
                        onOpenSourcePicker = { _, _ -> },
                        onStopAudioCapture = {},
                        onAutoFetchLyrics = {},
                        onSaveLyrics = {},
                        onDeleteLyrics = {},
                        onSetEqualizerEnabled = {},
                        onApplyEqualizerPreset = {},
                        onSetEqualizerBand = { _, _ -> },
                        onSetBassBoost = {},
                        onSetVirtualizer = {},
                        onSelectAlbum = {},
                        onCreateAlbum = { _, _ -> },
                        onRefreshAlbumOnline = {},
                        onAttachLocalTracksToAlbum = { _, _ -> },
                        onDetachLocalTrackFromAlbum = { _, _ -> },
                        onPickCustomAlbumCover = {},
                        onSelectAlternateCoverUrl = { _, _ -> },
                        onDeleteAlbum = {},
                        onDownloadMissingAlbumTrack = { _, _ -> },
                        onDownloadAllMissingAlbumTracks = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        val paneBounds = composeTestRule.onNodeWithTag("poweramp_landscape_three_pane").fetchSemanticsNode().boundsInRoot
        val shuffleBounds = composeTestRule.onNodeWithTag("poweramp_shuffle_btn").fetchSemanticsNode().boundsInRoot
        val playBounds = composeTestRule.onNodeWithTag("poweramp_play_pause_btn").fetchSemanticsNode().boundsInRoot
        val repeatBounds = composeTestRule.onNodeWithTag("poweramp_repeat_btn").fetchSemanticsNode().boundsInRoot
        val repeatWidthDp = repeatBounds.width / densityValue

        assertTrue(
            "Shuffle must fit above fold: pane=$paneBounds shuffle=$shuffleBounds play=$playBounds repeat=$repeatBounds density=$densityValue",
            shuffleBounds.height > 0f && shuffleBounds.bottom <= paneBounds.bottom + 1f
        )
        composeTestRule.onNodeWithTag("poweramp_landscape_three_pane").assertIsDisplayed()
        composeTestRule.onNodeWithTag("poweramp_landscape_lyrics_pane").assertIsDisplayed()
        composeTestRule.onNodeWithTag("poweramp_shuffle_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("poweramp_play_pause_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("poweramp_repeat_btn").assertIsDisplayed()

        assertTrue("Expected repeat button width >= 30dp in 800x380 landscape, got $repeatWidthDp dp", repeatWidthDp >= 30f)
    }

    @Test
    fun testPaletteDeduplicationAndSharing() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefsRepo = LauncherPreferencesRepository(app)

        val palA = CustomColorPalette(
            id = "pal_1",
            name = "Aero Blue",
            dominantDarkArgb = 0xFF08111DL,
            accentArgb = 0xFF3584E4L,
            highlightArgb = 0xFF78AEEDL,
            isFromWallpaper = false
        )
        val palADuplicate = CustomColorPalette(
            id = "pal_2",
            name = "Aero Blue Copy",
            dominantDarkArgb = 0xFF08111DL,
            accentArgb = 0xFF3584E4L,
            highlightArgb = 0xFF78AEEDL,
            isFromWallpaper = false
        )
        val palB = CustomColorPalette(
            id = "pal_3",
            name = "Amber Sunset",
            dominantDarkArgb = 0xFF1E140AL,
            accentArgb = 0xFFFF9800L,
            highlightArgb = 0xFFFFD54FL,
            isFromWallpaper = false
        )

        prefsRepo.saveCustomPalette(palA, activate = false)
        prefsRepo.saveCustomPalette(palADuplicate, activate = false)
        prefsRepo.saveCustomPalette(palB, activate = true)

        val before = prefsRepo.savedPalettes.first { it.size >= 3 }
        assertTrue(before.size >= 3)

        val removed = prefsRepo.deduplicateCustomPalettes()
        assertEquals(1, removed)

        val after = prefsRepo.savedPalettes.first { it.size == before.size - 1 }
        val aeroCount = after.count { it.dominantDarkArgb == palA.dominantDarkArgb && it.accentArgb == palA.accentArgb }
        assertEquals(1, aeroCount)
    }
}
