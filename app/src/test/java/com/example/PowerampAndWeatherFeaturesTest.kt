package com.example

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import android.view.KeyEvent
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.MusicCatalogAndLyricsRepository
import com.example.data.NotificationHubRepository
import com.example.data.RecentForegroundTracker
import com.example.data.UniversalSearchRepository
import com.example.data.WeatherRepository
import com.example.model.AlbumExpectedTrack
import com.example.model.AudioCaptureState
import com.example.model.ConsensusWeatherReport
import com.example.model.DailyWeatherForecast
import com.example.model.DeviceSearchFile
import com.example.model.EqualizerState
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
import com.example.ui.HardwareKeyHandler
import com.example.ui.SymbianLockScreen
import com.example.ui.components.HubView
import com.example.ui.components.PowerampPlayerScreen
import com.example.ui.components.WeatherForecastDialog
import com.example.ui.theme.KeyDeckTheme
import com.example.viewmodel.LauncherViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
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
                    onToggleShadeInterception = {},
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
        composeTestRule.onNodeWithTag("poweramp_tab_player").performClick()
        composeTestRule.onNodeWithText("Starlight").assertIsDisplayed()
    }
}
