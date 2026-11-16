package com.example

import android.app.Application
import android.os.Looper
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.example.model.AppEntry
import com.example.model.HubCategory
import com.example.model.MainTab
import com.example.ui.HardwareKeyHandler
import com.example.viewmodel.LauncherViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KeyboardNavigationTest {

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
    fun `dpad left right up down navigates tiles in 2d and home auto search filters apps`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = LauncherViewModel(app)

        // Let init's refreshInstalledApps() finish so it doesn't overwrite our injected test list
        pumpMainUntil { !viewModel.uiState.value.isLoadingApps }

        val field = LauncherViewModel::class.java.getDeclaredField("_allApps")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(viewModel) as MutableStateFlow<List<AppEntry>>
        val testApps = listOf(
            AppEntry("a/1", "Alpha", "a", "1", null, 'A', false, HubCategory.UTILITY, 0),
            AppEntry("b/2", "Beta", "b", "2", null, 'B', false, HubCategory.UTILITY, 1),
            AppEntry("c/3", "Chrome", "c", "3", null, 'C', false, HubCategory.NEWS, 2),
            AppEntry("d/4", "Delta", "d", "4", null, 'D', false, HubCategory.UTILITY, 3),
            AppEntry("e/5", "Evernote", "e", "5", null, 'E', false, HubCategory.UTILITY, 0)
        )
        flow.value = testApps

        pumpMainUntil { viewModel.uiState.value.filteredApps.size == 5 }

        viewModel.isLandscapeLayout = false // 2 columns
        assertEquals(MainTab.HOME, viewModel.uiState.value.activeTab)
        assertEquals(0, viewModel.uiState.value.focusedAppIndex)

        // Right moves from 0 -> 1
        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT),
                viewModel, {}, {}
            )
        )
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, viewModel.uiState.value.focusedAppIndex)

        // Down moves by 2 columns from 1 -> 3
        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN),
                viewModel, {}, {}
            )
        )
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(3, viewModel.uiState.value.focusedAppIndex)

        // Left moves from 3 -> 2
        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT),
                viewModel, {}, {}
            )
        )
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, viewModel.uiState.value.focusedAppIndex)

        // Up moves by 2 columns from 2 -> 0
        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP),
                viewModel, {}, {}
            )
        )
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, viewModel.uiState.value.focusedAppIndex)

        // Auto-search on HOME filters immediately when typing 'c', 'h'
        val cEvent = object : KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C) {
            override fun getUnicodeChar(): Int = 'c'.code
        }
        val hEvent = object : KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_H) {
            override fun getUnicodeChar(): Int = 'h'.code
        }
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(cEvent, viewModel, {}, {}))
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(hEvent, viewModel, {}, {}))
        pumpMainUntil { viewModel.uiState.value.appSearchQuery == "ch" && viewModel.uiState.value.filteredApps.size == 1 }

        assertEquals("ch", viewModel.uiState.value.appSearchQuery)
        assertEquals(1, viewModel.uiState.value.filteredApps.size)
        assertEquals("Chrome", viewModel.uiState.value.filteredApps.first().label)
        assertEquals(0, viewModel.uiState.value.focusedAppIndex)

        // Escape clears search on HOME
        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE),
                viewModel, {}, {}
            )
        )
        pumpMainUntil { viewModel.uiState.value.appSearchQuery.isEmpty() && viewModel.uiState.value.filteredApps.size == 5 }
        assertEquals("", viewModel.uiState.value.appSearchQuery)
        assertEquals(5, viewModel.uiState.value.filteredApps.size)
    }
}
