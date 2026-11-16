package com.example

import android.app.Application
import android.os.Looper
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.example.data.writer.BookEntity
import com.example.data.writer.ChapterEntity
import com.example.data.writer.DocxExporter
import com.example.model.CallsSubTab
import com.example.model.HubSubTab
import com.example.model.MainTab
import com.example.ui.HardwareKeyHandler
import com.example.viewmodel.LauncherViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocxAndTabHoldTest {

    private fun pumpMainUntil(timeoutMs: Long = 4000L, predicate: () -> Boolean) {
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
    fun `docx exporter produces valid openxml zip with book outline and chapters`() {
        val book = BookEntity(
            id = 1L,
            title = "Хроники Symbian & E61i",
            author = "Писатель",
            genre = "Фантастика",
            outlinePlan = "1. Старый телефон\n2. Новый код"
        )
        val chapters = listOf(
            ChapterEntity(id = 10L, bookId = 1L, orderIndex = 0, title = "Глава 1", content = "Текст <первой> главы & диалог."),
            ChapterEntity(id = 11L, bookId = 1L, orderIndex = 1, title = "Глава 2", content = "Продолжение истории.")
        )

        val bytesOut = ByteArrayOutputStream()
        DocxExporter.writeDocxStream(bytesOut, book, chapters, includeOutline = true)

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytesOut.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }

        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("_rels/.rels"))
        assertTrue(entries.containsKey("word/styles.xml"))
        assertTrue(entries.containsKey("word/document.xml"))

        val docXml = entries.getValue("word/document.xml")
        assertTrue(docXml.contains("Хроники Symbian &amp; E61i"))
        assertTrue(docXml.contains("План произведения"))
        assertTrue(docXml.contains("1. Старый телефон"))
        assertTrue(docXml.contains("Глава 1"))
        assertTrue(docXml.contains("Текст &lt;первой&gt; главы &amp; диалог."))
        assertTrue(docXml.contains("Глава 2"))
    }

    @Test
    fun `short tab switches subtabs and holding tab toggles writer mode`() {
        HardwareKeyHandler.resetKeyTracking()
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = LauncherViewModel(app)
        pumpMainUntil { !viewModel.uiState.value.isLoadingApps }

        // Short Tab on CALLS switches LOG -> CONTACTS
        viewModel.selectTab(MainTab.CALLS)
        pumpMainUntil { viewModel.uiState.value.activeTab == MainTab.CALLS }
        assertEquals(CallsSubTab.LOG, viewModel.uiState.value.callsSubTab)

        val shortDown = KeyEvent(1000L, 1000L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB, 0)
        val shortUp = KeyEvent(1000L, 1120L, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB, 0)
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(shortDown, viewModel, {}, {}))
        assertTrue(HardwareKeyHandler.handleAndroidKeyEvent(shortUp, viewModel, {}, {}))
        pumpMainUntil { viewModel.uiState.value.callsSubTab == CallsSubTab.CONTACTS }
        assertFalse(viewModel.uiState.value.isWriterModeOpen)

        // Short Tab on HUB cycles CHATS -> NEWS
        viewModel.selectTab(MainTab.HUB)
        pumpMainUntil { viewModel.uiState.value.activeTab == MainTab.HUB }
        assertEquals(HubSubTab.CHATS, viewModel.uiState.value.hubSubTab)

        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(2000L, 2000L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB, 0),
                viewModel, {}, {}
            )
        )
        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(2000L, 2100L, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB, 0),
                viewModel, {}, {}
            )
        )
        pumpMainUntil { viewModel.uiState.value.hubSubTab == HubSubTab.NEWS }

        // Holding Tab (>= 450ms or repeatCount >= 4) opens Writer Studio
        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(3000L, 3000L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB, 0),
                viewModel, {}, {}
            )
        )
        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(3000L, 3550L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB, 4),
                viewModel, {}, {}
            )
        )
        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(3000L, 3600L, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB, 0),
                viewModel, {}, {}
            )
        )
        pumpMainUntil { viewModel.uiState.value.isWriterModeOpen }
        assertTrue(viewModel.uiState.value.isWriterModeOpen)

        // Holding Tab again exits Writer Studio back to launcher
        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(4000L, 4000L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB, 0),
                viewModel, {}, {}
            )
        )
        assertTrue(
            HardwareKeyHandler.handleAndroidKeyEvent(
                KeyEvent(4000L, 4600L, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB, 0),
                viewModel, {}, {}
            )
        )
        pumpMainUntil { !viewModel.uiState.value.isWriterModeOpen }
        assertFalse(viewModel.uiState.value.isWriterModeOpen)
    }
}
