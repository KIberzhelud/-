package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.os.Looper
import android.view.KeyEvent
import android.media.session.PlaybackState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.core.app.ApplicationProvider
import com.example.data.AndroidOptimizerManager
import com.example.data.AppPreviewCache
import com.example.data.AutoCloseManager
import com.example.data.KeyboardSearchMatcher
import com.example.data.LinuxPackagesRepository
import com.example.data.MediaControlRepository
import com.example.data.MediaGuardRuntime
import com.example.data.NotificationHubRepository
import com.example.data.RecentForegroundTracker
import com.example.data.ShizukuBridge
import com.example.data.SymbianIconMapper
import com.example.data.UniversalSearchRepository
import com.example.data.WallpaperThemeHelper
import com.example.data.WeatherRepository
import com.example.model.AppEntry
import com.example.model.AppIconMode
import com.example.model.AppLanguage
import com.example.model.CallsSubTab
import com.example.model.DeviceSearchFile
import com.example.model.HubCategory
import com.example.model.HubNotificationItem
import com.example.model.HubSubTab
import com.example.model.LinuxPackageKind
import com.example.model.LocalAudioTrack
import com.example.model.LockedChatThread
import com.example.model.MainTab
import com.example.model.MediaPlayerState
import com.example.model.MediaSessionUi
import com.example.model.MusicDrawerTab
import com.example.model.PlayerCoverStyle
import com.example.model.SearchResultKind
import com.example.model.UiThemeColor
import com.example.model.WallpaperPaletteData
import com.example.ui.HardwareKeyHandler
import com.example.ui.theme.BlueVistaTokens
import com.example.ui.theme.GrayVistaTokens
import com.example.ui.theme.buildVistaThemeTokens
import com.example.viewmodel.LauncherViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SymbianAndSearchTest {

    private val trackedViewModels = mutableListOf<LauncherViewModel>()

    private fun createViewModel(app: Application): LauncherViewModel =
        LauncherViewModel(app).also { trackedViewModels.add(it) }

    @After
    fun tearDownViewModels() {
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
    fun `symbian icon mapper resolves kindred apps and ignores unrelated ones`() {
        assertEquals(R.drawable.ic_symbian_messaging, SymbianIconMapper.resolveSymbianIconRes("org.telegram.messenger", "Telegram"))
        assertEquals(R.drawable.ic_symbian_web_news, SymbianIconMapper.resolveSymbianIconRes("com.opera.browser", "Opera"))
        assertEquals(R.drawable.ic_symbian_files, SymbianIconMapper.resolveSymbianIconRes("com.lonelycatgames.Xplore", "X-plore"))
        assertEquals(R.drawable.ic_symbian_music, SymbianIconMapper.resolveSymbianIconRes("ru.yandex.music", "Яндекс Музыка"))
        assertEquals(R.drawable.ic_symbian_maps, SymbianIconMapper.resolveSymbianIconRes("ru.dublgis.dgismobile", "2ГИС"))
        assertEquals(R.drawable.ic_symbian_games, SymbianIconMapper.resolveSymbianIconRes("org.ppsspp.ppsspp", "PPSSPP"))
        assertEquals(R.drawable.ic_symbian_notes, SymbianIconMapper.resolveSymbianIconRes("md.obsidian", "Obsidian"))
        assertEquals(R.drawable.ic_symbian_shizuku, SymbianIconMapper.resolveSymbianIconRes("moe.shizuku.privileged.api", "Shizuku"))
        assertNull(SymbianIconMapper.resolveSymbianIconRes("com.unknown.randomvendor.app", "CustomGizmo"))
    }

    @Test
    fun `universal search finds local export files deletes them and formats sizes`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shizuku = ShizukuBridge(app)
        val repo = UniversalSearchRepository(app, shizuku)

        assertEquals("512 Б", UniversalSearchRepository.formatSize(512L))
        assertEquals("2 КБ", UniversalSearchRepository.formatSize(2048L))
        assertTrue(UniversalSearchRepository.formatSize(3L * 1024L * 1024L).contains("МБ"))

        val exportDir = File(app.cacheDir, "exports").apply { mkdirs() }
        val testFile = File(exportDir, "symbian_manifesto_draft.txt").apply {
            writeText("Nokia E61i Symbian Notes")
        }

        val found = repo.searchDevice("symbian_manifesto", emptySet())
        val match = found.firstOrNull { it.name == "symbian_manifesto_draft.txt" }
        assertNotNull(match)
        assertEquals(SearchResultKind.FILE, match?.kind)

        val deleted = repo.deleteFile(match!!)
        assertTrue(deleted)
        assertFalse(testFile.exists())
    }

    @Test
    fun `swipe music source cycles installed music apps and escape closes file context menu`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = createViewModel(app)
        pumpMainUntil { !viewModel.uiState.value.isLoadingApps }

        val field = LauncherViewModel::class.java.getDeclaredField("_allApps")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(viewModel) as MutableStateFlow<List<AppEntry>>
        flow.value = listOf(
            AppEntry("m1/1", "Yandex Music", "ru.yandex.music", "1", null, 'Y', false, HubCategory.MUSIC, 0, isSymbianIcon = true),
            AppEntry("m2/2", "AIMP", "com.aimp.player", "2", null, 'A', false, HubCategory.MUSIC, 1, isSymbianIcon = true)
        )
        pumpMainUntil { viewModel.uiState.value.musicApps.size == 2 }

        viewModel.swipeMusicSource(+1)
        pumpMainUntil { viewModel.uiState.value.statusToast?.contains("AIMP") == true }
        viewModel.swipeMusicSource(+1)
        pumpMainUntil { viewModel.uiState.value.statusToast?.contains("Yandex Music") == true }
        viewModel.swipeMusicSource(-1)
        pumpMainUntil { viewModel.uiState.value.statusToast?.contains("AIMP") == true }

        val dummyFile = DeviceSearchFile(
            id = "f1",
            name = "chapter.docx",
            displayPath = "/sdcard/Documents/chapter.docx",
            parentPath = "/sdcard/Documents",
            uriString = "file:///sdcard/Documents/chapter.docx",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            sizeBytes = 4096L,
            modifiedMs = 0L,
            kind = SearchResultKind.FILE
        )
        viewModel.openFileContextMenu(dummyFile)
        pumpMainUntil { viewModel.uiState.value.contextMenuFile?.id == "f1" }

        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE),
                viewModel, {}, {}
            )
        )
        pumpMainUntil { viewModel.uiState.value.contextMenuFile == null }
    }

    @Test
    fun `rename app and switch between original android and symbian icon catalog work end to end`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = createViewModel(app)
        pumpMainUntil { !viewModel.uiState.value.isLoadingApps }

        assertTrue(SymbianIconMapper.catalog.isNotEmpty())
        assertEquals(R.drawable.ic_symbian_games, SymbianIconMapper.resolveByKey("games"))

        val field = LauncherViewModel::class.java.getDeclaredField("_allApps")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(viewModel) as MutableStateFlow<List<AppEntry>>
        val target = AppEntry(
            id = "com.custom.demo/Main",
            label = "Zeta App",
            packageName = "com.custom.demo",
            activityName = "Main",
            icon = null,
            initialChar = 'Z',
            isPinned = false,
            hubCategory = HubCategory.UTILITY,
            colorBucket = 0
        )
        flow.value = listOf(target)
        pumpMainUntil { viewModel.uiState.value.allApps.any { it.id == target.id } }

        // Rename app to "Альфа" and verify label + initialChar + isRenamed
        viewModel.saveAppRename(target, "Альфа")
        pumpMainUntil {
            val found = viewModel.uiState.value.allApps.firstOrNull { it.id == target.id }
            found?.label == "Альфа" && found.isRenamed && found.initialChar == 'А'
        }

        // Switch icon to Symbian from catalog ("games")
        viewModel.setAppIconSymbian(target, "games")
        pumpMainUntil {
            val found = viewModel.uiState.value.allApps.firstOrNull { it.id == target.id }
            found?.iconMode == AppIconMode.SYMBIAN && found.isSymbianIcon
        }

        // Switch icon to Original Android
        viewModel.setAppIconOriginal(target)
        pumpMainUntil {
            val found = viewModel.uiState.value.allApps.firstOrNull { it.id == target.id }
            found?.iconMode == AppIconMode.ORIGINAL && !found.isSymbianIcon
        }

        // Reset rename & set icon back to Auto
        viewModel.resetAppRename(target)
        viewModel.setAppIconAuto(target)
        pumpMainUntil {
            val found = viewModel.uiState.value.allApps.firstOrNull { it.id == target.id }
            found?.label == "Zeta App" && !found.isRenamed && found.iconMode == AppIconMode.AUTO
        }
    }

    @Test
    fun `hub prioritizes old telegram clients and whatsapp above newer vk and email`() {
        val sample = listOf(
            HubNotificationItem(
                key = "gmail_new",
                packageName = "com.google.android.gm",
                appName = "Gmail",
                title = "New invoice",
                text = "Just arrived",
                postTime = 9_000_000L,
                timeFormatted = "12:00",
                subTab = HubSubTab.CHATS
            ),
            HubNotificationItem(
                key = "vk_new",
                packageName = "com.vkontakte.android",
                appName = "VK",
                title = "Feed update",
                text = "New comment",
                postTime = 8_900_000L,
                timeFormatted = "11:59",
                subTab = HubSubTab.CHATS
            ),
            HubNotificationItem(
                key = "outlook_new",
                packageName = "com.microsoft.office.outlook",
                appName = "Outlook",
                title = "Meeting",
                text = "Reminder",
                postTime = 8_800_000L,
                timeFormatted = "11:58",
                subTab = HubSubTab.CHATS
            ),
            HubNotificationItem(
                key = "mailru_new",
                packageName = "ru.mail.mailapp",
                appName = "Почта Mail",
                title = "Newsletter",
                text = "Fresh mail",
                postTime = 8_700_000L,
                timeFormatted = "11:57",
                subTab = HubSubTab.CHATS
            ),
            HubNotificationItem(
                key = "signal_mid",
                packageName = "org.thoughtcrime.securesms",
                appName = "Signal",
                title = "Alex",
                text = "Call me",
                postTime = 5_000_000L,
                timeFormatted = "10:15",
                subTab = HubSubTab.CHATS
            ),
            HubNotificationItem(
                key = "tg_old",
                packageName = "org.telegram.messenger",
                appName = "Telegram",
                title = "Channel",
                text = "Old post",
                postTime = 1_000_000L,
                timeFormatted = "08:00",
                subTab = HubSubTab.CHATS
            ),
            HubNotificationItem(
                key = "extera_old",
                packageName = "com.exteragram.messenger",
                appName = "exteraGram",
                title = "Dev chat",
                text = "Old ping",
                postTime = 1_200_000L,
                timeFormatted = "08:05",
                subTab = HubSubTab.CHATS
            ),
            HubNotificationItem(
                key = "wa_old",
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                title = "Family",
                text = "Photo",
                postTime = 1_100_000L,
                timeFormatted = "08:03",
                subTab = HubSubTab.CHATS
            ),
            HubNotificationItem(
                key = "ayu_old",
                packageName = "com.radolyn.ayugram",
                appName = "AyuGram",
                title = "Saved",
                text = "Draft",
                postTime = 900_000L,
                timeFormatted = "07:50",
                subTab = HubSubTab.CHATS
            )
        )

        val sorted = NotificationHubRepository.sortNotificationsByPriority(sample)
        val keys = sorted.map { it.key }

        // Tier 0 (TG/clients + WhatsApp sorted by postTime desc), then Tier 1 (Signal), then Tier 3 (Email/VK)
        assertEquals(
            listOf("extera_old", "wa_old", "tg_old", "ayu_old"),
            keys.subList(0, 4)
        )
        assertEquals("signal_mid", keys[4])
        assertEquals(
            listOf("gmail_new", "vk_new", "outlook_new", "mailru_new"),
            keys.subList(5, 9)
        )
    }

    @Test
    fun `inline music drawer toggles cleanly and local track search filters catalog`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = createViewModel(app)
        pumpMainUntil { !viewModel.uiState.value.isLoadingApps }

        assertFalse(viewModel.uiState.value.showMusicSheet)
        viewModel.toggleMusicDrawer()
        pumpMainUntil { viewModel.uiState.value.showMusicSheet }

        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE),
                viewModel, {}, {}
            )
        )
        pumpMainUntil { !viewModel.uiState.value.showMusicSheet }

        val mediaRepoField = LauncherViewModel::class.java.getDeclaredField("mediaRepo").apply {
            isAccessible = true
        }
        val mediaRepo = mediaRepoField.get(viewModel) as MediaControlRepository
        val tracksField = MediaControlRepository::class.java.getDeclaredField("_localTracks").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val tracksFlow = tracksField.get(mediaRepo) as MutableStateFlow<List<LocalAudioTrack>>
        tracksFlow.value = listOf(
            LocalAudioTrack("1", "Nokia Tune Classic", "Symbian Orchestra", "E61i", 18_000L, "file:///nokia.mp3"),
            LocalAudioTrack("2", "Aero Glass Startup", "Vista Audio", "Longhorn", 12_000L, "file:///vista.wav"),
            LocalAudioTrack("3", "Кино — Группа крови", "Виктор Цой", "1988", 280_000L, "file:///kino.flac")
        )
        pumpMainUntil { viewModel.uiState.value.localAudioTracks.size == 3 }

        viewModel.updateLocalTrackSearchQuery("vista")
        pumpMainUntil { viewModel.uiState.value.localAudioTracks.size == 1 }
        assertEquals("Aero Glass Startup", viewModel.uiState.value.localAudioTracks.first().title)

        viewModel.updateLocalTrackSearchQuery("цой")
        pumpMainUntil { viewModel.uiState.value.localAudioTracks.size == 1 && viewModel.uiState.value.localAudioTracks.first().id == "3" }
        assertEquals("3", viewModel.uiState.value.localAudioTracks.first().id)

        viewModel.updateLocalTrackSearchQuery("")
        pumpMainUntil { viewModel.uiState.value.localAudioTracks.size == 3 }
    }

    @Test
    fun `vista theme tokens adapt cleanly to blue gray and wallpaper glass palette`() {
        val blueNoWall = buildVistaThemeTokens(UiThemeColor.BLUE, null, hasWallpaper = false)
        assertFalse(blueNoWall.isGlassMode)
        assertEquals(BlueVistaTokens.background, blueNoWall.background)

        val grayNoWall = buildVistaThemeTokens(UiThemeColor.GRAY, null, hasWallpaper = false)
        assertFalse(grayNoWall.isGlassMode)
        assertEquals(GrayVistaTokens.surface, grayNoWall.surface)
        assertNotEquals(blueNoWall.background, grayNoWall.background)

        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(AndroidColor.rgb(20, 140, 70))
        val palette = WallpaperThemeHelper.extractPaletteFromBitmap(bmp)
        bmp.recycle()

        val adaptiveGlass = buildVistaThemeTokens(UiThemeColor.BLUE, palette, hasWallpaper = true)
        assertTrue(adaptiveGlass.isGlassMode)
        assertNotEquals(BlueVistaTokens.surface, adaptiveGlass.surface)
        assertNotEquals(BlueVistaTokens.accent, adaptiveGlass.accent)
    }

    @Test
    fun `auto close manager detects camera dialer utilities and respects active playback`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shizuku = ShizukuBridge(app)
        val manager = AutoCloseManager(app, shizuku)

        assertTrue(AutoCloseManager.isHeuristicOneTimeApp("com.android.camera2", "Camera"))
        assertTrue(AutoCloseManager.isHeuristicOneTimeApp("com.google.android.dialer", "Phone"))
        assertTrue(AutoCloseManager.isHeuristicOneTimeApp("com.sec.android.app.popupcalculator", "Calculator"))
        assertFalse(AutoCloseManager.isHeuristicOneTimeApp("org.telegram.messenger", "Telegram"))

        val fakeApps = listOf(
            AppEntry("c/1", "Камера", "com.oem.camera", "1", null, 'К'),
            AppEntry("d/1", "Телефон", "com.oem.dialer", "1", null, 'Т'),
            AppEntry("m/1", "Spotify", "com.spotify.music", "1", null, 'S')
        )
        val defaults = manager.discoverDefaultOneTimePackages(fakeApps)
        assertTrue(defaults.contains("com.oem.camera"))
        assertTrue(defaults.contains("com.oem.dialer"))
        assertFalse(defaults.contains("com.spotify.music"))

        val targets = manager.resolvePackagesToTerminate(
            configuredPackages = setOf("com.oem.camera", "com.spotify.music"),
            activeMediaPackages = setOf("com.spotify.music")
        )
        assertEquals(listOf("com.oem.camera"), targets)
    }

    @Test
    fun `viewmodel switches blue gray theme toggles autokill packages and changes music tabs`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = createViewModel(app)
        pumpMainUntil { !viewModel.uiState.value.isLoadingApps }

        assertEquals(UiThemeColor.BLUE, viewModel.uiState.value.uiThemeColor)
        viewModel.setUiThemeColor(UiThemeColor.GRAY)
        pumpMainUntil { viewModel.uiState.value.uiThemeColor == UiThemeColor.GRAY }

        viewModel.setUiThemeColor(UiThemeColor.BLUE)
        pumpMainUntil { viewModel.uiState.value.uiThemeColor == UiThemeColor.BLUE }

        val customPkg = "com.example.qrscanner"
        assertFalse(viewModel.uiState.value.autoKillPackages.contains(customPkg))
        viewModel.toggleAutoKillPackage(customPkg)
        pumpMainUntil { viewModel.uiState.value.autoKillPackages.contains(customPkg) }
        viewModel.toggleAutoKillPackage(customPkg)
        pumpMainUntil { !viewModel.uiState.value.autoKillPackages.contains(customPkg) }

        assertEquals(MusicDrawerTab.STREAMING, viewModel.uiState.value.musicDrawerTab)
        viewModel.selectMusicDrawerTab(MusicDrawerTab.LOCAL)
        pumpMainUntil { viewModel.uiState.value.musicDrawerTab == MusicDrawerTab.LOCAL }
        viewModel.selectMusicDrawerTab(MusicDrawerTab.STREAMING)
        pumpMainUntil { viewModel.uiState.value.musicDrawerTab == MusicDrawerTab.STREAMING }
    }

    @Test
    fun `f1 to f5 switch tabs open books toggle music drawer and shift f1 opens shortcuts`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = createViewModel(app)
        pumpMainUntil { !viewModel.uiState.value.isLoadingApps }

        assertEquals(MainTab.HOME, viewModel.uiState.value.activeTab)

        // F2 -> CALLS, second F2 -> toggles CallsSubTab
        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F2),
                viewModel, {}, {}
            )
        )
        pumpMainUntil { viewModel.uiState.value.activeTab == MainTab.CALLS }
        assertEquals(CallsSubTab.LOG, viewModel.uiState.value.callsSubTab)

        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F2),
                viewModel, {}, {}
            )
        )
        pumpMainUntil { viewModel.uiState.value.callsSubTab == CallsSubTab.CONTACTS }

        // F3 -> HUB, second F3 -> cycles HubSubTab
        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F3),
                viewModel, {}, {}
            )
        )
        pumpMainUntil { viewModel.uiState.value.activeTab == MainTab.HUB }
        assertEquals(HubSubTab.CHATS, viewModel.uiState.value.hubSubTab)

        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F3),
                viewModel, {}, {}
            )
        )
        pumpMainUntil { viewModel.uiState.value.hubSubTab == HubSubTab.NEWS }

        // F1 -> back to HOME
        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F1),
                viewModel, {}, {}
            )
        )
        pumpMainUntil { viewModel.uiState.value.activeTab == MainTab.HOME }

        // F4 -> opens Books/Writer mode, second F4 closes it
        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F4),
                viewModel, {}, {}
            )
        )
        pumpMainUntil { viewModel.uiState.value.isWriterModeOpen }

        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F4),
                viewModel, {}, {}
            )
        )
        pumpMainUntil { !viewModel.uiState.value.isWriterModeOpen }

        // F5 -> opens Music Drawer, Escape closes it
        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F5),
                viewModel, {}, {}
            )
        )
        pumpMainUntil { viewModel.uiState.value.showMusicSheet }
        HardwareKeyHandler.handleAndroidKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE),
            viewModel, {}, {}
        )
        pumpMainUntil { !viewModel.uiState.value.showMusicSheet }

        // Shift+F1 -> opens shortcuts dialog
        val shiftF1 = KeyEvent(
            0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F1, 0, KeyEvent.META_SHIFT_ON
        )
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(shiftF1, viewModel, {}, {}))
        pumpMainUntil { viewModel.uiState.value.showShortcutsDialog }
    }

    @Test
    fun `background media guard optimizer pip window detection and custom palette without wallpaper work`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shizuku = ShizukuBridge(app)
        val optimizer = AndroidOptimizerManager(app, shizuku)

        val ram = optimizer.readRamStats()
        assertTrue(ram.totalRamMb > 0)
        assertTrue(ram.usedRamPercent in 0..100)

        // Custom palette applies theme colors even when hasWallpaper is false
        val customPal = WallpaperPaletteData(
            dominantDarkArgb = 0xFF2B123F,
            accentArgb = 0xFFE070FF,
            highlightArgb = 0xFFF2AAFF
        )
        val themedNoWall = buildVistaThemeTokens(UiThemeColor.BLUE, customPal, hasWallpaper = false)
        assertFalse(themedNoWall.isGlassMode)
        assertNotEquals(BlueVistaTokens.surface, themedNoWall.surface)
        assertNotEquals(BlueVistaTokens.accent, themedNoWall.accent)

        RecentForegroundTracker.recordForeground("com.oem.camera", app.packageName)
        val recent = RecentForegroundTracker.drainVisited()
        assertTrue(recent.contains("com.oem.camera"))

        // Verify MediaGuardRuntime detects involuntary background pause vs PiP visible window pause vs user pause
        var detectedPkg: String? = null
        MediaGuardRuntime.isGuardEnabled = true
        MediaGuardRuntime.guardedPackages = setOf("com.google.android.youtube")
        MediaGuardRuntime.updateVisibleWindows(emptySet())
        MediaGuardRuntime.onUnexpectedPauseDetected = { detectedPkg = it }

        MediaGuardRuntime.recordUserPlay("com.google.android.youtube")
        MediaGuardRuntime.onSessionPlaybackState("com.google.android.youtube", PlaybackState.STATE_PLAYING)
        MediaGuardRuntime.onSessionPlaybackState("com.google.android.youtube", PlaybackState.STATE_PAUSED)
        assertEquals("com.google.android.youtube", detectedPkg)

        // If YouTube window is still visible (e.g. Picture-in-Picture), manual pause inside PiP is NOT overridden
        detectedPkg = null
        MediaGuardRuntime.updateVisibleWindows(setOf("com.google.android.youtube"))
        MediaGuardRuntime.onSessionPlaybackState("com.google.android.youtube", PlaybackState.STATE_PLAYING)
        MediaGuardRuntime.onSessionPlaybackState("com.google.android.youtube", PlaybackState.STATE_PAUSED)
        assertNull(detectedPkg)
        MediaGuardRuntime.updateVisibleWindows(emptySet())

        detectedPkg = null
        MediaGuardRuntime.onSessionPlaybackState("com.google.android.youtube", PlaybackState.STATE_PLAYING)
        MediaGuardRuntime.recordUserPause("com.google.android.youtube")
        MediaGuardRuntime.onSessionPlaybackState("com.google.android.youtube", PlaybackState.STATE_PAUSED)
        assertNull(detectedPkg)
        MediaGuardRuntime.onUnexpectedPauseDetected = null

        val viewModel = createViewModel(app)
        pumpMainUntil { !viewModel.uiState.value.isLoadingApps }

        viewModel.toggleBackgroundSpoofPackage("com.google.android.youtube")
        pumpMainUntil {
            viewModel.uiState.value.backgroundSpoofPackages.contains("com.google.android.youtube") &&
                viewModel.uiState.value.backgroundSpoofEnabled
        }

        viewModel.optimizeAndroidSystem()
        pumpMainUntil {
            viewModel.uiState.value.optimizationStats.lastCleanupSummary != null
        }
    }

    @Test
    fun `wildcard search google news routing weather consensus and deb metadata work`() {
        // 1. Wildcard file matching (*apk, *.docx, .flac)
        assertEquals("apk", UniversalSearchRepository.parseWildcardExtension("*apk"))
        assertEquals("docx", UniversalSearchRepository.parseWildcardExtension("*.DOCX"))
        assertEquals("flac", UniversalSearchRepository.parseWildcardExtension(".flac"))
        assertNull(UniversalSearchRepository.parseWildcardExtension("music"))
        assertTrue(UniversalSearchRepository.matchesFileName("KeyDeck-release.apk", "*apk"))
        assertFalse(UniversalSearchRepository.matchesFileName("notes.txt", "*apk"))
        assertTrue(UniversalSearchRepository.matchesFileName("chapter_01.docx", "*.docx"))
        assertTrue(UniversalSearchRepository.matchesFileName("kino.flac", ".flac"))

        // 2. Google Discover / Google News route to HubSubTab.NEWS
        assertEquals(
            HubSubTab.NEWS,
            NotificationHubRepository.categorizeNotificationForTest(
                packageName = "com.google.android.apps.magazines",
                category = null,
                hasReply = false,
                hasMessagingStyle = false,
                title = "Google Новости",
                body = "Главные новости дня"
            )
        )
        assertEquals(
            HubSubTab.NEWS,
            NotificationHubRepository.categorizeNotificationForTest(
                packageName = "com.google.android.googlequicksearchbox",
                category = null,
                hasReply = false,
                hasMessagingStyle = false,
                title = "Discover",
                body = "Подборка новостей"
            )
        )

        // 3. Media session priority switches to playing session & distinguishes streaming vs local
        val pausedYm = MediaSessionUi(
            id = "s_ym", packageName = "ru.yandex.music", appLabel = "Яндекс Музыка",
            title = "Track 1", artist = "Artist", album = "", isPlaying = false,
            positionMs = 0L, durationMs = 1000L, playbackSpeed = 1f, lastUpdateElapsedMs = 0L
        )
        val playingYt = MediaSessionUi(
            id = "s_yt", packageName = "com.google.android.apps.youtube.music", appLabel = "YT Music",
            title = "Track 2", artist = "Band", album = "", isPlaying = true,
            positionMs = 0L, durationMs = 2000L, playbackSpeed = 1f, lastUpdateElapsedMs = 0L
        )
        assertEquals(
            "s_yt",
            MediaControlRepository.selectPreferredSessionId(
                sessions = listOf(pausedYm, playingYt),
                currentSelectedId = "s_ym"
            )
        )
        assertTrue(MediaControlRepository.isStreamingPackage("ru.yandex.music"))
        assertFalse(MediaControlRepository.isStreamingPackage("com.aimp.player"))

        // 4. Multi-source Weather consensus parsing & averaging
        val openMeteoJson = """
            {"current":{"temperature_2m":12.0,"apparent_temperature":10.0,"relative_humidity_2m":70,"weather_code":1,"wind_speed_10m":15.0},
             "hourly":{"time":["2026-11-19T12:00"],"temperature_2m":[12.0],"precipitation_probability":[10],"weather_code":[1]},
             "daily":{"time":["2026-11-19"],"temperature_2m_min":[6.0],"temperature_2m_max":[14.0],"weather_code":[1],"wind_speed_10m_max":[18.0]}}
        """.trimIndent()
        val metNoJson = """
            {"properties":{"timeseries":[{"data":{"instant":{"details":{"air_temperature":14.0,"wind_speed":5.0,"relative_humidity":80.0}},"next_1_hours":{"summary":{"symbol_code":"fair_day"}}}}]}}
        """.trimIndent()
        val wttrJson = """
            {"current_condition":[{"temp_C":"16","windspeedKmph":"21","humidity":"60","lang_ru":[{"value":"Ясно"}]}]}
        """.trimIndent()

        val omParsed = WeatherRepository.parseOpenMeteoJson(openMeteoJson)
        val metParsed = WeatherRepository.parseMetNorwayJson(metNoJson)
        val wttrParsed = WeatherRepository.parseWttrJson(wttrJson)
        val consensus = WeatherRepository.buildConsensusReport("Москва", omParsed, metParsed, wttrParsed)
        assertNotNull(consensus)
        assertEquals(3, consensus?.sources?.size)
        // Average of 12, 14, 16 is 14°C; wind avg of 15, 18 (5 m/s * 3.6), 21 is 18 km/h; humidity avg of 70, 80, 60 is 70%
        assertEquals(14, consensus?.tempC)
        assertEquals(18, consensus?.windKmh)
        assertEquals(70, consensus?.humidityPercent)

        // 5. Linux .deb AR archive metadata inspection
        val app = ApplicationProvider.getApplicationContext<Application>()
        val linuxRepo = LinuxPackagesRepository(app, ShizukuBridge(app))
        val debFile = File(app.cacheDir, "neovim_0.9.5_arm64.deb").apply {
            writeText("!<arch>\nPackage: neovim\nVersion: 0.9.5\nArchitecture: arm64\nDescription: Modern Vim editor\n")
        }
        val debEntry = linuxRepo.parsePackageFile(debFile, LinuxPackageKind.DEB)
        assertEquals("neovim", debEntry.packageName)
        assertEquals("0.9.5", debEntry.version)
        assertEquals("arm64", debEntry.architecture)
        assertEquals("Modern Vim editor", debEntry.description)
        debFile.delete()

        // 6. RecentForegroundTracker snapshot & pinned retention
        RecentForegroundTracker.clearHistoryExcept(emptySet())
        RecentForegroundTracker.recordForeground("com.whatsapp", "com.example")
        RecentForegroundTracker.recordForeground("com.android.settings", "com.example")
        val snap = RecentForegroundTracker.snapshotRecentHistory().map { it.first }
        assertTrue(snap.contains("com.whatsapp"))
        assertTrue(snap.contains("com.android.settings"))
        RecentForegroundTracker.clearHistoryExcept(setOf("com.whatsapp"))
        val afterClear = RecentForegroundTracker.snapshotRecentHistory().map { it.first }
        assertTrue(afterClear.contains("com.whatsapp"))
        assertFalse(afterClear.contains("com.android.settings"))
    }

    @Test
    fun `bb10 recents ctrl r escape writer space launch language scale and cover style work`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = createViewModel(app)
        pumpMainUntil { !viewModel.uiState.value.isLoadingApps }

        // Language, UI Scale & Player Cover Style settings
        viewModel.setAppLanguage(AppLanguage.EN)
        viewModel.setUiScale(1.15f)
        viewModel.setPlayerCoverStyle(PlayerCoverStyle.DISC)
        pumpMainUntil {
            viewModel.uiState.value.appLanguage == AppLanguage.EN &&
                viewModel.uiState.value.uiScale == 1.15f &&
                viewModel.uiState.value.playerCoverStyle == PlayerCoverStyle.DISC
        }

        // Ctrl+R toggles BB10 Active Frames Recents, Esc closes it
        val ctrlR = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_R, 0, KeyEvent.META_CTRL_ON)
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(ctrlR, viewModel, {}, {}))
        pumpMainUntil { viewModel.uiState.value.showRecentsOverlay }

        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE),
                viewModel, {}, {}
            )
        )
        pumpMainUntil { !viewModel.uiState.value.showRecentsOverlay }

        // F4 opens Writer Studio and Escape closes it
        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F4),
                viewModel, {}, {}
            )
        )
        pumpMainUntil { viewModel.uiState.value.isWriterModeOpen }
        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE),
                viewModel, {}, {}
            )
        )
        pumpMainUntil { !viewModel.uiState.value.isWriterModeOpen }

        // F3 cycles all 4 Hub sub-tabs: CHATS -> NEWS -> OTHER -> SETTINGS -> CHATS
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F3), viewModel, {}, {}))
        pumpMainUntil { viewModel.uiState.value.activeTab == MainTab.HUB && viewModel.uiState.value.hubSubTab == HubSubTab.CHATS }
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F3), viewModel, {}, {}))
        pumpMainUntil { viewModel.uiState.value.hubSubTab == HubSubTab.NEWS }
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F3), viewModel, {}, {}))
        pumpMainUntil { viewModel.uiState.value.hubSubTab == HubSubTab.OTHER }
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F3), viewModel, {}, {}))
        pumpMainUntil { viewModel.uiState.value.hubSubTab == HubSubTab.SETTINGS }
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F3), viewModel, {}, {}))
        pumpMainUntil { viewModel.uiState.value.hubSubTab == HubSubTab.CHATS }
    }

    @Test
    fun `niagara side rail filters to matching initial letter apps only and toggles off cleanly`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = createViewModel(app)
        pumpMainUntil { !viewModel.uiState.value.isLoadingApps }

        val field = LauncherViewModel::class.java.getDeclaredField("_allApps")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(viewModel) as MutableStateFlow<List<AppEntry>>
        flow.value = listOf(
            AppEntry("a1/1", "AIMP", "com.aimp.player", "1", null, 'A', false, HubCategory.MUSIC, 0),
            AppEntry("t1/1", "Telegram", "org.telegram.messenger", "1", null, 'T', false, HubCategory.CHAT, 1),
            AppEntry("t2/2", "Termux", "com.termux", "2", null, 'T', false, HubCategory.UTILITY, 2),
            AppEntry("y1/1", "YouTube", "com.google.android.youtube", "1", null, 'Y', false, HubCategory.NEWS, 3)
        )
        pumpMainUntil { viewModel.uiState.value.filteredApps.size == 4 }

        // Scrubbing 'T' filters grid strictly to ONLY 'T' apps (Telegram, Termux)
        viewModel.onAlphabetScrub('T')
        pumpMainUntil {
            val filtered = viewModel.uiState.value.filteredApps
            filtered.size == 2 && filtered.all { it.initialChar == 'T' }
        }

        // Releasing finger after scrub keeps 'T' filter active so user can tap a matching app
        viewModel.onAlphabetScrub(null)
        pumpMainUntil {
            viewModel.uiState.value.activeScrubLetter == null &&
                viewModel.uiState.value.highlightedLetter == 'T' &&
                viewModel.uiState.value.filteredApps.size == 2
        }

        // Tapping the same letter 'T' toggles filter off and restores all apps
        viewModel.jumpToLetter('T')
        pumpMainUntil {
            viewModel.uiState.value.highlightedLetter == null &&
                viewModel.uiState.value.filteredApps.size == 4
        }

        // Tapping 'A' filters only AIMP, and switching tab clears the letter filter
        viewModel.jumpToLetter('A')
        pumpMainUntil { viewModel.uiState.value.filteredApps.map { it.label } == listOf("AIMP") }

        viewModel.selectTab(MainTab.CALLS)
        pumpMainUntil {
            viewModel.uiState.value.highlightedLetter == null &&
                viewModel.uiState.value.filteredApps.size == 4
        }
    }

    @Test
    fun `niagara rail supports cyrillic latin translit and 2d workspaces plus locked single chat work`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = createViewModel(app)
        pumpMainUntil { !viewModel.uiState.value.isLoadingApps }

        assertTrue(KeyboardSearchMatcher.matchesInitialWithTranslit("Telegram", 'T', 'Т'))
        assertTrue(KeyboardSearchMatcher.matchesInitialWithTranslit("Авито", 'А', 'A'))

        // 1. Ctrl+M opens AND second Ctrl+M closes music drawer
        val ctrlM = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_M, 0, KeyEvent.META_CTRL_ON)
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(ctrlM, viewModel, {}, {}))
        pumpMainUntil { viewModel.uiState.value.showMusicSheet }
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(ctrlM, viewModel, {}, {}))
        pumpMainUntil { !viewModel.uiState.value.showMusicSheet }

        // 2. Shift+F1 / F12 / Ctrl+/ open AND close shortcuts dialog
        val shiftF1 = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F1, 0, KeyEvent.META_SHIFT_ON)
        val f12 = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F12)
        val ctrlSlash = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SLASH, 0, KeyEvent.META_CTRL_ON)
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(shiftF1, viewModel, {}, {}))
        pumpMainUntil { viewModel.uiState.value.showShortcutsDialog }
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(shiftF1, viewModel, {}, {}))
        pumpMainUntil { !viewModel.uiState.value.showShortcutsDialog }

        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(f12, viewModel, {}, {}))
        pumpMainUntil { viewModel.uiState.value.showShortcutsDialog }
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(ctrlSlash, viewModel, {}, {}))
        pumpMainUntil { !viewModel.uiState.value.showShortcutsDialog }

        // 3. Ctrl+R toggles BB10 Recents open AND closed
        val ctrlR = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_R, 0, KeyEvent.META_CTRL_ON)
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(ctrlR, viewModel, {}, {}))
        pumpMainUntil { viewModel.uiState.value.showRecentsOverlay }
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(ctrlR, viewModel, {}, {}))
        pumpMainUntil { !viewModel.uiState.value.showRecentsOverlay }

        // 4. Ctrl+N toggles HUB <-> HOME cleanly
        val ctrlN = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_N, 0, KeyEvent.META_CTRL_ON)
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(ctrlN, viewModel, {}, {}))
        pumpMainUntil { viewModel.uiState.value.activeTab == MainTab.HUB }
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(ctrlN, viewModel, {}, {}))
        pumpMainUntil { viewModel.uiState.value.activeTab == MainTab.HOME }

        // 5. AppPreviewCache stores thumbnails and refreshRecentFrames attaches them
        val sampleApp = AppEntry("t1/1", "Telegram", "org.telegram.messenger", "1", null, 'T', false, HubCategory.CHAT, 0)
        val appsField = LauncherViewModel::class.java.getDeclaredField("_allApps").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val appsFlow = appsField.get(viewModel) as MutableStateFlow<List<AppEntry>>
        appsFlow.value = listOf(sampleApp)
        pumpMainUntil { viewModel.uiState.value.allApps.any { it.packageName == "org.telegram.messenger" } }

        val bmp = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(AndroidColor.rgb(30, 120, 200))
        AppPreviewCache.put("org.telegram.messenger", bmp.asImageBitmap())
        RecentForegroundTracker.recordForeground("org.telegram.messenger", app.packageName)
        viewModel.refreshRecentFrames()
        pumpMainUntil {
            viewModel.uiState.value.recentFrames.any {
                it.packageName == "org.telegram.messenger" && it.previewBitmap != null
            }
        }
        AppPreviewCache.remove("org.telegram.messenger")

        // 6. Locked single chat opens and Esc closes cleanly back to tiles
        val thread = LockedChatThread(
            key = "chat:1",
            packageName = "org.telegram.messenger",
            appName = "Telegram",
            contactName = "Алексей",
            messages = emptyList(),
            canReplyInline = true,
            supportsMediaReply = false,
            lastTimestamp = 1000L,
            timeFormatted = "12:00"
        )
        viewModel.openLockedChat(thread)
        pumpMainUntil { viewModel.uiState.value.lockedChatThread?.contactName == "Алексей" }
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE), viewModel, {}, {}))
        pumpMainUntil { viewModel.uiState.value.lockedChatThread == null }

        // 7. 2D Multi-workspace creation, navigation, Escape to (0,0), and deletion
        viewModel.createWorkspaceWithApp(sampleApp)
        pumpMainUntil {
            !viewModel.uiState.value.workspaceGrid.isOnHome &&
                viewModel.uiState.value.workspaceGrid.activeCell.packageName == "org.telegram.messenger"
        }
        val createdX = viewModel.uiState.value.workspaceGrid.activeX
        val createdY = viewModel.uiState.value.workspaceGrid.activeY
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE), viewModel, {}, {}))
        pumpMainUntil { viewModel.uiState.value.workspaceGrid.isOnHome }
        viewModel.deleteWorkspace(createdX, createdY)
        pumpMainUntil { viewModel.uiState.value.workspaceGrid.cells.none { it.x == createdX && it.y == createdY } }
    }
}
