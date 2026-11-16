package com.example

import android.app.Application
import android.os.Looper
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.example.data.ShizukuBridge
import com.example.data.SymbianIconMapper
import com.example.data.UniversalSearchRepository
import com.example.model.AppEntry
import com.example.model.AppIconMode
import com.example.model.DeviceSearchFile
import com.example.model.HubCategory
import com.example.model.SearchResultKind
import com.example.ui.HardwareKeyHandler
import com.example.viewmodel.LauncherViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        pumpMainUntil { viewModel.uiState.value.statusToast?.contains("Yandex Music") == true }
        viewModel.swipeMusicSource(+1)
        pumpMainUntil { viewModel.uiState.value.statusToast?.contains("AIMP") == true }
        viewModel.swipeMusicSource(-1)
        pumpMainUntil { viewModel.uiState.value.statusToast?.contains("Yandex Music") == true }

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
}
