package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.os.Looper
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.example.data.AutoCloseManager
import com.example.data.MediaControlRepository
import com.example.data.NotificationHubRepository
import com.example.data.ShizukuBridge
import com.example.data.SymbianIconMapper
import com.example.data.UniversalSearchRepository
import com.example.data.WallpaperThemeHelper
import com.example.model.AppEntry
import com.example.model.AppIconMode
import com.example.model.DeviceSearchFile
import com.example.model.HubCategory
import com.example.model.HubNotificationItem
import com.example.model.HubSubTab
import com.example.model.LocalAudioTrack
import com.example.model.MusicDrawerTab
import com.example.model.SearchResultKind
import com.example.model.UiThemeColor
import com.example.ui.HardwareKeyHandler
import com.example.ui.theme.BlueVistaTokens
import com.example.ui.theme.GrayVistaTokens
import com.example.ui.theme.buildVistaThemeTokens
import com.example.viewmodel.LauncherViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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

    private fun pumpMainUntil(timeoutMs: Long = 5000L, predicate: () -> Boolean) {
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
        val viewModel = LauncherViewModel(app)
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
        val viewModel = LauncherViewModel(app)
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
        val viewModel = LauncherViewModel(app)
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
        pumpMainUntil { viewModel.uiState.value.localAudioTracks.size == 1 }
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
        val viewModel = LauncherViewModel(app)
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

        assertEquals(MusicDrawerTab.QUEUE, viewModel.uiState.value.musicDrawerTab)
        viewModel.selectMusicDrawerTab(MusicDrawerTab.TELEGRAM)
        pumpMainUntil { viewModel.uiState.value.musicDrawerTab == MusicDrawerTab.TELEGRAM }
        viewModel.selectMusicDrawerTab(MusicDrawerTab.LOCAL)
        pumpMainUntil { viewModel.uiState.value.musicDrawerTab == MusicDrawerTab.LOCAL }
    }
}
